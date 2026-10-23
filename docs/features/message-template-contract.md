# 메일 템플릿 계약 (화면 목업 단계 — 2026-10-23)

**의도**: 서버가 보내는 메일의 제목·본문을 운영자가 고칠 수 있게 한다. 메일 종류(시스템 메일과 관리자 알림 이벤트)마다 코드가 주는 **기본 템플릿**이 있고, 운영자는 기본을 복사한 **사용자 템플릿**을 여러 개 만들어 종류마다 「기본으로 쓸 템플릿」과 「특수한 상황에서 쓸 템플릿(조건 규칙)」을 정한다. 지금은 문구가 코드(`AuthMailTemplate`·`OperatorMailTemplate`·`NotificationMailTemplate`)와 메시지 번들에 박혀 있어 바꿀 수 없다.
**결정(사용자 "추천대로", 2026-10-23)**: ① 본문은 단순 서식(간단 에디터 — `@roboyam/rich-editor` SIMPLE) + 변수 + 공통 레이아웃 ② 조건은 종류마다 코드가 정한 선택지만(자유 조건식 없음) ③ 1단계는 **이메일**만(Slack·Mattermost 글·회원 앱 알림 문구는 같은 구조로 2단계) ④ 새 권한 `template.read`(조회)·`template.manage`(편집·선택·규칙).

## 서버 구현 결과(엔진·관리 API) — 2026-10-23
관리 API·데이터·렌더링 엔진까지. **실제 발송 경로(인증·초대·임시 비밀번호·승인번호·시험 메일, 관리자 알림 서랍·이메일, Slack·Mattermost 게시, 회원 알림)는 아직 옛 코드 그대로**이고 다음 단계가 아래 엔진 인터페이스로 바꾼다.
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
- **미해결**: ① 저장된 관리자 메뉴 구성(admin 스트림 `admin_menu`)에 옛 화면 키 `notifications` 가 있으면 다음 저장이 `adminMenu.screen.unknown` 으로 거절된다 — 운영자가 메뉴 관리에서 그 줄을 지우거나 admin 스트림 정리 마이그레이션이 필요 ② 운영자가 쓴 Slack·Mattermost 문구·틀의 `@here` 같은 멘션은 무력화하지 않는다(값만) ③ Mattermost 첨부 `title` 이 마크다운을 그리는지(무력화 역슬래시가 보일 수 있음) 실제 서버 확인 필요 ④ 카카오톡 등록·검수 흐름(미확정 12) ⑤ 발송 경로 교체(다음 단계).

## 개정 4 — 용어 바로잡기·도구별 템플릿 (2026-10-23, 사용자 확정 — 개정 3의 용어·메뉴를 대체한다)
- **용어**: **템플릿 = 겉모양(디자인 틀)**, **문구 = 이벤트·채널별 제목과 본문**. 개정 3까지 「템플릿」이라 부르던 것은 이제 「문구」, 「메일 레이아웃」은 「메일 템플릿」이다.
- **채널**: `IN_APP`(앱 알림) · `EMAIL`(이메일) · `SLACK` · `MATTERMOST` · `KAKAO`(카카오톡 — 준비 중). 개정 3의 `CHAT`(Slack·Mattermost 묶음)은 둘로 나눈다(표기법·첨부 모양이 다르다).
- **템플릿은 도구별**이고 **설정 칸이 유형마다 다르다** — 앱 알림은 화면 모양이 정해져 템플릿이 없다.
  | 유형 | 템플릿이 정하는 것 |
  |---|---|
  | 메일 | 이름 · 쓰는 대상(회원/운영자/모두) · 로고·사이트 이름 보이기 · 머리말 띠 색 · 언어별 틀(`{{content}}` 1번) · 꼬리말 (개정 2의 메일 레이아웃 그대로) |
  | Slack | 이름 · 제목 줄 모양(심각도 아이콘 보이기, 굵게) · 심각도별 색 막대(첨부 color) · 꼬리말(사이트 이름·보낸 시각 보이기) · 「열기」 버튼 보이기·문구 · 본문 자리 `{{content}}` |
  | Mattermost | Slack과 같은 항목이되 Mattermost 첨부 모양(`attachments` — title/text/color/footer)과 표기법 |
  | 카카오톡(준비 중) | 카카오에 등록·승인된 **알림톡 템플릿 코드** · 승인된 본문(읽기 전용 표시) · 변수 대응표(`#{이름}` ↔ 로보얌 변수) · 버튼(이름·링크 변수). 알림 도구에서 카카오가 준비 중이라 화면은 흐리게(만들기 막음), 미리보기는 알림톡 말풍선 모양 |
- **미리보기**: 템플릿 편집 화면은 견본 문구를 넣은 미리보기(도구 모양 — 메일은 격리 iframe, Slack·Mattermost는 그 앱의 메시지 카드 모양, 카카오톡은 알림톡 말풍선). **문구 편집 화면은 고른 템플릿에 그 문구를 적용한 미리보기**(앱 알림은 서랍·알림함 한 줄 모양).
- **문구**는 채널마다 그 채널의 템플릿을 하나 고른다(앱 알림 제외). 받는 사람별 기본 템플릿(메일은 회원 기본·운영자 기본, Slack·Mattermost는 기본 하나씩).

### 메뉴 (설정 > 알림·이벤트, 차례)
1. **알림 도구** `/settings/notification-channels`
2. **템플릿** (부모) `/settings/notification-templates` → **메일** `/settings/notification-templates/mail` · **Slack** `/slack` · **Mattermost** `/mattermost` · **카카오톡** `/kakao`(준비 중) — 각 목록 → 편집 `/{type}/[id]`, 새로 `/{type}/new`, 복사 `?from=`
3. **문구** (부모) `/settings/notification-messages` → **운영자** `/operator` · **회원** `/member` · **필수 안내 메일** `/required-mails` — 문구 목록(왼쪽 위 채널 영역 유지) → 편집 `/settings/notification-messages/[id]`(새로 `new?audience=&target=&channel=`)
4. **이벤트** (부모) → 운영자 · 회원 (개정 3 그대로)
- 옛 주소(`/settings/notification-templates/templates/**`, `/layouts/**`, `/required-mails/**`)는 새 주소로 보낸다.

