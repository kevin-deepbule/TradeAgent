package com.tradeagent.scanner.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeagent.scanner.domain.ScannerContracts.*;
import com.tradeagent.scanner.pipeline.MessagePipeline;
import com.tradeagent.scanner.scoring.EventScorer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.tradeagent.scanner.repository.ScannerRepository;
import com.tradeagent.scanner.repository.entity.EventEntity;
import com.tradeagent.scanner.repository.entity.OutboxEntity;
import com.tradeagent.scanner.repository.entity.RawMessageEntity;

/** 编排 Scanner 持久化流程，支持证据去重、处理重放及事件与 Outbox 的原子写入。 */
@Service
public class ScannerService {
    private final ScannerRepository repository;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final MessagePipeline pipeline;
    private final EventScorer scorer;

    /** 注入数据库事务和无状态处理组件。 */
    public ScannerService(ScannerRepository repository, ObjectMapper json, TransactionTemplate transactions,
                          MessagePipeline pipeline, EventScorer scorer) {
        this.repository = repository; this.json = json; this.transactions = transactions;
        this.pipeline = pipeline; this.scorer = scorer;
    }

    /** 抽取前先保存原始证据，重复提交时返回同一 ID。 */
    public UUID submit(RawMessage raw) {
        return persist(raw, false);
    }

    /** 接收服务端 Collector 的消息；HTTP 提交不继承官方来源可信等级。 */
    public UUID submitCollected(RawMessage raw) {
        return persist(raw, true);
    }

    /** 校验并保存不可变证据，记录服务端确定的来源类型。 */
    private UUID persist(RawMessage raw, boolean collectorOrigin) {
        if (raw == null || raw.sourceId() == null || raw.sourceId().isBlank()
            || raw.title() == null || raw.content() == null || raw.publishedAt() == null
            || raw.symbols() == null || raw.symbols().stream().anyMatch(s -> s == null || !s.matches("\\d{6}"))
            || raw.sourceId().length() > 100 || raw.title().length() > 1000 || raw.content().length() > 200000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source, text, timestamp or A-share symbols");
        }
        String key = raw.externalId() != null && !raw.externalId().isBlank()
            ? "id:" + raw.externalId() : "hash:" + hash(raw.title() + "\n" + raw.content());
        key = (collectorOrigin ? "collector:" : "manual:") + key;
        var row = new RawMessageEntity();
        row.id = UUID.randomUUID(); row.sourceId = raw.sourceId(); row.dedupKey = key;
        row.payload = encode(raw); row.collectorOrigin = collectorOrigin;
        return repository.insertRaw(row);
    }

    /** 按限定批量处理消息，通过行锁避免多个工作线程重复处理同一证据。 */
    public void processBatch() {
        for (int i = 0; i < 20; i++) {
            Boolean processed = transactions.execute(tx -> {
                var row = repository.lockNextRaw();
                if (row == null) return false;
                UUID id = row.id;
                // 先抽取再写入事件，避免插件失败时留下部分事件，并保留失败记录。
                StandardMessage message;
                try {
                    message = pipeline.execute(id, decode(row.payload, RawMessage.class));
                    for (var candidate : message.candidates()) validateCandidate(candidate);
                }
                catch (RuntimeException error) {
                    int attempts = row.attempts + 1;
                    repository.failRaw(id, attempts, error.getClass().getSimpleName(),
                        Instant.now().plusSeconds(Math.min(3600, 30L << attempts)));
                    return true;
                }
                for (var candidate : message.candidates()) saveCandidate(id,
                    Boolean.TRUE.equals(row.collectorOrigin) ? message.raw().sourceId() : "", candidate);
                repository.completeRaw(id, encode(message.trace()));
                return true;
            });
            if (!Boolean.TRUE.equals(processed)) break;
        }
    }

