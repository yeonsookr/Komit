import type { Metadata } from "next";
import { JetBrains_Mono, Noto_Sans_KR } from "next/font/google";
import "./globals.css";
import { ToastProvider } from "@/components/feedback";
import { AuthStoreProvider } from "@/stores/auth";

// 본문 폰트. 디자인 기준은 Pretendard 이며, next/font 로 자체 호스팅할 수 있는
// 한글 폰트로 대체해 두었습니다. Pretendard 파일을 확보하면 next/font/local 로 교체합니다.
const sans = Noto_Sans_KR({
  variable: "--font-sans",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  display: "swap",
});

// 금액·시각·날짜·카운터는 전부 monospace + tabular-nums 규칙을 따릅니다.
const mono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "Komit",
  description: "믿고 맡기는 소프트웨어 외주 거래",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="ko"
      className={`${sans.variable} ${mono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <AuthStoreProvider>
          <ToastProvider>{children}</ToastProvider>
        </AuthStoreProvider>
      </body>
    </html>
  );
}
