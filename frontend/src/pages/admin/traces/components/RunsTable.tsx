import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { CheckCircle2, Copy, Eye, LayoutPanelTop, Loader2, XCircle } from "lucide-react";
import { toast } from "sonner";
import { RelativeTime } from "@/components/RelativeTime";
import {
  getRagTraceNodes,
  type RagTraceNode,
  type RagTraceRun
} from "@/services/ragTraceService";
import { getErrorMessage } from "@/utils/error";
import {
  formatDuration,
  normalizeStatus,
  prettifyNodeName,
  resolveNodeDuration,
  statusBadgeVariant,
  statusLabel
} from "@/pages/admin/traces/traceUtils";

interface RunsTableProps {
  runs: RagTraceRun[];
  loading: boolean;
  current: number;
  pages: number;
  total: number;
  onOpenRun: (traceId: string) => void;
  onPrevPage: () => void;
  onNextPage: () => void;
}

const renderEmptyPlaceholder = () => (
  <span className="trace-list-empty-placeholder">—</span>
);

const renderTextOrEmpty = (value?: string | null, title?: string) => {
  const trimmed = (value ?? "").trim();
  if (!trimmed || trimmed === "-") return renderEmptyPlaceholder();
  return (
    <span className="trace-list-run-meta-line line-clamp-1" title={title ?? trimmed}>
      {trimmed}
    </span>
  );
};

const StatusBadge = ({ status }: { status?: string | null }) => {
  const normalized = normalizeStatus(status);
  let Icon = CheckCircle2;
  if (normalized === "failed" || normalized === "timeout") Icon = XCircle;
  else if (normalized === "running") Icon = Loader2;

  const isRunning = normalized === "running";
  return (
    <Badge className="trace-list-status-badge" variant={statusBadgeVariant(status)}>
      <Icon className={`h-3 w-3 ${isRunning ? "animate-spin" : ""}`} />
      <span>{statusLabel(status)}</span>
    </Badge>
  );
};

function TraceIdCell({ traceId }: { traceId: string }) {
  const handleCopy = async (event: React.MouseEvent) => {
    event.stopPropagation();
    try {
      await navigator.clipboard.writeText(traceId);
      toast.success("Trace Id 已复制");
    } catch {
      toast.error("复制失败");
    }
  };

  return (
    <div className="trace-list-trace-id-row">
      <span className="trace-list-trace-id-text">{traceId}</span>
      <button
        type="button"
        className="trace-list-trace-id-copy"
        onClick={handleCopy}
        aria-label="复制 Trace Id"
      >
        <Copy className="h-3 w-3" />
      </button>
    </div>
  );
}

interface BriefDialogProps {
  run: RagTraceRun | null;
  onClose: () => void;
  onOpenDetail: (traceId: string) => void;
}

interface NodeStat {
  nodeName: string;
  durationMs: number;
}

interface BriefStats {
  totalNodes: number;
  failedNodes: number;
  maxDepth: number;
  totalDurationMs: number;
  topSlowNodes: NodeStat[];
}

const NODE_ALIAS_GROUP: Record<string, string> = {
  "retrieval-engine": "retrieval-engine",
  "multi-channel-retrieval": "retrieval-engine"
};

const canonicalNodeKey = (rawName?: string | null): string => {
  const trimmed = (rawName || "").trim();
  if (!trimmed) return "";
  return NODE_ALIAS_GROUP[trimmed] || trimmed;
};

const computeBriefStats = (nodes: RagTraceNode[]): BriefStats => {
  const totalNodes = nodes.length;
  const failedNodes = nodes.filter((node) => {
    const s = normalizeStatus(node.status);
    return s === "failed" || s === "timeout";
  }).length;
  const maxDepth = nodes.reduce((max, node) => {
    const d = Number(node.depth ?? 0);
    return Number.isFinite(d) && d > max ? d : max;
  }, 0);
  const totalDurationMs = nodes.reduce(
    (sum, node) => sum + resolveNodeDuration(node),
    0
  );

  const grouped = new Map<string, NodeStat>();
  for (const node of nodes) {
    const duration = resolveNodeDuration(node);
    if (duration <= 0) continue;
    const key = canonicalNodeKey(node.nodeName);
    if (!key) continue;
    const existing = grouped.get(key);
    if (!existing || duration > existing.durationMs) {
      grouped.set(key, {
        nodeName: prettifyNodeName(key),
        durationMs: duration
      });
    }
  }
  const topSlowNodes = Array.from(grouped.values())
    .sort((a, b) => b.durationMs - a.durationMs)
    .slice(0, 3);

  return { totalNodes, failedNodes, maxDepth, totalDurationMs, topSlowNodes };
};

