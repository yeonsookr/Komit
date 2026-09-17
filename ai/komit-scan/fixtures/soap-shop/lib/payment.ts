export async function pay(amount: number, orderId: string) {
  const res = await fetch('https://pg.example.com/v1/payments', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount, orderId }),
  })
  return res.json()
}
