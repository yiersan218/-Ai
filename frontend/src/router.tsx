import { lazy } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

import { siteConfig } from "@/config/site";
import { useAuthStore } from "@/stores/authStore";

const LoginPage = lazy(() =>
  import("@/pages/LoginPage").then(({ LoginPage }) => ({ default: LoginPage }))
);
const RegisterPage = lazy(() =>
  import("@/pages/LoginPage").then(({ RegisterPage }) => ({ default: RegisterPage }))
);
const ChatPage = lazy(() =>
  import("@/pages/ChatPage").then(({ ChatPage }) => ({ default: ChatPage }))
);
const ChangeLogsPage = lazy(() =>
  import("@/pages/ChangeLogsPage").then(({ ChangeLogsPage }) => ({ default: ChangeLogsPage }))
);
const DocPreviewPage = lazy(() =>
  import("@/pages/DocPreviewPage").then(({ DocPreviewPage }) => ({ default: DocPreviewPage }))
);
const NotFoundPage = lazy(() =>
  import("@/pages/NotFoundPage").then(({ NotFoundPage }) => ({ default: NotFoundPage }))
);
const AdminLayout = lazy(() =>
  import("@/pages/admin/AdminLayout").then(({ AdminLayout }) => ({ default: AdminLayout }))
);
const DashboardPage = lazy(() =>
  import("@/pages/admin/dashboard/DashboardPage").then(({ DashboardPage }) => ({
    default: DashboardPage
  }))
);
const KnowledgeListPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeListPage").then(({ KnowledgeListPage }) => ({
    default: KnowledgeListPage
  }))
);
const KnowledgeDocumentsPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeDocumentsPage").then(({ KnowledgeDocumentsPage }) => ({
    default: KnowledgeDocumentsPage
  }))
);
const KnowledgeChunksPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeChunksPage").then(({ KnowledgeChunksPage }) => ({
    default: KnowledgeChunksPage
  }))
);
const KnowledgeGraphPage = lazy(() =>
  import("@/pages/admin/knowledge-graph/KnowledgeGraphPage").then(({ KnowledgeGraphPage }) => ({
    default: KnowledgeGraphPage
  }))
);
const BizChangeLogPage = lazy(() =>
  import("@/pages/admin/change-logs/BizChangeLogPage").then(({ BizChangeLogPage }) => ({
    default: BizChangeLogPage
  }))
);
const IntentTreePage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentTreePage").then(({ IntentTreePage }) => ({
    default: IntentTreePage
  }))
);
const IntentListPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentListPage").then(({ IntentListPage }) => ({
    default: IntentListPage
  }))
);
const IntentEditPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentEditPage").then(({ IntentEditPage }) => ({
    default: IntentEditPage
  }))
);
const IngestionPage = lazy(() =>
  import("@/pages/admin/ingestion/IngestionPage").then(({ IngestionPage }) => ({
    default: IngestionPage
  }))
);
const RagTracePage = lazy(() =>
  import("@/pages/admin/traces/RagTracePage").then(({ RagTracePage }) => ({
    default: RagTracePage
  }))
);
const RagTraceDetailPage = lazy(() =>
  import("@/pages/admin/traces/RagTraceDetailPage").then(({ RagTraceDetailPage }) => ({
    default: RagTraceDetailPage
  }))
);
const SystemSettingsPage = lazy(() =>
  import("@/pages/admin/settings/SystemSettingsPage").then(({ SystemSettingsPage }) => ({
    default: SystemSettingsPage
  }))
);
const SampleQuestionPage = lazy(() =>
  import("@/pages/admin/sample-questions/SampleQuestionPage").then(({ SampleQuestionPage }) => ({
    default: SampleQuestionPage
  }))
);
const QueryTermMappingPage = lazy(() =>
  import("@/pages/admin/query-term-mapping/QueryTermMappingPage").then(
    ({ QueryTermMappingPage }) => ({ default: QueryTermMappingPage })
  )
);
const UserListPage = lazy(() =>
  import("@/pages/admin/users/UserListPage").then(({ UserListPage }) => ({ default: UserListPage }))
);

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return children;
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }
  return children;
}

function HomeRedirect() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return <Navigate to={isAuthenticated ? "/chat" : "/login"} replace />;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <HomeRedirect />
  },
  {
    path: "/login",
    element: (
      <RedirectIfAuth>
        <LoginPage />
      </RedirectIfAuth>
    )
  },
  {
    path: "/register",
    element: siteConfig.registrationEnabled ? (
      <RedirectIfAuth>
        <RegisterPage />
      </RedirectIfAuth>
    ) : (
      <Navigate to="/login" replace />
    )
  },
  {
    path: "/chat",
    element: (
      <RequireAuth>
        <ChatPage />
      </RequireAuth>
    )
  },
  {
    path: "/chat/:sessionId",
    element: (
      <RequireAuth>
        <ChatPage />
      </RequireAuth>
    )
  },
  {
    path: "/change-logs",
    element: (
      <RequireAuth>
        <ChangeLogsPage />
      </RequireAuth>
    )
  },
  {
    path: "/preview/doc/:docId",
    element: (
      <RequireAuth>
        <DocPreviewPage />
      </RequireAuth>
    )
  },
  {
    path: "/admin",
    element: (
      <RequireAdmin>
        <AdminLayout />
      </RequireAdmin>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/admin/dashboard" replace />
      },
      {
        path: "dashboard",
        element: <DashboardPage />
      },
      {
        path: "knowledge",
        element: <KnowledgeListPage />
      },
      {
        path: "knowledge/:kbId",
        element: <KnowledgeDocumentsPage />
      },
      {
        path: "knowledge/:kbId/docs/:docId",
        element: <KnowledgeChunksPage />
      },
      {
        path: "knowledge-graph",
        element: <KnowledgeGraphPage />
      },
      {
        path: "intent-tree",
        element: <IntentTreePage />
      },
      {
        path: "intent-list",
        element: <IntentListPage />
      },
      {
        path: "intent-list/:id/edit",
        element: <IntentEditPage />
      },
      {
        path: "ingestion",
        element: <IngestionPage />
      },
      {
        path: "traces",
        element: <RagTracePage />
      },
      {
        path: "traces/:traceId",
        element: <RagTraceDetailPage />
      },
      {
        path: "change-logs",
        element: <BizChangeLogPage />
      },
      {
        path: "settings",
        element: <SystemSettingsPage />
      },
      {
        path: "sample-questions",
        element: <SampleQuestionPage />
      },
      {
        path: "mappings",
        element: <QueryTermMappingPage />
      },
      {
        path: "users",
        element: <UserListPage />
      }
    ]
  },
  {
    path: "*",
    element: <NotFoundPage />
  }
]);
