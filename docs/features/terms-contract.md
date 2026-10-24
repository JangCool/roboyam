# 약관 계약 (결정 2026-10-24 · 구현 2026-10-24)

**의도**: 이용약관·개인정보처리방침 본문을 관리자 앱에서 쓰고 버전으로 게시하고, 공개 사이트에서 읽게 한다. 가입 때 동의한 버전을 남긴다. 가입·로그인 화면의 약관 「보기」 링크는 관리자 옵션으로 켜고 끈다. community 에는 약관 기능이 없어 로보얌에서 새로 정한다.

## 결정 (2026-10-24, 사용자)
- **「보기」 링크는 관리자 옵션**: 관리자 앱 설정에서 「가입·로그인 화면에 약관 보기 링크 표시」를 켜고 끈다. 끄면 가입 약관 동의(`TermsAgreement`)와 로그인 카드 아래(레이아웃 `auth`)의 약관 링크를 모두 숨긴다. 동의 체크는 옵션과 관계없이 필수다. 지금 화면의 코드 상수 `SHOW_TERMS_LINKS` 는 이 옵션으로 바꾼다.
- **버전**: 약관 종류(`TERMS`·`PRIVACY`)마다 본문을 버전으로 게시한다. 게시한 버전은 고치지 않고 새 버전을 낸다(시행일 포함).
- **약관이 바뀌면 알림만**: 새 버전을 게시하면 공개 사이트에 변경 안내만 보인다. 기존 회원에게 다시 동의를 받는 절차는 두지 않는다(필요해지면 더한다).
- **가입 때 동의 버전 저장**: 지금 `users.terms_version` 에 자리표시 `'install'`(설치 마법사 루트 계정)이 들어간다. 가입은 그때 게시된 버전을 저장한다.

## 결정 (2026-10-24, 구현 세션)
- **본문 형식 = 서식 HTML**(`@roboyam/rich-editor`, 이미지·유튜브·글자색 없음). 서버가 허용 목록으로 정화한다: `p br strong em u s a ul ol li blockquote h2 h3 hr`(링크 http·https·mailto, 새 탭 `rel="nofollow noopener noreferrer"`). 그 밖 태그·속성은 버리고 글자는 남긴다. 공개 화면은 정화된 HTML 을 `v-html` 로 그린다(서버 정화본만). 본문 상한 200,000자(정화 후), 보이는 글자가 없으면 거절.
- **초안 → 게시**: 종류마다 초안 하나(저장·삭제 가능, 게시본은 고치지 않는다). [게시] 는 초안 본문으로 새 버전(번호 1, 2, 3…)을 만들고 초안을 비운다. 게시할 때 **시행일**(날짜, 사이트 표시 시간대 기준)과 선택 **변경 요지**(0~200자, 변경 안내에 보인다)를 받는다.
- **시행일 규칙**: 시행일은 오늘(사이트 시간대) 이후이고, 그 종류의 마지막 게시본 시행일 이후(같은 날 허용)여야 한다. **지금 약관** = 시행일이 오늘 이전인 가장 큰 버전. 시행일이 미래인 게시본은 **시행 예정**이다(여럿이면 가장 이른 것을 안내).
- **변경 안내 모양**(재동의 없음): ① 공개 사이트 기본 레이아웃 위 얇은 띠 「이용약관이 바뀌어요 · 시행 YYYY-MM-DD [보기] [닫기]」 — 시행 예정 버전이 있거나, 지금 약관이 2번 이상이고 시행된 지 7일 안일 때. 닫으면 그 브라우저에서 그 종류·버전은 다시 안 보인다(localStorage). ② `/terms`·`/privacy` 맨 위 안내 상자 — 시행 예정이 있으면 「YYYY-MM-DD 부터 바뀐 약관이 시행돼요 [바뀐 약관 보기]」 + 변경 요지. ③ 같은 화면 아래 「이전 버전」 목록(버전·시행일)으로 지난 본문을 볼 수 있다.
- **옵션 「가입·로그인 화면에 약관 보기 링크 표시」 기본값 = 끔**(새 설치에는 게시본이 없어 링크가 빈 화면으로 간다). 저장은 `site_settings.terms_links_visible`. 관리자 약관 화면 맨 위에서 켜고 끈다(`terms.manage`).
- **빈 상태**: 게시본이 없는 종류의 공개 화면은 「아직 게시된 이용약관이 없어요」. 가입은 막지 않는다(동의 체크는 필수 그대로) — 동의 버전에는 그 종류를 `0` 으로 남긴다. 링크 옵션이 켜져 있어도 게시본이 없는 종류의 링크는 그린다(빈 상태 화면으로 간다).
- **동의 버전 문자열** = `T{이용약관 버전}.P{개인정보처리방침 버전}`(지금 약관 기준, 게시본 없으면 0) — 예 `T3.P2`, 새 설치 `T0.P0`. `users.terms_version varchar(16)` 에 맞는다. 설치 마법사의 `'install'` 은 그대로 둔다.
- **권한**: `terms.read`(조회) · `terms.manage`(초안·게시·옵션). 없으면 메뉴 숨김 / 편집 단추 숨김·읽기 전용.

