import type { Metadata } from "next";
import { PageHeader } from "@/components/common";
import { ScheduleView } from "@/components/domains/schedule";
import { Container } from "@/components/layouts";
import { getMySchedule } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";

export const metadata: Metadata = {
  title: "내 일정 | Komit",
  description: "의뢰하거나 맡은 컨설팅 일정을 확인합니다.",
  robots: { index: false },
};

export default async function MySchedulePage() {
  const schedule = await requireLogin(getMySchedule);

  return (
    <Container className="py-8 sm:py-10">
      <PageHeader title="내 일정" description="의뢰하거나 맡은 컨설팅 세션입니다." />
      <ScheduleView schedule={schedule} now={new Date().toISOString()} />
    </Container>
  );
}
