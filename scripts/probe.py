#!/usr/bin/env python3
"""Exercise the real Codex daemon without touching existing threads.

Uses a deterministic local Responses fixture, not a paid model. --exercise
creates one disposable thread and requests approval for a harmless printf.
Only this probe's request is approved. No user conversation is printed.
"""
import argparse
import sys
import base64
import http.server
import json
import os
import queue
import secrets
import struct
import subprocess
import threading
import time


class Client:
    def __init__(self, command):
        self.proc = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.events = queue.Queue()
        self.next_id = 100
        self.key = base64.b64encode(os.urandom(16)).decode()
        self.proc.stdin.write(("GET / HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n"
                               "Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\n"
                               f"Sec-WebSocket-Key: {self.key}\r\n\r\n").encode())
        self.proc.stdin.flush()
        header = b""
        while not header.endswith(b"\r\n\r\n"):
            b = self.proc.stdout.read(1)
            if not b:
                raise RuntimeError("proxy closed during HTTP upgrade: " + self.proc.stderr.read().decode()[:300])
            header += b
        if not header.startswith(b"HTTP/1.1 101"):
            raise RuntimeError("proxy did not accept WebSocket handshake")
        self.lock = threading.Lock()
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()
        self.request("initialize", {"clientInfo": {"name": "remote_gpt_probe", "version": "0.1.0"}})
        self.send({"method": "initialized", "params": {}})

    def _read(self):
        try:
            pending = b""
            while True:
                header = self.proc.stdout.read(2)
                if len(header) != 2:
                    return
                opcode, size = header[0] & 15, header[1] & 127
                if size == 126:
                    size = struct.unpack("!H", self.proc.stdout.read(2))[0]
                elif size == 127:
                    size = struct.unpack("!Q", self.proc.stdout.read(8))[0]
                data = self.proc.stdout.read(size)
                if opcode == 8:
                    return
                if opcode == 9:
                    self.frame(data, 10)
                    continue
                if opcode in (0, 1):
                    pending += data
                    if header[0] & 128:
                        self.events.put(json.loads(pending))
                        pending = b""
        except Exception as exc:
            self.events.put({"probeError": str(exc)})

    def frame(self, payload, opcode=1):
        mask = os.urandom(4)
        if len(payload) < 126:
            header = bytes([128 | opcode, 128 | len(payload)])
        elif len(payload) < 65536:
            header = bytes([128 | opcode, 254]) + struct.pack("!H", len(payload))
        else:
            header = bytes([128 | opcode, 255]) + struct.pack("!Q", len(payload))
        with self.lock:
            self.proc.stdin.write(header + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))
            self.proc.stdin.flush()

    def send(self, msg):
        self.frame(json.dumps(msg).encode())

    def wait(self, predicate, timeout=25):
        until = time.monotonic() + timeout
        deferred = []
        try:
            while time.monotonic() < until:
                msg = self.events.get(timeout=max(.01, until - time.monotonic()))
                if "probeError" in msg:
                    raise RuntimeError(msg["probeError"])
                if predicate(msg):
                    return msg
                deferred.append(msg)
            raise TimeoutError("expected protocol response did not arrive")
        finally:
            for msg in deferred:
                self.events.put(msg)

    def request(self, method, params=None):
        self.next_id += 1
        rid = self.next_id
        self.send({"method": method, "id": rid, "params": params or {}})
        msg = self.wait(lambda m: m.get("id") == rid and "method" not in m)
        if "error" in msg:
            raise RuntimeError(f"{method}: {msg['error']}")
        return msg["result"]

    def close(self):
        if self.proc.poll() is None:
            self.proc.stdin.close()
            try:
                self.proc.wait(3)
            except subprocess.TimeoutExpired:
                self.proc.terminate()
                self.proc.wait(3)


