"""Progress tracker: Eel + SQLite + LAN sync.  Run with:  python main.py"""
import calendar
import json
import os
import shutil
import socket
import sqlite3
import threading
import time
import urllib.request
import uuid
from contextlib import closing, contextmanager
from datetime import date, datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import eel

BASE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE, "progress.db")
SCHEMA_VERSION = 3          # 1 = tasks + completions, 2 = + weights/settings, 3 = + sync columns
KG_PER_LB = 0.45359237

DISCOVERY_PORT = 54321      # UDP
SYNC_PORT = 54322           # TCP
PROTOCOL_VERSION = 1
PING = b"THEMIS_DISCOVERY_PING"
MAX_BODY = 5 * 1024 * 1024
SYNCED_WINDOW = 60          # seconds a sync counts as "just now"
EPOCH = "1970-01-01T00:00:00Z"


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


@contextmanager
def db():
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def _add_column(c, table, col, ddl):
    """ALTER TABLE ADD COLUMN if missing. Returns True when it was added."""
    if col in {r["name"] for r in c.execute(f"PRAGMA table_info({table})")}:
        return False
    c.execute(f"ALTER TABLE {table} ADD COLUMN {col} {ddl}")
    return True


def meta_get(c, key, default=None):
    r = c.execute("SELECT value FROM device_meta WHERE key = ?", (key,)).fetchone()
    return r["value"] if r else default


def meta_set(c, key, value):
    c.execute("INSERT OR REPLACE INTO device_meta (key, value) VALUES (?, ?)", (key, value))


def init_db():
    """Create or upgrade the database. Existing rows are never dropped, and a
    backup copy is saved before any upgrade."""
    if os.path.exists(DB_PATH) and os.path.getsize(DB_PATH) > 0:
        with closing(sqlite3.connect(DB_PATH)) as c:
            version = c.execute("PRAGMA user_version").fetchone()[0]
        if version < SCHEMA_VERSION:
            backup = f"{DB_PATH}.v{version}.bak"
            if not os.path.exists(backup):
                shutil.copy2(DB_PATH, backup)

    with db() as c:
        c.executescript(
            f"""
            CREATE TABLE IF NOT EXISTS tasks (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                title       TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                created_at  TEXT NOT NULL,
                uid         TEXT,
                updated_at  TEXT NOT NULL DEFAULT '{EPOCH}',
                is_deleted  INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS completions (
                task_id    INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                day        TEXT NOT NULL,
                updated_at TEXT NOT NULL DEFAULT '{EPOCH}',
                is_deleted INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (task_id, day)
            );
            CREATE TABLE IF NOT EXISTS weights (
                day        TEXT PRIMARY KEY,
                kg         REAL NOT NULL,
                updated_at TEXT NOT NULL DEFAULT '{EPOCH}',
                is_deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS settings (
                key        TEXT PRIMARY KEY,
                value      TEXT NOT NULL,
                updated_at TEXT NOT NULL DEFAULT '{EPOCH}'
            );
            CREATE TABLE IF NOT EXISTS device_meta (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """
        )
        # v2 -> v3. SQLite can't ADD COLUMN with a non-constant default
        # (the spec's strftime default), so add a constant and backfill.
        stamp = now_iso()
        for table in ("tasks", "completions", "weights", "settings"):
            if _add_column(c, table, "updated_at", f"TEXT NOT NULL DEFAULT '{EPOCH}'"):
                c.execute(f"UPDATE {table} SET updated_at = ?", (stamp,))
        for table in ("tasks", "completions", "weights"):
            _add_column(c, table, "is_deleted", "INTEGER NOT NULL DEFAULT 0")
        _add_column(c, "tasks", "uid", "TEXT")
        for r in c.execute("SELECT id FROM tasks WHERE uid IS NULL OR uid = ''").fetchall():
            c.execute("UPDATE tasks SET uid = ? WHERE id = ?", (str(uuid.uuid4()), r["id"]))
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_uid ON tasks(uid)")
        if meta_get(c, "device_id") is None:
            meta_set(c, "device_id", str(uuid.uuid4()))
        c.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")


