# 관리자 알림(종 알림 서랍 + 이벤트 관리) 계약 (설계 확정 2026-10-22 — 서버 구현 완료(roles-contract 7단계), 화면은 목업 병행)

**의도**: 관리자 앱에서 일어나는 사건(**이벤트**)을 코드가 정의하고, 이벤트가 일어나면 수신 규칙에 맞는 운영자에게 **알림**으로 전달한다. 전달 경로는 앱 안 **종 알림 서랍**(필수)과 **이메일**(선택, 이벤트별 설정)이다. 운영자는 개인 설정에서 이벤트별로 끌 수 있고(필수 이벤트 제외), 권한이 있는 운영자는 **이벤트 관리 화면**에서 이벤트별 사용 여부·이메일 전달을 바꾼다. 회원용 알림(공개 사이트)은 별개다(이번 범위 밖).
**참고**: 조사 결과는 하모니카3(`D:\project\hamonica3\workspace\hamonica3`)와 로보얌 현재 코드. 하모니카3의 "이벤트"는 모니터링 감지 규칙이고 읽음은 브라우저(IndexedDB)에서만 센다 — 사용자별 알림함이 없어 **UX(토스트·OS 알림·종 배지, 20초 폴링, 심각도 3단계, `use_yn` 로 끄기)와 운영 설계(중복 억제·커서·정리)만 가져오고 구조는 로보얌 방식으로 만든다.

## 결정 (설계 질문 10개에 대한 답)
1. **알림 저장 = 수신자별 행(fan-out)**: 이벤트가 일어난 시점에 수신 규칙으로 운영자를 정하고 운영자마다 한 행(`admin_notifications`)을 만든다. 읽음(`read_at`)이 행에 있다. 운영자 수가 적어 행 수 부담이 작다. 수신자가 나중에 권한을 잃으면 **조회 시점**에 이벤트의 `requiredPermission` 으로 걸러서 안 보이게 한다(행은 보관 정책이 지운다).
2. **도착 경로 = 폴링**: 서랍이 열려 있지 않을 때는 가벼운 `GET /api/admin/notifications/summary`(안 읽은 수·최신 시각)를 **20초**마다, 탭이 다시 보이면 즉시 호출한다. 새 알림이 있으면 목록을 받아 토스트·OS 알림(기존 `useNotification.receive`)을 띄운다. 서버 푸시(SSE)는 만들지 않는다(다중 서버·프록시 부담, 하모니카3도 폴링).
3. **심각도 4단계**: `info` · `success` · `warning` · **`critical`**(새로 추가 — 로그인 잠금 반복·결제 이상처럼 급한 것). 서랍 아이콘·토스트 색은 이에 맞춘다.
4. **문구 = 키+인자로 저장하고 읽을 때 요청 언어로 푼다**: 발행은 행위자 요청 스레드에서 일어나 행위자의 언어가 수신자와 다를 수 있다. 행에는 `title_key`·`body_key`·`args`(JSON)를 저장하고 목록 조회 때 `Messages`(요청 `Accept-Language`)로 푼다(배치 실행 기록 `messageKey/messageArgs` 와 같은 방식). 키는 `messages_ko/en.properties` 에 같이 추가한다.
5. **발행 = 업무 트랜잭션 안에서 행 삽입**(`NotificationPublisher.publish(...)` 이 호출자의 트랜잭션에 참여 — 업무가 롤백되면 알림도 없다, 커밋됐는데 알림이 사라지는 일이 없다). **이메일은 커밋 뒤**(`@TransactionalEventListener(AFTER_COMMIT)`) 보내고 실패해도 업무는 정상 종료(기존 메일 패턴). 요청 밖에서 나는 사건(**배치 실패**)은 아래 「배치 실패 알림」 방식.
6. **이메일 채널 포함**: 이벤트마다 `emailable` 이고 운영자가 이메일을 끄지 않았고(`operator_preferences.email_notification`) 관리 화면에서 그 이벤트의 이메일이 켜져 있으면 보낸다. 알림 메일만 끌 수 있고 초대·재설정·승인번호 같은 시스템 메일은 끌 수 없다(문구 `profile.emailNotificationHint` 를 이에 맞게).
7. **이벤트 관리 화면의 조절 범위 = 사용 여부 + 이메일 전달**(앱 안 전달은 항상 켜짐). **수신 규칙(누가 받는지)은 코드에 고정**하고 화면에서 바꾸지 않는다(권한 상승 방지 — 규칙을 바꾸면 보면 안 될 알림을 받을 수 있다). 필수(mandatory) 이벤트는 끌 수 없다. 권한: 새 권한 `notification.read`(조회)·`notification.manage`(설정) — 영역 시스템, 유형 「사이트·연동」.
8. **개인별 구독(이벤트별 끄기) 허용**: 내 정보 > 개인 설정 > 알림 설정에서 내가 볼 수 있는 이벤트마다 끄기(`muted`). **필수 이벤트(보안: 비밀번호 변경·재설정, 내 계정 상태·권한이 바뀜)는 끌 수 없다.**
9. **보관·정리 = DB 배치**: 읽은 알림 **30일**, 안 읽은 알림 **90일**, 운영자당 최대 **500건**(초과분은 오래된 읽은 것부터) — 프로시저 배치 `notification-cleanup`(`R__batch_16_…`, 보관 값은 `batch_params`). 서랍은 최근 30건씩 **커서 페이지**(「더 보기」). 목록 삭제 UI는 없다(읽음만).
10. **링크**: 이벤트마다 이동 경로 템플릿이 있다(예: `/settings/operators?tab=pending`). 서랍에서 항목을 누르면 읽음 처리 후 그 경로로 이동한다(운영자가 그 화면의 권한이 없으면 이동하지 않고 읽음만 — 서버가 `link` 를 주되 화면이 `UtilAdminMenu.canOpen` 으로 확인). 이벤트 정의에 `requiredPermission` 이 있어, 권한이 없는 운영자에게는 항목 자체를 숨긴다.

