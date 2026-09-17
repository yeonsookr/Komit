export async function pay(amount: number) {
  const res = await fetch('https://pg.example.com/v1/pay', {
    method: 'POST',
    body: JSON.stringify({ amount, currency: 'KRW' }),
  })
  return res.json()
}
