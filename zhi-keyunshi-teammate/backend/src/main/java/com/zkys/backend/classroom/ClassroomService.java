package com.zkys.backend.classroom;

import com.zkys.backend.security.UserPrincipal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {
    private final JdbcTemplate jdbcTemplate;

    public ClassroomService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listStudents(UserPrincipal me, String classId) {
        String cid = s(classId);
        if (cid.isBlank() && "admin".equals(me.role())) {
            List<Map<String, Object>> all = jdbcTemplate.query(
                    "SELECT id, name, class_name AS className, class_id AS classId, account_username AS accountUsername, joined_at AS joinedAt FROM students ORDER BY class_name, id",
                    (rs, i) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", rs.getString("id"));
                        m.put("name", rs.getString("name"));
                        m.put("className", rs.getString("className"));
                        m.put("classId", rs.getString("classId"));
                        m.put("accountUsername", rs.getString("accountUsername"));
                        m.put("joinedAt", rs.getTimestamp("joinedAt"));
                        return m;
                    }
            );
            return Map.of("list", all);
        }
        if (cid.isBlank()) throw new IllegalArgumentException("缺少 classId");
        if (!canManageClass(me, cid)) throw new IllegalArgumentException("无权查看该班级学生");
        List<Map<String, Object>> list = jdbcTemplate.query(
                "SELECT id, name, class_name AS className, class_id AS classId, account_username AS accountUsername, joined_at AS joinedAt FROM students WHERE class_id = ? ORDER BY id",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("name", rs.getString("name"));
                    m.put("className", rs.getString("className"));
                    m.put("classId", rs.getString("classId"));
                    m.put("accountUsername", rs.getString("accountUsername"));
                    m.put("joinedAt", rs.getTimestamp("joinedAt"));
                    return m;
                },
                cid
        );
        return Map.of("list", list);
    }

    public Map<String, Object> listClassInvites(UserPrincipal me) {
        List<Map<String, Object>> list = jdbcTemplate.query(
                "SELECT id AS classId, name AS className, invite_code AS inviteCode FROM classes WHERE owner_teacher_id = ? ORDER BY name",
                (rs, i) -> Map.of(
                        "classId", rs.getString("classId"),
                        "className", rs.getString("className"),
                        "inviteCode", rs.getString("inviteCode")
                ),
                me.id()
        );
        return Map.of("list", list);
    }

    @Transactional
    public Map<String, Object> createClass(UserPrincipal me, String classNameRaw) {
        String className = s(classNameRaw);
        if (className.isBlank()) throw new IllegalArgumentException("班级名称不能为空");
        if (className.length() > 50) throw new IllegalArgumentException("班级名称不能超过50个字");
        Integer exists = queryOne(
                "SELECT 1 FROM classes WHERE owner_teacher_id = ? AND name = ? LIMIT 1",
                Integer.class,
                me.id(), className
        );
        if (exists != null) throw new IllegalStateException("您已拥有同名班级，请换一个名称");

        String classId = UUID.randomUUID().toString();
        String inviteCode = pickUnusedInviteCode(classId);
        jdbcTemplate.update(
                "INSERT INTO classes (id, name, invite_code, owner_teacher_id) VALUES (?, ?, ?, ?)",
                classId, className, inviteCode, me.id()
        );
        String signCode = "ZKYS-" + (1000 + (int) (Math.random() * 9000));
        LocalDateTime validUntil = LocalDateTime.now().plusMinutes(15);
        jdbcTemplate.update(
                "INSERT INTO sign_sessions (class_id, sign_code, valid_until, last_sign_day) VALUES (?, ?, ?, ?)",
                classId, signCode, Timestamp.valueOf(validUntil), beijingCalendarYmd()
        );
        return Map.of("message", "班级已创建", "classId", classId, "className", className, "inviteCode", inviteCode);
    }

    @Transactional
    public Map<String, Object> resetInviteCode(UserPrincipal me, String classIdRaw) {
        String classId = s(classIdRaw);
        if (classId.isBlank()) throw new IllegalArgumentException("缺少 classId");
        String className = queryOne(
                "SELECT name FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1",
                String.class, classId, me.id()
        );
        if (className == null) throw new IllegalArgumentException("班级不存在或无权限");
        String code = pickUnusedInviteCode(classId);
        int affected = jdbcTemplate.update("UPDATE classes SET invite_code = ? WHERE id = ?", code, classId);
        if (affected <= 0) throw new IllegalArgumentException("班级不存在或无权限");
        return Map.of("message", "邀请码已重置", "classId", classId, "className", className, "inviteCode", code);
    }

    @Transactional
    public Map<String, Object> createStudent(UserPrincipal me, Map<String, Object> body) {
        String id = s(body.get("id"));
        String name = s(body.get("name"));
        String classId = s(body.get("classId"));
        String accountUsername = s(body.get("accountUsername"));
        if (id.isBlank() || name.isBlank() || classId.isBlank()) throw new IllegalArgumentException("学号、姓名、班级不能为空");
        if (!canManageClass(me, classId)) throw new IllegalArgumentException("无权向该班级添加学生");
        if (!id.matches("^\\d{6,20}$")) throw new IllegalArgumentException("学号应为6-20位数字");
        validateStudentAccountOrThrow(accountUsername);
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        if (className == null || className.isBlank()) throw new IllegalArgumentException("班级不存在");
        try {
            jdbcTemplate.update(
                    "INSERT INTO students (id, name, class_name, class_id, account_username) VALUES (?, ?, ?, ?, ?)",
                    id, name, className, classId, accountUsername.isBlank() ? null : accountUsername
            );
            jdbcTemplate.update(
                    "INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, NULL, NULL) ON DUPLICATE KEY UPDATE class_id = VALUES(class_id)",
                    classId, id
            );
        } catch (Exception e) {
            throw new IllegalStateException("学号或学生账号已存在");
        }
        return Map.of("message", "已录入本班花名册（学号、姓名）");
    }

    @Transactional
    public Map<String, Object> deleteStudent(UserPrincipal me, String studentId) {
        String classId = queryOne("SELECT class_id FROM students WHERE id = ? LIMIT 1", String.class, studentId);
        if (classId == null) throw new IllegalArgumentException("学生不存在");
        if (!canManageClass(me, classId)) throw new IllegalArgumentException("无权删除该学生");
        jdbcTemplate.update("DELETE FROM students WHERE id = ?", studentId);
        jdbcTemplate.update("DELETE FROM sign_records WHERE student_id = ?", studentId);
        return Map.of("message", "删除成功");
    }

    @Transactional
    public Map<String, Object> updateStudent(UserPrincipal me, String studentId, Map<String, Object> body) {
        String name = s(body.get("name"));
        String classId = s(body.get("classId"));
        Object accountUsernameRaw = body.get("accountUsername");
        if (name.isBlank() || classId.isBlank()) throw new IllegalArgumentException("姓名、班级不能为空");
        if (!canManageClass(me, classId)) throw new IllegalArgumentException("无权将该学生调整到该班级");

        List<Map<String, Object>> students = jdbcTemplate.query(
                "SELECT class_id AS classId FROM students WHERE id = ? LIMIT 1",
                (rs, i) -> Map.of("classId", rs.getString("classId")),
                studentId
        );
        if (students.isEmpty()) throw new IllegalArgumentException("学生不存在");
        String oldClassId = s(students.get(0).get("classId"));
        if (!canManageClass(me, oldClassId)) throw new IllegalArgumentException("无权修改该学生");
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        if (className == null || className.isBlank()) throw new IllegalArgumentException("学生不存在");

        if (accountUsernameRaw == null && !body.containsKey("accountUsername")) {
            jdbcTemplate.update("UPDATE students SET name = ?, class_name = ?, class_id = ? WHERE id = ?", name, className, classId, studentId);
        } else {
            String accountUsername = s(accountUsernameRaw);
            validateStudentAccountOrThrow(accountUsername);
            jdbcTemplate.update(
                    "UPDATE students SET name = ?, class_name = ?, class_id = ?, account_username = ? WHERE id = ?",
                    name, className, classId, accountUsername.isBlank() ? null : accountUsername, studentId
            );
        }
        if (!oldClassId.equals(classId)) {
            jdbcTemplate.update("DELETE FROM sign_records WHERE student_id = ?", studentId);
            jdbcTemplate.update("INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, NULL, NULL)", classId, studentId);
        } else {
            jdbcTemplate.update("UPDATE sign_records SET class_id = ? WHERE student_id = ?", classId, studentId);
        }
        return Map.of("message", "更新成功");
    }

    @Transactional
    public Map<String, Object> batchStudents(UserPrincipal me, List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("请提供导入数据");
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String id = s(item.get("id"));
            String name = s(item.get("name"));
            String className = s(item.get("className"));
            String accountUsername = s(item.get("accountUsername"));
            if (!id.matches("^\\d{6,20}$") || name.isBlank() || className.isBlank()) {
                skipped++;
                errors.add(Map.of("id", id, "reason", "缺少学号、姓名或班级/学号格式错误"));
                continue;
            }
            String classId = queryOne("SELECT id FROM classes WHERE owner_teacher_id = ? AND name = ? LIMIT 1", String.class, me.id(), className);
            if (classId == null || classId.isBlank()) {
                skipped++;
                errors.add(Map.of("id", id, "reason", "班级「" + className + "」不存在或不属于当前教师"));
                continue;
            }
            if (!accountUsername.isBlank()) {
                Integer ok = queryOne("SELECT 1 FROM users WHERE username = ? AND role = 'student' LIMIT 1", Integer.class, accountUsername);
                if (ok == null) accountUsername = "";
            }
            Integer exists = queryOne("SELECT 1 FROM students WHERE id = ? LIMIT 1", Integer.class, id);
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("classId", classId);
            payload.put("accountUsername", accountUsername);
            try {
                if (exists == null) {
                    createStudent(me, Map.of("id", id, "name", name, "classId", classId, "accountUsername", accountUsername));
                    created++;
                } else {
                    updateStudent(me, id, payload);
                    updated++;
                }
            } catch (Exception e) {
                skipped++;
                errors.add(Map.of("id", id, "reason", "导入失败"));
            }
        }
        return Map.of("message", "导入完成：新增" + created + "，更新" + updated + "，跳过" + skipped, "created", created, "updated", updated, "skipped", skipped, "errors", errors);
    }

    @Transactional
    public Map<String, Object> joinClass(UserPrincipal me, String inviteCodeRaw) {
        String inviteCode = normalizeInviteCode(inviteCodeRaw);
        if (inviteCode.isBlank()) throw new IllegalArgumentException("邀请码不能为空");
        List<Map<String, Object>> classes = jdbcTemplate.query(
                "SELECT id AS classId, name AS className, invite_code AS inviteCode FROM classes",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("classId", rs.getString("classId"));
                    m.put("className", rs.getString("className"));
                    m.put("inviteCode", rs.getString("inviteCode"));
                    return m;
                }
        );
        List<Map<String, Object>> matches = classes.stream().filter(c -> normalizeInviteCode(s(c.get("inviteCode"))).equals(inviteCode)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("邀请码无效");
        String classId = s(matches.get(0).get("classId"));
        String className = s(matches.get(0).get("className"));

        List<Map<String, Object>> existing = jdbcTemplate.query(
                "SELECT id, class_id AS classId FROM students WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) LIMIT 1",
                (rs, i) -> Map.of("id", rs.getString("id"), "classId", rs.getString("classId")),
                me.username()
        );
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("老师尚未在花名册绑定该账号，请联系老师在学生管理中先绑定账号");
        }
        String rosterId = s(existing.get(0).get("id"));
        String eClassId = s(existing.get(0).get("classId"));
        if (!eClassId.equals(classId)) throw new IllegalStateException("该账号已在其他班级登记，请联系老师后再试");

        jdbcTemplate.update(
                "INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, NULL, NULL) ON DUPLICATE KEY UPDATE class_id = VALUES(class_id)",
                classId, rosterId
        );
        markJoined(me.username(), classId);
        return Map.of("message", "加入班级成功，已写入花名册", "className", className);
    }

    private void validateStudentAccountOrThrow(String accountUsername) {
        if (accountUsername == null || accountUsername.isBlank()) return;
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT role FROM users WHERE username = ? LIMIT 1",
                (rs, i) -> Map.of("role", rs.getString("role")),
                accountUsername
        );
        if (rows.isEmpty() || !"student".equals(s(rows.get(0).get("role")))) {
            throw new IllegalArgumentException("学生账号不存在或不是学生角色");
        }
    }

    private void markJoined(String username, String classId) {
        jdbcTemplate.update(
                "UPDATE students SET joined_at = ? WHERE LOWER(TRIM(account_username)) = LOWER(TRIM(?)) AND class_id = ?",
                Timestamp.valueOf(LocalDateTime.now()), username, classId
        );
    }

    private boolean teacherOwnsClass(String teacherId, String classId) {
        Integer ok = queryOne("SELECT 1 FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1", Integer.class, classId, teacherId);
        return ok != null;
    }

    private boolean canManageClass(UserPrincipal me, String classId) {
        if ("admin".equals(me.role())) return true;
        if (!"teacher".equals(me.role())) return false;
        return teacherOwnsClass(me.id(), classId);
    }

    private String pickUnusedInviteCode(String excludeClassId) {
        String ex = s(excludeClassId);
        for (int i = 0; i < 60; i++) {
            String code = "ZKYS-" + (100000 + (int) (Math.random() * 900000));
            Integer hit = queryOne("SELECT 1 FROM classes WHERE invite_code = ? AND (? = '' OR id <> ?) LIMIT 1", Integer.class, code, ex, ex);
            if (hit == null) return code;
        }
        return "ZKYS-" + String.valueOf(System.currentTimeMillis()).substring(7);
    }

    private String normalizeInviteCode(String raw) {
        if (raw == null) return "";
        return raw.trim().replace('\u2013', '-').replace('\u2014', '-').replace('\uff0d', '-').replaceAll("\\s+", "");
    }

    private String beijingCalendarYmd() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT));
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String s(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
