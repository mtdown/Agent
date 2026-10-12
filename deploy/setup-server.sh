#!/usr/bin/env bash
# 服务器初始化：只在第一台新机器上执行一次。
# 用法（root）：bash setup-server.sh
# 幂等：已经装好的组件会跳过。
set -euo pipefail

APP_DIR=/opt/cloudwiki

echo "==> [1/4] 安装 Docker（国内网络走阿里云镜像源）"
if command -v docker >/dev/null 2>&1; then
  echo "    docker 已安装，跳过"
else
  curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
  sh /tmp/get-docker.sh --mirror Aliyun
fi

echo "==> [2/4] 配置 Docker 镜像加速器（国内拉 Docker Hub 很慢，必须配）"
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  cat > /etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://hub-mirror.c.163.com"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
  echo "    已写入 daemon.json"
else
  echo "    daemon.json 已存在，跳过（如需加速器请自行追加 registry-mirrors）"
fi

echo "==> [3/4] 启动 Docker 并安装 compose 插件"
systemctl enable --now docker
if ! docker compose version >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y -qq docker-compose-plugin
fi
systemctl restart docker

echo "==> [4/4] 准备应用目录"
mkdir -p "${APP_DIR}"
echo "    应用目录：${APP_DIR}"

echo
echo "初始化完成。接下来由 GitHub Actions 推送产物到 ${APP_DIR} 并启动容器。"
echo "确认：docker --version ; docker compose version"
