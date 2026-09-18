"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/buttons";
import { Badge, Card, Field, Input } from "@/components/common";
import { Callout } from "@/components/feedback";
import { requestAdminLogin } from "@/lib/admin/requests";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

/**
 * 아직 운영자 계정을 발급하는 절차가 없어, 개발 빌드에서는 비워 두고 눌러도 목
 * 서버의 콘솔 계정으로 들어갑니다. 실제로 계정을 고르는 일은 서버 쪽 Route
 * Handler 가 하고(`app/api/admin/login/route.ts`) 여기서는 그 사실만 안내합니다 —
 * 비밀번호를 브라우저 번들에 넣지 않기 위해서입니다.
 */
const DEV_LOGIN_HINT = process.env.NODE_ENV === "production" ? null : "개발 빌드에서는 비워 두고 눌러도 들어갑니다.";

export interface AdminLoginCardProps {
  /** 로그인한 뒤 돌아갈 곳. proxy 가 운영 화면에서 튕겨 낼 때 실어 보냅니다. */
  next?: string;
}

/**
 * AdminLoginCard — 운영 콘솔 로그인 카드 (`9_로그인`).
 *
 * **사용자 로그인과 같은 화면이 아닙니다.** 사용자는 카카오로 들어오고 운영자는
 * 아이디·비밀번호로 들어옵니다. 여기서 받은 토큰으로만 운영 API를 부를 수 있고,
 * 반대로 사용자 토큰으로는 부를 수 없습니다(목 명세 `9 운영 콘솔`).
 *
 * 토큰은 브라우저로 내려오지 않습니다. Route Handler 가 받아 운영자 전용 httpOnly
 * 쿠키에 넣고, 이 카드가 받는 것은 누구로 들어왔는지뿐입니다.
 *
 * 아이디가 없는 것인지 비밀번호가 틀린 것인지는 서버가 구분해 알려주지 않습니다.
 * 화면도 서버가 준 문구를 그대로 보여주고 나눠 짐작하지 않습니다.
 */
export function AdminLoginCard({ next }: Readonly<AdminLoginCardProps>) {
  const router = useRouter();
  const [loginId, setLoginId] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    setError(null);

    try {
      await requestAdminLogin({ loginId, password });
      // 뒤로 가기로 로그인 화면에 돌아오면 이미 들어와 있는 상태라 혼란스럽습니다.
      router.replace(next ?? ROUTES.adminHome);
      router.refresh();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "로그인하지 못했습니다.");
      setSubmitting(false);
    }
  };

  return (
    <Card variant="elevated" padding="lg" className="mx-auto w-full max-w-[460px] sm:p-9">
      <div className="mb-6">
        <div className="mb-5 flex items-center gap-2">
          <span className="text-[15px] font-bold tracking-tight text-fg">Komit</span>
          <Badge tone="ink" shape="pill" size="sm">
            운영 콘솔
          </Badge>
        </div>
        <h1 className="text-xl font-bold text-fg">운영 콘솔 로그인</h1>
        <p className="mt-1.5 text-[13px] text-fg-muted">Komit 내부 직원 전용입니다.</p>
      </div>

      {error && (
        <Callout tone="danger" size="sm" className="mb-5" role="alert">
          {error}
        </Callout>
      )}

      <form className="space-y-4" onSubmit={submit}>
        <Field label="아이디" htmlFor="admin-login-id">
          <Input
            id="admin-login-id"
            name="loginId"
            autoComplete="username"
            placeholder="soomin"
            value={loginId}
            disabled={submitting}
            onChange={(event) => setLoginId(event.target.value)}
          />
        </Field>
        <Field label="비밀번호" htmlFor="admin-password" helper={DEV_LOGIN_HINT ?? undefined}>
          <Input
            id="admin-password"
            name="password"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호를 입력해 주세요"
            value={password}
            disabled={submitting}
            onChange={(event) => setPassword(event.target.value)}
          />
        </Field>
        <Button type="submit" variant="dark" fullWidth loading={submitting} className="mt-2">
          로그인
        </Button>
      </form>
    </Card>
  );
}
