# 🔔 Backend Notification Guide (`notification.md`)

## 1. 역할 및 기본 원칙
* 관리자 앱의 사건(**이벤트**)을 알림으로 전달한다 — 앱 안 종 알림 서랍(필수)과 이메일(이벤트별 선택). 회원용(공개 사이트) 알림은 별개다 — `docs/features/member-notification-contract.md`(shared `MemberNotificationPublisher`, 같은 세이브포인트 격리). 설계·결정은 `docs/features/notification-contract.md`, 구현 사실은 `docs/features/roles-contract.md` 「7단계 구현 결과」.
* **이벤트의 정의는 코드(`AdminNotificationEvent` enum, shared `com.community.notification.entity`)가 원천**이다(`Permission`·`BatchCatalog` 와 같은 방식). 표 `notification_events` 는 운영자가 화면에서 바꾸는 값(사용 여부·이메일 전달)만 든다. **수신 규칙·심각도·필수 여부·링크는 코드에 고정**이고 화면에서 바꾸지 않는다(규칙을 바꾸면 보면 안 될 알림을 받을 수 있다 — 권한 상승 방지).
* 알림은 이벤트가 일어난 시점에 수신자마다 한 행(`admin_notifications`)을 만든다(fan-out). 문구는 **키 + 인자**로 저장하고 읽을 때 읽는 사람의 요청 언어로 푼다.

---

## 2. 주요 업무 및 규약

### 2-1. 이벤트 정의 (`AdminNotificationEvent`)
한 줄 = 코드(상수 이름, **한 번 정하면 바꾸지 않는다** — 알림 행·개인 끄기·이벤트 설정이 이 값으로 가리킨다) · 영역(`NotificationArea` — 권한 영역 + 보안) · 심각도(`info`·`success`·`warning`·`critical`) · 수신 규칙 · `excludeActor` · 보는 권한(`requiredPermission`) · `mandatory` · `emailable` · 링크 템플릿 · `collapseSeconds` · `featureReady`.
* **수신 규칙**: `.self()` 대상 운영자 본인 / `.holders(권한…)` 그 권한 중 하나라도 가진 역할의 운영자 + 최고 관리자(**활성 운영자만** — 승인·잠기지 않음·사용 중·기간 안) / `ROOT`. 본인 이벤트(`*_ME`)는 상태와 상관없이 쌓는다. 맥락의 추가 수신자(`alsoTo`)는 규칙에 더해진다.
* **보는 권한**: 권한 보유자 규칙이면 첫 권한이 기본값이다. 권한을 잃은 운영자는 **조회 시점에** 그 이벤트 알림이 안 보인다(행은 보관 정책이 지운다). 본인 이벤트는 권한이 필요 없다. 링크가 가리키는 화면을 볼 권한과 맞춘다.
* **필수(`mandatory`)**: 보안(내 계정의 상태·권한·비밀번호가 바뀜) — 이벤트 관리에서도 개인 설정에서도 끌 수 없다(409 `NOTIFICATION_MANDATORY`).
* **`emailable`**: 이메일로 보낼 수 있는 이벤트 — 준비 중이 아니면 모두다. 처음 이메일 전달 값은 enum 의 `.email()` 로 정한 이벤트만 켬(`defaultEmailEnabled`).
* **회원 이벤트**(공개 사이트 회원 알림)의 원천은 shared `MemberEvent`(실제 = `MemberNotificationType` 이름, 그 밖은 준비 중)이고 운영자 값은 `member_event_settings`(사용·앱 알림·이메일·회원이 끌 수 있음). 발행기는 `enabled && inApp` 을 본다 — `docs/features/message-template-contract.md` 「서버 구현 결과(회원 이벤트·이메일 확장)」.
* **`featureReady=false`**(예약): 이벤트 관리에 「준비 중」으로만 보이고 발행되지 않는다. 기능이 만들어질 때 `.reserved()` 만 지운다.