# ---------- tasks ----------
@eel.expose
def add_task(title, description=""):
    title = (title or "").strip()
    if not title:
        return {"ok": False, "error": "A task needs a title."}
    with db() as c:
        c.execute(
            "INSERT INTO tasks (title, description, created_at, uid, updated_at) VALUES (?, ?, ?, ?, ?)",
            (title, (description or "").strip(), date.today().isoformat(), str(uuid.uuid4()), now_iso()),
        )
    return {"ok": True}


@eel.expose
def delete_task(task_id):
    # Soft delete so the deletion can sync to other devices.
    with db() as c:
        c.execute("UPDATE tasks SET is_deleted = 1, updated_at = ? WHERE id = ?", (now_iso(), task_id))
    return {"ok": True}


@eel.expose
def get_day(day):
    """Tasks that applied on `day`, each flagged done / not done."""
    with db() as c:
        rows = c.execute(
            """
            SELECT t.id, t.title, t.description,
                   EXISTS(SELECT 1 FROM completions x
                          WHERE x.task_id = t.id AND x.day = ? AND x.is_deleted = 0) AS done
            FROM tasks t
            WHERE t.created_at <= ? AND t.is_deleted = 0
            ORDER BY t.id
            """,
            (day, day),
        ).fetchall()
    return [dict(r) for r in rows]


@eel.expose
def set_completion(task_id, day, done):
    if day > date.today().isoformat():
        return {"ok": False, "error": "You can't tick off a future day."}
    with db() as c:
        t = c.execute(
            "SELECT created_at FROM tasks WHERE id = ? AND is_deleted = 0", (task_id,)
        ).fetchone()
        if not t or day < t["created_at"]:
            return {"ok": False, "error": "This task didn't exist on that day."}
        c.execute(
            """INSERT INTO completions (task_id, day, updated_at, is_deleted) VALUES (?, ?, ?, ?)
               ON CONFLICT(task_id, day) DO UPDATE SET
                 updated_at = excluded.updated_at, is_deleted = excluded.is_deleted""",
            (task_id, day, now_iso(), 0 if done else 1),
        )
    return {"ok": True}


# ---------- quote ----------
@eel.expose
def get_quote():
    """Fetch a random quote from ZenQuotes. Returns None if offline or rate-limited."""
    try:
        req = urllib.request.Request(
            "https://zenquotes.io/api/random", headers={"User-Agent": "progress-tracker"}
        )
        with urllib.request.urlopen(req, timeout=6) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        item = data[0]
        text, author = item.get("q", "").strip(), item.get("a", "").strip()
        if not text or author.lower() == "zenquotes.io":
            return None
        return {"text": text, "author": author}
    except Exception:
        return None


# ---------- weight ----------
def _unit(c):
    r = c.execute("SELECT value FROM settings WHERE key = 'unit'").fetchone()
    return r["value"] if r and r["value"] in ("kg", "lb") else "kg"


def _out(kg, unit):
    return round(kg / KG_PER_LB if unit == "lb" else kg, 1)


def _months_ago(d, n):
    m = d.month - n
    y = d.year
    while m < 1:
        m += 12
        y -= 1
    return date(y, m, min(d.day, calendar.monthrange(y, m)[1]))


@eel.expose
def set_unit(unit):
    if unit not in ("kg", "lb"):
        return {"ok": False, "error": "Unit must be kg or lb."}
    with db() as c:
        c.execute(
            "INSERT OR REPLACE INTO settings (key, value, updated_at) VALUES ('unit', ?, ?)",
            (unit, now_iso()),
        )
    return {"ok": True}


