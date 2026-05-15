package com.zkys.backend.ai;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/llm/status")
    public Map<String, Object> llmStatus(Authentication authentication) {
        Authz.principal(authentication);
        return aiService.llmStatus();
    }

    @PostMapping("/llm/chat")
    public Map<String, Object> llmChat(Authentication authentication, @RequestBody Map<String, Object> body) {
        Authz.principal(authentication);
        return aiService.llmChat(body == null ? "" : String.valueOf(body.getOrDefault("message", "")));
    }

    @PostMapping("/digital-human/chat")
    public Map<String, Object> digitalHumanChat(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        return aiService.digitalHumanChat(me, body == null ? Map.of() : body);
    }

    @PostMapping("/selftest/ai/generate")
    public Map<String, Object> generateSelfTest(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return aiService.generateSelfTestQuestions(me, body == null ? Map.of() : body);
    }

    @PostMapping("/selftest/ai/grade")
    public Map<String, Object> gradeSelfTest(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return aiService.gradeSelfTestAnswers(me, body == null ? Map.of() : body);
    }
}
