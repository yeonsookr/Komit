import type { Metadata } from "next";
import { CheckupStart } from "@/components/domains/checkup";

export const metadata: Metadata = {
  title: "사전점검 | Komit",
  description:
    "만든 서비스가 지금 어떤 상태인지 3분 만에 점검합니다. 무엇이 막혀 있는지 문장으로 정리해 드립니다.",
};

/** 설문1. 진단이 아직 없어 서버에서 문항을 받지 않습니다. */
export default function CheckupPage() {
  return <CheckupStart />;
}
