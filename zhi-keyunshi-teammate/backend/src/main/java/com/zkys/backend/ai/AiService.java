package com.zkys.backend.ai;

import com.zkys.backend.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    /** 结构化选择题卷（仅内存，提交批改后移除） */
    private final ConcurrentHashMap<String, QuestionPaper> selftestPapers = new ConcurrentHashMap<>();
    /** 结构化题目的元信息，供错题入库使用 */
    private final ConcurrentHashMap<String, Map<String, String>> selftestPaperMeta = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${OLLAMA_API_BASE:http://127.0.0.1:11434}")
    private String ollamaApiBase;
    @Value("${OLLAMA_MODEL:qwen2.5:7b}")
    private String ollamaModel;
    @Value("${EXTERNAL_DIGITAL_HUMAN_API:}")
    private String externalDigitalHumanApi;
    @Value("${EXTERNAL_SERVICE_API_KEY:}")
    private String externalServiceApiKey;
    @Value("${COZE_API_BASE:https://api.coze.cn}")
    private String cozeApiBase;
    @Value("${COZE_PAT:}")
    private String cozePat;
    @Value("${COZE_GENERATE_BOT_ID:}")
    private String cozeGenerateBotId;
    @Value("${COZE_GRADE_BOT_ID:}")
    private String cozeGradeBotId;

    public AiService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> llmStatus() {
        boolean ollamaAvailable = checkOllamaAvailable();
        return Map.of(
                "providerOrder", List.of("ollama"),
                "llmProvider", "ollama",
                "ollamaModel", ollamaModel,
                "digitalHuman", Map.of(
                        "enabled", !s(externalDigitalHumanApi).isBlank(),
                        "mode", s(externalDigitalHumanApi).isBlank() ? "unconfigured" : "external-service",
                        "endpoint", "/api/digital-human/chat",
                        "note", s(externalDigitalHumanApi).isBlank() ? "数字人服务未配置。" : "数字人服务已配置外部接口。"
                ),
                "status", Map.of("ollama", Map.of("configured", true, "available", ollamaAvailable))
        );
    }

    public Map<String, Object> llmChat(String message) {
        String msg = s(message);
        if (msg.isBlank()) throw new IllegalArgumentException("提问内容不能为空");
        try {
            String endpoint = trimSlash(ollamaApiBase) + "/api/chat";
            Map<String, Object> payload = Map.of(
                    "model", ollamaModel,
                    "stream", false,
                    "options", Map.of(
                            "temperature", 0.7,
                            "top_p", 0.9,
                            "repeat_penalty", 1.05
                    ),
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是小学生学习助手，请直接回答用户问题，简明清晰。"),
                            Map.of("role", "user", "content", msg)
                    )
            );
            Map<String, Object> data = postJson(endpoint, payload);
            Object content = ((Map<String, Object>) data.getOrDefault("message", Map.of())).get("content");
            String answer = s(content);
            if (answer.isBlank()) throw new RuntimeException("模型服务返回内容为空");
            return Map.of("answer", answer, "provider", "ollama");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, s(e.getMessage()).isBlank() ? "模型服务调用失败" : e.getMessage());
        }
    }

    public Map<String, Object> digitalHumanChat(UserPrincipal me, Map<String, Object> body) {
        String message = s(body.get("message"));
        if (message.isBlank()) throw new IllegalArgumentException("提问内容不能为空");
        if (s(externalDigitalHumanApi).isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "数字人服务 未配置，请在 .env 设置对应 EXTERNAL_*_API");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "user", Map.of("id", me.id(), "username", me.username(), "role", me.role(), "email", me.email(), "name", me.name()),
                    "message", message,
                    "sessionId", s(body.get("sessionId")),
                    "voiceId", s(body.get("voiceId")),
                    "avatarId", s(body.get("avatarId"))
            );
            return postJson(externalDigitalHumanApi, payload);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, s(e.getMessage()).isBlank() ? "数字人服务调用失败" : e.getMessage());
        }
    }

    public Map<String, Object> generateSelfTestQuestions(UserPrincipal me, Map<String, Object> body) {
        if (body != null && useStructuredForm(body)) {
            String subject = s(body.get("subject"));
            String grade = s(body.get("grade"));
            if (grade.isBlank()) {
                grade = "三年级";
            }
            String questionType = s(body.get("questionType"));
            String knowledgePoint = s(body.get("knowledgePoint"));
            if (knowledgePoint.isBlank()) {
                knowledgePoint = s(body.get("knowledge"));
            }
            String difficulty = s(body.get("difficulty"));
            if (difficulty.isBlank()) {
                difficulty = "基础";
            }
            int count = parseCount(body.get("count"), 3);
            QuestionPaper paper = buildStructuredPaper(subject, grade, questionType, knowledgePoint, difficulty, count, me.id());
            selftestPapers.put(paper.id, paper);
            selftestPaperMeta.put(
                    paper.id,
                    Map.of(
                            "subject", s(subject),
                            "grade", s(grade),
                            "knowledgePoint", s(knowledgePoint)
                    )
            );
            Map<String, Object> out = new HashMap<>();
            out.put("mode", "structured");
            out.put("paperId", paper.id);
            out.put("questions", paper.toClientQuestions());
            out.put("questionKind", paper.primaryKind());
            out.put("requestedSubject", subject);
            out.put("requestedGrade", grade);
            out.put("requestedKnowledgePoint", knowledgePoint);
            out.put("provider", paper.sourceProvider);
            out.put("botId", s(cozeGenerateBotId));
            return out;
        }

        String demand = s(body != null ? body.get("demand") : "");
        if (demand.isBlank()) {
            throw new IllegalArgumentException("请先选择出题条件，或填写练习需求");
        }
        int n = extractQuestionCount(demand);
        String prompt = buildGeneratePrompt(demand);
        String answer = askCozeBot(cozeGenerateBotId, me.id(), prompt);
        if (!isAcceptableGeneratedQuestions(answer, demand)) {
            if (isTwoDigitMultiplicationVerticalDemand(demand)) {
                answer = buildLocalTwoDigitMultiplicationVertical(n);
                log.warn("Coze output invalid or echoed user text; using local math fallback, n={}", n);
                return Map.of("answer", answer, "provider", "local-math-fallback", "botId", s(cozeGenerateBotId));
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "智能体未按格式出题（可能复述了需求）。请重试，或换一种描述；乘法竖式也可由系统自动出题。");
        }
        return Map.of("answer", answer, "provider", "coze", "botId", s(cozeGenerateBotId));
    }

    /**
     * 只要选了科目 + 题型即走结构化出题；题量缺省或传 0 时在下方用 parseCount(..., 3) 兜底。
     * 注意：前端曾误传 count=0（undefined 被写成 0），不能因此拒绝结构化分支。
     */
    private boolean useStructuredForm(Map<String, Object> body) {
        String subject = s(body.get("subject"));
        String qt = s(body.get("questionType"));
        return !subject.isBlank() && !qt.isBlank();
    }

    private int parseCount(Object v, int def) {
        if (v == null) {
            return def;
        }
        try {
            if (v instanceof Number n) {
                return Math.min(20, Math.max(def == 0 ? 0 : 1, n.intValue()));
            }
            int n = Integer.parseInt(s(v));
            if (def == 0) {
                return Math.min(20, Math.max(0, n));
            }
            return Math.min(20, Math.max(1, n));
        } catch (Exception e) {
            return def;
        }
    }

    private QuestionPaper buildStructuredPaper(
            String subject, String grade, String questionType, String knowledgePoint, String difficulty, int count, String userId) {
        int n = Math.min(20, Math.max(1, count));
        try {
            if (questionType.contains("选择")) {
                return buildPaperFromCozeMcqJson(subject, grade, knowledgePoint, difficulty, n, userId);
            }
            if (questionType.contains("填空")) {
                return buildPaperFromCozeFillJson(subject, grade, knowledgePoint, difficulty, n, userId);
            }
            if (questionType.contains("解答")) {
                return buildPaperFromCozeEssayJson(subject, grade, knowledgePoint, difficulty, n, userId);
            }
            throw new IllegalArgumentException("不支持的题型，请选择选择题、填空题或解答题");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("coze structured json failed, subject={}, type={}", subject, questionType, e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "智能体出题失败，请稍后重试或稍减题量。"
                            + (s(e.getMessage()).isBlank() ? "" : "（" + e.getMessage() + "）"));
        }
    }

    /**
     * 学生端下拉框参数 — 作为发给大模型的「唯一有效需求」，尽量抵消 Bot 默认「只出数学」的人设。
     */
    private String structuredPromptHeader(
            String subject, String grade, String knowledgePoint, String difficulty, int count, String questionLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║ 学生端出题任务（本次用户消息中唯一有效需求，优先级最高）           ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n");
        sb.append("以下六项由学生在「小学自主练习」页面选择，你必须按此生成题目；\n");
        sb.append("禁止忽略、禁止改写成数学；禁止用「数学应用题套壳」冒充其他学科。\n\n");
        sb.append("【1】学科（meta.subject 必须逐字一致）：").append(subject).append("\n");
        sb.append("【2】年级（meta.grade 须一致）：").append(grade).append("（或「小学").append(grade).append("」）\n");
        sb.append("【3】题型：").append(questionLabel).append("\n");
        sb.append("【4】难度：").append(difficulty).append("\n");
        sb.append("【5】题量：共 ").append(count).append(" 道题\n\n");
        if (!knowledgePoint.isBlank()) {
            sb.append("【6】知识点（必须围绕该知识点命题）：").append(knowledgePoint).append("\n\n");
            sb.append("若题目与该知识点无关，视为不合格，必须重写。\n\n");
        }
        if (subject.contains("数学")) {
            sb.append("当前学科为「数学」：可出算术、几何、应用题等符合「")
                    .append(grade)
                    .append("」学情的数学内容。\n\n");
        } else {
            sb.append("当前学科为「")
                    .append(subject)
                    .append("」——不是数学。你必须出该学科的真实题目。\n");
            sb.append("【严禁输出以下小学数学类内容作为本卷主体】\n");
            sb.append("口算、竖式、脱式、递等式、简便计算；加减乘除为主的算式题；\n");
            sb.append("分数/小数四则与混合运算；解方程、列方程；\n");
            sb.append("以「买东西/分苹果/路程」等数量关系为主的应用题；\n");
            sb.append("图形周长、面积、体积的数值计算题；钟表与人民币纯计算等。\n");
            sb.append("若题干读起来像数学作业，则视为错误，请改为「")
                    .append(subject)
                    .append("」学科典型考查（如语文：字词句篇、古诗、阅读；英语：词句与交际；科学：观察与现象；美术：造型与色彩；思想政治：行为规范与国情常识等）。\n\n");
        }
        sb.append("【关于你的系统人设】若你被配置为「数学/口算」类助手，本次任务中必须搁置该人设，只服从上方五项参数。\n\n");
        return sb.toString();
    }

    private JsonNode cozeStructuredJsonRoot(String prompt, String userId, String subject, String grade) throws Exception {
        String currentPrompt = prompt;
        for (int attempt = 0; attempt < 2; attempt++) {
            String raw = askCozeBot(cozeGenerateBotId, userId, currentPrompt);
            String normalized = normalizeStructuredJsonPayload(raw);
            JsonNode root;
            try {
                root = parseStructuredJsonRoot(normalized);
            } catch (Exception parseEx) {
                if (attempt == 0) {
                    log.warn("structured JSON parse failed, will retry Coze: {}", parseEx.getMessage());
                    currentPrompt =
                            prompt
                                    + "\n【上次】JSON 无法解析。请只输出一个合法 JSON 对象，含 meta 与 questions，不要用代码块。";
                    continue;
                }
                throw new IllegalArgumentException("JSON 解析失败：" + parseEx.getMessage());
            }
            try {
                assertMetaMatches(root, subject, grade);
                return root;
            } catch (IllegalArgumentException metaEx) {
                if (attempt == 0) {
                    currentPrompt =
                            prompt
                                    + "\n【上次未通过校验】"
                                    + metaEx.getMessage()
                                    + "\n请重新输出：meta.subject 必须与「"
                                    + subject
                                    + "」一致；meta.grade 必须与「"
                                    + grade
                                    + "」一致（或「小学"
                                    + grade
                                    + "」）。\n"
                                    + "若学科应为「"
                                    + subject
                                    + "」却出了数学口算/竖式/应用题，请把 questions 全部改成该学科真实题目后再输出 JSON。";
                    continue;
                }
                throw metaEx;
            }
        }
        throw new IllegalArgumentException("智能体多次未返回符合学科、年级要求的 JSON");
    }

    /**
     * 智能体常在回复前加说明文字，或把 JSON 放在 markdown 代码块里；这里尽量还原可解析的 JSON 字符串。
     */
    private String normalizeStructuredJsonPayload(String raw) {
        String fromFence = extractMarkdownJsonBlock(raw);
        if (!s(fromFence).isBlank()) {
            return fromFence;
        }
        return raw;
    }

    /**
     * 仅当代码块里明显是试卷 JSON（含 questions）时才采用；否则返回空串，让外层使用完整正文。
     * 若误把「分析说明」等最长 ``` 块当作正文，会丢掉后面的 JSON，导致全科无法出题。
     */
    private String extractMarkdownJsonBlock(String raw) {
        String t = s(raw);
        if (t.isEmpty()) {
            return "";
        }
        Pattern p = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(t);
        String longestWithQuestions = "";
        while (m.find()) {
            String block = s(m.group(1));
            if (block.contains("\"questions\"") || block.contains("'questions'")) {
                if (block.length() > longestWithQuestions.length()) {
                    longestWithQuestions = block;
                }
            }
        }
        return longestWithQuestions;
    }

    private JsonNode parseStructuredJsonRoot(String normalized) throws Exception {
        String stripped = stripJsonFence(normalized);
        try {
            return objectMapper.readTree(stripped);
        } catch (Exception first) {
            try {
                return objectMapper.readTree(normalized.trim());
            } catch (Exception ignored) {
                String fallback = tryExtractJsonObjectAroundKey(normalized, "questions");
                if (fallback != null) {
                    return objectMapper.readTree(fallback);
                }
                throw first;
            }
        }
    }

    /**
     * 当正文前有「好的，下面是题目：」等前缀时，从第一个含 key 的 JSON 对象起截取（括号配对，忽略字符串内括号较难，仅作补救）。
     */
    private String tryExtractJsonObjectAroundKey(String raw, String key) {
        String t = s(raw);
        int keyPos = t.indexOf("\"" + key + "\"");
        if (keyPos < 0) {
            keyPos = t.indexOf("'" + key + "'");
        }
        if (keyPos < 0) {
            return null;
        }
        int start = t.lastIndexOf('{', keyPos);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return t.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private void assertMetaMatches(JsonNode root, String subject, String grade) {
        JsonNode meta = root.get("meta");
        if (meta == null || !meta.isObject()) {
            throw new IllegalArgumentException("JSON 必须包含 meta 对象（subject、grade）");
        }
        JsonNode sNode = meta.get("subject");
        JsonNode gNode = meta.get("grade");
        if (sNode == null || !sNode.isTextual() || gNode == null || !gNode.isTextual()) {
            throw new IllegalArgumentException("meta 须为文本字段 subject、grade");
        }
        String ms = s(sNode.asText());
        String mg = s(gNode.asText());
        if (!subjectsAliasMatch(ms, subject)) {
            throw new IllegalArgumentException("meta.subject 须为「" + subject + "」，实际为「" + ms + "」");
        }
        if (!gradesAliasMatch(mg, grade)) {
            throw new IllegalArgumentException("meta.grade 须与「" + grade + "」一致，实际为「" + mg + "」");
        }
    }

    private boolean subjectsAliasMatch(String returned, String expected) {
        String r = s(returned).replaceAll("\\s+", "");
        String e = s(expected).replaceAll("\\s+", "");
        if (r.isEmpty() || e.isEmpty()) {
            return false;
        }
        if (r.equals(e)) {
            return true;
        }
        if (isPoliticsSubject(r) && isPoliticsSubject(e)) {
            return true;
        }
        return r.contains(e) || e.contains(r);
    }

    private boolean isPoliticsSubject(String x) {
        return x.contains("思想政治") || x.contains("道德与法治") || x.contains("道法");
    }

    private boolean gradesAliasMatch(String returned, String expected) {
        String r = simplifyGradeLabel(returned);
        String e = simplifyGradeLabel(expected);
        if (r.isEmpty() || e.isEmpty()) {
            return false;
        }
        if (r.equals(e)) {
            return true;
        }
        return r.contains(e) || e.contains(r);
    }

    /** 「三年级」「小学三年级」等视为同一学情标签。 */
    private String simplifyGradeLabel(String g) {
        String x = s(g).replaceAll("\\s+", "");
        if (x.startsWith("小学")) {
            x = x.substring(2);
        }
        return x;
    }

    private List<QuestionItem> parseMcqItemsFromArray(JsonNode arr, int count) {
        List<QuestionItem> items = new ArrayList<>();
        int seq = 1;
        for (JsonNode q : arr) {
            if (items.size() >= count) {
                break;
            }
            JsonNode stemNode = q.get("stem");
            if (stemNode == null || stemNode.isNull()) {
                continue;
            }
            String stem = s(stemNode.asText());
            JsonNode ckNode = q.get("correctKey");
            String correctKey = ckNode == null || ckNode.isNull() ? "" : s(ckNode.asText()).toUpperCase();
            JsonNode opts = q.get("options");
            List<Map<String, String>> options = new ArrayList<>();
            if (opts != null && opts.isArray()) {
                for (JsonNode o : opts) {
                    if (o == null || !o.isObject()) {
                        continue;
                    }
                    Map<String, String> m = new HashMap<>();
                    JsonNode k = o.get("key");
                    JsonNode t = o.get("text");
                    m.put("key", k == null || k.isNull() ? "" : s(k.asText()).toUpperCase());
                    m.put("text", t == null || t.isNull() ? "" : s(t.asText()));
                    if (!m.get("key").isBlank()) {
                        options.add(m);
                    }
                }
            }
            if (stem.isBlank() || options.size() < 3) {
                continue;
            }
            List<Map<String, String>> three = new ArrayList<>(options.subList(0, 3));
            int no = q.has("no") && q.get("no").isNumber() ? q.get("no").asInt() : seq;
            if (!List.of("A", "B", "C").contains(correctKey)) {
                correctKey = "A";
            }
            boolean keyOk = false;
            for (Map<String, String> opt : three) {
                if (correctKey.equalsIgnoreCase(s(opt.get("key")))) {
                    keyOk = true;
                    break;
                }
            }
            if (!keyOk) {
                correctKey = s(three.get(0).get("key"));
            }
            items.add(new QuestionItem(no, "mcq", stem, three, correctKey, "", ""));
            seq++;
        }
        return items;
    }

    /**
     * 各学科（数学、语文、英语、思想政治等）选择题均由出题智能体生成 JSON，再解析为试卷。
     */
    private QuestionPaper buildPaperFromCozeMcqJson(
            String subject, String grade, String knowledgePoint, String difficulty, int count, String userId) throws Exception {
        log.info("structured selftest MCQ: subject={}, grade={}, difficulty={}, count={}", subject, grade, difficulty, count);
        String header = structuredPromptHeader(subject, grade, knowledgePoint, difficulty, count, "选择题");
        String core =
                "【重要】只输出一个 JSON 对象，不要使用 markdown 代码块，不要任何解释文字。\n"
                        + "格式：{\"meta\":{\"subject\":\"须与上方指定学科一致\",\"grade\":\"须与上方指定年级一致\"},"
                        + "\"questions\":[{\"no\":1,\"stem\":\"题干\",\"options\":[{\"key\":\"A\",\"text\":\"选项A\"},"
                        + "{\"key\":\"B\",\"text\":\"选项B\"},{\"key\":\"C\",\"text\":\"选项C\"}],\"correctKey\":\"A\"}]}\n"
                        + "你必须设置 meta.subject 为「"
                        + subject
                        + "」，meta.grade 为「"
                        + grade
                        + "」或「小学"
                        + grade
                        + "」。\n"
                        + "要求：共 "
                        + count
                        + " 道「"
                        + grade
                        + "」「"
                        + subject
                        + "」选择题，难度「"
                        + difficulty
                        + "」，内容需符合小学阶段该学科教学要求。\n"
                        + (knowledgePoint.isBlank() ? "" : ("必须重点考查知识点：「" + knowledgePoint + "」。\n"))
                        + "题干与选项必须与所选学科「"
                        + subject
                        + "」一致，不得答非所问。\n"
                        + "所有题目必须由你根据知识点原创命制，禁止使用随机数程序、禁止使用与本学科无关的占位题。\n"
                        + "每题 options 必须恰好 3 个，key 分别为 A、B、C，correctKey 必须是 A/B/C 之一且指向正确答案。\n"
                        + "最后确认：meta.subject 与每道题 stem 必须体现学科「"
                        + subject
                        + "」；若该学科不是数学，则不得整套题为加减乘除口算或数学应用题为主。\n";
        String prompt = header + core;
        JsonNode root = cozeStructuredJsonRoot(prompt, userId, subject, grade);
        JsonNode arr = root.get("questions");
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            throw new IllegalArgumentException("JSON 缺少 questions");
        }
        List<QuestionItem> items = parseMcqItemsFromArray(arr, count);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("未能解析出有效选择题");
        }
        return new QuestionPaper(UUID.randomUUID().toString(), userId, items);
    }

    /**
     * 填空题：JSON 含 stem、correctAnswer（可用 / 或 | 分隔多个可接受答案）。
     */
    private QuestionPaper buildPaperFromCozeFillJson(
            String subject, String grade, String knowledgePoint, String difficulty, int count, String userId) throws Exception {
        log.info("structured selftest FILL: subject={}, grade={}, difficulty={}, count={}", subject, grade, difficulty, count);
        String header = structuredPromptHeader(subject, grade, knowledgePoint, difficulty, count, "填空题");
        String core =
                "【重要】只输出一个 JSON 对象，不要使用 markdown 代码块，不要任何解释文字。\n"
                        + "格式：{\"meta\":{\"subject\":\"须与指定学科一致\",\"grade\":\"须与指定年级一致\"},"
                        + "\"questions\":[{\"no\":1,\"stem\":\"题干（可含____或括号表示填空）\",\"correctAnswer\":\"参考答案\"}]}\n"
                        + "你必须设置 meta.subject 为「"
                        + subject
                        + "」，meta.grade 为「"
                        + grade
                        + "」或「小学"
                        + grade
                        + "」。\n"
                        + "要求：共 "
                        + count
                        + " 道「"
                        + grade
                        + "」「"
                        + subject
                        + "」填空题，难度「"
                        + difficulty
                        + "」，内容需符合小学阶段该学科教学要求。\n"
                        + (knowledgePoint.isBlank() ? "" : ("必须重点考查知识点：「" + knowledgePoint + "」。\n"))
                        + "correctAnswer 用于自动判题；若允许多个正确答案，用英文斜杠 / 分隔，例如 3/三 。\n"
                        + "题干必须与学科「"
                        + subject
                        + "」一致，原创命制，禁止无关占位题。\n"
                        + "最后确认：meta.subject 与每道 stem 必须体现「"
                        + subject
                        + "」；非数学学科不得出纯四则运算填空或典型数学应用题填空。\n";
        String prompt = header + core;
        JsonNode root = cozeStructuredJsonRoot(prompt, userId, subject, grade);
        JsonNode arr = root.get("questions");
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            throw new IllegalArgumentException("JSON 缺少 questions");
        }
        List<QuestionItem> items = new ArrayList<>();
        int seq = 1;
        for (JsonNode q : arr) {
            if (items.size() >= count) {
                break;
            }
            JsonNode stemNode = q.get("stem");
            JsonNode ansNode = q.get("correctAnswer");
            if (stemNode == null || stemNode.isNull() || ansNode == null || ansNode.isNull()) {
                continue;
            }
            String stem = s(stemNode.asText());
            String correctAnswer = s(ansNode.asText());
            if (stem.isBlank() || correctAnswer.isBlank()) {
                continue;
            }
            int no = q.has("no") && q.get("no").isNumber() ? q.get("no").asInt() : seq;
            items.add(new QuestionItem(no, "fill", stem, List.of(), "", correctAnswer, ""));
            seq++;
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("未能解析出有效填空题");
        }
        return new QuestionPaper(UUID.randomUUID().toString(), userId, items);
    }

    /**
     * 解答题：JSON 含 stem、referenceHint（教师判分参考，可为空字符串）。
     */
    private QuestionPaper buildPaperFromCozeEssayJson(
            String subject, String grade, String knowledgePoint, String difficulty, int count, String userId) throws Exception {
        log.info("structured selftest ESSAY: subject={}, grade={}, difficulty={}, count={}", subject, grade, difficulty, count);
        String header = structuredPromptHeader(subject, grade, knowledgePoint, difficulty, count, "解答题");
        String core =
                "【重要】只输出一个 JSON 对象，不要使用 markdown 代码块，不要任何解释文字。\n"
                        + "格式：{\"meta\":{\"subject\":\"须与指定学科一致\",\"grade\":\"须与指定年级一致\"},"
                        + "\"questions\":[{\"no\":1,\"stem\":\"题干\",\"referenceHint\":\"参考答案要点或评分要点\"}]}\n"
                        + "你必须设置 meta.subject 为「"
                        + subject
                        + "」，meta.grade 为「"
                        + grade
                        + "」或「小学"
                        + grade
                        + "」。\n"
                        + "要求：共 "
                        + count
                        + " 道「"
                        + grade
                        + "」「"
                        + subject
                        + "」解答题（主观题），难度「"
                        + difficulty
                        + "」，内容需符合小学阶段该学科教学要求。\n"
                        + (knowledgePoint.isBlank() ? "" : ("必须重点考查知识点：「" + knowledgePoint + "」。\n"))
                        + "题干应明确、可作答；referenceHint 供批改参考，可写关键步骤或要点。\n"
                        + "题干必须与学科「"
                        + subject
                        + "」一致，原创命制。\n"
                        + "最后确认：meta.subject 与每道 stem 必须体现「"
                        + subject
                        + "」；非数学学科不得要求长篇解数学应用题或纯计算过程。\n";
        String prompt = header + core;
        JsonNode root = cozeStructuredJsonRoot(prompt, userId, subject, grade);
        JsonNode arr = root.get("questions");
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            throw new IllegalArgumentException("JSON 缺少 questions");
        }
        List<QuestionItem> items = new ArrayList<>();
        int seq = 1;
        for (JsonNode q : arr) {
            if (items.size() >= count) {
                break;
            }
            JsonNode stemNode = q.get("stem");
            if (stemNode == null || stemNode.isNull()) {
                continue;
            }
            String stem = s(stemNode.asText());
            if (stem.isBlank()) {
                continue;
            }
            JsonNode hintNode = q.get("referenceHint");
            String referenceHint = hintNode == null || hintNode.isNull() ? "" : s(hintNode.asText());
            int no = q.has("no") && q.get("no").isNumber() ? q.get("no").asInt() : seq;
            items.add(new QuestionItem(no, "essay", stem, List.of(), "", "", referenceHint));
            seq++;
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("未能解析出有效解答题");
        }
        return new QuestionPaper(UUID.randomUUID().toString(), userId, items);
    }

    private String stripJsonFence(String raw) {
        String t = s(raw);
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    private QuestionItem buildOneMathMcq(Random r, String grade, String difficulty, int no) {
        int correct;
        String stem;
        if (grade.contains("一")) {
            if (r.nextBoolean()) {
                int a = 1 + r.nextInt(5);
                int b = 1 + r.nextInt(5);
                correct = a + b;
                stem = a + "+" + b + "=?";
            } else {
                int a = 1 + r.nextInt(5);
                int b = 1 + r.nextInt(5);
                correct = a * b;
                stem = a + "×" + b + "=?";
            }
        } else if (grade.contains("二")) {
            int op = r.nextInt(3);
            int a = 2 + r.nextInt(18);
            int b = 2 + r.nextInt(18);
            if (op == 0) {
                correct = a + b;
                stem = a + "+" + b + "=?";
            } else if (op == 1) {
                if (a < b) {
                    int t = a;
                    a = b;
                    b = t;
                }
                correct = a - b;
                stem = a + "-" + b + "=?";
            } else {
                a = 2 + r.nextInt(9);
                b = 2 + r.nextInt(9);
                correct = a * b;
                stem = a + "×" + b + "=?";
            }
        } else {
            int a = "挑战".equals(difficulty) ? (20 + r.nextInt(80)) : (10 + r.nextInt(90));
            int b = "挑战".equals(difficulty) ? (20 + r.nextInt(80)) : (10 + r.nextInt(90));
            correct = a * b;
            stem = a + "×" + b + "=?";
        }
        String correctStr = String.valueOf(correct);
        List<Map<String, String>> options = buildThreeOptions(r, correct, correctStr);
        String correctKey = findCorrectKey(options, correctStr);
        return new QuestionItem(no, "mcq", stem, options, correctKey, "", "");
    }

    private List<Map<String, String>> buildThreeOptions(Random r, int correct, String correctStr) {
        int w1 = correct + 1 + r.nextInt(25);
        if (w1 == correct) {
            w1++;
        }
        int w2 = correct - 1 - r.nextInt(25);
        if (w2 < 0) {
            w2 = correct + 2;
        }
        if (w2 == correct) {
            w2++;
        }
        if (w2 == w1) {
            w2 = w1 + 1;
        }
        List<String> vals = new ArrayList<>(List.of(correctStr, String.valueOf(w1), String.valueOf(w2)));
        Collections.shuffle(vals, r);
        String[] keys = {"A", "B", "C"};
        List<Map<String, String>> opts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, String> m = new HashMap<>();
            m.put("key", keys[i]);
            m.put("text", vals.get(i));
            opts.add(m);
        }
        return opts;
    }

    private String findCorrectKey(List<Map<String, String>> options, String correctStr) {
        for (Map<String, String> o : options) {
            if (correctStr.equals(s(o.get("text")))) {
                return s(o.get("key"));
            }
        }
        return "A";
    }

    /** 是否像「两位数乘法竖式」类练习（可用于本地兜底出题）。 */
    private boolean isTwoDigitMultiplicationVerticalDemand(String demand) {
        String d = s(demand);
        boolean hasMul = d.contains("乘") || d.contains("×") || d.contains("*") || d.contains("乘法") || d.contains("乘以");
        boolean twoDigit = d.contains("两位") || d.contains("2位") || d.contains("两位数");
        boolean vertical = d.contains("竖式") || d.contains("列竖式");
        return hasMul && (twoDigit || vertical || d.contains("小学"));
    }

    /**
     * 判断智能体输出是否合格：必须带题号，且不能像单纯复述用户需求。
     */
    private boolean isAcceptableGeneratedQuestions(String answer, String demand) {
        String a = s(answer);
        if (a.isBlank()) {
            return false;
        }
        if (isEchoOfDemand(a, demand)) {
            return false;
        }
        return hasQuestionNumbering(a);
    }

    private boolean isEchoOfDemand(String answer, String demand) {
        String a = normalizeForCompare(answer);
        String d = normalizeForCompare(demand);
        if (d.length() < 6) {
            return false;
        }
        if (a.equals(d)) {
            return true;
        }
        return a.length() <= d.length() + 8 && a.contains(d);
    }

    private String normalizeForCompare(String text) {
        return s(text).replaceAll("\\s+", "");
    }

    private boolean hasQuestionNumbering(String text) {
        return Pattern.compile("第[\\d一二三四五六七八九十百千]+题").matcher(s(text)).find();
    }

    /** 本地生成两位数乘法竖式题（不依赖智能体）。 */
    private String buildLocalTwoDigitMultiplicationVertical(int n) {
        int count = Math.min(20, Math.max(1, n));
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        sb.append("（以下为系统自动生成的练习题，请用竖式计算）\n\n");
        for (int i = 1; i <= count; i++) {
            int x = 10 + r.nextInt(90);
            int y = 10 + r.nextInt(90);
            sb.append("第").append(i).append("题  用竖式计算：").append(x).append(" × ").append(y).append(" = ?\n\n");
        }
        return sb.toString().trim();
    }

    /** 从用户描述里解析题目数量，默认 5 道。 */
    private int extractQuestionCount(String demand) {
        String d = s(demand);
        if (d.isEmpty()) {
            return 5;
        }
        Matcher m1 = Pattern.compile("(\\d+)\\s*[道题]").matcher(d);
        if (m1.find()) {
            try {
                int n = Integer.parseInt(m1.group(1));
                return Math.min(20, Math.max(1, n));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if ((d.contains("两") || d.contains("二")) && (d.contains("道") || d.contains("题"))) {
            return 2;
        }
        if (d.contains("三") && (d.contains("道") || d.contains("题"))) {
            return 3;
        }
        if (d.contains("四") && (d.contains("道") || d.contains("题"))) {
            return 4;
        }
        if (d.contains("五") && (d.contains("道") || d.contains("题"))) {
            return 5;
        }
        if (d.contains("六") && (d.contains("道") || d.contains("题"))) {
            return 6;
        }
        if (d.contains("十") && (d.contains("道") || d.contains("题"))) {
            return 10;
        }
        return 5;
    }

    /**
     * 强约束出题格式，避免智能体只输出「方法介绍」类开放式问答。
     */
    private String buildGeneratePrompt(String demand) {
        int n = extractQuestionCount(demand);
        return "【角色】小学练习出题助手。只输出题目正文，禁止输出长篇讲解、方法总结、知识科普、背景介绍。\n\n"
                + "【用户需求】\n"
                + demand
                + "\n\n"
                + "【硬性要求】\n"
                + "1. 必须输出 "
                + n
                + " 道题，每题以「第1题」「第2题」…「第"
                + n
                + "题」开头编号。\n"
                + "2. 每题必须是可计算或可作答的具体题（如竖式计算、填空、应用题），只写题干，不写解析。\n"
                + "3. 禁止复述用户输入、禁止只输出需求原文；第一题的第一行必须以「第1题」开头并给出具体算式或题干。\n"
                + "4. 禁止出「有哪些」「为什么」「请说明」「请介绍」「技巧有哪些」等开放式问答类题目。\n"
                + "5. 不要输出「参考答案」「解析」「小结」。\n"
                + "6. 每题之间空一行。\n";
    }

    public Map<String, Object> gradeSelfTestAnswers(UserPrincipal me, Map<String, Object> body) {
        String paperId = s(body != null ? body.get("paperId") : "");
        if (!paperId.isBlank()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> selections = body != null ? (Map<String, Object>) body.get("selections") : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> answers = body != null ? (Map<String, Object>) body.get("answers") : null;
            if (selections == null) {
                selections = new HashMap<>();
            }
            if (answers == null) {
                answers = new HashMap<>();
            }
            return gradeStructuredPaper(me, paperId, selections, answers);
        }

        String question = s(body != null ? body.get("question") : "");
        String studentAnswer = s(body != null ? body.get("studentAnswer") : "");
        if (question.isBlank()) {
            throw new IllegalArgumentException("题目不能为空");
        }
        if (studentAnswer.isBlank()) {
            throw new IllegalArgumentException("作答内容不能为空");
        }
        String prompt = "请根据以下题目和学生答案完成批改，按“总评 + 分点建议 + 参考答案”输出。\n"
                + "你当前任务是【批改】而不是出题。禁止输出 JSON、禁止输出 questions/meta 字段。\n"
                + "【题目】\n" + question + "\n\n"
                + "【学生答案】\n" + studentAnswer;
        String answer = askCozeBot(cozeGradeBotId, me.id(), prompt, true);
        return Map.of("answer", answer, "provider", "coze", "botId", s(cozeGradeBotId));
    }

    private Map<String, Object> gradeStructuredPaper(
            UserPrincipal me, String paperId, Map<String, Object> selections, Map<String, Object> textAnswers) {
        QuestionPaper paper = selftestPapers.remove(paperId);
        Map<String, String> paperMeta = selftestPaperMeta.remove(paperId);
        if (paper == null) {
            throw new IllegalArgumentException("题目已失效，请重新生成");
        }
        for (QuestionItem q : paper.items) {
            String kind = normalizeKind(q.kind);
            if ("mcq".equals(kind)) {
                String sel = pickSelection(selections, q.no);
                if (sel.isBlank()) {
                    throw new IllegalArgumentException("请先完成每道题的作答");
                }
            } else if ("fill".equals(kind)) {
                String ans = pickTextAnswer(textAnswers, q.no);
                if (ans.isBlank()) {
                    throw new IllegalArgumentException("请先填写每道题的答案");
                }
            } else if ("essay".equals(kind)) {
                String ans = pickTextAnswer(textAnswers, q.no);
                if (ans.isBlank()) {
                    throw new IllegalArgumentException("请先完成每道题的作答");
                }
            }
        }

        int objectiveCorrect = 0;
        int objectiveTotal = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        List<Map<String, Object>> wrongItems = new ArrayList<>();
        StringBuilder fact = new StringBuilder();
        List<String> essayBlocks = new ArrayList<>();

        for (QuestionItem q : paper.items) {
            String kind = normalizeKind(q.kind);
            if ("mcq".equals(kind)) {
                objectiveTotal++;
                String sel = pickSelection(selections, q.no);
                boolean ok = !sel.isBlank() && sel.equalsIgnoreCase(s(q.correctKey));
                if (ok) {
                    objectiveCorrect++;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("no", q.no);
                row.put("kind", "mcq");
                row.put("subjective", false);
                row.put("correct", ok);
                row.put("yourChoice", sel);
                row.put("correctKey", q.correctKey);
                row.put("stem", q.stem);
                details.add(row);
                if (!ok) {
                    wrongItems.add(Map.of(
                            "no", q.no,
                            "kind", "mcq",
                            "stem", s(q.stem),
                            "studentAnswer", s(sel),
                            "correctAnswer", s(q.correctKey)
                    ));
                }
                fact.append("第").append(q.no).append("题 ").append(q.stem).append(" 学生选 ")
                        .append(sel.isBlank() ? "未选" : sel).append(" 正确选项 ").append(q.correctKey).append("\n");
            } else if ("fill".equals(kind)) {
                objectiveTotal++;
                String ans = pickTextAnswer(textAnswers, q.no);
                boolean ok = matchFill(ans, q.referenceAnswer);
                if (ok) {
                    objectiveCorrect++;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("no", q.no);
                row.put("kind", "fill");
                row.put("subjective", false);
                row.put("correct", ok);
                row.put("yourChoice", ans);
                row.put("correctKey", q.referenceAnswer);
                row.put("stem", q.stem);
                details.add(row);
                if (!ok) {
                    wrongItems.add(Map.of(
                            "no", q.no,
                            "kind", "fill",
                            "stem", s(q.stem),
                            "studentAnswer", s(ans),
                            "correctAnswer", s(q.referenceAnswer)
                    ));
                }
                fact.append("第").append(q.no).append("题 ").append(q.stem).append(" 学生填 ")
                        .append(ans.isBlank() ? "未填" : ans).append(" 参考答案 ").append(q.referenceAnswer).append("\n");
            } else if ("essay".equals(kind)) {
                String ans = pickTextAnswer(textAnswers, q.no);
                Map<String, Object> row = new HashMap<>();
                row.put("no", q.no);
                row.put("kind", "essay");
                row.put("subjective", true);
                row.put("correct", null);
                row.put("yourChoice", ans.length() > 200 ? ans.substring(0, 200) + "…" : ans);
                row.put("correctKey", s(q.referenceHint).isBlank() ? "（主观题）" : "见参考要点");
                row.put("stem", q.stem);
                details.add(row);
                essayBlocks.add(
                        "第" + q.no + "题 题干：" + q.stem + "\n参考要点：" + s(q.referenceHint) + "\n学生作答：" + ans + "\n");
            }
        }

        String aiComment;
        boolean gradeUsedFallback = false;
        try {
            if (essayBlocks.isEmpty()) {
                String prompt = "你是小学老师。请根据以下批改数据写**详细**反馈（不少于约 150 字），必须包含：\n"
                        + "你当前任务是【批改】而不是出题。禁止输出 JSON、禁止输出 questions/meta 字段。\n"
                        + "1）总评：共几道题、答对几题、整体表现；\n"
                        + "2）逐题讲解：对错题说明错因或知识要点，对题可简要肯定考查点；不要只写「继续加油」一句话；\n"
                        + "3）结尾一句鼓励。\n"
                        + "语气亲切，适合小学生阅读。\n\n"
                        + "【批改数据】共" + objectiveTotal + "道客观题，答对" + objectiveCorrect + "道。\n"
                        + fact;
                aiComment = askCozeBot(cozeGradeBotId, me.id(), prompt, true);
            } else if (objectiveTotal == 0) {
                String prompt =
                        "你是小学老师。请对以下主观题（解答题）逐题批改，语气亲切。必须包含：\n"
                                + "你当前任务是【批改】而不是出题。禁止输出 JSON、禁止输出 questions/meta 字段。\n"
                                + "1）逐题：点评作答要点、与参考要点的差距、可改进之处；\n"
                                + "2）总评；\n"
                                + "3）结尾鼓励。\n\n"
                                + String.join("\n", essayBlocks);
                aiComment = askCozeBot(cozeGradeBotId, me.id(), prompt, true);
            } else {
                String prompt =
                        "你是小学老师。以下含客观题与主观题。请写**详细**反馈，必须包含：\n"
                                + "你当前任务是【批改】而不是出题。禁止输出 JSON、禁止输出 questions/meta 字段。\n"
                                + "1）客观题总体对错情况；\n"
                                + "2）客观题：对错题说明错因或知识要点；\n"
                                + "3）主观题：逐题点评与建议；\n"
                                + "4）总评与鼓励。\n\n"
                                + "【客观题】\n"
                                + fact
                                + "\n【主观题】\n"
                                + String.join("\n", essayBlocks);
                aiComment = askCozeBot(cozeGradeBotId, me.id(), prompt, true);
            }
            if (looksLikePromptEcho(aiComment)) {
                gradeUsedFallback = true;
                aiComment = "【提示】检测到智能体返回了提示词或无效评语，已自动切换本地详细批改。\n\n"
                        + buildLocalStructuredGradeComment(objectiveCorrect, objectiveTotal, details, !essayBlocks.isEmpty());
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            gradeUsedFallback = true;
            log.warn("structured grade: coze failed, local summary", e);
            aiComment = "【提示】智能批改服务暂时不可用，以下为本地简评（未走智能体详细讲解）。\n\n"
                    + buildLocalStructuredGradeComment(objectiveCorrect, objectiveTotal, details, !essayBlocks.isEmpty());
        }
        storeWrongQuestions(me.id(), paperId, paperMeta, wrongItems);
        Map<String, Object> out = new HashMap<>();
        out.put("mode", "structured");
        out.put("answer", aiComment);
        out.put("gradeText", aiComment);
        out.put("gradeUsedFallback", gradeUsedFallback);
        out.put("correctCount", objectiveCorrect);
        out.put("total", objectiveTotal);
        out.put("items", details);
        out.put("hasSubjective", !essayBlocks.isEmpty());
        out.put("provider", "coze-or-local");
        out.put("botId", s(cozeGradeBotId));
        return out;
    }

    private void storeWrongQuestions(String studentUserId, String paperId, Map<String, String> paperMeta, List<Map<String, Object>> wrongItems) {
        if (wrongItems == null || wrongItems.isEmpty()) {
            return;
        }
        ensureWrongQuestionTable();
        String subject = paperMeta == null ? "" : s(paperMeta.get("subject"));
        String grade = paperMeta == null ? "" : s(paperMeta.get("grade"));
        String knowledgePoint = paperMeta == null ? "" : s(paperMeta.get("knowledgePoint"));
        for (Map<String, Object> item : wrongItems) {
            jdbcTemplate.update(
                    "INSERT INTO wrong_question_records (student_user_id, source_type, paper_id, homework_id, question_no, question_kind, subject, grade, knowledge_point, stem, student_answer, correct_answer) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    s(studentUserId),
                    "selftest",
                    s(paperId),
                    null,
                    intVal(item.get("no")),
                    s(item.get("kind")),
                    subject,
                    grade,
                    knowledgePoint,
                    s(item.get("stem")),
                    s(item.get("studentAnswer")),
                    s(item.get("correctAnswer"))
            );
        }
    }

    private void ensureWrongQuestionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wrong_question_records (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  student_user_id VARCHAR(64) NOT NULL,
                  source_type VARCHAR(20) NOT NULL DEFAULT 'selftest',
                  paper_id VARCHAR(64) NOT NULL,
                  homework_id VARCHAR(64) NULL,
                  question_no INT NOT NULL,
                  question_kind VARCHAR(20) NOT NULL,
                  subject VARCHAR(50) NULL,
                  grade VARCHAR(30) NULL,
                  knowledge_point VARCHAR(120) NULL,
                  stem TEXT NOT NULL,
                  student_answer TEXT NULL,
                  correct_answer TEXT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_wrong_student_time (student_user_id, created_at),
                  INDEX idx_wrong_source_homework (source_type, homework_id),
                  INDEX idx_wrong_subject_grade (subject, grade)
                )
                """);
        // Avoid "ADD COLUMN IF NOT EXISTS" for older MySQL compatibility.
        if (!hasColumn("wrong_question_records", "source_type")) {
            jdbcTemplate.execute("ALTER TABLE wrong_question_records ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'selftest'");
        }
        if (!hasColumn("wrong_question_records", "homework_id")) {
            jdbcTemplate.execute("ALTER TABLE wrong_question_records ADD COLUMN homework_id VARCHAR(64) NULL");
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                (rs, i) -> rs.getInt(1),
                tableName,
                columnName
        );
        Integer c = rows.isEmpty() ? 0 : rows.get(0);
        return c != null && c > 0;
    }

    private boolean looksLikePromptEcho(String text) {
        String t = s(text);
        if (t.isBlank()) return true;
        String lower = t.toLowerCase();
        return lower.startsWith("写一段")
                || lower.startsWith("请写")
                || t.contains("不少于100字")
                || t.contains("根据以下要求")
                || t.contains("提示词")
                || t.length() < 12;
    }

    private String normalizeKind(String kind) {
        String k = s(kind);
        if (k.isBlank()) {
            return "mcq";
        }
        return k;
    }

    private String pickSelection(Map<String, Object> selections, int no) {
        Object v = selections.get(String.valueOf(no));
        if (v == null) {
            v = selections.get(no);
        }
        return s(v);
    }

    private String pickTextAnswer(Map<String, Object> textAnswers, int no) {
        if (textAnswers == null) {
            return "";
        }
        Object v = textAnswers.get(String.valueOf(no));
        if (v == null) {
            v = textAnswers.get(no);
        }
        return s(v);
    }

    private boolean matchFill(String student, String reference) {
        String ref = s(reference);
        if (ref.isBlank()) {
            return !s(student).isBlank();
        }
        String normalizedStudent = normalizeFillToken(student);
        if (normalizedStudent.isBlank()) {
            return false;
        }
        for (String part : ref.split("[/|／]")) {
            if (normalizedStudent.equals(normalizeFillToken(part))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeFillToken(String text) {
        return s(text).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String buildLocalStructuredGradeComment(
            int correctCount, int total, List<Map<String, Object>> details, boolean hasEssay) {
        StringBuilder sb = new StringBuilder();
        if (total > 0) {
            sb.append("总评：共 ").append(total).append(" 道客观题，答对 ").append(correctCount).append(" 道。\n\n");
            for (Map<String, Object> d : details) {
                if (Boolean.TRUE.equals(d.get("subjective"))) {
                    continue;
                }
                boolean ok = Boolean.TRUE.equals(d.get("correct"));
                sb.append("第").append(d.get("no")).append("题：").append(ok ? "✅ 正确，继续加油！" : "❌ 再看看正确答案哦").append("\n");
            }
        }
        if (hasEssay) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("主观题请结合题干与参考要点自查表达是否完整。");
        }
        return sb.toString().trim();
    }

    private static final class QuestionPaper {
        final String id;
        final String userId;
        final List<QuestionItem> items;
        final String sourceProvider;

        QuestionPaper(String id, String userId, List<QuestionItem> items) {
            this(id, userId, items, "coze-structured-json");
        }

        QuestionPaper(String id, String userId, List<QuestionItem> items, String sourceProvider) {
            this.id = id;
            this.userId = userId;
            this.items = items;
            this.sourceProvider = sStatic(sourceProvider).isBlank() ? "coze-structured-json" : sStatic(sourceProvider);
        }

        private static String sStatic(Object v) {
            return v == null ? "" : String.valueOf(v).trim();
        }

        String primaryKind() {
            if (items == null || items.isEmpty()) {
                return "mcq";
            }
            return normalizeKindStatic(items.get(0).kind);
        }

        private static String normalizeKindStatic(String kind) {
            String k = kind == null ? "" : kind.trim();
            return k.isEmpty() ? "mcq" : k;
        }

        List<Map<String, Object>> toClientQuestions() {
            List<Map<String, Object>> out = new ArrayList<>();
            for (QuestionItem q : items) {
                Map<String, Object> m = new HashMap<>();
                m.put("no", q.no);
                m.put("stem", q.stem);
                m.put("kind", normalizeKindStatic(q.kind));
                if ("mcq".equals(normalizeKindStatic(q.kind))) {
                    m.put("options", q.options);
                }
                out.add(m);
            }
            return out;
        }
    }

    private static final class QuestionItem {
        final int no;
        final String kind;
        final String stem;
        final List<Map<String, String>> options;
        final String correctKey;
        final String referenceAnswer;
        final String referenceHint;

        QuestionItem(
                int no,
                String kind,
                String stem,
                List<Map<String, String>> options,
                String correctKey,
                String referenceAnswer,
                String referenceHint) {
            this.no = no;
            this.kind = kind == null || kind.isBlank() ? "mcq" : kind;
            this.stem = stem;
            this.options = options == null ? List.of() : options;
            this.correctKey = correctKey == null ? "" : correctKey;
            this.referenceAnswer = referenceAnswer == null ? "" : referenceAnswer;
            this.referenceHint = referenceHint == null ? "" : referenceHint;
        }
    }

    private String askCozeBot(String botId, String userId, String message) {
        return askCozeBot(botId, userId, message, false);
    }

    /**
     * 解析 Coze POST /v3/chat 返回体：校验业务 code，并兼容 id/chat_id、嵌套 data.chat 等形态。
     */
    private CozeChatSession parseCozeChatCreateResponse(Map<String, Object> root) throws Exception {
        Object codeObj = root.get("code");
        if (codeObj instanceof Number n) {
            if (n.intValue() != 0) {
                throw new RuntimeException(
                        "Coze API 错误: code=" + n.intValue() + ", msg=" + s(root.get("msg")));
            }
        } else if (codeObj instanceof String cs && !cs.isBlank()) {
            if (!"0".equals(cs) && !"200".equals(cs)) {
                throw new RuntimeException(
                        "Coze API 错误: code=" + cs + ", msg=" + s(root.get("msg")));
            }
        } else if (Boolean.FALSE.equals(root.get("success"))) {
            throw new RuntimeException("Coze API 错误: " + s(root.get("msg")));
        }
        Object dataNode = root.get("data");
        Map<String, Object> data = mapVal(dataNode);
        if (data.isEmpty() && dataNode instanceof String str && !str.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(str, Map.class);
                data = mapVal(parsed);
            } catch (Exception ignore) {
                // ignore
            }
        }
        String chatId = firstNonBlankMapValue(data, "id", "chat_id");
        String conversationId = firstNonBlankMapValue(data, "conversation_id", "conversationId");
        Map<String, Object> nestedChat = mapVal(data.get("chat"));
        if (chatId.isBlank()) {
            chatId = firstNonBlankMapValue(nestedChat, "id", "chat_id");
        }
        if (conversationId.isBlank()) {
            conversationId = firstNonBlankMapValue(nestedChat, "conversation_id", "conversationId");
        }
        if (chatId.isBlank()) {
            chatId = firstNonBlankMapValue(root, "id", "chat_id");
        }
        if (conversationId.isBlank()) {
            conversationId = firstNonBlankMapValue(root, "conversation_id", "conversationId");
        }
        String status = s(data.get("status"));
        if (status.isBlank()) {
            status = s(nestedChat.get("status"));
        }
        if (chatId.isBlank() || conversationId.isBlank()) {
            String hint = truncateForLog(objectMapper.writeValueAsString(root));
            throw new RuntimeException(
                    "智能体返回会话参数异常：未解析到 chat_id / conversation_id。请检查 PAT、Bot 是否可用，"
                            + "以及 Coze 控制台该 Bot 是否已发布 API 接入。接口返回片段: "
                            + hint);
        }
        return new CozeChatSession(chatId, conversationId, status);
    }

    private String firstNonBlankMapValue(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        for (String k : keys) {
            String v = s(map.get(k));
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private String truncateForLog(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() > 480) {
            return t.substring(0, 480) + "...";
        }
        return t;
    }

    private record CozeChatSession(String chatId, String conversationId, String status) {}

    /**
     * @param gradingMode 为 true 时用「最后一条 assistant」作为批改正文，避免与出题 JSON 混用同一套 pick 逻辑。
     */
    private String askCozeBot(String botId, String userId, String message, boolean gradingMode) {
        if (s(cozePat).isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "智能体未配置：缺少 COZE_PAT");
        }
        if (s(botId).isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "智能体未配置：缺少 BOT_ID");
        }
        try {
            String base = trimSlash(cozeApiBase);
            Map<String, Object> chatPayload = new HashMap<>();
            chatPayload.put("bot_id", botId);
            chatPayload.put("user_id", s(userId).isBlank() ? "student-self-test" : userId);
            chatPayload.put("stream", false);
            chatPayload.put("auto_save_history", true);
            chatPayload.put("connector_id", "1024");
            chatPayload.put("additional_messages", List.of(
                    Map.of("role", "user", "content", message, "content_type", "text")
            ));
            Map<String, Object> created = postJsonWithBearer(base + "/v3/chat", chatPayload, cozePat);
            CozeChatSession session = parseCozeChatCreateResponse(created);
            String chatId = session.chatId();
            String conversationId = session.conversationId();
            String status = session.status();
            for (int i = 0; i < 30 && !"completed".equalsIgnoreCase(status); i++) {
                Thread.sleep(800);
                String retrieveUrl = base + "/v3/chat/retrieve?conversation_id="
                        + urlEncode(conversationId) + "&chat_id=" + urlEncode(chatId);
                Map<String, Object> retrieve = getJsonWithBearer(retrieveUrl, cozePat);
                status = s(mapVal(retrieve.get("data")).get("status"));
                if ("failed".equalsIgnoreCase(status)) {
                    throw new RuntimeException("智能体处理失败");
                }
            }
            String listUrl = base + "/v3/chat/message/list?conversation_id="
                    + urlEncode(conversationId) + "&chat_id=" + urlEncode(chatId);
            Map<String, Object> listResp = getJsonWithBearer(listUrl, cozePat);
            Object dataNode = listResp.get("data");
            List<Map<String, Object>> messages;
            if (dataNode instanceof Map<?, ?> dataMap) {
                messages = listVal(((Map<?, ?>) dataMap).get("messages"));
                if (messages.isEmpty()) {
                    messages = listVal(((Map<?, ?>) dataMap).get("items"));
                }
            } else {
                messages = listVal(dataNode);
            }
            List<Map<String, Object>> assistantMsgs = new ArrayList<>();
            for (Map<String, Object> msg : messages) {
                if ("assistant".equalsIgnoreCase(s(msg.get("role")))) {
                    assistantMsgs.add(msg);
                }
            }
            String answer =
                    gradingMode ? pickGradingAssistantPayload(assistantMsgs) : pickBestCozeAssistantPayload(assistantMsgs);
            if (answer.isBlank()) {
                answer = extractAssistantText(mapVal(dataNode));
            }
            if (answer.isBlank()) {
                answer = buildFallbackAnswer(status, dataNode, listResp);
            }
            log.info(
                    "Coze bot {} response extracted, gradingMode={}, status={}, answerLength={}",
                    botId,
                    gradingMode,
                    status,
                    answer.length());
            return answer;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, s(e.getMessage()).isBlank() ? "智能体调用失败" : e.getMessage());
        }
    }

    /** 批改场景：优先取「自然语言评语」，避免误拿出题 JSON。 */
    private String pickGradingAssistantPayload(List<Map<String, Object>> assistantMsgs) {
        if (assistantMsgs == null || assistantMsgs.isEmpty()) {
            return "";
        }
        String fallback = "";
        for (int i = assistantMsgs.size() - 1; i >= 0; i--) {
            String t = extractAssistantText(assistantMsgs.get(i));
            if (s(t).isBlank()) {
                continue;
            }
            if (fallback.isBlank()) {
                fallback = t;
            }
            if (looksLikeQuestionJson(t)) {
                continue;
            }
            if (looksLikeNaturalGradeText(t)) {
                return t;
            }
        }
        return fallback;
    }

    private boolean looksLikeQuestionJson(String text) {
        String t = s(text);
        if (t.isBlank()) {
            return false;
        }
        String compact = t.replaceAll("\\s+", "");
        boolean hasQuestions = compact.contains("\"questions\"") || compact.contains("'questions'");
        boolean hasMeta = compact.contains("\"meta\"") || compact.contains("'meta'");
        return (compact.startsWith("{") || compact.startsWith("[")) && hasQuestions && hasMeta;
    }

    private boolean looksLikeNaturalGradeText(String text) {
        String t = s(text);
        if (t.isBlank()) {
            return false;
        }
        // 批改常见关键词：总评/建议/第X题/正确率/鼓励；命中任一即可判定为评语文本。
        return t.contains("总评")
                || t.contains("建议")
                || t.contains("第1题")
                || t.contains("答对")
                || t.contains("正确率")
                || t.contains("继续加油")
                || t.contains("鼓励");
    }

    private boolean checkOllamaAvailable() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(ollamaApiBase) + "/api/tags")).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> postJson(String endpoint, Map<String, Object> payload) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (!s(externalServiceApiKey).isBlank()) {
            builder.header("Authorization", "Bearer " + externalServiceApiKey);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> data = s(response.body()).isBlank() ? Map.of() : objectMapper.readValue(response.body(), Map.class);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(s(data.getOrDefault("message", "外部服务调用失败(" + response.statusCode() + ")")));
        }
        return data;
    }

    private Map<String, Object> postJsonWithBearer(String endpoint, Map<String, Object> payload, String token) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> data = s(response.body()).isBlank() ? Map.of() : objectMapper.readValue(response.body(), Map.class);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(s(data.getOrDefault("msg", data.getOrDefault("message", "外部服务调用失败(" + response.statusCode() + ")"))));
        }
        return data;
    }

    private Map<String, Object> getJsonWithBearer(String endpoint, String token) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> data = s(response.body()).isBlank() ? Map.of() : objectMapper.readValue(response.body(), Map.class);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(s(data.getOrDefault("msg", data.getOrDefault("message", "外部服务调用失败(" + response.statusCode() + ")"))));
        }
        return data;
    }

    private String urlEncode(String v) {
        return java.net.URLEncoder.encode(s(v), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapVal(Object v) {
        return v instanceof Map<?, ?> ? (Map<String, Object>) v : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listVal(Object v) {
        if (!(v instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /**
     * Coze 可能返回多条 assistant 消息（思考/工具/最终回复）。只取「最后一条」会丢 JSON。
     * 优先选同时含 questions 与 meta 的片段，否则取最长（多为最终 JSON）。
     */
    private String pickBestCozeAssistantPayload(List<Map<String, Object>> assistantMsgs) {
        if (assistantMsgs == null || assistantMsgs.isEmpty()) {
            return "";
        }
        List<String> chunks = new ArrayList<>();
        for (Map<String, Object> m : assistantMsgs) {
            String t = extractAssistantText(m);
            if (!s(t).isBlank()) {
                chunks.add(t);
            }
        }
        if (chunks.isEmpty()) {
            return "";
        }
        for (String t : chunks) {
            boolean hasQ = t.contains("\"questions\"") || t.contains("'questions'");
            boolean hasMeta = t.contains("\"meta\"") || t.contains("'meta'");
            if (hasQ && hasMeta) {
                log.info("Coze: using assistant chunk with questions+meta, length={}", t.length());
                return t;
            }
        }
        for (String t : chunks) {
            if (t.contains("\"questions\"") || t.contains("'questions'")) {
                log.info("Coze: using assistant chunk with questions only, length={}", t.length());
                return t;
            }
        }
        String best = chunks.stream().max(Comparator.comparingInt(String::length)).orElse(chunks.get(chunks.size() - 1));
        log.info("Coze: using longest assistant chunk, length={}, totalChunks={}", best.length(), chunks.size());
        return best;
    }

    private String extractAssistantText(Map<String, Object> message) {
        if (message == null || message.isEmpty()) return "";
        String byContentNode = extractTextFromNode(message.get("content"));
        if (!byContentNode.isBlank()) return byContentNode;
        String byAnswerNode = extractTextFromNode(message.get("answer"));
        if (!byAnswerNode.isBlank()) return byAnswerNode;
        String byOutputNode = extractTextFromNode(message.get("output"));
        if (!byOutputNode.isBlank()) return byOutputNode;
        return "";
    }

    private String extractTextFromNode(Object node) {
        if (node == null) return "";
        if (node instanceof String text) {
            return extractTextFromString(text);
        }
        if (node instanceof Map<?, ?> mapNode) {
            String text = extractTextFromNode(mapNode.get("text"));
            if (!text.isBlank()) return text;
            text = extractTextFromNode(mapNode.get("content"));
            if (!text.isBlank()) return text;
            text = extractTextFromNode(mapNode.get("value"));
            if (!text.isBlank()) return text;
            return "";
        }
        if (node instanceof List<?> listNode) {
            StringBuilder sb = new StringBuilder();
            for (Object item : listNode) {
                String part = extractTextFromNode(item);
                if (!part.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(part);
                }
            }
            return sb.toString();
        }
        return s(node);
    }

    private String extractTextFromString(String raw) {
        String text = s(raw);
        if (text.isBlank()) return "";
        if (!(text.startsWith("{") || text.startsWith("["))) return text;
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                String byText = extractTextFromNode(map.get("text"));
                if (!byText.isBlank()) return byText;
                String byContent = extractTextFromNode(map.get("content"));
                if (!byContent.isBlank()) return byContent;
            }
            if (parsed instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object item : list) {
                    String part = extractTextFromNode(item);
                    if (!part.isBlank()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(part);
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }
        } catch (Exception ignore) {
            return text;
        }
        return text;
    }

    private String buildFallbackAnswer(String status, Object dataNode, Map<String, Object> listResp) {
        try {
            String dataJson = objectMapper.writeValueAsString(dataNode);
            String listJson = objectMapper.writeValueAsString(listResp);
            StringBuilder sb = new StringBuilder();
            sb.append("智能体已响应，但当前返回结构未完全适配。\n");
            sb.append("会话状态: ").append(s(status).isBlank() ? "unknown" : status).append("\n\n");
            sb.append("message.list.data:\n").append(dataJson).append("\n\n");
            sb.append("message.list.raw:\n").append(listJson);
            return sb.toString();
        } catch (Exception e) {
            return "智能体已响应，但解析失败。请稍后重试。";
        }
    }

    private String trimSlash(String s) {
        String t = s(s);
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    private String s(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private int intVal(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

}
