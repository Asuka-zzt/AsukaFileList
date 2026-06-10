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
    # LightRAG 的 PG 后端 DSN（asyncpg 原生格式，区别于上方 SQLAlchemy DSN）
    postgres_age_dsn: str = "postgresql://postgres:postgres@localhost:5432/asuka_ai"

    # -------- Embedding（本地 bge-m3）--------
    embed_provider: str = "bge-m3"             # 可切换托管 embedding API
    embed_model: str = "BAAI/bge-m3"           # FlagEmbedding 加载的模型

    # -------- LightRAG / Graph RAG --------
    lightrag_workspace_prefix: str = "kb_"     # workspace 命名前缀：kb_{kbId}

    # -------- Agent Loop --------
    agent_max_iters: int = 3                    # 检索-评估迭代上限
    agent_timeout_s: int = 60                   # 单次问答整体超时（秒）

    # -------- Redis / Celery --------
    redis_url: str = "redis://localhost:6379/1"

    # -------- Java 主服务（下载文件用）--------
    master_url: str = "http://localhost:8080"
    master_token: str = ""                     # 内部服务 token


settings = Settings()
