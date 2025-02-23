package com.gpsmonitoring.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class GpsData {
    private String deviceId;
    private double latitude;
    private double longitude;
    private double speed;
    private long timestamp;
    private String status;
}
