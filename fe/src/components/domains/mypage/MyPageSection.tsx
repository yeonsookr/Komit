import * as React from "react";
import { Card } from "@/components/common";

export interface MyPageSectionProps {
  title: string;
  /** 제목 오른쪽에 붙는 안내. "환불과 대금을 받는 계좌입니다" 처럼 짧은 한 줄입니다. */
  note?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * MyPageSection — 마이페이지의 한 칸.
 *   제목 (+ 우측 안내) 아래에 테두리 카드를 놓고, 카드 안에 KeyValueRow 를 쌓습니다.
 *
 * 계정·계좌·프로젝트·개발자 프로필·알림·약관·탈퇴가 모두 이 모양이라 여기로 모읍니다.
 */
export function MyPageSection({ title, note, children }: Readonly<MyPageSectionProps>) {
  return (
    <section className="mt-10">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 className="text-lg font-bold text-fg">{title}</h2>
        {note && <p className="text-[13px] text-fg-subtle">{note}</p>}
      </div>

      <Card variant="outline" padding="none" className="mt-3 px-5">
        {children}
      </Card>
    </section>
  );
}
