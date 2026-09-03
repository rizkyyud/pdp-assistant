package id.rizky.ramadhan.pdp_assistant.chat.controller;

import id.rizky.ramadhan.pdp_assistant.chat.dto.ChatReply;
import id.rizky.ramadhan.pdp_assistant.chat.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping
    public ChatReply chat(@RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();
        double temp = 0.3;

        String answer = chatClient.prompt()
                .user(request.message())
                .options(OllamaChatOptions.builder()
                        .model("qwen3:8b")
                        .temperature(temp)
                        .disableThinking())
                .call()
                .content();

        long endTime = System.currentTimeMillis();
        return new ChatReply(answer, endTime - startTime);
    }
}
