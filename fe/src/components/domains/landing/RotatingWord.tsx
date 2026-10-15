"use client";

import * as React from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { DURATION, EASE } from "@/lib/motion";

/** 한 낱말이 머무는 시간. 읽고 넘어가기에 충분하고, 눈에 거슬릴 만큼 길지 않은 값입니다. */
const HOLD_MS = 2200;

export interface RotatingWordProps {
  /** 돌아가며 보여줄 낱말. 첫 낱말이 서버에서 그려집니다. */
  words: readonly string[];
}

/**
 * RotatingWord — 제목 안의 한 낱말만 바꿔 끼웁니다.
 *
 * 랜딩은 SSG 라 본문이 스크립트 없이도 읽혀야 합니다(컨벤션 13장). 그래서 첫 낱말은
 * 서버에서 그대로 그리고, 자바스크립트가 붙은 뒤부터 다음 낱말로 넘깁니다. 스크립트가
 * 없거나 늦게 와도 제목은 처음부터 완성된 문장입니다.
 *
 * 움직임은 `transform` 과 `opacity` 만 씁니다. 낱말마다 폭이 달라 제목이 흔들리는 것을
 * 막으려고, 가장 긴 낱말만큼 자리를 미리 잡아 둡니다.
 *
 * `prefers-reduced-motion` 을 켠 사용자에게는 첫 낱말만 보여주고 넘기지 않습니다.
 * 읽는 동안 글자가 계속 바뀌는 것은 가독성을 해칩니다.
 */
export function RotatingWord({ words }: Readonly<RotatingWordProps>) {
  const [index, setIndex] = React.useState(0);
  const [rotating, setRotating] = React.useState(false);
  const prefersReducedMotion = useReducedMotion();

  React.useEffect(() => {
    if (prefersReducedMotion || words.length < 2) return;

    setRotating(true);
    const timer = setInterval(
      () => setIndex((current) => (current + 1) % words.length),
      HOLD_MS,
    );
    return () => clearInterval(timer);
  }, [prefersReducedMotion, words.length]);

  /*
    넘기기 전에는 낱말 하나만 둡니다. 서버가 그리는 것도 이 모양입니다.
    아래의 자리 잡기용 사본은 HTML 에 글자로 남기 때문에, CSS 없이 읽으면
    낱말이 줄줄이 이어진 문장이 됩니다. 사본은 넘길 때만 있으면 되므로
    마운트 전까지는 만들지 않습니다.
  */
  if (!rotating) {
    return <span className="text-primary">{words[0]}</span>;
  }

  return (
    <span className="inline-grid text-primary">
      {/*
        자리 잡기용 사본. 낱말을 모두 같은 칸에 겹쳐 두면 칸의 폭이 가장 긴 낱말에
        맞춰져, 낱말이 바뀌어도 제목의 줄바꿈과 폭이 그대로입니다.
      */}
      {words.map((candidate) => (
        <span key={candidate} aria-hidden className="invisible [grid-area:1/1]">
          {candidate}
        </span>
      ))}

      {/* `initial={false}` — 넘기기 시작하는 첫 낱말은 움직이지 않습니다. 서버가 그린 그대로 이어집니다. */}
      <AnimatePresence initial={false}>
        <motion.span
          key={words[index]}
          className="[grid-area:1/1]"
          initial={{ opacity: 0, y: "0.35em" }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: "-0.35em" }}
          transition={{ duration: DURATION.slow, ease: EASE }}
        >
          {words[index]}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