### 목업 가정 — 개정 4 (화면 목업 2026-10-23, 사용자 확인 전 — 개정 3 가정 중 이름·채널·메뉴·주소에 관한 것은 이것이 대체하고, 받는 사람·대상·규칙·검사·메일 템플릿(옛 레이아웃) 칸 규칙은 그대로)
- **이름(코드)**: 문구 = `Message*`(`useNotificationMessageApi` · `MessageView` · `NotificationMessageList` · `MessageChannelSelection` · `MessageUsageSummary` · `useNotificationMessageList` · `UtilNotificationMessage`), 템플릿 = `…Template`(`useMailTemplateApi`(옛 `useMailLayoutApi`) · `useChatTemplateApi` · `useKakaoTemplateApi` · `UtilChatTemplate` · `UtilKakaoTemplate`). i18n 은 `notificationMessage.*`(문구 화면) · `notificationTemplate.*`(템플릿 화면 + 두 쪽 공통 말). 변수 메뉴 `NotificationVariableMenu`, 미리보기 조각 `MailTemplatePreviewFrame` · `ChatMessageCard`(Slack·Mattermost 카드 — 두 앱 모양을 가른다) · `KakaoMessageBubble`.
- **메뉴 키**: `notificationTemplates`(부모 「템플릿」, `/settings/notification-templates` → 메일) › `mailTemplates` · `slackTemplates` · `mattermostTemplates` · `kakaoTemplates` / `notificationMessages`(부모 「문구」, `/settings/notification-messages` → 운영자) › `operatorMessages` · `memberMessages` · `requiredMails`. 문구 편집 `/settings/notification-messages/{id}` 는 어느 하위에도 속하지 않아 부모 「문구」만 활성·부모 권한(`UtilAdminMenu.parents`). 템플릿 편집 `/{유형}/{id}` 는 그 유형 하위. 카카오톡 메뉴는 준비 중이지만 계약대로 보인다. 권한은 그대로 `template.read`·`template.manage`(이름을 「알림 템플릿·문구 조회·관리」로).
- **옛 주소**: `…/templates[?audience=]`·`/templates/operator|member` → 그 받는 사람의 문구 목록, `…/templates/{id}|new` → `/settings/notification-messages/{id}|new`(쿼리 `channel=CHAT` → `SLACK`, `layout=` → `template=`), `…/layouts[/{id}]` → `…/mail[/{id}]`, `…/required-mails[/{kind}]` → `/settings/notification-messages/required-mails[/{kind}]`, 그 이전 `/settings/mail-layouts|mail-templates|mail-kinds` 도 새 주소로 바로(`kind=` → `target=`).
- **채널**: `IN_APP · EMAIL · SLACK · MATTERMOST · KAKAO`. 받는 사람별 — 운영자 이벤트 = 다섯(본인에게만 가는 4종은 Slack·Mattermost 없음), 회원 이벤트 = 앱 알림·이메일·카카오톡, 필수 안내 메일 = 이메일. 대상 요약 채널에 `preparing`(카카오톡 = true). 카카오톡은 기본 문구가 없고(`defaults` null) 쓸 문구가 비어 있을 수 있다(`selected` null). 개정 3 의 `CHAT` 조건 `chatTool` 은 없앴다(채널이 나뉘었다) — `jobName` 조건은 이메일·Slack·Mattermost.
- **문구 칸**: 앱 알림이 아니면 `templateId`(개정 3 의 `layoutId` 자리 — 그 채널의 템플릿). Slack·Mattermost = 제목 한 줄(200자) + 본문(그 앱 표기, 1500자, 비어도 됨, 템플릿의 `{{content}}` 자리), 기본 문구 = `{{title}}` / `{{body}}`(개정 3 채팅 글의 그림·굵게·「열기」는 기본 템플릿이 맡는다). 카카오톡 = 템플릿만(제목·본문 빈 글자, 승인 본문 그대로). 변수는 개정 3 그대로(`severityIcon` 변수는 없앴다 — 템플릿이 그린다). 이력 바뀐 칸 `templateId`.
- **문구 목록**: 왼쪽 위 채널 영역 = 전체 + 그 받는 사람이 쓰는 채널(카카오톡 「준비 중」 딱지·흐리게), 카카오톡을 고르면 안내 + 새로 만들기 잠금, 그 줄은 흐리게(열어 볼 수는 있고 복사·삭제 없음). 「템플릿」 열(앱 알림 「—」, 템플릿 편집 링크) + 거르기 `?template=`(옛 `?layout=`, 채널별 묶음).
- **문구 편집**: 채널(새로 만들 때만 — 카카오톡은 「준비 중」으로 고를 수 없음, 주소로 와도 첫 채널로) · **템플릿 고르기**(이메일 = 받는 사람 맞는 메일 템플릿, Slack·Mattermost = 그 종류, 카카오톡 = 알림톡 템플릿 — 새 문구는 그 채널(받는 사람)의 기본 템플릿, 채널을 바꾸면 그 채널의 기본으로). 미리보기 = 고른 템플릿에 이 문구를 씌운 것(메일 iframe — 서버, Slack·Mattermost 카드 — 화면이 `UtilChatTemplate.card` 로, 색 막대·그림은 대상의 `severity`, 카카오톡 말풍선 — 대상의 견본 값). 앱 알림은 서랍·알림함 한 줄. Slack·Mattermost 시험은 실제 알림 도구 목록에서 **그 종류의** 도구만(`POST chat-test` 에 `channel`·`templateId`·`subject`). 카카오톡 문구는 전부 읽기 전용(서버도 409 `CHANNEL_PREPARING`).
- **이벤트 상세**: 채널 구획이 새 채널 목록(운영자 = 앱 알림·이메일·Slack·Mattermost·카카오톡, 회원 = 앱 알림·이메일·카카오톡). Slack·Mattermost 안내는 그 종류의 도구를 「전달 도구」로 골랐는지로. 카카오톡 구획은 흐리게 · 「준비 중」 딱지 · 안내 + 「이 채널의 문구 보기」만(고르기 칸·「새 문구」 없음, 저장에 넣지 않는다). 「쓰는 문구」 요약은 준비 중 채널을 뺀다.
- **메일 템플릿**: 개정 3 의 메일 레이아웃 화면 그대로(구획 「운영자용」·「회원용」·「모두」, 칸·검사·미리보기·이력), 「쓰는 템플릿」 → 「쓰는 문구」(받는 사람의 문구 목록 `?channel=EMAIL&template=`). 목록 줄·하나에 `messageCount`·`messageCounts`(옛 `templateCount(s)`). 삭제 409 는 `TEMPLATE_IN_USE`(옛 `LAYOUT_IN_USE`), 기본 409 `TEMPLATE_BUILTIN`, 상한 `TEMPLATE_LIMIT`. id 는 `mail-member`·`mail-operator`·`mail-security`·`mail-notice`·`mail-simple`.
- **Slack·Mattermost 템플릿**(한 화면 `/{slack|mattermost}[/{id}]`): 종류마다 기본 1개(builtin — 그림·굵게·안내 파랑/주의 호박/긴급 빨강·사이트 이름·보낸 시각·「열기」/「Open」·틀 `{{content}}`) + 사용자 예시 1개씩(Slack 「짧은 카드」 — 그림·사이트 이름·버튼 없음, 운영 채널 문구가 씀 / Mattermost 「운영 채널(눈에 띄게)」 — 다른 색·버튼 글자·틀 뒤에 `*{{siteName}} 운영 알림*`). 칸 `name · showSeverityIcon · boldTitle · colors{INFO,WARNING,CRITICAL}`(팔레트 8색 가정 `GREEN·BLUE·TEAL·PURPLE·AMBER·ORANGE·RED·GRAY`) `· showSiteName · showSentAt · showOpenButton · openLabel{ko,en}`(보일 때 필수·20자) `· frames{ko,en}`(그 앱 표기, `{{content}}` 한 번, 500자, 변수는 `content`·운영자 공통 변수). 「열기」 버튼 주소는 이벤트의 `{{link}}`(가정). 미리보기는 견본 문구 + 미리 볼 심각도 고르기. 이력 바뀐 칸 `name|showSeverityIcon|boldTitle|colorInfo|colorWarning|colorCritical|showSiteName|showSentAt|showOpenButton|openLabelKo|openLabelEn|frameKo|frameEn`. 「쓰는 문구」 → 운영자 문구 목록 `?channel=<종류>&template=`.
- **카카오톡 템플릿**(준비 중): 목록·보기만(`GET` 둘), 만들기 단추 잠금 + 알림 도구 안내. 예시 1개 「댓글 알림톡(예시)」(코드 `RBY_COMMENT_01`, 승인됨, 강조 제목, 승인 본문의 `#{사이트}·#{닉네임}·#{댓글}·#{글제목}` ↔ `siteName·actorName·excerpt·postTitle`, 버튼 「글 보기」 → `{{link}}`) — 회원 「내 글에 댓글」의 카카오톡 예시 문구 1개가 쓴다. 모양 `{templateCode, status: REQUESTED|APPROVED|REJECTED, approvedBody, emphasis, mappings[{placeholder, variable, sample}], buttons[{name, linkVariable}], channelName, messageCount(s)}`.
- **API 모양(목업)**: 문구 `/api/admin/notification-messages`(`targets`·`targets/{type}/{code}`·`messages[/{id}][/history]`·`preview`(`templateId`|저장 전 `template`)·`test`·`chat-test`) — 개정 3 의 `/api/admin/notification-templates/{targets|templates}` 를 옮겼다(규칙의 `templateId` → `messageId`, 오류 `fields.channels.<채널>.rules[i].messageId`, 404 `MESSAGE_NOT_FOUND`·`MESSAGE_TARGET_NOT_FOUND`, 409 `MESSAGE_IN_USE`·`MESSAGE_BUILTIN`·`MESSAGE_LIMIT`·`CHANNEL_PREPARING`). 템플릿 `/api/admin/notification-templates/mail`(옛 `/api/admin/mail-layouts`) · `/slack` · `/mattermost`(`[/{id}][/history]`) · `/kakao`(읽기만).
- **`?mock=` 스위치**: 개정 3 그대로이되 `template-in-use` 는 이제 **템플릿** 지우기 409(옛 `layout-in-use`), 문구 지우기 409 는 `message-in-use`.
- **미확정(개정 4)**: 10. Slack·Mattermost 「열기」 버튼의 주소가 늘 이벤트 `link` 인지 11. 색 막대 팔레트·심각도 그림 이름 12. 카카오톡 템플릿의 등록·검수 흐름(화면에서 카카오 검수를 요청할지, 받아오기만 할지)과 대체 문자(SMS) 13. 회원 이벤트의 카카오톡(수신 동의·야간 발송 제한) 14. Slack 표기의 굵게가 한글 조사 바로 앞에서 풀리지 않는지(미리보기는 앞뒤가 빈칸·문장부호일 때만 굵게).

## 개정 3 — 알림 템플릿·채널·이벤트 화면 분리 (2026-10-23, 사용자 확정 — 아래 「개정」·「개정 2」의 메뉴·화면 구성을 대체한다)
**템플릿은 메일만의 것이 아니라 이벤트가 나가는 채널마다의 양식이다.** 템플릿 하나 = (이벤트 또는 필수 안내 메일) × 채널 × 언어.

