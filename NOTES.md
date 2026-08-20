# Catatan Harian

## Hari 1
- Thinking mode Qwen3 memakan ~400 token untuk pertanyaan sepele.
  Dengan `think: false` jauh lebih cepat. Perlu diukur ulang di Minggu 4.
- Kecepatan baseline: ~22 tok/s (qwen3:8b, M5 16GB).
- `java.version` di pom.xml ≠ runtime JDK IntelliJ. Sudah disamakan ke 25.
- `OllamaOptions` deprecated di Spring AI 2.0 → pakai `OllamaChatOptions`.