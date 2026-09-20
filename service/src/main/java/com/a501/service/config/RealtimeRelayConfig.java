package com.a501.service.config;

import java.util.concurrent.ThreadPoolExecutor;

import com.a501.service.common.realtime.relay.RealtimeControlSubscriber;
import com.a501.service.common.realtime.relay.RealtimeRelaySubscriber;
import com.a501.service.common.realtime.relay.RealtimeRelayTopic;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * pub/sub 수신 container 에 우리 구독을 붙인다. Sentinel failover 뒤 Lettuce 가 새 primary 로
 * 다시 붙으면 container 가 구독을 되살린다.
 *
 * <p>bean 이름은 {@code redisMessageListenerContainer} 여야 한다. Spring Boot 는 이 이름이
 * 있는지만 보고 자기 container 를 만들지 정하므로, 다른 이름을 쓰면 쓰지도 않는 container 가
 * 하나 더 뜬다.
 */
@Configuration
public class RealtimeRelayConfig implements DisposableBean {
    /**
     * bean 으로 올리지 않는다. Executor 타입 bean 이 있으면 Spring Boot 가
     * {@code applicationTaskExecutor} 를 만들지 않아 MVC 의 비동기 처리까지 딸려 바뀐다.
     */
    private final ThreadPoolTaskExecutor relayExecutor = createRelayExecutor();

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeRelaySubscriber subscriber,
            RealtimeControlSubscriber controlSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(relayExecutor);
        container.addMessageListener(subscriber, new PatternTopic(RealtimeRelayTopic.PATTERN));
        container.addMessageListener(controlSubscriber, new ChannelTopic(RealtimeRelayTopic.CONTROL));
        return container;
    }

    @Override
    public void destroy() {
        relayExecutor.shutdown();
    }

    /**
     * 기본값은 메시지 한 건마다 thread 를 새로 만든다. 통화를 여는 동안 ICE 후보가 몰려 오므로
     * 그대로 두면 thread 가 수십 개씩 생긴다.
     *
     * <p>받은 순서와 내보내는 순서가 같지는 않다. ICE 는 받는 쪽이 줄 세우므로 괜찮지만,
     * 순서가 곧 내용인 채팅을 붙일 때는 이 점을 먼저 확인해야 한다.
     */
    private static ThreadPoolTaskExecutor createRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("realtime-relay-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        // 신호 한 건이 사라지면 통화가 열리지 않으므로 버리지 않고 받은 thread 가 보낸다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
