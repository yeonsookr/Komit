-- ERD Cloud 가져오기용 MySQL 형식. 실제 PostgreSQL 생성 기준은 db/migration/V1__create_escrow_schema.sql이다.
-- PostgreSQL TIMESTAMPTZ는 ERD 표시를 위해 DATETIME(6)으로 표현했다.
-- 부분 유니크 인덱스와 감사 테이블 변경 방지 트리거는 MySQL ERD 형식으로 표현하지 않았다.
-- 운영 DB에 이 파일을 실행하지 않는다.

CREATE TABLE service_clients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '이용 서비스 ID | 서버에서 생성한 식별자',
    name VARCHAR(100) NOT NULL COMMENT '이용 서비스명 | 에스크로 이용 서비스 이름',
    api_key_hash VARCHAR(255) NOT NULL COMMENT 'API 키 해시 | 충분히 무작위인 인증키의 해시. 원문 저장 금지',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '서비스 상태 | ACTIVE / SUSPENDED / CLOSED',
    one_won_auth_message VARCHAR(100) COMMENT '1원 인증 문구 | 입금자명에 표시할 문구',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시 | 절대 시점',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시 | 애플리케이션이 갱신',
    CONSTRAINT uq_service_clients_api_key_hash UNIQUE (api_key_hash),
    CONSTRAINT ck_service_clients_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='이용 서비스 | 에스크로 API를 호출하는 서비스';

