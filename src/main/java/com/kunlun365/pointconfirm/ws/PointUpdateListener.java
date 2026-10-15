package com.kunlun365.pointconfirm.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunlun365.pointconfirm.service.PointCacheService;
import com.kunlun365.pointconfirm.service.PointUpdatePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PointUpdateListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PointUpdateListener.class);

    private final ObjectMapper mapper;
    private final PointCacheService pointCacheService;
    private final PointUpdatePublisher publisher;
    private final PointWebSocketHandler handler;

    public PointUpdateListener(
            ObjectMapper mapper,
            PointCacheService pointCacheService,
            PointUpdatePublisher publisher,
            PointWebSocketHandler handler) {
        this.mapper = mapper;
        this.pointCacheService = pointCacheService;
        this.publisher = publisher;
        this.handler = handler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);

            Map<String, Object> event = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });

            if (!"UPDATE".equals(event.get("type"))) {
                return;
            }

            Object pointIdValue = event.get("pointId");
            Object statusValue = event.get("status");
            Object sourceValue = event.get("sourceInstanceId");

            if (!(pointIdValue instanceof Number) || !(statusValue instanceof Number) || !(sourceValue instanceof String)) {
                log.warn("忽略格式不正确的 Redis 更新事件");
                return;
            }

            int pointId = ((Number) pointIdValue).intValue();
            int status = ((Number) statusValue).intValue();
            String sourceInstanceId = (String) sourceValue;

            // 其他实例收到事件后，清除共享列表缓存并广播。
            pointCacheService.evictAll();

            // 发布者已经广播给本机客户端，不再重复广播。
            if (!publisher.getInstanceId().equals(sourceInstanceId)) {
                handler.broadcastUpdate(pointId, status);
            }
        } catch (Exception e) {
            log.error("处理 Redis 点位更新事件失败", e);
        }
    }
}
