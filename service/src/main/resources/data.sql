-- 상태코드 시드. 스키마는 JPA(ddl-auto)가 만든다.
-- 매 기동마다 실행되므로 ON CONFLICT 로 멱등하게 둔다. (spring.sql.init.mode=always)

INSERT INTO system_enum_groups (group_code, group_name, description)
VALUES
    ('RECORD_STATUS', '레코드 상태', '공통 레코드의 사용 및 삭제 상태'),
    ('ROOM_TYPE', '대화방 유형', '문의·컨설팅·개발 대화방 구분'),
    ('ROOM_STATUS', '대화방 상태', '대화방 개폐 상태'),
    ('CONSULTING_AGREEMENT_STATUS', '컨설팅 계약 상태', '컨설팅 계약 진행 상태'),
    ('SPECIAL_AGREEMENT_TYPE', '특약 유형', '특약으로 변경하는 계약 항목'),
    ('CONTRACT_TEMPLATE_TYPE', '계약 양식 유형', '계약 문서 양식 구분'),
    ('RECOMMENDATION_TRIGGER', '추천 계기', '추천 회차가 생성된 계기'),
    ('RECOMMENDATION_OUTCOME', '추천 결과', '추천 회차의 최종 전환 결과'),
    ('MILESTONE_STATUS', '마일스톤 상태', '개발 작업의 진행 상태'),
    ('DEVELOPMENT_CONTRACT_STATUS', '개발 계약 상태', '개발 계약의 진행 상태'),
    ('TRANSACTION_TYPE', '거래 유형', '계좌 거래 기록의 유형'),
    ('RECOMMENDATION_DEV_RESPONSE', '추천 개발자 응답', '추천 항목에 대한 개발자 응답'),
    ('NOTIFICATION_TARGET_TYPE', '알림 이동 대상', '알림에서 이동할 업무 대상'),
    ('NOTIFICATION_TYPE', '알림 유형', '사용자에게 전달하는 알림 구분'),
    ('SURVEY_ANSWER_TYPE', '설문 응답 유형', '설문 문항이 답을 받는 방식'),
    ('TASK_TYPE', '작업 유형', '가격표의 컨설팅·개발 구분'),
    ('PAYMENT_METHOD_TYPE', '결제수단 유형', '등록 결제수단 구분'),
    ('RESERVATION_STATUS', '예약 상태', '컨설팅 예약의 진행 상태'),
    ('INSPECTION_STATUS', '검사 상태', '사전점검 실행 상태'),
    ('REPORT_STATUS', '신고 상태', '신고의 처리 상태'),
    ('CONTRACT_TYPE', '계약 유형', '컨설팅·개발 계약 구분'),
    ('CONTRACT_STATUS', '계약 상태', '공통 계약서의 진행 상태'),
    ('ISSUE_STATUS', '이슈 상태', '진단 이슈의 처리 상태'),
    ('ISSUE_SEVERITY', '이슈 심각도', '진단 이슈의 위험도'),
    ('QA_CRITERION_STATUS', 'QA 기준 상태', 'QA 확인 결과'),
    ('FILE_STATUS', '파일 상태', '파일 업로드 검증 상태')
ON CONFLICT (group_code) DO UPDATE
SET group_name = EXCLUDED.group_name,
    description = EXCLUDED.description;

