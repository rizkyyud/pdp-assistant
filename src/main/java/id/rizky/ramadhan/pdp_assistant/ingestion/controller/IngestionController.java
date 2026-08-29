package id.rizky.ramadhan.pdp_assistant.ingestion.controller;

import id.rizky.ramadhan.pdp_assistant.ingestion.service.DocumentReaderService;
import id.rizky.ramadhan.pdp_assistant.ingestion.service.PasalSplitter;
import id.rizky.ramadhan.pdp_assistant.ingestion.service.TextCleaner;
import org.springframework.ai.document.Document;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final DocumentReaderService documentReaderService;
    private final TextCleaner textCleaner;
    private final PasalSplitter pasalSplitter;

    public IngestionController(DocumentReaderService documentReaderService,
                               TextCleaner textCleaner,
                               PasalSplitter pasalSplitter) {
        this.documentReaderService = documentReaderService;
        this.textCleaner = textCleaner;
        this.pasalSplitter = pasalSplitter;
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") int mulai,
            @RequestParam(defaultValue = "1500") int panjang) throws IOException {
        var resource = new InputStreamResource(file.getInputStream()){
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        List<Document> documents = documentReaderService.read(resource);
        String text = documents.getFirst().getText();

        assert text != null;
        int awal = Math.min(mulai, text.length());
        int akhir = Math.min(awal + panjang, text.length());

        return Map.of(
                "jumlahDokumen", documents.size(),
                "totalKarakter", text.length(),
                "posisi", awal + "-" + akhir,
                "cuplikan", text.substring(awal, akhir)
        );
    }

    @PostMapping("/chunk")
    public Map<String, Object> chunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "500") int chunkSize,
            @RequestParam(defaultValue = "3") int tampilkan) throws IOException {

        var resource = new InputStreamResource(file.getInputStream()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        List<Document> chunks = documentReaderService.chunk(documentReaderService.read(resource), chunkSize);

        var contoh = chunks.stream()
                .limit(tampilkan)
                .map(c -> Map.of(
                        "panjang", c.getText().length(),
                        "isi", c.getText()))
                .toList();

        var panjangSemua = chunks.stream().mapToInt(c -> c.getText().length()).summaryStatistics();

        return Map.of(
                "chunkSize", chunkSize,
                "jumlahChunk", chunks.size(),
                "rataPanjang", (int) panjangSemua.getAverage(),
                "terpendek", panjangSemua.getMin(),
                "terpanjang", panjangSemua.getMax(),
                "contoh", contoh
        );
    }

    @PostMapping("/split-pasal")
    public Map<String, Object> splitPasal(@RequestParam("file") MultipartFile file) throws IOException {
        var resource = new InputStreamResource(file.getInputStream()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        String teks = textCleaner.clean(documentReaderService.read(resource).getFirst().getText());
        List<Document> chunks = pasalSplitter.split(teks, Map.of("sumber", file.getOriginalFilename()));

        var stat = chunks.stream().mapToInt(c -> c.getText().length()).summaryStatistics();

        return Map.of(
                "jumlahPasal", chunks.size(),
                "rataPanjang", (int) stat.getAverage(),
                "terpendek", stat.getMin(),
                "terpanjang", stat.getMax(),
                "daftarPasal", chunks.stream().map(c -> c.getMetadata().get("pasal")).toList(),
                "contoh", chunks.stream().limit(2).map(c -> Map.of(
                        "pasal", c.getMetadata().get("pasal"),
                        "isi", c.getText())).toList()
        );
    }

    @PostMapping("/cleaned")
    public Map<String, Object> cleaned(@RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "0") int mulai) throws IOException {
        var resource = new InputStreamResource(file.getInputStream()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        String teks = textCleaner.clean(documentReaderService.read(resource).getFirst().getText());
        int awal = Math.min(mulai, teks.length());
        int akhir = Math.min(awal + 1500, teks.length());
        return Map.of(
                "posisiPasal47", teks.indexOf("Pasal 47"),
                "totalKarakter", teks.length(),
                "cuplikan", teks.substring(awal, akhir)
        );
    }
}
