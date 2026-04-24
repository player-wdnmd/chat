const CLIENT_CONFIG_API_URL = "/api/config/public";
const PROFILE_API_URL = "/api/auth/me";
const LOGOUT_API_URL = "/api/auth/session";
const IMAGE_META_API_URL = "/api/images/meta";
const IMAGE_HISTORY_API_URL = "/api/images/history";
const IMAGE_GENERATIONS_API_URL = "/api/images/generations";
const IMAGE_EDITS_API_URL = "/api/images/edits";
const IMAGE_CHAT_COMPLETIONS_API_URL = "/api/images/chat/completions";

const clientConfig = {
    authTokenStorageKey: "",
    unlimitedPoints: 0
};

const elements = {
    accountNameText: document.getElementById("accountNameText"),
    accountPointsText: document.getElementById("accountPointsText"),
    logoutButton: document.getElementById("logoutButton"),
    showGenerateTab: document.getElementById("showGenerateTab"),
    showProcessTab: document.getElementById("showProcessTab"),
    operationCostText: document.getElementById("operationCostText"),
    constraintsText: document.getElementById("constraintsText"),
    historyCountText: document.getElementById("historyCountText"),
    historyList: document.getElementById("historyList"),
    clearHistoryButton: document.getElementById("clearHistoryButton"),
    panelTitleText: document.getElementById("panelTitleText"),
    generateForm: document.getElementById("generateForm"),
    generatePromptInput: document.getElementById("generatePromptInput"),
    generateSubmitButton: document.getElementById("generateSubmitButton"),
    processForm: document.getElementById("processForm"),
    processModeHint: document.getElementById("processModeHint"),
    processPromptInput: document.getElementById("processPromptInput"),
    processImageInput: document.getElementById("processImageInput"),
    localEditToggle: document.getElementById("localEditToggle"),
    maskField: document.getElementById("maskField"),
    processMaskInput: document.getElementById("processMaskInput"),
    processSubmitButton: document.getElementById("processSubmitButton"),
    emptyResult: document.getElementById("emptyResult"),
    loadingState: document.getElementById("loadingState"),
    loadingHintText: document.getElementById("loadingHintText"),
    resultContainer: document.getElementById("resultContainer"),
    resultImage: document.getElementById("resultImage"),
    resultPromptText: document.getElementById("resultPromptText"),
    resultInfoText: document.getElementById("resultInfoText"),
    downloadButton: document.getElementById("downloadButton"),
    imagePreviewModal: document.getElementById("imagePreviewModal"),
    closePreviewButton: document.getElementById("closePreviewButton"),
    previewImage: document.getElementById("previewImage")
};

const state = {
    token: "",
    user: null,
    imageMeta: {
        configured: false,
        operationCost: 0,
        maxUploadBytes: 0,
        allowedMimeTypes: [],
        rateLimitMaxRequests: 0,
        rateLimitWindowSeconds: 0
    },
    view: "generate",
    busy: false,
    statusText: "",
    resultImageUrl: "",
    resultPrompt: "",
    resultInfo: "",
    resultJson: "",
    previewOpen: false,
    history: [],
    restoreToken: 0,
    currentHistoryId: null,
    loadingStartedAt: 0,
    loadingTimerId: null
};

boot();

async function boot() {
    try {
        applyPublicConfig(await fetchPublicConfig());
    } catch (error) {
        window.location.replace("/");
        return;
    }

    state.token = loadStoredToken();
    if (!state.token) {
        window.location.replace("/");
        return;
    }

    bindEvents();

    try {
        const [user, imageMeta, history] = await Promise.all([fetchCurrentUser(), fetchImageMeta(), fetchImageHistory()]);
        state.user = user;
        state.imageMeta = normalizeImageMeta(imageMeta);
        state.history = normalizeImageHistoryList(history);
        render();
    } catch (error) {
        clearSession();
        window.location.replace("/");
    }
}

