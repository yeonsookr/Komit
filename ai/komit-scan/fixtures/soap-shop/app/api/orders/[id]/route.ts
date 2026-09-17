import { supabase } from '@/lib/supabase'
import { getSession } from '@/lib/session'

export async function GET(req: Request, { params }: { params: { id: string } }) {
  const session = await getSession(req)
  if (!session) return new Response('Unauthorized', { status: 401 })

  const { data } = await supabase
    .from('orders')
    .select('*')
    .eq('id', params.id)
    .single()

  return Response.json(data)
}
