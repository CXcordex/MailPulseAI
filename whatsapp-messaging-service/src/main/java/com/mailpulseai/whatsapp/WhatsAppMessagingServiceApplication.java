package com.mailpulseai.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class WhatsAppMessagingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhatsAppMessagingServiceApplication.class, args);
    }
}