async function fetchPublicConfig() {
    const response = await fetch(CLIENT_CONFIG_API_URL, {
        headers: {Accept: "application/json"}
    });
    const data = await readResponseData(response);
    if (!response.ok) {
        throw new Error(data.message || "读取前端配置失败");
    }
    return data;
}

function applyPublicConfig(data) {
    clientConfig.authTokenStorageKey = data.authTokenStorageKey || "";
    clientConfig.unlimitedPoints = Number(data.unlimitedPoints) || 0;
}

function loadStoredToken() {
    if (!clientConfig.authTokenStorageKey) {
        return "";
    }
    return localStorage.getItem(clientConfig.authTokenStorageKey) || "";
}

function bindEvents() {
    elements.logoutButton.addEventListener("click", handleLogout);
    elements.showGenerateTab.addEventListener("click", () => switchView("generate"));
    elements.showProcessTab.addEventListener("click", () => switchView("process"));
    elements.generateForm.addEventListener("submit", handleGenerateSubmit);
    elements.processForm.addEventListener("submit", handleProcessSubmit);
    elements.localEditToggle.addEventListener("change", handleLocalEditToggleChange);
    elements.downloadButton.addEventListener("click", downloadCurrentImage);
    elements.resultImage.addEventListener("click", openImagePreview);
    elements.closePreviewButton.addEventListener("click", closeImagePreview);
    elements.imagePreviewModal.addEventListener("click", handlePreviewOverlayClick);
    elements.historyList.addEventListener("click", handleHistoryClick);
    elements.clearHistoryButton.addEventListener("click", handleClearHistory);
    document.addEventListener("keydown", handleDocumentKeydown);
}

async function handleLogout() {
    try {
        if (state.token) {
            await apiFetch(LOGOUT_API_URL, {method: "DELETE"});
        }
    } catch (error) {
        console.warn(error);
    } finally {
        clearSession();
        window.location.replace("/");
    }
}

function switchView(view) {
    state.restoreToken += 1;
    state.view = view;
    resetWorkspaceForView(view);
    state.statusText = "";
    render();
    focusActivePromptInput();
}

function clearSession() {
    if (!clientConfig.authTokenStorageKey) {
        return;
    }
    localStorage.removeItem(clientConfig.authTokenStorageKey);
}

async function apiFetch(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (!headers.has("Accept")) {
        headers.set("Accept", "application/json");
    }
    if (state.token) {
        headers.set("Authorization", `Bearer ${state.token}`);
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (response.status === 401) {
        clearSession();
        window.location.replace("/");
        throw new Error("登录已失效，请重新登录。");
    }

    return response;
}

async function readResponseData(response) {
    const raw = await response.text();
    if (!raw) {
        return {};
    }
    try {
        return JSON.parse(raw);
    } catch (error) {
        return {message: raw};
    }
}

async function fetchCurrentUser() {
    const response = await apiFetch(PROFILE_API_URL);
    const data = await readResponseData(response);
    if (!response.ok) {
        throw new Error(data.message || "读取当前用户失败");
    }
    return data;
}

async function fetchImageMeta() {
    const response = await apiFetch(IMAGE_META_API_URL);
    const data = await readResponseData(response);
    if (!response.ok) {
        throw new Error(data.message || "读取图片工具配置失败");
    }
    return data;
}

async function fetchImageHistory() {
    const response = await apiFetch(IMAGE_HISTORY_API_URL);
    const data = await readResponseData(response);
    if (!response.ok) {
        throw new Error(data.message || "读取图片历史失败");
    }
    return data;
}

function normalizeImageMeta(raw) {
    return {
        configured: Boolean(raw?.configured),
        operationCost: Number(raw?.operationCost) || 0,
        maxUploadBytes: Number(raw?.maxUploadBytes) || 0,
        allowedMimeTypes: Array.isArray(raw?.allowedMimeTypes) ? raw.allowedMimeTypes : [],
        rateLimitMaxRequests: Number(raw?.rateLimitMaxRequests) || 0,
        rateLimitWindowSeconds: Number(raw?.rateLimitWindowSeconds) || 0
    };
}

function normalizeImageHistoryList(raw) {
    if (!Array.isArray(raw)) {
        return [];
    }
    return raw.map((entry) => ({
        id: entry.id,
        mode: entry.operationType || "generate",
        prompt: entry.prompt || "",
        model: entry.model || "",
        imageUrl: entry.imageDataUrl || "",
        resultInfo: entry.resultInfo || "",
        resultJson: "",
        createdAt: entry.createdAt || new Date().toISOString()
    }));
}

async function handleGenerateSubmit(event) {
    event.preventDefault();
    const prompt = elements.generatePromptInput.value.trim();
    if (!prompt) {
        setStatus("请先输入文生图提示词。");
        return;
    }

    await runRequest(async () => {
        const response = await apiFetch(IMAGE_GENERATIONS_API_URL, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({prompt})
        });
        const data = await readResponseData(response);
        if (!response.ok) {
            throw new Error(data.message || "文生图请求失败");
        }
        applyImageResponse(data, "文生图完成");
        await refreshImageHistory();
    });
}

