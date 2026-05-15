package com.zkys.backend.communication;

import com.zkys.backend.security.UserPrincipal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationService {
    private final JdbcTemplate jdbcTemplate;

    public CommunicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listTeacherTodos(UserPrincipal me) {
        List<Map<String, Object>> list = jdbcTemplate.query(
                "SELECT id, title, meta, urgent, is_done AS isDone, created_at AS createdAt FROM teacher_todos WHERE teacher_id = ? ORDER BY is_done ASC, urgent DESC, created_at DESC",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("title", rs.getString("title"));
                    m.put("meta", rs.getString("meta") == null ? "" : rs.getString("meta"));
                    m.put("urgent", rs.getInt("urgent") == 1);
                    m.put("done", rs.getInt("isDone") == 1);
                    m.put("createdAt", rs.getTimestamp("createdAt"));
                    return m;
                },
                me.id()
        );
        return Map.of("list", list);
    }

    @Transactional
    public Map<String, Object> createTeacherTodo(UserPrincipal me, Map<String, Object> body) {
        String title = s(body.get("title"));
        if (title.isBlank()) throw new IllegalArgumentException("待办标题不能为空");
        jdbcTemplate.update(
                "INSERT INTO teacher_todos (teacher_id, title, meta, urgent, is_done, created_at) VALUES (?, ?, ?, ?, 0, ?)",
                me.id(), title, s(body.get("meta")), b(body.get("urgent")) ? 1 : 0, Timestamp.valueOf(LocalDateTime.now())
        );
        return Map.of("message", "待办已添加");
    }

    @Transactional
    public Map<String, Object> updateTeacherTodo(UserPrincipal me, int todoId, Map<String, Object> body) {
        int affected = jdbcTemplate.update(
                "UPDATE teacher_todos SET title = COALESCE(?, title), meta = COALESCE(?, meta), urgent = COALESCE(?, urgent), is_done = COALESCE(?, is_done) WHERE id = ? AND teacher_id = ?",
                nullable(body.get("title")),
                nullable(body.get("meta")),
                body.containsKey("urgent") ? (b(body.get("urgent")) ? 1 : 0) : null,
                body.containsKey("done") ? (b(body.get("done")) ? 1 : 0) : null,
                todoId,
                me.id()
        );
        if (affected <= 0) throw new IllegalArgumentException("待办不存在");
        return Map.of("message", "待办已更新");
    }

    @Transactional
    public Map<String, Object> deleteTeacherTodo(UserPrincipal me, int todoId) {
        int affected = jdbcTemplate.update("DELETE FROM teacher_todos WHERE id = ? AND teacher_id = ?", todoId, me.id());
        if (affected <= 0) throw new IllegalArgumentException("待办不存在");
        return Map.of("message", "待办已删除");
    }

    @Transactional
    public Map<String, Object> createTeacherMessage(UserPrincipal me, Map<String, Object> body) {
        String classId = s(body.get("classId"));
        String title = s(body.get("title"));
        String content = s(body.get("content"));
        if (classId.isBlank() || title.isBlank()) throw new IllegalArgumentException("班级与标题不能为空");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权向该班级发送消息");
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        if (className == null || className.isBlank()) throw new IllegalArgumentException("班级不存在");
        jdbcTemplate.update(
                "INSERT INTO teacher_messages (teacher_id, teacher_name, class_name, class_id, title, content, msg_type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                me.id(),
                s(me.name()).isBlank() ? "老师" : me.name(),
                className,
                classId,
                title,
                content,
                s(body.get("type")).isBlank() ? "notice" : s(body.get("type")),
                Timestamp.valueOf(LocalDateTime.now())
        );
        return Map.of("message", "消息已发送");
    }

    @Transactional
    public Map<String, Object> markStudentMessageRead(UserPrincipal me, int messageId) {
        jdbcTemplate.update(
                "INSERT INTO student_message_reads (student_id, message_id, read_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE read_at = VALUES(read_at)",
                me.id(), messageId, Timestamp.valueOf(LocalDateTime.now())
        );
        return Map.of("message", "已标记已读");
    }

    private boolean teacherOwnsClass(String teacherId, String classId) {
        Integer ok = queryOne("SELECT 1 FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1", Integer.class, classId, teacherId);
        return ok != null;
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean b(Object v) {
        if (v instanceof Boolean x) return x;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private String s(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private String nullable(Object v) {
        if (v == null) return null;
        return String.valueOf(v);
    }
}
