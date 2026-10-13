
#%% Config
import json
import re
import statistics
import uuid
from collections import defaultdict
from pathlib import Path

import tiktoken

KNOWLEDGE_DIR = Path("knowledge")
MD_DIR = KNOWLEDGE_DIR / "md"
OUTPUT_PATH = KNOWLEDGE_DIR / "chunks.jsonl"
STATS_PATH = KNOWLEDGE_DIR / "chunks_stats.json"

MAX_TOKENS = 512
OVERLAP_TOKENS = 48
MERGE_BELOW_TOKENS = 60
MIN_CHUNK_TOKENS = 15
NAV_MIN_LINES = 5
NAV_LINE_RATIO = 0.6

DOC_TITLES = {
    "IRON_OAK_INNER_BOOK": "Iron Oak Company Book",
    "Products_and_Service": "Iron Oak Products and Services",
    "Home_Maintenance_For_Dummies": "Home Maintenance For Dummies",
    "Maintenance-Fundamentals-2nd-Ed": "Maintenance Fundamentals, 2nd Edition",
    "CNC_Fundamentals": "CNC Fundamentals",
    "EMC2_User_Manual": "EMC2 User Manual",
    "UCR-ME-SOP-Manual-Milling-Machines-v5": "UCR Standard Operating Procedures for Manual Milling Machines",
    "Hand-and-portable-power-tools": "Hand and Portable Power Tools",
    "Hand-and-power-tools": "Hand and Power Tools",
    "Hand-Power-tool-Safety": "Hand and Power Tool Safety",
    "Ontario-hand-and-power-tools": "Ontario Hand and Power Tools",
    "OSHA3170": "OSHA 3170 Safeguarding Equipment and Protecting Workers from Amputations",
    "SPP-1910.241": "SPP 1910.241 Hand and Portable Power Tools",
    "Electrical_Mesurement_Equipments" : "Electrical Mesurement Equipment",
    "Metal-Cutting-Notes": "Metal Cutting Notes",
    "Metal_Cutting_4th_Edition": "Metal Cutting, 4th Edition (Trent and Wright)",
    "CNC-Programming-Handbook-3rd": "CNC Programming Handbook, 3rd Edition (excerpt)",
    "VMC650-Operation-manual": "VMC650 Vertical Machining Center Operation Manual",
    "Machines-Models-for-Sales": "AVIA Vertical Machining Centres: Machine Models for Sale",
    "Iron_Oak_Business_Rules": "Iron Oak Business Rules",
}
DOC_TYPES = {
    "IRON_OAK_INNER_BOOK": "policy",
    "Products_and_Service": "catalog",
    "OSHA3170": "safety_guide",
    "Hand-Power-tool-Safety": "safety_guide",
    "SPP-1910.241": "regulation",
    "Iron_Oak_Business_Rules": "policy",
    "Machines-Models-for-Sales": "catalog",
}
DEFAULT_DOC_TYPE = "manual"
NAV_EXEMPT_DOC_TYPES = {"policy", "catalog"}

HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
PAGE_RE = re.compile(r"^<!--\s*page\s+(\d+)\s*-->\s*$")
FENCE_RE = re.compile(r"^\s*```")
NAV_LINE_RE = re.compile(r"(?:\s|\.{2,}|\|)\d{1,4}\s*\|?\s*$|,\s*\d{1,4}(?:\s*[,-]\s*\d{1,4})*\s*$")

ENC = tiktoken.encoding_for_model("text-embedding-3-small")


#%% Token helpers
def count_tokens(text):
    return len(ENC.encode(text, disallowed_special=()))


def hard_split(text, budget):
    ids = ENC.encode(text, disallowed_special=())
    return [ENC.decode(ids[i:i + budget]) for i in range(0, len(ids), budget)]


def split_text(text, budget, level=0):
    if count_tokens(text) <= budget:
        return [text]
    if level == 0:
        parts, joiner = text.split("\n"), "\n"
    elif level == 1:
        parts, joiner = re.split(r"(?<=[.!?;])\s+", text), " "
    else:
        return hard_split(text, budget)
    if len(parts) == 1:
        return split_text(text, budget, level + 1)
    out, current = [], ""
    for part in parts:
        pieces = split_text(part, budget, level + 1) if count_tokens(part) > budget else [part]
        for piece in pieces:
            candidate = current + joiner + piece if current else piece
            if count_tokens(candidate) <= budget:
                current = candidate
            else:
                if current:
                    out.append(current)
                current = piece
    if current:
        out.append(current)
    return out


