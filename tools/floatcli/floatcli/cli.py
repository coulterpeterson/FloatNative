"""Command-line entry point. Run `floatcli --help` after installing."""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path
from typing import Any, Optional

import typer
from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table

from . import __version__
from .auth import poll_for_token, refresh_credentials, start_device_auth
from .companion import CompanionClient
from .dpop import DPoPKey
from .fixtures import save_fixture
from .floatplane import FloatplaneClient, encode_array_query
from .spec import default_spec_path, load_spec, validate_response
from .storage import Credentials, dpop_key_path

app = typer.Typer(
    add_completion=False,
    help="Diagnostic CLI for the Floatplane and FloatNative Companion APIs.",
    rich_markup_mode="rich",
)
auth_app = typer.Typer(help="Login, logout, inspect saved tokens.")
feed_app = typer.Typer(help="Exercise feed-related endpoints.")
companion_app = typer.Typer(help="Hit the FloatNative Companion API.")
app.add_typer(auth_app, name="auth")
app.add_typer(feed_app, name="feed")
app.add_typer(companion_app, name="companion")

console = Console()
err_console = Console(stderr=True, style="bold red")


def _load_creds_or_die() -> Credentials:
    creds = Credentials.load()
    if creds is None:
        err_console.print("Not logged in. Run `floatcli auth login` first.")
        raise typer.Exit(code=2)
    return creds


def _client() -> FloatplaneClient:
    creds = _load_creds_or_die()
    key = DPoPKey.load_or_create(dpop_key_path())
    return FloatplaneClient(credentials=creds, dpop_key=key)


def _print_findings(findings: list, max_show: int) -> None:
    shown = findings[:max_show]
    for f in shown:
        console.print(f.render())
    if len(findings) > max_show:
        console.print(f"  … {len(findings) - max_show} more")


@app.callback()
def _root(
    version: bool = typer.Option(False, "--version", help="Show version and exit."),
) -> None:
    if version:
        console.print(f"floatcli {__version__}")
        raise typer.Exit()


# -----------------------------------------------------------------------------
# auth
# -----------------------------------------------------------------------------


@auth_app.command("login")
def auth_login() -> None:
    """Run the OAuth device-code flow against auth.floatplane.com."""
    key = DPoPKey.load_or_create(dpop_key_path())
    device = start_device_auth()
    console.print(
        Panel(
            f"Open this URL in a browser and approve:\n"
            f"  [cyan]{device.verification_uri_complete}[/cyan]\n\n"
            f"User code: [bold]{device.user_code}[/bold]\n"
            f"Polling every {device.interval}s, expires in {device.expires_in}s.",
            title="Floatplane device login",
        )
    )
    creds = poll_for_token(device.device_code, key=key, interval=device.interval)
    creds.save()
    console.print("[green]✓ Logged in. Tokens saved.[/green]")


@auth_app.command("status")
def auth_status() -> None:
    """Print the saved token state without revealing the tokens themselves."""
    creds = Credentials.load()
    if creds is None:
        console.print("Not logged in.")
        raise typer.Exit(code=1)
    table = Table(show_header=False, box=None)
    table.add_row("Access token", f"{creds.access_token[:8]}… ({len(creds.access_token)} chars)")
    table.add_row("Refresh token", "present" if creds.refresh_token else "missing")
    expires_in = creds.expires_at - time.time()
    table.add_row(
        "Expires in",
        f"{int(expires_in)}s" if expires_in > 0 else "[red]EXPIRED[/red]",
    )
    table.add_row(
        "Companion API key",
        "present" if creds.companion_api_key else "missing",
    )
    console.print(table)


@auth_app.command("refresh")
def auth_refresh() -> None:
    """Force a token refresh."""
    creds = _load_creds_or_die()
    key = DPoPKey.load_or_create(dpop_key_path())
    new = refresh_credentials(creds, key=key)
    new.save()
    console.print("[green]✓ Refreshed.[/green]")


@auth_app.command("set-cookie")
def auth_set_cookie(cookie: str = typer.Argument(..., help="sails.sid cookie string")) -> None:
    """Save a sails.sid session cookie for requests."""
    creds = _load_creds_or_die()
    clean = cookie.strip()
    if clean.startswith("sails.sid="):
        clean = clean[len("sails.sid="):].strip()
    creds.sails_sid = clean
    creds.save()
    console.print("[green]✓ sails.sid cookie saved.[/green]")


@auth_app.command("logout")
def auth_logout() -> None:
    """Delete saved credentials. Does NOT revoke server-side."""
    Credentials.clear()
    console.print("Cleared saved credentials.")


