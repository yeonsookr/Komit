"use client";

import * as React from "react";

/**
 * 담아 둘 형식. 앞에서부터 브라우저가 받아 주는 것을 씁니다.
 *
 * opus 가 말소리에 가장 작고 깨끗합니다. Safari 는 webm 을 만들지 못해 mp4 로 내려갑니다.
 */
const MIME_CANDIDATES = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4"] as const;

/**
 * 조각으로 끊어 받는 간격.
 *
 * 끊어 받아도 조각은 이 훅의 메모리에만 쌓입니다. **탭을 닫으면 전부 사라집니다** —
 * 어딘가에 남겨 두고 다음에 올리는 일은 아직 하지 않습니다. 지금 이 간격이 버는 것은
 * 녹음이 중간에 잘못돼도 그때까지 받은 조각은 우리 손에 있다는 것뿐입니다.
 */
const CHUNK_MS = 5_000;

export interface SessionRecorder {
  /** 지금 담고 있는지. 화면이 녹음 중임을 알릴 때 씁니다. */
  recording: boolean;
  /**
   * 담기를 멈추고 파일 하나로 묶어 돌려줍니다.
   *
   * 담긴 것이 없거나 이 브라우저가 녹음을 못 하면 `null` 입니다.
   */
  stop: () => Promise<File | null>;
}

function supportedMimeType(): string | null {
  if (typeof MediaRecorder === "undefined") return null;
  return MIME_CANDIDATES.find((type) => MediaRecorder.isTypeSupported(type)) ?? null;
}

/**
 * 세션 녹음.
 *
 * **자기 마이크 트랙만 담습니다** (`R-CON-14`). 상대 소리도, 화면도 담지 않습니다 —
 * 파일 하나가 곧 화자 한 명이라 나중에 누가 말했는지 가리는 데 손이 들지 않습니다.
 *
 * 마이크를 끈 동안에도 계속 담습니다. 트랙은 살아 있고 소리만 비어 있어, 멈췄다
 * 다시 시작하면 조각이 갈려 이어 붙일 수 없게 됩니다.
 */
export function useSessionRecorder(stream: MediaStream | null): SessionRecorder {
  const [recording, setRecording] = React.useState(false);
  const recorderRef = React.useRef<MediaRecorder | null>(null);
  const chunksRef = React.useRef<Blob[]>([]);

  React.useEffect(() => {
    const audio = stream?.getAudioTracks() ?? [];
    if (audio.length === 0) return;

    const mimeType = supportedMimeType();
    if (!mimeType) {
      // 담을 방법이 없는 브라우저입니다. 통화는 그대로 되고 요약만 못 만듭니다. (R-CON-16)
      console.warn("[recorder] 이 브라우저는 녹음을 지원하지 않습니다");
      return;
    }

    const recorder = new MediaRecorder(new MediaStream(audio), { mimeType });
    recorderRef.current = recorder;
    chunksRef.current = [];

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunksRef.current.push(event.data);
    };

    recorder.start(CHUNK_MS);
    setRecording(true);

    return () => {
      recorderRef.current = null;
      setRecording(false);
      if (recorder.state !== "inactive") recorder.stop();
    };
  }, [stream]);

  const stop = React.useCallback(async () => {
    const recorder = recorderRef.current;
    recorderRef.current = null;
    setRecording(false);

    if (recorder && recorder.state !== "inactive") {
      // 멈춤 신호를 보낸 뒤 마지막 조각이 올 때까지 기다립니다. 안 기다리면 끝이 잘립니다.
      await new Promise<void>((resolve) => {
        recorder.addEventListener("stop", () => resolve(), { once: true });
        recorder.stop();
      });
    }

    const chunks = chunksRef.current;
    chunksRef.current = [];
    if (chunks.length === 0) return null;

    const type = recorder?.mimeType ?? chunks[0].type;
    // 확장자는 형식을 따릅니다. 저장소가 이름으로 형식을 되짚는 경우가 있습니다.
    const extension = type.includes("mp4") ? "m4a" : "webm";
    return new File([new Blob(chunks, { type })], `session-recording.${extension}`, { type });
  }, []);

  return { recording, stop };
}
