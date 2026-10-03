"""Progress tracker: Eel + SQLite.  Run with:  python main.py"""
import json
import os
import sqlite3
import urllib.request
from contextlib import contextmanager
from datetime import date, timedelta

import eel

BASE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE, "progress.db")


@contextmanager
def db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db():
    with db() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                title       TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                created_at  TEXT NOT NULL            -- ISO date, first day it counts
            );
            CREATE TABLE IF NOT EXISTS completions (
                task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                day     TEXT NOT NULL,               -- ISO date
                PRIMARY KEY (task_id, day)
            );
            """
        )


# ---------- tasks ----------
@eel.expose
def add_task(title, description=""):
    title = (title or "").strip()
    if not title:
        return {"ok": False, "error": "A task needs a title."}
    with db() as c:
        c.execute(
            "INSERT INTO tasks (title, description, created_at) VALUES (?, ?, ?)",
            (title, (description or "").strip(), date.today().isoformat()),
        )
    return {"ok": True}


@eel.expose
def delete_task(task_id):
    with db() as c:
        c.execute("DELETE FROM tasks WHERE id = ?", (task_id,))
    return {"ok": True}


@eel.expose
def get_day(day):
    """Tasks that applied on `day`, each flagged done / not done."""
    with db() as c:
        rows = c.execute(
            """
            SELECT t.id, t.title, t.description,
                   EXISTS(SELECT 1 FROM completions x
                          WHERE x.task_id = t.id AND x.day = ?) AS done
            FROM tasks t
            WHERE t.created_at <= ?
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
        t = c.execute("SELECT created_at FROM tasks WHERE id = ?", (task_id,)).fetchone()
        if not t or day < t["created_at"]:
            return {"ok": False, "error": "This task didn't exist on that day."}
        if done:
            c.execute("INSERT OR IGNORE INTO completions (task_id, day) VALUES (?, ?)", (task_id, day))
        else:
            c.execute("DELETE FROM completions WHERE task_id = ? AND day = ?", (task_id, day))
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
        # ZenQuotes returns a placeholder "Too many requests" quote when rate limited.
        if not text or author.lower() == "zenquotes.io":
            return None
        return {"text": text, "author": author}
    except Exception:
        return None


# ---------- stats ----------
@eel.expose
def get_stats(weeks=26):
    today = date.today()
    with db() as c:
        tasks = [r["created_at"] for r in c.execute("SELECT created_at FROM tasks")]
        done_by_day = {
            r["day"]: r["n"]
            for r in c.execute("SELECT day, COUNT(*) AS n FROM completions GROUP BY day")
        }

    def totals(day_iso):
        return sum(1 for created in tasks if created <= day_iso)

    # Heatmap window: whole weeks, Sunday first (like GitHub), ending this week.
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

    # Overall average across every tracked day since the first task was added.
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

    # Streak of fully completed days. Today doesn't break it while still in progress.
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


if __name__ == "__main__":
    init_db()
    eel.init(os.path.join(BASE, "web"))
    eel.start("index.html", size=(1120, 860))
