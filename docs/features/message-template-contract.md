# 알림 템플릿·문구 계약 (화면 확정 2026-10-23 — 서버 단계 전, 화면은 메모리 목업)

**의도**: 서버가 보내는 알림(앱 알림·이메일·Slack·Mattermost·카카오톡)의 **겉모양(템플릿)** 과 **내용(문구)** 을 운영자가 고칠 수 있게 한다. 이벤트·필수 안내 메일마다 코드가 주는 기본 문구가 있고, 운영자는 사용자 문구를 만들어 채널·언어별로 「쓸 문구」와 「특수 상황 규칙」을 정한다. 지금은 문구가 코드(`AuthMailTemplate`·`OperatorMailTemplate`·`NotificationMailTemplate`·`ChannelMessageFormatter`)와 메시지 번들에 박혀 있다.
이 문서는 개정 1~4(2026-10-23, 모두 사용자 확정)를 합친 **확정본**이다. 대체된 옛 화면(메일 종류·메일 레이아웃·`CHAT` 채널·「알림 템플릿 = 내용」 용어)은 옛 주소 처리만 남기고 지웠다 — 경과는 git 이력.

## 결정
1. **용어**: **템플릿 = 겉모양(디자인 틀, 도구별)**, **문구 = 이벤트·채널별 제목과 본문**. 화면·코드·i18n 이 이 말을 쓴다(문구 `Message*`·`notificationMessage.*`, 템플릿 `…Template`·`notificationTemplate.*`).
2. 메일 본문은 간단 서식(`@roboyam/rich-editor` SIMPLE) + 변수 + 메일 템플릿. 변수 문법은 `{{변수}}` 만(조건·반복 없음).
3. 조건은 대상·채널마다 **코드가 정한 선택지만**(자유 조건식 없음).
4. **받는 사람은 메뉴로 나눈다** — 화면 안에서 회원/운영자를 고르는 선택을 두지 않는다(종류가 적은 필수 안내 메일만 한 화면 구획, 메일 템플릿은 왼쪽 위 영역).
5. 권한은 `template.read`(조회)·`template.manage`(편집·고르기·규칙) — 이름 「알림 템플릿·문구 조회·관리」. 이벤트 자체(사용·도구·팀)는 기존 `notification.read`·`notification.manage`.
6. 카카오톡은 **준비 중**(알림 도구에서 업체 계약 전) — 화면에 보이되 흐리게, 만들기·고르기 잠금, 서버도 409 `CHANNEL_PREPARING`.
## 서버 구현 결과(엔진·관리 API) — 2026-10-23
관리 API·데이터·렌더링 엔진까지. 실제 발송 경로는 다음 단계에서 이 엔진으로 바꿨다 — 아래 「서버 구현 결과(발송 연결)」.
- **저장(shared V57)**: `notification_mail_templates` · `notification_chat_templates`(tool SLACK|MATTERMOST) · `notification_kakao_templates`(표만, 행 없음) · `notification_messages`(사용자 문구) · `notification_message_selections`(대상×채널: `selected_ko|en` + `rules` jsonb `[{id,key,value,messageId}]`, 행 없으면 기본 문구·규칙 없음) · 이력 `notification_message_history` · `notification_mail_template_history` · `notification_chat_template_history`(설정 이력 모양, 바뀐 칸만). 템플릿·문구 참조는 글자 id(FK 없음 — 쓰는 중 검사는 서버).
- **기본(builtin)은 코드가 원천 — 조회 때 합성, DB 행 없음**: 문구 `builtin-<대상코드>-<채널>-<언어>`(내용 = 지금 번들 문구, `{0}` → 이름 변수 — `BuiltinMessages`), 템플릿 `mail-member`·`mail-operator`·`slack-default`·`mattermost-default`(`BuiltinTemplates`, 꼬리말·버튼 글자는 번들). 카카오톡은 기본 문구 없음.
- **대상 카탈로그(코드)** `MessageTargetCatalog`(shared `com.community.notificationtemplate.catalog`): 회원 이벤트 5(`MemberEvent` — **임시 정의, 통합 회원 이벤트 카탈로그가 생기면 그것으로 바꾼다**, 준비 중 5종은 문구 대상 아님) · 운영자 이벤트 22(`AdminNotificationEvent` 전부, `broadcastAllowed=false` 는 Slack·Mattermost 없음) · 필수 안내 메일 5(`RequiredMail`). 변수 설명·견본은 번들 `notificationTemplate.var.<id>.desc|sample`, 조건 `notificationTemplate.condition.*`(jobName 선택지 = 배치 카탈로그, inviteRole = 운영자 역할 DB).
- **API**: 목업 머리말 그대로 — `/api/admin/notification-messages/{targets[/{type}/{code}], messages[/{id}[/history]], preview, test, chat-test}`, `/api/admin/notification-templates/{mail|slack|mattermost}[/{id}[/history]]`, `/kakao[/{id}]`(읽기). 권한 `template.read`(읽기·미리보기)·`template.manage`(쓰기·시험). 감사 `NOTIFICATION_MESSAGE_CREATE|UPDATE|DELETE|TARGET_UPDATE|TEST` · `NOTIFICATION_TEMPLATE_CREATE|UPDATE|DELETE`(대상 종류 `NOTIFICATION_MESSAGE` · `NOTIFICATION_MESSAGE_TARGET` · `NOTIFICATION_TEMPLATE`).
- **메뉴**: `AdminScreenRegistry` 를 admin-web `UtilAdminMenu` 와 맞췄다 — 시스템(일반·소셜 로그인·조직) / 메일(메일 서버) / 알림·이벤트(알림 도구 · 폴더 템플릿(메일·Slack·Mattermost·카카오톡) · 폴더 문구(운영자·회원·필수 안내 메일) · 폴더 이벤트(`operatorEvents`·`memberEvents`)). 옛 키 `notifications` 는 없앴다.
- **엔진(다음 단계가 쓸 것)** — shared `NotificationMessageRenderer`(빈):
  - `RenderedInApp renderInApp(MessageRenderRequest)` → 제목·본문 평문 · `RenderedEmail renderEmail(…)` → `subject`(`[사이트] 제목`)·`untaggedSubject`·`html` → `MailDispatcher.sendTagged(to, subject, untaggedSubject, html)` · `RenderedChat renderChat(…)` → `NotificationChannelPoster.post(channelEntity, rendered)`(Slack `attachments`[blocks], Mattermost 웹훅 `attachments` / 봇 `props.attachments`) · `MessageValues pick(…)`.
  - 요청: `MessageRenderRequest.event(AdminNotificationEvent, MessageChannel)` / `.memberEvent(MemberEvent, channel)` / `.requiredMail(RequiredMail)` `.locale(Locale) .variable(name, value) .condition(key, values…) .severity(…) .occurredAt(Instant) .zone(ZoneId) .build()`. 예: `renderer.renderEmail(MessageRenderRequest.requiredMail(RequiredMail.MEMBER_EMAIL_VERIFY).locale(locale).variable("nickname", nick).variable("verifyLink", link).variable("validHours", 24).condition(MessageCondition.SIGNUP_METHOD, MessageCondition.SOCIAL).build())`.
  - 고르기: 규칙(위에서부터, 조건 값이 맞는 첫 규칙 — 언어 무관) → 받는 사람 언어의 「쓸 문구」(ko·en 밖이면 사이트 표시 기본 언어 → ko) → 그 문구가 없어졌으면 그 언어 기본 → ko 기본. 템플릿이 없어졌으면 받는 사람·종류의 기본 템플릿.
  - 변수: `siteName`·`siteUrl`(site_settings.public_url)·`adminUrl`(`ADMIN_URL`)은 엔진이 채운다. 이메일·Slack·Mattermost 의 `title`·`body` 를 주지 않으면 같은 요청으로 앱 알림 문구를 먼저 그려 채운다. `occurredAt` 을 안 주고 `occurredAt(Instant)` 을 주면 사이트 시간대 `yyyy-MM-dd HH:mm`. 값 가리기 — 이메일 HTML 이스케이프(링크 변수는 버튼 href 로만, http·https 아니면 `#`), Slack·Mattermost `ChannelMessageFormatter` 무력화(링크 변수는 구문 인코딩만), 앱 알림 평문. 발송에서 값이 없는 변수는 지운다.
  - 캐시: `MessageTemplateStore` 15초 JDBC 사본(세이브포인트 안에서 불러도 업무 트랜잭션을 깨지 않는다), 관리 API 저장 커밋 뒤 그 서버는 바로 비운다. 카탈로그에 없는 대상·채널·준비 중 채널은 `IllegalArgumentException`.
