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
  readyState: MediaStreamTrackState = "live";
  private readonly listeners = new Map<string, () => void>();

  constructor(readonly kind: "audio" | "video") {}

  stop() {
    this.stopped = true;
    this.readyState = "ended";
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

  /** 열 때 받은 설정. */
  readonly configurations: RTCConfiguration[] = [];

  constructor(configuration?: RTCConfiguration) {
    if (configuration) this.configurations.push(configuration);
    FakePeerConnection.instances.push(this);
  }

  addTrack(track: unknown) {
    return this.createSender(track);
  }

  /** 영상 자리는 트랙 없이 만든다. */
  addTransceiver() {
    this.video = this.createSender(null);
    return { sender: this.video };
  }

  private video: FakePeerConnection["senders"][number] | null = null;

  private createSender(track: unknown) {
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

  /** 영상 자리의 sender. 화면과 카메라가 갈아 끼우는 자리다. */
  videoSender() {
    return this.video!;
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

/** 중계를 보지 않는 시험의 기본값. 받아 오는 자리만 채우고 목록은 비운다. */
const noIceServers = () => Promise.resolve<RTCIceServer[]>([]);

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
      loadIceServers: noIceServers,
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
      loadIceServers: noIceServers,
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

  it("새로 얻은 트랙에도 꺼 둔 마이크 상태를 적용한다", async () => {
    let resolveStream!: (stream: MediaStream) => void;
    const permission = new Promise<MediaStream>((resolve) => {
      resolveStream = resolve;
    });
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockReturnValue(permission) },
    });
    const call = usePeerConnection({
      enabled: true,
      polite: false,
      send: () => true,
      loadIceServers: noIceServers,
    });

    call.toggleMic();
    const stream = new FakeStream();
    resolveStream(stream as unknown as MediaStream);
    await flush();

    expect(stream.audio.enabled).toBe(false);
  });

  it("현재 협상의 PEER_LEFT만 로컬 미디어와 peer를 종료한다", async () => {
    const stream = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    const call = usePeerConnection({
      enabled: true,
      polite: true,
      send: () => true,
      loadIceServers: noIceServers,
    });
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
    const call = usePeerConnection({
      enabled: true,
      polite: true,
      send: () => true,
      loadIceServers: noIceServers,
    });
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

  it("통화는 마이크만으로 열고 영상 자리는 비워 둔다", async () => {
    const getUserMedia = vi.fn().mockResolvedValue(new FakeStream());
    vi.stubGlobal("navigator", { mediaDevices: { getUserMedia } });
    usePeerConnection({ enabled: true, polite: true, send: () => true, loadIceServers: noIceServers });
    await flush();

    expect(getUserMedia).toHaveBeenCalledWith({ audio: expect.anything() });
    const peer = FakePeerConnection.instances[0];
    expect(peer.senders.map((sender) => (sender.track as FakeTrack | null)?.kind)).toEqual([
      "audio",
      undefined,
    ]);
  });

  it("입장하며 연 고르기를 기다리지 않고 통화를 열고, 고르는 대로 화면을 보낸다", async () => {
    let choose!: (screen: MediaStream) => void;
    const picking = new Promise<MediaStream | null>((resolve) => {
      choose = resolve;
    });
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(new FakeStream()) },
    });
    const sent: string[] = [];
    usePeerConnection({
      enabled: true,
      polite: true,
      send: (type) => {
        sent.push(type);
        return true;
      },
      loadIceServers: noIceServers,
      initialScreen: () => picking,
    });
    await flush();

    // 아직 고르는 중이어도 상대에게 준비됐다고 알린다. 연결과 녹음 시작이 밀리지 않는다.
    expect(sent).toContain("RTC_PEER_READY");
    const sender = FakePeerConnection.instances[0].videoSender();
    expect(sender.track).toBeNull();

    const screen = new FakeStream();
    choose(screen as unknown as MediaStream);
    await flush();

    expect(sender.track).toBe(screen.video);
  });

  it("화면 공유는 보내는 트랙만 갈아 끼우고 다시 협상하지 않는다", async () => {
    const screen = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValue(new FakeStream()),
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
      loadIceServers: noIceServers,
    });
    await flush();

    const sender = FakePeerConnection.instances[0].videoSender();
    const before = sent.length;

    await call.shareScreen();

    expect(sender.track).toBe(screen.video);
    // 갈아 끼우기만 하므로 SDP 를 주고받지 않는다.
    expect(sent.length).toBe(before);
  });

  it("공유 중에 다시 고르면 새 화면으로 바꾸고 옛 화면을 끊는다", async () => {
    const first = new FakeStream();
    const second = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValue(new FakeStream()),
        getDisplayMedia: vi.fn().mockResolvedValueOnce(first).mockResolvedValueOnce(second),
      },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true, loadIceServers: noIceServers });
    await flush();
    const sender = FakePeerConnection.instances[0].videoSender();

    await call.shareScreen();
    await call.shareScreen();

    expect(sender.track).toBe(second.video);
    expect(first.video.stopped).toBe(true);
    expect(second.video.stopped).toBe(false);
  });

  it("브라우저에서 공유를 멈추면 영상 자리를 비운다", async () => {
    const screen = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValue(new FakeStream()),
        getDisplayMedia: vi.fn().mockResolvedValue(screen),
      },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true, loadIceServers: noIceServers });
    await flush();
    const sender = FakePeerConnection.instances[0].videoSender();

    await call.shareScreen();
    screen.video.end();
    await flush();

    expect(sender.track).toBeNull();
    expect(screen.video.stopped).toBe(true);
  });

  it("카메라를 켜면 공유하던 화면 대신 카메라를 보내고, 끄면 비운다", async () => {
    const mic = new FakeStream();
    const camera = new FakeStream();
    const screen = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValueOnce(mic).mockResolvedValueOnce(camera),
        getDisplayMedia: vi.fn().mockResolvedValue(screen),
      },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true, loadIceServers: noIceServers });
    await flush();
    const sender = FakePeerConnection.instances[0].videoSender();

    await call.shareScreen();
    await call.toggleCamera();
    expect(sender.track).toBe(camera.video);
    expect(screen.video.stopped).toBe(true);

    await call.toggleCamera();
    expect(sender.track).toBeNull();
    expect(camera.video.stopped).toBe(true);
    // 녹음과 통화에 쓰는 마이크는 그대로다.
    expect(mic.audio.stopped).toBe(false);
  });

  it("카메라 권한을 묻는 사이 통화가 끝나면 카메라를 끈다", async () => {
    let allow!: (stream: MediaStream) => void;
    const asking = new Promise<MediaStream>((resolve) => {
      allow = resolve;
    });
    vi.stubGlobal("navigator", {
      mediaDevices: {
        getUserMedia: vi.fn().mockResolvedValueOnce(new FakeStream()).mockReturnValueOnce(asking),
      },
    });
    const call = usePeerConnection({ enabled: true, polite: true, send: () => true, loadIceServers: noIceServers });
    await flush();

    const turningOn = call.toggleCamera();
    call.hangUp();
    const camera = new FakeStream();
    allow(camera as unknown as MediaStream);
    await turningOn;

    expect(camera.video.stopped).toBe(true);
  });

  it("기다리는 동안 상대가 돌아오면 통화를 이어 간다", async () => {
    const stream = new FakeStream();
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    const call = usePeerConnection({
      enabled: true,
      polite: true,
      send: () => true,
      loadIceServers: noIceServers,
    });
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

  it("통화를 열 때 받아 온 ICE 서버를 그대로 넣는다", async () => {
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(new FakeStream()) },
    });
    const issued: RTCIceServer[] = [
      { urls: ["stun:turn.komit.site:3478"] },
      { urls: ["turn:turn.komit.site:3478?transport=udp"], username: "1:12", credential: "sig" },
    ];
    usePeerConnection({
      enabled: true,
      polite: true,
      send: () => true,
      loadIceServers: () => Promise.resolve(issued),
    });
    await flush();

    expect(FakePeerConnection.instances[0].configurations).toEqual([{ iceServers: issued }]);
  });

  it("받지 못해도 통화는 연다", async () => {
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(new FakeStream()) },
    });
    usePeerConnection({
      enabled: true,
      polite: true,
      send: () => true,
      loadIceServers: () => Promise.reject(new Error("서버가 답하지 않았다")),
    });
    await flush();

    expect(FakePeerConnection.instances).toHaveLength(1);
    expect(FakePeerConnection.instances[0].configurations).toEqual([{ iceServers: [] }]);
  });

  /*
    자격 수명이 한 통화보다 길어서(`R-CON-19`) 통화 도중에 다시 받을 일이 없다.

    **여기서 다시 받게 만들면 안 된다.** 길을 다시 뚫는 때는 망이 이미 망가진 때라,
    그 자리에 요청을 하나 더 걸면 회복이 가장 급할 때 서버 응답을 기다리게 된다.
  */
  it("길을 다시 뚫을 때 자격을 다시 받지 않는다", async () => {
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia: vi.fn().mockResolvedValue(new FakeStream()) },
    });
    const loadIceServers = vi.fn().mockResolvedValue([]);
    const call = usePeerConnection({
      enabled: true,
      polite: true,
      send: () => true,
      loadIceServers,
    });
    await flush();

    const peer = FakePeerConnection.instances[0];
    call.accept("RTC_ICE_RESTART", { negotiationId: "gen-2" });
    await flush();

    expect(peer.restarted).toBe(true);
    expect(loadIceServers).toHaveBeenCalledTimes(1);
  });
});
