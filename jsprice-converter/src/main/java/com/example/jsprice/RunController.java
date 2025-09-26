package com.example.jsprice;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RunController {

  private final ProducerTemplate producerTemplate;

  // Lombokなしのコンストラクタ注入
  public RunController(ProducerTemplate producerTemplate) {
    this.producerTemplate = producerTemplate;
  }

  @PostMapping("/converter/run")
  public ResponseEntity<Void> run(HttpServletRequest request) {
    String auth = request.getHeader("Authorization");
    Map<String, Object> headers = new HashMap<>();
    if (auth != null && !auth.isBlank()) {
      headers.put("Authorization", auth); // ← 受けたトークンをヘッダで渡す
    }
    producerTemplate.sendBodyAndHeaders("direct:run", null, headers);
    return ResponseEntity.ok().build();
  }
}
