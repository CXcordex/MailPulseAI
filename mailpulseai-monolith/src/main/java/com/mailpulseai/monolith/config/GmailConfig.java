package com.mailpulseai.monolith.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Configuration
@Slf4j
public class GmailConfig {

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.refresh-token}")
    private String refreshToken;

    @Bean
    public Gmail gmailClient() throws GeneralSecurityException, IOException {
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        // NOTE: Do NOT call credentials.refreshIfExpired() here at startup.
        // Doing so causes a hard crash if the OAuth token is expired or revoked.
        // The HttpCredentialsAdapter will auto-refresh credentials on the first API call.
        // Any auth errors will be caught and logged by GmailPollingService.

        log.info("Gmail OAuth client configured for client-id={}", clientId.substring(0, 20) + "...");

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("MailPulseAI")
                .build();
    }
}
