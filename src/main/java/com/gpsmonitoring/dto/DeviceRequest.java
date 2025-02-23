package com.gpsmonitoring.dto;

import lombok.Data;

@Data
public class DeviceRequest {
    private String deviceId;
    private String serverUrl;
    private Integer port;
    
    // Getters et setters générés par Lombok
}
