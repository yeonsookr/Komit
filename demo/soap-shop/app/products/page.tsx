import { read } from '@/lib/db'

export default function Products() {
  const products = read('products')

  return (
    <main>
      <h1>상품</h1>
      <div style={{ width: 1200, display: 'flex', gap: 16, marginTop: 20 }}>
        {products.map((p: any) => (
          <div className="card" key={p.id} style={{ width: 280 }}>
            <div className="ph" />
            <h3>{p.name}</h3>
            <p>{p.price.toLocaleString()}원</p>
            <p style={{ marginTop: 10 }}>
              <a href={`/products/${p.id}`}>자세히 보기</a>
            </p>
          </div>
        ))}
      </div>
    </main>
  )
}
