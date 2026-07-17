package com.fincontrol.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fincontrol.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * chat_history 表的 MyBatis-Plus DAO。
 * 继承 BaseMapper 获得 CRUD；额外定义按 conversationId 列表和类型统计的方法。
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    /**
     * 查某个 conversationId 的全部消息，按 created_at asc。
     */
    @Select("SELECT id, user_id, conversation_id, role, content, created_at, conversation_type " +
            "FROM chat_history WHERE conversation_id = #{conversationId} " +
            "ORDER BY created_at ASC, id ASC")
    List<ChatHistory> selectByConversationIdOrderByCreatedAt(@Param("conversationId") String conversationId);

    /**
     * 列出某个 user + type 下所有 assistant 消息（用于 parse-logs / operations/recent），按 created_at desc + id desc。
     * Phase 1 临时方案：operation_log 上线后改为跨表查询（[api-contract.md §9.2](#)）。
     */
    @Select("SELECT id, user_id, conversation_id, role, content, created_at, conversation_type " +
            "FROM chat_history " +
            "WHERE user_id = #{userId} " +
            "AND conversation_type = #{conversationType} AND role = 'assistant' " +
            "ORDER BY created_at DESC, id DESC")
    List<ChatHistory> selectAssistantByType(
            @Param("userId") Long userId,
            @Param("conversationType") String conversationType);

    /**
     * 1a.19 列出某 user 的所有对话（按 conversation_id 分组），按 MAX(created_at) desc。
     * <p>可选按 conversationType 过滤（{@code NULL} = 不过滤）。
     * <p>使用返回 {@code Map<String, Object>} 避免在 mapper 端 @Results 转换复杂。
     */
    @Select("SELECT conversation_id AS conversationId, " +
            "       conversation_type AS conversationType, " +
            "       MIN(created_at) AS createdAt, " +
            "       MAX(created_at) AS lastMessageAt, " +
            "       COUNT(*) AS messageCount " +
            "FROM chat_history " +
            "WHERE user_id = #{userId} " +
            "  AND (#{conversationType} IS NULL OR conversation_type = #{conversationType}) " +
            "GROUP BY conversation_id, conversation_type " +
            "ORDER BY lastMessageAt DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<java.util.Map<String, Object>> selectConversationList(
            @Param("userId") Long userId,
            @Param("conversationType") String conversationType,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 1a.19 count conversations（配合 selectConversationList 分页）。
     */
    @Select("SELECT COUNT(DISTINCT conversation_id) " +
            "FROM chat_history " +
            "WHERE user_id = #{userId} " +
            "  AND (#{conversationType} IS NULL OR conversation_type = #{conversationType})")
    int countConversations(
            @Param("userId") Long userId,
            @Param("conversationType") String conversationType);

    /**
     * 1a.22 物理删除某 conversation 的全部消息，返回删除行数。
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM chat_history WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
