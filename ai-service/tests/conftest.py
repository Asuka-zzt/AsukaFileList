"""AI service test configuration."""
import os
import sys
import types
from pathlib import Path


AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_SERVICE_ROOT))

os.environ.setdefault("API_KEY", "test-api-key")
os.environ.setdefault("MASTER_TOKEN", "test-master-token")
os.environ.setdefault("DEEPSEEK_API_KEY", "test-deepseek-key")
os.environ.setdefault(
    "POSTGRES_AGE_DSN",
    "postgresql://test_user:test_password@localhost:5432/test_ai",
)
os.environ.setdefault("REDIS_URL", "redis://localhost:6379/15")


class _QueryParam:
    """Minimal QueryParam replacement for deterministic wrapper tests."""

    def __init__(self, **kwargs):
        self.__dict__.update(kwargs)


class _LightRAG:
    """Import-only LightRAG replacement; tests patch all external behavior."""

    def __init__(self, **kwargs):
        self.options = kwargs


async def _unexpected_llm_call(*args, **kwargs):
    """Fail fast when a unit test accidentally reaches an external LLM."""
    raise AssertionError("external LLM call is not allowed in unit tests")


def _embedding_attrs(**attrs):
    """Preserve the production decorator shape without importing LightRAG."""
    def decorate(func):
        for key, value in attrs.items():
            setattr(func, key, value)
        return func

    return decorate


def _unexpected_pdf_call(**kwargs):
    """Fail fast when a unit test reaches the real PDF converter."""
    raise AssertionError("PDF converter call must be monkeypatched")


lightrag = types.ModuleType("lightrag")
lightrag.LightRAG = _LightRAG
lightrag.QueryParam = _QueryParam
lightrag_llm = types.ModuleType("lightrag.llm")
lightrag_openai = types.ModuleType("lightrag.llm.openai")
lightrag_openai.openai_complete_if_cache = _unexpected_llm_call
lightrag_utils = types.ModuleType("lightrag.utils")
lightrag_utils.wrap_embedding_func_with_attrs = _embedding_attrs
opendataloader_pdf = types.ModuleType("opendataloader_pdf")
opendataloader_pdf.convert = _unexpected_pdf_call

sys.modules["lightrag"] = lightrag
sys.modules["lightrag.llm"] = lightrag_llm
sys.modules["lightrag.llm.openai"] = lightrag_openai
sys.modules["lightrag.utils"] = lightrag_utils
sys.modules["opendataloader_pdf"] = opendataloader_pdf
