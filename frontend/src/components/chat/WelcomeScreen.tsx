import * as React from "react";
import {
  ArrowUpRight,
  BookOpen,
  Bot,
  Brain,
  Check,
  FileSearch2,
  Lightbulb,
  Send,
  ShieldCheck,
  Sparkles,
  Square
} from "lucide-react";

import { cn } from "@/lib/utils";
import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chatStore";

type PromptPreset = {
  id?: string;
  title: string;
  description: string;
  prompt: string;
  icon: React.ComponentType<{ className?: string }>;
};

const PRESET_ICONS = [BookOpen, Check, Lightbulb];

const DEFAULT_PRESETS: PromptPreset[] = [
  {
    title: "内容总结",
    description: "提炼 3-5 条关键信息与行动点",
    prompt: "请帮我总结以下内容，并列出3-5条要点：",
    icon: BookOpen
  },
  {
    title: "任务拆解",
    description: "把目标拆成可执行步骤与优先级",
    prompt: "请把下面需求拆解为步骤，并给出优先级和里程碑：",
    icon: Check
  },
  {
    title: "灵感扩展",
    description: "给出多个方案并比较优缺点",
    prompt: "围绕以下主题给出5-8个方案，并注明优缺点：",
    icon: Lightbulb
  }
];

const CAPABILITIES = [
  { label: "企业知识检索", icon: FileSearch2 },
  { label: "答案来源可追溯", icon: ShieldCheck },
  { label: "复杂问题深度分析", icon: Brain }
];

