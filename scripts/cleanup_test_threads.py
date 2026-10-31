#!/usr/bin/env python3
"""One-time, backed-up cleanup of this project's identified historical test threads.

Default is read-only. Never selects a thread solely because its cwd is /tmp.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
from datetime import datetime, timezone
from probe import Client

PROVIDERS = {"remote_gpt_fixture", "remote_gpt_device_fixture", "remote_gpt_image_fixture", "remote_gpt_status_fixture"}
EMPTY_PREFIXES = ("remote-gpt-create-check-", "remote-gpt-empty-check-", "remote-gpt-resume-check-",
                  "remote-gpt-empty-archive-", "remote-gpt-detach-check-", "remote-gpt-availability-")


def identified(row):
    path = Path(row["cwd"])
    if not row["archived"] or not (path == Path("/tmp") or Path("/tmp") in path.parents):
        return False
    if row["model_provider"] in PROVIDERS:
        return True
    return (path.parent == Path("/tmp") and path.name.startswith(EMPTY_PREFIXES)
            and not row["first_user_message"] and row["tokens_used"] == 0)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    data = Path.home() / ".codex"
    db = sqlite3.connect(f"file:{data / 'state_5.sqlite'}?mode=ro", uri=True)
    db.row_factory = sqlite3.Row
    rows = [dict(row) for row in db.execute("SELECT * FROM threads WHERE archived=1") if identified(row)]
    print(json.dumps({"identifiedArchivedTestThreads": len(rows), "apply": args.apply}))
    if not args.apply or not rows:
        return
    backup = Path.home() / ".local/share/remote-gpt/test-cleanup" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup.mkdir(parents=True, mode=0o700)
    manifest = []
    for row in rows:
        source = Path(row["rollout_path"])
        entry = {"thread": row, "backup": None, "sha256": None}
        if source.exists():
            # Only back up known rollout files inside this Codex data directory.
            if data not in source.resolve().parents or source.suffix != ".jsonl":
                raise RuntimeError("Unexpected rollout location; nothing has been deleted")
            target = backup / (row["id"] + ".jsonl")
            shutil.copy2(source, target)
            target.chmod(0o600)
            entry.update(backup=target.name, sha256=hashlib.sha256(target.read_bytes()).hexdigest())
        manifest.append(entry)
    (backup / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    (backup / "manifest.json").chmod(0o600)
    client = Client(["codex", "app-server", "proxy"])
    deleted = []
    try:
        loaded = set()
        cursor = None
        while True:
            page = client.request("thread/loaded/list", {"limit": 100, "cursor": cursor})
            loaded.update(page["data"])
            cursor = page.get("nextCursor")
            if not cursor:
                break
        for entry in manifest:
            row = entry["thread"]
            current = db.execute("SELECT * FROM threads WHERE id=?", (row["id"],)).fetchone()
            if current is None or dict(current) != row or row["id"] in loaded:
                raise RuntimeError("Thread changed or is loaded; cleanup stopped with backup retained")
            source = Path(row["rollout_path"])
            if entry["sha256"] and hashlib.sha256(source.read_bytes()).hexdigest() != entry["sha256"]:
                raise RuntimeError("Rollout changed after backup; cleanup stopped")
            client.request("thread/delete", {"threadId": row["id"]})
            deleted.append(row["id"])
            (backup / "deleted.json").write_text(json.dumps(deleted, indent=2))
        remaining = db.execute("SELECT count(*) FROM threads WHERE id IN (" + ",".join("?" for _ in rows) + ")", [r["id"] for r in rows]).fetchone()[0]
        print(json.dumps({"deleted": len(deleted), "remainingIdentified": remaining, "backup": str(backup)}))
        if remaining:
            raise RuntimeError("Server deletion did not remove all identified records")
    finally:
        client.close()


if __name__ == "__main__":
    main()
