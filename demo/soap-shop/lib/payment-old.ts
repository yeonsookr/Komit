export async function pay(amount: number) {
  await new Promise((r) => setTimeout(r, 120))
  return { ok: true, amount }
}
