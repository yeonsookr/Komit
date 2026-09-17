import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { KeyValueRow, SectionCard } from "@/components/common";
import { ROUTES } from "@/lib/routes";
import type { MyDeveloper } from "@/types/mypage";

export interface DeveloperSectionProps {
  /** 개발자 프로필이 없으면 null 입니다. 그때는 만들러 가는 줄만 그립니다. */
  developer: MyDeveloper | null;
}

/**
 * DeveloperSection — 개발자 프로필 칸.
 *
 * 프로필이 없어도 칸을 감추지 않습니다. 감추면 개발자로도 활동할 수 있다는 것을
 * 알 길이 없습니다. 「프로필」 한 줄만 남겨 만들러 보냅니다.
 *
 * 만드는 것은 계정이 개발자로 바뀌는 게 아니라 역할이 하나 늘어나는 것입니다
 * (R-ACC-08). 의뢰인으로 하던 일은 그대로 둔 채 개발자로도 활동하게 됩니다.
 *
 * 「일정」 줄은 프로필이 있어야 의미가 있어 없을 때는 빠집니다. 만들기와 고치기가
 * 같은 화면이라 두 버튼이 같은 주소로 갑니다.
 *
 * 화면 시안에는 이름과 공개 여부도 있지만 이 응답에 없어 적지 않습니다. 목이 주는 것은
 * 받은 리뷰 수와 아직 하지 않은 세션 수뿐입니다(목 명세 영역 8).
 */
export function DeveloperSection({ developer }: Readonly<DeveloperSectionProps>) {
  return (
    <SectionCard title="개발자 프로필">
      <KeyValueRow
        label="프로필"
        value={
          developer ? (
            <span className="text-fg-muted">
              리뷰 <span className="font-mono tabular-nums">{developer.reviewCount}</span>개
            </span>
          ) : (
            <span className="text-fg-muted">아직 만들지 않았습니다</span>
          )
        }
        action={
          <Link
            href={ROUTES.mypageProfile}
            className={buttonClass({ variant: "outline", size: "sm" })}
          >
            {developer ? "프로필 편집" : "프로필 생성"}
          </Link>
        }
      />

      {developer && (
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
      )}
    </SectionCard>
  );
}
