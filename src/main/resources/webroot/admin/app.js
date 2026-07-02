const state = {
    payments: [],
    selectedPaymentId: null,
    categoryFilter: "ALL",
    statusFilter: "ALL",
    refreshTimer: null,
    token: sessionStorage.getItem("paymentOpsToken") || ""
};

const els = {
    health: document.querySelector("#health"),
    refreshBtn: document.querySelector("#refreshBtn"),
    clientFilter: document.querySelector("#clientFilter"),
    categoryFilters: document.querySelector("#categoryFilters"),
    statusFilter: document.querySelector("#statusFilter"),
    prevPaymentBtn: document.querySelector("#prevPaymentBtn"),
    nextPaymentBtn: document.querySelector("#nextPaymentBtn"),
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
            sessionStorage.removeItem("paymentOpsToken");
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
    sessionStorage.setItem("paymentOpsToken", state.token);
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

function formatDuration(ms) {
    if (ms === null || ms === undefined) return "-";
    const value = Number(ms);
    if (!Number.isFinite(value)) return "-";
    if (value < 1000) return `${value} ms`;
    return `${(value / 1000).toFixed(2)} s`;
}

function shortId(value) {
    return value ? value.slice(0, 8) : "-";
}

function money(payment) {
    return `${payment.amount} ${payment.currency}`;
}

function categoryOf(payment) {
    return payment.serviceCategory || "TRANSFER";
}

function visiblePayments() {
    return state.payments.filter((payment) => {
        const categoryMatches = state.categoryFilter === "ALL" || categoryOf(payment) === state.categoryFilter;
        const statusMatches = state.statusFilter === "ALL" || payment.status === state.statusFilter;
        return categoryMatches && statusMatches;
    });
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
    if (state.selectedPaymentId && !visiblePayments().some((payment) => payment.paymentId === state.selectedPaymentId)) {
        clearSelectedPayment("Выберите платеж из текущей выборки");
    }
    renderCategoryFilters();
    renderMetrics();
    renderTable();

    if (state.selectedPaymentId) {
        await loadPaymentDetails(state.selectedPaymentId, false);
    }
}

function renderMetrics() {
    const payments = visiblePayments();
    const total = payments.length;
    const succeeded = payments.filter((p) => p.status === "SUCCESS").length;
    const failed = payments.filter((p) => p.status === "FAILED").length;
    const processing = payments.filter((p) =>
        ["CREATED", "CHECK_REQUISITE", "CONFIRMED", "PROCESSING"].includes(p.status)
    ).length;

    els.metricTotal.textContent = total;
    els.metricSucceeded.textContent = succeeded;
    els.metricProcessing.textContent = processing;
    els.metricFailed.textContent = failed;
}

function renderCategoryFilters() {
    const categories = ["ALL", "TRANSFER", "MOBILE_TOPUP", "UTILITY", "CARD_PAYMENT", "WALLET"];
    const counts = state.payments.reduce((acc, payment) => {
        const category = categoryOf(payment);
        acc[category] = (acc[category] || 0) + 1;
        return acc;
    }, { ALL: state.payments.length });

    els.categoryFilters.replaceChildren();
    categories.forEach((category) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "category-filter";
        button.classList.toggle("active", state.categoryFilter === category);
        button.textContent = `${category === "ALL" ? "Все" : category} ${counts[category] || 0}`;
        button.addEventListener("click", () => {
            state.categoryFilter = category;
            if (state.selectedPaymentId && !visiblePayments().some((payment) => payment.paymentId === state.selectedPaymentId)) {
                clearSelectedPayment("Выберите платеж из текущей категории");
            }
            renderCategoryFilters();
            renderMetrics();
            renderTable();
        });
        els.categoryFilters.appendChild(button);
    });
}