- **가정**: 상한(사용자 문구 대상·채널당 20 · 템플릿 종류당 20 · 규칙 10) · 길이(이름 50, 메일 제목 200·본문 20000·버튼 40, 앱 알림 100/300, 채팅 200/1500, 틀 메일 20000·채팅 500, 꼬리말 500, 버튼 글자 20) 목업 값. 이메일 본문·메일 틀 정화 = `MailHtml`(OWASP, 게시판 간단 허용 목록 + `p[data-mail-button]`, href 는 http·https·변수 하나). Mattermost 「열기」는 버튼이 아니라 본문 끝 링크(첨부 버튼은 연동 주소가 필요). Slack 보낸 시각은 `<!date^…>`, Mattermost 는 사이트 시간대 글자. 채팅 시험 429 는 목업대로 `MAIL_TEST_RATE_LIMITED`(한도는 알림 도구 연결 시험과 같은 창 — 분당 6). 메일 로고는 파일이면 공개 주소 붙인 절대 주소, SVG 면 data URI(메일 앱이 안 보일 수 있다).
- **미해결**: ① 저장된 관리자 메뉴 구성(admin 스트림 `admin_menu`)에 옛 화면 키 `notifications` 가 있으면 다음 저장이 `adminMenu.screen.unknown` 으로 거절된다 — 운영자가 메뉴 관리에서 그 줄을 지우거나 admin 스트림 정리 마이그레이션이 필요 ② 운영자가 쓴 Slack·Mattermost 문구·틀의 `@here` 같은 멘션은 무력화하지 않는다(값만) ③ Mattermost 첨부 `title` 이 마크다운을 그리는지(무력화 역슬래시가 보일 수 있음) 실제 서버 확인 필요 ④ 카카오톡 등록·검수 흐름(미확정 12) ⑤ ~~발송 경로 교체~~ — 「서버 구현 결과(발송 연결)」.

## 화면 (설정 > 알림·이벤트, 차례)
| 메뉴 | 경로 | 내용 |
|---|---|---|
| 알림 도구 | `/settings/notification-channels` | 기존 그대로(`notification-contract.md`) |
| 템플릿(부모 → 메일) | `/settings/notification-templates/{mail\|slack\|mattermost\|kakao}` | 유형별 목록 → 편집 `/{유형}/[id]`, 새로 `/{유형}/new`, 복사 `?from=` |
| 문구(부모 → 운영자) | `/settings/notification-messages/{operator\|member}` · `/required-mails` | 받는 사람별 문구 목록 → 편집 `/settings/notification-messages/[id]`(새로 `new?audience=&target=&channel=`) · 필수 안내 메일 목록 → 상세 `/required-mails/[kind]` |
| 이벤트(부모) | `/settings/notifications/{operator\|member}` | 운영자 이벤트(기존 화면, 실제 서버) · 회원 이벤트(새로) → 상세 `/settings/notifications/[audience]/[code]` |
- **메뉴 키**(서버 `AdminScreenRegistry` 를 서버 단계에서 맞춘다 — 지금은 옛 키 `notifications`): `notificationChannels` · `notificationTemplates` › `mailTemplates`·`slackTemplates`·`mattermostTemplates`·`kakaoTemplates` · `notificationMessages` › `operatorMessages`·`memberMessages`·`requiredMails` · `operatorEvents`·`memberEvents`. 문구 편집은 어느 하위에도 속하지 않아 부모 「문구」만 활성·부모 권한, 템플릿 편집은 그 유형 하위.
- **옛 주소는 새 화면으로 보낸다**: `/settings/mail-kinds[/{kind}]`(계정·보안 메일 코드 → 필수 안내 메일 상세, 알림 이벤트 코드 → 운영자 이벤트 상세) · `/settings/mail-templates[/{id}]` · `/settings/mail-layouts[/{id}]` · `/settings/notification-templates/templates/**`(→ 문구, `channel=CHAT` → `SLACK`, `layout=` → `template=`, `kind=` → `target=`) · `…/layouts/**`(→ `…/mail/**`) · `…/notification-templates/required-mails/**`(→ 문구 › 필수 안내 메일) · `/settings/notifications`(→ 운영자 이벤트).

## 채널 · 받는 사람 · 대상
| 채널 | 쓰이는 곳 | 템플릿 |
|---|---|---|
| `IN_APP` 앱 알림 | 관리자 종 서랍(운영자) · 회원 알림함(회원) | 없음(화면 모양이 정해져 있다) |
| `EMAIL` 이메일 | 메일 | 메일 템플릿 |
| `SLACK` · `MATTERMOST` | 알림 도구 글 | 그 앱의 템플릿(표기법·첨부 모양이 달라 나눈다) |
| `KAKAO` 카카오톡(준비 중) | 알림톡 | 카카오에 등록·승인된 알림톡 템플릿 |
- **대상** = 이벤트 또는 필수 안내 메일. 문구 하나 = 대상 × 채널 × 언어(ko·en). 대상 코드는 종류를 넘어 겹치지 않는다(가정 — 겹칠 수 있으면 `type:code`).
- 운영자 이벤트(서버 `AdminNotificationEvent` 22종, 준비 중 포함) = 다섯 채널, 단 본인에게만 가는 4종(`*_ME`)은 Slack·Mattermost 없음. 회원 이벤트 = 앱 알림·이메일·카카오톡. 필수 안내 메일 = 이메일뿐(받는 사람이 끌 수 없음).
- **필수 안내 메일**(지금 5종, 코드 그대로): 회원 이메일 인증 `MEMBER_EMAIL_VERIFY` · 운영자 초대 `OPERATOR_INVITE` · 임시 비밀번호 `OPERATOR_CREDENTIALS` · 재설정 승인번호 `OPERATOR_RESET_CODE` · 메일 서버 시험 `MAIL_TEST`. 앞의 4종은 **보안 메일**(필수 변수를 지우면 저장 안 됨).
- **회원 이벤트 통합 목록** — 실제 5종(`MemberNotificationType`) + 게시판 기본 설정 「알림」(`BoardMemberNotifyKind`)에만 있는 5종은 「준비 중」(흐리게, 문구 대상 아님):

  | 회원 이벤트(코드) | 지금 `MemberNotificationType` | 게시판 기본 설정 종류 | 상태 | 회원이 끌 수 있음 |
  |---|---|---|---|---|
  | 내 글에 댓글 `COMMENT` | `COMMENT` | `COMMENT_ON_POST` | 사용 | 운영자가 정함 |
  | 내 댓글에 답글 `REPLY` | `REPLY` | `REPLY_ON_COMMENT` | 사용 | 운영자가 정함 |
  | 포인트 회수 `POINTS_REVOKED` | `POINTS_REVOKED` | — | 사용 | 잠금(계정 알림) |
  | 내 글 숨김 `POST_HIDDEN` | `POST_HIDDEN` | — | 사용 | 잠금 |
  | 내 댓글 숨김 `COMMENT_HIDDEN` | `COMMENT_HIDDEN` | — | 사용 | 잠금 |
  | 멘션 `MENTION` | — | `MENTION` | 준비 중 | 운영자가 정함 |
  | 인기글 `POST_POPULAR` | — | `POST_POPULAR` | 준비 중 | 운영자가 정함 |
  | 운영 처리 `OPERATOR_ACTION` | — | `OPERATOR_ACTION` | 준비 중 | 잠금(처리 알림) |
  | 스텝·매니저 처리 `STAFF_ACTION` | — | `STAFF_ACTION` | 준비 중 | 잠금 |
  | 신고 처리 결과 `REPORT_RESULT` | — | `REPORT_RESULT` | 준비 중 | 운영자가 정함 |

