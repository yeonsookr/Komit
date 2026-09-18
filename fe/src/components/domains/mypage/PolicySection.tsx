import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { KeyValueRow, SectionCard } from "@/components/common";
import { formatFullDate } from "@/lib/format";
import { visibleDocuments } from "@/lib/policies";

export interface PolicySectionProps {
  /** 개발자 프로필이 있으면 판매자 약관도 보여줍니다. */
  isDeveloper: boolean;
}

/**
 * PolicySection — 마이페이지 약관 칸.
 *
 * 동의한 문서를 개정일과 함께 보여주고 「보기」로 본문에 보냅니다. 줄마다 문서가
 * 다르므로 목록 화면을 거치지 않고 각 본문으로 바로 갑니다.
 *
 * 판매자 약관은 개발자 프로필을 만든 사람에게만 나옵니다. 만드는 것으로 동의를
 * 갈음하므로, 프로필이 없는 사람에게는 동의한 적 없는 문서입니다.
 *
 * 목록과 개정일은 `lib/policies.ts` 한 곳에 있습니다. 서버가 주는 값이 아닙니다.
 */
export function PolicySection({ isDeveloper }: Readonly<PolicySectionProps>) {
  return (
    <SectionCard title="약관">
      {visibleDocuments(isDeveloper).map((doc) => (
        <KeyValueRow
          key={doc.href}
          label={<span className="font-semibold text-fg">{doc.label}</span>}
          labelWidth={180}
          value={`${formatFullDate(doc.revisedAt)} 판`}
          action={
            <Link href={doc.href} className={buttonClass({ variant: "outline", size: "sm" })}>
              보기
            </Link>
          }
        />
      ))}
    </SectionCard>
  );
}
