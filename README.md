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
docker compose logs dms-ocr-worker  # expect "Received OCR job"
```

PDFs live in the `document_uploads` volume until MinIO arrives in Sprint 4. Each upload sends a job to `dms.document.ocr`; the empty worker logs and acknowledges it. Broker failures return 500 and roll back the document and uploaded file. This is not crash-atomic across PostgreSQL and RabbitMQ; an outbox is deferred.

RabbitMQ console: http://localhost:15672 (`dms` / `dms`). Queue/storage errors and Spring's repository exceptions are logged server-side without exposing internal details. Malformed requests remain 400 responses.
