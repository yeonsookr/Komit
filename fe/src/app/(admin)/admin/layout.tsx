/**
 * (admin) 레이아웃 — 골격을 붙이지 않고 지나갑니다.
 *
 * 운영 콘솔의 상단바와 본문 폭은 `AdminShell` 이 그립니다. 레이아웃이 아니라
 * 화면이 쥐고 있는 이유는 상단바에 「기준 시각」이 들어가는데, 그 값이 각 화면이
 * 읽어 오는 `asOf` 이기 때문입니다 — 레이아웃에서 한 번 더 읽으면 화면과 다른
 * 시각이 나란히 보입니다.
 *
 * route group 을 따로 둔 것은 `(site)` 의 GNB·푸터와 `loading`·`error` 를 섞지
 * 않기 위해서입니다.
 */
export default function AdminLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return children;
}
