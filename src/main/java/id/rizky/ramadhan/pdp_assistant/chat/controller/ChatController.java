package id.rizky.ramadhan.pdp_assistant.chat.controller;

import id.rizky.ramadhan.pdp_assistant.chat.dto.ChatReply;
import id.rizky.ramadhan.pdp_assistant.chat.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
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
        String answer = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        long endTime = System.currentTimeMillis();
        return new ChatReply(answer, endTime - startTime);
    }
}
