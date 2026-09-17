import { read, write } from '@/lib/db'
import { setSession } from '@/lib/session'

export async function POST(req: Request) {
  const { email, password, phone, name } = await req.json()

  const users = read('users')
  const id = `u${Date.now()}`

  users.push({ id, email, password, phone, name, role: 'user' })
  write('users', users)

  await setSession(id)
  return Response.json({ id, email, name })
}
