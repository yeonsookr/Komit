import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from "axios";

export interface TokenRefreshOptions {
  /** 새 토큰을 받아 오는 호출. 실패하면 reject 합니다. 쿠키는 이 호출의 응답이 갈아 끼웁니다. */
  refresh: () => Promise<void>;
  /** 이 요청의 401 에 재발급을 시도할지. 인증 경로 자체는 제외해야 무한히 돌지 않습니다. */
  shouldRefresh: (url: string) => boolean;
  /** 재발급까지 실패했을 때. 세션이 끝난 것이므로 화면 상태를 비로그인으로 돌립니다. */
  onExpired: () => void;
}

/** 재시도한 요청에 표시해 두 번 재발급하지 않습니다. */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retriedAfterRefresh?: boolean;
}

/**
 * 401 이면 토큰을 한 번 재발급하고 같은 요청을 다시 보냅니다.
 *
 * 액세스 토큰이 만료되는 순간 화면의 여러 요청이 동시에 401 을 받을 수 있습니다.
 * 재발급은 한 번만 하고(진행 중인 약속을 공유) 나머지는 그 결과를 기다립니다.
 * 리프레시 토큰은 재발급하면 이전 값을 못 쓰므로, 두 번 부르면 두 번째가 실패합니다.
 *
 * `attachErrorNormalizer` 보다 **먼저** 붙여야 합니다. 정규화된 ApiError 에는 원래
 * 요청 설정이 없어 다시 보낼 수 없습니다. axios 응답 인터셉터는 붙인 순서대로 돕니다.
 */
export function attachTokenRefresh(
  instance: AxiosInstance,
  { refresh, shouldRefresh, onExpired }: TokenRefreshOptions,
): AxiosInstance {
  let inFlight: Promise<void> | null = null;

  const refreshOnce = (): Promise<void> => {
    inFlight ??= refresh().finally(() => {
      inFlight = null;
    });
    return inFlight;
  };

  instance.interceptors.response.use(
    (response) => response,
    async (error: unknown) => {
      if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
        throw error;
      }

      const config = error.config as RetriableConfig;
      if (config._retriedAfterRefresh || !shouldRefresh(config.url ?? "")) {
        throw error;
      }

      try {
        await refreshOnce();
      } catch {
        onExpired();
        throw error;
      }

      return instance.request({ ...config, _retriedAfterRefresh: true } as RetriableConfig);
    },
  );

  return instance;
}
