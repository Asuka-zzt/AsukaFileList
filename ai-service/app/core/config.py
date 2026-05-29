"""应用配置（从环境变量 / .env 读取）"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # -------- API 安全 --------
    api_key: str = "dev-key"                   # X-API-Key 鉴权

    # -------- DeepSeek --------
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com/v1"
    deepseek_embed_model: str = "deepseek-embedding"
    deepseek_chat_model: str  = "deepseek-chat"
    embed_dim: int = 1024                       # 向量维度

    # -------- PostgreSQL + pgvector --------
    postgres_dsn: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/cloud_ai"

    # -------- Redis / Celery --------
    redis_url: str = "redis://localhost:6379/1"

    # -------- Java 主服务（下载文件用）--------
    master_url: str = "http://localhost:8080"
    master_token: str = ""                     # 内部服务 token


settings = Settings()
