"use client";

import * as React from "react";
import type { RtcSignalType } from "@/types/realtime";

/**
 * 길을 찾아 주는 공개 서버.
 *
 * 공유기 뒤에 있는 내 주소가 바깥에서 어떻게 보이는지 물어보는 곳입니다.
 * 회사망처럼 막힌 곳에서는 이것만으로 길이 안 나고 **중계해 줄 서버**가 따로
 * 필요한데, 그건 아직 없습니다. (계획서 4단계)
 */
const ICE_SERVERS: RTCIceServer[] = [{ urls: "stun:stun.l.google.com:19302" }];

/** 카메라·마이크를 어떤 품질로 잡을지. 통화라 화질보다 끊기지 않는 쪽이 낫습니다. */
const MEDIA: MediaStreamConstraints = {
  audio: { echoCancellation: true, noiseSuppression: true },
  video: { width: { ideal: 1280 }, height: { ideal: 720 } },
};

/** 통화가 어디까지 왔는지. */
export type CallStatus =
  | "IDLE"
  | "NEED_PERMISSION"
  | "PERMISSION_DENIED"
  | "WAITING_PEER"
  | "CONNECTING"
  | "CONNECTED"
  | "ENDED";

export interface UsePeerConnectionOptions {
  /** 거짓이면 카메라도 켜지 않습니다. 양쪽이 동의하기 전에는 켜면 안 됩니다. (R-CON-17) */
  enabled: boolean;
  /**
   * 둘 중 누가 먼저 제안할지. 양쪽이 같은 값을 쓰면 안 됩니다.
   *
   * 동시에 제안하면 한쪽이 물러서야 하는데, 물러설 쪽을 미리 정해 두지 않으면
   * 둘 다 물러서거나 둘 다 버팁니다.
   */
  polite: boolean;
  /** 쪽지를 상대에게 보냅니다. 연결이 안 열렸으면 거짓을 돌려줍니다. */
  send: (type: RtcSignalType, payload?: unknown) => boolean;
}

export interface PeerConnection {
  status: CallStatus;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  micOn: boolean;
  cameraOn: boolean;
  toggleMic: () => void;
  toggleCamera: () => void;
  /** 받은 쪽지를 넣습니다. 연결 훅의 `onMessage` 에서 부릅니다. */
  accept: (type: RtcSignalType, payload: unknown) => void;
  /** 통화를 접습니다. 상대에게도 알립니다. */
  hangUp: () => void;
}

/**
 * 두 사람 사이의 통화를 맡습니다.
 *
 * 쪽지를 어떻게 나르는지는 모릅니다. `send` 로 내보내고 `accept` 로 받을 뿐이라
 * 나르는 길이 바뀌어도 이 훅은 그대로입니다.
 */
