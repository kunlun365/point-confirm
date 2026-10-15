package com.kunlun365.pointconfirm.config;

import com.kunlun365.pointconfirm.service.PointUpdatePublisher;
import com.kunlun365.pointconfirm.ws.PointUpdateListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory, PointUpdateListener listener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();

        container.setConnectionFactory(connectionFactory);

        container.addMessageListener(listener, new ChannelTopic(PointUpdatePublisher.CHANNEL));

        return container;
    }
}
