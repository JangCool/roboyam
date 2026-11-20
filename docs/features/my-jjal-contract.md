# 내 짤 — 계약

**의도**: 회원이 흔히 쓰는 움짤·짤을 「내 짤」 보관함에 올리거나, 글·댓글에 쓰인 남의 짤을 담아 두고 글·댓글에 붙인다. 로보티콘 상점(창작 팩·심사)과 따로 두는 **나만 쓰는 보관함**이라 심사 없이 신고로 관리한다. 같은 파일은 한 번만 저장하고, 담기는 새로 올리지 않고 같은 파일을 가리킨다.
**참고(읽기 전용)**: `emoticon-contract.md`(고르기 창·토큰 `:emo-ID:`·그림 검사), `board-public-contract.md` 「이미지 올리기」(`POST /api/images`·`files` 장부·`ImageCodec`/`ImageGate`·`file-orphan-cleanup`)·신고 사유 6종·자동 숨김, `report-admin-contract.md`(신고 사건).

## 화면 (시안 확정 2026-11-20)
- **고르기 창**(`EmoticonPickerPanel`): 탭 줄 😀 다음에 「짤」 탭(받은 팩 탭보다 앞). 첫 칸 「올리기」, 나머지는 **내가 정한 순서**(아래 「순서」), 위에 `12 / 50`, 아래 「나만 쓰는 보관함이에요. 상점에 나오지 않아요.」. 비회원은 탭 없음.
- **짤 메뉴**: 글·댓글에 붙은 짤을 누르면(휴대폰은 길게) 「내 짤에 담기」 · 「크게 보기」 · 「짤 신고」. 내려간 짤·19금 게시판의 짤은 「담기」 없음.
- **올리기 창**: 끌어 놓기 + 규칙 세 줄 + 확인 두 개(「음란물·불법촬영물·혐오 짤이 아니에요」, 「권리 침해 신고가 오면 내려갈 수 있어요」).
- **처음 안내 창**: 처음 「담기」나 「올리기」를 누를 때 한 번 — 나만 보는 보관함(누가 담았는지 안 보임) · 댓글의 짤은 누구나 담을 수 있음(내 짤도) · 신고되면 모든 곳에서 함께 내려감 · 올린 사람이 지워도 담은 짤은 남음 + 「확인했어요」. 「나중에」는 담지 않고 닫는다(다음에 또 나온다).
- **담은 뒤 문구**: 「내 짤에 담았어요 13 / 50」 · 「이미 내 짤에 있어요」 · 「내 짤 50개가 꽉 찼어요 — 정리하기」(→ 내 짤 칸).
- **내 로보티콘**(`/me/robotikon`) 「내 짤」 칸: 격자, 고르기·지우기(내 보관함에서 빼기), 내려간 짤은 빨간 칸 + 「숨겨진 짤 N개 — 권리 침해 신고로 내려갔어요. 쓴 댓글에서도 「내려간 짤」로 보여요.」

## 정책 (사용자 확정 2026-11-20)
1. **공유 = 「내 짤에 담기」**: 글·댓글에 보이는 짤을 내 보관함에 담는다. 파일은 그대로, 보관함 줄만 하나 더(자원 없음).
2. **누가 담았는지 안 보임**: 올린 사람에게 담은 사람·담은 수를 보이지 않는다.
3. **올린 사람이 담기를 막지 못한다**(칸 없음).
4. **올린 사람이 지워도 담은 짤은 남는다**: 지우기는 내 보관함 줄만 뺀다. 파일은 가리키는 보관함 줄도, 쓰는 글·댓글도 없을 때 지운다. 신고로 **내린** 짤만 모든 보관함·글·댓글에서 함께 내려간다.
5. **19금 게시판 글·댓글의 짤은 담기 없음**.
6. **출처 표시 없음**: 담은 짤에 원래 올린 사람 이름을 붙이지 않는다.
- **처음 안내 확인은 계정에 기록**(시각·안내 판) — 브라우저 저장이 아니라 PC·휴대폰에서 두 번 나오지 않게.

## 규칙 (가정 — 구현 때 값은 설정으로)
- **자격**: 로그인 · 이메일 인증 · 정상 회원(정지면 올리기·담기 `403 ACCOUNT_RESTRICTED`). 나이 제한 없음. 비회원 없음.
- **올리기 — 브라우저가 줄이고 서버는 검사만(사용자 확정 2026-11-20)**:
  - 브라우저: 올리기 창에서 긴 변 300px 로 줄여 GIF(움짤)·PNG/JPG(정지)로 만들어 올린다(올리기 창 열 때만 불러오는 작은 라이브러리 — 홈 번들에 넣지 않는다). WebP·큰 GIF 도 받아서 바꾼다. 움직이는 WebP 를 브라우저가 못 풀면 「GIF로 올려 주세요」.
  - 서버 검사 차례: 자격(`403`) → 2MB 초과(`413`) → 형식(첫 바이트로 GIF·PNG·JPEG 만 — 이름·Content-Type 무시, 위장 파일·SVG·WebP 거절) → 크기(헤더의 가로·세로, 긴 변 300px 초과면 **디코딩 전에** 거절) → 실제 그림(디코딩, 깨짐 거절 · GIF 는 프레임 100장·재생 10초 이하) → 상한 50개(`409`) → 속도(1분 10장 · 하루 50장 `429`) → 해시(내린 짤과 같으면 거절 · 이미 있으면 그 파일을 가리킨다).
  - 서버는 움짤을 다시 만들지 않는다(GIF 원본 그대로 — 글 이미지와 같다). JPEG·PNG 는 다시 인코딩해 메타데이터(휴대폰 사진의 위치 정보 등)를 지운다. 해시는 다시 만들기 전 원본 바이트로 낸다.
