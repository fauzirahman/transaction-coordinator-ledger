package com.fauzi.ledger.outbox;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxEventRepository
                .findTop100ByPublishedFalseOrderByCreatedAtAsc()) {
            String queue = event.getEventType().startsWith("TRANSFER_")
                    && !"TRANSFER_REQUESTED".equals(event.getEventType())
                    ? "transfer-notifications"
                    : "transfer-requests";
            rabbitTemplate.convertAndSend(queue, event.getPayload().get("transferId"));
            event.markPublished();
        }
    }
}
