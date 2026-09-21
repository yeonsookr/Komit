import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Card } from "@/components/common";
import { Callout } from "@/components/feedback";
import { NEXT_PARAM } from "@/lib/auth/params";
import { appPath } from "@/lib/basePath";
import { ROUTES } from "@/lib/routes";

/**
 * 카드 아래에 붙는 약관 링크. 약관 화면(`8_약관`)이 늘어놓는 문서 네 건과 같습니다.
 * 본문은 문서마다 따로라 목록을 거치지 않고 바로 보냅니다.
 */
const POLICY_LINKS = [
  { label: "이용약관", href: ROUTES.policiesTerms },
  { label: "개인정보 처리방침", href: ROUTES.policiesPrivacy },
  { label: "전자금융거래", href: ROUTES.policiesElectronicFinance },
  { label: "환불과 분쟁 처리", href: ROUTES.policiesRefundDispute },
];

/**
 * 로그인에 실패해 돌아왔을 때 보여줄 문구. 키는 서버의 실패 코드입니다(목 명세 영역 1).
 * 문구는 서버가 주는 message 와 같게 맞춰 두고, 모르는 코드는 일반 실패 문구로 덮습니다.
 */
const LOGIN_ERROR_MESSAGES: Record<string, string> = {
  KAKAO_AUTH_FAILED: "로그인하지 못했습니다. 다시 시도해 주세요.",
  KAKAO_UNAVAILABLE: "카카오 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
  REFRESH_TOKEN_INVALID: "로그인이 만료되었습니다. 다시 로그인해 주세요.",
};

export function loginErrorMessage(code: string): string {
  return LOGIN_ERROR_MESSAGES[code] ?? LOGIN_ERROR_MESSAGES.KAKAO_AUTH_FAILED;
}

export interface LoginCardProps {
  /**
   * 로그인한 뒤 돌아갈 곳. 사전점검처럼 로그인이 필요한 화면이 사용자를 여기로 보낼 때
   * 넘깁니다. 없으면 인가 시작 주소에 붙이지 않고, 콜백이 랜딩으로 보냅니다.
   */
  next?: string;
  /** 로그인에 실패해 돌아왔을 때의 실패 코드. 있으면 카드 위에 안내를 띄웁니다. */
  errorCode?: string;
}

/** 일반 `<a>`도 전역 앱 경로 설정을 사용해 현재 FE의 인가 시작점으로 보냅니다. */
export function kakaoStartHref(next?: string): string {
  const start = appPath(ROUTES.kakaoStart);
  return next ? `${start}?${NEXT_PARAM}=${encodeURIComponent(next)}` : start;
}

/**
 * LoginCard — 「카카오로 시작하기」 하나만 있는 로그인 카드.
 *
 * 처음 들어온 사용자는 카카오 로그인 뒤 가입 약관 화면으로 이동합니다.
 *
 * 버튼은 이동만 하므로 `<button>` 이 아니라 링크입니다. 목적지인 인가 시작
 * 경로는 Route Handler(`app/api/auth/kakao`)가 받아 카카오 로그인 화면으로 보냅니다.
 */
export function LoginCard({ next, errorCode }: Readonly<LoginCardProps>) {
  const kakaoHref = kakaoStartHref(next);

  // 카드 폭은 약관 링크 넷이 한 줄에 들어가는 값입니다. 좁히면 줄이 나뉩니다.
  return (
    <Card variant="elevated" padding="lg" className="mx-auto w-full max-w-[480px] sm:p-8">
      <p className="text-base font-bold text-fg">Komit</p>
      <h1 className="mt-4 text-3xl font-bold tracking-tight text-fg">시작하기</h1>

      {errorCode && (
        <Callout tone="danger" size="sm" className="mt-5">
          {loginErrorMessage(errorCode)}
        </Callout>
      )}

      {/*
        `<Link>` 가 아니라 `<a>` 입니다. 이 주소는 Route Handler 가 카카오로 리다이렉트하는
        곳이라 Next 라우터가 가로채면 RSC fetch 가 다른 출처로 튕겨 실패하고 이동이 멈춥니다.
        일반 브라우저 이동으로 보내야 카카오 로그인 화면이 열립니다.
      */}
      <a
        href={kakaoHref}
        className={buttonClass({
          variant: "kakao",
          size: "lg",
          fullWidth: true,
          className: "mt-6 gap-2",
        })}
      >
        <KakaoSymbol />
        카카오로 시작하기
      </a>

      <div className="mt-7 border-t border-line-subtle pt-5">
        <p className="text-[13px] leading-6 text-fg-muted">
          처음 가입하신다면 다음 화면에서 필수 약관을 확인하고 동의할 수 있습니다.
        </p>
        {/* R-LEG-01 — 통신판매중개자 고지. 문구를 바꿀 때는 규칙서를 함께 봅니다. */}
        <p className="mt-2 text-[13px] leading-6 text-fg-muted">
          Komit은 통신판매중개자이며 거래의 당사자가 아닙니다. 상담·컨설팅·개발의 이행
          책임은 각 개발자에게 있습니다.
        </p>

        <ul className="mt-4 flex flex-wrap items-center gap-x-2 gap-y-1">
          {POLICY_LINKS.map((policy, index) => (
            <li key={policy.href} className="flex items-center gap-2">
              {index > 0 && (
                <span aria-hidden className="text-fg-disabled">
                  ·
                </span>
              )}
              <Link
                href={policy.href}
                className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                {policy.label}
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  );
}

/** 카카오 말풍선. 연동할 때 카카오가 주는 공식 에셋으로 바꿉니다. */
function KakaoSymbol() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="currentColor" aria-hidden>
      <path d="M12 3.6c-4.7 0-8.5 2.96-8.5 6.6 0 2.33 1.56 4.37 3.9 5.54l-.95 3.5c-.08.3.25.54.51.37l4.2-2.77c.27.02.55.03.84.03 4.7 0 8.5-2.95 8.5-6.6S16.7 3.6 12 3.6Z" />
    </svg>
  );
}