async function handleProcessSubmit(event) {
    event.preventDefault();
    const prompt = elements.processPromptInput.value.trim();
    const imageFile = elements.processImageInput.files[0];
    const maskFile = elements.processMaskInput.files[0];
    const useLocalEdit = elements.localEditToggle.checked || Boolean(maskFile);

    if (!prompt) {
        setStatus("请先输入处理指令。");
        return;
    }
    if (!imageFile) {
        setStatus("请先选择参考图或待处理图片。");
        return;
    }

    await runRequest(async () => {
        let response;
        if (useLocalEdit) {
            const formData = new FormData();
            formData.append("prompt", prompt);
            formData.append("image", imageFile);
            if (maskFile) {
                formData.append("mask", maskFile);
            }
            response = await apiFetch(IMAGE_EDITS_API_URL, {
                method: "POST",
                body: formData
            });
        } else {
            const dataUrl = await readFileAsDataUrl(imageFile);
            response = await apiFetch(IMAGE_CHAT_COMPLETIONS_API_URL, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    role: "user",
                    content: [
                        {type: "text", text: prompt},
                        {type: "image_url", image_url: {url: dataUrl}}
                    ]
                })
            });
        }
        const data = await readResponseData(response);
        if (!response.ok) {
            throw new Error(data.message || "图像处理请求失败");
        }
        if (useLocalEdit) {
            applyImageResponse(data, "局部编辑完成");
        } else {
            applyCompatibleResponse(data, "图像转换完成", prompt);
        }
        await refreshImageHistory();
    });
}

function handleLocalEditToggleChange() {
    updateProcessModeHint();
    render();
}

async function runRequest(task) {
    if (state.busy) {
        return;
    }
    if (!canOperate()) {
        setStatus(`积分不足，至少需要 ${state.imageMeta.operationCost} 积分`);
        return;
    }

    state.busy = true;
    state.statusText = "";
    state.loadingStartedAt = Date.now();
    startLoadingTimer();
    render();

    try {
        await task();
    } catch (error) {
        setStatus(error.message || "请求失败");
    } finally {
        state.busy = false;
        stopLoadingTimer();
        render();
    }
}

function applyImageResponse(data, statusText) {
    const firstItem = Array.isArray(data.data) ? data.data[0] : null;
    if (!firstItem || !firstItem.b64_json) {
        throw new Error("上游没有返回 b64_json");
    }

    state.resultImageUrl = `data:image/png;base64,${firstItem.b64_json}`;
    state.resultPrompt = firstItem.revised_prompt || "";
    applyRemainingPoints(data);
    state.currentHistoryId = data.historyId || null;
    state.resultInfo = buildImageResultInfo({
        responseData: data,
        model: resolveResultModel(),
        remainingPoints: state.user?.points
    });
    state.resultJson = JSON.stringify(data, null, 2);
    setStatus(statusText);
}

