import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { KeyValueRow, SectionCard } from "@/components/common";
import { formatShortDate } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { MyProject } from "@/types/mypage";

/**
 * "8/17 진단 · 방 4개" — 프로젝트 한 줄의 요약.
 *
 * 진단 회차(v2)는 이 응답에 없어 적지 않습니다. 목이 주는 것은 가장 최근 진단의
 * 식별자와 시각뿐입니다(목 명세 영역 8).
 */
function summarize(project: MyProject): string {
  const diagnosed = project.latestDiagnosedAt
    ? `${formatShortDate(project.latestDiagnosedAt)} 진단`
    : "아직 진단 없음";
  return `${diagnosed} · 방 ${project.roomCount}개`;
}

export interface ProjectSectionProps {
  projects: MyProject[];
}

/**
 * ProjectSection — 등록한 프로젝트.
 *
 * 진단을 한 프로젝트는 그 진단서로, 아직 안 한 프로젝트는 사전점검으로 보냅니다.
 * 버튼 문구도 그에 맞춰 갈립니다.
 */
export function ProjectSection({ projects }: Readonly<ProjectSectionProps>) {
  return (
    <SectionCard
      title="등록한 프로젝트"
      note={
        projects.length > 0 ? (
          <>
            <span className="font-mono tabular-nums">{projects.length}</span>개
          </>
        ) : undefined
      }
    >
      {projects.length === 0 ? (
        <KeyValueRow
          label="프로젝트"
          value={<span className="text-fg-muted">아직 등록한 프로젝트가 없습니다</span>}
          action={
            <Link href={ROUTES.checkup} className={buttonClass({ variant: "outline", size: "sm" })}>
              사전점검 하기
            </Link>
          }
        />
      ) : (
        projects.map((project) => (
          <KeyValueRow
            key={project.projectId}
            label={project.name}
            labelWidth={160}
            value={<span className="text-fg-muted">{summarize(project)}</span>}
            action={
              project.latestDiagnosisId === null ? (
                <Link
                  href={ROUTES.checkup}
                  className={buttonClass({ variant: "outline", size: "sm" })}
                >
                  사전점검 하기
                </Link>
              ) : (
                <Link
                  href={ROUTES.checkupResult(project.latestDiagnosisId)}
                  className={buttonClass({ variant: "outline", size: "sm" })}
                >
                  진단서 보기
                </Link>
              )
            }
          />
        ))
      )}
    </SectionCard>
  );
}
