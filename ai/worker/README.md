# AI 로컬 실행

이 경로에는 두 가지가 있다. Service 가 부르는 AI 서비스(`app.py` 와 그 뒤의 모듈들)와,
한 번 돌리고 마는 실행 스크립트(`scripts/`)다. 배포 방식은 `deploy/k3s` 에 있다.

## AI 서비스로 띄우기 (S15P21A201-236)

Service 가 부르는 HTTP 경계다. 아래 모듈들을 경로 뒤에 붙여 두고, 받아 넘기고 실패를 상태
코드로 바꾸는 일만 한다. 판단은 각 모듈에 있다.

| 경로 | 뒤에 있는 것 |
| --- | --- |
| `POST /qa-suggestions` | `qa_suggestion.py` |
| `POST /contract-draft` | `contract_draft.py` |
| `POST /consulting-summary` | `consulting_summary.py` + `evidence_validator.py` |
| `GET /health` | 기동·생존 확인. 모델을 부르지 않는다 |

| 실패 | 상태 | `code` |
| --- | --- | --- |
| 모델에 닿지 못함 | `503` | `AI_SERVICE_UNAVAILABLE` |
| 계약에 맞는 결과를 못 만듦 | `502` | `AI_RESPONSE_INVALID` |
| 받은 요청이 계약과 다름 | `422` `400` `404` `405` | `AI_REQUEST_INVALID` |

본문은 언제나 `code` 와 `message` 두 필드다. 자세한 것은 `docs/job-contract.md` 에 있다.

`GMS_API_KEY` 가 없으면 **기동하지 않는다.** 키 없이 떠 있으면 모든 요청이 `503` 이 되는데
배포는 성공한 것처럼 보인다.

```powershell
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000 --app-dir ai\worker
.\.venv\Scripts\python.exe -m unittest discover -s ai\worker\tests -p "test_app.py"
```

의존성 파일이 둘이다. `requirements.txt` 는 서비스가 뜨는 데 필요한 것만 담고 이미지에
들어간다. STT 비교 스크립트는 `scripts/requirements.txt` 를 따로 쓴다 — `faster-whisper` 가
모델 런타임까지 끌고 와 이미지가 GiB 단위로 커진다.

부르는 쪽은 Service 하나다. 브라우저도 FE Route Handler 도 이 서버를 직접 부르지 않는다.

## 확인 문장 후보 생성 (S15P21A201-223)

`R-REQ-04`에 따라 요구사항 문장 한 건에 대해 저장 전 확인 문장 후보 3건을 만든다.
각 후보는 `testCase`만 가지며 1~500자여야 한다. 후보를 보여주는 것만으로 요청서에
저장되지 않는다. 실제 `issueId` 조회·소유권 확인과 저장은 Service 담당 범위다.

GMS 안내의 OpenAI Chat Completions 경로를 사용한다. 초기 시험 모델은 비용이 낮은
`gpt-4.1-nano`다. `ai/worker/.env.example`을 `ai/worker/.env`로 복사하고 본인의
`GMS_API_KEY`를 입력한다. `.env`는 Git에서 제외된다. 키를 채팅·코드·로그에 남기지 않는다.
`GMS_API_BASE_URL`, `GMS_MODEL`은 같은 파일이나 환경변수로 바꿀 수 있으며 환경변수가
우선한다.

**출력 토큰 한도는 설정이 아니라 호출 인자다.** `complete(messages, max_tokens=...)`에
반드시 넘겨야 하며 기본값이 없다. 작업마다 필요한 길이가 달라 한 값으로 묶을 수 없고,
안 주고 부를 수 있게 두었더니 두 작업이 그것을 잊어 응답이 문장 중간에서 끊겼다. 반쯤 온
JSON은 파싱 실패로만 보여 원인이 모델에 있는 것처럼 읽힌다. 그래서 잊을 수 있는 자리를
없앴고, `complete`는 `finish_reason`이 `length`면 잘렸다고 따로 말한다.

