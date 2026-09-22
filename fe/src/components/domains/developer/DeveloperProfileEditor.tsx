"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Avatar, Card, Field, Input, NativeSelect, Textarea } from "@/components/common";
import { Button, IconButton } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type {
  CodeItem,
  MyDeveloperProfile,
  MyCareer,
  MyPortfolioItem,
  PriceCatalogItem,
  Review,
  TaskType,
  WriteDeveloperProfileRequest,
} from "@/types/developer";
import type { UploadedFile, UploadPurpose } from "@/types/upload";

interface ProfileAccount { name: string; profileImageUrl: string | null }
interface Props {
  profile: MyDeveloperProfile | null;
  account: ProfileAccount;
  fields: CodeItem[];
  technologies: CodeItem[];
}

type CareerDraft = Omit<MyCareer, "careerId" | "isVerified" | "verifiedAt"> & {
  key: string; careerId: number | null; isVerified: boolean; verifiedAt: string | null;
};
type PortfolioDraft = Omit<MyPortfolioItem, "portfolioItemId"> & { key: string; portfolioItemId: number | null };
type PriceDraft = Omit<PriceCatalogItem, "priceId"> & { key: string; priceId: number | null };

let keyCount = 0;
const nextKey = () => `profile-row-${++keyCount}`;
const nullable = (value: string) => value.trim() === "" ? null : value.trim();

function initialCareers(profile: MyDeveloperProfile | null): CareerDraft[] {
  return (profile?.careers ?? []).map((item) => ({ ...item, key: nextKey() }));
}
function initialPortfolios(profile: MyDeveloperProfile | null): PortfolioDraft[] {
  return (profile?.portfolioItems ?? []).map((item) => ({ ...item, key: nextKey() }));
}
function initialPrices(profile: MyDeveloperProfile | null): PriceDraft[] {
  return (profile?.priceCatalog ?? []).map((item) => ({ ...item, key: nextKey() }));
}

