// FleetOps dashboard. Plain JavaScript, no build step: the page talks to
// fleet-api through the same origin (nginx proxies /api), so there is no CORS
// setup and no API address to configure.

const PAGE_SIZE = 20;
const REFRESH_MS = 5000;

const state = { model: "", page: 0, rolloutId: null, timer: null };

const $ = (id) => document.getElementById(id);

async function api(method, path, body) {
  const response = await fetch(path, {
    method,
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    // fleet-api errors carry a message; anything else (a proxy error page, say)
    // falls back to the status line.
    let message = `${response.status} ${response.statusText}`;
    try {
      message = (await response.json()).message || message;
    } catch (ignored) {
      // not JSON
    }
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

function showError(error) {
  $("error").textContent = error ? error.message : "";
  $("error").hidden = !error;
}

function cell(text) {
  const td = document.createElement("td");
  td.textContent = text;
  return td;
}

function row(...texts) {
  const tr = document.createElement("tr");
  texts.forEach((text) => tr.appendChild(cell(text)));
  return tr;
}

const date = (iso) => (iso ? new Date(iso).toLocaleString() : "");

// ---- vehicles ----

async function loadVehicles() {
  const params = new URLSearchParams({ page: state.page, size: PAGE_SIZE });
  if (state.model) params.set("model", state.model);
  const data = await api("GET", `/api/v1/vehicles?${params}`);

  // Spring Data serialises paging either nested under "page" or flat,
  // depending on its version and settings; read both.
  const page = data.page || data;
  const totalPages = Math.max(page.totalPages || 0, 1);

  $("vehicle-rows").replaceChildren(
    ...data.content.map((v) => row(v.vin, v.model, v.swVersion, date(v.registeredAt))));
  $("page-info").textContent = `Page ${state.page + 1} of ${totalPages}`;
  $("previous-page").disabled = state.page === 0;
  $("next-page").disabled = state.page + 1 >= totalPages;
}

// ---- releases ----

async function loadReleases() {
  const releases = await api("GET", "/api/v1/releases");
  $("release-rows").replaceChildren(
    ...releases.map((r) => row(r.version, r.targetModel, `${r.sizeBytes} bytes`, date(r.publishedAt))));

  $("rollout-release").replaceChildren(...releases.map((r) => {
    const option = document.createElement("option");
    option.value = r.id;
    option.textContent = `${r.version} (${r.targetModel})`;
    return option;
  }));
}

// ---- one rollout ----

async function loadRollout() {
  const r = await api("GET", `/api/v1/rollouts/${state.rolloutId}`);
  $("rollout-heading").textContent = `Rollout ${r.id}`;
  $("rollout-status").textContent = r.status;
  $("rollout-pause-reason").textContent = r.pauseReason || "none";
  $("rollout-percentage-value").textContent = `${r.percentage}%`;
  for (const name of ["pending", "installing", "succeeded", "failed"]) {
    $(`count-${name}`).textContent = r[name];
  }
  $("pause-rollout").disabled = r.status !== "RUNNING";
  $("resume-rollout").disabled = r.status !== "PAUSED";
}

// ---- navigation ----

async function show() {
  clearInterval(state.timer);
  showError(null);
  const [, view, id] = (location.hash || "#/vehicles").split("/");

  for (const name of ["vehicles", "releases", "rollout"]) {
    $(`${name}-view`).hidden = true;
  }

  try {
    if (view === "rollouts" && id) {
      state.rolloutId = id;
      $("rollout-view").hidden = false;
      await loadRollout();
      state.timer = setInterval(() => loadRollout().catch(showError), REFRESH_MS);
    } else if (view === "releases") {
      $("releases-view").hidden = false;
      await loadReleases();
    } else {
      $("vehicles-view").hidden = false;
      await loadVehicles();
    }
  } catch (error) {
    showError(error);
  }
}

// ---- wiring ----

function on(id, event, handler) {
  $(id).addEventListener(event, async (e) => {
    e.preventDefault();
    showError(null);
    try {
      await handler(e);
    } catch (error) {
      showError(error);
    }
  });
}

on("vehicle-filter", "submit", () => {
  state.model = $("model-filter").value.trim();
  state.page = 0;
  return loadVehicles();
});
on("previous-page", "click", () => { state.page -= 1; return loadVehicles(); });
on("next-page", "click", () => { state.page += 1; return loadVehicles(); });

on("start-rollout", "submit", async () => {
  const rollout = await api("POST", "/api/v1/rollouts", {
    releaseId: $("rollout-release").value,
    percentage: Number($("rollout-percentage").value),
  });
  location.hash = `#/rollouts/${rollout.id}`;
});

on("pause-rollout", "click", async () => {
  await api("POST", `/api/v1/rollouts/${state.rolloutId}/pause`);
  await loadRollout();
});
on("resume-rollout", "click", async () => {
  await api("POST", `/api/v1/rollouts/${state.rolloutId}/resume`);
  await loadRollout();
});
on("raise-rollout", "submit", async () => {
  await api("POST", `/api/v1/rollouts/${state.rolloutId}/percentage`, {
    percentage: Number($("raise-percentage").value),
  });
  await loadRollout();
});

window.addEventListener("hashchange", show);
show();
