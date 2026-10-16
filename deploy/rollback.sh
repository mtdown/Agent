#!/usr/bin/env bash
# 回滚：把上一版 jar / Compose 配置 / 代理配置换回来并重启服务。
# 前提：CI 在解压新产物之前会把上一版存为 <file>.bak（见 .github/workflows/deploy.yml），
#       deploy.sh 自己不做备份。
set -euo pipefail

APP_DIR=/opt/cloudwiki
cd "${APP_DIR}"

if [ ! -f app.jar.bak ]; then
  echo "没有 app.jar.bak，无法回滚（这是第一次部署）"
  exit 1
fi

echo "==> 恢复到上一版后端与 Agent 部署文件"
cp -f app.jar.bak app.jar
# nginx.conf 也要回滚：它是 bind mount，只换文件不重载的话
# 容器里跑的仍是新版本，回滚会"看起来成功但实际没生效"。
for file in agent.jar docker-compose.yml Dockerfile AgentDockerfile nginx.conf; do
  if [ -f "$file.bak" ]; then cp -f "$file.bak" "$file"; fi
done

echo "==> 重建并启动上一版服务（数据库卷不受影响）"
docker compose up -d --build --remove-orphans

echo "==> 应用回滚后的代理配置"
for i in $(seq 1 15); do
  docker compose exec -T nginx true >/dev/null 2>&1 && break
  sleep 2
done
if docker compose exec -T nginx nginx -t >/dev/null 2>&1; then
  docker compose exec -T nginx nginx -s reload
else
  echo "    回滚后的 nginx.conf 校验失败，重建 Nginx 容器使其生效"
  docker compose up -d --force-recreate nginx
fi

echo "==> 容器状态"
docker compose ps

PORT=$(grep -E '^HTTP_PORT=' .env 2>/dev/null | cut -d= -f2 || echo 8080)
web=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/" || echo 000)
api=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/api/user/get/login" || echo 000)
echo "首页返回：${web} / 接口返回：${api}"
# 只探首页会重演那次误判：后端挂了首页照样 200，所以接口必须单独探。
if [ "${web}" = "200" ] && [ "${api}" != "000" ] && [ "${api}" != "502" ] && [ "${api}" != "503" ]; then
  echo "回滚完成"
else
  echo "回滚后仍异常，查看：docker compose logs --tail=200 backend"
  docker compose logs --tail=60 nginx || true
  exit 1
fi
