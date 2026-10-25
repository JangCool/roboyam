# 공개 게시판 계약 (**화면 확정 2026-10-22** — 다음 단계는 공개 API 백엔드)

**의도**: 공개 사이트(user-web)에서 게시판 글 목록을 보고, 글과 댓글을 읽고 쓰고 고치고 지운다. 회원뿐 아니라 **비회원도** 게시판이 허용하면 글·댓글을 쓴다(이름·비밀번호·자동입력 방지). 동작 규칙(쓰기 자격·보호·지연·금지어·속도)은 관리자 게시판 설정의 **유효값**(`board-contract.md`)을 따른다.
**참고(읽기 전용)**: community `frontend/app/pages/boards/[slug].vue`·`pages/posts/**`·`pages/write.vue`, `docs/api-board.md`·`docs/api-board-settings.md`.

## 화면
| 화면 | 주소 | 기준 코드 |
|---|---|---|
| 게시판 목록 | `/boards/{slug}?prefix&sort&q&page` | `pages/boards/[slug].vue`, `components/board/BoardPost{List,Cards,Gallery}.vue` |
| 글 보기 + 댓글 | `/posts/{id}` | `pages/posts/[id]/index.vue`, `components/board/Comment*.vue` |
| 글쓰기 | `/write?board={slug}` | `pages/write.vue`, `components/board/PostEditorForm.vue` |
| 고치기 | `/posts/{id}/edit` | `pages/posts/[id]/edit.vue` |
- 요청·응답 모양의 원천: `apps/user-web/app/composables/board/api/use{Board,Post,Comment}Api.ts`(머리말 = API 후보·오류 코드) + `.types.ts`, `composables/report/api/useReportApi.ts`. 서버 규칙 흉내는 같은 폴더의 `mock/*.ts`.
- 글·댓글 ID 는 Snowflake, JSON 에서 문자열.

## 권한·정책
- 쓰기 판정은 서버가 한다. 게시판 응답의 `viewer{guest, staff, canWrite, writeBlockedReason, canComment, commentBlockedReason}` 가 화면 표시용 판정이다. 쓰기 자격 `GUEST`·등급 키·`STAFF`(·댓글 `OFF`), 쓰기 잠금, 정지·이메일 미인증 등은 `WriteBlockedReason` 으로 내려 준다.
- **비회원**: 이름(2~20)·비밀번호(4~20, 서버는 해시만 저장)·자동입력 방지 토큰(`guest.captchaToken` — 화면은 지금 자리 표시 체크, 실제 캡차는 후속). 링크·이미지 한도는 보안 설정의 비회원 값. 추천·신고는 회원만(비회원은 로그인 안내). 알림·포인트 없음.
- 비회원 작성자 표시: 이름 + 「비회원」 + **IP 앞 두 자리**(`123.45.*.*`, 서버가 비회원 글·댓글에만 마스킹해서 준다).
- 비회원 글·댓글 고치기·지우기는 비밀번호로 한다. 확인 `POST /api/posts/{id}/verify-password`, 지우기는 **`POST /api/posts/{id}/delete`·`POST /api/comments/{id}/delete`**(본문에 `password` — 비밀번호를 주소에 싣지 않는다).
- 추천은 취소 없음, 비추천 없음, 내 글 추천 불가. 신고 사유 6종(스팸·광고 / 욕설·비방 / 음란·선정 / 불법촬영물·아동성착취물 / 개인정보 노출·명예훼손 / 기타 — 기타는 설명 필수, 300자).
- 댓글 많은 글 보호(수정 막기·삭제 막기/예약 삭제/허용), 베스트 댓글(추천 기준 이상 최대 3개), 답글 1단계, 답글 있는 댓글을 지우면 자리 유지.
- 신고로 숨겨진 글·댓글은 자리만 보인다(글 직접 열기 `403 POST_HIDDEN`). 꺼진 게시판 `403 BOARD_INACTIVE`.