### 2-2. 이벤트 추가 방법 (4단계)
1. enum 에 한 줄 — 가장 가까운 기존 상수를 본뜬다.
2. 메시지 번들 **ko·en 두 곳**에 `notification.event.<코드>.name` · `.description` · `.recipients`(수신 대상 한 줄 — 권한 이름은 화면 쪽에 있어 서버가 지어내지 않는다) · `.title` · `.body`. 빠지면 기동 때 `local`·`dev` 는 멈추고 그 밖은 ERROR 로그다. 본문이 없는 알림은 `.body=` (빈 값).
3. 사건이 일어나는 서비스에서 `publisher.publish(EVENT, NotificationContext…)` 한 줄(행위자 `actor`·대상 `target`·인자 `args`). 문장이 상황에 따라 갈리면 `AdminNotices`(admin)에 메서드를 더하고 서비스는 그것을 한 줄로 부른다.
4. 끝 — 새 화면·표·권한 변경은 없다. 새 권한이 필요한 이벤트면 `Permission` 에 더한다(프론트 `util-permission-catalog.ts` 와 같은 코드).

### 2-3. 발행 규약 (`NotificationPublisher.publish`)
* **업무 트랜잭션 안에서 부른다** — 같은 트랜잭션에 참여해 업무가 롤백되면 알림도 없고, 커밋됐는데 알림이 사라지는 일이 없다. 업무 변경을 끝내고(감사 기록 다음) 마지막에 부른다.
* **알림 실패가 업무를 깨지 않는다**: 발행은 던지지 않는다(만든 수를 돌려주고 실패는 ERROR 로그 — 원인은 예외 클래스 이름만). 수신자 계산·행 만들기를 업무 트랜잭션 연결에 잡은 **JDBC 세이브포인트** 안에서 하고 실패하면 되돌린다. 알림 행은 JPA 엔티티로 `persist` 하지 않는다(실패한 엔티티가 업무의 다음 flush 를 깨뜨린다). 스프링 `PROPAGATION_NESTED` 는 JPA 방언 때문에 쓰지 못한다. 업무 서비스에서 발행을 `try-catch` 로 감싸지 않는다.
* **받는 사람**: 수신 규칙 + 추가 수신자 − 행위자(`excludeActor`) − 이벤트 관리에서 꺼진 이벤트(필수 제외) − 운영자가 개인 설정에서 끈 이벤트(필수 무시) − 묶음 중복.
* **묶기**: `collapseSeconds` 안에 같은 수신자가 같은 이벤트·같은 `dedupeKey` 알림을 이미 받았으면 **새 알림을 만들지 않는다**(처음 것이 남는다 — 기존 행을 고치지 않는다). `dedupeKey` 가 없으면 그 이벤트 전체가 한 묶음이다. 같은 행을 잠그고 있는 요청 안(사이트 설정 행 등)에서 부르면 동시 중복도 줄 선다.
* **요청 밖의 사건**(배치): 요청 스레드가 없으므로 감시자가 한다 — `BatchRunWatcher`(30초) 가 `batch_runs.notified_at IS NULL` 인 끝난 줄을 `FOR UPDATE SKIP LOCKED` 로 잡아 알림 + 표시를 한 트랜잭션으로 한다. Java 배치와 DB 배치(pg_cron)가 한 경로다. DB 프로시저가 수신자를 계산하게 하지 않는다.

### 2-4. 문구 키 규칙
* 키: `notification.event.<코드>.{name,description,recipients,title,body}` · 상황별 문장 `notification.variant.<이름>` · 오류 `error.NOTIFICATION_*` · 메일 `notification.mail.*`. **ko·en 을 함께** 추가한다.
* 인자는 `{0}` `{1}` 자리에 들어간다. 글자·수 외에 `MessageRef`(읽을 때 풀리는 문구 — 배치 이름, 「역할이 추가됐어요」 같은 문장)를 줄 수 있다. 인자가 있는 문구에는 작은따옴표를 쓰지 않는다.
* **인자에 싣지 않는다**: 비밀번호·토큰·승인번호·이메일 원문. 운영자 이름·배치 이름·역할 이름 같은 표시용 값만. 오류 요약은 이미 가려진 문장(`BatchErrorText`·`batch_error_text`)만, 긴 글은 300자에서 잘린다.