## 화면
- 관리자: `/settings/terms`(설정 메뉴 「시스템」 묶음 끝, 메뉴 키 `terms`). 위: 링크 옵션 스위치. 아래: 종류 탭(이용약관·개인정보처리방침) — 상태(지금 버전·시행일, 시행 예정), 초안 편집기 [초안 저장]·[초안 삭제]·[게시…](시행일·변경 요지 대화상자, 「게시한 버전은 고칠 수 없어요」), 이력 표(버전·시행일·게시 시각·게시자·상태 지금/예정/지난) → 본문 보기 대화상자 · [이 본문으로 초안 만들기].
- 공개: `/terms`·`/privacy`(기본 레이아웃, 비회원도). `?version=N` 으로 지난·예정 버전.

## API
(응답은 `ApiResponse`. 날짜 `effectiveDate` 는 `YYYY-MM-DD`, 시각은 ISO-8601. 경로의 `{kind}` 는 `terms`·`privacy`, 본문의 `kind` 는 `TERMS`·`PRIVACY`.)

관리자(`server/admin`, `/api/admin`):
| | 권한 | 요청 → 응답 |
|---|---|---|
| `GET /terms` | `terms.read` | → `{ showLinks, kinds:[{kind, current:{version,effectiveDate,publishedAt}\|null, upcoming:{version,effectiveDate,publishedAt}\|null, draftUpdatedAt\|null, versionCount}] }` |
| `GET /terms/{kind}` | `terms.read` | → `{ kind, draft:{body,updatedAt,updatedBy}\|null, versions:[{version,effectiveDate,publishedAt,publishedBy,changeNote,status:'CURRENT'\|'UPCOMING'\|'PAST'}] }`(최신 버전 먼저) |
| `GET /terms/{kind}/versions/{version}` | `terms.read` | → `{ version,effectiveDate,publishedAt,publishedBy,changeNote,status,body }` · 없으면 `404` |
| `PUT /terms/{kind}/draft` | `terms.manage` | `{body}` → `{body,updatedAt,updatedBy}` · `400 VALIDATION fields.body` |
| `DELETE /terms/{kind}/draft` | `terms.manage` | → `204`(없어도 성공) |
| `POST /terms/{kind}/publish` | `terms.manage` | `{effectiveDate, changeNote?}` → 새 버전(`GET …/versions/{v}` 모양) · `fields.effectiveDate`·`changeNote`, 초안 없음 `fields.draft` |
| `PUT /terms/settings` | `terms.manage` | `{showLinks}` → `{showLinks}` |
- `publishedBy`·`updatedBy` 는 운영자 표시 이름(없으면 null). 감사: `TERMS_PUBLISH`(대상 `TERMS`/종류, after 버전·시행일) · `TERMS_LINKS_UPDATE`(바뀐 때만). 초안 저장은 감사하지 않는다.

공개(`server/api`, 비로그인):
| | 요청 → 응답 |
|---|---|
| `GET /api/terms/summary` | → `{ showLinks, kinds:[{kind, current:{version,effectiveDate}\|null, notice:{version,effectiveDate,upcoming:boolean,changeNote}\|null}] }` — 레이아웃 `auth`·`TermsAgreement`(링크)·기본 레이아웃(띠)이 쓴다 |
| `GET /api/terms/{kind}` | → `{ kind, current:{version,effectiveDate,publishedAt,changeNote,body}\|null, upcoming:{version,effectiveDate,changeNote}\|null, history:[{version,effectiveDate,status}] }`(빈 상태 = `current` null) |
| `GET /api/terms/{kind}/versions/{version}` | → `{version,effectiveDate,publishedAt,changeNote,status,body}` · 없으면 `404` |
- 모르는 `{kind}` 는 `404`. 캐시는 서버 사본 짧게 + `Cache-Control: public, max-age=60`(사이트 정보와 같은 방식).