@eel.expose
def set_weight(day, value, unit):
    try:
        value = float(value)
        date.fromisoformat(day)
    except (TypeError, ValueError):
        return {"ok": False, "error": "Enter a valid date and weight."}
    if day > date.today().isoformat():
        return {"ok": False, "error": "You can't log a weight for a future day."}
    kg = value * KG_PER_LB if unit == "lb" else value
    if not 20 <= kg <= 500:
        return {"ok": False, "error": "That weight looks out of range."}
    with db() as c:
        c.execute(
            """INSERT INTO weights (day, kg, updated_at, is_deleted) VALUES (?, ?, ?, 0)
               ON CONFLICT(day) DO UPDATE SET
                 kg = excluded.kg, updated_at = excluded.updated_at, is_deleted = 0""",
            (day, kg, now_iso()),
        )
    return {"ok": True}


@eel.expose
def delete_weight(day):
    with db() as c:
        c.execute("UPDATE weights SET is_deleted = 1, updated_at = ? WHERE day = ?", (now_iso(), day))
    return {"ok": True}


@eel.expose
def get_weights():
    """Weight entries for the past 6 months, in the user's chosen unit."""
    today = date.today()
    start = _months_ago(today, 6)
    with db() as c:
        unit = _unit(c)
        rows = c.execute(
            "SELECT day, kg FROM weights WHERE day >= ? AND day <= ? AND is_deleted = 0 ORDER BY day",
            (start.isoformat(), today.isoformat()),
        ).fetchall()
        latest = c.execute(
            "SELECT day, kg FROM weights WHERE is_deleted = 0 ORDER BY day DESC LIMIT 1"
        ).fetchone()
    points = [{"day": r["day"], "value": _out(r["kg"], unit)} for r in rows]
    return {
        "unit": unit,
        "start": start.isoformat(),
        "end": today.isoformat(),
        "today": today.isoformat(),
        "points": points,
        "latest": {"day": latest["day"], "value": _out(latest["kg"], unit)} if latest else None,
        "change": round(points[-1]["value"] - points[0]["value"], 1) if len(points) > 1 else None,
        "lowest": min((p["value"] for p in points), default=None),
        "highest": max((p["value"] for p in points), default=None),
    }


# ---------- stats ----------
@eel.expose
def get_stats(weeks=26):
    today = date.today()
    with db() as c:
        tasks = [r["created_at"] for r in c.execute("SELECT created_at FROM tasks WHERE is_deleted = 0")]
        done_by_day = {
            r["day"]: r["n"]
            for r in c.execute(
                """SELECT x.day, COUNT(*) AS n FROM completions x
                   JOIN tasks t ON t.id = x.task_id
                   WHERE x.is_deleted = 0 AND t.is_deleted = 0 GROUP BY x.day"""
            )
        }

    def totals(day_iso):
        return sum(1 for created in tasks if created <= day_iso)

    days_since_sunday = (today.weekday() + 1) % 7
    start = today - timedelta(days=days_since_sunday + 7 * (weeks - 1))
    end = start + timedelta(days=7 * weeks - 1)

    days = []
    d = start
    while d <= end:
        iso = d.isoformat()
        total = totals(iso) if d <= today else 0
        done = min(done_by_day.get(iso, 0), total) if d <= today else 0
        days.append(
            {
                "date": iso,
                "total": total,
                "done": done,
                "pct": round(100 * done / total) if total else None,
                "future": d > today,
            }
        )
        d += timedelta(days=1)

    daily_pcts, perfect_days, tracked_days = [], 0, 0
    if tasks:
        d = date.fromisoformat(min(tasks))
        while d <= today:
            iso = d.isoformat()
            total = totals(iso)
            if total:
                done = min(done_by_day.get(iso, 0), total)
                daily_pcts.append(done / total)
                tracked_days += 1
                perfect_days += done == total
            d += timedelta(days=1)

    avg_done = round(100 * sum(daily_pcts) / len(daily_pcts), 1) if daily_pcts else 0

    streak = 0
    d = today
    if tasks:
        t = totals(today.isoformat())
        if not (t and done_by_day.get(today.isoformat(), 0) >= t):
            d = today - timedelta(days=1)
        while True:
            iso = d.isoformat()
            t = totals(iso)
            if t and done_by_day.get(iso, 0) >= t:
                streak += 1
                d -= timedelta(days=1)
            else:
                break

    return {
        "days": days,
        "today": today.isoformat(),
        "avg_done": avg_done,
        "avg_not_done": round(100 - avg_done, 1) if daily_pcts else 0,
        "tracked_days": tracked_days,
        "perfect_days": perfect_days,
        "streak": streak,
        "has_data": bool(daily_pcts),
    }


