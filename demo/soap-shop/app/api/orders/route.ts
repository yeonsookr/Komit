import { read } from '@/lib/db'
import { getSession } from '@/lib/session'

export async function GET() {
  const session = await getSession()
  if (!session) return new Response('Unauthorized', { status: 401 })

  const orders = read('orders').filter((o: any) => o.userId === session.userId)
  return Response.json(orders)
}
