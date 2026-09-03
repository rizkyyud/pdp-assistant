# PDP Assistant

A question-answering system over Indonesia's Personal Data Protection Law (UU No. 27/2022), built with Spring Boot and Spring AI. Runs entirely offline.

## The problem

Ask a general-purpose LLM about Indonesian data protection law and it will answer confidently — and wrong. Across nine trials at three temperature settings, the base model attributed the definition of personal data to **UU 11/2016 (ITE)** every single time. The correct source is UU 27/2022, passed six years later.

The model wasn't uncertain. It was consistently, fluently incorrect.

```
Baseline (no retrieval):        0/9 correct
With retrieval + citation:      5/5 correct
```

This matters beyond a demo. Compliance teams in Indonesian banks, insurers, and telcos need answers they can trace back to a specific article — an answer without a verifiable source is unusable in that context.

## What it does

- Answers questions about UU 27/2022 in Indonesian
- Cites the specific article (Pasal) behind every answer
- Returns the source text alongside the answer so the user can verify it
- Flags when the model cites an article that wasn't actually retrieved
- Declines to answer when the question falls outside the document

## Stack

| Layer | Choice |
|---|---|
| Runtime | Java 25, Spring Boot 4 |
| AI framework | Spring AI 2.0 |
| Answering model | qwen3:8b via Ollama |
| Query rewriting model | qwen3:1.7b via Ollama |
| Embeddings | bge-m3 (1024 dim, multilingual) |
| Vector store | PostgreSQL 17 + pgvector |

Everything runs locally. No API keys, no data leaving the machine.

## Architecture

```
PDF → OCR cleanup → article-aware chunking → embeddings → pgvector
                                                              ↓
question → query rewriting → similarity search → prompt → answer + sources
```

## Technical decisions

### Source document quality was the first real problem

The official PDF from the State Secretariat is a scan with an OCR layer produced in 2022 — apparently using an English language model on an Indonesian document. The General Explanation section was effectively unusable:

| Original OCR | After re-processing |
|---|---|
| `ele&onic @mmet@ (e-ammere)` | `electronic commerce (e-commerce)` |
| `eledrunic dtution (edtt@lion)` | `electronic education (e-education)` |
| `daJarla bidang perdidikan` | `dalam bidang pendidikan` |
| `Felindungan`, `sanga.t`, `sahr` | `Pelindungan`, `sangat`, `satu` |

The scanned images themselves were high quality, so re-running OCR with Tesseract using the Indonesian language model recovered the text:

```bash
ocrmypdf --force-ocr -l ind input.pdf output.pdf
```

**Corpus quality is the first RAG problem, not chunking.** Inspecting the source data before building the pipeline saved days of debugging downstream.

### Structure-aware chunking

`TokenTextSplitter` splits on length and is blind to document structure. On a legal text, that produced:

- Article definitions split mid-sentence across two chunks
- A single chunk containing ten unrelated articles (Pasal 6 through 15)
- Article numbers separated from their content, making citation impossible

Legal documents have a hard structural unit: the article. A custom splitter that cuts on `Pasal N` boundaries produced 76 chunks — one complete article each, with the article number preserved in metadata.

| | TokenTextSplitter | Article-aware |
|---|---|---|
| Chunks | 22 | 76 |
| Mean length | 2,863 chars | 547 chars |
| Complete articles | No | Yes |
| Citable | No | Yes |

Getting this right required combining a permissive regex with domain validation. A strict pattern missed Pasal 47 (OCR artifacts on the same line); a permissive one caught spurious matches from cross-references. The fix was accepting only articles that appear in ascending sequence — because articles in a statute are always numbered consecutively. Domain knowledge did what regex alone could not.

### pgvector over a dedicated vector database

One database for both relational and vector data. No additional service to operate, runs offline, and PostgreSQL is already deployed at most organizations. Adequate for tens of thousands of chunks.

Trade-off: a purpose-built vector database would outperform this at millions of vectors.

### Multilingual embeddings

Most default embedding models are English-weighted. The corpus is Indonesian, so bge-m3 (100+ languages, 8192 token context) was chosen instead.

### Two models of different sizes

