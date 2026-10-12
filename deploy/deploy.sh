#!/usr/bin/env bash
# 部署/更新：由 GitHub Actions 通过 SSH 在服务器上执行。
# 前提：产物已解压到 /opt/cloudwiki（app.jar / dist / Dockerfile / docker-compose.yml /
#       nginx.conf / schema-init.sql），.env 也已由 CI 写好。
set -euo pipefail

APP_DIR=/opt/cloudwiki
cd "${APP_DIR}"

echo "==> [1/4] 校验产物完整性"
for f in app.jar Dockerfile docker-compose.yml nginx.conf schema-init.sql .env; do
  [ -f "${f}" ] || { echo "缺少 ${f}，终止部署"; exit 1; }
done
[ -d dist ] || { echo "缺少 dist（前端产物），终止部署"; exit 1; }
echo "    产物齐全"

echo "==> [2/4] 拉取依赖镜像（后端镜像本地构建，只拉 mysql/redis/nginx）"
docker compose pull mysql redis nginx || echo "    拉取失败，将使用本地已有镜像"

echo "==> [3/4] 备份上一版 jar（用于回滚），然后构建并启动"
if [ -f app.jar ]; then
  cp -f app.jar app.jar.bak
  echo "    已备份为 app.jar.bak"
fi
# 后端镜像构建只是 COPY jar，不占内存
docker compose up -d --build --remove-orphans

echo "==> [4/4] 清理悬空镜像，释放磁盘"
docker image prune -f >/dev/null 2>&1 || true

echo
echo "==> 容器状态"
docker compose ps

echo
echo "==> 健康检查（HTTP 200 即部署成功）"
PORT=$(grep -E '^HTTP_PORT=' .env 2>/dev/null | cut -d= -f2 || echo 8080)
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/" || echo 000)
  if [ "${code}" = "200" ]; then
    echo "    首页返回 200，部署完成（端口 ${PORT}）"
    exit 0
  fi
  sleep 5
done
echo "    30 次探测仍未返回 200，请查看日志：docker compose logs --tail=200 backend"
docker compose logs --tail=60 backend || true
exit 1
