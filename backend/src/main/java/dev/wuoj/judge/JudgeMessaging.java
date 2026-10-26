package dev.wuoj.judge;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The RabbitMQ topology, declared on startup by Spring's {@code RabbitAdmin}. RabbitMQ only delivers work; whether a
 * submission still needs judging is always decided by its row in PostgreSQL (see design.md, decision 6).
 */
@Configuration(proxyBeanMethods = false)
public class JudgeMessaging {

    public static final String JOBS_EXCHANGE = "oj.judge";
    public static final String JOBS_QUEUE = "oj.judge.jobs";
    public static final String JOB_ROUTING_KEY = "job";
    public static final String DEAD_EXCHANGE = "oj.judge.dlx";
    public static final String DEAD_QUEUE = "oj.judge.dead";
    public static final String DEAD_ROUTING_KEY = "dead";
    /** Live status events; routing key {@code submission.<id>}. */
    public static final String EVENTS_EXCHANGE = "oj.events";

    /** After this many deliveries a message is dead-lettered; normally retries are driven by the database. */
    static final int DELIVERY_LIMIT = 5;

    @Bean
    DirectExchange jobsExchange() {
        return ExchangeBuilder.directExchange(JOBS_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue jobsQueue() {
        return QueueBuilder.durable(JOBS_QUEUE)
                .quorum()
                .deliveryLimit(DELIVERY_LIMIT)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding jobsBinding() {
        return BindingBuilder.bind(jobsQueue()).to(jobsExchange()).with(JOB_ROUTING_KEY);
    }

    @Bean
    DirectExchange deadExchange() {
        return ExchangeBuilder.directExchange(DEAD_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).quorum().build();
    }

    @Bean
    Binding deadBinding() {
        return BindingBuilder.bind(deadQueue()).to(deadExchange()).with(DEAD_ROUTING_KEY);
    }

    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
    }
}
