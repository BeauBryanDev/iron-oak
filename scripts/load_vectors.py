
#%% Config
import json
import os
import time
from pathlib import Path

from dotenv import find_dotenv, load_dotenv
from openai import OpenAI
from pymilvus import DataType, MilvusClient

load_dotenv(find_dotenv(usecwd=True))

CHUNKS_PATH = Path("knowledge") / "chunks.jsonl"
COLLECTION = "iron_oak_vectors"
EMBEDDING_MODEL = "text-embedding-3-small"
EMBEDDING_DIM = 1536
LOAD_TYPES = {"content", "toc"}
BATCH_SIZE = 100
RECREATE = True

openai_client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])
milvus = MilvusClient(uri=os.environ["ZILLIZ_URI"], token=os.environ["ZILLIZ_TOKEN"])


#%% Read chunks
records = []
with CHUNKS_PATH.open(encoding="utf-8") as f:
    for line in f:
        rec = json.loads(line)
        if rec["chunk_type"] in LOAD_TYPES:
            records.append(rec)

by_namespace = {}
for rec in records:
    by_namespace[rec["namespace"]] = by_namespace.get(rec["namespace"], 0) + 1
print(f"chunks to load: {len(records)}")
print(by_namespace)


#%% Collection
def create_collection():
    schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
    schema.add_field("doc_id", DataType.VARCHAR, is_primary=True, max_length=36)
    schema.add_field("content", DataType.VARCHAR, max_length=65535)
    schema.add_field("metadata", DataType.JSON)
    schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM)
    index_params = milvus.prepare_index_params()
    index_params.add_index(field_name="embedding", index_type="AUTOINDEX", metric_type="COSINE")
    milvus.create_collection(collection_name=COLLECTION, schema=schema, index_params=index_params)


if milvus.has_collection(COLLECTION) and RECREATE:
    milvus.drop_collection(COLLECTION)
if not milvus.has_collection(COLLECTION):
    create_collection()
print(milvus.describe_collection(COLLECTION)["fields"])


#%% Embed and upload
def embed(texts, retries=5):
    for attempt in range(retries):
        try:
            response = openai_client.embeddings.create(model=EMBEDDING_MODEL, input=texts)
            return [item.embedding for item in response.data]
        except Exception as exc:
            if attempt == retries - 1:
                raise
            wait = 2 ** attempt
            print(f"embedding error ({exc}), retrying in {wait}s")
            time.sleep(wait)


def to_row(rec, vector):
    return {
        "doc_id": rec["id"],
        "content": rec["text"],
        "metadata": {
            "namespace": rec["namespace"],
            "source": rec["source"],
            "doc_title": rec["doc_title"],
            "doc_type": rec["doc_type"],
            "section": rec["section"],
            "page": rec["page"],
            "chunk_index": rec["chunk_index"],
            "tokens": rec["tokens"],
            "chunk_type": rec["chunk_type"],
        },
        "embedding": vector,
    }


uploaded = 0
for start in range(0, len(records), BATCH_SIZE):
    batch = records[start:start + BATCH_SIZE]
    vectors = embed([rec["text"] for rec in batch])
    milvus.upsert(collection_name=COLLECTION, data=[to_row(r, v) for r, v in zip(batch, vectors)])
    uploaded += len(batch)
    print(f"{uploaded}/{len(records)}")

milvus.flush(COLLECTION)
print(milvus.query(COLLECTION, filter="", output_fields=["count(*)"]))


#%% Smoke test
QUESTIONS = [
    ("What is the warranty policy for power tools?", ["iron_oak"]),
    ("How do I fix a leaking faucet?", ["home_do_it_yourself"]),
    ("How do I set the spindle speed on a manual mill?", ["machine_operations"]),
    ("How often should bearings be lubricated?", ["machine_maintenance"]),
    ("What guarding is required on a bench grinder?", ["power_tools"]),
    ("Which chapter of Home Maintenance covers plumbing?", ["home_do_it_yourself"]),
]


def search(question, namespaces, top_k=3):
    vector = embed([question])[0]
    namespace_list = ", ".join(f'"{n}"' for n in namespaces)
    hits = milvus.search(
        collection_name=COLLECTION,
        data=[vector],
        limit=top_k,
        filter=f'metadata["namespace"] in [{namespace_list}]',
        output_fields=["content", "metadata"],
        search_params={"metric_type": "COSINE"},
    )
    return hits[0]


for question, namespaces in QUESTIONS:
    print(f"\n{question}  {namespaces}")
    for hit in search(question, namespaces):
        meta = hit["entity"]["metadata"]
        print(f"  {hit['distance']:.3f} | {meta['source']} | p{meta['page']} | {meta['section']}")
