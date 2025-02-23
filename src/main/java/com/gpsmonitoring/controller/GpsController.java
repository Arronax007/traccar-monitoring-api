package com.gpsmonitoring.controller;

import com.gpsmonitoring.dto.DeviceRequestDto;
import com.gpsmonitoring.model.GpsData;
import com.gpsmonitoring.service.TraccarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/devices")
@CrossOrigin(origins = "*", allowCredentials = "false")
public class GpsController {

    private final TraccarService traccarService;

    public GpsController(TraccarService traccarService) {
        this.traccarService = traccarService;
    }

    @PostMapping("/{deviceId}/start")
    public ResponseEntity<Void> startMonitoring(@PathVariable String deviceId, @RequestBody(required = false) DeviceRequestDto request) {
        String serverUrl = request != null ? request.getServerUrl() : null;
        traccarService.startDeviceMonitoring(deviceId, serverUrl);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{deviceId}/stop")
    public ResponseEntity<Void> stopMonitoring(@PathVariable String deviceId) {
        traccarService.stopDeviceMonitoring(deviceId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/positions")
    public ResponseEntity<List<GpsData>> getDevicePositions() {
        return ResponseEntity.ok(traccarService.getDevicePositions());
    }

    @RequestMapping(value = "/osmand", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> receiveGpsData(
            @RequestParam("id") String deviceId,
            @RequestParam("lat") double latitude,
            @RequestParam("lon") double longitude,
            @RequestParam(value = "timestamp", required = false) Long timestamp,
            @RequestParam(value = "speed", required = false, defaultValue = "0.0") double speed,
            @RequestParam(value = "bearing", required = false, defaultValue = "0.0") double bearing,
            @RequestParam(value = "altitude", required = false) Double altitude,
            @RequestParam(value = "accuracy", required = false) Double accuracy,
            @RequestParam(value = "batt", required = false) Double battery,
            HttpServletRequest request) {
        
        log.info("GPS data from {}: lat={}, lon={}, speed={}, battery={}%", 
                deviceId, latitude, longitude, speed, battery);

        GpsData gpsData = GpsData.builder()
                .deviceId(deviceId)
                .latitude(latitude)
                .longitude(longitude)
                .speed(speed)
                .timestamp(timestamp != null ? timestamp * 1000 : System.currentTimeMillis())
                .status("active")
                .build();

        traccarService.processGpsData(gpsData);
        return ResponseEntity.ok("OK\r\n");
    }

    @GetMapping("/test")
    public ResponseEntity<String> test(HttpServletRequest request) {
        log.info("Test endpoint called from: {} {}", request.getRemoteAddr(), request.getRemoteHost());
        return ResponseEntity.ok("Server is running!");
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}