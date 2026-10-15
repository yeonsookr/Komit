-- 같은 거래의 송금은 한 번만 시도한다 (R-PAY-12)
-- V1 의 uq_financial_operation_attempts_unresolved 는 결과를 모르는 동안만 새 시도를 막았다.
-- 송금이 성공(SUCCEEDED)한 뒤에도 같은 거래·단계의 새 시도가 생기지 않게 DB 가 한 번 더 막는다.
-- 실패(FAILED) 시도는 금융망이 거절해 자금 유출이 없는 경우이므로 세지 않는다. 실패한 거래를 다시 보내지 않는 것은 애플리케이션이 지킨다.
-- 이미 한 거래에 성공한 송금 시도가 둘 이상 있으면 이 인덱스를 만들지 못하고 배포가 멈춘다. 그 경우 이중 송금 여부를 먼저 확인한다.
CREATE UNIQUE INDEX uq_financial_operation_attempts_one_transfer
    ON financial_operation_attempts (transaction_id, step)
    WHERE step IN ('DEPOSIT_TRANSFER', 'PAYOUT_TRANSFER', 'FEE_TRANSFER', 'REFUND_TRANSFER')
      AND status <> 'FAILED';
