package com.tradeagent.scanner.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradeagent.scanner.repository.entity.EventEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 事件 Mapper，以指纹唯一约束和行锁保护并发合并。 */
@Mapper
public interface EventMapper extends BaseMapper<EventEntity> {
    /** 指纹已存在时保留当前事件，不覆盖已有快照。 */
    @Insert("""
        INSERT INTO scanner_event(id,fingerprint,event_type,payload,version)
        VALUES (#{row.id},#{row.fingerprint},#{row.eventType},#{row.payload},#{row.version})
        ON CONFLICT(fingerprint) DO NOTHING
        """)
    int insertIfAbsent(@Param("row") EventEntity row);

    /** 在调用方事务内锁定规范事件，串行执行版本和评分更新。 */
    @Select("SELECT * FROM scanner_event WHERE fingerprint=#{fingerprint} FOR UPDATE")
    EventEntity lockByFingerprint(@Param("fingerprint") String fingerprint);
}
