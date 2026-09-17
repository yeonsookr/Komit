import type { Metadata } from "next";
import { Container } from "@/components/layouts";
import { PageHeader } from "@/components/common";
import {
  AccountSection,
  DeveloperSection,
  NotificationSection,
  PolicySection,
  ProjectSection,
  SettlementAccountSection,
  WithdrawalSection,
} from "@/components/domains/mypage";
import { getMyPage } from "@/lib/api/services";

export const metadata: Metadata = {
  title: "마이페이지 | Komit",
  description: "계정과 등록한 프로젝트를 관리합니다.",
  // 내 정보를 보여주는 화면이라 검색에 싣지 않습니다.
  robots: { index: false },
};

/**
 * 마이페이지.
 *
 * 계정·계좌·프로젝트·개발자 프로필·알림 설정이 한 응답에 들어 있어 한 번만 부르고
 * 각 칸에 나눠 내려줍니다. 로그인한 사람의 화면이라 정적으로 만들지 않습니다.
 *
 * 로그인하지 않았으면 조회가 401 이라 error.tsx 로 갑니다. 로그인 화면으로 돌려보내는
 * 처리는 인증 연동 단계에서 다른 보호 화면과 함께 넣습니다.
 */
export default async function MyPage() {
  const mypage = await getMyPage();

  return (
    <Container className="py-8 sm:py-10">
      <PageHeader title="마이페이지" description="계정과 등록한 프로젝트를 관리합니다." />

      <AccountSection account={mypage.account} />
      <SettlementAccountSection account={mypage.settlementAccount} />
      <ProjectSection projects={mypage.projects} />
      {/* 개발자 프로필이 없으면 이 칸은 아무것도 그리지 않습니다. */}
      <DeveloperSection developer={mypage.developer} />
      <NotificationSection setting={mypage.notificationSetting} />
      {/* 약관은 서버가 주는 값이 아니라 화면이 가진 정적 목록입니다. */}
      <PolicySection />
      <WithdrawalSection />
    </Container>
  );
}
