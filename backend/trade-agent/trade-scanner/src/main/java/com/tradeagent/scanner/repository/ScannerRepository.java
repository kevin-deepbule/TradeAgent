package com.tradeagent.scanner.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tradeagent.scanner.repository.entity.*;
import com.tradeagent.scanner.repository.mapper.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;

/** 封装 MyBatis-Plus 数据访问，事务边界由 ScannerService 和 Publisher 管理。 */
@Repository
public class ScannerRepository {
    private final RawMessageMapper raw;
    private final EventMapper events;
    private final EventTypeMapper eventTypes;
    private final EventEvidenceMapper evidence;
    private final OutboxMapper outbox;
    private final CollectorStatusMapper collectors;
    private final DataSource dataSource;

    /** 注入模块 Mapper 和共享 DataSource，不重复创建 SqlSessionFactory。 */
    public ScannerRepository(RawMessageMapper raw, EventMapper events, EventTypeMapper eventTypes, EventEvidenceMapper evidence,
                             OutboxMapper outbox, CollectorStatusMapper collectors, DataSource dataSource) {
        this.raw = raw; this.events = events; this.eventTypes = eventTypes; this.evidence = evidence;
        this.outbox = outbox; this.collectors = collectors; this.dataSource = dataSource;
    }

    /** 使用模块 SQL 初始化已有和新数据库，业务 CRUD 全部交给 MyBatis-Plus。 */
    @PostConstruct
    public void initialize() {
        new ResourceDatabasePopulator(new ClassPathResource("scanner-schema.sql")).execute(dataSource);
    }

    /** 保存不可变消息并按外部 ID 或来源内幂等键读取稳定 ID。 */
    public UUID insertRaw(RawMessageEntity row) {
        raw.insertIfAbsent(row);
        if (row.externalId != null && !row.externalId.isBlank()) {
            return raw.selectOne(new QueryWrapper<RawMessageEntity>()
                .eq("external_id", row.externalId)).id;
        }
        return raw.selectOne(new QueryWrapper<RawMessageEntity>()
            .eq("source_id", row.sourceId).eq("dedup_key", row.dedupKey)).id;
    }

    /** 在调用方事务内领取下一条待处理消息。 */
    public RawMessageEntity lockNextRaw() { return raw.lockNext(); }

    /** 记录提取失败次数、状态和下一次尝试时间。 */
    public void failRaw(UUID id, int attempts, String error, Instant nextAttempt) {
        raw.update(null, new UpdateWrapper<RawMessageEntity>().eq("id", id)
            .set("status", attempts >= 5 ? "FAILED" : "RETRY_PENDING").set("attempts", attempts)
            .set("last_error", error).set("next_attempt_at", nextAttempt.atOffset(ZoneOffset.UTC)));
    }

    /** 完成消息处理并显式清空旧错误，避免默认非空字段策略遗漏 NULL 更新。 */
    public void completeRaw(UUID id, String trace) {
        raw.update(null, new UpdateWrapper<RawMessageEntity>().eq("id", id)
            .set("status", "COMPLETED").set("last_error", null).set("trace", trace));
    }

    /** 读取原始消息和处理状态，不存在时返回 null。 */
    public RawMessageEntity raw(UUID id) { return raw.selectById(id); }

    /** 仅重新领取失败或等待重试的消息，返回实际更新数量。 */
    public int retryRaw(UUID id) {
        return raw.update(null, new UpdateWrapper<RawMessageEntity>().eq("id", id)
            .in("status", "FAILED", "RETRY_PENDING").set("status", "COLLECTED").set("attempts", 0)
            .setSql("next_attempt_at=now()").set("last_error", null));
    }

    /** 幂等创建规范事件，再在当前事务内锁定指纹对应的记录。 */
    public EventEntity insertAndLockEvent(EventEntity row) {
        events.insertIfAbsent(row);
        return events.lockByFingerprint(row.fingerprint);
    }

    /** 判断候选事件类型是否已在数据库注册并启用。 */
    public boolean isEventTypeEnabled(String code) {
        return eventTypes.selectCount(new QueryWrapper<EventTypeEntity>()
            .eq("code", code).eq("enabled", true)) == 1;
    }

