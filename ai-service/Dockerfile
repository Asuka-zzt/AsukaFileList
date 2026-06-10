FROM python:3.11-slim

# opendataloader-pdf 实为 Java JAR 包装，需要 java 命令；安装 headless JRE。
# 其余 build 依赖供 FlagEmbedding/torch 等可能的源码编译使用。
RUN apt-get update \
    && apt-get install -y --no-install-recommends --no-install-suggests \
       default-jre-headless \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