## 결정 (2026-10-22, 추천대로)
1. 비회원 삭제는 `POST …/delete` 본문 방식.
2. 비회원 댓글 공개 지연은 **시간 숫자를 보이지 않고** 「로그인하면 바로 볼 수 있어요」(기존 `util-comment-delay-messages.ts` 규칙).
3. 날짜 표시: 표준형의 오래된 글은 사용자 표시 설정의 날짜 형식(`time.date`)을 따른다(그누보드의 `YY-MM-DD` 고정이 아님). 유동형은 모두 상대 시간. 기준은 공개 기본값 `dateDisplay`.
4. 비회원 IP 앞 두 자리 표시.
5. 19금: 성인 인증이 없으므로 확인 단추로 이번 방문만 연다(목록에는 19 표시와 함께 나온다). 성인 인증이 생기면 다시 정한다.
6. 공개 게시판 주소는 `/boards/{slug}`(관리자 계약 미확정 5번 해소).

## API 후보
`GET /api/boards` · `GET /api/boards/{slug}` · `GET /api/boards/{slug}/posts` · `GET /api/boards/writable` · `GET /api/site/board-display`(`{dateDisplay, newPostHours}` — `/api/site/info` 에 합칠지는 서버 구현 때) · `GET/POST /api/posts…`·`PUT /api/posts/{id}`·`POST …/delete`·`…/verify-password`·`…/like` · `GET/POST /api/posts/{id}/comments`·`PUT /api/comments/{id}`·`POST …/delete`·`…/like` · `POST /api/reports`. 오류 코드는 각 컴포저블 머리말.

## 서버 상수(가정 — 관리자 설정에 없는 값)
새 글 표시 24시간, 비회원 비밀번호 최대 20자, 댓글 1~2,000자, 신고 기타 설명 300자, 베스트 댓글 최대 3개. 제목 1~100자·본문 10~200,000자(고정 규칙).

## 범위 밖 (후속)
~~에디터·이미지 올리기~~(→ 「에디터 화면」), 임시저장, 투표, 스크랩, 차단·메모, 수정 이력, 실제 캡차, 성인 인증, 스텝·매니저 처리 화면, 포인트 지급, 알림 발송, 조회수 중복 판정 저장(`post_views`).

## 추가 결정 (2026-10-22, 사용자 "권장대로")
1. **자동입력 방지**: 서버에 검증기 인터페이스(`CaptchaVerifier`)를 두고 **Cloudflare Turnstile** 구현을 기본으로 한다(무료·개인정보 수집이 적고 사용자 퍼즐 없음). 비밀키가 설정되지 않은 환경(local·설치 직후)은 「검증 안 함」 구현으로 동작하고 기동 로그에 경고를 남긴다 — 그때 비회원 글은 작성 속도·하루 한도·링크 0개·금지어로만 막힌다. 화면은 지금 자리 표시 체크를 두고, 사이트 키를 공개 정보로 받으면 Turnstile 위젯으로 바꾼다(후속).
2. **공개 표시 기본값**은 별도 경로 없이 **`GET /api/site/info` 에 `boardDisplay{dateDisplay, newPostHours}` 를 더한다**(이미 캐시·ETag 가 있고 앱 시작 때 한 번 읽는다). `GET /api/site/board-display` 후보는 쓰지 않는다.
3. **캐시**: 지금은 목록·상세 응답 캐시를 두지 않는다(색인으로 충분, community 도 관측 후 첫 쪽 캐시를 더했다). 게시판 정보·유효 정책은 서버 메모리 사본(짧은 TTL, 관리자 저장 시 무효화)으로 읽는다.

## 미확정 사항
1. Turnstile 사이트 키·비밀키를 둘 자리(관리자 설정 화면 vs 환경 변수) — 지금은 환경 변수.

