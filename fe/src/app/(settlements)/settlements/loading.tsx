import { Container } from "@/components/layouts";

/** 정산을 읽는 동안. 왼쪽 목록까지 함께 기다리므로 본문 자리만 비워 둡니다. */
export default function SettlementLoading() {
  return (
    <Container className="py-10">
      <p className="text-sm text-fg-muted">정산 내역을 불러오는 중입니다…</p>
    </Container>
  );
}
