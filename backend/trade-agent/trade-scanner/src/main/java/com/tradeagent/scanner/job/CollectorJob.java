package com.tradeagent.scanner.job;

import java.time.Instant;
import java.util.List;
import com.tradeagent.scanner.repository.ScannerRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.collector.SourceCollector;
import com.tradeagent.scanner.service.ScannerService;

/** 隔离各来源采集失败，独立于消息处理保存 Collector 状态。 */
@Component
public class CollectorJob {
    private final List<SourceCollector> collectors;
    private final ScannerService service;
    private final ScannerRepository repository;
    /** 注入来源采集扩展、消息持久化服务和 MyBatis-Plus Repository。 */
    public CollectorJob(List<SourceCollector> collectors, ScannerService service, ScannerRepository repository) {
        this.collectors = collectors; this.service = service; this.repository = repository;
    }
    /** 保存采集证据并记录来源失败，避免影响其他 Collector。 */
    @Scheduled(initialDelayString = "${tradeagent.scanner.collect-delay-ms:60000}", fixedDelayString = "${tradeagent.scanner.collect-delay-ms:60000}")
    public void collect() {
        for (var collector : collectors) {
            String error = null;
            int count = 0;
            try {
                for (var raw : collector.collect()) {
                    if (!collector.sourceId().equals(raw.sourceId())) throw new IllegalArgumentException("Collector source mismatch");
                    service.submitCollected(raw); count++;
                }
            } catch (RuntimeException failure) { error = failure.getClass().getSimpleName(); }
            repository.recordCollector(collector.sourceId(), Instant.now(), count, error);
        }
    }
}
