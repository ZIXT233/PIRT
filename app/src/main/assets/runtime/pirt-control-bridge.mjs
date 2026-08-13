import readline from "node:readline";
import { mkdir, readFile, stat, writeFile, unlink } from "node:fs/promises";
import { createRequire } from "node:module";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { randomUUID } from "node:crypto";

const moduleUrl = process.env.PI_CODING_AGENT_MODULE
  ? pathToFileURL(process.env.PI_CODING_AGENT_MODULE).href
  : "file:///usr/local/lib/node_modules/@earendil-works/pi-coding-agent/dist/index.js";
const {
  createAgentSessionFromServices, createAgentSessionRuntime, createAgentSessionServices,
  ModelRuntime, SessionManager, SettingsManager,
} = await import(moduleUrl);
const moduleRequire = createRequire(moduleUrl);
const { Type } = await import(pathToFileURL(moduleRequire.resolve("typebox")).href);

const cwd = "/workspace";
const sessionDir = "/root/.pi/pirt-sessions";
const agentDir = process.env.PIRT_AGENT_DIR || `${process.env.HOME || "/root"}/.pi/agent`;
const modelsPath = `${agentDir}/models.json`;
const modelRuntime = await ModelRuntime.create({
  authPath: `${agentDir}/auth.json`, modelsPath,
  // Keep startup deterministic without placing the whole Pi process in offline
  // mode. The latter also blocks intentional `pi install` calls from the AI.
  allowModelNetwork: false, modelRefreshTimeoutMs: 15_000, refreshOnCreate: false,
});
const settings = SettingsManager.create(cwd, agentDir);

const PIRT_ENVIRONMENT_PROMPT = `PIRT is an integrated system combining a PRoot Linux environment, the Pi agent framework, runtime and supporting infrastructure, and an app-based conversation frontend. Its name reflects Pi Runtime and PRoot.

You run as an agent inside PIRT's PRoot Linux environment.

All conversations work in the same shared /workspace directory. When starting a distinct new project, prefer creating it in a new subdirectory under /workspace to keep projects organized.

PIRT's local graphical desktop uses the fixed X display :100. For tasks involving desktop inspection, screenshots, or GUI interaction, prefer DISPLAY=:100. If display :100 is not available or cannot be detected, ask the user to start the Desktop service from the PIRT app sidebar.

If a user message begins with "/" and clearly appears to be an attempt to run a Pi command, do not claim to execute it as chat text. Briefly tell the user to use the leftmost "/" command entry above the message box. Do not apply this instruction to ordinary prose, paths, URLs, or other non-command uses of slashes.

The following system prompt is provided by the underlying Pi agent framework. Treat it as the operational instructions for working inside PIRT:`;

function pirtEnvironmentExtension(pi) {
  pi.on("before_agent_start", async (event) => ({
    systemPrompt: `${PIRT_ENVIRONMENT_PROMPT}\n\n${event.systemPrompt}`,
  }));
}

const agents = new Map();
const pendingPrompts = new Map();
const pendingExtensionUi = new Map();
const loginControllers = new Map();
let modelsLoaded = false;
let modelsLoadPromise = null;
let modelsGeneration = 0;

const MAX_SENT_IMAGE_BYTES = 20 * 1024 * 1024;
const BLOCKED_PIRT_COMMANDS = new Set([
  "new", "fork", "clone", "tree", "model", "models", "thinking", "thinking-level",
  "compact", "session", "resume", "delete", "rename", "provider", "providers", "login", "logout",
]);
const SendImageParams = Type.Object({
  path: Type.String({
    description: "Absolute path or workspace-relative path of the local image to send",
  }),
}, { additionalProperties: false });

function detectImageMime(data) {
  if (data.length >= 8 && data.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return "image/png";
  }
  if (data.length >= 3 && data[0] === 0xff && data[1] === 0xd8 && data[2] === 0xff) return "image/jpeg";
  if (data.length >= 6 && (data.subarray(0, 6).toString("ascii") === "GIF87a" || data.subarray(0, 6).toString("ascii") === "GIF89a")) {
    return "image/gif";
  }
  if (data.length >= 12 && data.subarray(0, 4).toString("ascii") === "RIFF" && data.subarray(8, 12).toString("ascii") === "WEBP") {
    return "image/webp";
  }
  return null;
}

