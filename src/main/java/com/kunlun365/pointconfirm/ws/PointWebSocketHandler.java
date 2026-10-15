package com.kunlun365.pointconfirm.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunlun365.pointconfirm.model.Point;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PointWebSocketHandler extends TextWebSocketHandler {

    /**
     * Jackson
     */
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 所有在线连接
     */
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    /**
     * 点位状态数据（内存）
     */
    private static final Map<Long, Point> points = new ConcurrentHashMap<>();

    /** 初始化 1000 个点位 */
    static {
        Random random = new Random();

        // 深圳市大致经纬度范围
        double latMin = 22.50;
        double latMax = 22.60;
        double lngMin = 114.00;
        double lngMax = 114.10;

        // 将范围分成 32x32 个网格（1024 个格子，略多于 1000）
        int gridRows = 32;
        int gridCols = 32;
        double latStep = (latMax - latMin) / gridRows;
        double lngStep = (lngMax - lngMin) / gridCols;

        // 存储已经使用的格子索引，保证不重复
        Set<String> usedCells = new HashSet<>();

        for (int i = 1; i <= 1000; i++) {
            int row, col;
            String key;
            // 找一个还没使用的格子
            do {
                row = random.nextInt(gridRows);
                col = random.nextInt(gridCols);
                key = row + "_" + col;
            } while (usedCells.contains(key));

            usedCells.add(key);

            // 在格子内随机生成坐标
            double lat = latMin + row * latStep + random.nextDouble() * latStep;
            double lng = lngMin + col * lngStep + random.nextDouble() * lngStep;

            points.put((long) i, new Point(i, lat, lng));
        }
    }

    /**
     * 新连接
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);

        // 新连接推送全量点位状态
        session.sendMessage(new TextMessage(mapper.writeValueAsString(points.values())));
    }

    /**
     * 接收消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);

        String type = (String) msg.get("type");
        if (!"CONFIRM".equals(type)) return;

        long pointId = ((Number) msg.get("pointId")).longValue();
        int status = ((Number) msg.get("status")).intValue();

        Point p = points.get(pointId);
        if (p == null) return;

        // 已确认则忽略（轻并发控制）
        if (p.getStatus() != 0) return;

        // 更新状态
        p.setStatus(status);

        // 广播更新
        Map<String, Object> resp = new HashMap<>();
        resp.put("type", "UPDATE");
        resp.put("pointId", pointId);
        resp.put("status", status);

        broadcast(mapper.writeValueAsString(resp));
    }

    /**
     * 连接关闭
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /**
     * 广播
     */
    private void broadcast(String msg) throws Exception {
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(msg));
            }
        }
    }
}
