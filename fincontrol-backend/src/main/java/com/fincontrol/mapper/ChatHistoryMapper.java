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
     * 列出某个 type 下所有 assistant 消息（用于 parse-logs），按 created_at desc + id desc。
     * Phase 1 临时方案：operation_log 上线后改为跨表查询（[api-contract.md §9.2](#)）。
     */
    @Select("SELECT id, user_id, conversation_id, role, content, created_at, conversation_type " +
            "FROM chat_history " +
            "WHERE conversation_type = #{conversationType} AND role = 'assistant' " +
            "ORDER BY created_at DESC, id DESC")
    List<ChatHistory> selectAssistantByType(@Param("conversationType") String conversationType);
}
