package com.tradeagent.scanner.repository.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 映射事件最新快照；历史版本通过独立 Mapper 保持不可变。 */
@TableName("scanner_event")
public class EventEntity {
    @TableId(type = IdType.INPUT)
    public UUID id;
    public String fingerprint;
    public String eventType;
    public String payload;
    public Long version;
    public OffsetDateTime updatedAt;
}
