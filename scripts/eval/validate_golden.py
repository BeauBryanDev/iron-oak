"""Checks eval/golden_set.json against knowledge/chunks.jsonl and the registered Piper tools.

Run from the repo root: python scripts/eval/validate_golden.py
Exit code 1 when anything is wrong. The normalisation here must match
src/test/java/com/ironoak/eval/TextNormalizer.java.
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GOLDEN = ROOT / "eval" / "golden_set.json"
CHUNKS = ROOT / "knowledge" / "chunks.jsonl"
TOOLS_DIR = ROOT / "src" / "main" / "java" / "com" / "ironoak" / "agent" / "tools"

# 1,500 / 24,000 (comma groups) and 24 000 / 12 800 (space groups, 1-2 leading digits only,
# so table columns such as "700 850 1000" are not merged)
COMMA_THOUSANDS = re.compile(r"(?<![\d.,])([1-9]\d{0,2})((?:,\d{3})+)(?!\d)")
SPACE_THOUSANDS = re.compile(r"(?<![\d.,])([1-9]\d?)((?: \d{3})+)(?!\d)")
TIMES = re.compile(r"(\d)\s*[x×]\s*(\d)")
SPACES = re.compile(r"\s+")


def normalize(text: str) -> str:
    text = text.replace("¼", "1/4").replace("½", "1/2")
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c)).lower()
    text = text.replace("\u2044", "/")  # NFKD turns ⅛ into 1⁄8 (fraction slash)
    for pattern in (COMMA_THOUSANDS, SPACE_THOUSANDS):
        text = pattern.sub(lambda m: m.group(1) + re.sub(r"[ ,]", "", m.group(2)), text)
    text = TIMES.sub(r"\1x\2", text)
    return SPACES.sub(" ", text).strip()


def load_chunks() -> dict:
    chunks = {}
    for line in CHUNKS.open(encoding="utf-8"):
        row = json.loads(line)
        key = f"{row['source']}#{row['chunk_index']}"
        chunks.setdefault(key, row)
    return chunks


def registered_tools() -> set:
    names = set()
    for path in TOOLS_DIR.glob("*.java"):
        names.update(re.findall(r'@Tool\(name\s*=\s*"([^"]+)"', path.read_text(encoding="utf-8")))
    return names


def main() -> int:
    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
    chunks = load_chunks()
    tools = registered_tools()
    errors, warnings = [], []
    ids = set()

    for case in golden["cases"]:
        cid = case["id"]
        if cid in ids:
            errors.append(f"{cid}: duplicate id")
        ids.add(cid)

        for entry in case.get("expected_tools", []):
            for name in entry["names"]:
                if name not in tools:
                    errors.append(f"{cid}: unknown tool {name}")
        for name in case.get("optional_tools", []) + case.get("forbidden_tools", []):
            if name not in tools:
                errors.append(f"{cid}: unknown tool {name}")

        relevant = case.get("relevant", [])
        if not relevant:
            continue
        texts = {}
        for rel in relevant:
            row = chunks.get(rel["key"])
            if row is None:
                errors.append(f"{cid}: chunk {rel['key']} not in chunks.jsonl")
                continue
            if rel["grade"] not in (1, 2):
                errors.append(f"{cid}: grade must be 1 or 2 ({rel['key']})")
            texts[rel["key"]] = normalize(row["text"])

        top = [k for k in (r["key"] for r in relevant if r["grade"] == 2) if k in texts]
        if not top:
            errors.append(f"{cid}: no grade-2 chunk")
        evidence = [normalize(e) for e in case.get("evidence", [])]
        if evidence and not any(all(e in texts[k] for e in evidence) for k in top):
            errors.append(f"{cid}: no grade-2 chunk contains all evidence {evidence}")
        evidence_hits = [k for k, t in ((k, normalize(r["text"])) for k, r in chunks.items())
                         if evidence and all(e in t for e in evidence)]
        if len(evidence_hits) > 6:
            warnings.append(f"{cid}: evidence matches {len(evidence_hits)} chunks (too loose?)")

        pool = " ".join(texts.values())
        for fact in case.get("key_facts", []):
            if not any(normalize(alt) in pool for alt in fact):
                warnings.append(f"{cid}: fact {fact} not found in the labelled chunks")

    rag = sum(1 for c in golden["cases"] if c.get("relevant"))
    print(f"{len(golden['cases'])} cases ({rag} RAG, {len(golden['cases']) - rag} tool routing), "
          f"{len(chunks)} unique chunks, {len(tools)} registered tools")
    for w in warnings:
        print("WARN ", w)
    for e in errors:
        print("ERROR", e)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
