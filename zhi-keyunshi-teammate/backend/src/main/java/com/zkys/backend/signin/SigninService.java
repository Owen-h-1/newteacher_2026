package com.zkys.backend.signin;

import com.zkys.backend.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SigninService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${EXTERNAL_SIGNIN_FACE_API:}")
    private String externalSigninFaceApi;

    @Value("${EXTERNAL_SERVICE_API_KEY:}")
    private String externalServiceApiKey;

    public SigninService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> classes(UserPrincipal me) {
        List<Map<String, Object>> classes = jdbcTemplate.query(
                "SELECT id AS classId, name AS className FROM classes WHERE owner_teacher_id = ? ORDER BY name",
                (rs, i) -> Map.of("classId", rs.getString("classId"), "className", rs.getString("className")),
                me.id()
        );
        return Map.of("classes", classes);
    }

    public Map<String, Object> records(UserPrincipal me, String classIdRaw) {
        String classId = s(classIdRaw);
        if (classId.isBlank()) throw new IllegalArgumentException("缺少 classId");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权查看该班级签到");
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        Map<String, Object> data = getSignData(classId);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        int signedCount = (int) records.stream().filter(r -> s(r.get("time")).length() > 0).count();
        Map<String, Object> out = new HashMap<>();
        out.put("classId", classId);
        out.put("className", className == null ? "" : className);
        out.put("signCode", data.getOrDefault("signCode", "ZKYS-0000"));
        out.put("validUntil", data.getOrDefault("validUntil", Timestamp.valueOf(LocalDateTime.now().plusMinutes(15))));
        out.put("totalStudents", records.size());
        out.put("signedCount", signedCount);
        out.put("records", records);
        return out;
    }

    @Transactional
    public Map<String, Object> refresh(UserPrincipal me, String classIdRaw) {
        String classId = s(classIdRaw);
        if (classId.isBlank()) throw new IllegalArgumentException("缺少 classId");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权操作该班级");
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        Map<String, Object> refreshed = refreshSignCode(classId);
        Map<String, Object> out = new HashMap<>();
        out.put("classId", classId);
        out.put("className", className == null ? "" : className);
        out.putAll(refreshed);
        return out;
    }

    @Transactional
    public Map<String, Object> mark(UserPrincipal me, Map<String, Object> body) {
        String classId = s(body.get("classId"));
        String studentId = s(body.get("studentId"));
        String method = s(body.get("method"));
        if (classId.isBlank() || studentId.isBlank()) throw new IllegalArgumentException("缺少班级或学生信息");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权操作该班级");
        markSignRecord(classId, studentId, method.isBlank() ? "手动" : method);
        return Map.of("message", "签到已更新");
    }

    @Transactional
    public Map<String, Object> undo(UserPrincipal me, Map<String, Object> body) {
        String classId = s(body.get("classId"));
        String studentId = s(body.get("studentId"));
        if (classId.isBlank() || studentId.isBlank()) throw new IllegalArgumentException("缺少班级或学生信息");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权操作该班级");
        undoSignRecord(classId, studentId);
        return Map.of("message", "已撤销签到");
    }

    public ExportFile export(UserPrincipal me, String classIdRaw) {
        String classId = s(classIdRaw);
        if (classId.isBlank()) throw new IllegalArgumentException("缺少 classId");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权导出该班级");
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        Map<String, Object> data = getSignData(classId);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("records");
        byte[] bytes = buildXlsx(rows);
        String safeClass = s(className).replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeClass.isBlank()) safeClass = classId;
        return new ExportFile(bytes, safeClass + "-签到表.xlsx");
    }

    public Map<String, Object> photoRecognition(UserPrincipal me, Map<String, Object> body) {
        String classId = s(body.get("classId"));
        String imageName = s(body.get("imageName"));
        String imageData = body.get("imageData") == null ? "" : String.valueOf(body.get("imageData"));
        if (classId.isBlank() || imageName.isBlank()) throw new IllegalArgumentException("classId 和 imageName 不能为空");
        if (!teacherOwnsClass(me.id(), classId)) throw new IllegalArgumentException("无权操作该班级");
        String className = queryOne("SELECT name FROM classes WHERE id = ? LIMIT 1", String.class, classId);
        if (s(externalSigninFaceApi).isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "人脸签到服务 未配置，请在 .env 设置对应 EXTERNAL_*_API");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "teacher", Map.of("id", me.id(), "username", me.username(), "role", me.role(), "email", me.email(), "name", me.name()),
                    "className", className == null ? classId : className,
                    "imageName", imageName,
                    "imageData", imageData
            );
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(externalSigninFaceApi))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (!s(externalServiceApiKey).isBlank()) {
                builder.header("Authorization", "Bearer " + externalServiceApiKey);
            }
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> data = response.body() == null || response.body().isBlank()
                    ? Map.of()
                    : objectMapper.readValue(response.body(), Map.class);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, s(data.getOrDefault("message", "人脸签到服务调用失败")));
            }
            return data;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, s(ex.getMessage()).isBlank() ? "人脸签到服务调用失败" : ex.getMessage());
        }
    }

    private byte[] buildXlsx(List<Map<String, Object>> records) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("签到表");
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("学号");
            head.createCell(1).setCellValue("姓名");
            head.createCell(2).setCellValue("签到时间");
            head.createCell(3).setCellValue("签到方式");
            head.createCell(4).setCellValue("状态");
            for (int i = 0; i < records.size(); i++) {
                Map<String, Object> r = records.get(i);
                boolean signed = !s(r.get("time")).isBlank();
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(s(r.get("studentId")));
                row.createCell(1).setCellValue(s(r.get("name")));
                row.createCell(2).setCellValue(s(r.get("time")));
                row.createCell(3).setCellValue("null".equals(s(r.get("method"))) ? "" : s(r.get("method")));
                row.createCell(4).setCellValue(signed ? "已签到" : "未签到");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> getSignData(String classId) {
        maybeRollSignDayForClass(classId);
        List<Map<String, Object>> sessionRows = jdbcTemplate.query(
                "SELECT sign_code AS signCode, valid_until AS validUntil FROM sign_sessions WHERE class_id = ?",
                (rs, i) -> Map.of("signCode", rs.getString("signCode"), "validUntil", rs.getTimestamp("validUntil")),
                classId
        );
        List<Map<String, Object>> records = jdbcTemplate.query(
                "SELECT s.id AS studentId, s.name, r.time, r.method FROM students s LEFT JOIN sign_records r ON r.class_id = s.class_id AND r.student_id = s.id WHERE s.class_id = ? ORDER BY s.id",
                (rs, i) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("studentId", rs.getString("studentId"));
                    m.put("name", rs.getString("name"));
                    m.put("time", rs.getString("time"));
                    m.put("method", rs.getString("method") == null ? "--" : rs.getString("method"));
                    return m;
                },
                classId
        );
        Map<String, Object> out = new HashMap<>();
        if (!sessionRows.isEmpty()) out.putAll(sessionRows.get(0));
        out.put("records", records);
        return out;
    }

    private Map<String, Object> refreshSignCode(String classId) {
        maybeRollSignDayForClass(classId);
        String code = "ZKYS-" + (1000 + (int) (Math.random() * 9000));
        Timestamp validUntil = Timestamp.valueOf(LocalDateTime.now().plusMinutes(15));
        jdbcTemplate.update(
                "INSERT INTO sign_sessions (class_id, sign_code, valid_until, last_sign_day) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE sign_code = VALUES(sign_code), valid_until = VALUES(valid_until)",
                classId, code, validUntil, beijingCalendarYmd()
        );
        return Map.of("signCode", code, "validUntil", validUntil);
    }

    private void markSignRecord(String classId, String studentId, String method) {
        maybeRollSignDayForClass(classId);
        String now = LocalTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        jdbcTemplate.update(
                "INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE time = VALUES(time), method = VALUES(method)",
                classId, studentId, now, method
        );
    }

    private void undoSignRecord(String classId, String studentId) {
        maybeRollSignDayForClass(classId);
        jdbcTemplate.update(
                "INSERT INTO sign_records (class_id, student_id, time, method) VALUES (?, ?, NULL, NULL) ON DUPLICATE KEY UPDATE time = NULL, method = NULL",
                classId, studentId
        );
    }

    private void maybeRollSignDayForClass(String classId) {
        ensureSignSessionRow(classId);
        String day = beijingCalendarYmd();
        int affected = jdbcTemplate.update(
                "UPDATE sign_sessions SET last_sign_day = ? WHERE class_id = ? AND (last_sign_day IS NULL OR last_sign_day < ?)",
                day, classId, day
        );
        if (affected > 0) {
            jdbcTemplate.update("UPDATE sign_records SET time = NULL, method = NULL WHERE class_id = ?", classId);
        }
    }

    private void ensureSignSessionRow(String classId) {
        String code = "ZKYS-" + (1000 + (int) (Math.random() * 9000));
        Timestamp validUntil = Timestamp.valueOf(LocalDateTime.now().plusMinutes(15));
        jdbcTemplate.update(
                "INSERT IGNORE INTO sign_sessions (class_id, sign_code, valid_until, last_sign_day) VALUES (?, ?, ?, ?)",
                classId, code, validUntil, beijingCalendarYmd()
        );
    }

    private boolean teacherOwnsClass(String teacherId, String classId) {
        Integer ok = queryOne("SELECT 1 FROM classes WHERE id = ? AND owner_teacher_id = ? LIMIT 1", Integer.class, classId, teacherId);
        return ok != null;
    }

    private String beijingCalendarYmd() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String s(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    public record ExportFile(byte[] bytes, String filename) {}
}
