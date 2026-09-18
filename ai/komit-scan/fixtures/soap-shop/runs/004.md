# 검사 결과 — 수제 비누 가게

## 11점 / 100 · 지금 출시하면 위험합니다

문제 34건을 찾았습니다. HIGH 16건 · MEDIUM 17건 · LOW 1건. 2026-09-17 13:03 검사.

점수는 가장 위험한 문제가 구간을 정하고(HIGH가 있으면 49점에서 시작), 그 안에서 건수만큼 깎은 값입니다. 0–49 위험 · 50–79 정비 필요 · 80–100 출시 가능.

### 의뢰하신 증상과 이어지는 곳

> "결제 버튼을 누르면 가끔 아무 반응이 없어요"

| 무엇 | 어디 |
| --- | --- |
| 서버가 오류를 돌려주면 주문하기 버튼이 멈춘 채 아무것도 보여주지 않습니다 | `app/cart/page.tsx:32` |
| 재고를 화면이 보낸 값으로 계산해, 두 화면이 어긋나면 서버 쪽에서 오류가 납니다 | `app/api/checkout/route.ts:29` |

### 가장 급한 것

| 무엇 | 어디 |
| --- | --- |
| 누구나 회원 목록을 보고 회원을 지울 수 있습니다 | `app/api/admin/users/route.ts:3` |
| 브라우저 값만 바꾸면 운영자로 행동할 수 있습니다 | `lib/session.ts:6` |
| 검색창으로 서버에서 아무 명령이나 실행할 수 있습니다 | `app/api/search/route.ts:7` |
| 결제 금액과 재고를 화면이 보낸 값 그대로 믿습니다 | `app/api/checkout/route.ts:10` |
| 결제가 실제로 이루어지지 않는데 결제 완료로 저장됩니다 | `lib/payment.ts:3` |

### 분류별

보안 11건 · 준법 6건 · 결제 4건 · 데이터 4건 · 비용 3건 · 인계 1건 · AI 잔재 2건 · 기타 3건

| 분류 | 요약 |
| --- | --- |
| 보안 | 관리자 기능과 남의 주문이 열려 있고, 로그인 표시를 위조할 수 있으며, 비밀번호가 그대로 또는 약한 방식으로 저장됩니다 |
| 준법 | 개인정보처리방침·이용약관·회원탈퇴·판매자 정보·환불 안내·수집 동의가 모두 없습니다 |
| 결제 | 금액 변조, 결제 확인 없이 완료 처리, 중복 결제, 결제사 미연결 |
| 데이터 | 재고를 화면 값으로 덮어쓰고, 주문과 재고가 따로 저장되며, 동시 주문 때 하나가 사라질 수 있습니다 |
| 비용 | 상품·회원·주문 목록을 끊지 않고 한 번에 다 불러옵니다 |
| 인계 | 실행 방법 문서가 기본 문구 그대로입니다 |
| AI 잔재 | 예전 결제 파일이 남아 있고, 가입과 로그인이 비밀번호를 다르게 다뤄 새 회원은 로그인이 안 됩니다 |
| 기타 | 오류 처리가 없어 빈 화면이나 멈춤이 생기고, 모바일에서 레이아웃이 깨집니다 |

### 확인한 것

- 요청을 받는 파일 10개와 화면 8개 전부
- 세션·비밀번호·결제·자료 저장 코드
- 환경변수 파일, 저장소 무시 목록, 자료 파일의 저장소 포함 여부
- 의존성 검사 도구(`npm audit`) 출력 — 판단하지 않고 아래에 그대로 담았습니다

### 확인하지 못한 것

- 배포 환경의 환경변수 실제 값 — 배포 서비스에 있습니다
- 결제사 쪽 설정 — 아직 결제사와 연결되어 있지 않습니다
- 버튼이 실제로 멈추는 순간의 원인 — 돌려 보아야 알 수 있어, 코드에서 멈출 수 있는 자리만 찍었습니다
- 주문 취소 처리 — 받는 자리는 있으나 어느 화면도 부르지 않습니다. 만들다 만 상태로 성공만 돌려줍니다
- 자료 백업 여부 — 운영 환경에 있습니다

