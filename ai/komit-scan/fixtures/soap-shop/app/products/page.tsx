import { supabase } from '@/lib/supabase'

export default async function Products() {
  const { data: products } = await supabase.from('products').select('*')

  return (
    <div style={{ width: 1200, display: 'flex' }}>
      {products?.map((p: any) => (
        <div key={p.id} style={{ width: 300 }}>
          <img src={p.imageUrl} alt={p.name} />
          <h3>{p.name}</h3>
          <p>{p.price}원</p>
        </div>
      ))}
    </div>
  )
}
