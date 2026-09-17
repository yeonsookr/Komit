import { find } from '@/lib/db'
import { getSession } from '@/lib/session'

export async function GET(req: Request, { params }: { params: Promise<{ id: string }> }) {
  const session = await getSession()
  if (!session) return new Response('Unauthorized', { status: 401 })

  const { id } = await params
  const order = find('orders', id)
  if (!order) return new Response('Not found', { status: 404 })

  return Response.json(order)
}
