# SWEN3 Document Management System

## Sprint 1

The additional use case is [collections](https://github.com/Amikas/DMS/issues/3): a document can belong to several collections, and deleting a collection keeps its documents.

Run the REST server and PostgreSQL with Docker:

```sh
cd DMS_Backend
docker compose up --build -d
curl http://localhost:8081/api/health
```

Run unit tests from `DMS_Backend` with JDK 25 or newer: `mvn clean test`.

Collection endpoints:

| Method | Path | Action |
| --- | --- | --- |
| `GET`, `POST` | `/api/collections` | List or create collections |
| `GET`, `PUT`, `DELETE` | `/api/collections/{id}` | View, rename, or delete a collection |
| `PUT`, `DELETE` | `/api/collections/{id}/documents/{documentId}` | Add or remove a document |

`GET /api/collections/{id}` returns the collection name and its document IDs. Document metadata is available through `/api/documents`.

`mvn verify` runs the tests, produces a JaCoCo report in `DMS_Backend/target/site/jacoco`, and enforces at least 71% line coverage.

## Sprint 2

Web UI (React + Vite) served via nginx on port 80, REST API proxied same-origin under `/api/`:

```sh
docker compose build
docker compose up -d
curl http://localhost/            # frontend (expect 200 + HTML)
curl http://localhost/api/health  # proxied REST (expect OK)
```

Dashboard (`/`), collections (`/collections`) and document detail (`/documents/:id`) are served by nginx with SPA fallback. Forms validate client-side (`DMS_Frontend/src/lib/validation.ts`) and server-side (Jakarta validation, e.g. empty title yields 400).

## Sprint 3

The dashboard uploads PDFs (maximum 10 MB); the API also accepts multipart uploads:

```sh
curl --fail -F title=Example -F file=@example.pdf http://localhost/api/documents
docker compose logs dms-ocr-worker  # expect "OCR done" after processing
```

Each upload sends a job to `dms.document.ocr`. Broker failures return 500 and roll back the document and the uploaded file. This is not crash-atomic across PostgreSQL and RabbitMQ; an outbox is deferred.

RabbitMQ console: http://localhost:15672 (`dms` / `dms`). Queue/storage errors and Spring's repository exceptions are logged server-side without exposing internal details. Malformed requests remain 400 responses.

## Sprint 4

PDF uploads are stored in **libreFS** (S3-compatible storage, `documents` bucket). The backend and OCR worker use standard S3 clients. The legacy `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD` variable names are required by libreFS itself; no MinIO service is used. The pipeline:

```sh
docker compose up --build -d
curl --fail -F title=HelloWorld -F file=@"Test Artifacts/HelloWorld.pdf" http://localhost/api/documents
# wait a few seconds for OCR + indexing, then:
curl "http://localhost/api/documents/search?q=Hello"
```

1. Upload → backend stores the PDF in libreFS and publishes the document id + object key to `dms.document.ocr`.
2. `dms-ocr-worker` fetches the PDF from libreFS, extracts text (embedded text first, Tesseract OCR fallback for scanned PDFs) and publishes it to `dms.document.index`.
3. `dms-index-worker` writes the text to Elasticsearch (`documents` index).
4. The dashboard search box queries `GET /api/documents/search?q=`, which asks Elasticsearch and joins the hits with the document rows in PostgreSQL.

Ports: libreFS S3 :9002 / console :9001 (`dms` / `dms-secret`), Elasticsearch :9200, Kibana :5601, Logstash JSON-lines TCP input :15000. Containers access libreFS on internal port 9000. Logstash writes received events to `dms-logs-*`.

Document details retrieve the full OCR result from Elasticsearch and poll every two seconds while processing. Search returns an empty list before the first document has been indexed. Summaries are not part of Sprint 4.

Acceptance check: run `sh scripts/check-sprint4.sh` after starting Compose. It uploads the sample PDF through the same nginx/API route as the frontend and waits for its ID to appear in the Hello search and for its text to be available in the detail endpoint. It leaves the sample document in the archive. To check manually, open http://localhost/, choose **New document**, upload `Test Artifacts/HelloWorld.pdf`, then search for **Hello**.

When upgrading existing containers, use `docker compose up -d --build` so the OCR worker is recreated with the new code. Existing libreFS volumes are reused.

OCR jobs retry transient failures up to three attempts. Invalid PDFs and exhausted jobs
are retained in the durable `dms.document.ocr.failed` queue for inspection. OCR publishes
persistent messages with publisher confirms before acknowledging the original job.
The index worker retries Elasticsearch 429 and 5xx responses.

Worker regression checks (after building their images):

```sh
docker compose run --rm --no-deps -v "$PWD/DMS_OCR_Worker:/app:ro" dms-ocr-worker python -m unittest -v
docker compose run --rm --no-deps -v "$PWD/DMS_Index_Worker:/app:ro" dms-index-worker python -m unittest -v
```

## Sprint 5

```sh
docker compose up -d --build
sh scripts/check-sprint5.sh
```

On first startup, the CPU-only `llama.cpp` container downloads the official **LFM3‑1.7B Q4_K_M** model (about 1.1 GB) from Hugging Face into the `llama_models` volume. Once the images and model are cached, it can start without internet. Keep the volume (`docker compose down` without `-v`). No GPU, LM Studio, or API key is needed. The model's HTTP port is only available inside Compose.

OCR sends text to search indexing and to a separate summary queue. The GenAI worker summarizes one document at a time with a 16K context; if the full text does not fit, it summarizes the opening and prefixes the result with `Partial summary:`. Documents with no extracted text are not summarized. The backend saves the result in PostgreSQL and the existing document details show it. PDFs near the context limit can take several minutes on CPU, but search does not wait for inference.

The acceptance script needs `curl` and `jq`. It uploads the sample PDF, checks that it can be found through search, and reads the saved summary through the document API. For worker errors, run `docker compose logs dms-genai-worker`. Jobs that fail three attempts are retained in `dms.document.summarize.failed`; that queue contains extracted document text.

The document detail shows **Summarizing…** during inference and **Complete** once the summary is stored and the PDF is searchable. If a summary attempt fails, the badge returns to **OCR complete** until the next attempt starts.

```sh
docker compose run --rm --no-deps -v "$PWD/DMS_GenAI_Worker:/app:ro" dms-genai-worker python -m unittest -v
```
