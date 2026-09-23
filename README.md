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

`GET /api/collections/{id}` returns the collection name and its document IDs. Document metadata is available through `/api/documents`; PDF upload is scheduled for a later sprint.

Tag endpoints:

| Method | Path | Action |
| --- | --- | --- |
| `GET`, `POST` | `/api/tags` | List or create tags |
| `PUT`, `DELETE` | `/api/documents/{documentId}/tags/{tagId}` | Assign or remove a tag |

Document responses include assigned tags. `mvn verify` runs the tests, produces a JaCoCo report in `DMS_Backend/target/site/jacoco`, and enforces at least 71% line coverage.
