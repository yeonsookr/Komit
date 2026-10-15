/**
 * 내려받는 파일의 이름을 Content-Disposition 으로 주고받습니다.
 *
 * 백엔드는 한글 이름을 RFC 6266 의 `filename*=UTF-8''…` 로 함께 싣습니다. 그 값을 먼저
 * 읽고, 없으면 `filename="…"` 을 씁니다. 브라우저로 보낼 때도 같은 두 칸을 싣습니다.
 */

/** 이름을 읽지 못했을 때 쓰는 이름. */
export const FALLBACK_FILE_NAME = "deliverable";

/** Content-Disposition 에서 파일 이름을 읽습니다. */
export function fileNameFrom(disposition: string): string {
  const extended = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition);
  if (extended) {
    try {
      return decodeURIComponent(extended[1].trim());
    } catch {
      // 잘못 인코딩된 값이면 아래 일반 이름으로 넘어갑니다.
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ?? FALLBACK_FILE_NAME;
}

/** 브라우저에 보낼 Content-Disposition. 한글 이름이 깨지지 않게 두 칸을 모두 싣습니다. */
export function attachmentDisposition(fileName: string): string {
  const ascii = fileName.replaceAll(/[^\x20-\x7e]|["\\]/g, "_");
  const encoded = encodeURIComponent(fileName).replaceAll(
    /['()*]/g,
    (c) => `%${(c.codePointAt(0) ?? 0).toString(16).toUpperCase()}`,
  );
  return `attachment; filename="${ascii}"; filename*=UTF-8''${encoded}`;
}
