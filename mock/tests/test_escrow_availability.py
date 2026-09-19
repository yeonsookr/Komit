import unittest
import json
from typing import get_type_hints

from fastapi import FastAPI

from app.routers import escrow


class AvailabilityContractTest(unittest.IsolatedAsyncioTestCase):
    async def test_every_documented_request_example_can_be_called(self):
        app = FastAPI()
        app.include_router(escrow.router)
        specification = app.openapi()
        for route in escrow.router.routes:
            if not getattr(route, 'body_field', None):
                continue
            examples = specification['paths'][route.path]['post']['requestBody']['content']['application/json']['schema']['examples']
            for example in examples:
                with self.subTest(path=route.path, example=example):
                    payload = get_type_hints(route.endpoint)['payload'].model_validate(example)
                    result = await route.endpoint(payload)
                    self.assertIsInstance(result, route.response_model)
                    self.assertEqual(result.Header.responseCode, 'H0000')

    async def test_every_documented_post_returns_503_without_rec(self):
        app = FastAPI()
        app.include_router(escrow.router)
        specification = app.openapi()
        count = 0
        for path, methods in specification['paths'].items():
            operation = methods.get('post')
            if operation is None:
                continue
            with self.subTest(path=path):
                self.assertIn('503', operation['responses'])
                body_schema = operation['requestBody']['content']['application/json']['schema']
                body = body_schema['examples'][0]
                body = {**body, 'Header': {**body['Header']}}
                if path.endswith('/default'):
                    body['Header']['userKey'] = 'unavailable-user-key'
                else:
                    body['Header']['clientApiKey'] = escrow.UNAVAILABLE_CLIENT_API_KEY
                messages = []
                async def receive():
                    return {'type': 'http.request', 'body': json.dumps(body).encode(), 'more_body': False}
                async def send(message):
                    messages.append(message)
                await app({'type': 'http', 'asgi': {'version': '3.0'}, 'http_version': '1.1',
                           'method': 'POST', 'path': path, 'raw_path': path.encode(),
                           'root_path': '', 'query_string': b'', 'scheme': 'http',
                           'server': ('test', 80), 'client': ('test', 1),
                           'headers': [(b'content-type', b'application/json')]}, receive, send)
                status = next(m['status'] for m in messages if m['type'] == 'http.response.start')
                data = json.loads(b''.join(m.get('body', b'') for m in messages))
                self.assertEqual(status, 503, data)
                self.assertEqual(set(data), {'Header'})
                self.assertEqual(data['Header']['responseCode'], 'Q1000')
                count += 1
        self.assertEqual(count, 14)


if __name__ == '__main__':
    unittest.main()