## 이벤트 정의 방식 (코드가 원천 — `Permission` enum 과 같은 방식)
- Java enum `AdminNotificationEvent`(shared, `com.community.notification`): `code`(안정 식별자, 한 번 정하면 불변 — 감사 `AuditActions` 규칙) · `area`(`NotificationArea` — `PermissionArea` 의 영역에서 「종합」을 빼고 알림 전용 「보안」 `security` 를 더한 것, id 는 같다) · `severity` · **수신 규칙** `recipient`(`SELF`=대상 운영자 본인 / `PERMISSION_HOLDERS`(권한 코드들, 루트 포함) / `ROOT`) + `excludeActor`(행위자 제외) · `requiredPermission`(보기 권한, 없으면 누구나) · `mandatory` · `defaultEnabled` · `emailable` · `linkPath`(경로 템플릿, 인자 치환) · `collapseSeconds`(같은 이벤트·같은 키를 N초 안에 한 건으로 묶기, 0 이면 안 묶음) · `featureReady`(발행처가 아직 없는 예약 이벤트는 관리 화면에 「준비 중」으로만).
- 서버가 뜰 때 `notification_events` 표와 맞춘다(`PermissionCatalogSynchronizer` 패턴): 없으면 기본값으로 넣고, 운영자가 바꾼 `enabled`·`email_enabled` 는 덮어쓰지 않으며, 코드에서 사라진 이벤트는 지운다(알림 행은 이벤트 코드를 문자열로 갖고 이름은 문구 키로 푼다).
- **이벤트 추가 방법**(작성 방식, 서버 구현에서 `.recipients` 가 더해졌다): ① enum 에 한 줄 ② `messages_ko/en` 에 `notification.event.<코드>.name`·`.description`·`.recipients`(수신 대상 한 줄)·`.title`·`.body`(빠지면 기동 때 `local`·`dev` 는 멈추고 그 밖은 ERROR) ③ 사건이 일어나는 서비스에서 `publisher.publish(EVENT, args…)` 한 줄(+행위자·대상) ④ 끝. 새 화면·표·권한 변경 없음. 규약은 `.claude/guides/backend/notification.md`.

