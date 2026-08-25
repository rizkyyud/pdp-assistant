package id.rizky.ramadhan.pdp_assistant.chat.controller;

import id.rizky.ramadhan.pdp_assistant.chat.service.DocumentReaderService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
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

    public IngestionController(DocumentReaderService documentReaderService) {
        this.documentReaderService = documentReaderService;
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
}
