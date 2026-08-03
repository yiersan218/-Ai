import { Link } from "react-router-dom";
import { ArrowLeft, MessageSquare } from "lucide-react";

import { Button } from "@/components/ui/button";
import { BizChangeLogPage } from "@/pages/admin/change-logs/BizChangeLogPage";

export function ChangeLogsPage() {
  return (
    <div className="admin-layout h-full overflow-auto bg-slate-50">
      <div className="admin-main min-h-full">
        <header className="admin-topbar">
          <div className="admin-topbar-inner">
            <div className="flex items-center gap-3">
              <Link to="/chat">
                <Button variant="outline" size="sm">
                  <ArrowLeft className="mr-2 h-4 w-4" />
                  返回聊天
                </Button>
              </Link>
              <div>
                <div className="text-sm font-semibold text-slate-900">知源 AI</div>
                <div className="text-xs text-slate-500">业务变更审计</div>
              </div>
            </div>
            <Link to="/chat">
              <Button variant="ghost" size="sm">
                <MessageSquare className="mr-2 h-4 w-4" />
                对话
              </Button>
            </Link>
          </div>
        </header>
        <main className="admin-content">
          <BizChangeLogPage />
        </main>
      </div>
    </div>
  );
}