## 초기 이벤트 (발행처가 이미 있는 것 — `featureReady=true`)
| 코드 | 영역 | 수신 규칙 | 심각도 | 필수 | 이메일 | 링크 | 발행처 |
|---|---|---|---|---|---|---|---|
| `BATCH_RUN_FAILED` | 시스템 | `batch.read` 보유자 | warning | — | ✓ | `/settings/batch-logs?jobName={jobName}` | 배치 실패(아래 방식) |
| `OPERATOR_INVITATION_ACCEPTED` | 시스템 | 초대한 운영자 + `operator.manage` 보유자(행위자 제외) | info | — | — | `/settings/operators` | 초대 수락 |
| `OPERATOR_ACCOUNT_CREATED` | 시스템 | `operator.manage` 보유자(행위자 제외) | info | — | — | `/settings/operators` | 운영자 직접 추가 |
| `OPERATOR_STATUS_CHANGED_ME` | 보안 | 본인 | warning | ✓ | ✓ | `/me` | 내 계정 잠금·사용 중지·승인·기간 변경 |
| `OPERATOR_LOCKED_BY_FAILURES` | 보안 | `operator.lock` 보유자 | **critical** | — | ✓ | `/settings/operators` | 로그인 실패 5회로 잠금 시작 |
| `ROLES_CHANGED_ME` | 보안 | 본인 | info/warning(회수) | ✓ | — | `/me` | 내 역할 추가·회수, 내 역할의 권한 변경 |
| `PASSWORD_CHANGED_ME` | 보안 | 본인 | warning | ✓ | ✓ | `/me?tab=password` | 비밀번호 변경 |
| `PASSWORD_RESET_DONE_ME` | 보안 | 본인 | warning | ✓ | ✓ | `/me?tab=password` | 비밀번호 재설정 |
| `SITE_SETTINGS_CHANGED` | 시스템 | `site.read` 보유자(행위자 제외) | info | — | — | `/settings/general` | 사이트 설정 변경(묶음 60초) |
| `GRADE_SETTINGS_SAVED` | 회원 | `grade.read` 보유자(행위자 제외) | info | — | — | `/grades` | 등급 설정 저장 |
| `GRADE_PROMOTION_RUN` | 회원 | `grade.read` 보유자 | info | — | — | `/settings/batch-logs?jobName=grade-promotion` | 일수 승급 배치 종료(처리 건수>0) |

**예약 이벤트(`featureReady=false`, 관리 화면에 「준비 중」)**: 정책 문서의 영역별 대기열형 — `REPORT_RECEIVED`(`report.manage`) · `SANCTION_APPEAL_FILED`(`appeal.manage`) · `BOARD_SANCTION_REPEAT_REVIEW`(`sanction.manage`, warning) · `NICKNAME_IMAGE_PENDING`(`nickname-image.manage`) · `CLUB_OPEN_REQUEST_PENDING`(`group.manage`) · `HOTDEAL_SUSPECTED_PROMO`(`hotdeal.manage`, warning) · `COMMERCE_ORDER_ANOMALY`·`COMMERCE_REFUND_REQUESTED`(`commerce.*`, warning/critical) · `BUSINESS_APPLICATION_PENDING`(`business.manage`) · `AD_REVIEW_PENDING`(`ad.manage`) · `OPERATOR_APPLICATION_PENDING`(`operator.manage`, 신청 기능 구현 후). 기능이 만들어질 때 `featureReady=true` 와 발행 한 줄만 더한다.

## 배치 실패 알림
Java 배치와 DB(pg_cron) 배치 모두 요청 밖에서 끝난다. DB 프로시저가 알림 수신자를 계산하게 하지 않기 위해 **Java 감시자**가 한다: `batch_runs` 에 `notified_at` 칸을 두고, 짧은 주기(30초)로 `status='FAIL' AND notified_at IS NULL` 행을 `FOR UPDATE SKIP LOCKED` 로 가져와 `BATCH_RUN_FAILED` 를 발행하고 `notified_at` 을 채운다(여러 서버 중복 방지, 최대 지연 약 30초, 같은 배치가 계속 실패하면 `collapseSeconds`(30분)로 묶는다). 등급 승급 배치 종료 알림(`GRADE_PROMOTION_RUN`)도 같은 감시자가 `OK` 이고 처리 건수>0 인 `grade-promotion` 행을 본다.

