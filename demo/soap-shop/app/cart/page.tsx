'use client'
import { useEffect, useState } from 'react'

export default function Cart() {
  const [products, setProducts] = useState<any[]>([])
  const [picked, setPicked] = useState<any>(null)
  const [quantity, setQuantity] = useState(1)

  useEffect(() => {
    fetch('/api/products').then((r) => r.json()).then((rows) => {
      setProducts(rows)
      const id = new URLSearchParams(location.search).get('product')
      setPicked(rows.find((p: any) => p.id === id) || rows[0])
    })
  }, [])

  if (!picked) return <main><h1>장바구니</h1><p>불러오는 중입니다.</p></main>

  const amount = picked.price * quantity

  async function order() {
    const res = await fetch('/api/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        productId: picked.id,
        quantity,
        amount,
        stock: picked.stock,
      }),
    })
    const data = await res.json()
    location.href = `/mypage?ordered=${data.orderId}`
  }

  return (
    <main>
      <h1>장바구니</h1>
      <table style={{ marginTop: 20 }}>
        <tbody>
          <tr><th>상품</th><td>{picked.name}</td></tr>
          <tr><th>가격</th><td>{picked.price.toLocaleString()}원</td></tr>
          <tr>
            <th>수량</th>
            <td>
              <input
                type="number"
                min={1}
                value={quantity}
                onChange={(e) => setQuantity(Number(e.target.value))}
                style={{ width: 90 }}
              />
            </td>
          </tr>
          <tr><th>합계</th><td><b>{amount.toLocaleString()}원</b></td></tr>
        </tbody>
      </table>
      <p style={{ marginTop: 20 }}><button onClick={order}>주문하기</button></p>
    </main>
  )
}
