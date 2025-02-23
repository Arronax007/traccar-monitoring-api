package com.gpsmonitoring.service;

import com.gpsmonitoring.model.GpsData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
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

    @Value("${traccar.protocol.port:5055}")
    private int traccarPort;

    public TraccarService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void startDeviceMonitoring(String deviceId, String traccarServerUrl) {
        if (deviceThreads.containsKey(deviceId)) {
            log.info("Device {} is already being monitored", deviceId);
            return;
        }

        Thread deviceThread;
        if ("simulation".equals(trackingMode)) {
            deviceThread = createSimulationThread(deviceId);
        } else {
            deviceThread = createRealConnectionThread(deviceId);
        }

        deviceThreads.put(deviceId, deviceThread);
        deviceThread.start();
        log.info("Started monitoring device {} in {} mode", deviceId, trackingMode);
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

                    lastKnownPositions.put(deviceId, gpsData);
                    messagingTemplate.convertAndSend("/topic/gps/" + deviceId, gpsData);
                    Thread.sleep(updateInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private Thread createRealConnectionThread(String deviceId) {
        return new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(traccarPort)) {
                log.info("Listening for GPS data on port {}", traccarPort);
                while (!Thread.currentThread().isInterrupted()) {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                        
                        String line;
                        while ((line = in.readLine()) != null) {
                            try {
                                // Format attendu: deviceId,latitude,longitude,speed,timestamp
                                String[] data = line.split(",");
                                if (data.length >= 5 && data[0].equals(deviceId)) {
                                    GpsData gpsData = GpsData.builder()
                                            .deviceId(deviceId)
                                            .latitude(Double.parseDouble(data[1]))
                                            .longitude(Double.parseDouble(data[2]))
                                            .speed(Double.parseDouble(data[3]))
                                            .timestamp(Long.parseLong(data[4]))
                                            .status("active")
                                            .build();

                                    lastKnownPositions.put(deviceId, gpsData);
                                    messagingTemplate.convertAndSend("/topic/gps/" + deviceId, gpsData);
                                    log.debug("Received GPS data for device {}: {}", deviceId, gpsData);
                                }
                            } catch (Exception e) {
                                log.error("Error parsing GPS data: {}", e.getMessage());
                            }
                        }
                    } catch (IOException e) {
                        log.error("Error in client connection: {}", e.getMessage());
                        // Petite pause avant de réessayer
                        Thread.sleep(1000);
                    }
                }
            } catch (IOException e) {
                log.error("Error creating server socket: {}", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void stopDeviceMonitoring(String deviceId) {
        Thread thread = deviceThreads.remove(deviceId);
        if (thread != null) {
            thread.interrupt();
            log.info("Stopped monitoring device {}", deviceId);
        }
    }

    public Map<String, GpsData> getDevicePositions() {
        return lastKnownPositions;
    }

    public String getDeviceStatus(String deviceId) {
        return deviceThreads.containsKey(deviceId) ? "active" : "inactive";
    }
}