## 데이터(Flyway shared V40~ — 병행 작업과 번호가 겹치지 않게 큰 수)
- `notification_events`(code PK, `enabled`, `email_enabled`, updated_at/by) — 코드가 원천, 운영자가 바꾸는 값만.
- `admin_notifications`(id Snowflake, operator_id FK, event_code, severity, title_key, body_key, args jsonb, link_path, dedupe_key, created_at, read_at) + 인덱스 `(operator_id, id DESC)`, 안 읽은 수용 부분 인덱스 `(operator_id) WHERE read_at IS NULL`.
- `operator_notification_mutes`(operator_id, event_code, PK 둘).
- `batch_runs.notified_at`.
- 정리 DB 배치 `notification-cleanup`(`R__batch_16_notification_cleanup.sql`, 03:50 KST, `batch_params`: `read_retention_days` 30·`unread_retention_days` 90·`per_operator_cap` 500).

## API (`/api/admin`, 응답 `ApiResponse`, 쓰기는 CSRF)
**운영자 본인(로그인만 필요, `@AdminAuthenticated`)**
| | |
|---|---|
| `GET /notifications?cursor&size` | → `{items:[{id,eventCode,area,kind('info'\|'success'\|'warning'\|'critical'),title,body\|null,link:{path}\|null,createdAt,read}], nextCursor\|null, unread}` 최근순, size 기본 30(최대 100), 권한이 없는 이벤트·내가 끈 이벤트는 제외 |
| `GET /notifications/summary` | → `{unread, latestId\|null}` (폴링용, 가벼움) |
| `POST /notifications/{id}/read` | 204 (내 것만, 없으면 404 `NOTIFICATION_NOT_FOUND`) |
| `POST /notifications/read-all` | 204 |
| `GET /profile/notification/events` | → `[{code,area,name,description,severity,mandatory,muted,emailable}]` 내가 볼 수 있는 이벤트만(준비 중 제외) |
| `PUT /profile/notification/events/{code}` | `{muted}` → 200 같은 항목 / 필수 이벤트를 끄면 409 `NOTIFICATION_MANDATORY` |
| 기존 `GET/PUT /profile/notification` | `{emailEnabled}` 유지 |

**이벤트 관리(권한 `notification.read` / `notification.manage`)**
| | |
|---|---|
| `GET /notification-events` | → `[{code,area,severity,name,description,recipientText,mandatory,emailable,enabled,emailEnabled,featureReady,sentLast30Days}]` 영역·코드순 |
| `PUT /notification-events/{code}` | `{enabled,emailEnabled}` → 200 같은 항목 / 필수 이벤트를 끄면 409 `NOTIFICATION_MANDATORY`, 준비 중 이벤트는 409 `NOTIFICATION_EVENT_NOT_READY`, `emailable=false` 인데 이메일을 켜면 400 `fields.emailEnabled`. 감사 `NOTIFICATION_EVENT_UPDATE`(전·후 값) |

## 화면
- **종 알림 서랍**(기존, `components/shell/NotificationDrawer.vue`·`useNotification`): `kind` 에 `critical` 추가, 항목에 `link`(누르면 읽음 + 이동), 「더 보기」 커서 페이지, 폴링 20초(`summary`)+탭 복귀 즉시, 새 알림 토스트·OS 알림은 기존 `receive` 유지. 영역 칩/아이콘은 선택(이벤트 코드→아이콘).
- **이벤트 관리 `/settings/notifications`**(신규, 설정 메뉴 > 알림 — 권한 `notification.read`, 변경 `notification.manage`): 영역별 묶음 표 — 이벤트 이름·설명·수신 대상 문구·심각도·필수 표시·**사용 스위치**·**이메일 스위치**·최근 30일 발송 수·「준비 중」 표시. 저장은 스위치 즉시 적용(구획마다 따로 저장 아님).
- **내 정보 > 개인 설정 > 알림 설정**(기존 구획 확장): 이메일 수신 스위치(기존) + 이벤트별 끄기 목록(필수는 잠금 표시).

