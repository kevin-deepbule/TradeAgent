package com.tradeagent.scanner.repository.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 映射 Scanner 原始证据及其处理状态，JSON 契约由应用服务解析。 */
@TableName("scanner_raw_message")
public class RawMessageEntity {
    @TableId(type = IdType.INPUT)
    public UUID id;
    public String sourceId;
    public String dedupKey;
    public String payload;
    public String status;
    public Integer attempts;
    public OffsetDateTime nextAttemptAt;
    public String lastError;
    public String trace;
    public OffsetDateTime createdAt;
    public Boolean collectorOrigin;
}
