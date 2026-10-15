"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { Checkbox } from "@/components/common";
import { Modal, ModalFooter, ModalHeader } from "@/components/feedback";

/** 그리는 곳의 크기. 화면 폭과 무관하게 같은 그림이 나오도록 고정합니다. */
const WIDTH = 480;
const HEIGHT = 180;
/** 선 굵기. 손글씨로 보일 만큼만 굵게 잡습니다. */
const LINE_WIDTH = 2.5;

export interface SignaturePadProps {
  open: boolean;
  onClose: () => void;
  /** 어느 계약서에 서명하는지. 「개발 계약서 · 수제 비누 가게 · 650,000원」 */
  subject: string;
  /** 확인 버튼 문구. 의뢰인은 예치, 개발자는 계약이 이어집니다. */
  confirmLabel: string;
  saving?: boolean;
  /** 그린 서명을 PNG 로 넘깁니다. */
  onSign: (signature: File) => void;
}

/**
 * SignaturePad — 계약서 서명판 (`5_서명판`).
 *
 * 컨설팅과 개발이 같은 것을 씁니다. 그린 그림을 파일로 올려 받은 식별자로
 * 서명하므로 이 컴포넌트는 그리기와 동의 확인까지만 맡습니다.
 */
export function SignaturePad({
  open,
  onClose,
  subject,
  confirmLabel,
  saving,
  onSign,
}: Readonly<SignaturePadProps>) {
  const canvasRef = React.useRef<HTMLCanvasElement>(null);
  const drawing = React.useRef(false);
  const [drawn, setDrawn] = React.useState(false);
  const [agreed, setAgreed] = React.useState(false);

  // 열 때마다 빈 서명판에서 시작합니다. 지난번에 그린 것이 남아 있으면 안 됩니다.
  React.useEffect(() => {
    if (!open) return;
    setDrawn(false);
    setAgreed(false);
    const context = canvasRef.current?.getContext("2d");
    context?.clearRect(0, 0, WIDTH, HEIGHT);
  }, [open]);

  /** 화면에 보이는 크기와 캔버스 좌표가 다를 수 있어 비율로 환산합니다. */
  const pointOf = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return {
      x: ((event.clientX - rect.left) / rect.width) * WIDTH,
      y: ((event.clientY - rect.top) / rect.height) * HEIGHT,
    };
  };

  const start = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const context = canvasRef.current?.getContext("2d");
    if (!context) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    drawing.current = true;

    const { x, y } = pointOf(event);
    context.lineWidth = LINE_WIDTH;
    context.lineCap = "round";
    context.lineJoin = "round";
    context.strokeStyle = "#111111";
    context.beginPath();
    context.moveTo(x, y);
  };

  const move = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return;
    const context = canvasRef.current?.getContext("2d");
    if (!context) return;

    const { x, y } = pointOf(event);
    context.lineTo(x, y);
    context.stroke();
    setDrawn(true);
  };

  const end = () => {
    drawing.current = false;
  };

  const clear = () => {
    canvasRef.current?.getContext("2d")?.clearRect(0, 0, WIDTH, HEIGHT);
    setDrawn(false);
  };

  const sign = () => {
    const canvas = canvasRef.current;
    if (!canvas || !drawn || !agreed || saving) return;

    canvas.toBlob((blob) => {
      if (blob === null) return;
      onSign(new File([blob], "signature.png", { type: "image/png" }));
    }, "image/png");
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="md"
      aria-labelledby="signature-pad-title"
    >
      <ModalHeader
        id="signature-pad-title"
        title="계약서에 서명해 주세요"
        description={subject}
      />

      <div className="relative">
        <canvas
          ref={canvasRef}
          width={WIDTH}
          height={HEIGHT}
          onPointerDown={start}
          onPointerMove={move}
          onPointerUp={end}
          onPointerCancel={end}
          className="block w-full touch-none rounded-xl border border-dashed border-line bg-surface"
        />
        {!drawn && (
          <p className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-fg-subtle">
            이 안에 이름을 써 주세요
          </p>
        )}
      </div>

      <div className="mt-2 flex items-center justify-between">
        <p className="text-[13px] text-fg-subtle">
          마우스나 손가락으로 그립니다
        </p>
        <Button variant="ghost" size="sm" onClick={clear} disabled={!drawn}>
          지우기
        </Button>
      </div>

      <div className="mt-5">
        <Checkbox
          checked={agreed}
          onChange={(event) => setAgreed(event.target.checked)}
          label="계약서 내용을 확인했고 이에 동의합니다"
        />
      </div>

      <ModalFooter>
        <Button variant="outline" onClick={onClose} disabled={saving}>
          취소
        </Button>
        <Button
          variant="dark"
          loading={saving}
          disabled={!drawn || !agreed}
          onClick={sign}
        >
          {confirmLabel}
        </Button>
      </ModalFooter>
    </Modal>
  );
}
