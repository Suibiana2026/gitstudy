package com.bigData.main.service.API;

import com.bigData.main.Repository.ChatMessageRepository;
import com.bigData.main.pojo.ChatMessage;
import com.bigData.main.service.Agent.AgentTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大模型服务。
 * <p>
 * 两条链路：
 * 1. {@link #chatWithAI(String)} —— 原有单轮问答，AISystem.html 在用，保持兼容；
 * 2. {@link #streamChat} —— 智能体主链路，支持流式输出、多轮上下文记忆与工具调用。
 * <p>
 * 说明：Spring Boot 2.0.1 自带的 RestTemplate 不支持流式响应，
 * 所以流式部分用 HttpURLConnection 手写 SSE 帧解析。
 */
@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private static final String DATA_PREFIX = "data:";
    private static final String DONE_FLAG = "[DONE]";

    /** 会话历史最多保留的消息条数（不含 system），超出时从头裁剪 */
    private static final int MAX_HISTORY = 30;

    private static final String SYSTEM_PROMPT =
            "你是一个面向汽车销售大数据平台的智能体，名字叫「数据小助手」。"
                    + "你既能调用工具查数据，也能直接执行采集、清洗、上传等操作，而不是凭空编造。\n\n"
                    + "【平台数据背景】\n"
                    + "- MySQL 数据库 car_db 的 car_sales 表存放汽车销售明细，字段包括："
                    + "model（车型）、sale_date（销售日期）、sales_volume（销量）、"
                    + "customer_type（客户类型）、region（区域）、market_trend（市场趋势百分比）、inventory（库存）。\n"
                    + "- HDFS 中 /original_data 存放原始上传或实时采集的数据文件，"
                    + "/processed_data 存放清洗、去重、词频统计后的结果。\n"
                    + "- 平台具备三项「动手」能力：① TCP Socket 实时数据采集并落 HDFS；"
                    + "② 对 HDFS 上的 CSV 做分组统计并写入 MySQL 大屏数据表，也可以恢复回备份数据；"
                    + "③ 把服务器本地「上传目录」里的文件上传到 HDFS。\n\n"
                    + "【工作原则】\n"
                    + "1. 凡是涉及销量、排名、库存、文件清单、采集状态的问题，先调用对应工具拿到真实数据，再基于数据作答。\n"
                    + "2. 用户要求采集数据、跑统计、上传文件时，直接调用相应工具去执行，不要只给建议、也不要让他自己去点页面。\n"
                    + "3. 采集要克制：用户没说时长就按默认 5 秒，不要主动发起长时间采集。\n"
                    + "4. 上传文件只能上传「上传目录」内的文件。如果用户给的是该目录之外的路径，"
                    + "如实说明安全限制，并提示他先把文件放进上传目录。\n"
                    + "5. 一次可以调用多个工具。工具返回失败时，如实说明失败原因并给出可操作的建议，绝不要编造数据。\n"
                    + "6. 用中文回答，结论先行。涉及多个对象对比时用表格或列表，数字要带单位。\n"
                    + "7. 用户问的内容与平台业务无关时，正常闲聊即可，或礼貌说明你的能力范围。\n"
                    + "8. 不要向用户暴露工具名、参数名这类实现细节，用自然语言描述你做了什么。";

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.model}")
    private String model;

    @Value("${ai.agent.max-tool-rounds:5}")
    private int maxToolRounds;

    @Value("${ai.agent.timeout:180000}")
    private int timeout;

    @Autowired
    private AgentTools agentTools;

    @Autowired
    private SalesService salesService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** sessionId -> 完整消息历史 */
    private final Map<String, List<Map<String, Object>>> sessions =
            new ConcurrentHashMap<String, List<Map<String, Object>>>();

    /** 每个会话一把锁，避免同一会话并发请求把历史写乱 */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<String, Object>();

    // ==================================================================
    // 单轮问答（兼容 AISystem.html 的 /api/chat）
    // ==================================================================

    public String chatWithAI(String userMessage) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("model", model);

        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        Map<String, String> message = new HashMap<String, String>();
        message.put("role", "user");
        message.put("content", userMessage);
        messages.add(message);
        payload.put("messages", messages);

        HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            Map<String, Object> replyMessage = (Map<String, Object>) choices.get(0).get("message");
            return (String) replyMessage.get("content");
        } catch (Exception e) {
            return "AI 接口调用失败: " + e.getMessage();
        }
    }

    // ==================================================================
    // 智能体主链路：流式输出 + 多轮上下文 + 工具调用
    // ==================================================================

    /**
     * 流式对话。
     *
     * @param sessionId 会话 ID，同一 ID 共享上下文
     * @param userMessage 用户提问
     * @param onDelta 每收到一段模型输出就回调一次（用于 SSE 推送）
     * @param onTool 准备调用某个工具时的回调（用于前端展示过程）
     */
    public void streamChat(String sessionId, String userMessage,
                           Consumer<String> onDelta, Consumer<String> onTool) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("提问内容不能为空");
        }

        Object lock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            List<Map<String, Object>> messages = getOrCreateSession(sessionId);
            ensureSystem(messages, sessionId);
            messages.add(message("user", userMessage.trim()));
            persist(sessionId, "user", userMessage.trim(), null, null);

            // 记录本轮是否已经吐过字，用于判断能否安全重试
            final boolean[] emitted = new boolean[1];
            final Consumer<String> sink = new Consumer<String>() {
                @Override
                public void accept(String delta) {
                    if (delta != null && delta.length() > 0) {
                        emitted[0] = true;
                    }
                    if (onDelta != null) {
                        onDelta.accept(delta);
                    }
                }
            };

            for (int round = 0; round < maxToolRounds; round++) {
                StreamResult result;
                try {
                    result = callStreamWithRetry(messages, sink, emitted);
                } catch (Exception e) {
                    logger.error("调用大模型失败，转入本地兜底：" + e.getMessage());
                    if (!emitted[0]) {
                        sink.accept(localFallback(userMessage));
                    }
                    trimHistory(messages);
                    return;
                }

                if (result.toolCalls.isEmpty()) {
                    if (result.text.length() > 0) {
                        messages.add(message("assistant", result.text.toString()));
                        persist(sessionId, "assistant", result.text.toString(), null, null);
                    } else {
                        sink.accept("（模型这次没有返回内容，换个说法再问一次试试）");
                    }
                    trimHistory(messages);
                    return;
                }

                String toolCallsJson = toolCallsJson(result.toolCalls);
                messages.add(assistantToolCallMessage(result));
                persist(sessionId, "assistant",
                        result.text.length() > 0 ? result.text.toString() : "",
                        toolCallsJson, null);

                for (ToolCall call : result.toolCalls) {
                    if (onTool != null) {
                        onTool.accept(describeTool(call.name));
                    }
                    logger.info("会话 " + sessionId + " 调用工具：" + call.name + " 参数：" + call.arguments);
                    String toolResult = agentTools.execute(call.name, call.arguments.toString());
                    messages.add(toolResultMessage(call.id, toolResult));
                    persist(sessionId, "tool", toolResult, null, call.id);
                }
            }

            sink.accept("\n\n（已达到工具调用上限 " + maxToolRounds + " 轮，先给出目前掌握的信息）");
            trimHistory(messages);
        }
    }

    /**
     * 回显某会话的历史对话，供前端刷新后恢复聊天记录。
     * 只返回 user / assistant 的文本消息，工具调用细节不展示。
     */
    public List<Map<String, Object>> loadHistory(String sessionId) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        try {
            List<ChatMessage> rows = chatMessageRepository.findTop40BySessionIdOrderByIdDesc(sessionId);
            Collections.reverse(rows);
            for (ChatMessage row : rows) {
                String role = row.getRole();
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                if ("assistant".equals(role) && row.getToolCalls() != null && !row.getToolCalls().isEmpty()) {
                    continue; // 只调工具、没有文本的回答不展示
                }
                if (row.getContent() == null || row.getContent().trim().isEmpty()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("role", role);
                item.put("content", row.getContent());
                item.put("time", row.getCreateTime() == null
                        ? System.currentTimeMillis() : row.getCreateTime().getTime());
                result.add(item);
            }
        } catch (Exception e) {
            logger.warn("读取历史对话失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 带一次重试的流式调用。
     * 已经吐过字就不再重试，否则重试会把同一段内容重复输出给用户。
     */
    private StreamResult callStreamWithRetry(List<Map<String, Object>> messages,
                                             Consumer<String> sink, boolean[] emitted) {
        try {
            return callStream(messages, sink);
        } catch (RuntimeException first) {
            if (emitted[0]) {
                throw first;
            }
            logger.warn("调用大模型失败，1 秒后重试一次：" + first.getMessage());
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return callStream(messages, sink);
        }
    }

    /**
     * 大模型不可用时的本地兜底。
     * 能从问题里识别出时间范围，就直接用本地规则查一次库，保证答辩现场有结果可看。
     */
    private String localFallback(String question) {
        String date = extractDate(question);
        if (date == null) {
            return "【本地兜底】大模型服务当前不可用，且没能从你的问题里识别出时间范围。\n"
                    + "请稍后重试，或把问题写得更具体，例如「查一下 2025-03 的销量」。";
        }
        try {
            return "【本地兜底】大模型服务当前不可用，以下是直接查库得到的结果：\n\n"
                    + salesService.summarizeSales(date);
        } catch (Exception e) {
            return "【本地兜底】大模型服务不可用，本地查询也失败了：" + e.getMessage();
        }
    }

    /** 从问题里抠出 YYYY-MM-DD / YYYY-MM / YYYY，优先取最精确的那一级 */
    private static String extractDate(String question) {
        if (question == null) {
            return null;
        }
        Matcher full = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(question);
        if (full.find()) {
            return full.group(0);
        }
        Matcher month = Pattern.compile("(\\d{4})-(\\d{2})").matcher(question);
        if (month.find()) {
            return month.group(0);
        }
        Matcher year = Pattern.compile("(19|20)\\d{2}").matcher(question);
        if (year.find()) {
            return year.group(0);
        }
        return null;
    }

    /** 清空指定会话的上下文（内存 + 数据库） */
    public void resetSession(String sessionId) {
        sessions.remove(sessionId);
        try {
            chatMessageRepository.clearSession(sessionId);
        } catch (Exception e) {
            logger.warn("清理会话记录失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 流式 HTTP 调用与 SSE 解析
    // ==================================================================

    private StreamResult callStream(List<Map<String, Object>> messages, Consumer<String> onDelta) {
        HttpURLConnection conn = null;
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("stream", true);
            payload.put("tools", agentTools.buildToolDefinitions());

            byte[] body = objectMapper.writeValueAsBytes(payload);

            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(timeout);

            OutputStream os = conn.getOutputStream();
            try {
                os.write(body);
                os.flush();
            } finally {
                closeQuietly(os);
            }

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("大模型接口返回 HTTP " + code + "：" + shortText(readAll(conn.getErrorStream())));
            }

            StreamResult result = new StreamResult();
            Map<Integer, ToolCall> toolCalls = new LinkedHashMap<Integer, ToolCall>();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), Charset.forName("UTF-8")));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || !line.startsWith(DATA_PREFIX)) {
                        continue;
                    }
                    String data = line.substring(DATA_PREFIX.length()).trim();
                    if (DONE_FLAG.equals(data)) {
                        break;
                    }
                    handleChunk(data, result, toolCalls, onDelta);
                }
            } finally {
                closeQuietly(reader);
            }

            result.toolCalls = new ArrayList<ToolCall>(toolCalls.values());
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用大模型失败：" + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 处理单个 SSE 数据帧。
     * 流式返回的 tool_calls 是分片下发的，需要按 index 累积拼接。
     */
    private void handleChunk(String data, StreamResult result,
                             Map<Integer, ToolCall> toolCalls, Consumer<String> onDelta) {
        JsonNode node;
        try {
            node = objectMapper.readTree(data);
        } catch (Exception e) {
            return; // 忽略心跳或非 JSON 帧
        }

        JsonNode choices = node.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            return;
        }
        JsonNode delta = choices.get(0).path("delta");

        JsonNode content = delta.get("content");
        if (content != null && !content.isNull()) {
            String text = content.asText();
            if (!text.isEmpty()) {
                result.text.append(text);
                if (onDelta != null) {
                    onDelta.accept(text);
                }
            }
        }

        JsonNode calls = delta.get("tool_calls");
        if (calls != null && calls.isArray()) {
            for (JsonNode call : calls) {
                int index = call.path("index").asInt(0);
                ToolCall acc = toolCalls.get(index);
                if (acc == null) {
                    acc = new ToolCall();
                    acc.index = index;
                    acc.id = "call_" + index + "_" + System.currentTimeMillis();
                    toolCalls.put(index, acc);
                }
                if (call.hasNonNull("id")) {
                    acc.id = call.get("id").asText();
                }
                JsonNode fn = call.get("function");
                if (fn != null) {
                    if (fn.hasNonNull("name")) {
                        acc.name = fn.get("name").asText();
                    }
                    if (fn.hasNonNull("arguments")) {
                        acc.arguments.append(fn.get("arguments").asText());
                    }
                }
            }
        }
    }

    // ==================================================================
    // 消息构造与历史维护
    // ==================================================================

    private Map<String, Object> assistantToolCallMessage(StreamResult result) {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "assistant");
        msg.put("content", result.text.length() > 0 ? result.text.toString() : "");

        List<Map<String, Object>> calls = new ArrayList<Map<String, Object>>();
        for (ToolCall call : result.toolCalls) {
            Map<String, Object> c = new LinkedHashMap<String, Object>();
            c.put("id", call.id);
            c.put("type", "function");

            Map<String, Object> fn = new LinkedHashMap<String, Object>();
            fn.put("name", call.name);
            fn.put("arguments", call.arguments.length() > 0 ? call.arguments.toString() : "{}");
            c.put("function", fn);

            calls.add(c);
        }
        msg.put("tool_calls", calls);
        return msg;
    }

    private Map<String, Object> toolResultMessage(String callId, String content) {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "tool");
        msg.put("tool_call_id", callId);
        msg.put("content", content == null ? "" : content);
        return msg;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    /**
     * 裁剪会话历史，防止上下文无限膨胀。
     * 裁剪点要避开 role=tool 的消息，否则会留下没有对应 tool_calls 的孤儿消息导致接口报错。
     */
    private void trimHistory(List<Map<String, Object>> messages) {
        if (messages.size() <= MAX_HISTORY + 1) {
            return;
        }

        int start = messages.size() - MAX_HISTORY;
        while (start < messages.size() && "tool".equals(messages.get(start).get("role"))) {
            start++;
        }

        List<Map<String, Object>> kept = new ArrayList<Map<String, Object>>();
        kept.add(messages.get(0));
        for (int i = start; i < messages.size(); i++) {
            kept.add(messages.get(i));
        }

        messages.clear();
        messages.addAll(kept);
    }

    /**
     * 获取或加载会话历史。内存里没有就从数据库恢复最近若干条，
     * 让对话在应用重启后仍能续上上下文。
     */
    private List<Map<String, Object>> getOrCreateSession(String sessionId) {
        List<Map<String, Object>> cached = sessions.get(sessionId);
        if (cached != null) {
            return cached;
        }

        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        try {
            List<ChatMessage> rows = chatMessageRepository.findTop40BySessionIdOrderByIdDesc(sessionId);
            if (!rows.isEmpty()) {
                Collections.reverse(rows);
                // 跳过开头可能被截断的 tool / 带工具调用的 assistant，避免历史不完整导致接口报错
                while (!rows.isEmpty()) {
                    ChatMessage head = rows.get(0);
                    boolean hasToolCalls = head.getToolCalls() != null && !head.getToolCalls().isEmpty();
                    if ("tool".equals(head.getRole()) || ("assistant".equals(head.getRole()) && hasToolCalls)) {
                        rows.remove(0);
                        continue;
                    }
                    break;
                }
                for (ChatMessage row : rows) {
                    messages.add(fromRow(row));
                }
            }
        } catch (Exception e) {
            logger.warn("加载会话历史失败：" + e.getMessage());
        }

        List<Map<String, Object>> existing = sessions.putIfAbsent(sessionId, messages);
        return existing != null ? existing : messages;
    }

    /** 确保历史以 system 开头 */
    private void ensureSystem(List<Map<String, Object>> messages, String sessionId) {
        if (messages.isEmpty() || !"system".equals(messages.get(0).get("role"))) {
            messages.add(0, message("system", SYSTEM_PROMPT));
            persist(sessionId, "system", SYSTEM_PROMPT, null, null);
        }
    }

    /** 落库一条对话记录；失败不影响主流程 */
    private void persist(String sessionId, String role, String content, String toolCalls, String toolCallId) {
        try {
            ChatMessage row = new ChatMessage();
            row.setSessionId(sessionId);
            row.setRole(role);
            row.setContent(content);
            row.setToolCalls(toolCalls);
            row.setToolCallId(toolCallId);
            row.setCreateTime(new Date());
            chatMessageRepository.save(row);
        } catch (Exception e) {
            logger.warn("保存对话记录失败：" + e.getMessage());
        }
    }

    /** 把数据库行还原成发给大模型的消息结构 */
    private Map<String, Object> fromRow(ChatMessage row) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", row.getRole());
        m.put("content", row.getContent() == null ? "" : row.getContent());
        if (row.getToolCalls() != null && !row.getToolCalls().isEmpty()) {
            try {
                m.put("tool_calls", objectMapper.readTree(row.getToolCalls()));
            } catch (Exception e) {
                logger.warn("解析历史 tool_calls 失败：" + e.getMessage());
            }
        }
        if (row.getToolCallId() != null) {
            m.put("tool_call_id", row.getToolCallId());
        }
        return m;
    }

    /** 把一轮工具调用序列化成 JSON 串，供落库与回放 */
    private String toolCallsJson(List<ToolCall> calls) {
        try {
            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
            for (ToolCall call : calls) {
                Map<String, Object> c = new LinkedHashMap<String, Object>();
                c.put("id", call.id);
                c.put("type", "function");
                Map<String, Object> fn = new LinkedHashMap<String, Object>();
                fn.put("name", call.name);
                fn.put("arguments", call.arguments.length() > 0 ? call.arguments.toString() : "{}");
                c.put("function", fn);
                list.add(c);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private static String describeTool(String name) {
        if ("query_sales_data".equals(name)) {
            return "正在查询销售数据…";
        }
        if ("generate_sales_plan".equals(name)) {
            return "正在生成销售方案…";
        }
        if ("list_hdfs_files".equals(name)) {
            return "正在读取 HDFS 文件列表…";
        }
        if ("run_mapreduce_job".equals(name)) {
            return "正在提交离线计算作业，这一步可能要等一会儿…";
        }
        if ("get_collect_status".equals(name)) {
            return "正在查询数据采集服务状态…";
        }
        return "正在调用工具…";
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String shortText(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
    }

    /** 流式调用的累积结果 */
    private static class StreamResult {
        final StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<ToolCall>();
    }

    /** 一次工具调用的分片累积体 */
    private static class ToolCall {
        int index;
        String id;
        String name = "";
        final StringBuilder arguments = new StringBuilder();
    }
}
