import './globals.css'

export const metadata = {
  title: '수제 비누 가게',
  description: '손으로 만든 비누를 팝니다',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <header>
          <a className="brand" href="/">수제 비누 가게</a>
          <a href="/products">상품</a>
          <a href="/cart">장바구니</a>
          <a href="/mypage">마이페이지</a>
          <a href="/login">로그인</a>
        </header>
        {children}
        <footer>
          <p>수제 비누 가게</p>
        </footer>
      </body>
    </html>
  )
}
