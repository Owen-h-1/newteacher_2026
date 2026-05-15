package com.zkys.backend.communication;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CommunicationController {
    private final CommunicationService communicationService;

    public CommunicationController(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @GetMapping("/teacher/todos")
    public Map<String, Object> teacherTodos(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return communicationService.listTeacherTodos(me);
    }

    @PostMapping("/teacher/todos")
    public Map<String, Object> createTeacherTodo(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return communicationService.createTeacherTodo(me, body == null ? Map.of() : body);
    }

    @PutMapping("/teacher/todos/{id}")
    public Map<String, Object> updateTeacherTodo(Authentication authentication, @PathVariable int id, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return communicationService.updateTeacherTodo(me, id, body == null ? Map.of() : body);
    }

    @DeleteMapping("/teacher/todos/{id}")
    public Map<String, Object> deleteTeacherTodo(Authentication authentication, @PathVariable int id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return communicationService.deleteTeacherTodo(me, id);
    }

    @PostMapping("/teacher/messages")
    public Map<String, Object> createTeacherMessage(Authentication authentication, @RequestBody Map<String, Object> body) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return communicationService.createTeacherMessage(me, body == null ? Map.of() : body);
    }

    @PostMapping("/student/messages/{id}/read")
    public Map<String, Object> readStudentMessage(Authentication authentication, @PathVariable int id) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "student");
        return communicationService.markStudentMessageRead(me, id);
    }
}
