import { beforeEach, describe, expect, it, vi } from "vitest";
import { createRealtimeTicket } from "./realtime";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("../server", () => ({ authenticatedBackendApi: () => ({ post }) }));

beforeEach(() => {
  post.mockReset();
});

describe("createRealtimeTicket", () => {
  it("Spring 티켓 요청을 인증 Axios 인스턴스로 보낸다", async () => {
    const body = { channel: "CHAT_ROOM" as const, channelId: "42" };
    post.mockResolvedValueOnce({ data: { ticket: "ticket-1" } });

    await expect(createRealtimeTicket(body)).resolves.toEqual({ ticket: "ticket-1" });
    expect(post).toHaveBeenCalledWith("/api/realtime/tickets", body);
  });
});
