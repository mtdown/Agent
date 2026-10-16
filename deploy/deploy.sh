#!/usr/bin/env bash
# 部署/更新：由 GitHub Actions 通过 SSH 在服务器上执行。
# 前提：产物已解压到 /opt/cloudwiki（app.jar / dist / Dockerfile / docker-compose.yml /
#       nginx.conf / schema-init.sql），.env 也已由 CI 写好。
set -euo pipefail

APP_DIR=/opt/cloudwiki
cd "${APP_DIR}"

echo "==> [1/4] 校验产物完整性"
for f in app.jar agent.jar Dockerfile AgentDockerfile docker-compose.yml nginx.conf schema-init.sql .env; do
  [ -f "${f}" ] || { echo "缺少 ${f}，终止部署"; exit 1; }
done
[ -d dist ] || { echo "缺少 dist（前端产物），终止部署"; exit 1; }
echo "    产物齐全"

echo "==> [2/4] 拉取依赖镜像（后端镜像本地构建，只拉 mysql/redis/nginx）"
docker compose pull mysql redis nginx || echo "    拉取失败，将使用本地已有镜像"

echo "==> [3/4] 构建并启动后端与 Agent"
# 后端镜像构建只是 COPY jar，不占内存
docker compose up -d --build --remove-orphans

if [ "$(docker inspect -f '{{.State.Running}}' cw-agent 2>/dev/null)" != "true" ]; then
  echo "    Agent container failed to start"
  docker compose ps
  docker compose logs --tail=60 agent || true
  exit 1
fi

echo "==> [4/4] 清理悬空镜像，释放磁盘"
docker image prune -f >/dev/null 2>&1 || true

echo
echo "==> 容器状态"
docker compose ps

echo
echo "==> 健康检查（前端 200 且后端有响应才算成功）"
PORT=$(grep -E '^HTTP_PORT=' .env 2>/dev/null | cut -d= -f2 || echo 8080)
# 注意：首页是 Nginx 直接吐静态文件，后端没起来它照样返回 200。
# 只探 / 会造成"部署成功"的假象（表现为能打开首页、但所有接口 502），
# 所以后端存活必须单独探一个真实的 API。
for i in $(seq 1 60); do
  web=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/" || echo 000)
  api=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${PORT}/api/user/get/login" || echo 000)
  # 后端只要不是 000/502/503 就算活着（401/404 都说明 Tomcat 已经在处理请求）
  if [ "${web}" = "200" ] && [ "${api}" != "000" ] && [ "${api}" != "502" ] && [ "${api}" != "503" ]; then
    if [ "$(docker inspect -f '{{.State.Running}}' cw-agent 2>/dev/null)" != "true" ]; then
      echo "    Agent container exited during startup"
      docker compose logs --tail=60 agent || true
      exit 1
    fi
    echo "    前端 ${web} / 后端 ${api} —— 部署完成（端口 ${PORT}，第 ${i} 次探测）"
    exit 0
  fi
  sleep 5
done
echo "    60 次探测后端仍未就绪（前端 ${web} / 后端 ${api}），容器状态与后端日志如下："
docker compose ps
docker compose logs --tail=60 backend || true
docker compose logs --tail=60 agent || true
exit 1
