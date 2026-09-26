import { defineConfig, devices } from '@playwright/test';

/**
 * 두 브라우저로 WebRTC 를 붙여 보는 전용 설정입니다.
 *
 * **기존 `playwright.config.ts` 와 섞지 않습니다.** 그쪽은 목 서버를 보고 빌드본을 띄우지만,
 * 이 시험은 실제 Spring(8080)과 개발 서버(3000)를 봅니다. 띄우는 것도 여기서 하지 않습니다 —
 * PostgreSQL·Redis·Spring·FE 를 먼저 올려 두고 이 설정으로 돌립니다.
 *
 * 카메라는 가짜 장치를 물립니다. 진짜 카메라를 쓰면 기계마다 결과가 달라지고, 허가 창이
 * 떠서 자동으로 진행되지 않습니다.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

export default defineConfig({
  testDir: './e2e-webrtc',
  testMatch: '**/*.spec.ts',
  // 두 사람이 같은 세션을 두고 주고받는 시험이라 나눠 돌리면 서로를 밟습니다.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  timeout: 90_000,
  use: {
    baseURL,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        launchOptions: {
          args: [
            // 허가 창을 띄우지 않고 통과시킵니다.
            '--use-fake-ui-for-media-stream',
            // 진짜 카메라·마이크 대신 만들어 낸 영상과 소리를 물립니다.
            '--use-fake-device-for-media-stream',
            // 화면 고르는 창을 띄우지 않고 전체 화면을 고릅니다. 세션의 영상은 공유한 화면이 기본입니다.
            '--auto-select-desktop-capture-source=Entire screen',
          ],
        },
      },
    },
  ],
});