# ======================================================================
# LAN sync: UDP discovery beacon + HTTP sync server (background threads)
# ======================================================================
_sync = {"udp": False, "http": False, "last_sync": None, "peer": None}
_stop = threading.Event()


def _mark_synced(peer_ip):
    _sync["last_sync"] = time.time()
    _sync["peer"] = peer_ip


@eel.expose
def get_sync_status():
    with db() as c:
        last = meta_get(c, "last_synced_at")
    if not (_sync["udp"] and _sync["http"]):
        state = "offline"
    elif _sync["last_sync"] and time.time() - _sync["last_sync"] < SYNCED_WINDOW:
        state = "synced"
    else:
        state = "searching"
    return {"state": state, "last_synced_at": last, "peer": _sync["peer"]}


def _discovery_loop():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("", DISCOVERY_PORT))
    except OSError as e:
        print(f"[sync] UDP discovery disabled: {e}")
        sock.close()
        return
    sock.settimeout(1.0)
    _sync["udp"] = True
    reply = json.dumps(
        {
            "app": "Themis",
            "role": "desktop",
            "device_name": socket.gethostname(),
            "port": SYNC_PORT,
            "protocol_version": PROTOCOL_VERSION,
        }
    ).encode()
    while not _stop.is_set():
        try:
            data, addr = sock.recvfrom(1024)
        except socket.timeout:
            continue
        except OSError:
            break
        if data.strip() == PING:
            try:
                sock.sendto(reply, addr)
            except OSError:
                pass
    _sync["udp"] = False
    sock.close()


def _pull_records(since):
    # >= (not >) so a record written in the same second as `since` is never
    # missed; re-applying a record is harmless because merges are idempotent.
    with db() as c:
        tasks = [
            {"uid": r["uid"], "title": r["title"], "description": r["description"],
             "created_at": r["created_at"], "updated_at": r["updated_at"], "is_deleted": r["is_deleted"]}
            for r in c.execute("SELECT * FROM tasks WHERE updated_at >= ?", (since,))
        ]
        completions = [
            {"task_uid": r["uid"], "day": r["day"], "updated_at": r["updated_at"], "is_deleted": r["is_deleted"]}
            for r in c.execute(
                """SELECT t.uid, x.day, x.updated_at, x.is_deleted FROM completions x
                   JOIN tasks t ON t.id = x.task_id WHERE x.updated_at >= ?""",
                (since,),
            )
        ]
        weights = [
            {"day": r["day"], "kg": r["kg"], "updated_at": r["updated_at"], "is_deleted": r["is_deleted"]}
            for r in c.execute("SELECT * FROM weights WHERE updated_at >= ?", (since,))
        ]
        settings = [
            {"key": r["key"], "value": r["value"], "updated_at": r["updated_at"]}
            for r in c.execute("SELECT * FROM settings WHERE updated_at >= ?", (since,))
        ]
    return {"ok": True, "server_time": now_iso(),
            "tasks": tasks, "completions": completions, "weights": weights, "settings": settings}


