package com.mbc.puf.chat;

import com.mbc.puf.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service = service; }
    public record Question(@NotBlank @Size(max=500) String question) {}
    @PostMapping
    public ApiResponse<ChatService.Answer> answer(@Valid @RequestBody Question question) {
        return ApiResponse.ok(service.answer(question.question()));
    }
}