### 메뉴 (설정 메뉴)
- **메일** 묶음: 메일 서버(`/settings/mail`)만 남긴다. 「메일 종류」·「템플릿」·「레이아웃」 메뉴는 이 묶음에서 빠진다.
- **알림·이벤트** 묶음(차례 — 묶음 이름은 「알림」에서 「알림·이벤트」로 바꿨다, i18n `menu.notifications` ko 「알림·이벤트」 · en 「Notifications & events」):
  1. **알림 도구** `/settings/notification-channels` (그대로)
  2. **알림 템플릿** (하위 메뉴를 가진 부모 — 역할 관리처럼) `/settings/notification-templates` → 운영자 템플릿으로 보낸다. **받는 사람은 메뉴로 나눈다** — 화면 안에서 「전체/회원/운영자」를 고르지 않는다(2026-10-23 사용자 지적).
     - **운영자 템플릿** `/settings/notification-templates/templates/operator` · **회원 템플릿** `/settings/notification-templates/templates/member` (이벤트 메뉴처럼 운영자 먼저) — 그 받는 사람의 템플릿(이메일·채팅·앱 알림) 표, 왼쪽 위 채널 영역 + 거르기: 대상 종류(이벤트/필수 안내 메일)·대상(그 받는 사람의 것만)·언어·기본/사용자·레이아웃. 받는 사람 열·거르기 없음. 편집 `/templates/[id]`(두 메뉴 공용), 새로 `/templates/new?audience=&target=&channel=`, 복사 `?from=`. 옛 주소 `/templates` 는 `?audience=MEMBER` 면 회원, 그 밖은 운영자 템플릿으로(쿼리 유지).
     - **메일 레이아웃** `/settings/notification-templates/layouts` — 이메일 채널 전용 틀(받는 사람별 기본 2개 + 사용자 레이아웃, 「개정 2」 규칙 그대로). 목록은 「운영자용」·「회원용」·「모두」 구획(쓰는 대상 고르기 없음).
     - **필수 안내 메일** `/settings/notification-templates/required-mails` — 이벤트가 아닌 꼭 가는 메일(회원 이메일 인증·운영자 초대·임시 비밀번호·재설정 승인번호·메일 서버 시험) 목록 → 상세 `/required-mails/[kind]`: 언어별 쓸 템플릿 + 특수 상황 규칙. 받는 사람이 끌 수 없다. 채널은 이메일뿐. 목록은 한 화면에 「운영자에게 가는 메일」·「회원에게 가는 메일」 구획(종류가 적어 메뉴로 나누지 않는다).
  3. **이벤트** (하위 메뉴를 가진 부모, 2026-10-23 사용자 확정 — 묶음 맨 아래) `/settings/notifications` → **운영자** `/settings/notifications/operator`(지금 「이벤트」 화면 — 이름·주소 바뀜, 옛 주소는 새 주소로) · **회원** `/settings/notifications/member`(새로)
- 「메일 종류」 화면(`/settings/mail-kinds`)은 없앤다 — 이벤트 메일의 양식은 이벤트 상세에서, 필수 안내 메일은 위 화면에서 정한다.

### 채널
| 채널 | 쓰이는 곳 | 형식 |
|---|---|---|
| `EMAIL` 이메일 | 메일 | 제목 + 간단 서식 본문 + 버튼 블록 + **메일 레이아웃** |
| `CHAT` 채팅 | Slack·Mattermost 글(알림 도구) | 한 덩어리 글(간단 마크다운 — 굵게·링크·목록 + 변수, 레이아웃 없음) |
| `IN_APP` 앱 알림 | 관리자 종 서랍(운영자)·회원 알림함(회원) | 제목 한 줄 + 짧은 본문(평문 + 변수) |
- 받는 사람별 채널: 운영자 이벤트 = 앱 알림·이메일·채팅, 회원 이벤트 = 앱 알림·이메일, 필수 안내 메일 = 이메일.

### 이벤트 화면
- **회원 이벤트**(목록): 이벤트마다 사용 여부, 채널 켜기(앱 알림·이메일), 회원이 끌 수 있는지, 쓰는 템플릿 요약. 회원 이벤트 목록은 **하나로 통합**한다 — 지금 코드의 5종(댓글·답글·포인트 회수·글 숨김·댓글 숨김)과 게시판 기본 설정 「알림」의 7종(내 글에 댓글·내 댓글에 답글·멘션·인기글·운영자 조치·스태프 조치·신고 결과)을 합쳐 실제로 일어나는 것만 쓸 수 있고 나머지는 「준비 중」. 게시판 기본 설정 「알림」 탭의 이벤트별 칸은 서버 단계에서 이 화면으로 옮긴다(목업 단계에서는 그 탭을 건드리지 않고 이 화면에 「게시판 기본 설정에서 옮겨 올 칸」 안내만).
- **운영자 이벤트**(목록): 지금 이벤트 관리 화면 그대로(사용·전달 도구·추가 수신 팀 — 실제 서버 연결 유지) + 이메일을 코드가 허용한 5개만이 아니라 준비 중이 아닌 이벤트 모두 이메일을 켤 수 있게(목업 표시 — 서버 단계에서 `emailable` 확장).
- **이벤트 상세**(두 화면 공통 `/settings/notifications/{member|operator}/[code]`): 이벤트 설명·받는 사람·수신 규칙(운영자)·채널별 구획 — 채널마다 ① 언어별 쓸 템플릿 ② 특수 상황 규칙(조건 선택지는 이벤트·채널마다 코드가 정함) ③ 그 이벤트·채널의 템플릿 목록 링크. 저장 줄 하나.

### 템플릿 편집
- 채널에 따라 칸이 다르다: 이메일(제목·본문 에디터·버튼·레이아웃 고르기·미리보기(레이아웃 적용)·시험 발송), 채팅(본문 한 칸 — 간단 마크다운 미리보기, 「알림 도구로 시험 보내기」(도구 고르기)), 앱 알림(제목·본문 평문, 서랍/알림함 모양 미리보기). 공통: 이름·대상·채널·언어(새로 만들 때만 대상·채널), 변수 삽입, 기본으로 되돌리기, 제목 옆 「변경 이력」 서랍.
- 변수는 대상(이벤트·필수 안내 메일)이 정하고, 받는 사람별 공통 변수(회원 `siteUrl`, 운영자 `adminUrl`)는 「개정 2」 그대로.

### 목업 가정 — 개정 3 (화면 목업 2026-10-23, 사용자 확인 전 — 아래 「목업 가정」·「목업 가정 — 개정 2」 중 화면 구성·메일 종류에 관한 것은 이것이 대체하고, 레이아웃·검사·받는 사람별 공통 변수 규칙은 그대로)
- **화면·주소**: 메뉴는 위 「메뉴」 그대로(키 `memberEvents` · `operatorEvents` · `notificationChannels` · `notificationTemplates`(부모) › `operatorTemplates` · `memberTemplates` · `mailLayouts` · `requiredMails`). 옛 주소는 새 화면으로 보낸다 — `/settings/notifications` → `/settings/notifications/operator`, `/settings/mail-kinds[/{kind}]` → 필수 안내 메일(계정·보안 메일 코드) 또는 운영자 이벤트 상세(알림 이벤트 코드), `/settings/mail-templates[/{id}]` → `/settings/notification-templates/templates[/{id}]`(`?kind=` → `?target=`, 목록은 `?channel=EMAIL`), `/settings/notification-templates/templates`(목록) → `?audience=MEMBER` 면 `…/templates/member`, 그 밖은 `…/templates/operator`(나머지 쿼리 유지), `/settings/mail-layouts[/{id}]` → `…/layouts[/{id}]`. 부모 `/settings/notification-templates` → 운영자 템플릿.
- **받는 사람 = 메뉴**(2026-10-23 사용자 지적 「별도 메뉴로 뺀다며 왜 아직도 선택하게 되어 있냐」): 템플릿 목록·필수 안내 메일 목록·메일 레이아웃 목록에서 받는 사람(쓰는 대상)을 고르는 `SelectButton` 을 모두 없앴다. 템플릿 목록은 메뉴 둘(두 화면이 공용 조각 `NotificationTemplateList`(속성 `audience`) + 상태 `useNotificationTemplateList` 를 쓴다), 필수 안내 메일·메일 레이아웃은 한 화면 안 구획(운영자 먼저).
  - 목록으로 가는 링크는 받는 사람을 넣어 새 주소로 바로 간다: 이벤트 상세 「이 채널의 템플릿 보기」(그 이벤트의 받는 사람), 필수 안내 메일 상세 「템플릿 보기」, 레이아웃 「쓰는 템플릿」(받는 사람 하나짜리는 그 목록, 「모두」는 운영자 n개 · 회원 m개를 따로 링크 — 목록 줄에도 받는 사람별 수 `templateCounts` 를 받는다), 템플릿 편집 「목록으로」·삭제 뒤(그 템플릿의 받는 사람 목록). 「새 템플릿」은 `?audience=` 를 붙이고, 편집 화면의 새로 만들기는 그 받는 사람의 대상만 고르게 한다(대상이 받는 사람을 정하므로 만든 템플릿은 그 목록에 남는다).
  - 템플릿 편집(`templates/{id}`)은 두 메뉴 어디에도 속하지 않아 메뉴에서는 부모 「알림 템플릿」만 활성으로 보이고, 화면 권한·메뉴 모드는 부모의 것(`template.read`)으로 본다(`UtilAdminMenu.parents`). 서버 `AdminScreenRegistry` 는 아직 옛 키(`notifications` → `/settings/notifications`, 이름 `menu.notificationEvents` — 값을 「운영자 이벤트」로 바꿔 둠)라 서버 단계에서 맞춘다.
