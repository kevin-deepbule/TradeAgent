package com.tradeagent.scanner;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.tradeagent.scanner.repository.ScannerRepository;
import com.tradeagent.scanner.repository.entity.OutboxEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.tradeagent.scanner.messaging.ScannerMessaging;

/** 验证数据库发布状态取决于 Broker Confirm，而不是发送调用返回成功。 */
class OutboxPublisherTest {
    /** 构造待发布消息并模拟 Broker Confirm，无需外部基础设施。 */
    private ScannerRepository publish(boolean ack) {
        var repository = mock(ScannerRepository.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(call -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        var row = new OutboxEntity();
        row.id = UUID.randomUUID(); row.payload = "{}"; row.attempts = 0;
        when(repository.lockNextOutbox()).thenReturn(row);
        var rabbit = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "rejected"));
            return null;
        }).when(rabbit).send(eq("tradeagent.event"), eq("scanner.important"), any(Message.class), any(CorrelationData.class));
        var publisher = new ScannerMessaging(repository, transactions, mock(ConnectionFactory.class));
        ReflectionTestUtils.setField(publisher, "rabbit", rabbit);
        publisher.publish();
        return repository;
    }

    /** 验证仅在 Broker 确认接收后将 Outbox 标记为 PUBLISHED。 */
    @Test
    void confirmsBeforeMarkingPublished() {
        var repository = publish(true);
        verify(repository).markPublished(any(UUID.class));
        verify(repository, never()).retryOutbox(any(), anyInt(), any(), any());
    }

    /** 验证 Broker 拒绝消息后保留发布记录并安排重试。 */
    @Test
    void rejectedMessagesRemainRetryable() {
        var repository = publish(false);
        verify(repository, never()).markPublished(any(UUID.class));
        verify(repository).retryOutbox(any(), eq(1), any(), any());
    }
}
