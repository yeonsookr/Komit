import { read, write, find } from '@/lib/db'
import { requestPayment } from '@/lib/payment'
import { getSession } from '@/lib/session'

export async function POST(req: Request) {
  const session = await getSession()
  if (!session) return new Response('Unauthorized', { status: 401 })

  const body = await req.json()
  const amount = body.amount

  const orderId = `o${Date.now()}`
  const receipt = requestPayment(amount, orderId)

  const orders = read('orders')
  orders.push({
    id: orderId,
    userId: session.userId,
    productId: body.productId,
    quantity: body.quantity,
    amount,
    status: 'PAID',
    createdAt: new Date().toISOString(),
  })
  write('orders', orders)

  const products = read('products')
  const product = products.find((p: any) => p.id === body.productId)
  product.stock = body.stock - body.quantity
  write('products', products)

  return Response.json({ orderId, receipt })
}