| 작업 | 한도 | 근거 (2026-09-24 실측) |
| --- | --- | --- |
| 확인 문장 | 1024 | 계약이 3건 × 500자다. 한국어 0.5토큰/자로 약 750토큰. 실제 응답이 235~266토큰까지 나왔고 옛 기본값 256으로는 8회 중 6회가 잘렸다 |
| 계약 초안 | 2048 | 개수 상한이 없어 계산으로 못 낸다. 요구사항 6건·마일스톤 2~3개가 174~255토큰이었다 |
| 컨설팅 요약 | 2048 | 짧은 전사가 151~165토큰. 90분 세션은 오디오 fixture가 없어 아직 못 쟀다 |

설정을 읽는 `read_local_env`는 `.env` 값에 환경변수 전체를 합쳐 돌려준다. 쓰는 쪽에서
위 세 항목만 꺼내 쓰므로 동작에는 문제가 없다. 다만 이 반환값을 통째로 로그나 오류
메시지에 담으면 설정과 무관한 환경변수까지 함께 남는다. **로그를 붙일 때 설정 읽기를
라이브러리로 바꾸면서 함께 정리한다.** 지금 쓸 항목 목록을 코드에 따로 두면 `.env`와
그 목록 두 곳을 같이 고쳐야 하고, 한쪽만 고쳐도 오류 없이 값이 조용히 무시되므로
지금은 두지 않는다.

```powershell
Copy-Item ai\worker\.env.example ai\worker\.env
.\.venv\Scripts\python.exe ai\worker\qa_suggestion.py
.\.venv\Scripts\python.exe -m unittest discover -s ai\worker\tests -p "test_qa_suggestion.py"
```

키가 없는 단위 테스트는 외부 요청을 하지 않는다. 잘못된 JSON, 후보 수·길이·중복·필드
오류는 실패로 처리한다. 문장이 실제로 실행 가능하고 통과 여부를 판단할 수 있는지는
사람이 검토해야 한다. 실제 키 호출의 결과와 모델 품질은 별도로 확인한다.

실제 호출에서 확인한 결함이 세 가지다. 프롬프트의 지시어를 답변 문장에 그대로 베껴
쓴다. 요구사항에 없는 안내 메시지 문구를 지어낸다. 잠기지 않은 상태에서 잠금이 풀렸는지
확인하라는 식으로 앞뒤가 뒤집힌 문장을 낸다. 출력이 길어진 응답일수록 심했다. 프롬프트
수정은 나아졌는지 판정할 골든셋을 만든 뒤에 한다. 위 세 가지가 골든셋에서 떨어뜨려야
할 항목이다.

## 무료 상담 계약 초안 (S15P21A201-224)

`docs/ai/chat-contract-draft.md`에 따라 무료 상담방 하나의 메시지를 계약 유형별 초안으로
정리한다. `223`의 GMS 클라이언트를 그대로 쓴다.

| 구분 | 내용 |
| --- | --- |
| 입력 | `chatRefId` `authorRole`(`CLIENT`·`DEVELOPER`) `createdAt` `content` + 개발 계약이면 기존 요구사항 `issueId` `title` |
| 컨설팅 출력 | `topic`(1~255자) `detail`(근거 없으면 `null`) |
| 개발 출력 | `issueIds` `milestones[].title` `milestones[].qaCriteria` `rejectedIssues[].issueId` `.reason` |
| 확인 문장 | `R-REQ-01` `R-REQ-04` `R-QA-01` `R-QA-05`. 작업마다 하나 이상, 문장마다 1~500자 |
| 진행 안 함 | `proceed=false` → 초안 `None`. **오류가 아니다** |
| 생성 실패 | 형식·식별자 검증 위반은 `InvalidGmsResponseError`. 빈 계약서를 대신 만들지 않는다 |

일정·기간·금액·인계물·전달 메시지는 개발자 입력 필드다. 모델이 이 필드를 함께 내놓으면
초안 전체를 실패로 처리한다. `issueIds`와 `rejectedIssues[].issueId`는 입력으로 받은
요구사항 안에서만 고르고 같은 식별자를 양쪽에 함께 넣지 않는다. 서로 다른 `chatRefId`가
섞이거나 작성 시각 순이 아닌 입력은 호출 전에 거른다.

방 전환 성공 여부(`R-MATCH-03`), 채팅 저장·조회, 계약 제출·서명은 이 모듈 밖이다.

```powershell
.\.venv\Scripts\python.exe ai\worker\contract_draft.py <입력 JSON 경로>
.\.venv\Scripts\python.exe -m unittest discover -s ai\worker\tests -p "test_contract_draft.py"
```

