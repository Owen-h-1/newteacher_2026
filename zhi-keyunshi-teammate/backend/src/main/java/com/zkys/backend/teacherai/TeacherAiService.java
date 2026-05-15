package com.zkys.backend.teacherai;

import com.zkys.backend.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeacherAiService {
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${EXTERNAL_WARNINGS_AGENT_API:}")
    private String externalWarningsAgentApi;
    @Value("${EXTERNAL_COURSEWARE_GEN_API:}")
    private String externalCoursewareGenApi;
    @Value("${EXTERNAL_TEACHING_GEN_API:}")
    private String externalTeachingGenApi;
    @Value("${EXTERNAL_TEACHING_AGENT_API:}")
    private String externalTeachingAgentApi;
    @Value("${EXTERNAL_PPT_LECTURE_API:}")
    private String externalPptLectureApi;
    @Value("${EXTERNAL_SERVICE_API_KEY:}")
    private String externalServiceApiKey;

    public TeacherAiService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> warningsAgentPlan(UserPrincipal me, Map<String, Object> body) {
        requireEndpoint(externalWarningsAgentApi, "薄弱点智能体服务");
        return callExternal(externalWarningsAgentApi, Map.of("teacher", safeUser(me), "payload", body == null ? Map.of() : body), "薄弱点智能体服务调用失败");
    }

    public Map<String, Object> aiCoursewareGenerate(UserPrincipal me, Map<String, Object> body) {
        String topic = s(body == null ? null : body.get("topic"));
        if (topic.isBlank()) throw new IllegalArgumentException("topic 不能为空");
        requireEndpoint(externalCoursewareGenApi, "AI课件生成服务");
        return callExternal(externalCoursewareGenApi, Map.of("teacher", safeUser(me), "payload", body == null ? Map.of() : body), "AI课件生成服务调用失败");
    }

    public DownloadFile aiCoursewareDownload(UserPrincipal me, Map<String, Object> body) {
        String topic = s(body == null ? null : body.get("topic"));
        List<Map<String, Object>> slides = body != null && body.get("slides") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        if (topic.isBlank()) throw new IllegalArgumentException("topic 不能为空");
        if (slides.isEmpty()) throw new IllegalArgumentException("slides 不能为空");
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Map<String, Object> s : slides) {
                String title = safeDefault(s(s.get("title")), "课件内容");
                String desc = s(s.get("desc"));
                XSLFSlide slide = ppt.createSlide();
                slide.getBackground().setFillColor(new Color(244, 248, 255));

                XSLFTextBox t1 = slide.createTextBox();
                t1.setAnchor(new Rectangle(50, 30, 860, 60));
                t1.addNewTextParagraph().setTextAlign(TextAlign.LEFT);
                t1.setText(title);
                t1.getTextParagraphs().get(0).getTextRuns().get(0).setFontSize(30.0);
                t1.getTextParagraphs().get(0).getTextRuns().get(0).setBold(true);

                XSLFTextBox t2 = slide.createTextBox();
                t2.setAnchor(new Rectangle(70, 110, 820, 360));
                t2.setText(desc.isBlank() ? "本页暂无描述内容，可在在线编辑中补充。" : desc);
                t2.getTextParagraphs().get(0).getTextRuns().get(0).setFontSize(22.0);
            }
            ppt.write(out);
            return new DownloadFile(out.toByteArray(), safeFilename(topic) + ".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PPT 导出失败");
        }
    }

    public Map<String, Object> aiTeachingDesignGenerate(UserPrincipal me, Map<String, Object> body) {
        String topic = s(body == null ? null : body.get("topic"));
        if (topic.isBlank()) throw new IllegalArgumentException("topic 不能为空");
        requireEndpoint(externalTeachingGenApi, "AI教学设计生成服务");
        return callExternal(externalTeachingGenApi, Map.of("teacher", safeUser(me), "payload", body == null ? Map.of() : body), "AI教学设计生成服务调用失败");
    }

    public DownloadFile aiTeachingDesignDownload(Map<String, Object> body) {
        String topic = s(body == null ? null : body.get("topic"));
        if (topic.isBlank()) throw new IllegalArgumentException("topic 不能为空");
        String grade = s(body.get("grade"));
        String subject = s(body.get("subject"));
        Map<String, Object> design = body.get("design") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            p(doc, topic + " 教学设计", true);
            p(doc, grade + " · " + subject, false);
            p(doc, "", false);
            p(doc, "教学目标", true);
            for (String x : toStrings(design.get("objectives"))) p(doc, "• " + x, false);
            p(doc, "教学重难点", true);
            p(doc, "重点：" + s(design.get("keyPoints")), false);
            p(doc, "难点：" + s(design.get("difficultPoints")), false);
            p(doc, "教学方法", true);
            for (String x : toStrings(design.get("methods"))) p(doc, "• " + x, false);
            p(doc, "教学准备", true);
            for (String x : toStrings(design.get("preparations"))) p(doc, "• " + x, false);
            p(doc, "教学过程", true);
            List<Map<String, Object>> process = design.get("process") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            for (int i = 0; i < process.size(); i++) {
                Map<String, Object> x = process.get(i);
                p(doc, (i + 1) + ". " + s(x.get("title")) + "（" + s(x.get("time")) + "）：" + s(x.get("desc")), false);
            }
            p(doc, "作业布置", true);
            for (String x : toStrings(design.get("homework"))) p(doc, "• " + x, false);
            p(doc, "AI优化建议", true);
            p(doc, s(design.get("aiSuggestion")), false);
            doc.write(out);
            return new DownloadFile(out.toByteArray(), safeFilename(topic) + "-教学设计.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "教学设计导出失败");
        }
    }

    public Map<String, Object> aiTeachingDesignAgentPlan(UserPrincipal me, Map<String, Object> body) {
        requireEndpoint(externalTeachingAgentApi, "教学设计智能体服务");
        return callExternal(externalTeachingAgentApi, Map.of("teacher", safeUser(me), "payload", body == null ? Map.of() : body), "教学设计智能体服务调用失败");
    }

    public Map<String, Object> createTeacherPptAsset(UserPrincipal me, Map<String, Object> body) {
        String name = s(body == null ? null : body.get("name"));
        List<String> slides = toStrings(body == null ? null : body.get("slides")).stream().map(String::trim).filter(x -> !x.isBlank()).toList();
        if (name.isBlank()) throw new IllegalArgumentException("课件名称不能为空");
        Map<String, Object> item = new HashMap<>();
        item.put("id", "ppt-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000));
        item.put("teacherId", me.id());
        item.put("teacherUsername", me.username());
        item.put("name", name);
        item.put("uploadedAt", Instant.now().toString());
        item.put("slides", slides.isEmpty() ? List.of(name + " · 课程导入", name + " · 核心知识点", name + " · 例题讲解", name + " · 课堂总结") : slides);
        List<Map<String, Object>> source = readPptAssets();
        List<Map<String, Object>> next = new ArrayList<>();
        next.add(item);
        next.addAll(source);
        if (next.size() > 200) next = next.subList(0, 200);
        writePptAssets(next);
        return Map.of("message", "上传成功", "item", item);
    }

    public Map<String, Object> listTeacherPptAssets(UserPrincipal me) {
        List<Map<String, Object>> source = readPptAssets();
        List<Map<String, Object>> list = source.stream().filter(x -> {
            String ownerUsername = s(x.get("teacherUsername"));
            String ownerId = s(x.get("teacherId"));
            return ownerUsername.isBlank() ? ownerId.equals(me.id()) : ownerUsername.equals(me.username());
        }).toList();
        return Map.of("list", list);
    }

    public Map<String, Object> deleteTeacherPptAsset(UserPrincipal me, String idRaw) {
        String id = s(idRaw);
        if (id.isBlank()) throw new IllegalArgumentException("课件ID不能为空");
        List<Map<String, Object>> source = readPptAssets();
        Map<String, Object> target = source.stream().filter(x -> id.equals(s(x.get("id")))).findFirst().orElse(null);
        if (target == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课件不存在");
        String ownerUsername = s(target.get("teacherUsername"));
        String ownerId = s(target.get("teacherId"));
        boolean canDelete = ownerUsername.isBlank() ? ownerId.equals(me.id()) : ownerUsername.equals(me.username());
        if (!canDelete) throw new IllegalArgumentException("无权限删除该课件");
        List<Map<String, Object>> next = source.stream().filter(x -> !id.equals(s(x.get("id")))).toList();
        writePptAssets(next);
        return Map.of("message", "删除成功");
    }

    public Map<String, Object> listStudentPptAssets() {
        List<Map<String, Object>> source = readPptAssets();
        List<Map<String, Object>> list = source.stream().map(x -> mapOf(
                "id", x.get("id"),
                "name", x.get("name"),
                "uploadedAt", x.get("uploadedAt"),
                "slides", x.get("slides")
        )).toList();
        return Map.of("list", list);
    }

    public Map<String, Object> studentPptLectureGenerate(UserPrincipal me, Map<String, Object> body) {
        String pptId = s(body == null ? null : body.get("pptId"));
        if (pptId.isBlank()) throw new IllegalArgumentException("pptId 不能为空");
        List<Map<String, Object>> source = readPptAssets();
        Map<String, Object> item = source.stream().filter(x -> pptId.equals(s(x.get("id")))).findFirst().orElse(null);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课件不存在或已失效");
        requireEndpoint(externalPptLectureApi, "PPT讲解服务");
        return callExternal(externalPptLectureApi, Map.of("student", safeUser(me), "ppt", item, "payload", body == null ? Map.of() : body), "PPT讲解服务调用失败");
    }

    private void p(XWPFDocument doc, String text, boolean heading) {
        XWPFParagraph p = doc.createParagraph();
        if (heading) p.setStyle("Heading2");
        p.createRun().setText(text == null ? "" : text);
    }

    private List<Map<String, Object>> readPptAssets() {
        try {
            Path path = pptAssetsPath();
            if (!Files.exists(path)) return List.of();
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void writePptAssets(List<Map<String, Object>> list) {
        try {
            Path path = pptAssetsPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "写入课件数据失败");
        }
    }

    private Path pptAssetsPath() {
        return Path.of(System.getProperty("user.dir")).resolve("ppt-assets.json");
    }

    private void requireEndpoint(String endpoint, String name) {
        if (!s(endpoint).isBlank()) return;
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, name + " 未配置，请在 .env 设置对应 EXTERNAL_*_API");
    }

    private Map<String, Object> callExternal(String endpoint, Map<String, Object> payload, String failMsg) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (!s(externalServiceApiKey).isBlank()) builder.header("Authorization", "Bearer " + externalServiceApiKey);
            HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> data = s(res.body()).isBlank() ? Map.of() : objectMapper.readValue(res.body(), Map.class);
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, s(data.getOrDefault("message", failMsg)));
            }
            return data;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, s(e.getMessage()).isBlank() ? failMsg : e.getMessage());
        }
    }

    private Map<String, Object> safeUser(UserPrincipal me) {
        return mapOf("id", me.id(), "username", me.username(), "role", me.role(), "email", me.email(), "name", me.name());
    }

    private String safeFilename(String topic) {
        String name = s(topic).replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.isBlank() ? "download" : name;
    }

    private String safeDefault(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private List<String> toStrings(Object v) {
        if (!(v instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object x : list) out.add(String.valueOf(x));
        return out;
    }

    private String s(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    public record DownloadFile(byte[] bytes, String filename, String contentType) {}
}