## 서버 구현 결과 (2026-10-22) — 화면 연결은 후속
**DB** shared `V46__board_public.sql`: `posts`·`comments` 칸 추가(조회·추천·보이는 댓글 수, 19금·이미지 여부, 고정 시각, 숨김 까닭 `REPORTED|ILLEGAL|RIGHTS`·임시조치 끝 시각, 예약 삭제 시각, 수정 시각) · `post_likes`·`comment_likes`(대상 ID 해시 16) · `post_views`(조회 중복 방지, 열쇠 = 회원 ID 또는 IP 의 SHA-256) · `reports`(신고자·대상·사유·설명·당시 내용 스냅샷·셈 여부·상태). `R__batch_19` DB 배치 `board-post-upkeep`(매일 00:40, 예약 삭제 확정 + 조회 기록 정리). `R__batch_18` 개인정보 배치의 `reportRecord` 축이 `reports` 를 지운다.
**구조** 유효 정책·판정은 shared(`BoardCatalog` 메모리 사본 15초 · `BoardAccessPolicy` 판정 순서 · `BoardActor`) — **관리자 저장이 공개 사이트에 닿기까지 최대 15초**. 「운영진」= 그 게시판의 매니저·스텝(운영자는 공개 사이트에 로그인하지 않는다). 역할 권한(`post.write`·`comment.write`·`post.react`·`report.create`)과 게시판 규칙을 둘 다 본다(역할 표가 아직 시드되지 않았으면 제한 없음).
**API** 컴포저블 머리말 그대로(JSON 키 = `.types.ts`). 목업과 다른 점:
- 표시 기본값은 `GET /api/site/info` 의 `boardDisplay{dateDisplay, newPostHours}` — `useBoardApi.display` 의 `/api/site/board-display` 는 없다.
- 판정 순서: 댓글 끔 → 잠금(매니저·스텝 면제) → 운영진 전용 → 로그인(`401 UNAUTHENTICATED`) → 정지 `ACCOUNT_RESTRICTED` → 이메일 미인증(GUEST 게시판 제외) → 사업자 `BUSINESS_ACCOUNT_RESTRICTED` → 역할 권한 없음 `BOARD_RESTRICTED` → 등급 `GRADE_TOO_LOW`.
- 새 오류: `429 PASSWORD_ATTEMPTS_EXCEEDED`(가정: 같은 IP·같은 글/댓글 10분 5회 — DB `guest_password_attempts`(V47, 열쇠는 sha256(IP|대상)), 오래된 줄은 `board-post-upkeep` 이 정리) · `404 COMMENT_NOT_FOUND` · `403 COMMENT_HIDDEN` · `429 COMMENT_DAILY_LIMIT` · `PUT` 숨긴 글 `403 POST_HIDDEN` · 추천·신고는 정지 `403 ACCOUNT_RESTRICTED`·권한 없음 `403 BOARD_RESTRICTED` · 신고 `403 REPORT_DISABLED`·`404 REPORT_TARGET_NOT_FOUND`·`400 CANNOT_REPORT_OWN`·`403 REPORT_BLOCKED`·`429 REPORT_TOO_FAST`·`429 REPORT_DAILY_LIMIT`(한국시간 0시). 답글 대상(`replyToId`) 오류도 `fields.parentId`.
- 비회원 IP 표시: IPv6 는 앞 두 묶음(`2001:db8:*:*`). 비회원 댓글 공개 지연은 **같은 IP 의 비회원에게는 바로 보인다**(목업의 「이 브라우저」 가정). 쓰기 응답의 새 댓글은 늘 `VISIBLE`.
- 글·댓글 하루 한도 = 최근 24시간(목업과 같음, 지운 글 포함). 같은 글 반복은 지우지 않은 글만 본다. 금지어 가림(MASK)은 같은 길이의 `*`. 숨김 까닭이 무엇이든 목록 `hiddenReason` 은 `REPORTED`.
- 자동입력 방지: 비밀키(`TURNSTILE_SECRET_KEY`)가 없으면 토큰이 비어 있지만 않으면 통과(기동 WARN). 비밀키가 있으면 목업 토큰은 `fields.guest.captchaToken` 으로 거절된다.
- 자동 숨김: 불법촬영물 1건 즉시(끌 수 없음) · 권리침해는 임시조치 기간 숨김 후 **자동으로 다시 보임**(가정) · 기준 숨김은 신고 당시 최소 등급 이상인 서로 다른 회원 수. 「운영자 작성물·공지 제외」 = 고정 공지 + 그 게시판 매니저·스텝이 쓴 것. 관리자 알림 `REPORT_RECEIVED` 를 예약에서 풀어 발행한다(PER_CASE 면 대상의 첫 신고만, 링크 없음).
- 예약 삭제: 시각이 지나면 읽을 때 지운 글로 거르고, 배치가 하루 한 번 `deleted_at` 을 확정한다(관리자 글 수는 그때까지 예약 글을 센다).
**미확정(추가)**: ~~slug `writable` 충돌~~ → 예약어에 더함. ~~비밀번호 시도 제한이 서버 메모리~~ → DB 로 옮김(2026-10-22). 고정 공지를 거는 기능은 후속. ~~운영자 신고 처리·작성자/신고자 알림~~ → `report-admin-contract.md`(2026-10-24 — 신고는 대상별 사건으로 묶고, 자동 숨김 셈은 열린 사건의 신고만, 작성자 알림은 정책 `notifyAuthorOnAction`).

