# pgvector + Apache AGE 自定义镜像
# 在 pgvector 官方 pg18 镜像之上编译安装 Apache AGE 1.7.0（图存储，供 LightRAG 用）。
# 参考：ref/LightRAG/Dockerfile.postgres

# ── 构建阶段：基于 pg18 编译 AGE ──────────────────────────────
FROM pgvector/pgvector:pg18-trixie AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends --no-install-suggests \
       ca-certificates \
       git \
       bison \
       build-essential \
       flex \
       postgresql-server-dev-18

RUN git clone --depth 1 --branch release/PG18/1.7.0 https://github.com/apache/age.git /usr/src/age \
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
