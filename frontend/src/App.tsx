import { Suspense } from "react";
import { RouterProvider } from "react-router-dom";

import { ErrorBoundary } from "@/components/common/ErrorBoundary";
import { IcpFooter } from "@/components/common/IcpFooter";
import { Toast } from "@/components/common/Toast";
import { siteConfig } from "@/config/site";
import { router } from "@/router";

export default function App() {
  const hasIcpNumber = Boolean(siteConfig.icpNumber);

  return (
    <div className={hasIcpNumber ? "app-shell app-shell--with-icp" : "app-shell"}>
      <div className="app-shell__content">
        <ErrorBoundary>
          <Suspense
            fallback={
              <div className="flex min-h-screen items-center justify-center text-sm text-slate-500">
                页面加载中…
              </div>
            }
          >
            <RouterProvider router={router} />
          </Suspense>
        </ErrorBoundary>
      </div>
      <IcpFooter />
      <Toast />
    </div>
  );
}
