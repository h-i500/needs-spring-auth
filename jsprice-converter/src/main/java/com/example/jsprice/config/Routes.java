package com.example.jsprice.config;

import com.example.jsprice.ServiceTokenProvider;
import com.example.jsprice.processor.PdfToCsvProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration
public class Routes extends RouteBuilder {

  private final ServiceTokenProvider serviceTokenProvider;

  public Routes(ServiceTokenProvider serviceTokenProvider) {
    this.serviceTokenProvider = serviceTokenProvider;
  }

  private static final Logger LOG = LoggerFactory.getLogger(Routes.class);

  @Value("${app.sourceUrl}")
  String sourceUrl;

  @Value("${app.output.dir:data/output}")
  String outputDir;

  @Value("${app.output.filename:jsprice_20250630.csv}")
  String outputFileName;

  @Override
  public void configure() {

    // 例外時はエラーファイル退避＆ログ
    // onException(Exception.class)
    //   .handled(true)
    //   .log("Processing failed: ${exception.message}")
    //   .process(e -> {
    //     Object body = e.getIn().getBody();
    //     if (body != null) {
    //       e.getIn().setHeader(Exchange.FILE_NAME,
    //           "failed_" + System.currentTimeMillis() + ".bin");
    //     }
    //   })
    //   .to("file:data/error")
    //   .setBody(simple("ERROR: ${exception.message}"))
    //   .to("log:jsprice-error?level=ERROR");
    onException(Exception.class)
      .handled(true)
      .log("Processing failed: ${exception.message}")
      .setBody(simple("ERROR: ${exception.message}"))        // ← 先に本文を作る
      .process(e -> {
        e.getIn().setHeader(Exchange.FILE_NAME,
          "failed_" + System.currentTimeMillis() + ".txt");  // 拡張子も txt 推奨
      })
      .to("file:data/error")                                 // ← その後に書く
      .to("log:jsprice-error?level=ERROR");

    // 互換: 既存の direct:run 呼び出しがあれば runJob にフォワード
    // from("direct:run")
    //   .routeId("compat-direct-run")
    //   .to("direct:runJob");
    from("direct:run")
        .routeId("jsprice-run")
        .setHeader(org.apache.camel.Exchange.HTTP_METHOD, constant("GET"))
        // ここで Authorization ヘッダをそのまま引き継ぐ
        .setHeader("Authorization", header("Authorization"))
        // 401を例外にする
        .toD("http://pdf-host:10081/jsprice/sample?throwExceptionOnFailure=true")
        // 以降：ダウンロード結果の保存や後段処理 …
    ;


    // 実処理本体：手動・定期どちらもここを通る
    from("direct:runJob")
      .routeId("jsprice-runJob")
      .process(exchange -> {
          String token = serviceTokenProvider.getBearerToken();
          exchange.getMessage().setHeader("Authorization", "Bearer " + token);
      })
      .setHeader(org.apache.camel.Exchange.HTTP_METHOD, constant("GET"))
      .setHeader("outputDir", simple(outputDir))
      .setHeader("outputFileName", simple(outputFileName))
      // ★ 追加: Authorization ヘッダの先頭だけ安全にログ
      .process(e -> {
            String h = e.getMessage().getHeader("Authorization", String.class);
            String head = (h == null) ? "null" : (h.length() > 24 ? h.substring(0,24) + "..." : h);
            LOG.info("Outgoing Authorization header: {}", head);
        })
      .log("Downloading PDF from: " + sourceUrl)
      .toD("{{app.sourceUrl}}?throwExceptionOnFailure=true")
      .process(new PdfToCsvProcessor())
      .log("Writing CSV to: ${header.outputDir}/${header.outputFileName}")
      .toD("file:${header.outputDir}?fileName=${header.outputFileName}")
      .log("Done.");
  
  
    from("quartz://debug/everyMinute?cron=0+*+*+*+*+?")
      .routeId("debugEveryMinute")
      .log("DEBUG quartz fired: every minute (Asia/Tokyo)")
      // .to("direct:run-job")
      .to("direct:runJob")
    ;

    // from("direct:run-job")
    //   .routeId("jsprice-runJob")
    //   .process(exchange -> {
    //       // サービス用Bearerを取得して付与
    //       String token = serviceTokenProvider.getBearerToken();
    //       exchange.getMessage().setHeader("Authorization", "Bearer " + token);
    //   })
    //   .setHeader(org.apache.camel.Exchange.HTTP_METHOD, constant("GET"))
    //   .toD("http://pdf-host:10081/jsprice/sample?throwExceptionOnFailure=true")
    //   // 以降：保存／CSV変換など…
    // ;

  
  }
}
