# 메일 템플릿 계약 (화면 목업 단계 — 2026-10-23)

**의도**: 서버가 보내는 메일의 제목·본문을 운영자가 고칠 수 있게 한다. 메일 종류(시스템 메일과 관리자 알림 이벤트)마다 코드가 주는 **기본 템플릿**이 있고, 운영자는 기본을 복사한 **사용자 템플릿**을 여러 개 만들어 종류마다 「기본으로 쓸 템플릿」과 「특수한 상황에서 쓸 템플릿(조건 규칙)」을 정한다. 지금은 문구가 코드(`AuthMailTemplate`·`OperatorMailTemplate`·`NotificationMailTemplate`)와 메시지 번들에 박혀 있어 바꿀 수 없다.
**결정(사용자 "추천대로", 2026-10-23)**: ① 본문은 단순 서식(간단 에디터 — `@roboyam/rich-editor` SIMPLE) + 변수 + 공통 레이아웃 ② 조건은 종류마다 코드가 정한 선택지만(자유 조건식 없음) ③ 1단계는 **이메일**만(Slack·Mattermost 글·회원 앱 알림 문구는 같은 구조로 2단계) ④ 새 권한 `template.read`(조회)·`template.manage`(편집·선택·규칙).

## 화면
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

## API 후보 (목업이 부르는 모양 — 서버 단계에서 확정)
- `GET /api/admin/mail-templates/kinds` → 종류 목록(묶음·보안 여부·변수 목록·조건 선택지·쓰는 템플릿·규칙 수)
- `GET /api/admin/mail-templates/kinds/{kind}` → `{ kind, defaults: {ko, en}(기본 템플릿 id), selected: {ko, en}, rules: [{id, condition, templateId}], templates: [...] }`
- `PUT /api/admin/mail-templates/kinds/{kind}` `{ selected, rules }`
- `GET/POST/PUT/DELETE /api/admin/mail-templates/templates[/{id}]` — `{ id, kind, name, locale, subject, bodyHtml, builtin, updatedAt, updatedByName }`(기본 템플릿은 `builtin=true`, 읽기 전용)
- `POST /api/admin/mail-templates/preview` `{ kind, subject, bodyHtml, locale }` → `{ subject, html }`(견본 값, 공통 레이아웃 적용)
- `POST /api/admin/mail-templates/test` `{ kind, templateId | (subject, bodyHtml), to }` → `{ succeeded, reason, detail }`(메일 설정 시험과 같은 모양·한도)
- `GET/PUT /api/admin/mail-templates/layout`
- `GET /api/admin/mail-templates/templates/{id}/history` — 설정 이력 모양(`{items, total, page, size}`, 바뀐 칸 `subject|bodyHtml|name`)
- 오류: 400 `VALIDATION`(`fields.subject|bodyHtml|name` — 모르는 변수·필수 변수 없음·길이), 409 `TEMPLATE_IN_USE`(쓰는 중 삭제), 403.

## 미확정
1. 조건 선택지의 종류별 최종 목록(서버 단계).
2. 2단계: Slack·Mattermost 글 문구, 회원 알림(앱 안) 문구, 회원 알림 메일.
3. 사용자 템플릿 수 상한(종류당 20 가정), 본문 길이 상한.

