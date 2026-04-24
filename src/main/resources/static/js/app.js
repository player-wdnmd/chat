const CLIENT_CONFIG_API_URL = "/api/config/public";
const STATE_API_URL = "/api/state";
const SKILLS_API_URL = "/api/skills";
const CHAT_API_URL = "/api/chat";
const LOGIN_API_URL = "/api/auth/login";
const REGISTER_API_URL = "/api/auth/register";
const PROFILE_API_URL = "/api/auth/me";
const REDEEM_API_URL = "/api/auth/redeem";
const LOGOUT_API_URL = "/api/auth/session";

const runtimeConfig = {
    authTokenStorageKey: "",
    systemPrompt: "",
    unlimitedPoints: 0,
    defaultSkillPromptTemplate: ""
};

// 启动时一次性缓存常用 DOM 节点，让后续的 render 逻辑只关心状态而不是重复查 DOM。
const elements = {
    authScreen: document.getElementById("authScreen"),
    appShell: document.getElementById("appShell"),
    authStatus: document.getElementById("authStatus"),
    showLoginButton: document.getElementById("showLoginButton"),
    showRegisterButton: document.getElementById("showRegisterButton"),
    loginForm: document.getElementById("loginForm"),
    registerForm: document.getElementById("registerForm"),
    loginAccountInput: document.getElementById("loginAccountInput"),
    loginAccountError: document.getElementById("loginAccountError"),
    loginPasswordInput: document.getElementById("loginPasswordInput"),
    loginPasswordError: document.getElementById("loginPasswordError"),
    registerAccountInput: document.getElementById("registerAccountInput"),
    registerAccountError: document.getElementById("registerAccountError"),
    registerPasswordInput: document.getElementById("registerPasswordInput"),
    registerPasswordError: document.getElementById("registerPasswordError"),
    accountNameText: document.getElementById("accountNameText"),
    accountPointsText: document.getElementById("accountPointsText"),
    redeemCodeButton: document.getElementById("redeemCodeButton"),
    logoutButton: document.getElementById("logoutButton"),
    chatViewButton: document.getElementById("chatViewButton"),
    skillsViewButton: document.getElementById("skillsViewButton"),
    newConversationButton: document.getElementById("newConversationButton"),
    conversationCount: document.getElementById("conversationCount"),
    conversationList: document.getElementById("conversationList"),
    chatView: document.getElementById("chatView"),
    skillsManagerView: document.getElementById("skillsManagerView"),
    skillEditorForm: document.getElementById("skillEditorForm"),
    skillEditorModeText: document.getElementById("skillEditorModeText"),
    skillEditorTitle: document.getElementById("skillEditorTitle"),
    cancelSkillEditButton: document.getElementById("cancelSkillEditButton"),
    skillNameInput: document.getElementById("skillNameInput"),
    skillDescriptionInput: document.getElementById("skillDescriptionInput"),
    skillPromptInput: document.getElementById("skillPromptInput"),
    skillSubmitButton: document.getElementById("skillSubmitButton"),
    skillsManagerList: document.getElementById("skillsManagerList"),
    chatForm: document.getElementById("chatForm"),
    messageInput: document.getElementById("messageInput"),
    sendButton: document.getElementById("sendButton"),
    messageViewport: document.getElementById("messageViewport"),
    messageList: document.getElementById("messageList"),
    emptyState: document.getElementById("emptyState"),
    statusText: document.getElementById("statusText"),
    usageText: document.getElementById("usageText"),
    skillSelect: document.getElementById("skillSelect"),
    redeemModal: document.getElementById("redeemModal"),
    closeRedeemModalButton: document.getElementById("closeRedeemModalButton"),
    redeemCodeInput: document.getElementById("redeemCodeInput"),
    redeemStatusText: document.getElementById("redeemStatusText"),
    submitRedeemButton: document.getElementById("submitRedeemButton")
};

// `auth` 只保存前端真正需要知道的登录态信息：
// bearer token、当前用户摘要、认证页模式和提示文案。
const auth = {
    token: "",
    user: null,
    mode: "login",
    statusText: "请输入账号和密码。",
    fieldErrors: {
        login: {accountName: "", password: ""},
        register: {accountName: "", password: ""}
    }
};

const ui = {
    view: "chat",
    editingSkillId: null,
    redeemModalOpen: false
};

// `state` 和 `/api/state` 的后端结构保持一致，便于直接持久化。
let state = createDefaultState();
let busy = false;
let renamingConversationId = null;
let persistQueue = Promise.resolve();
let availableSkills = [];

boot();

// -----------------------------------------------------------------------------
// 启动与登录态恢复
// -----------------------------------------------------------------------------

async function boot() {
    try {
        applyPublicConfig(await fetchPublicConfig());
    } catch (error) {
        auth.statusText = error.message || "读取前端配置失败。";
        render();
        return;
    }

    auth.token = loadStoredToken();
    bindEvents();
    resetSkillForm();
    render();

    if (!auth.token) {
        render();
        return;
    }

    try {
        auth.user = await fetchCurrentUser();
        await hydrateWorkspace();
        auth.statusText = "登录状态已恢复。";
    } catch (error) {
        clearAuthSession("登录已失效，请重新登录。");
    }

    render();
}

