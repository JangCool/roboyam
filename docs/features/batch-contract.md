# 배치 계약 (서버 구현됨 2026-10-21 — 화면 연결은 후속)

**의도**: 관리자가 정해진 시각에 도는 일(배치)을 보고, 켜고 끄고, 주기(cron)를 바꾸고, 지금 실행하고, 실행 결과 로그를 본다. 배치 정의는 코드(카탈로그)가 원천이고 DB 에는 운영자가 바꾸는 값과 로그만 둔다. 구조·테이블·락·가정은 `roles-contract.md` 「배치 인프라 결과」.

## 화면 (admin-web — 아직 목업 `// MOCK:`)
| 경로 | 내용 |
|---|---|
| `/settings/batches` | 배치 목록: 이름·설명·주기(cron)·켜기/끄기·마지막 실행·지금 실행 |
| `/settings/batch-logs` | 실행 로그: 배치·결과 필터, 시작/끝·상태·처리 건수·메시지 |
- 타입의 기준: `app/composables/batch/api/useBatchApi.types.ts`. 서버 응답은 그 타입에 아래 칸을 **더한** 모양이다(어긋남은 아래 「프론트 타입과의 차이」).

## API (`/api/admin`, 응답은 `ApiResponse`)
| | 권한 | 내용 |
|---|---|---|
| `GET /batch-jobs` | `batch.read` | `BatchJobView[]` (카탈로그 순서) |
| `PUT /batch-jobs/{jobName}` `{enabled}` | `batch.manage` | 켜기/끄기 → 바뀐 목록 전체 |
| `PUT /batch-jobs/{jobName}/schedule` `{schedule}` | `batch.manage` | cron 수정(재기동 없이 반영) → 바뀐 목록 전체. 화면에 아직 입력칸이 없다 |
| `POST /batch-jobs/{jobName}/run` | `batch.run`(민감) | 지금 실행 — 끝날 때까지 기다린다 → 바뀐 목록 전체. 꺼져 있어도 돈다. 일이 실패해도 200(마지막 실행 상태 `FAIL`) |
| `GET /batch-logs?jobName&status&trigger&from&to&page&size` | `batch.read` | `{items, total, page(0부터), size}`, 시작 시각 최근순 |
| `GET /batch-logs/{logId}` | `batch.read` | 상세 |
- `jobName` = 배치 키(`grade-promotion`, `batch-log-cleanup`). 오류: 404 `BATCH_NOT_FOUND`·`BATCH_LOG_NOT_FOUND`, **409 `BATCH_RUNNING`**(이미 실행 중), 400 `VALIDATION`(`fields.schedule` — 형식 오류·실행 시각 없음·간격 60초 미만, 로그 조건 칸).
- `BatchJobView`: 목업 칸(`jobName displayName description i18n descI18n source schedule enabled lastRunAt lastStatus lastMessage runnable`) + `category`(`MEMBER`/`SYSTEM`) · `defaultSchedule` · `nextRunAt` · `lastAffectedCount` · `running`. `displayName`·`description`·`lastMessage` 는 **서버가 요청 언어로 푼 글**이고 `i18n`·`descI18n` 은 항상 null(화면은 `dbText` 규칙대로 서버 글을 쓴다). `source` 는 항상 `JAVA`, `runnable` 은 항상 true.
- `BatchLogView`: 목업 칸 + `logId`(문자열) · `trigger`(`SCHEDULED`/`MANUAL`) · `operatorName`(수동 실행한 운영자). `jobName` 은 키, `displayName` 은 배치 이름(카탈로그에서 사라진 배치는 키 그대로). `message` 는 성공/건너뜀이면 결과 요약, 실패면 사유. 상세는 거기에 `scheduledAt` · `summary` · `errorMessage` · `nodeId`.
- 상태 값: `RUNNING`(실행 중) · `OK` · `FAIL` · `SKIP`(처리 건수 0).

## 프론트 타입과의 차이 (`// MOCK:` → `// REAL:` 때)
1. `BatchStatus` 에 **`RUNNING`** 이 없다 — 서버는 실행 중인 줄과 마지막 실행 상태에 `RUNNING` 을 준다(`util-batch.ts`·`util-batch-messages.ts` 의 딱지와 필터에 추가).
2. 목업의 `jobName`(예: `audit-retention`)과 `pg_cron`·`POSTGRESQL` 배치는 서버에 없다 — 이 제품은 DB 가 도는 배치가 없다(`source` 는 항상 `JAVA`). 화면의 「DB 가 돌립니다」 분기는 쓰이지 않는다.
3. 서버는 `i18n`/`descI18n` 을 null 로 준다(이름·설명이 이미 번역돼 있다). 목록 호출은 `Accept-Language` 를 따른다.
4. 새 칸(`nextRunAt`·`defaultSchedule`·`category`·`running`·`logId`·`trigger`·`operatorName`)과 cron 수정·로그 상세·기간/실행 방식 필터는 화면에 아직 없다.
5. 지금 실행은 **`batch.run`** 권한이고 켜기/끄기는 `batch.manage` 다(목업 주석의 「권한 구분 미정」이 정해졌다).

## 가정 / 미확정
- 등급 승급 시각(04:30 KST 하루 한 번)·로그 보관 90일·cron 간격 하한 60초·`batch.run` 을 지금 실행에만 쓰는 것은 **가정**.
- 지금 실행은 요청 스레드에서 끝날 때까지 돈다 — 오래 걸리는 배치가 생기면 비동기(202 + 상태 조회)로 바꿔야 한다.
- 알림(배치 실패 알림)·실행 이력의 상세 처리 내역(건별)·감사 로그 조회 화면은 후속.
- 서버가 내려가 있던 동안 놓친 예약은 따라잡지 않는다(다음 시각부터).
