package com.gpsmonitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpsData {
    private String deviceId;
    private double latitude;
    private double longitude;
    private double speed;
    private long timestamp;
    private String status;
    private double bearing;
    private double battery;
}
