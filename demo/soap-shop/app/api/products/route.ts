import { read } from '@/lib/db'

export async function GET() {
  return Response.json(read('products'))
}
