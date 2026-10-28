package com.mbc.puf.chat;

import com.mbc.puf.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 고객 앱 AI 도우미(Gemini). 프런트 개발서버(Node)의 /api/assistant/chat 와 같은 요청·응답 형식을 쓴다. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService service;
    public AssistantController(AssistantService service) { this.service = service; }
    public record Message(String role, String content) {}
    public record Request(List<Message> messages, String sessionId) {}
    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@RequestBody Request request) {
        return ApiResponse.ok(Map.of("answer", service.answer(request.messages())));
    }
}
