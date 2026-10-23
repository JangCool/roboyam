# 메일 템플릿 계약 (화면 목업 단계 — 2026-10-23)

**의도**: 서버가 보내는 메일의 제목·본문을 운영자가 고칠 수 있게 한다. 메일 종류(시스템 메일과 관리자 알림 이벤트)마다 코드가 주는 **기본 템플릿**이 있고, 운영자는 기본을 복사한 **사용자 템플릿**을 여러 개 만들어 종류마다 「기본으로 쓸 템플릿」과 「특수한 상황에서 쓸 템플릿(조건 규칙)」을 정한다. 지금은 문구가 코드(`AuthMailTemplate`·`OperatorMailTemplate`·`NotificationMailTemplate`)와 메시지 번들에 박혀 있어 바꿀 수 없다.
**결정(사용자 "추천대로", 2026-10-23)**: ① 본문은 단순 서식(간단 에디터 — `@roboyam/rich-editor` SIMPLE) + 변수 + 공통 레이아웃 ② 조건은 종류마다 코드가 정한 선택지만(자유 조건식 없음) ③ 1단계는 **이메일**만(Slack·Mattermost 글·회원 앱 알림 문구는 같은 구조로 2단계) ④ 새 권한 `template.read`(조회)·`template.manage`(편집·선택·규칙).

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