def tail_text(text, n):
    ids = ENC.encode(text, disallowed_special=())
    if len(ids) <= n:
        return ""
    tail = ENC.decode(ids[-n:])
    cut = tail.find(" ")
    return tail[cut + 1:].strip() if cut != -1 else ""


#%% Parsing
def clean_title(text):
    text = re.sub(r"<[^>]+>", "", text)
    return " ".join(re.sub(r"[*_`]", "", text).split())


def parse_sections(text):
    current = {"path": [], "level": 0, "title": "", "page": None, "blocks": []}
    sections = [current]
    stack = {}
    page = None
    in_fence = False
    buf, buf_page = [], None

    def flush():
        nonlocal buf, buf_page
        if buf:
            current["blocks"].append(("\n".join(buf).strip(), buf_page))
        buf, buf_page = [], None

    for line in text.splitlines():
        if FENCE_RE.match(line):
            in_fence = not in_fence
        if not in_fence:
            page_match = PAGE_RE.match(line)
            if page_match:
                flush()
                page = int(page_match.group(1))
                continue
            heading = HEADING_RE.match(line)
            if heading and len(heading.group(1)) <= 3:
                title = clean_title(heading.group(2))
                if title:
                    flush()
                    level = len(heading.group(1))
                    stack = {k: v for k, v in stack.items() if k < level}
                    stack[level] = title
                    current = {
                        "path": [stack[k] for k in sorted(stack)],
                        "level": level,
                        "title": title,
                        "page": page,
                        "blocks": [],
                    }
                    sections.append(current)
                    continue
            if not line.strip():
                flush()
                continue
        if not buf:
            buf_page = page
        buf.append(line)
    flush()
    return sections


def merge_small_sections(sections):
    merged, carry = [], []
    for i, sec in enumerate(sections):
        is_last = i == len(sections) - 1
        blocks = carry + sec["blocks"]
        tokens_total = sum(count_tokens(t) for t, _ in blocks)
        can_merge = not is_last and (
            sec["level"] == 0 or sections[i + 1]["path"][:-1] == sec["path"][:-1]
        )
        if tokens_total < MERGE_BELOW_TOKENS and can_merge:
            heading = [("#" * sec["level"] + " " + sec["title"], sec["page"])] if sec["title"] else []
            carry = carry + heading + sec["blocks"]
            continue
        merged.append(dict(sec, blocks=blocks))
        carry = []
    return merged


#%% Chunking
def build_prefix(doc_title, path):
    prefix = f"Document: {doc_title}\n"
    if path:
        prefix += f"Section: {' > '.join(path)}\n"
    return prefix + "\n"


def fit_prefix(doc_title, path):
    for keep in (len(path), 2, 1, 0):
        prefix = build_prefix(doc_title, path[-keep:] if keep else [])
        budget = MAX_TOKENS - count_tokens(prefix) - OVERLAP_TOKENS - 4
        if budget >= 128:
            return prefix, budget
    return prefix, max(budget, 64)


def glue_headings(blocks):
    glued, pending = [], None
    for text, page in blocks:
        if pending:
            text, page = pending[0] + "\n\n" + text, pending[1]
            pending = None
        if text.splitlines()[-1].startswith("#"):
            pending = (text, page)
            continue
        glued.append((text, page))
    if pending:
        glued.append(pending)
    return glued


def pack_blocks(blocks, budget):
    units = []
    for text, page in glue_headings(blocks):
        for piece in split_text(text, budget):
            units.append((piece, page, count_tokens(piece)))
    chunks, current, current_tokens, current_page = [], [], 0, None
    for text, page, tokens in units:
        extra = tokens + (2 if current else 0)
        if current and current_tokens + extra > budget:
            chunks.append(("\n\n".join(current), current_page))
            current, current_tokens, extra = [], 0, tokens
        if not current:
            current_page = page
        current.append(text)
        current_tokens += extra
    if current:
        chunks.append(("\n\n".join(current), current_page))
    return chunks


def is_navigation(body):
    lines = [line for line in body.splitlines() if line.strip()]
    if len(lines) < NAV_MIN_LINES:
        return False
    hits = sum(1 for line in lines if NAV_LINE_RE.search(line))
    return hits / len(lines) >= NAV_LINE_RATIO