INSERT INTO system_enums (
    code_key,
    group_code,
    code_name,
    sort_order,
    is_deprecated,
    meta_json
)
VALUES
    ('ACTIVE', 'RECORD_STATUS', '사용중', 1, FALSE, NULL),
    ('DELETED', 'RECORD_STATUS', '삭제됨', 2, FALSE, NULL),

    ('INQUIRY', 'ROOM_TYPE', '문의', 1, FALSE, NULL),
    ('CONSULTING', 'ROOM_TYPE', '컨설팅', 2, FALSE, NULL),
    ('DEVELOPMENT', 'ROOM_TYPE', '개발', 3, FALSE, NULL),

    ('OPEN', 'ROOM_STATUS', '열림', 1, FALSE, NULL),
    ('CLOSED', 'ROOM_STATUS', '닫힘', 2, FALSE, NULL),

    ('SIGNED', 'CONSULTING_AGREEMENT_STATUS', '체결됨', 1, FALSE, NULL),
    ('DEPOSITED', 'CONSULTING_AGREEMENT_STATUS', '예치됨', 2, FALSE, NULL),
    ('IN_PROGRESS', 'CONSULTING_AGREEMENT_STATUS', '진행중', 3, FALSE, NULL),
    ('COMPLETED', 'CONSULTING_AGREEMENT_STATUS', '완료됨', 4, FALSE, NULL),
    ('TERMINATED', 'CONSULTING_AGREEMENT_STATUS', '파기됨', 5, FALSE, NULL),

    ('PRICE_CHANGE', 'SPECIAL_AGREEMENT_TYPE', '가격', 1, FALSE, NULL),
    ('PERIOD_CHANGE', 'SPECIAL_AGREEMENT_TYPE', '기간', 2, FALSE, NULL),
    ('QA_CRITERIA_CHANGE', 'SPECIAL_AGREEMENT_TYPE', '확인문장', 3, FALSE, NULL),
    ('ADDITIONAL', 'SPECIAL_AGREEMENT_TYPE', '추가합의', 4, FALSE, NULL),

    ('DEV_STANDARD', 'CONTRACT_TEMPLATE_TYPE', '개발 외주 계약서', 1, FALSE, NULL),
    ('CONSULT_STANDARD', 'CONTRACT_TEMPLATE_TYPE', '컨설팅 계약서', 2, FALSE, NULL),
    ('AMENDMENT', 'CONTRACT_TEMPLATE_TYPE', '부속 합의서', 3, FALSE, NULL),
    ('ACCEPTANCE', 'CONTRACT_TEMPLATE_TYPE', '검수·납품 확인서', 4, FALSE, NULL),

    ('CONSULT_REQUEST', 'RECOMMENDATION_TRIGGER', '상담요청', 1, FALSE, NULL),
    ('REMATCH', 'RECOMMENDATION_TRIGGER', '재매칭', 2, FALSE, NULL),
    ('MANUAL', 'RECOMMENDATION_TRIGGER', '수동', 3, FALSE, NULL),

    ('CONTRACTED', 'RECOMMENDATION_OUTCOME', '추천 내 계약', 1, FALSE, NULL),
    ('CONSULTED_ONLY', 'RECOMMENDATION_OUTCOME', '상담만', 2, FALSE, NULL),
    ('NONE', 'RECOMMENDATION_OUTCOME', '무전환', 3, FALSE, NULL),
    ('OUTSIDE', 'RECOMMENDATION_OUTCOME', '추천 외 개발자와 계약', 4, FALSE, NULL),

    ('PROPOSED', 'MILESTONE_STATUS', '제안됨', 1, FALSE, NULL),
    ('REJECTED', 'MILESTONE_STATUS', '거부됨', 2, FALSE, NULL),
    ('SCHEDULED', 'MILESTONE_STATUS', '예정됨', 3, FALSE, NULL),
    ('IN_PROGRESS', 'MILESTONE_STATUS', '진행중', 4, FALSE, NULL),
    ('SUBMITTED', 'MILESTONE_STATUS', '제출됨', 5, FALSE, NULL),
    ('CONFIRMED', 'MILESTONE_STATUS', '완료', 6, FALSE, NULL),

    ('PRE_SIGNED', 'DEVELOPMENT_CONTRACT_STATUS', '가계약', 1, FALSE, NULL),
    ('SIGNED', 'DEVELOPMENT_CONTRACT_STATUS', '체결됨', 2, FALSE, NULL),
    ('DEPOSITED', 'DEVELOPMENT_CONTRACT_STATUS', '예치됨', 3, FALSE, NULL),
    ('IN_PROGRESS', 'DEVELOPMENT_CONTRACT_STATUS', '개발중', 4, FALSE, NULL),
    ('AFTER_SERVICE', 'DEVELOPMENT_CONTRACT_STATUS', '하자보수', 5, FALSE, NULL),
    ('COMPLETED', 'DEVELOPMENT_CONTRACT_STATUS', '완료됨', 6, FALSE, NULL),
    ('TERMINATED', 'DEVELOPMENT_CONTRACT_STATUS', '파기됨', 7, FALSE, NULL),

    ('DEPOSIT', 'TRANSACTION_TYPE', '예치', 1, FALSE, NULL),
    ('PAYOUT', 'TRANSACTION_TYPE', '지급', 2, FALSE, NULL),
    ('REFUND', 'TRANSACTION_TYPE', '환불', 3, FALSE, NULL),
    ('ADJUSTMENT', 'TRANSACTION_TYPE', '조정', 4, FALSE, NULL),

    ('ACCEPTED', 'RECOMMENDATION_DEV_RESPONSE', '수락', 1, FALSE, NULL),
    ('REJECTED', 'RECOMMENDATION_DEV_RESPONSE', '거절', 2, FALSE, NULL),
    ('EXPIRED', 'RECOMMENDATION_DEV_RESPONSE', '무응답 만료', 3, FALSE, NULL),

    ('ANNOUNCEMENT', 'NOTIFICATION_TARGET_TYPE', '공지', 1, FALSE, NULL),
    ('CONTRACT', 'NOTIFICATION_TARGET_TYPE', '계약', 2, FALSE, NULL),
    ('ISSUE', 'NOTIFICATION_TARGET_TYPE', '이슈', 3, FALSE, NULL),

    ('공지', 'NOTIFICATION_TYPE', '공지', 1, FALSE, NULL),
    ('착수알림', 'NOTIFICATION_TYPE', '착수알림', 2, FALSE, NULL),
    ('승인알림', 'NOTIFICATION_TYPE', '승인알림', 3, FALSE, NULL),
    ('예약알림톡', 'NOTIFICATION_TYPE', '예약알림톡', 4, FALSE, NULL),

    ('TEXT', 'SURVEY_ANSWER_TYPE', '주관식', 1, FALSE, NULL),
    ('SINGLE', 'SURVEY_ANSWER_TYPE', '객관식 단수', 2, FALSE, NULL),
    ('MULTI', 'SURVEY_ANSWER_TYPE', '객관식 복수', 3, FALSE, NULL),

    ('CONSULT', 'TASK_TYPE', '컨설팅', 1, FALSE, NULL),
    ('DEVELOP', 'TASK_TYPE', '개발', 2, FALSE, NULL),

    ('CARD', 'PAYMENT_METHOD_TYPE', '카드', 1, FALSE, NULL),
    ('REALTIME_TRANSFER', 'PAYMENT_METHOD_TYPE', '실시간계좌이체', 2, FALSE, NULL),

    ('PROPOSED', 'RESERVATION_STATUS', '제안됨', 1, FALSE, NULL),
    ('CONFIRMED', 'RESERVATION_STATUS', '확정', 2, FALSE, NULL),
    ('DISABLED', 'RESERVATION_STATUS', '비활성화', 3, FALSE, NULL),

    ('PENDING', 'INSPECTION_STATUS', '대기', 1, FALSE, NULL),
    ('IN_PROGRESS', 'INSPECTION_STATUS', '검사중', 2, FALSE, NULL),
    ('DONE', 'INSPECTION_STATUS', '완료', 3, FALSE, NULL),

    ('OPEN', 'REPORT_STATUS', '접수됨', 1, FALSE, NULL),
    ('RESOLVED', 'REPORT_STATUS', '해결됨', 2, FALSE, NULL),

    ('CONSULTING', 'CONTRACT_TYPE', '컨설팅', 1, FALSE, NULL),
    ('DEVELOPMENT', 'CONTRACT_TYPE', '개발', 2, FALSE, NULL),

    ('NEGOTIATING', 'CONTRACT_STATUS', '협의중', 1, FALSE, NULL),
    ('AGREED', 'CONTRACT_STATUS', '합의됨', 2, FALSE, NULL),
    ('SIGNED', 'CONTRACT_STATUS', '체결됨', 3, FALSE, NULL),

    ('UNRESOLVED', 'ISSUE_STATUS', '미해결', 1, FALSE, NULL),
    ('RESOLVED', 'ISSUE_STATUS', '해결', 2, FALSE, NULL),
    ('SUGGESTED', 'ISSUE_STATUS', '작업 제안됨', 3, FALSE, NULL),
    ('CONTRACTED', 'ISSUE_STATUS', '계약 포함', 4, FALSE, NULL),
    ('DISABLED', 'ISSUE_STATUS', '제안 만료', 5, FALSE, NULL),

    ('LOW', 'ISSUE_SEVERITY', '낮음', 1, FALSE, NULL),
    ('MEDIUM', 'ISSUE_SEVERITY', '보통', 2, FALSE, NULL),
    ('HIGH', 'ISSUE_SEVERITY', '높음', 3, FALSE, NULL),

    ('UNRESOLVED', 'QA_CRITERION_STATUS', '미해결', 1, FALSE, NULL),
    ('RESOLVED', 'QA_CRITERION_STATUS', '해결', 2, FALSE, NULL),

    ('PENDING', 'FILE_STATUS', '발급됨, 검증 전', 1, FALSE, NULL),
    ('COMPLETED', 'FILE_STATUS', '검증 통과', 2, FALSE, NULL),
    ('FAILED', 'FILE_STATUS', '크기·해시 불일치로 거부', 3, FALSE, NULL)
ON CONFLICT (code_key, group_code) DO UPDATE
SET code_name = EXCLUDED.code_name,
    sort_order = EXCLUDED.sort_order,
    is_deprecated = EXCLUDED.is_deprecated,
    meta_json = EXCLUDED.meta_json;
