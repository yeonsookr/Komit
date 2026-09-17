import { find } from '@/lib/db'

export default async function ProductDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const product = find('products', id)

  return (
    <main>
      <h1>{product.name}</h1>
      <div className="ph" style={{ width: 360, height: 220, marginTop: 16 }} />
      <p style={{ marginTop: 16 }}>{product.desc}</p>
      <p style={{ fontSize: 20, fontWeight: 700, marginTop: 8 }}>{product.price.toLocaleString()}원</p>
      <p style={{ color: '#888', fontSize: 14 }}>남은 수량 {product.stock}개</p>
      <p style={{ marginTop: 20 }}>
        <a href={`/cart?product=${product.id}`}><button>장바구니에 담기</button></a>
      </p>
    </main>
  )
}
