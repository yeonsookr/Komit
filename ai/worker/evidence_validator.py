"""요약 항목이 가리킨 근거 발화가 그 전사에 실제로 있는지 확인한다.

`docs/ai/transcript-evidence-contract.md` 3절의 세 조건을 그대로 옮긴 결정적 단계다.
GMS 를 부르지 않고 문자열 유사도도 쓰지 않으므로 같은 입력은 언제나 같은 결과를 낸다.

확인하는 것은 순번의 **실존과 형식**뿐이다. 그 발화가 요약 문장의 의미를 뒷받침하는지,
당사자가 합의했는지는 보지 않는다. 통과를 계약 확정으로 읽지 않는다.
"""

from __future__ import annotations


def has_valid_evidence(indexes: object, utterance_count: int) -> bool:
    """계약 3절 세 조건을 모두 만족하는지 본다.

    비어 있지 않고, 모두 ``0 <= index < 발화 수`` 안의 정수이며, 서로 중복되지 않아야 한다.
    참·거짓은 파이썬에서 정수로 통하므로 따로 거른다.
    """
    if not isinstance(indexes, list) or not indexes:
        return False
    for index in indexes:
        if not isinstance(index, int) or isinstance(index, bool):
            return False
        if not 0 <= index < utterance_count:
            return False
    return len(set(indexes)) == len(indexes)


def validate(summary: dict, utterance_count: int) -> dict:
    """근거가 계약을 어긴 항목만 근거를 비우고 검토 대상으로 내린다.

    ``parse_summary`` 를 통과한 요약을 받는다. 항목 하나가 어겨도 나머지 항목의 근거는
    건드리지 않는다. 문장이 같다는 이유로 범위 밖 순번을 다른 발화로 바꾸거나 누락된 근거를
    만들어 넣지 않는다. 전사가 비면 어떤 항목도 유효한 근거를 가질 수 없다.

    ``reviewRequired`` 는 이 검증만이 정한다. 모델이 미리 적어 둔 값은 덮어쓴다.
    """
    if utterance_count < 0:
        raise ValueError("발화 수는 0 이상이어야 합니다")
    checked: dict = {}
    for section, rows in summary.items():
        marked = []
        for row in rows:
            kept = has_valid_evidence(row["evidenceUtteranceIndexes"], utterance_count)
            marked.append(
                {
                    **row,
                    "evidenceUtteranceIndexes": (
                        row["evidenceUtteranceIndexes"] if kept else []
                    ),
                    "reviewRequired": not kept,
                }
            )
        checked[section] = marked
    return checked
