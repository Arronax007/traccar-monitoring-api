package com.gpsmonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TraccarMonitoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraccarMonitoringApplication.class, args);
    }
}
