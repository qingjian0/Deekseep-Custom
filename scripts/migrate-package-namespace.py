#!/usr/bin/env python3
"""One-time, repository-wide migration from com.dsmod.probe to ds.dsh.DeepSeek."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OLD = "com.dsmod.probe"
NEW = "ds.dsh.DeepSeek"
OLD_PATH = "com/dsmod/probe"
NEW_PATH = "ds/dsh/DeepSeek"

raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
paths = [p.decode() for p in raw.split(b"\\0") if p]
changed = []
renames = []

# Rewrite every tracked UTF-8 text file, not only Java/Kotlin declarations.
for rel in paths:
    src = ROOT / rel
    if not src.is_file():
        continue
    try:
        original = src.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    updated = original.replace(OLD, NEW).replace(OLD_PATH, NEW_PATH)
    if updated != original:
        src.write_text(updated, encoding="utf-8", newline="")
        changed.append(rel)

# Relocate all source/resource files whose directory hierarchy encoded the old package.
for rel in paths:
    if OLD_PATH not in rel:
        continue
    src = ROOT / rel
    if not src.exists() or not src.is_file():
        continue
    target_rel = rel.replace(OLD_PATH, NEW_PATH)
    dst = ROOT / target_rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists():
        raise SystemExit(f"Refusing to overwrite existing path: {target_rel}")
    src.replace(dst)
    renames.append((rel, target_rel))

# Remove empty legacy package directories left behind by file moves.
for directory in sorted(ROOT.rglob("com/dsmod/probe"), key=lambda p: len(p.parts), reverse=True):
    if directory.is_dir():
        try:
            directory.rmdir()
        except OSError:
            pass

# Fail closed if any tracked text file still contains the old namespace/path.
remaining = []
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts or path == Path(__file__):
        continue
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    if OLD in content or OLD_PATH in content:
        remaining.append(str(path.relative_to(ROOT)))
if remaining:
    raise SystemExit("Old package reference remains in: " + ", ".join(remaining[:50]))

subprocess.check_call(["git", "add", "-A"], cwd=ROOT)
print(f"Updated text files: {len(changed)}")
print(f"Relocated package files: {len(renames)}")
print(f"New package: {NEW}")