## 템플릿 (겉모양)
- **메일 템플릿**(목록: 왼쪽 위 영역에서 쓰는 대상 「운영자용·회원용·모두」(운영자 먼저, 주소 `?audience=`)를 고르면 그 표만 — 이름+띠 색 점 · 기본/사용자 · 쓰는 문구 수(받는 사람별 문구 목록 링크, 「모두」는 운영자·회원 따로) · 마지막 수정 · 복사·삭제).
  - 칸: 이름(50자) · 쓰는 대상 `MEMBER|OPERATOR|ANY` · 로고 보이기 · 사이트 이름 보이기 · 머리말 띠 색(팔레트 8색 `GREEN·BLUE·TEAL·PURPLE·AMBER·ORANGE·RED·GRAY` 또는 없음 — 색이 있으면 머리말을 띠 안 흰 글씨로) · 꼬리말 ko·en(평문 500자, 공통 변수만) · 틀 ko·en(간단 서식, `{{content}}` 정확히 한 번).
  - 기본 2개(builtin, 고치기·지우기 불가, 복사로 만든다): 「회원 기본」(`mail-member` — 꼬리말 「{{siteName}}에서 보낸 발신 전용 메일」+`{{siteUrl}}`) · 「운영자 기본」(`mail-operator` — 관리 사이트 안내 + `{{adminUrl}}`). 새 문구는 받는 사람의 기본 템플릿으로 시작한다.
  - 쓰는 대상은 그 템플릿을 쓰는 문구의 받는 사람과 맞지 않게 바꿀 수 없다(`fields.audience`). `ANY` 는 공통 변수 중 `siteName`·`recipientName` 만.
- **Slack · Mattermost 템플릿**(한 화면 구조, 종류마다 기본 1개): 이름 · 심각도 그림 보이기 · 제목 굵게 · 심각도별 색 막대 `colors{INFO,WARNING,CRITICAL}`(같은 팔레트) · 사이트 이름 보이기 · 보낸 시각 보이기 · 「열기」 버튼 보이기 + 글자 ko·en(보일 때 필수, 20자) · 틀 ko·en(그 앱 표기, `{{content}}` 한 번, 500자, 변수는 `content`·운영자 공통 변수). 「열기」 주소는 이벤트의 `{{link}}`. 기본값 = 지금 `ChannelMessageFormatter` 모양(그림·굵게·안내 파랑/주의 호박/긴급 빨강·「열기」).
- **카카오톡 템플릿**(준비 중 — 목록·보기만): 알림톡 템플릿 코드 · 상태 `REQUESTED|APPROVED|REJECTED` · 승인 본문(읽기 전용) · 강조 · 변수 대응표(`#{이름}` ↔ 로보얌 변수, 견본) · 버튼(이름·링크 변수) · 채널 이름.
- 공통: 쓰는 문구가 있으면 삭제 409 `TEMPLATE_IN_USE`(단추도 잠금), 기본 템플릿 수정·삭제 409 `TEMPLATE_BUILTIN`, 유형별 사용자 템플릿 20개 409 `TEMPLATE_LIMIT`.

## 문구 (내용)
- **목록**(운영자 · 회원 — 같은 조각): 왼쪽 위 채널 영역(전체 + 그 받는 사람의 채널, 카카오톡은 「준비 중」 딱지·흐리게 — 고르면 안내 + 새로 만들기 잠금). 열: 이름(쓰는 중 딱지) · 채널 · 대상(이름 + 운영자 이벤트·영역 / 회원 이벤트 / 필수 안내 메일, 보안 메일 자물쇠, 대상 화면 링크) · 언어 · 템플릿(앱 알림 「—」, 편집 링크) · 기본/사용자 · 마지막 수정 · 관리. 거르기: 검색 · 대상 종류 · 대상 · 언어 · 기본/사용자 · 템플릿(주소 `?channel=&targetType=&target=&template=`). 50줄씩.
- **편집** 공통 칸: 이름(필수 50자) · 대상·채널(새로 만들 때만 — 대상·채널을 바꾸면 그 기본 내용으로 다시 채우고, 고친 내용이 있으면 묻는다) · 받는 사람(읽기 전용) · 언어 · 변수 넣기 · 기본으로 되돌리기 · 변경 이력 서랍. 만든 뒤에는 대상·채널을 바꿀 수 없다. 그 언어의 「쓸 문구」인 사용자 문구는 언어를 못 바꾼다(`fields.locale`).
  | 채널 | 칸 | 미리보기 · 시험 |
  |---|---|---|
  | 앱 알림 | 제목 한 줄 100자 · 본문 평문 300자(비어도 됨) | 서랍·알림함 한 줄 모양 · 시험 없음 |
  | 이메일 | 템플릿(받는 사람 맞는 메일 템플릿, 저장 `templateId`) · 제목 200자 한 줄 · 본문 간단 서식 20000자 · 버튼 블록 1개(글자 40자, 링크 변수만) | 서버가 견본 값·템플릿을 씌운 문서 → `iframe srcdoc` + 빈 `sandbox`(400ms 늦춰 다시 받음) · 「나에게 시험 발송」(메일 설정 시험과 같은 부품·한도) |
  | Slack · Mattermost | 템플릿(그 종류) · 제목 한 줄 200자 · 본문 1500자(그 앱 표기, 비어도 됨 — 템플릿의 `{{content}}` 자리) | 그 앱 메시지 카드 모양(색 막대·그림은 대상의 심각도) · 「알림 도구로 시험」 — 실제 알림 도구 중 **그 종류**만, 꺼진 도구 잠금 |
  | 카카오톡 | 알림톡 템플릿만(제목·본문 없음, 승인 본문 그대로) — 전부 읽기 전용 | 말풍선 모양(대상의 견본 값) |
  - 기본 문구: 이메일 = 지금 메일 문구(필수 안내 메일 `mail.*`, 운영자 이벤트 `notification.mail.*`, 회원 이벤트는 같은 꼴의 새 문구 「사이트에서 보기」), 앱 알림 = 지금 알림 문구(번들의 `{0}` 자리는 이름 붙은 변수로), Slack·Mattermost = `{{title}}` / `{{body}}`(겉모양은 기본 템플릿이 맡는다), 카카오톡 = 없음. 이메일·Slack·Mattermost 의 `{{title}}`·`{{body}}` 는 그 이벤트의 앱 알림 문구 결과(가정 — 미확정 8).
  - 메일 본문 저장 모양: 간단 서식 HTML + `{{변수}}`, 버튼은 `<p data-mail-button=""><a href="{{링크 변수}}">글자</a></p>` 하나(에디터 안에서는 `{{button}}` 한 줄). 제목의 `[사이트 이름]` 머리말은 보낼 때 붙인다(미리보기는 붙여 보인다). 값은 자동 HTML 이스케이프, 직접 쓴 링크는 http·https 만(그 밖은 저장 때 걷는다).
