"""Strip Python # comments from files using the tokenize module.

Preserves strings (including # inside them) and docstrings. Collapses
runs of blank lines to a single blank line and trims trailing whitespace.
"""
import io
import sys
import tokenize
from pathlib import Path


def strip_python_comments(src: str) -> str:
    out_tokens = []
    try:
        tokens = list(tokenize.generate_tokens(io.StringIO(src).readline))
    except tokenize.TokenizeError:
        return src
    for tok in tokens:
        if tok.type == tokenize.COMMENT:
            continue
        out_tokens.append(tok)
    try:
        return tokenize.untokenize(out_tokens)
    except ValueError:
        # Fallback: line-based strip outside of strings is hard; bail.
        return src


def collapse_blank_lines(src: str) -> str:
    lines = [ln.rstrip() for ln in src.splitlines()]
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
    return '\n'.join(result).rstrip() + '\n'


def process(path: Path):
    src = path.read_text(encoding='utf-8')
    stripped = strip_python_comments(src)
    cleaned = collapse_blank_lines(stripped)
    if cleaned != src:
        path.write_text(cleaned, encoding='utf-8')
        print(f"stripped: {path}")
    else:
        print(f"unchanged: {path}")


if __name__ == '__main__':
    for arg in sys.argv[1:]:
        process(Path(arg))
