package id.rizky.ramadhan.pdp_assistant.retrieval.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final String TEMPLATE = """
            Jawab pertanyaan Hanya berdasarkan kutipan peraturan dibawah ini.
            Jika jawabannya tidak ada dalam kutipan, katakan bahwa informasi
            tersebut tidak ditemukan dalam dokumen. Jangan menggunakan
            pengetahuan di luar kutipan.
            
            Sebutkan nomor pasal yang menjadi dasar jawaban Anda.
            
            === KUTIPAN PERATURAN ===
            %s
            === AKHIR KUTIPAN ===
            
            Pertanyaan: %s
            """;

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    public RagService(ChatClient chatClient, RetrievalService retrievalService) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
    }

    public String jawab(String pertanyaan, int topK){
        List<Document> konteks = retrievalService.cari(pertanyaan, topK, 0.0);

        String kutipan = konteks.stream()
                .map(d -> "[" + d.getMetadata().get("peraturan") + " — Pasal "
                        + d.getMetadata().get("pasal") + "]\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = TEMPLATE.formatted(kutipan, pertanyaan);
        return chatClient.prompt()
                .user(prompt)
                .options(OllamaChatOptions.builder()
                        .temperature(0.0)
                        .disableThinking())
                .call()
                .content();
    }
}