export function DeveloperProfileEditor({ profile, account, fields, technologies }: Readonly<Props>) {
  const router = useRouter();
  const toast = useToast();
  const [intro, setIntro] = React.useState(profile?.intro ?? "");
  const [description, setDescription] = React.useState(profile?.description ?? "");
  const [fieldCodeId, setFieldCodeId] = React.useState(profile?.field?.codeId ? String(profile.field.codeId) : "");
  const [years, setYears] = React.useState(profile?.yearsOfExperience === null || profile?.yearsOfExperience === undefined ? "" : String(profile.yearsOfExperience));
  const [activeFrom, setActiveFrom] = React.useState(profile?.activeFrom ?? "");
  const [activeTo, setActiveTo] = React.useState(profile?.activeTo ?? "");
  const [techCodeIds, setTechCodeIds] = React.useState(profile?.techCategories.map((item) => item.codeId) ?? []);
  const [careers, setCareers] = React.useState(() => initialCareers(profile));
  const [portfolios, setPortfolios] = React.useState(() => initialPortfolios(profile));
  const [prices, setPrices] = React.useState(() => initialPrices(profile));
  const [reviews, setReviews] = React.useState(profile?.reviews ?? []);
  const [dirty, setDirty] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

  const changed = <T,>(setter: React.Dispatch<React.SetStateAction<T>>, value: React.SetStateAction<T>) => {
    setter(value);
    setDirty(true);
  };

  const moveToPhoto = () => {
    if (dirty && !window.confirm("저장하지 않은 프로필 수정 내용이 있습니다. 내용을 버리고 사진 변경으로 이동할까요?")) return;
    router.push(`${ROUTES.mypage}#profile-image`);
  };

  const upload = async (file: File, purpose: UploadPurpose) => {
    const form = new FormData();
    form.append("file", file);
    form.append("purpose", purpose);
    const { data } = await clientApi.post<UploadedFile>(INTERNAL.PROFILE_UPLOAD, form);
    return data;
  };

  const save = async () => {
    if (intro.trim() === "") {
      toast({ tone: "danger", title: "한 줄 소개를 입력해 주세요." });
      return;
    }
    if (careers.some((item) => !item.company.trim() || !item.role.trim() || !item.startDate)) {
      toast({ tone: "danger", title: "경력의 회사, 역할, 시작일을 입력해 주세요." });
      return;
    }
    if (portfolios.some((item) => !item.title.trim())) {
      toast({ tone: "danger", title: "작업물 제목을 입력해 주세요." });
      return;
    }
    if (prices.some((item) => !item.taskName.trim() || item.price <= 0)) {
      toast({ tone: "danger", title: "가격표의 작업 이름과 금액을 확인해 주세요." });
      return;
    }

    const body: WriteDeveloperProfileRequest = {
      intro: intro.trim(),
      description: nullable(description),
      fieldCodeId: fieldCodeId ? Number(fieldCodeId) : null,
      yearsOfExperience: years ? Number(years) : null,
      activeFrom: nullable(activeFrom),
      activeTo: nullable(activeTo),
      techCodeIds,
      careers: careers.map(({ careerId, company, role, startDate, endDate, evidenceFileId }) => ({
        careerId, company: company.trim(), role: role.trim(), startDate,
        endDate: nullable(endDate ?? ""), evidenceFileId,
      })),
      portfolioItems: portfolios.map(({ portfolioItemId, title, description: itemDescription, linkUrl, thumbnailFileId }) => ({
        portfolioItemId, title: title.trim(), description: nullable(itemDescription ?? ""),
        linkUrl: nullable(linkUrl ?? ""), thumbnailFileId,
      })),
      prices: prices.map(({ priceId, taskType, taskName, price, periodDays }) => ({
        priceId, taskType, taskName: taskName.trim(), price,
        periodDays: taskType === "CONSULT" ? null : periodDays,
      })),
    };

    setSaving(true);
    try {
      await clientApi.put(INTERNAL.MY_DEVELOPER_PROFILE, body);
      setDirty(false);
      toast({ tone: "success", title: profile ? "프로필을 저장했습니다." : "개발자 프로필을 만들었습니다." });
      router.refresh();
    } catch (error) {
      toast({ tone: "danger", title: error instanceof ApiError ? error.message : "프로필을 저장하지 못했습니다." });
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="mt-10 space-y-12" onChange={() => setDirty(true)} onSubmit={(event) => { event.preventDefault(); void save(); }}>
      <ProfileSection title="기본 정보">
        <Field label="사진" layout="horizontal">
          <div className="flex flex-wrap items-center gap-4">
            <Avatar size="xl" name={account.name} src={profile?.profileImageUrl ?? account.profileImageUrl ?? undefined} />
            <div><Button variant="outline" size="sm" onClick={moveToPhoto}>사진 바꾸기</Button><p className="mt-2 text-xs text-fg-subtle">사진을 변경하면 전체 프로필에 반영됩니다.</p></div>
          </div>
        </Field>
        <Field label="이름" layout="horizontal"><Input value={account.name} disabled /></Field>
        <Field label="한 줄 소개" layout="horizontal" required><Input value={intro} maxLength={100} onChange={(e) => setIntro(e.target.value)} placeholder="어떤 개발자인지 짧게 소개해 주세요" /></Field>
        <Field label="전문 분야" layout="horizontal"><NativeSelect tone="outline" value={fieldCodeId} onChange={(e) => setFieldCodeId(e.target.value)}><option value="">선택 안 함</option>{fields.map((item) => <option key={item.codeId} value={item.codeId}>{item.codeName}</option>)}</NativeSelect></Field>
        <Field label="경력 연차" layout="horizontal"><Input type="number" min="0" max="99.9" step="0.1" value={years} onChange={(e) => setYears(e.target.value)} suffix="년" /></Field>
        <Field label="활동 시간대" layout="horizontal"><div className="flex items-center gap-2"><Input type="time" value={activeFrom} onChange={(e) => setActiveFrom(e.target.value)} /><span className="text-fg-subtle">~</span><Input type="time" value={activeTo} onChange={(e) => setActiveTo(e.target.value)} /></div></Field>
      </ProfileSection>

      <ProfileSection title="소개">
        <Field label="소개 글" layout="horizontal"><Textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} showCount /></Field>
        <Field label="기술" layout="horizontal"><div className="flex flex-wrap gap-2">{technologies.map((tech) => { const selected = techCodeIds.includes(tech.codeId); return <button type="button" key={tech.codeId} onClick={() => changed(setTechCodeIds, (items) => selected ? items.filter((id) => id !== tech.codeId) : [...items, tech.codeId])} className={`rounded-md border px-3 py-2 text-sm ${selected ? "border-primary bg-primary text-on-primary" : "border-line bg-bg text-fg"}`}>{tech.codeName}</button>; })}</div></Field>
      </ProfileSection>

      <ProfileSection title="경력" action={<Button size="sm" variant="outline" onClick={() => changed(setCareers, (items) => [...items, { key: nextKey(), careerId: null, company: "", role: "", startDate: "", endDate: null, isVerified: false, verifiedAt: null, evidenceFileId: null, evidenceFileName: null }])}>경력 추가</Button>}>
        {careers.length === 0 && <EmptyText>등록한 경력이 없습니다.</EmptyText>}
        {careers.map((item) => <Card key={item.key} className="space-y-3">
          <div className="flex gap-2"><Input value={item.company} maxLength={50} disabled={item.isVerified} placeholder="회사 이름" onChange={(e) => setCareers((rows) => rows.map((row) => row.key === item.key ? { ...row, company: e.target.value } : row))} /><Input value={item.role} maxLength={100} disabled={item.isVerified} placeholder="담당 역할" onChange={(e) => setCareers((rows) => rows.map((row) => row.key === item.key ? { ...row, role: e.target.value } : row))} /><IconButton aria-label="경력 삭제" onClick={() => changed(setCareers, (rows) => rows.filter((row) => row.key !== item.key))}>×</IconButton></div>
          <div className="flex gap-2"><Input type="date" value={item.startDate} disabled={item.isVerified} onChange={(e) => setCareers((rows) => rows.map((row) => row.key === item.key ? { ...row, startDate: e.target.value } : row))} /><Input type="date" value={item.endDate ?? ""} disabled={item.isVerified} onChange={(e) => setCareers((rows) => rows.map((row) => row.key === item.key ? { ...row, endDate: e.target.value || null } : row))} /></div>
          <div className="flex flex-wrap items-center gap-3 text-sm"><label className="cursor-pointer text-primary"><input className="sr-only" type="file" accept=".pdf,image/jpeg,image/png" disabled={item.isVerified} onChange={async (e) => { const file = e.target.files?.[0]; if (!file) return; try { const uploaded = await upload(file, "CAREER_EVIDENCE"); changed(setCareers, (rows) => rows.map((row) => row.key === item.key ? { ...row, evidenceFileId: uploaded.fileId, evidenceFileName: uploaded.originalName } : row)); } catch (error) { toast({ tone: "danger", title: error instanceof ApiError ? error.message : "증빙을 올리지 못했습니다." }); } }} />증빙 파일 선택</label><span className="text-fg-subtle">{item.isVerified ? "확인됨" : item.evidenceFileName ?? "선택한 파일 없음"}</span></div>
        </Card>)}
      </ProfileSection>

      <ProfileSection title="포트폴리오" action={<Button size="sm" variant="outline" onClick={() => changed(setPortfolios, (items) => [...items, { key: nextKey(), portfolioItemId: null, title: "", description: null, linkUrl: null, thumbnailFileId: null, thumbnailUrl: null }])}>작업물 추가</Button>}>
        {portfolios.length === 0 && <EmptyText>등록한 작업물이 없습니다.</EmptyText>}
        {portfolios.map((item) => <Card key={item.key} className="space-y-3">
          <div className="flex gap-2"><Input value={item.title} maxLength={200} placeholder="작업물 제목" onChange={(e) => setPortfolios((rows) => rows.map((row) => row.key === item.key ? { ...row, title: e.target.value } : row))} /><IconButton aria-label="작업물 삭제" onClick={() => changed(setPortfolios, (rows) => rows.filter((row) => row.key !== item.key))}>×</IconButton></div>
          <Input value={item.linkUrl ?? ""} maxLength={2048} placeholder="https://example.com" onChange={(e) => setPortfolios((rows) => rows.map((row) => row.key === item.key ? { ...row, linkUrl: e.target.value } : row))} />
          <Textarea value={item.description ?? ""} maxLength={500} showCount placeholder="작업 내용을 설명해 주세요" onChange={(e) => setPortfolios((rows) => rows.map((row) => row.key === item.key ? { ...row, description: e.target.value } : row))} />
          <div className="flex items-center gap-3"><label className="cursor-pointer text-sm text-primary"><input className="sr-only" type="file" accept="image/png,image/jpeg,image/webp" onChange={async (e) => { const file = e.target.files?.[0]; if (!file) return; try { const uploaded = await upload(file, "PORTFOLIO_IMAGE"); changed(setPortfolios, (rows) => rows.map((row) => row.key === item.key ? { ...row, thumbnailFileId: uploaded.fileId, thumbnailUrl: URL.createObjectURL(file) } : row)); } catch (error) { toast({ tone: "danger", title: error instanceof ApiError ? error.message : "사진을 올리지 못했습니다." }); } }} />사진 선택</label>{item.thumbnailUrl && <><img src={item.thumbnailUrl} alt="" className="size-16 rounded-md object-cover" /><Button size="sm" variant="ghost" onClick={() => changed(setPortfolios, (rows) => rows.map((row) => row.key === item.key ? { ...row, thumbnailFileId: null, thumbnailUrl: null } : row))}>사진 제거</Button></>}</div>
        </Card>)}
      </ProfileSection>

      <ProfileSection title="가격표" action={<Button size="sm" variant="outline" onClick={() => changed(setPrices, (items) => [...items, { key: nextKey(), priceId: null, taskType: "DEVELOP" as TaskType, taskName: "", price: 0, periodDays: null }])}>항목 추가</Button>}>
        {prices.length === 0 && <EmptyText>등록한 가격이 없습니다.</EmptyText>}
        {prices.map((item) => <div key={item.key} className="grid gap-2 sm:grid-cols-[120px_1fr_160px_130px_40px]">
          <NativeSelect tone="outline" value={item.taskType} onChange={(e) => changed(setPrices, (rows) => rows.map((row) => row.key === item.key ? { ...row, taskType: e.target.value as TaskType, periodDays: e.target.value === "CONSULT" ? null : row.periodDays } : row))}><option value="CONSULT">컨설팅</option><option value="DEVELOP">개발</option></NativeSelect>
          <Input value={item.taskName} maxLength={50} placeholder="작업 이름" onChange={(e) => setPrices((rows) => rows.map((row) => row.key === item.key ? { ...row, taskName: e.target.value } : row))} />
          <Input type="number" min="1" value={item.price || ""} suffix="원" onChange={(e) => setPrices((rows) => rows.map((row) => row.key === item.key ? { ...row, price: Number(e.target.value) } : row))} />
          <Input type="number" min="1" max="32767" disabled={item.taskType === "CONSULT"} value={item.periodDays ?? ""} suffix="일" onChange={(e) => setPrices((rows) => rows.map((row) => row.key === item.key ? { ...row, periodDays: e.target.value ? Number(e.target.value) : null } : row))} />
          <IconButton aria-label="가격 삭제" onClick={() => changed(setPrices, (rows) => rows.filter((row) => row.key !== item.key))}>×</IconButton>
        </div>)}
      </ProfileSection>

      {profile && <ReviewSection profile={profile} reviews={reviews} setReviews={setReviews} />}

      {!profile && <p className="text-center text-[13px] text-fg-subtle">저장하면 판매자 약관에 동의한 것으로 간주합니다.</p>}
      <div className="sticky bottom-0 -mx-4 border-t border-line bg-bg/95 px-4 py-4 backdrop-blur"><Button type="submit" fullWidth loading={saving}>{profile ? "저장하고 프로필 공개" : "프로필 만들고 공개"}</Button></div>
    </form>
  );
}