async function fetchPublicConfig() {
    const response = await fetch(CLIENT_CONFIG_API_URL, {
        headers: {Accept: "application/json"}
    });
    const raw = await response.text();
    const data = parseResponseBody(raw);
    if (!response.ok) {
        throw new Error(data.message || "读取前端配置失败");
    }
    return data;
}

function applyPublicConfig(data) {
    runtimeConfig.authTokenStorageKey = data.authTokenStorageKey || "";
    runtimeConfig.systemPrompt = data.systemPrompt || "";
    runtimeConfig.unlimitedPoints = Number(data.unlimitedPoints) || 0;
    runtimeConfig.defaultSkillPromptTemplate = data.defaultSkillPromptTemplate || "";
}

function bindEvents() {
    elements.showLoginButton.addEventListener("click", () => switchAuthMode("login"));
    elements.showRegisterButton.addEventListener("click", () => switchAuthMode("register"));
    elements.loginForm.addEventListener("submit", handleLoginSubmit);
    elements.registerForm.addEventListener("submit", handleRegisterSubmit);
    elements.loginAccountInput.addEventListener("input", () => clearAuthFieldError("login", "accountName"));
    elements.loginPasswordInput.addEventListener("input", () => clearAuthFieldError("login", "password"));
    elements.registerAccountInput.addEventListener("input", () => clearAuthFieldError("register", "accountName"));
    elements.registerPasswordInput.addEventListener("input", () => clearAuthFieldError("register", "password"));
    elements.redeemCodeButton.addEventListener("click", openRedeemModal);
    elements.logoutButton.addEventListener("click", handleLogout);
    elements.chatViewButton.addEventListener("click", () => switchView("chat"));
    elements.skillsViewButton.addEventListener("click", () => switchView("skills"));
    elements.newConversationButton.addEventListener("click", createConversation);
    elements.skillEditorForm.addEventListener("submit", handleCreateSkillSubmit);
    elements.cancelSkillEditButton.addEventListener("click", cancelSkillEditing);
    elements.closeRedeemModalButton.addEventListener("click", closeRedeemModal);
    elements.submitRedeemButton.addEventListener("click", handleRedeemSubmit);
    elements.chatForm.addEventListener("submit", handleSubmit);
    elements.messageInput.addEventListener("input", autoResizeTextarea);
    elements.messageInput.addEventListener("keydown", handleComposerKeydown);
    elements.skillSelect.addEventListener("change", handleSkillSelectChange);
}

function switchAuthMode(mode) {
    auth.mode = mode;
    auth.statusText = mode === "login" ? "请输入账号和密码。" : "注册后会自动登录，新账户默认积分为 0。";
    clearAuthFieldErrors();
    render();
}

function loadStoredToken() {
    if (!runtimeConfig.authTokenStorageKey) {
        return "";
    }
    return localStorage.getItem(runtimeConfig.authTokenStorageKey) || "";
}

function storeToken(token) {
    if (!runtimeConfig.authTokenStorageKey) {
        return;
    }
    localStorage.setItem(runtimeConfig.authTokenStorageKey, token);
}

function clearStoredToken() {
    if (!runtimeConfig.authTokenStorageKey) {
        return;
    }
    localStorage.removeItem(runtimeConfig.authTokenStorageKey);
}

async function handleLoginSubmit(event) {
    event.preventDefault();
    clearAuthFieldErrors();
    const accountName = elements.loginAccountInput.value.trim();
    const password = elements.loginPasswordInput.value;
    if (!accountName || !password) {
        auth.statusText = "账号和密码不能为空。";
        render();
        return;
    }

    try {
        const response = await apiFetch(LOGIN_API_URL, {
            method: "POST",
            body: JSON.stringify({accountName, password})
        }, false);
        const data = await readResponseData(response);
        if (!response.ok) {
            applyAuthFieldErrors("login", data.fieldErrors);
            auth.statusText = hasFieldErrors(data.fieldErrors) ? "" : (data.message || "登录失败");
            throw new Error(data.message || "登录失败");
        }

        await applyAuthSuccess(data, "登录成功。");
    } catch (error) {
        if (!hasCurrentModeFieldErrors()) {
            auth.statusText = error.message || "登录失败";
        }
        render();
    }
}

async function handleRegisterSubmit(event) {
    event.preventDefault();
    clearAuthFieldErrors();
    const accountName = elements.registerAccountInput.value.trim();
    const password = elements.registerPasswordInput.value;
    if (!accountName || !password) {
        auth.statusText = "账号和密码不能为空。";
        render();
        return;
    }

    try {
        const response = await apiFetch(REGISTER_API_URL, {
            method: "POST",
            body: JSON.stringify({accountName, password})
        }, false);
        const data = await readResponseData(response);
        if (!response.ok) {
            applyAuthFieldErrors("register", data.fieldErrors);
            auth.statusText = hasFieldErrors(data.fieldErrors) ? "" : (data.message || "注册失败");
            throw new Error(data.message || "注册失败");
        }

        await applyAuthSuccess(data, "注册成功，已自动登录。新账户默认积分为 0。");
    } catch (error) {
        if (!hasCurrentModeFieldErrors()) {
            auth.statusText = error.message || "注册失败";
        }
        render();
    }
}

