package com.tradeagent.scanner.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 定义 Scanner 输入、中间候选结果和对外事件的版本化契约。 */
public final class ScannerContracts {
    private ScannerContracts() {}

    /** 承载原始来源证据，来源可信等级由服务端配置决定。 */
    public record RawMessage(String sourceId, String externalId, String title, String content,
                             String url, Instant publishedAt, List<String> symbols) {}

    /** 在插件处理过程中保留不可变的原始证据 ID。 */
    public record StandardMessage(UUID rawMessageId, RawMessage raw, String text,
                                  List<EventCandidate> candidates, List<String> trace) {}

    /** 承载规则抽取的候选事件，不将其视为已验证事实。 */
    public record EventCandidate(String type, String title, String summary, Instant occurredAt,
                                 List<String> symbols) {}

    /** 对外描述数据库中注册的事件分类及其分类法版本。 */
    public record EventTypeRegistration(String code, String name, String description,
                                        String taxonomyVersion, boolean enabled) {}

    /** 分别记录确定性评分、评分解释和验证状态。 */
    public record Score(int credibility, int impact, int relevance, int novelty, int persistence,
                        int total, String verification, List<String> reasons, String policyVersion) {}

    /** 提供权威事件快照，供 Research 查询。 */
    public record EventRecord(UUID id, String type, String title, String summary, Instant occurredAt,
                              List<String> symbols, Score score, long version, Instant updatedAt) {}

    /** 在队列消息中引用精确的不可变事件版本。 */
    public record ResearchTask(UUID messageId, UUID eventId, long eventVersion, int schemaVersion) {}
}
