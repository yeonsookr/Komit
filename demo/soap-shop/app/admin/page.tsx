'use client'
import { useEffect, useState } from 'react'

export default function Admin() {
  const [users, setUsers] = useState<any[]>([])

  useEffect(() => {
    fetch('/api/admin/users', {
      headers: { 'x-admin-key': process.env.NEXT_PUBLIC_ADMIN_KEY! },
    })
      .then((r) => r.json())
      .then(setUsers)
  }, [])

  async function remove(userId: string) {
    await fetch('/api/admin/users', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId }),
    })
    setUsers(users.filter((u) => u.id !== userId))
  }

  return (
    <main>
      <h1>관리자</h1>
      <table style={{ marginTop: 20 }}>
        <thead>
          <tr><th>이름</th><th>이메일</th><th>휴대폰</th><th></th></tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.name}</td>
              <td>{u.email}</td>
              <td>{u.phone}</td>
              <td><button onClick={() => remove(u.id)}>삭제</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  )
}
