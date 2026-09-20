import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { POLICY_CONTENT } from "@/lib/policyContent";

export function generateStaticParams() {
  return Object.keys(POLICY_CONTENT).map((slug) => ({ slug }));
}

export async function generateMetadata({
  params,
}: Readonly<{ params: Promise<{ slug: string }> }>): Promise<Metadata> {
  const { slug } = await params;
  const document = POLICY_CONTENT[slug];
  return {
    title: document ? `${document.title} | Komit` : "문서 없음 | Komit",
    robots: { index: false },
  };
}

export default async function PolicyPage({
  params,
}: Readonly<{ params: Promise<{ slug: string }> }>) {
  const { slug } = await params;
  const document = POLICY_CONTENT[slug];
  if (!document) notFound();

  return (
    <Container width="content" className="py-10 sm:py-16">
      <div className="mx-auto max-w-[800px]">
        <p className="text-sm font-bold text-primary">Komit 정책</p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-fg sm:text-4xl">
          {document.title}
        </h1>
        <p className="mt-4 text-sm leading-6 text-fg-muted">
          2026년 9월 20일 작성 · 검토용 초안
        </p>
        <div className="mt-8 rounded-xl border border-line bg-surface px-5 py-4 text-sm leading-6 text-fg-secondary">
          이 문서는 서비스 기능과 현재 운영 계획을 바탕으로 작성한 초안입니다.
          정식 운영 전 사업자 정보, 금융 제공자, 보유 기간 및 법률 검토 결과를 반영해야 합니다.
        </div>
        <div className="mt-9 space-y-9 rounded-2xl border border-line bg-bg p-6 sm:p-9">
          {document.sections.map((section) => (
            <section key={section.title}>
              <h2 className="text-lg font-bold text-fg">{section.title}</h2>
              {section.paragraphs?.map((paragraph) => (
                <p key={paragraph} className="mt-3 text-sm leading-7 text-fg-secondary">
                  {paragraph}
                </p>
              ))}
              {section.items && (
                <ul className="mt-3 list-disc space-y-2 pl-5 text-sm leading-7 text-fg-secondary">
                  {section.items.map((item) => <li key={item}>{item}</li>)}
                </ul>
              )}
            </section>
          ))}
        </div>
      </div>
    </Container>
  );
}