## 목업 가정 (화면 목업 2026-10-23 — 사용자 확인 전)
- **메모리 목업**: `composables/template/api/mock/mail-template-mock.ts`(+ 공용 `common/mock/mock-util.ts`). 새로고침하면 처음 상태. `?mock=` 스위치: `template-error`·`template-detail-error`·`template-empty`·`template-denied`(읽기 403)·`template-readonly`(`template.manage` 없음)·`template-save-error`·`template-in-use`(삭제 409)·`template-test-fail`·`template-test-limit`(429)·`template-history-error`.
- **권한**: `template.read`(READ)·`template.manage`(CONFIG — `site.manage`·`notification.manage` 와 같은 분류)는 프론트 카탈로그에만 있는 후보다. 서버 `Permission`·`AdminScreenRegistry` 에 없어 지금은 최고 관리자에게만 메뉴가 보인다(서버 단계에서 추가).
- **종류**: 시스템 메일 5종 + 이메일을 보낼 수 있는 알림 이벤트 5종(`OPERATOR_STATUS_CHANGED_ME`·`OPERATOR_LOCKED_BY_FAILURES`·`PASSWORD_CHANGED_ME`·`PASSWORD_RESET_DONE_ME`·`BATCH_RUN_FAILED`). 기본 템플릿 = 지금 메시지 번들 문구. 운영자 임시 비밀번호 변수 `operatorName`·`temporaryPassword`(필수)·`adminUrl`(링크).
- **조건**: 알림 = 심각도·받는 운영자 언어, 인증 = 가입 방식(EMAIL·SOCIAL), 초대 = 역할(목업 역할 3개). 임시 비밀번호·승인번호·메일 시험은 조건 없음(규칙 구획이 「조건 없음」 안내). 규칙 10개까지, 같은 조건이 위에 있으면 「쓰이지 않음」 경고만(저장은 됨). 규칙의 템플릿은 언어와 상관없이 고른다.
- **본문 저장 모양**: 간단 서식 HTML + `{{변수}}`. 버튼 블록은 `<p data-mail-button=""><a href="{{링크 변수}}">글자</a></p>` 하나 — 에디터 안에서는 `{{button}}` 한 줄로 보이고 글자·링크는 에디터 아래 칸에서 고른다(간단 에디터가 단추를 못 그려서). 제목에 `[사이트 이름]` 머리말은 넣지 않는다(보낼 때 붙는다 — 미리보기는 붙여 보인다).
- **검사(서버도 같은 규칙)**: 이름 50자·제목 200자(한 줄)·본문 20000자·버튼 글자 40자(가정). 모르는 변수, 보안 메일 필수 변수(제목·본문·버튼 링크 어디에도 없음), 버튼 2개 이상, 링크 변수 아닌 버튼 → `fields.name|subject|bodyHtml`(버튼 문제도 `bodyHtml`). 직접 쓴 링크의 http·https 아닌 `href` 는 저장 때 걷는다(에디터는 `mailto:` 도 받으므로 서버가 걷는다).
- **언어 바꾸기**: 그 언어의 「쓰는 템플릿」인 사용자 템플릿은 언어를 못 바꾼다(`fields.locale`).
- **공통 레이아웃**: `{ showLogo, showSiteName, frames: {ko, en}, logoUrl, siteName, variables }` — 틀은 간단 서식 HTML, `{{content}}` 정확히 한 번(`fields.frames.ko|en`), `{{content}}` 뒤의 글은 꼬리말(작은 회색). 로고는 사이트 설정 「일반」의 로고(목업은 없음). 레이아웃 미리보기는 `kind` 없이(공통 변수만) 저장 전 값(`preview` 의 `layout`)으로 — `preview` 의 `kind` 는 선택.
- **미리보기**: 서버가 견본 값을 채우고 레이아웃을 씌운 문서 HTML 을 주고, 화면은 `iframe srcdoc` + 빈 `sandbox` 로만 보인다(400ms 늦춰 다시 받음). 목업은 `UtilMailTemplate.render`(서버 메일과 같은 인라인 스타일).
- **시험 발송**: 고치는 중인 값(`subject`·`bodyHtml`)으로 보낸다. 대화상자는 메일 서버 설정과 같은 부품(`MailTestDialog` — 이번에 `mail.vue` 에서 떼어 냄). 결과 모양·429 는 메일 설정 시험과 같다.
- **변경 이력**: 편집 서랍 제목 옆 「변경 이력」 → 오른쪽 `SettingHistoryDrawer`(처음 열 때 받음). 바뀐 칸 `name|locale|subject|bodyHtml`, 본문은 글자 요약 80자, `labelKey` = `settingHistory.field.<칸>`. 레이아웃·종류 저장의 이력은 화면에 없다(감사 로그 후보).
- **편집 화면**: 별도 화면이 아니라 서랍(운영자 상세와 같은 관례). 복사는 서랍을 「새 템플릿」으로 열고 [만들기] 때 `POST` 한다(바로 만들지 않음). 이름 기본값 「원본 (복사)」.
- **응답 언어**: 변수 설명·조건 이름·오류 문구는 서버가 요청 언어로 줄 글이라 목업은 한국어만 준다.
