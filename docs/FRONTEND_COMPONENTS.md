# Komit 공통 UI 컴포넌트

화면 캡처 67장(랜딩 · 설문/진단 · 개발자 찾기 · 상담/계약 · 진행/확인 · 정산 · 마이페이지 · 관리자)에서
반복되는 UI 요소를 추출해 만든 공통 컴포넌트입니다. 배치는 [`docs/FRONTEND_CONVENTIONS.md`](./FRONTEND_CONVENTIONS.md) 10장의 계층 규칙을 따릅니다.

## 1. 위치

```text
fe/src/
├─ styles/tokens.css        ← 디자인 토큰(CSS 변수). 색·폰트·라운드·그림자·레이아웃 폭
├─ app/globals.css          ← tokens.css import + @theme(토큰 → Tailwind 유틸리티) + base/components 레이어
├─ lib/cn.ts                ← clsx + tailwind-merge
├─ lib/routes.ts            ← 화면 경로 상수 ROUTES (컨벤션 8.1). href 에 URL 문자열을 직접 적지 않습니다
├─ lib/company.ts           ← 사업자 정보 COMPANY. 푸터가 씁니다. 화면마다 따로 적지 않습니다
├─ lib/policies.ts          ← 약관 문서 목록 POLICY_DOCUMENTS (이름 · 개정일 · 본문 주소)
├─ components/
│  ├─ layouts/  AppShell.tsx     AppShell (GNB + 본문 + 푸터)
│  │            Header.tsx       Header, PUBLIC_NAV, APP_NAV
│  │            Footer.tsx       Footer
│  │            Container.tsx    Container (본문 폭 720 / 840 / 960)
│  ├─ buttons/  Button.tsx      Button, IconButton
│  │            buttonStyles.ts buttonClass — 링크(<Link>)에 버튼 모양을 입힐 때. 서버 컴포넌트에서도 호출 가능
│  ├─ feedback/ Callout.tsx     Callout, HelpRow, EmptyState
│  │            Modal.tsx       Modal(+Header/Body/Footer), Popover, MenuItem, MenuDivider
│  │            Toast.tsx       ToastProvider, useToast
│  ├─ domains/
│  │  ├─ chat/     ChatRoom.tsx     ChatRoom, ChatNotice, ChatSessionBanner
│  │  │             ChatMessage.tsx  ChatMessage, ChatDateDivider
│  │  │             ChatComposer.tsx ChatComposer
│  │  ├─ landing/  LandingHero.tsx        히어로 (검색 폼 · 예시 칩 · 이번 주 상담 N건) — 유일한 클라이언트 컴포넌트
│  │  │             SymptomGrid.tsx        증상별로 찾기 (증상 → 분야 코드 대응 상수 포함)
│  │  │             FeaturedDevelopers.tsx 추천 개발자 카드 3장
│  │  │             ProcessSteps.tsx       진행 순서 (id="process", ROUTES.howTo 의 앵커)
│  │  │             CheckupIntro.tsx       사전 점검 소개 (정적 예시 카드)
│  │  ├─ auth/     LoginCard.tsx          로그인 카드 (카카오 버튼 · 약관 안내 · 문서 링크 4개 · 실패 안내)
│  │  │             SessionShell.tsx       인증 스토어를 읽어 헤더를 채우는 AppShell — (site) 레이아웃이 씁니다
│  │  ├─ room/     RoomSidebar.tsx        의뢰 화면 좌측 목록 · 맡긴 의뢰/맡은 의뢰 전환
│  │  │             RoomHeader.tsx         방 제목 · 상태 · 상대 정보
│  │  │             RoomTabs.tsx           채팅 · 계약서 · 진행 · 확인 · 리뷰 탭
│  │  │             RoomConversation.tsx   상담·컨설팅·개발 대화 흐름
│  │  ├─ settlement/ SettlementShell.tsx   정산 좌측 목록과 본문 골격
│  │  │                  SettlementSidebar.tsx 소비 · 소득 · 예치금 탐색
│  │  │                  SpendingView.tsx  월별 소비 내역
│  │  │                  IncomeView.tsx    개발 소득 내역
│  │  │                  DepositsView.tsx  계약별 예치금 현황
│  │  │                  MonthPicker.tsx   소비 내역 조회 월 선택
│  │  │                  ContractGroup.tsx 계약별 정산 행 묶음
│  │  │                  MoneyFlowRow.tsx  정산 금액 흐름 한 줄
│  │  ├─ schedule/ ScheduleView.tsx        의뢰인·개발자 일정을 함께 표시하는 내 일정
│  │  ├─ dispute/ ReportForm.tsx           문제 신고 내용 · 첨부파일 제출
│  │  └─ mypage/   AccountSection.tsx      계정 칸 · 계좌 칸
│  │                AccountVerificationForm.tsx 계좌 입력 · 1원 인증 흐름
│  │                AccountVerificationCodeInput.tsx 인증번호 네 자리 입력
│  │                ProfileImageEdit.tsx   계정과 헤더에 쓰는 프로필 사진 변경
│  │                NameEditRow.tsx         이름 줄 — 그 자리에서 고칩니다(클라이언트)
│  │                ProjectSection.tsx      등록한 프로젝트 칸
│  │                DeveloperSection.tsx    개발자 프로필 칸 — 프로필이 없으면 만들기 안내
│  │                NotificationSection.tsx 알림 칸 — 켜고 끄기(클라이언트)
│  │                PolicySection.tsx       약관 칸 — 문서마다 본문으로 바로 갑니다
│  │                WithdrawalSection.tsx   탈퇴 칸 — 탈퇴 화면으로 보내기만 합니다
│  │                WithdrawalBlockers.tsx  탈퇴를 막는 것 목록 (R-ACC-05)
│  │                WithdrawButton.tsx      탈퇴하기 — 잠금은 화면과 서버 두 겹(클라이언트)
│  └─ common/   Input.tsx       Field, Input, Textarea, SearchInput
│               Select.tsx      NativeSelect, Dropdown
│               Choice.tsx      Checkbox, Radio, ChoiceCard, Switch, RatingScale
│               Badge.tsx       Badge, CountBadge, Tag, StatusDot
│               Chip.tsx        Chip, ChipGroup
│               Card.tsx        Card, CardHeader, CardSection, KeyValueRow, StatCard
│               Section.tsx     SectionCard (제목 + 우측 안내 + 테두리 카드)
│               Tabs.tsx        SegmentedTabs, NavPill
│               Avatar.tsx      Avatar, RatingDots
│               Misc.tsx        Divider, Mono, Money, Eyebrow, PageHeader, Breadcrumb,
│                               NumberedSteps, ProgressList, StickyBar
└─ app/preview/page.tsx     ← 전체 컴포넌트 쇼케이스 (npm run dev → /preview)
```

