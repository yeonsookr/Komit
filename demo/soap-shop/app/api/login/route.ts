import { read } from '@/lib/db'
import { verify } from '@/lib/hash'
import { setSession } from '@/lib/session'

export async function POST(req: Request) {
  const { email, password } = await req.json()

  const user = read('users').find((u: any) => u.email === email)
  if (!user || !verify(password, user.password)) {
    return new Response('이메일 또는 비밀번호가 맞지 않습니다', { status: 401 })
  }

  await setSession(user.id)
  return Response.json({ id: user.id, name: user.name })
}
