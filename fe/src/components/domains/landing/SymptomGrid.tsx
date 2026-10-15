import Link from "next/link";
import { Card } from "@/components/common";
import { cn } from "@/lib/cn";
import { ROUTES } from "@/lib/routes";

/**
 * 증상 카드. 여섯 장 모두 개발자 찾기로 갑니다. 앞의 다섯은 그 증상을 다루는
 * 분야(DEV_FIELD 코드)로 좁히고, 마지막은 무엇이 문제인지 모르는 사람이라 좁히지 않습니다.
 *
 * 증상 → 분야 대응은 목 명세에 없어 화면이 정합니다. 코드는 `GET /api/codes/DEV_FIELD` 의 값입니다.
 *   41 인터넷에 올리기 · 42 DB 연결 · 43 도메인 연결 · 44 로그인 붙이기 · 46 비용 진단
 */
const SYMPTOMS = [
  { label: "남에게 안 보여요", href: ROUTES.developersByField(41), icon: GlobeIcon },
  { label: "저장이 안 돼요", href: ROUTES.developersByField(42), icon: DatabaseIcon },
  { label: "돈이 많이 나가요", href: ROUTES.developersByField(46), icon: TrendingUpIcon },
  { label: "주소가 안 붙어요", href: ROUTES.developersByField(43), icon: LinkIcon },
  { label: "로그인을 넣고 싶어요", href: ROUTES.developersByField(44), icon: LogInIcon },
  /** 분야를 고르지 않고 전체를 봅니다. 강조 카드(soft)로 그립니다. */
  { label: "뭐가 문제인지 몰라요", href: ROUTES.developers, icon: ChatIcon, emphasis: true },
] as const;

/** SymptomGrid — "증상별로 찾기". 카드 여섯 장을 3열로 놓습니다. */
export function SymptomGrid() {
  return (
    <section aria-labelledby="symptoms-heading" className="py-10">
      <h2 id="symptoms-heading" className="text-lg font-bold text-fg">
        증상별로 찾기
      </h2>

      <ul className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {SYMPTOMS.map(({ label, href, icon: Icon, ...rest }) => {
          const emphasis = "emphasis" in rest;
          return (
            <Card
              key={label}
              as="li"
              variant={emphasis ? "soft" : "outline"}
              interactive
              className="relative"
            >
              <Icon className={emphasis ? "text-primary" : "text-fg-secondary"} />
              {/* 카드 전체를 클릭 영역으로 넓힙니다(after 로 덮음). 포커스 링도 카드 테두리에 그립니다. */}
              <Link
                href={href}
                className={cn(
                  "mt-4 block text-base font-bold outline-none",
                  emphasis ? "text-primary" : "text-fg",
                  "after:absolute after:inset-0 after:rounded-xl",
                  "focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-primary",
                )}
              >
                {label}
              </Link>
            </Card>
          );
        })}
      </ul>
    </section>
  );
}

/* ---- 아이콘. 24px 선 아이콘, 색은 부모의 currentColor 를 따릅니다. ---- */

type IconProps = { className?: string };

function iconProps(className?: string) {
  return {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.75,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    className: `size-6 ${className ?? ""}`,
    "aria-hidden": true,
  } as const;
}

function GlobeIcon({ className }: IconProps) {
  return (
    <svg {...iconProps(className)}>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18" />
    </svg>
  );
}

function DatabaseIcon({ className }: IconProps) {
  return (
    <svg {...iconProps(className)}>
      <ellipse cx="12" cy="5.5" rx="8" ry="3" />
      <path d="M4 5.5v13c0 1.7 3.6 3 8 3s8-1.3 8-3v-13M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />
    </svg>
  );
}

function TrendingUpIcon({ className }: IconProps) {
  return (
    <svg {...iconProps(className)}>
      <path d="M3 17l6-6 4 4 8-8M15 7h6v6" />
    </svg>
  );
}

function LinkIcon({ className }: IconProps) {
  return (
    <svg {...iconProps(className)}>
      <path d="M10 13a5 5 0 0 0 7.1.1l3-3a5 5 0 0 0-7.1-7.1l-1.7 1.7" />
      <path d="M14 11a5 5 0 0 0-7.1-.1l-3 3a5 5 0 0 0 7.1 7.1l1.7-1.7" />
    </svg>
  );
}

function LogInIcon({ className }: IconProps) {
  return (
    <svg {...iconProps(className)}>
      <path d="M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5M10 17l5-5-5-5M15 12H3" />
    </svg>
  );
}

function ChatIcon({ className }: IconProps) {
  return (
    <svg {...iconProps(className)}>
      <path d="M21 12a8.5 8.5 0 0 1-12.6 7.4L3 21l1.6-5.4A8.5 8.5 0 1 1 21 12Z" />
    </svg>
  );
}
