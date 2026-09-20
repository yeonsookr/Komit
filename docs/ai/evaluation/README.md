# AI 골든셋 평가 기준

## 1. 범위와 데이터 등급

확인 문장, 한국어 전사, 컨설팅 요약을 같은 사례 형식으로 기록한다. `candidate`는 팀
문서에서 추출했거나 합성한 검토 전 사례이며 골든셋 정답이 아니다. `approved`는
담당자가 입력·기대 결과·이용 조건을 확인해 승인한 사례다. `candidate`는 평가
준비와 스키마 시험에는 사용할 수 있지만 정확도·합격률의 분모와 분자에 넣지 않는다.

`fixtures/`에는 사례 JSON과 필요할 때만 오디오 파일을 둔다. 각 사례에는 출처,
이용 조건, 익명화 상태, 데이터 등급, 분할(`development` 또는 `regression`)을
기록한다. 같은 원문·전사·의미상 동일한 변형은 두 분할에 동시에 넣지 않는다.
최종 회귀 검증용 `regression` 정답을 프롬프트 작성이나 모델 선택에 사용하지 않는다.

실제 상담 채팅·녹음·전사, 개인정보, 사용자 토큰, GMS 키를 저장소나 CI 산출물에
넣지 않는다. 실제 사례를 평가에 쓰려면 수집·이용 권한을 확인하고 식별 가능한
이름·연락처·계좌·주소·고유 링크를 제거한 뒤 별도 승인 절차를 거친다. 오디오는
텍스트와 달리 목소리 자체가 식별 정보일 수 있으므로 익명화된 텍스트만으로
대체할 수 있는 사례에는 오디오를 첨부하지 않는다.

외부 STT 자료는 전사 비교 또는 용어표 후보로만, 공개 요구사항·QA 자료는 형식
참고와 후보 평가 사례로만 사용한다. 공개 여부만으로 재배포·학습 권리를 추정하지
않는다. 이 자료를 `UPDATE`·`ADD` 분류용 SetFit 학습 데이터로 간주하지 않는다.
SetFit 비교에는 실제 상담 문장에 목표 라벨을 사람이 검수한 별도 데이터가 필요하다.

## 2. 사례 파일 계약

`cases.schema.json`이 JSON 사례의 구조를 정의한다. `fixtures/candidates.json`은
세 종류의 **합성 `candidate`** 예시이며 승인된 정답 수를 의미하지 않는다.

| 필드 | 의미 |
| --- | --- |
| `caseId` | 사례를 식별하는 안정적인 이름 |
| `task` | `qa_suggestion`, `transcription`, `summary` 중 하나 |
| `status` | `candidate` 또는 `approved` |
| `split` | 개발용 또는 최종 회귀 검증용 |
| `source` | 출처 종류·참조·이용 조건·익명화 상태 |
| `input` | 작업별 고정 입력. 개인정보 없는 텍스트 또는 승인된 오디오 참조 |
| `expected` | 사람이 검수할 기대 결과와 판정 메모 |

`approved` 사례에는 정답 승인자와 승인 시각을 기록한다. 원본 출처의 이용 조건이
불명확하거나 정답이 미확정이면 `candidate`로 유지한다. 모델이 생성한 답을 그대로
`expected`에 넣고 `approved`로 승격하지 않는다.

오디오가 필요한 전사 사례는 사례 JSON과 별도 파일로 두고 상대 경로와 파일
체크섬을 기록한다. 허용된 오디오만 포함하며, 실제 음성 자료를 `candidate` 예시의
빈 참조에 맞춰 임의로 추가하지 않는다.

## 3. 지표와 판정

모든 지표는 작업·분할·데이터 등급별로 사례 수와 함께 보고한다. 구조 검증 실패는
점수 계산 전에 `schemaFailureCount`로 기록하고 해당 사례를 성공으로 계산하지
않는다. 데이터가 없어 분모가 0이면 수치를 `null`로 보고한다.

| 작업 | 기계 판정·기록 지표 | 사람 검토 |
| --- | --- | --- |
| 확인 문장 | `qaFormatPassRate`: `R-REQ-04`의 3건·1~500자·`testCase` 형식 통과 비율. `qaDuplicateRate`: 동일 출력 안 중복 문장 비율 | `qaExecutableRate`: 의뢰인이 직접 실행할 수 있는 문장 비율. `qaDecidableRate`: 통과·실패를 판정할 수 있는 문장 비율 |
| 전사 | `sttCerRaw`, `sttWerRaw`, `sttCerNormalized`, `sttWerNormalized`: 원문·정규화문을 각각 같은 기준의 정답과 비교. `sttTermErrorCount`, `sttNumberErrorCount`, `sttAmountErrorCount`: 개발 용어·숫자·금액 오류 건수 | 애매한 발화와 정규화가 의미를 바꿨는지 확인 |
| 요약 | `summarySchemaPassRate`: 네 배열과 항목 형식 통과 비율. `summaryChangeKindAccuracy`: 승인 라벨에 대한 `UPDATE`·`ADD` 정확도. `summaryEvidenceIndexValidRate`: 내부 순번 실존·중복·범위 검증 통과 비율. `summaryReviewFlagAccuracy`: 검토 필요 표시의 승인 라벨 일치 비율 | `summaryOmissionCount`: 누락된 결정. `summaryFabricatedDecisionCount`: 발화에 없는 결정. 근거 발화가 실제 의미를 뒷받침하는지도 확인 |

확인 문장 후보는 저장 전 결과이며, 후보를 보여주는 것만으로 요청서에 반영되지
않는다(`R-REQ-04`). 전사의 문자·단어 오류율은 삽입·삭제·치환 수를 정답의 문자·단어
수로 나눈다. 한국어 단어 분할 기준은 평가 실행에서 고정하고 결과에 기록한다.
요약의 내부 근거 순번은 `../transcript-evidence-contract.md`의 같은 처리 입력에만
유효하다. 순번이 존재한다는 것과 내용이 충분한 근거라는 것은 별개로 평가한다.
무료 상담 채팅의 계약 초안은 컨설팅 세션 전사 요약과 섞어 채점하지 않는다.

## 4. 회귀 실행 결과 계약

후속 79번 회귀 실행은 입력 사례 파일·모델/프롬프트 설정·실제 출력·지표·실패 목록을
한 실행 묶음으로 남긴다. 결과 파일은 `run.json`과 `failures.json`으로 정한다.
`run.json`에는 `runId`, `createdAt`, `caseFile`, `modelRevision`, `gmsModel`,
`promptRevision`, `split`, `approvedCaseCount`, `candidateCaseCount`,
`schemaFailureCount`, 위 지표 이름별 값과 분모를 담는다. `failures.json`에는
`caseId`, `task`, `failureType`, `detail`을 담되 원본 상담 내용이나 비밀값은
기록하지 않는다. 모델의 확률적 출력은 정답 문자열 완전 일치만으로 실패시키지
않고 구조 오류·판정 오류·사람 검토 결과를 분리해 보고한다.

합격 수치, CPU 처리 시간 및 최대 메모리 한도는 아직 확정하지 않는다. 실제 장비와
승인 사례를 확인한 뒤 관련 이슈에서 정하며, 그 전에는 측정값만 보고하고
자동 합격으로 표시하지 않는다. 79번은 이 계약의 지표 이름과 파일 형식을 읽어
회귀 결과를 만들되, 이 문서는 실행기나 배포 구성을 만들지 않는다.
