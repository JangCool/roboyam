# 🗓️ DB Batch Guide (`db-batch.md`)

## 1. 역할 및 기본 원칙
* **DB 배치** = PostgreSQL 저장 프로시저(plpgsql) + pg_cron. 실행 주체가 DB 라서 서버를 몇 대 띄워도 한 번만 돌고 앱 락이 필요 없다. Java 배치(`BatchJob` 빈 + `BatchRunner`)와 **같은 화면·API·로그 표(`batch_runs`)** 를 쓴다. 어느 쪽으로 만들지는 `BatchSource` 로 정한다 — 오래 걸리는 정리/집계처럼 DB 안에서 끝나는 일은 DB 배치, 서비스 로직·외부 호출이 섞이면 Java 배치.
* 구조·설치·cron 문법·운영은 `docs/features/batch-contract.md` 「DB 배치」. 이 문서는 **새 DB 배치를 만들 때 지킬 규칙**만 둔다.
* **pg_cron 이 없어도 서버는 떠야 한다** — 프로시저와 정의는 항상 만들어지고(일반 plpgsql), pg_cron 호출(`cron.*`)은 `batch_pg_cron_ready()` 로 확인한 뒤에만 한다. 그때 목록의 DB 배치만 「사용 불가」가 된다.

---

## 2. 주요 업무 및 규약

### 2-1. 새 DB 배치를 더하는 순서 (3곳)
1. `BatchCatalog` 에 한 줄: `XXX("키", BatchCategory.SYSTEM, BatchSource.POSTGRESQL, null, true)` — 키는 소문자·숫자·하이픈, 한 번 정하면 바꾸지 않는다. 프로시저 이름은 `batch_` + 키의 하이픈→밑줄(`procedureName()`).
2. 메시지 번들 두 곳: `batch.job.<키>.name/desc`, 결과 요약 `batch.result.<이름>`(+ `.none`) — 인자 순서는 프로시저가 넘기는 `batch_args(...)` 와 같다. 한쪽 언어만 넣지 않는다.
3. 반복 마이그레이션 `server/shared/.../db/migration/shared/R__batch_<번호>_<이름>.sql` 한 파일에 **프로시저 + 설정 기본값 + 등록**. 가장 가까운 기존 파일(`R__batch_10_auth_token_cleanup.sql`)을 복사해 시작한다. `R__batch_00_helpers.sql` 보다 번호가 커야 한다(공통 함수가 먼저).

### 2-2. 프로시저 모양
* 시그니처: `batch_<이름>(p_trigger text DEFAULT 'SCHEDULED', p_operator bigint DEFAULT NULL, INOUT p_run_id bigint DEFAULT NULL)`. pg_cron 은 `CALL batch_<이름>()`, 앱의 「지금 실행」은 `CALL …('MANUAL', 운영자ID, NULL)`.
* 본문 순서(어기지 않는다):
  1. 선언부에서 설정값을 읽는다 — `batch_param(키, 'retention_days', 기본, 최솟값, 최댓값)`. 범위 밖이면 기본값이라 보관 0일 같은 사고가 없다.
  2. `p_run_id := batch_run_begin(c_job, p_trigger, p_operator)` — NULL 이면 이미 돌고 있으니 `RETURN`. 잠금·유일 색인·죽은 RUNNING 정리를 여기서 한다.
  3. `COMMIT` — 시작 줄(RUNNING)이 바로 화면에 보이게.
  4. 덩어리 루프: `LOOP BEGIN <한 덩어리 일> EXCEPTION WHEN OTHERS THEN <batch_error_text 로 v_err> EXIT; END; v_total := …; COMMIT; EXIT WHEN …; END LOOP`. **`EXCEPTION` 블록 안에서는 `COMMIT` 할 수 없다** — 블록을 닫은 뒤에 한다.
  5. `PERFORM batch_run_close(p_run_id, c_job, 처리건수, v_err, 성공키, 성공인자, 건너뜀키, 건너뜀인자)` → `COMMIT`. 건수 0 이면 SKIP, 오류가 있으면 FAIL, 아니면 OK.
