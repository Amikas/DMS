# Sprint 5: local document summaries

Generate a short summary of each uploaded PDF after OCR, without sending its text off the machine. The existing document API and details page show the stored summary. The smartphone app is a separate deliverable that uses the same API.

## Approaches

- A host-run LM Studio server was useful for testing the existing model, but would require separate setup on the grading machine.
- Hosted Gemini removes the local model download but requires an API key and sends document text to a hosted service.
- **Selected:** a CPU-only `llama.cpp` service in Docker Compose. It downloads the official LFM3‑1.7B Q4_K_M file on the first run and reuses it from a volume. No host inference app, GPU access, or API key is required.

## Flow

After OCR, send the extracted text to a separate durable RabbitMQ queue without making the index worker wait for inference. A Python worker makes one inference request at a time using a 16K context and reasoning off. Reserve room for the prompt and output; when text does not fit, summarize the opening and label the result “Partial summary.” Skip documents with no extracted text.

Spring Boot consumes summary results and updates the existing PostgreSQL summary field only for the matching document and file. Retry transient model failures with a bound, retain failed jobs for inspection, and log IDs and outcomes rather than document text. Do not add chunking, a cloud fallback, or a new public endpoint.

Verification: upload the repository sample through Compose, confirm its summary is stored and returned by the API, and check that search still finds the document.

## Concise summaries

Ask for a 2–3 sentence preview in the document's language: what it is about, the main takeaway, and at most one supporting detail. A summary around 100 words is fine; there is no fixed word cutoff. Keep the existing privacy instructions and 512-token ceiling. A smaller ceiling can cut off a usable answer rather than make it concise. In testing, a German example biased English output toward German, so the prompt uses direct instructions without examples. Keep the first paragraph of the final answer, excluding any draft text the model emits despite reasoning being off.
