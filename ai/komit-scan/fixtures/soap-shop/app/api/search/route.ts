import { read } from '@/lib/db'

export async function GET(req: Request) {
  const q = new URL(req.url).searchParams.get('q') || ''

  const products = read('products')
  const matcher = new Function('p', `return /${q}/i.test(p.name) || /${q}/i.test(p.desc)`)

  return Response.json(products.filter((p: any) => matcher(p)))
}