class ModelFixture(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if self.headers.get("Content-Encoding") == "zstd":
            import zstandard
            raw = zstandard.ZstdDecompressor().decompress(raw)
        body = json.loads(raw)
        inputs = body.get("input", [])
        done = any(i.get("type") == "function_call_output" for i in inputs)
        response_id = "resp_probe_" + secrets.token_hex(4)
        if done:
            item = {"id": "msg_" + response_id, "type": "message", "role": "assistant", "status": "completed",
                    "content": [{"type": "output_text", "text": "Remote GPT approval probe completed.", "annotations": []}]}
        else:
            tools = body.get("tools", [])
            names = [t.get("name") for t in tools]
            name = "exec_command" if "exec_command" in names else "shell_command"
            arguments = {"sandbox_permissions": "require_escalated", "justification": "Approve the isolated Remote GPT connectivity probe?"}
            arguments["cmd" if name == "exec_command" else "command"] = getattr(self, "probe_command", "printf 'remote-gpt-probe\\n'")
            item = {"id": "fc_" + response_id, "type": "function_call", "call_id": "call_" + response_id, "name": name,
                    "arguments": json.dumps(arguments), "status": "completed"}
        events = [
            {"type": "response.created", "response": {"id": response_id, "status": "in_progress"}},
            {"type": "response.output_item.added", "output_index": 0, "item": item},
            {"type": "response.output_item.done", "output_index": 0, "item": item},
            {"type": "response.completed", "response": {"id": response_id, "status": "completed", "output": [item],
                "usage": {"input_tokens": 1, "output_tokens": 1, "total_tokens": 2}}},
        ]
        encoded = "".join(f"event: {e['type']}\ndata: {json.dumps(e)}\n\n" for e in events).encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--exercise", action="store_true")
    ap.add_argument("--ssh-config")
    ap.add_argument("--codex", default="codex")
    args = ap.parse_args()
    if args.exercise:
        from codex_fixture import isolated_codex, require_fixture
        if not args.ssh_config and args.codex == "codex":
            with isolated_codex() as fixture:
                subprocess.run([sys.executable, __file__, "--exercise", "--codex", fixture["codex"]], check=True)
            return
        require_fixture(args.codex)
    command = [args.codex, "app-server", "proxy"]
    if args.ssh_config:
        import shlex
        command = ["ssh", "-F", args.ssh_config, "target", shlex.join(command)]
    a = Client(command)
    clients = [a]
    server = None
    try:
        loaded = a.request("thread/loaded/list")
        print(json.dumps({"check": "handshake_and_loaded_threads", "passed": True, "count": len(loaded["data"])}), flush=True)
        if not args.exercise:
            return
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), ModelFixture)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        provider = {"name": "Remote GPT local test fixture", "base_url": f"http://127.0.0.1:{server.server_port}/v1",
                    "wire_api": "responses", "requires_openai_auth": False, "supports_websockets": False}
        params = {"ephemeral": False, "cwd": "/tmp", "modelProvider": "remote_gpt_fixture", "model": "gpt-5.4",
                  "approvalPolicy": "on-request", "approvalsReviewer": "user", "sandbox": "read-only",
                  "config": {"model_providers.remote_gpt_fixture": provider, "features.unified_exec": True},
                  "developerInstructions": "This is an isolated transport test. Run only the harmless printf requested by the fixture."}
        thread = a.request("thread/start", params)["thread"]["id"]
        b = Client(command)
        clients.append(b)
        a.request("turn/start", {"threadId": thread, "input": [{"type": "text", "text": "Run the isolated approval probe."}]})
        predicate = lambda m: m.get("method") == "item/commandExecution/requestApproval" and m.get("params", {}).get("threadId") == thread
        first = a.wait(predicate, timeout=45)
        b.request("thread/resume", {"threadId": thread, "excludeTurns": True})
        second = b.wait(predicate, timeout=10)
        assert first["id"] == second["id"], "request IDs differed across subscribers"
        print(json.dumps({"check": "two_client_approval_broadcast", "passed": True}), flush=True)
        a.close()
        b.close()
        c = Client(command)
        clients.append(c)
        c.request("thread/resume", {"threadId": thread, "excludeTurns": True})
        replay = c.wait(predicate, timeout=10)
        assert replay["id"] == first["id"]
        c.send({"id": replay["id"], "result": {"decision": "accept"}})
        c.wait(lambda m: m.get("method") == "serverRequest/resolved" and m.get("params", {}).get("threadId") == thread)
        completed = c.wait(lambda m: m.get("method") == "turn/completed" and m.get("params", {}).get("threadId") == thread, timeout=45)
        assert completed["params"]["turn"]["status"] == "completed", completed
        print(json.dumps({"check": "disconnect_resume_pending_approval_and_complete", "passed": True}), flush=True)
        c.request("thread/archive", {"threadId": thread})
    finally:
        for client in clients:
            client.close()
        if server:
            server.shutdown()


if __name__ == "__main__":
    main()
