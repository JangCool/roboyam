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
