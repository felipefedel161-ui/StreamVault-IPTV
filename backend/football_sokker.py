#!/usr/bin/env python3
# coding: utf-8
"""
StreamVault Arena — SokkerPro Football Engine
Professional module: fixtures today / live / predictions
Compatible with FootballRepository (Android) contract.
"""
from __future__ import annotations

import datetime
import logging
import threading
import time
from typing import Any

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

logger = logging.getLogger("arena.sokker")

BR_TZ = datetime.timezone(datetime.timedelta(hours=-3))
BASE_URL = "https://m2.sokkerpro.com"

# Grandes ligas / competições relevantes (nome parcial, case-insensitive)
MAJOR_LEAGUE_KEYWORDS = [
    "premier league", "la liga", "laliga", "serie a", "bundesliga", "ligue 1",
    "eredivisie", "primeira liga", "liga portugal", "süper lig", "super lig",
    "liga profesional", "brasileirão", "brasileirao", "serie a brazil", "série a",
    "liga argentina", "primera división", "mls", "liga mx",
    "champions league", "europa league", "conference league",
    "libertadores", "sudamericana", "copa américa", "copa america",
    "world cup", "euro championship", "nations league",
    "fa cup", "copa del rey", "coppa italia", "dfb-pokal", "coupe de france",
    "carabao", "efl cup", "community shield", "supercopa", "uefa super cup",
    "club world cup", "recopa", "mtn8", "caf champions",
]

BLACKLIST_KEYWORDS = [
    "u19", "u21", "u23", "sub ", "reserve", "reserves", "women", "feminino",
    "femenino", "amateur", "regional", "youth", "junior", "friendlies clubs",
    "club friendly",
]


def _session() -> requests.Session:
    s = requests.Session()
    s.mount(
        "https://",
        HTTPAdapter(max_retries=Retry(total=3, backoff_factor=0.6, status_forcelist=[502, 503, 504])),
    )
    s.headers.update({
        "User-Agent": "StreamVault-Arena/1.0",
        "Accept": "application/json",
    })
    return s


def _safe_int(v: Any, default: int | None = None) -> int | None:
    try:
        if v is None or v == "":
            return default
        return int(float(str(v).strip().replace("%", "").split("+")[0]))
    except Exception:
        return default


def _is_major(league_name: str) -> bool:
    ln = (league_name or "").lower()
    if any(b in ln for b in BLACKLIST_KEYWORDS):
        return False
    return any(k in ln for k in MAJOR_LEAGUE_KEYWORDS)


def _status_map(raw: str, minute: Any) -> tuple[str, str, int | None, bool, bool]:
    """Returns status, status_long, elapsed, is_live, is_finished."""
    s = (raw or "NS").upper().strip()
    finished = {"FT", "AET", "PEN", "AWARDED", "ABAN", "POST", "CANC", "WO"}
    live = {"1ST", "2ND", "HT", "LIVE", "ET", "P", "BT", "INT"}

    if s in finished:
        long = {
            "FT": "Match Finished", "AET": "After Extra Time", "PEN": "Penalties",
            "AWARDED": "Awarded", "ABAN": "Abandoned", "POST": "Postponed",
            "CANC": "Cancelled", "WO": "Walkover",
        }.get(s, s)
        return s, long, None, False, True

    if s in live or s in ("1H", "2H"):
        elapsed = _safe_int(minute)
        display = f"{elapsed}'" if elapsed is not None else "LIVE"
        long = "Halftime" if s == "HT" else "In Play"
        return s if s != "LIVE" else "1H", long, elapsed, True, False

    return "NS", "Not Started", None, False, False


