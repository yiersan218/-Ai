import { api } from "@/services/api";

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface BizChangeLog {
  id: string;
  bizType: string;
  bizId: string;
  operationType: string;
  actionDesc?: string | null;
  beforeSnapshot?: string | null;
  afterSnapshot?: string | null;
  changeDiff?: string | null;
  operatorId?: string | null;
  operatorName?: string | null;
  operatorRole?: string | null;
  success: boolean;
  errorMessage?: string | null;
  className?: string | null;
  methodName?: string | null;
  ip?: string | null;
  userAgent?: string | null;
  createTime?: string | null;
}

export interface BizChangeLogQuery {
  current?: number;
  size?: number;
  bizType?: string;
  bizId?: string;
  operationType?: string;
  operatorId?: string;
  operatorName?: string;
  success?: boolean;
  beginTime?: string;
  endTime?: string;
}

export async function getBizChangeLogsPage(
  query: BizChangeLogQuery = {}
): Promise<PageResult<BizChangeLog>> {
  return api.get<PageResult<BizChangeLog>, PageResult<BizChangeLog>>("/biz-change-logs", {
    params: {
      current: query.current ?? 1,
      size: query.size ?? 10,
      bizType: query.bizType || undefined,
      bizId: query.bizId || undefined,
      operationType: query.operationType || undefined,
      operatorId: query.operatorId || undefined,
      operatorName: query.operatorName || undefined,
      success: query.success,
      beginTime: query.beginTime || undefined,
      endTime: query.endTime || undefined
    }
  });
}

export async function getBizChangeLog(id: string): Promise<BizChangeLog> {
  return api.get<BizChangeLog, BizChangeLog>(`/biz-change-logs/${id}`);
}