    /** 返回全部事件类型注册信息，供采集和运维确认分类边界。 */
    public List<EventTypeEntity> eventTypes() {
        return eventTypes.selectList(new QueryWrapper<EventTypeEntity>().orderByAsc("code"));
    }

    /** 更新锁定事件的最新快照和版本，保留数据库时间语义。 */
    public void updateEvent(UUID id, String payload, long version) {
        events.update(null, new UpdateWrapper<EventEntity>().eq("id", id)
            .set("payload", payload).set("version", version).setSql("updated_at=now()"));
    }

    /** 幂等追加证据，返回是否新增关系。 */
    public int addEvidence(UUID eventId, UUID rawId) { return evidence.insertSource(eventId, rawId); }

    /** 幂等保存不可变版本，不覆盖历史快照。 */
    public void saveRevision(UUID eventId, long version, String payload) { evidence.insertRevision(eventId, version, payload); }

    /** 使用 BaseMapper 查询最新快照或读取指定历史版本。 */
    public String eventPayload(UUID id, Long version) {
        if (version != null) return evidence.revision(id, version);
        EventEntity row = events.selectById(id);
        return row == null ? null : row.payload;
    }

    /** 返回有界事件列表，LIMIT 仅使用内部生成的整数。 */
    public List<EventEntity> recentEvents(int limit) {
        return events.selectList(new QueryWrapper<EventEntity>().orderByDesc("updated_at")
            .last("LIMIT " + Math.max(1, Math.min(100, limit))));
    }

    /** 返回原始消息对应的全部事件 ID。 */
    public List<UUID> eventIds(UUID rawId) { return evidence.eventIds(rawId); }

    /** 返回事件关联的全部证据 ID。 */
    public List<UUID> evidenceIds(UUID eventId) { return evidence.evidenceIds(eventId); }

    /** 在事件事务中保存 Outbox，唯一约束防止重复发布意图。 */
    public void saveOutbox(OutboxEntity row) { outbox.insertIfAbsent(row); }

    /** 在调用方发布事务内领取下一条 Outbox。 */
    public OutboxEntity lockNextOutbox() { return outbox.lockNext(); }

    /** 仅在 Broker 确认后标记已发布，并显式清空错误。 */
    public void markPublished(UUID id) {
        outbox.update(null, new UpdateWrapper<OutboxEntity>().eq("id", id)
            .set("status", "PUBLISHED").set("last_error", null));
    }

    /** 记录发送失败并安排退避重试，不重新执行事件处理。 */
    public void retryOutbox(UUID id, int attempts, String error, Instant nextAttempt) {
        outbox.update(null, new UpdateWrapper<OutboxEntity>().eq("id", id)
            .set("status", "RETRY").set("attempts", attempts).set("last_error", error)
            .set("next_attempt_at", nextAttempt.atOffset(ZoneOffset.UTC)));
    }

    /** 保存 Collector 最近结果，保持来源内原子 Upsert。 */
    public void recordCollector(String sourceId, Instant lastRun, int count, String error) {
        var row = new CollectorStatusEntity();
        row.sourceId = sourceId; row.lastRunAt = lastRun.atOffset(ZoneOffset.UTC);
        row.messageCount = count; row.lastError = error;
        collectors.upsert(row);
    }

    /** 保持 API 的数据库列名，返回各来源最近采集状态。 */
    public List<Map<String,Object>> collectors() {
        return collectors.selectMaps(new QueryWrapper<CollectorStatusEntity>().orderByAsc("source_id"));
    }

    /** 使用 MyBatis-Plus 聚合查询处理状态、发布积压和事件数量。 */
    public Map<String,Object> dashboard() {
        return Map.of("messages", raw.selectMaps(new QueryWrapper<RawMessageEntity>().select("status", "count(*) AS count").groupBy("status")),
            "outbox", outbox.selectMaps(new QueryWrapper<OutboxEntity>().select("status", "count(*) AS count").groupBy("status")),
            "eventCount", events.selectCount(null));
    }
}