def _parse_fixture(d: dict, force_major: bool | None = None) -> dict | None:
    league_name = d.get("leagueName") or d.get("league") or "Liga"
    if force_major is False:
        return None
    if force_major is None and not _is_major(league_name):
        return None

    mid = d.get("fixtureId") or d.get("id")
    if mid is None:
        return None
    try:
        fid = int(mid)
    except Exception:
        return None

    home = d.get("localTeamName") or d.get("home") or "Casa"
    away = d.get("visitorTeamName") or d.get("away") or "Fora"
    h_logo = d.get("localTeamFlag") or d.get("h_logo") or ""
    a_logo = d.get("visitorTeamFlag") or d.get("a_logo") or ""

    status_raw = str(d.get("status") or "NS")
    minute = d.get("minute") or d.get("minutePrimeiroTempo") or d.get("minuteSegundoTempo")
    status, status_long, elapsed, is_live, is_finished = _status_map(status_raw, minute)

    sh = _safe_int(d.get("scoresLocalTeam") if "scoresLocalTeam" in d else d.get("homeScore"))
    sa = _safe_int(d.get("scoresVisitorTeam") if "scoresVisitorTeam" in d else d.get("awayScore"))

    # Timestamp
    ts = None
    date_iso = None
    start_dt = d.get("startingAtDateTime") or d.get("startingAt")
    start_date = d.get("startingAtDate") or ""
    start_time = (d.get("startingAtTime") or "00:00:00")[:8]
    try:
        if start_dt:
            # Often "2026-08-12 20:00:00" or ISO
            raw = str(start_dt).replace("T", " ").replace("Z", "")[:19]
            dt = datetime.datetime.strptime(raw, "%Y-%m-%d %H:%M:%S")
            dt = dt.replace(tzinfo=datetime.timezone.utc)
            ts = int(dt.timestamp())
            date_iso = dt.astimezone(BR_TZ).isoformat()
        elif start_date:
            raw = f"{start_date} {start_time}"
            dt = datetime.datetime.strptime(raw[:19], "%Y-%m-%d %H:%M:%S")
            dt = dt.replace(tzinfo=datetime.timezone.utc)
            ts = int(dt.timestamp())
            date_iso = dt.astimezone(BR_TZ).isoformat()
    except Exception:
        pass

    country = d.get("countryName") or ""
    league_id = _safe_int(d.get("leagueId"))

    return {
        "id": fid,
        "date": date_iso,
        "timestamp": ts,
        "status": status,
        "status_long": status_long,
        "elapsed": elapsed,
        "is_live": is_live,
        "is_finished": is_finished,
        "is_major": True,
        "league": {
            "id": league_id,
            "name": league_name,
            "country": country,
            "logo": d.get("countryImagePath") or "",
            "round": d.get("round") or d.get("stageName") or "",
        },
        "home": {"id": _safe_int(d.get("localTeamId")), "name": home, "logo": h_logo or ""},
        "away": {"id": _safe_int(d.get("visitorTeamId")), "name": away, "logo": a_logo or ""},
        "goals": {"home": sh, "away": sa},
        "match_keywords": [],
    }


