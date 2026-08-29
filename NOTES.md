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

## Hari 3 — Eksperimen temperature
| temp | konsisten? | UU disebut | rata durationMs |
|------|-----------|--------------------------|------|
| 0.0  | Ya, identik 3/3 | 11/2016 Ps.1 angka 1 | 3194 |
| 0.3  | Tidak | 11/2016 & 11/2008, pasal beda | 3226 |
| 0.9  | Tidak sama sekali | angka 16, 10, 1 | 3538 |

Benar: UU 27/2022 Pasal 1 angka 1. Akurasi 0/9.

Catatan penting: temp 0.0 konsisten TAPI konsisten SALAH.
Determinisme ≠ kebenaran. Determinisme hanya prasyarat evaluasi.

Keputusan: evaluasi Minggu 4 pakai temp 0.0. Default app tetap 0.3.

## Hari 6 — pgvector via Docker

### Keputusan: pgvector, bukan Pinecone/Qdrant
Alasan:
- Satu database untuk data relasional + vektor, tidak perlu layanan terpisah
- Jalan offline, gratis, tanpa vendor lock-in
- PostgreSQL sudah umum dipakai perusahaan, mudah diadopsi
- Cukup untuk skala puluhan ribu chunk

Trade-off: kalah performa dibanding database vektor khusus di skala jutaan vektor.

### Masalah: konflik port 5432
PostgreSQL lokal (Homebrew) dan container Docker sama-sama mendengarkan
di 5432. Container tetap bisa start karena binding berbeda (localhost vs *),
tapi aplikasi berisiko tersambung ke instance yang salah — yang tidak
punya ekstensi pgvector.

Deteksi: `lsof -i :5432` menampilkan dua proses.
Solusi: container dipetakan ke 5433, PostgreSQL lokal dibiarkan jalan.

Pelajaran: container "healthy" tidak menjamin aplikasi bicara dengan
container itu. Selalu verifikasi dari sisi aplikasi, bukan cuma dari
sisi container.

### Status
- Container healthy, ekstensi vector aktif
- Spring Boot tersambung ke localhost:5433
- Aplikasi start bersih

## Hari 7 — Ekstraksi PDF & Kualitas Korpus

### Setup
- Dependency: spring-ai-tika-document-reader
- DocumentReaderService + endpoint POST /api/ingest/preview
- Parameter `mulai` & `panjang` supaya bisa jelajahi dokumen tanpa restart

### Temuan awal
Hasil: 1 Document, 62.230 karakter.

Masalah kualitas:
1. OCR error: REPUELIK→REPUBLIK, FRESIDEN→PRESIDEN, daLam→dalam,
   ayat (l)→ayat (1)
2. Urutan blok teks tidak sesuai urutan baca (Menimbang muncul
   sebelum judul UU)
3. Artefak berulang: "SK No \d+A" (stempel), "PRESIDEN REPUBLIK
   INDONESIA" (kop), "-\d+-" (nomor halaman)
4. Newline di tengah kalimat, bukan di batas kalimat

### Sampling kualitas per bagian
| Posisi | Bagian | Kualitas |
|--------|--------|----------|
| 0-1.5k | Pembuka | Sedang — urutan blok kacau |
| 30k | Batang tubuh pasal | Baik — cacat minor |
| 45k | Penjelasan Umum | RUSAK — "ele&onic @mmet@" |
| 60k | Penjelasan Pasal | Banyak artefak |

Kerusakan TIDAK terbatas di halaman awal. Bagian Penjelasan Umum
praktis tidak bisa dipakai.

### Diagnosis
Gambar pindaian PDF berkualitas baik — yang rusak hanya lapisan
teks OCR-nya. Setneg melakukan OCR saat membuat PDF ini (2022),
kemungkinan dengan model bahasa Inggris pada dokumen Indonesia.
Itu menjelaskan kenapa kata Indonesia rusak parah.

### Solusi: OCR ulang
ocrmypdf --force-ocr -l ind <input> <output>

Hasil perbandingan:
| Sebelum | Sesudah |
|---------|---------|
| ele&onic @mmet@ (e-ammere) | electronic commerce (e-commerce) |
| eledrunic dtution (edtt@lion) | electronic education (e-education) |
| neledronic hmlth (e-leaftfl | electronic health (e-health) |
| daJarla bidang perdidikan | dalam bidang pendidikan |
| Felindungan, sanga.t, sahr | Pelindungan, sangat, satu |

Total karakter: 62.230 → 63.033
Sisa cacat minor: "(" kadang terbaca "f" pada "(e-commerce)"

### Keputusan
Korpus kerja diganti ke uu-27-2022-clean.pdf.
PDF lama disimpan sebagai pembanding untuk bagian evaluasi.

### Pelajaran
Kualitas korpus adalah masalah RAG pertama, bukan chunking.
Memeriksa data sumber sebelum membangun pipeline menghemat
berhari-hari kerja sia-sia di hilir.

## Hari 8 — Chunking
| chunkSize | jumlah | rata | pasal utuh? | nomor pasal terbawa? |
|-----------|--------|------|-------------|---------------------|
| 200 | 111 | 566 | Tidak | Tidak |
| 500 | 44 | 1431 | Tidak | Tidak |
| 1000 | 22 | 2863 | Tidak | Tidak |

Kegagalan TokenTextSplitter pada dokumen hukum:
- Definisi Korporasi terbelah 2 chunk di tengah kalimat
- Satu chunk memuat Pasal 6-15 sekaligus (10 pasal tak berhubungan)
- Nomor pasal terpisah dari isi ("Pasal 6" di akhir chunk, isinya
  di chunk berikutnya) → sitasi mustahil
- Artefak "SK No", kop, nomor halaman masih bertebaran
- chunkSize adalah token, bukan karakter. minChunkSizeChars=350
  membatasi hasil akhir

Akar masalah: splitter memotong berdasarkan panjang, buta terhadap
struktur. Dokumen hukum punya unit makna yang tegas (Pasal).

Keputusan: buat custom splitter berbasis batas "Pasal N" + regex
pembersih artefak. Nomor pasal disimpan di metadata untuk sitasi.