package com.bigData.main.controller.Agent;

/**
 * 智能体对话请求体
 */
public class AgentChatRequest {

    /** 用户提问内容 */
    private String question;

    /** 会话 ID，同一 ID 共享上下文；为空时由服务端生成 */
    private String sessionId;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
