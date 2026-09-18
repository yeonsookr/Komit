import { read, write } from '@/lib/db'

export async function GET() {
  return Response.json(read('users'))
}

export async function DELETE(req: Request) {
  const { userId } = await req.json()
  const users = read('users').filter((u: any) => u.id !== userId)
  write('users', users)
  return Response.json({ ok: true })
}
