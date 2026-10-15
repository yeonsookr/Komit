import { Container } from "@/components/layouts";

/** 알림 목록을 읽는 동안 본문 자리를 유지합니다. */
export default function NotificationsLoading() {
  return (
    <Container className="py-10">
      <p className="text-sm text-fg-muted">알림을 불러오는 중입니다…</p>
    </Container>
  );
}
