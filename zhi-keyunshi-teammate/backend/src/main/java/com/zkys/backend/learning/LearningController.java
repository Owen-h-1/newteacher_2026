package com.zkys.backend.learning;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LearningController {
    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/selftest/exercises")
    public Map<String, Object> selftestExercises(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return learningService.selftestExercises(me);
    }

    @PostMapping("/selftest/exercises/{id}/collect")
    public Map<String, Object> toggleCollect(Authentication authentication, @PathVariable int id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return learningService.toggleCollect(me, id);
    }

    @GetMapping("/selftest/recommendations")
    public Map<String, Object> selftestRecommendations(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return learningService.selftestRecommendations(me);
    }

    @GetMapping("/selftest/history")
    public Map<String, Object> selftestHistory(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return learningService.selftestHistory();
    }

    @GetMapping("/platform/resources")
    public Map<String, Object> platformResources(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false, defaultValue = "小学") String grade
    ) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return learningService.platformResources(keyword, type, grade);
    }
}