async function applyAuthSuccess(data, statusText) {
    auth.token = data.token;
    auth.user = {
        accountName: data.accountName,
        points: Number.isFinite(data.points) ? data.points : 0
    };
    auth.statusText = statusText;
    clearAuthFieldErrors();
    storeToken(auth.token);
    resetAuthForms();
    await hydrateWorkspace();
    render();
}

function resetAuthForms() {
    elements.loginForm.reset();
    elements.registerForm.reset();
    clearAuthFieldErrors();
}

function clearAuthFieldErrors() {
    auth.fieldErrors.login = {accountName: "", password: ""};
    auth.fieldErrors.register = {accountName: "", password: ""};
}

function clearAuthFieldError(mode, field) {
    auth.fieldErrors[mode][field] = "";
    if (!hasCurrentModeFieldErrors()) {
        render();
        return;
    }
    render();
}

function applyAuthFieldErrors(mode, fieldErrors) {
    if (!fieldErrors || typeof fieldErrors !== "object") {
        return;
    }
    auth.fieldErrors[mode] = {
        accountName: fieldErrors.accountName || "",
        password: fieldErrors.password || ""
    };
}

function hasFieldErrors(fieldErrors) {
    if (!fieldErrors || typeof fieldErrors !== "object") {
        return false;
    }
    return Object.values(fieldErrors).some((value) => Boolean(value));
}

function hasCurrentModeFieldErrors() {
    return Object.values(auth.fieldErrors[auth.mode]).some((value) => Boolean(value));
}

async function handleLogout() {
    try {
        if (auth.token) {
            await apiFetch(LOGOUT_API_URL, {method: "DELETE"});
        }
    } catch (error) {
        console.warn(error);
    } finally {
        clearAuthSession("已退出登录。");
        render();
    }
}

function switchView(view) {
    ui.view = view;
    render();
}

function clearAuthSession(statusText) {
    auth.token = "";
    auth.user = null;
    auth.mode = "login";
    auth.statusText = statusText;
    clearAuthFieldErrors();
    clearStoredToken();
    state = createDefaultState();
    availableSkills = [];
    busy = false;
    renamingConversationId = null;
    ui.view = "chat";
    ui.editingSkillId = null;
    ui.redeemModalOpen = false;
    resetAuthForms();
    resetSkillForm();
    resetRedeemModal();
}

// -----------------------------------------------------------------------------
// API 包装与初始化加载
// -----------------------------------------------------------------------------