def chunk_type(body, doc_type, section_path):
    if doc_type in NAV_EXEMPT_DOC_TYPES:
        return "content"
    if not is_navigation(body):
        return "content"
    title = " ".join(section_path).lower()
    if "contents" in title:
        return "toc"
    return "navigation"


def chunk_document(path, namespace):
    stem = path.stem
    doc_title = DOC_TITLES.get(stem, stem.replace("_", " ").replace("-", " "))
    doc_type = DOC_TYPES.get(stem, DEFAULT_DOC_TYPE)
    sections = merge_small_sections(parse_sections(path.read_text(encoding="utf-8")))
    records, dropped, index = [], 0, 0
    for sec in sections:
        if not sec["blocks"]:
            continue
        prefix, budget = fit_prefix(doc_title, sec["path"])
        previous_body = None
        for body, page in pack_blocks(sec["blocks"], budget):
            if count_tokens(body) < MIN_CHUNK_TOKENS:
                dropped += 1
                continue
            overlap = tail_text(previous_body, OVERLAP_TOKENS) if previous_body else ""
            text = prefix + (overlap + "\n\n" if overlap else "") + body
            if count_tokens(text) > MAX_TOKENS:
                text = prefix + body
            previous_body = body
            records.append({
                "id": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{namespace}/{path.name}#{index}")),
                "text": text,
                "namespace": namespace,
                "source": path.name,
                "doc_title": doc_title,
                "doc_type": doc_type,
                "section": " > ".join(sec["path"]),
                "page": page if page is not None else sec["page"],
                "chunk_index": index,
                "tokens": count_tokens(text),
                "chunk_type": chunk_type(body, doc_type, sec["path"]),
            })
            index += 1
    return records, dropped


#%% Run
all_records = []
stats = defaultdict(lambda: {"files": {}, "tokens": [], "dropped": 0, "navigation": 0, "over_limit": 0})
for ns_dir in sorted(p for p in MD_DIR.iterdir() if p.is_dir()):
    for md_file in sorted(ns_dir.glob("*.md")):
        records, dropped = chunk_document(md_file, ns_dir.name)
        entry = stats[ns_dir.name]
        entry["files"][md_file.name] = len(records)
        entry["dropped"] += dropped
        for rec in records:
            entry["tokens"].append(rec["tokens"])
            entry["navigation"] += rec["chunk_type"] == "navigation"
            entry["over_limit"] += rec["tokens"] > MAX_TOKENS
        all_records.extend(records)

OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
with OUTPUT_PATH.open("w", encoding="utf-8") as f:
    for rec in all_records:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")


#%% Report
header = f"{'namespace':<22}{'files':>6}{'chunks':>8}{'min':>6}{'avg':>7}{'p95':>6}{'max':>6}{'>max':>6}{'nav':>6}{'drop':>6}"
print(header)
summary = {}
for ns, entry in sorted(stats.items()):
    toks = sorted(entry["tokens"])
    if not toks:
        continue
    p95 = toks[min(len(toks) - 1, int(len(toks) * 0.95))]
    avg = statistics.mean(toks)
    print(
        f"{ns:<22}{len(entry['files']):>6}{len(toks):>8}{toks[0]:>6}{avg:>7.0f}{p95:>6}{toks[-1]:>6}"
        f"{entry['over_limit']:>6}{entry['navigation']:>6}{entry['dropped']:>6}"
    )
    summary[ns] = {
        "files": entry["files"],
        "chunks": len(toks),
        "tokens_min": toks[0],
        "tokens_avg": round(avg, 1),
        "tokens_p95": p95,
        "tokens_max": toks[-1],
        "over_limit": entry["over_limit"],
        "navigation_chunks": entry["navigation"],
        "dropped_tiny": entry["dropped"],
    }
print(f"total chunks: {len(all_records)}, total tokens: {sum(r['tokens'] for r in all_records)}")
STATS_PATH.write_text(json.dumps(summary, indent=2), encoding="utf-8")


#%% Review navigation chunks
for rec in [r for r in all_records if r["chunk_type"] == "navigation"][:10]:
    print(rec["namespace"], rec["source"], "page", rec["page"], "|", rec["section"])



#%% Inspect
for rec in all_records:
    if rec["source"].startswith("Maintenance") and rec["page"] in (5, 207, 424):
        print(rec["chunk_type"], rec["page"], rec["section"])
        print(rec["text"][:600])
        print("-----")


