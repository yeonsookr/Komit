import { beforeEach, describe, expect, it, vi } from "vitest";

const reactHarness = vi.hoisted(() => {
  const cleanups: Array<() => void> = [];
  return {
    cleanups,
    reset() {
      cleanups.length = 0;
    },
    api: {
      useState<T>(initial: T) {
        return [initial, () => {}] as const;
      },
      useRef<T>(initial: T) {
        return { current: initial };
      },
      useEffect(effect: () => void | (() => void)) {
        const cleanup = effect();
        if (cleanup) cleanups.push(cleanup);
      },
      useCallback<T extends (...args: never[]) => unknown>(callback: T) {
        return callback;
      },
    },
  };
});

vi.mock("react", () => reactHarness.api);

import { useSessionRecorder } from "./useSessionRecorder";

class FakeRecorder {
  static instances: FakeRecorder[] = [];
  static supported = true;

  static isTypeSupported(type: string) {
    return FakeRecorder.supported && type === "audio/webm;codecs=opus";
  }

  state: "inactive" | "recording" = "inactive";
  ondataavailable: ((event: { data: Blob }) => void) | null = null;
  timeslice: number | undefined;
  private readonly listeners = new Map<string, () => void>();

  constructor(
    readonly stream: { tracks: unknown[] },
    readonly options: { mimeType: string },
  ) {
    FakeRecorder.instances.push(this);
  }

  get mimeType() {
    return this.options.mimeType;
  }

  start(timeslice?: number) {
    this.timeslice = timeslice;
    this.state = "recording";
  }

  addEventListener(type: string, listener: () => void) {
    this.listeners.set(type, listener);
  }

  stop() {
    this.state = "inactive";
    this.listeners.get("stop")?.();
  }

  /** 브라우저가 조각을 건네주는 순간. */
  emit(text: string) {
    this.ondataavailable?.({ data: new Blob([text], { type: "audio/webm" }) });
  }
}

function streamWith(audio: number, video: number) {
  const make = (kind: string, count: number) => Array.from({ length: count }, () => ({ kind }));
  const audioTracks = make("audio", audio);
  return {
    getAudioTracks: () => audioTracks,
    getVideoTracks: () => make("video", video),
  } as unknown as MediaStream;
}

beforeEach(() => {
  reactHarness.reset();
  FakeRecorder.instances.length = 0;
  FakeRecorder.supported = true;
  vi.stubGlobal("MediaRecorder", FakeRecorder);
  vi.stubGlobal("MediaStream", class {
    constructor(readonly tracks: unknown[]) {}
  });
});

describe("useSessionRecorder", () => {
  it("영상은 빼고 자기 마이크 트랙만 담는다", () => {
    useSessionRecorder(streamWith(1, 1), true);

    const recorder = FakeRecorder.instances[0];
    expect(recorder.stream.tracks).toHaveLength(1);
    expect(recorder.options.mimeType).toBe("audio/webm;codecs=opus");
    expect(recorder.state).toBe("recording");
    // 한 덩어리로 받으면 멈출 때까지 손에 아무것도 없습니다.
    expect(recorder.timeslice).toBeGreaterThan(0);
  });

  it("멈추면 조각을 모아 파일 하나로 묶는다", async () => {
    const recorder = useSessionRecorder(streamWith(1, 1), true);
    const fake = FakeRecorder.instances[0];
    fake.emit("first");
    fake.emit("second");

    const file = await recorder.stop();

    expect(file).not.toBeNull();
    expect(file?.type).toBe("audio/webm;codecs=opus");
    expect(file?.name).toBe("session-recording.webm");
    expect(await file?.text()).toBe("firstsecond");
  });

  it("담긴 것이 없으면 올릴 파일도 없다", async () => {
    const recorder = useSessionRecorder(streamWith(1, 0), true);

    await expect(recorder.stop()).resolves.toBeNull();
  });

  it("녹음을 못 하는 브라우저에서는 통화만 하고 넘어간다", async () => {
    FakeRecorder.supported = false;
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});

    const recorder = useSessionRecorder(streamWith(1, 1), true);

    expect(FakeRecorder.instances).toHaveLength(0);
    await expect(recorder.stop()).resolves.toBeNull();
    warn.mockRestore();
  });

  it("마이크가 없으면 아무것도 시작하지 않는다", () => {
    useSessionRecorder(streamWith(0, 1), true);

    expect(FakeRecorder.instances).toHaveLength(0);
  });

  /** 기다리는 동안 켜 둔 미리보기까지 담으면 상대가 동의하기 전의 말이 들어갑니다. */
  it("세션이 시작되기 전에는 카메라가 켜져 있어도 담지 않는다", () => {
    useSessionRecorder(streamWith(1, 1), false);

    expect(FakeRecorder.instances).toHaveLength(0);
  });

  /**
   * 정하지 않으면 브라우저 기본값이 붙어 90분에 83MiB 가 된다. 전사는 그 위를 쓰지 않는다.
   * (R-CON-18)
   */
  it("비트레이트를 고정해 파일 크기가 시간에만 비례하게 한다", () => {
    useSessionRecorder(streamWith(1, 1), true);

    expect(FakeRecorder.instances[0].options.audioBitsPerSecond).toBe(24_000);
  });

  /** 예약 시간이 녹음 상한은 아니라 통화가 길어질 수 있다. (R-CON-18) */
  it("90분이 되면 스스로 멈추고 담아 둔 조각은 지킨다", async () => {
    vi.useFakeTimers();
    try {
      const recorder = useSessionRecorder(streamWith(1, 1), true);
      const fake = FakeRecorder.instances[0];
      fake.emit("담은 것");

      vi.advanceTimersByTime(90 * 60 * 1_000);
      expect(fake.state).toBe("inactive");

      const file = await recorder.stop();
      expect(await file?.text()).toBe("담은 것");
    } finally {
      vi.useRealTimers();
    }
  });
});