async function apiFetch(url, options = {}, requireAuth = true) {
    const headers = new Headers(options.headers || {});
    if (!headers.has("Accept")) {
        headers.set("Accept", "application/json");
    }
    if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    if (requireAuth && auth.token) {
        headers.set("Authorization", `Bearer ${auth.token}`);
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (requireAuth && response.status === 401) {
        clearAuthSession("登录已失效，请重新登录。");
        render();
        throw new Error("登录已失效，请重新登录。");
    }

    return response;
}

async function readResponseData(response) {
    const raw = await response.text();
    return parseResponseBody(raw);
}

async function fetchCurrentUser() {
    const response = await apiFetch(PROFILE_API_URL);
    const data = await readResponseData(response);
    if (!response.ok) {
        throw new Error(data.message || "读取当前用户信息失败");
    }
    return {
        accountName: data.accountName,
        points: Number.isFinite(data.points) ? data.points : 0
    };
}

async function hydrateWorkspace() {
    const [nextState, nextSkills] = await Promise.all([hydrateState(), hydrateSkills()]);
    state = nextState;
    availableSkills = nextSkills;
    sanitizeConversationSkillIds();
}

async function hydrateSkills() {
    const response = await apiFetch(SKILLS_API_URL);
    const data = await readResponseData(response);
    if (!response.ok) {
        throw new Error(data.message || "读取 skills 失败");
    }
    return Array.isArray(data) ? data : [];
}

async function hydrateState() {
    const response = await apiFetch(STATE_API_URL);
    const data = await readResponseData(response);
    if (!response.ok) {
        throw new Error(data.message || "读取聊天状态失败");
    }
    return normalizeState(data);
}

// -----------------------------------------------------------------------------
// 状态归一化
// -----------------------------------------------------------------------------

function normalizeState(rawState) {
    if (!rawState || !Array.isArray(rawState.conversations) || rawState.conversations.length === 0) {
        return createDefaultState();
    }

    const conversations = rawState.conversations.map(normalizeConversation);
    const activeConversationId = conversations.some((conversation) => conversation.id === rawState.activeConversationId)
        ? rawState.activeConversationId
        : conversations[0].id;

    return {
        conversations,
        activeConversationId,
        lastUsage: rawState.lastUsage || "尚未发送消息"
    };
}

function createDefaultState() {
    const firstConversation = createConversationRecord(1);
    return {
        conversations: [firstConversation],
        activeConversationId: firstConversation.id,
        lastUsage: "尚未发送消息"
    };
}

function normalizeConversation(conversation) {
    const messages = Array.isArray(conversation.messages)
        ? conversation.messages.map(normalizeMessage)
        : [];

    return {
        id: conversation.id || crypto.randomUUID(),
        title: conversation.title || "未命名对话",
        subtitle: conversation.subtitle || "等待第一条消息",
        skillIds: normalizeSkillIds(conversation.skillIds),
        messages,
        updatedAt: conversation.updatedAt || Date.now()
    };
}

function normalizeSkillIds(skillIds) {
    if (!Array.isArray(skillIds)) {
        return [];
    }

    const normalized = [...new Set(skillIds
        .filter((skillId) => typeof skillId === "string" && skillId.trim())
        .map((skillId) => skillId.trim()))];
    return normalized.length > 0 ? [normalized[0]] : [];
}

function normalizeMessage(message) {
    const wasPending = Boolean(message?.pending);

    return {
        id: message?.id || crypto.randomUUID(),
        role: message?.role || "assistant",
        content: message?.content || "",
        createdAt: message?.createdAt || Date.now(),
        pending: false,
        meta: message?.meta || (wasPending ? "请求中断" : undefined),
        error: Boolean(message?.error) || wasPending
    };
}

function createConversationRecord(sequence) {
    return {
        id: crypto.randomUUID(),
        title: `会话 ${sequence}`,
        subtitle: "等待第一条消息",
        skillIds: [],
        messages: [],
        updatedAt: Date.now()
    };
}

// -----------------------------------------------------------------------------
// 持久化
// -----------------------------------------------------------------------------

function queuePersist() {
    const snapshot = createPersistedStateSnapshot(state);
    persistQueue = persistQueue
        .catch(() => undefined)
        .then(() => persistSnapshot(snapshot))
        .catch((error) => {
            console.error(error);
        });
    return persistQueue;
}

async function persistSnapshot(snapshot) {
    const response = await apiFetch(STATE_API_URL, {
        method: "PUT",
        body: JSON.stringify(snapshot)
    });

    if (!response.ok) {
        const data = await readResponseData(response);
        throw new Error(data.message || "保存聊天记录失败");
    }
}

function createPersistedStateSnapshot(sourceState) {
    return {
        conversations: sourceState.conversations.map((conversation) => ({
            id: conversation.id,
            title: conversation.title,
            subtitle: conversation.subtitle,
            skillIds: conversation.skillIds || [],
            messages: conversation.messages.map((message) => ({
                id: message.id,
                role: message.role,
                content: message.content,
                createdAt: message.createdAt,
                pending: Boolean(message.pending),
                meta: message.meta || null,
                error: Boolean(message.error)
            })),
            updatedAt: conversation.updatedAt
        })),
        activeConversationId: sourceState.activeConversationId,
        lastUsage: sourceState.lastUsage
    };
}

// -----------------------------------------------------------------------------
// 会话操作
// -----------------------------------------------------------------------------

function getActiveConversation() {
    return state.conversations.find((conversation) => conversation.id === state.activeConversationId) || state.conversations[0];
}

function createConversation() {
    const conversation = createConversationRecord(state.conversations.length + 1);
    state.conversations = [conversation, ...state.conversations];
    state.activeConversationId = conversation.id;
    state.lastUsage = "已新建聊天";
    queuePersist();
    render();
    elements.messageInput.focus();
}

function clearConversation(conversationId) {
    const conversation = state.conversations.find((item) => item.id === conversationId);
    if (!conversation) {
        return;
    }

    conversation.messages = [];
    conversation.title = "未命名对话";
    conversation.subtitle = "等待第一条消息";
    conversation.updatedAt = Date.now();
    state.lastUsage = "对话已清空";
    render();
    queuePersist();
}

function deleteConversation(conversationId) {
    const conversationIndex = state.conversations.findIndex((item) => item.id === conversationId);
    if (conversationIndex < 0) {
        return;
    }

    const deletedConversation = state.conversations[conversationIndex];
    const remainingConversations = state.conversations.filter((item) => item.id !== conversationId);

    if (remainingConversations.length === 0) {
        const replacementConversation = createConversationRecord(1);
        state.conversations = [replacementConversation];
        state.activeConversationId = replacementConversation.id;
        state.lastUsage = `${deletedConversation.title} 已删除，已创建空白会话`;
    } else {
        state.conversations = remainingConversations;
        if (state.activeConversationId === conversationId) {
            const fallbackIndex = Math.max(0, conversationIndex - 1);
            state.activeConversationId = remainingConversations[fallbackIndex].id;
        }
        state.lastUsage = `${deletedConversation.title} 已删除`;
    }

    if (renamingConversationId === conversationId) {
        renamingConversationId = null;
    }

    queuePersist();
    render();
}

function switchConversation(conversationId) {
    state.activeConversationId = conversationId;
    state.lastUsage = `${getActiveConversation().title} 已切换`;
    queuePersist();
    render();
}

// -----------------------------------------------------------------------------
// 渲染
// -----------------------------------------------------------------------------

function render() {
    const isAuthenticated = Boolean(auth.token && auth.user);
    elements.authScreen.hidden = isAuthenticated;
    elements.appShell.hidden = !isAuthenticated;
    elements.redeemModal.hidden = !isAuthenticated || !ui.redeemModalOpen;

    renderAuthPanel();
    if (!isAuthenticated) {
        return;
    }

    renderAccountPanel();
    renderViewSwitcher();
    renderSidebar();
    renderSkillComposer();
    renderMessages();
    renderSkillsManager();

    const canSend = auth.user.points > 0;
    elements.statusText.textContent = busy ? "思考中..." : (canSend ? "Ready" : "积分不足");
    elements.usageText.textContent = state.lastUsage;
    elements.sendButton.disabled = busy || !canSend;
    elements.messageInput.disabled = busy || !canSend;
    elements.messageInput.placeholder = canSend ? "发送消息" : "积分不足，请联系管理员加分";
    autoResizeTextarea();
}

function renderAuthPanel() {
    const isLogin = auth.mode === "login";
    elements.showLoginButton.classList.toggle("active", isLogin);
    elements.showRegisterButton.classList.toggle("active", !isLogin);
    elements.loginForm.hidden = !isLogin;
    elements.registerForm.hidden = isLogin;
    elements.authStatus.textContent = auth.statusText;
    elements.loginAccountError.textContent = auth.fieldErrors.login.accountName;
    elements.loginPasswordError.textContent = auth.fieldErrors.login.password;
    elements.registerAccountError.textContent = auth.fieldErrors.register.accountName;
    elements.registerPasswordError.textContent = auth.fieldErrors.register.password;
}

function renderAccountPanel() {
    elements.accountNameText.textContent = auth.user.accountName;
    elements.accountPointsText.textContent = `积分 ${formatPoints(auth.user.points)}`;
}

function formatPoints(points) {
    return runtimeConfig.unlimitedPoints > 0 && points >= runtimeConfig.unlimitedPoints ? "无限" : String(points);
}

function renderViewSwitcher() {
    const inChatView = ui.view === "chat";
    elements.chatViewButton.classList.toggle("active", inChatView);
    elements.skillsViewButton.classList.toggle("active", !inChatView);
    elements.chatView.hidden = !inChatView;
    elements.skillsManagerView.hidden = inChatView;
    elements.newConversationButton.hidden = !inChatView;
}

function renderSidebar() {
    elements.conversationCount.textContent = String(state.conversations.length);
    elements.conversationList.innerHTML = "";

    state.conversations.forEach((conversation) => {
        const row = document.createElement("div");
        row.className = `conversation-item ${conversation.id === state.activeConversationId ? "active" : ""}`;
        const isRenaming = renamingConversationId === conversation.id;

        if (isRenaming) {
            row.innerHTML = `
                <div class="conversation-meta">
                    <input class="conversation-title-input" type="text" value="${escapeAttribute(conversation.title)}" maxlength="60" aria-label="重命名对话">
                    <p class="conversation-preview">${escapeHtml(conversation.subtitle)}</p>
                </div>
                <div class="conversation-tools">
                    <button class="rename-button" type="button" aria-label="保存重命名">✓</button>
                </div>
            `;

            const input = row.querySelector(".conversation-title-input");
            const saveButton = row.querySelector(".rename-button");
            const save = () => commitRenameConversation(conversation.id, input.value);

            saveButton.addEventListener("click", save);
            input.addEventListener("keydown", (event) => {
                if (event.key === "Enter") {
                    event.preventDefault();
                    save();
                }
                if (event.key === "Escape") {
                    event.preventDefault();
                    cancelRenameConversation();
                }
            });
            input.addEventListener("blur", save);
            elements.conversationList.appendChild(row);
            queueMicrotask(() => {
                input.focus();
                input.select();
            });
            return;
        }

        row.innerHTML = `
            <button class="conversation-main" type="button">
                <div class="conversation-meta">
                    <p class="conversation-title">${escapeHtml(conversation.title)}</p>
                    <p class="conversation-preview">${escapeHtml(conversation.subtitle)}</p>
                </div>
                <span class="conversation-time">${formatTime(conversation.updatedAt)}</span>
            </button>
            <div class="conversation-tools">
                <button class="rename-button" type="button" aria-label="重命名对话">✎</button>
                <button class="clear-button" type="button" aria-label="清空对话">⌫</button>
                <button class="delete-button" type="button" aria-label="删除对话">×</button>
            </div>
        `;

        row.querySelector(".conversation-main").addEventListener("click", () => switchConversation(conversation.id));
        row.querySelector(".rename-button").addEventListener("click", () => startRenameConversation(conversation.id));
        row.querySelector(".clear-button").addEventListener("click", () => clearConversation(conversation.id));
        row.querySelector(".delete-button").addEventListener("click", () => deleteConversation(conversation.id));
        elements.conversationList.appendChild(row);
    });
}

function startRenameConversation(conversationId) {
    renamingConversationId = conversationId;
    render();
}

function cancelRenameConversation() {
    renamingConversationId = null;
    render();
}

function commitRenameConversation(conversationId, nextTitle) {
    const conversation = state.conversations.find((item) => item.id === conversationId);
    if (!conversation) {
        renamingConversationId = null;
        render();
        return;
    }

    const trimmedTitle = (nextTitle || "").trim();
    conversation.title = trimmedTitle || "未命名对话";
    conversation.updatedAt = Date.now();
    state.lastUsage = `${conversation.title} 已重命名`;
    renamingConversationId = null;
    queuePersist();
    render();
}

function renderMessages() {
    const active = getActiveConversation();
    const hasMessages = active.messages.length > 0;
    elements.emptyState.classList.toggle("hidden", hasMessages);
    elements.messageList.innerHTML = "";

    active.messages.forEach((message) => {
        const row = document.createElement("div");
        row.className = `message-row ${message.role}`;
        const errorClass = message.error ? " error" : "";
        const pendingClass = message.pending ? " pending" : "";
        const label = message.role === "user" ? "你" : getAssistantDisplayName(active);
        const metaText = resolveMessageMetaText(message);
        const metaMarkup = metaText ? `<div class="bubble-meta">${metaText}</div>` : "";

        row.innerHTML = `
            <article class="bubble ${message.role}${errorClass}${pendingClass}">
                <span class="bubble-label">${label}</span>
                <div class="bubble-content">${renderMessageContent(message.content)}</div>
                ${metaMarkup}
            </article>
        `;
        elements.messageList.appendChild(row);
    });

    elements.messageViewport.scrollTop = hasMessages ? elements.messageViewport.scrollHeight : 0;
}

function getAssistantDisplayName(conversation) {
    const selectedSkillId = conversation?.skillIds?.[0];
    if (!selectedSkillId) {
        return "Chat";
    }
    return findSkillById(selectedSkillId)?.name || "Chat";
}

function renderSkillComposer() {
    const active = getActiveConversation();
    const selectedSkillId = active?.skillIds[0] || "";
    const options = availableSkills.map((skill) => `<option value="${escapeAttribute(skill.id)}">${escapeHtml(skill.name)}</option>`).join("");
    elements.skillSelect.innerHTML = `<option value="">无 skill</option>${options}`;
    elements.skillSelect.value = selectedSkillId;
    elements.skillSelect.disabled = busy || availableSkills.length === 0;
}

function renderSkillsManager() {
    if (ui.view !== "skills") {
        return;
    }

    renderSkillEditorState();

    if (availableSkills.length === 0) {
        elements.skillsManagerList.innerHTML = `<div class="skill-library-empty">当前账号还没有 skills，可以先创建一个。</div>`;
        return;
    }

    elements.skillsManagerList.innerHTML = "";
    availableSkills.forEach((skill) => {
        const row = document.createElement("article");
        row.className = "skill-library-item";
        row.innerHTML = `
            <div class="skill-library-item-head">
                <div>
                    <p class="skill-library-name">${escapeHtml(skill.name)}</p>
                    <p class="skill-library-description">${escapeHtml(skill.description || "无简介")}</p>
                </div>
                <div class="skill-library-actions">
                    <button class="skill-library-edit" type="button" aria-label="查看或编辑 ${escapeAttribute(skill.name)}">查看/编辑</button>
                    <button class="skill-library-delete" type="button" aria-label="删除 ${escapeAttribute(skill.name)}">删除</button>
                </div>
            </div>
        `;
        row.querySelector(".skill-library-edit").addEventListener("click", () => startSkillEditing(skill.id));
        row.querySelector(".skill-library-delete").addEventListener("click", () => handleDeleteSkill(skill.id));
        elements.skillsManagerList.appendChild(row);
    });
}

function renderSkillEditorState() {
    const isEditing = Boolean(ui.editingSkillId);
    elements.skillEditorModeText.textContent = isEditing ? "Edit" : "Create";
    elements.skillEditorTitle.textContent = isEditing ? "编辑 Skill" : "新增 Skill";
    elements.skillSubmitButton.textContent = isEditing ? "保存修改" : "新增 Skill";
    elements.cancelSkillEditButton.hidden = !isEditing;
}

function findSkillById(skillId) {
    return availableSkills.find((skill) => skill.id === skillId);
}

function handleSkillSelectChange(event) {
    const nextSkillId = event.target.value;
    const active = getActiveConversation();
    const previousSkillId = active.skillIds[0] || "";
    if (nextSkillId === previousSkillId) {
        render();
        return;
    }

    active.skillIds = nextSkillId ? [nextSkillId] : [];
    active.updatedAt = Date.now();
    state.lastUsage = nextSkillId
        ? `${findSkillById(nextSkillId)?.name || nextSkillId} 已启用`
        : `${findSkillById(previousSkillId)?.name || "Skill"} 已移除`;
    queuePersist();
    render();
}

async function handleCreateSkillSubmit(event) {
    event.preventDefault();
    const skillName = elements.skillNameInput.value.trim();
    const skillDescription = elements.skillDescriptionInput.value.trim();
    const systemPrompt = elements.skillPromptInput.value.trim();
    if (!skillName || !systemPrompt) {
        auth.statusText = "技能名称和 System Prompt 不能为空。";
        render();
        return;
    }

    try {
        const isEditing = Boolean(ui.editingSkillId);
        const response = await apiFetch(
            isEditing ? `${SKILLS_API_URL}/${encodeURIComponent(ui.editingSkillId)}` : SKILLS_API_URL,
            {
                method: isEditing ? "PUT" : "POST",
                body: JSON.stringify({skillName, skillDescription, systemPrompt})
            }
        );
        const data = await readResponseData(response);
        if (!response.ok) {
            throw new Error(data.message || (isEditing ? "更新技能失败" : "创建技能失败"));
        }

        resetSkillForm();
        await refreshSkills();
        auth.statusText = `${data.name || skillName} ${isEditing ? "已更新" : "已创建"}。`;
        render();
    } catch (error) {
        auth.statusText = error.message || (ui.editingSkillId ? "更新技能失败" : "创建技能失败");
        render();
    }
}

async function startSkillEditing(skillId) {
    try {
        const response = await apiFetch(`${SKILLS_API_URL}/${encodeURIComponent(skillId)}`);
        const data = await readResponseData(response);
        if (!response.ok) {
            throw new Error(data.message || "读取技能详情失败");
        }

        ui.editingSkillId = data.id;
        elements.skillNameInput.value = data.name || "";
        elements.skillDescriptionInput.value = data.description || "";
        elements.skillPromptInput.value = data.systemPrompt || "";
        auth.statusText = `${data.name || "Skill"} 已载入编辑器。`;
        render();
        elements.skillNameInput.focus();
    } catch (error) {
        auth.statusText = error.message || "读取技能详情失败";
        render();
    }
}

function cancelSkillEditing() {
    resetSkillForm();
    auth.statusText = "已取消技能编辑。";
    render();
}

async function handleDeleteSkill(skillId) {
    try {
        const response = await apiFetch(`${SKILLS_API_URL}/${encodeURIComponent(skillId)}`, {
            method: "DELETE"
        });
        if (!response.ok) {
            const data = await readResponseData(response);
            throw new Error(data.message || "删除技能失败");
        }

        removeDeletedSkillFromConversations(skillId);
        await refreshSkills();
        auth.statusText = "技能已删除。";
        render();
    } catch (error) {
        auth.statusText = error.message || "删除技能失败";
        render();
    }
}

function resetSkillForm() {
    elements.skillEditorForm.reset();
    elements.skillPromptInput.value = runtimeConfig.defaultSkillPromptTemplate || "";
    ui.editingSkillId = null;
}

function openRedeemModal() {
    ui.redeemModalOpen = true;
    elements.redeemCodeInput.value = "";
    elements.redeemStatusText.textContent = "";
    render();
    elements.redeemCodeInput.focus();
}

function closeRedeemModal() {
    ui.redeemModalOpen = false;
    render();
}

function resetRedeemModal() {
    if (elements.redeemCodeInput) {
        elements.redeemCodeInput.value = "";
    }
    if (elements.redeemStatusText) {
        elements.redeemStatusText.textContent = "";
    }
}

async function handleRedeemSubmit() {
    const redeemCode = elements.redeemCodeInput.value.trim().toUpperCase();
    if (!redeemCode) {
        elements.redeemStatusText.textContent = "请输入兑换码。";
        return;
    }

    try {
        const response = await apiFetch(REDEEM_API_URL, {
            method: "POST",
            body: JSON.stringify({redeemCode})
        });
        const data = await readResponseData(response);
        if (!response.ok) {
            throw new Error(data.message || "兑换失败");
        }

        auth.user.points = Number.isFinite(data.points) ? data.points : auth.user.points;
        elements.redeemStatusText.textContent = `兑换成功，当前积分：${formatPoints(auth.user.points)}`;
        state.lastUsage = `兑换成功，当前积分 ${formatPoints(auth.user.points)}`;
        render();
    } catch (error) {
        elements.redeemStatusText.textContent = error.message || "兑换失败";
    }
}

async function refreshSkills() {
    availableSkills = await hydrateSkills();
    sanitizeConversationSkillIds();
}

function sanitizeConversationSkillIds() {
    const validSkillIds = new Set(availableSkills.map((skill) => skill.id));
    let changed = false;
    state.conversations.forEach((conversation) => {
        const currentSkillId = conversation.skillIds[0];
        if (currentSkillId && !validSkillIds.has(currentSkillId)) {
            conversation.skillIds = [];
            conversation.updatedAt = Date.now();
            changed = true;
        }
    });

    if (changed && auth.token) {
        state.lastUsage = "部分已删除的技能引用已自动清理。";
        queuePersist();
    }
}

function removeDeletedSkillFromConversations(skillId) {
    let changed = false;
    state.conversations.forEach((conversation) => {
        if (conversation.skillIds.includes(skillId)) {
            conversation.skillIds = [];
            conversation.updatedAt = Date.now();
            changed = true;
        }
    });

    if (changed) {
        state.lastUsage = "已删除技能，并清理相关会话引用。";
        queuePersist();
    }

    if (ui.editingSkillId === skillId) {
        resetSkillForm();
    }
}

function handleComposerKeydown(event) {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        elements.chatForm.requestSubmit();
    }
}

