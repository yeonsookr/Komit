/**
 * 브라우저에서 현재 FE 배포의 하위 경로를 읽습니다.
 * 값은 next.config.ts가 빌드 시 전역으로 주입합니다.
 */
export function clientBasePath(
  value = process.env.NEXT_PUBLIC_BASE_PATH?.trim() ?? "",
): string {
  if (value && (!value.startsWith("/") || value.endsWith("/"))) {
    throw new Error(`NEXT_PUBLIC_BASE_PATH 는 "/"로 시작하고 "/"로 끝나지 않아야 합니다: ${value}`);
  }
  return value;
}

/** Next Link가 아닌 일반 브라우저 이동에 현재 FE의 하위 경로를 붙입니다. */
export function appPath(path: string, basePath = clientBasePath()): string {
  if (!path.startsWith("/")) throw new Error(`앱 경로는 "/"로 시작해야 합니다: ${path}`);
  return `${basePath}${path}`;
}