import 은 폴더 배럴에서 합니다.

```tsx
import { Button } from "@/components/buttons";
import { Card, CardHeader, Field, Input, Money } from "@/components/common";
import { Modal, useToast } from "@/components/feedback";
import { AppShell, Container } from "@/components/layouts";
import { ChatRoom, ChatMessage } from "@/components/domains/chat";
```

`ToastProvider` 는 `app/layout.tsx` 에서 이미 트리 전체를 감싸고 있습니다. `useToast()` 만 호출하면 됩니다.

## 2. 기본 레이아웃

`src/app/(site)/layout.tsx` 가 `AppShell` 로 GNB·본문·푸터 골격을 만듭니다. 이 route group 아래에 만드는
페이지는 헤더와 푸터를 따로 붙이지 않습니다.

```tsx
// src/app/(site)/layout.tsx
export default function SiteLayout({ children }) {
  return <AppShell>{children}</AppShell>;
}
```

- **GNB 는 화면 전체 폭**(좌우 32px), **푸터는 본문과 같은 720** 입니다. 캡처에서 랜딩 히어로·증상 카드·
  푸터가 모두 720 한 폭으로 맞춰져 있고 GNB 만 끝까지 갑니다.
- **본문 폭은 레이아웃이 정하지 않습니다.** 화면마다 다르기 때문에(일반 720 · 관리자 840)
  각 페이지가 `<Container width="content | wide | landing">` 로 감쌉니다.
