package com.tradeagent.scanner.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradeagent.scanner.repository.entity.EventTypeEntity;
import org.apache.ibatis.annotations.Mapper;

/** 读取数据库中注册的 Scanner 事件类型。 */
@Mapper
public interface EventTypeMapper extends BaseMapper<EventTypeEntity> {
}
