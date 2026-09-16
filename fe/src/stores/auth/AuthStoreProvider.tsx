"use client";

import * as React from "react";
import { useStore } from "zustand";
import { onSessionExpired } from "@/lib/auth/sessionEvents";
import { type AuthStore, type AuthStoreApi, createAuthStore } from "./authStore";

const AuthStoreContext = React.createContext<AuthStoreApi | undefined>(undefined);

/**
 * 인증 스토어를 트리에 내려주고, 마운트되면 내 정보를 한 번 읽습니다.
 *
 * 스토어는 `useState` 초기화 함수로 만들어 마운트당 하나만 생깁니다. 루트 레이아웃에
 * 두므로 화면을 옮겨도 다시 만들어지지 않고, 서버 요청마다는 새로 만들어집니다.
 *
 * 서버 컴포넌트는 이 스토어를 읽지도 쓰지도 않습니다. 서버에서 이미 확정된 사용자
 * 데이터가 필요한 화면(방 레이아웃 등)은 `getMe()` 로 직접 읽어 props 로 내립니다.
 */
export function AuthStoreProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [store] = React.useState(createAuthStore);

  React.useEffect(() => {
    // loadMe 는 실패를 안에서 비로그인 상태로 삼키므로 기다리지 않아도 됩니다.
    store.getState().loadMe();
    return onSessionExpired(() => store.getState().clear());
  }, [store]);

  return <AuthStoreContext.Provider value={store}>{children}</AuthStoreContext.Provider>;
}

/**
 * 인증 상태를 읽는 훅. 필요한 조각만 고르면 그 조각이 바뀔 때만 다시 그립니다.
 *
 * @example
 * const status = useAuthStore((s) => s.status);
 * const user = useAuthStore((s) => s.user);
 */
export function useAuthStore<T>(selector: (store: AuthStore) => T): T {
  const context = React.useContext(AuthStoreContext);
  if (!context) {
    throw new Error("useAuthStore 는 AuthStoreProvider 안에서만 쓸 수 있습니다.");
  }
  return useStore(context, selector);
}
