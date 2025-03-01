package com.gpsmonitoring.service;

import com.gpsmonitoring.model.DeviceStatus;
import com.gpsmonitoring.model.GpsData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class TraccarService {
    private final SimpMessagingTemplate messagingTemplate;
    private Map<String, DeviceStatus> deviceStatusMap = new ConcurrentHashMap<>();
    private Map<String, Instant> lastUpdateMap = new ConcurrentHashMap<>();
    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(5);

    public TraccarService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void updateDeviceStatus(String deviceId, Map<String, String> params) {
        DeviceStatus status = deviceStatusMap.computeIfAbsent(deviceId, k -> new DeviceStatus());
        
        // Mise à jour du statut
        status.setDeviceId(deviceId);
        status.setLatitude(params.getOrDefault("latitude", params.get("lat")));
        status.setLongitude(params.getOrDefault("longitude", params.get("lon")));
        status.setSpeed(params.getOrDefault("speed", "0"));
        status.setBearing(params.getOrDefault("bearing", "0"));
        status.setPower(params.getOrDefault("power", "1"));
        status.setBattery(params.getOrDefault("battery", params.get("batt")));
        status.setActive(true);
        status.setLastUpdate(System.currentTimeMillis());
        
        // Si power=0, marquer comme hors ligne
        if ("0".equals(params.get("power"))) {
            log.info("Device marked as offline: {}", deviceId);
            deviceStatusMap.remove(deviceId);
            lastUpdateMap.remove(deviceId);
        } else {
            lastUpdateMap.put(deviceId, Instant.now());
            // Notifier les clients WebSocket du changement
            messagingTemplate.convertAndSend("/topic/devices", getDevicesStatus());
        }
    }

    // Méthode pour le simulateur Python
    public void processGpsData(GpsData gpsData) {
        Map<String, String> params = Map.of(
            "latitude", String.valueOf(gpsData.getLatitude()),
            "longitude", String.valueOf(gpsData.getLongitude()),
            "speed", String.valueOf(gpsData.getSpeed()),
            "bearing", String.valueOf(gpsData.getBearing()),
            "battery", String.valueOf(gpsData.getBattery()),
            "power", "1",
            "timestamp", String.valueOf(gpsData.getTimestamp())
        );
        
        updateDeviceStatus(gpsData.getDeviceId(), params);
        // Notification spécifique pour le simulateur
        messagingTemplate.convertAndSend("/topic/gps/" + gpsData.getDeviceId(), gpsData);
    }

    @Scheduled(fixedRate = 60000) // Vérifie toutes les minutes
    public void cleanupOfflineDevices() {
        Instant now = Instant.now();
        deviceStatusMap.entrySet().removeIf(entry -> {
            String deviceId = entry.getKey();
            DeviceStatus status = entry.getValue();
            Instant lastUpdate = lastUpdateMap.get(deviceId);
            
            // Supprime si le device est marqué comme hors ligne (power=0) ou n'a pas été mis à jour depuis longtemps
            boolean shouldRemove = "0".equals(status.getPower()) ||
                                 (lastUpdate != null && now.isAfter(lastUpdate.plus(OFFLINE_THRESHOLD)));
            
            if (shouldRemove) {
                lastUpdateMap.remove(deviceId);
                log.info("Device removed: {}", deviceId);
                // Notifier les clients WebSocket de la suppression
                messagingTemplate.convertAndSend("/topic/devices", getDevicesStatus());
            }
            
            return shouldRemove;
        });
    }

    public Map<String, DeviceStatus> getDevicesStatus() {
        return deviceStatusMap;
    }

    public List<GpsData> getCurrentPositions() {
        return deviceStatusMap.values().stream()
            .map(status -> GpsData.builder()
                .deviceId(status.getDeviceId())
                .latitude(Double.parseDouble(status.getLatitude()))
                .longitude(Double.parseDouble(status.getLongitude()))
                .speed(status.getSpeed() != null ? Double.parseDouble(status.getSpeed()) : 0.0)
                .bearing(status.getBearing() != null ? Double.parseDouble(status.getBearing()) : 0.0)
                .battery(status.getBattery() != null ? Double.parseDouble(status.getBattery()) : 100.0)
                .timestamp(status.getLastUpdate())
                .status(status.isActive() ? "active" : "inactive")
                .build())
            .collect(Collectors.toList());
    }
}
