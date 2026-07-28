"""HTTP client for the Floatplane API. Handles DPoP, nonce loops, and refresh.

The behavior here intentionally matches FloatplaneAPI.swift's `request<T>`:
- Send DPoP proof + `Authorization: DPoP <token>` on every authenticated call
- On 401/403 with `WWW-Authenticate: DPoP error=use_dpop_nonce`, retry with the
  server-provided nonce (up to 3 times)
- On 401, attempt one refresh and retry the original request
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import httpx

from .auth import refresh_credentials
from .dpop import DPoPKey, generate_proof
from .storage import Credentials

API_BASE = "https://www.floatplane.com"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


@dataclass
class Response:
    status_code: int
    headers: dict[str, str]
    body: bytes
    url: str

    @property
    def text(self) -> str:
        return self.body.decode("utf-8", errors="replace")

    def json(self) -> Any:
        import json

        return json.loads(self.text)


class FloatplaneClient:
    def __init__(
        self,
        *,
        credentials: Credentials,
        dpop_key: DPoPKey,
        save_on_refresh: bool = True,
        client: httpx.Client | None = None,
    ) -> None:
        self.credentials = credentials
        self.dpop_key = dpop_key
        self.save_on_refresh = save_on_refresh
        self._client = client or httpx.Client(
            timeout=30,
            headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
        )
        self._nonce: str | None = None

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> "FloatplaneClient":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    def request(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, Any] | list[tuple[str, Any]] | None = None,
        json_body: Any = None,
        authenticated: bool = True,
        _refresh_attempted: bool = False,
        _nonce_retries: int = 0,
    ) -> Response:
        url = f"{API_BASE}{path}"
        headers: dict[str, str] = {}
        if authenticated:
            if self.credentials.sails_sid:
                headers["Cookie"] = f"sails.sid={self.credentials.sails_sid}"
            if self.credentials.is_expired:
                self._refresh()
            access = self.credentials.access_token
            proof = generate_proof(
                self.dpop_key,
                http_method=method,
                http_url=url,
                access_token=access,
                nonce=self._nonce,
            )
            headers["DPoP"] = proof
            headers["Authorization"] = f"DPoP {access}"

        resp = self._client.request(
            method, url, params=params, json=json_body, headers=headers
        )

        new_nonce = resp.headers.get("DPoP-Nonce")
        if new_nonce:
            self._nonce = new_nonce

        if resp.status_code in (401, 403):
            www_auth = resp.headers.get("WWW-Authenticate", "").lower()
            if "use_dpop_nonce" in www_auth and _nonce_retries < 3:
                return self.request(
                    method,
                    path,
                    params=params,
                    json_body=json_body,
                    authenticated=authenticated,
                    _refresh_attempted=_refresh_attempted,
                    _nonce_retries=_nonce_retries + 1,
                )
            if resp.status_code == 401 and authenticated and not _refresh_attempted:
                self._refresh()
                return self.request(
                    method,
                    path,
                    params=params,
                    json_body=json_body,
                    authenticated=authenticated,
                    _refresh_attempted=True,
                )

        return Response(
            status_code=resp.status_code,
            headers=dict(resp.headers),
            body=resp.content,
            url=str(resp.url),
        )

    def get(
        self,
        path: str,
        *,
        params: dict[str, Any] | list[tuple[str, Any]] | None = None,
        authenticated: bool = True,
    ) -> Response:
        return self.request("GET", path, params=params, authenticated=authenticated)

    def _refresh(self) -> None:
        new_creds = refresh_credentials(self.credentials, key=self.dpop_key)
        self.credentials = new_creds
        if self.save_on_refresh:
            new_creds.save()


def encode_array_query(name: str, values: list[str]) -> list[tuple[str, str]]:
    """Encode a PHP-style indexed array query: ids[0]=a&ids[1]=b.

    Matches HomeFeedViewModel's `idsParam` construction in iOS.
    """
    return [(f"{name}[{i}]", v) for i, v in enumerate(values)]
