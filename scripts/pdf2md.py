#%% Config
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

import pymupdf4llm

KNOWLEDGE_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = KNOWLEDGE_DIR / "md"
PDF_FILES = sys.argv[1:] or sorted(p.name for p in KNOWLEDGE_DIR.glob("*.pdf"))

FOOTER_RE = re.compile(r"^\s*\d+\s*\|\s*P\s*a\s*g\s*e\s*$", re.MULTILINE)
TAG_RE = re.compile(r"</?(u|mark|b|i|sub|sup)>")


#%% Helpers
class TableParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.row = None
        self.cell = None

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.row = []
        elif tag in ("td", "th"):
            self.cell = []
        elif tag == "br" and self.cell is not None:
            self.cell.append(" ")

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.cell is not None:
            self.row.append(" ".join("".join(self.cell).split()))
            self.cell = None
        elif tag == "tr" and self.row is not None:
            if any(self.row):
                self.rows.append(self.row)
            self.row = None

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)


def table_to_markdown(html):
    parser = TableParser()
    parser.feed(html)
    rows = parser.rows
    if not rows:
        return ""
    width = max(len(r) for r in rows)
    if width == 2:
        return "\n".join(f"- {a}: {b}" if a else f"- {b}" for a, b in (r + [""] * (2 - len(r)) for r in rows))
    rows = [r + [""] * (width - len(r)) for r in rows]
    lines = ["| " + " | ".join(rows[0]) + " |", "|" + "---|" * width]
    lines += ["| " + " | ".join(r) + " |" for r in rows[1:]]
    return "\n".join(lines)


def clean_page(text):
    text = re.sub(r"<table>.*?</table>", lambda m: "\n" + table_to_markdown(m.group(0)) + "\n", text, flags=re.DOTALL)
    text = TAG_RE.sub("", text)
    text = FOOTER_RE.sub("", text)
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def convert(pdf_path, out_path):
    chunks = pymupdf4llm.to_markdown(
        str(pdf_path),
        page_chunks=True,
        use_ocr=False,
        ignore_images=True,
        table_output="html",
    )
    parts = []
    for chunk in chunks:
        page = chunk["metadata"].get("page_number", chunk["metadata"].get("page"))
        body = clean_page(chunk["text"])
        if body:
            parts.append(f"<!-- page {page} -->\n\n{body}")
    out_path.write_text("\n\n".join(parts) + "\n", encoding="utf-8")
    return len(chunks), out_path.stat().st_size


#%% Run
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
for name in PDF_FILES:
    pages, size = convert(KNOWLEDGE_DIR / name, OUTPUT_DIR / (Path(name).stem + ".md"))
    print(f"{name}: {pages} pages, {size / 1024:.0f} KB")

