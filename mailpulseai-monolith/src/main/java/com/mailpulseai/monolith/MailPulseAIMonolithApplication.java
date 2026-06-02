package com.mailpulseai.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MailPulseAIMonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(MailPulseAIMonolithApplication.class, args);
    }
}
