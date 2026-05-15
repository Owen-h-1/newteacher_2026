package com.zkys.backend.dashboard;

import com.zkys.backend.common.Authz;
import com.zkys.backend.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/student-board")
    public Map<String, Object> studentBoard(Authentication authentication, @RequestParam(required = false) String classId) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return dashboardService.teacherStudentBoard(me, classId);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(Authentication authentication) {
        UserPrincipal me = Authz.principal(authentication);
        Authz.requireRole(me, "teacher");
        return dashboardService.teacherOverview(me);
    }
}
