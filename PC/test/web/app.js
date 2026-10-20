const $ = (id) => document.getElementById(id);
const state = { selected: null, today: null, weights: {}, unit: "kg" };

const esc = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const parse = (iso) => { const [y, m, d] = iso.split("-").map(Number); return new Date(y, m - 1, d); };
const fmt = (iso, opts) => parse(iso).toLocaleDateString(undefined, opts);

function levelClass(day) {
  if (day.future) return "future";
  if (!day.total) return "empty";
  if (day.pct === 0) return "missed";
  if (day.pct < 34) return "l1";
  if (day.pct < 67) return "l2";
  if (day.pct < 100) return "l3";
  return "l4";
}

function renderHeatmap(days) {
  const heat = $("heat");
  const months = $("months");
  heat.innerHTML = "";
  months.innerHTML = "";

  days.forEach((day) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = `cell ${levelClass(day)}`;
    if (day.date === state.today) b.classList.add("today");
    if (day.date === state.selected) b.classList.add("selected");
    const label = fmt(day.date, { weekday: "short", day: "numeric", month: "short", year: "numeric" });
    const detail = day.total ? `${day.done} of ${day.total} done` : "no tasks";
    b.title = `${label}: ${detail}`;
    b.setAttribute("aria-label", `${label}, ${detail}`);
    b.disabled = day.future;
    b.addEventListener("click", () => selectDay(day.date));
    heat.appendChild(b);
  });

  // month labels: one per week column where the month changes
  const weeks = days.length / 7;
  let lastMonth = -1;
  for (let w = 0; w < weeks; w++) {
    const first = parse(days[w * 7].date);
    const span = document.createElement("span");
    if (first.getMonth() !== lastMonth) {
      span.textContent = first.toLocaleDateString(undefined, { month: "short" });
      lastMonth = first.getMonth();
    }
    months.appendChild(span);
  }
}

function renderPie(stats) {
  const done = stats.avg_done;
  $("pie-fg").setAttribute("stroke-dasharray", `${done} ${100 - done}`);
  $("pie-label").textContent = stats.has_data ? `${Math.round(done)}%` : "–";
  $("pie-done").textContent = stats.has_data ? `${done}%` : "–";
  $("pie-not").textContent = stats.has_data ? `${stats.avg_not_done}%` : "–";
}

async function refreshStats() {
  const stats = await eel.get_stats(26)();
  state.today = stats.today;
  if (!state.selected) state.selected = stats.today;
  renderHeatmap(stats.days);
  renderPie(stats);
  $("stat-streak").textContent = `${stats.streak} day${stats.streak === 1 ? "" : "s"}`;
  $("stat-perfect").textContent = stats.perfect_days;
  $("stat-tracked").textContent = stats.tracked_days;
}

async function renderDay() {
  const day = state.selected;
  const isToday = day === state.today;
  $("day-title").textContent = isToday ? "Today" : fmt(day, { weekday: "long" });
  $("day-sub").textContent = fmt(day, { weekday: "long", day: "numeric", month: "long", year: "numeric" });
  $("btn-today").hidden = isToday;

  const tasks = await eel.get_day(day)();
  const list = $("tasks");
  list.innerHTML = "";
  $("empty").hidden = tasks.length > 0;

  tasks.forEach((t) => {
    const li = document.createElement("li");
    li.className = "task" + (t.done ? " done" : "");
    li.innerHTML = `
      <input type="checkbox" ${t.done ? "checked" : ""} aria-label="Mark ${esc(t.title)} as done">
      <div>
        <div class="name">${esc(t.title)}</div>
        ${t.description ? `<div class="desc">${esc(t.description)}</div>` : ""}
      </div>
      <button type="button" class="del">Delete</button>`;

    li.querySelector("input").addEventListener("change", async (e) => {
      const res = await eel.set_completion(t.id, day, e.target.checked)();
      if (!res.ok) alert(res.error);
      await refreshAll();
    });
    li.querySelector(".del").addEventListener("click", async () => {
      if (!confirm(`Delete "${t.title}" and its history?`)) return;
      await eel.delete_task(t.id)();
      await refreshAll();
    });
    list.appendChild(li);
  });
}

/* ---------- weight ---------- */
const SVGNS = "http://www.w3.org/2000/svg";
const trim = (n) => String(Math.round(n * 10) / 10);