- **검사(서버도 같은 규칙)**: 모르는 변수 · 보안 메일 필수 변수가 제목·본문·버튼 어디에도 없음 · 버튼 2개 이상·링크 변수 아닌 버튼 · 다른 받는 사람의 공통 변수(회원 쪽 `{{adminUrl}}`, 운영자 쪽 `{{siteUrl}}` — 문제 코드 `AUDIENCE_VARIABLE`) · 길이 · 제목 한 줄 · 받는 사람에 맞지 않거나 없는 템플릿 → 400 `fields.name|channel|locale|templateId|subject|body`. 사용자 문구는 대상·채널당 20개(409 `MESSAGE_LIMIT`), 쓰는 중(언어별 쓸 문구·규칙) 삭제 409 `MESSAGE_IN_USE`, 기본 문구 수정·삭제 409 `MESSAGE_BUILTIN`.
- **새로 만들기·복사**: 편집 화면을 새 문구로 열어 [저장] 때 만든다. 이름 기본값: 새로 빈칸, 복사 「원본 (복사)」. 저장하지 않은 변경이 있는 채 떠나면 묻는다.

## 쓸 문구 · 특수 상황 규칙 (이벤트 상세 · 필수 안내 메일 상세)
- 채널마다 구획: ① 언어별(ko·en) 쓸 문구 ② 특수 상황 규칙 — 위에서부터 보고 맞는 첫 규칙의 문구, 없으면 ①. 규칙 = 조건(코드가 정한 선택지) + 문구(언어와 상관없이 고른다), 10개까지, 같은 조건이 위에 있으면 「쓰이지 않음」 경고만. ③ 「이 채널의 문구 보기」·「새 문구」. 채널 전부를 한 번에 저장(`StickySaveBar`).
- 그 채널로 지금 보내지 않으면 안내 한 줄(목록에서 꺼짐 · 이메일은 서버 단계에서 열림 · Slack·Mattermost 는 그 종류 도구를 골랐을 때만) — 문구는 미리 정해 둘 수 있다. 카카오톡 구획은 흐리게·안내·「문구 보기」만(저장에 넣지 않는다).
- **조건 선택지(목업 — 최종 목록은 서버 단계)**: 운영자 이벤트 이메일 `recipientSource`(기본 수신 규칙 / 추가 수신 팀) · 배치 실패의 이메일·Slack·Mattermost `jobName` · 회원 댓글 `grouped`(댓글 하나 / 묶인 댓글) · 글·댓글 숨김 `hideReason`(신고 누적 / 권리 침해 임시 숨김) · 인증 메일 `signupMethod`(이메일 / 소셜) · 초대 메일 `inviteRole`. 임시 비밀번호·승인번호·메일 시험은 조건 없음.
- 지금 코드의 특수 문구(묶인 댓글 「…님 외 N개」, 권리 침해 임시 숨김)는 목업에서 사용자 문구 + 규칙으로 넣어 두었다(미확정 6).

## 이벤트 화면
- **운영자 이벤트 목록**: 실제 API 그대로(사용·전달 도구·추가 수신 팀 즉시 저장) + 「문구」 칸(채널마다 한국어 쓸 문구, 영어가 다르면 「· EN …」, 규칙 수 — `template.read` 없으면 감춤) + 「상세」. 서버 `emailable=false` 인 이벤트의 이메일은 「서버 단계에서 열려요」(서버 단계에서 준비 중이 아닌 이벤트 모두로 넓힌다).
- **회원 이벤트 목록**: 영역별 표 — 사용 · 앱 알림 · 이메일(처음엔 모두 꺼짐) · 회원 끄기(스위치 즉시 저장, 낙관적 갱신, 실패하면 그 줄만 되돌림) · 문구 요약 · 상세. 맨 위 안내: 게시판 기본 설정 › 알림의 이벤트별 칸은 서버 단계에서 이 화면으로 옮긴다(그 탭은 지금 건드리지 않음). 권한 `notification.read`·`notification.manage`.
- **이벤트 상세**: ① 이벤트 구획은 **보여 주기만**(설명·받는 사람·수신 규칙·현재 설정 — 바꾸기는 목록에서) ② 위 「쓸 문구 · 특수 상황 규칙」. 문구 고르기는 `template.manage`.

## 변수
- 대상(이벤트·필수 안내 메일)마다 코드가 정한다(이름·설명·견본 값·필수 여부 — 서버가 요청 언어로). 받는 사람별 공통 변수: 회원 `siteName`·`siteUrl`(공개 사이트)·`recipientName`, 운영자 `siteName`·`adminUrl`(관리 사이트)·`recipientName`. 예: 인증 `nickname`·`verifyLink`(필수)·`validHours`, 초대 `inviteLink`(필수)·`validHours`, 임시 비밀번호 `operatorName`·`temporaryPassword`(필수), 승인번호 `code`(필수)·`validMinutes`, 운영자 이벤트 `title`·`body`·`link`·`occurredAt`·`jobName`·`reason`·`operatorName`·`change`·`changedItems`·`count`, 회원 이벤트 `actorName`·`excerpt`·`postTitle`·`amount`·`reason`·`otherCount`.
- 변수 메뉴·미리보기 견본 값은 받는 사람을 따른다(회원 `https://roboyam.example.com`, 운영자 `https://admin.roboyam.example.com`). Slack·Mattermost 로 올릴 때 변수 값의 멘션·표기는 지금처럼 서버가 무력화한다.

## 변경 이력
- 문구·템플릿 편집 화면 제목 옆 「변경 이력」 → `SettingHistoryDrawer`(설정 이력 모양 `{items, total, page, size}`, `labelKey` = `settingHistory.field.<칸>`).
  - 문구: `name|locale|templateId|subject|body`(템플릿은 이름, 본문은 글자 요약 80자).
  - 메일 템플릿: `name|audience|showLogo|showSiteName|headerColor|footerKo|footerEn|frameKo|frameEn`(켜고 끄기 `"true"/"false"`, 색·대상은 요청 언어 이름, 틀은 글자 요약).
  - Slack·Mattermost 템플릿: `name|showSeverityIcon|boldTitle|colorInfo|colorWarning|colorCritical|showSiteName|showSentAt|showOpenButton|openLabelKo|openLabelEn|frameKo|frameEn`.
