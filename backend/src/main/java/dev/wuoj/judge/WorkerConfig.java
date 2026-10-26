package dev.wuoj.judge;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.OjProperties;
import dev.wuoj.common.config.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(Role.WORKER)
public class WorkerConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkerConfig.class);

    /**
     * One consumer per judge slot, each holding at most one unacknowledged job, so jobs are taken in queue order and
     * never wait behind another job on a busy consumer.
     */
    @Bean
    SimpleRabbitListenerContainerFactory judgeListenerFactory(ConnectionFactory connectionFactory, JudgeSlots slots) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(slots.size());
        factory.setMaxConcurrentConsumers(slots.size());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(name = "oj.judge.simulated", havingValue = "true")
    JudgeEngine simulatedJudgeEngine(OjProperties properties) {
        log.warn("The simulated judge is enabled: submissions are NOT compiled or run. Never use this in production.");
        return new SimulatedJudgeEngine(properties.judge().simulatedStep());
    }
}
