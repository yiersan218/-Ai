import { SourceIcon } from "@/components/chat/SourceIcon";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import type { SourceRef } from "@/types";

interface SourcesButtonProps {
  messageId: string;
  sources: SourceRef[];
}

export function SourcesButton({ messageId, sources }: SourcesButtonProps) {
  const openedSourceMessageId = useChatStore((state) => state.openedSourceMessageId);
  const toggleSourcesPanel = useChatStore((state) => state.toggleSourcesPanel);

  if (!sources || sources.length === 0) {
    return null;
  }

  const active = openedSourceMessageId === messageId;
  const preview = sources.slice(0, 3);

  return (
    <button
      type="button"
      onClick={() => toggleSourcesPanel(messageId)}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full py-1 pl-1.5 pr-2.5 text-xs transition-colors",
        active
          ? "bg-[#F0F0F1] text-[#1A1A1A]"
          : "text-[#666666] hover:bg-[#F0F0F1] hover:text-[#1A1A1A]"
      )}
    >
      <span className="flex items-center">
        {preview.map((source, idx) => (
          <span
            key={`${source.docId}-${idx}`}
            className={cn(
              "flex h-5 w-5 items-center justify-center rounded-md bg-white ring-1 ring-[#EAEAEA]",
              idx > 0 && "-ml-1.5"
            )}
          >
            <SourceIcon source={source} className="h-3 w-3" />
          </span>
        ))}
      </span>
      {sources.length} 篇来源
    </button>
  );
}
