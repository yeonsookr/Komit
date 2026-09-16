"use client";

import * as React from "react";
import Form from "next/form";
import { Button } from "@/components/buttons";
import { Chip, SearchInput } from "@/components/common";
import { KEYWORD_PARAM } from "@/lib/developerSearch";
import { ROUTES } from "@/lib/routes";

/** 검색바 아래 입력 예시. 누르면 입력칸에 그대로 채워집니다. */
const SYMPTOM_EXAMPLES = [
  "예약 버튼이 반응이 없어요",
  "갑자기 API 비용이 40만원 나갔어요",
  "주소 연결하는 방법을 모르겠어요",
];

export interface LandingHeroProps {
  /** 이번 주에 열린 상담 수. 지표를 읽지 못했으면 null 이고 그 줄은 비웁니다. */
  weeklyInquiryRoomCount: number | null;
}

/**
 * LandingHero — 랜딩 첫 화면. 제목 · 증상 검색바 · 입력 예시 칩 · 이번 주 상담 N건.
 *
 * "문제 진단하기"는 개발자 찾기의 문제 검색으로 이어집니다. 적은 문장이 그대로
 * 검색어가 되고, 검색 방식은 개발자 찾기의 기본값(`problem`)이라 따로 싣지 않습니다.
 * 로그인 여부와 무관한 경로입니다.
 *
 * 클라이언트 컴포넌트인 이유는 예시 칩이 입력칸을 채워야 해서입니다. 제출 자체는
 * next/form 의 GET 동작이라 라우터 코드가 없습니다.
 */
export function LandingHero({ weeklyInquiryRoomCount }: Readonly<LandingHeroProps>) {
  const [symptom, setSymptom] = React.useState("");

  return (
    <section className="flex flex-col items-center py-16 text-center sm:py-24">
      <h1 className="text-3xl font-bold tracking-tight text-fg sm:text-4xl">
        당신의 ( ) 아이디어, Komit과 함께 완성해보세요.
      </h1>

      <Form
        action={ROUTES.developers}
        className="mt-8 flex w-full max-w-[1136px] flex-col gap-3 sm:flex-row"
      >
        <SearchInput
          // 비었을 때 이름을 빼면 폼에 담기지 않아 주소가 `/developers` 로만 갑니다.
          // 이름을 둔 채 보내면 빈 검색어(`?keyword=`)가 주소에 남습니다.
          name={symptom.trim() ? KEYWORD_PARAM : undefined}
          value={symptom}
          onChange={(event) => setSymptom(event.target.value)}
          placeholder="예: 딸한테 주소를 보내도 안 열려요"
          aria-label="겪고 있는 문제"
          autoComplete="off"
          containerClassName="min-w-0 flex-1"
        />
        <Button type="submit" size="lg" className="shrink-0 sm:px-8">
          문제 진단하기
        </Button>
      </Form>

      <ul className="mt-5 flex flex-wrap justify-center gap-2" aria-label="입력 예시">
        {SYMPTOM_EXAMPLES.map((example) => (
          <li key={example}>
            <Chip variant="suggest" onClick={() => setSymptom(example)}>
              {example}
            </Chip>
          </li>
        ))}
      </ul>

      {weeklyInquiryRoomCount !== null && (
        <p className="mt-6 text-[13px] text-fg-subtle">
          이번 주 상담 <span className="font-mono tabular-nums">{weeklyInquiryRoomCount}</span>건
        </p>
      )}
    </section>
  );
}
