/**
 * Motion 공통 값. 화면마다 다른 감각이 생기지 않게 한 곳에서 관리합니다
 * (docs/FRONTEND_CONVENTIONS.md 13장).
 *
 * `EASE` 는 `styles/tokens.css` 의 `--k-ease` 와 같은 곡선입니다. CSS 전환(hover 등)과
 * Motion 애니메이션이 같은 곡선을 써야 한 화면 안에서 움직임이 따로 놀지 않습니다.
 * 한쪽을 바꾸면 다른 쪽도 함께 바꿉니다.
 */
export const EASE = [0.2, 0, 0, 1] as const;

/**
 * 지속 시간(초). 컨벤션은 진입·강조 효과를 0.2~0.4초로 잡습니다.
 * 그보다 길어야 한다면 이유를 설명할 수 있어야 합니다.
 *
 * - `fast`  — 색·테두리처럼 눈에 띄지 않아야 하는 변화. `--k-duration` 과 같습니다.
 * - `base`  — 기본 진입·퇴장.
 * - `slow`  — 읽는 사람이 바뀐 것을 알아채야 하는 변화.
 */
export const DURATION = {
  fast: 0.15,
  base: 0.24,
  slow: 0.4,
} as const;
