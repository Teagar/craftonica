#!/usr/bin/env python3
"""Validate an Arduino source bundle and create an immutable compiler snapshot."""

import os
import re
import stat
import sys
from pathlib import Path

MAX_FILES = 16
MAX_FILE_BYTES = 32 * 1024
MAX_TOTAL_BYTES = 64 * 1024
SAFE_NAME = re.compile(r"^[A-Za-z][A-Za-z0-9_.-]{0,63}$")
SAFE_MAIN = re.compile(r"^[A-Za-z][A-Za-z0-9_-]{0,63}\.ino$")
ALLOWED_EXTENSIONS = {".ino", ".h", ".c", ".cpp"}
SYSTEM_HEADERS = {
    "Arduino.h", "stdint.h", "stddef.h", "stdbool.h", "string.h", "stdlib.h",
    "math.h", "limits.h", "float.h", "ctype.h", "WString.h", "Print.h",
}
SYSTEM_PREFIXES = ("avr/", "util/")


def reject(message: str) -> None:
    raise ValueError(message)


def without_comments(data: str, strip_strings: bool = False) -> str:
    out = []
    i = 0
    state = "code"
    while i < len(data):
        c = data[i]
        n = data[i + 1] if i + 1 < len(data) else ""
        if state == "code" and c == "/" and n == "/":
            out.extend("  ")
            i += 2
            state = "line"
        elif state == "code" and c == "/" and n == "*":
            out.extend("  ")
            i += 2
            state = "block"
        elif state == "line":
            out.append("\n" if c == "\n" else " ")
            i += 1
            if c == "\n":
                state = "code"
        elif state == "block":
            if c == "*" and n == "/":
                out.extend("  ")
                i += 2
                state = "code"
            else:
                out.append("\n" if c == "\n" else " ")
                i += 1
        elif state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if c == "\\" and i + 1 < len(data):
                out.extend("  " if strip_strings else data[i:i + 2])
                i += 2
            else:
                out.append(" " if strip_strings and c != "\n" else c)
                i += 1
                if c == quote:
                    state = "code"
        elif c in ('"', "'"):
            state = "string" if c == '"' else "char"
            out.append(" " if strip_strings else c)
            i += 1
        else:
            out.append(c)
            i += 1
    if state == "block":
        reject("unterminated block comment")
    return "".join(out)


def validate_source(name: str, raw: bytes, available_headers: set[str]) -> str:
    if b"\x00" in raw:
        reject(f"{name}: NUL byte is forbidden")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        reject(f"{name}: source must be UTF-8")
    if "%:" in text or "??" in text or "##" in text or "\\u" in text or "\\U" in text:
        reject(f"{name}: alternate or generated preprocessor tokens are forbidden")
    spliced = re.sub(r"\\\r?\n", "", text)
    visible = without_comments(spliced)
    lexical = without_comments(spliced, strip_strings=True)
    if re.search(r"\b(?:__asm__|__asm|asm)\b", lexical):
        reject(f"{name}: inline assembly is forbidden")
    if re.search(r"\b_Pragma\s*\(", lexical):
        reject(f"{name}: pragmas are forbidden")

    logical_lines = visible.splitlines()
    for line in logical_lines:
        if re.match(r"^\s*#\s*(?:pragma|include_next|line)\b", line):
            reject(f"{name}: forbidden preprocessor directive")
        match = re.match(r'^\s*#\s*include\s*([<"])([^>"]+)[>"]\s*$', line)
        if not match:
            if re.match(r"^\s*#\s*include\b", line):
                reject(f"{name}: computed or malformed include is forbidden")
            continue
        delimiter, header = match.groups()
        if header.startswith("/") or ".." in header.split("/") or "\\" in header:
            reject(f"{name}: absolute or traversing include is forbidden")
        if delimiter == '"':
            if header not in available_headers:
                reject(f"{name}: local include is outside the bundle: {header}")
        elif header not in SYSTEM_HEADERS and not header.startswith(SYSTEM_PREFIXES):
            reject(f"{name}: unsupported library/header: {header}")
    return text


