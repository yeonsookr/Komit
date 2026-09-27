"use client";

import * as React from "react";
import { Button, IconButton } from "@/components/buttons";
import { Container, Footer, Header } from "@/components/layouts";
import { ChatComposer, ChatDateDivider, ChatMessage, ChatNotice, ChatRoom, ChatSessionBanner } from "@/components/domains/chat";
import {
  Avatar,
  Badge,
  Breadcrumb,
  Card,
  CardHeader,
  CardSection,
  Checkbox,
  Chip,
  ChipGroup,
  ChoiceCard,
  CountBadge,
  Divider,
  Dropdown,
  Eyebrow,
  Field,
  Input,
  KeyValueRow,
  Money,
  NativeSelect,
  NavPill,
  NumberedSteps,
  PageHeader,
  ProgressList,
  Radio,
  RatingDots,
  RatingScale,
  SearchInput,
  SegmentedTabs,
  StatCard,
  StatusDot,
  Switch,
  Tag,
  Textarea,
} from "@/components/common";
import {
  Callout,
  EmptyState,
  HelpRow,
  MenuDivider,
  MenuItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Popover,
  useToast,
} from "@/components/feedback";

function Section({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <section className="scroll-mt-20" id={title}>
      <div className="mb-5 flex items-baseline gap-3">
        <h2 className="text-xl font-bold">{title}</h2>
        {description && <p className="text-[13px] text-fg-subtle">{description}</p>}
      </div>
      <div className="flex flex-col gap-6">{children}</div>
    </section>
  );
}

function Row({ label, children }: { label?: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      {label && <p className="text-xs font-semibold text-fg-subtle uppercase tracking-wide">{label}</p>}
      <div className="flex flex-wrap items-center gap-3">{children}</div>
    </div>
  );
}

const SORT = [
  { value: "skill", label: "실력 높은 순" },
  { value: "explain", label: "설명력 높은 순" },
  { value: "price", label: "비용 낮은 순" },
  { value: "count", label: "계약 많은 순" },
];
const BANKS = ["국민은행", "신한은행", "우리은행", "하나은행", "농협은행", "카카오뱅크", "토스뱅크"];

