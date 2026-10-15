import type { Metadata } from "next";
import { AccountVerificationForm } from "@/components/domains/mypage";
import { Container } from "@/components/layouts";
import { getCodes, getMyPage } from "@/lib/api/services";

export const metadata: Metadata = {
  title: "계좌 등록 | Komit",
  description: "거래 대금을 받을 계좌를 등록합니다.",
  robots: { index: false },
};

const BANK_CODE = "BANK_CODE";

/** 마이페이지에서 사용하는 계좌를 등록하거나 새 계좌로 바꿉니다. */
export default async function AccountPage() {
  const [mypage, banks] = await Promise.all([getMyPage(), getCodes(BANK_CODE)]);

  return (
    <Container className="py-8 sm:py-10">
      <AccountVerificationForm currentAccount={mypage.settlementAccount} banks={banks} />
    </Container>
  );
}
