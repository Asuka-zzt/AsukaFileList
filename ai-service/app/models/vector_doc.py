"""pgvector 向量文档表模型"""
from datetime import datetime
from sqlalchemy import BigInteger, String, Text, DateTime, func
from sqlalchemy.orm import Mapped, mapped_column
from pgvector.sqlalchemy import Vector
from app.core.database import Base
from app.core.config import settings


class VectorDoc(Base):
    """文档向量表，每个文档切分为若干 chunk 存储"""
    __tablename__ = "vector_doc"

    id:          Mapped[int]      = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id:     Mapped[int]      = mapped_column(BigInteger, nullable=False, index=True)
    user_file_id: Mapped[int]     = mapped_column(BigInteger, nullable=False, index=True)
    chunk_index: Mapped[int]      = mapped_column(nullable=False)
    content:     Mapped[str]      = mapped_column(Text, nullable=False)
    embedding:   Mapped[list]     = mapped_column(Vector(settings.embed_dim), nullable=False)
    created_at:  Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
