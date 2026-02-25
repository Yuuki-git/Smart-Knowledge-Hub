<template>
  <div class="min-h-screen text-slate-100">
    <div class="grid-glow min-h-screen">
      <header class="px-6 py-6 lg:px-12">
        <div class="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p class="mono text-emerald-300 text-xs tracking-[0.35em]">SMART KNOWLEDGE HUB</p>
            <h1 class="mt-3 text-3xl font-semibold lg:text-4xl">Spring Cloud 分布式架构智能助理</h1>
            <p class="mt-2 text-slate-300">
              多源解析、混合检索与可溯源对话。上传你的文档，让答案具备证据。
            </p>
          </div>
          <div class="flex flex-wrap gap-3">
            <button class="glass rounded-full px-4 py-2 text-sm" @click="resetSession">
              新会话
            </button>
            <button class="glass rounded-full px-4 py-2 text-sm" @click="clearChat">
              清空对话
            </button>
          </div>
        </div>
      </header>

      <main class="grid gap-6 px-6 pb-10 lg:grid-cols-[360px_minmax(0,1fr)] lg:px-12">
        <section class="glass rounded-xl p-6 shadow-soft">
          <h2 class="text-lg font-semibold">数据入库</h2>
          <p class="mt-2 text-sm text-slate-300">支持 PDF / Markdown / Java 源码。</p>

          <div class="mt-4 rounded-xl border border-dashed border-slate-700 p-4">
            <input
              ref="fileInput"
              type="file"
              class="w-full text-sm text-slate-200 file:mr-3 file:rounded-full file:border-0 file:bg-emerald-400/20 file:px-4 file:py-2 file:text-emerald-200"
              @change="onFileSelected"
            />
            <button
              class="mt-4 w-full rounded-lg bg-emerald-400/80 px-4 py-2 text-sm font-semibold text-ink hover:bg-emerald-300 disabled:cursor-not-allowed disabled:bg-emerald-500/30"
              :disabled="!selectedFile || uploading"
              @click="uploadFile"
            >
              {{ uploading ? "上传中..." : "上传并入库" }}
            </button>
          </div>

          <div v-if="uploadStatus" class="mt-4 rounded-lg bg-slate-900/60 p-3 text-xs text-slate-200">
            <div>文件: {{ uploadStatus.fileName }}</div>
            <div>Job: {{ uploadStatus.jobId }}</div>
            <div>状态: {{ uploadStatus.status }}</div>
          </div>

          <div class="mt-6">
            <h3 class="text-sm font-semibold text-slate-200">检索配置</h3>
            <div class="mt-3 grid gap-3">
              <label class="text-xs uppercase tracking-widest text-slate-400">Top K</label>
              <input
                v-model.number="topK"
                type="number"
                min="1"
                max="20"
                class="mono rounded-lg border border-slate-700 bg-slate-900/70 px-3 py-2 text-sm"
              />
              <label class="text-xs uppercase tracking-widest text-slate-400">模型</label>
              <select
                v-model="modelProvider"
                class="rounded-lg border border-slate-700 bg-slate-900/70 px-3 py-2 text-sm"
              >
                <option value="AUTO">AUTO</option>
                <option value="DEEPSEEK">DEEPSEEK</option>
                <option value="OPENAI">OPENAI</option>
                <option value="OLLAMA">OLLAMA</option>
              </select>
            </div>
          </div>
        </section>

        <section class="glass rounded-xl p-6 shadow-soft">
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-semibold">对话流</h2>
            <span class="mono text-xs text-emerald-200">SSE Streaming</span>
          </div>
          <div class="mt-4 h-[520px] space-y-4 overflow-y-auto pr-2">
            <div v-for="(msg, idx) in messages" :key="idx" class="rounded-xl border border-slate-800 bg-slate-900/70 p-4">
              <div class="flex items-center justify-between text-xs uppercase tracking-widest text-slate-400">
                <span>{{ msg.role === "user" ? "用户" : "助手" }}</span>
                <span>{{ formatTime(msg.timestamp) }}</span>
              </div>
              <p class="mt-3 whitespace-pre-wrap text-sm text-slate-100">{{ msg.content }}</p>
              <div v-if="msg.citations && msg.citations.length" class="mt-3 border-t border-slate-800 pt-3">
                <p class="text-xs text-slate-400">来源</p>
                <ul class="mt-2 space-y-2 text-xs text-slate-300">
                  <li v-for="(cit, cidx) in msg.citations" :key="cidx" class="mono">
                    {{ cit.sourceRef || "unknown" }}
                  </li>
                </ul>
              </div>
            </div>

            <div v-if="streaming" class="rounded-xl border border-emerald-400/40 bg-slate-900/60 p-4">
              <div class="flex items-center justify-between text-xs uppercase tracking-widest text-emerald-300">
                <span>助手</span>
                <span>Streaming</span>
              </div>
              <p class="mt-3 whitespace-pre-wrap text-sm text-emerald-100">{{ streamingBuffer }}</p>
            </div>
          </div>

          <form class="mt-4 flex flex-col gap-3 lg:flex-row" @submit.prevent="sendMessage">
            <textarea
              v-model="question"
              rows="3"
              class="flex-1 rounded-xl border border-slate-700 bg-slate-900/70 p-3 text-sm text-slate-100"
              placeholder="输入你的问题，例如：Spring Cloud Nacos 配置中心集群部署指南"
            ></textarea>
            <button
              class="rounded-xl bg-emerald-400/90 px-6 py-3 text-sm font-semibold text-ink hover:bg-emerald-300 disabled:cursor-not-allowed disabled:bg-emerald-500/30"
              :disabled="!question || streaming"
            >
              {{ streaming ? "生成中..." : "发送" }}
            </button>
          </form>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";

