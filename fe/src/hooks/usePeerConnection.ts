"use client";

import * as React from "react";
import type {
  RtcDescriptionPayload,
  RtcIceCandidatePayload,
  RtcNegotiationId,
  RtcPeerPayload,
  RtcSignalType,
} from "@/types/realtime";

const ICE_SERVERS: RTCIceServer[] = [{ urls: "stun:stun.l.google.com:19302" }];

const MEDIA: MediaStreamConstraints = {
  audio: { echoCancellation: true, noiseSuppression: true },
  video: { width: { ideal: 1280 }, height: { ideal: 720 } },
};

/**
 * 상대 연결이 끊겼을 때 기다려 주는 시간.
 *
 * 회선이 잠깐 튄 것과 정말 나간 것을 서버는 구분하지 못합니다. 다시 붙는 데 걸리는 시간
 * (`useRealtimeConnection` 의 재시도는 최대 10초)보다 넉넉히 두고, 그때까지 안 오면 접습니다.
 */
const PEER_AWAY_GRACE_MS = 15_000;

export type CallStatus =
  | "IDLE"
  | "NEED_PERMISSION"
  | "PERMISSION_DENIED"
  | "WAITING_PEER"
  | "CONNECTING"
  | "CONNECTED"
  | "PEER_AWAY"
  | "ENDED";

export interface UsePeerConnectionOptions {
  /** 거짓이면 카메라도 켜지 않습니다. 양쪽이 동의하기 전에는 켜면 안 됩니다. (R-CON-17) */
  enabled: boolean;
  /** 동시에 제안했을 때 자기 제안을 버리고 상대 제안을 받을 쪽인지. */
  polite: boolean;
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
  accept: (type: RtcSignalType, payload: unknown) => void;
  /** 상대 연결이 끊겼습니다. 돌아올 시간을 주고 그때까지 안 오면 접습니다. */
  peerAway: () => void;
  /** 상대가 (다시) 붙었습니다. */
  peerPresent: () => void;
  hangUp: () => void;
}

