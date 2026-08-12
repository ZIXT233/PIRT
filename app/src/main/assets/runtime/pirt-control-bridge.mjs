import readline from "node:readline";
import { unlink } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const moduleUrl = process.env.PI_CODING_AGENT_MODULE
  ? pathToFileURL(process.env.PI_CODING_AGENT_MODULE).href
  : "file:///usr/local/lib/node_modules/@earendil-works/pi-coding-agent/dist/index.js";
const {
  createAgentSessionFromServices, createAgentSessionRuntime, createAgentSessionServices,
  ModelRuntime, SessionManager, SettingsManager,
} = await import(moduleUrl);

const cwd = "/workspace";
const sessionDir = "/root/.pi/pirt-sessions";
const agentDir = process.env.PIRT_AGENT_DIR || `${process.env.HOME || "/root"}/.pi/agent`;
const modelRuntime = await ModelRuntime.create({
  authPath: `${agentDir}/auth.json`, modelsPath: `${agentDir}/models.json`,
  allowModelNetwork: process.env.PI_OFFLINE !== "1", modelRefreshTimeoutMs: 15_000, refreshOnCreate: false,
});
const settings = SettingsManager.create(cwd, agentDir);

const agents = new Map();
const pendingPrompts = new Map();
const loginControllers = new Map();
let modelsLoaded = false;
let modelsLoadPromise = null;
let modelsGeneration = 0;

function send(value) { process.stdout.write(`${JSON.stringify(value)}\n`); }
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
  return modelRuntime.getProviders().map((provider) => ({
    id: provider.id, name: provider.name, authTypes: authTypes(provider),
    configuredAuth: configured.get(provider.id) || null, status: modelRuntime.getProviderAuthStatus(provider.id),
  }));
}

function defaultModel() {
  return {
    selectedProvider: settings.getDefaultProvider() || null,
    selectedModel: settings.getDefaultModel() || null,
  };
}

function requestInput(loginId, prompt) {
  const promptId = crypto.randomUUID();
  send({ type: "auth_prompt", loginId, promptId, prompt });
  return new Promise((resolve, reject) => {
    const abort = () => { pendingPrompts.delete(promptId); reject(new Error("登录已取消")); };
    prompt.signal?.addEventListener("abort", abort, { once: true });
    pendingPrompts.set(promptId, { resolve, reject });
  });
}

async function login(command) {
  const controller = new AbortController();
  loginControllers.set(command.id, controller);
  try {
    await modelRuntime.login(command.providerId, command.authType, {
      signal: controller.signal,
      prompt: (prompt) => requestInput(command.id, prompt),
      notify: (event) => send({ type: "auth_event", loginId: command.id, event }),
    });
    invalidateModels();
    reply(command, true, { providers: await providers() });
  } finally { loginControllers.delete(command.id); }
}

function wireEvent(key, event) {
  if (event.type === "message_update" && event.assistantMessageEvent && "partial" in event.assistantMessageEvent) {
    const { partial: _partial, ...assistantMessageEvent } = event.assistantMessageEvent;
    send({ ...event, assistantMessageEvent, sessionKey: key });
  } else send({ ...event, sessionKey: key });
}

async function createRuntimeSession({ cwd: effectiveCwd, sessionManager, sessionStartEvent }) {
  const runtimeSettings = SettingsManager.create(effectiveCwd, agentDir);
  const services = await createAgentSessionServices({
    cwd: effectiveCwd,
    agentDir,
    settingsManager: runtimeSettings,
    modelRuntime,
  });
  const created = await createAgentSessionFromServices({
    services,
    sessionManager,
    sessionStartEvent,
  });
  return { ...created, services, diagnostics: services.diagnostics };
}

async function bindRuntime(entry, session = entry.runtime.session) {
  entry.unsubscribe?.();
  await session.bindExtensions({ mode: "rpc" });
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
  await bindRuntime(entry);
  agents.set(entry.key, entry);
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
  return session.sessionManager.getBranch()
    .filter((entry) => entry.type === "message")
    .map((entry) => ({
      entryId: entry.id,
      role: entry.message.role,
      content: entry.message.content,
      errorMessage: entry.message.errorMessage,
    }));
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
        images: command.images, source: "rpc",
        preflightResult: (ok) => { if (ok && !replied) { replied = true; reply(command, true, {}); } },
      }).catch((error) => {
        if (!replied) reply(command, false, error?.message || error);
        else send({ type: "session_error", sessionKey: command.sessionKey, error: error?.message || String(error) });
      });
      return true;
    }
    case "steer": {
      await session.steer(command.message, command.images);
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
      await session.setModel(model); reply(command, true, model); return true;
    }
    case "get_available_thinking_levels": reply(command, true, { levels: session.getAvailableThinkingLevels() }); return true;
    case "set_thinking_level": session.setThinkingLevel(command.level); reply(command, true, {}); return true;
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
    case "logout": await modelRuntime.logout(command.providerId); invalidateModels(); reply(command, true, { providers: await providers(), ...defaultModel() }); return;
    case "auth_prompt_response": pendingPrompts.get(command.promptId)?.resolve(command.value); pendingPrompts.delete(command.promptId); return;
    case "cancel_login": loginControllers.get(command.loginId)?.abort(); return;
    default: throw new Error(`Unknown command: ${command.type}`);
  }
}

const lines = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
lines.on("line", (line) => {
  if (!line.trim()) return;
  let command;
  try { command = JSON.parse(line); } catch { send({ type: "error", error: "Invalid JSON command" }); return; }
  void handle(command).catch((error) => reply(command, false, error?.message || error));
});
process.on("SIGTERM", () => {
  void Promise.all([...agents.values()].map(async ({ runtime, unsubscribe }) => {
    unsubscribe?.();
    await runtime.dispose();
  })).finally(() => process.exit(0));
});
send({ type: "ready" });