입력 JSON은 `targetType`, `messages`, 개발 계약이면 `issues`를 가진다. 단위 테스트는
고정 응답을 쓰므로 키 없이 실행되고 외부 요청을 하지 않는다. 실제 채팅 저장·조회와
전환 트리거는 BE 연동 전까지 확인할 수 없다.

## 컨설팅 요약과 근거 검증 (S15P21A201-225, S15P21A201-226)

`docs/ai/summary-change-contract.md`와 `docs/ai/transcript-evidence-contract.md`에 따라
컨설팅 세션의 병합 전사를 네 갈래로 정리하고, 각 항목이 가리킨 근거 발화가 그 전사에
실제로 있는지 확인한다. 무료 상담 계약 초안(`224`)과는 다른 입력·다른 출력이다.

| 구분 | 내용 |
| --- | --- |
| 입력 | 발화마다 `utteranceIndex` `speaker`(`CLIENT`·`DEVELOPER`) `rawText` `normalizedText` + 기존 요구사항 `issueId` `title` |
| 출력 | `discussed` `decided` `nextActions` `changes` 네 배열. 항목마다 `title` `detail` `evidenceUtteranceIndexes` `reviewRequired` |
| 변경 후보 | `changes`는 위 네 개에 더해 `kind`(`UPDATE`·`ADD`)와 `issueId`를 갖는다. `UPDATE`는 입력으로 받은 요구사항 안에서만 고른다 |
| 근거 검증 | 목록이 비지 않고, 모두 `0 <= index < 발화 수`의 정수이며, 한 항목 안에서 중복되지 않아야 한다 |
| 검증 실패 | **그 항목만** 근거를 비우고 `reviewRequired=true`로 내린다. 다른 항목은 그대로 둔다 |
| 생성 실패 | 키 집합·필드 구성이 어긋나면 `InvalidGmsResponseError`. 순번 내용이 틀린 것은 여기에 넣지 않는다 |

`evidenceUtteranceIndexes`와 `reviewRequired`는 AI 내부값이며 Mock·공개 API·Service ERD에
추가하지 않는다. 순번은 해당 처리 입력 안에서만 유효하고 영속 ID로 쓰지 않는다. 검증은
순번의 실존과 형식만 보며, 그 발화가 요약 문장을 뒷받침하는지나 당사자가 합의했는지까지
보증하지 않는다. 문장이 같다는 이유로 범위 밖 순번을 다른 발화로 바꾸거나 누락된 근거를
만들어 넣지 않는다.

요약은 GMS를 부르지만 근거 검증은 부르지 않는다. 문자열 유사도나 모델의 자기평가로
통과시키지 않으므로 같은 입력은 언제나 같은 결과를 낸다.

```powershell
.\.venv\Scripts\python.exe ai\worker\consulting_summary.py <입력 JSON 경로>
.\.venv\Scripts\python.exe -m unittest discover -s ai\worker\tests -p "test_consulting_summary.py"
.\.venv\Scripts\python.exe -m unittest discover -s ai\worker\tests -p "test_evidence_validator.py"
```

입력 JSON은 `utterances`와, 개발 요구사항이 있으면 `requirements`를 가진다. 단위 테스트는
고정 응답을 쓰므로 키 없이 실행되고 외부 요청을 하지 않는다. 실제 녹음 전사와의 연결,
세션 종료 시 자동 실행, 결과 저장 위치는 이 모듈 밖이며 BE·`230` 계약에 따라 붙인다.

## N7: 클로바 자동 전사와 STT 시험 비교

### 모델과 실행 엔진 선택

이번 단계의 필수 조건은 외부 API 사용료 없이 자체 장비에서 실행하고, 상담 음성을 외부
서비스로 보내지 않으며, 짧은 일정 안에 한국어와 영어 개발 용어가 섞인 녹음을 처리하는 것이다.

