package com.tradeagent.scanner.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.tradeagent.scanner.service.ScannerService;

/** 按限定批量处理持久化证据，通过数据库锁支持多个工作线程。 */
@Component
public class ScannerJob {
    private final ScannerService service;
    /** 注入负责事务编排的应用服务。 */
    public ScannerJob(ScannerService service) { this.service = service; }
    /** 推进待处理证据，由 Spring 记录数据库异常。 */
    @Scheduled(fixedDelayString = "${tradeagent.scanner.process-delay-ms:2000}")
    public void process() { service.processBatch(); }
}