const sendImageTool = {
  name: "send_image",
  label: "Send image",
  description: "Send an existing local image file to the user as an inline chat image. Use this for delivery; read only inspects an image for the agent.",
  promptSnippet: "Send an existing PNG, JPEG, GIF, or WebP file to the user.",
  promptGuidelines: [
    "When the user asks to see or receive an image file, call send_image with its path instead of relying on read output.",
  ],
  parameters: SendImageParams,
  async execute(_toolCallId, params, _signal, _onUpdate, context) {
    const imagePath = resolve(context.cwd, params.path);
    const metadata = await stat(imagePath);
    if (!metadata.isFile()) throw new Error(`Not a file: ${imagePath}`);
    if (metadata.size > MAX_SENT_IMAGE_BYTES) {
      throw new Error(`Image is too large to send (${metadata.size} bytes; limit ${MAX_SENT_IMAGE_BYTES} bytes)`);
    }
    const data = await readFile(imagePath);
    const mimeType = detectImageMime(data);
    if (!mimeType) throw new Error(`Unsupported image format: ${imagePath}. Use PNG, JPEG, GIF, or WebP.`);
    return {
      content: [
        { type: "text", text: `Sent image: ${imagePath}` },
        { type: "image", data: data.toString("base64"), mimeType },
      ],
      details: { path: imagePath, mimeType, size: data.length },
    };
  },
};

function send(value) { process.stdout.write(`${JSON.stringify(value)}\n`); }
function errorDetails(error) {
  const details = [];
  const seen = new Set();
  let current = error;
  while (current && !seen.has(current)) {
    seen.add(current);
    const message = current instanceof Error ? current.message : String(current);
    const code = typeof current?.code === "string" ? current.code : null;
    const hostname = typeof current?.hostname === "string" ? current.hostname : null;
    const entry = [code, message, hostname ? `host=${hostname}` : null].filter(Boolean).join(": ");
    if (entry && !details.includes(entry)) details.push(entry);
    current = current?.cause;
  }
  return details.join("; ") || "未知错误";
}

function loginErrorMessage(error) {
  const detail = errorDetails(error);
  if (/ENOTFOUND|EAI_AGAIN|getaddrinfo/i.test(detail)) {
    return `Codex 登录后续请求的 DNS 解析失败：${detail}`;
  }
  if (/CERT_|certificate|self[- ]signed|unable to verify/i.test(detail)) {
    return `Codex 登录后续请求的 TLS 证书校验失败：${detail}`;
  }
  return `Codex 登录后续网络请求失败：${detail}`;
}

function reply(command, success, dataOrError) {
  send(success
    ? { type: "response", id: command.id, command: command.type, success: true, data: dataOrError }
    : { type: "response", id: command.id, command: command.type, success: false, error: String(dataOrError) });
}

async function ensureModelsLoaded() {
  while (!modelsLoaded) {
    if (!modelsLoadPromise) {
      const generation = modelsGeneration;
      modelsLoadPromise = modelRuntime.refresh({ allowNetwork: false }).then(() => {
        if (generation === modelsGeneration) modelsLoaded = true;
      }).finally(() => { modelsLoadPromise = null; });
    }
    await modelsLoadPromise;
  }
}

function invalidateModels() {
  modelsGeneration += 1;
  modelsLoaded = false;
}

function modelRecord(model) {
  return {
    provider: model.provider,
    id: model.id,
    name: model.name,
    reasoning: model.reasoning,
    input: model.input,
    contextWindow: model.contextWindow,
  };
}

function authTypes(provider) {
  const types = [];
  if (provider.auth?.apiKey?.login) types.push("api_key");
  if (provider.auth?.oauth) types.push("oauth");
  return types;
}

