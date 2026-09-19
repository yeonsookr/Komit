-- 빈 DB에 V1 적용 후 psql -v ON_ERROR_STOP=1 -f 로 실행한다. 테스트 데이터는 롤백한다.
BEGIN;
INSERT INTO service_clients (id, name, api_key_hash) VALUES (900001, '검증 서비스', 'schema-test');
INSERT INTO account_auth_requests
    (id, service_client_id, idempotency_key, request_fingerprint, user_key_lookup_hash,
     user_key_encrypted, account_no_encrypted, auth_text, request_reference)
VALUES (900001,900001,'auth-test',repeat('b',64),repeat('c',64),'encrypted-key','encrypted-account','KOMIT','auth-ref');
DO $$
BEGIN
    BEGIN
        UPDATE account_auth_requests SET status='SUCCEEDED' WHERE id=900001;
        RAISE EXCEPTION '실행 정보와 인증 결과 없는 성공이 허용됨';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END $$;
UPDATE account_auth_requests SET status='DISPATCHING', dispatched_by='pod-test', dispatched_at=clock_timestamp()
WHERE id=900001 AND status='REQUESTED';
UPDATE account_auth_requests SET status='UNKNOWN' WHERE id=900001;
INSERT INTO operation_audit_events (account_auth_request_id, actor_type, actor_id, action, reason)
VALUES (900001,'OPERATOR','test','인증 결과 확인','금융망 증빙 확인');
UPDATE account_auth_requests SET status='SUCCEEDED', transaction_unique_no='auth-bank-id',
expires_at=clock_timestamp()+interval '10 minutes' WHERE id=900001;
INSERT INTO member_account_provisions
    (id, service_client_id, user_id, account_type_unique_no, idempotency_key, request_fingerprint, seed_amount)
VALUES (900001, 900001, 'schema@example.com', 'product', 'provision-test', repeat('a',64), 1);
INSERT INTO escrows (id, service_client_id, payer_provision_id, external_contract_id, contract_type, expected_amount)
VALUES (900001, 900001, 900001, 'schema-test', 'DEVELOPMENT', 100);
INSERT INTO escrow_transactions
    (id, service_client_id, escrow_id, transaction_type, amount, external_reference_id, idempotency_key,
     request_fingerprint, depositor_name_encrypted)
VALUES (900001, 900001, 900001, 'DEPOSIT', 100, 'INITIAL', 'deposit-test', repeat('a',64), 'encrypted');
INSERT INTO service_clients (id,name,api_key_hash) VALUES (900002,'다른 서비스','other-hash');
INSERT INTO member_account_provisions
    (id,service_client_id,user_id,account_type_unique_no,idempotency_key,request_fingerprint,seed_amount)
VALUES (900002,900002,'other@example.com','product','provision-test',repeat('a',64),1);
INSERT INTO escrows (id,service_client_id,payer_provision_id,external_contract_id,contract_type,expected_amount)
VALUES (900002,900002,900002,'schema-test','DEVELOPMENT',100);
-- 다른 서비스의 동일 멱등키는 충돌하지 않는다.
INSERT INTO escrow_transactions (id,service_client_id,escrow_id,transaction_type,amount,
    external_reference_id,idempotency_key,request_fingerprint,depositor_name_encrypted)
VALUES (900002,900002,900002,'DEPOSIT',100,'INITIAL','deposit-test',repeat('a',64),'encrypted');
DO $$
BEGIN
    BEGIN
        UPDATE escrow_transactions SET service_client_id=900002, idempotency_key='wrong-tenant' WHERE id=900001;
        RAISE EXCEPTION '거래를 다른 서비스로 연결할 수 있음';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
END $$;
INSERT INTO financial_operation_attempts
    (id, transaction_id, step, attempt_no, request_reference)
VALUES (900001, 900001, 'DEPOSIT_TRANSFER', 1, 'attempt-test');
DO $$
BEGIN
    BEGIN
        UPDATE financial_operation_attempts SET status='DISPATCHING' WHERE id=900001;
        RAISE EXCEPTION '실행 주체 없는 호출이 허용됨';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO financial_operation_attempts (transaction_id, step, attempt_no, request_reference)
        VALUES (900001, 'DEPOSIT_TRANSFER', 2, 'duplicate-attempt');
        RAISE EXCEPTION '미확정 호출의 중복 생성이 허용됨';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END $$;
UPDATE financial_operation_attempts
SET status='DISPATCHING', dispatched_by='pod-uid-test', dispatched_at=clock_timestamp()
WHERE id=900001 AND status='REQUESTED';
DO $$
DECLARE claimed_count integer;
BEGIN
    UPDATE financial_operation_attempts SET status='DISPATCHING'
    WHERE id=900001 AND status='REQUESTED';
    GET DIAGNOSTICS claimed_count=ROW_COUNT;
    IF claimed_count <> 0 THEN RAISE EXCEPTION '실행권 중복 획득'; END IF;
END $$;
UPDATE financial_operation_attempts SET status='UNKNOWN' WHERE id=900001;
-- 응답 유실 후 조회로 성공이 확정되어도 직접 응답 시각을 조작하지 않는다.
UPDATE financial_operation_attempts SET status='SUCCEEDED', external_transaction_id='bank-test'
WHERE id=900001;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM financial_operation_attempts
                   WHERE id=900001 AND status='SUCCEEDED' AND responded_at IS NULL)
    THEN RAISE EXCEPTION '조회 기반 성공 복구 실패'; END IF;
END $$;
ROLLBACK;
