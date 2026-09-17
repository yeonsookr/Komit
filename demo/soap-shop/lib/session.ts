import { cookies } from 'next/headers'
import { read } from './db'

export async function getSession() {
  const jar = await cookies()
  const userId = jar.get('uid')?.value
  if (!userId) return null
  const user = read('users').find((u: any) => u.id === userId)
  if (!user) return null
  return { userId: user.id, role: user.role, name: user.name }
}

export async function setSession(userId: string) {
  const jar = await cookies()
  jar.set('uid', userId, { httpOnly: true, sameSite: 'lax', path: '/' })
}

export async function clearSession() {
  const jar = await cookies()
  jar.delete('uid')
}
