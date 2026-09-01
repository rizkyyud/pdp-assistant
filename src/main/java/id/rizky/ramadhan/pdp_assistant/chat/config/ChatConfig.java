package id.rizky.ramadhan.pdp_assistant.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                Anda asisten hukum yang menjawab berdasarkan peraturan Indonesia.
                Jawab singkat, maksimal 3 kalimat.
                Selalu sebutkan nomor UU dan pasal yang menjadi dasar jawaban.
                Jika tidak yakin, katakan tidak tahu. Jangan mengarang.
                """)
                .build();
    }
}
