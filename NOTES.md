# Catatan Harian

## Hari 1
- Thinking mode Qwen3 memakan ~400 token untuk pertanyaan sepele.
  Dengan `think: false` jauh lebih cepat. Perlu diukur ulang di Minggu 4.
- Kecepatan baseline: ~22 tok/s (qwen3:8b, M5 16GB).
- `java.version` di pom.xml ≠ runtime JDK IntelliJ. Sudah disamakan ke 25.
- `OllamaOptions` deprecated di Spring AI 2.0 → pakai `OllamaChatOptions`.

## Hari 2 — Baseline tanpa RAG
Pertanyaan: "Apa itu data pribadi menurut hukum Indonesia?"
Benar: UU 27/2022 Pasal 1 angka 1

Percobaan 1: UU 11/2016 ITE Pasal 1 angka 13 (SALAH)
Percobaan 2: UU 11/2016 ITE Pasal 1 angka 16 (SALAH, beda dari #1)
- Kedua jawaban mengarang kutipan langsung
- Sitasi PP tidak konsisten (110/2019 vs 110/2018)
- Akurasi baseline: 0/2
- durationMs: 31.685 (terlalu lama)

### Setelah system prompt 
- penambahan prompt (Anda asisten hukum yang menjawab berdasarkan peraturan Indonesia.
                Jawab singkat, maksimal 3 kalimat.
                Selalu sebutkan nomor UU dan pasal yang menjadi dasar jawaban.
                Jika tidak yakin, katakan tidak tahu. Jangan mengarang.)
- durationMs: 31.685 → 4.820 (turun 85%)
- Panjang jawaban terkendali
- Akurasi: tetap 0/1 — masih UU 11/2016
- Instruksi "jangan mengarang" tidak berpengaruh
→ Kesimpulan: prompt mengatur bentuk, bukan fakta. Butuh RAG.