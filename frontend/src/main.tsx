import React from "react";
import ReactDOM from "react-dom/client";

import App from "@/App";
import { useAuthStore } from "@/stores/authStore";
import { useThemeStore } from "@/stores/themeStore";
import "@/styles/globals.css";

useThemeStore.getState().initialize();
useAuthStore.getState().checkAuth();

let scrollIdleTimer: ReturnType<typeof setTimeout> | null = null;
const handleScrollActivity = () => {
  document.documentElement.classList.add("is-scrolling");
  if (scrollIdleTimer) clearTimeout(scrollIdleTimer);
  scrollIdleTimer = setTimeout(() => {
    document.documentElement.classList.remove("is-scrolling");
  }, 800);
};
window.addEventListener("scroll", handleScrollActivity, { capture: true, passive: true });
window.addEventListener("wheel", handleScrollActivity, { capture: true, passive: true });
window.addEventListener("touchmove", handleScrollActivity, { capture: true, passive: true });

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
