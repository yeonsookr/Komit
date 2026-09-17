import { read } from '@/lib/db'
import { getSession } from '@/lib/session'

export default async function MyPage() {
  const session = await getSession()

  if (!session) {
    return (
      <main>
        <h1>마이페이지</h1>
        <p>로그인이 필요합니다. <a href="/login">로그인하기</a></p>
      </main>
    )
  }

  const orders = read('orders').filter((o: any) => o.userId === session.userId)
  const products = read('products')

  return (
    <main>
      <h1>마이페이지</h1>
      <p>{session.name}님</p>
      <h2 style={{ fontSize: 18, marginTop: 28 }}>주문 내역</h2>
      <table style={{ marginTop: 10 }}>
        <thead>
          <tr><th>주문번호</th><th>상품</th><th>수량</th><th>금액</th><th>상태</th></tr>
        </thead>
        <tbody>
          {orders.map((o: any) => (
            <tr key={o.id}>
              <td><a href={`/api/orders/${o.id}`}>{o.id}</a></td>
              <td>{products.find((p: any) => p.id === o.productId)?.name}</td>
              <td>{o.quantity}</td>
              <td>{o.amount.toLocaleString()}원</td>
              <td>{o.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  )
}
