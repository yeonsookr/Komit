import { supabase } from '@/lib/supabase'
import { pay } from '@/lib/payment'

export async function POST(req: Request) {
  const body = await req.json()
  const amount = body.amount

  const res = pay(amount, body.orderId)

  await supabase.from('orders').insert({
    id: body.orderId,
    amount,
    status: 'PAID',
  })

  await supabase
    .from('products')
    .update({ stock: body.stock - body.quantity })
    .eq('id', body.productId)

  return Response.json(res)
}