function renderTable() {
    els.paymentsTable.replaceChildren();
    const payments = visiblePayments();

    if (payments.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 7;
        cell.textContent = "Платежи в этой выборке не найдены";
        row.appendChild(cell);
        els.paymentsTable.appendChild(row);
        renderNavigation();
        return;
    }

    payments.forEach((payment) => {
        const row = els.rowTemplate.content.firstElementChild.cloneNode(true);
        row.classList.toggle("selected", payment.paymentId === state.selectedPaymentId);
        row.querySelector(".status").textContent = payment.status;
        row.querySelector(".status").classList.add(payment.status);
        row.querySelector(".payment-id").textContent = shortId(payment.paymentId);
        row.querySelector(".payment-id").title = payment.paymentId;
        row.querySelector(".client").textContent = payment.clientId;
        row.querySelector(".category").textContent = payment.serviceCategory || "TRANSFER";
        row.querySelector(".category").classList.add(payment.serviceCategory || "TRANSFER");
        row.querySelector(".category").title = "Показать только эту категорию";
        row.querySelector(".category").addEventListener("click", (event) => {
            event.stopPropagation();
            state.categoryFilter = categoryOf(payment);
            if (state.selectedPaymentId && !visiblePayments().some((item) => item.paymentId === state.selectedPaymentId)) {
                clearSelectedPayment("Выберите платеж из текущей категории");
            }
            renderCategoryFilters();
            renderMetrics();
            renderTable();
        });
        row.querySelector(".amount").textContent = money(payment);
        row.querySelector(".provider").textContent = payment.providerId;
        row.querySelector(".created").textContent = formatDate(payment.createdAt);
        row.addEventListener("click", () => selectPayment(payment.paymentId));
        els.paymentsTable.appendChild(row);
    });
    renderNavigation();
}

async function selectPayment(paymentId) {
    state.selectedPaymentId = paymentId;
    renderTable();
    await loadPaymentDetails(paymentId, true);
}

function clearSelectedPayment(message) {
    state.selectedPaymentId = null;
    els.selectedStatus.textContent = "Выберите платеж";
    els.detailBody.className = "empty-state";
    els.detailBody.textContent = message || "Здесь появятся реквизит, текущий статус и история переходов выбранного платежа.";
    renderNavigation();
}

function selectedIndex() {
    return visiblePayments().findIndex((payment) => payment.paymentId === state.selectedPaymentId);
}

function renderNavigation() {
    const payments = visiblePayments();
    const index = selectedIndex();
    els.prevPaymentBtn.disabled = index <= 0;
    els.nextPaymentBtn.disabled = index < 0 || index >= payments.length - 1;
}

async function moveSelection(offset) {
    const payments = visiblePayments();
    const index = selectedIndex();
    const nextIndex = index + offset;
    if (nextIndex < 0 || nextIndex >= payments.length) return;
    await selectPayment(payments[nextIndex].paymentId);
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

    const history = uniqueHistory(payment.history || []);
    const rail = renderStatusRail(payment.status, history);
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
    addKv(dl, "total_time", formatDuration(payment.totalDurationMs));
    addKv(dl, "failure", payment.failureReason || "-");

    const timeline = document.createElement("div");
    timeline.className = "timeline";
    const title = document.createElement("h3");
    title.textContent = "История статусов";
    timeline.appendChild(title);

    history.forEach((item) => {
        const step = document.createElement("div");
        step.className = "step";
        const label = document.createElement("strong");
        label.textContent = `${item.oldStatus || "START"} -> ${item.newStatus}`;
        const meta = document.createElement("span");
        meta.textContent = `${formatDate(item.changedAt)} · +${formatDuration(item.durationMs)}`;
        step.append(label, meta);
        timeline.appendChild(step);
    });

    els.detailBody.append(rail, dl, timeline);
}

function renderStatusRail(status, history) {
    const stages = ["CREATED", "CHECK_REQUISITE", "CONFIRMED", "PROCESSING", status === "FAILED" ? "FAILED" : status === "CANCELLED" ? "CANCELLED" : "SUCCESS"];
    const activeIndex = stages.indexOf(status);
    const durations = new Map(history.map((item) => [item.newStatus, item.durationMs]));
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
        const speed = document.createElement("small");
        speed.textContent = durations.has(stage) ? formatDuration(durations.get(stage)) : "-";
        item.append(dot, label, speed);
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
    els.prevPaymentBtn.addEventListener("click", () => moveSelection(-1).catch((error) => toast(error.message)));
    els.nextPaymentBtn.addEventListener("click", () => moveSelection(1).catch((error) => toast(error.message)));
    els.statusFilter.addEventListener("change", () => {
        state.statusFilter = els.statusFilter.value;
        if (state.selectedPaymentId && !visiblePayments().some((payment) => payment.paymentId === state.selectedPaymentId)) {
            clearSelectedPayment("Выберите платеж с текущим статусом");
        }
        renderMetrics();
        renderTable();
    });
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