- `AppShell` 옵션: `user`(로그인 헤더로 전환) · `nav`(메뉴 교체) · `hideFooter` · `headerWidth` · `footerWidth`.

`src/app/(auth)/layout.tsx` 는 GNB·푸터가 없는 화면의 골격입니다. 연회색 배경 위에 내용을 가운데 놓기만
하고 카드는 각 페이지가 그립니다. 로그인(`1_로그인`)과 운영 콘솔 로그인(`9_로그인`)이 같은 골격입니다.
- 세션(화상) 화면이나 운영 콘솔처럼 골격이 다른 화면은 이 그룹 밖에 두고 별도 레이아웃을 만듭니다.

`src/app/(rooms)/layout.tsx` 는 `RoomSidebar` 와 방 본문을 배치합니다. 정산은 조회 월이
`searchParams`에 있어야 하므로 route group 레이아웃이 아니라 각 페이지의 `SettlementShell`이
`SettlementSidebar`와 본문을 배치합니다.

### Header

| 상태 | 구성 |
|---|---|
| 비로그인 | 로고 + `PUBLIC_NAV`(이용 방법 · 의뢰 · 개발자 찾기 · 사전점검) + 로그인 |
| 로그인 | 로고 + `APP_NAV`(의뢰 · 개발자 찾기 · 사전점검 · 정산) + 알림 + 아바타 메뉴 |

높이는 56px(`h-gnb`) 고정이고, 현재 경로를 `usePathname` 으로 읽어 `NavPill` 활성 상태를 정합니다.
**md 미만에서는 메뉴가 햄버거 패널로 접힙니다.** 알림·아바타는 자주 쓰는 동작이라 접지 않습니다.
`user` 를 넘기지 않으면 비로그인 헤더입니다. `pending` 이 true 면 로그인 여부를 아직 모르는 상태라
로그인 링크도 아바타도 그리지 않습니다(로그인한 사용자에게 '로그인' 이 잠깐 비치지 않게).

`(site)/layout.tsx` 는 `AppShell` 대신 `SessionShell`(domains/auth) 을 씁니다. 인증 스토어(`stores/auth`)의
상태를 읽어 `user`·`pending`·`onLogout` 을 채우고 본문은 서버 컴포넌트 그대로 지나갑니다. 레이아웃에서
쿠키를 읽으면 랜딩까지 동적 렌더링이 되기 때문에 헤더만 클라이언트에서 채웁니다(컨벤션 7장).
방 레이아웃처럼 이미 서버에서 `getMe()` 를 읽는 화면은 그 값을 `<AppShell user>` 로 그대로 넘깁니다.

### 좁은 화면 대응

컨벤션 12장에 맞춰 컴포넌트 쪽에서 아래를 보장합니다. 화면을 만들 때 따로 처리하지 않아도 됩니다.

- `Header`: md 미만에서 메뉴가 햄버거 패널로 접힙니다. 로고·알림·아바타는 항상 보입니다.
- `NavPill`: `whitespace-nowrap` + `shrink-0` — 폭이 모자라도 글자가 세로로 쪼개지지 않습니다.
- `SegmentedTabs`: 탭이 많으면 트랙 안에서만 가로 스크롤됩니다(페이지는 가로로 스크롤되지 않습니다).
- `Field layout="horizontal"`: sm 미만에서 라벨이 입력 위로 쌓입니다.
- `Container`: 좌우 여백 16px(sm 이상 24px), GNB 는 32px.

