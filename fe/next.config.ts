import type { NextConfig } from "next";

/**
 * 하위 경로로 서비스할 때 붙이는 접두사입니다. 빌드 시점에 굽는 값이라 실행 환경변수로는
 * 바꿀 수 없습니다. mock을 보는 테스트용 FE(`/test`)만 값을 넣어 따로 빌드합니다.
 * 값은 `/`로 시작하고 `/`로 끝나지 않아야 합니다.
 */
const basePath = process.env.BASE_PATH?.trim();
if (basePath && (!basePath.startsWith("/") || basePath.endsWith("/"))) {
  throw new Error(`BASE_PATH 는 "/"로 시작하고 "/"로 끝나지 않아야 합니다: ${basePath}`);
}

const nextConfig: NextConfig = {
  // 도커 이미지에 node_modules 전체를 넣지 않고 실제로 쓰는 것만 추려 담습니다.
  output: "standalone",
  // 브라우저 코드가 자기 배포의 Route Handler를 호출할 때 쓰는 전역 접두사입니다.
  // CI는 BASE_PATH 하나만 넣고, 컴포넌트는 환경변수를 직접 다루지 않습니다.
  env: { NEXT_PUBLIC_BASE_PATH: basePath ?? "" },
  ...(basePath ? { basePath } : {}),
};

export default nextConfig;
