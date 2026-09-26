-- 복식부기 원장 (R-PAY-15)
-- escrow_transactions 가 COMPLETED 로 바뀌는 순간 같은 DB 트랜잭션 안에서 분개를 남깁니다.
-- 애플리케이션 쓰기 경로는 바꾸지 않습니다. 트리거가 DB에 있으므로 롤링 배포 중 이전 이미지 파드가
-- 완료 처리한 거래도 분개됩니다. 이전 이미지로 되돌려도 Flyway 는 모르는 V3 를 future 로 보고 넘어갑니다.

CREATE TABLE ledger_accounts (
    code VARCHAR(30) PRIMARY KEY,
    account_type VARCHAR(20) NOT NULL,
    normal_side VARCHAR(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_ledger_accounts_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'REVENUE')),
    CONSTRAINT ck_ledger_accounts_normal_side CHECK (
        (account_type = 'ASSET' AND normal_side = 'DEBIT')
        OR (account_type IN ('LIABILITY', 'REVENUE') AND normal_side = 'CREDIT')
    )
);

COMMENT ON TABLE ledger_accounts IS '원장 계정과목 | 복식부기 분개가 쓰는 고정 계정. 코드로만 참조';
COMMENT ON COLUMN ledger_accounts.code IS '계정 코드 | 분개의 차변·대변 계정';
COMMENT ON COLUMN ledger_accounts.account_type IS '계정 유형 | ASSET 자산 / LIABILITY 부채 / REVENUE 수익';
COMMENT ON COLUMN ledger_accounts.normal_side IS '정상 잔액 방향 | 자산은 DEBIT, 부채·수익은 CREDIT';
COMMENT ON COLUMN ledger_accounts.name IS '계정 이름 | 운영 화면 표시용';

INSERT INTO ledger_accounts (code, account_type, normal_side, name) VALUES
    ('CUSTODY_CASH', 'ASSET', 'DEBIT', '보관계좌 현금'),
    ('FEE_CASH', 'ASSET', 'DEBIT', '수수료계좌 현금'),
    ('ESCROW_LIABILITY', 'LIABILITY', 'CREDIT', '예치금 반환 의무'),
    ('FEE_REVENUE', 'REVENUE', 'CREDIT', '중개 수수료 수익');

CREATE TABLE ledger_postings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    line_no SMALLINT NOT NULL,
    service_client_id BIGINT NOT NULL,
    escrow_id BIGINT NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    debit_account VARCHAR(30) NOT NULL,
    credit_account VARCHAR(30) NOT NULL,
    amount NUMERIC(19, 0) NOT NULL,
    occurred_at TIMESTAMPTZ(6) NOT NULL,
    posted_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_postings_transaction FOREIGN KEY (transaction_id) REFERENCES escrow_transactions (id),
    CONSTRAINT fk_ledger_postings_escrow
        FOREIGN KEY (escrow_id, service_client_id) REFERENCES escrows (id, service_client_id),
    CONSTRAINT fk_ledger_postings_debit FOREIGN KEY (debit_account) REFERENCES ledger_accounts (code),
    CONSTRAINT fk_ledger_postings_credit FOREIGN KEY (credit_account) REFERENCES ledger_accounts (code),
    CONSTRAINT uq_ledger_postings_line UNIQUE (transaction_id, line_no),
    CONSTRAINT ck_ledger_postings_line_no CHECK (line_no > 0),
    CONSTRAINT ck_ledger_postings_entry_type CHECK (entry_type IN ('DEPOSIT', 'RELEASE', 'FEE', 'REFUND')),
    CONSTRAINT ck_ledger_postings_sides CHECK (debit_account <> credit_account),
    CONSTRAINT ck_ledger_postings_amount CHECK (amount > 0)
);

CREATE INDEX idx_ledger_postings_escrow ON ledger_postings (escrow_id);

