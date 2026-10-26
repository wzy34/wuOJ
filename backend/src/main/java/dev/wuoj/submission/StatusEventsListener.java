package dev.wuoj.submission;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.judge.EventPublisher.StatusMessage;
import dev.wuoj.judge.JudgeMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives every submission's status events on this API instance through its own exclusive, auto-deleted queue, and
 * hands them to the open streams.
 */
@Component
@ConditionalOnRole(Role.API)
public class StatusEventsListener {

    private static final Logger log = LoggerFactory.getLogger(StatusEventsListener.class);

    private final StreamRegistry registry;
    private final JsonMapper json;

    public StatusEventsListener(StreamRegistry registry, JsonMapper json) {
        this.registry = registry;
        this.json = json;
    }

    @RabbitListener(queues = "#{statusEventsQueue.name}")
    public void onEvent(Message message) {
        try {
            registry.dispatch(json.readValue(message.getBody(), StatusMessage.class));
        } catch (JacksonException e) {
            log.warn("Ignoring a malformed status event on {}", JudgeMessaging.EVENTS_EXCHANGE, e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnRole(Role.API)
    static class Queues {

        /** Named by the broker, exclusive to this instance and deleted when it disconnects. */
        @Bean
        Queue statusEventsQueue() {
            return new AnonymousQueue();
        }

        @Bean
        Binding statusEventsBinding(Queue statusEventsQueue, TopicExchange eventsExchange) {
            return BindingBuilder.bind(statusEventsQueue).to(eventsExchange).with("submission.*");
        }
    }
}
