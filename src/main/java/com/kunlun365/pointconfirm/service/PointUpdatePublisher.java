package com.kunlun365.pointconfirm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunlun365.pointconfirm.PointInstanceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PointUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(PointUpdatePublisher.class);

    public static final String CHANNEL = "pointconfirm:point-updates";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper;
    private final PointInstanceId instanceId;

    public PointUpdatePublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper mapper,
            PointInstanceId instanceId) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId.getValue();
    }

    public void publishUpdate(int pointId, int status) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "UPDATE");
            event.put("pointId", pointId);
            event.put("status", status);
            event.put("sourceInstanceId", instanceId.getValue());

            String json = mapper.writeValueAsString(event);
            redisTemplate.convertAndSend(CHANNEL, json);
            log.info("发布点位更新事件：pointId={}, status={}", pointId, status);
        } catch (Exception e) {
            // MySQL 已经成功更新，不能因为发布失败而把确认操作当成失败。
            log.error("发布 Redis 点位更新事件失败：pointId={}", pointId, e);
        }
    }
}
