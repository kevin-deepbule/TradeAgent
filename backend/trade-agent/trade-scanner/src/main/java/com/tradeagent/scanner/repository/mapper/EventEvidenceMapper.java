package com.tradeagent.scanner.repository.mapper;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 维护复合主键的证据关系与不可变事件版本，不套用单主键 CRUD。 */
@Mapper
public interface EventEvidenceMapper {
    /** 幂等关联事件和原始消息，返回新增关系数量。 */
    @Insert("""
        INSERT INTO scanner_event_source(event_id,raw_message_id) VALUES (#{eventId},#{rawId})
        ON CONFLICT DO NOTHING
        """)
    int insertSource(@Param("eventId") UUID eventId, @Param("rawId") UUID rawId);

    /** 只插入历史版本，重复写入不覆盖快照。 */
    @Insert("""
        INSERT INTO scanner_event_revision(event_id,version,payload) VALUES (#{eventId},#{version},#{payload})
        ON CONFLICT DO NOTHING
        """)
    int insertRevision(@Param("eventId") UUID eventId, @Param("version") long version, @Param("payload") String payload);

    /** 读取精确版本，供 Research 按 MQ 引用获取输入。 */
    @Select("SELECT payload FROM scanner_event_revision WHERE event_id=#{eventId} AND version=#{version}")
    String revision(@Param("eventId") UUID eventId, @Param("version") long version);

    /** 返回原始消息生成的事件 ID。 */
    @Select("SELECT event_id FROM scanner_event_source WHERE raw_message_id=#{rawId}")
    List<UUID> eventIds(@Param("rawId") UUID rawId);

    /** 返回事件的原始证据 ID。 */
    @Select("SELECT raw_message_id FROM scanner_event_source WHERE event_id=#{eventId}")
    List<UUID> evidenceIds(@Param("eventId") UUID eventId);
}
