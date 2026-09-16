package com.bigData.main.controller.API;
import com.bigData.main.service.API.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
// ChatController.java
@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private AiService aiService;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String userMessage = request.getMessage();
        String aiReply = aiService.chatWithAI(userMessage);
        return new ChatResponse(aiReply);
    }
}
