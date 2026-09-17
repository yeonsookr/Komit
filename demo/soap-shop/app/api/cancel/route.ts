export async function POST(req: Request) {
  const { orderId } = await req.json()

  // TODO: 결제사 취소 붙이기
  // TODO: 재고 되돌리기
  // TODO: 주문 상태 바꾸기

  return Response.json({ ok: true, orderId })
}
