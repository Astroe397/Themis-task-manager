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

/* ---------- one-off tasks ---------- */
function oneOffRow(t) {
  const li = document.createElement("li");
  li.className = "task" + (t.done ? " done" : "");
  li.innerHTML = `
    <input type="checkbox" ${t.done ? "checked" : ""} aria-label="Mark ${esc(t.title)} as ${t.done ? "not done" : "done"}">
    <div>
      <div class="name">${esc(t.title)}</div>
      ${t.description ? `<div class="desc">${esc(t.description)}</div>` : ""}
    </div>
    <button type="button" class="del">Delete</button>`;
  li.querySelector("input").addEventListener("change", async (e) => {
    await eel.set_one_off_done(t.id, e.target.checked)();
    await renderOneOffs();
  });
  li.querySelector(".del").addEventListener("click", async () => {
    if (!confirm(`Delete "${t.title}"?`)) return;
    await eel.delete_task(t.id)();
    await renderOneOffs();
  });
  return li;
}

async function renderOneOffs() {
  const all = await eel.get_one_offs()();
  const open = all.filter((t) => !t.done);
  const done = all.filter((t) => t.done);
  const openList = $("oneoff-open");
  const doneList = $("oneoff-done");
  openList.innerHTML = "";
  doneList.innerHTML = "";
  open.forEach((t) => openList.appendChild(oneOffRow(t)));
  done.forEach((t) => doneList.appendChild(oneOffRow(t)));
  $("oneoff-empty").hidden = open.length > 0;
  $("oneoff-done-wrap").hidden = done.length === 0;
  $("oneoff-done-count").textContent = done.length;
}

$("oneoff-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const err = $("o-error");
  err.hidden = true;
  const res = await eel.add_task($("o-title").value, $("o-desc").value, true)();
  if (!res.ok) { err.textContent = res.error; err.hidden = false; return; }
  $("o-title").value = "";
  $("o-desc").value = "";
  $("o-title").focus();
  await renderOneOffs();
});

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

/* ---------- tabs ---------- */
const TABS = ["habits", "strength", "activity"];

function showTab(name) {
  TABS.forEach((t) => {
    const on = t === name;
    $(`tab-${t}`).setAttribute("aria-selected", String(on));
    $(`tab-${t}`).tabIndex = on ? 0 : -1;
    $(`panel-${t}`).hidden = !on;
  });
}

TABS.forEach((t, i) => {
  $(`tab-${t}`).addEventListener("click", () => showTab(t));
  $(`tab-${t}`).addEventListener("keydown", (e) => {
    const dir = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
    if (!dir) return;
    const next = TABS[(i + dir + TABS.length) % TABS.length];
    showTab(next);
    $(`tab-${next}`).focus();
  });
});

