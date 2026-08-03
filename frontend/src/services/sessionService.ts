import { api } from "@/services/api";
import type { PersistedMessageStatus, SourceRef } from "@/types";

export interface ConversationVO {
  conversationId: string;
  title: string;
  lastTime?: string;
}

export interface ConversationMessageVO {
  id: number | string;
  conversationId: string;
  role: string;
  content: string;
  thinkingContent?: string | null;
  thinkingDuration?: number | null;
  vote: number | null;
  sources?: SourceRef[] | null;
  recommendedQuestions?: string[] | null;
  messageStatus?: PersistedMessageStatus | null;
  createTime?: string;
}

export async function listSessions() {
  return api.get<ConversationVO[]>("/conversations");
}

export async function deleteSession(conversationId: string) {
  return api.delete<void>(`/conversations/${conversationId}`);
}

export async function renameSession(conversationId: string, title: string) {
  return api.put<void>(`/conversations/${conversationId}`, { title });
}

export async function listMessages(conversationId: string) {
  return api.get<ConversationMessageVO[]>(`/conversations/${conversationId}/messages`);
}
