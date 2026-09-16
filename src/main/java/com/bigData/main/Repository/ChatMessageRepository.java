package com.bigData.main.Repository;

import com.bigData.main.pojo.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 智能体对话记录仓储。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 取某会话最近的若干条消息（按 id 倒序后取前 N，再由调用方反转为正序）。
     * 取最近而非最早，是为了在历史很长时仍保留对当前对话最相关的上下文。
     */
    List<ChatMessage> findTop40BySessionIdOrderByIdDesc(String sessionId);

    /**
     * 清空某会话的全部记录。配合前端「新会话」按钮使用。
     */
    @Transactional
    @Modifying
    @Query("delete from ChatMessage c where c.sessionId = :sessionId")
    int clearSession(@Param("sessionId") String sessionId);
}