function applyCompatibleResponse(data, statusText, displayPrompt = "") {
    const content = data?.choices?.[0]?.message?.content || "";
    const imageUrl = extractMarkdownImageUrl(content);
    if (!imageUrl) {
        throw new Error("兼容返回里没有可解析的图片数据");
    }

    state.resultImageUrl = imageUrl;
    state.resultPrompt = displayPrompt || "兼容转换完成";
    applyRemainingPoints(data);
    state.currentHistoryId = data.historyId || null;
    state.resultInfo = buildImageResultInfo({
        responseData: data,
        model: data.model || resolveResultModel(),
        remainingPoints: state.user?.points
    });
    state.resultJson = JSON.stringify(data, null, 2);
    setStatus(statusText);
}

function applyRemainingPoints(data) {
    if (!state.user) {
        return;
    }
    const remainingPoints = Number(data?.remainingPoints);
    if (Number.isFinite(remainingPoints)) {
        state.user.points = remainingPoints;
    }
}

function extractMarkdownImageUrl(content) {
    const match = String(content).match(/!\[[^\]]*]\((data:image\/[^)]+)\)/);
    return match ? match[1] : "";
}

function resolveResultModel() {
    const responseData = parseJsonSafe(state.resultJson);
    const responseModel = responseData?.model;
    return responseModel || "gpt-image-2";
}

function setStatus(text) {
    state.statusText = text;
    render();
}

function render() {
    if (state.user) {
        elements.accountNameText.textContent = state.user.accountName || "当前账号";
        elements.accountPointsText.textContent = `积分 ${formatPoints(state.user.points)}`;
    }

    const canUseTools = canOperate();
    elements.loadingHintText.textContent = buildLoadingHintText();
    elements.operationCostText.textContent = `每次操作消耗 ${state.imageMeta.operationCost} 积分`;
    elements.constraintsText.textContent = buildConstraintsText();
    renderHistory();
    elements.clearHistoryButton.disabled = state.history.length === 0;

    elements.showGenerateTab.classList.toggle("active", state.view === "generate");
    elements.showProcessTab.classList.toggle("active", state.view === "process");
    elements.panelTitleText.textContent = resolvePanelTitle();

    elements.generateForm.hidden = state.view !== "generate";
    elements.processForm.hidden = state.view !== "process";
    elements.maskField.hidden = !(state.view === "process" && elements.localEditToggle.checked);
    updateProcessModeHint();

    const inputsDisabled = state.busy;
    const actionsDisabled = state.busy || !canUseTools;
    elements.generatePromptInput.disabled = inputsDisabled;
    elements.generateSubmitButton.disabled = actionsDisabled;
    elements.processPromptInput.disabled = inputsDisabled;
    elements.processImageInput.disabled = inputsDisabled;
    elements.localEditToggle.disabled = inputsDisabled;
    elements.processMaskInput.disabled = inputsDisabled;
    elements.processSubmitButton.disabled = actionsDisabled;
    elements.generateSubmitButton.textContent = state.busy && state.view === "generate" ? "生成中..." : "生成图片";
    elements.processSubmitButton.textContent = state.busy && state.view === "process"
        ? (elements.localEditToggle.checked ? "编辑中..." : "转换中...")
        : (elements.localEditToggle.checked ? "开始局部编辑" : "开始转换");

    elements.loadingState.hidden = !state.busy;
    elements.imagePreviewModal.hidden = !state.previewOpen;

    if (state.busy) {
        elements.emptyResult.hidden = true;
        elements.resultContainer.hidden = true;
    } else if (state.resultImageUrl) {
        elements.emptyResult.hidden = true;
        elements.resultContainer.hidden = false;
        elements.resultImage.src = state.resultImageUrl;
        elements.resultPromptText.textContent = state.resultPrompt || "无 revised prompt";
        elements.resultInfoText.textContent = state.resultInfo;
        elements.downloadButton.disabled = false;
        elements.previewImage.src = state.resultImageUrl;
    } else {
        elements.emptyResult.hidden = false;
        elements.resultContainer.hidden = true;
        elements.downloadButton.disabled = true;
        closeImagePreview();
    }
}

