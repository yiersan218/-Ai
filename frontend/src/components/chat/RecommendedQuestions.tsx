import { ArrowUpRight, RotateCw, Sparkles } from "lucide-react";

import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import type { Message } from "@/types";

interface RecommendedQuestionsProps {
  message: Message;
}

/**
 * 追问推荐面板：内联渲染于助手消息操作行下方
 * 展开态由 message.recommendedOpen 驱动 数据/加载态挂在消息上 天然按消息隔离
 */
export function RecommendedQuestions({ message }: RecommendedQuestionsProps) {
  const sendMessage = useChatStore((state) => state.sendMessage);
  const isStreaming = useChatStore((state) => state.isStreaming);
  const loadRecommended = useChatStore((state) => state.loadRecommended);

  const open = Boolean(message.recommendedOpen);
  const state = message.recommendedState;
  const questions = message.recommended ?? [];

  if (!open) {
    return null;
  }

  // 面板内联展开于消息内，不主动滚动：虚拟列表（react-virtuoso）里调用原生 scrollIntoView 会打乱其滚动记账、
  // 导致条目错位与空白区域，展开后若超出视口由用户自行下滑
  return (
    <div className="animate-fade-up overflow-hidden rounded-2xl border border-[#EFEFEF] bg-[#FAFAFA] p-1.5">
      <div className="flex items-center gap-1.5 px-2.5 pt-1.5 pb-1">
        <Sparkles className="h-3.5 w-3.5 text-[#3B82F6]" />
        <span className="text-xs font-medium text-[#666666]">猜你想问</span>
      </div>

      {state === "loading" ? (
        <ul className="space-y-0.5" aria-label="推荐问题加载中">
          {[68, 52, 60].map((width, idx) => (
            <li key={idx} className="px-3 py-2.5">
              <div
                className="h-3.5 rounded-full bg-[#ECECEC] animate-pulse"
                style={{ width: `${width}%` }}
              />
            </li>
          ))}
        </ul>
      ) : null}

      {state === "error" ? (
        <div className="flex items-center justify-between gap-3 px-3 py-2.5">
          <span className="text-sm text-[#999999]">推荐问题加载失败</span>
          <button
            type="button"
            onClick={() => loadRecommended(message.id)}
            className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-[#2563EB] transition-colors hover:bg-[#EAF1FF]"
          >
            <RotateCw className="h-3 w-3" />
            重试
          </button>
        </div>
      ) : null}

      {state === "ready" && questions.length === 0 ? (
        <div className="px-3 py-2.5 text-sm text-[#999999]">暂无推荐问题</div>
      ) : null}

      {state === "ready" && questions.length > 0 ? (
        <ul className="space-y-0.5">
          {questions.map((question, idx) => (
            <li
              key={`${idx}-${question}`}
              className="animate-fade-up [animation-fill-mode:both]"
              style={{ animationDelay: `${idx * 45}ms` }}
            >
              <button
                type="button"
                disabled={isStreaming}
                onClick={() => {
                  if (isStreaming) return;
                  // 发起提问 sendMessage 内部会收起所有历史推荐面板
                  void sendMessage(question);
                }}
                title={question}
                className={cn(
                  "group/rq flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-left transition-all",
                  isStreaming
                    ? "cursor-not-allowed opacity-50"
                    : "hover:bg-white hover:shadow-[0_2px_10px_rgba(0,0,0,0.05)]"
                )}
              >
                <span className="flex-1 text-sm leading-relaxed text-[#4A4A4A] transition-colors group-hover/rq:text-[#1A1A1A]">
                  {question}
                </span>
                <ArrowUpRight
                  className="h-4 w-4 shrink-0 -translate-x-1 text-[#C4C4C4] opacity-0 transition-all group-hover/rq:translate-x-0 group-hover/rq:text-[#3B82F6] group-hover/rq:opacity-100"
                  aria-hidden="true"
                />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
