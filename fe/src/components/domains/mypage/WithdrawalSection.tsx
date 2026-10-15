import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { KeyValueRow, SectionCard } from "@/components/common";
import { ROUTES } from "@/lib/routes";

/**
 * WithdrawalSection — 마이페이지 탈퇴 칸.
 *
 * 여기서 바로 탈퇴시키지 않고 탈퇴 화면으로 보냅니다. 되돌릴 수 없는 일이라
 * 무엇이 남는지 읽고 무엇을 정리해야 하는지 확인한 뒤에 누르게 합니다. (R-ACC-05)
 */
export function WithdrawalSection() {
  return (
    <SectionCard title="탈퇴">
      <KeyValueRow
        label="회원 탈퇴"
        labelWidth={180}
        action={
          <Link
            href={ROUTES.mypageWithdrawal}
            className={buttonClass({ variant: "danger", size: "sm" })}
          >
            탈퇴하기
          </Link>
        }
      />
    </SectionCard>
  );
}