function formatPoints(points) {
    return clientConfig.unlimitedPoints > 0 && points >= clientConfig.unlimitedPoints ? "无限" : String(points || 0);
}

function canOperate() {
    if (!state.user) {
        return false;
    }
    if (!state.imageMeta.configured) {
        return false;
    }
    return state.user.points >= state.imageMeta.operationCost
        || (clientConfig.unlimitedPoints > 0 && state.user.points >= clientConfig.unlimitedPoints);
}

function defaultStatusText(canUseTools) {
    return canUseTools
        ? `每次操作消耗 ${state.imageMeta.operationCost} 积分`
        : `积分不足，至少需要 ${state.imageMeta.operationCost} 积分`;
}

function resolvePanelTitle() {
    if (state.view === "process") {
        return "图像处理";
    }
    return "文字生图";
}

function downloadCurrentImage() {
    if (!state.resultImageUrl) {
        return;
    }
    const link = document.createElement("a");
    link.href = state.resultImageUrl;
    link.download = `image-result-${Date.now()}.png`;
    link.click();
}

function openImagePreview() {
    if (!state.resultImageUrl || state.busy) {
        return;
    }
    state.previewOpen = true;
    render();
}

async function refreshImageHistory() {
    try {
        const history = await fetchImageHistory();
        state.history = normalizeImageHistoryList(history);
        render();
    } catch (error) {
        console.error(error);
    }
}

function renderHistory() {
    elements.historyCountText.textContent = String(state.history.length);
    if (state.history.length === 0) {
        elements.historyList.innerHTML = `<div class="image-history-empty">当前会话还没有结果记录，生成一张图后就会出现在这里。</div>`;
        return;
    }

    elements.historyList.innerHTML = state.history.map((entry) => `
        <div class="image-history-item">
            <button class="image-history-main" type="button" data-history-id="${entry.id}">
                <img class="image-history-thumb" src="${escapeAttribute(entry.imageUrl)}" alt="${escapeAttribute(entry.mode)}">
                <div class="image-history-body">
                    <p class="image-history-title">${escapeHtml(historyModeLabel(entry.mode))}</p>
                    <p class="image-history-subtitle">${escapeHtml(shortenText(entry.prompt, 28))}</p>
                    <p class="image-history-time">${escapeHtml(formatHistoryTime(entry.createdAt))}</p>
                </div>
            </button>
            <button class="image-history-delete" type="button" data-delete-history-id="${entry.id}" aria-label="删除历史">×</button>
        </div>
    `).join("");
}

function handleHistoryClick(event) {
    const deleteButton = event.target.closest("[data-delete-history-id]");
    if (deleteButton) {
        deleteHistoryEntry(deleteButton.getAttribute("data-delete-history-id"));
        return;
    }

    const button = event.target.closest("[data-history-id]");
    if (!button) {
        return;
    }
    restoreHistoryEntry(button.getAttribute("data-history-id"));
}

async function restoreHistoryEntry(entryId) {
    const entry = state.history.find((item) => String(item.id) === String(entryId));
    if (!entry) {
        return;
    }

    const restoreToken = ++state.restoreToken;
    try {
        const response = await apiFetch(`${IMAGE_HISTORY_API_URL}/${encodeURIComponent(entryId)}`);
        const data = await readResponseData(response);
        if (!response.ok) {
            throw new Error(data.message || "读取图片历史详情失败");
        }
        if (restoreToken !== state.restoreToken) {
            return;
        }

        const restoredOperationType = data.operationType || entry.mode;
        state.currentHistoryId = data.id || entry.id;
        state.view = restoredOperationType === "generate" ? "generate" : "process";
        state.resultImageUrl = data.imageDataUrl || entry.imageUrl;
        state.resultPrompt = data.prompt || entry.prompt;
        state.resultJson = data.responseJson || "{}";
        state.resultInfo = buildImageResultInfo({
            responseData: parseJsonSafe(state.resultJson),
            model: data.model || entry.model,
            remainingPoints: extractRemainingPointsFromText(data.resultInfo || entry.resultInfo),
            fallbackInfo: data.resultInfo || entry.resultInfo
        });

        const model = data.model || entry.model;
        const prompt = data.prompt || entry.prompt;
        if (state.view === "generate") {
            elements.generatePromptInput.value = prompt;
        } else if (state.view === "process") {
            elements.processPromptInput.value = prompt;
            elements.localEditToggle.checked = restoredOperationType === "edit";
            elements.maskField.hidden = !elements.localEditToggle.checked;
        }

        setStatus("已从数据库恢复历史图片。");
        focusActivePromptInput();
    } catch (error) {
        if (restoreToken !== state.restoreToken) {
            return;
        }
        setStatus(error.message || "恢复历史图片失败");
    }
}

