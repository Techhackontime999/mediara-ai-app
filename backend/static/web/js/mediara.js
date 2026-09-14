/* Mediara web client helpers.
   Thin JWT session + fetch wrapper on top of the existing DRF /api/ contract. */

(() => {
  const TOKENS_KEY = "mediara.tokens";
  const USER_KEY = "mediara.user";

  const storage = {
    getTokens: () => {
      try { return JSON.parse(localStorage.getItem(TOKENS_KEY) || "null"); } catch { return null; }
    },
    setTokens: (t) => localStorage.setItem(TOKENS_KEY, JSON.stringify(t)),
    clearTokens: () => localStorage.removeItem(TOKENS_KEY),
    getUser: () => {
      try { return JSON.parse(localStorage.getItem(USER_KEY) || "null"); } catch { return null; }
    },
    setUser: (u) => localStorage.setItem(USER_KEY, JSON.stringify(u)),
    clearUser: () => localStorage.removeItem(USER_KEY),
  };

  async function api(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = { ...(options.headers || {}) };
    if (options.body && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    const tokens = storage.getTokens();
    if (tokens?.access) headers["Authorization"] = "Bearer " + tokens.access;

    let resp = await fetch(window.API_BASE + path.replace(/^\//, ""), {
      method,
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });

    if (resp.status === 401 && tokens?.refresh && !options._retried) {
      const refreshed = await tryRefresh(tokens.refresh);
      if (refreshed) {
        return api(path, { ...options, _retried: true });
      }
    }
    return resp;
  }

  async function tryRefresh(refresh) {
    try {
      const r = await fetch(window.API_BASE + "auth/refresh/", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh }),
      });
      if (!r.ok) return null;
      const data = await r.json();
      const keep = storage.getTokens() || {};
      storage.setTokens({ ...keep, access: data.access, refresh: data.refresh || refresh });
      return true;
    } catch {
      return null;
    }
  }

  async function apiJson(path, options = {}) {
    const resp = await api(path, options);
    let data = null;
    try { data = await resp.json(); } catch { /* non-JSON body */ }
    if (!resp.ok) {
      const detail = data?.detail || (Array.isArray(data) ? data[0] : undefined) || null;
      const err = new Error(typeof detail === "string" ? detail : "Request failed (" + resp.status + ").");
      err.status = resp.status;
      err.payload = data;
      throw err;
    }
    return data;
  }

  function isAuthenticated() {
    const t = storage.getTokens();
    return Boolean(t?.access);
  }

  function requireAuth(redirectUrl) {
    if (isAuthenticated()) return true;
    clearSession();
    const target = redirectUrl || (location.pathname + location.search);
    location.href = "/login/?next=" + encodeURIComponent(target);
    return false;
  }

  function clearSession() {
    storage.clearTokens();
    storage.clearUser();
  }

  /* ---------- toast ---------- */

  const toasts = () => {
    let el = document.getElementById("toasts");
    if (!el) {
      el = document.createElement("div");
      el.id = "toasts";
      document.body.appendChild(el);
    }
    return el;
  };

  function toast(message, kind = "") {
    const el = document.createElement("div");
    el.className = "toast" + (kind ? " toast-" + kind : "");
    el.textContent = message;
    toasts().appendChild(el);
    setTimeout(() => {
      el.style.opacity = "0";
      el.style.transition = "opacity .3s ease";
      setTimeout(() => el.remove(), 320);
    }, 3600);
  }

  /* ---------- snippets / date ---------- */

  function formatDate(iso) {
    if (!iso) return "";
    try {
      return new Date(iso).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });
    } catch {
      return iso;
    }
  }

  function timeAgo(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    const s = Math.floor((Date.now() - d.getTime()) / 1000);
    if (s < 60) return "just now";
    const m = Math.floor(s / 60);
    if (m < 60) return m + "m ago";
    const h = Math.floor(m / 60);
    if (h < 24) return h + "h ago";
    const days = Math.floor(h / 24);
    if (days < 30) return days + "d ago";
    return formatDate(iso);
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  function statusBadge(status) {
    const map = {
      CREATED: "indigo",
      INVITED: "indigo",
      PRIVATE_COLLECTION: "neutral",
      ANALYSIS_READY: "teal",
      PROPOSALS_READY: "amber",
      REVIEW: "amber",
      AGREEMENT_READY: "teal",
      SIGNED: "teal",
      CONCLUDED: "neutral",
      RESOLVED: "teal",
      CLOSED: "neutral",
      SAFETY_HOLD: "rose",
    };
    const label = (status || "").replace(/_/g, " ");
    return `<span class="badge badge-${map[status] || "neutral"}">${escapeHtml(label)}</span>`;
  }

  /* ---------- modal ---------- */

  function openModal({ title, body, onMount }) {
    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";
    backdrop.innerHTML = `
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-head">
          <h3>${escapeHtml(title)}</h3>
          <button class="modal-close" aria-label="Close">&times;</button>
        </div>
        <div class="modal-body" data-modal-body></div>
      </div>`;
    const close = () => backdrop.remove();
    backdrop.addEventListener("click", (e) => { if (e.target === backdrop) close(); });
    backdrop.querySelector(".modal-close").addEventListener("click", close);
    document.addEventListener("keydown", function onKey(e) {
      if (e.key === "Escape") { close(); document.removeEventListener("keydown", onKey); }
    });
    const bodyEl = backdrop.querySelector("[data-modal-body]");
    if (typeof body === "string") bodyEl.innerHTML = body;
    else if (body) bodyEl.appendChild(body);
    document.body.appendChild(backdrop);
    onMount?.(bodyEl, close);
    return close;
  }

  window.Mediara = {
    storage,
    api,
    apiJson,
    isAuthenticated,
    requireAuth,
    clearSession,
    toast,
    formatDate,
    timeAgo,
    escapeHtml,
    statusBadge,
    openModal,
  };
})();