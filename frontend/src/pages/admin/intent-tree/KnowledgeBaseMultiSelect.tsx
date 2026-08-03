import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Database, Search, X } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import type { KnowledgeBase } from "@/services/knowledgeService";

interface KnowledgeBaseMultiSelectProps {
  knowledgeBases: KnowledgeBase[];
  value: string[];
  onChange: (collectionNames: string[]) => void;
  disabled?: boolean;
  placeholder?: string;
}

export function KnowledgeBaseMultiSelect({
  knowledgeBases,
  value,
  onChange,
  disabled,
  placeholder = "选择知识库…"
}: KnowledgeBaseMultiSelectProps) {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);

  const selected = useMemo(() => new Set(value), [value]);

  // 打开后聚焦搜索框，rAF 保证在 Radix 默认聚焦首个选项之后执行
  useEffect(() => {
    if (!open) return;
    const raf = requestAnimationFrame(() => searchRef.current?.focus());
    return () => cancelAnimationFrame(raf);
  }, [open]);

  // 已选知识库按选择顺序保留，缺失的 collectionName 也占位展示
  const selectedItems = useMemo(
    () =>
      value.map((collectionName) => ({
        collectionName,
        knowledgeBase: knowledgeBases.find((item) => item.collectionName === collectionName)
      })),
    [value, knowledgeBases]
  );

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return knowledgeBases;
    return knowledgeBases.filter((item) =>
      `${item.name} ${item.collectionName}`.toLowerCase().includes(kw)
    );
  }, [knowledgeBases, keyword]);

  const toggle = (collectionName: string, checked: boolean) => {
    if (checked) {
      onChange([...value, collectionName]);
      return;
    }
    onChange(value.filter((item) => item !== collectionName));
  };

  const selectAll = () => onChange(knowledgeBases.map((item) => item.collectionName));
  const clearAll = () => onChange([]);

  return (
    <DropdownMenu
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setKeyword("");
      }}
    >
      <DropdownMenuTrigger asChild disabled={disabled}>
        <div
          role="button"
          tabIndex={disabled ? -1 : 0}
          aria-disabled={disabled}
          className={cn(
            "flex min-h-10 w-full flex-wrap items-center gap-1.5 rounded-md border border-input bg-background px-2.5 py-1.5 text-sm ring-offset-background transition-colors",
            "focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
            "data-[state=open]:ring-2 data-[state=open]:ring-ring data-[state=open]:ring-offset-2",
            disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer hover:border-slate-300"
          )}
        >
          {selectedItems.length === 0 ? (
            <span className="text-muted-foreground">{placeholder}</span>
          ) : (
            selectedItems.map(({ collectionName, knowledgeBase }) => (
              <span
                key={collectionName}
                className="inline-flex max-w-[16rem] items-center gap-1 rounded-md border border-indigo-100 bg-indigo-50 py-0.5 pl-2 pr-1 text-xs font-medium text-indigo-700"
              >
                <span className="truncate">{knowledgeBase?.name || collectionName}</span>
                <button
                  type="button"
                  tabIndex={-1}
                  className="flex h-4 w-4 shrink-0 items-center justify-center rounded-sm text-indigo-400 transition-colors hover:bg-indigo-100 hover:text-indigo-700"
                  aria-label={`移除 ${knowledgeBase?.name || collectionName}`}
                  onPointerDown={(event) => {
                    // 阻止触发器展开，仅执行移除
                    event.preventDefault();
                    event.stopPropagation();
                  }}
                  onClick={(event) => {
                    event.stopPropagation();
                    toggle(collectionName, false);
                  }}
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))
          )}
          <ChevronDown className="ml-auto h-4 w-4 shrink-0 opacity-50" />
        </div>
      </DropdownMenuTrigger>

      <DropdownMenuContent
        align="start"
        className="w-[var(--radix-dropdown-menu-trigger-width)] p-0"
      >
        <div className="flex items-center gap-2 border-b border-border px-2.5 py-2">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
          <input
            ref={searchRef}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape") {
                setOpen(false);
                return;
              }
              // 阻断 Radix 菜单的字母跳转，保证搜索框正常输入
              event.stopPropagation();
            }}
            placeholder="搜索知识库名称或 Collection…"
            className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          />
        </div>

        {knowledgeBases.length > 0 ? (
          <div className="flex items-center justify-between px-2.5 py-1.5 text-xs text-muted-foreground">
            <span>
              已选 <span className="font-medium text-foreground">{value.length}</span> / {knowledgeBases.length}
            </span>
            <div className="flex items-center gap-3">
              <button
                type="button"
                className="font-medium text-indigo-600 transition-colors hover:text-indigo-700 disabled:opacity-40"
                disabled={value.length === knowledgeBases.length}
                onClick={selectAll}
              >
                全选
              </button>
              <button
                type="button"
                className="font-medium text-slate-500 transition-colors hover:text-slate-700 disabled:opacity-40"
                disabled={value.length === 0}
                onClick={clearAll}
              >
                清空
              </button>
            </div>
          </div>
        ) : null}

        <div className="max-h-64 overflow-y-auto p-1">
          {knowledgeBases.length === 0 ? (
            <div className="flex flex-col items-center gap-1 px-2 py-8 text-center">
              <Database className="h-5 w-5 text-muted-foreground/60" />
              <span className="text-sm text-muted-foreground">暂无可用知识库</span>
            </div>
          ) : filtered.length === 0 ? (
            <div className="px-2 py-8 text-center text-sm text-muted-foreground">
              未找到匹配「{keyword}」的知识库
            </div>
          ) : (
            filtered.map((knowledgeBase) => (
              <DropdownMenuCheckboxItem
                key={knowledgeBase.id}
                checked={selected.has(knowledgeBase.collectionName)}
                onCheckedChange={(checked) =>
                  toggle(knowledgeBase.collectionName, checked === true)
                }
                onSelect={(event) => event.preventDefault()}
                className="items-start gap-3 py-2 pl-8 pr-2 data-[state=checked]:bg-indigo-50/60"
              >
                <div className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate text-sm font-medium text-foreground">
                    {knowledgeBase.name}
                  </span>
                  <span className="truncate font-mono text-xs text-muted-foreground">
                    {knowledgeBase.collectionName}
                  </span>
                </div>
                {typeof knowledgeBase.documentCount === "number" ? (
                  <span className="shrink-0 self-center rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-500">
                    {knowledgeBase.documentCount} 文档
                  </span>
                ) : null}
              </DropdownMenuCheckboxItem>
            ))
          )}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
