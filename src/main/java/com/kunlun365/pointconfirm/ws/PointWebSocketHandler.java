package com.kunlun365.pointconfirm.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunlun365.pointconfirm.repo.PointRepository;
import com.kunlun365.pointconfirm.service.PointCacheService;
import com.kunlun365.pointconfirm.service.PointUpdatePublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PointWebSocketHandler extends TextWebSocketHandler {

    /**
     * Jackson
     */
    private final ObjectMapper mapper;
    private final PointCacheService pointCacheService;
    private final PointUpdatePublisher pointUpdatePublisher;

    /**
     * 当前应用实例的所有在线WebSocket连接
     */
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final PointRepository pointRepository;

    public PointWebSocketHandler(ObjectMapper mapper, PointCacheService pointCacheService,
                                 PointRepository pointRepository, PointUpdatePublisher pointUpdatePublisher) {
        this.mapper = mapper;
        this.pointCacheService = pointCacheService;
        this.pointRepository = pointRepository;
        this.pointUpdatePublisher = pointUpdatePublisher;
    }

    /**
     * 新客户端连接：从 MySQL 读取完整点位列表并发送。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);

        try {
            String json = mapper.writeValueAsString(pointCacheService.getAllPoints());

            synchronized (session) {
                if (session.isOpen()) {
                    // 新连接推送全量点位状态
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            sessions.remove(session);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception ignored) {
                // 关闭失败不影响其他连接。
            }
            throw e;
        }
    }

    /**
     * 接收客户端确认消息。
     * 协议：{"type":"CONFIRM","pointId":123,"status":1}
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg;

        try {
            msg = mapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            // 忽略格式错误的消息，避免影响 WebSocket 连接。
            return;
        }

        if (!"CONFIRM".equals(msg.get("type"))) {
            return;
        }

        Object pointIdValue = msg.get("pointId");
        Object statusValue = msg.get("status");

        System.out.println("收到客户端消息：" + message.getPayload());
        if (!(pointIdValue instanceof Number) || !(statusValue instanceof Number)) {
            return;
        }

        Number pointIdNumber = (Number) pointIdValue;
        Number statusNumber = (Number) statusValue;

        int pointId = pointIdNumber.intValue();
        int status = statusNumber.intValue();

        // 只接受有效的整数 ID 和状态 1（可疑）、2（排除）。
        if (pointId <= 0
                || pointIdNumber.doubleValue() != pointId
                || (status != 1 && status != 2)
                || statusNumber.doubleValue() != status) {
            return;
        }

        // 先更新 MySQL；只有成功更新待确认记录才广播。
        int updated = pointRepository.updateStatusIfPending(pointId, status);

        if (updated != 1) {
            // 点位不存在或已被其他用户确认。
            return;
        }

        // MySQL 更新成功后，删除旧的完整列表缓存。
        pointCacheService.evictAll();

//        // 广播更新
//        Map<String, Object> resp = new HashMap<>();
//        resp.put("type", "UPDATE");
//        resp.put("pointId", pointId);
//        resp.put("status", status);
//
//        broadcast(mapper.writeValueAsString(resp));

        // 本实例立即广播一次。
        broadcastUpdate(pointId, status);

        // 通知其他后端实例。
        pointUpdatePublisher.publishUpdate(pointId, status);
    }

    /**
     * 连接关闭
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /**
     * 向所有在线客户端广播状态。
     */
    private void broadcast(String json) {
        TextMessage text = new TextMessage(json);

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }

            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(text);
                    }
                }
            } catch (Exception e) {
                sessions.remove(session);
                try {
                    session.close();
                } catch (Exception ignored) {
                    // 关闭失败不影响其他客户端。
                }
            }
        }
    }

    public void broadcastUpdate(int pointId, int status) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("type", "UPDATE");
        resp.put("pointId", pointId);
        resp.put("status", status);

        broadcastUnchecked(resp);
    }

    private void broadcastUnchecked(Map<String, Object> resp) {
        try {
            broadcast(mapper.writeValueAsString(resp));
        } catch (Exception e) {
            // 广播序列化失败时记录错误。
            org.slf4j.LoggerFactory.getLogger(PointWebSocketHandler.class).error("序列化点位广播消息失败", e);
        }
    }
}