- **대상 = 이벤트 · 필수 안내 메일**, 템플릿 하나 = 대상 × 채널 × 언어. 화면 주소는 대상 코드만 쓴다(`?target=<code>`) — 목업 가정: 코드는 이벤트·필수 안내 메일을 넘어 겹치지 않는다(겹칠 수 있으면 `type:code`).
  - 운영자 이벤트 = 서버 `AdminNotificationEvent` 22종(준비 중 포함). 채널 앱 알림·이메일·채팅 — 본인에게만 가는 4종(`*_ME`)은 채팅 없음(공유 채널 불가와 같은 기준).
  - 회원 이벤트(템플릿 대상) = 지금 일어나는 5종(`MemberNotificationType`). 채널 앱 알림·이메일. 「준비 중」 회원 이벤트는 템플릿 대상이 아니다(상세에서 「아직 템플릿이 없어요」).
  - 필수 안내 메일 = 개정 전 「메일 종류」의 계정·보안 메일 5종(코드 그대로). 채널 이메일뿐, 「끌 수 없음」 딱지. 개정 전 「알림 메일」 5종은 운영자 이벤트의 이메일 채널이 됐다.
- **회원 이벤트 통합 목록(이름 대응)** — 실제 5종 + 게시판 기본 설정 「알림」(`BoardMemberNotifyKind`)에만 있는 5종은 「준비 중」:

  | 회원 이벤트(코드) | 지금 코드 `MemberNotificationType` | 게시판 기본 설정 종류 | 상태 | 회원이 끌 수 있음 |
  |---|---|---|---|---|
  | 내 글에 댓글(`COMMENT`) | `COMMENT` | `COMMENT_ON_POST` | 사용 | 운영자가 정함 |
  | 내 댓글에 답글(`REPLY`) | `REPLY` | `REPLY_ON_COMMENT` | 사용 | 운영자가 정함 |
  | 포인트 회수(`POINTS_REVOKED`) | `POINTS_REVOKED` | — | 사용 | 잠금(계정 알림) |
  | 내 글 숨김(신고)(`POST_HIDDEN`) | `POST_HIDDEN` | — | 사용 | 잠금(계정 알림) |
  | 내 댓글 숨김(신고)(`COMMENT_HIDDEN`) | `COMMENT_HIDDEN` | — | 사용 | 잠금(계정 알림) |
  | 멘션(`MENTION`) | — | `MENTION` | 준비 중 | 운영자가 정함 |
  | 내 글이 인기글이 됨(`POST_POPULAR`) | — | `POST_POPULAR` | 준비 중 | 운영자가 정함 |
  | 운영 처리(`OPERATOR_ACTION`) | — | `OPERATOR_ACTION` | 준비 중 | 잠금(처리 알림) |
  | 스텝·매니저 처리(`STAFF_ACTION`) | — | `STAFF_ACTION` | 준비 중 | 잠금(처리 알림) |
  | 신고 처리 결과(`REPORT_RESULT`) | — | `REPORT_RESULT` | 준비 중 | 운영자가 정함 |
  - 회원 이벤트 줄: 사용 · 앱 알림 · 이메일 · 회원이 끌 수 있음(스위치 — 바로 저장, 낙관적 갱신) · 쓰는 템플릿 요약 · 상세. 이메일은 지금 회원 알림에 없어 모두 꺼진 채 시작(목업). 맨 위 안내 「게시판 기본 설정 › 알림의 이벤트별 칸은 서버 단계에서 이 화면으로 옮겨요」 + 그 탭 링크(탭은 건드리지 않았다). 권한은 `notification.read`·`notification.manage`(운영자 이벤트와 같다).
- **운영자 이벤트 목록**: 실제 API 그대로(사용·전달 도구·추가 수신 팀 즉시 저장) + 「쓰는 템플릿」 칸(채널마다 한국어 쓸 템플릿, 영어가 다르면 「· EN …」, 규칙 수 — 목업) + 「상세」. 서버 `emailable=false` 인 이벤트의 이메일 도구는 지금처럼 고를 수 없고 까닭만 「이 이벤트의 이메일은 서버 단계에서 열려요」(실제 PUT 400 을 피한다). `template.read` 가 없으면 「쓰는 템플릿」 칸을 감춘다.
- **이벤트 상세**(한 페이지 `/settings/notifications/[audience]/[code]`, 채널 칸은 필수 안내 메일 상세와 같은 조각 `TemplateChannelSelection`): ① 이벤트 구획은 **보여 주기만**(설명·받는 사람·수신 규칙(운영자)·사용·전달 도구·추가 수신 팀 / 회원은 채널·회원이 끌 수 있음 · 최근 30일 발송 — 바꾸기는 목록에서, 「목록에서 바꾸기」 링크). 목록의 즉시 저장과 상세의 한 번 저장이 같은 값을 두 곳에서 고치지 않게 했다. 운영자 이벤트 정보는 실제 `GET notification-events` 목록에서 찾는다(한 줄 조회 API 가 없다). ② 채널마다 구획: 쓸 템플릿(언어별) + 특수 상황 규칙 + 「이 채널의 템플릿 보기」·「새 템플릿」. 그 채널로 지금 안 보내면 안내 한 줄(목록에서 꺼짐 · 이메일은 서버 단계에서 열림 · 채팅은 Slack·Mattermost 도구를 골랐을 때만). 저장 줄 하나(`PUT targets/EVENT/{code}` — 채널 전부).
- **조건(코드가 정한 선택지 — 목업)**: 운영자 이벤트 이메일 `recipientSource`(기본 수신 규칙 / 추가 수신 팀) · 채팅 `chatTool`(Slack / Mattermost) · 배치 실패의 이메일·채팅 `jobName`, 운영자 앱 알림은 조건 없음. 회원 댓글 `grouped`(댓글 하나 / 묶인 댓글), 글·댓글 숨김 `hideReason`(신고가 쌓여 / 권리 침해 임시 숨김). 필수 안내 메일은 개정 전 그대로(인증 `signupMethod`, 초대 `inviteRole`).
- **기본 템플릿(목업 데이터)**:
  - 이메일 — 지금 메일 문구(필수 안내 메일 = `mail.*`, 운영자 이벤트 = 한 양식 `NotificationMailTemplate`·`notification.mail.*`). 회원 이벤트 이메일은 지금 없어 같은 꼴의 새 문구(「사이트에서 보기」 버튼).
  - 앱 알림 — 지금 알림 문구(`notification.event.<코드>.title|body`, `member.notification.<코드>.title`). 번들의 `{0}`·`{2}` 자리는 이름 붙은 변수로(`jobName`·`reason`·`operatorName`·`change`·`changedItems`·`count` / 회원 `actorName`·`excerpt`·`postTitle`·`amount`·`reason`·`otherCount`). 회원 앱 알림 본문은 지금 번들에 없어 `{{excerpt}}`·`{{postTitle}}`·`{{reason}}` 로 가정.
  - 채팅 — 지금 `ChannelMessageFormatter.format` 의 모양: `{{severityIcon}} **{{title}}**` + 줄바꿈 `{{body}}` + `[열기]({{link}})`(번들 `notification.channel.post.open`). 간단 마크다운은 Mattermost 표기로 저장하고 Slack 으로 올릴 때 서버가 `*굵게*`·`<주소|글자>` 로 바꾼다(가정). 변수 값의 멘션·마크다운 무력화는 지금처럼 서버가 한다.
  - 이메일·채팅의 `{{title}}`·`{{body}}` = 그 이벤트의 앱 알림 템플릿으로 만든 제목·본문(가정 — 이메일·채팅만 따로 고치고 싶으면 이벤트 변수를 직접 쓴다).
  - 지금 코드의 특수 문구(묶인 댓글 「…님 외 N개의 댓글이…」, 권리 침해 임시 숨김)는 목업에서 **사용자 템플릿 + 규칙**으로 넣어 두었다 — 서버 단계에서 기본 규칙으로 줄지 미확정(아래 6).