COMMENT ON TABLE ledger_postings IS '복식부기 분개 | 한 행이 차변 1건과 대변 1건. 같은 금액이 양쪽에 걸려 행마다 대차가 맞음. 추가만 허용 (R-PAY-15)';
COMMENT ON COLUMN ledger_postings.id IS '분개 ID | 기록 순서';
COMMENT ON COLUMN ledger_postings.transaction_id IS '거래 ID | 분개를 만든 완료 거래';
COMMENT ON COLUMN ledger_postings.line_no IS '분개 줄 번호 | 거래 안의 순번. 거래와 함께 유일해 같은 거래를 두 번 분개하지 않음';
COMMENT ON COLUMN ledger_postings.service_client_id IS '이용 서비스 ID | 거래의 서비스';
COMMENT ON COLUMN ledger_postings.escrow_id IS '에스크로 ID | 예치금 반환 의무의 보조원장 키';
COMMENT ON COLUMN ledger_postings.entry_type IS '분개 유형 | 거래 유형과 같음. 같은 계정 조합인 지급과 환불을 구분';
COMMENT ON COLUMN ledger_postings.debit_account IS '차변 계정 | ledger_accounts.code';
COMMENT ON COLUMN ledger_postings.credit_account IS '대변 계정 | ledger_accounts.code';
COMMENT ON COLUMN ledger_postings.amount IS '분개 금액 | 원 단위 양수';
COMMENT ON COLUMN ledger_postings.occurred_at IS '발생 일시 | 거래 완료 일시';
COMMENT ON COLUMN ledger_postings.posted_at IS '기록 일시 | 분개가 원장에 들어간 시각. 이관분은 V3 적용 시각';

-- 완료 거래 한 건을 분개합니다. 이미 분개한 거래는 줄 번호 유일성으로 건너뜁니다.
-- 예치:   보관계좌 현금 증가 / 반환 의무 증가
-- 지급:   반환 의무 감소 / 보관계좌 현금 감소
-- 환불:   반환 의무 감소 / 보관계좌 현금 감소
-- 수수료: 반환 의무 감소 / 수수료 수익, 수수료계좌 현금 증가 / 보관계좌 현금 감소
CREATE FUNCTION post_ledger_entries(
    p_transaction_id BIGINT, p_service_client_id BIGINT, p_escrow_id BIGINT,
    p_type VARCHAR, p_amount NUMERIC, p_occurred_at TIMESTAMPTZ
) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO ledger_postings
        (transaction_id, line_no, service_client_id, escrow_id, entry_type,
         debit_account, credit_account, amount, occurred_at)
    SELECT p_transaction_id, line.line_no, p_service_client_id, p_escrow_id, p_type,
           line.debit_account, line.credit_account, p_amount, p_occurred_at
    FROM (VALUES
            ('DEPOSIT', 1, 'CUSTODY_CASH', 'ESCROW_LIABILITY'),
            ('RELEASE', 1, 'ESCROW_LIABILITY', 'CUSTODY_CASH'),
            ('REFUND', 1, 'ESCROW_LIABILITY', 'CUSTODY_CASH'),
            ('FEE', 1, 'ESCROW_LIABILITY', 'FEE_REVENUE'),
            ('FEE', 2, 'FEE_CASH', 'CUSTODY_CASH')
         ) AS line (entry_type, line_no, debit_account, credit_account)
    WHERE line.entry_type = p_type
    ON CONFLICT (transaction_id, line_no) DO NOTHING;
END;
$$;

-- 거래가 COMPLETED 로 들어오거나 바뀔 때만 분개합니다. 완료 뒤 다시 COMPLETED 로 갱신해도 새로 분개하지 않습니다.
-- 여기서는 거래를 거절하지 않습니다. 거래 쓰기 경로의 동작은 V2 까지와 같습니다.
CREATE FUNCTION post_completed_escrow_transaction() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'COMPLETED' AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM 'COMPLETED') THEN
        PERFORM post_ledger_entries(NEW.id, NEW.service_client_id, NEW.escrow_id,
            NEW.transaction_type, NEW.amount, COALESCE(NEW.completed_at, clock_timestamp()));
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_escrow_transactions_post_ledger
    AFTER INSERT OR UPDATE OF status ON escrow_transactions
    FOR EACH ROW EXECUTE FUNCTION post_completed_escrow_transaction();

CREATE FUNCTION prevent_ledger_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_ledger_postings_append_only
    BEFORE UPDATE OR DELETE ON ledger_postings
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TRIGGER trg_ledger_postings_no_truncate
    BEFORE TRUNCATE ON ledger_postings
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TRIGGER trg_ledger_accounts_append_only
    BEFORE UPDATE OR DELETE ON ledger_accounts
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TRIGGER trg_ledger_accounts_no_truncate
    BEFORE TRUNCATE ON ledger_accounts
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_ledger_mutation();

-- 이미 완료된 거래를 거래 순서대로 이관합니다. 트리거를 먼저 만들었으므로 이 사이에 완료된 거래도 빠지지 않습니다.
SELECT post_ledger_entries(id, service_client_id, escrow_id, transaction_type, amount, completed_at)
FROM escrow_transactions
WHERE status = 'COMPLETED'
ORDER BY id;

COMMENT ON TABLE escrow_transactions IS '에스크로 거래 | 업무 단위 자금 거래. 완료 시 ledger_postings 로 분개됨 (R-PAY-15)';
