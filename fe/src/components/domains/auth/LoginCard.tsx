import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Card } from "@/components/common";
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

export interface LoginCardProps {
  /**
   * 로그인한 뒤 돌아갈 곳. 사전점검처럼 로그인이 필요한 화면이 사용자를 여기로 보낼 때
   * 넘깁니다. 없으면 인가 시작 주소에 붙이지 않고, 백엔드가 기본 위치로 보냅니다.
   */
  next?: string;
}

/**
 * LoginCard — 「카카오로 시작하기」 하나만 있는 로그인 카드.
 *
 * 가입 화면과 약관 동의 화면이 따로 없습니다. 처음 들어온 사용자는 이 버튼 하나로
 * 가입까지 끝나고, 필수 약관 동의는 아래 안내로 갈음합니다(목 명세 영역 1).
 *
 * 버튼은 이동만 하므로 `<button>` 이 아니라 `<Link>` 입니다. 목적지인 인가 시작
 * 경로는 백엔드 연동 단계에서 Route Handler 로 만들며, 그때까지는 열리지 않습니다.
 */
export function LoginCard({ next }: Readonly<LoginCardProps>) {
  const kakaoHref = next
    ? `${ROUTES.kakaoStart}?next=${encodeURIComponent(next)}`
    : ROUTES.kakaoStart;

  // 카드 폭은 약관 링크 넷이 한 줄에 들어가는 값입니다. 좁히면 줄이 나뉩니다.
  return (
    <Card variant="elevated" padding="lg" className="mx-auto w-full max-w-[480px] sm:p-8">
      <p className="text-base font-bold text-fg">Komit</p>
      <h1 className="mt-4 text-3xl font-bold tracking-tight text-fg">시작하기</h1>

      <Link
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
      </Link>

      <div className="mt-7 border-t border-line-subtle pt-5">
        <p className="text-[13px] leading-6 text-fg-muted">
          계속하시면 아래 약관에 동의하신 것으로 간주합니다.
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