### 2-5. 링크
* 이벤트의 `linkPath` 는 관리자 앱 안의 경로 템플릿이고 `{이름}` 자리는 발행 때 `NotificationContext.link(이름, 값)` 으로 채운다(값은 URL 인코딩). 채울 값이 없으면 링크 없이 낸다. 서버는 `link.path` 만 주고, 권한이 없는 화면으로는 이동하지 않는 것은 화면의 몫이다.

### 2-6. 이메일
* 커밋 뒤(`@TransactionalEventListener(AFTER_COMMIT)`) 별도 스레드풀에서 보낸다(`NotificationEmailSender`). `emailable` + 이벤트 관리의 이메일 전달 + 운영자의 `email_notification` 이 모두 참일 때만. 제목·본문은 **받는 사람의 언어**(내 표시 형식을 쓰면 그 언어 → 사이트 표시 기본 언어 → ko)로 풀고 HTML 로 가린다. 링크는 `ADMIN_URL` 기준 절대 주소. 메일 서버가 없으면 로그만. 주소·내용·예외 문구는 로그에 남기지 않는다.

### 2-7. API·정리
* 본인 API 는 `@AdminAuthenticated`(경로에 운영자 id 가 없고 대상은 늘 요청한 사람), 이벤트 관리는 `notification.read`/`notification.manage`. 목록·미읽음·요약·모두 읽음은 `NotificationVisibility` **한 곳의 같은 거르기**를 쓴다(권한 + 끄지 않음 + 발행처 있음).
* 보관·정리는 DB 배치 `notification-cleanup`(`R__batch_16_…`, 읽은 지 30일 · 안 읽은 채 90일 · 운영자당 500건, `batch_params`). 새 표가 쌓이면 `db-batch.md` 를 따라 정리 규칙을 같이 만든다.

### 2-8. 알림 도구(채널)·팀·이벤트별 전달
* **도구**: 서랍·이메일·브라우저는 코드가 내려주는 **기본 제공**(id `drawer`·`email`·`browser`, 이메일·브라우저의 사용 여부·시험 결과만 `notification_builtin_channels`)이고, 사용자가 만드는 것은 **Slack·Mattermost 뿐**(`notification_channels`, 최대 20개). 카카오·SMS 는 enum 에도 만들지 않고 400 `fields.type`. 이메일은 설정 > 메일 서버로 보내며 도구 전용 SMTP 가 없다. 브라우저 알림은 순수 클라이언트 동작이라 서버는 이벤트의 `browser_enabled` 선택만 저장한다.
* **이벤트별 전달**: 이벤트마다 `channels`(`email` = 기존 `email_enabled`, `browser`, Slack/Mattermost 도구 id)와 `teamIds`(추가 수신 팀). **`broadcastAllowed`**(코드 정의: 수신 규칙 `SELF` 가 아니고 `mandatory` 가 아님)가 false 인 이벤트에는 팀·Slack/Mattermost 를 지정할 수 없다(400 `NOTIFICATION_BROADCAST_NOT_ALLOWED`). 꺼진 도구·멈춘 팀은 **새로** 고를 수 없다(이미 고른 것은 둔다). 팀 선택을 바꾸려면 `team.read` 도 필요하다.
* **수신**: 기본 수신 규칙 + 사용 중인 팀의 팀원 중 **활성 운영자이면서 `requiredPermission` 이 있는 사람**(Set 이라 중복 없음). 행위자 제외·개인 끄기·묶기는 팀원에게도 같다. Slack·Mattermost 는 수신자 수와 상관없이 도구마다 **이벤트당 한 번**, 커밋 뒤(`NotificationChannelPostSender`, `AFTER_COMMIT` + 별도 스레드풀)에 올린다 — 업무·서랍·다른 도구는 실패에 영향받지 않는다. 이메일은 이벤트의 이메일 전달 **그리고** 이메일 도구가 켜져 있어야 나간다.
* **비밀·외부 요청**: 봇 토큰·웹훅 주소는 `SecretEncryptor` 암호문만 저장하고 응답·로그·감사·예외·`toString` 에 평문도 암호문도 싣지 않는다(응답은 `secretKeys`). 외부로 나가는 요청은 **반드시** `SafeHttpClient`(+`OutboundTargetGuard`)를 지난다 — 검사한 IP 로 직접 연결, 리다이렉트 안 따라감, 짧은 제한 시간, 응답 본문은 돌려주지 않고 **짧은 사유 코드**(`ChannelPostResult`, 번들 `notification.channel.test.<코드>`)만. 메시지에 값을 섞을 때는 `ChannelMessageFormatter` 로 멘션·마크다운을 무력화한다. 새 외부 연동(도구 종류)을 더할 때 `HttpClient`/`URL` 로 직접 요청하지 않는다.
* 감사 `TEAM_*`·`NOTIFICATION_CHANNEL_*` — 비밀·주소는 값 없이 바뀐 칸 이름만. 구조·검증 결과는 `docs/features/notification-contract.md` 「서버 구현 결과 (알림 도구·조직 구성)」.

