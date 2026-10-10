package com.tradeagent.scanner.repository.entity;

import java.time.OffsetDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 映射数据库注册的事件类型及其分类版本和启用状态。 */
@TableName("scanner_event_type")
public class EventTypeEntity {
    @TableId(type = IdType.INPUT)
    public String code;
    public String name;
    public String description;
    public String taxonomyVersion;
    public Boolean enabled;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
