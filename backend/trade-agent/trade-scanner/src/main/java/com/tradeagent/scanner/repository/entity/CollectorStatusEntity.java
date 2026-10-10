package com.tradeagent.scanner.repository.entity;

import java.time.OffsetDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 映射 Collector 最近一次采集的时间、提交数量及错误。 */
@TableName("scanner_collector_status")
public class CollectorStatusEntity {
    @TableId(type = IdType.INPUT)
    public String sourceId;
    public OffsetDateTime lastRunAt;
    public Integer messageCount;
    public String lastError;
}
