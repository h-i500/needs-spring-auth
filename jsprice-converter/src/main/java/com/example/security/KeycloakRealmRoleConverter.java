// 例: src/main/java/com/example/security/KeycloakRealmRoleConverter.java
package com.example.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@SuppressWarnings("unchecked")
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Object realmAccess = jwt.getClaims().get("realm_access");
    if (!(realmAccess instanceof java.util.Map<?, ?> map)) {
      return Collections.emptyList();
    }
    Object roles = map.get("roles");
    if (!(roles instanceof List<?> list)) {
      return Collections.emptyList();
    }
    return list.stream()
      .filter(String.class::isInstance)
      .map(String.class::cast)
      .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
      .collect(Collectors.toSet());
  }
}