- 포인트 지급·회원 알림 발송(후속이던 것): `point-contract.md` · `member-notification-contract.md`(2026-10-23).

## 에디터·이미지 (2026-10-23, 서버 구현 — 화면 연결은 프론트 작업)
**의도**: community 텍스트 에디터를 가져와 게시판마다 글 에디터(없음·간단·고급)·에디터 이미지 올리기·댓글 에디터를 정한다. 관리자 칸은 `board-contract.md` 「에디터·이미지」.

### 공개 게시판 응답
`GET /api/boards`·`/writable`·`/{slug}` 게시판 칸에 유효값 `postEditor`(`NONE|SIMPLE|ADVANCED`)·`commentEditor`(`NONE|SIMPLE`)·`imageUploadAllowed`(이미 있던 칸 — **글 에디터 유효값이 `ADVANCED` 가 아니면 늘 false**). 관리자 저장이 닿기까지 최대 15초(카탈로그 사본).

### 본문 형식
- 글·댓글 요청에 `contentFormat: 'TEXT'|'HTML'`(빠지면 TEXT — 예전 클라이언트), 응답(`GET/PUT /api/posts/{id}`, 댓글 목록·쓰기·고치기)에 `contentFormat`. 행 칸 `posts.content_format`·`comments.content_format`(기존 행 TEXT), HTML 글의 평문 칸 `posts.content_text`. 파티션 표 칸 추가는 상수 기본값이라 표를 다시 쓰지 않는다(PG 18 에서 행이 있는 조각의 relfilenode 가 그대로임을 확인), CHECK 는 `NOT VALID`.
- 모르는 형식 → `400 fields.contentFormat`. 게시판 유효 글 에디터(댓글은 댓글 에디터)가 `NONE` 인데 HTML → `400 fields.contentFormat`. 에디터 게시판이어도 TEXT 는 받는다.
- **HTML 은 서버가 허용 목록으로 정화해 저장하고 정화된 것을 응답한다**(OWASP Java HTML Sanitizer, `RichText`). 공개 화면은 이 HTML 만 `v-html` 로 그린다.
  - `SIMPLE`(글·댓글 — 댓글은 늘 이 수준): `p br strong em u s a ul ol li blockquote`. `a` 는 `http|https|mailto` 만, 저장 때 `target="_blank" rel="nofollow noopener noreferrer"` 를 붙인다.
  - `ADVANCED`(글만): SIMPLE + `h2 h3 hr code pre` + 이미지 표식 `<img data-image-id="숫자">` + 글자색·배경색 `span`(`style` 의 `color`·`background-color` 만, 값은 팔레트 — 소문자 `#rrggbb`, `rgb(r, g, b)` 도 받아 바꾼다. 글자 `#dc2626 #ea580c #ca8a04 #16a34a #2563eb #9333ea #64748b`, 배경 `#fef08a #bbf7d0 #bfdbfe #fecaca #e9d5ff #e2e8f0` — community 와 같다. 팔레트 밖이면 버리고 남는 것이 없으면 span 을 벗긴다) + YouTube 표식 `<div data-youtube-id="11자"></div>`(ID 형식만 검사, iframe 은 저장하지 않는다 — 화면이 `youtube-nocookie` 로 그린다. 표식 안의 내용은 블록 뒤로 나온다).
  - 그 밖의 태그·속성·`style`·이벤트 속성은 모두 지워진다(글자는 남는다 — 예 `<h1>제목</h1>` → `제목`). 간단 수준에 온 `img` 표식도 조용히 지워진다(422 가 아니다). 앞뒤 빈 문단은 저장하지 않는다.
  - 서버가 채운 이미지 태그: `<img src="/media/post-image/…" data-image-id="ID" width="W" height="H" alt="" loading="lazy">`. 고칠 때 화면이 이 태그를 그대로 돌려보내면 정화가 `data-image-id` 만 남기고 다시 채운다.