const apiBase = import.meta.env.VITE_API_BASE || "";
const sessionId = ref(loadSessionId());
const messages = ref([]);
const question = ref("");
const streaming = ref(false);
const streamingBuffer = ref("");
const modelProvider = ref("AUTO");
const topK = ref(5);

const selectedFile = ref(null);
const fileInput = ref(null);
const uploading = ref(false);
const uploadStatus = ref(null);

function loadSessionId() {
  const existing = localStorage.getItem("skh-session");
  if (existing) {
    return existing;
  }
  const created = crypto.randomUUID();
  localStorage.setItem("skh-session", created);
  return created;
}

function resetSession() {
  const created = crypto.randomUUID();
  localStorage.setItem("skh-session", created);
  sessionId.value = created;
  messages.value = [];
  streamingBuffer.value = "";
}

function clearChat() {
  messages.value = [];
  streamingBuffer.value = "";
}

function formatTime(ts) {
  if (!ts) return "刚刚";
  const date = new Date(ts);
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
}

function onFileSelected(event) {
  const [file] = event.target.files || [];
  selectedFile.value = file || null;
}

async function uploadFile() {
  if (!selectedFile.value) return;
  uploading.value = true;
  uploadStatus.value = null;

  const form = new FormData();
  form.append("file", selectedFile.value);

  try {
    const response = await fetch(`${apiBase}/api/files/upload`, {
      method: "POST",
      body: form
    });
    const data = await response.json();
    uploadStatus.value = {
      fileName: selectedFile.value.name,
      jobId: data.jobId,
      status: data.status
    };
    pollJobStatus(data.jobId);
  } catch (error) {
    uploadStatus.value = {
      fileName: selectedFile.value.name,
      jobId: "-",
      status: "FAILED"
    };
  } finally {
    uploading.value = false;
    if (fileInput.value) {
      fileInput.value.value = "";
    }
    selectedFile.value = null;
  }
}

async function pollJobStatus(jobId) {
  if (!jobId) return;
  const timer = setInterval(async () => {
    const res = await fetch(`${apiBase}/api/ingestion/${jobId}`);
    const data = await res.json();
    uploadStatus.value = {
      ...uploadStatus.value,
      status: data.status
    };
    if (data.status === "INDEXED" || data.status === "FAILED" || data.status === "EMPTY") {
      clearInterval(timer);
    }
  }, 1500);
}

async function sendMessage() {
  if (!question.value || streaming.value) return;
  const content = question.value.trim();
  if (!content) return;

  messages.value.push({
    role: "user",
    content,
    timestamp: new Date().toISOString()
  });

  streaming.value = true;
  streamingBuffer.value = "";
  question.value = "";

  const payload = {
    sessionId: sessionId.value,
    question: content,
    modelProvider: modelProvider.value,
    topK: topK.value
  };

  try {
    const response = await fetch(`${apiBase}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!response.body) {
      throw new Error("No stream");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split("\n\n");
      buffer = events.pop() || "";
      for (const rawEvent of events) {
        handleSseEvent(rawEvent);
      }
    }
  } catch (error) {
    messages.value.push({
      role: "assistant",
      content: "请求失败，请检查后端服务或网络。",
      timestamp: new Date().toISOString()
    });
  } finally {
    streaming.value = false;
    streamingBuffer.value = "";
  }
}

function handleSseEvent(rawEvent) {
  const lines = rawEvent.split("\n").filter(Boolean);
  let eventType = "message";
  let data = "";
  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventType = line.replace("event:", "").trim();
    } else if (line.startsWith("data:")) {
      data += line.replace("data:", "").trim();
    }
  }
  if (!data) return;
  const parsed = JSON.parse(data);
  if (eventType === "delta") {
    streamingBuffer.value += parsed.content || "";
  }
  if (eventType === "final") {
    const finalText = parsed.content || streamingBuffer.value;
    messages.value.push({
      role: "assistant",
      content: finalText,
      citations: parsed.citations || [],
      timestamp: new Date().toISOString()
    });
  }
}

onMounted(() => {
  messages.value = [];
});
</script>