class SokkerArenaEngine:
    """Thread-safe cache + SokkerPro client for Arena endpoints."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._today_cache: dict[str, Any] = {}
        self._live_cache: list[dict] = []
        self._live_ts: float = 0
        self._detail_cache: dict[int, tuple[float, dict]] = {}
        self._session = _session()
        self._refresh_rate = 45  # seconds for live

    # ------------------------------------------------------------------ #
    # Low-level fetch
    # ------------------------------------------------------------------ #
    def _fetch_day(self, date_str: str, mini: bool = True) -> list[dict]:
        suffix = "/mini" if mini else ""
        url = f"{BASE_URL}/home/fixtures/{date_str}/utc{suffix}"
        try:
            r = self._session.get(url, timeout=25, verify=False)
            r.raise_for_status()
            payload = r.json()
            if not payload.get("success") and "data" not in payload:
                logger.warning("Sokker day payload unexpected for %s", date_str)
                return []
            categories = (payload.get("data") or {}).get("sortedCategorizedFixtures") or []
            out: list[dict] = []
            for cat in categories:
                lname = cat.get("leagueName") or ""
                # inject league name into each fixture
                for f in cat.get("fixtures") or []:
                    f = dict(f)
                    f.setdefault("leagueName", lname)
                    f.setdefault("countryName", cat.get("countryName") or "")
                    f.setdefault("leagueId", cat.get("leagueId"))
                    f.setdefault("countryImagePath", cat.get("countryImagePath") or "")
                    parsed = _parse_fixture(f)
                    if parsed:
                        out.append(parsed)
            return out
        except Exception as e:
            logger.error("fetch_day %s: %s", date_str, e)
            return []

    def _fetch_livescores(self) -> list[dict]:
        url = f"{BASE_URL}/livescores"
        try:
            r = self._session.get(url, timeout=15, verify=False)
            r.raise_for_status()
            payload = r.json()
            data = payload.get("data") or {}
            categories = data.get("sortedCategorizedFixtures") or []
            out: list[dict] = []
            for cat in categories:
                lname = cat.get("leagueName") or ""
                for f in cat.get("fixtures") or []:
                    f = dict(f)
                    f.setdefault("leagueName", lname)
                    f.setdefault("countryName", cat.get("countryName") or "")
                    f.setdefault("leagueId", cat.get("leagueId"))
                    parsed = _parse_fixture(f)
                    if parsed and parsed["is_live"]:
                        out.append(parsed)
            return out
        except Exception as e:
            logger.error("fetch_livescores: %s", e)
            return []

    def fetch_fixture_detail(self, fixture_id: int) -> dict | None:
        now = time.time()
        with self._lock:
            cached = self._detail_cache.get(fixture_id)
            if cached and now - cached[0] < 120:
                return cached[1]

        url = f"{BASE_URL}/fixture/{fixture_id}"
        try:
            r = self._session.get(url, timeout=20, verify=False)
            r.raise_for_status()
            payload = r.json()
            data = payload.get("data") or payload
            with self._lock:
                self._detail_cache[fixture_id] = (now, data)
            return data
        except Exception as e:
            logger.error("fixture detail %s: %s", fixture_id, e)
            return None

    # ------------------------------------------------------------------ #
    # Public API (matches Android contract)
    # ------------------------------------------------------------------ #
    def get_today(self, date_str: str | None = None) -> dict:
        if not date_str:
            date_str = datetime.datetime.now(BR_TZ).strftime("%Y-%m-%d")

        with self._lock:
            cached = self._today_cache.get(date_str)
            if cached and time.time() - cached["ts"] < 180:
                return {
                    "ok": True,
                    "date": date_str,
                    "count": len(cached["fixtures"]),
                    "fixtures": cached["fixtures"],
                }

        fixtures = self._fetch_day(date_str, mini=True)
        # sort by timestamp / time
        fixtures.sort(key=lambda x: (x.get("timestamp") or 0, x.get("id") or 0))

        with self._lock:
            self._today_cache[date_str] = {"ts": time.time(), "fixtures": fixtures}

        return {
            "ok": True,
            "date": date_str,
            "count": len(fixtures),
            "fixtures": fixtures,
        }

    def get_live(self) -> dict:
        now = time.time()
        with self._lock:
            if self._live_cache and now - self._live_ts < self._refresh_rate:
                return {
                    "ok": True,
                    "count": len(self._live_cache),
                    "fixtures": self._live_cache,
                    "leagues": self._league_catalog(),
                }

        live = self._fetch_livescores()
        # Fallback: if livescores empty, derive from today's live flags
        if not live:
            today = datetime.datetime.now(BR_TZ).strftime("%Y-%m-%d")
            day = self.get_today(today).get("fixtures") or []
            live = [f for f in day if f.get("is_live")]

        with self._lock:
            self._live_cache = live
            self._live_ts = now

        return {
            "ok": True,
            "count": len(live),
            "fixtures": live,
            "leagues": self._league_catalog(),
        }

    def get_prediction(self, fixture_id: int) -> dict:
        """Derive prediction from SokkerPro odds + xG (no external API)."""
        detail = self.fetch_fixture_detail(fixture_id)
        if not detail:
            return {"ok": False, "prediction": None}

        def _odd(key: str) -> float | None:
            raw = detail.get(key)
            if raw is None:
                return None
            try:
                return float(str(raw).split("#")[0])
            except Exception:
                return None

        oh = _odd("XBET_VENCEDOR_HOME") or _odd("BET365_VENCEDOR_HOME")
        od = _odd("XBET_VENCEDOR_DRAW") or _odd("BET365_VENCEDOR_DRAW")
        oa = _odd("XBET_VENCEDOR_AWAY") or _odd("BET365_VENCEDOR_AWAY")

        # Implied probabilities from odds
        inv = []
        labels = []
        for label, odd in (("home", oh), ("draw", od), ("away", oa)):
            if odd and odd > 1.01:
                inv.append(1.0 / odd)
                labels.append(label)
        total = sum(inv) or 1.0
        perc = {lab: round(100.0 * v / total, 1) for lab, v in zip(labels, inv)}

        winner = None
        if perc:
            winner = max(perc, key=perc.get)  # type: ignore
            winner = {"home": "Home", "draw": "Draw", "away": "Away"}.get(winner, winner)

        # Advice from xG + odds
        xg_h = detail.get("localXg") or detail.get("medias_home_xg")
        xg_a = detail.get("visitorXg") or detail.get("medias_away_xg")
        advice_parts = []
        if oh and oh < 1.50:
            advice_parts.append("Favorito claro da casa")
        elif oa and oa < 1.50:
            advice_parts.append("Favorito claro visitante")
        if xg_h is not None and xg_a is not None:
            try:
                advice_parts.append(f"xG {float(xg_h):.2f} x {float(xg_a):.2f}")
            except Exception:
                pass
        if not advice_parts and oh and oa:
            advice_parts.append("Odds equilibradas — cautela")

        prediction = {
            "winner": winner,
            "winner_comment": advice_parts[0] if advice_parts else None,
            "advice": " · ".join(advice_parts) if advice_parts else None,
            "percent": {
                "home": f"{perc.get('home', 0)}%",
                "draw": f"{perc.get('draw', 0)}%",
                "away": f"{perc.get('away', 0)}%",
            },
        }
        return {"ok": True, "prediction": prediction}

    @staticmethod
    def _league_catalog() -> dict[str, str]:
        return {
            "1": "World Cup", "2": "Champions League", "3": "Europa League",
            "4": "Euro Championship", "9": "Copa América", "11": "Sudamericana",
            "13": "Libertadores", "15": "FIFA Club World Cup", "39": "Premier League",
            "61": "Ligue 1", "71": "Brasileirão", "78": "Bundesliga",
            "88": "Eredivisie", "94": "Primeira Liga", "128": "Liga Argentina",
            "135": "Serie A", "140": "La Liga", "144": "Belgian Pro League",
            "203": "Süper Lig", "253": "MLS", "307": "Saudi Pro League",
            "848": "Conference League",
        }

    def warm_cache(self) -> None:
        today = datetime.datetime.now(BR_TZ).strftime("%Y-%m-%d")
        self.get_today(today)
        self.get_live()


# Singleton for simple import
arena_engine = SokkerArenaEngine()
