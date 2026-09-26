/** 컨설팅 계약서 폼이 보내는 값. 칸에 적힌 그대로입니다. */
export interface ConsultingContractFields {
  topic: string;
  detail: string;
  /** `YYYY-MM-DD` */
  date: string;
  /** `HH` */
  hour: string;
  /** `mm` */
  minute: string;
  durationMinutes: number;
  price: number;
  message: string;
}

/**
 * 세션 일시. 한국 기준으로 굳혀 보냅니다.
 *
 * 브라우저의 시간대를 그대로 따르면 해외에서 계약서를 쓴 개발자와 의뢰인이 서로 다른
 * 시각을 보게 됩니다 — 세션은 두 사람이 같은 시각에 만나는 약속이라 어긋나면 안 됩니다.
 */
export function toScheduledAt({ date, hour, minute }: ConsultingContractFields): string {
  return `${date}T${hour}:${minute}:00+09:00`;
}

/** 이미 지난 일시인지. 서버는 지난 일시를 받지 않습니다. */
export function isPastSchedule(scheduledAt: string, now: Date = new Date()): boolean {
  return Date.parse(scheduledAt) <= now.getTime();
}

/** 새로 보낼 때. 비운 설명과 메시지는 보내지 않습니다. */
export function writeConsultingBody(fields: ConsultingContractFields) {
  const detail = fields.detail.trim();
  const message = fields.message.trim();
  return {
    topic: fields.topic.trim(),
    scheduledAt: toScheduledAt(fields),
    durationMinutes: fields.durationMinutes,
    price: fields.price,
    ...(detail === "" ? {} : { detail }),
    ...(message === "" ? {} : { message }),
  };
}

/**
 * 고칠 때. 수정은 보낸 항목만 바꿉니다.
 *
 * - 설명은 늘 보냅니다. 비워서 보내면 서버가 지웁니다.
 * - 일시는 바꿨을 때만 보냅니다. 그대로 보내면 서버가 그 일시를 다시 검사합니다.
 */
export function amendConsultingBody(
  fields: ConsultingContractFields,
  originalScheduledAt: string | null,
) {
  const scheduledAt = toScheduledAt(fields);
  const changed =
    originalScheduledAt === null || Date.parse(originalScheduledAt) !== Date.parse(scheduledAt);
  const message = fields.message.trim();
  return {
    topic: fields.topic.trim(),
    durationMinutes: fields.durationMinutes,
    price: fields.price,
    detail: fields.detail.trim(),
    ...(changed ? { scheduledAt } : {}),
    ...(message === "" ? {} : { message }),
  };
}
