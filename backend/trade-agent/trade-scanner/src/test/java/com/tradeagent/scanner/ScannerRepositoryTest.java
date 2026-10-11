package com.tradeagent.scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeagent.scanner.domain.ScannerContracts.*;
import com.tradeagent.scanner.pipeline.MessagePipeline;
import com.tradeagent.scanner.pipeline.RuleExtractionPlugin;
import com.tradeagent.scanner.repository.ScannerRepository;
import com.tradeagent.scanner.repository.entity.*;
import com.tradeagent.scanner.scoring.EventScorer;
import com.tradeagent.scanner.service.ScannerService;
import com.tradeagent.watchlist.repository.mapper.WatchlistMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用测试专属 PostgreSQL 验证真实 Mapper、事务、行锁与 Watchlist XML 兼容性。 */
@SpringJUnitConfig(ScannerRepositoryTest.DatabaseConfiguration.class)
@TestPropertySource(properties = {
    "mybatis-plus.mapper-locations=classpath*:mybatis/*.xml",
    "mybatis-plus.type-handlers-package=com.tradeagent.scanner.repository.typehandler"
})
class ScannerRepositoryTest {
    /** 仅为测试启动临时 PostgreSQL，关闭 Spring Context 时回收数据库进程。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
    @Import(ScannerRepository.class)
    @MapperScan({"com.tradeagent.scanner.repository.mapper", "com.tradeagent.watchlist.repository.mapper"})
    static class DatabaseConfiguration {
        /** 提供无需 Docker 的测试 PostgreSQL。 */
        @Bean(destroyMethod = "close")
        EmbeddedPostgres postgres() throws Exception { return EmbeddedPostgres.start(); }
        /** 提供所有 Mapper 与事务共享的测试 DataSource。 */
        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) { return postgres.getPostgresDatabase(); }
    }

    @Autowired private ScannerRepository repository;
    @Autowired private TransactionTemplate transactions;
    @Autowired private DataSource dataSource;
    @Autowired private WatchlistMapper watchlist;

    /** 只清理本测试创建的临时数据库，不连接应用数据库。 */
    @BeforeEach
    void clearTestDatabase() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE scanner_outbox,scanner_event_source,scanner_event_revision,scanner_event,scanner_raw_message,scanner_collector_status");
        }
    }

    /** 构造采用外部 ID 去重的原始消息行。 */
    private RawMessageEntity raw(String key) {
        var row = new RawMessageEntity();
        row.id = UUID.randomUUID(); row.sourceId = "test"; row.dedupKey = key;
        row.payload = "original"; row.collectorOrigin = false;
        return row;
    }

    /** 构造规范事件行，保持与应用输入相同的 UUID 类型。 */
    private EventEntity event(String fingerprint) {
        var row = new EventEntity();
        row.id = UUID.randomUUID(); row.fingerprint = fingerprint; row.eventType = "BUYBACK";
        row.payload = "v1"; row.version = 1L;
        return row;
    }

    /** 构造精确引用事件版本的 Outbox 行。 */
    private OutboxEntity outbox(UUID eventId, long version) {
        var row = new OutboxEntity();
        row.id = UUID.randomUUID(); row.eventId = eventId; row.eventVersion = version; row.payload = "{}";
        return row;
    }

    /** 相同幂等键返回旧 ID，保留证据并正确读取 UUID、默认状态与时间。 */
    @Test
    void duplicateIntakePreservesEvidenceAndDatabaseDefaults() {
        var first = raw("duplicate");
        UUID id = repository.insertRaw(first);
        var duplicate = raw("duplicate");
        duplicate.payload = "changed";
        assertEquals(id, repository.insertRaw(duplicate));
        var saved = repository.raw(id);
        assertEquals("original", saved.payload);
        assertEquals("COLLECTED", saved.status);
        assertEquals(0, saved.attempts);
        assertNotNull(saved.createdAt);
        assertFalse(saved.collectorOrigin);
    }

    /** externalId 在不同来源间仍保持唯一，并返回首次保存的消息 ID。 */
    @Test
    void externalIdIsTheGlobalNewsDeduplicationKey() {
        var first = raw("first-key");
        first.externalId = "shared-external-id";
        UUID id = repository.insertRaw(first);
        var duplicate = raw("second-key");
        duplicate.sourceId = "another-source";
        duplicate.externalId = "shared-external-id";

        assertEquals(id, repository.insertRaw(duplicate));
        assertEquals("test", repository.raw(id).sourceId);
    }

    /** 初始化脚本注册稳定分类，并允许数据库按类型区分事件。 */
    @Test
    void eventTypesAreRegisteredAndStoredOnEvents() {
        assertTrue(repository.isEventTypeEnabled("BUYBACK"));
        assertFalse(repository.isEventTypeEnabled("UNKNOWN"));
        assertEquals(6, repository.eventTypes().size());
        var saved = transactions.execute(tx -> repository.insertAndLockEvent(event("typed-event")));
        assertNotNull(saved);
        assertEquals("BUYBACK", saved.eventType);
    }

    /** 原子事务失败时事件、证据、历史版本与发布意图一起回滚。 */
    @Test
    void eventAndOutboxRollbackTogether() {
        UUID rawId = repository.insertRaw(raw("rollback"));
        var draft = event("rollback");
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(tx -> {
            var saved = repository.insertAndLockEvent(draft);
            repository.addEvidence(saved.id, rawId);
            repository.saveRevision(saved.id, 1, saved.payload);
            repository.saveOutbox(outbox(saved.id, 1));
            repository.completeRaw(rawId, "[]");
            throw new IllegalStateException("rollback");
        }));
        assertNull(repository.eventPayload(draft.id, null));
        assertNull(repository.eventPayload(draft.id, 1L));
        assertTrue(repository.eventIds(rawId).isEmpty());
        assertNull(transactions.execute(tx -> repository.lockNextOutbox()));
        assertEquals("COLLECTED", repository.raw(rawId).status);
    }

    /** 合并保留规范 ID，证据和版本写入幂等，重复 Outbox 不重置已发布状态。 */
    @Test
    void eventEvidenceRevisionsAndOutboxRemainIdempotent() {
        UUID rawId = repository.insertRaw(raw("merge"));
        transactions.executeWithoutResult(tx -> {
            var saved = repository.insertAndLockEvent(event("same"));
            assertEquals(saved.id, repository.insertAndLockEvent(event("same")).id);
            assertEquals(1, repository.addEvidence(saved.id, rawId));
            assertEquals(0, repository.addEvidence(saved.id, rawId));
            repository.saveRevision(saved.id, 1, "v1");
            repository.saveRevision(saved.id, 1, "must-not-overwrite");
            repository.updateEvent(saved.id, "v2", 2);
            repository.saveRevision(saved.id, 2, "v2");
            assertEquals("v1", repository.eventPayload(saved.id, 1L));
            assertEquals("v2", repository.eventPayload(saved.id, null));
            var publication = outbox(saved.id, 2);
            repository.saveOutbox(publication);
            repository.markPublished(publication.id);
            repository.saveOutbox(outbox(saved.id, 2));
            assertNull(repository.lockNextOutbox());
        });
        assertEquals(1L, repository.dashboard().get("eventCount"));
    }

    /** Wrapper 必须显式更新 NULL，避免成功后留下旧错误。 */
    @Test
    void retryAndSuccessClearPreviousErrors() {
        UUID rawId = repository.insertRaw(raw("retry"));
        repository.failRaw(rawId, 5, "failure", Instant.now().plusSeconds(60));
        assertEquals("FAILED", repository.raw(rawId).status);
        assertEquals(1, repository.retryRaw(rawId));
        assertNull(repository.raw(rawId).lastError);
        assertEquals(0, repository.raw(rawId).attempts);
        repository.completeRaw(rawId, "[]");
        assertEquals(0, repository.retryRaw(rawId));
        assertEquals("[]", repository.raw(rawId).trace);
        repository.recordCollector("test", Instant.now(), 1, "failure");
        repository.recordCollector("test", Instant.now(), 2, null);
        var status = repository.collectors().get(0);
        assertEquals(2, status.get("message_count"));
        assertNull(status.get("last_error"));
        transactions.executeWithoutResult(tx -> {
            var saved = repository.insertAndLockEvent(event("publish-retry"));
            var publication = outbox(saved.id, 1);
            repository.saveOutbox(publication);
            repository.retryOutbox(publication.id, 1, "failure", Instant.now().minusSeconds(1));
            assertEquals("failure", repository.lockNextOutbox().lastError);
            repository.markPublished(publication.id);
            assertNull(repository.lockNextOutbox());
        });
        assertTrue(repository.dashboard().get("outbox").toString().contains("PUBLISHED"));
    }

    /** 独立连接必须跳过其他事务持有的原始消息与 Outbox 行锁。 */
    @Test
    void concurrentWorkersSkipLockedRows() throws Exception {
        repository.insertRaw(raw("locked"));
        var executor = Executors.newSingleThreadExecutor();
        try {
            transactions.executeWithoutResult(tx -> {
                assertNotNull(repository.lockNextRaw());
                var next = executor.submit(() -> transactions.execute(other -> repository.lockNextRaw()));
                assertNull(assertDoesNotThrow(() -> next.get(5, TimeUnit.SECONDS)));
                var event = repository.insertAndLockEvent(event("locked-outbox"));
                repository.saveOutbox(outbox(event.id, 1));
            });
            transactions.executeWithoutResult(tx -> {
                assertNotNull(repository.lockNextOutbox());
                var next = executor.submit(() -> transactions.execute(other -> repository.lockNextOutbox()));
                assertNull(assertDoesNotThrow(() -> next.get(5, TimeUnit.SECONDS)));
            });
        } finally { executor.shutdownNow(); }
    }

    /** MyBatis-Plus 的共享 SqlSessionFactory 继续加载已有 Watchlist XML。 */
    @Test
    void existingWatchlistXmlStillWorks() {
        watchlist.createWatchlistTable();
        watchlist.ensureTimestampDefaults();
        watchlist.insertWatchlistIfAbsent("000001", "平安银行");
        var rows = watchlist.listWatchlist();
        assertEquals("000001", rows.get(0).symbol);
        assertFalse(rows.get(0).created_at.isBlank());
    }

    /** 从 API 数据契约到 Mapper 的完整处理生成可查询事件与 Outbox。 */
    @Test
    void pipelinePersistsUnverifiedResearchCandidate() {
        var json = new ObjectMapper().findAndRegisterModules();
        var scorer = mock(EventScorer.class);
        when(scorer.score(any(), anyString())).thenReturn(
            new Score(5, 30, 20, 10, 5, 70, "UNVERIFIED", List.of("test"), "test:v1"));
        var service = new ScannerService(repository, json, transactions,
            new MessagePipeline(List.of(new RuleExtractionPlugin())), scorer);
        var raw = new RawMessage("manual", "pipeline", "股份回购", "公司发布回购计划", null, Instant.now(), List.of("000001"));
        UUID id = service.submit(raw);
        service.processBatch();
        assertEquals("COMPLETED", service.message(id).get("status"));
        assertNull(service.message(id).get("last_error"));
        var event = service.events(20).get(0);
        assertEquals("UNVERIFIED", event.score().verification());
        assertEquals(event, service.event(event.id(), 1L));
        assertEquals(List.of(id), service.evidence(event.id()));
        transactions.executeWithoutResult(tx -> {
            var publication = repository.lockNextOutbox();
            assertNotNull(publication);
            assertEquals(event.id(), publication.eventId);
            assertEquals(1L, publication.eventVersion);
        });
    }
}