- **길이**: 보이는 글자(태그를 떼고 엔티티를 풀고, 블록 경계·`br` = 줄바꿈 1자, 이어진 공백·줄바꿈은 하나로) 기준 기존 범위(글 10~200,000 · 댓글 1~2,000). 정화된 HTML 상한 글 400,000자(`board.content.html.tooLong`)·댓글 20,000자, 정화 전 입력이 상한의 두 배를 넘으면 정화하지 않고 거절. 이미지만 있는 글도 보이는 글자 10자가 필요하다.
- 글당 이미지 30장·YouTube 10개(서버 상수, `board.content.image.limit`·`youtube.limit`). 새 회원은 보안 설정의 새 회원 이미지 수가 먼저.
- **평문을 쓰는 곳**: 검색(`coalesce(content_text, content)`), 같은 글 반복(제목 + 보이는 글자 — 서식만 바꾼 글도 같은 글), 금지어(보이는 글자 **+ 링크 주소** — 거부 방식은 `href` 에 든 금지어도 거절, 가림 방식은 태그 밖 글자만 가린다·주소는 가리지 않는다), 회원 알림 `commentPreview`·숨김 알림 미리보기, 신고 스냅샷(`snapshot_content`), 관리자 개인정보 대상 보기(댓글 앞 40자 — `R__batch_18`). 링크 수 = `<a href>` 하나당 1 + 링크 밖 글자 속 주소. 활동 수·포인트는 본문을 보지 않는다.

### 이미지 올리기 `POST /api/images`
- multipart `file` 한 장, **로그인 회원만**(비회원 401), CSRF. → `201 {id, url, width, height}`(`Cache-Control: no-store`). 게시판을 모른다.
- 정지 `403 ACCOUNT_RESTRICTED`, 글쓰기 역할 권한 없음 `403 BOARD_RESTRICTED` → 칸(`400 fields.file`: 빈 파일·형식·용량·깨짐·화소) → 1분 20장 `429 IMAGE_RATE_LIMITED`(+`retryAfterSeconds`) → 최근 24시간 300장 `429 IMAGE_DAILY_LIMIT`. 10MB 초과는 multipart 한도라 `413 FILE_TOO_LARGE`. 값은 `roboyam.post-image.*`.
- 형식은 첫 바이트로 JPEG·PNG·WebP·GIF 만(이름·Content-Type 무시 — 위장 파일·SVG 는 `post.image.type`). 화소 2,500만 초과는 디코딩 전에 거절. 디코딩해 실제 그림인지 확인.
- 다시 만들기(메타데이터 제거): JPEG 는 EXIF 방향을 반영해 돌린 뒤 긴 변 2048 로 줄이고 다시 인코딩(EXIF 없음), PNG 는 줄여 다시 인코딩, 움직이는 GIF 는 원본 그대로(긴 변 2048 이하일 때 — 넘으면 첫 장 PNG), 한 장 GIF 는 PNG, WebP 는 디코더가 없어 컨테이너만 정리(EXIF·XMP 제거, **줄이지 않는다**).
- 장부는 기존 `files`(용도 `POST_IMAGE`, 올린 회원 `uploaded_by_user`, 쓰는 글 `post_id`): 올리면 PENDING → 글에 쓰이면 IN_USE. 저장소 키 `post-image/{UTC 년월}/{무작위 32자}.{확장자}`, `/media/**` 1년 캐시. 이미지 코덱(`ImageCodec`·`ImageGate`)은 admin 에서 shared 로 옮겼다(api 도 쓴다).

