package com.zkys.backend.homework;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class HomeworkController {
    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    @GetMapping("/homework")
    public Map<String, Object> listHomework(Authentication authentication, @RequestParam(required = false) String status) {
        UserPrincipal me = Authz.principal(authentication);
        return Map.of("list", homeworkService.listHomework(me, status));
    }

    @PostMapping("/homework")
    public Map<String, Object> createHomework(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return homeworkService.createHomework(me, body);
    }

    @GetMapping("/homework/{id}")
    public Map<String, Object> detail(Authentication authentication, @PathVariable String id) {
        UserPrincipal me = Authz.principal(authentication);
        return homeworkService.getHomeworkDetail(me, id);
    }

    @PostMapping("/homework/{id}/submit")
    public Map<String, Object> submit(Authentication authentication, @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        List<Map<String, Object>> answers = null;
        if (body != null && body.get("answers") instanceof List<?> list) {
            answers = (List<Map<String, Object>>) list;
        }
        return homeworkService.submitHomework(me, id, answers);
    }

    @GetMapping("/teacher/homework")
    public Map<String, Object> listTeacherHomework(
            Authentication authentication,
            @RequestParam(required = false) String classId
    ) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return homeworkService.listTeacherHomeworkManage(me.id(), classId);
    }

    @GetMapping("/teacher/homework/{id}/submissions")
    public Map<String, Object> teacherHomeworkSubmissions(Authentication authentication, @PathVariable String id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return homeworkService.listSubmissionsForTeacher(id, me.id());
    }

    @GetMapping("/teacher/homework/{id}/submission-detail")
    public Map<String, Object> teacherHomeworkSubmissionDetail(
            Authentication authentication,
            @PathVariable String id,
            @RequestParam String studentNo
    ) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return homeworkService.submissionDetailForTeacher(id, me.id(), studentNo);
    }

    @PostMapping("/teacher/homework/grade")
    public Map<String, Object> setTeacherHomeworkGrade(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        String homeworkId = body == null ? "" : String.valueOf(body.getOrDefault("homeworkId", ""));
        String studentNo = body == null ? "" : String.valueOf(body.getOrDefault("studentNo", ""));
        if (homeworkId.trim().isEmpty()) throw new IllegalArgumentException("homeworkId 不能为空");
        if (studentNo.trim().isEmpty()) throw new IllegalArgumentException("studentNo 不能为空");
        return homeworkService.setSubmissionGrade(homeworkId.trim(), me.id(), studentNo.trim(), body.get("grade"));
    }

    @PostMapping("/teacher/homework/import-questions")
    public Map<String, Object> importHomeworkQuestions(Authentication authentication, @RequestParam("file") MultipartFile file) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return homeworkService.importQuestionsFromDocument(me.id(), file);
    }

    @DeleteMapping("/teacher/homework/{id}")
    public Map<String, Object> deleteTeacherHomework(Authentication authentication, @PathVariable String id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return homeworkService.deleteTeacherHomework(id, me.id());
    }

    @GetMapping("/student/overview")
    public Map<String, Object> studentOverview(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return homeworkService.studentOverview(me);
    }
}