* 대량 삭제·갱신은 **덩어리(기본 5000) + 한 번 실행 상한(기본 100000)** — 한 번에 큰 트랜잭션·긴 잠금을 만들지 않는다. 남은 것은 다음 실행이 이어서 한다. 일을 PK 로 고른 뒤 지우는 `WHERE id IN (SELECT id … ORDER BY … LIMIT …)` 형태를 쓰고, 훑는 칸에 색인이 있게 한다(없으면 `V` 마이그레이션으로 색인).
* 오류 문구는 `batch_error_text(SQLSTATE, MESSAGE_TEXT)` 만 거친다 — 이메일·비밀번호/토큰 값·`(칸)=(값)` 을 가려 500자. DETAIL 은 받지 않는다. 비밀값을 `RAISE` 문구에 쓰지 않는다.
* 처리 건수는 **실제로 바뀐 행 수**. 0 이면 SKIP — 「돌았지만 할 일이 없었다」를 OK 와 가른다.

### 2-3. 등록과 설정값 (R__ 멱등 규칙)
* 파일 끝: `SELECT batch_param_seed('키','retention_days',기본)…` (처음에만 넣는다 — **운영자가 바꾼 값을 덮어쓰지 않는다**) + `SELECT batch_define('키','프로시저',시,분)` — 한국시간 매일 시각. `batch_define` 은 정의 표(`batch_db_jobs`)는 갱신하고 **pg_cron 에는 없을 때만** `cron.schedule` 한다. `cron.schedule` 은 같은 이름이 있으면 식을 덮어쓰므로 직접 부르지 않는다. 매시 도는 배치는 `SELECT batch_define_hourly('키','프로시저',분)`(같은 규칙 — 한국시간 기준 매시 M분, `batch_db_jobs.cadence = HOURLY`).
* 그래서 `R__` 파일을 고쳐 다시 적용돼도 운영자가 바꾼 cron·사용 여부는 그대로다. 기본 cron 을 **바꾸는** 릴리스는 이미 등록된 곳에 반영되지 않는다 — 꼭 필요하면 새 `V` 마이그레이션에서 `batch_pg_cron_ready()` 일 때만 `cron.alter_job` 을 부른다.
* 이미 적용된 `V` 파일은 고치지 않는다. 프로시저·공통 함수는 `R__` 를 고치고 `CREATE OR REPLACE` 로 둔다(반환형·인자 목록을 바꾸면 `DROP` 이 먼저 필요하다 — 새 `V` 에서).
* 시각 환산: pg_cron 은 `cron.timezone`(우리 구성 `Asia/Seoul`, 없으면 GMT)으로 식을 읽는다. 시각은 항상 한국시간으로 적고 `batch_daily_cron` 이 환산한다. 매일이 아닌 식(주간·월간)은 환산 때 요일이 밀릴 수 있어 별도로 확인한다.
* 권한: 앱 DB 계정이 cron job 의 소유자라 별도 GRANT·SECURITY DEFINER 래퍼가 필요 없다(권한 상승 없음). 슈퍼유저가 아니면 `cron` 스키마 USAGE 만 받으면 된다.

### 2-4. 앱 쪽 (건드릴 일이 거의 없다)
* 목록·켜기/끄기·cron 수정·지금 실행·로그는 `source` 로 갈라 이미 구현돼 있다(`BatchQueryService` · `BatchAdminService` · `DbBatchGateway`). DB 배치를 더할 때 Java 코드는 카탈로그 한 줄뿐이다.
* DB 읽기/쓰기는 `DbBatchGateway` 한 곳에 모은다(native SQL — `cron.*`·`CALL` 은 JPQL/QueryDSL 에 없다). 읽기는 트랜잭션 밖(NOT_SUPPORTED) — PostgreSQL 은 실패한 문장이 트랜잭션을 망가뜨려 화면 전체가 500 이 되기 때문이다. 「지금 실행」은 요청 표에 넣고, 배치를 켠 서버가 집어 core 의 `DbBatchCaller` 로 트랜잭션 밖의 새 연결에서 `CALL` 한다(프로시저가 COMMIT 하므로). DB 배치는 단계·건별 실패를 기록하지 않고 재실행(실패 지점부터·실패 건만)이 없다.
* 켜기/끄기·cron 수정은 `cron.alter_job` 이고 감사 기록(`BATCH_TOGGLE`·`BATCH_SCHEDULE_CHANGE`·`BATCH_RUN`)은 Java 배치와 같다.

