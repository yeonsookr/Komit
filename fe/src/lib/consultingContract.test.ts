import { describe, expect, it } from "vitest";
import {
  amendConsultingBody,
  isPastSchedule,
  writeConsultingBody,
  type ConsultingContractFields,
} from "./consultingContract";

const fields: ConsultingContractFields = {
  topic: " 결제 실패 원인 확인 ",
  detail: "",
  date: "2026-09-27",
  hour: "14",
  minute: "07",
  durationMinutes: 45,
  price: 50_000,
  message: "",
};

describe("consultingContract", () => {
  it("분 단위 일시를 한국 기준으로 보낸다", () => {
    expect(writeConsultingBody(fields).scheduledAt).toBe("2026-09-27T14:07:00+09:00");
  });

  it("새로 보낼 때는 비운 설명과 메시지를 보내지 않는다", () => {
    const body = writeConsultingBody(fields);

    expect(body.topic).toBe("결제 실패 원인 확인");
    expect(body).not.toHaveProperty("detail");
    expect(body).not.toHaveProperty("message");
  });

  it("고칠 때 설명을 비우면 빈 문자열로 보내 지우게 한다", () => {
    expect(amendConsultingBody(fields, null).detail).toBe("");
  });

  it("고칠 때 일시를 그대로 두면 보내지 않는다", () => {
    const body = amendConsultingBody(fields, "2026-09-27T05:07:00Z");

    expect(body).not.toHaveProperty("scheduledAt");
  });

  it("고칠 때 일시를 바꾸면 보낸다", () => {
    const body = amendConsultingBody({ ...fields, minute: "08" }, "2026-09-27T05:07:00Z");

    expect(body.scheduledAt).toBe("2026-09-27T14:08:00+09:00");
  });

  it("지난 일시를 가린다", () => {
    const now = new Date("2026-09-27T05:07:00Z");

    expect(isPastSchedule("2026-09-27T14:07:00+09:00", now)).toBe(true);
    expect(isPastSchedule("2026-09-27T14:08:00+09:00", now)).toBe(false);
  });
});
