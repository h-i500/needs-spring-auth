package com.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()

            // ★ 内部呼び出し（jsprice-converter → pdf-host）用: /jsprice/**
            .requestMatchers(HttpMethod.GET, "/jsprice/**").permitAll()

            // ★ 外向け（Kong 経由）で現在使っているパス: /pdf/jsprice/**
            .requestMatchers(HttpMethod.GET, "/pdf/jsprice/**").permitAll()

            // それ以外はトークン必須
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    return http.build();
  }

  //（恒久対応でなくてよければこれでOK。すぐ 200 になります。但し、認証しません。）
  @Bean
  WebSecurityCustomizer webSecurityCustomizer() {
    return web -> web.ignoring()
        .requestMatchers(HttpMethod.GET, "/jsprice/**", "/pdf/jsprice/**", "/actuator/**");
  }
}
