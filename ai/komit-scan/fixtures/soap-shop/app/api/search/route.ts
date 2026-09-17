import { supabase } from '@/lib/supabase'

export async function GET(req: Request) {
  const q = new URL(req.url).searchParams.get('q')

  const { data } = await supabase.rpc('run_sql', {
    query: `SELECT * FROM products WHERE name LIKE '%${q}%'`,
  })

  return Response.json(data)
}
