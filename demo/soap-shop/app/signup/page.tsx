'use client'
import { useState } from 'react'

export default function Signup() {
  const [form, setForm] = useState({ email: '', password: '', phone: '', name: '' })

  function set(key: string, value: string) {
    setForm({ ...form, [key]: value })
  }

  async function submit() {
    await fetch('/api/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form),
    })
    location.href = '/'
  }

  return (
    <main>
      <h1>회원가입</h1>
      <input placeholder="이름" value={form.name} onChange={(e) => set('name', e.target.value)} />
      <input placeholder="이메일" value={form.email} onChange={(e) => set('email', e.target.value)} />
      <input placeholder="비밀번호" type="password" value={form.password} onChange={(e) => set('password', e.target.value)} />
      <input placeholder="휴대폰 번호" value={form.phone} onChange={(e) => set('phone', e.target.value)} />
      <p style={{ marginTop: 16 }}><button onClick={submit}>가입하기</button></p>
    </main>
  )
}
