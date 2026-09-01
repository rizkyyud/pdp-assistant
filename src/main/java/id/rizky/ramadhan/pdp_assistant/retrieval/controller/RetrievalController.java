package id.rizky.ramadhan.pdp_assistant.retrieval.controller;

import id.rizky.ramadhan.pdp_assistant.retrieval.service.RetrievalService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class RetrievalController {

    private final RetrievalService retrievalService;
    public RetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping
    public Map<String,Object> cari(@RequestParam String q,
                                   @RequestParam(defaultValue = "5") int topK,
                                   @RequestParam(defaultValue = "0.0") double ambang){

        long start = System.currentTimeMillis();
        List<Document> hasil = retrievalService.cari(q, topK, ambang);

        var daftar = hasil.stream().map(document -> Map.of(
                "pasal", document.getMetadata().get("pasal"),
                "skor", document.getScore(),
                "cuplikan", document.getText().substring(0, Math.min(200, document.getText().length()))
        )).toList();

        return Map.of("pertanyaan", q,
                "durasiMs", System.currentTimeMillis()-start,
                "hasil", daftar);
    }
}