function renderWeightChart(data) {
  const svg = $("w-chart");
  const W = 720, H = 260, L = 46, R = 16, T = 16, B = 30;
  const pts = data.points;
  let out = "";

  if (!pts.length) {
    svg.innerHTML = `<text class="none" x="${W / 2}" y="${H / 2}" text-anchor="middle">No weight entries in the past 6 months yet.</text>`;
    return;
  }

  const t0 = parse(data.start).getTime();
  const t1 = parse(data.end).getTime();
  const x = (iso) => L + ((parse(iso).getTime() - t0) / (t1 - t0 || 1)) * (W - L - R);

  let lo = Math.min(...pts.map((p) => p.value));
  let hi = Math.max(...pts.map((p) => p.value));
  if (hi - lo < 2) { lo -= 1; hi += 1; }
  const pad = (hi - lo) * 0.12;
  lo = Math.floor(lo - pad);
  hi = Math.ceil(hi + pad);
  const y = (v) => T + (1 - (v - lo) / (hi - lo)) * (H - T - B);

  // horizontal gridlines + value labels
  for (let i = 0; i <= 4; i++) {
    const v = lo + ((hi - lo) * i) / 4;
    out += `<line class="grid" x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}"/>`;
    out += `<text x="${L - 8}" y="${y(v) + 4}" text-anchor="end">${trim(v)}</text>`;
  }

  // month labels along the bottom
  const start = parse(data.start);
  let m = new Date(start.getFullYear(), start.getMonth() + 1, 1);
  while (m.getTime() <= t1) {
    const iso = `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, "0")}-01`;
    out += `<text x="${x(iso)}" y="${H - 8}" text-anchor="middle">${m.toLocaleDateString(undefined, { month: "short" })}</text>`;
    m = new Date(m.getFullYear(), m.getMonth() + 1, 1);
  }

  const path = pts.map((p, i) => `${i ? "L" : "M"}${x(p.day).toFixed(1)},${y(p.value).toFixed(1)}`).join(" ");
  out += `<path class="line" d="${path}"/>`;
  const showDots = pts.length <= 60;
  pts.forEach((p) => {
    const label = `${fmt(p.day, { day: "numeric", month: "short", year: "numeric" })}: ${trim(p.value)} ${data.unit}`;
    out += `<circle class="pt" cx="${x(p.day).toFixed(1)}" cy="${y(p.value).toFixed(1)}" r="${showDots ? 4 : 2.5}"><title>${label}</title></circle>`;
  });
  svg.innerHTML = out;
}

function syncWeightForm() {
  const day = $("w-date").value;
  const existing = state.weights[day];
  $("w-remove").hidden = existing === undefined;
  if (existing !== undefined) $("w-val").value = existing;
}

async function refreshWeight() {
  const data = await eel.get_weights()();
  state.unit = data.unit;
  state.weights = Object.fromEntries(data.points.map((p) => [p.day, p.value]));
  $("w-unit").value = data.unit;
  $("w-date").max = data.today;
  if (!$("w-date").value) $("w-date").value = data.today;

  const u = data.unit;
  $("w-latest").textContent = data.latest ? `${trim(data.latest.value)} ${u}` : "–";
  $("w-change").textContent = data.change === null ? "–" : `${data.change > 0 ? "+" : ""}${trim(data.change)} ${u}`;
  $("w-low").textContent = data.lowest === null ? "–" : `${trim(data.lowest)} ${u}`;
  $("w-high").textContent = data.highest === null ? "–" : `${trim(data.highest)} ${u}`;

  renderWeightChart(data);
  syncWeightForm();
}

$("w-date").addEventListener("change", () => {
  $("w-val").value = "";
  syncWeightForm();
});

$("w-unit").addEventListener("change", async (e) => {
  await eel.set_unit(e.target.value)();
  $("w-val").value = "";
  await refreshWeight();
});

$("w-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const err = $("w-error");
  err.hidden = true;
  const res = await eel.set_weight($("w-date").value, $("w-val").value, $("w-unit").value)();
  if (!res.ok) { err.textContent = res.error; err.hidden = false; return; }
  await refreshWeight();
});

$("w-remove").addEventListener("click", async () => {
  const day = $("w-date").value;
  if (!confirm(`Remove the weight entry for ${fmt(day, { day: "numeric", month: "short", year: "numeric" })}?`)) return;
  await eel.delete_weight(day)();
  $("w-val").value = "";
  await refreshWeight();
});

/* ---------- wiring ---------- */
async function refreshAll() {
  await refreshStats();
  await renderDay();
}

async function selectDay(iso) {
  state.selected = iso;
  await refreshAll();
}

$("btn-today").addEventListener("click", () => selectDay(state.today));

$("add-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const err = $("form-error");
  err.hidden = true;
  const res = await eel.add_task($("t-title").value, $("t-desc").value)();
  if (!res.ok) { err.textContent = res.error; err.hidden = false; return; }
  $("t-title").value = "";
  $("t-desc").value = "";
  $("t-title").focus();
  await refreshAll();
});

async function loadQuote() {
  const q = await eel.get_quote()();
  if (!q) return; // offline or rate-limited: leave the quote area hidden
  $("quote-text").textContent = q.text;
  $("quote-author").textContent = q.author;
  $("quote").hidden = false;
}

/* ---------- LAN sync status ---------- */
const SYNC_LABELS = { synced: "Connected & synced", searching: "Searching\u2026", offline: "Offline / disabled" };
let lastSyncSeen;

async function pollSync() {
  try {
    const s = await eel.get_sync_status()();
    $("sync").dataset.state = s.state;
    $("sync-label").textContent = SYNC_LABELS[s.state];
    $("sync").title = s.last_synced_at ? `Last sync: ${s.last_synced_at}${s.peer ? " from " + s.peer : ""}` : "No sync yet";
    // New data may have arrived from the phone: reload the views.
    if (lastSyncSeen !== undefined && s.last_synced_at !== lastSyncSeen) {
      await refreshAll();
      await refreshWeight();
    }
    lastSyncSeen = s.last_synced_at;
  } catch (e) { /* backend momentarily unavailable */ }
}

refreshAll();
refreshWeight();
loadQuote();
pollSync();
setInterval(pollSync, 5000);