function autoResizeTextarea() {
    elements.messageInput.style.height = "0";
    elements.messageInput.style.height = `${Math.min(elements.messageInput.scrollHeight, 240)}px`;
}

// -----------------------------------------------------------------------------
// 发送消息主流程
// -----------------------------------------------------------------------------

async function handleSubmit(event) {
    event.preventDefault();
    if (busy || !auth.user || auth.user.points <= 0) {
        if (auth.user && auth.user.points <= 0) {
            state.lastUsage = "积分不足，无法发送消息。";
            render();
        }
        return;
    }

    const content = elements.messageInput.value.trim();
    if (!content) {
        return;
    }

    const active = getActiveConversation();
    const userMessage = {
        id: crypto.randomUUID(),
        role: "user",
        content,
        createdAt: Date.now()
    };
    const assistantPlaceholder = createPendingAssistantMessage();

    active.messages.push(userMessage, assistantPlaceholder);
    if (active.messages.length === 2) {
        active.title = deriveTitleFromContent(content);
    }
    active.subtitle = shortenText(content, 36);
    active.updatedAt = Date.now();
    state.lastUsage = "请求已发送";
    busy = true;
    elements.messageInput.value = "";
    queuePersist();
    render();

    try {
        const response = await apiFetch(CHAT_API_URL, {
            method: "POST",
            body: JSON.stringify({
                conversationId: active.id,
                skillIds: active.skillIds,
                messages: buildPayloadMessages(active)
            })
        });

        const data = await readResponseData(response);
        if (!response.ok) {
            throw new Error(data.message || "请求失败");
        }

        applyAssistantSuccess(assistantPlaceholder, data);
        active.updatedAt = Date.now();
        active.subtitle = shortenText(data.content, 36);
        auth.user.points = Number.isFinite(data.remainingPoints) ? data.remainingPoints : auth.user.points;
        state.lastUsage = `回复完成，剩余积分 ${formatPoints(auth.user.points)}`;
    } catch (error) {
        applyAssistantFailure(assistantPlaceholder, error);
        active.updatedAt = Date.now();
        active.subtitle = "请求失败，请查看配置";
        if (String(error.message || "").includes("积分不足")) {
            auth.user.points = 0;
            state.lastUsage = "积分不足，无法发送消息。";
        } else {
            state.lastUsage = error.message || "请求失败，请检查服务端配置。";
        }
    } finally {
        busy = false;
        queuePersist();
        render();
    }
}

