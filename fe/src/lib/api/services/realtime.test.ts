import { beforeEach, describe, expect, it, vi } from "vitest";
import { createRealtimeTicket } from "./realtime";

const { post, authorized } = vi.hoisted(() => ({
  post: vi.fn(),
  authorized: vi.fn(),
}));

vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ backendApi: () => ({ post }) }));
vi.mock("@/lib/auth/session", () => ({ authorized }));

beforeEach(() => {
  post.mockReset();
  authorized.mockReset();
});

describe("createRealtimeTicket", () => {
  it("Spring 티켓 요청에 로그인 Bearer 헤더를 붙인다", async () => {
    const auth = { headers: { Authorization: "Bearer access-token" } };
    const body = { channel: "CHAT_ROOM" as const, channelId: "42" };
    authorized.mockResolvedValueOnce(auth);
    post.mockResolvedValueOnce({ data: { ticket: "ticket-1" } });

    await expect(createRealtimeTicket(body)).resolves.toEqual({ ticket: "ticket-1" });
    expect(post).toHaveBeenCalledWith("/api/realtime/tickets", body, auth);
  });
});