- 쓸 문구·규칙 저장의 이력은 화면에 없다(감사 로그 후보).

## 권한
- 화면: 템플릿·문구 메뉴 `template.read`, 이벤트 메뉴 `notification.read`. `template.read` 만 있으면 템플릿·문구 화면은 읽기 전용(입력 잠금, 저장 줄·만들기·복사·삭제·시험 없음).
- `template.read`(READ)·`template.manage`(CONFIG)는 **프론트 카탈로그에만 있다** — 서버 `Permission`·`AdminScreenRegistry` 에 없어 지금은 최고 관리자에게만 메뉴가 보인다(서버 단계에서 추가).

## API 후보 (목업이 부르는 모양 — 서버 단계에서 확정)
- **문구** `/api/admin/notification-messages` (`useNotificationMessageApi`)
  - `GET targets?type=&audience=` → 대상 목록(받는 사람·채널(`preparing`)·변수·조건 선택지·언어별 쓸 문구·규칙 수·사용자 문구 수)
  - `GET/PUT targets/{type}/{code}` — 저장 `{ channels: { <채널>: { selected: {ko, en}, rules: [{id?, condition, messageId}] } } }`(보낸 채널만 바뀐다, 준비 중 채널은 보내지 않는다. `type` = `EVENT|REQUIRED_MAIL`, 카카오톡은 `defaults`·`selected` 가 null 일 수 있다). 오류 `fields.channels.<채널>.selected.ko|en`·`fields.channels.<채널>.rules[i].messageId`
  - `GET/POST/PUT/DELETE messages[/{id}]` — 요청 `{targetType, targetCode, channel, name, locale, templateId|null, subject, body}`(`PUT` 은 대상·채널을 바꾸지 못한다), 목록 줄은 본문 없이 · `GET messages/{id}/history`
  - `POST preview`(이메일 — `templateId` 또는 저장 전 `template`) → `{subject, html}` · `POST test` `{target: {type, code}, locale, templateId?, subject, bodyHtml, to}`(이메일, 결과·한도·429 는 메일 설정 시험과 같다) · `POST chat-test` `{target, channel: SLACK|MATTERMOST, locale, templateId, subject, body, channelId(그 종류의 알림 도구)}` → `{ok, message}`
- **템플릿** `/api/admin/notification-templates/{mail|slack|mattermost}[/{id}][/history]`(GET·POST·PUT·DELETE) · `/kakao[/{id}]`(GET 만). 목록 줄에 `messageCount`, 메일 템플릿 하나에 받는 사람별 `messageCounts: {MEMBER, OPERATOR}`·`logoUrl`·`siteName`·쓰는 대상별 `variables`.
- **회원 이벤트** `/api/admin/member-events` — `GET` · `PUT /{code}` `{enabled, inApp, email, memberCanTurnOff}`.
- 오류: 400 `VALIDATION`(위 칸들) · 404 `MESSAGE_NOT_FOUND`·`MESSAGE_TARGET_NOT_FOUND`·`TEMPLATE_NOT_FOUND` · 409 `MESSAGE_IN_USE`·`MESSAGE_BUILTIN`·`MESSAGE_LIMIT`·`TEMPLATE_IN_USE`·`TEMPLATE_BUILTIN`·`TEMPLATE_LIMIT`·`CHANNEL_PREPARING` · 403.
- 목업은 메모리(`composables/template/api/mock/notification-template-mock.ts` — 새로고침하면 처음 상태, `?mock=` 스위치 목록은 그 파일 머리말). 응답 언어로 풀 글(변수 설명·조건·이름·오류)은 목업이 한국어만 준다.

## 서버 단계에서 할 일
- `Permission` 에 `template.read`·`template.manage`, `AdminScreenRegistry` 메뉴 키를 위 표대로.
- 문구·템플릿·대상 설정 표와 발송 경로 교체(지금 코드 문구 → 기본 문구, 보낼 때 쓸 문구·규칙 해석, 템플릿 씌우기). 기본 문구·기본 템플릿은 코드가 준다(읽기 전용).
- 운영자 이벤트 `emailable` 확장, 회원 이벤트 이메일 발송, 게시판 기본 설정 「알림」의 이벤트별 칸을 회원 이벤트로 옮기기.
- 본문 칸 이름을 `body` 하나로 맞춘다 — 목업의 이메일 시험 요청만 아직 `bodyHtml` 이다.
- 「준비 중」 회원 이벤트는 문구 대상으로 주지 않는다 — 서버는 실제로 일어나는 것만 대상으로 준다.
- **다른 기능과 맞물림**: 커머스 메일(결제 확인·환불·성사·발송 — `commerce-foundation-contract.md` 결정 11)은 필수 안내 메일 목록에 들어갈 종류로 본다. 커머스 메일은 유실되지 않는 메일 대기열(`mail_outbox`)로 나가므로 서버 단계의 발송 경로 설계 때 대기열이 같은 문구 해석을 쓰게 맞춘다.

## 미확정
1. 조건 선택지의 대상별 최종 목록.
2. 사용자 문구·템플릿 수 상한(20 가정), 본문·꼬리말·틀 길이 상한.
3. 띠·색 막대 팔레트의 최종 색(8색 가정)과 심각도 그림 이름.
4. 회원 이벤트 이메일의 수신 동의·조용한 시간(게시판 기본 설정의 이메일 조용한 시간을 옮길지).
5. 브라우저 알림(운영자 도구 `browser`)이 앱 알림 문구를 같이 쓰는지(가정: 같이 쓴다).
6. 지금 코드의 특수 문구(묶인 댓글·임시 숨김)를 기본 규칙으로 줄지.
7. Slack·Mattermost 「열기」 버튼 주소가 늘 이벤트 `link` 인지.
8. 이메일·Slack·Mattermost 의 `{{title}}`·`{{body}}` 를 앱 알림 문구 결과로 둘지.
9. 대상 코드가 종류를 넘어 겹치지 않는다는 가정.
10. 카카오톡: 템플릿 등록·검수 흐름(화면에서 검수 요청 / 받아오기만), 대체 문자(SMS), 회원 수신 동의·야간 발송 제한.
11. Slack 굵게 표기가 한글 조사 바로 앞에서 풀리지 않는지(미리보기는 앞뒤가 빈칸·문장부호일 때만 굵게).

## 서버 구현 결과(회원 이벤트·이메일 확장) (2026-10-23)
문구·템플릿 엔진(`/api/admin/notification-messages`·`/notification-templates`, shared V57)은 이 절의 범위가 아니다.

### 회원 이벤트 카탈로그 (코드가 원천 — shared `com.community.membernotification.entity.MemberEvent`)
- 10종 = 실제 5종(`MemberNotificationType` 이름 그대로 — 사용 가능) + 준비 중 5종(`MENTION`·`POST_POPULAR`·`OPERATOR_ACTION`·`STAFF_ACTION`·`REPORT_RESULT`). 차례·영역·잠금·옛 칸 대응은 「목업 가정 — 개정 3」의 이름 대응 표 그대로(영역 `content`·`member`·`safety`, 잠금 `ACCOUNT` = 포인트 회수·글/댓글 숨김, `ACTION` = 운영·스텝 처리).
- 이름·설명·받는 사람은 번들 `member.event.<코드>.name|description|recipients`(ko·en, 관리자 기동 때 빠진 키 검사 — 알림 이벤트와 같은 규칙).
- 처음 값(설정 줄이 없을 때): 실제 = 사용·앱 알림 켬, 준비 중 = 사용 꺼짐·앱 알림 켬, 이메일 모두 꺼짐, 회원이 끌 수 있음 = 잠기지 않았으면 켬.
- **발행기**(`MemberNotificationPublisher`)는 이제 이 설정의 `enabled && inApp` 을 본다 — 5종 모두(포인트 회수·숨김도 운영자가 끄면 안 나간다). 예전에 보던 게시판 설정 `site` 는 읽지 않는다. **이메일 칸은 저장만**(회원 메일 발송은 다음 단계).

