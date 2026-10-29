# Iron & Oak

A full-stack hardware-tools online shop built as an AI engineering demo: a real e-commerce
system with three machine-learning models and an AI assistant, Piper, that sells, advises
and diagnoses through tool calling and retrieval.

- Shop: https://ironoak.tensorgeek.com
- API: https://api.ironoak.tensorgeek.com

Iron & Oak Hardware Supply Co. is a fictional company. Payments run in Stripe test mode.

## How it started

This project began on **3 September 2026** with a single model: a **MobileNet-V3** image
classifier trained in **PyTorch** on a hardware-tools dataset from **Kaggle** (87 tool
classes). The goal was to give that CNN a purpose worth its training.

This was the original dataset the frist vision model was trained from and all this project started as an Academic Convolutional Neural Netowrk training:
[Diverse Tools Image Dataset for Machine Learning](https://www.kaggle.com/datasets/oortdatahub/diverse-tools-image-dataset-for-machine-learning)

A second model followed: a **CNC machine-health** MLP that predicts machine failures from
sensor readings (AI4I 2020 dataset). From there grew the idea of a complete online shop for
tools and milling machines, where the models are part of how the business works rather than
a demo on the side. The catalog itself comes from the CNN: its 87 classes are the shop's
products, plus a few tools added by hand.

A third model came later: a **YOLOv11-nano** detector that recognises six end mills (milling
cutters) in a photo.

## What the shop does

- Catalog of hand and power tools, end mills, milling machines and technician services
- Cart and checkout with Stripe Checkout and signed webhooks; stock is held while the
  customer pays
- Shipping priced by destination city (road distance, weight, volume; fixed air rates), and
  taxes by country, for ten countries in Latin America and the US
- Order tracking by order number, shipment status and expected delivery date
- Technician bookings (Bogotá and Medellín), service quotes, warranty claims and support
  tickets
- Staff panel: secure login with refresh tokens and lockouts, dashboard with KPIs, order
  management (payments, refunds, shipping), and an audit trail of every staff action

## What Piper does

Piper is the shop's assistant, built with Spring AI on an OpenAI model. It answers from real
data, never from guesses, by calling about twenty tools:

- **Knowledge (RAG):** searches company policies, machine manuals, metal-cutting and safety
  books (about 3,300 passages in a Milvus vector database) and cites its sources
- **Catalog:** finds products, stock, prices, end mills, machines and services, and
  recommends what to buy after giving how-to advice
- **Vision:** identifies a tool (MobileNet-V3) or an end mill (YOLOv11) from a photo the
  customer uploads, and links it to the catalog
- **Machine health:** runs the CNC model on a machine's readings and recommends maintenance
  or repair
- **Machining:** looks up material properties and calculates cutting parameters (speed,
  feed, forces, power, tool life)
- **Actions:** creates orders, books technician visits, files warranty claims and hands
  the customer over to a human when needed

### How well Piper performs

Piper is measured end to end against a 50-case golden set (34 knowledge questions and 16
tool-routing cases), using the real chat model, the production vector store and a copy of the
database. Two rounds of improvements followed: smarter tool routing and fuller search results,
then hybrid search (vector plus BM25 keyword search in Milvus). Over those rounds, answer
correctness rose from 0.81 to **0.93**, recall of the right passages in the top 5 from 0.61
to **0.72**, and tool calls with the right tool and exact arguments from 0.96 to **0.98**.
Piper never cited a source it had not read. Full tables and method:
[eval/PIPER_EVAL_REPORT.md](eval/PIPER_EVAL_REPORT.md).

## Machine-learning models

| Model | Task | Trained with | Runs in |
|---|---|---|---|
| MobileNet-V3 | 87 hardware-tool classes | PyTorch, Kaggle dataset | Java, ONNX Runtime |
| MLP | CNC failure risk (5 outputs) | PyTorch, AI4I 2020 | Java, ONNX Runtime |
| YOLOv11-nano | 6 end-mill classes | Ultralytics (PyTorch) | Java, ONNX Runtime |

All three are exported to ONNX and run inside the Java backend; image preprocessing and the
YOLO post-processing (letterbox, NMS) are implemented in Java. Training notebooks are in
`ml/`.

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Security (JWT), Spring AI, PostgreSQL 16,
  Flyway, ONNX Runtime, Stripe
- **Frontend:** TypeScript, Next.js 15, React, Tailwind CSS
- **Machine learning:** PyTorch, Ultralytics YOLO, ONNX
- **Vector search:** Milvus (Zilliz Cloud), OpenAI embeddings
- **Deployment (AWS):** EC2 (Ubuntu, systemd, nginx, Let's Encrypt), RDS PostgreSQL, S3 +
  CloudFront for images; the frontend on Vercel
- **CI:** GitHub Actions (backend tests with Testcontainers, frontend build)

## Repository

- `src/` Spring Boot backend (`agent/` Piper's tools, `vision/` and `prognosis/` the models)
- `frontend/` Next.js storefront and staff panel
- `ml/` training notebooks and metrics
- `knowledge/` and `scripts/` documents and the Python pipeline that loads them into Milvus
- `deploy/` systemd unit, nginx site and deployment guide

## Running locally

Requires Java 21, Docker, Node.js 20+ and a `.env` file with the keys listed in
`deploy/DEPLOY.md`.

```bash
docker compose up -d                                              # PostgreSQL 16
./mvnw spring-boot:run -Dspring-boot.run.profiles=local           # API on :8080
cd frontend && npm install && npm run dev                         # shop on :3000
```

## License

Apache License 2.0, see `LICENSE`.
