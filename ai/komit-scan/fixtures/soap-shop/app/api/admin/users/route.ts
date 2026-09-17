import { supabase } from '@/lib/supabase'

export async function GET() {
  const { data } = await supabase.from('users').select('*')
  return Response.json(data)
}

export async function DELETE(req: Request) {
  const { userId } = await req.json()
  await supabase.from('users').delete().eq('id', userId)
  return Response.json({ ok: true })
}
