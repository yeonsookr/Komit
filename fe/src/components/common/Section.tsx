import * as React from "react";
import { Card } from "./Card";

export interface SectionCardProps {
  title: string;
  /** 제목 오른쪽에 붙는 짧은 안내. "환불과 대금을 받는 계좌입니다" · "4건" */
  note?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * SectionCard — 제목 (+ 우측 안내) 아래에 테두리 카드를 놓는 칸.
 *   카드 안에는 보통 `KeyValueRow` 를 쌓습니다.
 *
 * 마이페이지(계정·계좌·프로젝트·알림·약관)가 쓰고, 설정처럼 같은 모양인 화면이 이어서
 * 나오므로 한 도메인에 두지 않고 공용으로 둡니다.
 */
export function SectionCard({ title, note, children }: Readonly<SectionCardProps>) {
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
