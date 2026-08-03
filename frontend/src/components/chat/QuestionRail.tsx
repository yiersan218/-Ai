import * as React from "react";

import { cn } from "@/lib/utils";

export interface QuestionRailItem {
  id: string;
  flatIndex: number;
  text: string;
}

interface QuestionRailProps {
  items: QuestionRailItem[];
  activeId: string | null;
  onSelect: (flatIndex: number) => void;
}

export function QuestionRail({ items, activeId, onSelect }: QuestionRailProps) {
  const [expanded, setExpanded] = React.useState(false);
  const activeRef = React.useRef<HTMLLIElement | null>(null);

  React.useEffect(() => {
    if (!expanded) return;
    activeRef.current?.scrollIntoView({ block: "nearest" });
  }, [expanded, activeId]);

  if (items.length < 2) return null;

  return (
    <div
      className="pointer-events-auto absolute right-3 top-1/2 z-30 -translate-y-1/2 hidden lg:block"
      onMouseEnter={() => setExpanded(true)}
      onMouseLeave={() => setExpanded(false)}
    >
      <div
        className={cn(
          "transition-all duration-200 ease-out",
          expanded
            ? "w-[260px] rounded-2xl border border-[#E5E5E5] bg-white px-1.5 py-2 shadow-lg"
            : "w-[28px] bg-transparent"
        )}
      >
        <ul
          className={cn(
            "flex max-h-[60vh] flex-col overflow-y-auto sidebar-scroll",
            expanded ? "items-stretch gap-0.5" : "items-end gap-[26px] py-2"
          )}
        >
          {items.map((item) => {
            const isActive = item.id === activeId;
            return (
              <li
                key={item.id}
                ref={isActive ? activeRef : null}
                className="list-none"
              >
                <button
                  type="button"
                  onClick={() => onSelect(item.flatIndex)}
                  className={cn(
                    "flex w-full items-center transition-colors",
                    expanded
                      ? "gap-3 rounded-md px-3 py-1.5 hover:bg-[#F5F5F5]"
                      : "justify-end"
                  )}
                  aria-label={item.text}
                >
                  {expanded ? (
                    <span
                      className={cn(
                        "flex-1 truncate text-left text-[13px] transition-colors",
                        isActive
                          ? "font-medium text-[#3B82F6]"
                          : "text-[#666666]"
                      )}
                    >
                      {item.text}
                    </span>
                  ) : null}
                  <span
                    aria-hidden="true"
                    className={cn(
                      "inline-block w-[14px] shrink-0 rounded-full transition-all",
                      isActive ? "h-[3px] bg-[#2563EB]" : "h-[1.5px] bg-[#D4D4D4]"
                    )}
                  />
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