### 설정 표 · 데이터 이전 (shared `V58__member_event_settings.sql`)
- `member_event_settings(code PK, enabled, in_app, email, member_can_turn_off, updated_at, updated_by → operators ON DELETE SET NULL)`. V58 이 10종 줄을 모두 넣는다.
- 옛 칸 `board_notification_settings.member_defaults` → 이 표: `COMMENT ← COMMENT_ON_POST`, `REPLY ← REPLY_ON_COMMENT`, 준비 중 5종 ← 같은 이름. `in_app ← site`, `email ← email`, `member_can_turn_off ← memberCanTurnOff`(잠긴 이벤트는 false), **`enabled ← site OR email`**(둘 다 꺼져 있으면 나가는 것이 없었다 — 가정). 그 종류가 옛 칸에 없거나 값이 boolean 이 아니면 처음 값, 대응이 없는 3종(포인트 회수·숨김 2종)도 처음 값. 바꾼 시각 = 옛 행의 마지막 저장(없으면 V58 이 돈 시각), 바꾼 운영자 = 옛 행의 것(대응 없는 줄은 null).
- **옛 칸**: `member_defaults` 는 지우지 않는다(이미 배포 호환). 앱은 더 이상 읽지도 쓰지도 않는다(엔티티 매핑에서 뺐다 — 마지막 값이 그대로 남는다).

### API
- `GET /api/admin/member-events`(`notification.read`) → `MemberEventView[]` 카탈로그 차례(목업과 같은 차례·칸): `{code, area, name, description, recipientText, featureReady, enabled, inApp, email, memberCanTurnOff, lockReason: 'ACCOUNT'|'ACTION'|null, boardSettingKind: string|null, sentLast30Days}`. 잠긴 이벤트의 `memberCanTurnOff` 는 늘 false. `sentLast30Days` = 최근 30일 `member_notifications` 줄 수(묶인 댓글은 한 줄, 준비 중은 0).
- `PUT /api/admin/member-events/{code}`(`notification.manage`) `{enabled, inApp, email, memberCanTurnOff}`(네 칸 필수) → 200 바뀐 한 줄. 검사 순서: 404 `MEMBER_EVENT_NOT_FOUND` → 400 `VALIDATION` `fields.<빠진 칸>` → 409 `MEMBER_EVENT_NOT_READY` → 잠긴 이벤트에 `memberCanTurnOff: true` 400 `VALIDATION` `fields.memberCanTurnOff`(목업 규칙 그대로). 같은 이벤트 동시 저장은 행 잠금. 감사 `MEMBER_EVENT_UPDATE`(대상 `MEMBER_EVENT`, 바뀐 칸의 `before`/`after`).
- **게시판 기본 설정 「알림」** `GET/PUT /api/admin/boards/defaults/notifications`: 응답 `settings` 에서 `memberDefaults` 를 뺐다 — `{groupWindowMinutes, emailQuietHoursEnabled, emailQuietStart, emailQuietEnd, memberNotificationRetentionDays}`. 요청에 `memberDefaults` 가 와도 **무시**(검사도 하지 않는다 — `board.notify.rows` 400 은 더 이상 없다). 감사 `BOARD_DEFAULTS_UPDATE`(tab `notifications`)는 남은 칸만 — 예전 기록은 그대로.
- **운영자 이벤트 이메일**: `AdminNotificationEvent.emailable()` = **준비 중이 아니면 모두 true**(12종). 예외 없음 — 본인 전용·보안 이벤트(`*_ME`)도 받는 사람이 본인이라 허용(공유 채널·팀은 `broadcastAllowed` 가 그대로 막는다). 처음 이메일 전달 값은 예전 5개(`BATCH_RUN_FAILED`·`OPERATOR_LOCKED_BY_FAILURES`·`OPERATOR_STATUS_CHANGED_ME`·`PASSWORD_CHANGED_ME`·`PASSWORD_RESET_DONE_ME`)만 켬 — 이미 있는 DB 의 값은 바뀌지 않는다. 메일 양식은 지금 한 가지(`NotificationMailTemplate`) 그대로.
- `GET /api/admin/notification-events/{code}`(`notification.read`) 추가 — 목록 한 줄과 같은 모양(준비 중 포함), 모르는 코드 404 `NOTIFICATION_EVENT_NOT_FOUND`.

### 가정·미해결(다음 단계)
- 회원 이벤트 ~~이메일 발송·조용한 시간~~(「서버 구현 결과(발송 연결)」), **수신 동의**, 회원 개인 끄기(`memberCanTurnOff` 의 적용 지점 — 회원 설정 화면이 아직 없다).
- `enabled ← site OR email` 이전 규칙(가정). 준비 중 이벤트도 옛 칸 값을 옮겨 `enabled` 가 켜진 채일 수 있다(목업은 준비 중을 늘 꺼짐으로 그렸다 — 화면은 `featureReady=false` 면 「준비 중」으로만 보여 영향 없음).
- 프론트 후속: 게시판 기본 설정 「알림」 탭(`pages/boards/defaults/notifications.vue`)의 이벤트별 표·`memberDefaults` 타입을 빼고, 회원 이벤트 목업(`useMemberEventApi`)을 실제 API 로, 운영자 이벤트 화면의 「서버 단계에서 열려요」 안내·`emailable=false` 처리를 정리한다(서버는 이제 준비 중이 아닌 이벤트에 `emailable=false` 를 주지 않는다). 이벤트 상세는 목록에서 찾지 않고 `GET notification-events/{code}` 를 쓸 수 있다. → 아래 「프론트 연결 결과」에서 했다.

