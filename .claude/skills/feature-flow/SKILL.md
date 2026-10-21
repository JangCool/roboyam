---
name: feature-flow
description: Runs the project's feature-first workflow: discuss scope briefly, implement UI with mock data, pause for human confirmation, then implement backend and connect the API.
disable-model-invocation: true
---

# Feature Flow

Arguments: `$ARGUMENTS`

사용자가 명시적으로 호출했을 때만 실행한다. 현재 대화와 저장소 상태를 이어서 쓰고 저장소 전체를 다시 읽지 않는다.

## 1. 단계 판단
`$ARGUMENTS`와 대화 맥락으로 새 기능 / 화면 수정 / 화면 확정 후 백엔드 / API 연동 중 어느 단계인지 정한다. 새 세션에서 이어받으면 `docs/features/<feature>-contract.md`와 관련 `docs/domain/` 문서만 먼저 읽고, 나머지는 필요한 코드만 탐색한다. 스택·명령어·라우트가 있다고 추정하지 않는다.

## 2. 새 기능 시작
1. 요구사항을 대화로 간단히 정리한다. 장문의 기획서·스토리보드를 만들지 않는다.
2. 구현에 중대한 영향을 주는 미확정 정책만 질문하고, 나머지는 가정을 명시한다.
3. 화면 중심 기능이면 `frontend-developer` 에게 목업(`// MOCK:`) 기반 화면 구현을 맡긴다.
4. 결제·환불·정산·권한·보안·배치·데이터 변경처럼 서버 정책이 우선인 기능은 정책과 데이터 규칙을 먼저 확인하고 `backend-developer` 부터 쓴다. UI 프로토타입은 별도 단계다.
5. 한 기능 단위로 제한한다.

## 3. 화면 구현 후 멈춤
`frontend-developer` 완료 후 다음만 요약하고 멈춘다: 수정 핵심 파일·화면 경로 / 구현된 동작 / 목업 가정 / 개발자가 확인할 항목 / 백엔드가 줄 데이터·동작 후보. 시각 리뷰는 개발자가 하며, 백엔드로 넘어가지 않는다. 화면 수정 요청은 그 범위만 `frontend-developer` 에게 맡기고 다시 확인을 기다린다.

## 4. 화면 확정 후 백엔드
사용자가 `화면 확정`, `백엔드 진행` 등을 명시하면:
1. 확정된 화면과 코드를 기준으로 계약을 정리한다: 사용자 동작, 필드·검증, 권한·정책, API 후보, 오류 상황, 미확정 사항.
2. 같은 세션에서 바로 이어가고 계약이 단순하면 요약을 `backend-developer` 에게 직접 넘긴다.
3. 세션을 나누거나 여러 세션이 공유하거나 복잡하면 `docs/features/<feature>-contract.md` 를 만든다(맨 위 의도 2~3줄 + 위 항목만). 만든 뒤 새 세션이나 `/clear` 후 이어가도록 안내한다.
4. `backend-developer` 에게 API·업무 로직 구현을 맡기고, 완료 후 실제 API 계약과 변경 파일을 요약한다.

정책상 중요한 충돌·미결정은 한 번에 묶어 확인한다. 사소한 세부마다 승인을 요구하지 않는다.

## 5. API 연동
백엔드 완료 후 목업(`// MOCK:`)을 실제 호출(`// REAL:`)로 교체한다. 작으면 메인 세션에서, 여러 파일·복잡한 상태면 `frontend-developer` 에게 맡긴다. 검증은 최소(`pnpm --filter <앱> typecheck`, `./gradlew :server:api:compileJava :server:admin:compileJava`)와 dev 서버에서 해당 화면·API 를 한 번 실제로 호출하는 것까지다. 전체 테스트·E2E 는 실행하지 않는다.

## 6. 완료 보고
변경 내용, 핵심 파일, 실행한 검증, 실행하지 않은 테스트, 미확정 사항을 나눠 보고한다. 실행하지 않은 테스트를 통과했다고 쓰지 않는다. QA 는 자동으로 시작하지 않고 사용자의 다음 지시(`/targeted-qa`)를 기다린다.
