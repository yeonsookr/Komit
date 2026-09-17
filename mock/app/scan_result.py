"""올린 검사 결과 파일을 화면이 쓰는 모양으로 바꾼다.

목 서버는 본래 고정 예시만 돌려준다. 여기에 올린 파일을 반영하는 길을 하나
더 둔 것은, 플러그인이 실제로 만든 결과가 진단서에 그대로 뜨는 것을 보여주기
위해서다.

- 파일을 올리지 않았으면 예전대로 고정 예시가 나간다
- 올렸으면 그 파일에서 뽑은 값이 나간다

담아 두는 곳은 프로세스 메모리다. 서버를 다시 띄우면 사라진다.

점수 내는 법은 docs/RULES.md 의 R-SCAN-10 · R-SCAN-18 을 따른다.
"""

import json
import re
from typing import Any

# 결과 파일은 마크다운이고, Komit 이 읽는 부분은 그 안의 json 코드블록 하나다.
# 앞부분은 의뢰인이 읽는 요약이라 모양이 정해져 있지 않다.
_JSON_BLOCK = re.compile(r"```json\s*\n(.*?)\n```", re.DOTALL)

# 진단 식별자 -> 올린 결과에서 뽑은 값
_store: dict[int, dict[str, Any]] = {}


# 검사 결과의 여덟 분류를 화면이 쓰는 네 분류로 옮긴다.
# 화면의 칩이 네 종뿐이라, 여덟 종을 그대로 내보내면 렌더가 깨진다.
_CATEGORY = {
    "COMPLIANCE": (72, "위법"),
    "SECURITY": (71, "보안"),
    "PAYMENT": (73, "결제"),
    "DATA": (73, "결제"),
    "COST": (74, "기타"),
    "HANDOVER": (74, "기타"),
    "AI_RESIDUE": (74, "기타"),
    "ETC": (74, "기타"),
}


def readiness_score(issues: list[dict]) -> int:
    """런칭 준비도. 구간을 먼저 정하고 그 안에서 건수만큼 깎는다."""
    high = sum(1 for i in issues if i.get("severity") == "HIGH")
    medium = sum(1 for i in issues if i.get("severity") == "MEDIUM")
    low = sum(1 for i in issues if i.get("severity") == "LOW")

    if high:
        return 49 - min(high * 3, 30) - min(medium, 8)
    if medium:
        return 79 - min(medium * 2, 20) - min(low, 6)
    if low:
        return 100 - min(low * 2, 12)
    return 100


def parse(body: bytes) -> dict[str, Any] | None:
    """결과 파일을 읽는다. 읽을 수 없으면 None 을 돌려준다.

    마크다운 안의 json 코드블록을 먼저 찾고, 없으면 파일 전체를 JSON 으로 본다.
    블록이 여럿이면 마지막 것을 쓴다. 앞부분 요약에 실수로 블록을 넣었을 때
    Komit 이 읽는 블록이 파일 끝에 있기 때문이다.
    """
    try:
        text = body.decode("utf-8")
    except UnicodeDecodeError:
        return None

    blocks = _JSON_BLOCK.findall(text)
    candidates = [blocks[-1]] if blocks else [text]

    for candidate in candidates:
        try:
            data = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(data, dict) and isinstance(data.get("issues"), list):
            return data

    return None


def remember(diagnosis_id: int, data: dict[str, Any]) -> None:
    """올린 결과를 담아 둔다."""
    issues = []

    for order, raw in enumerate(data["issues"]):
        code_id, code_name = _CATEGORY.get(raw.get("category"), (74, "기타"))
        issues.append(
            {
                "issueId": 90000 + order,
                "title": raw.get("title") or "제목 없음",
                "detail": raw.get("detail"),
                "location": raw.get("location"),
                "isInput": bool(raw.get("isInput")),
                "category": {"codeId": code_id, "codeName": code_name},
                "status": "UNRESOLVED",
                "severity": raw.get("severity") or "MEDIUM",
                "fixedVersion": None,
                "originIssueId": None,
                "qaCriteria": [],
            }
        )

    _store[diagnosis_id] = {
        "issues": issues,
        "readinessScore": readiness_score(data["issues"]),
        "scannedAt": data.get("scannedAt"),
        "techCategories": data.get("techCategories") or [],
        "questions": data.get("questions") or [],
    }


def stored(diagnosis_id: int) -> dict[str, Any] | None:
    """담아 둔 것이 있으면 돌려준다."""
    return _store.get(diagnosis_id)


def diagnosis(diagnosis_id: int, example: dict[str, Any]) -> dict[str, Any]:
    """진단 요약. 올린 것이 있으면 그 값으로 덮어쓴다."""
    kept = _store.get(diagnosis_id)
    if kept is None:
        return example

    merged = dict(example)
    merged["readinessScore"] = kept["readinessScore"]
    merged["scannedAt"] = kept["scannedAt"] or example["scannedAt"]
    merged["totalIssueCount"] = len(kept["issues"])
    merged["unresolvedIssueCount"] = len(kept["issues"])
    merged["resolvedSinceLastVersionCount"] = 0

    if kept["techCategories"]:
        project = dict(merged["project"])
        project["techCategories"] = [
            {"techCodeId": 900 + order, "codeName": name}
            for order, name in enumerate(kept["techCategories"])
        ]
        merged["project"] = project

    return merged


def issue_list(diagnosis_id: int, example: dict[str, Any]) -> dict[str, Any]:
    """결함 목록. 올린 것이 있으면 그 목록을 돌려준다."""
    kept = _store.get(diagnosis_id)
    if kept is None:
        return example

    return {
        "content": kept["issues"],
        "page": 1,
        "size": len(kept["issues"]),
        "totalElements": len(kept["issues"]),
        "totalPages": 1,
    }


def survey_questions(diagnosis_id: int, example: dict[str, Any]) -> dict[str, Any]:
    """마지막 설문. 올린 결과에 문항이 있으면 뒤에 이어 붙인다.

    고정 문항은 Komit 이 정하고, 만든 문항은 검사 결과에서 온다. (R-SCAN-14)
    """
    kept = _store.get(diagnosis_id)
    if kept is None or not kept["questions"]:
        return example

    merged = dict(example)
    questions = list(example.get("questions") or [])

    for order, raw in enumerate(kept["questions"][:5]):
        answer_type = raw.get("answerType") or "TEXT"
        options = raw.get("options") or []
        questions.append(
            {
                "questionId": 9000 + order,
                "content": raw.get("content") or "",
                "answerType": answer_type,
                "options": [
                    {
                        "optionId": 9500 + order * 10 + seat,
                        "content": option,
                        "isSelected": False,
                    }
                    for seat, option in enumerate(options)
                ],
            }
        )

    merged["questions"] = questions
    return merged
