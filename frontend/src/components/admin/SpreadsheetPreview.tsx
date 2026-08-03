import { useEffect, useRef, useState } from "react";
import jsPreviewExcel, { type JsExcelPreview } from "@js-preview/excel";
import "@js-preview/excel/lib/index.css";

import { fetchDocumentFile } from "@/services/knowledgeService";

interface SpreadsheetPreviewProps {
  docId: string;
}

// xlsx/xls 在线预览：基于 @js-preview/excel(底层 exceljs + x-data-spreadsheet)
// 保留多 sheet 页签、合并单元格与单元格样式，直接渲染源文件二进制
export function SpreadsheetPreview({ docId }: SpreadsheetPreviewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    let previewer: JsExcelPreview | null = null;
    let cancelled = false;
    setLoading(true);
    setError(false);

    (async () => {
      try {
        const buffer = await fetchDocumentFile(docId);
        if (cancelled || !containerRef.current) {
          return;
        }
        previewer = jsPreviewExcel.init(containerRef.current);
        await previewer.preview(buffer);
      } catch {
        if (!cancelled) {
          setError(true);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
      previewer?.destroy();
    };
  }, [docId]);

  return (
    <div className="relative flex-1 overflow-hidden">
      {loading ? (
        <div className="absolute inset-0 z-10 flex items-center justify-center text-muted-foreground">加载中...</div>
      ) : null}
      {error ? (
        <div className="absolute inset-0 z-10 flex items-center justify-center text-muted-foreground">表格预览失败</div>
      ) : null}
      <div ref={containerRef} className="h-full w-full" />
    </div>
  );
}
