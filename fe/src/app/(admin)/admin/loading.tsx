/**
 * 운영 콘솔의 뼈대. 상단바와 본문 폭을 그대로 두고 안쪽만 비워, 데이터가 도착해도
 * 화면이 밀리지 않게 합니다. 폭이 바뀌면 운영자가 읽던 자리를 놓칩니다.
 */
export default function AdminLoading() {
  return (
    <div className="min-h-dvh bg-bg">
      <div className="h-gnb border-b border-line" />
      <div className="mx-auto w-full max-w-content-wide animate-pulse px-4 py-8 sm:px-6 sm:py-10">
        <div className="h-8 w-48 rounded bg-surface" />
        <div className="mt-3 h-4 w-64 rounded bg-surface" />
        <div className="mt-10 space-y-3">
          <div className="h-20 rounded-xl bg-surface" />
          <div className="h-20 rounded-xl bg-surface" />
          <div className="h-20 rounded-xl bg-surface" />
        </div>
      </div>
    </div>
  );
}