export function usePeerConnection({
  enabled,
  polite,
  send,
}: UsePeerConnectionOptions): PeerConnection {
  const [status, setStatus] = React.useState<CallStatus>("IDLE");
  const [localStream, setLocalStream] = React.useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = React.useState<MediaStream | null>(null);
  const [micOn, setMicOn] = React.useState(true);
  const [cameraOn, setCameraOn] = React.useState(true);

  const peerRef = React.useRef<RTCPeerConnection | null>(null);
  const localRef = React.useRef<MediaStream | null>(null);

  /** 제안을 만드는 중인지. 이때 들어온 상대 제안은 물러설지 버틸지 따져야 합니다. */
  const makingOffer = React.useRef(false);
  /** 물러서는 중인지. 이 동안 들어오는 답은 버립니다. */
  const ignoringOffer = React.useRef(false);
  /**
   * 상대를 알기 전에 먼저 도착한 길 후보.
   *
   * 후보는 제안보다 먼저 올 수 있습니다. 그때 그냥 넣으면 예외가 나므로 쌓아 두었다가
   * 제안을 받은 뒤 한꺼번에 넣습니다.
   */
  const pendingCandidates = React.useRef<RTCIceCandidateInit[]>([]);

  const sendRef = React.useRef(send);
  React.useEffect(() => {
    sendRef.current = send;
  }, [send]);

  const politeRef = React.useRef(polite);
  React.useEffect(() => {
    politeRef.current = polite;
  }, [polite]);

  /* ---------------------------------------------------------------- 시작 */

  React.useEffect(() => {
    if (!enabled) return;

    let alive = true;
    setStatus("NEED_PERMISSION");

    const start = async () => {
      let stream: MediaStream;
      try {
        stream = await navigator.mediaDevices.getUserMedia(MEDIA);
      } catch {
        // 거부는 오류가 아니라 사람의 선택입니다. 화면은 그 상태를 그대로 보여 줍니다.
        if (alive) setStatus("PERMISSION_DENIED");
        return;
      }

      if (!alive) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }

      localRef.current = stream;
      setLocalStream(stream);

      const peer = new RTCPeerConnection({ iceServers: ICE_SERVERS });
      peerRef.current = peer;

      stream.getTracks().forEach((track) => peer.addTrack(track, stream));

      peer.ontrack = (event) => {
        if (alive) setRemoteStream(event.streams[0] ?? null);
      };

      peer.onicecandidate = (event) => {
        if (event.candidate) {
          sendRef.current("RTC_ICE_CANDIDATE", event.candidate.toJSON());
        }
      };

      /*
        내 쪽 사정이 바뀌면(트랙을 넣거나 길을 다시 찾으면) 브라우저가 알려 줍니다.
        그때 제안을 만들어 보냅니다. 언제 만들지 직접 고르지 않는 편이 안전합니다.
      */
      peer.onnegotiationneeded = async () => {
        try {
          makingOffer.current = true;
          await peer.setLocalDescription();
          sendRef.current("RTC_OFFER", peer.localDescription?.toJSON());
        } finally {
          makingOffer.current = false;
        }
      };

      peer.onconnectionstatechange = () => {
        if (!alive) return;
        if (peer.connectionState === "connected") setStatus("CONNECTED");
        else if (peer.connectionState === "failed") {
          // 길이 끊겼습니다. 처음부터 다시 찾습니다.
          peer.restartIce();
          sendRef.current("RTC_ICE_RESTART");
          setStatus("CONNECTING");
        } else if (peer.connectionState === "disconnected") setStatus("CONNECTING");
      };

      setStatus("WAITING_PEER");
      sendRef.current("RTC_PEER_READY");
    };

    void start();

    return () => {
      alive = false;
      teardown();
      setStatus("ENDED");
    };
    // teardown 은 ref 만 만져 매 렌더 같은 일을 하므로 의존성에 넣지 않습니다.
  }, [enabled]);

  /* ---------------------------------------------------------- 받는 쪽지 */

  const accept = React.useCallback((type: RtcSignalType, payload: unknown) => {
    const peer = peerRef.current;
    if (!peer) return;

    void (async () => {
      switch (type) {
        case "RTC_PEER_READY":
          /*
            상대가 들어왔습니다. 내가 먼저 들어와 보낸 제안은 상대가 듣기 전이라
            사라졌을 수 있으니 다시 만들어 보냅니다.

            되받아 READY 를 보내면 둘이 영원히 주고받습니다. **먼저 거는 쪽만**
            움직이고 물러서는 쪽은 기다립니다 — 누가 먼저 들어왔든 한 번만 옵니다.
          */
          if (!politeRef.current && peer.signalingState === "stable") {
            await peer.setLocalDescription();
            sendRef.current("RTC_OFFER", peer.localDescription?.toJSON());
          }
          break;

        case "RTC_OFFER":
        case "RTC_ANSWER": {
          const description = payload as RTCSessionDescriptionInit;

          /*
            둘이 동시에 제안했을 때입니다. 미리 정해 둔 「물러설 쪽」만 자기 제안을
            버리고 상대 것을 받습니다. 양쪽이 같은 판단을 하니 엇갈리지 않습니다.
          */
          const collision =
            description.type === "offer" &&
            (makingOffer.current || peer.signalingState !== "stable");

          ignoringOffer.current = !politeRef.current && collision;
          if (ignoringOffer.current) return;

          await peer.setRemoteDescription(description);

          // 상대를 알게 됐으니 쌓아 둔 길 후보를 이제 넣습니다.
          for (const candidate of pendingCandidates.current) {
            await peer.addIceCandidate(candidate).catch(() => undefined);
          }
          pendingCandidates.current = [];

          if (description.type === "offer") {
            await peer.setLocalDescription();
            sendRef.current("RTC_ANSWER", peer.localDescription?.toJSON());
          }
          break;
        }

        case "RTC_ICE_CANDIDATE": {
          const candidate = payload as RTCIceCandidateInit;
          if (!peer.remoteDescription) {
            pendingCandidates.current.push(candidate);
            return;
          }
          // 물러서는 중에 들어온 후보는 버려도 됩니다. 곧 새 제안이 옵니다.
          await peer.addIceCandidate(candidate).catch(() => {
            if (!ignoringOffer.current) throw new Error("길 후보를 넣지 못했습니다");
          });
          break;
        }

        case "RTC_ICE_RESTART":
          peer.restartIce();
          break;

        case "RTC_PEER_LEFT":
          setRemoteStream(null);
          setStatus("WAITING_PEER");
          break;
      }
    })();
  }, []);

  /* ------------------------------------------------------------ 장치·종료 */

  const toggleMic = React.useCallback(() => {
    const tracks = localRef.current?.getAudioTracks() ?? [];
    const next = !tracks.every((track) => track.enabled);
    tracks.forEach((track) => (track.enabled = next));
    setMicOn(next);
  }, []);

  const toggleCamera = React.useCallback(() => {
    const tracks = localRef.current?.getVideoTracks() ?? [];
    const next = !tracks.every((track) => track.enabled);
    tracks.forEach((track) => (track.enabled = next));
    setCameraOn(next);
  }, []);

  const teardown = React.useCallback(() => {
    localRef.current?.getTracks().forEach((track) => track.stop());
    localRef.current = null;
    peerRef.current?.close();
    peerRef.current = null;
    pendingCandidates.current = [];
    setLocalStream(null);
    setRemoteStream(null);
  }, []);

  const hangUp = React.useCallback(() => {
    sendRef.current("RTC_PEER_LEFT");
    teardown();
    setStatus("ENDED");
  }, [teardown]);

  return {
    status,
    localStream,
    remoteStream,
    micOn,
    cameraOn,
    toggleMic,
    toggleCamera,
    accept,
    hangUp,
  };
}
