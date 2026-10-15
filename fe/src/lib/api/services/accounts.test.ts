import { beforeEach, describe, expect, it, vi } from "vitest";
import { confirmAccountVerification, requestAccountVerification } from "./accounts";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ authenticatedBackendApi: () => ({ post }) }));

beforeEach(() => {
  post.mockReset();
});

describe("requestAccountVerification", () => {
  it("계좌 정보로 확인 건을 연다", async () => {
    const body = {
      bankCodeId: 52,
      accountNumber: "11012345678842",
      accountHolder: "김미미",
    };
    const verification = {
      verificationId: 77301,
      bankName: "신한은행",
      maskedNumber: "···· 8842",
      accountHolder: "김미미",
      expiresAt: "2026-09-17T23:30:00+09:00",
    };
    post.mockResolvedValueOnce({ data: verification });

    await expect(requestAccountVerification(body)).resolves.toEqual(verification);
    expect(post).toHaveBeenCalledWith("/api/accounts/verifications", body);
  });
});

describe("confirmAccountVerification", () => {
  it("확인 건에 네 자리 문자열을 싣는다", async () => {
    const account = {
      accountId: 22,
      bankName: "신한은행",
      maskedNumber: "···· 8842",
      accountHolder: "김미미",
    };
    post.mockResolvedValueOnce({ data: account });

    await expect(confirmAccountVerification(77301, { authCode: "0001" })).resolves.toEqual(
      account,
    );
    expect(post).toHaveBeenCalledWith(
      "/api/accounts/verifications/77301/confirm",
      { authCode: "0001" },
    );
  });
});
