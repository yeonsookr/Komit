'use client'
import { supabase } from '@/lib/supabase'
import { useState } from 'react'

export default function Signup() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phone, setPhone] = useState('')

  async function submit() {
    await supabase.from('users').insert({ email, password, phone })
    location.href = '/'
  }

  return (
    <main>
      <h1>회원가입</h1>
      <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="이메일" />
      <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" placeholder="비밀번호" />
      <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="휴대폰 번호" />
      <button onClick={submit}>가입하기</button>
    </main>
  )
}
