"""Tests for environment-derived AI service configuration."""
from app.core.config import Settings


def test_resolved_redis_url_prefers_explicit_url() -> None:
    """An explicit Redis URL remains unchanged."""
    settings = Settings(
        _env_file=None,
        redis_url="redis://cache:6380/3",
    )

    assert settings.resolved_redis_url() == "redis://cache:6380/3"


def test_resolved_redis_url_encodes_password() -> None:
    """Redis credentials with URL-reserved characters remain parseable."""
    settings = Settings(
        _env_file=None,
        redis_url="",
        redis_host="redis",
        redis_port=6379,
        redis_password="p@ss:/word",
        redis_db=0,
    )

    assert (
        settings.resolved_redis_url()
        == "redis://:p%40ss%3A%2Fword@redis:6379/0"
    )
