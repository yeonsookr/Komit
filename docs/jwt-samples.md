# JWT 검증용 샘플 토큰

알고리즘 HS256. 사용자와 운영자는 **서명 키를 분리**한다. 클레임은 `sub` + 표준(`iat`, `exp`)뿐이다.

```
KOMIT_USER_JWT_SECRET  = komit-user-jwt-secret-dev-only-change-me
KOMIT_ADMIN_JWT_SECRET = komit-admin-jwt-secret-dev-only-change-me
```

> 개발용 값이다. 운영 시크릿은 커밋하지 않는다 (BACKEND_CONVENTIONS 610).

## 1. 사용자 · 유효 (userId 12, 만료 2027-09-14)

| 클레임 | 값 | 뜻 |
|---|---|---|
| `sub` | `12` | 주체 ID |
| `iat` | `1789344000` | 발급 2026-09-14 09:00 KST |
| `exp` | `1820880000` | 만료 2027-09-14 09:00 KST |

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMiIsImlhdCI6MTc4OTM0NDAwMCwiZXhwIjoxODIwODgwMDAwfQ.RRh0GbxV2FKuHiJUmTjXSk478NE8L4jhYalwJlk4o2E
```

## 2. 사용자 · 만료 (401 UNAUTHORIZED 재현용)

| 클레임 | 값 | 뜻 |
|---|---|---|
| `sub` | `12` | 주체 ID |
| `iat` | `1789257600` | 발급 2026-09-13 09:00 KST |
| `exp` | `1789261200` | 만료 2026-09-13 10:00 KST |

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMiIsImlhdCI6MTc4OTI1NzYwMCwiZXhwIjoxNzg5MjYxMjAwfQ.xmbqD0EZqIbLVT8EtTBKahS3a960Zhct7qj7VhSyxXE
```

## 3. 운영자 · 유효 (adminId 1, 만료 2027-09-14)

| 클레임 | 값 | 뜻 |
|---|---|---|
| `sub` | `1` | 주체 ID |
| `iat` | `1789344000` | 발급 2026-09-14 09:00 KST |
| `exp` | `1820880000` | 만료 2027-09-14 09:00 KST |

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzg5MzQ0MDAwLCJleHAiOjE4MjA4ODAwMDB9.PHvB4fHadcLDiyvn-Eldn-9k9OOvfh6vXoviY7CGZ4A
```

## 검증 케이스

| # | 토큰 | 호출 | 기대 |
|---|---|---|---|
| 1 | 1 | `GET /api/users/me` | 200 |
| 2 | 2 | `GET /api/users/me` | 401 UNAUTHORIZED |
| 3 | 없음 | `GET /api/users/me` | 401 UNAUTHORIZED |
| 4 | 3 | `GET /api/admin/reports` | 200 |
| 5 | **1** | `GET /api/admin/reports` | **401** — 사용자 토큰으로 운영 영역 호출 |
| 6 | **3** | `GET /api/users/me` | **401** — 운영자 토큰으로 사용자 영역 호출 |

5·6번이 핵심이다. 키를 분리했으므로 서명 검증 단계에서 걸러진다.

```bash
curl -H "Authorization: Bearer <토큰>" http://localhost:8000/mock/service/api/users/me
```

## 재생성

```bash
python scripts/make_jwt.py
```