| 후보 | 운영 방식 | 장점 | 이번 단계 판단 |
| --- | --- | --- | --- |
| OpenAI Whisper | 공개 모델을 자체 실행 | 다국어 음성 인식 모델과 크기별 가중치가 공개되어 있고 별도 학습 없이 실행 가능 | 채택 |
| Meta wav2vec 2.0 계열 | 공개 모델을 자체 실행 | 자체 데이터로 세부 조정하는 연구·특화 작업에 유연함 | 현재 일정에는 한국어 체크포인트 선정·후처리·품질 검증 범위가 커서 보류 |
| Google·Azure STT | 외부 클라우드 API | 서버에 모델을 직접 설치·관리하지 않아도 됨 | 사용량에 따른 비용과 외부 전송이 필수 조건에 맞지 않아 제외 |
| Clova Speech | 외부 상용 서비스 | 한국어 STT 비교 기준으로 활용 가능 | 운영 모델이 아니라 현재 확보한 클로바노트 전사를 임시 비교 기준으로만 사용 |

실행에는 Whisper의 모델 가중치를 그대로 사용하는 `faster-whisper`를 선택했다. 이는 별도 STT
모델이 아니라 CTranslate2 기반 Whisper 실행 엔진이다. 공식 구현보다 적은 메모리와 INT8 실행을
지원하고 CPU·GPU 양쪽을 선택할 수 있어 현재 로컬 시험과 이후 배포 환경 변경에 대응하기 쉽다.
근거는 [Whisper 공식 저장소](https://github.com/openai/whisper),
[Whisper 모델 카드](https://github.com/openai/whisper/blob/main/model-card.md),
[faster-whisper 저장소](https://github.com/SYSTRAN/faster-whisper),
[wav2vec 2.0 논문](https://arxiv.org/abs/2006.11477),
[Google STT 과금 문서](https://cloud.google.com/speech-to-text/pricing),
[Azure Speech 과금 문서](https://azure.microsoft.com/pricing/details/speech/)를 따른다.

현재 노트북의 GPU 사용 가능 여부와 별개로 이번 비교는 `CPU·INT8`로 고정한다. 이는 GPU 서버를
확보하지 못한 배포 환경에서도 처리 가능한지 확인하는 최소 운영 기준선이다. GPU 서버 도입이
확정되면 같은 평가셋과 설정을 사용해 `CUDA` 조건을 별도로 측정한다. 따라서 이번 결과를 GPU
성능이나 최종 배포 사양으로 해석하지 않는다.

### 실행 방법

PowerShell에서 저장소 루트로 이동한 뒤 실행한다. Python 3.9 이상과 인터넷 연결이 필요하다.
`python --version`으로 설치를 확인한다. 모델 파일은 첫 실행 시 자동 다운로드되며
저장소에 넣지 않는다.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r ai\worker\scripts\requirements.txt
.\.venv\Scripts\python.exe ai\worker\scripts\compare_stt.py --input-dir "담당자 개인 개발 관련 녹취본 폴더 경로"
```

이미 가상환경·패키지를 설치했다면 마지막 줄만 실행한다. 폴더 바로 아래에서 음성과
**파일 이름이 같은 txt**를 짝지어 비교한다. txt가 없는 음성은 건너뛴다.
실행 전에 짝이 맞는 파일만 확인하려면 같은 명령 끝에 `--list-only`를 붙인다.
이때 모델은 실행하지 않고 결과 파일도 만들지 않는다. 실행할 때도 모델 로드 전에
비교 대상 파일 이름을 모두 표시한다.
기본 실행은 각 파일의 처음 300초를 `base`·`small`·`medium`으로 비교한다. 2분은 설치와
동작을 확인하는 빠른 시험에는 충분하지만 모델 선택 근거로는 발화량이 적어, 서로 다른
화자와 발화 상황이 더 포함되도록 5분을 기본 구간으로 사용한다. `medium`은
첫 실행 때 추가로 다운로드되므로 시작 시간이 더 걸리고 로컬 모델 캐시 공간을 사용한다.
특정 모델만 시험하려면 `--models base small`처럼 이름을 지정한다. 파일 전체를 비교하려면
마지막에 `--seconds 0`을 붙이되, 긴 녹음 여러 건은 CPU에서 오래 걸릴 수 있다.
모델은 각 파일마다 다시 로드하지 않는다. 모델 로드/다운로드 시간과 전사 시간을 분리해 기록한다.

결과는 입력 폴더의 `stt_results/실행시각/`에 저장된다. `summary.csv`는 파일별 모델 시간·
CER와 보조 유사도를 한 표로 보여준다. `summary.json`에는 실행 시각, OS, CPU, 물리·논리 코어 수,
총 RAM, 입력 디스크의 전체·실행 전후 여유 공간, Python·패키지 버전, 고정 실행 설정도
기록한다. 파일별로 평가한 음성 길이, 전사 시간, 실시간 배수(RTF), 처리 배속,
프로세스 평균·최대 CPU 사용률, 프로세스 최대 메모리와 클로바 기준 CER를 남긴다.
모델이나 파일 하나가 실패해도 나머지 비교는 계속하며 실패 위치와 원인을 결과에 남긴다.
CER는 두 전사에서 공백·구두점 등
특수기호를 제거하고 한글·영문·숫자만 남긴 뒤 `(삽입+삭제+치환)/클로바 글자 수`로 계산한다.
클로바가 사람 정답이 아닌 자동 전사이므로 절대 정확도가 아니라 비교 지표다.
각 음성 이름의 하위 폴더에 모델별 전사 원문을 둔다. 이 결과와
음성·클로바 txt는 저장소 밖에 두며 Git에 올리지 않는다.

### 모델 선정 결과

담당자 개인 개발 관련 녹취본 7건에서 각각 처음 5분을 사용해 총 35분을 비교했다. 동일한
노트북에서 `CPU·INT8·한국어·beam size 5` 조건을 모든 모델에 적용했다. 가중 CER는 일곱
결과의 편집 거리 합을 기준 글자 수 합으로 나눈 값이고, 평균 CER는 파일별 CER의 단순 평균이다.

| 모델 | 가중 CER | 평균 CER | 5분당 평균 전사 | 실시간 대비 처리 배속 | 최대 프로세스 메모리 | 파일별 최저 CER 횟수 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `base` | 35.41% | 37.71% | 42.34초 | 7.09배 | 5,919 MiB | 0/7 |
| `small` | 23.14% | 24.34% | 86.86초 | 3.45배 | 6,112 MiB | 2/7 |
| `medium` | 20.83% | 22.68% | 218.16초 | 1.38배 | 6,708 MiB | 5/7 |

현재 CPU 운영 기준 모델은 **`small`**로 선정한다. `base`보다 가중 CER가 12.27%p 낮으면서도
5분 음성을 평균 86.86초에 처리했다. `medium`은 `small`보다 가중 CER가 2.31%p 낮지만 평균
전사 시간이 약 2.51배이고, 7건 중 2건에서는 `small`의 CER가 더 낮았다. GPU 서버가 없는
상황에서 상담 종료 후 계약 초안·요약 생성까지 이어지는 대기 시간을 고려하면 `small`의
품질과 처리시간 균형이 가장 적절하다. GPU 배포 환경이 확정되면 같은 평가셋으로 `small`과
`medium`을 다시 비교한다.

클로바 자동 전사는 사람이 검수한 정답이 아니므로 이 결과는 절대 정확도나 합격 기준이 아니다.
현재 모델 크기를 정하기 위한 상대 비교 근거이며, 향후 사람 검수 정답지와 개발 용어 평가셋으로
재검증한다.

### 현재 범위와 후속 작업

- WER와 개발 용어·숫자·금액별 오류 분석은 일정과 검수 정답지 제약으로 제외했다. 향후
  테스트용 녹취 데이터와 사람이 검수한 정답지를 만든 뒤 STT 고도화 단계에서 수행한다.
- 전사 인터페이스와 오프라인 고정 응답은 Service→AI 호출 계약 및 후속 연동 코드가 필요한
  시점에 구현한다. AI 내부 인터페이스는 AI 범위이고, 실제 호출 계약은 BE·AI 공동 범위다.
- 모델 revision과 체크섬은 배포 환경과 모델 저장·배포 방식이 정해질 때 고정한다. 현재는
  모델 크기를 비교·선정하는 단계이므로 모델 이름과 실행 라이브러리 버전만 기록한다.
- Whisper 코드와 모델 가중치, `faster-whisper`는 MIT License로 공개되어 있다. 현재 평가는
  AI 담당자 본인의 개발 관련 녹취를 로컬에서만 사용하며, 저장소에는 넣지 않는다. 향후
  별도의 테스트용 녹취 데이터로 교체한다.

### 기록 지표와 목적

| 지표 | 기록하는 이유 |
| --- | --- |
| OS·CPU·물리/논리 코어·RAM | 다른 장비의 결과와 구분하고 동일 조건 재실행 여부를 판단한다. |
| Python·라이브러리 버전 | 버전 차이로 발생하는 속도·호환성 차이를 재현한다. |
| 입력 경로의 디스크 전체/여유 공간 | 모델 캐시와 결과 파일 저장 중 용량 부족 가능성을 확인한다. |
| 모델 로드 시간 | 최초 다운로드·초기화 비용과 실제 전사 시간을 분리한다. |
| 전체/평가 음성 길이·파일 크기 | 동일한 입력 구간을 비교했는지 확인한다. |
| 전사 시간 | 사용자가 실제로 기다리는 처리 시간을 비교한다. |
| RTF·실시간 대비 처리 배속 | 길이가 다른 음성도 같은 기준으로 비교하고 CPU 운영 가능성을 판단한다. |
| 평균/최대 프로세스 CPU 사용률 | 모델이 사용하는 연산량과 코어 활용 정도를 비교한다. |
| 최대 프로세스 메모리 | 모델별 최소 RAM과 동시 처리 가능성을 판단한다. |
| 클로바 기준 CER | 공백·구두점 차이를 제외한 글자 단위 품질을 모델 선정의 주 지표로 사용한다. |
| 보조 문자 유사도 | CER에 익숙하지 않은 사람이 결과 일치 정도를 직관적으로 확인한다. 모델 순위는 CER로 판단한다. |
| 편집 거리·기준 글자 수 | CER 계산을 검산하기 위한 중간값이며 단독으로 모델을 비교하지 않는다. |
| 실패 위치·원인 | 메모리 부족이나 모델 로드 실패를 숨기지 않고 운영 제약으로 남긴다. |

### 이번 비교에서 제외한 지표

- WER와 단어별 치환·삭제·삽입 내역: 한국어 띄어쓰기에 민감하고 클로바 결과도 사람이
  검수한 정답이 아니다. 추후 사람 검수 정답지를 마련한 뒤 `jiwer`로 오류 유형을 분석한다.
- 개발 용어·숫자·금액별 정확도: 현재 자료에 해당 항목이 충분하다는 보장이 없고 정답
  라벨도 없다. 도메인 평가셋을 만든 뒤 항목별 오류율을 별도로 측정한다.
- 반복 실행 평균·표준편차: 세 모델을 빠르게 거르는 1차 비교이므로 우선 한 번씩 실행한다.
  최종 후보 1~2개는 같은 조건에서 2~3회 반복해 실행시간 편차를 확인한다.
- GPU 사용률·VRAM: 이번 비교 조건은 노트북 CPU·INT8로 고정한다. GPU 배포를 결정하면
  해당 서버에서 별도 측정한다.
- SSD/HDD 종류와 디스크 처리량: Windows에서 저장장치 종류를 안정적으로 자동 판별하기
  어렵고, 모델 추론 성능의 핵심 지표도 아니다. 대신 입력 경로와 실행 전후 여유 공간을 남긴다.
- 동시 요청 처리량: 현재는 로컬 단일 작업 시험이다. 배포 구조와 동시 실행 수를 정한 뒤
  서비스 부하 시험에서 측정한다.

파일 하나만 다시 비교하려면 다음 형식을 쓴다.

```powershell
.\.venv\Scripts\python.exe ai\worker\scripts\compare_stt.py --audio "담당자 개인 개발 관련 녹취본 경로" --reference "해당 녹취본의 클로바 전사 경로"
```

모델을 내려받거나 실행하지 않고 비교 로직만 검사하려면 다음 명령을 사용한다.

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s ai\worker\tests -v
```

클로바 전사는 사람이 검수한 정답이 아니다. CER는 **클로바와 글자 단위로 얼마나 다른지**만 나타내며
STT 정확도나 실제 상담 품질 합격 판정이 아니다. 짧은 구간에서는 클로바의 마지막 발화가
구간 경계를 넘어가면 점수가 어긋날 수 있다. 정식 모델 선정에는 사람 검수 전사,
개발 용어 사례와 실제 배포 환경 재검증이 추가로 필요하다.