## 서버 구현 결과 (2026-10-22 — 상세·검증은 `docs/features/roles-contract.md` 「7단계 구현 결과」)
- Flyway shared `V40__admin_notifications`(세 표 + `batch_runs.notified_at`) · `R__batch_16_notification_cleanup.sql`. 권한 `notification.read`·`notification.manage`(서버 `Permission` 80개).
- 발행기는 shared(`NotificationPublisher`), API·이벤트 관리·감시자·카탈로그 맞춤은 admin. 알림 행은 **JDBC 세이브포인트** 안에서 업무 트랜잭션에 참여해 만들고(롤백되면 함께 사라짐, 실패해도 업무는 그대로), 이메일은 커밋 뒤 별도 스레드풀.
- 묶기 규칙(확정): 묶음 시간 안의 같은 수신자·이벤트·묶음 키 중복은 **새 알림을 만들지 않는다**(처음 것이 남는다).
- 수신 규칙의 「활성 운영자」 = 승인·잠기지 않음·만료 표시 없음·사용 중·계정 기간 안. 「내 계정」(`SELF`) 이벤트는 상태와 무관하게 쌓는다.
- 이벤트 관리 `PUT` 검사 순서: 404 → 400(`fields.enabled|emailEnabled` 없음) → 409 `NOTIFICATION_EVENT_NOT_READY` → 409 `NOTIFICATION_MANDATORY` → 400 `fields.emailEnabled`(이메일 불가 이벤트). 감사 `NOTIFICATION_EVENT_UPDATE`(대상 종류 `NOTIFICATION_EVENT`, `before/after = {enabled, emailEnabled}`, 바뀐 때만).
- 보관: 읽은 알림 30일은 **읽은 시각** 기준, 안 읽은 알림 90일은 **만든 시각** 기준, 운영자당 500건 초과는 안 읽은 것을 먼저 남기고 오래된 읽은 것부터 지운다.
- 역할 삭제는 알림을 내지 않는다(쓰는 운영자가 있으면 삭제가 409 라 받을 사람이 없다). 로그인 잠금 알림은 계정이 있는 이메일일 때만 나가고 이메일 원문이 아니라 운영자 이름을 싣는다.

## 미확정·가정
- 알림 보관값(30/90일·500건), 폴링 20초, 묶음 시간(사이트 설정 60초·배치 실패 30분)은 가정이며 설정값이다.
- 이메일 알림 템플릿은 간단한 한 통 형식(제목=알림 제목, 본문=알림 본문+링크).
- 회원용(공개 사이트) 알림은 별도 설계(이벤트 구조는 같은 방식을 쓸 수 있다).
- 「내 계정」 이벤트의 수신자는 로그인 상태와 무관하게 행이 쌓이고, 잠긴 계정은 로그인 후에 본다.

## 알림 도구·조직 구성 (목업 단계 — 화면 확정 전, 서버 미구현)

**의도**: 알림이 서랍 + 이메일뿐이던 것을 하모니카3처럼 **도구(채널)를 고를 수 있게** 넓힌다 — Slack · Mattermost · 이메일 · 브라우저 알림(카카오·SMS는 업체 계약 때문에 새로 만들지 않고 「준비 중」으로만 보인다). 팀 단위 수신을 위해 **조직 구성(팀)** 화면을 만들고, 이벤트의 「추가 수신 팀」으로 쓴다. 이메일은 지금 방식 그대로 설정 > 메일 서버 설정으로 보내고 도구 전용 SMTP 는 두지 않는다. 참고: 하모니카3 `settings/application/teams.vue`·`TeamFormDialog`·`TeamMemberDialog`, `settings/workspace/notifications.vue`·`NotificationConfigForm`·`NtfSecretField`, 서버 `ChannelSpec`.

