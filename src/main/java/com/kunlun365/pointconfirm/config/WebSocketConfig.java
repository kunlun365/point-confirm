package com.kunlun365.pointconfirm.config;

import com.kunlun365.pointconfirm.ws.PointWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PointWebSocketHandler pointWebSocketHandler;

    public WebSocketConfig(PointWebSocketHandler pointWebSocketHandler) {
        this.pointWebSocketHandler = pointWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pointWebSocketHandler, "/ws/point").setAllowedOrigins("*");
    }
}
