---
name: targeted-qa
description: Writes focused business test scenarios or runs selected existing tests for a completed feature when the user explicitly asks.
disable-model-invocation: true
---

# Targeted QA

Arguments: `$ARGUMENTS`

사용자가 직접 호출했을 때만 수행한다. 화면의 시각 리뷰와 브라우저 확인은 개발자가 하므로 하지 않는다.

## 모드
- `시나리오만` 이 있으면 시나리오만 쓰고 어떤 명령도 실행하지 않는다.
- `QA 실행` 또는 `테스트 실행` 이 있으면 시나리오를 쓰고 관련 테스트를 선별 실행한다.
- 모드가 불명확하면 실행하지 않고 시나리오와 권장 테스트 범위만 제시한다.

## 순서
1. 변경 diff, 해당 기능의 계약(`docs/features/<feature>-contract.md`)과 관련 `docs/domain/` 문서, 그 기능의 테스트 파일만 확인한다.
2. 기능 규모에 맞게 정상·예외·경계 시나리오를 쓴다. 단순 기능은 항목을 늘리지 않는다.
3. 실행 요청이 있으면 저장소에서 실제 테스트 명령과 관련 테스트 클래스를 확인한 뒤 그것만 실행한다. 전체 테스트·전체 빌드·E2E 로 대체하지 않는다.
4. 관련 테스트가 없으면 전체 테스트를 대신 돌리지 않고 자동화 테스트가 없다는 사실과 미검증 위험을 보고한다. 테스트 코드 작성은 별도 요청이 있을 때만 한다.
5. 실패하면 원인을 요약한다. 요청 없이 큰 범위를 수정하거나 반복 테스트 루프를 돌리지 않는다.

## 보고 형식
- 시나리오: 조건 / 수행 / 기대 결과
- 실행한 명령과 결과(실행 모드)
- 실패 항목과 영향
- 미검증 항목과 이유, 필요한 후속 조치
