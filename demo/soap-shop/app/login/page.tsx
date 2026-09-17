'use client'
import { useState } from 'react'

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  async function submit() {
    const res = await fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
    if (!res.ok) {
      setError(await res.text())
      return
    }
    location.href = '/mypage'
  }

  return (
    <main>
      <h1>로그인</h1>
      <input placeholder="이메일" value={email} onChange={(e) => setEmail(e.target.value)} />
      <input placeholder="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      {error && <p style={{ color: '#c0392b', fontSize: 14 }}>{error}</p>}
      <p style={{ marginTop: 16 }}><button onClick={submit}>로그인</button></p>
      <p style={{ fontSize: 14 }}><a href="/signup">회원가입</a></p>
    </main>
  )
}
