package com.tradeagent.scanner.repository.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 映射事务 Outbox 的消息内容、发布状态和退避信息。 */
@TableName("scanner_outbox")
public class OutboxEntity {
    @TableId(type = IdType.INPUT)
    public UUID id;
    public UUID eventId;
    public Long eventVersion;
    public String payload;
    public String status;
    public Integer attempts;
    public OffsetDateTime nextAttemptAt;
    public String lastError;
    public OffsetDateTime createdAt;
}
