# StreamVault Arena Backend (SokkerPro)

Professional football data layer for the **Centro de Futebol / Arena** screen.

## Endpoints (Android contract)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ping` | Health check (wakes Render free tier) |
| GET | `/api/football/today` | Today's major-league fixtures |
| GET | `/api/football/live` | Live matches only |
| GET | `/api/football/predictions/<id>` | Odds + xG derived tip |
| GET | `/api/football/fixture/<id>` | Full SokkerPro detail (odds, pressure, xG) |

## Data source

**Only SokkerPro** (`m2.sokkerpro.com`):

- `/home/fixtures/{date}/utc/mini` — day grid
- `/livescores` — in-play
- `/fixture/{id}` — rich match detail (751 fields)

No api-football / RapidAPI key required.

## Integrate into existing Flask app

```python
from arena_routes import register_arena_routes
register_arena_routes(app)
```

Or run standalone:

```bash
pip install -r requirements.txt
# wire register_arena_routes into your app factory / main
```

## Deploy on Render

1. Copy `football_sokker.py` + `arena_routes.py` into the vault service repo.
2. Call `register_arena_routes(app)` at startup.
3. Redeploy. Cold start is handled by the app's `/api/ping` wake.

## Filter policy

- Whitelist of major leagues (Premier, La Liga, Serie A, Bundesliga, Ligue 1, Brasileirão, Libertadores, Champions, etc.)
- Blacklist: Women, U19/U21, Reserves, Amateur
