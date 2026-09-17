import { read, write } from '@/lib/db'
import { getSession } from '@/lib/session'

export async function POST(req: Request) {
  const session = await getSession()
  if (!session || session.role !== 'admin') {
    return new Response('Forbidden', { status: 403 })
  }

  const { name, price, stock, desc } = await req.json()
  const products = read('products')
  products.push({ id: `p${Date.now()}`, name, price, stock, desc, image: '/placeholder.jpg' })
  write('products', products)

  return Response.json({ ok: true })
}