function BriefDialog({ run, onClose, onOpenDetail }: BriefDialogProps) {
  const [nodes, setNodes] = useState<RagTraceNode[] | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!run) {
      setNodes(null);
      return;
    }
    let active = true;
    setLoading(true);
    getRagTraceNodes(run.traceId)
      .then((data) => {
        if (!active) return;
        setNodes(data || []);
      })
      .catch((error) => {
        if (!active) return;
        toast.error(getErrorMessage(error, "加载链路节点失败"));
        setNodes([]);
      })
      .finally(() => {
        if (!active) return;
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [run]);

  if (!run) return null;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(run.traceId);
      toast.success("Trace Id 已复制");
    } catch {
      toast.error("复制失败");
    }
  };

  const stats = nodes ? computeBriefStats(nodes) : null;

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-3">
            <LayoutPanelTop className="h-4 w-4 text-indigo-500" />
            <span>链路概览</span>
            <StatusBadge status={run.status} />
          </DialogTitle>
        </DialogHeader>

        <div className="trace-brief-body">
          <div className="trace-brief-id-row">
            <span className="trace-brief-label">Trace Id</span>
            <div className="trace-brief-id-value">
              <span className="trace-brief-id-text">{run.traceId}</span>
              <button
                type="button"
                className="trace-brief-id-copy"
                onClick={handleCopy}
                aria-label="复制 Trace Id"
              >
                <Copy className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>

          {loading ? (
            <div className="trace-brief-loading">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span>加载节点信息...</span>
            </div>
          ) : stats ? (
            <>
              <div className="trace-brief-stat-row">
                <div className="trace-brief-stat">
                  <span className="trace-brief-stat-value">{stats.totalNodes}</span>
                  <span className="trace-brief-stat-label">执行节点</span>
                </div>
                <div className="trace-brief-stat">
                  <span
                    className={`trace-brief-stat-value ${stats.failedNodes > 0 ? "is-danger" : ""}`}
                  >
                    {stats.failedNodes}
                  </span>
                  <span className="trace-brief-stat-label">失败节点</span>
                </div>
                <div className="trace-brief-stat">
                  <span className="trace-brief-stat-value">{stats.maxDepth}</span>
                  <span className="trace-brief-stat-label">最大深度</span>
                </div>
                <div className="trace-brief-stat">
                  <span className="trace-brief-stat-value">
                    {formatDuration(stats.totalDurationMs)}
                  </span>
                  <span className="trace-brief-stat-label">节点累计</span>
                </div>
              </div>

              <div className="trace-brief-section">
                <div className="trace-brief-section-head">
                  <span className="trace-brief-section-title">耗时 Top 3</span>
                  <span className="trace-brief-section-hint">
                    占总链路 {formatDuration(run.durationMs ?? undefined)} 比例
                  </span>
                </div>
                {stats.topSlowNodes.length === 0 ? (
                  <p className="trace-brief-empty">无可统计的节点耗时</p>
                ) : (
                  <ol className="trace-brief-top-list">
                    {stats.topSlowNodes.map((node, index) => {
                      const totalDuration = Number(run.durationMs ?? 0);
                      const percent =
                        totalDuration > 0
                          ? Math.min(100, Math.round((node.durationMs / totalDuration) * 100))
                          : 0;
                      return (
                        <li key={`${node.nodeName}-${index}`} className="trace-brief-top-item">
                          <div className="trace-brief-top-head">
                            <span className="trace-brief-top-rank">{index + 1}</span>
                            <span className="trace-brief-top-name" title={node.nodeName}>
                              {node.nodeName}
                            </span>
                            <span className="trace-brief-top-duration">
                              {formatDuration(node.durationMs)}
                            </span>
                          </div>
                          <div className="trace-brief-top-bar">
                            <div
                              className="trace-brief-top-bar-fill"
                              style={{ width: `${percent}%` }}
                            />
                            <span className="trace-brief-top-percent">{percent}%</span>
                          </div>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </div>

              <div className="trace-brief-meta-row">
                <div className="trace-brief-meta-item">
                  <span className="trace-brief-label">任务 ID</span>
                  <span
                    className="trace-brief-meta-value font-mono text-xs"
                    title={run.taskId || undefined}
                  >
                    {run.taskId || "—"}
                  </span>
                </div>
                <div className="trace-brief-meta-item">
                  <span className="trace-brief-label">会话 ID</span>
                  <span
                    className="trace-brief-meta-value font-mono text-xs"
                    title={run.conversationId || undefined}
                  >
                    {run.conversationId || "—"}
                  </span>
                </div>
              </div>

              {run.errorMessage ? (
                <div className="trace-brief-error">
                  <span className="trace-brief-label">错误信息</span>
                  <p className="trace-brief-error-text">{run.errorMessage}</p>
                </div>
              ) : null}
            </>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            关闭
          </Button>
          <Button
            onClick={() => {
              onOpenDetail(run.traceId);
              onClose();
            }}
          >
            <Eye className="h-4 w-4 mr-1.5" />
            查看详情
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function RunsTable({
  runs,
  loading,
  current,
  pages,
  total,
  onOpenRun,
  onPrevPage,
  onNextPage
}: RunsTableProps) {
  const [briefRun, setBriefRun] = useState<RagTraceRun | null>(null);

  return (
    <Card className="trace-list-table-card">
      <CardContent className="trace-list-table-content">
        {loading ? (
          <div className="trace-list-table-empty">加载中...</div>
        ) : runs.length === 0 ? (
          <div className="trace-list-table-empty">暂无链路数据</div>
        ) : (
          <div className="trace-list-table-wrap">
            <Table className="trace-list-table">
              <TableHeader>
                <TableRow>
                  <TableHead className="trace-col-question">用户问题</TableHead>
                  <TableHead className="trace-col-trace-id">Trace Id</TableHead>
                  <TableHead className="trace-col-user">用户名</TableHead>
                  <TableHead className="trace-col-duration">耗时</TableHead>
                  <TableHead className="trace-col-duration">首字耗时</TableHead>
                  <TableHead className="trace-col-status">状态</TableHead>
                  <TableHead className="trace-col-time">执行时间</TableHead>
                  <TableHead className="trace-col-action">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {runs.map((run) => (
                  <TableRow key={run.traceId} className="trace-list-table-row">
                    <TableCell className="trace-col-question">
                      {renderTextOrEmpty(run.question)}
                    </TableCell>
                    <TableCell className="trace-col-trace-id">
                      <TraceIdCell traceId={run.traceId} />
                    </TableCell>
                    <TableCell className="trace-col-user">
                      {renderTextOrEmpty(run.userName || run.username || run.userId)}
                    </TableCell>
                    <TableCell className="trace-col-duration trace-list-duration-cell">
                      {formatDuration(run.durationMs ?? undefined)}
                    </TableCell>
                    <TableCell className="trace-col-duration trace-list-duration-cell">
                      {run.ttftMs != null ? formatDuration(run.ttftMs) : renderEmptyPlaceholder()}
                    </TableCell>
                    <TableCell className="trace-col-status trace-list-status-cell">
                      <StatusBadge status={run.status} />
                    </TableCell>
                    <TableCell className="trace-col-time">
                      <RelativeTime value={run.startTime ?? undefined} />
                    </TableCell>
                    <TableCell className="trace-col-action trace-list-action-cell">
                      <div className="trace-list-action-group">
                        <Button
                          size="sm"
                          variant="outline"
                          className="trace-list-action-btn trace-list-action-btn-primary"
                          onClick={() => setBriefRun(run)}
                        >
                          <LayoutPanelTop className="h-3.5 w-3.5" />
                          概览
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="trace-list-action-btn"
                          onClick={() => onOpenRun(run.traceId)}
                        >
                          <Eye className="h-3.5 w-3.5" />
                          详情
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        <div className="trace-list-table-footer">
          <span className="trace-list-table-meta">
            第 {current} / {pages} 页，共 {total.toLocaleString("zh-CN")} 条
          </span>
          <div className="trace-list-pagination">
            <Button
              className="trace-list-pagination-btn"
              variant="outline"
              disabled={current <= 1 || loading}
              onClick={onPrevPage}
            >
              上一页
            </Button>
            <Button
              className="trace-list-pagination-btn"
              variant="outline"
              disabled={current >= pages || loading}
              onClick={onNextPage}
            >
              下一页
            </Button>
          </div>
        </div>
      </CardContent>
      <BriefDialog
        run={briefRun}
        onClose={() => setBriefRun(null)}
        onOpenDetail={onOpenRun}
      />
    </Card>
  );
}
