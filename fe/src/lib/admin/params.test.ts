import { describe, expect, it } from "vitest";
import { safeAdminNext } from "./params";

describe("운영 로그인 뒤 돌아갈 곳", () => {
  it("운영 콘솔 안이면 그대로 돌려준다", () => {
    expect(safeAdminNext("/admin")).toBe("/admin");
    expect(safeAdminNext("/admin/reports?status=OPEN")).toBe("/admin/reports?status=OPEN");
  });

  it("콘솔 밖으로는 보내지 않는다", () => {
    // 운영자에게는 사용자 계정이 없을 수 있어 사용자 화면에 떨어지면 갈 곳이 없습니다.
    expect(safeAdminNext("/mypage")).toBeUndefined();
    expect(safeAdminNext("/")).toBeUndefined();
    // 이름만 비슷한 경로도 막습니다.
    expect(safeAdminNext("/administrators")).toBeUndefined();
  });

  it("로그인 화면 자신으로는 돌아가지 않는다", () => {
    expect(safeAdminNext("/admin/login")).toBeUndefined();
    expect(safeAdminNext("/admin/login?next=%2Fadmin")).toBeUndefined();
  });

  it("남의 사이트로 튕기는 값은 막는다", () => {
    expect(safeAdminNext("https://evil.example/admin")).toBeUndefined();
    expect(safeAdminNext("//evil.example/admin")).toBeUndefined();
    expect(safeAdminNext("/\\evil.example/admin")).toBeUndefined();
    expect(safeAdminNext(undefined)).toBeUndefined();
  });
});