- **사용자 템플릿 목업 7개 · 규칙 4개**: 소셜 가입 회원용(인증 메일, 규칙) · 긴급 배치 실패(배치 실패 이메일, 규칙 `jobName=grade-promotion`) · 초대 메일(간결)(초대, 한국어 쓸 템플릿) · Plain test(메일 시험, 안 씀) · 운영 채널용(짧게)(배치 실패 채팅, 한국어 쓸 템플릿) · 묶인 댓글(회원 댓글 앱 알림, 규칙) · 권리 침해 임시 숨김(글 숨김 앱 알림, 규칙).
- **템플릿 목록**(운영자 템플릿 · 회원 템플릿 — 같은 조각): 화면 제목 「운영자 템플릿」/「회원 템플릿」. 열 이름 · 채널 · 대상(이름 + 「운영자 이벤트 · 영역 / 회원 이벤트 / 필수 안내 메일」, 보안 메일 자물쇠, 대상 화면 링크) · 언어 · 레이아웃(이메일만, 그 밖 「—」) · 구분 · 마지막 수정 · 관리. 왼쪽 위 채널 영역(전체·앱 알림·이메일·채팅 + 그 받는 사람의 수) + 거르기: 검색 · 대상 종류 · 대상(그 받는 사람의 것, 묶음 고르기, 검색 칸) · 언어 · 기본/사용자 · 레이아웃(채널이 전체·이메일일 때만). 주소 `?channel=&targetType=&target=&layout=`. 목록 API 는 그대로 전부 받아 화면이 받는 사람으로 거른다(가정 — 수가 적다). 50줄씩 쪽 나눔. 대상·채널당 사용자 템플릿 20개(가정).
- **필수 안내 메일 목록**: 「운영자에게 가는 메일」 · 「회원에게 가는 메일」 구획(제목 옆 수, 빈 구획은 빈 안내). 표에서 받는 사람 열을 뺐다.
- **메일 레이아웃 목록**: 「운영자용」 · 「회원용」 · 「모두(회원·운영자)」 구획(제목 옆 수, 빈 구획은 「레이아웃이 없어요」). 표에서 쓰는 대상 열을 뺐다. 「새 레이아웃」은 쿼리 없이(편집 화면 기본 — 회원 기본 레이아웃 값으로 시작). 목록 줄 API 모양에 `templateCounts: {MEMBER, OPERATOR}` 를 더했다(하나 조회와 같은 칸 — 「모두」 레이아웃의 받는 사람별 링크용).
- **템플릿 편집(채널별 칸)**: 공통 — 이름 · 대상(새로 만들 때만, 묶음 고르기) · 채널(새로 만들 때만, 대상이 나가는 채널 중) · 받는 사람(읽기 전용) · 언어 · 변수 넣기 · 기본으로 되돌리기 · 변경 이력. 대상·채널을 바꾸면 그 기본 내용으로 다시 채운다(고친 내용이 있으면 묻는다).
  - 이메일: 개정 전 메일 템플릿 편집 그대로(레이아웃 · 제목 200자 · 간단 에디터 · 버튼 · iframe 미리보기 · 나에게 시험 발송).
  - 채팅: 글 한 칸(2000자 가정, 고정폭 글꼴) → 채팅 글 모양 미리보기(화면이 견본 값으로 그린다 — `UtilNotificationTemplate.renderChat`, 값은 가리고 `**`·`[글자](http…)`·`- ` 목록만 살린다). 「알림 도구로 시험 보내기」 → 대화상자에서 **실제 알림 도구 목록**(`GET /api/admin/notification-channels`)의 Slack·Mattermost 도구를 고른다(꺼진 도구는 고를 수 없음, 없으면 「알림 도구 열기」 안내). 보내기는 목업(`POST chat-test` → `{ok, message}`).
  - 앱 알림: 제목 한 줄(100자 가정) · 본문 평문(300자 가정, 비어도 됨) → 운영자는 관리자 종 서랍 한 줄, 회원은 회원 알림함 한 줄 모양 미리보기(화면이 견본 값으로 그린다). 시험 보내기 없음.
  - 검사(서버도 같은 규칙): 이메일은 개정 전 그대로, 채팅·앱 알림은 이름(필수·50자) · 길이 · 제목 한 줄 · 모르는 변수(다른 받는 사람의 공통 변수는 따로 알린다). 서버 칸 `fields.name|channel|locale|layoutId|subject|body`(개정 전 `bodyHtml` 은 `body` — 이력의 바뀐 칸도 `body`).
- **API 모양(목업)**: `useNotificationTemplateApi`(`/api/admin/notification-templates`) — `GET targets?type=&audience=` · `GET/PUT targets/{type}/{code}`(채널마다 `{selected, rules}`, 오류 `fields.channels.<채널>.selected.ko …`) · `GET/POST/PUT/DELETE templates[/{id}]`(요청 `{targetType, targetCode, channel, name, locale, layoutId|null, subject, body}`) · `POST preview`(이메일만, `target`) · `POST test`(이메일) · `POST chat-test` · `GET templates/{id}/history`. 회원 이벤트 `useMemberEventApi`(`/api/admin/member-events` — `GET` · `PUT /{code}` `{enabled, inApp, email, memberCanTurnOff}`). 메일 레이아웃 `/api/admin/mail-layouts` 는 그대로(이메일 템플릿만 센다).
- **`?mock=` 스위치**: 개정 전 `template-*`·`layout-in-use` 그대로(`template-test-fail`·`template-test-limit` 은 채팅 시험에도) + 회원 이벤트 `member-events-error` · `member-events-save-error` · `member-events-denied`.
- **권한 이름**: `template.read`·`template.manage` 의 이름을 「알림 템플릿 조회·관리」로 바꿨다(코드는 그대로 — 서버 `Permission` 에 아직 없다).
- **미확정(개정 3)**: 5. 회원 이벤트 이메일의 수신 동의·조용한 시간(게시판 기본 설정의 이메일 조용한 시간을 옮길지) 6. 지금 코드의 특수 문구(묶인 댓글·임시 숨김)를 기본 규칙으로 줄지 7. 브라우저 알림(운영자 도구 `browser`)이 앱 알림 템플릿을 같이 쓰는지(가정: 같이 쓴다) 8. 이메일·채팅의 `{{title}}`·`{{body}}` 를 앱 알림 결과로 둘지 9. 대상 코드가 종류를 넘어 겹치지 않는다는 가정.

### API 후보 변화
- 템플릿에 `channel`(`EMAIL|CHAT|IN_APP`)·`target`(`{type: 'EVENT'|'REQUIRED_MAIL', code, audience}`), 레이아웃은 이메일만. 대상별 설정: `GET/PUT /api/admin/notification-templates/targets/{type}/{code}` → 채널별 `{ selected: {ko, en}, rules: [...] }`. 메뉴 키(서버 `AdminScreenRegistry` 맞출 것): `memberEvents`, `operatorEvents`(옛 `notifications`), `notificationChannels`, `notificationTemplates`(부모) — `operatorTemplates`(`/settings/notification-templates/templates/operator`), `memberTemplates`(`…/templates/member`), `mailLayouts`, `requiredMails`(옛 `notificationTemplateList` 는 없앴다). `GET /api/admin/mail-layouts` 목록 줄에 `templateCounts: {MEMBER, OPERATOR}`.

## 개정 (2026-10-23, 사용자 의견 — 아래 「화면」 절을 대체한다)
- **레이아웃은 여러 개**다. 「공통 레이아웃 하나」가 아니라 레이아웃이 별도 목록(예: 기본·보안 안내·공지·간단)이고, **템플릿마다 어느 레이아웃을 쓸지 고른다**. 기본 레이아웃(코드 제공, 읽기 전용 — 복사해서 만들기)이 하나 있고 운영자가 레이아웃을 더 만든다. 레이아웃 = 이름·로고/사이트 이름 보이기·머리말 띠 색(팔레트)·꼬리말 문구·언어별 틀(`{{content}}` 필수 1번). 쓰는 템플릿이 있는 레이아웃은 삭제 막음(409 `LAYOUT_IN_USE`).
- **한 화면에 다 넣지 않고 화면을 나눈다** — 설정 메뉴에 새 묶음 **「메일」**(「알림」·「운영자와 권한」처럼 묶음): 
  1. **메일 서버** `/settings/mail`(기존 화면을 시스템 묶음에서 이 묶음으로 옮김)
  2. **메일 종류** `/settings/mail-kinds` — 종류 목록(묶음·보안 메일·쓰는 템플릿·규칙 수) → 행을 누르면 `/settings/mail-kinds/[kind]` 상세: ① 언어별 쓰는 템플릿 ② 특수한 상황(조건 규칙) — 저장 줄 하나.
  3. **템플릿** `/settings/mail-templates` — 모든 종류의 템플릿 표(거르기: 종류·언어·기본/사용자, 검색) → `/settings/mail-templates/[id]` 편집 화면(이름·종류·언어·**레이아웃 고르기**·제목·본문·버튼·미리보기·시험 발송·기본으로 되돌리기·변경 이력 서랍), 새로 만들기는 `/settings/mail-templates/new?kind=`(또는 복사 `?from=`).
  4. **레이아웃** `/settings/mail-layouts` — 레이아웃 목록(쓰는 템플릿 수) → `/settings/mail-layouts/[id]` 편집 화면(위 칸 + 미리보기 + 변경 이력 서랍), 새로 만들기·복사.
- 템플릿 편집 화면의 레이아웃 고르기는 「이 종류에서 처음 만들 때의 기본」으로 기본 레이아웃을 넣는다. 미리보기는 고른 레이아웃으로 그린다.
- 권한은 그대로 `template.read`·`template.manage`(레이아웃 포함).
- API 후보 추가: `GET/POST/PUT/DELETE /api/admin/mail-layouts[/{id}]`, `GET /api/admin/mail-layouts/{id}/history`, 템플릿 응답·요청에 `layoutId`. 기존 `GET/PUT /api/admin/mail-templates/layout`(공통 레이아웃 하나)은 없앤다.

## 개정 2 — 받는 사람 구분 (2026-10-23, 사용자 확정)
- 메일 종류의 **최상위 구분은 받는 사람**이다: `MEMBER`(회원 — 공개 사이트) · `OPERATOR`(운영자 — 관리 사이트). 그 안의 묶음은 `ACCOUNT`(계정·보안 메일 — 인증·초대·임시 비밀번호·승인번호·시험) · `NOTIFICATION`(알림 메일).
  - 지금 종류: 회원 — 이메일 인증. 운영자 — 초대·임시 비밀번호·재설정 승인번호·메일 서버 시험 + 관리자 알림 이메일 이벤트 5종.
  - 앞으로 회원 쪽에 회원 알림 메일(댓글·답글·포인트 회수 등)·비밀번호 찾기·제재 안내·휴면 예정이 더해진다(목업에는 「준비 중」으로 흐리게 보여도 된다 — 없는 기능은 감춘다 규칙상 목업 표시는 화면 확인용만).