def _apply_push(p):
    """Merge mobile records into the local DB. Last-write-wins on updated_at."""
    with db() as c:
        for r in p.get("tasks", []):
            row = c.execute("SELECT updated_at FROM tasks WHERE uid = ?", (r["uid"],)).fetchone()
            vals = (r["title"], r.get("description", ""), r["created_at"], int(bool(r.get("is_deleted"))), r["updated_at"])
            if row is None:
                c.execute(
                    "INSERT INTO tasks (title, description, created_at, is_deleted, updated_at, uid) VALUES (?,?,?,?,?,?)",
                    vals + (r["uid"],),
                )
            elif r["updated_at"] > row["updated_at"]:
                c.execute(
                    "UPDATE tasks SET title=?, description=?, created_at=?, is_deleted=?, updated_at=? WHERE uid=?",
                    vals + (r["uid"],),
                )
        for r in p.get("completions", []):
            t = c.execute("SELECT id FROM tasks WHERE uid = ?", (r["task_uid"],)).fetchone()
            if t is None:
                continue
            c.execute(
                """INSERT INTO completions (task_id, day, updated_at, is_deleted) VALUES (?,?,?,?)
                   ON CONFLICT(task_id, day) DO UPDATE SET
                     updated_at = excluded.updated_at, is_deleted = excluded.is_deleted
                   WHERE excluded.updated_at > completions.updated_at""",
                (t["id"], r["day"], r["updated_at"], int(bool(r.get("is_deleted")))),
            )
        for r in p.get("weights", []):
            kg = float(r["kg"])
            if not 20 <= kg <= 500:
                continue
            c.execute(
                """INSERT INTO weights (day, kg, updated_at, is_deleted) VALUES (?,?,?,?)
                   ON CONFLICT(day) DO UPDATE SET
                     kg = excluded.kg, updated_at = excluded.updated_at, is_deleted = excluded.is_deleted
                   WHERE excluded.updated_at > weights.updated_at""",
                (r["day"], kg, r["updated_at"], int(bool(r.get("is_deleted")))),
            )
        for r in p.get("settings", []):
            c.execute(
                """INSERT INTO settings (key, value, updated_at) VALUES (?,?,?)
                   ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
                   WHERE excluded.updated_at > settings.updated_at""",
                (r["key"], r["value"], r["updated_at"]),
            )
        synced_at = now_iso()
        meta_set(c, "last_synced_at", synced_at)
    return synced_at


class SyncHandler(BaseHTTPRequestHandler):
    server_version = "ThemisSync/1"

    def log_message(self, *a):  # keep the console quiet
        pass

    def _send(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _json_body(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n > MAX_BODY:
            raise ValueError("body too large")
        return json.loads(self.rfile.read(n) or b"{}")

    def do_GET(self):
        if self.path == "/api/v1/sync/status":
            with db() as c:
                return self._send(200, {"device_id": meta_get(c, "device_id"),
                                        "last_synced_at": meta_get(c, "last_synced_at")})
        self._send(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        try:
            body = self._json_body()
            if self.path == "/api/v1/sync/pull":
                return self._send(200, _pull_records(body.get("since") or EPOCH))
            if self.path == "/api/v1/sync/push":
                synced_at = _apply_push(body)
                _mark_synced(self.client_address[0])
                return self._send(200, {"ok": True, "synced_at": synced_at})
            self._send(404, {"ok": False, "error": "not found"})
        except (ValueError, KeyError, TypeError) as e:
            self._send(400, {"ok": False, "error": f"bad request: {e}"})
        except Exception as e:
            self._send(500, {"ok": False, "error": str(e)})


def _http_loop():
    try:
        server = ThreadingHTTPServer(("0.0.0.0", SYNC_PORT), SyncHandler)
    except OSError as e:
        print(f"[sync] HTTP sync server disabled: {e}")
        return
    server.daemon_threads = True
    _sync["http"] = True
    server.serve_forever()


def start_sync_services():
    for fn in (_discovery_loop, _http_loop):
        threading.Thread(target=fn, daemon=True).start()


if __name__ == "__main__":
    init_db()
    start_sync_services()
    eel.init(os.path.join(BASE, "web"))
    eel.start("index.html", size=(1120, 860))
