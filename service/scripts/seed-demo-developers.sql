-- 발표 시연용 개발자 10명과 각 개발자의 리뷰 2건. Service PostgreSQL의 writer(primary)에만 수동 실행한다.
-- 실행: psql -X -v ON_ERROR_STOP=1 -d service -f seed-demo-developers.sql
-- 기존 회원과 충돌하지 않도록 예약한 kakao_id와 example.invalid 이메일로 식별한다.
-- 같은 파일을 다시 실행하면 기존 시연 계정과 리뷰는 중복 생성하지 않고 평점 집계만 다시 맞춘다.
-- 이 계정은 카카오 로그인이 불가능하다. 상담 응답 시연에는 실제 시험 계정을 사용한다.
-- R-REV-02의 계약당 리뷰 하나와 완료 계약 연결은 맞추되, 아래 거래는 실제 유료 거래가 아니다.
-- 이 거래·서명·리뷰는 발표 화면용 합성 데이터이며 실제 결제/에스크로 이력이 없다.
-- R-DEV-01: 증빙을 확인하지 않은 경력을 인증 완료로 표시하지 않는다.

BEGIN;
SET LOCAL TIME ZONE 'UTC';
SELECT pg_advisory_xact_lock(282, 1);

CREATE TEMP TABLE demo_developer_seed (
    seed_no integer PRIMARY KEY,
    kakao_id bigint NOT NULL UNIQUE,
    name varchar(25) NOT NULL,
    intro varchar(100) NOT NULL,
    description varchar(1000) NOT NULL,
    field_code_id integer NOT NULL,
    years_of_experience numeric(3, 1) NOT NULL,
    active_from time NOT NULL,
    active_to time NOT NULL,
    tech_code_ids integer[] NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_developer_seed VALUES
    (1, 990028200001, '김도윤', '로그인과 회원가입 흐름을 안정적으로 고칩니다.',
     '기존 인증 흐름을 살펴보고 로그인 실패, 세션 만료, 권한 오류를 재현 가능한 문제로 나눠 해결합니다. 사용자가 겪는 증상과 서버 기록을 함께 확인해 수정 범위를 설명합니다.',
     44, 6.5, '10:00', '19:00', ARRAY[21, 23, 24, 26, 32]),
    (2, 990028200002, '이서연', '엉킨 데이터 구조와 느린 조회를 정리합니다.',
     '서비스를 운영하며 쌓인 데이터의 중복과 누락을 점검합니다. 테이블 구조, 이관 절차, 조회 성능을 함께 고려해 운영 중에도 확인할 수 있는 변경 계획을 제안합니다.',
     42, 7.0, '09:00', '18:00', ARRAY[22, 28, 29, 32, 33]),
    (3, 990028200003, '박지훈', '배포 뒤에만 발생하는 오류를 추적합니다.',
     '로컬에서는 동작하지만 서버에서 실패하는 문제를 주로 맡습니다. 환경 변수와 빌드 설정, 컨테이너 로그를 확인하고 재배포 후에도 같은 문제가 반복되지 않도록 정리합니다.',
     41, 8.0, '11:00', '20:00', ARRAY[21, 26, 32, 35, 36]),
    (4, 990028200004, '최유진', '버튼과 화면 흐름의 작은 오류를 꼼꼼히 잡습니다.',
     '예약, 폼 제출, 반응형 화면에서 사용자가 실제로 겪는 오류를 재현해 고칩니다. 수정 전후 동작과 예외 상황을 함께 확인해 인수 테스트가 가능하도록 전달합니다.',
     47, 5.5, '10:00', '18:00', ARRAY[21, 23, 24, 34]),
    (5, 990028200005, '정민재', '결제 연동과 주문 상태를 안전하게 연결합니다.',
     '결제 요청부터 결과 확인, 취소와 재시도까지 상태가 어긋나지 않도록 API를 설계합니다. 중복 요청과 실패 응답을 먼저 점검하고 거래 기록을 확인할 수 있게 만듭니다.',
     48, 9.0, '09:30', '18:30', ARRAY[30, 31, 32, 35]),
    (6, 990028200006, '한지우', '도메인 연결과 HTTPS 설정을 도와드립니다.',
     '도메인, DNS, 인증서, 프록시 설정을 함께 확인합니다. 접속 주소가 바뀌면서 생기는 로그인·API 오류도 살펴보고 운영자가 갱신 방법을 이해할 수 있게 문서로 남깁니다.',
     43, 5.0, '13:00', '21:00', ARRAY[21, 22, 23, 35]),
    (7, 990028200007, '윤태호', '알림이 빠지거나 중복되는 문제를 해결합니다.',
     '이메일과 앱 알림의 발송 조건, 재시도, 중복 방지 흐름을 다룹니다. 예약이나 주문처럼 중요한 이벤트가 실제 수신까지 이어지는지 확인할 수 있는 로그도 정리합니다.',
     45, 5.5, '10:00', '19:00', ARRAY[25, 26, 27, 33, 34]),
    (8, 990028200008, '서하린', '클라우드 비용과 느린 API를 함께 살핍니다.',
     '트래픽에 비해 과한 서버 자원, 불필요한 요청과 느린 데이터 조회를 찾아냅니다. 비용을 줄이는 변경이 서비스 성능에 어떤 영향을 주는지도 수치로 확인합니다.',
     46, 7.5, '09:00', '17:30', ARRAY[21, 26, 32, 35, 36]),
    (9, 990028200009, '오세진', 'Supabase와 PostgreSQL 데이터를 정리합니다.',
     '빠르게 만든 서비스에서 권한 정책과 데이터 모델이 맞지 않아 생긴 문제를 해결합니다. 데이터 손실 없이 구조를 바꾸고 간단한 운영 쿼리와 확인 절차를 함께 전달합니다.',
     42, 3.5, '11:00', '19:00', ARRAY[22, 28, 29, 32]),
    (10, 990028200010, '배수아', 'Next.js 화면과 API 연결 오류를 고칩니다.',
     '페이지 이동, 데이터 로딩, 폼 제출 과정에서 발생하는 오류를 재현합니다. 화면에 보이는 증상과 API 응답을 같이 확인하고 모바일 화면까지 점검합니다.',
     47, 8.0, '10:00', '19:30', ARRAY[21, 23, 24, 26]);

-- 다른 회원의 식별자나 이메일을 시연 계정으로 재사용하지 않는다.
DO $$
BEGIN
    IF current_database() <> 'service' THEN
        RAISE EXCEPTION 'Service DB가 아닙니다: %', current_database();
    END IF;

    IF EXISTS (
        SELECT 1 FROM demo_developer_seed seed
        JOIN users existing ON existing.kakao_id = seed.kakao_id
        WHERE existing.email IS DISTINCT FROM
              'demo-developer-' || lpad(seed.seed_no::text, 2, '0') || '@example.invalid'
    ) OR EXISTS (
        SELECT 1 FROM demo_developer_seed seed
        JOIN users existing ON existing.email =
             'demo-developer-' || lpad(seed.seed_no::text, 2, '0') || '@example.invalid'
        WHERE existing.kakao_id <> seed.kakao_id
    ) THEN
        RAISE EXCEPTION '시연 계정 식별자가 기존 회원과 충돌합니다';
    END IF;

    IF EXISTS (
        SELECT 1 FROM demo_developer_seed seed
        WHERE NOT EXISTS (
            SELECT 1 FROM dynamic_codes code
            JOIN dynamic_code_groups group_row ON group_row.group_id = code.group_id
            WHERE code.code_id = seed.field_code_id AND code.is_active
              AND group_row.group_key = 'DEV_FIELD' AND group_row.is_active
        )
    ) OR EXISTS (
        SELECT 1 FROM demo_developer_seed seed
        CROSS JOIN LATERAL unnest(seed.tech_code_ids) AS tech(code_id)
        WHERE NOT EXISTS (
            SELECT 1 FROM dynamic_codes code
            JOIN dynamic_code_groups group_row ON group_row.group_id = code.group_id
            WHERE code.code_id = tech.code_id AND code.is_active
              AND group_row.group_key = 'TECH_CATEGORY' AND group_row.is_active
        )
    ) THEN
        RAISE EXCEPTION '필요한 분야 또는 기술 코드가 없습니다. 기술 코드 배포를 먼저 확인하세요';
    END IF;
END $$;

-- 처음 만든 사용자만 아래 관계 데이터를 생성한다. 재실행은 전부 건너뛴다.
CREATE TEMP TABLE new_demo_users (
    user_id integer NOT NULL,
    seed_no integer NOT NULL
) ON COMMIT DROP;

WITH inserted AS (
    INSERT INTO users (
        kakao_id, name, email, has_dev_profile, is_email_verified,
        created_at, updated_at, record_status, record_status_group, is_deleted
    )
    SELECT seed.kakao_id, seed.name,
           'demo-developer-' || lpad(seed.seed_no::text, 2, '0') || '@example.invalid',
           TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
    FROM demo_developer_seed seed
    ON CONFLICT (kakao_id) DO NOTHING
    RETURNING id, kakao_id
)
INSERT INTO new_demo_users (user_id, seed_no)
SELECT inserted.id, seed.seed_no
FROM inserted
JOIN demo_developer_seed seed USING (kakao_id);

CREATE TEMP TABLE new_demo_developers (
    developer_id integer NOT NULL,
    seed_no integer NOT NULL
) ON COMMIT DROP;

WITH inserted AS (
    INSERT INTO developers (
        user_id, dev_review_count, explanatory_review_count,
        created_at, updated_at, record_status, record_status_group, is_deleted
    )
    SELECT new_user.user_id, 0, 0,
           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
    FROM new_demo_users new_user
    RETURNING id, user_id
)
INSERT INTO new_demo_developers (developer_id, seed_no)
SELECT inserted.id, new_user.seed_no
FROM inserted
JOIN new_demo_users new_user USING (user_id);

INSERT INTO developer_profiles (
    developer_id, intro, description, field_code_id, years_of_experience,
    active_from, active_to, created_at, updated_at,
    record_status, record_status_group, is_deleted
)
SELECT developer.developer_id, seed.intro, seed.description, seed.field_code_id,
       seed.years_of_experience, seed.active_from, seed.active_to,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM new_demo_developers developer
JOIN demo_developer_seed seed USING (seed_no);

INSERT INTO developer_tech_categories (
    developer_id, tech_code_id, created_at, updated_at,
    record_status, record_status_group, is_deleted
)
SELECT developer.developer_id, tech.code_id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM new_demo_developers developer
JOIN demo_developer_seed seed USING (seed_no)
CROSS JOIN LATERAL unnest(seed.tech_code_ids) AS tech(code_id);

CREATE TEMP TABLE demo_career_seed (
    seed_no integer NOT NULL,
    company varchar(50) NOT NULL,
    role varchar(100) NOT NULL,
    start_date date NOT NULL,
    end_date date
) ON COMMIT DROP;

INSERT INTO demo_career_seed VALUES
    (1, '회원 서비스 개발팀', '로그인·계정 관리 API 개발', '2022-04-01', NULL),
    (1, '예약 플랫폼 개발팀', '회원가입 화면과 인증 오류 개선', '2020-03-01', '2022-03-31'),
    (2, '커머스 데이터 개발팀', '상품·주문 데이터 모델과 조회 성능 개선', '2021-02-01', NULL),
    (2, '지역 서비스 개발팀', '운영 데이터 정리 및 이관', '2019-06-01', '2021-01-31'),
    (3, 'SaaS 인프라 개발팀', 'AWS 배포 자동화와 장애 대응', '2020-01-01', NULL),
    (3, '웹 서비스 운영팀', 'Docker 환경 구성과 배포 로그 점검', '2018-09-01', '2019-12-31'),
    (4, '예약 서비스 프론트엔드팀', '예약·결제 전 화면 흐름 개발', '2023-01-01', NULL),
    (4, '소상공인 웹 제작팀', '반응형 화면과 폼 오류 수정', '2021-04-01', '2022-12-31'),
    (5, '주문 서비스 백엔드팀', '결제 승인·취소 API와 중복 요청 처리', '2020-03-01', NULL),
    (5, '커머스 플랫폼 개발팀', '주문 상태 및 정산 데이터 개발', '2017-10-01', '2020-02-29'),
    (6, '웹 서비스 운영팀', '도메인·HTTPS·리버스 프록시 설정', '2023-03-01', NULL),
    (6, '교육 서비스 개발팀', '서비스 배포와 DNS 변경 지원', '2021-07-01', '2023-02-28'),
    (7, '예약 플랫폼 백엔드팀', '예약 알림 발송 및 재시도 처리', '2022-05-01', NULL),
    (7, '고객 관리 서비스팀', '이메일 템플릿과 발송 기록 개발', '2020-11-01', '2022-04-30'),
    (8, '클라우드 운영팀', '서버 비용 점검과 API 성능 분석', '2021-01-01', NULL),
    (8, '콘텐츠 플랫폼 개발팀', '캐시 적용 및 느린 쿼리 개선', '2019-04-01', '2020-12-31'),
    (9, '스타트업 제품 개발팀', 'Supabase 권한 정책과 DB 구조 개선', '2023-06-01', NULL),
    (10, '웹 제품 프론트엔드팀', 'Next.js 화면과 API 연동 개발', '2020-02-01', NULL),
    (10, '예약 서비스 개발팀', '페이지 이동 및 입력 오류 개선', '2018-08-01', '2020-01-31');

INSERT INTO careers (
    developer_id, company, role, start_date, end_date, is_verified,
    created_at, updated_at, record_status, record_status_group, is_deleted
)
SELECT developer.developer_id, career.company, career.role, career.start_date,
       career.end_date, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       'ACTIVE', 'RECORD_STATUS', FALSE
FROM new_demo_developers developer
JOIN demo_career_seed career USING (seed_no);

CREATE TEMP TABLE demo_portfolio_seed (
    seed_no integer NOT NULL,
    title varchar(200) NOT NULL,
    description varchar(500) NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_portfolio_seed VALUES
    (1, '회원가입 오류 재현과 인증 흐름 정리', '가입 단계별 실패 원인을 구분하고 만료된 세션에서 다시 로그인할 수 있도록 흐름을 정리했습니다.'),
    (1, '권한별 화면 접근 개선', '로그인 상태와 사용자 역할에 따라 접근 가능한 화면과 API 응답을 일치시켰습니다.'),
    (2, '주문 내역 조회 속도 개선', '중복 조회를 줄이고 인덱스를 조정해 자주 쓰는 조회 경로를 개선했습니다.'),
    (2, '운영 데이터 이관 절차 설계', '기존 데이터의 누락과 중복을 확인하고 단계별 이관 및 검증 쿼리를 작성했습니다.'),
    (3, '컨테이너 배포 오류 진단', '환경 변수와 빌드 결과의 차이를 찾아 배포 과정에서 반복되던 오류를 해결했습니다.'),
    (3, '서비스 배포 자동화', '테스트부터 이미지 배포까지의 단계를 정리하고 실패 시 확인할 로그를 남겼습니다.'),
    (4, '예약 버튼 동작 오류 수정', '중복 클릭과 느린 응답 상황을 재현해 예약이 한 번만 저장되도록 화면 상태를 개선했습니다.'),
    (4, '모바일 예약 화면 개선', '좁은 화면에서 잘리던 날짜 선택과 입력 오류 안내를 정리했습니다.'),
    (5, '결제 중복 요청 방지', '같은 결제 요청이 다시 들어와도 주문 상태와 결제 기록이 한 번만 확정되도록 처리했습니다.'),
    (5, '취소·환불 상태 추적', '외부 결제 응답이 늦거나 불명확한 경우를 구분하고 운영 확인 절차를 정리했습니다.'),
    (6, '도메인 이전과 HTTPS 적용', '도메인 변경 후 로그인과 API 주소가 어긋나지 않도록 프록시 및 리다이렉트를 점검했습니다.'),
    (6, 'DNS 장애 점검 가이드', '접속 실패 원인을 DNS, 인증서, 서버 응답으로 나눠 확인하는 안내서를 작성했습니다.'),
    (7, '예약 알림 누락 개선', '발송 실패 재시도와 처리 이력을 분리해 누락 여부를 확인할 수 있게 했습니다.'),
    (7, '이메일 발송 중복 방지', '이벤트 재전달 시 같은 알림이 반복되지 않도록 발송 조건을 정리했습니다.'),
    (8, '클라우드 자원 사용량 점검', '사용률이 낮은 자원을 찾아 축소하고 변경 전후 비용과 응답 시간을 비교했습니다.'),
    (8, '느린 API 경로 개선', '반복 호출과 DB 조회를 분석해 병목 구간을 줄였습니다.'),
    (9, 'Supabase 접근 정책 정리', '사용자별 데이터 접근 범위를 점검하고 누락된 권한 정책을 수정했습니다.'),
    (9, 'PostgreSQL 데이터 구조 개선', '중복 컬럼을 정리하고 기존 데이터 검증 절차를 마련했습니다.'),
    (10, 'Next.js 데이터 로딩 오류 수정', '로딩·오류·빈 결과 상태를 구분해 페이지 이동 중 잘못된 정보를 보여주지 않게 했습니다.'),
    (10, '모바일 폼 제출 흐름 개선', '입력 검증과 API 실패 안내를 연결하고 작은 화면에서 제출 버튼이 가려지지 않게 했습니다.');

INSERT INTO portfolio_items (
    developer_id, title, description, created_at, updated_at,
    record_status, record_status_group, is_deleted
)
SELECT developer.developer_id, portfolio.title, portfolio.description,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM new_demo_developers developer
JOIN demo_portfolio_seed portfolio USING (seed_no);

CREATE TEMP TABLE demo_price_seed (
    seed_no integer NOT NULL,
    task_type varchar(50) NOT NULL,
    task_name varchar(50) NOT NULL,
    price bigint NOT NULL,
    period_days smallint
) ON COMMIT DROP;

INSERT INTO demo_price_seed VALUES
    (1, 'CONSULT', '로그인 문제 상담', 60000, NULL), (1, 'DEVELOP', '로그인 오류 수정', 450000, 4),
    (2, 'CONSULT', 'DB 구조 상담', 70000, NULL), (2, 'DEVELOP', '데이터 모델 정리', 650000, 6),
    (3, 'CONSULT', '배포 오류 상담', 70000, NULL), (3, 'DEVELOP', '배포 환경 개선', 800000, 7),
    (4, 'CONSULT', '화면 오류 상담', 50000, NULL), (4, 'DEVELOP', '예약 화면 수정', 400000, 4),
    (5, 'CONSULT', '결제 연동 상담', 90000, NULL), (5, 'DEVELOP', '결제 API 개선', 1000000, 10),
    (6, 'CONSULT', '도메인 연결 상담', 60000, NULL), (6, 'DEVELOP', 'HTTPS 연결 설정', 350000, 3),
    (7, 'CONSULT', '알림 발송 상담', 60000, NULL), (7, 'DEVELOP', '알림 흐름 개선', 550000, 5),
    (8, 'CONSULT', '클라우드 비용 상담', 80000, NULL), (8, 'DEVELOP', '서버 비용 개선', 700000, 6),
    (9, 'CONSULT', 'Supabase 구조 상담', 50000, NULL), (9, 'DEVELOP', 'DB 권한 정책 수정', 500000, 5),
    (10, 'CONSULT', 'Next.js 오류 상담', 70000, NULL), (10, 'DEVELOP', '화면·API 연결 수정', 600000, 5);

INSERT INTO price_catalogs (
    developer_id, task_type, task_type_group, task_name, price, period_days,
    created_at, updated_at, record_status, record_status_group, is_deleted
)
SELECT developer.developer_id, price.task_type, 'TASK_TYPE', price.task_name,
       price.price, price.period_days, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       'ACTIVE', 'RECORD_STATUS', FALSE
FROM new_demo_developers developer
JOIN demo_price_seed price USING (seed_no);

-- 리뷰는 실제 프로필 화면의 목록과 평점에 함께 쓰인다. 각각 독립된 시연 의뢰인/프로젝트/계약을 갖는다.
CREATE TEMP TABLE demo_review_seed (
    seed_no integer NOT NULL,
    review_no integer NOT NULL,
    client_name varchar(25) NOT NULL,
    project_name varchar(100) NOT NULL,
    dev_score integer NOT NULL CHECK (dev_score BETWEEN 1 AND 5),
    exp_score integer NOT NULL CHECK (exp_score BETWEEN 1 AND 5),
    content text NOT NULL,
    reply text,
    PRIMARY KEY (seed_no, review_no)
) ON COMMIT DROP;

INSERT INTO demo_review_seed VALUES
    (1, 1, '오지민', '로그인 오류 수정', 5, 5, '로그인이 풀리는 조건을 먼저 찾아주셨고 수정 후에도 같은 문제가 생기지 않는지 확인해 주셨어요.', '재현 과정을 자세히 공유해 주셔서 원인을 빨리 찾을 수 있었습니다.'),
    (1, 2, '한유리', '회원가입 인증 개선', 5, 4, '인증 메일이 늦게 오는 문제를 함께 점검했습니다. 진행 상황을 자주 알려주셔서 좋았습니다.', NULL),
    (2, 1, '정민서', '주문 내역 조회 개선', 5, 5, '주문 조회가 느렸는데 원인을 설명해 주시고 인덱스까지 정리해 주셨어요. 체감 속도가 좋아졌습니다.', '운영 데이터 특성을 알려주셔서 조회 경로를 조정할 수 있었습니다.'),
    (2, 2, '김태윤', '중복 데이터 정리', 4, 5, '기존 데이터부터 확인하고 안전하게 정리하는 순서로 진행해 주셔서 안심됐습니다.', NULL),
    (3, 1, '서하은', '배포 실패 원인 분석', 5, 4, '배포 때만 나던 오류를 로그와 설정 차이로 찾아주셨습니다. 수정 내역도 문서로 받았습니다.', NULL),
    (3, 2, '백승우', '서버 환경 설정', 4, 5, '막혀 있던 서버 배포를 끝냈습니다. 제가 다시 배포할 때 확인할 항목도 알려주셨어요.', '다음 배포도 무리 없이 진행되길 바랍니다.'),
    (4, 1, '이소연', '예약 화면 오류 수정', 5, 5, '예약 버튼이 두 번 눌리는 문제를 해결해 주셨습니다. 모바일에서도 직접 확인해 주셨어요.', NULL),
    (4, 2, '윤도현', '모바일 입력 화면 개선', 4, 5, '작은 화면에서 입력이 잘 안 되던 부분을 고쳤습니다. 피드백도 빠르게 반영해 주셨어요.', NULL),
    (5, 1, '조예린', '결제 요청 중복 방지', 5, 5, '중복 결제 요청이 발생하는 경로를 찾아서 막아주셨습니다. 확인해야 할 로그도 정리해 주셨어요.', '운영 중 이상 징후가 보이면 공유해 주세요.'),
    (5, 2, '강민재', '주문 상태 API 정리', 5, 4, '주문 취소 뒤 상태가 다르게 보이던 문제를 해결했습니다. 예외 상황까지 챙겨주셨습니다.', NULL),
    (6, 1, '문채원', 'HTTPS 연결 복구', 5, 5, '인증서 교체 뒤 접속이 안 되던 문제를 빠르게 해결했습니다. 갱신 절차도 남겨주셨어요.', NULL),
    (6, 2, '장현우', '도메인 연결 점검', 4, 5, '도메인 변경 후 일부 페이지가 안 열렸는데 DNS부터 차근차근 확인해 주셨습니다.', NULL),
    (7, 1, '임수빈', '예약 알림 누락 수정', 5, 5, '예약 알림이 빠지는 조건을 찾아 수정해 주셨어요. 테스트 결과도 함께 전달받았습니다.', '알림이 다시 누락되면 확인할 수 있도록 로그를 보강했습니다.'),
    (7, 2, '송준혁', '이메일 중복 발송 방지', 4, 5, '같은 메일이 여러 번 가던 문제가 해결됐습니다. 원인을 쉽게 설명해 주셔서 이해하기 좋았어요.', NULL),
    (8, 1, '유나경', '클라우드 비용 점검', 5, 5, '사용하지 않는 서버 자원을 찾아 정리해 주셨습니다. 비용이 어디서 발생하는지도 알게 됐어요.', NULL),
    (8, 2, '홍지훈', 'API 응답 속도 개선', 5, 4, '느린 API 구간을 측정해서 개선해 주셨습니다. 전후 수치를 보여주셔서 좋았습니다.', NULL),
    (9, 1, '신지아', 'Supabase 접근 권한 수정', 5, 5, '권한 설정 때문에 데이터가 보이지 않던 문제를 해결했습니다. 필요한 접근 범위도 설명해 주셨어요.', NULL),
    (9, 2, '노태경', '데이터 구조 정리', 4, 5, '중복된 데이터를 정리하고 조회 쿼리까지 손봐주셨습니다. 작업 전 확인도 꼼꼼했습니다.', NULL),
    (10, 1, '배서윤', 'Next.js 화면 오류 수정', 5, 5, '페이지를 옮길 때 화면이 비던 문제를 해결했습니다. 모바일 동작도 같이 확인해 주셨어요.', NULL),
    (10, 2, '황도윤', '폼 제출 오류 개선', 4, 5, '제출 버튼을 누른 뒤 반응이 없던 문제를 고쳤습니다. 오류 안내도 훨씬 이해하기 쉬워졌어요.', NULL);

-- 이미 실행했던 개발자 시드에도 리뷰를 붙일 수 있도록 신규 개발자 목록 대신 예약된 kakao_id로 찾는다.
DO $$
BEGIN
    IF (SELECT count(*) FROM demo_review_seed) <> 20 THEN
        RAISE EXCEPTION '시연 리뷰 원본은 20건이어야 합니다.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM demo_review_seed seed
        JOIN users usr ON usr.kakao_id = 990028300000::bigint + seed.seed_no * 10 + seed.review_no
        WHERE usr.email IS DISTINCT FROM
            'demo-review-' || lpad(seed.seed_no::text, 2, '0') || '-' || seed.review_no || '@example.invalid'
    ) OR EXISTS (
        SELECT 1 FROM demo_review_seed seed
        JOIN users usr ON usr.email =
            'demo-review-' || lpad(seed.seed_no::text, 2, '0') || '-' || seed.review_no || '@example.invalid'
        WHERE usr.kakao_id <> 990028300000::bigint + seed.seed_no * 10 + seed.review_no
    ) THEN
        RAISE EXCEPTION '시연 리뷰 의뢰인 계정이 기존 회원과 충돌합니다.';
    END IF;
END $$;

INSERT INTO users (
    kakao_id, name, email, has_dev_profile, is_email_verified,
    created_at, updated_at, record_status, record_status_group, is_deleted
)
SELECT 990028300000::bigint + seed.seed_no * 10 + seed.review_no,
       seed.client_name,
       'demo-review-' || lpad(seed.seed_no::text, 2, '0') || '-' || seed.review_no || '@example.invalid',
       FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM demo_review_seed seed
ON CONFLICT (kakao_id) DO NOTHING;

CREATE TEMP TABLE demo_review_context ON COMMIT DROP AS
SELECT seed.*, client.id AS client_id, dev.id AS developer_id
FROM demo_review_seed seed
JOIN users client ON client.kakao_id = 990028300000::bigint + seed.seed_no * 10 + seed.review_no
JOIN demo_developer_seed developer_seed ON developer_seed.seed_no = seed.seed_no
JOIN users developer_user ON developer_user.kakao_id = developer_seed.kakao_id
JOIN developers dev ON dev.user_id = developer_user.id;

INSERT INTO projects (
    name, description, client_id, created_at, updated_at,
    record_status, record_status_group, is_deleted
)
SELECT context.project_name, '서비스 오류를 수정하고 변경 결과를 확인하는 프로젝트', context.client_id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM demo_review_context context
WHERE NOT EXISTS (
    SELECT 1 FROM projects project
    WHERE project.client_id = context.client_id AND project.name = context.project_name
);

INSERT INTO rooms (
    project_id, chat_ref_id, opened_at, developer_id, type, type_group, status, status_group,
    created_at, updated_at, record_status, record_status_group, is_deleted
)
SELECT project.id, 'demo-review-' || context.seed_no || '-' || context.review_no,
       CURRENT_TIMESTAMP, context.developer_id, 'DEVELOPMENT', 'ROOM_TYPE', 'CLOSED', 'ROOM_STATUS',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM demo_review_context context
JOIN projects project ON project.client_id = context.client_id AND project.name = context.project_name
WHERE NOT EXISTS (
    SELECT 1 FROM rooms room WHERE room.project_id = project.id AND room.developer_id = context.developer_id
);

-- 서명 이미지 파일은 만들지 않는다. 시연 계약에만 쓰이는 서명 메타데이터다.
CREATE TEMP TABLE demo_review_contracts (
    contract_id integer PRIMARY KEY,
    client_id integer NOT NULL,
    developer_id integer NOT NULL,
    dev_score integer NOT NULL,
    exp_score integer NOT NULL,
    content text NOT NULL,
    reply text
) ON COMMIT DROP;

INSERT INTO contracts (
    room_id, contract_type, contract_type_group, status, status_group, contract_text,
    created_at, updated_at, record_status, record_status_group, is_deleted
)
SELECT room.id, 'DEVELOPMENT', 'CONTRACT_TYPE', 'SIGNED', 'CONTRACT_STATUS',
       '프로젝트 범위 내 오류 수정 및 결과 확인에 관한 개발 계약', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       'ACTIVE', 'RECORD_STATUS', FALSE
FROM demo_review_context context
JOIN projects project ON project.client_id = context.client_id AND project.name = context.project_name
JOIN rooms room ON room.project_id = project.id AND room.developer_id = context.developer_id
WHERE NOT EXISTS (SELECT 1 FROM contracts contract WHERE contract.room_id = room.id);

INSERT INTO demo_review_contracts
SELECT contract.id, context.client_id, context.developer_id,
       context.dev_score, context.exp_score, context.content, context.reply
FROM demo_review_context context
JOIN projects project ON project.client_id = context.client_id AND project.name = context.project_name
JOIN rooms room ON room.project_id = project.id AND room.developer_id = context.developer_id
JOIN contracts contract ON contract.room_id = room.id;

DO $$
DECLARE
    demo_contract record;
    signature_id integer;
BEGIN
    FOR demo_contract IN
        SELECT context.contract_id, context.client_id, developer.user_id AS developer_user_id,
               contract.client_signature_id, contract.developer_signature_id
        FROM demo_review_contracts context
        JOIN developers developer ON developer.id = context.developer_id
        JOIN contracts contract ON contract.id = context.contract_id
    LOOP
        IF demo_contract.client_signature_id IS NULL THEN
            INSERT INTO contract_signatures (
                is_signed, signed_at, user_id, created_at, updated_at,
                record_status, record_status_group, is_deleted
            ) VALUES (
                TRUE, CURRENT_TIMESTAMP, demo_contract.client_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                'ACTIVE', 'RECORD_STATUS', FALSE
            ) RETURNING id INTO signature_id;
            UPDATE contracts SET client_signature_id = signature_id
            WHERE id = demo_contract.contract_id;
        END IF;
        IF demo_contract.developer_signature_id IS NULL THEN
            INSERT INTO contract_signatures (
                is_signed, signed_at, user_id, created_at, updated_at,
                record_status, record_status_group, is_deleted
            ) VALUES (
                TRUE, CURRENT_TIMESTAMP, demo_contract.developer_user_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                'ACTIVE', 'RECORD_STATUS', FALSE
            ) RETURNING id INTO signature_id;
            UPDATE contracts SET developer_signature_id = signature_id
            WHERE id = demo_contract.contract_id;
        END IF;
    END LOOP;
END $$;

INSERT INTO development_contracts (
    contract_id, start_date, due_date, free_repair_days, status, status_group, total_amount,
    created_at, updated_at, record_status, record_status_group, is_deleted
)
SELECT context.contract_id, CURRENT_DATE - 30, CURRENT_DATE - 7, 7,
       'COMPLETED', 'DEVELOPMENT_CONTRACT_STATUS', 400000,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM demo_review_contracts context
WHERE NOT EXISTS (
    SELECT 1 FROM development_contracts dev_contract WHERE dev_contract.contract_id = context.contract_id
);

INSERT INTO reviews (
    contract_id, dev_score, exp_score, content, reply, client_id, developer_id,
    created_at, updated_at, record_status, record_status_group, is_deleted
)
SELECT context.contract_id, context.dev_score, context.exp_score, context.content, context.reply,
       context.client_id, context.developer_id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE', 'RECORD_STATUS', FALSE
FROM demo_review_contracts context
WHERE NOT EXISTS (SELECT 1 FROM reviews review WHERE review.contract_id = context.contract_id);

-- R-REV-01/R-REV-02: 카드 평점 두 축과 건수는 저장된 리뷰에서 다시 계산한다.
UPDATE developers dev
SET dev_rating = aggregate.dev_rating,
    explanatory_rating = aggregate.exp_rating,
    dev_review_count = aggregate.review_count,
    explanatory_review_count = aggregate.review_count,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT review.developer_id,
           avg(review.dev_score)::double precision AS dev_rating,
           avg(review.exp_score)::double precision AS exp_rating,
           count(*)::integer AS review_count
    FROM reviews review
    JOIN developers developer ON developer.id = review.developer_id
    JOIN users usr ON usr.id = developer.user_id
    JOIN demo_developer_seed seed ON seed.kakao_id = usr.kakao_id
    WHERE NOT review.is_deleted
    GROUP BY review.developer_id
) aggregate
WHERE dev.id = aggregate.developer_id;