- 화면: 메일 종류 목록 위 「회원 메일 / 운영자 메일」 전환(주소 `?audience=MEMBER|OPERATOR`), 템플릿 목록에 받는 사람 열·거르기, 템플릿·레이아웃 편집 화면에 받는 사람 표시.
- **레이아웃 기본값은 받는 사람별**: 기본 레이아웃을 둘로 — 「회원 기본」(공개 사이트 로고·사이트 이름·공개 주소 꼬리말) · 「운영자 기본」(관리 사이트 안내 꼬리말). 새 템플릿은 그 종류 받는 사람의 기본 레이아웃으로 시작하고 운영자가 바꿀 수 있다. 레이아웃에 `audience`(`MEMBER|OPERATOR|ANY`) 칸 — 템플릿 편집의 레이아웃 고르기는 받는 사람이 맞는 것과 `ANY` 만 보인다.
- **변수 차이**: 회원 메일 공통 변수 `siteUrl`(공개 사이트 주소), 운영자 메일 공통 변수 `adminUrl`(관리 사이트 주소). 종류 정의(`audience`)가 공통 변수를 정한다.
- API: 종류·템플릿·레이아웃 응답에 `audience`, 목록 거르기 `audience`.

## 화면 (최초안 — 위 「개정」이 대체)
- `/settings/mail-templates`(설정 > 시스템, 「메일 서버」 다음 — 메뉴 `mailTemplates`, 권한 `template.read`).
- 왼쪽: 메일 종류 목록(묶음: 시스템 메일 / 관리자 알림 — 이벤트 영역별). 종류마다 지금 쓰는 템플릿 이름, 조건 규칙 수, 보안 메일 표시(🔒 필수 변수·끌 수 없음).
- 오른쪽(종류를 고르면):
  1. **쓰는 템플릿** — 언어(ko·en)별 「기본으로 쓸 템플릿」 고르기(기본 템플릿 또는 사용자 템플릿).
  2. **특수한 상황** — 조건 규칙 목록(위에서부터 차례로 보고 맞는 첫 규칙의 템플릿, 없으면 1번). 규칙 = 조건(종류마다 코드가 정한 선택지) + 템플릿. 순서 바꾸기·추가·삭제.
  3. **템플릿 목록** — 이 종류의 기본 템플릿(읽기 전용, 「복사해서 만들기」) + 사용자 템플릿(이름·언어·마지막 수정). 편집·복사·삭제(쓰는 중이면 삭제 막음).
- 템플릿 편집(드로어 또는 화면): 이름, 언어, **제목**(한 줄, 변수 삽입 가능), **본문**(간단 에디터 + 변수 삽입 단추 + 「버튼」 블록 1개까지 — 링크 변수만), 미리보기(견본 값, 공통 레이아웃 안에서), 「나에게 시험 발송」, 변경 이력(설정 이력과 같은 표 `SettingHistoryTable` 재사용), 「기본으로 되돌리기」(기본 템플릿 내용으로 덮기).
- **공통 레이아웃** 템플릿 하나(로고·사이트 이름·꼬리말 문구) — 같은 화면 상단 「공통 레이아웃」 구획에서 편집. 본문 자리 `{{content}}` 는 지울 수 없다.

## 메일 종류(1단계)
- 시스템 메일 5종: 회원 이메일 인증(`MEMBER_EMAIL_VERIFY`), 운영자 초대(`OPERATOR_INVITE`), 운영자 임시 비밀번호(`OPERATOR_CREDENTIALS`), 운영자 비밀번호 재설정 승인번호(`OPERATOR_RESET_CODE`), 메일 서버 시험(`MAIL_TEST`) — 앞의 4종은 **보안 메일**(필수 변수를 지우면 저장 안 됨, 보내기를 끌 수 없음).
- 관리자 알림 이벤트 중 이메일을 보낼 수 있는 것(`AdminNotificationEvent` 의 `email()` — 지금 단일 양식 `NotificationMailTemplate`) — 이벤트마다 하나의 종류.

## 변수
- 종류마다 코드가 정한다(이름·설명·견본 값·필수 여부). 공통: `siteName`, `siteUrl`, `recipientName`. 예: 인증 `nickname`·`verifyLink`(필수)·`validHours`, 초대 `inviteLink`(필수)·`validHours`, 승인번호 `code`(필수)·`validMinutes`, 알림 `title`·`body`·`link`·`occurredAt`.
- 문법 `{{변수}}` 만(조건·반복 없음). 값은 자동으로 HTML 이스케이프. 링크는 변수로만(본문에 직접 쓴 `href` 는 http·https 만, `javascript:` 등은 저장 때 제거). 모르는 변수는 저장 거절.

## 조건(코드가 정한 선택지 — 예시)
- 관리자 알림 공통: 심각도(`INFO|WARNING|CRITICAL`), 받는 운영자 언어.
- 회원 이메일 인증: 가입 방식(이메일 / 소셜 — 소셜 가입 회원의 미확인 이메일).
- 운영자 초대: 초대받는 역할(역할 선택).
- 목업은 위 선택지로 흉내 낸다. 서버 단계에서 종류별 목록을 확정한다.

## API 후보 (목업이 부르는 모양 — 서버 단계에서 확정. 「개정」 반영)
- `GET /api/admin/mail-templates/kinds` → 종류 목록(묶음·보안 여부·변수 목록·조건 선택지·언어별 쓰는 템플릿·규칙 수·사용자 템플릿 수)
- `GET /api/admin/mail-templates/kinds/{kind}` → `{ kind, summary(목록 한 줄과 같은 모양), defaults: {ko, en}(기본 템플릿 id), selected: {ko, en}, rules: [{id, condition, templateId}], templates: [...] }`
- `PUT /api/admin/mail-templates/kinds/{kind}` `{ selected, rules }`
- `GET /api/admin/mail-templates/templates` → 모든 종류의 템플릿 목록(본문 없이) `{ id, kind, audience, kindName, security, name, locale, layoutId, layoutName, builtin, updatedAt, updatedByName, defaultFor: [locale], ruleCount }` — 거르기·검색은 화면이 한다(수가 적다는 가정)
- `GET/POST/PUT/DELETE /api/admin/mail-templates/templates[/{id}]` — `{ id, kind, name, locale, layoutId, subject, bodyHtml, builtin, updatedAt, updatedByName }`(기본 템플릿은 `builtin=true`, 읽기 전용. `PUT` 은 `kind` 를 바꾸지 못한다)
- `POST /api/admin/mail-templates/preview` `{ kind?, subject, bodyHtml, locale, layoutId? | layout? }` → `{ subject, html }`(견본 값, 고른 레이아웃 또는 저장 전 레이아웃 값 — 둘 다 없으면 기본 레이아웃)
- `POST /api/admin/mail-templates/test` `{ kind, locale, templateId | (subject, bodyHtml, layoutId), to }` → `{ succeeded, reason, detail }`(메일 설정 시험과 같은 모양·한도)
- `GET /api/admin/mail-templates/templates/{id}/history` — 설정 이력 모양(`{items, total, page, size}`, 바뀐 칸 `name|locale|layoutId|subject|bodyHtml`)
- `GET/POST/PUT/DELETE /api/admin/mail-layouts[/{id}]` — 목록 `{ id, name, builtin, audience, headerColor, templateCount, updatedAt, updatedByName }`, 하나 `{ …목록 칸, showLogo, showSiteName, footer: {ko, en}, frames: {ko, en}, templateCounts: {MEMBER, OPERATOR}, logoUrl, siteName, variables: {MEMBER, OPERATOR, ANY} }`, 저장 `{ name, audience, showLogo, showSiteName, headerColor, footer, frames }`(개정 2 — `audience` 와 받는 사람별 칸은 「목업 가정 — 개정 2」)
- `GET /api/admin/mail-layouts/{id}/history` — 설정 이력 모양(바뀐 칸 `name|showLogo|showSiteName|headerColor|footerKo|footerEn|frameKo|frameEn`)
- 없앤 것: `GET/PUT /api/admin/mail-templates/layout`(공통 레이아웃 하나).
- 오류: 400 `VALIDATION`(템플릿 `fields.name|locale|layoutId|subject|bodyHtml` — 모르는 변수·필수 변수 없음·길이·없는 레이아웃, 레이아웃 `fields.name|headerColor|footer.ko|footer.en|frames.ko|frames.en`), 409 `TEMPLATE_IN_USE`·`LAYOUT_IN_USE`(쓰는 중 삭제)·`TEMPLATE_BUILTIN`·`LAYOUT_BUILTIN`(기본 것 고치기·지우기)·`TEMPLATE_LIMIT`·`LAYOUT_LIMIT`, 404 `TEMPLATE_NOT_FOUND`·`MAIL_KIND_NOT_FOUND`·`LAYOUT_NOT_FOUND`, 403.

## 미확정
1. 조건 선택지의 종류별 최종 목록(서버 단계).
2. 2단계: Slack·Mattermost 글 문구, 회원 알림(앱 안) 문구, 회원 알림 메일.
3. 사용자 템플릿 수 상한(종류당 20 가정), 사용자 레이아웃 수 상한(20 가정), 본문·꼬리말 길이 상한.
4. 머리말 띠 색 팔레트의 최종 색(8색 가정)과 띠 모양(색이 있으면 머리말을 띠 안에 흰 글씨로).