function createPendingAssistantMessage() {
    const assistantName = getAssistantDisplayName(getActiveConversation());
    return {
        id: crypto.randomUUID(),
        role: "assistant",
        content: `正在等待 ${assistantName} 响应`,
        pending: true,
        createdAt: Date.now(),
        meta: null
    };
}

function applyAssistantSuccess(message, data) {
    message.content = data.content;
    message.pending = false;
    message.meta = summarizeUsage(data);
}

function applyAssistantFailure(message, error) {
    message.pending = false;
    message.error = true;
    message.content = error.message || "请求失败";
    message.meta = "请求失败";
}

function buildPayloadMessages(conversation) {
    const dialogue = conversation.messages
        .filter((message) => !message.pending)
        .map((message) => ({
            role: message.role,
            content: message.content
        }));

    return [
        {
            role: "system",
            content: runtimeConfig.systemPrompt
        },
        ...dialogue
    ];
}

function summarizeUsage(data) {
    return data.latencyMs ? `${data.latencyMs} ms` : "已完成";
}

function resolveMessageMetaText(message) {
    if (message.pending) {
        return message.meta || "";
    }
    return message.meta || formatTime(message.createdAt);
}

function deriveTitleFromContent(content) {
    return shortenText(content.replace(/\s+/g, " "), 16) || "新对话";
}

