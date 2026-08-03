import * as React from "react";
import { useParams } from "react-router-dom";
import { FileText, Loader2 } from "lucide-react";

import { DocumentPreview } from "@/components/document/DocumentPreview";
import { getDocument } from "@/services/knowledgeService";

type DocMeta = Awaited<ReturnType<typeof getDocument>>;

export function DocPreviewPage() {
  const { docId } = useParams<{ docId: string }>();
  const [doc, setDoc] = React.useState<DocMeta | null>(null);
  const [status, setStatus] = React.useState<"loading" | "done" | "error">("loading");

  React.useEffect(() => {
    if (!docId) {
      setStatus("error");
      return;
    }
    let cancelled = false;
    setStatus("loading");
    getDocument(docId)
      .then((data) => {
        if (cancelled) return;
        setDoc(data);
        setStatus("done");
        document.title = `${data.docName || "文档"} - 来源预览`;
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [docId]);

  return (
    <div className="flex h-full flex-col bg-white">
      <header className="flex shrink-0 items-center gap-2 border-b border-[#EFEFEF] px-6 py-3.5">
        <FileText className="h-5 w-5 shrink-0 text-[#666666]" />
        <h1 className="truncate text-base font-medium text-[#1A1A1A]" title={doc?.docName || ""}>
          {doc?.docName || "文档预览"}
        </h1>
      </header>
      <div className="flex flex-1 flex-col overflow-hidden">
        {status === "loading" ? (
          <div className="flex flex-1 items-center justify-center gap-2 text-sm text-[#999999]">
            <Loader2 className="h-4 w-4 animate-spin" />
            正在加载…
          </div>
        ) : status === "error" || !doc || !docId ? (
          <div className="flex flex-1 items-center justify-center text-sm text-[#999999]">
            无法加载该文档，可能已被删除。
          </div>
        ) : (
          <DocumentPreview docId={docId} fileType={doc.fileType} docName={doc.docName} />
        )}
      </div>
    </div>
  );
}
