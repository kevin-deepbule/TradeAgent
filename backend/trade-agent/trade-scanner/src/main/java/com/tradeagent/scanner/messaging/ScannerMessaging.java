package com.tradeagent.scanner.messaging;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.tradeagent.scanner.repository.ScannerRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** 通过事务 Outbox 发布消息，并检查 Broker Confirm 和 mandatory 路由返回。 */
@Configuration
public class ScannerMessaging {
    private final ScannerRepository repository;
    private final TransactionTemplate transactions;
    private final RabbitTemplate rabbit;
    /** 创建独立 RabbitTemplate 并启用 mandatory，不修改其他模块的实例。 */
    public ScannerMessaging(ScannerRepository repository, TransactionTemplate transactions,
                            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        this.repository = repository; this.transactions = transactions;
        this.rabbit = new RabbitTemplate(connectionFactory);
        this.rabbit.setMandatory(true);
    }

    /** 声明持久化 Research Queue 及其 Dead Letter 路由。 */
    @Bean
    public Declarables scannerTopology() {
        return new Declarables(new DirectExchange("tradeagent.event", true, false),
            new DirectExchange("tradeagent.event.dlx", true, false),
            QueueBuilder.durable("research.important-event").deadLetterExchange("tradeagent.event.dlx")
                .deadLetterRoutingKey("research.failed").build(),
            QueueBuilder.durable("research.important-event.dlq").build(),
            new Binding("research.important-event", Binding.DestinationType.QUEUE, "tradeagent.event", "scanner.important", null),
            new Binding("research.important-event.dlq", Binding.DestinationType.QUEUE, "tradeagent.event.dlx", "research.failed", null));
    }

    /** 每轮确认一条待发布消息；发送后进程退出可能导致重复投递，消费端需要保证幂等。 */
    @Scheduled(fixedDelayString = "${tradeagent.scanner.publish-delay-ms:1000}")
    public void publish() {
        transactions.executeWithoutResult(tx -> {
            var row = repository.lockNextOutbox();
            if (row == null) return;
            try {
                var correlation = new CorrelationData(row.id.toString());
                var properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setMessageId(row.id.toString());
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                rabbit.send("tradeagent.event", "scanner.important",
                    new Message(row.payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), properties), correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException("Broker rejected or returned publication");
                repository.markPublished(row.id);
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                int attempts = row.attempts + 1;
                repository.retryOutbox(row.id, attempts, error.getClass().getSimpleName(),
                    Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(attempts, 6))));
            }
        });
    }
}
