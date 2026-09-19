"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/buttons";
import { Card, CardHeader, Field, Input, NativeSelect, Textarea } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/** ERD `consulting_agreements.topic` 과 같은 길이. 넘기면 서버가 422 를 줍니다. */
const MAX_TOPIC_LENGTH = 255;

/** 세션 길이. 한 번 만나는 자리라 짧게 끊어 고르게 합니다. */
const DURATIONS = [30, 45, 60, 90];

/** 정각과 30분만 잡습니다. 5분 단위까지 열면 고르는 일만 번거로워집니다. */
const MINUTES = ["00", "30"];

const HOURS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, "0"));

export interface ConsultingContractFormProps {
  roomId: number;
  /** 상대 이름. 무엇을 정해 보내는지 적는 데 씁니다. */
  counterpartName: string;
  /** 고칠 계약. 없으면 새로 씁니다. */
  contract?: Contract | null;
}

/**
 * ConsultingContractForm — 컨설팅 계약서 작성·수정 (`4_개발자_계약서작성`).
 *
 * **개발 계약서와 달리 작업을 쪼개지 않습니다.** 한 번 만나서 무엇을 다룰지·언제
 * 얼마나·얼마에 하는지가 계약의 전부입니다.
 *
 * 작성과 수정이 부르는 곳이 다릅니다. 새로 쓰면 방에 계약서를 만들고(`POST`), 고치면
 * 이미 있는 계약서의 보낸 항목만 바꿉니다(`PATCH`). 개발 계약서는 다시 써서 덮어쓰지만
 * 컨설팅은 고칠 것이 적어 바뀐 값만 보내는 편이 무엇을 바꿨는지 분명합니다.
 *
 * 서명한 뒤에는 고칠 수 없습니다 (R-CON-02). 그 판단은 서버가 하고 화면은 서명 전에만
 * 이 폼으로 들어옵니다.
 */
