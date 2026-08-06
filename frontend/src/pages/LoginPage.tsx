import * as React from "react";
import {
  ArrowRight,
  Bot,
  CircleAlert,
  Eye,
  EyeOff,
  LoaderCircle,
  LockKeyhole,
  Network,
  SearchCheck,
  ShieldCheck,
  Sparkles,
  UserPlus,
  UserRound
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { useAuthStore } from "@/stores/authStore";

const productHighlights = [
  {
    icon: SearchCheck,
    title: "多路知识检索",
    description: "从文档、关键词与知识图谱中定位可靠信息"
  },
  {
    icon: Network,
    title: "答案来源可追溯",
    description: "引用与原文关联，让每个结论都有依据"
  },
  {
    icon: Sparkles,
    title: "深度思考与生成",
    description: "将复杂问题整理为清晰、可执行的回答"
  }
];

type FieldErrors = {
  username?: string;
  password?: string;
  confirmPassword?: string;
};

type AuthMode = "login" | "register";

function AuthPage({ mode }: { mode: AuthMode }) {
  const isRegister = mode === "register";
  const navigate = useNavigate();
  const { login, register, isLoading } = useAuthStore();
  const [showPassword, setShowPassword] = React.useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = React.useState(false);
  const [remember, setRemember] = React.useState(false);
  const [form, setForm] = React.useState({
    username: "",
    password: "",
    confirmPassword: ""
  });
  const [fieldErrors, setFieldErrors] = React.useState<FieldErrors>({});
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    const nextErrors: FieldErrors = {};
    const username = form.username.trim();
    if (!username) {
      nextErrors.username = "请输入用户名";
    } else if (isRegister && (username.length < 3 || username.length > 32)) {
      nextErrors.username = "用户名长度应为 3-32 个字符";
    } else if (isRegister && !/^[\p{L}\p{N}_-]+$/u.test(username)) {
      nextErrors.username = "用户名只能包含文字、数字、下划线和短横线";
    }
    if (!form.password) {
      nextErrors.password = "请输入密码";
    } else if (
      isRegister &&
      (form.password.length < 8 ||
        form.password.length > 64 ||
        !/\p{L}/u.test(form.password) ||
        !/\d/u.test(form.password))
    ) {
      nextErrors.password = "密码需为 8-64 位，且同时包含字母和数字";
    }
    if (isRegister && !form.confirmPassword) {
      nextErrors.confirmPassword = "请再次输入密码";
    } else if (isRegister && form.password !== form.confirmPassword) {
      nextErrors.confirmPassword = "两次输入的密码不一致";
    }
    setFieldErrors(nextErrors);

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    try {
      if (isRegister) {
        await register(username, form.password, form.confirmPassword);
      } else {
        await login(username, form.password);
      }
      if (!isRegister && !remember) {
        // 如需仅在内存中保存登录态，可在此扩展。
      }
      navigate("/chat");
    } catch (err) {
      setError(
        (err as Error).message ||
          (isRegister ? "注册失败，请稍后重试。" : "登录失败，请检查账号信息后重试。")
      );
    }
  };

  return (
    <main className="relative h-full overflow-x-hidden overflow-y-auto bg-[#F6F8FC] text-slate-950 dark:bg-slate-950 dark:text-white">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_8%_12%,rgba(99,102,241,0.16),transparent_30%),radial-gradient(circle_at_92%_88%,rgba(14,165,233,0.12),transparent_32%)] dark:bg-[radial-gradient(circle_at_8%_12%,rgba(99,102,241,0.2),transparent_30%),radial-gradient(circle_at_92%_88%,rgba(14,165,233,0.12),transparent_32%)]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.035] [background-image:linear-gradient(to_right,#0f172a_1px,transparent_1px),linear-gradient(to_bottom,#0f172a_1px,transparent_1px)] [background-size:40px_40px] dark:opacity-[0.06]"
      />

      <div className="relative z-10 mx-auto grid min-h-full w-full max-w-[1440px] lg:grid-cols-[minmax(0,1.08fr)_minmax(440px,0.92fr)]">
        <section className="hidden min-h-full flex-col justify-between px-12 py-8 lg:flex xl:px-20">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-[0_12px_30px_rgba(79,70,229,0.28)]">
              <Bot className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <p className="font-display text-lg font-semibold tracking-tight text-slate-950 dark:text-white">
                知源 AI
              </p>
              <p className="text-xs font-medium tracking-[0.16em] text-slate-500 dark:text-slate-400">
                AGENTIC RAG WORKSPACE
              </p>
            </div>
          </div>

          <div className="my-6 max-w-[620px]">
            <div className="inline-flex items-center gap-2 rounded-full border border-indigo-200/70 bg-white/70 px-3 py-1.5 text-xs font-semibold text-indigo-700 shadow-sm backdrop-blur dark:border-indigo-400/20 dark:bg-indigo-400/10 dark:text-indigo-200">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              企业知识智能中枢
            </div>
            <h1 className="mt-4 font-display text-5xl font-semibold leading-[1.08] tracking-[-0.04em] text-slate-950 xl:text-[54px] dark:text-white">
              让每一次提问，
              <span className="block bg-gradient-to-r from-indigo-600 via-violet-600 to-sky-500 bg-clip-text text-transparent">
                都抵达可信答案
              </span>
            </h1>
            <p className="mt-4 whitespace-nowrap text-xs leading-6 text-slate-600 xl:text-base xl:leading-7 dark:text-slate-300">
              连接企业知识、检索证据与智能推理，在一个工作空间中完成从问题到行动的闭环。
            </p>

            <div className="mt-6 grid gap-2">
              {productHighlights.map((item) => {
                const Icon = item.icon;
                return (
                  <div
                    key={item.title}
                    className="group flex max-w-[560px] items-center gap-3 rounded-2xl border border-white/80 bg-white/60 p-3 shadow-[0_14px_40px_rgba(15,23,42,0.05)] backdrop-blur transition-colors duration-200 hover:border-indigo-200 hover:bg-white/90 dark:border-white/10 dark:bg-white/[0.04] dark:hover:border-indigo-400/30 dark:hover:bg-white/[0.07] motion-reduce:transition-none"
                  >
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-400/10 dark:text-indigo-300">
                      <Icon className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-slate-900 dark:text-slate-100">
                        {item.title}
                      </span>
                      <span className="mt-0.5 block text-[13px] leading-5 text-slate-500 dark:text-slate-400">
                        {item.description}
                      </span>
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          <p className="font-display text-base font-medium leading-relaxed tracking-[0.02em] text-slate-600 xl:text-lg dark:text-slate-300">
            <span className="bg-gradient-to-r from-indigo-600 to-violet-600 bg-clip-text font-semibold text-transparent dark:from-indigo-300 dark:to-violet-300">
              知源 AI
            </span>
            <span className="mx-2 text-indigo-400 dark:text-indigo-500">·</span>
            <span>
              面向{" "}
              <span className="font-semibold text-slate-700 dark:text-slate-200">Java 生态</span>的
              Agentic RAG 平台
            </span>
          </p>
        </section>

        <section className="flex min-h-full flex-col items-center justify-center px-4 py-8 sm:px-8 lg:border-l lg:border-white/70 lg:bg-white/35 lg:px-12 lg:backdrop-blur-sm dark:lg:border-white/10 dark:lg:bg-slate-950/25">
          <div className="mb-8 flex w-full max-w-[480px] items-center gap-3 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-lg shadow-indigo-500/20">
              <Bot className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <p className="font-display text-base font-semibold text-slate-950 dark:text-white">
                知源 AI
              </p>
              <p className="text-[11px] font-medium tracking-[0.12em] text-slate-500 dark:text-slate-400">
                AGENTIC RAG WORKSPACE
              </p>
            </div>
          </div>

          <div className="w-full max-w-[480px] rounded-[28px] border border-white/90 bg-white/90 p-6 shadow-[0_30px_80px_-32px_rgba(30,41,59,0.35)] backdrop-blur-xl sm:p-10 dark:border-white/10 dark:bg-slate-900/90 dark:shadow-black/30">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-indigo-600 dark:text-indigo-300">
                  {isRegister ? "创建账号" : "账号登录"}
                </p>
                <h2 className="mt-2 font-display text-3xl font-semibold tracking-[-0.03em] text-slate-950 dark:text-white">
                  {isRegister ? "开始探索知识" : "欢迎回来"}
                </h2>
                <p className="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">
                  {isRegister ? "注册后即可进入检索增强对话" : "登录后继续你的检索增强对话"}
                </p>
              </div>
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-slate-200 bg-slate-50 text-slate-500 dark:border-white/10 dark:bg-white/[0.04] dark:text-slate-300">
                {isRegister ? (
                  <UserPlus className="h-5 w-5" aria-hidden="true" />
                ) : (
                  <ShieldCheck className="h-5 w-5" aria-hidden="true" />
                )}
              </span>
            </div>

            <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
              <div className="space-y-2">
                <label
                  htmlFor="login-username"
                  className="text-sm font-medium text-slate-700 dark:text-slate-200"
                >
                  用户名
                </label>
                <div className="relative">
                  <UserRound
                    className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-slate-400"
                    aria-hidden="true"
                  />
                  <Input
                    id="login-username"
                    name="username"
                    placeholder="请输入用户名"
                    value={form.username}
                    onChange={(event) => {
                      setForm((prev) => ({ ...prev, username: event.target.value }));
                      setFieldErrors((prev) => ({ ...prev, username: undefined }));
                      setError(null);
                    }}
                    className="h-12 rounded-xl border-slate-200 bg-slate-50/70 pl-11 pr-4 text-base text-slate-900 shadow-none transition-[border-color,box-shadow,background-color] placeholder:text-slate-400 hover:border-slate-300 focus-visible:border-indigo-500 focus-visible:bg-white focus-visible:ring-4 focus-visible:ring-indigo-100 dark:border-white/10 dark:bg-white/[0.04] dark:text-white dark:hover:border-white/20 dark:focus-visible:border-indigo-400 dark:focus-visible:bg-white/[0.07] dark:focus-visible:ring-indigo-400/10 motion-reduce:transition-none"
                    autoComplete="username"
                    aria-invalid={Boolean(fieldErrors.username)}
                    aria-describedby={fieldErrors.username ? "login-username-error" : undefined}
                  />
                </div>
                {fieldErrors.username ? (
                  <p
                    id="login-username-error"
                    className="text-xs font-medium text-red-600 dark:text-red-400"
                  >
                    {fieldErrors.username}
                  </p>
                ) : null}
              </div>

              <div className="space-y-2">
                <label
                  htmlFor="login-password"
                  className="text-sm font-medium text-slate-700 dark:text-slate-200"
                >
                  密码
                </label>
                <div className="relative">
                  <LockKeyhole
                    className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-slate-400"
                    aria-hidden="true"
                  />
                  <Input
                    id="login-password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    placeholder={isRegister ? "请输入 8-64 位密码" : "请输入密码"}
                    value={form.password}
                    onChange={(event) => {
                      setForm((prev) => ({ ...prev, password: event.target.value }));
                      setFieldErrors((prev) => ({ ...prev, password: undefined }));
                      setError(null);
                    }}
                    className="h-12 rounded-xl border-slate-200 bg-slate-50/70 pl-11 pr-12 text-base text-slate-900 shadow-none transition-[border-color,box-shadow,background-color] placeholder:text-slate-400 hover:border-slate-300 focus-visible:border-indigo-500 focus-visible:bg-white focus-visible:ring-4 focus-visible:ring-indigo-100 dark:border-white/10 dark:bg-white/[0.04] dark:text-white dark:hover:border-white/20 dark:focus-visible:border-indigo-400 dark:focus-visible:bg-white/[0.07] dark:focus-visible:ring-indigo-400/10 motion-reduce:transition-none"
                    autoComplete={isRegister ? "new-password" : "current-password"}
                    aria-invalid={Boolean(fieldErrors.password)}
                    aria-describedby={fieldErrors.password ? "login-password-error" : undefined}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="absolute right-0.5 top-1/2 flex h-11 w-11 -translate-y-1/2 cursor-pointer items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1 dark:hover:bg-white/10 dark:hover:text-white motion-reduce:transition-none"
                    aria-label={showPassword ? "隐藏密码" : "显示密码"}
                    aria-pressed={showPassword}
                  >
                    {showPassword ? (
                      <EyeOff className="h-[18px] w-[18px]" aria-hidden="true" />
                    ) : (
                      <Eye className="h-[18px] w-[18px]" aria-hidden="true" />
                    )}
                  </button>
                </div>
                {fieldErrors.password ? (
                  <p
                    id="login-password-error"
                    className="text-xs font-medium text-red-600 dark:text-red-400"
                  >
                    {fieldErrors.password}
                  </p>
                ) : null}
              </div>

              {isRegister ? (
                <div className="space-y-2">
                  <label
                    htmlFor="register-confirm-password"
                    className="text-sm font-medium text-slate-700 dark:text-slate-200"
                  >
                    确认密码
                  </label>
                  <div className="relative">
                    <ShieldCheck
                      className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-slate-400"
                      aria-hidden="true"
                    />
                    <Input
                      id="register-confirm-password"
                      name="confirmPassword"
                      type={showConfirmPassword ? "text" : "password"}
                      placeholder="请再次输入密码"
                      value={form.confirmPassword}
                      onChange={(event) => {
                        setForm((prev) => ({ ...prev, confirmPassword: event.target.value }));
                        setFieldErrors((prev) => ({ ...prev, confirmPassword: undefined }));
                        setError(null);
                      }}
                      className="h-12 rounded-xl border-slate-200 bg-slate-50/70 pl-11 pr-12 text-base text-slate-900 shadow-none transition-[border-color,box-shadow,background-color] placeholder:text-slate-400 hover:border-slate-300 focus-visible:border-indigo-500 focus-visible:bg-white focus-visible:ring-4 focus-visible:ring-indigo-100 dark:border-white/10 dark:bg-white/[0.04] dark:text-white dark:hover:border-white/20 dark:focus-visible:border-indigo-400 dark:focus-visible:bg-white/[0.07] dark:focus-visible:ring-indigo-400/10 motion-reduce:transition-none"
                      autoComplete="new-password"
                      aria-invalid={Boolean(fieldErrors.confirmPassword)}
                      aria-describedby={
                        fieldErrors.confirmPassword ? "register-confirm-password-error" : undefined
                      }
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPassword((prev) => !prev)}
                      className="absolute right-0.5 top-1/2 flex h-11 w-11 -translate-y-1/2 cursor-pointer items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1 dark:hover:bg-white/10 dark:hover:text-white motion-reduce:transition-none"
                      aria-label={showConfirmPassword ? "隐藏确认密码" : "显示确认密码"}
                      aria-pressed={showConfirmPassword}
                    >
                      {showConfirmPassword ? (
                        <EyeOff className="h-[18px] w-[18px]" aria-hidden="true" />
                      ) : (
                        <Eye className="h-[18px] w-[18px]" aria-hidden="true" />
                      )}
                    </button>
                  </div>
                  {fieldErrors.confirmPassword ? (
                    <p
                      id="register-confirm-password-error"
                      className="text-xs font-medium text-red-600 dark:text-red-400"
                    >
                      {fieldErrors.confirmPassword}
                    </p>
                  ) : null}
                </div>
              ) : null}

              {isRegister ? (
                <p className="rounded-xl bg-indigo-50/80 px-3.5 py-3 text-xs leading-5 text-indigo-700 dark:bg-indigo-400/10 dark:text-indigo-200">
                  为防止批量注册，同一网络短时间内的注册请求和成功账号数量会受到限制。
                </p>
              ) : (
                <label
                  htmlFor="login-remember"
                  className="flex min-h-11 cursor-pointer items-center gap-3 rounded-xl px-1 text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100 motion-reduce:transition-none"
                >
                  <Checkbox
                    id="login-remember"
                    checked={remember}
                    onCheckedChange={(value) => setRemember(Boolean(value))}
                    className="h-5 w-5 rounded-md border-slate-300 data-[state=checked]:border-indigo-600 data-[state=checked]:bg-indigo-600 dark:border-slate-600"
                  />
                  记住我
                </label>
              )}

              {error ? (
                <div
                  role="alert"
                  aria-live="assertive"
                  className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 px-3.5 py-3 text-sm leading-5 text-red-700 dark:border-red-400/20 dark:bg-red-400/10 dark:text-red-300"
                >
                  <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  <span>{error}</span>
                </div>
              ) : null}

              <Button
                type="submit"
                className="h-12 w-full cursor-pointer rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 px-5 text-base text-white shadow-[0_14px_30px_-12px_rgba(79,70,229,0.65)] transition-[filter,box-shadow] duration-200 hover:brightness-105 hover:shadow-[0_18px_36px_-14px_rgba(79,70,229,0.75)] focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none"
                disabled={isLoading}
              >
                {isLoading ? (
                  <>
                    <LoaderCircle
                      className="h-4 w-4 animate-spin motion-reduce:animate-none"
                      aria-hidden="true"
                    />
                    <span aria-live="polite">{isRegister ? "正在注册..." : "正在登录..."}</span>
                  </>
                ) : (
                  <>
                    {isRegister ? "注册并登录" : "登录"}
                    <ArrowRight className="h-4 w-4" aria-hidden="true" />
                  </>
                )}
              </Button>
            </form>

            <div className="mt-7 flex flex-wrap items-center justify-center gap-x-2 gap-y-3 border-t border-slate-100 pt-6 text-sm text-slate-500 dark:border-white/10 dark:text-slate-400">
              <span>{isRegister ? "已经有账号？" : "还没有账号？"}</span>
              <Link
                to={isRegister ? "/login" : "/register"}
                className="inline-flex min-h-11 items-center rounded-lg px-2 font-semibold text-indigo-600 transition-colors hover:bg-indigo-50 hover:text-indigo-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:text-indigo-300 dark:hover:bg-indigo-400/10 dark:hover:text-indigo-200 motion-reduce:transition-none"
              >
                {isRegister ? "返回登录" : "立即注册"}
              </Link>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}

export function LoginPage() {
  return <AuthPage mode="login" />;
}

export function RegisterPage() {
  return <AuthPage mode="register" />;
}
