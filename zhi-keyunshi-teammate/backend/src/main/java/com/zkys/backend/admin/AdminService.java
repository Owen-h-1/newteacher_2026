package com.zkys.backend.admin;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final JdbcTemplate jdbcTemplate;

    public AdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> createTeacher(Map<String, Object> body) {
        ensureTeacherRegistryTable();
        String username = s(body.get("username"));
        String name = s(body.get("name"));
        String subject = s(body.get("subject"));
        String email = s(body.get("email")).toLowerCase();
        if (username.isBlank() || name.isBlank() || subject.isBlank()) {
            throw new IllegalArgumentException("教师ID号、姓名、学科不能为空");
        }
        if (!username.matches("^\\d{4,20}$")) {
            throw new IllegalArgumentException("教师ID号必须为4-20位数字");
        }
        Integer exists = queryOne("SELECT 1 FROM teacher_registry WHERE username = ? LIMIT 1", Integer.class, username);
        if (exists != null) {
            throw new IllegalArgumentException("教师账号已存在");
        }
        jdbcTemplate.update(
                "INSERT INTO teacher_registry (username, name, email, subject, enabled) VALUES (?, ?, ?, ?, 1)",
                username, name, email.isBlank() ? null : email, subject
        );
        return Map.of("message", "教师已添加");
    }

    public Map<String, Object> listTeachers() {
        ensureTeacherRegistryTable();
        List<Map<String, Object>> list = new ArrayList<>(jdbcTemplate.query(
                "SELECT tr.username, tr.name, tr.email, tr.subject, tr.enabled, " +
                        "u.id AS userId, u.role, " +
                        "(SELECT COUNT(1) FROM classes c WHERE c.owner_teacher_id = u.id) AS classCount " +
                        "FROM teacher_registry tr LEFT JOIN users u ON LOWER(TRIM(u.username)) = LOWER(TRIM(tr.username)) " +
                        "ORDER BY tr.subject, tr.username",
                (rs, i) -> Map.of(
                        "username", s(rs.getString("username")),
                        "name", s(rs.getString("name")),
                        "email", s(rs.getString("email")),
                        "subject", s(rs.getString("subject")),
                        "enabled", rs.getBoolean("enabled"),
                        "registered", "teacher".equals(s(rs.getString("role"))),
                        "classCount", rs.getInt("classCount")
                )
        ));
        List<Map<String, Object>> legacyTeachers = jdbcTemplate.query(
                "SELECT u.id, u.username, u.name, u.email, " +
                        "(SELECT COUNT(1) FROM classes c WHERE c.owner_teacher_id = u.id) AS classCount " +
                        "FROM users u " +
                        "WHERE u.role = 'teacher' " +
                        "AND NOT EXISTS (SELECT 1 FROM teacher_registry tr WHERE LOWER(TRIM(tr.username)) = LOWER(TRIM(u.username))) " +
                        "ORDER BY u.username",
                (rs, i) -> Map.of(
                        "username", s(rs.getString("username")),
                        "name", s(rs.getString("name")),
                        "email", s(rs.getString("email")),
                        "subject", "未设置",
                        "enabled", true,
                        "registered", true,
                        "classCount", rs.getInt("classCount")
                )
        );
        list.addAll(legacyTeachers);
        return Map.of("list", list);
    }

    public Map<String, Object> listTeacherClasses(String usernameRaw) {
        String username = s(usernameRaw);
        if (username.isBlank()) throw new IllegalArgumentException("缺少教师ID号");
        String teacherId = queryOne(
                "SELECT id FROM users WHERE username = ? AND role = 'teacher' LIMIT 1",
                String.class,
                username
        );
        if (teacherId == null) return Map.of("list", List.of());
        List<Map<String, Object>> list = jdbcTemplate.query(
                "SELECT id AS classId, name AS className, invite_code AS inviteCode FROM classes WHERE owner_teacher_id = ? ORDER BY name",
                (rs, i) -> Map.of(
                        "classId", s(rs.getString("classId")),
                        "className", s(rs.getString("className")),
                        "inviteCode", s(rs.getString("inviteCode"))
                ),
                teacherId
        );
        return Map.of("list", list);
    }

    public Map<String, Object> listAllClasses() {
        List<Map<String, Object>> classes = jdbcTemplate.query(
                "SELECT c.id AS classId, c.name AS className, u.username AS teacherUsername, u.name AS teacherName " +
                        "FROM classes c LEFT JOIN users u ON u.id = c.owner_teacher_id ORDER BY c.name",
                (rs, i) -> Map.of(
                        "classId", s(rs.getString("classId")),
                        "className", s(rs.getString("className")),
                        "teacherUsername", s(rs.getString("teacherUsername")),
                        "teacherName", s(rs.getString("teacherName"))
                )
        );
        return Map.of("classes", classes);
    }

    @Transactional
    public Map<String, Object> deleteTeacher(String usernameRaw) {
        ensureTeacherRegistryTable();
        String username = s(usernameRaw);
        if (username.isBlank()) throw new IllegalArgumentException("缺少教师账号");
        List<Map<String, Object>> users = jdbcTemplate.query(
                "SELECT id FROM users WHERE username = ? AND role = 'teacher' LIMIT 1",
                (rs, i) -> Map.of("id", s(rs.getString("id"))),
                username
        );
        if (!users.isEmpty()) {
            String teacherId = s(users.get(0).get("id"));
            Integer classCount = queryOne("SELECT COUNT(1) FROM classes WHERE owner_teacher_id = ?", Integer.class, teacherId);
            if (classCount != null && classCount > 0) {
                throw new IllegalStateException("该教师已有班级，无法删除");
            }
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", teacherId);
        }
        jdbcTemplate.update("DELETE FROM teacher_registry WHERE username = ?", username);
        return Map.of("message", "教师已删除");
    }

    @Transactional
    public Map<String, Object> updateTeacherSubject(String usernameRaw, String subjectRaw) {
        ensureTeacherRegistryTable();
        String username = s(usernameRaw);
        String subject = s(subjectRaw);
        if (username.isBlank()) throw new IllegalArgumentException("缺少教师账号");
        if (subject.isBlank()) throw new IllegalArgumentException("学科不能为空");

        List<Map<String, Object>> userRows = jdbcTemplate.query(
                "SELECT name, email FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(?)) AND role = 'teacher' LIMIT 1",
                (rs, i) -> Map.of(
                        "name", s(rs.getString("name")),
                        "email", s(rs.getString("email"))
                ),
                username
        );
        String fallbackName = username;
        String fallbackEmail = "";
        if (!userRows.isEmpty()) {
            fallbackName = s(userRows.get(0).get("name"));
            fallbackEmail = s(userRows.get(0).get("email"));
        }

        Integer exists = queryOne("SELECT 1 FROM teacher_registry WHERE username = ? LIMIT 1", Integer.class, username);
        if (exists == null) {
            jdbcTemplate.update(
                    "INSERT INTO teacher_registry (username, name, email, subject, enabled) VALUES (?, ?, ?, ?, 1)",
                    username, fallbackName.isBlank() ? username : fallbackName, fallbackEmail.isBlank() ? null : fallbackEmail, subject
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE teacher_registry SET subject = ? WHERE LOWER(TRIM(username)) = LOWER(TRIM(?))",
                    "UPDATE teacher_registry SET subject = ? WHERE username = ?",
                    subject, username
            );
        }
        return Map.of("message", "教师学科已更新", "username", username, "subject", subject);
    }

    public Map<String, Object> listSubjects() {
        List<Map<String, Object>> teachers = (List<Map<String, Object>>) listTeachers().get("list");
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> teacher : teachers) {
            String subject = s(teacher.get("subject"));
            if (subject.isBlank()) subject = "未设置";
            grouped.computeIfAbsent(subject, k -> new ArrayList<>()).add(Map.of(
                    "username", s(teacher.get("username")),
                    "name", s(teacher.get("name")),
                    "email", s(teacher.get("email")),
                    "registered", Boolean.TRUE.equals(teacher.get("registered"))
            ));
        }
        List<Map<String, Object>> list = grouped.entrySet().stream()
                .map(e -> Map.of("subject", e.getKey(), "teachers", e.getValue()))
                .toList();
        return Map.of("list", list);
    }

    private void ensureTeacherRegistryTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS teacher_registry (
                  username VARCHAR(50) PRIMARY KEY,
                  name VARCHAR(80) NOT NULL,
                  email VARCHAR(120) NULL,
                  subject VARCHAR(80) NOT NULL,
                  enabled TINYINT(1) NOT NULL DEFAULT 1,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String s(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
