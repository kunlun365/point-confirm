package com.kunlun365.pointconfirm;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PointInstanceId {

    private final String value = UUID.randomUUID().toString();

    public String getValue() {
        return value;
    }
}