## 프론트 연결 결과 (2026-10-23)
- **목업 → 실제 API**: admin-web `useNotificationMessageApi`(대상·문구·미리보기·시험·채팅 시험·이력) · `useMailTemplateApi` · `useChatTemplateApi` · `useKakaoTemplateApi` · `useMemberEventApi` 가 `useApiClient` 로 서버를 부른다. 목업 파일(`template/api/mock/`, `notification/api/mock/`, `common/mock/mock-util.ts` — 다른 쓰는 곳 없음)·`?mock=` 스위치·`mockTemplateCan` 을 지웠다. 권한은 `useAdminPermission().can` 그대로. 목업 전용 메일 그리기(`UtilMailTemplate.render`·`cleanHrefs`)도 지웠다 — 미리보기는 서버 HTML 을 빈 `sandbox` iframe 으로만 보인다.
- **목업과 달라진 점 반영**: id 는 글자 그대로 쓴다(Snowflake · `builtin-…` · `mail-member` — 예시 id 를 가정한 코드는 없었다) · 채팅 시험 429 `MAIL_TEST_RATE_LIMITED` 는 「N초 뒤에」 문구 · Mattermost 미리보기 카드의 「열기」를 버튼 대신 본문 끝 링크로(`ChatMessageCard`) · 준비 중 회원 이벤트는 목록의 「쓰는 문구」가 「—」, 상세는 404 → 「아직 문구가 없어요」 안내 · 카카오톡 템플릿은 빈 목록(예시 문구 안내를 지웠다).
- **이벤트**: 운영자 이벤트 상세는 `GET notification-events/{code}`(404 → 「찾을 수 없어요」), 회원 이벤트 상세는 한 줄 조회가 없어 목록에서 찾는다. 「서버 단계에서 열려요」 문구·키를 지웠다(`emailable=false` 가 오면 일반 「이메일로 보내지 않아요」). 준비 중 회원 이벤트는 스위치를 잠근 채 옮긴 값 그대로 보이고, 안내를 「옮겨 온 값 · 기능이 생기면 바꿀 수 있어요」로 바꿨다.
- **게시판 기본 설정 「알림」**: 이벤트별 표와 `BoardNotificationSettings.memberDefaults`(+ `BoardMemberNotifyRow`·`BOARD_NOTIFY_LOCKED`·표 i18n)를 걷어 냈다. 남은 칸 = 묶는 시간 · 메일 조용한 시간 · 보관 일수. 맨 위 「회원 이벤트별 알림」 구획은 안내 + 「알림·이벤트 › 이벤트 › 회원」 링크(`notification.read` 가 있을 때, 없으면 안내만).
- **오류 표시**: 대상 저장 `fields.channels.<채널>.selected.ko|en`·`.rules[i].condition|messageId`·`.rules` → 그 채널 구획의 칸 아래, `channels.<채널>` 자체(준비 중·대상에 없는 채널)와 409 는 저장 줄 안 한 줄(이벤트 상세 · 필수 안내 메일 상세). 문구·템플릿 저장의 `fields.*` 는 칸 아래, 목록 한 줄 실패(삭제 409 등)는 토스트 8초.
- **권한 카탈로그**: `template.read`(READ)·`template.manage`(CONFIG) = 서버 `Permission.TEMPLATE_READ|MANAGE`(SITE_INTEGRATION, 준비됨)과 같다 — 「목업 후보」 주석을 정리했다.
- **확인(일회용 DB·9191 서버·nuxt 3273)**: 템플릿 4종 목록 · 메일/Mattermost 복사·저장·이력 · 메일 미리보기(서버 HTML) · 문구 목록 · 이메일 문구 복사·저장·이력 · 시험 발송(mailpit 수신) · Mattermost 문구에 사용자 템플릿 적용 미리보기 · 채팅 시험(가짜 웹훅 수신 — 「열기」 본문 끝 링크 확인) · 429 · 이벤트 상세 쓸 문구·규칙 저장 · 쓰는 중 문구 삭제 잠금 · 필수 안내 메일 규칙 저장 · 회원 이벤트 저장 · 준비 중 상세 · 게시판 알림 탭 저장(`memberDefaults` 없이) · `template.read`·`notification.read`·`board.read` 만 가진 운영자의 읽기 전용(쓰기 API 403).
- **서버에서 본 것(서버 확인 필요)**: ① 새로 설치한 DB 에서도 준비 중 회원 이벤트 5종이 `enabled=true`(운영 처리는 `email=true` 도) — 「처음 값: 준비 중 = 사용 꺼짐」과 다르다(V58 이 옛 게시판 기본값 줄을 옮긴 것으로 보임) ② 대상 저장 오류 문구 「카카오톡은(는)」 — 조사 처리.

## 서버 구현 결과(발송 연결) (2026-10-23)
모든 서버 알림·메일이 문구 엔진(`NotificationMessageRenderer`)을 지난다. API·DB 변경 없음(마이그레이션 없음). 옛 문구 클래스 `AuthMailTemplate`·`OperatorMailTemplate`·`NotificationMailTemplate`·`ChannelMessageFormatter.format` 은 지웠다(값 무력화 `neutralizeValue`·`linkValue`·`testMessage` 는 남김).

### 필수 안내 메일 5종
- 인증(`EmailVerificationService` · api) · 운영자 초대·임시 비밀번호(`OperatorMailSender`) · 재설정 승인번호(`PasswordResetMailSender`) · 메일 서버 시험(`MailTestService`) → `renderEmail(requiredMail(…))` + `MailDispatcher.sendTagged`(머리말·보내는 이름 규칙 그대로 — 시험 메일도 이제 `sendTagged`). 받는 사람 언어는 그대로(요청 언어 · 재설정은 요청 때 담은 언어).
- 조건: 인증 `signupMethod` = **비밀번호 없이 소셜로만 가입한 회원이면 `SOCIAL`, 아니면 `EMAIL`**(가입 직후뿐 아니라 나중의 다시 보내기도 같은 판단 — `IssuedVerification.social`). 초대 `inviteRole` = 초대받는 역할 id 들(`OperatorInvitationIssued.roleIds`, 하나라도 맞으면). 닉네임이 없으면 `nickname`·`recipientName` = 번들 `mail.verify.defaultName`.

### 운영자 이벤트 — 앱 알림은 **읽을 때 그린다**(지금 구조 유지)
- `admin_notifications` 는 예전처럼 인자(`args` — 글자·수·읽을 때 풀리는 문구 참조)만 저장하고, 서랍(`NotificationQueryService`)이 **읽는 사람 언어**로 인자를 푼 뒤 카탈로그의 자리 이름(`InAppText.argNames` — 예: 배치 실패 `jobName`·`reason`)으로 변수를 넘겨 `renderInApp` 한다(`AdminNotificationMessages`, shared). `recipientName` = 읽는 운영자 이름. 제목이 비면 이벤트 이름.
- **판단 근거**: ① 받는 사람마다 언어가 다르고 발행은 다른 사람의 요청에서 일어난다 — 보낼 때 그려 저장하면 수신자마다 언어를 알아내 행마다 다른 글을 써야 하고, 읽는 사람이 언어를 바꾸면 옛 알림은 옛 언어로 남는다 ② 배치 이름 같은 문구 참조는 읽을 때 풀려야 한다(지금 규약) ③ 운영자가 문구를 고치면 이미 쌓인 알림도 새 문구로 보인다(서랍은 「지금 무슨 일인지」 — 고친 문구가 옛 알림에 보이는 것은 받아들인다) ④ 행·마이그레이션 변경이 없다 ⑤ 엔진은 15초 메모리 사본만 읽어 목록당 비용이 작다(목록은 DB 읽기를 먼저 끝내고 그린다). 회원 알림함도 같은 판단.
- 이메일(`NotificationEmailSender`) · Slack/Mattermost(`NotificationChannelPostSender` → `renderChat` + `NotificationChannelPoster.post(channel, RenderedChat)`)는 커밋 뒤 스레드풀에서 받는 사람 언어(이메일 — 운영자 표시 설정 → 사이트 기본) / 사이트 표시 기본 언어(채팅)로 그린다. 채팅 카드는 도구 종류마다 한 번 그린다. `link` = `ADMIN_URL` + 이동 경로(없으면 버튼 없이), `occurredAt` = 발행 시각(사이트 시간대), 심각도 = 발행 때 값.
- **조건 값**: 발행 지점이 `NotificationContext.condition(key, values…)` 로 채운다(배치 실패 `jobName` = 배치 키 — `BatchRunNotifier`). 이메일의 `recipientSource` 는 발행기가 받는 사람마다: 기본 수신 규칙(+ 추가 수신자)으로 받으면 `DEFAULT`, 추가 수신 팀의 팀원이면 `TEAM`(**둘 다면 둘 다** — 위에서부터 맞는 첫 규칙). 이벤트의 「권한자 전원에게 보내기」를 끈 이벤트(`defaultRecipients=false`, `notification-contract.md` 「팀에만 보내기」)는 받는 사람이 모두 팀원이라 **`TEAM` 만**이고, 받을 팀원이 없어 기본 수신으로 되돌려 보낸 경우는 위와 같다.