### 의뢰인께 묻는 것

1. 실제 결제를 받을 계획인가요?
2. 지금 서비스에 실제 사용자가 들어오고 있나요?
3. 기존 회원 데이터를 유지해야 하나요?
4. 사업자등록과 통신판매업 신고를 하셨나요?

### 다음에 할 일

이 파일을 Komit에 올려 주세요. 소스 코드는 어디에도 보내지 않았습니다.

---

<!-- 아래는 Komit이 읽는 부분입니다. 지우지 마세요. -->

```json
{
  "scanVersion": "0.1.0",
  "scannedAt": "2026-09-17T13:03:00+09:00",
  "readinessScore": 11,
  "techCategories": [
    "Next.js",
    "React",
    "TypeScript"
  ],
  "issues": [
    {
      "title": "누구나 회원 목록을 볼 수 있습니다",
      "detail": "로그인하지 않아도 관리자 주소로 들어가면 이름·이메일·휴대폰 번호·비밀번호 값까지 모두 내려옵니다",
      "location": "app/api/admin/users/route.ts:3",
      "codeSnippet": {
        "startLine": 3,
        "lines": [
          "export async function GET() {",
          "  return Response.json(read('users'))",
          "}"
        ],
        "highlightLine": 3
      },
      "category": "SECURITY",
      "securityGroup": "ACCESS_CONTROL",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/ACCESS_CONTROL/missing-auth/app/api/admin/users/GET"
    },
    {
      "title": "누구나 회원을 삭제할 수 있습니다",
      "detail": "로그인 확인 없이 회원 번호만 보내면 그 회원이 지워집니다",
      "location": "app/api/admin/users/route.ts:7",
      "codeSnippet": {
        "startLine": 7,
        "lines": [
          "export async function DELETE(req: Request) {",
          "  const { userId } = await req.json()",
          "  const users = read('users').filter((u: any) => u.id !== userId)"
        ],
        "highlightLine": 7
      },
      "category": "SECURITY",
      "securityGroup": "ACCESS_CONTROL",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/ACCESS_CONTROL/missing-auth/app/api/admin/users/DELETE"
    },
    {
      "title": "남의 주문 내역을 볼 수 있습니다",
      "detail": "로그인한 사람이면 주소의 주문번호만 바꿔서 다른 사람 주문을 열 수 있습니다",
      "location": "app/api/orders/[id]/route.ts:9",
      "codeSnippet": {
        "startLine": 8,
        "lines": [
          "  const { id } = await params",
          "  const order = find('orders', id)",
          "  if (!order) return new Response('Not found', { status: 404 })",
          "",
          "  return Response.json(order)"
        ],
        "highlightLine": 9
      },
      "category": "SECURITY",
      "securityGroup": "ACCESS_CONTROL",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/ACCESS_CONTROL/missing-ownership-check/app/api/orders"
    },
    {
      "title": "로그인 표시를 위조할 수 있습니다",
      "detail": "브라우저에 저장되는 값이 회원 번호 그대로라, 그 값을 바꾸면 다른 회원이나 운영자로 행동할 수 있습니다",
      "location": "lib/session.ts:6",
      "codeSnippet": {
        "startLine": 5,
        "lines": [
          "  const jar = await cookies()",
          "  const userId = jar.get('uid')?.value",
          "  if (!userId) return null"
        ],
        "highlightLine": 6
      },
      "category": "SECURITY",
      "securityGroup": "AUTH_SESSION",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/AUTH_SESSION/forgeable-session/lib/session"
    },
    {
      "title": "시험용 운영자 계정이 그대로 들어 있습니다",
      "detail": "저장소에 올라간 회원 자료에 운영자 계정이 있고 비밀번호가 짐작하기 쉬운 다섯 자리 숫자라, 누구나 운영자로 로그인할 수 있습니다",
      "location": "data/users.json:3",
      "category": "SECURITY",
      "securityGroup": "AUTH_SESSION",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/AUTH_SESSION/fixture-admin-account/data/users"
    },
    {
      "title": "가입할 때 비밀번호가 그대로 저장됩니다",
      "detail": "자료가 새면 회원들의 비밀번호가 그대로 드러납니다. 법에서 되돌릴 수 없게 바꿔 저장하도록 요구하는 항목입니다",
      "location": "app/api/signup/route.ts:10",
      "codeSnippet": {
        "startLine": 8,
        "lines": [
          "  const id = `u${Date.now()}`",
          "",
          "  users.push({ id, email, password, phone, name, role: 'user' })",
          "  write('users', users)"
        ],
        "highlightLine": 10
      },
      "category": "SECURITY",
      "securityGroup": "PII_ENCRYPTION",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/PII_ENCRYPTION/plaintext-password/app/api/signup"
    },
    {
      "title": "비밀번호를 되돌리기 쉬운 방식으로 바꿔 저장합니다",
      "detail": "기존 회원의 비밀번호에 쓰인 방식은 오래되어 원래 값을 쉽게 알아낼 수 있습니다",
      "location": "lib/hash.ts:4",
      "codeSnippet": {
        "startLine": 3,
        "lines": [
          "export function hash(password: string) {",
          "  return crypto.createHash('md5').update(password).digest('hex')",
          "}"
        ],
        "highlightLine": 4
      },
      "category": "SECURITY",
      "securityGroup": "PII_ENCRYPTION",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/PII_ENCRYPTION/weak-password-hash/lib/hash"
    },
    {
      "title": "검색창으로 서버에서 아무 명령이나 실행할 수 있습니다",
      "detail": "검색어를 그대로 실행 코드로 만들어 쓰고 있어, 특정한 글자를 넣으면 서버 안의 자료를 읽거나 바꿀 수 있습니다",
      "location": "app/api/search/route.ts:7",
      "codeSnippet": {
        "startLine": 6,
        "lines": [
          "  const products = read('products')",
          "  const matcher = new Function('p', `return /${q}/i.test(p.name) || /${q}/i.test(p.desc)`)",
          "",
          "  return Response.json(products.filter((p: any) => matcher(p)))"
        ],
        "highlightLine": 7
      },
      "category": "SECURITY",
      "securityGroup": "INPUT",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "SECURITY/INPUT/code-execution-from-query/app/api/search"
    },
    {
      "title": "관리자 열쇠가 브라우저로 내려갑니다",
      "detail": "브라우저에 노출되는 이름으로 관리자 열쇠를 두었습니다. 지금은 개발용 값이고 서버가 이 열쇠를 확인하지도 않지만, 실제 값을 넣으면 그대로 새어 나갑니다",
      "location": ".env.local:1",
      "category": "SECURITY",
      "securityGroup": "SECRET",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "SECURITY/SECRET/public-prefix-admin-key/.env.local"
    },
    {
      "title": "비밀값 설정 파일이 저장소에 올라가 있습니다",
      "detail": "무시 목록에 없어 저장소를 볼 수 있는 사람은 누구나 볼 수 있습니다. 지금은 개발용 값이지만 실제 값을 넣는 순간 함께 올라갑니다",
      "location": ".gitignore:3",
      "codeSnippet": {
        "startLine": 1,
        "lines": [
          "node_modules",
          ".next",
          "next-env.d.ts"
        ],
        "highlightLine": 3
      },
      "category": "SECURITY",
      "securityGroup": "SECRET",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "SECURITY/SECRET/env-file-committed/.gitignore"
    },
    {
      "title": "로그인 시도 횟수에 제한이 없습니다",
      "detail": "비밀번호를 계속 넣어 보면 언젠가 뚫립니다",
      "location": "app/api/login/route.ts:5",
      "category": "SECURITY",
      "securityGroup": "AUTH_SESSION",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "SECURITY/AUTH_SESSION/no-rate-limit/app/api/login"
    },
    {
      "title": "개인정보처리방침 페이지 추가",
      "detail": "회원가입과 주문으로 개인정보를 받고 있는데 개인정보처리방침이 어디에도 없습니다. 실제 사용자를 받는 순간부터 필요합니다",
      "location": "app/layout.tsx:20",
      "codeSnippet": {
        "startLine": 20,
        "lines": [
          "        <footer>",
          "          <p>수제 비누 가게</p>",
          "        </footer>"
        ],
        "highlightLine": 20
      },
      "category": "COMPLIANCE",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "COMPLIANCE/missing-privacy-policy/app/layout"
    },
    {
      "title": "회원탈퇴 경로 구현",
      "detail": "가입은 되는데 탈퇴할 방법이 없습니다",
      "location": "app/mypage/page.tsx:22",
      "category": "COMPLIANCE",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "COMPLIANCE/missing-withdrawal/app/mypage"
    },
    {
      "title": "이용약관 페이지 추가",
      "detail": "분쟁이 났을 때 기준이 될 약관이 없습니다",
      "location": "app/layout.tsx:21",
      "category": "COMPLIANCE",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "COMPLIANCE/missing-terms/app/layout"
    },
    {
      "title": "판매자 정보 표시 추가",
      "detail": "물건을 파는 화면인데 상호·대표자·주소·연락처·사업자등록번호·통신판매업 신고번호가 화면 어디에도 없습니다",
      "location": "app/layout.tsx:21",
      "category": "COMPLIANCE",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "COMPLIANCE/missing-seller-info/app/layout"
    },
    {
      "title": "환불·교환·배송 안내 추가",
      "detail": "주문하기 버튼 앞에 환불 조건이나 배송 기간을 볼 수 있는 자리가 없습니다",
      "location": "app/cart/page.tsx:58",
      "category": "COMPLIANCE",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "COMPLIANCE/missing-refund-shipping-notice/app/cart"
    },
    {
      "title": "가입할 때 개인정보 수집 동의를 받지 않습니다",
      "detail": "이름·이메일·휴대폰 번호를 받으면서 무엇을 왜 받는지 알리고 동의받는 절차가 없습니다",
      "location": "app/signup/page.tsx:27",
      "category": "COMPLIANCE",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "COMPLIANCE/missing-consent/app/signup"
    },
    {
      "title": "결제 금액이 변조될 수 있습니다",
      "detail": "화면이 보낸 금액을 그대로 결제에 넘겨서, 1만원짜리를 100원으로 고쳐 보낼 수 있습니다",
      "location": "app/api/checkout/route.ts:10",
      "codeSnippet": {
        "startLine": 9,
        "lines": [
          "  const body = await req.json()",
          "  const amount = body.amount",
          "",
          "  const orderId = `o${Date.now()}`"
        ],
        "highlightLine": 10
      },
      "category": "PAYMENT",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "PAYMENT/client-amount-trusted/app/api/checkout"
    },
    {
      "title": "결제 응답을 기다리지 않고 주문을 완료 처리합니다",
      "detail": "결제가 끝났는지 확인하지 않고 주문을 결제 완료로 저장합니다. 결제가 실패해도 주문은 완료로 남습니다",
      "location": "app/api/checkout/route.ts:13",
      "codeSnippet": {
        "startLine": 12,
        "lines": [
          "  const orderId = `o${Date.now()}`",
          "  const receipt = requestPayment(amount, orderId)",
          "",
          "  const orders = read('orders')"
        ],
        "highlightLine": 13
      },
      "category": "PAYMENT",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "PAYMENT/no-await-approval/app/api/checkout"
    },
    {
      "title": "버튼을 두 번 누르면 두 번 결제됩니다",
      "detail": "같은 주문인지 걸러 내는 장치가 없어 중복 주문과 중복 결제가 생길 수 있습니다",
      "location": "app/api/checkout/route.ts:12",
      "category": "PAYMENT",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "PAYMENT/no-idempotency/app/api/checkout"
    },
    {
      "title": "실제 결제가 이루어지지 않는데 결제 완료로 저장됩니다",
      "detail": "결제 처리가 결제사와 연결되지 않은 자리표시 상태라 항상 승인으로 돌아옵니다. 겉으로는 결제된 것처럼 보이지만 돈은 오가지 않습니다",
      "location": "lib/payment.ts:3",
      "codeSnippet": {
        "startLine": 1,
        "lines": [
          "export async function requestPayment(amount: number, orderId: string) {",
          "  await new Promise((r) => setTimeout(r, 120))",
          "  return { approved: true, amount, orderId, receiptId: `rc_${orderId}` }",
          "}"
        ],
        "highlightLine": 3
      },
      "category": "PAYMENT",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "PAYMENT/stub-always-approves/lib/payment"
    },
    {
      "title": "재고를 화면이 보낸 값으로 덮어씁니다",
      "detail": "남은 수량을 서버가 다시 읽지 않고 화면이 보낸 값에서 빼기 때문에, 그 값을 고쳐 보내면 재고를 마음대로 바꿀 수 있습니다",
      "location": "app/api/checkout/route.ts:29",
      "codeSnippet": {
        "startLine": 27,
        "lines": [
          "  const products = read('products')",
          "  const product = products.find((p: any) => p.id === body.productId)",
          "  product.stock = body.stock - body.quantity",
          "  write('products', products)"
        ],
        "highlightLine": 29
      },
      "category": "DATA",
      "severity": "HIGH",
      "isInput": true,
      "fingerprint": "DATA/client-stock-trusted/app/api/checkout"
    },
    {
      "title": "주문 저장이 중간에 끊기면 재고만 어긋납니다",
      "detail": "주문 기록과 재고를 따로 저장해 한쪽만 반영될 수 있습니다. 돈이 걸린 처리라 어긋나면 되돌리기 어렵습니다",
      "location": "app/api/checkout/route.ts:25",
      "category": "DATA",
      "severity": "HIGH",
      "isInput": false,
      "fingerprint": "DATA/no-transaction/app/api/checkout"
    },
    {
      "title": "주문이 동시에 들어오면 하나가 사라질 수 있습니다",
      "detail": "자료를 파일 하나에 통째로 읽고 다시 쓰는 방식이라, 두 사람이 거의 같은 순간에 주문하면 먼저 쓴 쪽이 지워집니다",
      "location": "lib/db.ts:11",
      "codeSnippet": {
        "startLine": 10,
        "lines": [
          "export function write(name: string, rows: any[]) {",
          "  fs.writeFileSync(path.join(dir, `${name}.json`), JSON.stringify(rows, null, 2))",
          "}"
        ],
        "highlightLine": 11
      },
      "category": "DATA",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "DATA/lost-update-file-store/lib/db"
    },
    {
      "title": "회원을 지우면 되돌릴 수 없습니다",
      "detail": "삭제 표시만 남기는 대신 자료를 실제로 지웁니다. 그 회원의 주문 기록은 주인 없는 채로 남습니다",
      "location": "app/api/admin/users/route.ts:9",
      "category": "DATA",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "DATA/hard-delete-user/app/api/admin/users"
    },
    {
      "title": "상품 목록을 한 번에 다 불러옵니다",
      "detail": "몇 개까지 가져올지 정하는 값이 없어 상품이 쌓이면 화면이 점점 느려지다가 열리지 않게 됩니다",
      "location": "app/api/products/route.ts:4",
      "category": "COST",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "COST/unbounded-list/app/api/products"
    },
    {
      "title": "회원 목록을 한 번에 다 불러옵니다",
      "detail": "회원이 늘어나면 관리자 화면이 점점 느려지다가 열리지 않게 됩니다",
      "location": "app/api/admin/users/route.ts:4",
      "category": "COST",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "COST/unbounded-list/app/api/admin/users"
    },
    {
      "title": "주문 내역을 한 번에 다 불러옵니다",
      "detail": "주문이 쌓이면 마이페이지가 점점 느려집니다",
      "location": "app/mypage/page.tsx:16",
      "category": "COST",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "COST/unbounded-list/app/mypage"
    },
    {
      "title": "실행 방법 문서 추가",
      "detail": "안내 문서가 처음 만들었을 때의 기본 문구 그대로라, 받아 든 개발자가 어떤 설정값을 채워야 하는지 알 수 없습니다",
      "location": "README.md:1",
      "category": "HANDOVER",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "HANDOVER/no-setup-doc/README"
    },
    {
      "title": "같은 결제 처리가 두 곳에 다르게 들어 있습니다",
      "detail": "쓰이지 않는 예전 결제 파일이 남아 있어 고칠 때 한쪽만 고치면 문제가 계속 남습니다",
      "location": "lib/payment-old.ts:1",
      "category": "AI_RESIDUE",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "AI_RESIDUE/duplicate-impl/lib/payment"
    },
    {
      "title": "새로 가입한 회원은 로그인할 수 없습니다",
      "detail": "로그인은 비밀번호를 바꿔서 비교하는데 가입은 바꾸지 않고 저장해서 서로 맞지 않습니다. 가입은 성공한 것처럼 보이지만 로그인은 항상 실패합니다",
      "location": "app/api/login/route.ts:9",
      "codeSnippet": {
        "startLine": 8,
        "lines": [
          "  const user = read('users').find((u: any) => u.email === email)",
          "  if (!user || !verify(password, user.password)) {",
          "    return new Response('이메일 또는 비밀번호가 맞지 않습니다', { status: 401 })"
        ],
        "highlightLine": 9
      },
      "category": "AI_RESIDUE",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "AI_RESIDUE/inconsistent-password-handling/app/api/login"
    },
    {
      "title": "주문하기 버튼이 오류 때 아무 반응이 없습니다",
      "detail": "서버가 오류를 돌려주면 그 응답을 읽는 데서 멈춰 버려 화면에 아무것도 나오지 않습니다. 의뢰인이 적은 증상과 이어지는 곳입니다",
      "location": "app/cart/page.tsx:32",
      "codeSnippet": {
        "startLine": 31,
        "lines": [
          "    })",
          "    const data = await res.json()",
          "    location.href = `/mypage?ordered=${data.orderId}`"
        ],
        "highlightLine": 32
      },
      "category": "ETC",
      "severity": "MEDIUM",
      "isInput": true,
      "fingerprint": "ETC/no-error-handling/app/cart/order"
    },
    {
      "title": "없는 상품 주소로 들어가면 빈 화면이 됩니다",
      "detail": "상품을 찾지 못한 경우를 처리하지 않아 오류 화면이 뜹니다",
      "location": "app/products/[id]/page.tsx:9",
      "category": "ETC",
      "severity": "MEDIUM",
      "isInput": false,
      "fingerprint": "ETC/no-not-found-handling/app/products/[id]"
    },
    {
      "title": "모바일에서 레이아웃이 깨집니다",
      "detail": "상품 목록 너비가 고정되어 있어 좁은 화면에서 화면 밖으로 밀려납니다",
      "location": "app/products/page.tsx:9",
      "category": "ETC",
      "severity": "LOW",
      "isInput": false,
      "fingerprint": "ETC/responsive-break/app/products"
    }
  ],
  "questions": [
    {
      "content": "실제 결제를 받을 계획인가요?",
      "answerType": "SINGLE",
      "options": [
        "네",
        "아니요"
      ],
      "reason": "결제 처리 코드를 찾았으나 결제사와 연결되지 않은 상태다. 실제 결제를 받는지에 따라 전자상거래 관련 의무와 금액 검증의 중요도가 달라진다."
    },
    {
      "content": "지금 서비스에 실제 사용자가 들어오고 있나요?",
      "answerType": "SINGLE",
      "options": [
        "네, 사용 중입니다",
        "아직 저만 씁니다"
      ],
      "reason": "이름·이메일·휴대폰 번호를 받는 가입 화면을 찾았다. 실제 사용자를 받는 시점부터 개인정보처리방침과 수집 동의가 필요하다."
    },
    {
      "content": "기존 회원 데이터를 유지해야 하나요?",
      "answerType": "SINGLE",
      "options": [
        "유지해야 합니다",
        "지워도 괜찮습니다"
      ],
      "reason": "회원 기능은 있는데 탈퇴 경로가 없다. 자료를 남겨야 하는지에 따라 탈퇴를 어떻게 만들지가 달라진다."
    },
    {
      "content": "사업자등록과 통신판매업 신고를 하셨나요?",
      "answerType": "SINGLE",
      "options": [
        "둘 다 했습니다",
        "사업자등록만 했습니다",
        "아직입니다"
      ],
      "reason": "상품을 파는 화면과 주문 처리를 찾았으나 판매자 정보가 화면에 없다. 등록 여부는 코드로 알 수 없어 여쭙는다."
    }
  ],
  "scannedScope": [
    "app/api 아래 요청 처리 파일 10개 전부",
    "app/ 아래 화면 8개 전부와 공통 틀",
    "lib/ 아래 세션·비밀번호·결제·자료 저장 코드 5개",
    "환경변수 파일과 그 사용처, 저장소 무시 목록",
    "data/ 아래 자료 파일 3개의 구조와 저장소 포함 여부",
    "README와 패키지 정의",
    "비용 — 외부 유료 서비스 호출은 없었고, 쌓이는 자료를 통째로 불러오는 곳 세 곳을 적었다"
  ],
  "unscannedScope": [
    {
      "scope": "배포 환경의 환경변수 실제 값",
      "reason": "배포 서비스에 들어 있어 이 검사로는 볼 수 없다."
    },
    {
      "scope": "결제사 쪽 설정과 알림 수신",
      "reason": "코드에 결제사 연결이 아직 없어 확인할 대상이 없다."
    },
    {
      "scope": "실제로 주문하기 버튼이 반응하지 않는 순간의 원인",
      "reason": "돌려 보아야 아는 동작이다. 오류 처리가 없는 자리와 재고 계산이 어긋날 수 있는 자리만 코드에서 찍었다."
    },
    {
      "scope": "주문 취소 처리",
      "reason": "취소 요청을 받는 자리는 있으나 아무 화면도 부르지 않아 실제로 쓰이는지 알 수 없다. 만들다 만 상태로 성공만 돌려주므로 붙일 때 확인이 필요하다."
    },
    {
      "scope": "자료 백업 여부",
      "reason": "자료가 서버 안의 파일에 있어 백업은 운영 환경에서만 확인할 수 있다."
    }
  ],
  "dependencyAudit": {
    "command": "npm audit --json",
    "output": "{\n  \"auditReportVersion\": 2,\n  \"vulnerabilities\": {\n    \"next\": {\n      \"name\": \"next\",\n      \"severity\": \"moderate\",\n      \"isDirect\": true,\n      \"via\": [\n        \"postcss\"\n      ],\n      \"effects\": [],\n      \"range\": \"9.3.4-canary.0 - 16.3.0-preview.10\",\n      \"nodes\": [\n        \"node_modules/next\"\n      ],\n      \"fixAvailable\": {\n        \"name\": \"next\",\n        \"version\": \"16.3.5\",\n        \"isSemVerMajor\": true\n      }\n    },\n    \"postcss\": {\n      \"name\": \"postcss\",\n      \"severity\": \"high\",\n      \"isDirect\": false,\n      \"via\": [\n        {\n          \"source\": 1117015,\n          \"name\": \"postcss\",\n          \"dependency\": \"postcss\",\n          \"title\": \"PostCSS has XSS via Unescaped </style> in its CSS Stringify Output\",\n          \"url\": \"https://github.com/advisories/GHSA-qx2v-qp2m-jg93\",\n          \"severity\": \"moderate\",\n          \"cwe\": [\n            \"CWE-79\"\n          ],\n          \"cvss\": {\n            \"score\": 6.1,\n            \"vectorString\": \"CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N\"\n          },\n          \"range\": \"<8.5.10\"\n        },\n        {\n          \"source\": 1124252,\n          \"name\": \"postcss\",\n          \"dependency\": \"postcss\",\n          \"title\": \"PostCSS: Arbitrary file read and information disclosure via attacker-controlled sourceMappingURL in CSS comments\",\n          \"url\": \"https://github.com/advisories/GHSA-6g55-p6wh-862q\",\n          \"severity\": \"high\",\n          \"cwe\": [\n            \"CWE-22\",\n            \"CWE-200\"\n          ],\n          \"cvss\": {\n            \"score\": 7.5,\n            \"vectorString\": \"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N\"\n          },\n          \"range\": \"<=8.5.11\"\n        },\n        {\n          \"source\": 1130709,\n          \"name\": \"postcss\",\n          \"dependency\": \"postcss\",\n          \"title\": \"PostCSS: incomplete fix of GHSA-6g55-p6wh-862q — attacker-controlled sourceMappingURL reads arbitrary .map files when `from` is unset\",\n          \"url\": \"https://github.com/advisories/GHSA-fxqj-rqcc-2cmp\",\n          \"severity\": \"moderate\",\n          \"cwe\": [\n            \"CWE-22\",\n            \"CWE-200\"\n          ],\n          \"cvss\": {\n            \"score\": 0,\n            \"vectorString\": null\n          },\n          \"range\": \"<=8.5.22\"\n        },\n        {\n          \"source\": 1139510,\n          \"name\": \"postcss\",\n          \"dependency\": \"postcss\",\n          \"title\": \"PostCSS: Path Traversal in Previous Source Map Auto-Loading (sourceMappingURL) leads to Arbitrary .map File Disclosure\",\n          \"url\": \"https://github.com/advisories/GHSA-r28c-9q8g-f849\",\n          \"severity\": \"high\",\n          \"cwe\": [\n            \"CWE-22\"\n          ],\n          \"cvss\": {\n            \"score\": 7.5,\n            \"vectorString\": \"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N\"\n          },\n          \"range\": \"<=8.5.17\"\n        }\n      ],\n      \"effects\": [\n        \"next\"\n      ],\n      \"range\": \"<=8.5.22\",\n      \"nodes\": [\n        \"node_modules/postcss\"\n      ],\n      \"fixAvailable\": {\n        \"name\": \"next\",\n        \"version\": \"16.3.5\",\n        \"isSemVerMajor\": true\n      }\n    }\n  },\n  \"metadata\": {\n    \"vulnerabilities\": {\n      \"info\": 0,\n      \"low\": 0,\n      \"moderate\": 1,\n      \"high\": 1,\n      \"critical\": 0,\n      \"total\": 2\n    },\n    \"dependencies\": {\n      \"prod\": 15,\n      \"dev\": 5,\n      \"optional\": 39,\n      \"peer\": 0,\n      \"peerOptional\": 0,\n      \"total\": 58\n    }\n  }\n}\n"
  }
}
```
