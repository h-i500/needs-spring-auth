package com.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // API想定なので CSRF は無効化
        .csrf(csrf -> csrf.disable())

        // パス毎の許可/制限
        .authorizeHttpRequests(auth -> auth
            // ヘルス/情報は無認証でOK（任意）
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()

            // ↓必要ならサービス別の制御をここに追加（例）-----------
            // jsprice-converter: /run は認証必須
            // .requestMatchers(HttpMethod.POST, "/run").authenticated();
            //
            // pdf-host: /jsprice/** は認証必須
            // .requestMatchers(HttpMethod.GET, "/jsprice/**").authenticated();
            // -----------------------------------------------------------

            // それ以外はすべて認証必須
            .anyRequest().authenticated()
        )

        // Resource Server (JWT)
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    return http.build();
  }
}