async function deleteHistoryEntry(historyId) {
    if (!historyId) {
        return;
    }
    if (!window.confirm("确认删除这条最近结果吗？")) {
        return;
    }

    try {
        const response = await apiFetch(`${IMAGE_HISTORY_API_URL}/${encodeURIComponent(historyId)}`, {
            method: "DELETE"
        });
        if (!response.ok) {
            const data = await readResponseData(response);
            throw new Error(data.message || "删除历史失败");
        }

        if (String(state.currentHistoryId) === String(historyId)) {
            clearCurrentResult("已删除当前展示的历史结果。");
        }
        await refreshImageHistory();
        setStatus("历史结果已删除。");
    } catch (error) {
        setStatus(error.message || "删除历史失败");
    }
}

async function handleClearHistory() {
    if (state.history.length === 0) {
        return;
    }
    if (!window.confirm("确认清空当前账号的全部最近结果吗？")) {
        return;
    }

    try {
        const response = await apiFetch(IMAGE_HISTORY_API_URL, {
            method: "DELETE"
        });
        if (!response.ok) {
            const data = await readResponseData(response);
            throw new Error(data.message || "清空历史失败");
        }

        clearCurrentResult("历史结果已全部清空。");
        await refreshImageHistory();
        setStatus("历史结果已全部清空。");
    } catch (error) {
        setStatus(error.message || "清空历史失败");
    }
}

function historyModeLabel(mode) {
    if (mode === "edit" || mode === "compatible" || mode === "process") {
        return "图像处理";
    }
    return "文字生图";
}

function formatHistoryTime(timestamp) {
    return new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(timestamp));
}

function buildImageResultInfo({responseData, model, remainingPoints, fallbackInfo = ""}) {
    if (!responseData || typeof responseData !== "object") {
        return fallbackInfo || `模型: ${model || "-"} | 剩余积分: ${formatPoints(remainingPoints)}`;
    }

    const segments = [];
    if (model) {
        segments.push(`模型: ${model}`);
    }

    if (responseData.created != null) {
        segments.push(`时间: ${formatCreatedValue(responseData.created)}`);
    } else if (responseData.id) {
        segments.push(`请求ID: ${responseData.id}`);
    }

    if (remainingPoints != null) {
        segments.push(`剩余积分: ${formatPoints(remainingPoints)}`);
    }

    return segments.length > 0 ? segments.join(" | ") : fallbackInfo;
}

function formatCreatedValue(rawValue) {
    const numericValue = Number(rawValue);
    if (!Number.isFinite(numericValue)) {
        return String(rawValue || "-");
    }

    const timestamp = numericValue < 1_000_000_000_000 ? numericValue * 1000 : numericValue;
    return new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    }).format(new Date(timestamp));
}

function extractRemainingPointsFromText(text) {
    const match = String(text || "").match(/剩余积分:\s*([0-9]+)/);
    return match ? Number(match[1]) : null;
}

function parseJsonSafe(value) {
    if (!value) {
        return null;
    }
    try {
        return JSON.parse(value);
    } catch (error) {
        return null;
    }
}

