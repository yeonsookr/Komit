import { createStore } from "zustand/vanilla";
import { fetchMe, requestLogout } from "@/lib/api/services/session";
import type { Me } from "@/types/user";

/**
 * 로그인 상태.
 *
 * - `loading`       — 아직 모릅니다. 서버 렌더와 첫 클라이언트 렌더는 항상 이 값입니다.
 *                     서버와 클라이언트가 같은 화면을 그려야 hydration 이 어긋나지 않습니다.
 * - `authenticated` — 내 정보를 받았습니다. `user` 가 있습니다.
 * - `anonymous`     — 로그인하지 않았거나 세션이 끝났습니다.
 *
 * 토큰은 여기 없습니다. httpOnly 쿠키에만 있고 Next 서버가 다룹니다.
 */
export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface AuthState {
  status: AuthStatus;
  user: Me | null;
}

export interface AuthActions {
  /** 내 정보 조회 결과를 반영합니다. 로그인 직후나 프로필을 고친 뒤 부릅니다. */
  setUser: (user: Me) => void;
  /** 비로그인으로 돌립니다. 세션 만료 알림이 오면 Provider 가 부릅니다. */
  clear: () => void;
  /** `/api/users/me` 를 불러 상태를 정하고, 인증 성공 여부를 돌려줍니다. */
  loadMe: () => Promise<boolean>;
  /** 서버에 로그아웃을 알리고 비로그인으로 돌립니다. 서버가 실패해도 화면은 로그아웃합니다. */
  logout: () => Promise<void>;
}

export type AuthStore = AuthState & AuthActions;

export const defaultAuthState: AuthState = { status: "loading", user: null };

/**
 * 스토어를 만드는 함수. 전역 변수로 두지 않고 Provider 가 마운트마다 하나씩 만듭니다.
 * Next 서버는 요청을 동시에 여러 개 처리하므로, 모듈 전역 스토어는 요청 사이에
 * 상태가 섞일 수 있습니다(Zustand 공식 Next.js 가이드).
 */
export function createAuthStore(initState: AuthState = defaultAuthState) {
  return createStore<AuthStore>()((set) => ({
    ...initState,

    setUser: (user) => set({ status: "authenticated", user }),

    clear: () => set({ status: "anonymous", user: null }),

    loadMe: async () => {
      try {
        const me = await fetchMe();
        set({ status: "authenticated", user: me });
        return true;
      } catch {
        // 401 은 로그인하지 않은 것이고, 네트워크 오류도 로그인했다고 볼 근거가 없습니다.
        set({ status: "anonymous", user: null });
        return false;
      }
    },

    logout: async () => {
      try {
        await requestLogout();
      } catch (error) {
        // 서버 쪽이 실패해도 쿠키는 Route Handler 가 지웠거나 이미 없는 상태입니다.
        console.error("[auth] 로그아웃 요청이 실패했습니다", error);
      } finally {
        set({ status: "anonymous", user: null });
      }
    },
  }));
}

export type AuthStoreApi = ReturnType<typeof createAuthStore>;