---

## 3. 작성 예시
```java
// enum — 한 줄
SITE_SETTINGS_CHANGED(spec(SYSTEM, INFO).holders(Permission.SITE_READ).excludeActor().link("/settings/general").collapse(60)),

// 업무 서비스 — 트랜잭션 안, 마지막에 한 줄 (문장이 갈리면 AdminNotices 메서드)
audit.record(event);
notices.siteSettingsChanged(actor, "siteBasic");
// 갈리지 않으면 직접:
publisher.publish(AdminNotificationEvent.GRADE_SETTINGS_SAVED, NotificationContext.empty().actor(actor.operatorId()));

// 배치 실패 — 인자에 읽을 때 풀리는 문구 참조, 묶음 키, 링크 값
publisher.publish(AdminNotificationEvent.BATCH_RUN_FAILED,
        NotificationContext.empty().args(new MessageRef(job.nameKey()), job.key(), errorSummary).dedupe(job.key()).link("jobName", job.key()));
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ 수신 규칙·심각도·필수 여부를 DB·화면에서 바꾸게 하는 것, 이벤트 코드를 바꾸거나 재사용하는 것
2. ❌ 한쪽 언어 번들에만 키를 추가하는 것, 문구를 코드에 리터럴로 쓰는 것, 인자에 비밀번호·토큰·승인번호·이메일 원문을 싣는 것
3. ❌ 업무 트랜잭션 밖에서 알림 행을 만드는 것(롤백돼도 알림이 남는다), 알림 행을 JPA `persist` 로 올리는 것, 발행을 `try-catch` 로 감싸거나 알림 실패로 업무를 실패시키는 것
4. ❌ 트랜잭션 안에서 메일을 보내는 것(이메일은 커밋 뒤 발송기가 한다), 메일 주소·내용을 로그에 남기는 것
5. ❌ 배치 실패를 DB 프로시저·배치 코드가 직접 알리는 것(감시자 한 경로), 알림 조회에서 권한·끄기 거르기를 목록/요약마다 따로 구현하는 것
6. ❌ 운영자가 입력한 주소로 `SafeHttpClient` 를 거치지 않고 요청하는 것, 외부 응답 본문·주소·토큰을 응답/로그/DB 에 남기는 것, 업무 트랜잭션 안에서 Slack·Mattermost 를 부르는 것, `broadcastAllowed=false` 이벤트를 팀·공유 채널로 보내는 것
