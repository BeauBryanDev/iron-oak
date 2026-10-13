#%% Config
import re
from html.parser import HTMLParser
from pathlib import Path

import pymupdf
import pymupdf4llm
from pymupdf4llm.helpers.pymupdf_rag import IdentifyHeaders

ROOT_DIR = Path(__file__).resolve().parent.parent
KNOWLEDGE_DIR = ROOT_DIR / "knowledge"
OUTPUT_DIR = KNOWLEDGE_DIR / "md"

# PDFs are looked up in the repo root first, then in knowledge/.
# pages: 1-based, inclusive (PDF page numbers, not printed ones).
# top / bottom: points cropped off every page (running headers, footers).
# columns: 2 reads the left column, then the right one, split at the widest
#          gap between them; a title crossing that gap is read full width first.
# strip_captions: drop _italic_ runs in long lines (Ontario's inline captions);
#          off by default, because most books put real terms in italics.
PDF_JOBS = {
    "Metal_Cutting_4th_Edition.pdf": {
        "pages": (9, 451),
        "top": 68,      # headers sit at y 42-63; body text starts below 75
        "bottom": 22,
        "columns": 1,
    },
    "VMC650-Operation-manual.pdf": {
        "pages": (2, 13),
        "top": 0,
        "bottom": 0,
        "columns": 1,
    },
    "CNC-Programming-Handbook-3rd.pdf": {
        "pages": (21, 50),
        "top": 80,
        "bottom": 10,
        "columns": 2,
    },
}
"""Earlier runs:
    "Ontario-hand-and-power-tools.pdf":
        {"top": 40, "bottom": 50, "columns": 2, "strip_captions": True}
    "Electrical_Mesurement_Equipments.pdf": {"top": 0, "bottom": 0, "columns": 1}
    UCR-ME-SOP-Manual Milling Machines-v5.pdf, OSHA3170.pdf, EMC2_User_Manual.pdf,
    Home_Maintenance_For_Dummies.pdf
"""
FOOTER_RE = re.compile(r"^\s*\d+\s*\|\s*P\s*a\s*g\s*e\s*$", re.MULTILINE)
TAG_RE = re.compile(r"</?(u|mark|b|i|sub|sup)>")
# text pymupdf4llm reads out of figures (labels, dials): images are skipped,
# so their inner text goes too
PICTURE_TEXT_RE = re.compile(
    r"<!-- Start of picture text -->.*?<!-- End of picture text -->",
    re.DOTALL)
# a printed page number left alone on the last line of a page
TRAILING_PAGE_NO_RE = re.compile(r"\n\s*\**\d{1,4}\**\s*$")
# stray control characters (the CNC handbook draws its bullets as \x02)
CONTROL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\ufffd]")


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
    text = PICTURE_TEXT_RE.sub("", text)
    text = TAG_RE.sub("", text)
    text = CONTROL_RE.sub("", text)
    text = FOOTER_RE.sub("", text)
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = TRAILING_PAGE_NO_RE.sub("", text.strip())
    return text.strip()


def strip_inline_captions(text):
    lines = []
    for line in text.split("\n"):
        if len(line) > 100 and not line.startswith("#"):
            line = re.sub(r"\s*_(?:Figure [0-9.a-z]+|[^_]{1,60})_", "", line)
        lines.append(line)
    return "\n".join(lines)


def find_pdf(name):

    for folder in (ROOT_DIR, KNOWLEDGE_DIR):
        if (folder / name).exists():
            return folder / name

    raise FileNotFoundError(name)


TITLE_FONT_SIZE = 14   # a line at least this large near the top is a title
MIN_COLUMN_GAP = 12    # points of clear space needed between two columns


def title_band(page, top):
    """Bottom y of the large title lines at the top of a page (a chapter
    opening), or None. Read full width, so a title is never cut in two."""

    titles = [line["bbox"]
              for block in page.get_text("dict")["blocks"]
              for line in block.get("lines", [])
              if line["spans"][0]["size"] >= TITLE_FONT_SIZE
              and line["bbox"][1] >= top
              and line["bbox"][3] < page.rect.height * 0.4]

    return max(b[3] for b in titles) + 2 if titles else None


def column_split(page, top, bottom):
    """x of the widest clear vertical gap near the middle, or None when there
    is none (a full-width table or figure page: read it as one column)."""

    width = page.rect.width
    words = [w for w in page.get_text("words")
             if w[1] >= top and w[3] <= page.rect.height - bottom]
    lo = int(width * 0.45)
    hi = int(width * 0.60)
    free = [x for x in range(lo, hi)
            if not any(w[0] < x < w[2] for w in words)]

    best_start = None
    best_len = 0
    run_start = None
    run_len = 0
    prev = None

    for x in free:

        if prev is not None and x == prev + 1:
            run_len += 1
        else:
            run_start, run_len = x, 1

        if run_len > best_len:
            best_start, best_len = run_start, run_len

        prev = x

    if best_len < MIN_COLUMN_GAP:
        return None

    return best_start + best_len / 2


def region_markdown(doc,
                    i,
                    hdr_info,
                    rect,
                    strip_captions):

    page = doc[i]
    page.set_cropbox(page.mediabox)
    page.set_cropbox(rect)
    md = pymupdf4llm.to_markdown(doc,
                                 pages=[i],
                                 hdr_info=hdr_info,
                                 ignore_images=True,
                                 use_ocr=False,
                                 table_output="html")
    body = clean_page(md)

    return strip_inline_captions(body) if strip_captions else body


def convert(pdf_path,
            out_path,
            pages,
            top=0,
            bottom=0,
            columns=1,
            strip_captions=False):
    """Crops each page (and splits it in columns), then writes one
    <!-- page N --> block per PDF page, numbered as in the PDF."""

    doc = pymupdf.open(str(pdf_path))
    hdr_info = IdentifyHeaders(doc)
    first, last = pages
    parts = []

    for i in range(first - 1, last):

        page = doc[i]
        page.set_cropbox(page.mediabox)
        width = page.rect.width
        height = page.rect.height
        regions = []

        body_top = top
        split = None

        if columns == 2:
            body_top = title_band(page, top) or top
            split = column_split(page, body_top, bottom)

        if body_top > top:
            regions.append(pymupdf.Rect(0, top, width, body_top))

        if split is None:
            regions.append(pymupdf.Rect(0, body_top, width, height - bottom))
        else:
            regions.append(pymupdf.Rect(0, body_top, split, height - bottom))
            regions.append(pymupdf.Rect(split, body_top, width, height - bottom))

        texts = [region_markdown(doc, i, hdr_info, r, strip_captions)
                 for r in regions]
        texts = [t for t in texts if t]

        if texts:
            parts.append(f"<!-- page {i + 1} -->\n\n" + "\n\n".join(texts))

    out_path.write_text("\n\n".join(parts) + "\n", encoding="utf-8")

    return last - first + 1, out_path.stat().st_size


#%% Run
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

for name, job in PDF_JOBS.items():

    out = OUTPUT_DIR / (Path(name).stem + ".md")
    pages, size = convert(find_pdf(name), out, **job)

    print(f"{name}: {pages} pages, {size / 1024:.0f} KB -> {out}")