export default function Preview() {
  const toast = useToast();
  const [role, setRole] = React.useState("client");
  const [tab, setTab] = React.useState("progress");
  const [sort, setSort] = React.useState("skill");
  const [bank, setBank] = React.useState<string | undefined>();
  const [pay, setPay] = React.useState("card");
  const [skills, setSkills] = React.useState<string[]>(["인터넷에 올리기", "DB 연결"]);
  const [open, setOpen] = React.useState(false);
  const [pop, setPop] = React.useState(false);
  const [menu, setMenu] = React.useState(false);
  const [pub, setPub] = React.useState(true);
  const [rating, setRating] = React.useState<number | undefined>(4);

  const toggleSkill = (s: string) =>
    setSkills((l) => (l.includes(s) ? l.filter((x) => x !== s) : [...l, s]));

  return (
    <div className="flex min-h-screen flex-col bg-bg">
      {/* GNB — 공통 Header 를 그대로 씁니다 */}
      <Header user={{ name: "김미미", unreadCount: 3 }} onLogout={() => {}} />

      <main className="mx-auto flex w-full max-w-[960px] flex-1 flex-col gap-16 px-4 py-12 sm:px-8">
        <div>
          <Eyebrow>Komit Design System</Eyebrow>
          <h1 className="mt-1 text-[28px] font-bold">공통 컴포넌트</h1>
          <p className="mt-2 text-sm text-fg-muted">
            67개 화면에서 추출한 기본 UI 요소입니다. 모든 색상·라운드·간격은 <code className="font-mono text-xs">styles/tokens.css</code> 의 CSS 변수를 참조합니다.
          </p>
        </div>

        {/* ---------------- Colors ---------------- */}
        <Section title="Tokens" description="styles/tokens.css">
          <div className="grid grid-cols-4 gap-3 sm:grid-cols-6">
            {[
              ["primary", "bg-primary"],
              ["primary-soft", "bg-primary-soft"],
              ["ink", "bg-ink"],
              ["surface", "bg-surface"],
              ["line", "bg-line"],
              ["fg-subtle", "bg-fg-subtle"],
              ["success", "bg-success"],
              ["success-bg", "bg-success-bg"],
              ["warning", "bg-warning"],
              ["warning-bg", "bg-warning-bg"],
              ["danger", "bg-danger"],
              ["danger-bg", "bg-danger-bg"],
            ].map(([n, c]) => (
              <div key={n} className="flex flex-col gap-1.5">
                <div className={`h-12 rounded-md border border-line-subtle ${c}`} />
                <span className="font-mono text-[11px] text-fg-subtle">{n}</span>
              </div>
            ))}
          </div>
          <Row label="Typography">
            <div className="flex flex-col gap-2">
              <p className="text-[28px] font-bold leading-tight">페이지 제목 28 / Bold</p>
              <p className="text-xl font-bold">섹션 제목 20 / Bold</p>
              <p className="text-base font-bold">카드 제목 16 / Bold</p>
              <p className="text-sm">본문 14 / Regular — 계약이 끝나면 대금을 한 번에 보냅니다.</p>
              <p className="text-[13px] text-fg-subtle">보조 13 / fg-subtle — 8/18 16:40 갱신됨</p>
              <p className="font-mono text-sm tabular-nums">Mono — 650,000원 · 10:14 · 2026.03.01 · STEP 1 / 4</p>
            </div>
          </Row>
        </Section>

        {/* ---------------- Button ---------------- */}
        <Section title="Button" description="primary · dark · outline · ghost · danger / sm · md · lg · xl">
          <Row label="variants">
            <Button>상담 시작하기</Button>
            <Button variant="dark">보내기</Button>
            <Button variant="outline">취소</Button>
            <Button variant="ghost">+ 항목 추가</Button>
            <Button variant="danger">문제 신고</Button>
          </Row>
          <Row label="sizes">
            <Button size="sm">저장</Button>
            <Button size="md">다음</Button>
            <Button size="lg" rightIcon={<ArrowIcon />}>진단서 보기</Button>
            <Button size="xl">착수하고 김미미님에게 알리기</Button>
          </Row>
          <Row label="states">
            <Button disabled>확인된 기능 제출</Button>
            <Button loading>보내는 중</Button>
            <Button variant="outline" leftIcon={<CopyIcon />}>복사</Button>
            <IconButton aria-label="삭제"><XIcon /></IconButton>
            <IconButton aria-label="이전" variant="outline" size="sm">‹</IconButton>
          </Row>
          <Row label="button group (계약서 하단)">
            <div className="grid w-full max-w-md grid-cols-2 gap-3">
              <Button variant="outline" size="lg">수정 제안하기</Button>
              <Button variant="dark" size="lg">서명하고 계약하기</Button>
            </div>
          </Row>
        </Section>

        {/* ---------------- Input ---------------- */}
        <Section title="Input" description="Field · Input · Textarea · SearchInput">
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="점검을 진행할 프로젝트명을 입력해주세요" helper="3분이면 끝나요.">
              <Input placeholder="예) 수제 비누 가게" />
            </Field>
            <Field label="계좌번호" error="하이픈 없이 숫자만 적어주세요">
              <Input placeholder="하이픈 없이 숫자만" invalid mono />
            </Field>
            <Field label="가격">
              <Input defaultValue="400,000" suffix="원" mono />
            </Field>
            <Field label="기간">
              <Input defaultValue="10" suffix="일" mono containerClassName="w-28" />
            </Field>
          </div>
          <SearchInput placeholder="예: 딸한테 주소를 보내도 안 열려요" />
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="무슨 일이 있었는지 적어주세요">
              <Textarea placeholder="예) 결제 버튼을 누르면 가끔 아무 반응이 없어요" maxLength={300} showCount />
            </Field>
            <Field label="신고 내용을 적어주세요">
              <Textarea tone="surface" maxLength={255} showCount defaultValue="관리자 주소로 들어가면 로그인 화면이 뜨지 않습니다." />
            </Field>
          </div>
          <Card padding="none" className="divide-y divide-line-subtle">
            <div className="px-5 py-3.5"><Field layout="horizontal" label="은행"><NativeSelect placeholder="은행을 선택해주세요">{BANKS.map((b) => <option key={b}>{b}</option>)}</NativeSelect></Field></div>
            <div className="px-5 py-3.5"><Field layout="horizontal" label="계좌번호"><Input placeholder="하이픈 없이 숫자만 적어주세요" mono /></Field></div>
            <div className="px-5 py-3.5"><Field layout="horizontal" label="예금주" helper="통장에 적힌 이름 그대로 적어주세요"><Input /></Field></div>
          </Card>
        </Section>

        {/* ---------------- Select ---------------- */}
        <Section title="Select / Dropdown" description="NativeSelect · Dropdown(field / text / pill)">
          <Row>
            <div className="w-64"><Dropdown options={BANKS.map((b) => ({ value: b, label: b }))} value={bank} onChange={setBank} placeholder="은행을 선택해주세요" /></div>
            <Dropdown trigger="text" prefix="정렬" options={SORT} value={sort} onChange={setSort} align="end" />
            <Dropdown trigger="pill" options={[{ value: "all", label: "분야 선택" }, ...SORT]} placeholder="분야 선택" onChange={() => {}} />
            <div className="w-40"><NativeSelect tone="outline" defaultValue="14"><option value="14">14 시</option><option value="15">15 시</option></NativeSelect></div>
          </Row>
        </Section>

        {/* ---------------- Choice ---------------- */}
        <Section title="Checkbox / Radio / Switch" description="Checkbox · Radio · ChoiceCard · Switch · RatingScale">
          <Row>
            <Checkbox label="계약서 내용을 확인했고 이에 동의합니다" defaultChecked />
            <Checkbox label="관리자 주소로 들어가면 로그인 화면이 뜬다" locked defaultChecked />
            <Checkbox label="미체크" />
            <Checkbox label="비활성" disabled />
          </Row>
          <Row>
            <Radio name="r" label="배포는 했는데 아직 아무도 안 씁니다" defaultChecked />
            <Radio name="r" label="실제 결제를 받습니다" />
            <Switch checked={pub} onChange={setPub} label={pub ? "공개" : "비공개"} />
          </Row>
          <div className="grid max-w-lg gap-2 sm:grid-cols-2">
            <ChoiceCard type="radio" name="pay" value="card" label="카드 결제" meta="일시불" checked={pay === "card"} onChange={() => setPay("card")} />
            <ChoiceCard type="radio" name="pay" value="bank" label="계좌 이체" checked={pay === "bank"} onChange={() => setPay("bank")} />
            <ChoiceCard type="checkbox" label="보안이 걱정돼요" defaultChecked />
            <ChoiceCard type="checkbox" label="비용이 걱정돼요" />
          </div>
          <Row label="compact">
            <ChoiceCard compact type="checkbox" label="속도가 느림" defaultChecked />
            <ChoiceCard compact type="checkbox" label="모바일에서 깨짐" />
            <ChoiceCard compact type="checkbox" label="없음" />
          </Row>
          <Card className="max-w-lg">
            <p className="mb-3 text-sm font-semibold">실력</p>
            <RatingScale name="skill" value={rating} onChange={setRating} labels={["매우 아쉬움", "아쉬움", "보통", "좋음", "매우 좋음"]} />
          </Card>
        </Section>

        {/* ---------------- Badge / Chip ---------------- */}
        <Section title="Badge / Chip / Tag" description="표시 전용은 Badge, 클릭 가능한 건 Chip">
          <Row label="badge · pill">
            <Badge tone="brand">진행 중</Badge>
            <Badge tone="neutral">예치 대기</Badge>
            <Badge tone="success">완료</Badge>
            <Badge tone="warning">9월 5일 자동 통과</Badge>
            <Badge tone="danger">계약 전</Badge>
            <Badge tone="info">지급 예정</Badge>
            <Badge tone="ink">운영 콘솔</Badge>
          </Row>
          <Row label="badge · square (카테고리 / 방 유형)">
            <Badge shape="square" tone="danger">보안</Badge>
            <Badge shape="square" tone="warning">위법</Badge>
            <Badge shape="square" tone="brand">결제</Badge>
            <Badge shape="square" tone="neutral">기타</Badge>
            <Badge shape="square" size="sm" tone="success">상담</Badge>
            <Badge shape="square" size="sm" tone="warning">컨설팅</Badge>
            <Badge shape="square" size="sm" tone="brand">개발</Badge>
          </Row>
          <Row label="dot / count / status dot">
            <Badge dot plain tone="success">확인됨</Badge>
            <Badge dot plain tone="warning">확인 중</Badge>
            <Badge dot tone="success">일치</Badge>
            <CountBadge count={3} />
            <CountBadge count={120} />
            <StatusDot tone="success" /> <StatusDot tone="warning" /> <StatusDot tone="neutral" />
          </Row>
          <Row label="chip · filter">
            <ChipGroup>
              {["인터넷에 올리기", "DB 연결", "버그 잡기", "알림 보내기"].map((s) => (
                <Chip key={s} selected={skills.includes(s)} onClick={() => toggleSkill(s)} onRemove={skills.includes(s) ? () => toggleSkill(s) : undefined}>
                  {s}
                </Chip>
              ))}
            </ChipGroup>
          </Row>
          <Row label="chip · toggle / suggest">
            <Chip variant="toggle" selected>도메인 연결</Chip>
            <Chip variant="toggle">로그인 붙이기</Chip>
            <Chip variant="suggest">예약 버튼이 반응이 없어요</Chip>
            <Chip variant="suggest">갑자기 API 비용이 40만원 나갔어요</Chip>
          </Row>
          <Row label="tag">
            <Tag>TypeScript</Tag>
            <Tag onRemove={() => {}}>Next.js</Tag>
            <Tag onRemove={() => {}}>Supabase</Tag>
          </Row>
        </Section>

        {/* ---------------- Card ---------------- */}
        <Section title="Card" description="outline · surface · soft · elevated / CardHeader · KeyValueRow · StatCard">
          <div className="grid gap-4 sm:grid-cols-3">
            <Card interactive>
              <GlobeIcon />
              <p className="mt-3 text-base font-bold">남에게 안 보여요</p>
            </Card>
            <Card interactive selected>
              <p className="text-base font-bold">뭐가 문제인지 몰라요</p>
            </Card>
            <Card variant="surface">
              <CardHeader size="sm" title="확인 문장" action="3건" />
              <p className="mt-2 text-[13px] text-fg-muted">관리자 주소로 들어가면 로그인 화면이 뜬다</p>
            </Card>
          </div>
          <Card>
            <CardHeader title="계약 조건" action={<span className="font-mono">3건 · 합계 650,000원 · 25일</span>} />
            <CardSection className="mt-3 pt-0 border-0">
              <KeyValueRow label="하자보수" value="통과 후 14일" />
              <KeyValueRow label="인계물" value="소스코드 · 배포 설정 · 관리자 계정" />
              <KeyValueRow label="예치" mono value="650,000원" action={<Button variant="ghost" size="sm">원장 보기 ›</Button>} />
            </CardSection>
          </Card>
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard className="sm:col-span-2" label="계약이 끝나면 받을 돈" value="650,000" unit="원" tone="info" badge={<Badge tone="success">4건 중 1건 통과</Badge>} description="개발 대금은 4건을 모두 통과한 뒤 3영업일 이내에 한 번에 들어갑니다." />
            <div className="flex flex-col gap-3">
              <StatCard size="md" label="예치한 돈" value="650,000" unit="원" />
              <StatCard size="md" variant="outline" label="남은 작업" value="250,000" unit="원" tone="primary" />
            </div>
          </div>
        </Section>

        {/* ---------------- Tabs ---------------- */}
        <Section title="Tabs" description="SegmentedTabs — 모든 탭은 pill 세그먼트 형태">
          <Row>
            <SegmentedTabs
              value={tab}
              onChange={setTab}
              items={[
                { value: "chat", label: "채팅", count: 6 },
                { value: "contract", label: "계약서" },
                { value: "progress", label: "진행" },
                { value: "check", label: "확인", count: 3 },
                { value: "review", label: "리뷰", disabled: true },
              ]}
            />
            <SegmentedTabs size="sm" value="all" onChange={() => {}} items={[{ value: "all", label: "전체" }, { value: "unread", label: "안 읽음", count: 3 }]} />
          </Row>
        </Section>

        {/* ---------------- Avatar / Rating ---------------- */}
        <Section title="Avatar / Rating" description="Avatar · RatingDots">
          <Row>
            <Avatar name="이준" size="xs" /><Avatar name="한서" size="sm" /><Avatar name="박선" size="md" /><Avatar name="김미미" size="lg" status="online" /><Avatar name="운영" size="xl" />
          </Row>
          <Card className="max-w-xs">
            <div className="flex items-center gap-3">
              <Avatar name="이준" size="lg" />
              <div><p className="font-bold">이준</p><p className="text-[13px] text-fg-subtle">백엔드 5년</p></div>
            </div>
            <div className="mt-4 flex flex-col gap-1.5">
              <RatingDots label="실력" value={4.4} score="4.4" count={13} />
              <RatingDots label="설명력" value={4.9} score="4.9" tone="primary" count={13} />
            </div>
            <Divider className="my-4" />
            <div className="flex justify-between text-[13px]"><span className="text-fg-subtle">컨설팅</span><Money value={50000} from /></div>
            <div className="mt-1 flex justify-between text-[13px]"><span className="text-fg-subtle">개발</span><Money value={100000} from /></div>
            <Button fullWidth className="mt-4">상담 시작하기</Button>
          </Card>
        </Section>

        {/* ---------------- Callout ---------------- */}
        <Section title="Callout / HelpRow / EmptyState">
          <Callout tone="info" title="계약 진행 중 문제가 생겼나요?">계약이나 작업 상태는 자동으로 바뀌지 않습니다.</Callout>
          <Callout tone="warning" size="sm">통과시키면 그 작업은 완료로 확정되고 되돌릴 수 없습니다.</Callout>
          <Callout tone="warning" title="등록한 계좌가 없습니다" action={<Button variant="outline" size="sm">마이페이지에서 등록</Button>}>계좌를 등록해야 계약이 끝날 때 대금을 보낼 수 있습니다.</Callout>
          <Callout tone="danger" title="지금은 탈퇴할 수 없습니다">진행 중인 의뢰 1건과 예치금 650,000원이 남아 있습니다.</Callout>
          <Callout tone="neutral" size="sm">어떤 알림을 받을지는 마이페이지 › 알림에서 끄고 켤 수 있습니다.</Callout>
          <HelpRow boxed label="확인이 어려우면" action={<Button variant="outline" size="sm">무료로 물어보기</Button>}>개발자에게 물어보고 확인할 수 있습니다.</HelpRow>
          <HelpRow label="답이 없으면" action={<Button variant="danger" size="sm">문제 신고</Button>}>Komit 이 중간에서 확인합니다.</HelpRow>
          <EmptyState title="등록한 카드가 없습니다" description="결제 수단을 등록하면 계약 예치를 바로 진행할 수 있습니다." action={<Button size="sm">카드 등록</Button>} />
        </Section>

        {/* ---------------- Misc ---------------- */}
        <Section title="Misc" description="PageHeader · Breadcrumb · NumberedSteps · ProgressList · Divider · Money">
          <PageHeader
            breadcrumb={<Breadcrumb items={["의뢰", "개발"]} />}
            title="수제 비누 가게"
            badge={<Badge tone="brand">진행 중</Badge>}
            meta="이준 개발자 · 8/22 개설"
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <Card variant="surface">
              <p className="mb-4 text-sm font-bold">예치금 처리 방식</p>
              <NumberedSteps items={[
                { title: "예치", description: "계약 금액을 Komit 이 보관합니다." },
                { title: "확인", description: "작업마다 확인 문장을 체크합니다." },
                { title: "지급", description: "모두 통과하면 한 번에 지급합니다." },
              ]} />
            </Card>
            <Card>
              <p className="mb-2 text-sm font-bold">진행 상황</p>
              <ProgressList items={[
                { label: "예치 완료", status: "done", right: <Money value={650000} /> },
                { label: "1차 · 관리자 API에 인증 추가", sub: "통과 확정", status: "done", right: <Money value={400000} /> },
                { label: "2차 · 회원탈퇴 경로 구현", sub: "확인 대기", status: "active", right: <Money value={250000} /> },
                { label: "계약 완료 후 지급", status: "pending" },
              ]} />
            </Card>
          </div>
          <Divider label="8월 22일" />
          <Row>
            <Money value={650000} /><Money value={-50000} signed tone="danger" /><Money value={650000} signed tone="success" /><Money value={50000} from tone="muted" />
          </Row>
        </Section>

        {/* ---------------- Modal / Toast ---------------- */}
        <Section title="Modal / Popover / Toast">
          <Row>
            <Button onClick={() => setOpen(true)}>서명 모달 열기</Button>
            <span className="relative">
              <Button variant="outline" onClick={() => setPop((v) => !v)}>직접 해결했어요</Button>
              <Popover open={pop} onClose={() => setPop(false)}>
                <p className="text-sm font-bold text-primary">해결 처리할까요?</p>
                <p className="mt-1.5 text-[13px] text-fg-muted">해결 처리하면 이 요구사항이 요청서에서 빠집니다. 언제든 되돌릴 수 있습니다.</p>
                <div className="mt-4 flex justify-end gap-2">
                  <Button variant="outline" size="sm" onClick={() => setPop(false)}>취소</Button>
                  <Button size="sm" onClick={() => setPop(false)}>해결 처리</Button>
                </div>
              </Popover>
            </span>
            <Button variant="outline" onClick={() => toast({ title: "저장했습니다", description: "프로필이 공개됐습니다.", tone: "success" })}>토스트 (성공)</Button>
            <Button variant="outline" onClick={() => toast({ title: "네트워크 오류", tone: "danger", action: { label: "다시 시도", onClick: () => {} } })}>토스트 (에러)</Button>
          </Row>
          <Modal open={open} onClose={() => setOpen(false)} aria-labelledby="sign-title">
            <ModalHeader id="sign-title" title="계약서에 서명해 주세요" description="개발 계약서 · 수제 비누 가게 · 650,000원" />
            <ModalBody>
              <div className="flex h-[150px] items-center justify-center rounded-md border border-primary-border bg-primary-soft/50 text-fg-subtle">이 안에 이름을 써 주세요</div>
              <div className="mt-2 flex justify-between text-xs text-fg-subtle"><span>마우스나 손가락으로 그립니다</span><button className="text-primary">지우기</button></div>
              <Checkbox className="mt-5" label="계약서 내용을 확인했고 이에 동의합니다" />
            </ModalBody>
            <ModalFooter>
              <Button variant="outline" onClick={() => setOpen(false)}>취소</Button>
              <Button onClick={() => setOpen(false)}>서명하고 650,000원 예치</Button>
            </ModalFooter>
          </Modal>
        </Section>

        <Section title="채팅" description="ChatRoom · ChatMessage · ChatComposer — 상담/컨설팅/개발 방 공용">
          <Row label="대화 + 입력바">
            <ChatRoom
              className="w-full"
              bodyClassName="max-h-[320px]"
              footer={<ChatComposer onSend={(text) => toast({ title: "보냈습니다", description: text })} />}
            >
              <ChatDateDivider date="8월 20일" />
              <ChatMessage author="이준 개발자" time="10:14">
                요청서 확인했습니다. 관리자 API 인증은 middleware에서 한 번에 처리 가능해 보입니다. 회원탈퇴는 데이터 보관 정책을 어떻게 하실지 알려주세요.
              </ChatMessage>
              <ChatMessage mine time="10:32">
                보관은 30일 후 완전 삭제로 부탁드려요
              </ChatMessage>
              <ChatMessage author="이준 개발자" time="11:02">
                알겠습니다. 계약서 보내드릴게요.
              </ChatMessage>
            </ChatRoom>
          </Row>

          <Row label="세션 안내 + 마무리 안내">
            <ChatRoom
              className="w-full"
              banner={
                <ChatSessionBanner
                  title="컨설팅 세션 · 8월 29일 (토) 14:00 · 45분"
                  description="시작 10분 전부터 들어갈 수 있습니다"
                  action={<Button size="sm">입장</Button>}
                />
              }
              footer={
                <ChatNotice action={<Button variant="dark" size="sm">개발 방으로</Button>}>
                  개발 계약으로 진행해 이 상담은 마무리됐습니다. 이어지는 이야기는 개발 방에서 합니다.
                </ChatNotice>
              }
            >
              <ChatDateDivider date="8월 22일" />
              <ChatMessage author="이준 개발자" time="11:20">
                세션 전에 결제 실패가 언제 나는지 대충이라도 알려주시면 미리 볼게요.
              </ChatMessage>
              <ChatMessage mine time="11:44">
                네, 이미지 쪽부터 부탁드립니다
              </ChatMessage>
            </ChatRoom>
          </Row>
        </Section>

        <Section title="레이아웃" description="공통 GNB · 본문 폭 (AppShell) · 푸터는 이 페이지 맨 아래">
          <Row label="Header — 비로그인">
            <div className="w-full overflow-hidden rounded-xl border border-line">
              <Header className="static" />
            </div>
          </Row>
          <Row label="Header — 로그인">
            <div className="w-full overflow-hidden rounded-xl border border-line">
              <Header className="static" user={{ name: "김미미", unreadCount: 3 }} onLogout={() => {}} />
            </div>
          </Row>
          <Row label="Container — 본문 폭">
            <div className="w-full">
              <Container className="bg-primary-soft py-2 text-center text-xs text-primary">content · 720</Container>
              <Container width="wide" className="mt-2 bg-surface py-2 text-center text-xs text-fg-muted">wide · 840</Container>
              <Container width="landing" className="mt-2 bg-surface py-2 text-center text-xs text-fg-muted">landing · 960</Container>
            </div>
          </Row>
        </Section>
      </main>

      <Footer />
    </div>
  );
}

/* icons (demo) */
function ArrowIcon() {
  return <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M5 12h14M13 6l6 6-6 6" /></svg>;
}
function CopyIcon() {
  return <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden><rect x="9" y="9" width="12" height="12" rx="2" /><path d="M5 15V5a2 2 0 0 1 2-2h10" /></svg>;
}
function XIcon() {
  return <svg viewBox="0 0 16 16" className="size-4" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden><path d="M4 4l8 8M12 4l-8 8" /></svg>;
}
function GlobeIcon() {
  return <svg viewBox="0 0 24 24" className="size-6 text-fg-secondary" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18" /></svg>;
}