### 글 저장 때의 이미지 규칙
- 검사 순서: 칸(400) → 본문 이미지가 쓸 수 있는 것인지(400) → 이미지 금지 게시판의 새 이미지(422) → 금지어 → 캡차 → 속도.
- 본문의 `data-image-id` 는 **글쓴이가 올린 PENDING 이미지** 또는 **이 글이 이미 쓰던 이미지**만 — 없는 ID·남의 이미지·다른 글이 쓰는 이미지는 `400 VALIDATION fields.content`(「이미지를 찾을 수 없어요」, community 방식 — 조용히 지우지 않는다). 저장 트랜잭션에서 행을 잠가 다시 확인한다.
- 이미지 유효값(고급 에디터 + 허용)이 false 인 게시판에 **새로 들어간** 이미지 ID 가 있으면 `422 IMAGE_UPLOAD_NOT_ALLOWED`. 고치기는 그 글이 쓰던 이미지 집합과 비교해 새 것만 막는다.
- 비회원: 올리기 401 이고, 고급 게시판 HTML 에 이미지 표식을 넣으면 기존 비회원 이미지 규칙 `400 fields.content`(「비회원은 이미지를 넣을 수 없어요」).
- 고치면서 빠진 이미지·평문으로 고친 글의 이미지는 PENDING 으로 돌아가고, 지운 글(바로·예약 삭제 시각 지남)·행이 없어진 글의 이미지와 함께 `file-orphan-cleanup`(매일 03:45, 관리자 앱)이 저장소·장부에서 지운다. 개인정보 파기(`R__batch_18`)는 `content_text` 도 지운다.
- 목록 `thumbnailUrl` 은 아직 늘 null(후속), `hasImage` 는 이미지 표식이 있으면 true.

### 이모티콘 — 범위 밖
community 의 이모티콘은 아이템샵(포인트 구매) 품목이라 로보얌에는 없다. 토큰 `:emo-ID:` 는 평문 그대로 저장·표시된다. **아이템샵이 생기면** 이모티콘 표식·허용 목록을 후속으로 더한다.

