import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';

/**
 * 두 브라우저가 실제로 통화를 붙이는지 봅니다.
 *
 * **미리 올려 두어야 하는 것** — PostgreSQL, Redis, Spring(8080), FE 개발 서버(3000).
 * 그리고 아직 아무도 동의하지 않은 세션 하나와 그 세션 당사자 두 사람의 토큰입니다.
 *
 *   E2E_SESSION_ID=6003
 *   E2E_TOKEN_CLIENT=<의뢰인 토큰>
 *   E2E_TOKEN_DEVELOPER=<개발자 토큰>
 *
 * 토큰은 로컬 개발 키로 만든 것이며 저장소에 두지 않습니다.
 * `E2E_TRACE=1` 을 주면 오간 쪽지와 요청을 찍습니다. 막혔을 때 어디서 멈췄는지 봅니다.
 */
const SESSION_ID = process.env.E2E_SESSION_ID ?? '6003';
const ACCESS_TOKEN_COOKIE = 'komit_access_token';
const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
const TRACE = process.env.E2E_TRACE === '1';

/** 상대가 붙기까지 기다려 주는 시간. 로컬이라 넉넉하면 충분합니다. */
const CONNECT_TIMEOUT_MS = 20_000;

function requiredToken(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} 가 없습니다. 로컬 개발 키로 만든 토큰을 넣어 주세요.`);
  }
  return value;
}

function trace(page: Page, who: string): void {
  if (!TRACE) return;

  page.on('pageerror', (error) => console.log(`[${who}] 화면 오류 ${error.message}`));
  page.on('response', (response) => {
    if (response.url().includes('/api/')) {
      console.log(`[${who}] ${response.status()} ${response.url().replace(BASE_URL, '')}`);
    }
  });
  page.on('websocket', (socket) => {
    if (!socket.url().includes('/service/ws/')) return;
    console.log(`[${who}] 소켓 열림`);
    socket.on('framereceived', (frame) => {
      console.log(`[${who}] 받음 ${String(frame.payload).slice(0, 90)}`);
    });
    socket.on('framesent', (frame) => {
      console.log(`[${who}] 보냄 ${String(frame.payload).slice(0, 90)}`);
    });
    socket.on('close', () => console.log(`[${who}] 소켓 닫힘`));
  });
}

/** 그 사람 몫의 브라우저 하나. 쿠키가 섞이지 않게 context 를 따로 씁니다. */
async function openAs(browser: Browser, token: string, who: string) {
  const context: BrowserContext = await browser.newContext({
    permissions: ['camera', 'microphone'],
  });
  // 카카오를 거치지 않고 로그인한 상태로 만듭니다. 이 쿠키는 Next 서버가 읽습니다.
  await context.addCookies([{ name: ACCESS_TOKEN_COOKIE, value: token, url: BASE_URL }]);

  const page = await context.newPage();
  trace(page, who);
  return { context, page };
}

/** 그 자리에 실제로 그림이 흐르는지. 크기가 잡히면 트랙이 오고 있다는 뜻입니다. */
async function hasMovingVideo(page: Page, index: number): Promise<boolean> {
  return page.evaluate((i) => {
    const video = document.querySelectorAll('video')[i] as HTMLVideoElement | undefined;
    return Boolean(video && video.videoWidth > 0 && video.videoHeight > 0);
  }, index);
}

/**
 * 양쪽에 서로가 보일 때까지 기다립니다.
 *
 * 붙고 난 뒤 화면에는 큰 무대(상대)가 먼저, 작은 타일(나)이 그다음에 놓입니다. 둘 다 그림이
 * 흘러야 통화가 오간 것입니다 — 내 것만 흐르면 카메라만 켜진 것이고, 상대 것만 흐르면 내
 * 트랙이 실리지 않은 것입니다.
 */
async function expectTwoWayVideo(pages: Page[]): Promise<void> {
  for (const page of pages) {
    await expect.poll(() => hasMovingVideo(page, 0), { timeout: CONNECT_TIMEOUT_MS }).toBe(true);
    await expect.poll(() => hasMovingVideo(page, 1), { timeout: CONNECT_TIMEOUT_MS }).toBe(true);
  }
}

test('먼저 동의한 사람은 미리보기만 보고, 둘 다 동의하면 통화가 붙는다', async ({ browser }) => {
  const client = await openAs(browser, requiredToken('E2E_TOKEN_CLIENT'), '의뢰인');
  const developer = await openAs(browser, requiredToken('E2E_TOKEN_DEVELOPER'), '개발자');

  try {
    await client.page.goto(`/sessions/${SESSION_ID}`);

    await expect(client.page.getByText('상대방의 동의를 기다리고 있습니다')).toBeVisible({
      timeout: CONNECT_TIMEOUT_MS,
    });

    // 자기 카메라는 켜진다. 기다리는 화면에는 미리보기 하나뿐이라 첫 자리가 내 화면이다.
    // 상대는 아직 채널에 없어 이 화면을 받지 못한다.
    await expect
      .poll(() => hasMovingVideo(client.page, 0), { timeout: CONNECT_TIMEOUT_MS })
      .toBe(true);

    await developer.page.goto(`/sessions/${SESSION_ID}`);

    // 기다리던 쪽 화면이 저절로 바뀐다. 되묻기가 아니라 서버 알림으로 안다.
    await expect(client.page.getByText('상대방의 동의를 기다리고 있습니다')).toBeHidden({
      timeout: CONNECT_TIMEOUT_MS,
    });

    await expectTwoWayVideo([client.page, developer.page]);
  } finally {
    await client.context.close();
    await developer.context.close();
  }
});

test('마이크와 카메라를 껐다 켜도 통화는 끊기지 않는다', async ({ browser }) => {
  const client = await openAs(browser, requiredToken('E2E_TOKEN_CLIENT'), '의뢰인');
  const developer = await openAs(browser, requiredToken('E2E_TOKEN_DEVELOPER'), '개발자');

  try {
    await client.page.goto(`/sessions/${SESSION_ID}`);
    await developer.page.goto(`/sessions/${SESSION_ID}`);
    await expectTwoWayVideo([client.page, developer.page]);

    const mic = client.page.getByRole('button', { name: /마이크/ });
    const camera = client.page.getByRole('button', { name: /카메라/ });

    // 끄면 트랙을 없애지 않고 소리만 비웁니다. 없애면 다시 협상해야 합니다.
    await mic.click();
    await expect(mic).toHaveAttribute('aria-pressed', 'true');
    await expect(mic).toHaveText('마이크 켜기');

    await camera.click();
    await expect(camera).toHaveAttribute('aria-pressed', 'true');
    await expect(camera).toHaveText('카메라 켜기');

    await mic.click();
    await camera.click();
    await expect(mic).toHaveAttribute('aria-pressed', 'false');
    await expect(camera).toHaveAttribute('aria-pressed', 'false');

    // 끄고 켜는 동안 연결이 살아 있어야 합니다.
    await expectTwoWayVideo([client.page, developer.page]);
  } finally {
    await client.context.close();
    await developer.context.close();
  }
});

test('한쪽이 새로고침해도 다시 붙는다', async ({ browser }) => {
  const client = await openAs(browser, requiredToken('E2E_TOKEN_CLIENT'), '의뢰인');
  const developer = await openAs(browser, requiredToken('E2E_TOKEN_DEVELOPER'), '개발자');

  try {
    await client.page.goto(`/sessions/${SESSION_ID}`);
    await developer.page.goto(`/sessions/${SESSION_ID}`);
    await expectTwoWayVideo([client.page, developer.page]);

    // 새로고침하면 표도 소켓도 통화도 처음부터 다시 만든다.
    await client.page.reload();

    // 이미 양쪽이 동의한 세션이라 기다리지 않고 바로 들어간다.
    await expect(client.page.getByText('상대방의 동의를 기다리고 있습니다')).toBeHidden({
      timeout: CONNECT_TIMEOUT_MS,
    });

    // 남아 있던 쪽도 새 제안을 받아 다시 협상한다.
    await expectTwoWayVideo([client.page, developer.page]);
  } finally {
    await client.context.close();
    await developer.context.close();
  }
});

/**
 * 한 사람이 끝내면 양쪽이 안다. 이 쪽지가 없으면 끝낸 사람만 녹음을 마감하고, 상대는 자기도
 * 종료를 누르기 전까지 올리지 않는다.
 *
 * **맨 마지막에 둔다.** 세션을 실제로 끝내므로, 뒤에 오는 시험은 끝난 세션을 열게 되어
 * 요약 화면으로 넘어간다.
 */
test('한쪽이 세션을 끝내면 양쪽에 종료가 전해진다', async ({ browser }) => {
  const client = await openAs(browser, requiredToken('E2E_TOKEN_CLIENT'), '의뢰인');
  const developer = await openAs(browser, requiredToken('E2E_TOKEN_DEVELOPER'), '개발자');

  // 두 사람이 받은 쪽지를 모아 둔다. 화면이 아직 이 쪽지를 쓰지 않으므로 회선에서 직접 본다.
  const ended: Record<string, string[]> = { 의뢰인: [], 개발자: [] };
  for (const [who, page] of [['의뢰인', client.page], ['개발자', developer.page]] as const) {
    page.on('websocket', (socket) => {
      if (!socket.url().includes('/service/ws/')) return;
      socket.on('framereceived', (frame) => {
        const text = String(frame.payload);
        if (text.includes('SYSTEM_SESSION_ENDED')) ended[who].push(text);
      });
    });
  }

  try {
    await client.page.goto(`/sessions/${SESSION_ID}`);
    await developer.page.goto(`/sessions/${SESSION_ID}`);
    await expectTwoWayVideo([client.page, developer.page]);

    // 의뢰인만 끝낸다.
    await client.page.getByRole('button', { name: '세션 종료' }).click();
    await client.page.getByRole('button', { name: '세션 종료' }).last().click();

    await expect.poll(() => ended.개발자.length, { timeout: CONNECT_TIMEOUT_MS }).toBeGreaterThan(0);
    await expect.poll(() => ended.의뢰인.length, { timeout: CONNECT_TIMEOUT_MS }).toBeGreaterThan(0);

    // 양쪽이 같은 종료 시각을 받는다.
    const endedAt = (text: string) => JSON.parse(text).payload.endedAt as string;
    expect(endedAt(ended.개발자[0])).toBe(endedAt(ended.의뢰인[0]));
  } finally {
    await client.context.close();
    await developer.context.close();
  }
});
