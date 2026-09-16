package com.bigData.main.controller.Agent;

import com.bigData.main.service.API.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 业务智能体接口。
 * <p>
 * 前端 AISystem.html（智能体页）通过 fetch 流式读取本接口的 SSE 输出。
 * 推送的事件类型：
 * - session：服务端分配或确认的会话 ID
 * - delta  ：模型输出的增量文本
 * - tool   ：准备调用某个业务工具时的过程提示
 * - done   ：本轮结束
 * - error  ：异常信息
 */
@RestController
@RequestMapping("/ai")
public class AiAgentController {

    private static final Logger logger = LoggerFactory.getLogger(AiAgentController.class);

    /** SSE 连接最长保持 10 分钟（统计入库要起 Spark，可能跑几十秒，留足余量） */
    private static final long SSE_TIMEOUT = 600000L;

    @Autowired
    private AiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping(value = "/bigdata/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AgentChatRequest request) {
        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        final String sessionId = (request.getSessionId() == null || request.getSessionId().trim().isEmpty())
                ? UUID.randomUUID().toString()
                : request.getSessionId().trim();
        final String question = request.getQuestion();

        emitter.onTimeout(new Runnable() {
            @Override
            public void run() {
                emitter.complete();
            }
        });
        emitter.onError(new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                logger.warn("SSE 连接异常：" + e.getMessage());
            }
        });

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    send(emitter, "session", sessionId);
                    aiService.streamChat(sessionId, question,
                            new java.util.function.Consumer<String>() {
                                @Override
                                public void accept(String delta) {
                                    send(emitter, "delta", delta);
                                }
                            },
                            new java.util.function.Consumer<String>() {
                                @Override
                                public void accept(String tool) {
                                    send(emitter, "tool", tool);
                                }
                            });
                    send(emitter, "done", "ok");
                    emitter.complete();
                } catch (Exception e) {
                    logger.error("智能体对话失败", e);
                    send(emitter, "error", e.getMessage() == null ? "服务异常" : e.getMessage());
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    /**
     * 清空指定会话的上下文，供前端「新会话」按钮调用
     */
    @GetMapping("/bigdata/reset")
    public Map<String, Object> reset(@RequestParam("sessionId") String sessionId) {
        aiService.resetSession(sessionId);
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("status", "ok");
        resp.put("sessionId", sessionId);
        return resp;
    }

    /**
     * 回显某会话的历史对话，供前端刷新页面后恢复聊天记录
     */
    @GetMapping("/bigdata/history")
    public List<Map<String, Object>> history(@RequestParam("sessionId") String sessionId) {
        return aiService.loadHistory(sessionId);
    }

    /**
     * 把内容包成 JSON 再推送。
     * 模型输出里常含换行符，直接当 data 发送会被拆成多个 data 行，
     * 包一层 JSON 可以保证每个事件只有一行数据。
     */
    private void send(SseEmitter emitter, String event, String payload) {
        try {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("v", payload == null ? "" : payload);
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(body)));
        } catch (Exception e) {
            // 客户端主动断开时会抛异常，忽略
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
