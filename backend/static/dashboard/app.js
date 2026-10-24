/* LocalRush Admin dashboard — vanilla JS SPA over /api/v1/admin/ + /ws/admin/live/ */
(() => {
  const API = "/api/v1";
  let token = null, refreshToken = null, me = null, ws = null, wsTimer = null;
  const $ = (s, el = document) => el.querySelector(s);
  const view = $("#view"), title = $("#page-title");
  const fmtMoney = v => "₹" + Number(v || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const fmtDT = v => v ? new Date(v).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "—";
  const fmtT = v => v ? new Date(v).toLocaleTimeString("en-IN", { timeStyle: "short" }) : "—";
  const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const chip = s => `<span class="chip ${esc(s)}">${esc(String(s || "").replace(/_/g, " "))}</span>`;
  const toast = (msg, err = false) => { const t = document.createElement("div"); t.className = "toast" + (err ? " err" : ""); t.textContent = msg; $("#toast-root").appendChild(t); setTimeout(() => t.remove(), 3500); };

  // ---------- auth
  async function getToken() {
    const r = await fetch("/dashboard/api-token/", { credentials: "same-origin" });
    if (!r.ok) { location.href = "/dashboard/login/"; return; }
    const d = await r.json(); token = d.access; refreshToken = d.refresh; me = d.user;
    $("#user-chip").textContent = (me.name || "A")[0].toUpperCase(); $("#user-chip").title = me.name;
  }
  async function api(path, opts = {}) {
    const headers = { Authorization: "Bearer " + token, ...(opts.body && !(opts.body instanceof FormData) ? { "Content-Type": "application/json" } : {}) };
    let r = await fetch(API + path, { ...opts, headers, body: opts.body && !(opts.body instanceof FormData) ? JSON.stringify(opts.body) : opts.body });
    if (r.status === 401) { await getToken(); return api(path, opts); }
    if (r.status === 204) return null;
    const data = await r.json().catch(() => ({}));
    if (!r.ok) { const m = data?.error?.message || r.statusText; toast(m, true); throw new Error(m); }
    return data;
  }

  // ---------- modal
  function modal(html, onMount) {
    const root = $("#modal-root"); root.innerHTML = `<div class="modal-bg"><div class="modal">${html}</div></div>`;
    root.querySelector(".modal-bg").addEventListener("click", e => { if (e.target.classList.contains("modal-bg")) closeModal(); });
    onMount && onMount(root.querySelector(".modal"));
  }
  const closeModal = () => { $("#modal-root").innerHTML = ""; };
  function confirmBox(msg, label = "Confirm", withReason = false) {
    return new Promise(res => modal(`<h2>${esc(msg)}</h2>${withReason ? '<div class="form"><label>Reason</label><input id="m-reason" placeholder="Reason"></div>' : ""}<div class="row"><button class="btn" id="m-cancel">Cancel</button><button class="btn primary" id="m-ok">${esc(label)}</button></div>`,
      m => { $("#m-cancel", m).onclick = () => { closeModal(); res(null); }; $("#m-ok", m).onclick = () => { const v = withReason ? $("#m-reason", m).value : true; closeModal(); res(v || (withReason ? "" : true)); }; }));
  }

  // ---------- maps
  function makeMap(el, center = [72.8777, 19.076], zoom = 11) {
    const map = new maplibregl.Map({ container: el, style: window.LR.mapStyle, center, zoom, attributionControl: false });
    map.addControl(new maplibregl.AttributionControl({ customAttribution: window.LR.attribution, compact: true }));
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right");
    return map;
  }
  const scooterSvg = (rot, ring) => `<svg viewBox="0 0 56 56" width="40" height="40" style="filter:drop-shadow(0 3px 5px rgba(0,0,0,.3))"><circle cx="28" cy="28" r="25" fill="#fff" stroke="${ring}" stroke-width="3"/><g transform="rotate(${rot} 28 28)"><rect x="24" y="12" width="8" height="30" rx="4" fill="#F26B1D"/><rect x="18" y="16" width="20" height="6" rx="3" fill="#23272F"/><rect x="21" y="30" width="14" height="8" rx="3" fill="#23272F"/><circle cx="28" cy="13" r="4" fill="#23272F"/><circle cx="28" cy="43" r="4" fill="#23272F"/></g></svg>`;
  function riderMarkerEl(busy, bearing = 0) { const d = document.createElement("div"); d.className = "rider-marker" + (busy ? " busy" : ""); d.innerHTML = scooterSvg(bearing, busy ? "#2563EB" : "#F26B1D"); d.dataset.busy = busy ? "1" : ""; return d; }
  function setMarkerHeading(marker, bearing, busy) { const el = marker.getElement(); el.innerHTML = scooterSvg(Math.round(bearing || 0), busy ? "#2563EB" : "#F26B1D"); }

  // ---------- websocket
  const liveListeners = new Set();
  function connectWS() {
    if (ws) { try { ws.close(); } catch (_) { } }
    const proto = location.protocol === "https:" ? "wss" : "ws";
    ws = new WebSocket(`${proto}://${location.host}/ws/admin/live/?token=${token}`);
    ws.onopen = () => $("#ws-dot").classList.add("on");
    ws.onclose = () => { $("#ws-dot").classList.remove("on"); clearTimeout(wsTimer); wsTimer = setTimeout(connectWS, 3000); };
    ws.onmessage = e => { const m = JSON.parse(e.data); if (m.type === "ping") { ws.send(JSON.stringify({ type: "pong" })); return; } liveListeners.forEach(fn => fn(m)); };
  }

  // ---------- table helper with pagination
  async function pagedTable(container, path, columns, { params = {}, onRow, pageSize = 20 } = {}) {
    let page = 1;
    async function load() {
      const q = new URLSearchParams({ ...params, page, page_size: pageSize }).toString();
      const data = await api(`${path}?${q}`);
      const rows = data.results || data;
      container.innerHTML = `<table><thead><tr>${columns.map(c => `<th>${esc(c.label)}</th>`).join("")}</tr></thead><tbody>${rows.length ? rows.map(r => `<tr class="${onRow ? "click" : ""}" data-id="${esc(r.id)}">${columns.map(c => `<td>${c.render ? c.render(r) : esc(r[c.key])}</td>`).join("")}</tr>`).join("") : `<tr><td colspan="${columns.length}" class="empty">Nothing here yet</td></tr>`}</tbody></table>
        ${data.count !== undefined ? `<div class="pager"><span class="muted">${data.count} total</span><button class="btn sm" id="pg-prev" ${!data.previous ? "disabled" : ""}>‹ Prev</button><span>Page ${page}</span><button class="btn sm" id="pg-next" ${!data.next ? "disabled" : ""}>Next ›</button></div>` : ""}`;
      if (onRow) container.querySelectorAll("tr.click").forEach(tr => tr.onclick = () => onRow(rows.find(r => String(r.id) === tr.dataset.id)));
      const p = $("#pg-prev", container), n = $("#pg-next", container);
      if (p) p.onclick = () => { page--; load(); }; if (n) n.onclick = () => { page++; load(); };
    }
    await load(); return { reload: load, setParams: np => { params = np; page = 1; return load(); } };
  }

  // ---------- order detail modal (shared)
  async function openOrder(id) {
    const o = await api(`/admin/orders/${id}/detail_full/`);
    const tl = o.timeline.map(t => `<li class="${t.done ? "done" : ""} ${t.current ? "current" : ""}"><span class="dot"></span><span>${esc(t.label)}</span><span class="muted" style="margin-left:auto">${fmtT(t.at)}</span></li>`).join("");
    modal(`<h2>Order ${esc(o.order_number)} ${chip(o.status)}</h2>
      <div class="grid grid-2">
        <div><div class="kv"><b>Customer</b><span>${esc(o.delivery_name)} · ${esc(o.customer_phone)}</span><b>Restaurant</b><span>${esc(o.restaurant_name)}</span><b>Rider</b><span>${o.rider ? esc(o.rider.full_name) + " · " + esc(o.rider_phone || "") : "—"}</span>
          <b>Address</b><span>${esc(o.delivery_address_text)}</span><b>Payment</b><span>${esc(o.payment_method.toUpperCase())} ${o.is_paid ? "· paid" : "· unpaid"}</span><b>Placed</b><span>${fmtDT(o.placed_at)}</span><b>ETA</b><span>${fmtDT(o.estimated_delivery_at)}</span>
          <b>Pickup OTP</b><span class="mono">${esc(o.pickup_otp)}</span><b>Delivery OTP</b><span class="mono">${esc(o.delivery_otp)}</span>${o.cancellation_reason ? `<b>Cancel reason</b><span>${esc(o.cancelled_by)}: ${esc(o.cancellation_reason)}</span>` : ""}</div>
          <h3 style="margin-top:14px">Items</h3><table>${o.items.map(i => `<tr><td>${i.quantity} × ${esc(i.name)} ${i.variant_name ? "(" + esc(i.variant_name) + ")" : ""}<br><span class="muted">${esc((i.addon_names || []).join(", "))}</span></td><td class="right">${fmtMoney(i.line_total)}</td></tr>`).join("")}
          <tr><td class="right muted">Items</td><td class="right">${fmtMoney(o.pricing.item_total)}</td></tr><tr><td class="right muted">Delivery</td><td class="right">${fmtMoney(o.pricing.delivery_fee)} ${Number(o.pricing.delivery_discount) ? "− " + fmtMoney(o.pricing.delivery_discount) : ""}</td></tr><tr><td class="right muted">Fees + GST</td><td class="right">${fmtMoney(Number(o.pricing.platform_fee) + Number(o.pricing.tax_amount) + Number(o.pricing.packaging_charge))}</td></tr>${Number(o.pricing.coupon_discount) ? `<tr><td class="right muted">Coupon ${esc(o.pricing.coupon_code)}</td><td class="right">− ${fmtMoney(o.pricing.coupon_discount)}</td></tr>` : ""}<tr><td class="right"><b>Total</b></td><td class="right"><b>${fmtMoney(o.pricing.total)}</b></td></tr></table></div>
        <div><h3>Timeline</h3><ul class="timeline">${tl}</ul>
          <h3 style="margin-top:14px">Payments</h3>${o.payments.map(p => `<div>${chip(p.status)} ${esc(p.method)} ${fmtMoney(p.amount)} <span class="mono muted">${esc(p.provider_payment_id || "")}</span></div>`).join("") || '<span class="muted">—</span>'}
          <h3 style="margin-top:14px">Refunds</h3>${o.refunds.map(r => `<div>${chip(r.status)} ${fmtMoney(r.amount)} · ${esc(r.reason)}</div>`).join("") || '<span class="muted">—</span>'}
          <h3 style="margin-top:14px">Rider offers</h3>${o.assignments.map(a => `<div>${chip(a.status)} ${esc(a.rider__full_name)} <span class="muted">${fmtT(a.offered_at)}</span></div>`).join("") || '<span class="muted">—</span>'}
          <div id="o-map" class="map sm" style="margin-top:14px"></div></div></div>
      <div class="row"><button class="btn" id="o-track">Track live</button>${o.is_active ? '<button class="btn" id="o-assign">Assign / reassign rider</button><button class="btn danger" id="o-cancel">Cancel order</button>' : ""}${o.is_paid && o.status !== "cancelled" ? '<button class="btn" id="o-refund">Refund</button>' : ""}<button class="btn primary" id="o-close">Close</button></div>`,
      m => {
        $("#o-close", m).onclick = closeModal; $("#o-track", m).onclick = () => { closeModal(); location.hash = "#/live?order=" + o.id; };
        const map = makeMap($("#o-map", m), [o.restaurant_location.longitude, o.restaurant_location.latitude], 12);
        map.on("load", () => {
          new maplibregl.Marker({ color: "#F26B1D" }).setLngLat([o.restaurant_location.longitude, o.restaurant_location.latitude]).addTo(map);
          new maplibregl.Marker({ color: "#2563EB" }).setLngLat([o.delivery_location.longitude, o.delivery_location.latitude]).addTo(map);
          const b = new maplibregl.LngLatBounds(); b.extend([o.restaurant_location.longitude, o.restaurant_location.latitude]); b.extend([o.delivery_location.longitude, o.delivery_location.latitude]);
          if (o.rider_location) { new maplibregl.Marker({ element: riderMarkerEl(true) }).setLngLat([o.rider_location.longitude, o.rider_location.latitude]).addTo(map); b.extend([o.rider_location.longitude, o.rider_location.latitude]); }
          map.fitBounds(b, { padding: 40 });
        });
        const c = $("#o-cancel", m); if (c) c.onclick = async () => { const r = await confirmBox("Cancel this order?", "Cancel order", true); if (r === null) return; await api(`/admin/orders/${o.id}/cancel/`, { method: "POST", body: { reason: r } }); toast("Order cancelled"); closeModal(); render(); };
        const rf = $("#o-refund", m); if (rf) rf.onclick = () => modal(`<h2>Refund ${esc(o.order_number)}</h2><div class="form"><label>Amount</label><input id="rf-amt" value="${o.pricing.total}"><label>Reason</label><input id="rf-reason" placeholder="Reason"><label><input type="checkbox" id="rf-ded" style="width:auto"> Deduct from restaurant settlement</label></div><div class="row"><button class="btn" onclick="document.getElementById('modal-root').innerHTML=''">Cancel</button><button class="btn primary" id="rf-ok">Refund</button></div>`, mm => { $("#rf-ok", mm).onclick = async () => { await api(`/admin/orders/${o.id}/refund/`, { method: "POST", body: { amount: $("#rf-amt", mm).value, reason: $("#rf-reason", mm).value, deduct_restaurant: $("#rf-ded", mm).checked } }); toast("Refund initiated"); closeModal(); }; });
        const as = $("#o-assign", m); if (as) as.onclick = async () => {
          const riders = await api(`/admin/orders/${o.id}/candidate-riders/`);
          modal(`<h2>Assign rider</h2>${riders.length ? `<table>${riders.map(r => `<tr><td>${esc(r.full_name)}</td><td>${esc(r.phone)}</td><td>${r.distance_km} km</td><td>★ ${r.rating_avg}</td><td class="right"><button class="btn sm primary" data-r="${r.id}">Assign</button></td></tr>`).join("")}</table>` : '<div class="empty">No online riders nearby. Try searching all riders:</div>'}<div class="form"><label>Or rider ID (any approved rider)</label><input id="as-id" placeholder="rider uuid"></div><div class="row"><button class="btn" id="as-cancel">Cancel</button><button class="btn primary" id="as-ok">Assign by ID</button></div>`,
            mm => { $("#as-cancel", mm).onclick = closeModal; const go = async rid => { await api(`/admin/orders/${o.id}/assign-rider/`, { method: "POST", body: { rider_id: rid } }); toast("Rider assigned"); closeModal(); }; mm.querySelectorAll("[data-r]").forEach(b => b.onclick = () => go(b.dataset.r)); $("#as-ok", mm).onclick = () => go($("#as-id", mm).value); });
        };
      });
  }

  // ---------- pages
  const pages = {};
  let mapCenter = null;
  async function serviceCenter() {
    if (mapCenter) return mapCenter;
    try { const r = await api("/admin/restaurants/?page_size=1"); const x = r.results && r.results[0]; if (x && x.lng) mapCenter = [x.lng, x.lat]; } catch (_) { }
    return mapCenter || [72.8777, 19.076];
  }
  pages["/"] = async (params, alive) => {
    title.textContent = "Dashboard";
    view.innerHTML = `<div class="grid grid-4" id="stats"></div><div class="grid grid-21"><div class="card"><h3>Order & sales overview <select id="days"><option value="7">Last 7 days</option><option value="30" selected>Last 30 days</option><option value="90">Last 90 days</option></select></h3><canvas id="chart" height="110"></canvas></div><div class="card"><h3>Live delivery tracking <a href="#/live" class="muted">Open ›</a></h3><div id="mini-map" class="map sm"></div><div id="live-count" class="muted" style="margin-top:8px"></div></div></div><div class="card"><h3>Recent orders <a href="#/orders" class="muted">All orders ›</a></h3><div id="recent"></div></div>`;
    const ov = await api("/admin/overview/");
    if (alive && !alive()) return;
    $("#stats").innerHTML = [
      ["s1", "Orders today", ov.orders_today.total, `${ov.orders_today.active} active · ${ov.orders_today.completed} delivered`, "🧾"],
      ["s2", "Revenue today", fmtMoney(ov.orders_today.platform_revenue), `GOV ${fmtMoney(ov.orders_today.gross_order_value)}`, "💰"],
      ["s3", "Active deliveries", ov.riders.on_delivery, `${ov.riders.online} riders online · ${ov.orders_today.awaiting_rider} awaiting rider`, "🛵"],
      ["s4", "Customers", ov.users.total, `+${ov.users.new_today} today · ${ov.users.active_30d} active (30d)`, "👥"],
    ].map(([c, l, v, s, i]) => `<div class="stat ${c}"><div class="label">${l}</div><div class="value">${v}</div><div class="sub">${s}</div><div class="icon">${i}</div></div>`).join("");
    const extra = `<div class="grid grid-4"><div class="card"><div class="muted">Pending restaurant approvals</div><div class="big">${ov.restaurants.pending}</div></div><div class="card"><div class="muted">Pending rider approvals</div><div class="big">${ov.riders.pending}</div></div><div class="card"><div class="muted">Pending settlements</div><div class="big">${fmtMoney(ov.pending_settlements)}</div></div><div class="card"><div class="muted">Open tickets</div><div class="big">${ov.open_tickets}</div></div></div>`;
    $("#stats").insertAdjacentHTML("afterend", extra);
    let chart;
    async function drawChart() {
      const series = await api(`/admin/sales-series/?days=${$("#days").value}`);
      chart && chart.destroy();
      chart = new Chart($("#chart"), { type: "line", data: { labels: series.map(s => s.date.slice(5)), datasets: [
        { label: "Orders", data: series.map(s => s.orders), borderColor: "#7A1F1F", tension: .4, yAxisID: "y" },
        { label: "Sales (₹)", data: series.map(s => Number(s.sales)), borderColor: "#F26B1D", tension: .4, yAxisID: "y1" },
        { label: "Customers", data: series.map(s => s.customers), borderColor: "#9BC53D", tension: .4, yAxisID: "y" }] },
        options: { responsive: true, plugins: { legend: { position: "top", align: "end" } }, scales: { y: { beginAtZero: true }, y1: { position: "right", beginAtZero: true, grid: { drawOnChartArea: false } } } } });
    }
    $("#days").onchange = drawChart; drawChart();
    const map = makeMap($("#mini-map"), await serviceCenter(), 12); const markers = {};
    const snap = await api("/admin/riders/live/");
    const upd = r => { const ll = [r.longitude, r.latitude]; if (markers[r.rider_id]) { markers[r.rider_id].setLngLat(ll); setMarkerHeading(markers[r.rider_id], r.bearing, r.on_delivery); } else markers[r.rider_id] = new maplibregl.Marker({ element: riderMarkerEl(r.on_delivery, r.bearing) }).setLngLat(ll).addTo(map); };
    map.on("load", () => { snap.riders.forEach(upd); if (snap.riders.length) { const b = new maplibregl.LngLatBounds(); snap.riders.forEach(r => b.extend([r.longitude, r.latitude])); map.fitBounds(b, { padding: 40, maxZoom: 13 }); } });
    $("#live-count").textContent = `${snap.riders.length} riders online · ${snap.active_orders.length} active orders`;
    const l = m => { if (m.type === "rider.location") upd({ rider_id: m.rider_id, latitude: m.latitude, longitude: m.longitude, bearing: m.bearing, on_delivery: !!m.order_id }); };
    liveListeners.add(l); currentCleanup = () => liveListeners.delete(l);
    await pagedTable($("#recent"), "/admin/orders/", orderCols, { pageSize: 8, onRow: r => openOrder(r.id) });
  };

  const orderCols = [
    { label: "Order", render: r => `<b>${esc(r.order_number)}</b><br><span class="muted">${fmtDT(r.placed_at || r.created_at)}</span>` },
    { label: "Customer", render: r => `${esc(r.customer_name || "")}<br><span class="muted">${esc(r.customer_phone)}</span>` },
    { label: "Restaurant", key: "restaurant_name" }, { label: "Rider", render: r => esc(r.rider_name || "—") },
    { label: "Amount", render: r => fmtMoney(r.total_amount) + `<br><span class="muted">${esc(r.payment_method.toUpperCase())}${r.is_paid ? " ✓" : ""}</span>` },
    { label: "Status", render: r => chip(r.status) }, { label: "ETA", render: r => fmtT(r.estimated_delivery_at) }];

  pages["/orders"] = async () => {
    title.textContent = "Orders";
    view.innerHTML = `<div class="card"><div class="toolbar"><input id="q" placeholder="Order #, phone, restaurant"><select id="status"><option value="">All statuses</option>${["placed", "restaurant_accepted", "preparing", "ready_for_pickup", "rider_assigned", "picked_up", "out_for_delivery", "near_destination", "delivered", "cancelled", "rejected", "payment_failed", "delivery_failed", "pending_payment"].map(s => `<option value="${s}">${s.replace(/_/g, " ")}</option>`).join("")}</select><input type="date" id="from"><input type="date" id="to"><label><input type="checkbox" id="active"> Active only</label><button class="btn" id="apply">Apply</button></div><div id="tbl" style="margin-top:14px"></div></div>`;
    const t = await pagedTable($("#tbl"), "/admin/orders/", orderCols, { onRow: r => openOrder(r.id) });
    $("#apply").onclick = () => t.setParams({ q: $("#q").value, status: $("#status").value, date_from: $("#from").value, date_to: $("#to").value, scope: $("#active").checked ? "active" : "" });
    const l = m => { if (m.type && m.type.startsWith("status.")) t.reload(); }; liveListeners.add(l); currentCleanup = () => liveListeners.delete(l);
  };

  pages["/live"] = async (params) => {
    title.textContent = "Live tracking";
    view.innerHTML = `<div class="grid" style="grid-template-columns:1fr 340px"><div class="card" style="padding:8px"><div id="live-map" class="map lg"></div></div><div class="card" style="overflow:auto;max-height:calc(100vh - 140px)"><h3>Active orders</h3><div id="active-list"></div><h3 style="margin-top:14px">Riders online</h3><div id="rider-list"></div></div></div>`;
    const map = makeMap($("#live-map"), await serviceCenter(), 13); const markers = {}; let routeAdded = false;
    const snap = await api("/admin/riders/live/");
    const riders = {}; snap.riders.forEach(r => riders[r.rider_id] = r);
    const upd = r => { riders[r.rider_id] = { ...(riders[r.rider_id] || {}), ...r }; const ll = [r.longitude, r.latitude]; if (markers[r.rider_id]) { markers[r.rider_id].setLngLat(ll); setMarkerHeading(markers[r.rider_id], r.bearing, r.on_delivery); } else { const el = riderMarkerEl(r.on_delivery, r.bearing); el.title = r.name || ""; markers[r.rider_id] = new maplibregl.Marker({ element: el }).setLngLat(ll).setPopup(new maplibregl.Popup({ offset: 20 }).setText(r.name || r.rider_id)).addTo(map); } renderLists(); };
    const renderLists = () => {
      $("#active-list").innerHTML = snap.active_orders.map(o => `<div style="padding:8px 0;border-bottom:1px solid var(--line)"><b class="click" data-o="${o.id}" style="cursor:pointer">${esc(o.order_number)}</b> ${chip(o.status)}<br><span class="muted">${esc(o.restaurant_name)} · ${fmtMoney(o.total_amount)}</span></div>`).join("") || '<div class="muted">No active orders</div>';
      $("#active-list").querySelectorAll("[data-o]").forEach(b => b.onclick = () => openOrder(b.dataset.o));
      $("#rider-list").innerHTML = Object.values(riders).map(r => `<div style="padding:6px 0;cursor:pointer" data-r="${r.rider_id}">${r.on_delivery ? "🔵" : "🟢"} ${esc(r.name || r.rider_id.slice(0, 8))} <span class="muted">${r.speed ? Math.round(r.speed) + " km/h" : ""}</span></div>`).join("") || '<div class="muted">No riders online</div>';
      $("#rider-list").querySelectorAll("[data-r]").forEach(d => d.onclick = () => { const r = riders[d.dataset.r]; map.flyTo({ center: [r.longitude, r.latitude], zoom: 15 }); });
    };
    map.on("load", async () => {
      snap.riders.forEach(upd); renderLists();
      if (snap.riders.length) { const b = new maplibregl.LngLatBounds(); snap.riders.forEach(r => b.extend([r.longitude, r.latitude])); map.fitBounds(b, { padding: 60, maxZoom: 13 }); }
      if (params.get("order")) {
        const o = await api(`/admin/orders/${params.get("order")}/detail_full/`);
        new maplibregl.Marker({ color: "#F26B1D" }).setLngLat([o.restaurant_location.longitude, o.restaurant_location.latitude]).addTo(map);
        new maplibregl.Marker({ color: "#2563EB" }).setLngLat([o.delivery_location.longitude, o.delivery_location.latitude]).addTo(map);
        const b = new maplibregl.LngLatBounds([o.restaurant_location.longitude, o.restaurant_location.latitude], [o.delivery_location.longitude, o.delivery_location.latitude]);
        map.fitBounds(b, { padding: 80 });
      }
    });
    const l = m => {
      if (m.type === "rider.location") { upd({ rider_id: m.rider_id, latitude: m.latitude, longitude: m.longitude, speed: m.speed, bearing: m.bearing, on_delivery: !!m.order_id });
        if (m.route && m.route.geometry && params.get("order") === m.order_id) drawRoute(map, m.route.geometry, routeAdded); routeAdded = true; }
      else if (m.type && m.type.startsWith("status.")) { api("/admin/riders/live/").then(s => { snap.active_orders = s.active_orders; renderLists(); }); }
    };
    liveListeners.add(l); currentCleanup = () => liveListeners.delete(l);
  };
  function decodePolyline(str) { let i = 0, lat = 0, lng = 0, out = []; while (i < str.length) { let b, sh = 0, r = 0; do { b = str.charCodeAt(i++) - 63; r |= (b & 31) << sh; sh += 5; } while (b >= 32); lat += (r & 1) ? ~(r >> 1) : r >> 1; sh = 0; r = 0; do { b = str.charCodeAt(i++) - 63; r |= (b & 31) << sh; sh += 5; } while (b >= 32); lng += (r & 1) ? ~(r >> 1) : r >> 1; out.push([lng / 1e5, lat / 1e5]); } return out; }
  function drawRoute(map, geometry, exists) { const data = { type: "Feature", geometry: { type: "LineString", coordinates: decodePolyline(geometry) } }; if (exists && map.getSource("route")) map.getSource("route").setData(data); else { if (map.getSource("route")) map.getSource("route").setData(data); else { map.addSource("route", { type: "geojson", data }); map.addLayer({ id: "route", type: "line", source: "route", paint: { "line-color": "#F26B1D", "line-width": 5 }, layout: { "line-cap": "round", "line-join": "round" } }); } } }

  pages["/customers"] = async () => {
    title.textContent = "Customers";
    view.innerHTML = `<div class="card"><div class="toolbar"><input id="q" placeholder="Phone, name, email"><select id="role"><option value="customer">Customers</option><option value="partner">Partner accounts</option><option value="rider">Rider accounts</option><option value="admin">Admins</option></select><button class="btn" id="apply">Search</button><button class="btn primary" id="new-user" style="margin-left:auto">+ Create staff/partner login</button></div><div id="tbl" style="margin-top:14px"></div></div>`;
    const cols = [{ label: "Name", render: r => `<b>${esc(r.full_name || "—")}</b><br><span class="muted">${esc(r.email || "")}</span>` }, { label: "Phone", key: "phone" }, { label: "Role", key: "role" }, { label: "Orders", key: "total_orders" }, { label: "Joined", render: r => fmtDT(r.created_at) }, { label: "Status", render: r => r.is_suspended ? chip("suspended") : (r.is_active ? chip("approved") : chip("rejected")) }];
    const t = await pagedTable($("#tbl"), "/admin/users/", cols, { params: { role: "customer" }, onRow: async u => {
      const orders = await api(`/admin/users/${u.id}/orders/`);
      modal(`<h2>${esc(u.full_name || u.phone)} ${u.is_suspended ? chip("suspended") : ""}</h2><div class="form"><label>Full name</label><input id="u-name" value="${esc(u.full_name)}"><label>Email</label><input id="u-email" value="${esc(u.email || "")}"></div>
        <h3 style="margin-top:14px">Recent orders</h3><table>${orders.map(o => `<tr class="click" data-o="${o.id}"><td>${esc(o.order_number)}</td><td>${esc(o.restaurant_name)}</td><td>${fmtMoney(o.total_amount)}</td><td>${chip(o.status)}</td></tr>`).join("") || "<tr><td class='muted'>No orders</td></tr>"}</table>
        <div class="row"><button class="btn danger" id="u-susp">${u.is_suspended ? "Unsuspend" : "Suspend"}</button><button class="btn" id="u-cancel">Close</button><button class="btn primary" id="u-save">Save</button></div>`, m => {
        $("#u-cancel", m).onclick = closeModal; m.querySelectorAll("[data-o]").forEach(tr => tr.onclick = () => openOrder(tr.dataset.o));
        $("#u-save", m).onclick = async () => { await api(`/admin/users/${u.id}/`, { method: "PATCH", body: { full_name: $("#u-name", m).value, email: $("#u-email", m).value || null } }); toast("Saved"); closeModal(); t.reload(); };
        $("#u-susp", m).onclick = async () => { const r = await confirmBox(u.is_suspended ? "Unsuspend this account?" : "Suspend this account? All sessions will be revoked.", "Confirm", !u.is_suspended); if (r === null) return; await api(`/admin/users/${u.id}/suspend/`, { method: "POST", body: { suspend: !u.is_suspended, reason: r === true ? "" : r } }); closeModal(); t.reload(); };
      }); } });
    $("#apply").onclick = () => t.setParams({ q: $("#q").value, role: $("#role").value });
    $("#new-user").onclick = () => modal(`<h2>Create login</h2><div class="form"><label>Phone</label><input id="n-phone" placeholder="+91…"><label>Full name</label><input id="n-name"><label>Role</label><select id="n-role"><option value="partner">Partner</option><option value="admin">Admin</option></select><label>Password</label><input id="n-pass" type="password"></div><div class="row"><button class="btn" id="n-cancel">Cancel</button><button class="btn primary" id="n-ok">Create</button></div>`, m => { $("#n-cancel", m).onclick = closeModal; $("#n-ok", m).onclick = async () => { await api("/admin/users/create/", { method: "POST", body: { phone: $("#n-phone", m).value, full_name: $("#n-name", m).value, role: $("#n-role", m).value, password: $("#n-pass", m).value } }); toast("Created"); closeModal(); t.reload(); }; });
  };

  const docsHtml = (docs, base) => docs.length ? docs.map(d => `<div class="card" style="padding:10px"><div><b>${esc(d.doc_type.replace(/_/g, " "))}</b> ${chip(d.status)} <span class="muted">${esc(d.number || "")}</span></div><div class="docs" style="margin:8px 0">${/\.pdf($|\?)/i.test(d.file) ? `<a class="btn sm" href="${esc(d.file)}" target="_blank">Open PDF</a>` : `<a href="${esc(d.file)}" target="_blank"><img src="${esc(d.file)}" style="max-height:160px"></a>`}</div>${d.status !== "verified" ? `<button class="btn sm primary" data-v="${d.id}" data-a="1">Verify</button> <button class="btn sm danger" data-v="${d.id}" data-a="0">Reject</button>` : ""}${d.rejection_reason ? `<div class="muted">${esc(d.rejection_reason)}</div>` : ""}</div>`).join("") : '<div class="muted">No documents uploaded</div>';
  function bindDocButtons(m, base, reload) { m.querySelectorAll("[data-v]").forEach(b => b.onclick = async () => { const approve = b.dataset.a === "1"; const reason = approve ? "" : await confirmBox("Reject document", "Reject", true); if (reason === null) return; await api(`${base}/documents/${b.dataset.v}/verify/`, { method: "POST", body: { approve, reason } }); toast("Updated"); closeModal(); reload(); }); }

  pages["/restaurants"] = async () => {
    title.textContent = "Restaurants";
    view.innerHTML = `<div class="card"><div class="tabs" id="tabs"><button data-s="" class="active">All</button><button data-s="pending">Pending</button><button data-s="approved">Approved</button><button data-s="rejected">Rejected</button><button data-s="suspended">Suspended</button></div><div class="toolbar" style="margin-top:12px"><input id="q" placeholder="Name, owner phone, city"><button class="btn" id="apply">Search</button></div><div id="tbl" style="margin-top:14px"></div></div>`;
    const cols = [{ label: "Restaurant", render: r => `<b>${esc(r.name)}</b><br><span class="muted">${esc(r.city)} · ${esc(r.restaurant_type)}</span>` }, { label: "Owner", render: r => `${esc(r.owner_name)}<br><span class="muted">${esc(r.owner_phone)}</span>` }, { label: "Rating", render: r => `★ ${r.rating_avg} (${r.rating_count})` }, { label: "Orders", key: "total_orders" }, { label: "Commission", render: r => r.effective_commission_percent + "%" }, { label: "Open", render: r => r.is_open ? "🟢" : "⚪" }, { label: "Status", render: r => chip(r.status) }];
    let status = "";
    const t = await pagedTable($("#tbl"), "/admin/restaurants/", cols, { onRow: r => openRestaurant(r.id, () => t.reload()) });
    $("#tabs").querySelectorAll("button").forEach(b => b.onclick = () => { $("#tabs .active").classList.remove("active"); b.classList.add("active"); status = b.dataset.s; t.setParams({ status, q: $("#q").value }); });
    $("#apply").onclick = () => t.setParams({ status, q: $("#q").value });
  };
  async function openRestaurant(id, reload) {
    const r = await api(`/admin/restaurants/${id}/`);
    modal(`<h2>${esc(r.name)} ${chip(r.status)}</h2><div class="grid grid-2"><div class="kv"><b>Owner</b><span>${esc(r.owner_name)} · ${esc(r.owner_phone)}</span><b>Phone</b><span>${esc(r.phone)}</span><b>Address</b><span>${esc(r.address_line1)}, ${esc(r.city)} ${esc(r.postal_code)}</span><b>FSSAI</b><span>${esc(r.fssai_number || "—")}</span><b>GST</b><span>${esc(r.gst_number || "—")}</span><b>PAN</b><span>${esc(r.pan_number || "—")}</span><b>Bank</b><span>${esc(r.bank_account_name)} ${esc(r.bank_account_number)} ${esc(r.bank_ifsc)} ${esc(r.upi_id)}</span><b>Min order</b><span>${fmtMoney(r.min_order_amount)}</span><b>Prep time</b><span>${r.avg_preparation_minutes} min</span><b>Rating</b><span>★ ${r.rating_avg} (${r.rating_count}) · ${r.total_orders} orders</span>${r.rejection_reason ? `<b>Note</b><span>${esc(r.rejection_reason)}</span>` : ""}</div>
      <div class="form"><label>Commission % (blank = platform default)</label><input id="r-comm" value="${r.commission_percent ?? ""}"><label><input type="checkbox" id="r-feat" style="width:auto" ${r.is_featured ? "checked" : ""}> Featured</label><label><input type="checkbox" id="r-promo" style="width:auto" ${r.is_promoted ? "checked" : ""}> Promoted (top of lists)</label><button class="btn sm" id="r-save" style="margin-top:8px">Save</button></div></div>
      <h3 style="margin-top:14px">Documents</h3><div class="grid grid-3">${docsHtml(r.documents)}</div>
      <div class="row">${r.status !== "approved" ? '<button class="btn primary" id="r-approve">Approve</button><button class="btn danger" id="r-reject">Reject</button>' : '<button class="btn danger" id="r-suspend">Suspend</button>'}${r.status === "suspended" ? '<button class="btn" id="r-unsuspend">Reactivate</button>' : ""}<button class="btn" id="r-close">Close</button></div>`, m => {
      $("#r-close", m).onclick = closeModal; bindDocButtons(m, `/admin/restaurants/${id}`, () => openRestaurant(id, reload));
      $("#r-save", m).onclick = async () => { await api(`/admin/restaurants/${id}/`, { method: "PATCH", body: { commission_percent: $("#r-comm", m).value || null, is_featured: $("#r-feat", m).checked, is_promoted: $("#r-promo", m).checked } }); toast("Saved"); reload(); };
      const dec = async (approve) => { const reason = approve ? "" : await confirmBox("Rejection reason (sent to the owner)", "Reject", true); if (reason === null) return; await api(`/admin/restaurants/${id}/decide/`, { method: "POST", body: { approve, reason } }); toast(approve ? "Approved" : "Rejected"); closeModal(); reload(); };
      const a = $("#r-approve", m); if (a) a.onclick = () => dec(true); const rj = $("#r-reject", m); if (rj) rj.onclick = () => dec(false);
      const su = $("#r-suspend", m); if (su) su.onclick = async () => { const reason = await confirmBox("Suspend restaurant?", "Suspend", true); if (reason === null) return; await api(`/admin/restaurants/${id}/suspend/`, { method: "POST", body: { suspend: true, reason } }); closeModal(); reload(); };
      const un = $("#r-unsuspend", m); if (un) un.onclick = async () => { await api(`/admin/restaurants/${id}/suspend/`, { method: "POST", body: { suspend: false } }); closeModal(); reload(); };
    });
  }

  pages["/riders"] = async () => {
    title.textContent = "Riders";
    view.innerHTML = `<div class="card"><div class="tabs" id="tabs"><button data-s="" class="active">All</button><button data-s="pending">Pending</button><button data-s="approved">Approved</button><button data-s="rejected">Rejected</button><button data-s="suspended">Suspended</button></div><div class="toolbar" style="margin-top:12px"><input id="q" placeholder="Name, phone, vehicle"><label><input type="checkbox" id="online"> Online only</label><button class="btn" id="apply">Search</button></div><div id="tbl" style="margin-top:14px"></div></div>`;
    const cols = [{ label: "Rider", render: r => `<b>${esc(r.full_name || "—")}</b><br><span class="muted">${esc(r.phone)} · ${esc(r.city || "")}</span>` }, { label: "Vehicle", render: r => `${esc(r.vehicle_type)} ${esc(r.vehicle_number)}` }, { label: "Rating", render: r => `★ ${r.rating_avg}` }, { label: "Deliveries", key: "total_deliveries" }, { label: "Wallet", render: r => fmtMoney(r.wallet_balance) }, { label: "Online", render: r => r.is_online ? (r.is_on_delivery ? "🔵 delivering" : "🟢 online") : "⚪" }, { label: "Status", render: r => chip(r.status) }];
    let status = "";
    const t = await pagedTable($("#tbl"), "/admin/riders/", cols, { onRow: r => openRider(r.id, () => t.reload()) });
    $("#tabs").querySelectorAll("button").forEach(b => b.onclick = () => { $("#tabs .active").classList.remove("active"); b.classList.add("active"); status = b.dataset.s; t.setParams({ status, q: $("#q").value, online: $("#online").checked ? 1 : "" }); });
    $("#apply").onclick = () => t.setParams({ status, q: $("#q").value, online: $("#online").checked ? 1 : "" });
  };
  async function openRider(id, reload) {
    const [r, e] = await Promise.all([api(`/admin/riders/${id}/`), api(`/admin/riders/${id}/earnings/`)]);
    modal(`<h2>${esc(r.full_name || r.phone)} ${chip(r.status)} ${r.is_suspended ? chip("suspended") : ""}</h2><div class="grid grid-2"><div class="kv"><b>Phone</b><span>${esc(r.phone)}</span><b>City</b><span>${esc(r.city || "—")}</span><b>Address</b><span>${esc(r.address || "—")}</span><b>Vehicle</b><span>${esc(r.vehicle_type)} ${esc(r.vehicle_number)}</span><b>DL</b><span>${esc(r.driving_licence_number || "—")} (exp ${r.driving_licence_expiry || "—"})</span><b>Aadhaar</b><span>XXXX ${esc(r.aadhaar_last4 || "")}</span><b>PAN</b><span>${esc(r.pan_number || "—")}</span><b>Bank</b><span>${esc(r.bank_account_name)} ${esc(r.bank_account_number)} ${esc(r.bank_ifsc)} ${esc(r.upi_id)}</span><b>Emergency</b><span>${esc(r.emergency_contact_name)} ${esc(r.emergency_contact_phone)}</span><b>Stats</b><span>★ ${r.rating_avg} · ${r.total_deliveries} deliveries · ${r.total_rejections} rejections</span><b>Wallet</b><span>${fmtMoney(r.wallet_balance)} (COD in hand ${fmtMoney(r.cod_cash_in_hand)})</span>${r.rejection_reason ? `<b>Note</b><span>${esc(r.rejection_reason)}</span>` : ""}</div>
      <div><div class="grid grid-3"><div class="card"><div class="muted">Today</div><div class="big">${fmtMoney(e.summary.today_earnings)}</div><span class="muted">${e.summary.today_deliveries} deliveries</span></div><div class="card"><div class="muted">This week</div><div class="big">${fmtMoney(e.summary.week_earnings)}</div></div><div class="card"><div class="muted">This month</div><div class="big">${fmtMoney(e.summary.month_earnings)}</div></div></div>${r.photo ? `<img src="${esc(r.photo)}" style="width:90px;height:90px;border-radius:50%;object-fit:cover;margin-top:10px">` : ""}</div></div>
      <h3 style="margin-top:14px">Documents</h3><div class="grid grid-3">${docsHtml(r.documents)}</div>
      <div class="row">${r.status !== "approved" ? '<button class="btn primary" id="x-approve">Approve</button><button class="btn danger" id="x-reject">Reject</button>' : '<button class="btn danger" id="x-suspend">Suspend</button>'}${r.status === "suspended" ? '<button class="btn" id="x-unsuspend">Reactivate</button>' : ""}<button class="btn" id="x-close">Close</button></div>`, m => {
      $("#x-close", m).onclick = closeModal; bindDocButtons(m, `/admin/riders/${id}`, () => openRider(id, reload));
      const dec = async approve => { const reason = approve ? "" : await confirmBox("Rejection reason (sent to the rider)", "Reject", true); if (reason === null) return; await api(`/admin/riders/${id}/decide/`, { method: "POST", body: { approve, reason } }); closeModal(); reload(); };
      const a = $("#x-approve", m); if (a) a.onclick = () => dec(true); const rj = $("#x-reject", m); if (rj) rj.onclick = () => dec(false);
      const su = $("#x-suspend", m); if (su) su.onclick = async () => { const reason = await confirmBox("Suspend rider?", "Suspend", true); if (reason === null) return; await api(`/admin/riders/${id}/suspend/`, { method: "POST", body: { suspend: true, reason } }); closeModal(); reload(); };
      const un = $("#x-unsuspend", m); if (un) un.onclick = async () => { await api(`/admin/riders/${id}/suspend/`, { method: "POST", body: { suspend: false } }); closeModal(); reload(); };
    });
  }

  pages["/finance"] = async () => {
    title.textContent = "Finance";
    view.innerHTML = `<div class="card"><div class="tabs" id="tabs"><button data-t="settlements" class="active">Restaurant settlements</button><button data-t="payouts">Rider payouts</button><button data-t="payments">Payments</button><button data-t="refunds">Refunds</button></div><div id="body" style="margin-top:14px"></div></div>`;
    const body = $("#body");
    const tabs = {
      settlements: async () => {
        body.innerHTML = `<div class="toolbar"><select id="st"><option value="">All</option><option value="pending">Pending</option><option value="paid">Paid</option></select><button class="btn" id="apply">Filter</button><button class="btn primary" id="gen" style="margin-left:auto">Generate last week's settlements</button></div><div id="tbl" style="margin-top:12px"></div>`;
        const cols = [{ label: "Restaurant", key: "restaurant_name" }, { label: "Period", render: r => `${r.period_start} → ${r.period_end}` }, { label: "Orders", key: "order_count" }, { label: "Gross", render: r => fmtMoney(r.gross_sales) }, { label: "Commission", render: r => fmtMoney(r.commission_amount) }, { label: "Deductions", render: r => fmtMoney(Number(r.coupon_deductions) + Number(r.refund_deductions)) }, { label: "Net payable", render: r => `<b>${fmtMoney(r.net_payable)}</b>` }, { label: "Status", render: r => chip(r.status) }];
        const t = await pagedTable($("#tbl", body), "/admin/settlements/", cols, { onRow: async st => { const lines = await api(`/admin/settlements/${st.id}/lines/`); modal(`<h2>${esc(st.restaurant_name)} · ${st.period_start} → ${st.period_end} ${chip(st.status)}</h2><table><tr><th>Order</th><th>Delivered</th><th>Gross</th><th>Commission</th><th>Net</th></tr>${lines.map(l => `<tr><td>${esc(l.order_number)}</td><td>${fmtDT(l.delivered_at)}</td><td>${fmtMoney(l.gross)}</td><td>${fmtMoney(l.commission)}</td><td>${fmtMoney(l.net)}</td></tr>`).join("")}</table><div class="row">${st.status === "pending" ? '<div class="form" style="flex:1"><input id="s-ref" placeholder="Bank/UPI reference"></div><button class="btn primary" id="s-paid">Mark paid</button>' : `<span class="muted">Ref: ${esc(st.reference)}</span>`}<button class="btn" id="s-close">Close</button></div>`, m => { $("#s-close", m).onclick = closeModal; const p = $("#s-paid", m); if (p) p.onclick = async () => { await api(`/admin/settlements/${st.id}/mark-paid/`, { method: "POST", body: { reference: $("#s-ref", m).value } }); toast("Marked paid"); closeModal(); t.reload(); }; }); } });
        $("#apply", body).onclick = () => t.setParams({ status: $("#st", body).value });
        $("#gen", body).onclick = async () => { const r = await api("/admin/settlements/generate/", { method: "POST", body: {} }); toast(`Generated ${r.generated} settlements`); t.reload(); };
      },
      payouts: async () => {
        body.innerHTML = `<div id="tbl"></div>`;
        const cols = [{ label: "Rider", render: r => esc(r.rider_name || r.rider) }, { label: "Amount", render: r => fmtMoney(r.amount) }, { label: "Requested", render: r => fmtDT(r.created_at) }, { label: "Status", render: r => chip(r.status) }, { label: "Ref", key: "reference" }];
        const t = await pagedTable($("#tbl", body), "/admin/rider-payouts/", cols, { onRow: p => { if (p.status !== "requested") return; modal(`<h2>Process payout ${fmtMoney(p.amount)}</h2><div class="form"><label>Transaction reference</label><input id="p-ref"><label>Note</label><input id="p-note"></div><div class="row"><button class="btn danger" id="p-rej">Reject</button><button class="btn" id="p-close">Close</button><button class="btn primary" id="p-paid">Mark paid</button></div>`, m => { $("#p-close", m).onclick = closeModal; const go = async status => { await api(`/admin/rider-payouts/${p.id}/process/`, { method: "POST", body: { status, reference: $("#p-ref", m).value, note: $("#p-note", m).value } }); closeModal(); t.reload(); }; $("#p-paid", m).onclick = () => go("paid"); $("#p-rej", m).onclick = () => go("rejected"); }); } });
      },
      payments: async () => { const rows = await api("/admin/payments/?kind=payments"); body.innerHTML = `<table><tr><th>Order</th><th>Method</th><th>Amount</th><th>Status</th><th>Provider id</th><th>When</th></tr>${rows.map(p => `<tr><td>${esc(p.order_number)}</td><td>${esc(p.method)}</td><td>${fmtMoney(p.amount)}</td><td>${chip(p.status)}</td><td class="mono">${esc(p.provider_payment_id || p.provider_order_id || "")}</td><td>${fmtDT(p.created_at)}</td></tr>`).join("")}</table>`; },
      refunds: async () => { const rows = await api("/admin/payments/?kind=refunds"); body.innerHTML = `<table><tr><th>Order</th><th>Amount</th><th>Reason</th><th>Status</th><th>When</th></tr>${rows.map(p => `<tr><td>${esc(p.order_number)}</td><td>${fmtMoney(p.amount)}</td><td>${esc(p.reason)}</td><td>${chip(p.status)}</td><td>${fmtDT(p.created_at)}</td></tr>`).join("") || '<tr><td class="empty" colspan="5">No refunds</td></tr>'}</table>`; },
    };
    $("#tabs").querySelectorAll("button").forEach(b => b.onclick = () => { $("#tabs .active").classList.remove("active"); b.classList.add("active"); tabs[b.dataset.t](); });
    tabs.settlements();
  };

  pages["/promotions"] = async () => {
    title.textContent = "Promotions";
    view.innerHTML = `<div class="card"><div class="tabs" id="tabs"><button data-t="coupons" class="active">Coupons</button><button data-t="banners">Banners</button><button data-t="campaigns">Push campaigns</button></div><div id="body" style="margin-top:14px"></div></div>`;
    const body = $("#body");
    const couponForm = (c = {}) => `<div class="form grid grid-2"><div><label>Code</label><input id="c-code" value="${esc(c.code || "")}"><label>Title</label><input id="c-title" value="${esc(c.title || "")}"><label>Description</label><input id="c-desc" value="${esc(c.description || "")}"><label>Type</label><select id="c-type"><option value="percent" ${c.discount_type === "percent" ? "selected" : ""}>Percent</option><option value="fixed" ${c.discount_type === "fixed" ? "selected" : ""}>Fixed</option><option value="free_delivery" ${c.discount_type === "free_delivery" ? "selected" : ""}>Free delivery</option></select><label>Value</label><input id="c-val" value="${c.value ?? 10}"></div><div><label>Min order</label><input id="c-min" value="${c.min_order_amount ?? 0}"><label>Max discount (percent only)</label><input id="c-max" value="${c.max_discount ?? ""}"><label>Usage limit total (blank = unlimited)</label><input id="c-lim" value="${c.usage_limit_total ?? ""}"><label>Per-user limit</label><input id="c-per" value="${c.usage_limit_per_user ?? 1}"><label>Expires at</label><input id="c-exp" type="datetime-local" value="${c.expires_at ? c.expires_at.slice(0, 16) : ""}"><label>Restaurant ID (blank = platform-wide)</label><input id="c-rest" value="${c.restaurant || ""}"><label><input type="checkbox" id="c-first" style="width:auto" ${c.first_order_only ? "checked" : ""}> First order only</label><label><input type="checkbox" id="c-fund" style="width:auto" ${c.funded_by_restaurant ? "checked" : ""}> Funded by restaurant</label><label><input type="checkbox" id="c-active" style="width:auto" ${c.is_active !== false ? "checked" : ""}> Active</label><label><input type="checkbox" id="c-public" style="width:auto" ${c.is_public !== false ? "checked" : ""}> Visible in app</label></div></div>`;
    const readCoupon = m => ({ code: $("#c-code", m).value, title: $("#c-title", m).value, description: $("#c-desc", m).value, discount_type: $("#c-type", m).value, value: $("#c-val", m).value, min_order_amount: $("#c-min", m).value || 0, max_discount: $("#c-max", m).value || null, usage_limit_total: $("#c-lim", m).value || null, usage_limit_per_user: $("#c-per", m).value || 1, expires_at: $("#c-exp", m).value || null, restaurant: $("#c-rest", m).value || null, first_order_only: $("#c-first", m).checked, funded_by_restaurant: $("#c-fund", m).checked, is_active: $("#c-active", m).checked, is_public: $("#c-public", m).checked });
    const tabs = {
      coupons: async () => {
        body.innerHTML = `<div class="toolbar"><button class="btn primary" id="new">+ New coupon</button></div><div id="tbl" style="margin-top:12px"></div>`;
        const cols = [{ label: "Code", render: r => `<b>${esc(r.code)}</b><br><span class="muted">${esc(r.title)}</span>` }, { label: "Discount", render: r => r.discount_type === "percent" ? `${r.value}% (max ${r.max_discount ? fmtMoney(r.max_discount) : "—"})` : r.discount_type === "fixed" ? fmtMoney(r.value) : "Free delivery" }, { label: "Min order", render: r => fmtMoney(r.min_order_amount) }, { label: "Used", render: r => `${r.used_count}${r.usage_limit_total ? " / " + r.usage_limit_total : ""}` }, { label: "Expires", render: r => fmtDT(r.expires_at) }, { label: "Scope", render: r => r.restaurant ? "Restaurant" : "Platform" }, { label: "Active", render: r => r.is_active ? "✅" : "⛔" }];
        const t = await pagedTable($("#tbl", body), "/admin/coupons/", cols, { onRow: c => modal(`<h2>Edit ${esc(c.code)}</h2>${couponForm(c)}<div class="row"><button class="btn danger" id="c-del">Delete</button><button class="btn" id="c-cancel">Cancel</button><button class="btn primary" id="c-save">Save</button></div>`, m => { $("#c-cancel", m).onclick = closeModal; $("#c-save", m).onclick = async () => { await api(`/admin/coupons/${c.id}/`, { method: "PATCH", body: readCoupon(m) }); toast("Saved"); closeModal(); t.reload(); }; $("#c-del", m).onclick = async () => { if (!await confirmBox("Delete coupon?", "Delete")) return; await api(`/admin/coupons/${c.id}/`, { method: "DELETE" }); closeModal(); t.reload(); }; }) });
        $("#new", body).onclick = () => modal(`<h2>New coupon</h2>${couponForm()}<div class="row"><button class="btn" id="c-cancel">Cancel</button><button class="btn primary" id="c-save">Create</button></div>`, m => { $("#c-cancel", m).onclick = closeModal; $("#c-save", m).onclick = async () => { await api("/admin/coupons/", { method: "POST", body: readCoupon(m) }); toast("Created"); closeModal(); t.reload(); }; });
      },
      banners: async () => {
        body.innerHTML = `<div class="toolbar"><button class="btn primary" id="new">+ New banner</button></div><div id="tbl" style="margin-top:12px"></div>`;
        const cols = [{ label: "Image", render: r => `<img src="${esc(r.image)}" style="height:48px;border-radius:8px">` }, { label: "Title", render: r => `<b>${esc(r.title)}</b><br><span class="muted">${esc(r.subtitle || "")}</span>` }, { label: "Coupon", key: "coupon_code" }, { label: "City", render: r => esc(r.city || "All") }, { label: "Active", render: r => r.is_active ? "✅" : "⛔" }];
        const t = await pagedTable($("#tbl", body), "/admin/banners/", cols, { onRow: async b => { if (await confirmBox(`Delete banner "${b.title}"?`, "Delete")) { await api(`/admin/banners/${b.id}/`, { method: "DELETE" }); t.reload(); } } });
        $("#new", body).onclick = () => modal(`<h2>New banner</h2><div class="form"><label>Title</label><input id="b-title"><label>Subtitle</label><input id="b-sub"><label>Image</label><input id="b-img" type="file" accept="image/*"><label>Coupon code</label><input id="b-code"><label>Restaurant ID (optional)</label><input id="b-rest"><label>City (blank = all)</label><input id="b-city"></div><div class="row"><button class="btn" id="b-cancel">Cancel</button><button class="btn primary" id="b-save">Create</button></div>`, m => { $("#b-cancel", m).onclick = closeModal; $("#b-save", m).onclick = async () => { const fd = new FormData(); fd.append("title", $("#b-title", m).value); fd.append("subtitle", $("#b-sub", m).value); fd.append("image", $("#b-img", m).files[0]); fd.append("coupon_code", $("#b-code", m).value); if ($("#b-rest", m).value) fd.append("restaurant", $("#b-rest", m).value); fd.append("city", $("#b-city", m).value); await api("/admin/banners/", { method: "POST", body: fd }); toast("Created"); closeModal(); t.reload(); }; });
      },
      campaigns: async () => {
        const rows = await api("/admin/campaigns/");
        body.innerHTML = `<div class="grid grid-21"><div><table><tr><th>Title</th><th>Audience</th><th>City</th><th>Sent</th><th>When</th></tr>${rows.map(c => `<tr><td><b>${esc(c.title)}</b><br><span class="muted">${esc(c.body)}</span></td><td>${esc(c.audience)}</td><td>${esc(c.city || "All")}</td><td>${c.sent_count}</td><td>${fmtDT(c.sent_at)}</td></tr>`).join("") || '<tr><td class="empty" colspan="5">No campaigns yet</td></tr>'}</table></div><div class="card form"><h3>Send push campaign</h3><label>Title</label><input id="k-title"><label>Message</label><textarea id="k-body" rows="3"></textarea><label>Deep link (optional)</label><input id="k-link" placeholder="localrush://restaurant/slug"><label>Audience</label><select id="k-aud"><option value="customer">Customers</option><option value="rider">Riders</option><option value="partner">Partners</option></select><label>City (blank = all)</label><input id="k-city"><button class="btn primary" id="k-send" style="margin-top:12px">Send now</button></div></div>`;
        $("#k-send", body).onclick = async () => { if (!await confirmBox("Send this push to all matching users?", "Send")) return; await api("/admin/campaigns/", { method: "POST", body: { title: $("#k-title", body).value, body: $("#k-body", body).value, deep_link: $("#k-link", body).value, audience: $("#k-aud", body).value, city: $("#k-city", body).value } }); toast("Campaign queued"); tabs.campaigns(); };
      },
    };
    $("#tabs").querySelectorAll("button").forEach(b => b.onclick = () => { $("#tabs .active").classList.remove("active"); b.classList.add("active"); tabs[b.dataset.t](); });
    tabs.coupons();
  };

  pages["/support"] = async () => {
    title.textContent = "Support";
    view.innerHTML = `<div class="card"><div class="tabs" id="tabs"><button data-s="open" class="active">Open</button><button data-s="in_progress">In progress</button><button data-s="waiting">Waiting</button><button data-s="resolved">Resolved</button><button data-s="">All</button></div><div id="tbl" style="margin-top:14px"></div></div>`;
    const cols = [{ label: "Ticket", render: r => `<b>${esc(r.ticket_number)}</b><br><span class="muted">${fmtDT(r.created_at)}</span>` }, { label: "Category", render: r => esc(r.category.replace(/_/g, " ")) }, { label: "Subject", key: "subject" }, { label: "Order", render: r => esc(r.order_number || "—") }, { label: "Priority", render: r => chip(r.priority === "urgent" ? "rejected" : r.priority === "high" ? "pending" : "open").replace(/>.*</, `>${r.priority}<`) }, { label: "Status", render: r => chip(r.status) }];
    const t = await pagedTable($("#tbl"), "/admin/tickets/", cols, { params: { status: "open" }, onRow: tk => modal(`<h2>${esc(tk.ticket_number)} · ${esc(tk.subject)} ${chip(tk.status)}</h2><div class="muted">${esc(tk.category)} ${tk.order_number ? "· order " + esc(tk.order_number) : ""}</div><div style="margin:12px 0;max-height:300px;overflow:auto">${tk.messages.map(m => `<div style="padding:8px 10px;border-radius:10px;margin:6px 0;background:${m.is_staff_reply ? "var(--orange-soft)" : "var(--bg)"}"><b>${esc(m.sender_name)}</b> <span class="muted">${fmtDT(m.created_at)}</span><br>${esc(m.text)}${m.attachment ? `<br><a href="${esc(m.attachment)}" target="_blank">attachment</a>` : ""}</div>`).join("")}</div><div class="form"><label>Reply</label><textarea id="t-text" rows="3"></textarea><label>Set status</label><select id="t-status"><option value="waiting">Waiting for customer</option><option value="in_progress">In progress</option><option value="resolved">Resolved</option><option value="closed">Closed</option></select></div><div class="row">${tk.order ? `<button class="btn" id="t-order">Open order</button>` : ""}<button class="btn" id="t-close">Close</button><button class="btn primary" id="t-send">Send reply</button></div>`, m => { $("#t-close", m).onclick = closeModal; const o = $("#t-order", m); if (o) o.onclick = () => openOrder(tk.order); $("#t-send", m).onclick = async () => { await api(`/admin/tickets/${tk.id}/reply/`, { method: "POST", body: { text: $("#t-text", m).value, status: $("#t-status", m).value } }); toast("Reply sent"); closeModal(); t.reload(); }; }) });
    $("#tabs").querySelectorAll("button").forEach(b => b.onclick = () => { $("#tabs .active").classList.remove("active"); b.classList.add("active"); t.setParams({ status: b.dataset.s }); });
  };

  pages["/reports"] = async () => {
    title.textContent = "Reports & analytics";
    view.innerHTML = `<div class="card"><div class="toolbar"><select id="days"><option value="7">7 days</option><option value="30" selected>30 days</option><option value="90">90 days</option></select></div></div><div class="grid grid-2"><div class="card"><h3>Restaurant performance</h3><div id="r-perf"></div></div><div class="card"><h3>Rider performance</h3><div id="d-perf"></div></div><div class="card"><h3>Cancellations</h3><div id="canc"></div></div><div class="card"><h3>Customer retention</h3><div id="ret"></div></div><div class="card" style="grid-column:1/-1"><h3>Revenue & commission (daily ledger)</h3><div id="rev"></div></div></div>`;
    async function load() {
      const d = $("#days").value;
      const [rp, dp, c, r, rev] = await Promise.all(["restaurants", "riders", "cancellations", "retention", "revenue"].map(k => api(`/admin/reports/${k}/?days=${d}`)));
      $("#r-perf").innerHTML = `<table><tr><th>Restaurant</th><th>Orders</th><th>Delivered</th><th>Rejected</th><th>Sales</th><th>Commission</th></tr>${rp.map(x => `<tr><td>${esc(x.name)}<br><span class="muted">${esc(x.city)} · ★${x.rating_avg}</span></td><td>${x.orders}</td><td>${x.delivered}</td><td>${x.rejected}</td><td>${fmtMoney(x.sales)}</td><td>${fmtMoney(x.commission)}</td></tr>`).join("") || '<tr><td class="empty">No data</td></tr>'}</table>`;
      $("#d-perf").innerHTML = `<table><tr><th>Rider</th><th>Deliveries</th><th>Failed</th><th>Rejections</th><th>Earnings</th><th>Avg km</th></tr>${dp.map(x => `<tr><td>${esc(x.full_name)}<br><span class="muted">${esc(x.city || "")} · ★${x.rating_avg}</span></td><td>${x.deliveries}</td><td>${x.failed}</td><td>${x.total_rejections}</td><td>${fmtMoney(x.earnings)}</td><td>${Number(x.avg_km || 0).toFixed(1)}</td></tr>`).join("") || '<tr><td class="empty">No data</td></tr>'}</table>`;
      $("#canc").innerHTML = `<div class="big">${c.total}</div><div class="muted">cancelled / rejected / failed</div><table style="margin-top:10px"><tr><th>By</th><th>Count</th></tr>${c.by_actor.map(x => `<tr><td>${esc(x.cancelled_by || "—")}</td><td>${x.n}</td></tr>`).join("")}</table><table style="margin-top:10px"><tr><th>Top reasons</th><th></th></tr>${c.top_reasons.map(x => `<tr><td>${esc(x.cancellation_reason)}</td><td>${x.n}</td></tr>`).join("")}</table>`;
      $("#ret").innerHTML = `<div class="big">${r.repeat_rate}%</div><div class="muted">repeat customer rate</div><div class="kv" style="margin-top:10px"><b>Total customers</b><span>${r.total_customers}</span><b>Repeat (2+ orders)</b><span>${r.repeat_customers}</span><b>Single order</b><span>${r.single_order_customers}</span><b>Never ordered</b><span>${r.never_ordered}</span></div>`;
      $("#rev").innerHTML = `<table><tr><th>Date</th><th>Orders</th><th>GOV</th><th>Commission</th><th>Delivery fees</th><th>Platform fees</th><th>Coupon cost</th><th>Rider payouts</th><th>Refunds</th><th>Net</th></tr>${rev.map(x => `<tr><td>${x.date}</td><td>${x.orders}</td><td>${fmtMoney(x.gross_order_value)}</td><td>${fmtMoney(x.commission_revenue)}</td><td>${fmtMoney(x.delivery_fee_revenue)}</td><td>${fmtMoney(x.platform_fee_revenue)}</td><td>${fmtMoney(x.coupon_cost)}</td><td>${fmtMoney(x.rider_payout_cost)}</td><td>${fmtMoney(x.refunds)}</td><td><b>${fmtMoney(x.net_revenue)}</b></td></tr>`).join("") || '<tr><td class="empty" colspan="10">Ledger rolls up hourly</td></tr>'}</table>`;
    }
    $("#days").onchange = load; load();
  };

  pages["/settings"] = async () => {
    title.textContent = "Platform settings";
    const s = await api("/admin/settings/");
    const fields = [["default_commission_percent", "Default commission %"], ["gst_percent", "GST %"], ["platform_fee", "Platform fee (₹)"], ["base_delivery_fee", "Base delivery fee (₹)"], ["base_delivery_km", "Base km included"], ["per_km_delivery_fee", "Per extra km fee (₹)"], ["free_delivery_above", "Free delivery above (₹, 0=off)"], ["max_delivery_radius_km", "Max delivery radius (km)"], ["rider_base_pay", "Rider base pay (₹)"], ["rider_per_km_pay", "Rider per-km pay (₹)"], ["rider_search_radius_km", "Rider search radius (km)"], ["rider_offer_timeout_seconds", "Rider offer timeout (s)"], ["rider_max_concurrent_orders", "Max concurrent orders per rider"], ["restaurant_accept_timeout_minutes", "Restaurant accept timeout (min)"], ["customer_cancel_window_seconds", "Free cancel window after accept (s)"], ["cancellation_fee_after_accept", "Cancellation fee after accept (₹)"], ["cod_max_order_value", "COD max order value (₹)"], ["min_app_version_customer", "Min customer app version"], ["min_app_version_rider", "Min rider app version"], ["min_app_version_partner", "Min partner app version"], ["support_phone", "Support phone"], ["support_email", "Support email"]];
    view.innerHTML = `<div class="card form"><div class="grid grid-3">${fields.map(([k, l]) => `<div><label>${l}</label><input data-k="${k}" value="${esc(s[k] ?? "")}"></div>`).join("")}<div><label>Cash on delivery</label><select data-k="cod_enabled"><option value="true" ${s.cod_enabled ? "selected" : ""}>Enabled</option><option value="false" ${!s.cod_enabled ? "selected" : ""}>Disabled</option></select></div></div><div class="row"><button class="btn primary" id="save">Save settings</button></div></div><div class="card"><h3>Audit log</h3><div id="audit"></div></div>`;
    $("#save").onclick = async () => { const body = {}; view.querySelectorAll("[data-k]").forEach(i => body[i.dataset.k] = i.tagName === "SELECT" ? i.value === "true" : i.value); await api("/admin/settings/", { method: "PATCH", body }); toast("Settings saved"); };
    const logs = await api("/admin/audit-logs/");
    $("#audit").innerHTML = `<table><tr><th>When</th><th>Actor</th><th>Action</th><th>Target</th><th>Details</th></tr>${logs.map(l => `<tr><td>${fmtDT(l.created_at)}</td><td>${esc(l.actor)}</td><td>${esc(l.action)}</td><td class="mono">${esc(l.target_type)} ${esc(String(l.target_id).slice(0, 8))}</td><td class="mono muted">${esc(JSON.stringify(l.metadata)).slice(0, 120)}</td></tr>`).join("")}</table>`;
  };

  // ---------- router
  let currentCleanup = null, renderSeq = 0;
  async function render() {
    currentCleanup && currentCleanup(); currentCleanup = null;
    const seq = ++renderSeq;
    const [path, qs] = (location.hash.slice(1) || "/").split("?");
    const params = new URLSearchParams(qs || "");
    document.querySelectorAll("#nav a").forEach(a => a.classList.toggle("active", a.dataset.route === path));
    const page = pages[path] || pages["/"];
    view.innerHTML = '<div class="empty">Loading…</div>';
    try { await page(params, () => seq === renderSeq); } catch (e) { console.error(e); if (seq === renderSeq) view.innerHTML = `<div class="card empty">Failed to load: ${esc(e.message)}</div>`; }
  }
  $("#global-search").addEventListener("keydown", e => { if (e.key === "Enter") { location.hash = "#/orders"; setTimeout(() => { const q = $("#q"); if (q) { q.value = e.target.value; $("#apply").click(); } }, 300); } });
  window.addEventListener("hashchange", render);
  (async () => { await getToken(); connectWS(); render(); })();
})();
