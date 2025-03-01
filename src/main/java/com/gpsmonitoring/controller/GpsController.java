package com.gpsmonitoring.controller;

import com.gpsmonitoring.model.DeviceStatus;
import com.gpsmonitoring.model.GpsData;
import com.gpsmonitoring.service.TraccarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/devices")
@Slf4j
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "false")
public class GpsController {

    private final TraccarService traccarService;
    
    // Pattern pour parser les trames NMEA GPRMC
    private static final Pattern GPRMC_PATTERN = Pattern.compile(
        "\\$GPRMC,(\\d{2})(\\d{2})(\\d{2})\\.?\\d*,([AV]),?" +
        "(\\d{2})(\\d{2}\\.\\d+),([NS]),?(\\d{3})(\\d{2}\\.\\d+),([EW]),?" +
        "(\\d*\\.?\\d*),(\\d*\\.?\\d*),?(\\d{6})?,?.*");

    public GpsController(TraccarService traccarService) {
        this.traccarService = traccarService;
    }

    // Endpoint pour le simulateur Python
    @PostMapping("/simulator")
    public ResponseEntity<String> receiveSimulatorData(@RequestBody GpsData gpsData) {
        try {
            log.info("Received simulator data: {}", gpsData);
            traccarService.processGpsData(gpsData);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error processing simulator data: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/osmand")
    public ResponseEntity<String> handleOsmAndData(
            @RequestParam String id,
            @RequestParam Map<String, String> params) {
        
        traccarService.updateDeviceStatus(id, params);
        return ResponseEntity.ok("OK");
    }
    
    @PostMapping("/nmea")
    public ResponseEntity<String> handleNmeaData(
            @RequestParam String deviceId,
            @RequestBody String nmeaString) {
        
        try {
            // Parse NMEA GPRMC sentence
            Matcher matcher = GPRMC_PATTERN.matcher(nmeaString);
            if (matcher.matches()) {
                // Extraction des données
                String time = matcher.group(1) + matcher.group(2) + matcher.group(3);
                String status = matcher.group(4);
                double latitude = Double.parseDouble(matcher.group(5)) + 
                                Double.parseDouble(matcher.group(6)) / 60.0;
                if (matcher.group(7).equals("S")) latitude = -latitude;
                
                double longitude = Double.parseDouble(matcher.group(8)) + 
                                 Double.parseDouble(matcher.group(9)) / 60.0;
                if (matcher.group(10).equals("W")) longitude = -longitude;
                
                String speed = matcher.group(11);
                String bearing = matcher.group(12);
                
                // Conversion en format compatible
                Map<String, String> params = Map.of(
                    "lat", String.valueOf(latitude),
                    "lon", String.valueOf(longitude),
                    "speed", speed,
                    "bearing", bearing,
                    "timestamp", time,
                    "power", status.equals("A") ? "1" : "0"  // A = actif, V = warning
                );
                
                traccarService.updateDeviceStatus(deviceId, params);
                return ResponseEntity.ok("Position updated");
            }
            return ResponseEntity.badRequest().body("Invalid NMEA format");
        } catch (Exception e) {
            log.error("Error processing NMEA data for device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest().body("Error processing NMEA data");
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, DeviceStatus>> getDevicesStatus() {
        return ResponseEntity.ok(traccarService.getDevicesStatus());
    }

    @GetMapping("/positions")
    public ResponseEntity<List<GpsData>> getCurrentPositions() {
        return ResponseEntity.ok(traccarService.getCurrentPositions());
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("OK");
    }
}