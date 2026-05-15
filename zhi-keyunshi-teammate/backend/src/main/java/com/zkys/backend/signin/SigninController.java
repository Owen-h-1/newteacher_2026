package com.zkys.backend.signin;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signin")
public class SigninController {
    private final SigninService signinService;

    public SigninController(SigninService signinService) {
        this.signinService = signinService;
    }

    @GetMapping("/classes")
    public Map<String, Object> classes(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return signinService.classes(me);
    }

    @GetMapping("/records")
    public Map<String, Object> records(Authentication authentication, @RequestParam String classId) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return signinService.records(me, classId);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return signinService.refresh(me, body == null ? "" : String.valueOf(body.getOrDefault("classId", "")));
    }

    @PostMapping("/mark")
    public Map<String, Object> mark(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return signinService.mark(me, body == null ? Map.of() : body);
    }

    @PostMapping("/undo")
    public Map<String, Object> undo(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return signinService.undo(me, body == null ? Map.of() : body);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication, @RequestParam String classId) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        SigninService.ExportFile file = signinService.export(me, classId);
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.bytes());
    }

    @PostMapping("/photo-recognition")
    public Map<String, Object> photoRecognition(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return signinService.photoRecognition(me, body == null ? Map.of() : body);
    }
}
