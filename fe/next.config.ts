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
  ...(basePath ? { basePath } : {}),
};

export default nextConfig;