function ProfileSection({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return <section><div className="mb-4 flex items-center justify-between"><h2 className="text-xl font-bold text-fg">{title}</h2>{action}</div><div className="space-y-4">{children}</div></section>;
}
function EmptyText({ children }: { children: React.ReactNode }) { return <p className="rounded-lg bg-surface px-4 py-6 text-center text-sm text-fg-subtle">{children}</p>; }

function ReviewSection({ profile, reviews, setReviews }: { profile: MyDeveloperProfile; reviews: Review[]; setReviews: React.Dispatch<React.SetStateAction<Review[]>> }) {
  return <ProfileSection title="리뷰 점수"><Card variant="surface" className="space-y-5"><div className="grid gap-4 sm:grid-cols-2"><Rating label="실력" score={profile.devRating.score} count={profile.devRating.reviewCount} /><Rating label="설명력" score={profile.explanatoryRating.score} count={profile.explanatoryRating.reviewCount} /></div><div className="space-y-3">{reviews.length === 0 ? <EmptyText>받은 리뷰가 없습니다.</EmptyText> : reviews.map((review) => <ReviewReply key={review.reviewId} review={review} onSaved={(next) => setReviews((items) => items.map((item) => item.reviewId === next.reviewId ? next : item))} />)}</div></Card></ProfileSection>;
}
function Rating({ label, score, count }: { label: string; score: number | null; count: number }) { return <div><p className="text-sm text-fg-subtle">{label}</p><p className="mt-1 font-mono text-lg font-bold text-primary">{score?.toFixed(1) ?? "-"} <span className="text-xs font-normal text-fg-subtle">{count}건</span></p></div>; }
function ReviewReply({ review, onSaved }: { review: Review; onSaved: (review: Review) => void }) {
  const toast = useToast(); const [reply, setReply] = React.useState(review.reply ?? ""); const [editing, setEditing] = React.useState(review.reply === null); const [saving, setSaving] = React.useState(false);
  const save = async () => { if (!reply.trim()) return; setSaving(true); try { const { data } = await clientApi.put<Review>(INTERNAL.REVIEW_REPLY(review.reviewId), { reply: reply.trim() }); onSaved(data); setEditing(false); toast({ tone: "success", title: "답글을 저장했습니다." }); } catch (error) { toast({ tone: "danger", title: error instanceof ApiError ? error.message : "답글을 저장하지 못했습니다." }); } finally { setSaving(false); } };
  return <article className="rounded-lg bg-bg p-4"><div className="flex justify-between gap-3"><p className="font-semibold text-fg">{review.clientName}</p><time className="text-xs text-fg-subtle">{review.createdAt.slice(0, 10)}</time></div>{review.content && <p className="mt-2 text-sm leading-6 text-fg-secondary">{review.content}</p>}{editing ? <div className="mt-3"><Textarea maxLength={1000} value={reply} onChange={(e) => setReply(e.target.value)} placeholder="답글을 입력해 주세요" /><div className="mt-2 flex justify-end gap-2">{review.reply && <Button size="sm" variant="ghost" onClick={() => { setReply(review.reply ?? ""); setEditing(false); }}>취소</Button>}<Button size="sm" loading={saving} onClick={() => void save()}>답글 저장</Button></div></div> : <div className="mt-3 rounded-md bg-surface px-3 py-3 text-sm text-fg-secondary"><p>{review.reply}</p><button type="button" className="mt-2 text-xs font-semibold text-primary" onClick={() => setEditing(true)}>답글 수정</button></div>}</article>;
}
