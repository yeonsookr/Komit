# AGENTS.md — ai

`ai/`에서 작업하는 에이전트가 따르는 규칙입니다. 저장소 루트 `AGENTS.md`를 전제로 합니다. 다른 파트와 달리 `ai`에는 대응하는 `docs/*_CONVENTIONS.md`가 없어 코드 컨벤션도 이 문서에 있습니다.

이 폴더에는 성격이 다른 둘이 들어 있습니다.

| 갈래 | 무엇인가 | 어떻게 재나 |
| --- | --- | --- |
| `ai/worker/` | 파이썬 모듈. GMS를 호출한다 | `unittest`, 고정 응답 |
| `ai/komit-scan/` | 사전점검 지시문. 실행 코드가 없다 | 정답표와 회차 기록 |

2~4절은 둘 다에, 5·6절은 각 갈래에 적용합니다.

## 1. 문서 지도

값이나 형식이 필요하면 추측하지 말고 아래에서 찾습니다. 없으면 없다고 말합니다.

| 문서 | 담는 것 | 보는 때 |
| --- | --- | --- |
| `ai/worker/README.md` | 모듈별 입출력과 실행 명령 | 돌려 볼 때 |
| `ai/worker/docs/job-contract.md` | Service가 AI를 부르는 방식, 실패 두 갈래, 타임아웃 | 밖에서 부르는 경로를 붙일 때 |
| `docs/ai/transcript-evidence-contract.md` | `utteranceIndex` 부여 규칙, 근거 검증 세 조건 | 전사·요약의 근거를 다룰 때 |
| `docs/ai/summary-change-contract.md` | 요청서 변경 유형(`UPDATE`·`ADD`) | 컨설팅 요약의 `changes`를 다룰 때 |
| `docs/ai/chat-contract-draft.md` | 상담 채팅 기반 계약 초안 | 계약 초안을 다룰 때 |
| `docs/ai/evaluation/README.md` | 골든셋 사례 등급과 평가 기준 | 새 작업을 회귀에 올릴 때 |
| `docs/ai/evaluation/cases.schema.json` | 사례 형식 | 골든셋에 케이스를 더할 때 |
| `docs/RULES.md` | 기한·금액·횟수·상한 | 값을 코드에 옮길 때 |

`komit-scan` 쪽 문서는 6절에 있습니다.

## 2. 근거를 못 대면 내놓지 않는다

검사 결과와 요약은 계약과 대금으로 이어집니다. 없는 것을 만들어내면 의뢰인이 필요 없는 일을 개발자에게 맡깁니다.

- `worker`는 입력 발화의 `utteranceIndex`로만 근거를 댑니다. 발화에 없는 합의·금액·일정을 만들지 않습니다. 문장이 비슷하다는 이유로 범위 밖 순번을 다른 발화로 바꾸거나, 누락된 근거를 지어내지 않습니다.
- `komit-scan`은 파일 경로와 줄 번호를 못 대면 문제로 올리지 않습니다. (`R-SCAN-12`)

근거의 범위는 그 처리에 주어진 입력뿐입니다. URL, 외부 문서, 검색 결과로 대체하지 않습니다.

## 3. 판정할 수 없으면 비우지 말고 표시한다

빈 결과를 내면 사람이 볼 자리가 사라집니다. 모른다는 사실을 결과 안에 남깁니다.

- `worker`는 근거 검증에 걸린 항목만 `reviewRequired=true`로 내립니다. 나머지 항목은 그대로 둡니다. 항목 하나 때문에 응답 전체를 실패시키지 않습니다.
- `komit-scan`은 코드로 판정할 수 없는 것을 「미검사 범위」와 「추가 확인 질문」으로 넘깁니다. (`R-SCAN-11`)

표시를 통과했다는 것이 사람의 승인이나 계약 확정을 뜻하지 않습니다. 그렇게 읽히는 문구를 결과에 쓰지 않습니다.

## 4. 경계 밖을 정하지 않는다

AI가 정하는 것은 **결과의 내용**뿐입니다. 아래는 전부 밖에서 정해집니다. 값이 필요하면 새로 정하지 말고 인용합니다.

| 무엇 | 누가 정하나 |
| --- | --- |
| HTTP 상태와 오류 코드 | Service. AI는 실패의 갈래만 구분합니다 |
| 입력 상한 | `R-CON-18` — 세션 녹음 90분·24kbps 고정. FE가 강제합니다 |
| 처리 시작 시점 | BE. 세션 녹음은 종료 뒤에만 올라옵니다 |
| 재처리와 중복 | BE의 멱등 규칙 |
| 저장 위치와 형식 | Service ERD |
| 기한·금액·횟수 | `docs/RULES.md`. 규칙 번호를 주석이나 상수 이름에 남깁니다 |

AI 코드는 Service DB를 읽지도 쓰지도 않습니다. 파일 본문은 저장소에서 직접 읽습니다. Service를 통과시키지 않습니다.

밖으로 내보내지 않는 것도 여기 있습니다. 사용자 JWT, Service DB 연결값, GMS 키를 작업 입력에 싣지 않습니다. 키와 토큰을 코드·로그·커밋에 남기지 않습니다. `ai/worker/.env`는 gitignore에 있습니다. `komit-scan`은 검사 대상의 소스 코드를 서버로 보내지 않습니다. 결과 파일만 올립니다.

## 5. worker — 파이썬

