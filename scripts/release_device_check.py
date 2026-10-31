#!/usr/bin/env python3
"""Exercise R8 release against disposable SSH keys/server; no Codex daemon or paid model."""
import base64
import getpass
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

adb = os.environ.get("ADB", "adb")
serial = os.environ.get("ANDROID_SERIAL")
if not serial:
    devices = [line.split()[0] for line in subprocess.check_output([adb, "devices"], text=True).splitlines()[1:]
               if line.strip().endswith("device")]
    if len(devices) != 1: raise SystemExit("Select one disposable emulator with ANDROID_SERIAL")
    serial = devices[0]
if not serial.startswith("emulator-"): raise SystemExit("Use a disposable emulator for release tests")
base = [adb, "-s", serial]

with tempfile.TemporaryDirectory(prefix="ghub-release-ssh-") as folder:
    root = Path(folder)
    for name, kind, phrase in (("host", "ed25519", ""), ("ed25519", "ed25519", "fixture-passphrase"), ("rsa", "rsa", "fixture-passphrase")):
        args = ["ssh-keygen", "-q", "-t", kind, "-N", phrase, "-f", str(root / name)]
        if kind == "rsa": args += ["-m", "PEM", "-b", "2048"]
        subprocess.run(args, check=True)
    (root / "authorized_keys").write_text((root / "ed25519.pub").read_text() + (root / "rsa.pub").read_text())
    fingerprint = subprocess.check_output(["ssh-keygen", "-lf", str(root / "host.pub"), "-E", "sha256"], text=True).split()[1]
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    (root / "sshd.conf").write_text(f"""Port {port}
ListenAddress 127.0.0.1
HostKey {root}/host
PidFile {root}/pid
AuthorizedKeysFile {root}/authorized_keys
StrictModes no
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
UsePAM no
AllowUsers {getpass.getuser()}
Subsystem sftp internal-sftp
LogLevel ERROR
""")
    process = subprocess.Popen([shutil.which("sshd") or "/usr/sbin/sshd", "-D", "-f", str(root / "sshd.conf"), "-E", str(root / "sshd.log")])
    try:
        for _ in range(100):
            if process.poll() is not None: raise RuntimeError((root / "sshd.log").read_text())
            try:
                with socket.create_connection(("127.0.0.1", port), .1): break
            except OSError: time.sleep(.05)
        else: raise RuntimeError("SSH fixture did not start")
        subprocess.run(base + ["reverse", f"tcp:{port}", f"tcp:{port}"], check=True)
        values = {"releaseSshHost": "127.0.0.1", "class": "dev.remotegpt.app.ReleaseRuntimeDeviceTest", "releaseSshPort": str(port),
                  "releaseSshUser": getpass.getuser(), "releaseFingerprint": fingerprint, "releaseRoot": str(root),
                  "releaseEd25519": base64.b64encode((root / "ed25519").read_bytes()).decode(),
                  "releaseRsa": base64.b64encode((root / "rsa").read_bytes()).decode()}
        result = subprocess.run(["./gradlew", "-PtestBuildType=release", ":app:connectedReleaseAndroidTest"] +
                                [f"-Pandroid.testInstrumentationRunnerArguments.{key}={value}" for key, value in values.items()])
        if result.returncode:
            print((root / "sshd.log").read_text())
            raise SystemExit("Release device check failed; see Gradle report")
        reports = Path("app/build/outputs/androidTest-results/connected/release").rglob("TEST-*.xml")
        cases = [case for report in reports for case in ET.parse(report).getroot().iter("testcase")
                 if case.get("classname", "").endswith("ReleaseRuntimeDeviceTest")]
        if len(cases) != 1 or any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
            raise SystemExit("Expected one passing release runtime test; refusing an empty/skipped run")
    finally:
        subprocess.run(base + ["reverse", "--remove", f"tcp:{port}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        process.terminate()
        try: process.wait(timeout=10)
        except subprocess.TimeoutExpired: process.kill(); process.wait()
