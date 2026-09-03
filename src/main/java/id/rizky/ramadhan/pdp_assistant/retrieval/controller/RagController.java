package id.rizky.ramadhan.pdp_assistant.retrieval.controller;

import id.rizky.ramadhan.pdp_assistant.chat.dto.ChatRequest;
import id.rizky.ramadhan.pdp_assistant.retrieval.dto.RagReply;
import id.rizky.ramadhan.pdp_assistant.retrieval.service.RagService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public RagReply ask(@RequestBody ChatRequest request,
                                  @RequestParam(defaultValue = "5") int topK){
        return ragService.jawab(request.message(), topK);
    }
}
