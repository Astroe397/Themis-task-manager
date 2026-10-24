"""Progress tracker: Eel + SQLite + LAN sync.  Run with:  python main.py"""
import calendar
import json
import os
import re
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
SCHEMA_VERSION = 5          # 1 = tasks + completions, 2 = + weights/settings, 3 = + sync columns, 4 = + one-off tasks,
                            # 5 = + strength sets, activities, meditation sessions
KG_PER_LB = 0.45359237

DISCOVERY_PORT = 54321      # UDP
SYNC_PORT = 54322           # TCP
PROTOCOL_VERSION = 1
PING = b"THEMIS_DISCOVERY_PING"
MAX_BODY = 25 * 1024 * 1024   # activity routes can be large
SYNCED_WINDOW = 60          # seconds a sync counts as "just now"
EPOCH = "1970-01-01T00:00:00Z"
ISO_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")


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
                is_deleted  INTEGER NOT NULL DEFAULT 0,
                is_one_off  INTEGER NOT NULL DEFAULT 0,   -- 1 = do once, never repeats
                is_done     INTEGER NOT NULL DEFAULT 0    -- only meaningful for one-off tasks
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
            -- v5. Rows below are keyed by UUID so phone and desktop never collide.
            CREATE TABLE IF NOT EXISTS exercises (
                id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                name                TEXT NOT NULL,
                primary_group       TEXT NOT NULL,
                uses_bodyweight     INTEGER NOT NULL DEFAULT 0,
                baseline_multiplier REAL NOT NULL DEFAULT 1.0,
                updated_at          TEXT NOT NULL DEFAULT '{EPOCH}',
                is_deleted          INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS strength_sets (
                id            TEXT PRIMARY KEY,
                exercise_name TEXT NOT NULL,
                primary_group TEXT NOT NULL,
                weight_kg     REAL NOT NULL,
                reps          INTEGER NOT NULL,
                e1rm_kg       REAL NOT NULL,
                timestamp     INTEGER NOT NULL,          -- epoch milliseconds
                updated_at    TEXT NOT NULL DEFAULT '{EPOCH}',
                is_deleted    INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS activities (
                id               TEXT PRIMARY KEY,
                type             TEXT NOT NULL,
                start_time       INTEGER NOT NULL,       -- epoch milliseconds
                end_time         INTEGER NOT NULL,
                distance_meters  REAL NOT NULL DEFAULT 0.0,
                duration_seconds INTEGER NOT NULL DEFAULT 0,
                steps            INTEGER NOT NULL DEFAULT 0,
                route_json       TEXT NOT NULL DEFAULT '[]',
                updated_at       TEXT NOT NULL DEFAULT '{EPOCH}',
                is_deleted       INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS meditation_sessions (
                id               TEXT PRIMARY KEY,
                timestamp        INTEGER NOT NULL,       -- epoch milliseconds
                duration_seconds INTEGER NOT NULL,
                sound_pattern    TEXT NOT NULL DEFAULT '',
                updated_at       TEXT NOT NULL DEFAULT '{EPOCH}',
                is_deleted       INTEGER NOT NULL DEFAULT 0
            );
            CREATE UNIQUE INDEX IF NOT EXISTS idx_exercises_name ON exercises(name);
            CREATE INDEX IF NOT EXISTS idx_sets_updated  ON strength_sets(updated_at);
            CREATE INDEX IF NOT EXISTS idx_sets_time     ON strength_sets(timestamp);
            CREATE INDEX IF NOT EXISTS idx_act_updated   ON activities(updated_at);
            CREATE INDEX IF NOT EXISTS idx_med_updated   ON meditation_sessions(updated_at);
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
        _add_column(c, "tasks", "is_one_off", "INTEGER NOT NULL DEFAULT 0")   # v4
        _add_column(c, "tasks", "is_done", "INTEGER NOT NULL DEFAULT 0")
        for r in c.execute("SELECT id FROM tasks WHERE uid IS NULL OR uid = ''").fetchall():
            c.execute("UPDATE tasks SET uid = ? WHERE id = ?", (str(uuid.uuid4()), r["id"]))
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_uid ON tasks(uid)")
        # v4 -> v5: the exercise catalogue ships with the same defaults as the phone.
        for name, group, mult, bw in DEFAULT_EXERCISES:
            c.execute(
                "INSERT OR IGNORE INTO exercises (name, primary_group, uses_bodyweight, baseline_multiplier, updated_at) "
                "VALUES (?,?,?,?,?)", (name, group, bw, mult, stamp))
        if meta_get(c, "device_id") is None:
            meta_set(c, "device_id", str(uuid.uuid4()))
        c.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")


# ---------- tasks ----------
@eel.expose
def add_task(title, description="", one_off=False):
    title = (title or "").strip()
    if not title:
        return {"ok": False, "error": "A task needs a title."}
    with db() as c:
        c.execute(
            "INSERT INTO tasks (title, description, created_at, uid, updated_at, is_one_off) VALUES (?, ?, ?, ?, ?, ?)",
            (title, (description or "").strip(), date.today().isoformat(), str(uuid.uuid4()), now_iso(),
             1 if one_off else 0),
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
            WHERE t.created_at <= ? AND t.is_deleted = 0 AND t.is_one_off = 0
            ORDER BY t.id
            """,
            (day, day),
        ).fetchall()
    return [dict(r) for r in rows]


@eel.expose
def get_one_offs():
    """One-off tasks: open ones first (oldest first), then finished ones (latest first).
    They never feed the heatmap, streak or percentages."""
    with db() as c:
        rows = c.execute(
            """SELECT id, title, description, created_at, is_done AS done FROM tasks
               WHERE is_one_off = 1 AND is_deleted = 0
               ORDER BY is_done, CASE WHEN is_done = 0 THEN id END ASC, updated_at DESC"""
        ).fetchall()
    return [dict(r) for r in rows]


@eel.expose
def set_one_off_done(task_id, done):
    with db() as c:
        cur = c.execute(
            "UPDATE tasks SET is_done = ?, updated_at = ? WHERE id = ? AND is_one_off = 1 AND is_deleted = 0",
            (1 if done else 0, now_iso(), task_id),
        )
    return {"ok": cur.rowcount == 1}


@eel.expose
def set_completion(task_id, day, done):
    if day > date.today().isoformat():
        return {"ok": False, "error": "You can't tick off a future day."}
    with db() as c:
        t = c.execute(
            "SELECT created_at FROM tasks WHERE id = ? AND is_deleted = 0 AND is_one_off = 0", (task_id,)
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
        tasks = [r["created_at"] for r in c.execute(
            "SELECT created_at FROM tasks WHERE is_deleted = 0 AND is_one_off = 0")]
        done_by_day = {
            r["day"]: r["n"]
            for r in c.execute(
                """SELECT x.day, COUNT(*) AS n FROM completions x
                   JOIN tasks t ON t.id = x.task_id
                   WHERE x.is_deleted = 0 AND t.is_deleted = 0 AND t.is_one_off = 0 GROUP BY x.day"""
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
# Strength & ranks (ported from the mobile StrengthEngine)
# ======================================================================
RANK_WINDOW_DAYS = 30
MUSCLE_GROUPS = [("CHEST", "Chest"), ("BACK", "Back"), ("LEGS", "Legs"),
                 ("SHOULDERS", "Shoulders"), ("ARMS", "Arms"), ("CORE", "Core")]
GROUP_KEYS = {k for k, _ in MUSCLE_GROUPS}
# (label, colour, bench-press-scale 1RM in kg needed to reach it). Wood = anything below Bronze.
TIERS = [("Wood", "#8B5A2B", 0.0), ("Bronze", "#CD7F32", 38.5), ("Silver", "#C0C0C0", 54.4),
         ("Gold", "#FFD700", 65.8), ("Platinum", "#4ECCA3", 77.1), ("Diamond", "#9D94FF", 88.5),
         ("Champion", "#D980FA", 102.1), ("Titan", "#FF4D4D", 115.7), ("Olympian", "#48DBFB", 129.3)]
# (name, group, baseline multiplier vs bench press, uses body weight)
DEFAULT_EXERCISES = [
    ("Bench Press", "CHEST", 1.0, 0), ("Incline Bench Press", "CHEST", 0.85, 0),
    ("Deadlift", "BACK", 1.5, 0), ("Barbell Row", "BACK", 0.9, 0), ("Lat Pulldown", "BACK", 0.85, 0),
    ("Pull-Ups", "BACK", 1.0, 1),
    ("Squat", "LEGS", 1.35, 0), ("Leg Press", "LEGS", 2.2, 0), ("Romanian Deadlift", "LEGS", 1.2, 0),
    ("Overhead Press", "SHOULDERS", 0.7, 0), ("Lateral Raise", "SHOULDERS", 0.12, 0),
    ("Barbell Curl", "ARMS", 0.45, 0), ("Triceps Pushdown", "ARMS", 0.55, 0),
    ("Close-Grip Bench Press", "ARMS", 0.85, 0),
    ("Cable Crunch", "CORE", 0.6, 0), ("Weighted Sit-Up", "CORE", 0.35, 0),
]


def epley(weight_kg, reps):
    """Epley 1RM = w * (1 + reps/30). One rep is the weight itself; reps are capped at 30."""
    return weight_kg if reps <= 1 else weight_kg * (1 + min(reps, 30) / 30.0)


def _ms_ago(days):
    return int((time.time() - days * 86400) * 1000)


def _standing(normalized_kg):
    idx = max(i for i, t in enumerate(TIERS) if normalized_kg >= t[2])
    nxt = TIERS[idx + 1] if idx + 1 < len(TIERS) else None
    progress = 1.0 if nxt is None else min(1.0, max(0.0, (normalized_kg - TIERS[idx][2]) / (nxt[2] - TIERS[idx][2])))
    return {"tier": TIERS[idx][0], "level": idx + 1, "color": TIERS[idx][1],
            "progress": round(progress, 4), "next": nxt[0] if nxt else None, "normalized_kg": normalized_kg}


def _multipliers(c):
    return {r["name"]: r["baseline_multiplier"] for r in c.execute("SELECT name, baseline_multiplier FROM exercises")}


def _bodyweight_kg(c):
    r = c.execute("SELECT kg FROM weights WHERE is_deleted = 0 ORDER BY day DESC LIMIT 1").fetchone()
    return r["kg"] if r else None


def _group_bests(c, mult):
    """{group: {exercise, e1rm_kg, normalized_kg}} using each group's best exercise in the rolling window."""
    rows = c.execute(
        """SELECT exercise_name, primary_group, MAX(e1rm_kg) AS e1 FROM strength_sets
           WHERE is_deleted = 0 AND timestamp >= ? GROUP BY exercise_name, primary_group""",
        (_ms_ago(RANK_WINDOW_DAYS),),
    ).fetchall()
    best = {}
    for r in rows:
        norm = r["e1"] / (mult.get(r["exercise_name"]) or 1.0)
        cur = best.get(r["primary_group"])
        if cur is None or norm > cur["normalized_kg"]:
            best[r["primary_group"]] = {"exercise": r["exercise_name"], "e1rm_kg": r["e1"], "normalized_kg": norm}
    return best


@eel.expose
def get_exercises():
    with db() as c:
        rows = c.execute(
            "SELECT name, primary_group, uses_bodyweight, baseline_multiplier FROM exercises "
            "WHERE is_deleted = 0 ORDER BY id").fetchall()
    return [{"name": r["name"], "group": r["primary_group"], "uses_bodyweight": bool(r["uses_bodyweight"]),
             "multiplier": r["baseline_multiplier"]} for r in rows]


@eel.expose
def get_strength_ranks():
    """Best 1RM per muscle group over the last 30 days, resolved to a tier."""
    with db() as c:
        unit = _unit(c)
        bests = _group_bests(c, _multipliers(c))
        bw = _bodyweight_kg(c)
    groups = []
    for key, label in MUSCLE_GROUPS:
        b = bests.get(key)
        if b is None:
            groups.append({"group": key, "label": label, "ranked": False, "tier": "Unranked", "level": 0,
                           "color": "#8a98a1", "progress": 0, "next": TIERS[0][0], "normalized": 0,
                           "normalized_kg": 0, "best": None})
            continue
        s = _standing(b["normalized_kg"])
        groups.append({"group": key, "label": label, "ranked": True, "tier": s["tier"], "level": s["level"],
                       "color": s["color"], "progress": s["progress"], "next": s["next"],
                       "normalized": _out(b["normalized_kg"], unit), "normalized_kg": b["normalized_kg"],
                       "best": {"exercise": b["exercise"], "e1rm": _out(b["e1rm_kg"], unit)}})
    return {
        "unit": unit, "window_days": RANK_WINDOW_DAYS, "kg_per_lb": KG_PER_LB,
        "bodyweight": _out(bw, unit) if bw else None, "bodyweight_kg": bw,
        "groups": groups,
        "tiers": [{"label": t[0], "color": t[1], "level": i + 1, "base": _out(t[2], unit), "base_kg": t[2]}
                  for i, t in enumerate(TIERS)],
    }


def _public_standing(s, unit):
    return None if s is None else {"tier": s["tier"], "level": s["level"], "color": s["color"],
                                   "progress": s["progress"], "next": s["next"]}


@eel.expose
def log_strength_set(exercise_name, primary_group, weight_kg, reps, uses_bodyweight=False, sets=1):
    """Log `sets` identical sets. weight_kg is the weight lifted (added weight for bodyweight lifts)."""
    name = (exercise_name or "").strip()
    try:
        kg, reps, sets = float(weight_kg), int(reps), int(sets)
    except (TypeError, ValueError):
        return {"ok": False, "error": "Enter a valid weight, reps and sets."}
    if not name:
        return {"ok": False, "error": "Pick an exercise."}
    if not 1 <= sets <= 20:
        return {"ok": False, "error": "Sets must be between 1 and 20."}
    if not 1 <= reps <= 30:
        return {"ok": False, "error": "Reps must be between 1 and 30."}
    if not 0 <= kg <= 1000:
        return {"ok": False, "error": "That weight looks out of range."}

    with db() as c:
        ex = c.execute("SELECT * FROM exercises WHERE name = ? AND is_deleted = 0", (name,)).fetchone()
        if ex:  # the catalogue is the source of truth for group and body-weight use
            group, bodyweight_lift = ex["primary_group"], bool(ex["uses_bodyweight"])
        else:
            group, bodyweight_lift = str(primary_group or "").upper(), bool(uses_bodyweight)
        if group not in GROUP_KEYS:
            return {"ok": False, "error": "Unknown muscle group."}
        if not bodyweight_lift and kg <= 0:
            return {"ok": False, "error": "Enter a weight above 0."}
        effective = kg + ((_bodyweight_kg(c) or 0.0) if bodyweight_lift else 0.0)
        if effective <= 0:
            return {"ok": False, "error": "Log your body weight first, or add weight."}
        e1 = epley(effective, reps)

        mult = _multipliers(c)
        mult.setdefault(name, 1.0)
        before_best = _group_bests(c, mult).get(group)
        prev = c.execute(
            "SELECT MAX(e1rm_kg) AS m FROM strength_sets WHERE exercise_name = ? AND is_deleted = 0 AND timestamp >= ?",
            (name, _ms_ago(RANK_WINDOW_DAYS))).fetchone()["m"]
        now_ms, stamp = int(time.time() * 1000), now_iso()
        for i in range(sets):
            c.execute(
                """INSERT INTO strength_sets (id, exercise_name, primary_group, weight_kg, reps, e1rm_kg,
                                              timestamp, updated_at, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,0)""",
                (str(uuid.uuid4()), name, group, kg, reps, e1, now_ms + i, stamp))
        after_best = _group_bests(c, mult)[group]
        unit = _unit(c)

    before = _standing(before_best["normalized_kg"]) if before_best else None
    after = _standing(after_best["normalized_kg"])
    return {"ok": True, "exercise": name, "group": group,
            "label": dict(MUSCLE_GROUPS)[group], "sets": sets, "reps": reps, "e1rm": _out(e1, unit), "unit": unit,
            "before": _public_standing(before, unit), "after": _public_standing(after, unit),
            "promoted": before is not None and after["level"] > before["level"],
            "new_best": prev is None or e1 > prev}


@eel.expose
def delete_strength_set(set_id):
    with db() as c:
        c.execute("UPDATE strength_sets SET is_deleted = 1, updated_at = ? WHERE id = ?", (now_iso(), set_id))
    return {"ok": True}


@eel.expose
def get_recent_sets(limit=20):
    try:
        limit = max(1, min(int(limit), 200))
    except (TypeError, ValueError):
        limit = 20
    with db() as c:
        unit = _unit(c)
        rows = c.execute(
            "SELECT * FROM strength_sets WHERE is_deleted = 0 ORDER BY timestamp DESC, id DESC LIMIT ?",
            (limit,)).fetchall()
    return {"unit": unit, "sets": [
        {"id": r["id"], "exercise": r["exercise_name"], "group": r["primary_group"],
         "weight": _out(r["weight_kg"], unit), "reps": r["reps"], "e1rm": _out(r["e1rm_kg"], unit),
         "timestamp": r["timestamp"]} for r in rows]}


# ======================================================================
# Activities (GPS recordings from the phone) and 10-minute resets
# ======================================================================
def _parse_route(raw):
    """Decode route_json into [{lat, lng, alt}], dropping anything malformed."""
    try:
        data = json.loads(raw) if isinstance(raw, str) else raw
    except ValueError:
        return []
    out = []
    for p in data if isinstance(data, list) else []:
        try:
            if isinstance(p, dict):
                lat = p.get("lat", p.get("latitude"))
                lng = p.get("lng", p.get("lon", p.get("longitude")))
                alt = p.get("alt", p.get("altitude"))
            else:
                lat, lng, alt = p[0], p[1], (p[2] if len(p) > 2 else None)
            lat, lng = float(lat), float(lng)
        except (TypeError, ValueError, KeyError, IndexError):
            continue
        if -90 <= lat <= 90 and -180 <= lng <= 180:
            out.append({"lat": lat, "lng": lng, "alt": alt if isinstance(alt, (int, float)) else None})
    return out


def _thin(points, limit=300):
    if len(points) <= limit:
        return points
    step = (len(points) - 1) / (limit - 1)
    return [points[round(i * step)] for i in range(limit)]


@eel.expose
def get_activities(limit=200):
    try:
        limit = max(1, min(int(limit), 1000))
    except (TypeError, ValueError):
        limit = 200
    with db() as c:
        rows = c.execute("SELECT * FROM activities WHERE is_deleted = 0 ORDER BY start_time DESC LIMIT ?",
                         (limit,)).fetchall()
        tot = c.execute(
            """SELECT COUNT(*) AS n, COALESCE(SUM(distance_meters),0) AS d, COALESCE(SUM(duration_seconds),0) AS s,
                      COALESCE(SUM(steps),0) AS st FROM activities WHERE is_deleted = 0""").fetchone()
    items = []
    for r in rows:
        route = _parse_route(r["route_json"])
        km = r["distance_meters"] / 1000
        items.append({
            "id": r["id"], "type": r["type"], "start_time": r["start_time"], "end_time": r["end_time"],
            "distance_km": round(km, 3), "duration_seconds": r["duration_seconds"], "steps": r["steps"],
            "pace_sec_per_km": round(r["duration_seconds"] / km) if km >= 0.05 and r["duration_seconds"] else None,
            "route": _thin(route), "route_points": len(route)})
    return {"activities": items,
            "summary": {"count": tot["n"], "distance_km": round(tot["d"] / 1000, 2),
                        "duration_seconds": tot["s"], "steps": tot["st"]}}


@eel.expose
def delete_activity(activity_id):
    with db() as c:
        c.execute("UPDATE activities SET is_deleted = 1, updated_at = ? WHERE id = ?", (now_iso(), activity_id))
    return {"ok": True}


@eel.expose
def get_meditation_history(limit=100):
    try:
        limit = max(1, min(int(limit), 1000))
    except (TypeError, ValueError):
        limit = 100
    with db() as c:
        rows = c.execute("SELECT * FROM meditation_sessions WHERE is_deleted = 0 ORDER BY timestamp DESC LIMIT ?",
                         (limit,)).fetchall()
        tot = c.execute(
            """SELECT COUNT(*) AS n, COALESCE(SUM(duration_seconds),0) AS s
               FROM meditation_sessions WHERE is_deleted = 0""").fetchone()
    return {"sessions": [{"id": r["id"], "timestamp": r["timestamp"], "duration_seconds": r["duration_seconds"],
                          "sound_pattern": r["sound_pattern"]} for r in rows],
            "summary": {"count": tot["n"], "duration_seconds": tot["s"]}}


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
             "created_at": r["created_at"], "updated_at": r["updated_at"], "is_deleted": r["is_deleted"],
             "is_one_off": r["is_one_off"], "is_done": r["is_done"]}
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
        strength_sets = [
            {"id": r["id"], "exercise_name": r["exercise_name"], "primary_group": r["primary_group"],
             "weight_kg": r["weight_kg"], "reps": r["reps"], "e1rm_kg": r["e1rm_kg"], "timestamp": r["timestamp"],
             "updated_at": r["updated_at"], "is_deleted": r["is_deleted"]}
            for r in c.execute("SELECT * FROM strength_sets WHERE updated_at >= ?", (since,))
        ]
        activities = [
            {"id": r["id"], "type": r["type"], "start_time": r["start_time"], "end_time": r["end_time"],
             "distance_meters": r["distance_meters"], "duration_seconds": r["duration_seconds"],
             "steps": r["steps"], "route_json": r["route_json"],
             "updated_at": r["updated_at"], "is_deleted": r["is_deleted"]}
            for r in c.execute("SELECT * FROM activities WHERE updated_at >= ?", (since,))
        ]
        meditation_sessions = [
            {"id": r["id"], "timestamp": r["timestamp"], "duration_seconds": r["duration_seconds"],
             "sound_pattern": r["sound_pattern"], "updated_at": r["updated_at"], "is_deleted": r["is_deleted"]}
            for r in c.execute("SELECT * FROM meditation_sessions WHERE updated_at >= ?", (since,))
        ]
    return {"ok": True, "server_time": now_iso(),
            "tasks": tasks, "completions": completions, "weights": weights, "settings": settings,
            "strength_sets": strength_sets, "activities": activities, "meditation_sessions": meditation_sessions}


def _apply_push(p):
    """Merge mobile records into the local DB. Last-write-wins on updated_at.
    Returns (synced_at, skipped): malformed strength/activity/meditation records are skipped, not fatal,
    so one bad row can never block a phone's sync forever."""
    skipped = 0
    with db() as c:
        for r in p.get("tasks", []):
            row = c.execute("SELECT updated_at FROM tasks WHERE uid = ?", (r["uid"],)).fetchone()
            vals = (r["title"], r.get("description", ""), r["created_at"], int(bool(r.get("is_deleted"))), r["updated_at"],
                    int(bool(r.get("is_one_off"))), int(bool(r.get("is_done"))))
            if row is None:
                c.execute(
                    "INSERT INTO tasks (title, description, created_at, is_deleted, updated_at, is_one_off, is_done, uid) VALUES (?,?,?,?,?,?,?,?)",
                    vals + (r["uid"],),
                )
            elif r["updated_at"] > row["updated_at"]:
                c.execute(
                    "UPDATE tasks SET title=?, description=?, created_at=?, is_deleted=?, updated_at=?, is_one_off=?, is_done=? WHERE uid=?",
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
        # ---- v5 entities: UUID-keyed, last-write-wins on updated_at ----
        for r in p.get("strength_sets") or []:
            try:
                sid, name = str(r["id"]).strip(), str(r["exercise_name"]).strip()
                group, upd = str(r["primary_group"]).upper(), str(r["updated_at"])
                kg, e1 = float(r["weight_kg"]), float(r["e1rm_kg"])
                reps, ts = int(r["reps"]), int(r["timestamp"])
                if not (sid and name and group in GROUP_KEYS and ISO_RE.match(upd)
                        and 0 <= kg <= 1000 and 0 < e1 <= 5000 and 1 <= reps <= 30 and ts > 0):
                    raise ValueError("out of range")
            except (KeyError, TypeError, ValueError):
                skipped += 1
                continue
            c.execute(
                """INSERT INTO strength_sets (id, exercise_name, primary_group, weight_kg, reps, e1rm_kg,
                                              timestamp, updated_at, is_deleted) VALUES (?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(id) DO UPDATE SET
                     exercise_name = excluded.exercise_name, primary_group = excluded.primary_group,
                     weight_kg = excluded.weight_kg, reps = excluded.reps, e1rm_kg = excluded.e1rm_kg,
                     timestamp = excluded.timestamp, updated_at = excluded.updated_at,
                     is_deleted = excluded.is_deleted
                   WHERE excluded.updated_at > strength_sets.updated_at""",
                (sid, name, group, kg, reps, e1, ts, upd, int(bool(r.get("is_deleted")))))
        for r in p.get("activities") or []:
            try:
                aid, upd = str(r["id"]).strip(), str(r["updated_at"])
                start, end = int(r["start_time"]), int(r.get("end_time") or r["start_time"])
                dist, dur, steps = float(r.get("distance_meters") or 0), int(r.get("duration_seconds") or 0), int(r.get("steps") or 0)
                route = r.get("route_json") or "[]"
                if not isinstance(route, str):
                    route = json.dumps(route)
                if not (aid and ISO_RE.match(upd) and start > 0 and dist >= 0 and dur >= 0 and steps >= 0):
                    raise ValueError("out of range")
            except (KeyError, TypeError, ValueError):
                skipped += 1
                continue
            c.execute(
                """INSERT INTO activities (id, type, start_time, end_time, distance_meters, duration_seconds,
                                           steps, route_json, updated_at, is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(id) DO UPDATE SET
                     type = excluded.type, start_time = excluded.start_time, end_time = excluded.end_time,
                     distance_meters = excluded.distance_meters, duration_seconds = excluded.duration_seconds,
                     steps = excluded.steps, route_json = excluded.route_json,
                     updated_at = excluded.updated_at, is_deleted = excluded.is_deleted
                   WHERE excluded.updated_at > activities.updated_at""",
                (aid, str(r.get("type") or "Workout"), start, end, dist, dur, steps, route, upd,
                 int(bool(r.get("is_deleted")))))
        for r in p.get("meditation_sessions") or []:
            try:
                mid, upd = str(r["id"]).strip(), str(r["updated_at"])
                ts, dur = int(r["timestamp"]), int(r["duration_seconds"])
                if not (mid and ISO_RE.match(upd) and ts > 0 and dur >= 0):
                    raise ValueError("out of range")
            except (KeyError, TypeError, ValueError):
                skipped += 1
                continue
            c.execute(
                """INSERT INTO meditation_sessions (id, timestamp, duration_seconds, sound_pattern, updated_at, is_deleted)
                   VALUES (?,?,?,?,?,?)
                   ON CONFLICT(id) DO UPDATE SET
                     timestamp = excluded.timestamp, duration_seconds = excluded.duration_seconds,
                     sound_pattern = excluded.sound_pattern, updated_at = excluded.updated_at,
                     is_deleted = excluded.is_deleted
                   WHERE excluded.updated_at > meditation_sessions.updated_at""",
                (mid, ts, dur, str(r.get("sound_pattern") or ""), upd, int(bool(r.get("is_deleted")))))
        synced_at = now_iso()
        meta_set(c, "last_synced_at", synced_at)
    return synced_at, skipped


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
                synced_at, skipped = _apply_push(body)
                _mark_synced(self.client_address[0])
                return self._send(200, {"ok": True, "synced_at": synced_at, "skipped": skipped})
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
