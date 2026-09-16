import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { KeyValueRow, SectionCard } from "@/components/common";
import { formatFullDate } from "@/lib/format";
import { POLICY_DOCUMENTS } from "@/lib/policies";

/**
 * PolicySection — 마이페이지 약관 칸.
 *
 * 가입할 때 동의한 문서를 개정일과 함께 보여주고 「보기」로 본문에 보냅니다.
 * 줄마다 문서가 다르므로 목록 화면을 거치지 않고 각 본문으로 바로 갑니다.
 *
 * 목록과 개정일은 `lib/policies.ts` 한 곳에 있습니다. 서버가 주는 값이 아닙니다.
 */
export function PolicySection() {
  return (
    <SectionCard title="약관">
      {POLICY_DOCUMENTS.map((doc) => (
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
