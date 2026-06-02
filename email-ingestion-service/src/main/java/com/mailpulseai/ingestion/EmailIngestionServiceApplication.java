package com.mailpulseai.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling          // allows @Scheduled polling of Gmail inbox
public class EmailIngestionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmailIngestionServiceApplication.class, args);
    }
}