### 미확정
1. 이미지만 있는 글도 보이는 글자 10자가 필요(기존 본문 최소 규칙 그대로) — 사진 글을 허용할지.
2. WebP 는 줄이지 않는다(디코더 없음). CMYK JPEG 는 JDK 가 못 읽어 「읽을 수 없어요」.
3. 목록 썸네일(첫 이미지)·갤러리 레이아웃 연결은 후속.
4. 올리기 1분 한도는 서버 메모리(서버 대수만큼 늘 수 있다), 하루 한도는 DB.
## 에디터 화면 (2026-10-23 — 프론트 구현, 서버는 동시 구현 중)
**의도**: 게시판 설정에 따라 글·댓글을 평문(Textarea) 또는 서식 에디터(community 에디터를 옮긴 공용 레이어 `@roboyam/rich-editor`)로 쓰고, 에디터 글은 서버가 정화한 HTML 로 보인다.
- **게시판 칸**(공개 `GET /api/boards`·`/{slug}`·`/writable`): `postEditor: NONE|SIMPLE|ADVANCED` · `imageUploadAllowed` · `commentEditor: NONE|SIMPLE`(유효값, 없으면 `NONE`/`false`/`NONE`). 관리자 게시판 설정·기본 설정 「일반 › 쓰기 권한·에디터」에 같은 이름 칸(정책 칸 모양, 기본값 따르기). 전체 기본값 글 `SIMPLE`·이미지 `true`·댓글 `NONE`.
- **본문 형식**: 글·댓글 요청/응답 `contentFormat: TEXT|HTML`(응답에 없으면 `TEXT`). `NONE` → Textarea + `TEXT`, 에디터 → HTML + `HTML`(HTML 은 다듬지 않고 그대로 보낸다). 길이(글 10~200,000 · 댓글 1~2,000)는 **태그를 뗀 보이는 글자 수**(연속 공백 하나, 앞뒤 자름 — `UtilRichText.visibleLength`).
- **보기**: `contentFormat==='HTML'` 일 때만 서버 HTML 을 `v-html`(글 본문 `pages/posts/[id]/index.vue`, 댓글 `CommentItem.vue` — 이 두 곳뿐). 그린 뒤 `UtilRichBody.enhance` 가 `div[data-youtube-id]` 를 썸네일 → 누르면 `youtube-nocookie` iframe 으로, 못 불러온 `img[data-image-id]` 를 자리표시로 바꾼다. 모양은 `rich-text.css`(레이어가 넣는다).
- **수준**: SIMPLE(글·댓글) `p br strong em u s a ul ol li blockquote` · ADVANCED(글만) + `h2 h3 hr code pre`, 팔레트 색 `span style="color|background-color: #rrggbb"`(community 팔레트 — TipTap 의 `rgb()` 직렬화를 `#rrggbb` 로 되돌려 보낸다), 이미지 `<img data-image-id src width height>`, 유튜브 `<div data-youtube-id>`, 블록 끌기 손잡이. 이모티콘·표·정렬·글꼴은 없다.
- **이미지**: 고급 + `imageUploadAllowed` + 회원일 때만 단추·드롭존·붙여넣기·끌어놓기(비회원 글쓰기·비회원 글 고치기는 감춘다). `POST /api/images` multipart `file` → `201 {id,url,width,height}`(`useImageApi`, 진행률 XHR). 오류는 서버 문구, 다시 시도는 0·429·5xx 만. 올리는 중·실패가 남으면 등록을 막는다. 저장 `422 IMAGE_UPLOAD_NOT_ALLOWED` 는 본문 칸 아래 서버 문구.
- **유튜브**: 서버 확인 API 가 없어 화면이 주소에서 ID 를 읽는다(`youtu.be`·`watch?v=`·`shorts`·`embed`·`live`).
- **형식이 바뀐 글**(가정): 고치기는 **지금** 게시판 설정으로 연다 — 평문 글을 에디터 게시판에서 고치면 문단 HTML 로, 에디터 글을 평문 게시판에서 고치면 글자만 남긴다(서식·이미지 빠짐). 글쓰기에서 게시판을 바꿀 때도 같다.
- **미확정**: ① 관리자 「이미지 올리기」 칸은 글 에디터가 **고급이 아니면** 끄고 안내한다(간단 에디터는 이미지를 만들지 않으므로 — 계약 문구 「평문이면」보다 넓게). ② 서버 정화의 `a` 속성(`target`·`rel`)·빈 `<p></p>`(에디터 끝 빈 문단) 처리. ③ 이미지 형식 미리 검사는 JPG·PNG·GIF·WebP 가정(크기·장수 한도는 서버 문구로만). ④ 서버 연동 확인은 서버가 develop 에 들어온 뒤.