function shortenText(content, maxLength) {
    const normalized = (content || "").trim();
    if (normalized.length <= maxLength) {
        return normalized || "等待第一条消息";
    }
    return `${normalized.slice(0, maxLength)}...`;
}

function parseResponseBody(raw) {
    if (!raw) {
        return {};
    }
    try {
        return JSON.parse(raw);
    } catch (error) {
        return {message: raw};
    }
}

function formatTime(timestamp) {
    if (!timestamp) {
        return "--:--";
    }
    return new Intl.DateTimeFormat("zh-CN", {
        hour: "2-digit",
        minute: "2-digit"
    }).format(timestamp);
}

function renderMessageContent(content) {
    const source = String(content || "");
    const markdownTokenPattern = /!\[([^\]]*)\]\((https?:\/\/[^\s)]+)\)|\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g;
    let result = "";
    let cursor = 0;
    let match;

    while ((match = markdownTokenPattern.exec(source)) !== null) {
        result += escapeHtml(source.slice(cursor, match.index));

        if (match[1] != null && match[2] != null) {
            result += renderImageToken(match[1], match[2]);
        } else if (match[3] != null && match[4] != null) {
            result += renderLinkToken(match[3], match[4]);
        }

        cursor = markdownTokenPattern.lastIndex;
    }

    result += escapeHtml(source.slice(cursor));
    return result;
}

