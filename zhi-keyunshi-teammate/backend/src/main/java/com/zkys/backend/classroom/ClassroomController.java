package com.zkys.backend.classroom;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClassroomController {
    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    @GetMapping("/teacher/students")
    public Map<String, Object> teacherStudents(Authentication authentication, @RequestParam String classId) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return classroomService.listStudents(me, classId);
    }

    @GetMapping("/teacher/class-invites")
    public Map<String, Object> teacherClassInvites(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return classroomService.listClassInvites(me);
    }

    @PostMapping("/teacher/classes")
    public Map<String, Object> createClass(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return classroomService.createClass(me, body == null ? "" : String.valueOf(body.getOrDefault("className", "")));
    }

    @PostMapping("/teacher/class-invites/reset")
    public Map<String, Object> resetClassInvite(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return classroomService.resetInviteCode(me, body == null ? "" : String.valueOf(body.getOrDefault("classId", "")));
    }

    @PostMapping("/teacher/students")
    public Map<String, Object> createStudent(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return classroomService.createStudent(me, body == null ? Map.of() : body);
    }

    @DeleteMapping("/teacher/students/{id}")
    public Map<String, Object> deleteStudent(Authentication authentication, @PathVariable String id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return classroomService.deleteStudent(me, id);
    }

    @PutMapping("/teacher/students/{id}")
    public Map<String, Object> updateStudent(Authentication authentication, @PathVariable String id, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return classroomService.updateStudent(me, id, body == null ? Map.of() : body);
    }

    @PostMapping("/teacher/students/batch")
    public Map<String, Object> batchStudents(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        Object items = body == null ? null : body.get("items");
        List<Map<String, Object>> list = items instanceof List<?> x ? (List<Map<String, Object>>) x : List.of();
        return classroomService.batchStudents(me, list);
    }

    @PostMapping("/student/join-class")
    public Map<String, Object> joinClass(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return classroomService.joinClass(me, body == null ? "" : String.valueOf(body.getOrDefault("inviteCode", "")));
    }

    @GetMapping("/admin/students")
    public Map<String, Object> adminStudents(Authentication authentication, @RequestParam(required = false, defaultValue = "") String classId) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return classroomService.listStudents(me, classId);
    }

    @PostMapping("/admin/students")
    public Map<String, Object> adminCreateStudent(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return classroomService.createStudent(me, body == null ? Map.of() : body);
    }

    @PutMapping("/admin/students/{id}")
    public Map<String, Object> adminUpdateStudent(Authentication authentication, @PathVariable String id, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return classroomService.updateStudent(me, id, body == null ? Map.of() : body);
    }

    @DeleteMapping("/admin/students/{id}")
    public Map<String, Object> adminDeleteStudent(Authentication authentication, @PathVariable String id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return classroomService.deleteStudent(me, id);
    }
}