### Footer

브랜드 · 링크 3열(서비스 / 개발자 / 정책) · 통신판매중개자 고지 · 사업자 정보로 구성됩니다.
링크와 사업자 정보는 `columns`, `notices`, `business` props 의 기본값으로 들어 있고, 값이 확정되면
호출부에서 덮어쓰거나 기본 상수(`DEFAULT_FOOTER_COLUMNS` 등)를 고칩니다. 정책 본문과 정산 방식 안내처럼
아직 화면이 없는 링크는 6절에 따로 적습니다.

## 3. 디자인 규칙 요약 (화면에서 관찰된 것)

| 항목 | 값 |
|---|---|
| 주 보라 | `#6d34e2` · 연보라 배경 `#f2f0ff` · 연보라 테두리 `#d0c1f8` |
| 먹색 | `#111019` — 본문 텍스트이자 **두 번째 solid 버튼 색**(보내기, 서명하고 계약하기) |
| 텍스트 단계 | `#111019` → `#4c4b52` → `#5a5966` → `#88878c` → `#acacb2`(비활성) |
| 면/테두리 | 회색 면 `#f3f2f8` · 테두리 `#dfdfe8` · hairline `#ececf1` |
| 시맨틱 | 성공 `#006d32`/`#e9f7ee` · 경고 `#8e4f00`/`#fff6e8` · 위험 `#ac011a`/`#fdeeef` · 정보 `#0b4fa8` |
| 라운드 | 체크박스 4 · 사각 배지 6 · **버튼/인풋 8** · 드롭다운/배너 10 · **카드 12** · 모달 14 · pill 999 |
| 컨트롤 높이 | sm 32 · md 40 · lg 48 · xl 56 |
| 그림자 | 거의 없음(플랫). 드롭다운·팝오버·모달·세그먼트 활성 탭에만 |
| 레이아웃 | GNB 56px · 본문 720px · 사이드바 320px · 관리자 840px · 랜딩 960px |
| 숫자 | **금액·시각·날짜·카운터·STEP 은 전부 monospace + tabular-nums** — 가장 뚜렷한 규칙 |
| 탭 | 언더라인 탭 없음. 전부 회색 트랙 + 흰 pill 세그먼트 |

색은 컴포넌트에 hex 로 적지 않고 `tokens.css` 의 `--k-*` 변수나 그 변수를 노출한 Tailwind 유틸리티
(`bg-primary`, `text-fg-muted`, `border-line` …)로만 씁니다.

## 4. 컴포넌트 ↔ 화면 대응

