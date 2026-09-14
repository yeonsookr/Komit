import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 도커 이미지에 node_modules 전체를 넣지 않고 실제로 쓰는 것만 추려 담습니다.
  output: "standalone",
};

export default nextConfig;
