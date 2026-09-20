package com.a501.service.config;

import com.a501.service.common.realtime.RealtimeHandshakeInterceptor;
import com.a501.service.common.realtime.RealtimeWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private static final String ENDPOINT = "/ws/realtime";

    private final RealtimeWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;
    private final RealtimeProperties properties;

    public WebSocketConfig(
            RealtimeWebSocketHandler handler,
            RealtimeHandshakeInterceptor handshakeInterceptor,
            RealtimeProperties properties) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    /**
     * 내장 Tomcat 의 기본 버퍼는 8KB 라 그보다 큰 프레임은 이 애플리케이션에 닿기도 전에
     * 세션이 닫힌다. SDP 는 코덱과 ICE 후보가 붙으면 그보다 커지므로 한도를 맞춰 둔다.
     * Spring Boot 에 이 값을 바꾸는 설정 속성은 없다.
     */
    @Bean
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.maxMessageBytes());
        container.setMaxBinaryMessageBufferSize(properties.maxMessageBytes());
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(handler, ENDPOINT).addInterceptors(handshakeInterceptor);

        // 비워 두면 Spring 이 같은 출처만 받는다. 운영은 프론트와 같은 도메인이라 그걸로 충분하고,
        // 로컬은 3000 과 8080 이 갈려 설정으로 열어 준다.
        if (!properties.allowedOrigins().isEmpty()) {
            registration.setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
        }
    }
}
