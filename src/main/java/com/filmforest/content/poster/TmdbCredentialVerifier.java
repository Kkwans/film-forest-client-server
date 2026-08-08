package com.filmforest.content.poster;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class TmdbCredentialVerifier {

    private static final URI CONFIGURATION_URI = URI.create("https://api.themoviedb.org/3/configuration");

    private final HttpClient httpClient;

    public TmdbCredentialVerifier() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    TmdbCredentialVerifier(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ValidationResult verify(String credentialType, String credential) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri(credentialType, credential))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET();
        if ("read_access_token".equals(credentialType)) {
            request.header("Authorization", "Bearer " + credential);
        }
        try {
            int status = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            return classify(status);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ValidationResult("unavailable", "interrupted");
        } catch (IOException unavailable) {
            return new ValidationResult("unavailable", "network_error");
        }
    }

    static ValidationResult classify(int statusCode) {
        if (statusCode == 200) return new ValidationResult("valid", null);
        if (statusCode == 401 || statusCode == 403) return new ValidationResult("invalid", "authentication_failed");
        if (statusCode == 429) return new ValidationResult("rate_limited", "rate_limited");
        if (statusCode >= 500) return new ValidationResult("unavailable", "service_unavailable");
        return new ValidationResult("invalid", "request_rejected");
    }

    private URI uri(String credentialType, String credential) {
        if ("api_key".equals(credentialType)) {
            return URI.create(CONFIGURATION_URI + "?api_key="
                    + URLEncoder.encode(credential, StandardCharsets.UTF_8));
        }
        return CONFIGURATION_URI;
    }

    public record ValidationResult(String status, String errorCode) {
    }
}
