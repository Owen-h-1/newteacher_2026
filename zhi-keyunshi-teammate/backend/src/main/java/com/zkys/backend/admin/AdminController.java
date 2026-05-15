package com.zkys.backend.admin;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
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
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/teachers")
    public Map<String, Object> listTeachers(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return adminService.listTeachers();
    }

    @PostMapping("/teachers")
    public Map<String, Object> createTeacher(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return adminService.createTeacher(body == null ? Map.of() : body);
    }

    @DeleteMapping("/teachers/{username}")
    public Map<String, Object> deleteTeacher(Authentication authentication, @PathVariable String username) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return adminService.deleteTeacher(username);
    }

    @PutMapping("/teachers/{username}/subject")
    public Map<String, Object> updateTeacherSubject(Authentication authentication, @PathVariable String username, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        String subject = body == null ? "" : String.valueOf(body.getOrDefault("subject", ""));
        return adminService.updateTeacherSubject(username, subject);
    }

    @GetMapping("/teacher-classes")
    public Map<String, Object> teacherClasses(Authentication authentication, @RequestParam String username) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return adminService.listTeacherClasses(username);
    }

    @GetMapping("/subjects")
    public Map<String, Object> subjects(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return adminService.listSubjects();
    }

    @GetMapping("/classes")
    public Map<String, Object> classes(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "admin");
        return adminService.listAllClasses();
    }
}
