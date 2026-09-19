-- R-PAY-07: 계약 생성 시 수수료율을 고정하고 지급·수수료·환불을 별도 거래로 기록한다.
-- 기존 V1~V4의 체크섬은 유지한다.

ALTER TABLE escrows
    ADD COLUMN fee_rate_bps SMALLINT NOT NULL DEFAULT 100,
    ADD CONSTRAINT ck_escrows_fee_rate_bps CHECK (fee_rate_bps BETWEEN 0 AND 9999);

COMMENT ON COLUMN escrows.fee_rate_bps IS 'R-PAY-07. 생성 시 적용한 중개 수수료율(1bp=0.01%). 생성 후 불변. 설정 변경은 새 계약에만 적용';
COMMENT ON COLUMN escrows.closed_at IS '계약 최종 정산의 작업자 지급·플랫폼 수수료·환불이 모두 완료되고 잔액이 0인 종료 시각. 예치 전 취소도 종료. 한쪽 처리 중이면 NULL';
COMMENT ON COLUMN escrow_transactions.transaction_type IS 'DEPOSIT 예치 / RELEASE 작업자 순지급 / FEE 플랫폼 수수료 이체 / REFUND 계약 잔액 환불. 마일스톤 개별 승인만으로 RELEASE 생성 금지';
COMMENT ON COLUMN escrow_transactions.amount IS '원 단위 양수. RELEASE는 승인 총액에서 수수료를 뺀 순지급액, FEE는 원 미만 절사한 수수료, REFUND는 환불액. 승인 총액은 같은 정산의 RELEASE+FEE. 수수료 0원이면 FEE 거래를 만들지 않음. 생성 후 불변';
COMMENT ON COLUMN escrow_transactions.external_reference_id IS '예치는 INITIAL. 계약 최종 정산의 RELEASE/FEE/REFUND는 같은 SETTLEMENT:정산ID 사용, 거래 유형으로 구분. 재시도도 동일 값. 마일스톤 ID 사용 금지';
COMMENT ON COLUMN escrow_transactions.recipient_bank_code IS '지급·수수료 또는 환불 수취 은행코드';
COMMENT ON COLUMN financial_operation_attempts.step IS 'VA_CREATE / DEPOSIT_TRANSFER / DEPOSIT_VERIFY / VA_SETTLE / VA_CLOSE / HOLDER_VERIFY / PAYOUT_TRANSFER / FEE_TRANSFER / REFUND_TRANSFER / RESULT_QUERY';