| 컴포넌트 | variant / 옵션 | 화면 예 |
|---|---|---|
| `Button` | `primary` `dark` `outline` `ghost` `danger` `kakao` / `sm~xl` / `fullWidth` `loading` | 문제 진단하기 · 보내기 · 취소 · + 항목 추가 |
| `buttonClass` | Button 과 같은 옵션 | 이동만 하는 요소에 버튼 모양 (`<Link className={buttonClass(...)}>`) |
| `IconButton` | 정사각 | 행 삭제 ✕, 월 이동 ‹ › |
| `Field` | `layout="vertical" \| "horizontal"`, `helper` `error` | 설문 라벨(세로) · 계좌/계약서 폼(가로) |
| `Input` | `leftIcon` `suffix` `mono` `underline` `invalid` | 검색, 금액 400,000 원, 기간 10 일 |
| `Textarea` | `showCount` `maxLength` `tone="surface"` | 0 / 255 카운터, 문제 신고 |
| `SearchInput` | 큰 높이 + 돋보기 | 랜딩 히어로, 개발자 찾기 |
| `NativeSelect` | `tone="surface" \| "outline"` | 은행 선택, 14 시 / 00 분 |
| `Dropdown` | `trigger="field" \| "text" \| "pill"` | 펼침_정렬, 펼침_은행선택, 펼침_분야선택 |
| `Checkbox` | `locked`(회색 확정) | 동의 체크, 확정된 확인 문장(취소선) |
| `Radio` / `ChoiceCard` | `type="radio" \| "checkbox"`, `meta`, `compact` | 설문 선택지 카드, 결제 수단 |
| `Switch` | | 포트폴리오 공개/비공개 |
| `RatingScale` | 1~5 + 라벨 | 리뷰 작성(실력/설명력) |
| `Badge` | `tone` 7종 / `shape="pill" \| "square"` / `dot` `plain` | 진행 중, 완료, 보안, 컨설팅 |
| `CountBadge` / `Tag` | `onRemove` | 알림 벨 숫자 · 기술 스택 Next.js ✕ |
| `Chip` | `filter` `toggle` `suggest` / `selected` `onRemove` | 필터 칩, 잘하는 일, 랜딩 예시 문장 |
| `Card` | `outline` `surface` `soft` `elevated` / `interactive` `selected` | 증상 카드, 요약 카드, 연보라 안내 카드 |
| `CardHeader` / `CardSection` | | 요구사항 · 3건 · 합계 650,000원 |
| `KeyValueRow` | `mono` `action` `labelWidth` | 계약서 조항, 마이페이지 설정 행 |
| `SectionCard` | `note` | 마이페이지 계정·계좌·약관 칸, 탈퇴 화면의 정리해야 할 것 |
| `StatCard` | `size` `tone` `badge` | 계약이 끝나면 받을 돈 650,000원 |
| `SegmentedTabs` | `count` `disabled` `fullWidth` | 채팅 6 / 계약서 / 진행 / 확인 3 / 리뷰 · 의뢰인｜개발자 |
| `NavPill` | `active` | GNB 메뉴 |
| `Avatar` / `RatingDots` | `xs~xl` `status` / `tone` | GNB 32, 리스트 44, 프로필 72 · 실력 ●●●●○ 4.4 |
| `Callout` | `info` `neutral` `warning` `danger` `success` / `action` | 등록한 계좌가 없습니다 |
| `HelpRow` / `EmptyState` | `boxed` | 확인이 어려우면 · 등록한 카드가 없습니다 |
| `Modal` | `sm/md/lg` `dismissible` `showClose` | 서명 모달 |
| `Popover` / `MenuItem` | `align` | 해결 처리할까요?, 아바타 메뉴 |
| `ToastProvider` / `useToast` | `tone` `action` | 공통 정의 |
| `Money` / `Mono` | `from` `signed` `tone` | 50,000원 ~, +650,000, -50,000 |
| `PageHeader` / `Breadcrumb` | | 의뢰 / 개발 → 수제 비누 가게 |
| `NumberedSteps` / `ProgressList` | `done` `active` `pending` | 예치금 처리 방식 1·2·3 · 진행 상황 |
| `Divider` / `StickyBar` | `label` | 채팅 날짜 구분 · 하단 고정 CTA |
| `ChatRoom` | `banner` `footer` `bodyClassName` | 상담·컨설팅·개발 방의 대화 카드 |
| `ChatMessage` | `mine` `author` `time` `hideAvatar` | 내 말(연보라·우측) / 상대 말(흰 면·아바타) |
| `ChatDateDivider` | | 8월 22일 |
| `ChatComposer` | `onSend` `disabled` `sending` | 메시지를 입력하세요 + 보내기 (Enter 전송) |
| `ChatNotice` | `action` | 개발 계약으로 진행해 이 상담은 마무리됐습니다 + 개발 방으로 |
| `ChatSessionBanner` | `title` `description` `action` | 컨설팅 세션 · 8월 29일 14:00 · 45분 + 입장 |
| `RoomSidebar` | `rooms` `me` `previews` | 맡긴 의뢰·맡은 의뢰 목록과 현재 방 표시 |
| `SettlementSidebar` | 소비·소득·예치금 요약 | 월별 소비, 개발 소득, 계약별 예치금 이동 |
| `SettlementShell` | `month` `spending` `income` `deposits` `title` | 정산 화면의 320px 좌측 목록과 본문 배치 |
| `SpendingView` / `IncomeView` / `DepositsView` | 정산 응답 | 소비 내역 · 소득 내역 · 예치금 현황 |
| `AccountVerificationForm` / `AccountVerificationCodeInput` | 계좌 입력 · 네 자리 인증번호 | `/mypage/account` 계좌 등록·변경 |
| `ScheduleView` | `schedule` `now` | `/mypage/schedule` 의뢰한 일정과 맡은 일정 |
| `ReportForm` | `roomId` `contractId` | `/rooms/{roomId}/report` 문제 신고 |

