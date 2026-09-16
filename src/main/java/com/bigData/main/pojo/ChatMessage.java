package com.bigData.main.pojo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.util.Date;

/**
 * 智能体对话记录。
 * <p>
 * 应用启动时由 Hibernate（ddl-auto=update）自动建表 chat_message，
 * 无需手写 SQL 或 MyBatis XML。
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会话 ID，同一会话的多条消息共用 */
    @Column(name = "session_id", length = 64, nullable = false)
    private String sessionId;

    /** 消息角色：system / user / assistant / tool */
    @Column(name = "role", length = 16, nullable = false)
    private String role;

    /** 消息文本内容；assistant 调用工具时可能为空 */
    @Lob
    @Column(name = "content")
    private String content;

    /** assistant 消息携带的 tool_calls 的 JSON 串；仅 role=assistant 且调用工具时有值 */
    @Lob
    @Column(name = "tool_calls")
    private String toolCalls;

    /** role=tool 时对应的 tool_call_id */
    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    @Column(name = "create_time")
    private Date createTime = new Date();

    public ChatMessage() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
