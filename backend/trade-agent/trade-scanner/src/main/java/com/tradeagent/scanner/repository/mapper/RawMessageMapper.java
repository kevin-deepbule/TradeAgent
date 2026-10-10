package com.tradeagent.scanner.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradeagent.scanner.repository.entity.RawMessageEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 原始消息 Mapper，保留 PostgreSQL 原子去重与并发领取语义。 */
@Mapper
public interface RawMessageMapper extends BaseMapper<RawMessageEntity> {
    /** 重复提交不覆盖原始证据，依赖来源内唯一约束保证幂等。 */
    @Insert("""
        INSERT INTO scanner_raw_message(id,source_id,dedup_key,payload,collector_origin)
        VALUES (#{row.id},#{row.sourceId},#{row.dedupKey},#{row.payload},#{row.collectorOrigin})
        ON CONFLICT(source_id,dedup_key) DO NOTHING
        """)
    int insertIfAbsent(@Param("row") RawMessageEntity row);

    /** 在调用方事务内锁定一条到期消息，跳过其他工作线程已持有的行锁。 */
    @Select("""
        SELECT * FROM scanner_raw_message
        WHERE status IN ('COLLECTED','RETRY_PENDING') AND next_attempt_at <= now()
        ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
        """)
    RawMessageEntity lockNext();
}
