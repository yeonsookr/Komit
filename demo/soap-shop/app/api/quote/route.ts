import { find } from '@/lib/db'

export async function POST(req: Request) {
  const { productId, quantity } = await req.json()

  const product = find('products', productId)
  if (!product) return new Response('Not found', { status: 404 })

  const amount = product.price * quantity
  return Response.json({ productId, quantity, unitPrice: product.price, amount })
}