## 목업 가정 (화면 목업 2026-10-23 재구성 — 사용자 확인 전)
- **화면 구조(「개정」 그대로)**: 설정 메뉴 새 묶음 「메일」(`settingsMail`, 아이콘 `pi pi-envelope`, 시스템 묶음 다음·알림 묶음 앞) = 메일 서버 `/settings/mail`(키 `mailSettings` 유지, 아이콘 `pi pi-server`, `site.read`) · 메일 종류 `/settings/mail-kinds`(`mailKinds`, `pi pi-tags`) · 템플릿 `/settings/mail-templates`(`mailTemplates`, `pi pi-file-edit`) · 레이아웃 `/settings/mail-layouts`(`mailLayouts`, `pi pi-objects-column`) — 뒤의 셋은 `template.read`. 서버 `AdminScreenRegistry` 는 서버 단계에서 맞춘다(지금은 「시스템」 묶음에 `mailSettings` 만 있다).
  - 메일 종류 목록(표: 묶음 · 이름(보안 메일 딱지·코드·설명) · 언어별 쓰는 템플릿 · 규칙 수 · 사용자 템플릿 수) → 상세 `/settings/mail-kinds/[kind]`: ① 쓰는 템플릿(언어별, 「템플릿 열기」 링크) ② 특수한 상황 — 저장 줄 하나(`StickySaveBar`). 머리 단추 「이 종류의 템플릿 보기」(`/settings/mail-templates?kind=`) · 「새 템플릿」(`new?kind=`).
  - 템플릿 목록(표: 이름+쓰는 중 딱지 · 종류 · 언어 · 레이아웃 · 기본/사용자 · 마지막 수정 · 복사·삭제, 거르기: 검색·종류·언어·기본/사용자·레이아웃 — 종류·레이아웃은 주소 `?kind=`·`?layout=`) → 편집 화면 `/settings/mail-templates/[id]`(서랍이 아니다). 새로 만들기 `new?kind=`(없으면 첫 종류), 복사 `new?from=`.
  - 레이아웃 목록(표: 이름+띠 색 점 · 기본/사용자 · 쓰는 템플릿 수(→ `?layout=` 로 거른 템플릿 목록) · 마지막 수정 · 복사·삭제) → 편집 화면 `/settings/mail-layouts/[id]`, 새로 만들기 `new`(기본 레이아웃 값으로 채움, 이름 비움) · 복사 `new?from=`.
- **메모리 목업**: `composables/template/api/mock/mail-template-mock.ts` 하나가 종류·템플릿·레이아웃 상태를 같이 둔다(`useMailTemplateApi`·`useMailLayoutApi` 가 부른다, 공용 `common/mock/mock-util.ts`). 새로고침하면 처음 상태. `?mock=` 스위치: `template-error`(목록 3개 500)·`template-detail-error`(상세·편집 화면 500)·`template-empty`(종류·템플릿 없음)·`template-denied`(읽기 403)·`template-readonly`(`template.manage` 없음)·`template-save-error`(쓰기 500)·`template-in-use`(템플릿 삭제 409)·`layout-in-use`(레이아웃 삭제 409)·`template-test-fail`·`template-test-limit`(429)·`template-history-error`(이력 500).
- **권한**: `template.read`(READ)·`template.manage`(CONFIG — 레이아웃 포함)는 프론트 카탈로그에만 있는 후보다. 서버 `Permission`·`AdminScreenRegistry` 에 없어 지금은 최고 관리자에게만 메뉴가 보인다(서버 단계에서 추가). `template.read` 만 있으면 세 화면 모두 읽기 전용(입력 잠금, 저장 줄·만들기·복사·삭제·시험 발송 없음).
- **종류**: 시스템 메일 5종 + 이메일을 보낼 수 있는 알림 이벤트 5종(`OPERATOR_STATUS_CHANGED_ME`·`OPERATOR_LOCKED_BY_FAILURES`·`PASSWORD_CHANGED_ME`·`PASSWORD_RESET_DONE_ME`·`BATCH_RUN_FAILED`). 기본 템플릿 = 지금 메시지 번들 문구, 모두 기본 레이아웃. 운영자 임시 비밀번호 변수 `operatorName`·`temporaryPassword`(필수)·`adminUrl`(링크).
- **레이아웃 목업 4개**: 기본 레이아웃(builtin — 로고·사이트 이름, 띠 없음, 꼬리말 「발신 전용」) · 보안 안내(빨강 띠, 보안 안내 꼬리말 2줄 — 템플릿 2개가 씀) · 공지(파랑 띠, 인사말이 있는 틀 — 쓰는 템플릿 없음, 지울 수 있음) · 간단(로고·사이트 이름·꼬리말 없음 — 템플릿 1개).
- **레이아웃 모양**: `{ name, showLogo, showSiteName, headerColor(팔레트 8색 | null), footer: {ko, en}, frames: {ko, en} }`. 틀은 간단 서식 HTML, `{{content}}` 정확히 한 번(`fields.frames.ko|en`) — 앞뒤 글은 본문과 같은 글씨. 꼬리말은 **평문**(줄바꿈 유지, 500자, 공통 변수만 — `{{content}}` 불가)이고 가는 줄 밑 작은 회색 글씨. 띠 색이 있으면 머리말(로고·사이트 이름)을 띠 안에 흰 글씨로, 머리말이 비면 얇은 띠만. 로고는 사이트 설정 「일반」의 로고(목업은 없음 — 「로고 올리기」 링크). 이름 50자.
- **템플릿의 레이아웃 고르기**: 새로 만들기는 기본 레이아웃, 복사는 원본의 레이아웃. 미리보기·시험 발송은 고른 레이아웃(저장 전 값)으로. 기본 템플릿의 레이아웃은 기본 레이아웃 고정(읽기 전용). 쓰는 템플릿이 있는 레이아웃은 삭제를 잠그고(서버 409 `LAYOUT_IN_USE`), 기본 레이아웃은 고치기·지우기 불가(409 `LAYOUT_BUILTIN`).
- **조건**: 알림 = 심각도·받는 운영자 언어, 인증 = 가입 방식(EMAIL·SOCIAL), 초대 = 역할(목업 역할 3개). 임시 비밀번호·승인번호·메일 시험은 조건 없음(규칙 구획이 「조건 없음」 안내). 규칙 10개까지, 같은 조건이 위에 있으면 「쓰이지 않음」 경고만(저장은 됨). 규칙의 템플릿은 언어와 상관없이 고른다.
- **본문 저장 모양**: 간단 서식 HTML + `{{변수}}`. 버튼 블록은 `<p data-mail-button=""><a href="{{링크 변수}}">글자</a></p>` 하나 — 에디터 안에서는 `{{button}}` 한 줄로 보이고 글자·링크는 에디터 아래 칸에서 고른다(간단 에디터가 단추를 못 그려서). 제목에 `[사이트 이름]` 머리말은 넣지 않는다(보낼 때 붙는다 — 미리보기는 붙여 보인다).
- **검사(서버도 같은 규칙)**: 이름 50자·제목 200자(한 줄)·본문 20000자·버튼 글자 40자(가정). 모르는 변수, 보안 메일 필수 변수(제목·본문·버튼 링크 어디에도 없음), 버튼 2개 이상, 링크 변수 아닌 버튼 → `fields.name|subject|bodyHtml`(버튼 문제도 `bodyHtml`), 레이아웃 없음 → `fields.layoutId`. 직접 쓴 링크의 http·https 아닌 `href` 는 저장 때 걷는다.
- **새로 만들기·복사**: 바로 만들지 않고 편집 화면을 「새 템플릿/레이아웃」으로 열어 [저장] 때 `POST` 한다 — 만들면 그 편집 주소로 바뀐다. 이름 기본값: 새 템플릿 빈칸, 복사 「원본 (복사)」(기본 템플릿 복사는 「종류 이름 (복사)」). 새 템플릿에서 종류를 바꾸면 그 종류·언어의 기본 템플릿 내용으로 다시 채운다(고친 내용이 있으면 묻는다), 내용을 안 고쳤으면 언어를 바꿀 때도 그 언어의 기본 내용으로. 만든 뒤에는 종류를 바꿀 수 없다.
- **언어 바꾸기**: 그 언어의 「쓰는 템플릿」인 사용자 템플릿은 언어를 못 바꾼다(`fields.locale`).
- **삭제**: 템플릿은 목록·편집 화면 모두(쓰는 중 — 언어별 기본·규칙 — 이면 단추를 잠그고 서버도 409 `TEMPLATE_IN_USE`), 레이아웃도 목록·편집 화면 모두(쓰는 템플릿이 있으면 잠금·409 `LAYOUT_IN_USE`). 확인 후 바로, 실패는 토스트 8초.
- **미리보기**: 서버가 견본 값을 채우고 레이아웃을 씌운 문서 HTML 을 주고, 화면은 `iframe srcdoc` + 빈 `sandbox` 로만 보인다(400ms 늦춰 다시 받음). 레이아웃 편집 화면은 `kind` 없이(공통 변수만) 견본 본문 한 줄 + 저장 전 레이아웃 값(`preview` 의 `layout`). 목업은 `UtilMailTemplate.render`(서버 메일과 같은 인라인 스타일).
- **시험 발송**: 템플릿 편집 화면 미리보기 구획의 단추 — 고치는 중인 값(`subject`·`bodyHtml`·`layoutId`)으로 보낸다(기본 템플릿도 `template.manage` 가 있으면 보낼 수 있다). 대화상자는 메일 서버 설정과 같은 부품(`MailTestDialog`). 결과 모양·429 는 메일 설정 시험과 같다.
- **변경 이력**: 템플릿·레이아웃 편집 화면 제목 옆 「변경 이력」 → 오른쪽 `SettingHistoryDrawer`(처음 열 때 받음, 저장 뒤 받은 적 있으면 첫 쪽부터 다시). 템플릿 바뀐 칸 `name|locale|layoutId|subject|bodyHtml`(레이아웃은 이름, 본문은 글자 요약 80자), 레이아웃 바뀐 칸 `name|showLogo|showSiteName|headerColor|footerKo|footerEn|frameKo|frameEn`(켜고 끄기는 `"true"/"false"`, 띠 색은 서버가 요청 언어의 색 이름, 틀은 글자 요약). `labelKey` = `settingHistory.field.<칸>`. 메일 종류 저장의 이력은 화면에 없다(감사 로그 후보).
- **떠나기 확인**: 종류 상세·템플릿 편집·레이아웃 편집에서 저장하지 않은 변경이 있는 채 다른 화면(또는 같은 화면의 다른 항목 — 복사해서 만들기)으로 가면 묻는다. 새로 만들기는 처음 채운 값에서 고쳤을 때만.
- **응답 언어**: 변수 설명·조건 이름·종류 이름·기본 템플릿/레이아웃 이름·오류 문구는 서버가 요청 언어로 줄 글이라 목업은 한국어만 준다.

