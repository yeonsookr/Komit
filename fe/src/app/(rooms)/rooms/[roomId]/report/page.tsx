import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { ReportForm } from "@/components/domains/dispute";
import { RoomHeader } from "@/components/domains/room";
import { Container } from "@/components/layouts";
import { getContract, getMe, getRoom } from "@/lib/api/services";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

async function loadReportPage(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    if (room.contractId === null) notFound();
    const contract = await getContract(room.contractId);
    return { room, me, contract };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ roomId: string }>;
}): Promise<Metadata> {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) return { title: "방을 찾을 수 없습니다 | Komit" };
  const { room } = await loadReportPage(id);
  return { title: `문제 신고 · ${room.projectName} | Komit` };
}

export default async function RoomReportPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract } = await loadReportPage(id);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />
      <ReportForm roomId={room.roomId} contractId={contract.contractId} />
    </Container>
  );
}