export function WelcomeScreen() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [promptPresets, setPromptPresets] = React.useState<PromptPreset[]>(DEFAULT_PRESETS);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const {
    sendMessage,
    isStreaming,
    cancelGeneration,
    deepThinkingEnabled,
    setDeepThinkingEnabled
  } = useChatStore();

  const focusInput = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    const next = Math.min(el.scrollHeight, 160);
    el.style.height = `${next}px`;
  }, []);

  React.useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  React.useEffect(() => {
    let active = true;

    const loadPresets = async () => {
      const data = await listSampleQuestions().catch(() => null);
      if (!active || !data || data.length === 0) {
        return;
      }
      const mapped = data
        .filter((item) => item.question && item.question.trim())
        .slice(0, 3)
        .map((item, index) => {
          const question = item.question.trim();
          const title =
            item.title?.trim() ||
            (question.length > 12 ? `${question.slice(0, 12)}...` : question) ||
            `推荐问法 ${index + 1}`;
          const description = item.description?.trim() || "直接点选即可开始对话";
          return {
            id: item.id,
            title,
            description,
            prompt: question,
            icon: PRESET_ICONS[index % PRESET_ICONS.length]
          };
        });
      if (mapped.length > 0) {
        setPromptPresets(mapped);
      }
    };

    loadPresets();
    return () => {
      active = false;
    };
  }, []);

  const applyPreset = React.useCallback(
    (prompt: string) => {
      if (isStreaming) return;
      setValue(prompt);
      focusInput();
    },
    [isStreaming, focusInput]
  );

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      focusInput();
      return;
    }
    if (!value.trim()) return;
    const next = value;
    setValue("");
    focusInput();
    await sendMessage(next);
    focusInput();
  };

  const hasContent = value.trim().length > 0;

  return (
    <div className="relative h-full overflow-y-auto bg-slate-50 px-4 py-8 sm:px-6 sm:py-10 lg:py-12">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_50%_-10%,rgba(99,102,241,0.13),transparent_34%),linear-gradient(145deg,#f8fafc_0%,#ffffff_52%,#eef4ff_100%)]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-grid-pattern opacity-25 [background-size:40px_40px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-24 top-10 h-72 w-72 rounded-full bg-indigo-200/25 blur-3xl motion-safe:animate-float"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-32 -left-24 h-72 w-72 rounded-full bg-sky-200/25 blur-3xl motion-safe:animate-float"
      />

      <div className="relative mx-auto flex min-h-full w-full max-w-[960px] flex-col justify-center">
        <div
          className="text-center opacity-0 motion-safe:animate-fade-up motion-reduce:opacity-100"
          style={{ animationFillMode: "both" }}
        >
          <span className="inline-flex items-center gap-2 rounded-full border border-indigo-100 bg-white/85 px-3.5 py-1.5 text-xs font-semibold text-indigo-700 shadow-sm backdrop-blur">
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-indigo-50">
              <Bot className="h-3 w-3" aria-hidden="true" />
            </span>
            知源 AI · 企业知识助手
          </span>
          <h1 className="mt-5 font-display text-[34px] font-semibold leading-[1.12] tracking-[-0.035em] text-slate-950 sm:text-5xl lg:text-[54px]">
            从知识中，找到
            <span className="bg-gradient-to-r from-indigo-600 via-violet-600 to-sky-500 bg-clip-text text-transparent">
              可信答案
            </span>
          </h1>
          <p className="mx-auto mt-4 max-w-[660px] text-sm leading-6 text-slate-600 sm:text-base sm:leading-7">
            检索企业知识、核验信息来源，并将复杂问题整理成清晰、可执行的结论。
          </p>
          <div
            className="mt-5 flex flex-wrap items-center justify-center gap-2"
            aria-label="问答能力"
          >
            {CAPABILITIES.map((capability) => {
              const CapabilityIcon = capability.icon;
              return (
                <span
                  key={capability.label}
                  className="inline-flex items-center gap-1.5 rounded-full border border-slate-200/80 bg-white/70 px-3 py-1.5 text-xs font-medium text-slate-600 shadow-sm backdrop-blur"
                >
                  <CapabilityIcon className="h-3.5 w-3.5 text-indigo-500" aria-hidden="true" />
                  {capability.label}
                </span>
              );
            })}
          </div>
        </div>

        <div
          className="mt-7 opacity-0 motion-safe:animate-fade-up motion-reduce:opacity-100 sm:mt-8"
          style={{ animationDelay: "80ms", animationFillMode: "both" }}
        >
          <div
            className={cn(
              "relative flex flex-col rounded-[28px] border bg-white/95 p-3 shadow-[0_20px_60px_rgba(15,23,42,0.10)] backdrop-blur-xl transition-[border-color,box-shadow] duration-200 sm:p-4",
              isFocused
                ? "border-indigo-300 shadow-[0_20px_64px_rgba(79,70,229,0.16)] ring-4 ring-indigo-100/70"
                : "border-slate-200/90 hover:border-indigo-200"
            )}
          >
            <div className="relative">
              <label htmlFor="welcome-question" className="sr-only">
                向知源 AI 提问
              </label>
              <textarea
                id="welcome-question"
                ref={textareaRef}
                value={value}
                onChange={(event) => setValue(event.target.value)}
                placeholder={
                  deepThinkingEnabled
                    ? "描述需要深入分析的问题，我会结合知识库进行推理..."
                    : "输入问题，我会检索知识库并给出有依据的回答..."
                }
                className="max-h-40 min-h-[64px] w-full resize-none border-0 bg-transparent px-2 py-2 text-base leading-6 text-slate-800 placeholder:text-slate-400 focus:outline-none sm:min-h-[72px] sm:px-3"
                rows={1}
                onFocus={() => setIsFocused(true)}
                onBlur={() => setIsFocused(false)}
                onCompositionStart={() => {
                  isComposingRef.current = true;
                }}
                onCompositionEnd={() => {
                  isComposingRef.current = false;
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    const nativeEvent = event.nativeEvent as KeyboardEvent;
                    if (
                      nativeEvent.isComposing ||
                      isComposingRef.current ||
                      nativeEvent.keyCode === 229
                    ) {
                      return;
                    }
                    event.preventDefault();
                    handleSubmit();
                  }
                }}
                aria-label="发送消息"
              />
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-3">
              <button
                type="button"
                onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                disabled={isStreaming}
                aria-pressed={deepThinkingEnabled}
                className={cn(
                  "inline-flex min-h-11 cursor-pointer items-center rounded-xl border px-3 text-sm font-medium transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 motion-reduce:transition-none",
                  deepThinkingEnabled
                    ? "border-indigo-200 bg-indigo-50 text-indigo-700"
                    : "border-slate-200 bg-slate-50 text-slate-600 hover:border-slate-300 hover:bg-slate-100",
                  isStreaming && "cursor-not-allowed opacity-60"
                )}
              >
                <span className="inline-flex items-center gap-2">
                  <Brain
                    className={cn("h-4 w-4", deepThinkingEnabled && "text-indigo-600")}
                    aria-hidden="true"
                  />
                  {deepThinkingEnabled ? "深度思考已开启" : "深度思考"}
                  {deepThinkingEnabled ? (
                    <span className="h-1.5 w-1.5 rounded-full bg-indigo-500 motion-safe:animate-pulse" />
                  ) : null}
                </span>
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!hasContent && !isStreaming}
                aria-label={isStreaming ? "停止生成" : "发送消息"}
                className={cn(
                  "ml-auto inline-flex h-11 min-w-11 cursor-pointer items-center justify-center gap-2 rounded-xl px-3.5 text-sm font-semibold transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 motion-reduce:transition-none",
                  isStreaming
                    ? "bg-rose-50 text-rose-600 hover:bg-rose-100"
                    : hasContent
                      ? "bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-[0_8px_22px_rgba(79,70,229,0.24)] hover:from-indigo-700 hover:to-violet-700"
                      : "cursor-not-allowed bg-slate-100 text-slate-400"
                )}
              >
                {isStreaming ? (
                  <Square className="h-4 w-4" aria-hidden="true" />
                ) : (
                  <Send className="h-4 w-4" aria-hidden="true" />
                )}
                <span className="hidden sm:inline">{isStreaming ? "停止" : "发送"}</span>
              </button>
            </div>
          </div>
          {deepThinkingEnabled ? (
            <p className="mt-3 px-2 text-xs leading-5 text-indigo-600" role="status">
              <span className="inline-flex items-center gap-1.5">
                <Lightbulb className="h-3.5 w-3.5" aria-hidden="true" />
                将进行更深入的分析推理，回答时间可能稍长
              </span>
            </p>
          ) : null}
        </div>

        <div
          className="mt-8 opacity-0 motion-safe:animate-fade-up motion-reduce:opacity-100"
          style={{ animationDelay: "160ms", animationFillMode: "both" }}
        >
          <div className="flex items-center gap-2 px-1 text-sm font-semibold text-slate-700">
            <Sparkles className="h-4 w-4 text-indigo-500" aria-hidden="true" />
            你可以这样开始
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-3">
            {promptPresets.map((preset) => {
              const Icon = preset.icon;
              return (
                <button
                  key={preset.id ?? preset.title}
                  type="button"
                  onClick={() => applyPreset(preset.prompt)}
                  disabled={isStreaming}
                  className={cn(
                    "group min-h-[128px] cursor-pointer rounded-2xl border border-slate-200/80 bg-white/80 p-4 text-left shadow-sm backdrop-blur transition-[border-color,box-shadow,transform] duration-200 hover:-translate-y-0.5 hover:border-indigo-200 hover:bg-white hover:shadow-[0_14px_34px_rgba(15,23,42,0.08)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 motion-reduce:transform-none motion-reduce:transition-none",
                    isStreaming && "cursor-not-allowed opacity-60"
                  )}
                >
                  <div className="flex items-start gap-3">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 transition-colors group-hover:bg-indigo-100">
                      <Icon className="h-4 w-4" aria-hidden="true" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className="truncate text-sm font-semibold text-slate-900">
                          {preset.title}
                        </p>
                        <ArrowUpRight
                          className="ml-auto h-4 w-4 shrink-0 text-slate-300 transition-colors group-hover:text-indigo-500"
                          aria-hidden="true"
                        />
                      </div>
                      <p className="mt-1 text-xs leading-5 text-slate-500">{preset.description}</p>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
