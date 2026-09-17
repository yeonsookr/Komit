# 데모 실행서

내려받은 뒤 처음 한 번 준비하고, 그다음부터는 서버 셋만 띄우면 된다.

`git pull` 만으로는 돌지 않는다. 아래 네 가지가 저장소에 들어가지 않기 때문이다.

| 없는 것 | 왜 |
| --- | --- |
| `.claude/skills/komit-scan` | 원본이 `ai/komit-scan/skills` 에 있고 사본은 무시 목록에 있다 |
| `fe/.env.local` | 각자 환경이 달라 저장소에 두지 않는다 |
| `node_modules`, `.venv` | 받아서 설치하는 것이다 |
| 검사 결과 파일 | 검사를 돌리면 만들어진다. 미리 만들어 둔 것도 있다 |

---

## 준비 — 처음 한 번

### 1. 검사 스킬 복사

```bash
cp -r ai/komit-scan/skills/komit-scan .claude/skills/
```

윈도우 명령 프롬프트라면:

```
xcopy /E /I ai\komit-scan\skills\komit-scan .claude\skills\komit-scan
```

### 2. 예시 프로젝트 설치

```bash
cd demo/soap-shop
npm install
cd ../..
```

### 3. 목 서버 준비

파이썬 3.12 가 필요하다. 없으면 [`uv`](https://docs.astral.sh/uv/) 로 받는 것이 빠르다.

```bash
cd mock
uv python install 3.12
uv venv --python 3.12
uv pip install -r requirements.txt
cd ..
```

파이썬이 이미 있으면 이렇게 해도 된다.

```bash
cd mock
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt
cd ..
```

### 4. 프론트 준비

```bash
cd fe
npm install
cp .env.example .env.local
```

`.env.local` 을 열어 세 줄을 로컬 목 서버로 바꾼다.

```
BACKEND_API_BASE_URL=http://127.0.0.1:8080/service
ESCROW_API_BASE_URL=http://127.0.0.1:8080/escrow
AI_API_BASE_URL=http://127.0.0.1:8080/service
```

---

## 띄우기 — 매번

창 세 개가 필요하다.

```bash
# 1. 목 서버
cd mock
.venv/Scripts/python -m uvicorn app.main:app --host 127.0.0.1 --port 8080
```

```bash
# 2. Komit 화면
cd fe
npm run dev
```

```bash
# 3. 예시 프로젝트
cd demo/soap-shop
npm run dev
```

| 주소 | 무엇 |
| --- | --- |
| `http://localhost:3000` | 예시 프로젝트 (수제 비누 가게) |
| `http://localhost:3001` | Komit 화면 |
| `http://127.0.0.1:8080/service/docs` | 목 서버 문서 |

프론트는 3000 이 이미 쓰이고 있으면 3001 로 뜬다. 켤 때 나오는 주소를 본다.

### 잘 떴는지

```bash
curl http://127.0.0.1:8080/health
```

---

## 데모 순서

### ① 잘 돌아가는 것을 먼저 — `localhost:3000`

상품을 고르고 로그인하고 주문까지 해 본다.

| 계정 | 비밀번호 |
| --- | --- |
| `mimi@example.com` | `test` |

> 바이브코딩으로 만든 쇼핑몰입니다. 겉으로는 잘 돌아갑니다.

### ② 뚫린 것을 보인다

로그인하지 않은 채로 주소창에 넣는다.

```
http://localhost:3000/api/admin/users
```

**회원 전체의 이름·이메일·휴대폰 번호·비밀번호가 그대로 나온다.**

> 돌아가는 것과 안전한 것은 다릅니다. 만든 사람은 이걸 모릅니다.

### ③ 사전점검 — `localhost:3001/checkup`

STEP 1·2 를 채우고 STEP 3 으로 간다. `① 검사 파일 받기` 를 눌러 내려받는다.

> 코드를 저희에게 보내지 않습니다. 이 파일만 받아 가세요.

### ④ 검사 — 터미널

```bash
cd demo/soap-shop
```

Claude Code 에서 요청한다.

```
komit-scan 으로 이 프로젝트를 검사해줘
```

`komit-result.md` 가 만들어진다. 열어서 앞부분 요약을 보여준다.

> 제 컴퓨터에서만 돌았습니다. 이 파일 하나만 올립니다.

**검사를 돌릴 시간이 없으면** 미리 만들어 둔 것을 쓴다. 같은 파일이 두 곳에 있다.



검사를 다시 돌리면 앞의 것이 새 결과로 덮인다. 검사기는 이 파일을 읽지 않으므로 남아 있어도 다음 검사에 영향을 주지 않는다.```
ai/komit-scan/fixtures/soap-shop/runs/004.md
```

### ⑤ 올리기

STEP 3 의 `③ 만들어진 결과 파일 올리기` 에서 그 파일을 고른다.

### ⑥ 마지막 설문 — STEP 4

```
실제 결제를 받을 계획인가요?
지금 서비스에 실제 사용자가 들어오고 있나요?
기존 회원 데이터를 유지해야 하나요?
사업자등록과 통신판매업 신고를 하셨나요?
```

> 이 질문들은 미리 정해 둔 것이 아닙니다. **검사가 코드를 보고 만든 것입니다.** 결제 코드를 찾았으니 실제 결제를 받는지 묻고, 회원 기능은 있는데 탈퇴가 없으니 데이터를 남겨야 하는지 묻습니다.

### ⑦ 진단서 — `localhost:3001/checkup/31/result`

```
11/100  런칭 준비도

의뢰 내용
  "결제 버튼을 누르면 가끔 아무 반응이 없어요"
  app/cart/page.tsx:32

의뢰 내용 외 문제점  34건
  누구나 회원 목록을 볼 수 있습니다   보안 11건
  ...
```

②에서 보여준 그 구멍이 여기 잡혀 있다. **이 연결이 데모의 핵심이다.**

> 아까 그 문제입니다. 개발 용어 없이, 어느 파일 몇 번째 줄인지까지.

### ⑧ 요청서 만들기

고칠 것을 골라 개발자에게 맡기는 화면으로 넘어간다.

---

## 말할 것 세 가지

**1. 코드를 보내지 않는다** — 결과 파일만 올라간다

**2. 비개발자의 말로 쓴다** — `IDOR 취약점` 이 아니라 `누구나 남의 주문을 볼 수 있습니다`

**3. 점수가 판정이다** — 위험한 것이 하나라도 있으면 49점을 넘지 못한다. 사소한 것을 먼저 고쳐도 점수가 오르지 않는다

---

## 막혔을 때

### 진단서가 52점 · 3건으로 나온다

결과 파일이 올라가지 않았다. 목 서버를 다시 띄우면 담아 둔 것이 사라지므로 다시 올린다.

```bash
curl -X POST -F "file=@ai/komit-scan/fixtures/soap-shop/runs/004.md" \
  http://127.0.0.1:8080/service/api/diagnoses/31/inspections
```

### 올렸는데 거부당한다

읽을 수 있는 결과 파일인지 본다. 마크다운 안에 `json` 코드블록이 하나 있어야 한다.

### 마지막 설문에 문항이 하나만 보인다

프론트를 다시 받았는지 본다. 이 저장소의 `fe/src/lib/survey.ts` 가 최신이어야 한다.

### 화면이 목 서버를 못 본다

`fe/.env.local` 의 주소가 `http://127.0.0.1:8080/service` 인지 본다. `localhost` 대신 `127.0.0.1` 을 쓴다.

---

## 하지 말 것

- `demo/soap-shop` 을 **배포하지 않는다.** 일부러 뚫어 둔 코드다
- 로컬 밖으로 열지 않는다
- 다른 프로젝트에 복사하지 않는다
- 검사를 돌릴 때 `ai/komit-scan/fixtures/soap-shop/EXPECTED.md` 를 읽히지 않는다. 정답표다
