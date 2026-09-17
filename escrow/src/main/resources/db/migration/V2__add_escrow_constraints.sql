-- 중복 처리 차단과 참조 무결성. 기획서 7절 "동일 요청의 중복 처리를 막고"를 DB가 보장하게 한다.
-- 애플리케이션 락만으로는 동시 요청과 재시도에서 중복 거래가 생길 수 있다.

-- 같은 이용 서비스가 같은 외부 계약으로 에스크로를 두 번 만들 수 없다.
ALTER TABLE escrows
    ADD CONSTRAINT uq_escrows_service_client_external_contract
    UNIQUE (service_client_id, external_contract_id);

-- 동일 요청 재전송이 새 거래를 만들지 않는다.
ALTER TABLE escrow_transactions
    ADD CONSTRAINT uq_escrow_transactions_idempotency_key
    UNIQUE (idempotency_key);

-- 같은 업무 참조에 같은 유형의 거래는 하나뿐이다.
-- 최종 정산의 지급과 환불은 external_reference_id를 공유하고 transaction_type으로 갈린다.
ALTER TABLE escrow_transactions
    ADD CONSTRAINT uq_escrow_transactions_business_reference
    UNIQUE (escrow_id, external_reference_id, transaction_type);

-- 같은 거래와 단계 안에서 시도 번호는 중복되지 않는다.
ALTER TABLE financial_operation_attempts
    ADD CONSTRAINT uq_financial_operation_attempts_attempt
    UNIQUE (transaction_id, step, attempt_no);

-- 외부 조회의 기준값이므로 전역에서 유일해야 한다.
ALTER TABLE financial_operation_attempts
    ADD CONSTRAINT uq_financial_operation_attempts_request_reference
    UNIQUE (request_reference);


-- 자금 기록은 삭제하지 않으므로 CASCADE를 두지 않는다.
ALTER TABLE escrows
    ADD CONSTRAINT fk_escrows_service_client
    FOREIGN KEY (service_client_id) REFERENCES service_clients (id);

ALTER TABLE escrow_transactions
    ADD CONSTRAINT fk_escrow_transactions_escrow
    FOREIGN KEY (escrow_id) REFERENCES escrows (id);

ALTER TABLE financial_operation_attempts
    ADD CONSTRAINT fk_financial_operation_attempts_transaction
    FOREIGN KEY (transaction_id) REFERENCES escrow_transactions (id);


-- 에스크로별 거래 조회.
CREATE INDEX idx_escrow_transactions_escrow_status
    ON escrow_transactions (escrow_id, status);

-- 미확정 호출을 오래된 것부터 찾아 후속 조회하거나 운영자가 확인한다.
CREATE INDEX idx_financial_operation_attempts_status_requested_at
    ON financial_operation_attempts (status, requested_at);

-- 동결 건과 진행 중 계약 조회.
CREATE INDEX idx_escrows_status_hold_status
    ON escrows (status, hold_status);