    /** 合并相同候选事件，原子保存值得研究的版本及发布意图，不以验证状态作为门槛。 */
    private void saveCandidate(UUID rawId, String sourceId, EventCandidate candidate) {
        validateCandidate(candidate);
        if (!repository.isEventTypeEnabled(candidate.type())) {
            throw new IllegalArgumentException("Unregistered or disabled event type: " + candidate.type());
        }
        String fingerprint = hash(candidate.type() + "|" + candidate.symbols().stream().sorted().toList()
            + "|" + candidate.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate() + "|" + candidate.title() + "|" + candidate.summary());
        UUID proposed = UUID.randomUUID();
        Score score = scorer.score(candidate, sourceId);
        var initial = new EventRecord(proposed, candidate.type(), candidate.title(), candidate.summary(), candidate.occurredAt(), candidate.symbols(), score, 1, Instant.now());
        var draft = new EventEntity();
        draft.id = proposed; draft.fingerprint = fingerprint; draft.eventType = candidate.type();
        draft.payload = encode(initial); draft.version = 1L;
        var row = repository.insertAndLockEvent(draft);
        UUID eventId = row.id;
        int added = repository.addEvidence(eventId, rawId);
        EventRecord existing = decode(row.payload, EventRecord.class);
        EventRecord event = existing;
        if (!eventId.equals(proposed) && added > 0 && score.total() > existing.score().total()) {
            event = new EventRecord(eventId, candidate.type(), candidate.title(), candidate.summary(), candidate.occurredAt(), candidate.symbols(), score, existing.version() + 1, Instant.now());
            repository.updateEvent(eventId, encode(event), event.version());
        }
        repository.saveRevision(eventId, event.version(), encode(event));
        if (event.score().total() >= 70 && !event.symbols().isEmpty()) {
            UUID messageId = UUID.randomUUID();
            var publication = new OutboxEntity();
            publication.id = messageId; publication.eventId = eventId; publication.eventVersion = event.version();
            publication.payload = encode(new ResearchTask(messageId, eventId, event.version(), 1));
            repository.saveOutbox(publication);
        }
    }

    /** 写入事件前校验插件输出，拒绝格式不合法的候选结果。 */
    private void validateCandidate(EventCandidate candidate) {
        if (candidate.type() == null || candidate.title() == null || candidate.summary() == null
            || candidate.occurredAt() == null || candidate.symbols() == null
            || candidate.symbols().stream().anyMatch(s -> s == null || !s.matches("\\d{6}"))) {
            throw new IllegalArgumentException("Invalid event candidate");
        }
    }

    /** 按限定数量读取最近的不可变事件快照。 */
    public List<EventRecord> events(int limit) {
        return repository.recentEvents(limit).stream().map(row -> decode(row.payload, EventRecord.class)).toList();
    }

    /** 返回数据库中注册的事件类型及其启用状态。 */
    public List<EventTypeRegistration> eventTypes() {
        return repository.eventTypes().stream().map(row -> new EventTypeRegistration(
            row.code, row.name, row.description, row.taxonomyVersion, Boolean.TRUE.equals(row.enabled))).toList();
    }

    /** 读取最新事件或指定版本，不存在时返回错误。 */
    public EventRecord event(UUID id, Long version) {
        String payload = repository.eventPayload(id, version);
        if (payload == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        return decode(payload, EventRecord.class);
    }

    /** 返回原始证据、处理状态及生成的事件 ID。 */
    public Map<String,Object> message(UUID id) {
        var row = repository.raw(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        var result = new java.util.HashMap<String,Object>();
        result.put("id", row.id); result.put("status", row.status); result.put("attempts", row.attempts);
        result.put("last_error", row.lastError); result.put("trace", row.trace); result.put("collector_origin", row.collectorOrigin);
        result.put("rawMessage", decode(row.payload, RawMessage.class));
        result.put("eventIds", repository.eventIds(id));
        return result;
    }

    /** 返回事件关联的原始证据 ID，不复制或改写来源数据。 */
    public List<UUID> evidence(UUID eventId) {
        event(eventId, null);
        return repository.evidenceIds(eventId);
    }

    /** 仅重置可重试的失败消息，保留已完成结果和发布幂等性。 */
    public void retry(UUID id) {
        int changed = repository.retryRaw(id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Message is missing or not retryable");
    }

    /** 汇总数据库中的处理状态与发布积压。 */
    public Map<String,Object> dashboard() {
        return repository.dashboard();
    }

    /** 返回各来源最近一次持久化的采集结果。 */
    public List<Map<String,Object>> collectors() {
        return repository.collectors();
    }

    /** 使用应用的 ObjectMapper 序列化快照，保留时间字段。 */
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Cannot serialize scanner contract", e); }
    }
    /** 反序列化已持久化的版本快照。 */
    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException("Invalid scanner snapshot", e); }
    }
    /** 生成稳定、固定长度的去重指纹。 */
    private String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
