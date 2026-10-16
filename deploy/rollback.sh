#!/usr/bin/env bash
# 回滚：把上一版 jar 换回来并重启后端。
# 前提：deploy.sh 每次部署都会把上一版存为 app.jar.bak。
set -euo pipefail

APP_DIR=/opt/cloudwiki
cd "${APP_DIR}"

if [ ! -f app.jar.bak ]; then
  echo "没有 app.jar.bak，无法回滚（这是第一次部署）"
  exit 1
fi

echo "==> 恢复到上一版后端与 Agent 部署文件"
cp -f app.jar.bak app.jar
for file in agent.jar docker-compose.yml Dockerfile AgentDockerfile; do
  if [ -f "$file.bak" ]; then cp -f "$file.bak" "$file"; fi
done

echo "==> 重建并启动上一版服务（数据库卷不受影响）"
docker compose up -d --build --remove-orphans

echo "==> 容器状态"
docker compose ps

PORT=$(grep -E '^HTTP_PORT=' .env 2>/dev/null | cut -d= -f2 || echo 8080)
code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/" || echo 000)
echo "首页返回：${code}"
[ "${code}" = "200" ] && echo "回滚完成" || echo "回滚后仍异常，查看：docker compose logs --tail=200 backend"
