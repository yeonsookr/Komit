export async function requestPayment(amount: number, orderId: string) {
  await new Promise((r) => setTimeout(r, 120))
  return { approved: true, amount, orderId, receiptId: `rc_${orderId}` }
}
