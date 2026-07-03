import pytest

from scripts.dev_preview_server import _backend_url


@pytest.mark.no_db
def test_backend_url_allows_only_relative_api_paths() -> None:
    assert (
        _backend_url("https://forestmate.onrender.com", "/api/v1/mountains?name=%EC%82%B0")
        == "https://forestmate.onrender.com/api/v1/mountains?name=%EC%82%B0"
    )


@pytest.mark.no_db
@pytest.mark.parametrize(
    "request_target",
    [
        "https://evil.example/api/v1/mountains",
        "//evil.example/api/v1/mountains",
        "/api/v1/%2e%2e/admin",
        "/dashboard.html",
    ],
)
def test_backend_url_rejects_unsafe_proxy_targets(request_target: str) -> None:
    with pytest.raises(ValueError):
        _backend_url("https://forestmate.onrender.com", request_target)