-- 10명 모두 공개 프로필까지 있는지 확인한다. 실행 중 한 명이라도 빠지면 전부 롤백된다.
DO $$
DECLARE
    complete_count integer;
BEGIN
    SELECT count(*) INTO complete_count
    FROM demo_developer_seed seed
    JOIN users usr ON usr.kakao_id = seed.kakao_id AND NOT usr.is_deleted
    JOIN developers dev ON dev.user_id = usr.id AND NOT dev.is_deleted
    JOIN developer_profiles profile ON profile.developer_id = dev.id AND NOT profile.is_deleted;

    IF complete_count <> 10 THEN
        RAISE EXCEPTION '시연 개발자 프로필 수가 10명이 아닙니다: %', complete_count;
    END IF;
    IF (SELECT count(*) FROM demo_review_contracts) <> 20
       OR EXISTS (
           SELECT 1 FROM demo_review_contracts context
           JOIN contracts contract ON contract.id = context.contract_id
           JOIN development_contracts dev_contract ON dev_contract.contract_id = contract.id
           LEFT JOIN reviews review ON review.contract_id = contract.id AND NOT review.is_deleted
           WHERE contract.status <> 'SIGNED' OR contract.client_signature_id IS NULL
              OR contract.developer_signature_id IS NULL OR dev_contract.status <> 'COMPLETED'
              OR review.id IS NULL OR review.client_id <> context.client_id
              OR review.developer_id <> context.developer_id
       )
       OR (SELECT count(*) FROM reviews review
           JOIN demo_review_contracts context ON context.contract_id = review.contract_id
           WHERE NOT review.is_deleted) <> 20 THEN
        RAISE EXCEPTION '시연 리뷰 20건과 완료 계약의 연결이 일치하지 않습니다.';
    END IF;
    IF EXISTS (
        SELECT dev.id
        FROM demo_developer_seed seed
        JOIN users usr ON usr.kakao_id = seed.kakao_id
        JOIN developers dev ON dev.user_id = usr.id
        LEFT JOIN reviews review ON review.developer_id = dev.id AND NOT review.is_deleted
        GROUP BY dev.id, dev.dev_review_count, dev.explanatory_review_count,
                 dev.dev_rating, dev.explanatory_rating
        HAVING count(review.id) <> 2
            OR dev.dev_review_count <> count(review.id)
            OR dev.explanatory_review_count <> count(review.id)
            OR dev.dev_rating IS DISTINCT FROM avg(review.dev_score)::double precision
            OR dev.explanatory_rating IS DISTINCT FROM avg(review.exp_score)::double precision
    ) THEN
        RAISE EXCEPTION '시연 개발자 리뷰 건수 또는 평균 평점이 실제 리뷰와 다릅니다.';
    END IF;
END $$;

SELECT count(*) AS newly_inserted_developers FROM new_demo_developers;
SELECT seed.seed_no, usr.name, dev.id AS developer_id,
       (SELECT count(*) FROM developer_tech_categories tech
        WHERE tech.developer_id = dev.id AND NOT tech.is_deleted) AS tech_count,
       (SELECT count(*) FROM careers career
        WHERE career.developer_id = dev.id AND NOT career.is_deleted) AS career_count,
       (SELECT count(*) FROM portfolio_items item
        WHERE item.developer_id = dev.id AND NOT item.is_deleted) AS portfolio_count,
       (SELECT count(*) FROM price_catalogs price
        WHERE price.developer_id = dev.id AND NOT price.is_deleted) AS price_count,
       (SELECT count(*) FROM reviews review
        WHERE review.developer_id = dev.id AND NOT review.is_deleted) AS review_count,
       dev.dev_rating, dev.explanatory_rating
FROM demo_developer_seed seed
JOIN users usr ON usr.kakao_id = seed.kakao_id
JOIN developers dev ON dev.user_id = usr.id
ORDER BY seed.seed_no;

COMMIT;
