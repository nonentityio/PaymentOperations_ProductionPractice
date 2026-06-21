const state = {
    payments: [],
    selectedPaymentId: null,
    refreshTimer: null,
    token: localStorage.getItem("paymentOpsToken") || ""
};

const els = {
    health: document.querySelector("#health"),
    refreshBtn: document.querySelector("#refreshBtn"),
    clientFilter: document.querySelector("#clientFilter"),
    paymentsTable: document.querySelector("#paymentsTable"),
    selectedStatus: document.querySelector("#selectedStatus"),
    detailBody: document.querySelector("#detailBody"),
    rowTemplate: document.querySelector("#paymentRowTemplate"),
    metricTotal: document.querySelector("#metricTotal"),
    metricSucceeded: document.querySelector("#metricSucceeded"),
    metricProcessing: document.querySelector("#metricProcessing"),
    metricFailed: document.querySelector("#metricFailed")
};

async function api(path) {
    if (!state.token) {
        askToken();
    }
    const response = await fetch(path, {
        method: "GET",
        headers: {
            Authorization: `Bearer ${state.token}`
        }
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
        if (response.status === 401) {
            localStorage.removeItem("paymentOpsToken");
            state.token = "";
        }
        throw new Error(payload.error || `HTTP ${response.status}`);
    }
    return payload;
}

function askToken() {
    const token = window.prompt("Введите PaymentOperations API token");
    if (!token) {
        throw new Error("API token не указан");
    }
    state.token = token.trim();
    localStorage.setItem("paymentOpsToken", state.token);
}

function formatDate(value) {
    if (!value) return "-";
    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    }).format(new Date(value));
}

function shortId(value) {
    return value ? value.slice(0, 8) : "-";
}

function money(payment) {
    return `${payment.amount} ${payment.currency}`;
}

function toast(message) {
    document.querySelector(".toast")?.remove();
    const node = document.createElement("div");
    node.className = "toast";
    node.textContent = message;
    document.body.appendChild(node);
    setTimeout(() => node.remove(), 3200);
}

async function checkHealth() {
    try {
        const health = await fetch("/health").then((res) => res.json());
        els.health.textContent = `API: ${health.status}`;
    } catch (_error) {
        els.health.textContent = "API: недоступен";
    }
}

async function loadPayments() {
    const clientId = els.clientFilter.value.trim();
    const query = clientId ? `?clientId=${encodeURIComponent(clientId)}` : "";
    const payload = await api(`/payments${query}`);
    state.payments = payload.items || [];
    renderMetrics();
    renderTable();

    if (state.selectedPaymentId) {
        await loadPaymentDetails(state.selectedPaymentId, false);
    }
}

function renderMetrics() {
    const total = state.payments.length;
    const succeeded = state.payments.filter((p) => p.status === "SUCCESS").length;
    const failed = state.payments.filter((p) => p.status === "FAILED").length;
    const processing = state.payments.filter((p) =>
        ["CREATED", "CHECK_REQUISITE", "CONFIRMED", "PROCESSING"].includes(p.status)
    ).length;

    els.metricTotal.textContent = total;
    els.metricSucceeded.textContent = succeeded;
    els.metricProcessing.textContent = processing;
    els.metricFailed.textContent = failed;
}

function renderTable() {
    els.paymentsTable.replaceChildren();

    if (state.payments.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 7;
        cell.textContent = "Платежи не найдены";
        row.appendChild(cell);
        els.paymentsTable.appendChild(row);
        return;
    }

    state.payments.forEach((payment) => {
        const row = els.rowTemplate.content.firstElementChild.cloneNode(true);
        row.classList.toggle("selected", payment.paymentId === state.selectedPaymentId);
        row.querySelector(".status").textContent = payment.status;
        row.querySelector(".status").classList.add(payment.status);
        row.querySelector(".payment-id").textContent = shortId(payment.paymentId);
        row.querySelector(".payment-id").title = payment.paymentId;
        row.querySelector(".client").textContent = payment.clientId;
        row.querySelector(".category").textContent = payment.serviceCategory || "TRANSFER";
        row.querySelector(".category").classList.add(payment.serviceCategory || "TRANSFER");
        row.querySelector(".amount").textContent = money(payment);
        row.querySelector(".provider").textContent = payment.providerId;
        row.querySelector(".created").textContent = formatDate(payment.createdAt);
        row.addEventListener("click", () => selectPayment(payment.paymentId));
        els.paymentsTable.appendChild(row);
    });
}

