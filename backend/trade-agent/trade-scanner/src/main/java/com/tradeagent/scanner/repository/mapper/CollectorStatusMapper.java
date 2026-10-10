package com.tradeagent.scanner.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradeagent.scanner.repository.entity.CollectorStatusEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Collector 状态 Mapper，原子保存各来源最近一次运行结果。 */
@Mapper
public interface CollectorStatusMapper extends BaseMapper<CollectorStatusEntity> {
    /** 覆盖最近运行结果，包括将上次错误清空。 */
    @Insert("""
        INSERT INTO scanner_collector_status(source_id,last_run_at,message_count,last_error)
        VALUES (#{row.sourceId},#{row.lastRunAt},#{row.messageCount},#{row.lastError,jdbcType=VARCHAR})
        ON CONFLICT(source_id) DO UPDATE SET last_run_at=excluded.last_run_at,
        message_count=excluded.message_count,last_error=excluded.last_error
        """)
    int upsert(@Param("row") CollectorStatusEntity row);
}
