import { Container } from "@/components/layouts";

export default function MyScheduleLoading() {
  return (
    <Container className="py-10">
      <p className="text-sm text-fg-muted">일정을 불러오는 중입니다…</p>
    </Container>
  );
}