function clearCurrentResult(statusText = "") {
    state.currentHistoryId = null;
    state.resultImageUrl = "";
    state.resultPrompt = "";
    state.resultInfo = "";
    state.resultJson = "";
    closeImagePreview();
    if (statusText) {
        state.statusText = statusText;
    }
    render();
}

function resetWorkspaceForView(view) {
    clearCurrentResult();

    if (view === "generate") {
        elements.generateForm.reset();
        return;
    }

    elements.processForm.reset();
    elements.maskField.hidden = true;
}

function startLoadingTimer() {
    stopLoadingTimer(false);
    state.loadingTimerId = window.setInterval(() => {
        if (!state.busy) {
            stopLoadingTimer();
            return;
        }
        render();
    }, 1000);
}

function stopLoadingTimer(resetStartedAt = true) {
    if (state.loadingTimerId) {
        clearInterval(state.loadingTimerId);
        state.loadingTimerId = null;
    }
    if (resetStartedAt) {
        state.loadingStartedAt = 0;
    }
}

function buildLoadingHintText() {
    if (!state.busy || !state.loadingStartedAt) {
        return "已等待 0 秒";
    }

    const elapsedSeconds = Math.max(1, Math.floor((Date.now() - state.loadingStartedAt) / 1000));
    if (elapsedSeconds >= 120) {
        return `已等待 ${elapsedSeconds} 秒。当前请求明显偏慢，可能会触发上游超时，建议继续等待一会儿，若失败可稍后重试。`;
    }
    if (elapsedSeconds >= 60) {
        return `已等待 ${elapsedSeconds} 秒。当前请求耗时较长，通常是上游模型处理较慢，请不要重复点击提交。`;
    }
    if (elapsedSeconds >= 20) {
        return `已等待 ${elapsedSeconds} 秒。图片生成通常比文本慢，如果网络正常请继续等待。`;
    }
    return `已等待 ${elapsedSeconds} 秒`;
}

function shortenText(text, maxLength) {
    const normalized = String(text || "").trim();
    if (!normalized) {
        return "无提示词";
    }
    return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized;
}

function buildConstraintsText() {
    const sizeText = state.imageMeta.maxUploadBytes > 0 ? formatBytes(state.imageMeta.maxUploadBytes) : "10MB";
    return `单图上限 ${sizeText} | 仅能上传图片格式`;
}

function formatBytes(bytes) {
    if (bytes >= 1024 * 1024) {
        return `${(bytes / (1024 * 1024)).toFixed(0)}MB`;
    }
    if (bytes >= 1024) {
        return `${(bytes / 1024).toFixed(0)}KB`;
    }
    return `${bytes}B`;
}

function resolveCurrentPrompt() {
    if (state.view === "process") {
        return elements.processPromptInput.value.trim();
    }
    return elements.generatePromptInput.value.trim();
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function escapeAttribute(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("\"", "&quot;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

function closeImagePreview() {
    if (!state.previewOpen) {
        return;
    }
    state.previewOpen = false;
    render();
}

function handlePreviewOverlayClick(event) {
    if (event.target === elements.imagePreviewModal || event.target.classList.contains("image-preview-backdrop")) {
        closeImagePreview();
    }
}

function handleDocumentKeydown(event) {
    if (event.key === "Escape") {
        closeImagePreview();
    }
}

function focusActivePromptInput() {
    if (state.busy || !canOperate()) {
        return;
    }

    queueMicrotask(() => {
        if (state.view === "generate") {
            elements.generatePromptInput.focus();
            return;
        }
        elements.processPromptInput.focus();
    });
}

function updateProcessModeHint() {
    if (!elements.processModeHint) {
        return;
    }
    elements.processModeHint.textContent = elements.localEditToggle.checked
        ? "用输入图片做基础，可选上传遮罩图来指定“哪些区域允许修改”。"
        : "上传一张输入图片作为参考图或待处理图片，再用文字说明你想要的效果。";
}

function readFileAsDataUrl(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ""));
        reader.onerror = () => reject(new Error("读取图片失败"));
        reader.readAsDataURL(file);
    });
}
