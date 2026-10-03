const $ = (id) => document.getElementById(id);
const state = { selected: null, today: null };

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
      await async function loadQuote() {
  const q = await eel.get_quote()();
  if (!q) return; // offline or rate-limited: leave the quote area hidden
  $("quote-text").textContent = q.text;
  $("quote-author").textContent = q.author;
  $("quote").hidden = false;
}

refreshAll();
loadQuote();
    });
    li.querySelector(".del").addEventListener("click", async () => {
      if (!confirm(`Delete "${t.title}" and its history?`)) return;
      await eel.delete_task(t.id)();
      await async function loadQuote() {
  const q = await eel.get_quote()();
  if (!q) return; // offline or rate-limited: leave the quote area hidden
  $("quote-text").textContent = q.text;
  $("quote-author").textContent = q.author;
  $("quote").hidden = false;
}

refreshAll();
loadQuote();
    });
    list.appendChild(li);
  });
}

async function refreshAll() {
  await refreshStats();
  await renderDay();
}

async function selectDay(iso) {
  state.selected = iso;
  await async function loadQuote() {
  const q = await eel.get_quote()();
  if (!q) return; // offline or rate-limited: leave the quote area hidden
  $("quote-text").textContent = q.text;
  $("quote-author").textContent = q.author;
  $("quote").hidden = false;
}

refreshAll();
loadQuote();
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
  await async function loadQuote() {
  const q = await eel.get_quote()();
  if (!q) return; // offline or rate-limited: leave the quote area hidden
  $("quote-text").textContent = q.text;
  $("quote-author").textContent = q.author;
  $("quote").hidden = false;
}

refreshAll();
loadQuote();
});

async function loadQuote() {
  const q = await eel.get_quote()();
  if (!q) return; // offline or rate-limited: leave the quote area hidden
  $("quote-text").textContent = q.text;
  $("quote-author").textContent = q.author;
  $("quote").hidden = false;
}

refreshAll();
loadQuote();