## 가입 연결 (합칠 때 메인 세션)
- shared 조회 메서드: `com.community.terms.service.TermsService#currentAgreementVersion()` → `T{n}.P{m}`. 가입(`POST /api/auth/signup`)과 소셜 가입 마무리(`SocialAccountWriter.SIGNUP_TERMS_VERSION = "v1"` 상수 자리)가 이 값을 `users.terms_version` 에 넣는다.

## 미확정
- 오픈 전 법무 검토(개인정보 수집 항목·목적·보유 기간 고지 요건, 변경 공지 기간 — 지금은 시행일을 오늘로도 고를 수 있다).
- 회원에게 메일·알림으로 변경을 알리는 것은 하지 않았다(필요하면 회원 이벤트로 더한다).

## 서버 구현 결과 (2026-10-24)
- **가입 동의 버전**: `com.community.terms.service.TermsService#currentAgreementVersion()`(server/shared, `@Service`) → `T{n}.P{m}`. 가입·`SocialAccountWriter` 연결은 아직 안 했다(합칠 때 메인).
- **마이그레이션**: shared `V62__terms.sql` — `terms_versions`(UNIQUE(kind, version), FK 없는 `published_by`), `terms_drafts`(PK = 종류), `site_settings.terms_links_visible`. 권한 부여 데이터는 없다(역할은 사용자가 만들고 루트는 모든 권한, 카탈로그는 기동 때 코드에서 맞춘다).
- **계약과 달라진 점**: `DELETE /terms/{kind}/draft` 는 `204`(본문 없음 — 다른 삭제 API 와 같다). 공개 `history` 는 모든 게시본(지금·예정·지난, 최신 먼저)이고 화면이 `status` 로 거른다. 변경 안내의 「시행된 지 7일 안」= 시행일부터 0~6일째. 공개 사본은 20초(`PublicSiteService` 와 같다) — 관리자 게시·옵션이 공개 화면에 최대 20초 + 브라우저 60초 늦게 보인다. 지난·예정 버전 본문은 사본 없이 그때 읽는다.
- **감사 대상 이름**: `TERMS` 대상은 종류로 이름을 낸다(`audit.targetName.TERMS.TERMS`=이용약관 · `.PRIVACY`=개인정보처리방침, `AuditQueryService.targetName`).
- **검증 문구 키**: `terms.body.required`·`.max`, `terms.effectiveDate.required`·`.invalid`·`.past`·`.beforeLast`, `terms.changeNote.max`, `terms.draft.missing`, `terms.showLinks.required`(`PUT /terms/settings` 의 `showLinks` 가 없을 때 `fields.showLinks`).

## 화면 구현 결과 (2026-10-24 — 목업 없이 위 API 를 바로 부른다)
- 관리자 `pages/settings/terms.vue` + `components/terms/TermsPublishDialog.vue`·`TermsVersionDialog.vue`, `composables/terms/api/useTermsApi(.types).ts`. 옵션 스위치는 누르면 바로 저장(실패 시 되돌림). [게시…] 는 저장된 초안이 있고 저장 안 한 변경이 없을 때만. 「이 본문으로 초안 만들기」는 편집기에만 넣는다([초안 저장] 을 눌러야 남는다). 시행일 최소값(오늘·마지막 시행일)은 화면 편의고 판정은 서버.
- 공개 `pages/terms.vue`·`privacy.vue` → `components/terms/TermsDocumentView.vue`, 띠 `components/terms/TermsChangeNotice.vue`(기본 레이아웃 `#after-header` 끝, 그 종류 화면에서는 숨김, 닫기 localStorage `roboyam:terms-notice:<KIND>`), 요약 공유 상태 `composables/terms/useTermsSummary.ts`(상태 키 `terms-summary`). 띠 문구: 시행 예정 「…이 바뀌어요」, 최근 시행 「…이 바뀌었어요」. 날짜는 표시 날짜 형식(`time.serviceDate`).
- 이름은 용어 기능(`useSiteTerms`·`/api/site/terms`)과 겹치지 않게 `composables/terms/`·`Terms*`·i18n `terms` 로 뒀다.
- **편집기 수준 = `DOCUMENT`**(2026-10-24 사용자 요청으로 `@roboyam/rich-editor` 에 더함 — SIMPLE + 제목 h2·h3·구분선, 서버 허용 목록과 같다). `ADVANCED` 는 서버가 버리는 코드·글자색·이미지·유튜브까지 켜서 쓰지 않는다.

## 미확정 (구현 뒤)
- 실제 서버에서 API·화면·Flyway V62 적용을 아직 확인하지 않았다.
