package com.tradeagent.scanner.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradeagent.scanner.repository.entity.OutboxEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Outbox Mapper，保证每个事件版本只有一条发布记录。 */
@Mapper
public interface OutboxMapper extends BaseMapper<OutboxEntity> {
    /** 在事件事务中幂等记录发布意图，不重置已发布状态。 */
    @Insert("""
        INSERT INTO scanner_outbox(id,event_id,event_version,payload)
        VALUES (#{row.id},#{row.eventId},#{row.eventVersion},#{row.payload})
        ON CONFLICT(event_id,event_version) DO NOTHING
        """)
    int insertIfAbsent(@Param("row") OutboxEntity row);

    /** 在发布事务中领取一条到期记录，防止工作线程同时发送同一消息。 */
    @Select("""
        SELECT * FROM scanner_outbox WHERE status IN ('PENDING','RETRY') AND next_attempt_at <= now()
        ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    OutboxEntity lockNext();
}
