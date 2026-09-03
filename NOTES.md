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


## Hari 9 — Structure-aware chunking

Hasil akhir: 76 chunk, rata-rata 547 karakter (95–2.425).
Sebelumnya dengan TokenTextSplitter: 22 chunk, rata-rata 2.863,
pasal terpotong di tengah.

### Perjalanan debugging
1. Regex ketat `^\s*Pasal\s+(\d+)\s*$` → Pasal 47 hilang.
   Penyebab: artefak OCR (bintik di margin) menempel di baris yang sama.
2. Regex longgar (toleran tanda baca) → Pasal 47 tertangkap,
   tapi muncul duplikat 25 dan 40 dari rujukan silang antar pasal.
3. Solusi: regex longgar + validasi urutan (nomor harus n+1).
   Pengetahuan domain (pasal selalu berurutan) menyaring apa yang
   tidak bisa disaring regex.
4. Efek samping menguntungkan: bagian Penjelasan otomatis terbuang
   karena penomorannya mengulang dari kecil.

### Pelajaran
- Regex saja jarang cukup untuk teks hasil OCR. Pola longgar +
  validasi domain lebih andal daripada mengejar satu regex sempurna.
- Urutan operasi penting: TextCleaner sempat merusak struktur baris
  yang dibutuhkan splitter (replaceAll(" ") vs replaceAll("\n")).
- Statistik (terpendek/terpanjang) mengungkap bug yang tidak
  terlihat dari daftar nomor.

## Hari 10 — Embedding & vector store

Model embedding: bge-m3 (1024 dimensi, multilingual 100+ bahasa)
Alasan: korpus berbahasa Indonesia. Model embedding default
kebanyakan berat ke bahasa Inggris.

Hasil: 76 chunk tersimpan di pgvector, metadata pasal utuh.
Durasi ingest: <isi angka durasiMs>

Utang teknis:
- vectorStore.add() selalu menambah, tidak upsert. Ingest ulang
  menghasilkan duplikat. Sementara: TRUNCATE manual sebelum ingest.
- Tabel vector_store dibuat dengan dimensi tetap 1024. Ganti model
  embedding berarti harus DROP tabel dulu.

Catatan: Pasal 3 (daftar asas) terlihat berantakan di potongan awal.
Jadikan pertanyaan uji Minggu 4: "Apa saja asas dalam UU PDP?"

Kendala: Docker Desktop tidak jalan setelah restart laptop →
bean vectorStore gagal dibuat. Biasakan `docker compose ps` di awal sesi.

## Hari 11 — Similarity search
| Pertanyaan | Pasal benar | Rank | Skor | Lolos? |
|-----------|-------------|------|------|--------|
| Apa itu data pribadi | 1 | 1 | 0.683 | Ya |
| Asas UU PDP | 3 | 2 | 0.461 | Sebagian |
| Batas waktu kebocoran | 46 | 1 | 0.642 | Ya |
| Denda korporasi | 70 | 1 | 0.695 | Ya |
| Cara mengurus SIM | - | - | 0.494 | Benar-benar rendah |

Hit@1 = 3/4 · Hit@5 = 4/4
Latensi: 1.357ms (cold) → ~80ms (warm)

MASALAH: Pasal 3 (0.461) skornya DI BAWAH query out-of-scope (0.494).
Penyebab: teks Pasal 3 rusak OCR (daftar asas jadi "a. v : pelindungan").
Implikasi: ambang tunggal tidak bisa memisahkan relevan vs tidak relevan
selama masih ada chunk dengan kualitas teks buruk.

Kandidat ambang: 0.55 (menolak SIM, tapi juga menolak Pasal 3)
Keputusan ditunda ke Hari 15 setelah data lebih banyak.

## Hari 12 — RAG lengkap

### Hasil: 5/5 benar (baseline Hari 3: 0/9)

| Pertanyaan | Hari 3 (tanpa RAG) | Hari 12 (dengan RAG) |
|-----------|--------------------|-----------------------|
| Data pribadi | UU 11/2016 ITE (salah) | UU 27/2022 Pasal 1 |
| Asas UU PDP | - | 8 asas lengkap, Pasal 3 |
| Batas waktu kebocoran | - | 3x24 jam, Pasal 46 ayat (1) |
| Denda korporasi | - | 10x maksimal, Pasal 70 ayat (2) |
| Cara mengurus SIM | - | Menolak menjawab |

