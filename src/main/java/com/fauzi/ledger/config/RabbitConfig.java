package com.fauzi.ledger.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RabbitConfig {

    @Bean
    Queue transferQueue() {
        return new Queue("transfer-requests", true);
    }

    @Bean
    Queue notificationQueue() {
        return new Queue("transfer-notifications", true);
    }
}
