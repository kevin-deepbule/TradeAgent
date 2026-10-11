package com.tradeagent.scanner.collector;

import java.util.List;
import com.tradeagent.scanner.domain.ScannerContracts.RawMessage;

/** 定义无状态来源采集扩展，由应用层持久化返回的证据。 */
public interface SourceCollector {
    /** 返回稳定的来源标识。 */
    String sourceId();
    /** 表示该来源是否只保存消息而不进入事件处理流水线。 */
    default boolean ingestionOnly() { return false; }
    /** 采集可用证据，通过稳定的外部 ID 保证重复采集幂等。 */
    List<RawMessage> collect();
}