function renderImageToken(alt, rawUrl) {
    const safeUrl = sanitizeUrl(rawUrl);
    if (!safeUrl) {
        return escapeHtml(`![${alt}](${rawUrl})`);
    }

    const safeAlt = escapeAttribute(alt || "图片");
    const safeHref = escapeAttribute(safeUrl);
    return `
        <a class="message-image-link" href="${safeHref}" target="_blank" rel="noreferrer noopener">
            <img class="message-image" src="${safeHref}" alt="${safeAlt}" loading="lazy" referrerpolicy="no-referrer">
        </a>
    `;
}

function renderLinkToken(label, rawUrl) {
    const safeUrl = sanitizeUrl(rawUrl);
    if (!safeUrl) {
        return escapeHtml(`[${label}](${rawUrl})`);
    }

    return `<a class="message-link" href="${escapeAttribute(safeUrl)}" target="_blank" rel="noreferrer noopener">${escapeHtml(label)}</a>`;
}

function sanitizeUrl(value) {
    try {
        const parsed = new URL(value);
        if (parsed.protocol === "http:" || parsed.protocol === "https:") {
            return parsed.toString();
        }
        return null;
    } catch (error) {
        return null;
    }
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;")
        .replaceAll("\n", "<br>");
}

function escapeAttribute(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("\"", "&quot;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}
