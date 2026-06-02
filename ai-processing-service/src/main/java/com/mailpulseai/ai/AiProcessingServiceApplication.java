package com.mailpulseai.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching   // enables @Cacheable on Redis
public class AiProcessingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiProcessingServiceApplication.class, args);
    }
}