export function ConsultingContractForm({
  roomId,
  counterpartName,
  contract = null,
}: Readonly<ConsultingContractFormProps>) {
  const router = useRouter();
  const showToast = useToast();

  const session = contract?.consulting?.sessions[0] ?? null;
  const scheduled = session === null ? null : new Date(session.reservation.scheduledAt);

  const [topic, setTopic] = React.useState(contract?.consulting?.topic ?? "");
  const [date, setDate] = React.useState(toDateValue(scheduled));
  const [hour, setHour] = React.useState(scheduled === null ? "14" : pad(scheduled.getHours()));
  const [minute, setMinute] = React.useState(
    scheduled === null ? "00" : pad(scheduled.getMinutes() < 30 ? 0 : 30),
  );
  const [durationMinutes, setDurationMinutes] = React.useState(
    String(session?.durationMinutes ?? 30),
  );
  const [price, setPrice] = React.useState(
    session === null ? "" : session.price.toLocaleString("ko-KR"),
  );
  const [message, setMessage] = React.useState("");
  const [sending, setSending] = React.useState(false);

  const amending = contract !== null;
  const priceValue = Number(price.replace(/\D/g, ""));
  const canSend = topic.trim() !== "" && date !== "" && price !== "" && !sending;

  const send = async () => {
    if (!canSend) return;
    setSending(true);

    /*
      시각은 한국 기준으로 굳혀 보냅니다. 브라우저의 시간대를 그대로 따르면 해외에서
      계약서를 쓴 개발자와 의뢰인이 서로 다른 시각을 보게 됩니다 — 세션은 두 사람이
      같은 시각에 만나는 약속이라 어긋나면 안 됩니다.
    */
    const scheduledAt = `${date}T${hour}:${minute}:00+09:00`;
    const body = {
      topic: topic.trim(),
      scheduledAt,
      durationMinutes: Number(durationMinutes),
      price: priceValue,
      message: message.trim() === "" ? null : message.trim(),
    };

    try {
      if (amending) {
        await clientApi.patch(INTERNAL.CONTRACT(contract.contractId), body);
      } else {
        await clientApi.post(INTERNAL.CONSULTING_CONTRACTS(roomId), body);
      }
      showToast({
        tone: "success",
        title: amending ? "계약서를 다시 보냈습니다." : "계약서를 보냈습니다.",
        description: `${counterpartName}님이 확인하면 서명할 수 있습니다.`,
      });
      router.push(ROUTES.roomContract(roomId));
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "계약서를 보내지 못했습니다.",
      });
      setSending(false);
    }
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">
          {amending ? "컨설팅 계약서 수정" : "컨설팅 계약서 작성"}
        </h2>
        <p className="mt-1.5 text-[13px] leading-6 text-fg-subtle">
          {amending
            ? `고치면 ${counterpartName}님에게 다시 보냅니다.`
            : `${counterpartName}님이 컨설팅 계약으로 진행하기로 했습니다. 세션 구성과 시간, 가격을 정해 보냅니다.`}
        </p>
      </header>

      <div className="mt-6">
        <CardHeader title="컨설팅 구성" />
        <Card className="mt-3">
          <div className="flex flex-col gap-5">
            <Field
              layout="horizontal"
              label="다룰 항목"
              htmlFor="consulting-topic"
              helper="이 세션에서 무엇을 볼지 한 줄로 적습니다."
            >
              <Input
                id="consulting-topic"
                value={topic}
                maxLength={MAX_TOPIC_LENGTH}
                placeholder="예) 회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다"
                onChange={(event) => setTopic(event.target.value)}
              />
            </Field>

            {/* 날짜와 시각은 한 줄에 둡니다. 떨어뜨리면 언제 만나는지가 한눈에 안 들어옵니다. */}
            {/* 라벨은 줄 전체를 가리키지만 htmlFor 는 첫 칸(날짜)을 짚습니다. */}
            <Field layout="horizontal" label="날짜와 시간" htmlFor="consulting-date">
              <div className="flex flex-wrap items-center gap-2">
                <Input
                  id="consulting-date"
                  type="date"
                  className="w-40"
                  value={date}
                  onChange={(event) => setDate(event.target.value)}
                />
                <NativeSelect
                  containerClassName="w-20"
                  aria-label="시"
                  value={hour}
                  onChange={(event) => setHour(event.target.value)}
                >
                  {HOURS.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </NativeSelect>
                <span className="text-[13px] text-fg-subtle">시</span>
                <NativeSelect
                  containerClassName="w-20"
                  aria-label="분"
                  value={minute}
                  onChange={(event) => setMinute(event.target.value)}
                >
                  {MINUTES.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </NativeSelect>
                <span className="text-[13px] text-fg-subtle">분</span>
              </div>
            </Field>

            <Field layout="horizontal" label="시간">
              <div className="flex items-center gap-2">
                <NativeSelect
                  containerClassName="w-24"
                  aria-label="세션 시간"
                  value={durationMinutes}
                  onChange={(event) => setDurationMinutes(event.target.value)}
                >
                  {DURATIONS.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </NativeSelect>
                <span className="text-[13px] text-fg-subtle">분</span>
              </div>
            </Field>

            <Field layout="horizontal" label="가격" htmlFor="consulting-price">
              <Input
                id="consulting-price"
                mono
                suffix="원"
                containerClassName="w-44"
                inputMode="numeric"
                value={price}
                placeholder="50,000"
                onChange={(event) => {
                  const digits = event.target.value.replace(/\D/g, "");
                  setPrice(digits === "" ? "" : Number(digits).toLocaleString("ko-KR"));
                }}
              />
            </Field>
          </div>
        </Card>
      </div>

      <div className="mt-10">
        <CardHeader title="전달 메시지" />
        <Card className="mt-3">
          <Textarea
            aria-label="전달 메시지"
            value={message}
            maxLength={500}
            showCount
            placeholder="계약서와 함께 남길 말이 있으면 적어주세요."
            onChange={(event) => setMessage(event.target.value)}
          />
        </Card>
      </div>

      <div className="mt-8 flex justify-end">
        <Button size="lg" onClick={send} loading={sending} disabled={!canSend}>
          {amending ? "계약서 다시 보내기" : "계약서 보내기"}
        </Button>
      </div>
    </section>
  );
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

/** `<input type="date">` 가 읽는 `YYYY-MM-DD`. 고칠 계약이 없으면 비웁니다. */
function toDateValue(date: Date | null): string {
  if (date === null) return "";
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