## 5. 사용 예

```tsx
import { Button } from "@/components/buttons";
import { Card, CardHeader, Field, Input, KeyValueRow, Money, Badge } from "@/components/common";

<Card>
  <CardHeader title="계약 조건" action={<Mono>3건 · 합계 650,000원</Mono>} />
  <KeyValueRow label="예치" value={<Money value={650000} />} />
</Card>

<Field label="가격" helper="금액이나 기간이 달라지면 개발자가 계약서를 다시 보냅니다.">
  <Input mono suffix="원" defaultValue="400,000" />
</Field>

<Badge tone="brand">진행 중</Badge>
<Button variant="dark" size="lg" fullWidth>서명하고 650,000원 예치하기</Button>
```

설정 화면처럼 「제목 + 줄 목록」이 이어지는 곳은 `SectionCard` 로 감쌉니다.
제목 오른쪽의 작은 안내는 `note` 입니다.

```tsx
import { KeyValueRow, SectionCard } from "@/components/common";

<SectionCard title="계좌" note="환불과 대금을 받는 계좌입니다">
  <KeyValueRow label="등록 계좌" value="신한은행 ···· 4412" action={<Button size="sm">계좌 변경</Button>} />
</SectionCard>
```

## 6. 아직 남은 것

- **다크 모드**: 토큰은 현재 라이트 전용입니다. `tokens.css` 아래쪽에 다크 블록 자리를 주석으로 잡아 두었고,
  디자인에서 팔레트가 확정되면 같은 이름의 `--k-*` 변수만 재정의하면 컴포넌트 전체가 따라옵니다.
  (컨벤션 11장 기준 미완 항목입니다.)
- **본문 폰트**: 디자인 기준은 Pretendard 이지만 next/font 로 자체 호스팅하기 위해 `Noto Sans KR` 로 대체해
  두었습니다. Pretendard woff2 를 확보하면 `app/layout.tsx` 에서 `next/font/local` 로 교체합니다.
- **아직 화면이 없는 링크**: 정책 본문(`/policies/*`)과 푸터의 정산 방식 안내
  (`/settlements/guide`)는 경로만 있고 화면은 없습니다. 목적지는 `src/lib/routes.ts` 의 `ROUTES` 에서
  관리하며 `Header.tsx`·`Footer.tsx` 의 메뉴 상수에는 URL 을 직접 적지 않습니다.
- **세션 컨트롤 바**: 일정과 채팅의 세션 안내는 구현되어 있지만 실제 화상 세션 화면의 입장·종료 컨트롤은
  아직 별도 도메인 컴포넌트로 구현되지 않았습니다.

의뢰 좌측 목록(`RoomSidebar`), 정산 좌측 목록과 소비·소득·예치금 화면, 계좌 인증번호 입력,
내 일정, 문제 신고는 구현되어 있습니다. `/checkup`, `/settlements`, `/mypage/account`,
`/mypage/profile`, `/mypage/schedule`, `/mypage/withdrawal`, `/rooms/{roomId}/report`는 실제 화면 경로입니다.