- **상한**: 한 회원 50개(올린 것 + 담은 것). 등급별로 늘리는 것은 후속.
- **저장 합치기**: 파일 한 줄 = **원본 바이트 SHA-256 유일**. 같은 해시가 있으면 새로 저장하지 않고 그 파일을 가리키는 보관함 줄만 만든다(올린 사람 화면은 같다 — 이미 있다는 사실을 드러내지 않는다). 크기·압축이 다른 같은 짤은 합치지 않는다(비슷하다고 합치면 화질·글자 넣은 버전이 바뀌는 사고).
- **저장소 키는 무작위**(해시를 키·주소에 쓰지 않는다 — 주소로 파일 존재를 떠볼 수 없게). 해시는 DB 칸.
- **보관함 줄**: (회원, 파일) 유일, 출처 `UPLOAD|COLLECT`, 순서 값. 이미 있으면 담기 `409`(문구 「이미 내 짤에 있어요」), 꽉 차면 `409`.
- **순서(사용자 요청 2026-11-20)**: 회원이 내 짤 순서를 정한다 — 내 로보티콘 「내 짤」 칸의 「순서 바꾸기」에서 끌어 옮기기(PC) + 앞으로·뒤로 단추(휴대폰·키보드), [저장]·[취소]. 고르기 창·내 짤 칸 모두 이 순서. **새로 올리거나 담은 짤은 맨 앞**. 내려간 짤도 자리를 갖되 고르기 창에서는 빠진다. 저장은 전체 ID 목록 한 번(`PUT /api/me/jjals/order { ids }` — 보관함과 다르면 `409`, 화면은 다시 받는다). 최근 쓴 차례는 쓰지 않는다.
- **글·댓글에 붙이기**: 토큰 `:jjal-{공개ID}:`(로보티콘 토큰과 같은 방식 — 저장 때 서버가 검사, 그릴 때 그림). 붙일 수 있는 것 = 지금 내 보관함에 있는 짤. 댓글 1개 · 글 5개까지(도배 방지 가정).
- **게시판별 사용(사용자 확정 2026-11-20)**: 글 본문과 댓글을 따로 본다.
  - **글 본문** = 새 정책 칸 `postJjalAllowed`(본문 짤 허용, `boards` NULL = 기본값 따름 · `board_defaults` NOT NULL 기본 `true`). 관리자 「일반 › 쓰기 권한·에디터」의 「이미지 올리기 허용」 옆. **이미지 칸과 따로**(사용자 확정 2026-11-20 — 이미지 칸은 고급 에디터에서만 바꿀 수 있어 간단 에디터 게시판에서 본문 짤을 끌 수 없었다). 글 에디터가 `NONE` 이면 고르기 창이 없어 본문 짤도 못 넣는다(칸은 그대로 보인다).
  - **댓글** = 새 정책 칸 `commentJjalAllowed`(댓글 짤 허용, 같은 모양 · 기본 `true`). 댓글 에디터 옆. 이미지 칸과는 상관없다.
  - 공개 게시판 칸에 유효값 `postJjalAllowed`·`commentJjalAllowed`(게시판 값 ?? 기본값).
  - 막힌 곳에서는 고르기 창의 「짤」 탭만 숨긴다(이모지·로보티콘은 그대로). 서버는 저장 때 새로 들어간 짤 토큰만 거절한다 — 나중에 끄면 이미 붙은 짤은 그대로(글 이미지 「고치면 새 것만 막는다」와 같다).
- **신고**: 짤 메뉴 「짤 신고」 = 대상 종류 **짤(파일 단위)** 를 신고에 더한다, 사유 6종 그대로. 불법촬영물 1건 즉시 숨김 · 권리침해 임시조치 · 기준 숨김은 기존 글·댓글 규칙과 같게. 내리면 파일 상태 `BLOCKED` → 모든 보관함·글·댓글에서 「내려간 짤」, **같은 해시 다시 올리기 거절**(이미 가진 해시 비교라 비용 없음).
- **비슷한 짤 비교는 하지 않는다**(사용자 결정 2026-11-20 — 크기·압축이 다른 짤을 100% 잡지 못해 의미가 없다). 지각 해시를 저장하지 않는다.

