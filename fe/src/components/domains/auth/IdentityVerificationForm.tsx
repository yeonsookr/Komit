"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button, buttonClass } from "@/components/buttons";
import { Card, Field, Input } from "@/components/common";
import { ROUTES } from "@/lib/routes";

const CARRIERS = ["SKT", "KT", "LG U+", "알뜰폰"] as const;
type Carrier = (typeof CARRIERS)[number];

export function IdentityVerificationForm({ next }: Readonly<{ next?: string }>) {
  const router = useRouter();
  const [carrier, setCarrier] = useState<Carrier | null>(null);
  const [name, setName] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [phone, setPhone] = useState("");
  const [agreed, setAgreed] = useState(false);
  const ready = carrier !== null && name.trim() !== "" && /^\d{6}$/.test(birthDate)
    && /^\d{10,11}$/.test(phone) && agreed;

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (ready) router.push(ROUTES.signupVerification(next));
  };

  return (
    <Card variant="elevated" padding="lg" className="mx-auto w-full max-w-[520px] sm:p-9">
      <p className="text-sm font-extrabold tracking-tight text-primary">PASS</p>
      <h1 className="mt-4 text-3xl font-bold tracking-tight text-fg">휴대폰 본인인증</h1>
      <p className="mt-3 text-sm leading-6 text-fg-muted">
        본인 명의 계좌를 등록하기 전에 정보를 확인해 주세요.
      </p>

      <form onSubmit={submit} className="mt-8 space-y-6">
        <fieldset>
          <legend className="mb-3 text-sm font-semibold text-fg">통신사 선택</legend>
          <div className="grid grid-cols-2 gap-2">
            {CARRIERS.map((option) => (
              <button
                key={option}
                type="button"
                aria-pressed={carrier === option}
                onClick={() => setCarrier(option)}
                className={`rounded-xl border px-4 py-3 text-sm font-semibold transition-colors ${
                  carrier === option
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-line-subtle bg-bg text-fg hover:border-primary"
                }`}
              >
                {option}
              </button>
            ))}
          </div>
        </fieldset>

        <Field label="이름" htmlFor="identity-name" required>
          <Input
            id="identity-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            autoComplete="name"
            placeholder="실명을 입력해 주세요"
          />
        </Field>
        <Field label="생년월일" htmlFor="identity-birth-date" required>
          <Input
            id="identity-birth-date"
            value={birthDate}
            onChange={(event) => setBirthDate(event.target.value.replace(/\D/g, "").slice(0, 6))}
            inputMode="numeric"
            placeholder="생년월일 6자리"
          />
        </Field>
        <Field label="휴대폰 번호" htmlFor="identity-phone" required>
          <Input
            id="identity-phone"
            value={phone}
            onChange={(event) => setPhone(event.target.value.replace(/\D/g, "").slice(0, 11))}
            inputMode="tel"
            autoComplete="tel"
            placeholder="숫자만 입력해 주세요"
          />
        </Field>

        <label className="flex items-start gap-3 text-sm leading-6 text-fg-muted">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(event) => setAgreed(event.target.checked)}
            className="mt-1 size-4 accent-primary"
          />
          입력한 정보로 본인인증을 진행하는 데 동의합니다.
        </label>

        <div className="flex flex-col gap-3 pt-2">
          <Button type="submit" size="lg" fullWidth disabled={!ready}>
            계속하기
          </Button>
          <Link
            href={ROUTES.signupAccount(next)}
            className={buttonClass({ variant: "outline", size: "lg", fullWidth: true })}
          >
            이전으로
          </Link>
        </div>
      </form>
    </Card>
  );
}
