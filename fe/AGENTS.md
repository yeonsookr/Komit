<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# Komit FE 작업 규칙

## 코드 품질 검사: ESLint가 아니라 SonarQube MCP

이 프로젝트는 ESLint를 사용하지 않습니다. `eslint`, `eslint-config-next` 등을 설치하지 말고 `next lint`를 실행하지 마세요. 코드 품질 검사는 **SonarQube MCP**로 합니다.

사용하는 도구는 `mcp__sonarqube__*` 입니다.

| 목적 | 도구 |
| --- | --- |
| 작성·수정한 코드 조각 검사 | `analyze_code_snippet` |
| 프로젝트에 남아 있는 이슈 조회 | `search_sonar_issues_in_projects` |
| Quality Gate 통과 여부 확인 | `get_project_quality_gate_status` |
| 규칙 내용과 수정 방법 확인 | `show_rule` |
| 보안 핫스팟 확인 | `search_security_hotspots` |

검사 시점은 다음과 같습니다.

- 컴포넌트나 모듈을 새로 작성하거나 크게 고친 직후
- 작업을 완료했다고 보고하기 전
- PR을 올리기 전

지킬 것:

- 이슈를 발견하면 규칙 ID와 위치를 함께 보고하고, 고칠지 남길지 사용자에게 확인합니다.
- 이슈 상태 변경(`change_sonar_issue_status`, `change_security_hotspot_status`)은 사용자가 요청했을 때만 합니다. 지적을 없애려고 임의로 처리하지 않습니다.
- 브랜치를 조회할 때는 `branch`(git 브랜치 이름)와 `pullRequest`(SonarQube PR 키)를 섞어 쓰지 않습니다.
- MCP 서버가 연결되지 않으면 ESLint로 대체하지 말고 연결이 안 된다는 사실을 알립니다. 설정 방법은 저장소 루트의 `SONAR_MCP.md`에 있습니다.
- 타입 검사는 별개입니다. `npx tsc --noEmit`과 `npm run build`는 그대로 사용합니다.

## SonarQube for IDE 정보 요청

MCP는 SonarQube 서버에 올라간 분석 결과만 봅니다. 저장만 하고 아직 분석에 올리지 않은 코드의 실시간 지적은 보이지 않습니다. 그 정보는 사용자의 VS Code에 설치된 **SonarQube for IDE** 확장에 있습니다.

MCP만으로 판단이 서지 않으면 사용자에게 해당 정보를 요청해도 됩니다. 요청할 수 있는 예시:

- 특정 파일에 대한 SonarQube for IDE의 Problems 패널 내용
- 실시간으로 뜬 경고의 규칙 ID와 메시지
- Connected Mode 연결 상태와 바인딩된 프로젝트 키

요청할 때는 어떤 파일의 무엇이 필요한지 구체적으로 적습니다. 사용자가 주지 않으면 추측으로 단정하지 말고 확인되지 않았다고 밝힙니다.

## 컨벤션

화면 구조, API 호출, 테마, 반응형, 애니메이션 규칙은 저장소의 `docs/FRONTEND_CONVENTIONS.md`를 따릅니다.

## 상태 관리: Zustand

화면 여러 곳이 함께 보는 클라이언트 상태가 필요하면 **Zustand**를 씁니다. 다른 상태 관리 라이브러리(Redux, Recoil, Jotai 등)를 추가로 들이지 않습니다. 한 컴포넌트 안에서 끝나는 상태는 `useState`로 두고 스토어로 올리지 않습니다.

규칙은 `docs/FRONTEND_CONVENTIONS.md` 9.1절에 있습니다. 요지만 적습니다.

- 스토어는 `src/stores/<domain>/`에 두고, `create()` 전역 훅이 아니라 `createStore()` 팩토리 + Provider + `useXxxStore(selector)` 훅으로 만듭니다. Next 서버는 요청을 동시에 처리하므로 전역 스토어는 요청 사이에 상태가 섞입니다.
- 서버 컴포넌트는 스토어를 읽지도 쓰지도 않습니다. 서버에서 확정된 데이터는 props로 내립니다.
- 서버 렌더와 첫 클라이언트 렌더의 초기 상태를 같게 둡니다. 브라우저에서만 아는 값은 "아직 모름"으로 시작해 마운트 뒤에 채웁니다.
- 토큰·비밀값을 스토어에 넣지 않습니다. 인증 토큰은 httpOnly 쿠키에만 있습니다.

기존 예시는 `src/stores/auth/`(로그인 상태)입니다. 새 스토어를 만들 때 같은 구조를 따릅니다.
