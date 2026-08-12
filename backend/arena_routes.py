#!/usr/bin/env python3
# coding: utf-8
"""
Flask routes for StreamVault Arena (SokkerPro).
Drop-in for the vault backend on Render.

Usage:
    from arena_routes import register_arena_routes
    register_arena_routes(app)
"""
from __future__ import annotations

import logging
from flask import jsonify, request

from football_sokker import arena_engine

logger = logging.getLogger("arena.routes")


def register_arena_routes(app) -> None:
    """Register /api/football/* and /api/ping on an existing Flask app."""

    @app.route("/api/ping")
    def api_ping():
        return jsonify({
            "ok": True,
            "db": "online",
            "versao": "arena-sokker-1.0",
            "hora": __import__("datetime").datetime.now(
                __import__("datetime").timezone(__import__("datetime").timedelta(hours=-3))
            ).strftime("%d/%m/%Y %H:%M"),
        })

    @app.route("/api/football/today")
    def api_football_today():
        date = request.args.get("date")  # optional YYYY-MM-DD
        try:
            data = arena_engine.get_today(date)
            return jsonify(data)
        except Exception as e:
            logger.exception("today failed")
            return jsonify({"ok": False, "count": 0, "fixtures": [], "error": str(e)}), 500

    @app.route("/api/football/live")
    def api_football_live():
        try:
            data = arena_engine.get_live()
            return jsonify(data)
        except Exception as e:
            logger.exception("live failed")
            return jsonify({"ok": False, "count": 0, "fixtures": [], "error": str(e)}), 500

    @app.route("/api/football/predictions/<int:fixture_id>")
    def api_football_predictions(fixture_id: int):
        try:
            data = arena_engine.get_prediction(fixture_id)
            return jsonify(data)
        except Exception as e:
            logger.exception("prediction failed")
            return jsonify({"ok": False, "prediction": None, "error": str(e)}), 500

    @app.route("/api/football/fixture/<int:fixture_id>")
    def api_football_fixture(fixture_id: int):
        """Rich detail (odds, xG, pressure) — optional for future UI."""
        try:
            detail = arena_engine.fetch_fixture_detail(fixture_id)
            if not detail:
                return jsonify({"ok": False, "data": None}), 404
            return jsonify({"ok": True, "data": detail})
        except Exception as e:
            logger.exception("fixture detail failed")
            return jsonify({"ok": False, "error": str(e)}), 500

    # Warm cache on first import / register
    try:
        arena_engine.warm_cache()
        logger.info("Arena cache warmed")
    except Exception as e:
        logger.warning("warm_cache: %s", e)
