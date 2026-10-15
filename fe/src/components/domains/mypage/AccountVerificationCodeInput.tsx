"use client";

import * as React from "react";
import { Input } from "@/components/common";

export interface AccountVerificationCodeInputProps {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

const CODE_LENGTH = 4;

/** 앞자리 0을 보존하며 한 자리씩 입력하는 계좌 확인 번호 입력칸. */
export function AccountVerificationCodeInput({
  value,
  onChange,
  disabled,
}: Readonly<AccountVerificationCodeInputProps>) {
  const refs = React.useRef<Array<HTMLInputElement | null>>([]);
  const digits = Array.from({ length: CODE_LENGTH }, (_, index) => value[index] ?? "");

  const fill = (start: number, input: string) => {
    const incoming = input.replace(/\D/g, "").slice(0, CODE_LENGTH - start);
    if (incoming === "") return;

    const next = [...digits];
    incoming.split("").forEach((digit, offset) => {
      next[start + offset] = digit;
    });
    onChange(next.join(""));
    refs.current[Math.min(start + incoming.length, CODE_LENGTH - 1)]?.focus();
  };

  return (
    <div className="flex justify-center gap-2 sm:gap-3">
      {digits.map((digit, index) => (
        <Input
          key={index}
          ref={(element) => {
            refs.current[index] = element;
          }}
          type="text"
          inputMode="numeric"
          autoComplete={index === 0 ? "one-time-code" : "off"}
          autoFocus={index === 0}
          aria-label={`확인 번호 ${index + 1}번째 자리`}
          value={digit}
          maxLength={1}
          disabled={disabled}
          mono
          containerClassName="w-14"
          className="h-16 px-0 text-center text-2xl font-bold"
          onChange={(event) => {
            const incoming = event.target.value.replace(/\D/g, "");
            if (incoming === "") {
              onChange(value.slice(0, index));
              return;
            }
            fill(index, incoming);
          }}
          onKeyDown={(event) => {
            if (event.key === "Backspace" && digit === "" && index > 0) {
              onChange(value.slice(0, index - 1));
              refs.current[index - 1]?.focus();
            }
            if (event.key === "ArrowLeft" && index > 0) refs.current[index - 1]?.focus();
            if (event.key === "ArrowRight" && index < CODE_LENGTH - 1) {
              refs.current[index + 1]?.focus();
            }
          }}
          onPaste={(event) => {
            event.preventDefault();
            fill(index, event.clipboardData.getData("text"));
          }}
        />
      ))}
    </div>
  );
}
