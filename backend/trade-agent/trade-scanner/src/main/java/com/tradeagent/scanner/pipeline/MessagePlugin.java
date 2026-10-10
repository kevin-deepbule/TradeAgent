package com.tradeagent.scanner.pipeline;

import com.tradeagent.scanner.domain.ScannerContracts.StandardMessage;

/** 定义由 Spring 排序的无状态处理插件，失败后从不可变输入重试。 */
public interface MessagePlugin {
    /** 返回插件及契约版本标识，用于处理审计。 */
    String id();
    /** 转换消息并保留原始证据身份。 */
    StandardMessage process(StandardMessage message);
}
