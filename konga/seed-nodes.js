module.exports = [
  {
    name: "local-db-less",
    type: "default",          // 認証しない通常接続なら "default"
    kong_admin_url: "http://kong:8001",
    health_checks: false
  }
];
