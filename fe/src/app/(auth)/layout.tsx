import { Container } from "@/components/layouts";

/**
 * (auth) 레이아웃 — 연회색 배경 위에 내용을 가운데 놓습니다.
 *
 * GNB 도 푸터도 없습니다. 로그인은 서비스로 들어가기 전 화면이라 메뉴를 보여줄 것이
 * 없고, 화면에 선택지를 하나만 남기기 위해서입니다.
 *
 * 카드는 각 페이지가 직접 그립니다. 폭과 내용이 화면마다 다르기 때문입니다.
 * 운영 콘솔 로그인(`9_로그인`)도 같은 골격이라 이 레이아웃을 그대로 씁니다.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col justify-center bg-bg-alt py-10">
      <Container width="content">{children}</Container>
    </div>
  );
}
