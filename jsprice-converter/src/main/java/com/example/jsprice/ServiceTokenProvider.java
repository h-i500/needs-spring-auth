package com.example.jsprice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ServiceTokenProvider {

  private final WebClient webClient;
  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;

  // 簡易キャッシュ
  private final AtomicReference<String> cachedToken = new AtomicReference<>();
  private volatile Instant expiresAt = Instant.EPOCH;

  public ServiceTokenProvider(
      @Value("${app.keycloak.tokenUrl}") String tokenUrl,
      @Value("${app.keycloak.clientId}") String clientId,
      @Value("${app.keycloak.clientSecret}") String clientSecret) {
    this.webClient = WebClient.builder().build();
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public String getBearerToken() {
    // 有効期限の少し前で更新（余裕5秒）
    if (Instant.now().isBefore(expiresAt.minusSeconds(5))) {
      return cachedToken.get();
    }
    Map<String, Object> resp = webClient.post()
        .uri(tokenUrl)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters
            .fromFormData("grant_type", "client_credentials")
            .with("client_id", clientId)
            .with("client_secret", clientSecret))
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    String accessToken = (String) resp.get("access_token");
    Integer expiresIn = (Integer) resp.getOrDefault("expires_in", 60);
    cachedToken.set(accessToken);
    expiresAt = Instant.now().plusSeconds(expiresIn);
    return accessToken;
  }
}
