import * as React from "react";
import { Brain, Send, Square } from "lucide-react";

import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";

export function ChatInput() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const {
    sendMessage,
    isStreaming,
    cancelGeneration,
    deepThinkingEnabled,
    setDeepThinkingEnabled,
    inputFocusKey
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
    if (!inputFocusKey) return;
    focusInput();
  }, [inputFocusKey, focusInput]);

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
    <div>
      <div
        className={cn(
          "relative flex flex-col rounded-[24px] border bg-white/95 p-3 shadow-[0_12px_34px_rgba(15,23,42,0.09)] backdrop-blur-xl transition-[border-color,box-shadow] duration-200 sm:p-4",
          isFocused
            ? "border-indigo-300 shadow-[0_14px_40px_rgba(79,70,229,0.13)] ring-4 ring-indigo-100/60"
            : "border-slate-200 hover:border-indigo-200"
        )}
      >
        <div className="relative">
          <label htmlFor="chat-composer" className="sr-only">
            向知源 AI 提问
          </label>
          <Textarea
            id="chat-composer"
            ref={textareaRef}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder={
              deepThinkingEnabled
                ? "继续描述需要深入分析的问题..."
                : "继续提问，或补充更多上下文..."
            }
            className="max-h-40 min-h-[52px] w-full resize-none border-0 bg-transparent px-2 py-2 text-base leading-6 text-slate-800 shadow-none placeholder:text-slate-400 focus-visible:ring-0 sm:px-3"
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
            aria-label="聊天输入框"
          />
        </div>
        <div className="mt-2 flex items-center gap-2 border-t border-slate-100 pt-3">
          <button
            type="button"
            onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
            disabled={isStreaming}
            aria-pressed={deepThinkingEnabled}
            aria-label={deepThinkingEnabled ? "关闭深度思考" : "开启深度思考"}
            className={cn(
              "inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-xl border px-3 text-sm font-medium transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 motion-reduce:transition-none",
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
              <span className="hidden sm:inline">
                {deepThinkingEnabled ? "深度思考已开启" : "深度思考"}
              </span>
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
                  ? "bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-[0_8px_22px_rgba(79,70,229,0.22)] hover:from-indigo-700 hover:to-violet-700"
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
      <span className="sr-only" aria-live="polite">
        {deepThinkingEnabled ? "深度思考模式已开启" : "深度思考模式已关闭"}
      </span>
    </div>
  );
}