### Eksperimen thinking mode & metadata

| Konfigurasi | Rata latensi | Sitasi UU |
|-------------|--------------|-----------|
| Thinking ON | 37.8 s | Benar |
| Thinking OFF | 6.4 s | SALAH — mengarang UU 19/2019 |
| Thinking OFF + nomor UU di konteks | 5.8 s | Benar |

Latensi turun 85% tanpa mengorbankan akurasi.

### Temuan utama

1. **Model hanya bisa mengutip apa yang ada di prompt.**
   Konteks awal hanya ditandai "[Pasal 1]" tanpa nomor UU. Model tahu
   pasalnya tapi harus menebak undang-undangnya, lalu mengarang
   UU 19/2019. Setelah metadata `peraturan` disertakan ke penanda
   konteks, halusinasi hilang. Ini kesalahan konstruksi prompt,
   bukan kesalahan model.

2. **Thinking mode membantu model berpegang pada konteks.**
   Saat dimatikan, model lebih cepat "melompat" ke pola dari
   ingatannya. Diatasi dengan memperkaya konteks, bukan dengan
   menyalakan kembali thinking.

3. **Retrieval tidak sempurna masih bisa menghasilkan jawaban benar.**
   Pasal 3 hanya rank-2 dengan skor 0.461 dan teksnya rusak OCR
   ("a. v : pelindungan"), tapi model berhasil memulihkan kedelapan
   asas. Yang penting chunk benar masuk top-K, bukan harus rank-1.

4. **Instruksi menolak menjawab dipatuhi.**
   Pertanyaan SIM mendapat 5 pasal tentang perlindungan data, model
   tetap menolak. Berbeda dari Hari 3 ketika instruksi "jangan
   mengarang" diabaikan total — bedanya sekarang ada konteks nyata
   untuk berpegang.

5. **Model mengolah, bukan sekadar mengutip.**
   Sempat menjawab "72 jam" padahal dokumen menulis "3 x 24 jam".
   Hilang setelah thinking dimatikan, tapi perilaku ini perlu
   diawasi di konteks hukum.

### Utang teknis
- Sitasi ayat belum terverifikasi. "Pasal 70 ayat (2)" perlu dicek —
  dugaan seharusnya ayat (3). Metadata baru sampai tingkat pasal.
- Sitasi masih melalui teks jawaban model, artinya bisa salah.
  Hari 13: kembalikan daftar sumber terpisah dari metadata.
- ChatConfig.defaultSystem dari Hari 2 bertentangan dengan prompt RAG.
  Sudah dinetralkan.

## Hari 13 — Sitasi terstruktur

Respons berubah dari string menjadi RagReply:
{jawaban, sumber[], pasalDisebutModel[], sitasiTerverifikasi, durasiMs}

Sitasi pasal diambil dari metadata database, bukan dari teks model.
Verifikasi silang: pasal yang disebut model dibandingkan dengan pasal
yang benar-benar di-retrieve. Halusinasi sitasi terdeteksi otomatis.

### Keputusan: sitasi dibatasi tingkat pasal
Penanda ayat (1)(2)(3) hilang tidak konsisten saat OCR — hanya pada
pasal berayat banyak (Pasal 70 rusak, Pasal 66/68/69 utuh karena
satu paragraf). Model menebak nomor ayat dan salah (menyebut ayat 2,
seharusnya ayat 3). Prompt diubah: dilarang menyebut ayat.

Membatasi klaim sistem pada data yang tersedia lebih baik daripada
sitasi presisi yang salah.

### Hasil uji
| Pertanyaan | Jawaban | Sitasi valid | Durasi |
|-----------|---------|--------------|--------|
| Denda maksimal korporasi | 10x maksimal, Pasal 70 | true | 9.361 ms |
| Denda rupiah korporasi palsukan data | Rp60 miliar, Pasal 68 & 70 | true | 6.995 ms |

Temuan: pertanyaan berantai BERHASIL. Model merangkai Pasal 68
(maks Rp6 M) dengan Pasal 70 (10x lipat) → Rp60 M. Di luar dugaan.

Retrieval juga menyesuaikan: pertanyaan spesifik menaikkan Pasal 68
ke rank-1 (0.715), Pasal 70 turun ke rank-4.