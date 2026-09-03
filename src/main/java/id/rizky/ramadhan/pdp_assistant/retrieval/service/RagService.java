package id.rizky.ramadhan.pdp_assistant.retrieval.service;

import id.rizky.ramadhan.pdp_assistant.retrieval.dto.RagReply;
import id.rizky.ramadhan.pdp_assistant.retrieval.dto.Sumber;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final String TEMPLATE = """
            Jawab pertanyaan HANYA berdasarkan kutipan peraturan di bawah ini.
            Jika jawabannya tidak ada dalam kutipan, katakan bahwa informasi
            tersebut tidak ditemukan dalam dokumen.
            
            Sebutkan nomor pasal yang menjadi dasar jawaban.
            JANGAN menyebut nomor ayat — cukup nomor pasal saja.
            
            === KUTIPAN PERATURAN ===
            %s
            === AKHIR KUTIPAN ===
            
            Pertanyaan: %s
            """;

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    private static final Pattern SEBUT_PASAL = Pattern.compile("Pasal\\s+(\\d+)");

    public RagService(ChatClient chatClient, RetrievalService retrievalService) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
    }

    public RagReply jawab(String pertanyaan, int topK){
        long start = System.currentTimeMillis();

        List<Document> konteks = retrievalService.cari(pertanyaan, topK, 0.0);

        String kutipan = konteks.stream()
                .map(d -> "[" + d.getMetadata().get("peraturan") + " — Pasal "
                        + d.getMetadata().get("pasal") + "]\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        String jawaban = chatClient.prompt()
                .user(TEMPLATE.formatted(kutipan, pertanyaan))
                .options(OllamaChatOptions.builder()
                        .temperature(0.0)
                        .disableThinking())
                .call()
                .content();

        List<Sumber> sumber = konteks.stream()
                .map(d -> new Sumber(
                        (String) d.getMetadata().get("pasal"),
                        (String) d.getMetadata().get("peraturan"),
                        d.getScore(),
                        potong(d.getText(), 300)))
                .toList();

        List<String> disebut = ekstrakPasal(jawaban);
        Set<String> tersedia = konteks.stream()
                .map(d -> (String) d.getMetadata().get("pasal"))
                .collect(Collectors.toSet());

        boolean valid = tersedia.containsAll(disebut);

        return new RagReply(jawaban, sumber, disebut, valid,
                System.currentTimeMillis() - start);
    }

    private List<String> ekstrakPasal(String jawaban) {
        return SEBUT_PASAL.matcher(jawaban).results()
                .map(r -> r.group(1))
                .distinct()
                .toList();
    }

    private String potong(String teks, int maks) {
        return teks.length() <= maks ? teks : teks.substring(0, maks) + "...";
    }
}