---

## 3. 작성 예시
`R__batch_13_audit_log_retention.sql` (줄여서): 선언부 `v_days := batch_param(c_job,'retention_days',365,1,3650)` → `batch_run_begin` → `COMMIT` → 덩어리 루프 `DELETE FROM audit_logs WHERE id IN (SELECT id … LIMIT least(v_chunk, v_max - v_total))` → `batch_run_close` → `COMMIT`. 끝에 `batch_param_seed` 3줄과 `batch_define('audit-log-retention','batch_audit_log_retention',3,30)`.

**대량 갱신(업무 배치) 예시** `R__batch_15_grade_promotion.sql`: 삭제가 아니라 `UPDATE` 라 처리한 행이 다시 대상이 되지 않아야 루프가 끝난다 — 대상 조건이 갱신 뒤 거짓이 되게(승급은 서열이 올라가므로 그렇다) 하고, `user_id > v_last` 키셋으로 덩어리를 이어 훑는다(앞쪽의 대상 아닌 행을 매번 다시 읽지 않게). 고른 뒤 다른 곳이 바꾼 행은 `UPDATE … WHERE 칸 = 고른 때의 값` 으로 건너뛴다. 다른 기능과 같은 행을 건드리면 그 기능이 잡는 잠금(예: `grade_settings` 행 `FOR UPDATE`)을 **덩어리마다** 먼저 잡고 `lock_timeout` 을 짧게 건다 — COMMIT 마다 잠금이 풀리므로 덩어리 안에서 다시 잡고 판정 기준(등급 목록 등)을 다시 읽는다.

### 3-1. 업무 배치(정리가 아닌 DB 배치) 추가 규약
* **Java 에도 같은 판정이 있으면 이중 구현이다** — SQL 파일 머리말과 Java 쪽 메서드 주석에 서로의 파일·메서드를 적고(어느 쪽을 고치면 다른 쪽도 고친다), 같은 데이터로 두 쪽의 결과가 같은지 확인한다(예: 등급 승급은 등급 저장 PUT = Java `promoteAll`, 지금 실행 = SQL 을 같은 시험 데이터에 각각 돌려 비교).
* 정리 배치는 pg_cron 이 없어도 쌓일 뿐이지만 **업무 배치(`BatchCatalog#isBusinessCritical` — 분류가 SYSTEM 이 아닌 DB 배치)는 멈추면 서비스 동작이 멈춘다.** 그래서 기동 때 `DbBatchGateway#prepare` 가 pg_cron 을 못 쓰거나 등록이 없을 때 WARN 을 남긴다. Java 폴백은 만들지 않는다(DB 배치로 하기로 한 배치다).
* 키를 Java 배치에서 DB 배치로 옮길 때는 키·로그 이름을 이어 쓴다. 옛 `batch_jobs` 행은 기동 때 `BatchCatalogSynchronizer` 가 지우므로 따로 마이그레이션하지 않는다(실행 로그는 `batch_runs` 에 남는다).

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ 프로시저 안에서 로그를 `batch_runs` 에 직접 쓰는 것(`batch_run_begin/close` 를 거친다) · 오류를 삼키고 OK 로 끝내는 것
2. ❌ `cron.schedule` 직접 호출(운영자가 바꾼 cron 을 덮어쓴다) · `R__` 에서 설정값을 `INSERT … ON CONFLICT DO UPDATE` 로 덮어쓰는 것
3. ❌ `EXCEPTION` 블록 안에서 `COMMIT` · 덩어리 없는 대량 `DELETE`/`UPDATE`
4. ❌ pg_cron 이 없다고 마이그레이션이 실패하는 것(없으면 건너뛴다) · 적용된 `V` 파일 수정
5. ❌ 앱 코드에서 `cron.job` 을 엔티티로 매핑하거나 서비스 트랜잭션 안에서 읽는 것