## API 후보
- 회원: `GET /api/me/jjals`(목록·개수·상한·안내 확인 여부) · `POST /api/me/jjals`(multipart 올리기) · `POST /api/me/jjals/collect { jjalId }`(담기) · `DELETE /api/me/jjals/{id}`(빼기) · `PUT /api/me/jjals/order { ids }`(순서) · `POST /api/me/jjals/intro-ack`(처음 안내 확인).
- 공개: 글·댓글 응답에 짤 토큰 → `{id, url, width, height, animated, blocked, collectable}`(카탈로그는 로보티콘처럼 따로 받을지 응답에 실을지 구현 때 정함).
- 신고: 기존 신고 API 에 대상 종류 짤.
- 관리자: 신고 사건 처리(기존 화면) + 짤 내림·되돌림. 권한 후보 `jjal.moderate`. 게시판 설정 칸 `commentJjalAllowed`(`board.settings`, 공개 게시판 칸에도 유효값).

## 화면 확정 결과 (사용자 확정 2026-11-20, 브랜치 `claude/my-jjal`)
- **코드**: user-web `components/jjal/*`·`composables/jjal/*`(API `composables/jjal/api/useJjalApi.ts` — `// MOCK:` → 연결 때 `// REAL:`)·`utils/util-jjal*.ts`·`i18n/locales/{ko,en}/jjal.ts`, 고르기 창 `EmoticonPicker(Panel).vue`, 그리기 `EmoticonText.vue`·`CommentItem.vue`·`posts/[id]/index.vue`, 넣기 `CommentForm.vue`·`PostEditorForm.vue`, `pages/me/robotikon.vue`. rich-editor `utils/util-rich-jjal.ts`·`editor/jjalInline.ts`. admin-web 목업 `composables/board/api/mock/board-jjal-mock.ts`·`composables/report/api/mock/report-jjal-mock.ts`.
- **의존성**: user-web `gifenc` 1.0.3 · `omggif` 1.0.10 — 올리기 창에서만 동적 import(`scripts/nuxt/chunk-groups.mjs` 묶음 `gif`).
- **화면이 쓰는 API(위 「API 후보」를 대신한다)**:
  - `GET /api/me/jjals` → `{ items:[{id, url, width, height, animated, source:'UPLOAD'|'COLLECT', blocked}], count, limit, introAcknowledged }` — **배열 순서 = 회원이 정한 순서**(시각 칸 없음).
  - `POST /api/me/jjals`(multipart `file`) · `POST /api/me/jjals/collect { jjalId }` → `{ item, count, limit }`(토스트 개수용). 새 짤은 맨 앞.
  - `DELETE /api/me/jjals/{id}` · `PUT /api/me/jjals/order { ids }` → `204`(전체 ID 와 빠짐·겹침 없이 같아야 함, 다르면 `409 JJAL_ORDER_CONFLICT`) · `POST /api/me/jjals/intro-ack`.
  - 글·댓글 속 짤 정보는 따로 받는다: `GET /api/jjals?ids=` → `{ items:[{id, url, width, height, animated, blocked, collectable}] }`. `collectable` 은 서버가 정한다(19금 게시판·19금 글이면 false — 화면은 게시판 `adultOnly` 도 본다).
  - 오류 코드(화면 가정): `JJAL_ALREADY_IN_BOX`(409) · `JJAL_LIMIT_REACHED`(409) · `JJAL_BLOCKED` · `JJAL_TOO_LARGE`(413) · `JJAL_NOT_FOUND` · `ACCOUNT_RESTRICTED`(403) · `JJAL_ORDER_CONFLICT`(409). 공개 ID = 영문·숫자 1~32자.
  - 신고: 기존 신고 API 대상 종류 `JJAL`.
  - 공개 게시판 칸 `postJjalAllowed`·`commentJjalAllowed`(유효값 — 없으면 화면은 켬으로 본다).
  - admin: `BoardDefaults.postJjalAllowed`·`commentJjalAllowed`(NOT NULL 기본 true), 게시판 `policy.*`(null = 따름). 신고 사건 `targetType: 'JJAL'` + `target.jjal{id, url, width, height, animated, blocked}`, 조치 `JJAL_BLOCK`(기존 처리 창 — 사유·처리 글), 되돌림 `POST /api/admin/reports/{id}/jjal-restore`. 권한은 기존 `report.manage`(`jjal.moderate` 는 만들지 않는다).
- **화면에서 정한 것**: 내려간 짤은 메뉴가 뜨지 않는다 · 휴대폰은 길게 누르기(0.5초)만 메뉴 · 글 에디터 `NONE` 게시판은 본문 짤 불가 · admin 짤 사건은 작성자를 보이지 않아 「제재만」·제재 곁들이기 없음 · 보관함 개수에 내려간 짤도 센다 · 알림·목록 한 줄 요약에서 짤 토큰은 「(짤)」 · 브라우저가 받는 원본 상한 20MB(가정) · 영어 화면 용어 "GIFs".

## 미확정
- ~~움짤 줄이기~~ → **정함(2026-11-20)**: 위 「올리기」. WebP 로 저장해 용량 줄이기는 후속.
- 법무: 개인 짤 업로드(서비스 제공자 책임 제한·임시조치·불법촬영물 기술적 조치 의무 규모 기준) — 로보티콘 「남은 일 = 법무 확인」에 함께 묻는다.
