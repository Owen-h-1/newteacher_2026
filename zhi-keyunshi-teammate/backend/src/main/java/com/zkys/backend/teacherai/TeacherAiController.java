package com.zkys.backend.teacherai;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TeacherAiController {
    private final TeacherAiService teacherAiService;

    public TeacherAiController(TeacherAiService teacherAiService) {
        this.teacherAiService = teacherAiService;
    }

    @PostMapping("/teacher/warnings/agent-plan")
    public Map<String, Object> warningsAgentPlan(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return teacherAiService.warningsAgentPlan(me, body == null ? Map.of() : body);
    }

    @PostMapping("/teacher/ai-courseware/generate")
    public Map<String, Object> aiCoursewareGenerate(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return teacherAiService.aiCoursewareGenerate(me, body == null ? Map.of() : body);
    }

    @PostMapping("/teacher/ai-courseware/download")
    public ResponseEntity<byte[]> aiCoursewareDownload(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        TeacherAiService.DownloadFile file = teacherAiService.aiCoursewareDownload(me, body == null ? Map.of() : body);
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    @PostMapping("/teacher/ai-teaching-design/generate")
    public Map<String, Object> aiTeachingDesignGenerate(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return teacherAiService.aiTeachingDesignGenerate(me, body == null ? Map.of() : body);
    }

    @PostMapping("/teacher/ai-teaching-design/download")
    public ResponseEntity<byte[]> aiTeachingDesignDownload(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        TeacherAiService.DownloadFile file = teacherAiService.aiTeachingDesignDownload(body == null ? Map.of() : body);
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }

    @PostMapping("/teacher/ai-teaching-design/agent-plan")
    public Map<String, Object> aiTeachingDesignAgentPlan(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return teacherAiService.aiTeachingDesignAgentPlan(me, body == null ? Map.of() : body);
    }

    @PostMapping("/teacher/ppt-assets")
    public Map<String, Object> createTeacherPptAsset(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return teacherAiService.createTeacherPptAsset(me, body == null ? Map.of() : body);
    }

    @GetMapping("/teacher/ppt-assets")
    public Map<String, Object> listTeacherPptAssets(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return teacherAiService.listTeacherPptAssets(me);
    }

    @DeleteMapping("/teacher/ppt-assets/{id}")
    public Map<String, Object> deleteTeacherPptAsset(Authentication authentication, @PathVariable String id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return teacherAiService.deleteTeacherPptAsset(me, id);
    }

    @GetMapping("/student/ppt-assets")
    public Map<String, Object> listStudentPptAssets(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return teacherAiService.listStudentPptAssets();
    }

    @PostMapping("/student/ppt-lecture/generate")
    public Map<String, Object> studentPptLectureGenerate(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return teacherAiService.studentPptLectureGenerate(me, body == null ? Map.of() : body);
    }
}
