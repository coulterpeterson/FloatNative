"""On-disk credential storage. Lives in the OS user-config dir, file mode 0600."""

from __future__ import annotations

import json
import os
import time
from dataclasses import asdict, dataclass
from pathlib import Path

from platformdirs import user_config_path


def _config_dir() -> Path:
    path = user_config_path(appname="floatcli", appauthor=False)
    path.mkdir(parents=True, exist_ok=True)
    return path


def credentials_path() -> Path:
    return _config_dir() / "credentials.json"


def dpop_key_path() -> Path:
    return _config_dir() / "dpop_key.pem"


@dataclass
class Credentials:
    access_token: str
    refresh_token: str | None
    expires_at: float  # epoch seconds
    companion_api_key: str | None = None
    sails_sid: str | None = None

    @property
    def is_expired(self) -> bool:
        return time.time() >= self.expires_at - 30  # 30s safety margin

    def save(self) -> None:
        path = credentials_path()
        path.write_text(json.dumps(asdict(self), indent=2))
        os.chmod(path, 0o600)

    @classmethod
    def load(cls) -> "Credentials | None":
        path = credentials_path()
        if not path.exists():
            return None
        data = json.loads(path.read_text())
        return cls(**data)

    @classmethod
    def clear(cls) -> None:
        path = credentials_path()
        if path.exists():
            path.unlink()
