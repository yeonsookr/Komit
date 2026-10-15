import unittest
from typing import get_args

from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.routers import service


def _milestone(**overrides):
    base = {"issueId": 312, "price": 400000, "workDate": 10, "qaCriteria": ["로그인 화면이 뜬다"]}
    base.update(overrides)
    return base


def _new_work(**overrides):
    base = {
        "title": "관리자 화면에 검색 추가",
        "detail": "관리자가 회원을 찾을 수 있다",
        "price": 200000,
        "qaCriteria": ["검색이 된다"],
    }
    base.update(overrides)
    return base


def _handover(**overrides):
    base = {
        "title": "인계 · 소스 · 계정 · 설명서",
        "price": 0,
        "isHandover": True,
        "qaCriteria": ["소스 파일 전체를 받았다"],
    }
    base.update(overrides)
    return base


def _request(**overrides):
    base = {
        "startDate": "2026-08-26",
        "dueDate": "2026-09-16",
        "freeRepairDays": 14,
        "milestones": [_milestone(), _new_work(), _handover()],
        "deliverableTitles": ["소스 파일 전체"],
        "message": "잘 부탁드립니다.",
    }
    base.update(overrides)
    return service.WriteDevelopmentContractRequest(**base)


def _errors(**overrides) -> set[tuple[str, str]]:
    """실패 응답에 실릴 (필드, 사유) 쌍. 실제 응답과 같은 변환을 거친다."""
    try:
        _request(**overrides)
    except ValidationError as error:
        return _fields(error)
    raise AssertionError("검증을 통과했다")


def _fields(error: ValidationError) -> set[tuple[str, str]]:
    return {
        (service._field_name(("body", *detail["loc"])), service._reason(detail))
        for detail in error.errors()
    }


class DevelopmentContractWriteRequestTest(unittest.TestCase):
    def test_accepts_existing_issue_and_new_work(self):
        request = _request()

        self.assertEqual(request.milestones[0].issueId, 312)
        self.assertIsNone(request.milestones[1].issueId)

    def test_existing_issue_may_carry_the_name_changed_in_the_contract(self):
        request = _request(
            milestones=[_milestone(title="관리자 인증 강화", detail="토큰도 확인한다"), _handover()]
        )

        self.assertEqual(request.milestones[0].title, "관리자 인증 강화")

    def test_optional_fields_may_be_left_out(self):
        request = service.WriteDevelopmentContractRequest(
            startDate="2026-08-26",
            dueDate="2026-09-16",
            freeRepairDays=0,
            milestones=[
                {"issueId": 312, "price": 400000, "qaCriteria": ["로그인 화면이 뜬다"]},
                {"title": "인계", "price": 0, "isHandover": True, "qaCriteria": ["받았다"]},
            ],
        )

        self.assertEqual(request.deliverableTitles, [])
        self.assertIsNone(request.message)
        # 보내지 않으면 일반 작업이다.
        self.assertFalse(request.milestones[0].isHandover)

    def test_rejects_missing_required_fields(self):
        with self.assertRaises(ValidationError) as raised:
            service.WriteDevelopmentContractRequest()

        self.assertEqual(
            _fields(raised.exception),
            {
                ("startDate", "필수 항목입니다."),
                ("dueDate", "필수 항목입니다."),
                ("freeRepairDays", "필수 항목입니다."),
                ("milestones", "필수 항목입니다."),
            },
        )

    def test_rejects_empty_milestones(self):
        self.assertEqual(_errors(milestones=[]), {("milestones", "1개 이상이어야 합니다.")})

    def test_new_work_needs_a_title(self):
        self.assertEqual(
            _errors(milestones=[_new_work(title=None), _handover()]),
            {("milestones.0.title", "필수 항목입니다.")},
        )

    def test_title_and_detail_may_not_be_empty(self):
        self.assertEqual(
            _errors(
                milestones=[
                    _milestone(title=""),
                    _new_work(title=""),
                    _new_work(title="   "),
                    _milestone(issueId=313, detail=""),
                    _handover(),
                ]
            ),
            {
                ("milestones.0.title", "1자 이상이어야 합니다."),
                ("milestones.1.title", "1자 이상이어야 합니다."),
                ("milestones.3.detail", "1자 이상이어야 합니다."),
            },
        )

    def test_same_issue_may_not_appear_in_two_milestones(self):
        self.assertEqual(
            _errors(milestones=[_milestone(), _milestone(issueId=313), _milestone(), _handover()]),
            {("milestones", service.DUPLICATE_ISSUE_REASON)},
        )

    def test_list_errors_come_with_errors_inside_a_milestone_like_the_server(self):
        # 서버의 클래스 검증은 작업 하나가 잘못돼도 목록 검사를 그대로 한다. 오류를 모두 함께 낸다.
        self.assertEqual(
            _errors(milestones=[_new_work(title=None), _milestone(), _milestone()]),
            {
                ("milestones.0.title", "필수 항목입니다."),
                ("milestones", service.DUPLICATE_ISSUE_REASON),
                ("milestones", service.HANDOVER_COUNT_REASON),
            },
        )

    def test_several_new_works_do_not_collide(self):
        request = _request(milestones=[_new_work(), _new_work(), _handover()])

        self.assertEqual(len(request.milestones), 3)

    def test_handover_must_be_free(self):
        self.assertEqual(
            _errors(milestones=[_milestone(), _handover(price=10000)]),
            {("milestones.1.price", service.HANDOVER_PRICE_REASON)},
        )

    def test_needs_a_handover(self):
        self.assertEqual(
            _errors(milestones=[_milestone(), _new_work()]),
            {("milestones", service.HANDOVER_COUNT_REASON)},
        )

    def test_takes_only_one_handover_for_now(self):
        self.assertEqual(
            _errors(milestones=[_milestone(), _handover(), _handover(title="운영 설명서")]),
            {("milestones", service.HANDOVER_COUNT_REASON)},
        )

    def test_regular_work_must_be_priced(self):
        # 인계 작업은 금액이 아니라 표시로 가린다. 표시가 없는 0원 작업은 일반 작업이라 거부한다.
        self.assertEqual(
            _errors(milestones=[_milestone(price=0), _handover()]),
            {("milestones.0.price", service.WORK_PRICE_REASON)},
        )
        self.assertEqual(
            _errors(milestones=[_milestone(), _handover(isHandover=False)]),
            {
                ("milestones.1.price", service.WORK_PRICE_REASON),
                ("milestones", service.HANDOVER_COUNT_REASON),
            },
        )

    def test_rejects_negative_numbers(self):
        self.assertEqual(
            _errors(freeRepairDays=-1, milestones=[_milestone(price=-1, workDate=-1), _handover()]),
            {
                ("freeRepairDays", "0 이상이어야 합니다."),
                ("milestones.0.price", "0 이상이어야 합니다."),
                ("milestones.0.workDate", "0 이상이어야 합니다."),
            },
        )

    def test_rejects_bad_qa_criteria(self):
        self.assertEqual(
            _errors(
                milestones=[
                    _milestone(issueId=311, qaCriteria=[]),
                    _milestone(issueId=312, qaCriteria=[""]),
                    _milestone(issueId=313, qaCriteria=["가" * 501]),
                    _handover(),
                ]
            ),
            {
                ("milestones.0.qaCriteria", "1개 이상이어야 합니다."),
                ("milestones.1.qaCriteria.0", "1자 이상이어야 합니다."),
                ("milestones.2.qaCriteria.0", "500자 이하여야 합니다."),
            },
        )

    def test_rejects_too_long_text(self):
        self.assertEqual(
            _errors(
                milestones=[_new_work(title="가" * 201, detail="가" * 1001), _handover()],
                deliverableTitles=["가" * 101, ""],
                message="가" * 301,
            ),
            {
                ("milestones.0.title", "200자 이하여야 합니다."),
                ("milestones.0.detail", "1000자 이하여야 합니다."),
                ("deliverableTitles.0", "100자 이하여야 합니다."),
                ("deliverableTitles.1", "1자 이상이어야 합니다."),
                ("message", "300자 이하여야 합니다."),
            },
        )

    def test_accepts_text_at_the_limit(self):
        request = _request(
            milestones=[
                _new_work(title="가" * 200, detail="가" * 1000, qaCriteria=["가" * 500]),
                _handover(),
            ],
            deliverableTitles=["가" * 100],
            message="가" * 300,
        )

        self.assertEqual(len(request.milestones[0].title), 200)