**화면 경로**
- `/settings/teams` 조직 구성(설정 메뉴 > 시스템 묶음 > 일반 · 알림 · 알림 도구 · **조직 구성** 순) — 왼쪽 팀 목록(검색·새 팀·위/아래 화살표·「사용 안 함」 딱지) + 오른쪽 팀 정보(직접 수정)·팀원(추가/빼기). 새 팀·팀원 추가는 다이얼로그.
- `/settings/notification-channels` 알림 도구(설정 메뉴 > 시스템 묶음, 「알림」 바로 아래) — 도구 표(종류 딱지·이름·사용·최근 연결 시험·시험/수정/삭제)와 추가/수정 다이얼로그. 이벤트 관리와 **별도 화면**이고 도구 목록 상태는 두 화면이 공유한다.
- `/settings/notifications` 알림 설정(이벤트 관리) — 기존 이벤트 표의 「이메일 스위치」를 **「전달 도구」(다중 선택, 등록된 도구 목록 사용) + 「추가 수신 팀」(다중 선택)** 으로 교체. 도구 관리는 위 알림 도구 화면으로 가는 안내 한 줄과 링크(고를 도구가 없거나 모두 꺼져 있으면 경고색 안내)만 둔다. 영역별 구획 이동은 우측 구역 내비(탭 아님).
- 내 정보 > 개인 설정 > 알림 설정에 「이 브라우저에서 알림 받기」 스위치.

**필드·검증** (화면 선검사는 서버와 같은 규칙으로 서버가 다시 본다)
- 팀: 이름(필수·50자·중복 불가) · 설명(선택·500자) · 사용 여부 · 정렬 순서(숫자 입력 없이 화살표만, 서버가 다시 매김). 팀원 = 운영자 계정(여러 명 선택, 이미 든 사람은 표시만). **팀원이 있는 팀은 삭제 불가**(409 `TEAM_HAS_MEMBERS`) → 「사용 안 함」.
- 도구: 종류(만든 뒤 변경 불가) · 이름(필수·100자·중복 불가) · 설명 · 사용 여부. 종류별 칸 — Slack: 봇 토큰(비밀)+채널 / Mattermost: 웹훅 주소(비밀) 또는 서버 주소+봇 토큰(비밀)+채널 / 이메일·브라우저·서랍: 칸 없음. 주소는 `http(s)://`. **비밀 값은 응답에 없고** 화면은 「입력됨」만 보이며, 비워 두면 저장된 값을 그대로 쓴다.
- 기본 제공: 서랍(항상 켜짐·끄기/지우기/이름 변경 불가) · 이메일 · 브라우저 알림(이름 변경·삭제 불가, 사용 여부만). 카카오·SMS 는 만들기 400 `NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED`.
- 연결 시험: 이메일·Slack·Mattermost 만. 결과(`ok`·`message`·`testedAt`)가 도구의 `lastTest` 로 남는다. 시험이 **실패한 것**은 오류가 아니라 200 `ok:false`.
- 이벤트 전달: 서랍은 고르는 것이 아니라 항상 켜짐(필수 이벤트는 사용 자체가 잠김). 도구마다 이벤트에서 쓸 수 있는지가 다르다 — 이메일은 `emailable`, Slack·Mattermost·팀은 `broadcastAllowed`(본인에게만 가는 이벤트는 불가), 꺼진 도구·멈춘 팀은 **새로** 고를 수 없음(이미 고른 것은 해제 가능), 준비 중 이벤트는 모두 잠김.

**권한**: 알림 도구 화면도 기존 `notification.read`(화면·조회) / `notification.manage`(변경·시험)를 그대로 쓴다. 조직 구성 `team.read`(화면·조회) · `team.manage`(만들기·고치기·지우기·옮기기·팀원 관리) — 영역 시스템, 유형 「운영자·역할」(`operatorRole`). 알림 도구·전달 설정은 기존 `notification.read` / `notification.manage`. 팀 이름을 보려면 `team.read` 도 있어야 한다(없으면 알림 화면은 고른 팀 **개수**만 보인다).