# -----------------------------------------------------------------------------
# generic raw + decode + diff
# -----------------------------------------------------------------------------


@app.command("get")
def get_cmd(
    path: str = typer.Argument(..., help="Path like /api/v3/user/self"),
    pretty: bool = typer.Option(True, help="Pretty-print JSON responses."),
    show_status: bool = typer.Option(True, help="Print status + matched op id."),
    capture: Optional[Path] = typer.Option(
        None,
        help="Directory to write a JSON fixture to.",
    ),
) -> None:
    """Issue an authenticated GET against floatplane.com and print the response."""
    with _client() as fp:
        resp = fp.get(path)
    if show_status:
        console.print(f"[dim]{resp.status_code} {resp.url}[/dim]")
    try:
        body = resp.json()
    except Exception:  # noqa: BLE001
        console.print(resp.text)
        return
    if pretty:
        console.print(Syntax(json.dumps(body, indent=2), "json", theme="monokai"))
    else:
        console.print_json(data=body)
    if capture is not None and resp.status_code == 200:
        fx = save_fixture(
            out_dir=capture,
            method="GET",
            path=path,
            status=resp.status_code,
            body=body,
        )
        console.print(f"[green]Captured fixture → {fx.path}[/green]")


@app.command("decode")
def decode_cmd(
    path: str = typer.Argument(
        ..., help="Path like /api/v3/content/creator/list?ids[0]=...&limit=20"
    ),
    spec_path: Optional[Path] = typer.Option(
        None, help="Override path to the OpenAPI spec."
    ),
    show_body: bool = typer.Option(
        False, help="Also dump the response body when validation fails."
    ),
    max_findings: int = typer.Option(20, help="Max number of findings to print."),
    capture: Optional[Path] = typer.Option(
        None, help="Save the response as a fixture even if validation fails."
    ),
) -> None:
    """Fetch a Floatplane endpoint and validate against the OpenAPI spec.

    Reports every required field that's missing, every type mismatch, and
    every enum violation — with full JSON pointer paths. This is the command
    to reach for when the iOS app shows "data couldn't be read because it
    is missing".
    """
    spec = load_spec(str(spec_path) if spec_path else None)
    with _client() as fp:
        resp = fp.get(path)

    if resp.status_code != 200:
        err_console.print(
            f"HTTP {resp.status_code} from {resp.url}\n{resp.text[:500]}"
        )
        raise typer.Exit(code=1)

    try:
        body: Any = resp.json()
    except Exception:  # noqa: BLE001
        err_console.print("Response was not JSON.")
        err_console.print(resp.text[:500])
        raise typer.Exit(code=1) from None

    result = validate_response(
        spec, method="GET", path=path, status=resp.status_code, body=body
    )
    header = (
        f"operation: [cyan]{result.operation_id or '?'}[/cyan] "
        f"({result.matched_path or 'no match'})"
    )
    console.print(header)
    if result.ok:
        console.print("[green]✓ Response matches the OpenAPI schema exactly.[/green]")
        if capture is not None:
            fx = save_fixture(
                out_dir=capture, method="GET", path=path, status=200, body=body
            )
            console.print(f"[green]Captured fixture → {fx.path}[/green]")
        return

    console.print(
        f"[yellow]✗ {len(result.findings)} schema deviation(s):[/yellow]"
    )
    _print_findings(result.findings, max_findings)
    if show_body:
        console.print("\n[dim]Full response body:[/dim]")
        console.print(Syntax(json.dumps(body, indent=2), "json", theme="monokai"))
    if capture is not None:
        fx = save_fixture(
            out_dir=capture, method="GET", path=path, status=200, body=body
        )
        console.print(f"[green]Captured fixture → {fx.path}[/green]")
    raise typer.Exit(code=1)


@app.command("diff")
def diff_cmd(
    path: str = typer.Argument(...),
    spec_path: Optional[Path] = typer.Option(None),
    max_findings: int = typer.Option(50),
) -> None:
    """Same as `decode` but exits 0 even with findings — useful in CI."""
    spec = load_spec(str(spec_path) if spec_path else None)
    with _client() as fp:
        resp = fp.get(path)
    if resp.status_code != 200:
        err_console.print(f"HTTP {resp.status_code} from {resp.url}")
        raise typer.Exit(code=1)
    body = resp.json()
    result = validate_response(
        spec, method="GET", path=path, status=resp.status_code, body=body
    )
    if result.ok:
        console.print("[green]✓ No schema deviations.[/green]")
        return
    console.print(f"{len(result.findings)} deviation(s):")
    _print_findings(result.findings, max_findings)


# -----------------------------------------------------------------------------
# feed (the screen that's broken)
# -----------------------------------------------------------------------------


