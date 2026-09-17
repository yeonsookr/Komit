export async function POST(req: Request) {
  const { orderId } = await req.json()

  // TODO: 결제사 취소 API 붙이기
  // TODO: 재고 되돌리기

  return Response.json({ ok: true, orderId })
}
