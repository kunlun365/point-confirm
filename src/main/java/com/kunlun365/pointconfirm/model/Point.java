package com.kunlun365.pointconfirm.model;

public class Point {
    private long id;
    private double lat;
    private double lng;
    /**
     * 0 = 未确认
     * 1 = √
     * 2 = ×
     */
    private int status = 0;

    public Point(long id, double lat, double lng) {
        this.id = id;
        this.lat = lat;
        this.lng = lng;
    }

    public long getId() {
        return id;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
