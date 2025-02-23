package com.gpsmonitoring.service;

import com.gpsmonitoring.model.GpsData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TraccarService {

    private final Map<String, Thread> deviceThreads = new ConcurrentHashMap<>();
    private final Map<String, GpsData> lastKnownPositions = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    @Value("${gps.tracking.mode:simulation}")
    private String trackingMode;

    @Value("${simulation.update.interval:5000}")
    private long updateInterval;

    @Value("${simulation.center.latitude:46.5197}")
    private double centerLatitude;

    @Value("${simulation.center.longitude:6.6323}")
    private double centerLongitude;

    @Value("${simulation.radius:2000}")
    private double radius;

    public TraccarService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void startDeviceMonitoring(String deviceId, String traccarServerUrl) {
        if (deviceThreads.containsKey(deviceId)) {
            log.info("Device {} is already being monitored", deviceId);
            return;
        }

        if ("simulation".equals(trackingMode)) {
            Thread simulationThread = createSimulationThread(deviceId);
            deviceThreads.put(deviceId, simulationThread);
            simulationThread.start();
            log.info("Started monitoring device {} in simulation mode", deviceId);
        } else {
            log.info("Device {} ready for real GPS data", deviceId);
        }
    }

    private Thread createSimulationThread(String deviceId) {
        return new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Simuler un mouvement aléatoire autour du centre
                    double lat = centerLatitude + (random.nextDouble() - 0.5) * radius / 111000.0;
                    double lon = centerLongitude + (random.nextDouble() - 0.5) * radius / (111000.0 * Math.cos(Math.toRadians(centerLatitude)));
                    double speed = random.nextDouble() * 60.0; // Vitesse entre 0 et 60 km/h

                    GpsData gpsData = GpsData.builder()
                            .deviceId(deviceId)
                            .latitude(lat)
                            .longitude(lon)
                            .speed(speed)
                            .timestamp(System.currentTimeMillis())
                            .status("active")
                            .build();

                    processGpsData(gpsData);
                    Thread.sleep(updateInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void processGpsData(GpsData gpsData) {
        String deviceId = gpsData.getDeviceId();
        lastKnownPositions.put(deviceId, gpsData);
        messagingTemplate.convertAndSend("/topic/gps/" + deviceId, gpsData);
        log.debug("Processed GPS data for device {}: {}", deviceId, gpsData);
    }

    public void stopDeviceMonitoring(String deviceId) {
        Thread thread = deviceThreads.remove(deviceId);
        if (thread != null) {
            thread.interrupt();
            log.info("Stopped monitoring device {}", deviceId);
        }
    }

    public List<GpsData> getDevicePositions() {
        return new ArrayList<>(lastKnownPositions.values());
    }

    public String getDeviceStatus(String deviceId) {
        return deviceThreads.containsKey(deviceId) ? "active" : "inactive";
    }
}
