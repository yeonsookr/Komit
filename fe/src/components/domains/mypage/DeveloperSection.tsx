import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { KeyValueRow, SectionCard } from "@/components/common";
import { ROUTES } from "@/lib/routes";
import type { MyDeveloper } from "@/types/mypage";

export interface DeveloperSectionProps {
  /** 개발자 프로필이 없으면 null 입니다. 그때는 이 칸을 그리지 않습니다. */
  developer: MyDeveloper | null;
}

/**
 * DeveloperSection — 개발자 프로필 칸.
 *
 * 한 계정이 의뢰인과 개발자를 함께 가질 수 있고(R-ACC-08), 개발자 프로필이 없으면
 * 응답이 null 로 와서 칸이 통째로 빠집니다. "없음" 이라고 적지 않습니다.
 *
 * 화면 시안에는 이름과 공개 여부도 있지만 이 응답에 없어 적지 않습니다. 목이 주는 것은
 * 받은 리뷰 수와 아직 하지 않은 세션 수뿐입니다(목 명세 영역 8).
 */
export function DeveloperSection({ developer }: Readonly<DeveloperSectionProps>) {
  if (developer === null) return null;

  return (
    <SectionCard title="개발자 프로필" note="개발자로 이용 중일 때만 보입니다">
      <KeyValueRow
        label="프로필"
        value={
          <span className="text-fg-muted">
            리뷰 <span className="font-mono tabular-nums">{developer.reviewCount}</span>개
          </span>
        }
        action={
          <Link
            href={ROUTES.mypageProfile}
            className={buttonClass({ variant: "outline", size: "sm" })}
          >
            프로필 편집
          </Link>
        }
      />

      <KeyValueRow
        label="일정"
        value={
          <span className="text-fg-muted">
            잡힌 세션{" "}
            <span className="font-mono tabular-nums">{developer.upcomingSessionCount}</span>건
          </span>
        }
        action={
          <Link
            href={ROUTES.mypageSchedule}
            className={buttonClass({ variant: "outline", size: "sm" })}
          >
            일정 관리
          </Link>
        }
      />
    </SectionCard>
  );
}
