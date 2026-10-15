package com.kunlun365.pointconfirm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Point {

    @JsonProperty("serial_no")
    private Integer serialNo;

    private String code;

    @JsonProperty("management_mode")
    private String managementMode;

    @JsonProperty("management_unit")
    private String managementUnit;

    private String district;

    private String name;

    @JsonProperty("formatted_address")
    private String formattedAddress;

    private double lng;

    private double lat;

    /**
     * 0 = 待探索
     * 1 = 种草
     * 2 = 避雷
     * <p>
     * 不从 JSON 读取，由后端维护。
     */
    private volatile int status = 0;
}
