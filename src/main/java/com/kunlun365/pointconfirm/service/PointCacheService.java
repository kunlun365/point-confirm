package com.kunlun365.pointconfirm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunlun365.pointconfirm.model.Point;
import com.kunlun365.pointconfirm.repo.PointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class PointCacheService {

    private static final Logger log = LoggerFactory.getLogger(PointCacheService.class);

    private static final String CACHE_KEY = "pointconfirm:points:all";

    // 缓存最多保留 5 分钟，避免缓存异常时数据长期过期。
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private static final TypeReference<List<Point>> POINT_LIST_TYPE = new TypeReference<List<Point>>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper;
    private final PointRepository pointRepository;

    public PointCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper mapper,
            PointRepository pointRepository) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.pointRepository = pointRepository;
    }

    /**
     * 获取全部点位。
     * 优先读取 Redis，缓存未命中或不可用时读取 MySQL。
     */
    public List<Point> getAllPoints() {

        // 1. 尝试读取 Redis 缓存。
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);

            if (cached != null && !cached.isEmpty()) {
                try {
                    List<Point> points = mapper.readValue(cached, POINT_LIST_TYPE);
                    log.info("从 Redis 缓存读取点位，总数：{}", points.size());
                    return points;
                } catch (Exception e) {
                    // 缓存内容无法解析时，回退到 MySQL。
                    log.warn("Redis 点位缓存解析失败，将从 MySQL 重新加载", e);
                }
            }
        } catch (Exception e) {
            // Redis 不可用不应该阻止客户端获取点位。
            log.warn("读取 Redis 点位缓存失败，回退/降级从 MySQL 加载", e);
        }

        // 2. 缓存未命中或读取失败，从 MySQL 获取完整列表。
        List<Point> points = pointRepository.findAll();

        // 3. 尝试写入 Redis 缓存。
        try {
            String json = mapper.writeValueAsString(points);
            redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL);
            log.info("点位已写入 Redis 缓存，总数：{}", points.size());
        } catch (Exception e) {
            // 缓存写入失败不影响 MySQL 查询结果返回。
            log.warn("写入 Redis 点位缓存失败", e);
        }

        return points;
    }

    /**
     * 删除完整点位列表缓存。MySQL 更新成功后调用。
     */
    public void evictAll() {
        try {
            Boolean deleted = redisTemplate.delete(CACHE_KEY);
            log.info("删除 Redis 点位缓存，结果：{}", deleted);
        } catch (Exception e) {
            // 不让缓存删除失败影响后续 WebSocket 广播。
            log.error("删除 Redis 点位缓存失败，缓存可能暂时存在旧数据", e);
        }
    }
}
