// MetalRat – Browser-Erweiterung für Chrome, Brave, Edge und Firefox.
// Alt+Umschalt+B, Klick aufs Symbol oder Rechtsklick › „In MetalRat merken“: die Seite geht mit
// Video-Zeitpunkt, markiertem Text und Vorschaubild an die MetalRat-App auf diesem Rechner.
// Läuft die App gerade nicht, wartet der Eintrag hier und wird jede Minute neu zugestellt.

const api = globalThis.browser ?? globalThis.chrome;
const ENDPOINT = "http://127.0.0.1:47123/add";
const MENU_ID = "metalrat-merken";
const TITLE = "In MetalRat merken";

api.runtime.onInstalled.addListener(async () => {
  try { await api.contextMenus.removeAll(); } catch (_) {}
  api.contextMenus.create({ id: MENU_ID, title: TITLE, contexts: ["page", "selection", "link", "video", "audio", "image"] });
  flushQueue();
});
api.runtime.onStartup.addListener(() => flushQueue());
api.action.onClicked.addListener((tab) => capture(tab));
api.commands.onCommand.addListener((command, tab) => { if (command === "merken") capture(tab); });
api.contextMenus.onClicked.addListener((info, tab) => { if (info.menuItemId === MENU_ID) capture(tab, info); });
api.alarms.onAlarm.addListener((alarm) => { if (alarm.name === "flush") flushQueue(); });

async function capture(tab, info = {}) {
  if (!tab?.id || !tab.url) {
    const [active] = await api.tabs.query({ active: true, lastFocusedWindow: true });
    tab = active ?? tab;
  }
  if (!tab?.url) return;

  const frames = await grabFrames(tab.id);
  const top = frames.find((f) => f.top) ?? {};
  const media = frames.find((f) => f.playing) ?? frames.find((f) => f.time != null);
  const selection = (info.selectionText || frames.map((f) => f.selection).find(Boolean) || "").trim();
  // Right-click on a link: save that link, not the page.
  const isLink = !!info.linkUrl && !info.selectionText;

  await deliver({
    id: crypto.randomUUID(),
    url: isLink ? info.linkUrl : tab.url,
    title: isLink ? null : tab.title || top.title || null,
    time: isLink ? null : media?.time ?? null,
    duration: isLink ? null : media?.duration ?? null,
    selection: isLink ? null : selection || null,
    image: isLink ? null : top.image ?? null,
    source: sourceName(),
    capturedAt: Date.now(),
  });
}

async function grabFrames(tabId) {
  const out = [];
  const run = async (target) => {
    try {
      const results = await api.scripting.executeScript({ target, func: grab });
      for (const r of results ?? []) if (r?.result) out.push(r.result);
    } catch (_) { /* chrome://, Add-on-Seiten, fremde iframes … */ }
  };
  await run({ tabId, frameIds: [0] });
  await run({ tabId, allFrames: true }); // eingebettete Videos, soweit der Browser es erlaubt
  return out;
}

// Runs inside the page, so it must be self-contained.
function grab() {
  const area = (el) => { const r = el.getBoundingClientRect(); return r.width * r.height; };
  const media = [...document.querySelectorAll("video, audio")].filter((m) => m.currentTime > 0 || m.duration > 0);
  const m = media.find((x) => !x.paused) || media.sort((a, b) => area(b) - area(a))[0];
  const meta = (name) => document.querySelector(`meta[property="${name}"], meta[name="${name}"]`)?.content || null;
  const selection = String(window.getSelection?.() ?? "").trim();
  return {
    top: window === window.top,
    selection: selection || null,
    time: m ? m.currentTime : null,
    duration: m && Number.isFinite(m.duration) ? m.duration : null,
    playing: !!(m && !m.paused),
    image: meta("og:image") || meta("twitter:image"),
    title: meta("og:title"),
  };
}

async function deliver(item) {
  try {
    await post(item);
    await flash("✓", "#2e7d32");
    flushQueue();
  } catch (_) {
    const queue = await loadQueue();
    queue.push(item);
    await saveQueue(queue);
    api.alarms.create("flush", { periodInMinutes: 1 });
  }
}

async function post(item) {
  const res = await fetch(ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(item),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
}

async function flushQueue() {
  const queue = await loadQueue();
  if (!queue.length) { await api.alarms.clear("flush"); return; }
  const left = [];
  for (const item of queue) {
    if (left.length) { left.push(item); continue; } // app not reachable: don't hammer it
    try { await post(item); } catch (_) { left.push(item); }
  }
  await saveQueue(left);
  if (left.length) api.alarms.create("flush", { periodInMinutes: 1 });
  else await api.alarms.clear("flush");
}

async function loadQueue() {
  return (await api.storage.local.get("queue")).queue ?? [];
}

async function saveQueue(queue) {
  await api.storage.local.set({ queue });
  await api.action.setBadgeBackgroundColor({ color: "#E08A3C" });
  await api.action.setBadgeText({ text: queue.length ? String(queue.length) : "" });
  await api.action.setTitle({
    title: queue.length ? `${queue.length} wartend – MetalRat-App öffnen, dann wird zugestellt` : TITLE,
  });
}

async function flash(text, color) {
  await api.action.setBadgeBackgroundColor({ color });
  await api.action.setBadgeText({ text });
  setTimeout(async () => {
    const queue = await loadQueue();
    await api.action.setBadgeBackgroundColor({ color: "#E08A3C" });
    await api.action.setBadgeText({ text: queue.length ? String(queue.length) : "" });
  }, 1500);
}

function sourceName() {
  const ua = navigator.userAgent;
  const browser = navigator.brave ? "Brave"
    : /Firefox\//.test(ua) ? "Firefox"
    : /Edg\//.test(ua) ? "Edge"
    : /OPR\//.test(ua) ? "Opera"
    : "Chrome";
  const os = /Mac OS X|Macintosh/.test(ua) ? "Mac"
    : /Android/.test(ua) ? "Android"
    : /Linux|X11|CrOS/.test(ua) ? "Linux"
    : /Windows/.test(ua) ? "Windows" : "";
  return os ? `${browser} · ${os}` : browser;
}
