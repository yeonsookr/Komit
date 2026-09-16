import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Avatar, KeyValueRow, SectionCard } from "@/components/common";
import { ROUTES } from "@/lib/routes";
import type { MyAccount, SettlementAccount } from "@/types/mypage";
import { NameEditRow } from "./NameEditRow";

export interface AccountSectionProps {
  account: MyAccount;
}

/**
 * AccountSection — 계정 칸. 프로필 사진 · 이름 · 카카오 계정.
 *
 * 사진 바꾸기는 파일 올리기가 필요해 아직 붙이지 않았습니다. 지금은 어떤 사진을
 * 쓰고 있는지만 알려줍니다.
 */
export function AccountSection({ account }: Readonly<AccountSectionProps>) {
  return (
    <SectionCard title="계정">
      <KeyValueRow
        label="프로필 사진"
        value={
          <span className="flex items-center gap-3">
            <Avatar size="md" name={account.name} src={account.profileImageUrl ?? undefined} />
            <span className="text-[13px] text-fg-muted">
              {account.profileImageUrl
                ? "카카오 프로필 사진을 쓰고 있습니다"
                : "등록한 사진이 없어 이름 첫 글자를 씁니다"}
            </span>
          </span>
        }
      />

      <NameEditRow name={account.name} />

      {/* 카카오 계정은 우리가 바꿀 수 없는 값이라 보여주기만 합니다. */}
      <KeyValueRow label="카카오 계정" value={account.email ?? "동의하지 않아 받지 못했습니다"} />
    </SectionCard>
  );
}

export interface SettlementAccountSectionProps {
  /** 등록하지 않았으면 null 이고 칸이 등록 안내로 바뀝니다. */
  account: SettlementAccount | null;
}

/**
 * SettlementAccountSection — 계좌 칸.
 *
 * 계좌는 사람마다 하나이고 새 계좌를 확인하면 기존 것을 대신합니다. (R-ACC-04)
 * 그래서 버튼 문구가 등록 전에는 「계좌 등록」, 등록 후에는 「계좌 변경」입니다.
 */
export function SettlementAccountSection({ account }: Readonly<SettlementAccountSectionProps>) {
  return (
    <SectionCard title="계좌" note="환불과 대금을 받는 계좌입니다">
      <KeyValueRow
        label="등록 계좌"
        value={
          account ? (
            `${account.bankName} ${account.maskedNumber} · 예금주 ${account.accountHolder}`
          ) : (
            <span className="text-fg-muted">등록한 계좌가 없습니다</span>
          )
        }
        action={
          <Link href={ROUTES.accountRegister} className={buttonClass({ variant: "outline", size: "sm" })}>
            {account ? "계좌 변경" : "계좌 등록"}
          </Link>
        }
      />
    </SectionCard>
  );
}
