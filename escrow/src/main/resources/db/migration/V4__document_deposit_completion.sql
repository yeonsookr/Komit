-- R-PAY-08: 예치 완료는 입금 검증, 보관계좌 정산, 로컬 완료 기록이 모두 확정된 시점이다.
-- 이미 적용된 V1의 체크섬을 바꾸지 않고 상태 컬럼의 의미를 명확히 한다.
COMMENT ON COLUMN escrows.status IS 'CREATED / DEPOSIT_PENDING / ACTIVE / COMPLETED / REFUNDED / CANCELLED. ACTIVE는 계약 금액 입금 검증, 보관계좌 정산, 로컬 완료 기록이 모두 확정된 뒤에만 설정';

COMMENT ON COLUMN escrow_transactions.deposit_step IS 'DEPOSIT 전용: VA_CREATING / VA_CREATED / TRANSFER_SENDING / TRANSFERRED / VERIFIED / SETTLED / VA_CLOSED. VA_CLOSED는 예치 완료 후 별도 정리 단계';