class DevelopmentContractWriteEndpointTest(unittest.TestCase):
    def test_due_date_before_start_is_rejected(self):
        result = service.write_development_contract(
            93, _request(startDate="2026-09-16", dueDate="2026-09-15")
        )

        self.assertIsInstance(result, JSONResponse)
        self.assertEqual(result.status_code, 400)

    def test_due_date_on_the_start_date_is_accepted(self):
        result = service.write_development_contract(
            93, _request(startDate="2026-09-16", dueDate="2026-09-16")
        )

        self.assertIsInstance(result, service.ContractResponse)

    def test_every_milestone_names_its_issue(self):
        result = service.write_development_contract(93, _request())

        milestones = result.development.milestones
        self.assertTrue(all(milestone.issueId is not None for milestone in milestones))
        # 계약의 요구사항 목록은 작업이 가리키는 요구사항을 작업 순서대로 모은 것이다.
        self.assertEqual(
            result.development.issueIds,
            list(dict.fromkeys(milestone.issueId for milestone in milestones)),
        )

    def test_sent_contract_is_negotiating_and_development_side_is_signed(self):
        result = service.write_development_contract(93, _request())

        # 체결 여부는 공통 계약 상태가 가진다. 개발 쪽은 처음 쓸 때부터 SIGNED 다. (R-CON-01)
        self.assertEqual(result.status, "NEGOTIATING")
        self.assertEqual(result.development.status, "SIGNED")

    def test_no_pre_signed_status(self):
        for model in (service.DevelopmentAgreementResponse, service.ConsultingAgreementResponse):
            self.assertNotIn("PRE_SIGNED", get_args(model.model_fields["status"].annotation))
        self.assertEqual(service.DRAFT_DEV_CONTRACT_EXAMPLE["development"]["status"], "SIGNED")


class DevelopmentContractWriteSpecificationTest(unittest.TestCase):
    def setUp(self):
        from fastapi import FastAPI

        app = FastAPI()
        app.include_router(service.router)
        self.write = app.openapi()["paths"]["/api/rooms/{roomId}/development-contracts"]["post"]

    def test_documents_every_failure_case(self):
        self.assertEqual(
            set(self.write["responses"]), {"201", "400", "401", "403", "404", "409", "422", "500"}
        )

    def test_conflicts_name_the_three_reasons(self):
        examples = self.write["responses"]["409"]["content"]["application/json"]["examples"]

        self.assertEqual(
            set(examples), {"NOT_DEVELOPMENT_ROOM", "CONTRACT_ALREADY_SIGNED", "CONTRACT_CANCELLED"}
        )

    def test_milestones_mark_the_handover(self):
        self.assertEqual(
            [milestone["isHandover"] for milestone in service.MILESTONES], [False, False, False, True]
        )

    def test_milestones_carry_a_detail(self):
        milestone = service.MILESTONES[0]

        self.assertIn("detail", milestone)
        self.assertEqual(service.MilestoneResponse(**milestone).detail, milestone["detail"])


if __name__ == "__main__":
    unittest.main()
