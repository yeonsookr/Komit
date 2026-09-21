import { beforeEach, describe, expect, it, vi } from "vitest";

const reactHarness = vi.hoisted(() => {
  const states: unknown[] = [];
  const cleanups: Array<() => void> = [];
  return {
    states,
    cleanups,
    reset() {
      states.length = 0;
      cleanups.length = 0;
    },
    api: {
      useState<T>(initial: T) {
        const index = states.length;
        states.push(initial);
        return [initial, (next: T | ((previous: T) => T)) => {
          const previous = states[index] as T;
          states[index] = typeof next === "function"
            ? (next as (previous: T) => T)(previous)
            : next;
        }] as const;
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

import { usePeerConnection } from "./usePeerConnection";

class FakeTrack {
  enabled = true;
  stopped = false;
  private readonly listeners = new Map<string, () => void>();

  constructor(readonly kind: "audio" | "video") {}

  stop() {
    this.stopped = true;
  }

  addEventListener(type: string, listener: () => void) {
    this.listeners.set(type, listener);
  }

  /** 브라우저가 띄우는 「공유 중지」를 눌렀을 때 일어나는 일. */
  end() {
    this.listeners.get("ended")?.();
  }
}

class FakeStream {
  readonly audio = new FakeTrack("audio");
  readonly video = new FakeTrack("video");

  getTracks() {
    return [this.audio, this.video] as unknown as MediaStreamTrack[];
  }

  getAudioTracks() {
    return [this.audio] as unknown as MediaStreamTrack[];
  }

  getVideoTracks() {
    return [this.video] as unknown as MediaStreamTrack[];
  }
}

class FakePeerConnection {
  static instances: FakePeerConnection[] = [];

  signalingState: RTCSignalingState = "stable";
  connectionState: RTCPeerConnectionState = "new";
  localDescription: RTCSessionDescription | null = null;
  remoteDescription: RTCSessionDescription | null = null;
  ontrack: RTCPeerConnection["ontrack"] = null;
  onicecandidate: RTCPeerConnection["onicecandidate"] = null;
  onnegotiationneeded: RTCPeerConnection["onnegotiationneeded"] = null;
  onconnectionstatechange: RTCPeerConnection["onconnectionstatechange"] = null;
  closed = false;
  restarted = false;
  readonly events: string[] = [];

  readonly senders: Array<{ track: unknown; replaced: unknown[]; replaceTrack: (next: unknown) => Promise<void> }> = [];

  constructor() {
    FakePeerConnection.instances.push(this);
  }

  addTrack(track: unknown) {
    const sender = {
      track,
      replaced: [] as unknown[],
      replaceTrack: async (next: unknown) => {
        sender.replaced.push(next);
        sender.track = next;
      },
    };
    this.senders.push(sender);
    return sender;
  }

  /** 영상 자리의 sender. 화면 공유가 갈아 끼우는 자리다. */
  videoSender() {
    return this.senders.find((sender) => (sender.track as FakeTrack)?.kind === "video")!;
  }

  async setLocalDescription() {
    const type = this.signalingState === "have-remote-offer" ? "answer" : "offer";
    this.localDescription = {
      type,
      sdp: `${type}-sdp`,
      toJSON: () => ({ type, sdp: `${type}-sdp` }),
    } as RTCSessionDescription;
    this.signalingState = type === "offer" ? "have-local-offer" : "stable";
    this.events.push(`local:${type}`);
  }

  async setRemoteDescription(description: RTCSessionDescriptionInit) {
    this.remoteDescription = description as RTCSessionDescription;
    this.signalingState = description.type === "offer" ? "have-remote-offer" : "stable";
    this.events.push(`remote:${description.type}`);
  }

  async addIceCandidate(candidate: RTCIceCandidateInit) {
    this.events.push(`ice:${candidate.candidate}`);
  }

  restartIce() {
    this.restarted = true;
  }

  close() {
    this.closed = true;
    this.signalingState = "closed";
  }
}

const flush = async () => {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
  await new Promise((resolve) => setTimeout(resolve, 0));
};

describe("usePeerConnection", () => {
  beforeEach(() => {
    reactHarness.reset();
    FakePeerConnection.instances.length = 0;
    vi.stubGlobal("RTCPeerConnection", FakePeerConnection);
  });

  it("상대 READY 전에는 offer를 보내지 않고 READY 뒤 같은 협상 ID로 제안한다", async () => {
    const stream = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    const sent: Array<{ type: string; payload: Record<string, unknown> }> = [];
    const call = usePeerConnection({
      enabled: true,
      polite: false,
      send: (type, payload) => {
        sent.push({ type, payload: payload as Record<string, unknown> });
        return true;
      },
    });
    await flush();

    const peer = FakePeerConnection.instances[0];
    const onNegotiationNeeded = peer.onnegotiationneeded as ((event: Event) => void) | null;
    onNegotiationNeeded?.(new Event("negotiationneeded"));
    await flush();
    expect(sent.map(({ type }) => type)).toEqual(["RTC_PEER_READY"]);

    const initialId = sent[0].payload.negotiationId;
    call.accept("RTC_PEER_READY", { negotiationId: "remote-generation" });
    await flush();

    expect(sent.at(-1)).toEqual({
      type: "RTC_OFFER",
      payload: {
        negotiationId: initialId,
        description: { type: "offer", sdp: "offer-sdp" },
      },
    });

    call.accept("RTC_ANSWER", {
      negotiationId: initialId,
      description: { type: "answer", sdp: "answer-sdp" },
    });
    await flush();
    call.accept("RTC_PEER_READY", { negotiationId: "reconnected-peer" });
    await flush();

    const reconnectedOffer = sent.at(-1);
    expect(reconnectedOffer?.type).toBe("RTC_OFFER");
    expect(reconnectedOffer?.payload.negotiationId).not.toBe(initialId);
  });

  it("협상 ID별 ICE를 SDP 뒤에 직렬 처리하고 이전 세대 answer를 버린다", async () => {
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(new FakeStream()) },
    });
    const sent: Array<{ type: string; payload: Record<string, unknown> }> = [];
    const call = usePeerConnection({
      enabled: true,
      polite: true,
      send: (type, payload) => {
        sent.push({ type, payload: payload as Record<string, unknown> });
        return true;
      },
    });
    await flush();

    call.accept("RTC_ICE_CANDIDATE", {
      negotiationId: "next",
      candidate: { candidate: "candidate-next" },
    });
    call.accept("RTC_OFFER", {
      negotiationId: "next",
      description: { type: "offer", sdp: "next-offer" },
    });
    await flush();

    const peer = FakePeerConnection.instances[0];
    expect(peer.events).toEqual([
      "remote:offer",
      "ice:candidate-next",
      "local:answer",
    ]);
    expect(sent.at(-1)).toMatchObject({
      type: "RTC_ANSWER",
      payload: { negotiationId: "next" },
    });

    call.accept("RTC_ANSWER", {
      negotiationId: "old",
      description: { type: "answer", sdp: "stale-answer" },
    });
    await flush();
    expect(peer.events).not.toContain("remote:answer");
  });

  it("새로 얻은 트랙에도 보존한 마이크와 카메라 상태를 적용한다", async () => {
    let resolveStream!: (stream: MediaStream) => void;
    const permission = new Promise<MediaStream>((resolve) => {
      resolveStream = resolve;
    });
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockReturnValue(permission) },
    });
    const call = usePeerConnection({ enabled: true, polite: false, send: () => true });

    call.toggleMic();
    call.toggleCamera();
    const stream = new FakeStream();
    resolveStream(stream as unknown as MediaStream);
    await flush();

    expect(stream.audio.enabled).toBe(false);
    expect(stream.video.enabled).toBe(false);
  });

  it("현재 협상의 PEER_LEFT만 로컬 미디어와 peer를 종료한다", async () => {
    const stream = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true });
    await flush();
    const peer = FakePeerConnection.instances[0];

    call.accept("RTC_OFFER", {
      negotiationId: "current",
      description: { type: "offer", sdp: "offer" },
    });
    await flush();
    call.accept("RTC_PEER_LEFT", { negotiationId: "stale" });
    await flush();
    expect(peer.closed).toBe(false);

    call.accept("RTC_PEER_LEFT", { negotiationId: "current" });
    await flush();
    expect(peer.closed).toBe(true);
    expect(stream.audio.stopped).toBe(true);
    expect(stream.video.stopped).toBe(true);
    expect(reactHarness.states[0]).toBe("ENDED");
  });

  it("상대 연결이 끊기면 기다렸다가 돌아오지 않을 때만 접는다", async () => {
    const stream = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true });
    await flush();
    const peer = FakePeerConnection.instances[0];

    vi.useFakeTimers();
    try {
      call.peerAway();
      expect(reactHarness.states[0]).toBe("PEER_AWAY");
      expect(peer.closed).toBe(false);

      vi.advanceTimersByTime(15_000);
      expect(peer.closed).toBe(true);
      expect(stream.audio.stopped).toBe(true);
      expect(reactHarness.states[0]).toBe("ENDED");
    } finally {
      vi.useRealTimers();
    }
  });

  it("화면 공유는 보내는 트랙만 갈아 끼우고 다시 협상하지 않는다", async () => {
    const stream = new FakeStream();
    const screen = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValue(stream),
        getDisplayMedia: vi.fn().mockResolvedValue(screen),
      },
    });
    const sent: string[] = [];
    const call = usePeerConnection({
      enabled: true,
      polite: true,
      send: (type) => {
        sent.push(type);
        return true;
      },
    });
    await flush();

    const sender = FakePeerConnection.instances[0].videoSender();
    const before = sent.length;

    await call.toggleScreenShare();

    expect(sender.track).toBe(screen.video);
    // 갈아 끼우기만 하므로 SDP 를 주고받지 않는다.
    expect(sent.length).toBe(before);
  });

  it("브라우저에서 공유를 멈추면 카메라로 돌아오고 꺼둔 상태를 지킨다", async () => {
    const stream = new FakeStream();
    const screen = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValue(stream),
        getDisplayMedia: vi.fn().mockResolvedValue(screen),
      },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true });
    await flush();

    const sender = FakePeerConnection.instances[0].videoSender();
    await call.toggleScreenShare();
    call.toggleCamera();

    screen.video.end();
    await flush();

    expect(sender.track).toBe(stream.video);
    expect(screen.video.stopped).toBe(true);
    expect(stream.video.enabled).toBe(false);
  });

  it("기다리는 동안 상대가 돌아오면 통화를 이어 간다", async () => {
    const stream = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true });
    await flush();
    const peer = FakePeerConnection.instances[0];

    vi.useFakeTimers();
    try {
      call.peerAway();
      call.peerPresent();
      vi.advanceTimersByTime(30_000);

      expect(peer.closed).toBe(false);
      expect(stream.audio.stopped).toBe(false);
      expect(reactHarness.states[0]).toBe("CONNECTING");
    } finally {
      vi.useRealTimers();
    }
  });
});