### 회원 이벤트
- **앱 알림(알림함)**: 읽을 때 그린다(위와 같은 판단). api `MemberNotificationService` 가 지금처럼 읽을 때의 현재 값(닉네임·글 제목·댓글 앞부분)을 모아 `MemberNotificationMessages.Facts` 로 넘기면 변수 `actorName`·`postTitle`·`excerpt`·`otherCount`·`amount`·`reason`(포인트 회수 까닭 한 줄 — 번들 문장), 조건 `grouped`(`groupCount>1` → `GROUPED`)·`hideReason`(기간 있는 숨김 → `RIGHTS`, 아니면 `REPORTS`)으로 바꾼다.
- **기본 문구의 상황별 제목(미확정 6 의 가정)**: 기본 문구가 골라졌을 때만 지금 코드의 특수 제목 — 묶인 댓글 「…님 외 N개의 댓글」, 권리 침해 임시 숨김 — 을 번들 문구로 낸다(기본 규칙 행은 만들지 않았다). 사용자 문구·규칙이 골라지면 그 문구 그대로.
- **회원 이메일(새로)**: 설정 `enabled && email` 이면 발행기(`MemberNotificationPublisher`)가 세이브포인트 일의 마지막에 `MemberEmailRequested` 를 알리고, 커밋 뒤 `MemberNotificationEmailSender`(shared, 스레드풀 2·대기열 500, 가득 차면 버림)가 보낸다 — 실패·거절은 업무에 영향 없음. 앱 알림이 꺼져 있어도 메일은 나간다(사용 + 이메일만 본다).
  - 받는 사람: 이메일이 있고 **인증을 마친** 회원만, 탈퇴·삭제 제외. **정지 회원**(`SUSPENDED`·`BANNED`)은 활동 알림(댓글·답글 — 잠기지 않은 이벤트) 메일을 받지 않고, 계정 알림(포인트 회수·신고로 숨김 — `lockReason` 잠김)은 받는다(판단: 정지 중에는 활동에 답할 수 없지만 계정에 일어난 일은 알아야 한다).
  - 언어: 회원 표시 설정 언어(`member_display_preferences.language`), 없으면 사이트 표시 기본 언어. 메일의 `title`·`body` = 같은 언어로 그린 앱 알림 문구(상황별 제목 포함). `recipientName` = 받는 회원 닉네임. `link` = 사이트 공개 주소 + `/posts/{글}`(댓글·답글·댓글 숨김 — 알림함 링크와 같다, 없으면 버튼 없이).
  - **조용한 시간 가정**: 게시판 기본 설정 「알림」의 이메일 조용한 시간이 켜져 있고 지금(사이트 표시 시간대)이 `[시작, 끝)` 안이면 **그 메일은 보내지 않고 로그만** 남긴다(앱 알림은 남음) — 미뤄 보낼 대기열(`mail_outbox`)이 아직 코드에 없다(커머스 계약 결정 11 의 대기열이 생기면 미뤄 보내기로 바꾼다). 자정을 넘는 구간 가능, 시작 = 끝이면 조용한 시간 없음.
  - **묶인 댓글은 메일도 한 통**: 새 줄을 만들 때만 보내고, 묶음에 더해질 때(`addToGroup`)는 다시 보내지 않는다(판단: 묶음의 뜻이 「같은 글의 여러 댓글을 한 번 알림」이고, 묶음마다 메일이 나가면 묶는 의미가 없다). 앱 알림이 꺼져 있으면 묶을 줄이 없어 댓글마다 한 통.
  - 회원 개인 끄기(`memberCanTurnOff`)는 범위 밖 — 회원 쪽 설정 화면·표가 없다.
- `CommentNotice` 에 `postTitle`·`excerpt`(가린 뒤의 평문, 60자로 자름)를 더했다(메일 변수 — 알림함은 읽을 때의 현재 값).

### 엔진 쪽 보탬
- `MailRenderer`(실제 발송만): 링크 값이 없거나 http·https 가 아닌 버튼 블록은 「#」 단추로 남기지 않고 걷는다. 변수 하나만 든 문단(`<p>{{body}}</p>`·`<p><strong>{{title}}</strong></p>`)의 값이 비면 문단째 걷는다(운영자가 넣은 빈 문단은 둔다). 미리보기는 그대로(`#`·모르는 변수 보임).
- 캐시·세이브포인트: 발송 쪽은 엔진을 업무 트랜잭션 밖(커밋 뒤 스레드풀·읽기 API)에서만 부른다. 읽기 API 는 DB 읽기를 끝낸 뒤 그린다.

### 정리
- 번들 키 지움: `notification.mail.subject`·`mail.common.noReply`(ko·en — 쓰는 곳 없음). `mail.verify.*`·`mail.operator*`·`mail.test.*`·`notification.mail.open|reason`·`notification.channel.post.open`·`member.notification.*` 는 기본 문구·상황별 제목의 원천이라 남긴다.
- `mail_outbox`: 코드에 없어 만들지 않았다(커머스 메일 경로 없음).

### 검증(일회용 DB `rbverify_dispatch` = 개발 DB 복제, admin 9181 · api 9182 · 가짜 Slack/Mattermost 9185 · mailpit 1035)
- 필수 안내 메일 5종: 기본 문구 결과가 옛 메일과 같은 글(템플릿 머리말·꼬리말만 기본 템플릿 모양), 요청 언어(시험·재설정 en) · 사용자 문구로 바꾸면 그 결과 · 소셜(비밀번호 없음) 회원은 `signupMethod=SOCIAL` 규칙 문구 · `inviteRole` 규칙 · 제목 머리말·보내는 이름 규칙.
- 운영자 이벤트(배치 실패, 감시자 경로): 서랍 ko·en · 이메일 · Slack(`attachments`[blocks]) · Mattermost 웹훅 — 기본 문구·템플릿, 값 무력화(`@here`·`<b>`·`*`), 사용자 문구·Slack 사용자 템플릿·이메일 `jobName` 규칙.
- 회원 댓글: 이메일 꺼짐 → 메일 없음 · 켬 → 한 통, 묶인 댓글은 다시 안 보냄(앱 제목 「…외 1개」 ko·en) · 조용한 시간 → 메일 없음·앱 알림 있음 · 미인증·정지 → 메일 없음 · 회원 표시 언어 en → 영어 메일 · 사용자 앱 문구·묶음 규칙·회원 메일 문구.
- 실행하지 않음: 포인트 회수·글/댓글 숨김의 메일(같은 경로, 직접 호출 안 함), 실제 Slack·Mattermost 서버, 팀 수신(`recipientSource=TEAM`), 단위·전체 테스트·E2E.

