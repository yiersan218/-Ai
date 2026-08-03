import { api } from "@/services/api";
import type { RecommendedQuestionsPayload } from "@/types";

export async function stopTask(taskId: string) {
  return api.post<void>(`/rag/v3/stop?taskId=${encodeURIComponent(taskId)}`);
}

export async function submitFeedback(messageId: string, vote: number) {
  return api.post<void>(`/conversations/messages/${messageId}/feedback`, {
    vote
  });
}

export async function cancelFeedback(messageId: string) {
  return api.delete<void>(`/conversations/messages/${messageId}/feedback`);
}

export async function generateRecommendedQuestions(messageId: string) {
  return api.post<RecommendedQuestionsPayload>(
    `/conversations/messages/${messageId}/recommended-questions`
  );
}