CREATE TABLE member_account_provisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '발급 기록 ID | 서버에서 생성한 식별자',
    service_client_id BIGINT NOT NULL COMMENT '이용 서비스 ID | 발급을 요청한 서비스',
    user_id VARCHAR(40) NOT NULL COMMENT '금융망 사용자 ID | SSAFY 회원 등록용 이메일 형식 ID',
    account_type_unique_no VARCHAR(20) NOT NULL COMMENT '계좌 상품 고유번호 | 에스크로 설정의 SSAFY 입출금 상품 (R-ACC-10)',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '멱등 키 | 같은 발급 업무의 재요청 식별값',
    request_fingerprint VARCHAR(64) NOT NULL COMMENT '요청 지문 | 사용자 ID를 포함한 최초 발급 요청의 SHA-256',
    provision_step VARCHAR(20) NOT NULL DEFAULT 'USER_REGISTER' COMMENT '발급 단계 | USER_REGISTER / ACCOUNT_OPEN / SEED_DEPOSIT / COMPLETED',
    provision_status VARCHAR(12) NOT NULL DEFAULT 'PENDING' COMMENT '발급 상태 | PENDING / UNKNOWN / COMPLETED',
    step_requested_at DATETIME(6) COMMENT '단계 요청 일시 | 외부 호출 직전 기록. 단계 변경 시 비움',
    user_key_encrypted TEXT COMMENT '사용자 키 암호문 | 복구용 암호화 봉투. 평문 로그 금지',
    user_key_lookup_hash VARCHAR(64) COMMENT '사용자 키 조회 해시 | 요청 Header.userKey의 서비스별 HMAC-SHA-256. 키 버전을 운영 설정에서 관리',
    bank_code VARCHAR(3) COMMENT '은행코드 | 발급 계좌의 은행코드',
    account_no_encrypted TEXT COMMENT '계좌번호 암호문 | 복구용 암호화 봉투. 평문 로그 금지',
    seed_amount BIGINT NOT NULL COMMENT '데모 입금액 | 회원 계좌에 한 번 입금한 금액 (R-PAY-10)',
    seed_status VARCHAR(12) NOT NULL DEFAULT 'PENDING' COMMENT '데모 입금 상태 | PENDING / UNKNOWN / COMPLETED',
    seed_request_reference VARCHAR(20) COMMENT '데모 입금 요청 식별자 | 외부 호출 전에 저장한 기관 거래번호',
    seed_transaction_no VARCHAR(40) COMMENT '데모 입금 거래번호 | SSAFY 거래 응답 번호',
    seeded_at DATETIME(6) COMMENT '데모 입금 완료 일시 | 성공 확인 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시 | 발급 업무 시작 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시 | 애플리케이션이 갱신',
    CONSTRAINT fk_member_account_provisions_service_client
        FOREIGN KEY (service_client_id) REFERENCES service_clients (id),
    CONSTRAINT uq_member_account_provisions_id_service UNIQUE (id, service_client_id),
    CONSTRAINT uq_member_account_provisions_service_user UNIQUE (service_client_id, user_id),
    CONSTRAINT uq_member_account_provisions_service_idempotency UNIQUE (service_client_id, idempotency_key),
    CONSTRAINT uq_member_account_provisions_service_user_key_hash UNIQUE (service_client_id, user_key_lookup_hash),
    CONSTRAINT uq_member_account_provisions_seed_request_reference UNIQUE (seed_request_reference),
    CONSTRAINT ck_member_account_provisions_fingerprint CHECK (request_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_member_account_provisions_step
        CHECK (provision_step IN ('USER_REGISTER', 'ACCOUNT_OPEN', 'SEED_DEPOSIT', 'COMPLETED')),
    CONSTRAINT ck_member_account_provisions_status
        CHECK (provision_status IN ('PENDING', 'UNKNOWN', 'COMPLETED')),
    CONSTRAINT ck_member_account_provisions_unknown_request
        CHECK (provision_status <> 'UNKNOWN' OR step_requested_at IS NOT NULL),
    CONSTRAINT ck_member_account_provisions_user_key_hash
        CHECK (user_key_lookup_hash IS NULL OR user_key_lookup_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_member_account_provisions_user_key_pair
        CHECK ((user_key_encrypted IS NULL) = (user_key_lookup_hash IS NULL)),
    CONSTRAINT ck_member_account_provisions_bank_code
        CHECK (bank_code IS NULL OR bank_code REGEXP '^[0-9]{3}$'),
    CONSTRAINT ck_member_account_provisions_seed_amount CHECK (seed_amount > 0),
    CONSTRAINT ck_member_account_provisions_seed_status
        CHECK (seed_status IN ('PENDING', 'UNKNOWN', 'COMPLETED')),
    CONSTRAINT ck_member_account_provisions_seed_completion CHECK (
        (seed_status = 'COMPLETED' AND seed_request_reference IS NOT NULL
            AND seed_transaction_no IS NOT NULL AND seeded_at IS NOT NULL)
        OR (seed_status <> 'COMPLETED' AND seeded_at IS NULL)
    ),
    CONSTRAINT ck_member_account_provisions_seed_unknown_reference
        CHECK (seed_status <> 'UNKNOWN' OR seed_request_reference IS NOT NULL),
    CONSTRAINT ck_member_account_provisions_progress CHECK (
        (provision_step = 'USER_REGISTER'
            AND provision_status IN ('PENDING', 'UNKNOWN')
            AND user_key_encrypted IS NULL AND bank_code IS NULL
            AND account_no_encrypted IS NULL AND seed_status = 'PENDING')
        OR (provision_step = 'ACCOUNT_OPEN'
            AND provision_status IN ('PENDING', 'UNKNOWN')
            AND user_key_encrypted IS NOT NULL AND bank_code IS NULL
            AND account_no_encrypted IS NULL AND seed_status = 'PENDING')
        OR (provision_step = 'SEED_DEPOSIT'
            AND provision_status IN ('PENDING', 'UNKNOWN')
            AND user_key_encrypted IS NOT NULL AND bank_code IS NOT NULL
            AND account_no_encrypted IS NOT NULL AND seed_status = provision_status)
        OR (provision_step = 'COMPLETED'
            AND provision_status = 'COMPLETED' AND step_requested_at IS NULL
            AND user_key_encrypted IS NOT NULL AND bank_code IS NOT NULL
            AND account_no_encrypted IS NOT NULL AND seed_status = 'COMPLETED')
    )

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회원 계좌 발급 기록 | SSAFY 사용자·계좌 발급 및 1회 데모 입금의 복구 기록 (R-ACC-09, R-PAY-10)';

CREATE TABLE account_auth_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '인증 요청 ID | 내부 복구 기록 식별자',
    service_client_id BIGINT NOT NULL COMMENT '이용 서비스 ID | 인증 요청 소유 서비스',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '멱등 키 | 동일 인증 요청의 재전송 식별자',
    request_fingerprint VARCHAR(64) NOT NULL COMMENT '요청 지문 | 사용자 키와 계좌번호를 포함한 정규화 업무 요청의 HMAC-SHA-256',
    user_key_lookup_hash VARCHAR(64) NOT NULL COMMENT '사용자 키 조회 해시 | 서비스별 HMAC-SHA-256',
    user_key_encrypted TEXT NOT NULL COMMENT '사용자 키 암호문 | 장애 복구용 암호화 봉투',
    account_no_encrypted TEXT NOT NULL COMMENT '인증 계좌 암호문 | 요청 대조와 복구용 암호화 봉투',
    auth_text VARCHAR(100) NOT NULL COMMENT '인증 문구 | 요청 당시 서비스 설정을 고정',
    request_reference VARCHAR(100) NOT NULL UNIQUE COMMENT '금융망 요청 식별자 | 실행 전 저장하고 재송금 판단 근거로 사용',
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' COMMENT '호출 상태 | REQUESTED 준비 / DISPATCHING 실행권 획득 / SUCCEEDED 성공 / FAILED 확정 실패 / UNKNOWN 결과 불명',
    dispatched_by VARCHAR(100) COMMENT '실행 인스턴스 | 호출 실행권을 획득한 인스턴스 식별자',
    dispatched_at DATETIME(6) COMMENT '실행권 획득 일시 | 실제 금융망 도달을 보장하지 않음',
    transaction_unique_no VARCHAR(100) COMMENT '인증 거래번호 | 금융망 성공 결과. 인증 확인 요청과 연결',
    expires_at DATETIME(6) COMMENT '인증 만료 일시 | R-ACC-02 적용. 재요청으로 연장하지 않음',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시 | 최초 요청 접수 시각',
    FOREIGN KEY (service_client_id) REFERENCES service_clients (id),
    CONSTRAINT uq_account_auth_requests_key UNIQUE (service_client_id, idempotency_key),
    CONSTRAINT uq_account_auth_requests_transaction UNIQUE (service_client_id, transaction_unique_no),
    CONSTRAINT ck_account_auth_requests_key CHECK (length(trim(idempotency_key)) > 0),
    CONSTRAINT ck_account_auth_requests_fingerprint CHECK (request_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_account_auth_requests_user_hash CHECK (user_key_lookup_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_account_auth_requests_status CHECK (status IN ('REQUESTED','DISPATCHING','SUCCEEDED','FAILED','UNKNOWN')),
    CONSTRAINT ck_account_auth_requests_dispatch CHECK (
        (status='REQUESTED' AND dispatched_by IS NULL AND dispatched_at IS NULL)
        OR (status<>'REQUESTED' AND dispatched_by IS NOT NULL
            AND length(trim(dispatched_by))>0 AND dispatched_at IS NOT NULL)
    ),
    CONSTRAINT ck_account_auth_requests_result CHECK (
        (status='SUCCEEDED' AND transaction_unique_no IS NOT NULL AND expires_at IS NOT NULL)
        OR (status<>'SUCCEEDED' AND transaction_unique_no IS NULL AND expires_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='계좌 인증 요청 | 1원 송금의 멱등 처리와 응답 유실 복구 기록';

CREATE TABLE escrows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '에스크로 ID | 서버에서 생성한 식별자',
    service_client_id BIGINT NOT NULL COMMENT '이용 서비스 ID | 계약을 소유한 서비스',
    payer_provision_id BIGINT NOT NULL COMMENT '의뢰인 발급 기록 ID | 해당 서비스가 회원가입 때 발급한 출금 계좌. 요청 계좌와 대조 (R-PAY-09)',
    external_contract_id VARCHAR(100) NOT NULL COMMENT '외부 계약 ID | 서비스 안에서 유일한 계약 식별값',
    contract_type VARCHAR(30) NOT NULL COMMENT '계약 유형 | 서비스가 정의한 구분',
    expected_amount NUMERIC(19, 0) NOT NULL COMMENT '예치 예정 금액 | 생성 후 불변인 계약금 전액',
    fee_rate_bps SMALLINT NOT NULL DEFAULT 100 COMMENT '중개 수수료율 | 생성 시 고정. 1bp=0.01% (R-PAY-07)',
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED' COMMENT '에스크로 상태 | CREATED / DEPOSIT_PENDING / ACTIVE / COMPLETED / REFUNDED / CANCELLED. ACTIVE는 예치 검증·정산·로컬 기록 후',
    closed_at DATETIME(6) COMMENT '종료 일시 | 지급·수수료·환불 완료와 잔액 0 확정 시각 또는 예치 전 취소 시각',
    cancelled_at DATETIME(6) COMMENT '취소 일시 | 예치 전 취소 시각',
    hold_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '동결 상태 | NONE / HELD / RELEASED',
    external_report_id VARCHAR(100) COMMENT '외부 신고 ID | 현재 또는 최근 동결의 Komit 신고 식별값',
    held_at DATETIME(6) COMMENT '동결 일시 | 자금 동결 시각',
    released_at DATETIME(6) COMMENT '동결 해제 일시 | 동결 해제 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시 | 절대 시점',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시 | 애플리케이션이 갱신',
    CONSTRAINT uq_escrows_id_service UNIQUE (id, service_client_id),
    CONSTRAINT uq_escrows_service_client_external_contract UNIQUE (service_client_id, external_contract_id),
    CONSTRAINT fk_escrows_service_client FOREIGN KEY (service_client_id) REFERENCES service_clients (id),
    CONSTRAINT fk_escrows_payer_provision FOREIGN KEY (payer_provision_id, service_client_id)
        REFERENCES member_account_provisions (id, service_client_id),
    CONSTRAINT ck_escrows_expected_amount CHECK (expected_amount > 0),
    CONSTRAINT ck_escrows_fee_rate_bps CHECK (fee_rate_bps BETWEEN 0 AND 9999),
    CONSTRAINT ck_escrows_status
        CHECK (status IN ('CREATED', 'DEPOSIT_PENDING', 'ACTIVE', 'COMPLETED', 'REFUNDED', 'CANCELLED')),
    CONSTRAINT ck_escrows_hold_status CHECK (hold_status IN ('NONE', 'HELD', 'RELEASED')),
    CONSTRAINT ck_escrows_closed_at CHECK (
        (status IN ('COMPLETED', 'REFUNDED', 'CANCELLED')) = (closed_at IS NOT NULL)
    ),
    CONSTRAINT ck_escrows_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT ck_escrows_hold_details CHECK (
        (hold_status='NONE' AND external_report_id IS NULL AND held_at IS NULL AND released_at IS NULL)
        OR (hold_status='HELD' AND external_report_id IS NOT NULL
            AND length(trim(external_report_id))>0 AND held_at IS NOT NULL AND released_at IS NULL)
        OR (hold_status='RELEASED' AND external_report_id IS NOT NULL
            AND length(trim(external_report_id))>0 AND held_at IS NOT NULL AND released_at IS NOT NULL
            AND released_at >= held_at)
    )

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='에스크로 | 계약 1건의 예치와 최종 정산';

-- 취소·동결·해제는 계약 행 잠금 아래 수행한다. 예치 송금 시작도 같은 잠금을 사용한다.
-- 취소는 진행 중·결과 불명 자금 이동이 없고 미입금이 확인된 경우에만 허용한다.
-- 신고 해제는 현재 신고 식별자와 대조하며, 같은 멱등키는 당시 성공 결과를 반환한다.
CREATE TABLE escrow_state_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '상태 명령 ID | 서버에서 생성한 식별자',
    service_client_id BIGINT NOT NULL COMMENT '이용 서비스 ID | 명령을 보낸 서비스',
    escrow_id BIGINT NOT NULL COMMENT '에스크로 ID | 해당 서비스가 소유한 명령 대상',
    api_name VARCHAR(30) NOT NULL COMMENT 'API 이름 | cancelEscrow / holdEscrow / unholdEscrow',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '멱등 키 | 같은 API와 서비스 안에서 재요청을 식별',
    request_fingerprint VARCHAR(64) NOT NULL COMMENT '요청 지문 | 변하는 전송 Header를 제외한 업무 본문의 SHA-256 hex',
    external_report_id VARCHAR(100) COMMENT '외부 신고 ID | 동결·해제 명령의 신고 식별값. 취소는 NULL',
    result_state VARCHAR(20) NOT NULL COMMENT '성공 당시 결과 상태 | CANCELLED / HELD / RELEASED. 재요청 응답 재구성에 사용',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '명령 확정 일시 | 계약 상태 변경과 같은 트랜잭션에서 기록',
    CONSTRAINT fk_escrow_state_commands_escrow
        FOREIGN KEY (escrow_id, service_client_id) REFERENCES escrows (id, service_client_id),
    CONSTRAINT uq_escrow_state_commands_idempotency
        UNIQUE (service_client_id, api_name, idempotency_key),
    CONSTRAINT ck_escrow_state_commands_idempotency_key CHECK (length(trim(idempotency_key)) > 0),
    CONSTRAINT ck_escrow_state_commands_request_fingerprint
        CHECK (request_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_escrow_state_commands_result CHECK (
        (api_name = 'cancelEscrow' AND external_report_id IS NULL AND result_state = 'CANCELLED')
        OR (api_name = 'holdEscrow' AND external_report_id IS NOT NULL
            AND length(trim(external_report_id)) > 0 AND result_state = 'HELD')
        OR (api_name = 'unholdEscrow' AND external_report_id IS NOT NULL
            AND length(trim(external_report_id)) > 0 AND result_state = 'RELEASED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='에스크로 상태 명령 | 취소·동결·해제의 성공 결과와 멱등 키를 기록';

CREATE TABLE settlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '정산 ID | 내부 정산 식별자',
    escrow_id BIGINT NOT NULL COMMENT '에스크로 ID | 정산 대상 계약',
    external_reference_id VARCHAR(200) NOT NULL COMMENT '외부 정산 참조 ID | SETTLEMENT:정산ID. 재요청에도 동일',
    approved_amount NUMERIC(19, 0) COMMENT '작업 승인 총액 | 지급 요청의 수수료 공제 전 금액. 요청 전 NULL',
    refund_amount NUMERIC(19, 0) COMMENT '환불 승인 금액 | 환불 요청 금액. 요청 전 NULL',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '정산 업무 상태 | OPEN / PROCESSING / UNKNOWN / CLOSED / FAILED. 지급 API 응답 상태는 자식 거래로 계산',
    closed_at DATETIME(6) COMMENT '정산 종료 일시 | 모든 필요한 이체가 확인되고 계약 잔액이 0인 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시 | 최초 지급 또는 환불 요청 기록 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시 | 애플리케이션이 갱신',
    CONSTRAINT fk_settlements_escrow FOREIGN KEY (escrow_id) REFERENCES escrows (id),
    CONSTRAINT uq_settlements_escrow_reference UNIQUE (escrow_id, external_reference_id),
    CONSTRAINT uq_settlements_id_escrow_reference UNIQUE (id, escrow_id, external_reference_id),
    CONSTRAINT ck_settlements_reference CHECK (external_reference_id LIKE 'SETTLEMENT:%' AND length(external_reference_id)>11),
    CONSTRAINT ck_settlements_amount CHECK (
        (approved_amount IS NOT NULL OR refund_amount IS NOT NULL)
        AND (approved_amount IS NULL OR approved_amount > 0)
        AND (refund_amount IS NULL OR refund_amount > 0)
    ),
    CONSTRAINT ck_settlements_status CHECK (status IN ('OPEN', 'PROCESSING', 'UNKNOWN', 'CLOSED', 'FAILED')),
    CONSTRAINT ck_settlements_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='최종 정산 | 같은 계약의 작업자 지급·수수료·환불을 묶는 업무 기록';

CREATE TABLE escrow_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '거래 ID | 논리 금융 거래 식별자',
    service_client_id BIGINT NOT NULL COMMENT '이용 서비스 ID | 멱등키 범위와 계약 소유 서비스',
    escrow_id BIGINT NOT NULL COMMENT '에스크로 ID | 대상 계약',
    settlement_id BIGINT COMMENT '정산 ID | RELEASE / FEE / REFUND가 공유하는 정산 업무. DEPOSIT는 NULL',
    transaction_type VARCHAR(20) NOT NULL COMMENT '거래 유형 | DEPOSIT / RELEASE 순지급 / FEE 수수료 / REFUND',
    amount NUMERIC(19, 0) NOT NULL COMMENT '거래 금액 | 원 단위 양수. RELEASE는 순지급, FEE는 수수료, REFUND는 환불액',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '거래 상태 | PENDING / PROCESSING / UNKNOWN / COMPLETED / FAILED',
    external_reference_id VARCHAR(200) NOT NULL COMMENT '외부 업무 참조 ID | 예치는 INITIAL, 최종 정산 거래는 부모 정산의 참조 ID',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '멱등 키 | 같은 논리 거래의 재요청에 유지. 분리된 지급·수수료·환불 거래는 각각 고유 키',
    request_fingerprint VARCHAR(64) NOT NULL COMMENT '요청 지문 | 정규화된 요청의 SHA-256 hex',
    deposit_step VARCHAR(30) COMMENT '예치 처리 단계 | VA_CREATING / VA_CREATED / TRANSFER_SENDING / TRANSFERRED / VERIFIED / SETTLED / VA_CLOSED',
    depositor_name_encrypted TEXT COMMENT '입금자명 암호문 | VA 생성과 예치 이체에 동일하게 사용할 요청 입금자명의 암호화 봉투',
    virtual_account_id VARCHAR(100) COMMENT '가상계좌 ID | 금융망이 반환한 식별자',
    virtual_account_no VARCHAR(100) COMMENT '가상계좌 번호 | 표시 목적 외 접근 금지. 평문 로그 금지',
    deposit_verification_status VARCHAR(20) COMMENT '예치 검증 상태 | PENDING / MATCHED / MISMATCHED / UNKNOWN',
    verified_deposit_amount NUMERIC(19, 0) COMMENT '검증된 입금액 | 금융망 조회로 확인한 금액',
    depositor_name_matched BOOLEAN COMMENT '입금자명 일치 여부 | 거래 요약과 VA 지정 이름의 일치 결과. 실제 명의 증명은 아님',
    source_bank_code VARCHAR(10) COMMENT '출금 은행코드 | 거래 당시 출금 계좌의 은행코드',
    source_account_encrypted TEXT COMMENT '출금 계좌 암호문 | 거래 당시 계좌번호의 암호화 봉투',
    recipient_bank_code VARCHAR(10) COMMENT '수취 은행코드 | 지급·수수료·환불 수취 계좌',
    recipient_account_encrypted TEXT COMMENT '수취 계좌 암호문 | 거래 당시 계좌번호의 암호화 봉투',
    recipient_name VARCHAR(100) COMMENT '수취 예금주명 | 금융망 예금주 조회 후 이체',
    financial_transaction_id VARCHAR(100) COMMENT '금융망 거래 ID | 최종 자금 이동 결과 식별자',
    virtual_account_closed_at DATETIME(6) COMMENT '가상계좌 해지 일시 | 보관계좌 정산 후 별도 처리',
    failure_code VARCHAR(30) COMMENT '실패 코드 | 자금 유출이 없고 최종 실패가 확인된 경우',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시 | 외부 호출 전 기록 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '수정 일시 | 애플리케이션이 갱신',
    completed_at DATETIME(6) COMMENT '완료 일시 | 자금 이동과 로컬 기록 모두 확정된 시각',
    CONSTRAINT fk_escrow_transactions_escrow FOREIGN KEY (escrow_id, service_client_id) REFERENCES escrows (id, service_client_id),
    CONSTRAINT fk_escrow_transactions_settlement
        FOREIGN KEY (settlement_id, escrow_id, external_reference_id)
        REFERENCES settlements (id, escrow_id, external_reference_id),
    CONSTRAINT uq_escrow_transactions_idempotency_key UNIQUE (service_client_id, transaction_type, idempotency_key),
    CONSTRAINT uq_escrow_transactions_business_reference
        UNIQUE (escrow_id, external_reference_id, transaction_type),
    CONSTRAINT ck_escrow_transactions_type
        CHECK (transaction_type IN ('DEPOSIT', 'RELEASE', 'FEE', 'REFUND')),
    CONSTRAINT ck_escrow_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_escrow_transactions_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'UNKNOWN', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_escrow_transactions_completion
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_escrow_transactions_reference CHECK (
        (transaction_type = 'DEPOSIT' AND settlement_id IS NULL AND external_reference_id = 'INITIAL')
        OR (transaction_type IN ('RELEASE', 'FEE', 'REFUND')
            AND settlement_id IS NOT NULL AND external_reference_id LIKE 'SETTLEMENT:%')
    ),
    CONSTRAINT ck_escrow_transactions_deposit_fields CHECK (
        (transaction_type = 'DEPOSIT') OR
        (deposit_step IS NULL AND depositor_name_encrypted IS NULL
            AND virtual_account_id IS NULL AND virtual_account_no IS NULL
            AND deposit_verification_status IS NULL AND verified_deposit_amount IS NULL
            AND depositor_name_matched IS NULL AND virtual_account_closed_at IS NULL)
    ),
    CONSTRAINT ck_escrow_transactions_depositor_name CHECK (
        transaction_type <> 'DEPOSIT' OR depositor_name_encrypted IS NOT NULL
    ),
    CONSTRAINT ck_escrow_transactions_deposit_verification
        CHECK (deposit_verification_status IS NULL OR
            deposit_verification_status IN ('PENDING', 'MATCHED', 'MISMATCHED', 'UNKNOWN')),
    CONSTRAINT ck_escrow_transactions_verified_amount
        CHECK (verified_deposit_amount IS NULL OR verified_deposit_amount >= 0),
    CONSTRAINT ck_escrow_transactions_completed_deposit CHECK (
        transaction_type <> 'DEPOSIT' OR status <> 'COMPLETED'
        OR deposit_verification_status <=> 'MATCHED'
    ),
    CONSTRAINT ck_escrow_transactions_request_fingerprint
        CHECK (request_fingerprint REGEXP '^[0-9a-f]{64}$')

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='에스크로 거래 | 업무 단위 자금 거래. 복식부기 전표가 아닌 대사 집계 대상';

CREATE TABLE financial_operation_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '호출 시도 ID | 서버에서 생성한 식별자',
    transaction_id BIGINT NOT NULL COMMENT '거래 ID | 논리 금융 거래',
    step VARCHAR(30) NOT NULL COMMENT '호출 단계 | 가상계좌·이체·예금주 확인·결과 조회의 단계',
    attempt_no INT NOT NULL COMMENT '시도 번호 | 같은 거래와 단계 안에서 1부터 증가',
    request_reference VARCHAR(100) NOT NULL COMMENT '요청 식별자 | 외부 호출 전에 저장. 금융망 요구 형식 사용',
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' COMMENT '호출 상태 | REQUESTED 준비 / DISPATCHING 실행권 획득 / SUCCEEDED 성공 / FAILED 확정 실패 / UNKNOWN 결과 불명',
    dispatched_by VARCHAR(100) COMMENT '실행 인스턴스 | 금융망 호출 실행권을 획득한 불변 인스턴스 식별자',
    dispatched_at DATETIME(6) COMMENT '실행권 획득 일시 | 금융망 도달을 증명하지 않으며 장애 복구 판단에 사용',
    external_transaction_id VARCHAR(100) COMMENT '외부 거래 ID | 해당 단계의 금융망 결과 식별자',
    response_code VARCHAR(50) COMMENT '응답 코드 | 금융망 응답 코드',
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '요청 기록 일시 | 실제 전송 여부를 증명하지 않는 준비 시각',
    responded_at DATETIME(6) COMMENT '응답 수신 일시 | 직접 응답 유실 시 NULL을 유지. 조회로 결과 확정 시 감사 기록으로 근거 연결',
    CONSTRAINT fk_financial_operation_attempts_transaction
        FOREIGN KEY (transaction_id) REFERENCES escrow_transactions (id),
    CONSTRAINT uq_financial_operation_attempts_attempt UNIQUE (transaction_id, step, attempt_no),
    CONSTRAINT uq_financial_operation_attempts_request_reference UNIQUE (request_reference),
    CONSTRAINT ck_financial_operation_attempts_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_financial_operation_attempts_step CHECK (step IN (
        'VA_CREATE', 'DEPOSIT_TRANSFER', 'DEPOSIT_VERIFY', 'VA_SETTLE', 'VA_CLOSE',
        'HOLDER_VERIFY', 'PAYOUT_TRANSFER', 'FEE_TRANSFER', 'REFUND_TRANSFER', 'RESULT_QUERY'
    )),
    CONSTRAINT ck_financial_operation_attempts_status
        CHECK (status IN ('REQUESTED', 'DISPATCHING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT ck_financial_operation_attempts_dispatch CHECK (
        (status = 'REQUESTED' AND dispatched_by IS NULL AND dispatched_at IS NULL)
        OR (status IN ('DISPATCHING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')
            AND dispatched_by IS NOT NULL AND length(trim(dispatched_by)) > 0
            AND dispatched_at IS NOT NULL)
    ),
    CONSTRAINT ck_financial_operation_attempts_response CHECK (
        (status IN ('REQUESTED', 'DISPATCHING') AND responded_at IS NULL)
        OR status IN ('SUCCEEDED', 'FAILED', 'UNKNOWN')
    ),
    CONSTRAINT ck_financial_operation_attempts_response_time
        CHECK (responded_at IS NULL OR responded_at >= dispatched_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='금융망 호출 시도 | 거래의 외부 API 호출과 결과 조회 기록';

CREATE TABLE operation_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '감사 사건 ID | 서버에서 생성한 식별자',
    escrow_id BIGINT COMMENT '에스크로 ID | 관련 계약',
    transaction_id BIGINT COMMENT '거래 ID | 관련 자금 거래',
    member_provision_id BIGINT COMMENT '발급 기록 ID | 관련 회원 계좌 발급',
    account_auth_request_id BIGINT COMMENT '인증 요청 ID | 1원 인증 결과 불명 해소의 대상',
    actor_type VARCHAR(20) NOT NULL COMMENT '처리 주체 유형 | SYSTEM / OPERATOR',
    actor_id VARCHAR(100) NOT NULL COMMENT '처리 주체 ID | 작업자 또는 시스템 작업 식별자',
    action VARCHAR(100) NOT NULL COMMENT '조치 유형 | 조회 확인·결과 확정·수동 복구 등',
    reason TEXT NOT NULL COMMENT '조치 근거 | 민감정보 원문을 제외한 판단 사유',
    evidence_reference VARCHAR(200) COMMENT '증빙 참조 | 금융망 거래·운영 티켓 등 추적 가능한 참조값',
    previous_status VARCHAR(30) COMMENT '이전 상태 | 조치 전 상태',
    resulting_status VARCHAR(30) COMMENT '결과 상태 | 조치 후 상태',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '기록 일시 | 절대 시점',
    CONSTRAINT fk_operation_audit_events_escrow FOREIGN KEY (escrow_id) REFERENCES escrows (id),
    CONSTRAINT fk_operation_audit_events_transaction
        FOREIGN KEY (transaction_id) REFERENCES escrow_transactions (id),
    CONSTRAINT fk_operation_audit_events_member_provision
        FOREIGN KEY (member_provision_id) REFERENCES member_account_provisions (id),
    FOREIGN KEY (account_auth_request_id) REFERENCES account_auth_requests (id),
    CONSTRAINT ck_operation_audit_events_actor_type CHECK (actor_type IN ('SYSTEM', 'OPERATOR')),
    CONSTRAINT ck_operation_audit_events_target
        CHECK (escrow_id IS NOT NULL OR transaction_id IS NOT NULL OR member_provision_id IS NOT NULL OR account_auth_request_id IS NOT NULL),
    CONSTRAINT ck_operation_audit_events_actor_id CHECK (length(trim(actor_id)) > 0),
    CONSTRAINT ck_operation_audit_events_action CHECK (length(trim(action)) > 0),
    CONSTRAINT ck_operation_audit_events_reason CHECK (length(trim(reason)) > 0)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='운영 감사 사건 | 결과 불명 해소·수동 조치의 근거와 주체를 추가 전용으로 기록';

CREATE INDEX idx_escrow_transactions_escrow_status
    ON escrow_transactions (escrow_id, status);

CREATE INDEX idx_escrow_transactions_settlement_status
    ON escrow_transactions (settlement_id, status);

CREATE INDEX idx_financial_operation_attempts_status_requested_at
    ON financial_operation_attempts (status, requested_at);

CREATE INDEX idx_financial_operation_attempts_status_dispatched_at
    ON financial_operation_attempts (status, dispatched_at);

CREATE INDEX idx_operation_audit_events_escrow_created_at
    ON operation_audit_events (escrow_id, created_at);

CREATE INDEX idx_operation_audit_events_transaction_created_at
    ON operation_audit_events (transaction_id, created_at);

CREATE INDEX idx_escrows_status_hold_status ON escrows (status, hold_status);
CREATE INDEX idx_escrow_state_commands_escrow_created_at ON escrow_state_commands (escrow_id, created_at);
