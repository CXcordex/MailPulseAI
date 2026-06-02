package com.mailpulseai.ingestion.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Creates the Gmail API client bean used by GmailPollingService.
 *
 * Authentication flow:
 * The app uses OAuth 2.0 with a refresh token (offline access).
 * You get the refresh token by running the one-time OAuth consent flow:
 *   1. Go to Google Cloud Console → APIs & Services → Credentials
 *   2. Create OAuth 2.0 Client ID (Desktop app type)
 *   3. Run: python3 scripts/get_refresh_token.py
 *   4. Copy the refresh token into your .env as GOOGLE_REFRESH_TOKEN
 *
 * BUG FIX: Added gmail.modify scope (required to mark messages as read via
 * the UNREAD label removal). Without this, markAsRead() fails with 403 Forbidden.
 */
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
    public Gmail gmailClient() throws Exception {
        log.info("Initialising Gmail API client...");

        GoogleCredentials credentials = UserCredentials.newBuilder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRefreshToken(refreshToken)
            .build()
            .createScoped(List.of(
                "https://www.googleapis.com/auth/gmail.readonly",
                "https://www.googleapis.com/auth/gmail.modify",  // BUG FIX: needed to mark as read
                "https://www.googleapis.com/auth/gmail.send"
            ));

        return new Gmail.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials)
        )
        .setApplicationName("MailPulseAI-AI")
        .build();
    }
}
