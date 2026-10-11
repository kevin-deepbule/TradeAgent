package com.tradeagent.scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeagent.scanner.domain.ScannerContracts.*;
import com.tradeagent.scanner.pipeline.*;
import com.tradeagent.scanner.scoring.EventScorer;
import com.tradeagent.scanner.service.ScannerService;
import com.tradeagent.scanner.repository.ScannerRepository;
import com.tradeagent.scanner.repository.entity.RawMessageEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 验证保守抽取、不可变证据边界和消息持久化入口校验。 */
class ScannerPipelineTest {
    /** 构造包含多个事件和明确发布时间的公告证据。 */
    private RawMessage raw() {
        return new RawMessage("exchange", "notice-1", "回购与业绩预告", "净利润增长，计划回购股份",
            "https://example.org/notice", Instant.parse("2026-10-07T07:00:00Z"), List.of("000001"));
    }

    /** 验证一条公告可产生多个候选事件，同一类型不重复抽取。 */
    @Test
    void splitsTypesAndRetainsEvidence() {
        UUID id = UUID.randomUUID();
        var result = new MessagePipeline(List.of(new RuleExtractionPlugin())).execute(id, raw());
        assertEquals(id, result.rawMessageId());
        assertEquals(List.of("EARNINGS", "BUYBACK"), result.candidates().stream().map(EventCandidate::type).toList());
        assertEquals(List.of("rule-extract:v1"), result.trace());
    }

    /** 验证未匹配关键词的新闻不会生成虚构事件。 */
    @Test
    void unknownTextProducesNoCandidate() {
        var raw = new RawMessage("news", null, "公司动态", "召开交流会", null, Instant.now(), List.of());
        assertTrue(new MessagePipeline(List.of(new RuleExtractionPlugin())).execute(UUID.randomUUID(), raw).candidates().isEmpty());
    }

    /** 验证插件不能在转换过程中替换原始证据。 */
    @Test
    void rejectsEvidenceReplacement() {
        var plugin = mock(MessagePlugin.class);
        when(plugin.id()).thenReturn("bad:v1");
        when(plugin.process(any())).thenAnswer(call -> {
            StandardMessage input = call.getArgument(0);
            return new StandardMessage(UUID.randomUUID(), input.raw(), input.text(), List.of(), List.of());
        });
        assertThrows(IllegalStateException.class, () -> new MessagePipeline(List.of(plugin)).execute(UUID.randomUUID(), raw()));
    }

    /** 验证来源名称只有匹配配置的可信策略时才被标记为 CONFIRMED。 */
    @Test
    void trustComesFromConfiguration() {
        var candidate = new EventCandidate("EARNINGS", "业绩预告", "净利润", Instant.now(), List.of("000001"));
        var untrusted = new EventScorer(List.of()).score(candidate, "exchange");
        assertEquals("UNVERIFIED", untrusted.verification());
        assertTrue(untrusted.total() < 70);
        var trusted = new EventScorer(List.of("exchange")).score(candidate, "exchange");
        assertEquals("CONFIRMED", trusted.verification());
        assertEquals(75, trusted.total());
    }

    /** 验证入口先保存证据，不同步执行事件抽取。 */
    @Test
    void intakeIsDurableBeforeProcessing() {
        var repository = mock(ScannerRepository.class);
        var pipeline = mock(MessagePipeline.class);
        UUID id = UUID.randomUUID();
        when(repository.insertRaw(any())).thenReturn(id);
        var service = new ScannerService(repository, new ObjectMapper().findAndRegisterModules(),
            mock(TransactionTemplate.class), pipeline, new EventScorer(List.of()));
        assertEquals(id, service.submit(raw()));
        verify(repository).insertRaw(argThat(row -> row.sourceId.equals("exchange")
            && row.dedupKey.equals("manual:id:notice-1") && !row.collectorOrigin));
        verifyNoInteractions(pipeline);
        assertThrows(ResponseStatusException.class, () -> service.submit(
            new RawMessage("exchange", null, "test", "test", null, Instant.now(), List.of("invalid"))));
    }