/* ---------- shared formatting ---------- */
const fmtWhen = (ms) =>
  new Date(ms).toLocaleString(undefined, { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

function fmtDuration(sec) {
  sec = Math.max(0, Math.round(sec));
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  if (h) return `${h}h ${String(m).padStart(2, "0")}m`;
  return `${m}m ${String(s).padStart(2, "0")}s`;
}

const fmtPace = (secPerKm) =>
  secPerKm ? `${Math.floor(secPerKm / 60)}:${String(secPerKm % 60).padStart(2, "0")} /km` : "–";

/* ---------- strength & ranks ---------- */
Object.assign(state, { group: "CHEST", ranks: null, exercises: [] });

function renderGroups() {
  const box = $("s-groups");
  box.innerHTML = "";
  state.ranks.groups.forEach((g) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "group" + (g.group === state.group ? " selected" : "");
    b.setAttribute("aria-pressed", String(g.group === state.group));
    const pct = Math.round(g.progress * 100);
    const toNext = !g.ranked ? "No lifts in the last 30 days" : g.next ? `${pct}% to ${g.next}` : "Top tier reached";
    b.innerHTML = `
      <span class="g-name">${esc(g.label)}</span>
      <span class="badge"><span class="b-dot" style="background:${g.color}"></span>${esc(g.tier)}</span>
      <span class="bar" aria-hidden="true"><span style="width:${g.ranked ? pct : 0}%;background:${g.color}"></span></span>
      <span class="g-sub">${esc(toNext)}</span>
      <span class="g-sub">${g.best ? `${esc(g.best.exercise)} &middot; ${trim(g.best.e1rm)} ${state.ranks.unit}` : "&nbsp;"}</span>`;
    b.addEventListener("click", () => { state.group = g.group; renderGroups(); renderLadder(); });
    box.appendChild(b);
  });
}

function renderLadder() {
  const r = state.ranks;
  const g = r.groups.find((x) => x.group === state.group);
  $("s-ladder-title").textContent = `${g.label} ladder`;
  $("s-ladder-sub").textContent = g.ranked ? `Current 1RM score: ${trim(g.normalized)} ${r.unit}` : "Unranked";
  const list = $("s-ladder");
  list.innerHTML = "";
  [...r.tiers].reverse().forEach((t) => {
    const next = r.tiers[t.level]; // tiers are 1-based levels, so index t.level is the tier above
    let fill = 0;
    if (g.normalized_kg >= t.base_kg) {
      fill = next ? Math.min(1, (g.normalized_kg - t.base_kg) / (next.base_kg - t.base_kg)) : 1;
      if (next && g.normalized_kg >= next.base_kg) fill = 1;
    }
    const current = g.ranked && t.label === g.tier;
    const li = document.createElement("li");
    li.className = "rung" + (current ? " current" : "");
    if (current) li.setAttribute("aria-current", "true");
    li.innerHTML = `
      <span class="r-name"><span class="b-dot" style="background:${t.color}"></span>${esc(t.label)}${current ? ' <span class="you">you are here</span>' : ""}</span>
      <span class="bar" aria-hidden="true"><span style="width:${Math.round(fill * 100)}%;background:${t.color}"></span></span>
      <span class="r-base">${t.base ? `${trim(t.base)} ${r.unit}` : "start"}</span>`;
    list.appendChild(li);
  });
}

async function renderRecentSets() {
  const data = await eel.get_recent_sets(20)();
  const body = $("s-recent").querySelector("tbody");
  body.innerHTML = "";
  $("s-recent-empty").hidden = data.sets.length > 0;
  $("s-recent").hidden = data.sets.length === 0;
  data.sets.forEach((s) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${esc(fmtWhen(s.timestamp))}</td><td>${esc(s.exercise)}</td>
      <td class="num">${trim(s.weight)} ${data.unit}</td><td class="num">${s.reps}</td>
      <td class="num">${trim(s.e1rm)} ${data.unit}</td>
      <td><button type="button" class="del">Delete</button></td>`;
    tr.querySelector(".del").addEventListener("click", async () => {
      if (!confirm(`Delete this ${s.exercise} set?`)) return;
      await eel.delete_strength_set(s.id)();
      await refreshStrength();
    });
    body.appendChild(tr);
  });
}

async function refreshStrength() {
  state.ranks = await eel.get_strength_ranks()();
  state.exercises = await eel.get_exercises()();
  $("s-window").textContent = state.ranks.window_days;
  renderGroups();
  renderLadder();
  await renderRecentSets();
  if ($("s-dialog").open) updatePreview();
}

/* log-a-lift dialog with live 1RM preview */
const currentEx = () => state.exercises.find((e) => e.name === $("s-ex").value);

function fillExerciseSelect(preferGroup) {
  const sel = $("s-ex");
  const keep = sel.value;
  sel.innerHTML = "";
  state.ranks.groups.forEach((g) => {
    const list = state.exercises.filter((e) => e.group === g.group);
    if (!list.length) return;
    const og = document.createElement("optgroup");
    og.label = g.label;
    list.forEach((e) => { const o = document.createElement("option"); o.value = e.name; o.textContent = e.name; og.appendChild(o); });
    sel.appendChild(og);
  });
  const first = state.exercises.find((e) => e.group === preferGroup);
  sel.value = keep && state.exercises.some((e) => e.name === keep) ? keep : first ? first.name : "";
}

function updatePreview() {
  const ex = currentEx();
  if (!ex || !state.ranks) return;
  const r = state.ranks, u = r.unit;
  $("s-w-label").textContent = `${ex.uses_bodyweight ? "Added weight" : "Weight per rep"} (${u})`;
  const note = $("s-bw-note");
  note.hidden = !ex.uses_bodyweight;
  note.textContent = r.bodyweight_kg
    ? `Your latest body weight (${trim(r.bodyweight)} ${u}) is added to this lift automatically. Enter 0 for none.`
    : "Log your body weight in the Habits tab first, or enter added weight.";

  const w = parseFloat($("s-w").value), reps = parseInt($("s-reps").value, 10);
  const out = $("s-preview");
  if (!(w >= 0) || !(reps >= 1 && reps <= 30)) { out.textContent = "Enter a weight and reps to preview your 1RM."; return; }
  const kg = w * (u === "lb" ? r.kg_per_lb : 1);
  const eff = kg + (ex.uses_bodyweight ? r.bodyweight_kg || 0 : 0);
  if (eff <= 0) { out.textContent = "Enter a weight above 0."; return; }
  const e1 = reps <= 1 ? eff : eff * (1 + reps / 30);   // Epley
  const norm = e1 / ex.multiplier;
  const tier = [...r.tiers].reverse().find((t) => norm >= t.base_kg);
  const grp = r.groups.find((g) => g.group === ex.group);
  const e1Out = u === "lb" ? e1 / r.kg_per_lb : e1;
  let verdict;
  if (!grp.ranked) verdict = `Would start ${grp.label} at ${tier.label}.`;
  else if (norm > grp.normalized_kg && tier.level > grp.level) verdict = `Would promote ${grp.label}: ${grp.tier} \u2192 ${tier.label}.`;
  else verdict = `${grp.label} stays ${grp.tier}.`;
  out.textContent = `Estimated 1RM: ${trim(e1Out)} ${u} (${tier.label}-level lift). ${verdict}`;
}

$("s-open").addEventListener("click", () => {
  $("s-error").hidden = true;
  fillExerciseSelect(state.group);
  $("s-w").value = "";
  updatePreview();
  $("s-dialog").showModal();
  $("s-w").focus();
});
$("s-cancel").addEventListener("click", () => $("s-dialog").close());
["s-ex", "s-w", "s-reps", "s-sets"].forEach((id) => $(id).addEventListener("input", updatePreview));

$("s-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const ex = currentEx();
  const err = $("s-error");
  err.hidden = true;
  const u = state.ranks.unit;
  const kg = parseFloat($("s-w").value) * (u === "lb" ? state.ranks.kg_per_lb : 1);
  const res = await eel.log_strength_set(ex.name, ex.group, kg, $("s-reps").value, ex.uses_bodyweight, $("s-sets").value)();
  if (!res.ok) { err.textContent = res.error; err.hidden = false; return; }
  $("s-dialog").close();
  state.group = res.group;
  const banner = $("s-result");
  const best = res.new_best ? " New best for this lift." : "";
  banner.textContent = res.promoted
    ? `Promoted! ${res.label}: ${res.before.tier} \u2192 ${res.after.tier}. ${res.exercise}, est. 1RM ${trim(res.e1rm)} ${res.unit}.`
    : !res.before
      ? `${res.label} ranked ${res.after.tier}. ${res.exercise}, est. 1RM ${trim(res.e1rm)} ${res.unit}.`
      : `Logged ${res.sets} \u00d7 ${res.reps} ${res.exercise} (est. 1RM ${trim(res.e1rm)} ${res.unit}). ${res.label} stays ${res.after.tier}.${best}`;
  banner.hidden = false;
  await refreshStrength();
});

/* ---------- activities ---------- */
function routeSvg(route) {
  if (!route || route.length < 2) return "<span class=\"muted\">–</span>";
  const W = 120, H = 56, P = 5;
  const midLat = route.reduce((a, p) => a + p.lat, 0) / route.length;
  const k = Math.cos((midLat * Math.PI) / 180);       // keep east-west distances true to north-south
  const xs = route.map((p) => p.lng * k), ys = route.map((p) => -p.lat);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const scale = Math.min((W - 2 * P) / (maxX - minX || 1e-9), (H - 2 * P) / (maxY - minY || 1e-9));
  const ox = (W - (maxX - minX) * scale) / 2, oy = (H - (maxY - minY) * scale) / 2;
  const pts = xs.map((x, i) => `${(ox + (x - minX) * scale).toFixed(1)},${(oy + (ys[i] - minY) * scale).toFixed(1)}`);
  const [sx, sy] = pts[0].split(","), [ex, ey] = pts[pts.length - 1].split(",");
  return `<svg class="route" viewBox="0 0 ${W} ${H}" role="img" aria-label="Route preview">
    <polyline points="${pts.join(" ")}"/><circle class="start" cx="${sx}" cy="${sy}" r="3"/><circle class="end" cx="${ex}" cy="${ey}" r="3"/></svg>`;
}

async function refreshActivities() {
  const data = await eel.get_activities()();
  const s = data.summary;
  $("a-count").textContent = s.count;
  $("a-dist").textContent = `${s.distance_km.toFixed(2)} km`;
  $("a-time").textContent = fmtDuration(s.duration_seconds);
  $("a-steps").textContent = s.steps.toLocaleString();
  const body = $("a-table").querySelector("tbody");
  body.innerHTML = "";
  $("a-empty").hidden = data.activities.length > 0;
  $("a-table").hidden = data.activities.length === 0;
  data.activities.forEach((a) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${esc(fmtWhen(a.start_time))}</td><td>${esc(a.type)}</td>
      <td class="num">${a.distance_km.toFixed(2)} km</td><td class="num">${fmtDuration(a.duration_seconds)}</td>
      <td class="num">${fmtPace(a.pace_sec_per_km)}</td><td class="num">${a.steps ? a.steps.toLocaleString() : "–"}</td>
      <td>${routeSvg(a.route)}</td>
      <td><button type="button" class="del">Delete</button></td>`;
    tr.querySelector(".del").addEventListener("click", async () => {
      if (!confirm(`Delete this ${a.type.toLowerCase()} from ${fmtWhen(a.start_time)}?`)) return;
      await eel.delete_activity(a.id)();
      await refreshActivities();
    });
    body.appendChild(tr);
  });
}

async function refreshMeditation() {
  const data = await eel.get_meditation_history()();
  const n = data.summary.count;
  $("m-sub").textContent = `${n} session${n === 1 ? "" : "s"} \u00b7 ${fmtDuration(data.summary.duration_seconds)} total`;
  const body = $("m-table").querySelector("tbody");
  body.innerHTML = "";
  $("m-empty").hidden = data.sessions.length > 0;
  $("m-table").hidden = data.sessions.length === 0;
  data.sessions.forEach((m) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${esc(fmtWhen(m.timestamp))}</td><td class="num">${fmtDuration(m.duration_seconds)}</td><td>${esc(m.sound_pattern || "–")}</td>`;
    body.appendChild(tr);
  });
}

async function refreshMobileData() {
  await Promise.all([refreshStrength(), refreshActivities(), refreshMeditation()]);
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
      await renderOneOffs();
      await refreshMobileData();   // strength, activities, resets
    }
    lastSyncSeen = s.last_synced_at;
  } catch (e) { /* backend momentarily unavailable */ }
}

refreshAll();
refreshWeight();
renderOneOffs();
refreshMobileData();
loadQuote();
pollSync();
setInterval(pollSync, 5000);
