"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/buttons";
import { Checkbox } from "@/components/common";
import { Modal, ModalFooter, ModalHeader, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { dropHeldScreen, holdScreen, requestScreen } from "@/lib/screenShare";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

/**
 * 동의를 받는 항목. 둘 다 켜야 들어갈 수 있습니다.
 *
 * 서버에는 「동의했다」 한 줄로만 남습니다 (`R-CON-17`). 항목별로 남길 자리가 없고,
 * 하나만 켜고 들어가는 길도 없습니다.
 */
const CONSENTS = [
  "이 통화를 녹음합니다",
  "녹음을 글로 옮겨 외부 AI 서비스로 요약을 만듭니다",
];

export interface SessionEntryDialogProps {
  open: boolean;
  onClose: () => void;
  sessionId: number;
}

/**
 * SessionEntryDialog — 컨설팅 방에서 세션에 들어가기 전에 뜨는 동의 팝업.
 *
 * **동의는 여기서만 받습니다.** 세션 화면은 동의한 사람만 엽니다 — 주소를 직접 열어
 * 이 자리를 건너뛰면 방으로 돌아옵니다.
 *
 * **들어간 것과 기다리는 것을 가르지 않습니다.** 양쪽이 모여야 시작하지만(`R-CON-13`)
 * 기다리는 동안에도 자기 카메라를 확인할 수 있어야 하고, 상대가 들어온 것을 알려면
 * 세션 화면의 연결이 필요합니다. 그래서 둘 다 세션 화면으로 보냅니다.
 */
export function SessionEntryDialog({
  open,
  onClose,
  sessionId,
}: Readonly<SessionEntryDialogProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [agreed, setAgreed] = React.useState<boolean[]>(() => CONSENTS.map(() => false));
  const [entering, setEntering] = React.useState(false);

  // 닫았다 다시 열면 처음부터 묻습니다. 지난번 체크가 남아 있으면 읽지 않고 들어갑니다.
  React.useEffect(() => {
    if (!open) {
      setAgreed(CONSENTS.map(() => false));
      setEntering(false);
    }
  }, [open]);

  const toggle = (index: number) =>
    setAgreed((previous) => previous.map((value, at) => (at === index ? !value : value)));

  const enter = async () => {
    setEntering(true);
    /*
      보낼 화면을 고르는 창은 누른 이 순간에만 뜹니다. 그래서 입장 요청보다 먼저 엽니다.
      고르기를 기다리지 않고 세션 화면으로 넘어가며, 고른 화면은 그쪽 통화가 받아 씁니다.
    */
    holdScreen(sessionId, requestScreen());
    try {
      await clientApi.post(INTERNAL.SESSION_PARTICIPATIONS(sessionId));
      router.push(ROUTES.session(sessionId));
    } catch (error) {
      dropHeldScreen();
      setEntering(false);
      showToast({
        tone: "danger",
        title: "세션에 들어가지 못했습니다",
        // 취소된 계약(R-CON-21)이나 입장 시각 전처럼 서버가 이유를 알려 주면 그대로 보여줍니다.
        description:
          error instanceof ApiError
            ? error.message
            : "잠시 후 다시 시도해 주세요. 이미 끝난 세션일 수 있습니다.",
      });
    }
  };

  return (
    <Modal open={open} onClose={onClose} aria-labelledby="session-entry-title">
      <ModalHeader id="session-entry-title" title="컨설팅 세션 입장" />

      <div className="flex flex-col gap-3">
        {CONSENTS.map((label, index) => (
          <Checkbox
            key={label}
            label={label}
            checked={agreed[index]}
            onChange={() => toggle(index)}
          />
        ))}
      </div>

      <ModalFooter>
        <Button variant="outline" onClick={onClose} disabled={entering}>
          취소
        </Button>
        <Button onClick={enter} disabled={!agreed.every(Boolean) || entering}>
          입장
        </Button>
      </ModalFooter>
    </Modal>
  );
}
