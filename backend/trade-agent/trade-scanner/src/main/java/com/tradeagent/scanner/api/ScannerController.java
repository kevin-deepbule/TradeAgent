package com.tradeagent.scanner.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.tradeagent.scanner.domain.ScannerContracts.*;
import com.tradeagent.scanner.service.ScannerService;

/** 提供 Scanner 消息接入、证据状态和权威事件查询 API。 */
@RestController
@RequestMapping("/api/scanner")
public class ScannerController {
    private final ScannerService service;
    /** 注入 ScannerService，承接 API 请求。 */
    public ScannerController(ScannerService service) { this.service = service; }
    /** 接收不可变证据并返回消息 ID，供调用方查询异步处理进度。 */
    @PostMapping("/messages")
    public ResponseEntity<Map<String,UUID>> submit(@RequestBody RawMessage raw) {
        return ResponseEntity.accepted().body(Map.of("messageId", service.submit(raw)));
    }
    /** 返回消息处理进度及生成的事件 ID。 */
    @GetMapping("/messages/{id}")
    public Map<String,Object> message(@PathVariable UUID id) { return service.message(id); }
    /** 从已保存证据重新处理可重试的失败消息。 */
    @PostMapping("/messages/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id) { service.retry(id); return ResponseEntity.accepted().build(); }
    /** 按限定数量返回最近的事件快照。 */
    @GetMapping("/events")
    public List<EventRecord> events(@RequestParam(defaultValue = "20") int limit) { return service.events(limit); }
    /** 返回数据库注册的事件分类，便于调用方区分事件。 */
    @GetMapping("/event-types")
    public List<EventTypeRegistration> eventTypes() { return service.eventTypes(); }
    /** 读取最新事件或 MQ 引用的指定版本。 */
    @GetMapping("/events/{id}")
    public EventRecord event(@PathVariable UUID id, @RequestParam(required = false) Long version) { return service.event(id, version); }
    /** 返回事件证据对应的不可变原始消息 ID。 */
    @GetMapping("/events/{id}/evidence")
    public List<UUID> evidence(@PathVariable UUID id) { return service.evidence(id); }
    /** 汇总数据库中的 Pipeline 处理状态和发布积压。 */
    @GetMapping("/dashboard")
    public Map<String,Object> dashboard() { return service.dashboard(); }
    /** 返回 Collector 最近一次采集的持久化状态。 */
    @GetMapping("/collectors/status")
    public List<Map<String,Object>> collectors() { return service.collectors(); }
}