def prototypes(sketch: str) -> list[str]:
    clean = without_comments(sketch, strip_strings=True)
    result = []
    depth = 0
    pattern = re.compile(
        r"^\s*((?:(?:static|inline|const|volatile|unsigned|signed|long|short)\s+)*"
        r"[A-Za-z_]\w*(?:\s*[*&]\s*)?)\s+([A-Za-z_]\w*)\s*\(([^{};]*)\)\s*\{"
    )
    for line in clean.splitlines():
        if depth == 0:
            match = pattern.match(line)
            if match:
                return_type, function_name, arguments = match.groups()
                if function_name not in {"if", "for", "while", "switch"}:
                    result.append(f"{return_type.strip()} {function_name}({arguments.strip()});")
        depth += line.count("{") - line.count("}")
        if depth < 0:
            reject("unbalanced braces in sketch")
    if depth != 0:
        reject("unbalanced braces in sketch")
    return list(dict.fromkeys(result))


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: prepare_bundle.py SOURCE_DIR MAIN.ino STAGING_DIR", file=sys.stderr)
        return 2
    source = Path(sys.argv[1])
    main_name = sys.argv[2]
    staging = Path(sys.argv[3])
    if not SAFE_MAIN.fullmatch(main_name):
        reject("unsafe main sketch filename")
    if not source.is_dir() or source.is_symlink():
        reject("source must be a real directory")

    entries = sorted(source.iterdir(), key=lambda path: os.fsencode(path.name))
    if not entries or len(entries) > MAX_FILES:
        reject(f"bundle must contain 1..{MAX_FILES} files")
    headers = {entry.name for entry in entries if entry.suffix == ".h"}
    contents = {}
    raw_contents = {}
    total = 0
    for entry in entries:
        relative = entry.name
        if not SAFE_NAME.fullmatch(relative) or entry.suffix not in ALLOWED_EXTENSIONS:
            reject(f"unsupported or unsafe bundle entry: {relative}")
        try:
            descriptor = os.open(entry, os.O_RDONLY | os.O_NOFOLLOW)
        except OSError:
            reject(f"bundle entries must be regular files: {relative}")
        try:
            before = os.fstat(descriptor)
            if not stat.S_ISREG(before.st_mode):
                reject(f"bundle entries must be regular files: {relative}")
            with os.fdopen(descriptor, "rb", closefd=False) as stream:
                raw = stream.read(MAX_FILE_BYTES + 1)
            after = os.fstat(descriptor)
            if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) != (
                    after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns):
                reject(f"bundle entry changed while being read: {relative}")
        finally:
            os.close(descriptor)
        if len(raw) > MAX_FILE_BYTES:
            reject(f"file exceeds {MAX_FILE_BYTES} bytes: {relative}")
        total += len(raw)
        if total > MAX_TOTAL_BYTES:
            reject(f"bundle exceeds {MAX_TOTAL_BYTES} bytes")
        contents[relative] = validate_source(relative, raw, headers)
        raw_contents[relative] = raw
    if main_name not in contents:
        reject(f"main sketch is missing: {main_name}")

    ino_names = [main_name] + sorted(
        (name for name in contents if name.endswith(".ino") and name != main_name),
        key=os.fsencode,
    )
    sketch_parts = [f'#line 1 "{name}"\n{contents[name]}' for name in ino_names]
    sketch = "\n".join(sketch_parts)
    if not re.search(r"\bvoid\s+setup\s*\(", without_comments(sketch, True)):
        reject("sketch must define void setup()")
    if not re.search(r"\bvoid\s+loop\s*\(", without_comments(sketch, True)):
        reject("sketch must define void loop()")
    prefix = "" if re.search(r"^\s*#\s*include\s*[<\"]Arduino\.h[>\"]", sketch, re.M) else "#include <Arduino.h>\n"
    generated = prefix + "\n".join(prototypes(sketch)) + "\n" + sketch + "\n"

    staging.mkdir(mode=0o700)
    (staging / "sketch.cpp").write_text(generated, encoding="utf-8", newline="\n")
    manifest = ["sketch.cpp"]
    for name in sorted(contents, key=os.fsencode):
        if name.endswith(".ino"):
            continue
        destination = staging / name
        destination.write_bytes(raw_contents[name])
        manifest.append(name)
    (staging / "manifest").write_text("\n".join(manifest) + "\n", encoding="ascii")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print(f"COMPILE_REJECTED: {error}", file=sys.stderr)
        sys.exit(1)