처음 돌릴 때는 저장소 루트의 `.venv`를 씁니다. `ai/worker/`에 따로 만들지 않습니다. `ai/worker/.env.example`을 같은 폴더의 `.env`로 복사하고 `GMS_API_KEY`를 채웁니다. 나머지 세 값은 예시 그대로 두면 됩니다.

모듈은 평면에 둡니다. `ai/worker/<작업>.py`와 `ai/worker/tests/test_<작업>.py` 한 쌍입니다. 파일이 몇 개 늘었다고 하위 디렉터리를 파지 않습니다. `scripts/`는 모듈이 아니라 한 번 돌리고 마는 실행 스크립트 자리입니다. `compare_stt.py`가 `222` 모델 비교용으로 거기 있습니다. 다른 모듈이 부를 것이면 `scripts/`에 두지 않습니다.

`app.py`는 HTTP 경계입니다. 받아서 모듈로 넘기고 실패를 상태 코드로 바꾸는 일만 하고, 판단은 모듈에 둡니다. 새 작업을 더할 때 로직을 여기에 쓰지 않습니다.

실패는 두 갈래뿐입니다. `GmsUnavailableError`(상류에 닿지 못함)와 `InvalidGmsResponseError`(출력이 계약과 다름)입니다. 세 번째를 만들지 않습니다.

**구조가 깨진 것과 값이 틀린 것을 구분합니다.** 키 집합·필드 구성·타입이 어긋나면 쓸 수 없는 출력이라 예외로 막습니다. 값이 실재하지 않거나 범위를 벗어난 것은 그 항목만 내립니다.

```python
# 구조가 깨진 것 — 쓸 수 없다
if not isinstance(row["evidenceUtteranceIndexes"], list):
    raise InvalidGmsResponseError("evidenceUtteranceIndexes 가 배열이 아닙니다")

# 값이 틀린 것 — 그 항목만 내리고 나머지는 살린다
{**row, "evidenceUtteranceIndexes": [], "reviewRequired": True}
```

테스트는 모델을 부르지 않는 가짜 클라이언트에 고정 응답을 넣어 돌립니다. `complete()` 하나만 가진 작은 클래스면 되고, 세 모듈이 같은 모양을 쓰고 있습니다. 메서드 이름은 한글로 무엇이 일어나는지 적습니다(`test_근거_순번이_중복되면_실패한다`). `compare_stt` 쪽만 영문인데 먼저 쓴 것이고, 새로 더하는 것은 한글로 맞춥니다.

출력 토큰 한도는 모듈 상수로 박지 말고 호출에서 정합니다. 작업마다 결과 길이가 다릅니다. 잡은 값은 실측 근거를 주석에 함께 남깁니다.

결과는 stdout에 JSON으로, 오류와 사용량은 stderr로 냅니다. `logging`을 들이지 않았습니다.

외부 라이브러리를 더하기 전에 표준 라이브러리로 되는지 봅니다. GMS 호출은 `urllib`로 하고 있어 HTTP 클라이언트를 따로 들이지 않았습니다. HTTP **서버**는 표준 라이브러리로 안 됩니다 — `http.server`는 문서가 프로덕션용이 아니라고 적어 둔 시험용이고 동시 요청을 못 받습니다. 그래서 `mock`과 같은 조합(`fastapi` + `uvicorn[standard]`)을 씁니다.

의존성 파일은 둘입니다. `requirements.txt`는 서비스가 뜨는 데 필요한 것만 담고 이미지에 들어갑니다. `scripts/requirements.txt`는 STT 비교 도구용이며 `faster-whisper`가 모델 런타임까지 끌고 와 이미지에 넣지 않습니다.

## 6. komit-scan — 지시문

이 폴더는 자체 문서를 갖고 있습니다. 여기서 규칙을 새로 만들지 말고 그쪽을 따릅니다.

| 무엇 | 어디 |
| --- | --- |
| 구조, 검사 범위, 환각을 막는 장치 | `komit-scan/PLAN.md` |
| 품질을 재는 방법과 시험 대상 만드는 법 | `komit-scan/fixtures/README.md` |
| 결과 파일 형식 | `komit-scan/schema/komit-result.schema.json` |

`.claude-plugin/`과 `skills/`는 플러그인 규격이 정한 이름이라 바꾸지 않습니다.

## 7. 검증 방법

`worker`는 표준 `unittest`로 돕니다.

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s ai\worker\tests -p "test_*.py"
```

단위 테스트는 고정 응답을 쓰므로 GMS 키 없이 돌고 외부 요청을 하지 않습니다. 모델을 실제로 부르는 확인은 테스트에 넣지 않습니다. 결과가 매번 달라 회귀 판정에 쓸 수 없습니다.

`komit-scan`은 회차를 `fixtures/<대상>/runs/`에 쌓고 `RUNS.md` 표로 비교합니다. 지시문을 쓴 세션에서 돌리면 시험이 되지 않습니다. 새 세션에서 돌리고, 검사하는 쪽은 `EXPECTED.md`를 읽지 않습니다.

포매터는 쓰지 않습니다. 도입하려면 `worker`와 CI를 함께 정합니다.

CI에 `ai` 잡이 없습니다. `.gitlab-ci.yml`의 build·test는 `service`·`escrow`·`fe`뿐이고, `convention` 스테이지가 브랜치 파트로 `ai`를 허용할 뿐입니다. 배포 경로를 만들 때(`N3`) 함께 붙입니다. 그때까지 테스트는 로컬에서 돌리고 결과를 MR에 적습니다.