    /** 验证仅采集资讯按 externalId 保存后直接完成，不调用事件处理流水线。 */
    @Test
    void ingestionOnlyNewsSkipsPipeline() {
        var repository = mock(ScannerRepository.class);
        var pipeline = mock(MessagePipeline.class);
        var transactions = mock(TransactionTemplate.class);
        UUID id = UUID.randomUUID();
        when(repository.insertRaw(any())).thenReturn(id);
        when(transactions.execute(any())).thenAnswer(call -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });
        var service = new ScannerService(repository, new ObjectMapper().findAndRegisterModules(),
            transactions, pipeline, new EventScorer(List.of()));
        var news = new RawMessage("cls-news", "external-1", "标题", "正文", null,
            Instant.parse("2026-10-11T07:00:00Z"), List.of());

        assertEquals(id, service.submitCollectedOnly(news));

        verify(repository).insertRaw(argThat(row -> row.externalId.equals("external-1")
            && row.sourceId.equals("cls-news") && row.collectorOrigin));
        verify(repository).completeRaw(id, "[\"ingestion-only\"]");
        verifyNoInteractions(pipeline);
    }

    /** 验证发布不依赖验证状态，但仍要求达到评分阈值且关联股票。 */
    @ParameterizedTest
    @CsvSource({"CONFIRMED,75,true,true", "UNVERIFIED,70,true,true",
        "UNVERIFIED,69,true,false", "UNVERIFIED,75,false,false"})
    void publicationDependsOnScoreAndStocks(String verification, int total, boolean hasStocks, boolean publish) {
        var repository = mock(ScannerRepository.class);
        var transactions = mock(TransactionTemplate.class);
        var json = new ObjectMapper().findAndRegisterModules();
        UUID rawId = UUID.randomUUID();
        RawMessage evidence = raw();
        if (!hasStocks) {
            evidence = new RawMessage(evidence.sourceId(), evidence.externalId(), evidence.title(), evidence.content(),
                evidence.url(), evidence.publishedAt(), List.of());
        }
        when(transactions.execute(any())).thenAnswer(call -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });
        try {
            var row = new RawMessageEntity();
            row.id = rawId; row.payload = json.writeValueAsString(evidence); row.attempts = 0; row.collectorOrigin = false;
            when(repository.lockNextRaw()).thenReturn(row, null);
        } catch (Exception e) { throw new AssertionError(e); }
        when(repository.insertAndLockEvent(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.isEventTypeEnabled(anyString())).thenReturn(true);
        when(repository.addEvidence(any(), any())).thenReturn(1);
        // 使用固定评分隔离发布条件测试，避免依赖当前评分权重。
        var scorer = mock(EventScorer.class);
        when(scorer.score(any(), anyString())).thenReturn(
            new Score(5, 30, 20, 10, total - 65, total, verification, List.of("test policy"), "test:v1"));
        var service = new ScannerService(repository, json, transactions, new MessagePipeline(List.of(new RuleExtractionPlugin())), scorer);
        service.processBatch();
        verify(repository, times(2)).saveRevision(any(), eq(1L), any());
        verify(repository, times(publish ? 2 : 0)).saveOutbox(any());
        verify(repository).completeRaw(eq(rawId), any());
    }

    /** 验证插件失败后安排重试，不创建事件或发布记录。 */
    @Test
    void pluginFailureSchedulesRetryWithoutPublishing() throws Exception {
        var repository = mock(ScannerRepository.class);
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });
        UUID id = UUID.randomUUID();
        var json = new ObjectMapper().findAndRegisterModules();
        var row = new RawMessageEntity();
        row.id = id; row.payload = json.writeValueAsString(raw()); row.attempts = 0;
        when(repository.lockNextRaw()).thenReturn(row, null);
        var pipeline = mock(MessagePipeline.class);
        when(pipeline.execute(eq(id), any())).thenThrow(new IllegalStateException("temporary"));
        new ScannerService(repository, json, transactions, pipeline, new EventScorer(List.of())).processBatch();
        verify(repository).failRaw(eq(id), eq(1), eq("IllegalStateException"), any());
        verify(repository, never()).saveOutbox(any());
    }
}