Query rewriting is a mechanical task — substituting pronouns — and doesn't require reasoning. Running qwen3:1.7b for rewriting and qwen3:8b for answering cut peak memory from 6.6 GB to 3.1 GB, which is the difference between the system running and not running on a 16 GB machine. Rewriting quality was unchanged.

### Citations come from metadata, not from the model

The model writes the answer; the article numbers in the `sources` array come directly from the retrieved chunks' metadata. The system then cross-checks: any article the model mentions that wasn't actually retrieved is flagged as a hallucinated citation.

```json
{
  "jawaban": "Data Pribadi adalah data tentang orang perseorangan...\nDASAR: Pasal 1",
  "sumber": [{ "pasal": "1", "skor": 0.683, "kutipan": "Pasal 1\n\nDalam Undang-Undang ini..." }],
  "pasalDisebutModel": ["1"],
  "pasalHalusinasi": [],
  "sitasiTerverifikasi": true
}
```

### Citations are limited to the article level, deliberately

Sub-article markers — `(1)`, `(2)`, `(3)` — were lost inconsistently during OCR, surviving in single-paragraph articles but disappearing in multi-clause ones. When the model was allowed to cite at that level, it guessed and got it wrong.

Constraining the system's claims to the data actually available is better than emitting precise-looking citations that can't be verified.

### Prompt construction determines hallucination

Two findings that generalize:

Disabling the model's thinking mode cut mean latency from 37.8s to 5.8s, but introduced a new hallucination: the model started attributing answers to a fabricated law. The context blocks were labelled `[Pasal 1]` without naming the statute, so the model filled the gap from memory. Adding the regulation name to the context label eliminated it.

**A model can only cite what's in its prompt.** Anything you expect in the output but don't supply as context will be invented.

### Conversation history feeds retrieval, not the answer prompt

Memory advisors inject history into the LLM prompt but leave retrieval blind to it. A follow-up like *"Apa saja jenisnya?"* was being sent to the vector store as a standalone phrase with almost no semantic content — scoring 0.439, lower than a question entirely outside the corpus.

The fix rewrites the question into a self-contained one before retrieval:

```
"Apa saja jenisnya?"  →  "Apa saja jenis data pribadi?"
score: 0.439          →  0.698  (+59%)
```

History is used *only* for rewriting and is never injected into the answering prompt, which keeps that prompt small regardless of conversation length.

## Evaluation

*In progress — see `NOTES.md` for interim measurements.*

## Known limitations

- **Query rewriting succeeds on roughly 1 in 3 follow-up questions.** The 1.7b model sometimes answers the question instead of rewriting it. Output guards catch this and fall back to the original question, so retrieval degrades rather than breaking.
- **Ambiguous questions are answered rather than clarified.** The statute contains four distinct `3 x 24 hour` deadlines; asked "how long is the deadline?" with no antecedent, the system picks one instead of asking which.
- **Pasal 3 remains partially corrupted.** The list of legal principles survived OCR as fragments, which depresses its retrieval score below that of out-of-scope queries — meaning no single similarity threshold cleanly separates relevant from irrelevant results.
- **Single document corpus.** Implementing regulations (PP) and sector rules are not ingested, so cross-regulation questions are out of scope.
- **Ingestion is not idempotent.** Re-running it duplicates rows; the table must be truncated first.

## Running it

Requires Docker, Ollama, and JDK 25.

```bash
# Vector store
docker compose up -d

# Models
ollama pull qwen3:8b
ollama pull qwen3:1.7b
ollama pull bge-m3

# Application
./mvnw spring-boot:run
```

The container maps to port **5433** to avoid colliding with a local PostgreSQL install.

Ingest the corpus:

```bash
curl -X POST http://localhost:8080/api/ingest/store \
  -F "file=@docs/corpus/uu-27-2022-clean.pdf"
```

Ask a question:

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"message":"Apa itu data pribadi?"}'
```

### Memory

Peak usage is roughly 3.1 GB for models plus 3 GB for the database container. On a 16 GB machine, running several requests in quick succession can exhaust available memory because Ollama retains loaded models. Shortening the retention window helps:

```bash
launchctl setenv OLLAMA_KEEP_ALIVE 30s
```

## Development notes

`NOTES.md` contains the working log: measurements, failed approaches, and the reasoning behind decisions that didn't make it into this document.