@feed_app.command("home")
def feed_home(
    creator_ids: list[str] = typer.Option(
        None,
        "--creator",
        "-c",
        help=(
            "Creator ID to include. Repeat to add more. If omitted, will fetch "
            "your subscriptions and use those."
        ),
    ),
    limit: int = typer.Option(20),
    decode: bool = typer.Option(
        True, help="Also validate the response against the OpenAPI spec."
    ),
    show_body: bool = typer.Option(False),
    capture: Optional[Path] = typer.Option(None),
) -> None:
    """Replicate the iOS home-feed call — `GET /api/v3/content/creator/list`."""
    with _client() as fp:
        if not creator_ids:
            sub_resp = fp.get("/api/v3/user/subscriptions")
            if sub_resp.status_code != 200:
                err_console.print(f"subscriptions HTTP {sub_resp.status_code}")
                raise typer.Exit(code=1)
            creator_ids = [s["creator"] for s in sub_resp.json() if s.get("creator")]
            console.print(f"[dim]Using {len(creator_ids)} subscribed creator(s).[/dim]")

        params: list[tuple[str, str]] = [("limit", str(limit))]
        params += encode_array_query("ids", creator_ids)
        path = "/api/v3/content/creator/list"
        full = f"{path}?{httpx_qs(params)}"
        resp = fp.get(path, params=params)

    console.print(f"[dim]{resp.status_code} {resp.url}[/dim]")
    if resp.status_code != 200:
        err_console.print(resp.text[:500])
        raise typer.Exit(code=1)
    body = resp.json()

    n_posts = len(body.get("blogPosts") or [])
    console.print(f"Got {n_posts} post(s); {len(body.get('lastElements') or [])} cursor(s).")

    if decode:
        spec = load_spec()
        result = validate_response(
            spec, method="GET", path=full, status=resp.status_code, body=body
        )
        if result.ok:
            console.print("[green]✓ Response matches the OpenAPI schema exactly.[/green]")
        else:
            console.print(f"[yellow]✗ {len(result.findings)} schema deviation(s):[/yellow]")
            _print_findings(result.findings, 50)
    if show_body:
        console.print(Syntax(json.dumps(body, indent=2), "json", theme="monokai"))
    if capture is not None:
        fx = save_fixture(
            out_dir=capture, method="GET", path=full, status=200, body=body
        )
        console.print(f"[green]Captured fixture → {fx.path}[/green]")


def httpx_qs(params: list[tuple[str, str]]) -> str:
    from urllib.parse import urlencode

    return urlencode(params)


# -----------------------------------------------------------------------------
# spec inspection
# -----------------------------------------------------------------------------


@app.command("spec-info")
def spec_info(
    spec_path: Optional[Path] = typer.Option(None),
) -> None:
    """Print metadata about the bundled OpenAPI spec."""
    spec = load_spec(str(spec_path) if spec_path else None)
    info = spec.get("info", {})
    console.print(f"Title: {info.get('title')}")
    console.print(f"Version: {info.get('version')}")
    console.print(f"Path: {spec_path or default_spec_path()}")
    n_paths = len(spec.get("paths", {}))
    n_schemas = len(spec.get("components", {}).get("schemas", {}))
    console.print(f"Endpoints: {n_paths} | Schemas: {n_schemas}")


# -----------------------------------------------------------------------------
# companion
# -----------------------------------------------------------------------------


@companion_app.command("set-key")
def companion_set_key(
    api_key: str = typer.Argument(..., help="API key from `POST /auth/login`."),
) -> None:
    """Save a Companion API key into the credentials file.

    The companion API issues keys via `POST /auth/login` with a Floatplane
    access token + DPoP proof. For now, the easiest way to get one is from the
    iOS app's keychain or by running the iOS app once with debug logging on.
    Future versions of this CLI will mint one directly.
    """
    creds = _load_creds_or_die()
    creds.companion_api_key = api_key
    creds.save()
    console.print("[green]✓ Companion API key saved.[/green]")


@companion_app.command("get")
def companion_get(
    path: str = typer.Argument(..., help="Path like /playlists or /watch-later"),
) -> None:
    """Issue an authenticated GET against the Companion API."""
    creds = _load_creds_or_die()
    with CompanionClient(credentials=creds) as cc:
        resp = cc.request("GET", path)
    console.print(f"[dim]{resp.status_code}[/dim]")
    try:
        console.print_json(data=resp.json())
    except Exception:  # noqa: BLE001
        console.print(resp.text)


# -----------------------------------------------------------------------------


def main() -> None:  # for `python -m floatcli`
    app()


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())  # type: ignore[func-returns-value]