async function providers() {
  const credentials = await modelRuntime.listCredentials();
  const configured = new Map(credentials.map((value) => [value.providerId, value.type]));
  const file = await readModelsFile();
  return modelRuntime.getProviders().map((provider) => {
    const fromFile = file.providers?.[provider.id];
    const hasModelsKey = typeof fromFile?.apiKey === "string" && fromFile.apiKey.trim().length > 0;
    const configuredAuth = configured.get(provider.id) || (hasModelsKey ? "api_key" : null);
    return {
      id: provider.id,
      name: provider.name || fromFile?.name || provider.id,
      authTypes: authTypes(provider),
      configuredAuth,
      status: modelRuntime.getProviderAuthStatus(provider.id),
      custom: !!fromFile,
    };
  });
}

function defaultModel() {
  return {
    selectedProvider: settings.getDefaultProvider() || null,
    selectedModel: settings.getDefaultModel() || null,
  };
}

function clearPendingPrompts(reason = "登录已取消") {
  for (const [, pending] of pendingPrompts) {
    try { pending.reject(new Error(reason)); } catch { /* ignore */ }
  }
  pendingPrompts.clear();
}

function abortAllLogins(reason = "登录已取消") {
  for (const [, controller] of loginControllers) {
    try { controller.abort(); } catch { /* ignore */ }
  }
  loginControllers.clear();
  clearPendingPrompts(reason);
}

function requestInput(loginId, prompt) {
  const promptId = crypto.randomUUID();
  send({ type: "auth_prompt", loginId, promptId, prompt });
  return new Promise((resolve, reject) => {
    const abort = () => {
      pendingPrompts.delete(promptId);
      reject(new Error("登录已取消"));
    };
    prompt.signal?.addEventListener("abort", abort, { once: true });
    pendingPrompts.set(promptId, { resolve, reject });
  });
}

function promptForLogin(command, prompt) {
  const requestedMethod = command.loginMethod;
  const options = Array.isArray(prompt?.options) ? prompt.options : [];
  const canSelectRequestedMethod =
    command.providerId === "openai-codex" &&
    prompt?.type === "select" &&
    (requestedMethod === "browser" || requestedMethod === "device_code") &&
    options.some((option) => option?.id === requestedMethod);
  if (canSelectRequestedMethod) return Promise.resolve(requestedMethod);
  return requestInput(command.id, prompt);
}

async function login(command) {
  // Codex 浏览器登录失败后若残留旧 login/prompt，下一次会一直卡在半截状态。
  abortAllLogins("已开始新的登录");
  const controller = new AbortController();
  loginControllers.set(command.id, controller);
  try {
    await modelRuntime.login(command.providerId, command.authType, {
      signal: controller.signal,
      prompt: (prompt) => promptForLogin(command, prompt),
      notify: (event) => send({ type: "auth_event", loginId: command.id, event }),
    });
    invalidateModels();
    reply(command, true, { providers: await providers(), ...defaultModel() });
  } catch (error) {
    clearPendingPrompts(error?.message || "登录失败");
    if (command.providerId === "openai-codex" && !controller.signal.aborted) {
      throw new Error(loginErrorMessage(error));
    }
    throw error;
  } finally {
    loginControllers.delete(command.id);
  }
}

function sanitizeProviderId(value) {
  const id = String(value || "custom")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
  return id || "custom";
}

function parseModelIds(value) {
  const raw = Array.isArray(value) ? value : String(value || "").split(/[\n,]/);
  return [...new Set(raw.map((item) => String(item || "").trim()).filter(Boolean))];
}

