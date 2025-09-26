
---

# needs-spring-auth

Keycloak を用いた OAuth2/JWT 認可で、PDF 保護リソースと PDF→CSV 変換ジョブを動かす **Spring Boot + Apache Camel** 構成のサンプルです。
前段に **Kong** を API Gateway として配置し、各コンテナは `docker compose` で起動します。

## 構成

```
docker-compose
├─ keycloak (25.0.2)           # IdP / 認可サーバ（realm: needs-realm を import）
├─ kong (3.6) + konga          # API Gateway と管理UI
├─ pdf-host                    # 保護リソース (PDF を返す) Spring Boot
├─ jsprice-converter           # PDF→CSV 変換ジョブ (Spring Boot + Camel/Quartz)
└─ csv-postprocessor           # CSV の整形（例: カンマ→タブ）
```

* **Issuer（iss）** はテスト容易性のため **`http://localhost:8080`** に統一。

  * `issuer-uri`: `http://localhost:8080/realms/needs-realm`
  * `jwk-set-uri`: コンテナ間解決のため `http://keycloak:8080/.../certs` を使用（= 内部 DNS 名）
* 共有データはホスト `./data` を各サービスの `/data` にマウントします。

## 事前準備

* Docker / Docker Compose
* `curl`, `jq`（動作確認に使用）
* ポート空き: `8000`, `8001`, `8080`, `10080–10082`, `1337`

## クイックスタート

```bash
# 依存コンテナ含めてビルド & 起動
docker compose up -d --build

# Keycloak 起動ログ（初回は realm import が走ります）
docker logs -f needs-spring-auth-keycloak-1
```

Keycloak は dev モードで下記オプションにより `localhost` で固定発行します：

```
start-dev --import-realm --hostname-strict=false \
          --hostname-url=http://localhost:8080 \
          --hostname-admin-url=http://localhost:8080
```

## トークン取得（Client Credentials）

```bash
TOK=$(curl -s http://localhost:8080/realms/needs-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d grant_type=client_credentials \
  -d client_id=service-cli \
  -d client_secret=service-secret | jq -r .access_token)

# iss を確認（URL-safe Base64 のパディング補正込み）
PAY=$(echo "$TOK" | cut -d. -f2 | tr '_-' '/+'); PAD=$(( (4 - ${#PAY} % 4) % 4 ))
echo "${PAY}$(printf '=%.0s' $(seq 1 $PAD))" | base64 -d 2>/dev/null | jq -r .iss
# => http://localhost:8080/realms/needs-realm
```

## 動作確認

### 保護リソース（PDF）

* 直接（pdf-host）:

```bash
curl -i http://localhost:10081/jsprice/sample \
  -H "Authorization: Bearer $TOK"
```

* ゲートウェイ経由（Kong）:

```bash
curl -i http://localhost:8000/jsprice/sample \
  -H "Authorization: Bearer $TOK"
```

### 変換ジョブの手動起動（Kong 経由）

```bash
curl -i -X POST http://localhost:8000/converter/run \
  -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" \
  -d '{}'
```

* 生成 CSV: `./data/output/`
* 整形後（例：タブ区切り）: `./data/txt/`

> `jsprice-converter` にはデバッグ用の Quartz ジョブ（毎分実行）も入っています。

## 各サービスの要点

### pdf-host

* Spring Security Resource Server（JWT）
* 主な設定（`docker-compose.yml` の `SPRING_APPLICATION_JSON`）:

  * `issuer-uri`: `http://localhost:8080/realms/needs-realm`
  * `jwk-set-uri`: `http://keycloak:8080/realms/needs-realm/protocol/openid-connect/certs`
* エンドポイント: `GET /jsprice/sample`（PDF を返却、JWT 必須）

### jsprice-converter

* Spring Boot + Apache Camel 4.6 + Quartz
* ルート:

  * `direct:runJob` … サービス用トークンを取得 → `pdf-host` へ認証付き GET → PDF→CSV
  * `quartz://debug/everyMinute` … デバッグ用（毎分起動）
  * `POST /converter/run`（Kong 公開）… 手動起動
* 主な設定（`SPRING_APPLICATION_JSON`）:

  * `app.sourceUrl`: `http://pdf-host:10081/jsprice/sample`
  * `app.output.dir`: `/data/output`
  * `app.output.filename`: `jsprice_YYYYMMDD.csv`（例）
  * `app.keycloak.tokenUrl`: `http://keycloak:8080/realms/needs-realm/protocol/openid-connect/token`
  * `app.keycloak.clientId` / `clientSecret`: `service-cli` / `service-secret`

### csv-postprocessor

* `./data/output` から `./data/txt` へ整形コピー（例：カンマ→タブ）

### Keycloak

* イメージ: `quay.io/keycloak/keycloak:25.0.2`
* `./realms/` の `needs-realm` を起動時に import
* Dev モードで hostname を `localhost` 固定（上記クイックスタート参照）

### Kong / Konga

* Kong: DB-less（宣言的設定 `kong/kong.yml`・ルータは `expressions`）
* Konga: 管理 UI（`http://localhost:1337`）

## 主要エンドポイント（例）

| サービス     | 経路                                      | メソッド | 説明              |
| -------- | --------------------------------------- | ---- | --------------- |
| pdf-host | `http://localhost:10081/jsprice/sample` | GET  | PDF 取得（JWT 必須）  |
| Kong     | `http://localhost:8000/jsprice/sample`  | GET  | 上記のゲートウェイ公開     |
| Kong     | `http://localhost:8000/converter/run`   | POST | 変換ジョブ起動（JWT 必須） |
| Konga    | `http://localhost:1337`                 | -    | Kong の管理 UI     |

## トラブルシュート

* **401 / iss 不一致**

  * 受け側（Spring）の `issuer-uri` と、発行トークンの `iss` を一致させること。
    本プロジェクトでは `http://localhost:8080/realms/needs-realm` に統一しています。
* **Keycloak 起動時の hostname エラー**

  * `--hostname-strict=false` と `--hostname-url`/`--hostname-admin-url` を `http://localhost:8080` に設定。
* **`jsprice-converter` が 401 で失敗する**

  * サービス用トークンの `iss` が `localhost` になっているか確認。
  * `app.sourceUrl` が `pdf-host`（コンテナ DNS 名）宛になっているか確認。
* **Camel の Logger エラー（`getLogger()` 未定義）**

  * `CamelContext#getLogger()` は使用せず、`org.slf4j.LoggerFactory.getLogger(...)` を使用。

## 開発メモ

* JDK: Eclipse Temurin 21（ランタイムは `21-jre`）
* Spring Boot 3.3.x / Spring Security Resource Server
* Apache Camel 4.6 / Quartz 2.3
* 共有ボリューム: `./data`（CSV・ログなど）


---
