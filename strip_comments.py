"""Strip Java // and /* */ comments from files, preserving strings/chars.

Also collapses runs of >1 blank lines left behind into a single blank line.
"""
import sys
import re
from pathlib import Path


def strip_java_comments(src: str) -> str:
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        # String literal
        if c == '"':
            out.append(c)
            i += 1
            while i < n:
                ch = src[i]
                out.append(ch)
                if ch == '\\' and i + 1 < n:
                    out.append(src[i + 1])
                    i += 2
                    continue
                if ch == '"':
                    i += 1
                    break
                i += 1
            continue
        # Char literal
        if c == "'":
            out.append(c)
            i += 1
            while i < n:
                ch = src[i]
                out.append(ch)
                if ch == '\\' and i + 1 < n:
                    out.append(src[i + 1])
                    i += 2
                    continue
                if ch == "'":
                    i += 1
                    break
                i += 1
            continue
        # Line comment
        if c == '/' and nxt == '/':
            while i < n and src[i] != '\n':
                i += 1
            continue
        # Block comment
        if c == '/' and nxt == '*':
            i += 2
            while i < n - 1 and not (src[i] == '*' and src[i + 1] == '/'):
                i += 1
            i += 2  # skip */
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def collapse_blank_lines(src: str) -> str:
    # Strip trailing whitespace per line
    lines = [ln.rstrip() for ln in src.splitlines()]
    # Collapse consecutive blank lines
    result = []
    blank = False
    for ln in lines:
        if ln == '':
            if not blank:
                result.append('')
            blank = True
        else:
            result.append(ln)
            blank = False
    # Ensure single trailing newline
    text = '\n'.join(result).rstrip() + '\n'
    return text


def process(path: Path):
    src = path.read_text(encoding='utf-8')
    stripped = strip_java_comments(src)
    cleaned = collapse_blank_lines(stripped)
    path.write_text(cleaned, encoding='utf-8')
    print(f"stripped: {path}")


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        process(Path(arg))
