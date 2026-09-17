export const metadata = { title: 'soap-shop' }

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <header>
          <a href="/">수제 비누 가게</a>
          <a href="/products">상품</a>
          <a href="/signup">회원가입</a>
        </header>
        {children}
        <footer>
          <p>수제 비누 가게</p>
        </footer>
      </body>
    </html>
  )
}
