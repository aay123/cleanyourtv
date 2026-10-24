#!/usr/bin/env python3
"""Copy the tested TV lists from examples/ into the phone app's assets.

Run from the repository root:

    python3 phone-app/tools/bundle_lists.py

The app never edits these files. A list is copied as-is (so its comments, including the
"keep this package" notes, travel with it) and catalog.json carries the README row for each one.
Re-run this after changing examples/ and commit the assets with the app.
"""
from __future__ import annotations

import hashlib
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXAMPLES = ROOT / "examples"
ASSETS = ROOT / "phone-app" / "app" / "src" / "main" / "assets"
LISTS = ASSETS / "lists"
PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
ROW_RE = re.compile(r"^\| `([^`/]+)/` \| ([^|]*) \| ([^|]*) \| ([^|]*) \|$")


def read_packages(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    names: list[str] = []
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        name = line.split()[0]
        if PACKAGE_RE.fullmatch(name) and name not in names:
            names.append(name)
    return names


def read_header(path: Path) -> list[str]:
    lines: list[str] = []
    for line in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped.startswith("#"):
            break
        text = stripped.lstrip("#").strip()
        if text:
            lines.append(text)
        if len(lines) >= 14:
            break
    return lines


def read_readme_rows() -> dict[str, dict[str, str]]:
    rows: dict[str, dict[str, str]] = {}
    text = (EXAMPLES / "README.md").read_text(encoding="utf-8-sig")
    for line in text.splitlines():
        match = ROW_RE.match(line.strip())
        if not match:
            continue
        folder, title, os_name, notes = (part.strip() for part in match.groups())
        rows[folder] = {"title": title, "os": os_name, "notes": notes}
    return rows


def main() -> None:
    rows = read_readme_rows()
    LISTS.mkdir(parents=True, exist_ok=True)
    entries: list[dict[str, object]] = []
    seen: set[str] = set()
    digest = hashlib.sha256()

    for folder in sorted(p for p in EXAMPLES.iterdir() if p.is_dir()):
        source = folder / "debloat.txt"
        if not source.is_file():
            continue
        names = read_packages(source)
        if not names:
            continue
        row = rows.get(folder.name, {})
        header = read_header(source)
        title = row.get("title") or (header[0] if header else folder.name)
        payload = source.read_bytes()
        digest.update(folder.name.encode())
        digest.update(payload)
        shutil.copyfile(source, LISTS / f"{folder.name}.txt")
        seen.add(f"{folder.name}.txt")
        entries.append(
            {
                "id": folder.name,
                "title": title,
                "os": row.get("os", ""),
                "notes": row.get("notes", ""),
                "header": header,
                "packageCount": len(names),
            }
        )

    for stale in LISTS.glob("*.txt"):
        if stale.name not in seen:
            stale.unlink()

    catalog = {
        "version": 1,
        "source": "examples/",
        "digest": digest.hexdigest()[:16],
        "lists": entries,
    }
    (ASSETS / "catalog.json").write_text(
        json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )
    print(f"bundled {len(entries)} lists into {LISTS}")
    print(f"catalog: {ASSETS / 'catalog.json'}")


if __name__ == "__main__":
    main()