async function selectPayment(paymentId) {
    state.selectedPaymentId = paymentId;
    renderTable();
    await loadPaymentDetails(paymentId, true);
}

async function loadPaymentDetails(paymentId, showLoading) {
    if (showLoading) {
        els.detailBody.className = "empty-state";
        els.detailBody.textContent = "Загрузка...";
    }

    try {
        const payment = await api(`/payments/${paymentId}`);
        renderDetails(payment);
    } catch (error) {
        els.detailBody.className = "empty-state";
        els.detailBody.textContent = error.message;
        els.selectedStatus.textContent = "Ошибка";
    }
}

function renderDetails(payment) {
    els.selectedStatus.textContent = payment.status;
    els.detailBody.className = "detail-body";
    els.detailBody.replaceChildren();

    const rail = renderStatusRail(payment.status);
    const dl = document.createElement("dl");
    dl.className = "kv";
    addKv(dl, "payment_id", payment.paymentId);
    addKv(dl, "client_id", payment.clientId);
    addKv(dl, "provider_id", payment.providerId);
    addKv(dl, "service_category", payment.serviceCategory || "TRANSFER");
    addKv(dl, "amount", money(payment));
    addKv(dl, "requisite", payment.requisite);
    addKv(dl, "created_at", formatDate(payment.createdAt));
    addKv(dl, "updated_at", formatDate(payment.updatedAt));
    addKv(dl, "failure", payment.failureReason || "-");

    const timeline = document.createElement("div");
    timeline.className = "timeline";
    const title = document.createElement("h3");
    title.textContent = "История статусов";
    timeline.appendChild(title);

    uniqueHistory(payment.history || []).forEach((item) => {
        const step = document.createElement("div");
        step.className = "step";
        const label = document.createElement("strong");
        label.textContent = `${item.oldStatus || "START"} -> ${item.newStatus}`;
        const time = document.createElement("span");
        time.textContent = formatDate(item.changedAt);
        step.append(label, time);
        timeline.appendChild(step);
    });

    els.detailBody.append(rail, dl, timeline);
}

function renderStatusRail(status) {
    const stages = ["CREATED", "CHECK_REQUISITE", "CONFIRMED", "PROCESSING", status === "FAILED" ? "FAILED" : status === "CANCELLED" ? "CANCELLED" : "SUCCESS"];
    const activeIndex = stages.indexOf(status);
    const rail = document.createElement("div");
    rail.className = "rail";

    stages.forEach((stage, index) => {
        const item = document.createElement("div");
        item.className = "rail-step";
        if (index <= activeIndex || status === "SUCCESS" || status === "FAILED" || status === "CANCELLED") item.classList.add("passed");
        if (stage === status) item.classList.add("current");
        if (stage === "FAILED") item.classList.add("failed");
        if (stage === "CANCELLED") item.classList.add("failed");

        const dot = document.createElement("span");
        const label = document.createElement("strong");
        label.textContent = stage;
        item.append(dot, label);
        rail.appendChild(item);
    });

    return rail;
}

function uniqueHistory(history) {
    const seen = new Set();
    return history.filter((item) => {
        const key = `${item.oldStatus || ""}:${item.newStatus}:${item.changedAt}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

function addKv(parent, key, value) {
    const dt = document.createElement("dt");
    const dd = document.createElement("dd");
    dt.textContent = key;
    dd.textContent = value;
    parent.append(dt, dd);
}

function startPolling() {
    clearInterval(state.refreshTimer);
    state.refreshTimer = setInterval(() => {
        loadPayments().catch((error) => {
            els.health.textContent = error.message;
        });
    }, 1800);
}

function bindEvents() {
    els.refreshBtn.addEventListener("click", () => loadPayments().catch((error) => toast(error.message)));
    els.clientFilter.addEventListener("input", () => {
        clearTimeout(els.clientFilter._timer);
        els.clientFilter._timer = setTimeout(() => loadPayments().catch((error) => toast(error.message)), 300);
    });
}

async function init() {
    bindEvents();
    await checkHealth();
    await loadPayments().catch((error) => toast(error.message));
    startPolling();
}

init();
