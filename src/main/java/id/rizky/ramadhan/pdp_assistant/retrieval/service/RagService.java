package id.rizky.ramadhan.pdp_assistant.retrieval.service;

import id.rizky.ramadhan.pdp_assistant.chat.dto.ChatRequest;
import id.rizky.ramadhan.pdp_assistant.retrieval.dto.RagReply;
import id.rizky.ramadhan.pdp_assistant.retrieval.dto.Sumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final Logger log = LoggerFactory.getLogger(RagService.class);

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

    private static final String REWRITE = """
        Ubah pertanyaan terakhir menjadi pertanyaan mandiri dengan
        mengganti kata rujukan (itu, tersebut, -nya) berdasarkan
        pertanyaan sebelumnya.
        
        Contoh:
        Sebelumnya: Apa itu data pribadi?
        Terakhir: Apa saja jenisnya?
        Keluaran: Apa saja jenis data pribadi?
        
        Pertanyaan sebelumnya:
        %s
        
        Pertanyaan terakhir: %s
        Keluaran:
        """;

    private final ChatClient chatClient;
    private final ChatClient rewriteClient;
    private final RetrievalService retrievalService;
    private final ChatMemory chatMemory;

    private static final Pattern SEBUT_PASAL = Pattern.compile("Pasal\\s+(\\d+)");

    public RagService(ChatClient chatClient,
                      @Qualifier("rewriteClient") ChatClient rewriteClient,
                      RetrievalService retrievalService,
                      ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.rewriteClient = rewriteClient;
        this.retrievalService = retrievalService;
        this.chatMemory = chatMemory;

    }

    public RagReply jawab(ChatRequest request, int topK){
        long start = System.currentTimeMillis();

        String convId = (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId()
                : "anon-" + UUID.randomUUID();

        String pertanyaanRetrieval = tulisUlang(request.message(), convId);
        List<Document> konteks = retrievalService.cari(pertanyaanRetrieval, topK, 0.0);

        String kutipan = konteks.stream()
                .map(d -> "[" + d.getMetadata().get("peraturan") + " — Pasal "
                        + d.getMetadata().get("pasal") + "]\n" + d.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        String jawaban = chatClient.prompt()
                .user(TEMPLATE.formatted(kutipan, request.message()))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
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

        return new RagReply(jawaban, pertanyaanRetrieval, sumber, disebut, valid,
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

    private String tulisUlang(String pertanyaan, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return pertanyaan;

        List<Message> riwayat = chatMemory.get(conversationId);
        log.info("Riwayat untuk {}: {} pesan", conversationId, riwayat.size());
        if (riwayat.isEmpty()) return pertanyaan;

        String ringkas = riwayat.stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText) .collect(Collectors.joining("\n"));

        String hasil = rewriteClient.prompt()
                .user(REWRITE.formatted(ringkas, pertanyaan))
                .call()
                .content()
                .trim();

        if (hasil.isBlank() || hasil.length() > 200 || !hasil.contains("?")) {
            log.warn("Tulis ulang gagal, pakai pertanyaan asli: {}", hasil);
            return pertanyaan;
        }

        return hasil;
    }
}