**API 후보** (`/api/admin`, 응답 `ApiResponse`, 쓰기는 CSRF — 상세 모양은 각 `use*Api.ts` 머리말)
| | |
|---|---|
| `GET·POST /teams` · `PUT·DELETE /teams/{teamId}` | 팀 목록(팀원 포함)·만들기·고치기·지우기 |
| `PUT /teams/{teamId}/move` | `{direction:'UP'\|'DOWN'}` → 목록 전부 |
| `PUT /teams/{teamId}/members` | `{operatorIds}` 팀원 전체 교체 |
| `GET /teams/{teamId}/candidates` | 활성 운영자 + `alreadyIn` |
| `GET·POST /notification-channels` · `PUT·DELETE /notification-channels/{channelId}` | 도구 목록(비밀 제외, `secretKeys`로 「입력됨」)·만들기·고치기·지우기 |
| `PUT /notification-channels/{channelId}/enabled` | `{enabled}` 켜기/끄기 |
| `POST /notification-channels/{channelId}/test` | → `{ok, message, testedAt}` |
| `GET·PUT /notification-events[/{code}]` 확장 | 응답에 `channels: string[]`(고른 도구 id, 이메일 도구 `email` 포함·서랍 제외)·`teamIds: string[]`·`broadcastAllowed`, 요청에 `channels`·`teamIds` 추가(서버가 `channels`의 `email` 여부를 `emailEnabled` 와 같은 값으로 봄) |

**목업 가정** (화면 확정 때 되묻는다)
1. **수신 의미**: 코드에 고정된 기본 수신 규칙은 그대로(권한 상승 방지 — 화면에서 못 바꾼다). 고른 팀의 팀원은 **서랍·이메일·브라우저 알림**으로 추가 수신하고(이벤트의 `requiredPermission` 이 없는 팀원은 걸러짐), **Slack·Mattermost 도구는 팀원에게 따로 가지 않고 도구에 설정한 채널로 한 번** 게시된다.
2. 본인에게만 가는 이벤트(`*_ME`, 필수 보안 이벤트)는 팀·공유 채널로 보낼 수 없다(`broadcastAllowed=false`). 실제 판정은 이벤트 정의의 수신 규칙(`SELF`)이 한다.
3. 멈춘 팀은 알림을 받지 않고 새로 고를 수 없다(이미 고른 선택은 남는다). 팀·도구를 지우면 이벤트 선택에서 함께 빠진다(서버 연쇄 삭제).
4. 이메일 도구/이벤트의 이메일 전달만 **지금의 실제 서버 `emailEnabled`** 에 붙어 있고, 그 밖의 도구·팀·전달 선택은 메모리 목업이다(새로고침하면 처음으로 돌아감). 서버가 `channels`/`teamIds` 를 받으면 이 나눔이 없어진다.
5. 브라우저 알림(개인)은 **목업이 아니라 실제 구현** — 서버 필요 없음. 새 설정을 만들지 않고 기존 화면 설정 `notifyOs`(이 브라우저의 localStorage) 와 20초 폴링의 OS 알림 경로를 같이 쓴다: 켜면 권한을 묻고, 창이 가려졌거나 다른 창을 볼 때 `new Notification` 을 띄우며, 알림이 한 건이면 누를 때 그 알림의 링크로 이동한다(여러 건이면 서랍). 이벤트별 개인 끄기는 기존 그대로.
6. 목업 스위치(주소 `?mock=`): `team-error|team-empty|team-save-error|team-no-read`, `notification-channel-error|-save-error|-test-error|-no-read`(알림 도구 화면), `notification-delivery-error|-save-error`(알림 설정 화면).

**미확정**
- 팀 수신 권한: 팀원이 이벤트를 볼 권한(`requiredPermission`)이 없으면 건너뛰는 것으로 가정 — 서버 정책 확인 필요. 팀원 수 한도·팀 중첩(상위 팀) 없음.
- Slack/Mattermost 게시 문구 형식·재시도·실패 알림, 연결 시험의 실제 동작(Slack `auth.test`/메시지 발송 여부), 비밀 암호화 방식(DB 비밀번호와 같은 `ROBOYAM_CRYPTO_KEY` 후보), 감사 로그 항목(`TEAM_*`·`NOTIFICATION_CHANNEL_*`, 비밀 값은 제외).
- 권한 `team.read`/`team.manage` 는 서버 `Permission` 목록에 아직 없다 — 서버 구현 전에는 최고 관리자만 화면을 본다. 역할 편집기에서 이 권한을 체크해 저장하면 서버가 모르는 코드라 거절할 수 있다.
- 이메일 도구를 이벤트의 `channels` 로 합치는 시점, 메일 서버 설정 화면(설정 > 메일 서버 설정)은 아직 없다(안내 문구만).