async function fetchOpenAiCompatibleModels(baseUrl, apiKey) {
  const url = `${baseUrl.replace(/\/+$/, "")}/models`;
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${apiKey}`,
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    throw new Error(`拉取模型失败：HTTP ${response.status}${detail ? `（${detail.slice(0, 160)}）` : ""}`);
  }
  const body = await response.json();
  const list = Array.isArray(body?.data) ? body.data : Array.isArray(body) ? body : [];
  const ids = [...new Set(list.map((item) => {
    if (typeof item === "string") return item.trim();
    if (item && typeof item === "object") return String(item.id || item.name || "").trim();
    return "";
  }).filter(Boolean))];
  if (ids.length === 0) throw new Error("接口未返回可用模型，请确认 Base URL 是否为 OpenAI 兼容的 /v1 地址");
  return ids;
}

async function readModelsFile() {
  try {
    const parsed = JSON.parse(await readFile(modelsPath, "utf8"));
    if (!parsed || typeof parsed !== "object") return { providers: {} };
    if (!parsed.providers || typeof parsed.providers !== "object") parsed.providers = {};
    return parsed;
  } catch {
    return { providers: {} };
  }
}

async function writeModelsFile(data) {
  await mkdir(agentDir, { recursive: true });
  await writeFile(modelsPath, `${JSON.stringify(data, null, 2)}\n`, "utf8");
}

async function configureCustomProvider(command) {
  const providerId = sanitizeProviderId(command.providerId || command.name || "custom");
  const name = String(command.name || providerId).trim() || providerId;
  const baseUrl = String(command.baseUrl || "").trim().replace(/\/+$/, "");
  const apiKey = String(command.apiKey || "").trim();
  if (!baseUrl) throw new Error("请填写 Base URL");
  if (!apiKey) throw new Error("请填写 API Key");

  let modelIds = parseModelIds(command.models);
  if (modelIds.length === 0) {
    modelIds = await fetchOpenAiCompatibleModels(baseUrl, apiKey);
  }

  const file = await readModelsFile();
  const provider = {
    name,
    baseUrl,
    api: "openai-completions",
    apiKey,
    authHeader: true,
    compat: {
      supportsDeveloperRole: false,
      supportsReasoningEffort: false,
    },
    models: modelIds.map((id) => ({
      id,
      name: id,
      reasoning: false,
      input: ["text"],
      contextWindow: 128000,
      maxTokens: 8192,
      cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
    })),
  };
  file.providers[providerId] = provider;
  await writeModelsFile(file);
  invalidateModels();
  await ensureModelsLoaded();
  settings.setDefaultModelAndProvider(providerId, modelIds[0]);
  await settings.flush();
  reply(command, true, {
    providers: await providers(),
    ...defaultModel(),
    providerId,
    modelId: modelIds[0],
    modelCount: modelIds.length,
  });
}

async function removeCustomProvider(command) {
  const providerId = sanitizeProviderId(command.providerId);
  const file = await readModelsFile();
  if (!file.providers[providerId]) throw new Error(`未找到自定义服务：${providerId}`);
  delete file.providers[providerId];
  await writeModelsFile(file);
  try { await modelRuntime.logout(providerId); } catch { /* optional */ }
  invalidateModels();
  await ensureModelsLoaded();
  reply(command, true, { providers: await providers(), ...defaultModel() });
}

function wireEvent(key, event) {
  if (event.type === "message_update" && event.assistantMessageEvent && "partial" in event.assistantMessageEvent) {
    const { partial: _partial, ...assistantMessageEvent } = event.assistantMessageEvent;
    send({ ...event, assistantMessageEvent, sessionKey: key });
  } else send({ ...event, sessionKey: key });
}

function extensionUiEvent(entry, request) {
  send({ type: "extension_ui_request", sessionKey: entry.key, id: randomUUID(), ...request });
}

const portableTheme = new Proxy({
  fg: (_color, text) => text,
  bg: (_color, text) => text,
  bold: (text) => text,
  italic: (text) => text,
  underline: (text) => text,
  inverse: (text) => text,
  strikethrough: (text) => text,
  getFgAnsi: () => "",
  getBgAnsi: () => "",
  getColorMode: () => "dark",
  getThinkingBorderColor: () => (text) => text,
  getBashModeBorderColor: () => (text) => text,
}, { get: (target, property) => property in target ? target[property] : ((...args) => args.at(-1) ?? "") });

function extensionDialog(entry, options, defaultValue, request, parseResponse) {
  if (options?.signal?.aborted) return Promise.resolve(defaultValue);
  const id = randomUUID();
  return new Promise((resolve) => {
    let timeoutId;
    let settled = false;
    const finish = (response, cancelledByRuntime = false) => {
      if (settled) return;
      settled = true;
      if (timeoutId) clearTimeout(timeoutId);
      options?.signal?.removeEventListener("abort", onAbort);
      pendingExtensionUi.delete(id);
      if (cancelledByRuntime) send({ type: "extension_ui_cancel", sessionKey: entry.key, id });
      resolve(response == null ? defaultValue : parseResponse(response));
    };
    const onAbort = () => finish(null, true);
    options?.signal?.addEventListener("abort", onAbort, { once: true });
    if (options?.timeout) timeoutId = setTimeout(() => finish(null, true), options.timeout);
    pendingExtensionUi.set(id, { entry, finish });
    send({ type: "extension_ui_request", sessionKey: entry.key, id, ...request, timeout: options?.timeout });
  });
}

function createExtensionUiContext(entry) {
  const setEditorText = (text) => extensionUiEvent(entry, { method: "set_editor_text", text: String(text) });
  return {
    select: (title, options, dialogOptions) => extensionDialog(
      entry, dialogOptions, undefined, { method: "select", title, options },
      (response) => response.cancelled ? undefined : response.value,
    ),
    confirm: (title, message, dialogOptions) => extensionDialog(
      entry, dialogOptions, false, { method: "confirm", title, message },
      (response) => response.cancelled ? false : response.confirmed === true,
    ),
    input: (title, placeholder, dialogOptions) => extensionDialog(
      entry, dialogOptions, undefined, { method: "input", title, placeholder },
      (response) => response.cancelled ? undefined : response.value,
    ),
    editor: (title, prefill) => extensionDialog(
      entry, undefined, undefined, { method: "editor", title, prefill },
      (response) => response.cancelled ? undefined : response.value,
    ),
    notify: (message, type) => extensionUiEvent(entry, { method: "notify", message, notifyType: type }),
    onTerminalInput: () => () => {},
    setStatus: (key, text) => extensionUiEvent(entry, { method: "setStatus", statusKey: key, statusText: text }),
    setWorkingMessage: () => {},
    setWorkingVisible: () => {},
    setWorkingIndicator: () => {},
    setHiddenThinkingLabel: () => {},
    setWidget: (key, content, options) => {
      if (content === undefined || Array.isArray(content)) {
        extensionUiEvent(entry, { method: "setWidget", widgetKey: key, widgetLines: content, widgetPlacement: options?.placement });
      }
    },
    setFooter: () => {},
    setHeader: () => {},
    setTitle: (title) => extensionUiEvent(entry, { method: "setTitle", title }),
    custom: async () => undefined,
    pasteToEditor: setEditorText,
    setEditorText,
    getEditorText: () => "",
    addAutocompleteProvider: () => {},
    setEditorComponent: () => {},
    getEditorComponent: () => undefined,
    get theme() { return portableTheme; },
    getAllThemes: () => [],
    getTheme: () => undefined,
    setTheme: () => ({ success: false, error: "Theme switching is not supported by PIRT" }),
    getToolsExpanded: () => false,
    setToolsExpanded: () => {},
  };
}

function cancelExtensionUiForEntry(entry) {
  for (const [, pending] of pendingExtensionUi) {
    if (pending.entry === entry) pending.finish(null, true);
  }
}

async function createRuntimeSession({ cwd: effectiveCwd, sessionManager, sessionStartEvent }) {
  const runtimeSettings = SettingsManager.create(effectiveCwd, agentDir);
  const services = await createAgentSessionServices({
    cwd: effectiveCwd,
    agentDir,
    settingsManager: runtimeSettings,
    modelRuntime,
    resourceLoaderOptions: { extensionFactories: [pirtEnvironmentExtension] },
  });
  const created = await createAgentSessionFromServices({
    services,
    sessionManager,
    sessionStartEvent,
    customTools: [sendImageTool],
  });
  return { ...created, services, diagnostics: services.diagnostics };
}

async function bindRuntime(entry, session = entry.runtime.session) {
  entry.unsubscribe?.();
  cancelExtensionUiForEntry(entry);
  await session.bindExtensions({ uiContext: createExtensionUiContext(entry), mode: "rpc" });
  entry.unsubscribe = session.subscribe((event) => wireEvent(entry.key, event));
}

async function openAgent(command) {
  const existing = agents.get(command.sessionKey);
  if (existing) return state(existing.runtime.session);
  await ensureModelsLoaded();
  const manager = command.sessionPath
    ? SessionManager.open(command.sessionPath, sessionDir, cwd)
    : SessionManager.create(cwd, sessionDir);
  const entry = { key: command.sessionKey, runtime: null, unsubscribe: null };
  entry.runtime = await createAgentSessionRuntime(createRuntimeSession, {
    cwd: manager.getCwd(),
    agentDir,
    sessionManager: manager,
  });
  entry.runtime.setRebindSession(async (session) => bindRuntime(entry, session));
  agents.set(entry.key, entry);
  try {
    await bindRuntime(entry);
  } catch (error) {
    agents.delete(entry.key);
    await entry.runtime.dispose();
    throw error;
  }
  return state(entry.runtime.session);
}

function state(session) {
  return {
    model: session.model, thinkingLevel: session.thinkingLevel,
    isStreaming: session.isStreaming, isCompacting: session.isCompacting,
    sessionFile: session.sessionFile, sessionId: session.sessionId, sessionName: session.sessionName,
    autoCompactionEnabled: session.autoCompactionEnabled,
    messageCount: session.messages.length, pendingMessageCount: session.pendingMessageCount,
    steeringMessages: [...session.getSteeringMessages()],
  };
}

function requireEntry(command) {
  const value = agents.get(command.sessionKey);
  if (!value) throw new Error(`Session is not open: ${command.sessionKey}`);
  return value;
}

function messageEntries(session) {
  const entries = session.sessionManager.getBranch()
    .flatMap((entry) => {
      if (entry.type === "message") return [{
        entryId: entry.id,
        role: entry.message.role,
        toolName: entry.message.toolName,
        content: entry.message.content,
        errorMessage: entry.message.errorMessage,
      }];
      if (entry.type === "custom_message" && entry.display !== false) return [{
        entryId: entry.id,
        role: "custom",
        content: entry.content,
        customType: entry.customType,
      }];
      return [];
    });

  // Pi persists every failed provider attempt before auto-retrying it. Keep
  // those records as Pi-owned history, but only expose the final outcome of a
  // user turn to the Android chat UI. Otherwise returning to the foreground
  // reveals a stale "fetch failed" bubble even though a retry is still running
  // or has already succeeded.
  return entries.filter((entry, index) => {
    if (entry.role !== "assistant" || !entry.errorMessage) return true;

    let turnEnd = entries.length;
    for (let cursor = index + 1; cursor < entries.length; cursor += 1) {
      if (entries[cursor].role === "user") {
        turnEnd = cursor;
        break;
      }
    }
    const laterAssistant = entries
      .slice(index + 1, turnEnd)
      .filter((candidate) => candidate.role === "assistant");
    if (laterAssistant.length > 0) return false;

    const isActiveTurn = turnEnd === entries.length && (session.isStreaming || session.isRetrying);
    return !isActiveTurn;
  });
}

function replacement(entry, result) {
  const session = entry.runtime.session;
  const oldKey = entry.key;
  const newKey = session.sessionId;
  if (!newKey) throw new Error("Pi replacement session has no id");
  const collision = agents.get(newKey);
  if (collision && collision !== entry) throw new Error(`Session is already open: ${newKey}`);
  agents.delete(oldKey);
  entry.key = newKey;
  agents.set(newKey, entry);
  return {
    cancelled: false,
    selectedText: result.selectedText || null,
    sessionKey: newKey,
    state: state(session),
    messages: messageEntries(session),
  };
}

async function handleAgent(command) {
  if (command.type === "session_open") {
    reply(command, true, await openAgent(command));
    return true;
  }
  if (!command.sessionKey) return false;
  const entry = requireEntry(command);
  const session = entry.runtime.session;
  switch (command.type) {
    case "get_state": reply(command, true, state(session)); return true;
    case "get_messages":
    case "get_entries":
      reply(command, true, { messages: messageEntries(session), leafId: session.sessionManager.getLeafId() }); return true;
    case "prompt": {
      let replied = false;
      void session.prompt(command.message, {
        images: command.images, source: "rpc", expandPromptTemplates: false,
        preflightResult: (ok) => { if (ok && !replied) { replied = true; reply(command, true, {}); } },
      }).catch((error) => {
        if (!replied) reply(command, false, error?.message || error);
        else send({ type: "session_error", sessionKey: command.sessionKey, error: error?.message || String(error) });
      });
      return true;
    }
    case "execute_pi_command": {
      const text = String(command.text || "").trim();
      const match = text.match(/^\/([^\s]+)(?:\s+[\s\S]*)?$/);
      if (!match) throw new Error("请输入以 / 开头的 Pi 命令");
      const commandName = match[1];
      if (BLOCKED_PIRT_COMMANDS.has(commandName.toLowerCase())) {
        throw new Error(`/${commandName} 可能改变 PIRT 会话、模型或上下文，请使用应用内对应功能`);
      }
      const extensionNames = new Set(session.extensionRunner.getRegisteredCommands().map((item) => item.invocationName));
      const templateNames = new Set(session.promptTemplates.map((item) => item.name));
      const skillNames = new Set(session.resourceLoader.getSkills().skills.map((item) => `skill:${item.name}`));
      if (!extensionNames.has(commandName) && !templateNames.has(commandName) && !skillNames.has(commandName)) {
        throw new Error(`当前会话没有可执行命令：/${commandName}`);
      }
      await session.prompt(text, { source: "rpc" });
      reply(command, true, {});
      return true;
    }
    case "steer": {
      await session.prompt(command.message, {
        images: command.images,
        source: "rpc",
        expandPromptTemplates: false,
        streamingBehavior: "steer",
      });
      reply(command, true, {});
      return true;
    }
    case "abort": await session.abort(); reply(command, true, {}); return true;
    case "get_available_models": {
      await ensureModelsLoaded();
      reply(command, true, { models: modelRuntime.getAvailableSnapshot().map(modelRecord) });
      return true;
    }
    case "set_model": {
      const model = modelRuntime.getAvailableSnapshot().find((it) => it.provider === command.provider && it.id === command.modelId);
      if (!model) throw new Error(`Model not found: ${command.provider}/${command.modelId}`);
      await session.setModel(model);
      reply(command, true, {
        model,
        thinkingLevel: session.thinkingLevel,
        thinkingLevels: session.getAvailableThinkingLevels(),
      });
      return true;
    }
    case "get_available_thinking_levels": reply(command, true, { levels: session.getAvailableThinkingLevels() }); return true;
    case "set_thinking_level": session.setThinkingLevel(command.level); reply(command, true, {}); return true;
    case "get_session_stats": reply(command, true, session.getSessionStats()); return true;
    case "export_html": {
      const exportDir = `${cwd}/.pirt/exports`;
      await mkdir(exportDir, { recursive: true });
      const safeSessionId = String(session.sessionId || "session").replace(/[^a-zA-Z0-9_-]+/g, "-").slice(0, 64);
      const outputPath = `${exportDir}/pirt-${safeSessionId}-${Date.now()}.html`;
      reply(command, true, { path: await session.exportToHtml(outputPath) }); return true;
    }
    case "compact": await session.compact(command.customInstructions); reply(command, true, {}); return true;
    case "set_auto_compaction": session.setAutoCompactionEnabled(command.enabled); reply(command, true, {}); return true;
    case "set_auto_retry": session.setAutoRetryEnabled(command.enabled); reply(command, true, {}); return true;
    case "set_session_name": session.setSessionName(command.name.trim()); reply(command, true, {}); return true;
    case "fork": {
      const result = await entry.runtime.fork(command.entryId);
      reply(command, true, result.cancelled ? { cancelled: true } : replacement(entry, result));
      return true;
    }
    case "clone": {
      const leafId = session.sessionManager.getLeafId();
      if (!leafId) throw new Error("Cannot clone session: no current entry selected");
      const result = await entry.runtime.fork(leafId, { position: "at" });
      reply(command, true, result.cancelled ? { cancelled: true } : replacement(entry, result));
      return true;
    }
    case "get_commands": {
      const commands = [];
      for (const item of session.extensionRunner.getRegisteredCommands()) commands.push({ name: item.invocationName, description: item.description, source: "extension" });
      for (const item of session.promptTemplates) commands.push({ name: item.name, description: item.description, source: "prompt" });
      for (const item of session.resourceLoader.getSkills().skills) commands.push({ name: `skill:${item.name}`, description: item.description, source: "skill" });
      reply(command, true, { commands }); return true;
    }
    case "reload_runtime": {
      if (session.isStreaming || session.isCompacting) throw new Error("AI 正在运行，暂时不能重新加载运行资源");
      await session.reload({ beforeSessionStart: async () => bindRuntime(entry, session) });
      reply(command, true, {});
      return true;
    }
    case "extension_ui_response": {
      const pending = pendingExtensionUi.get(command.requestId);
      if (pending?.entry === entry) pending.finish(command);
      reply(command, true, {});
      return true;
    }
    default: return false;
  }
}

async function handle(command) {
  if (await handleAgent(command)) return;
  switch (command.type) {
    case "sessions_list": {
      const sessions = await SessionManager.list(cwd, sessionDir);
      reply(command, true, { sessions: sessions.map((session) => ({
        id: session.id, path: session.path, name: session.name || "", firstMessage: session.firstMessage || "",
        createdAt: session.created.getTime(), updatedAt: session.modified.getTime(), messageCount: session.messageCount,
      })) }); return;
    }
    case "session_rename": SessionManager.open(command.path, sessionDir, cwd).appendSessionInfo(command.name.trim()); reply(command, true, {}); return;
    case "session_delete": {
      for (const [key, value] of agents) {
        if (value.runtime.session.sessionFile === command.path) {
          cancelExtensionUiForEntry(value);
          value.unsubscribe?.();
          await value.runtime.dispose();
          agents.delete(key);
        }
      }
      await unlink(command.path); reply(command, true, {}); return;
    }
    case "providers": reply(command, true, { providers: await providers(), ...defaultModel() }); return;
    case "models": {
      await ensureModelsLoaded();
      const models = modelRuntime.getAvailableSnapshot().filter((model) => !command.providerId || model.provider === command.providerId);
      reply(command, true, { models: models.map(modelRecord), ...defaultModel() }); return;
    }
    case "select_model": settings.setDefaultModelAndProvider(command.providerId, command.modelId); await settings.flush(); reply(command, true, { providerId: command.providerId, modelId: command.modelId }); return;
    case "login": await login(command); return;
    case "configure_custom_provider": await configureCustomProvider(command); return;
    case "remove_custom_provider": await removeCustomProvider(command); return;
    case "logout": await modelRuntime.logout(command.providerId); invalidateModels(); reply(command, true, { providers: await providers(), ...defaultModel() }); return;
    case "auth_prompt_response": pendingPrompts.get(command.promptId)?.resolve(command.value); pendingPrompts.delete(command.promptId); return;
    case "cancel_login": {
      const controller = loginControllers.get(command.loginId);
      if (controller) controller.abort();
      else abortAllLogins("登录已取消");
      return;
    }
    default: throw new Error(`Unknown command: ${command.type}`);
  }
}

const lines = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
lines.on("line", (line) => {
  if (!line.trim()) return;
  let command;
  try { command = JSON.parse(line); } catch { send({ type: "error", error: "Invalid JSON command" }); return; }
  void handle(command).catch((error) => reply(command, false, errorDetails(error)));
});
process.on("SIGTERM", () => {
  void Promise.all([...agents.values()].map(async (entry) => {
    const { runtime, unsubscribe } = entry;
    cancelExtensionUiForEntry(entry);
    unsubscribe?.();
    await runtime.dispose();
  })).finally(() => process.exit(0));
});
send({ type: "ready" });
