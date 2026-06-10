#!/usr/bin/env sh
# 预取 Apache AGE 源码 tarball 到构建上下文。
#
# 背景：本环境的 Docker 构建容器无法访问 github.com（宿主机可以），
# 因此不在 Dockerfile 内 `git clone`，改为在宿主机预取 tarball 后 COPY 进镜像。
# 在 `docker compose build postgres` 之前运行本脚本一次即可（已存在则跳过）。
set -eu

DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$DIR/age-src.tar.gz"
# 与 Dockerfile 中的 AGE 版本保持一致：pg18 + AGE 1.7.0
URL="https://codeload.github.com/apache/age/tar.gz/refs/heads/release/PG18/1.7.0"

if [ -f "$OUT" ]; then
  echo "age-src.tar.gz 已存在，跳过下载：$OUT"
  exit 0
fi

echo "下载 AGE 源码：$URL"
curl -fSL -o "$OUT" "$URL"
# 校验是有效的 gzip tar
tar tzf "$OUT" >/dev/null
echo "已保存：$OUT"