### 목업 가정 — 개정 2 받는 사람 구분 (2026-10-23, 사용자 확인 전 — 위 가정 중 이와 다른 것은 이것이 대체)
- **모양**: 종류·템플릿(목록·하나)에 `audience: MEMBER|OPERATOR`(템플릿은 종류의 것 — 저장 요청에는 없다), 종류 묶음 `group: ACCOUNT|NOTIFICATION`(이전 `SYSTEM` 은 `ACCOUNT`), 레이아웃(목록·하나·저장)에 `audience: MEMBER|OPERATOR|ANY`. 레이아웃 하나에 `templateCounts: {MEMBER, OPERATOR}`(받는 사람별 쓰는 템플릿 수), `variables` 는 쓰는 대상별 `{MEMBER, OPERATOR, ANY}`. 목록 거르기 `GET kinds|templates|mail-layouts?audience=`(선택 — 화면은 전부 받아 화면에서 거른다). 이력 바뀐 칸에 레이아웃 `audience`(값은 「회원·운영자·모두」 — 서버가 요청 언어로).
- **종류**: 회원 — 계정·보안 메일 1종(이메일 인증). 운영자 — 계정·보안 메일 4종(초대·임시 비밀번호·재설정 승인번호·메일 서버 시험) + 알림 메일 5종(관리자 알림 이벤트, 영역 보안 4 · 시스템 1). 알림 메일은 묶음 이름 옆에 영역(「알림 메일 · 보안」).
  - **「준비 중」(목업 전용 — 화면 확인용)**: 회원 메일 6종(계정·보안: 비밀번호 찾기 · 제재 안내 · 휴면 전환 예정 / 알림: 내 글에 댓글 · 내 댓글에 답글 · 포인트 회수)을 메일 종류 목록에만 흐리게 「준비 중」 딱지로 보이고 누를 수 없다. API 가 아니라 `mockPlannedKinds()`(목업 파일)가 주고, 템플릿 고르기·목록에는 나오지 않는다. **서버 단계에서는 실제 있는 종류만 준다**(없는 기능은 감춘다 — 서버가 붙으면 이 줄과 함수를 지운다).
- **공통 변수**: 회원 `siteName`·`siteUrl`(공개 사이트 주소)·`recipientName`, 운영자 `siteName`·`adminUrl`(관리 사이트 주소)·`recipientName`. 운영자 임시 비밀번호의 종류 변수였던 `adminUrl` 은 공통 변수로 옮겼다. 레이아웃 `ANY` 는 `siteName`·`recipientName` 만.
  - 검사: 다른 받는 사람의 공통 변수(운영자 메일·운영자 레이아웃의 `{{siteUrl}}`, 회원 쪽의 `{{adminUrl}}`, `ANY` 레이아웃의 둘 다)는 「이 메일에서는 쓸 수 없는 변수예요: {{siteUrl}} — 회원 메일에서만 쓸 수 있어요.」(문제 코드 `AUDIENCE_VARIABLE`, 서버는 같은 `fields.subject|bodyHtml|footer.*|frames.*`). 그 밖의 모르는 변수는 이전 문구 그대로. 화면은 받는 사람별 변수 이름을 `UtilMailTemplate` 에 두고 가르는 데만 쓴다(쓸 수 있는 목록은 서버가 준다).
  - 변수 메뉴·미리보기 견본 값도 받는 사람(레이아웃은 쓰는 대상)을 따른다 — 회원 `https://roboyam.example.com`, 운영자 `https://admin.roboyam.example.com`.
- **레이아웃 목업 5개**: 「회원 기본」(builtin, 회원 — 로고·사이트 이름, 꼬리말 「{{siteName}}에서 보낸 발신 전용 메일」 + `{{siteUrl}}`) · 「운영자 기본」(builtin, 운영자 — 꼬리말 「관리 사이트의 운영자에게 보내는 메일」 + 「관리 사이트: {{adminUrl}}」) · 보안 안내(모두 — 회원 인증 템플릿 1 · 운영자 배치 실패 템플릿 1이 씀) · 공지(회원, 쓰는 템플릿 없음) · 간단(운영자, 초대 템플릿 1). 기본 템플릿은 받는 사람의 기본 레이아웃을 쓴다. 두 기본 레이아웃 모두 띠 없음(가정).
- **화면**:
  - 메일 종류 목록: 맨 위 「회원 메일 / 운영자 메일」 `SelectButton`(주소 `?audience=`, 없거나 모르는 값이면 회원) + 받는 사람별 공통 변수 안내 한 줄 → 묶음(계정·보안 메일 / 알림 메일)마다 제목·종 수·표(이전 「묶음」 열은 없앴다, 알림 메일은 줄에 영역). 빈 묶음은 「이 묶음에는 아직 메일이 없어요」. 종류 상세 머리에 「받는 사람: 회원」 딱지, 「목록으로」는 그 받는 사람 목록으로.
  - 템플릿 목록: 「받는 사람」 열(아이콘 + 회원/운영자) + 거르기 `SelectButton` 전체/회원/운영자(주소 `?audience=`). 종류 고르기는 받는 사람별 묶음이고 받는 사람을 거르면 그 묶음만 — 받는 사람을 바꿀 때 맞지 않는 종류 거르기는 푼다. 「새 템플릿」은 거른 종류(`?kind=`), 없으면 거른 받는 사람(`?audience=` → 그 받는 사람의 첫 종류).
  - 템플릿 편집: 머리에 「받는 사람: …」 딱지, 칸 「받는 사람」(읽기 전용 — 「메일 종류에서 정해져요」). 종류 고르기는 「회원 메일 · 계정·보안 메일」 같은 받는 사람 › 묶음 묶음. 레이아웃 고르기는 받는 사람이 같거나 `ANY` 인 것만(`ANY` 는 「이름 (모두)」). 새 템플릿은 그 받는 사람의 기본 레이아웃으로 시작하고, 새로 만들기에서 종류를 바꿔 받는 사람이 바뀌면 — 고른 레이아웃이 앞 받는 사람의 기본이었거나 새 받는 사람에게 맞지 않을 때만 — 새 받는 사람의 기본 레이아웃으로 바꾼다. 서버는 맞지 않는 레이아웃을 400 `fields.layoutId` 로 거절한다.
  - 레이아웃 목록: 「쓰는 대상」 열 + 거르기 `SelectButton` 전체/회원/운영자/모두(값 그대로 맞는 것 — 「회원」은 `ANY` 를 넣지 않는다, 주소 `?audience=`). 「새 레이아웃」은 회원·운영자로 거른 중이면 `new?audience=` → 그 받는 사람의 기본 레이아웃 값으로 시작(없으면 회원 기본).
  - 레이아웃 편집: 이름 아래 「쓰는 대상」 `SelectButton`(회원/운영자/모두). 쓰는 템플릿의 받는 사람과 맞지 않는 대상은 고를 수 없게 잠그고(예: 운영자 템플릿이 쓰는 레이아웃의 「회원」) 까닭 한 줄 — 서버도 400 `fields.audience`(칸 아래 그대로). 대상을 바꾸면 틀·꼬리말 변수 메뉴·검사·미리보기가 그 대상의 공통 변수로 바뀐다. 머리에 「받는 사람: …」 딱지. 기본 레이아웃은 대상도 바꿀 수 없다(읽기 전용).
- `?mock=` 스위치는 그대로다(`template-empty` 면 「준비 중」 줄도 없다).
