import { beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const { createRealtimeTicket } = vi.hoisted(() => ({ createRealtimeTicket: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ createRealtimeTicket }));

beforeEach(() => createRealtimeTicket.mockReset());

describe("POST /api/realtime/tickets", () => {
  it("백엔드 계약과 같은 200으로 티켓을 반환한다", async () => {
    createRealtimeTicket.mockResolvedValueOnce({ ticket: "ticket-1" });
    const request = new Request("http://localhost/api/realtime/tickets", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ channel: "CHAT_ROOM", channelId: "42" }),
    });

    const response = await POST(request);

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ ticket: "ticket-1" });
  });
});
