import { api } from "@/services/api";

export interface GraphNode {
  id: string;
  name: string;
  type?: string;
  description?: string;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  /** 关系描述，供前端悬浮展示可读的关系说明 */
  description?: string;
}

export interface GraphView {
  nodes: GraphNode[];
  edges: GraphEdge[];
  truncated: boolean;
}

export interface GraphQuery {
  entity?: string;
  /** 知识库 collectionName，限定只看该库子图 */
  collection?: string;
  /** 文档 id，限定只看该文档子图，优先级高于 collection */
  doc?: string;
  depth?: number;
  limit?: number;
}

/** 拉取图谱子图，entity 为空取全图；collection/doc 限定范围（axios 自动省略 undefined） */
export async function getKnowledgeGraph(params: GraphQuery = {}) {
  return api.get<GraphView, GraphView>("/admin/kg/graph", { params });
}

/** 检索实体标签，keyword 为空取热门标签 */
export async function searchGraphEntities(keyword: string, limit = 50) {
  return api.get<string[], string[]>("/admin/kg/labels", { params: { keyword, limit } });
}
