package com.kunlun365.pointconfirm.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunlun365.pointconfirm.model.Point;
import com.kunlun365.pointconfirm.repo.PointRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PointDataInitializer implements ApplicationRunner {

    private final PointRepository pointRepository;
    private final ObjectMapper objectMapper;

    public PointDataInitializer(
            PointRepository pointRepository,
            ObjectMapper objectMapper) {
        this.pointRepository = pointRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ClassPathResource resource = new ClassPathResource("points2025.json");

        if (!resource.exists()) {
            throw new IllegalStateException("找不到 points.json，请确认文件位于 src/main/resources/");
        }

        List<Point> points;

        try (InputStream inputStream = resource.getInputStream()) {
            points = objectMapper.readValue(inputStream, new TypeReference<List<Point>>() {
            });
        }

        Set<Integer> serialNumbers = new HashSet<>();
        Set<String> codes = new HashSet<>();
        int inserted = 0;

        for (Point point : points) {
            if (point.getSerialNo() == null) {
                throw new IllegalStateException("JSON 中存在空 serial_no");
            }

            if (point.getCode() == null || point.getCode().trim().isEmpty()) {
                throw new IllegalStateException("点位 " + point.getSerialNo() + " 的 code 不能为空");
            }

            if (point.getLng() < -180 || point.getLng() > 180
                    || point.getLat() < -90 || point.getLat() > 90) {
                throw new IllegalStateException("点位 " + point.getSerialNo() + " 的经纬度不合法");
            }

            if (!serialNumbers.add(point.getSerialNo())) {
                throw new IllegalStateException("JSON 中存在重复 serial_no：" + point.getSerialNo());
            }

            if (!codes.add(point.getCode())) {
                throw new IllegalStateException("JSON 中存在重复 code：" + point.getCode());
            }

            inserted += pointRepository.insertIfAbsent(point);
        }

        System.out.println("========== 点位初始化完成 ==========");
        System.out.println("JSON 点位数量：" + points.size());
        System.out.println("本次新增数量：" + inserted);
        System.out.println("数据库现有数量：" + pointRepository.findAll().size());
        System.out.println("====================================");
    }
}
