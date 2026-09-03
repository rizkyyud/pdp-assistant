package id.rizky.ramadhan.pdp_assistant.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("Anda asisten yang menjawab berdasarkan dokumen yang diberikan.")
                .build();
    }

    @Bean
    ChatMemory chatMemory(){
        return MessageWindowChatMemory.builder()
                .maxMessages(10).build();
    }

    @Bean
    ChatClient rewriteClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                    Tugas Anda HANYA menulis ulang pertanyaan.
                    Jangan pernah menjawab pertanyaan.
                    Keluarkan satu kalimat pertanyaan saja.
                    """)
                .defaultOptions(OllamaChatOptions.builder()
                        .model("qwen3:1.7b")
                        .temperature(0.0)
                        .disableThinking())
                .build();
    }
}
