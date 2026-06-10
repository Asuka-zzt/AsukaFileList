# pgvector + Apache AGE 自定义镜像
# 在 pgvector 官方 pg18 镜像之上编译安装 Apache AGE 1.7.0（图存储，供 LightRAG 用）。
# 参考：ref/LightRAG/Dockerfile.postgres

# ── 构建阶段：基于 pg18 编译 AGE ──────────────────────────────
FROM pgvector/pgvector:pg18-trixie AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends --no-install-suggests \
       ca-certificates \
       bison \
       build-essential \
       flex \
       postgresql-server-dev-18

# AGE 源码以 vendored tarball 提供（构建容器内无法访问 github.com，故不在容器内 git clone；
# tarball 由 docker/postgres/fetch-age-src.sh 在宿主机预取，见同目录脚本）
COPY age-src.tar.gz /tmp/age-src.tar.gz
RUN mkdir -p /usr/src/age \
    && tar -xzf /tmp/age-src.tar.gz -C /usr/src/age --strip-components=1 \
    && cd /usr/src/age \
    && make \
    && make install

# ── 最终阶段：拷贝编译产物，配置扩展与 locale ────────────────
FROM pgvector/pgvector:pg18-trixie

RUN apt-get update \
    && apt-get install -y --no-install-recommends --no-install-suggests \
       locales \
    && rm -rf /var/lib/apt/lists/*

RUN echo "en_US.UTF-8 UTF-8" > /etc/locale.gen \
    && locale-gen \
    && update-locale LANG=en_US.UTF-8

ENV LANG=en_US.UTF-8
ENV LC_COLLATE=en_US.UTF-8
ENV LC_CTYPE=en_US.UTF-8

COPY --from=build /usr/lib/postgresql/18/lib/age.so /usr/lib/postgresql/18/lib/
COPY --from=build /usr/share/postgresql/18/extension/age--1.7.0.sql /usr/share/postgresql/18/extension/
COPY --from=build /usr/share/postgresql/18/extension/age.control /usr/share/postgresql/18/extension/

# 初始化脚本：首次启动建 vector + age 扩展
RUN printf '%s\n' \
    'CREATE EXTENSION IF NOT EXISTS vector;' \
    'CREATE EXTENSION IF NOT EXISTS age CASCADE;' \
    > /docker-entrypoint-initdb.d/00-create-extensions.sql

# AGE 需要通过 shared_preload_libraries 预加载
CMD ["postgres", "-c", "shared_preload_libraries=age"]