/** UUID가 없는 테스트 브라우저에서도 협상 세대가 겹치지 않게 만듭니다. */
export function createRtcNegotiationId(): RtcNegotiationId {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

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
  const micOnRef = React.useRef(true);
  const cameraOnRef = React.useRef(true);
  const activeNegotiationId = React.useRef<RtcNegotiationId | null>(null);
  const retiredNegotiationIds = React.useRef(new Set<RtcNegotiationId>());
  const acknowledgedReadyIds = React.useRef(new Set<RtcNegotiationId>());
  const pendingCandidates = React.useRef(new Map<RtcNegotiationId, RTCIceCandidateInit[]>());
  const makingOffer = React.useRef(false);
  const ignoreOffer = React.useRef(false);
  const remoteReady = React.useRef(false);
  const waitingForInitialReady = React.useRef(true);
  const reuseNegotiationIdForNextOffer = React.useRef(false);
  const signalingQueue = React.useRef(Promise.resolve());
  const awayTimer = React.useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const peerIsAway = React.useRef(false);

  const sendRef = React.useRef(send);
  React.useEffect(() => {
    sendRef.current = send;
  }, [send]);

  const politeRef = React.useRef(polite);
  React.useEffect(() => {
    politeRef.current = polite;
  }, [polite]);

  const replaceNegotiation = React.useCallback((nextId: RtcNegotiationId) => {
    const previousId = activeNegotiationId.current;
    if (previousId === nextId) return;
    if (previousId) {
      retiredNegotiationIds.current.add(previousId);
      pendingCandidates.current.delete(previousId);
    }
    retiredNegotiationIds.current.delete(nextId);
    activeNegotiationId.current = nextId;
    ignoreOffer.current = false;
  }, []);

  const enqueueSignaling = React.useCallback((operation: () => Promise<void>) => {
    const run = signalingQueue.current.then(operation, operation);
    signalingQueue.current = run.catch(() => undefined);
    return run;
  }, []);

  const sendDescription = React.useCallback(
    (type: "RTC_OFFER" | "RTC_ANSWER", peer: RTCPeerConnection, id: RtcNegotiationId) => {
      const description = peer.localDescription?.toJSON();
      if (description) sendRef.current(type, { negotiationId: id, description });
    },
    [],
  );

  /** 같은 세대의 READY 를 서로 되받으면 메아리가 됩니다. 한 세대에 한 번만 답합니다. */
  const announceReady = React.useCallback((id: RtcNegotiationId) => {
    if (acknowledgedReadyIds.current.has(id)) return;
    acknowledgedReadyIds.current.add(id);
    sendRef.current("RTC_PEER_READY", { negotiationId: id });
  }, []);

  const negotiate = React.useCallback(
    async (peer: RTCPeerConnection, rotateId: boolean) => {
      if (!remoteReady.current || peer.signalingState === "closed") return;

      try {
        makingOffer.current = true;
        if (rotateId || !activeNegotiationId.current) {
          replaceNegotiation(createRtcNegotiationId());
        }
        const id = activeNegotiationId.current;
        if (!id) return;
        await peer.setLocalDescription();
        if (peer.localDescription?.type === "offer") sendDescription("RTC_OFFER", peer, id);
      } finally {
        makingOffer.current = false;
      }
    },
    [replaceNegotiation, sendDescription],
  );

  const requestNegotiation = React.useCallback(
    (peer: RTCPeerConnection) => {
      if (!remoteReady.current) return;
      // 길만 다시 뚫는 재협상이면 세대를 그대로 씁니다. 상대가 세대를 이미 알고 있습니다.
      const reuseId = reuseNegotiationIdForNextOffer.current;
      reuseNegotiationIdForNextOffer.current = false;
      enqueueSignaling(() => negotiate(peer, !reuseId)).catch(() => setStatus("CONNECTING"));
    },
    [enqueueSignaling, negotiate],
  );

  const stopWaitingForPeer = React.useCallback(() => {
    if (awayTimer.current !== undefined) {
      clearTimeout(awayTimer.current);
      awayTimer.current = undefined;
    }
    peerIsAway.current = false;
  }, []);

  const teardown = React.useCallback(() => {
    stopWaitingForPeer();
    localRef.current?.getTracks().forEach((track) => track.stop());
    localRef.current = null;
    peerRef.current?.close();
    peerRef.current = null;
    activeNegotiationId.current = null;
    pendingCandidates.current.clear();
    retiredNegotiationIds.current.clear();
    acknowledgedReadyIds.current.clear();
    remoteReady.current = false;
    waitingForInitialReady.current = true;
    reuseNegotiationIdForNextOffer.current = false;
    signalingQueue.current = Promise.resolve();
    setLocalStream(null);
    setRemoteStream(null);
  }, [stopWaitingForPeer]);

  /*
    상대 연결이 끊겼다는 서버 알림. **나갔다는 뜻은 아닙니다** — 회선이 잠깐 튀어도 옵니다.
    다만 쪽지 길이 끊긴 동안에는 다시 협상할 방법이 없으므로, 기다려도 안 오면 접습니다.
  */
  const peerAway = React.useCallback(() => {
    if (!peerRef.current || awayTimer.current !== undefined) return;

    peerIsAway.current = true;
    setStatus("PEER_AWAY");
    awayTimer.current = setTimeout(() => {
      awayTimer.current = undefined;
      peerIsAway.current = false;
      teardown();
      setStatus("ENDED");
    }, PEER_AWAY_GRACE_MS);
  }, [teardown]);

  const peerPresent = React.useCallback(() => {
    const wasAway = peerIsAway.current;
    stopWaitingForPeer();
    if (!wasAway) return;
    // 기다리는 동안 영상이 계속 흘렀을 수도, 끊겼을 수도 있습니다. 지금 상태를 그대로 적습니다.
    setStatus(peerRef.current?.connectionState === "connected" ? "CONNECTED" : "CONNECTING");
  }, [stopWaitingForPeer]);

  React.useEffect(() => {
    if (!enabled) return;

    let alive = true;
    setStatus("NEED_PERMISSION");

    const start = async () => {
      let stream: MediaStream;
      try {
        stream = await navigator.mediaDevices.getUserMedia(MEDIA);
      } catch {
        if (alive) setStatus("PERMISSION_DENIED");
        return;
      }

      if (!alive) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }

      stream.getAudioTracks().forEach((track) => (track.enabled = micOnRef.current));
      stream.getVideoTracks().forEach((track) => (track.enabled = cameraOnRef.current));
      localRef.current = stream;
      setLocalStream(stream);

      const peer = new RTCPeerConnection({ iceServers: ICE_SERVERS });
      peerRef.current = peer;
      const initialId = createRtcNegotiationId();
      replaceNegotiation(initialId);

      stream.getTracks().forEach((track) => peer.addTrack(track, stream));

      peer.ontrack = (event) => {
        if (alive) setRemoteStream(event.streams[0] ?? null);
      };

      peer.onicecandidate = (event) => {
        const id = activeNegotiationId.current;
        if (event.candidate && id) {
          sendRef.current("RTC_ICE_CANDIDATE", {
            negotiationId: id,
            candidate: event.candidate.toJSON(),
          });
        }
      };

      peer.onnegotiationneeded = () => requestNegotiation(peer);

      peer.onconnectionstatechange = () => {
        if (!alive) return;
        if (peer.connectionState === "connected") {
          setStatus("CONNECTED");
        } else if (peer.connectionState === "failed") {
          const id = createRtcNegotiationId();
          replaceNegotiation(id);
          reuseNegotiationIdForNextOffer.current = true;
          peer.restartIce();
          sendRef.current("RTC_ICE_RESTART", { negotiationId: id });
          setStatus("CONNECTING");
        } else if (peer.connectionState === "disconnected") {
          setStatus("CONNECTING");
        }
      };

      setStatus("WAITING_PEER");
      sendRef.current("RTC_PEER_READY", { negotiationId: initialId });
    };

    void start();

    return () => {
      alive = false;
      teardown();
      setStatus("ENDED");
    };
  }, [enabled, replaceNegotiation, requestNegotiation, teardown]);

  const acceptPeerReady = React.useCallback(
    async (peer: RTCPeerConnection, id: RtcNegotiationId) => {
      if (retiredNegotiationIds.current.has(id)) return;
      remoteReady.current = true;

      // 물러설 쪽은 되받기만 합니다. 제안은 버티는 쪽이 한 번만 냅니다.
      if (politeRef.current) {
        replaceNegotiation(id);
        announceReady(id);
        return;
      }

      const firstReady = waitingForInitialReady.current;
      const remoteReconnected = !firstReady && id !== activeNegotiationId.current;
      if (!firstReady && !remoteReconnected) return;

      const ownId = remoteReconnected
        ? createRtcNegotiationId()
        : (activeNegotiationId.current ?? createRtcNegotiationId());
      replaceNegotiation(ownId);
      if (id !== ownId) announceReady(ownId);
      if (peer.signalingState === "stable") {
        waitingForInitialReady.current = false;
        await negotiate(peer, false);
      }
    },
    [announceReady, negotiate, replaceNegotiation],
  );

  const applyRemoteDescription = React.useCallback(
    async (
      peer: RTCPeerConnection,
      description: RTCSessionDescriptionInit,
      id: RtcNegotiationId,
    ) => {
      await peer.setRemoteDescription(description);
      const queued = pendingCandidates.current.get(id) ?? [];
      pendingCandidates.current.delete(id);
      for (const candidate of queued) await peer.addIceCandidate(candidate);
    },
    [],
  );

  const acceptDescription = React.useCallback(
    async (type: "RTC_OFFER" | "RTC_ANSWER", peer: RTCPeerConnection, payload: unknown) => {
      const message = asDescriptionPayload(payload);
      const expectedType = type === "RTC_OFFER" ? "offer" : "answer";
      if (message?.description.type !== expectedType) return;

      const id = message.negotiationId;
      // 답은 내가 낸 제안에만 붙습니다. 세대가 다르면 지난 통화의 답입니다.
      if (type === "RTC_ANSWER" && id !== activeNegotiationId.current) return;
      if (retiredNegotiationIds.current.has(id)) return;

      const collision =
        expectedType === "offer" &&
        (makingOffer.current || peer.signalingState !== "stable");
      ignoreOffer.current = !politeRef.current && collision;
      if (ignoreOffer.current) {
        pendingCandidates.current.delete(id);
        retiredNegotiationIds.current.add(id);
        return;
      }

      if (expectedType === "offer") replaceNegotiation(id);
      await applyRemoteDescription(peer, message.description, id);

      if (expectedType === "offer") {
        await peer.setLocalDescription();
        sendDescription("RTC_ANSWER", peer, id);
      }
    },
    [applyRemoteDescription, replaceNegotiation, sendDescription],
  );

  const acceptIceCandidate = React.useCallback(
    async (peer: RTCPeerConnection, payload: unknown) => {
      const message = asIceCandidatePayload(payload);
      if (!message) return;

      const id = message.negotiationId;
      if (retiredNegotiationIds.current.has(id)) return;

      // 설명보다 먼저 온 후보는 아직 넣을 수 없습니다. 세대별로 모아 둡니다.
      if (id !== activeNegotiationId.current || !peer.remoteDescription) {
        const queued = pendingCandidates.current.get(id) ?? [];
        queued.push(message.candidate);
        pendingCandidates.current.set(id, queued);
        return;
      }

      try {
        await peer.addIceCandidate(message.candidate);
      } catch {
        if (!ignoreOffer.current) throw new Error("길 후보를 넣지 못했습니다");
      }
    },
    [],
  );

  const acceptIceRestart = React.useCallback(
    (peer: RTCPeerConnection, id: RtcNegotiationId) => {
      if (retiredNegotiationIds.current.has(id)) return;
      replaceNegotiation(id);
      reuseNegotiationIdForNextOffer.current = true;
      peer.restartIce();
      setStatus("CONNECTING");
    },
    [replaceNegotiation],
  );

  const acceptPeerLeft = React.useCallback(
    (id: RtcNegotiationId) => {
      if (id !== activeNegotiationId.current) return;
      teardown();
      setStatus("ENDED");
    },
    [teardown],
  );

  const accept = React.useCallback(
    (type: RtcSignalType, payload: unknown) => {
      const peer = peerRef.current;
      const peerPayload = asPeerPayload(payload);
      if (!peer || !peerPayload) return;
      const id = peerPayload.negotiationId;

      enqueueSignaling(async () => {
        // 훅이 새로 열린 뒤에 닿은 쪽지는 지난 통화의 것입니다.
        if (peer !== peerRef.current || peer.signalingState === "closed") return;

        switch (type) {
          case "RTC_PEER_READY":
            return acceptPeerReady(peer, id);
          case "RTC_OFFER":
          case "RTC_ANSWER":
            return acceptDescription(type, peer, payload);
          case "RTC_ICE_CANDIDATE":
            return acceptIceCandidate(peer, payload);
          case "RTC_ICE_RESTART":
            return acceptIceRestart(peer, id);
          case "RTC_PEER_LEFT":
            return acceptPeerLeft(id);
        }
      }).catch(() => {
        // 쪽지 하나가 어긋나도 통화를 접지는 않습니다. 다음 협상에서 따라잡습니다.
        if (peer === peerRef.current) setStatus("CONNECTING");
      });
    },
    [
      acceptDescription,
      acceptIceCandidate,
      acceptIceRestart,
      acceptPeerLeft,
      acceptPeerReady,
      enqueueSignaling,
    ],
  );

  const toggleMic = React.useCallback(() => {
    const next = !micOnRef.current;
    micOnRef.current = next;
    localRef.current?.getAudioTracks().forEach((track) => (track.enabled = next));
    setMicOn(next);
  }, []);

  const toggleCamera = React.useCallback(() => {
    const next = !cameraOnRef.current;
    cameraOnRef.current = next;
    localRef.current?.getVideoTracks().forEach((track) => (track.enabled = next));
    setCameraOn(next);
  }, []);

  const hangUp = React.useCallback(() => {
    const id = activeNegotiationId.current;
    if (id) sendRef.current("RTC_PEER_LEFT", { negotiationId: id });
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
    peerAway,
    peerPresent,
    hangUp,
  };
}

function asPeerPayload(payload: unknown): RtcPeerPayload | null {
  if (!payload || typeof payload !== "object") return null;
  const negotiationId = (payload as { negotiationId?: unknown }).negotiationId;
  return typeof negotiationId === "string" && negotiationId.length > 0 ? { negotiationId } : null;
}

function asDescriptionPayload(payload: unknown): RtcDescriptionPayload | null {
  const peerPayload = asPeerPayload(payload);
  const description = (payload as { description?: unknown } | null)?.description;
  if (!peerPayload || !description || typeof description !== "object") return null;
  const type = (description as { type?: unknown }).type;
  if (type !== "offer" && type !== "answer") return null;
  return { ...peerPayload, description: description as RTCSessionDescriptionInit };
}

function asIceCandidatePayload(payload: unknown): RtcIceCandidatePayload | null {
  const peerPayload = asPeerPayload(payload);
  const candidate = (payload as { candidate?: unknown } | null)?.candidate;
  if (!peerPayload || !candidate || typeof candidate !== "object") return null;
  return { ...peerPayload, candidate: candidate as RTCIceCandidateInit };
}
