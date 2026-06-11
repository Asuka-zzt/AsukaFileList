"""AI service test configuration."""
import os
import sys
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
