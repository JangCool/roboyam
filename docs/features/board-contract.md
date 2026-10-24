# 게시판 관리 계약 (**관리자 화면 확정 2026-10-22** — 이번 범위는 관리자 백엔드 + 화면 연결)

**의도**: 관리자 앱에서 게시판을 만들고(목록·순서·켜고 끄기·삭제) 게시판마다 기본 정보·정책·말머리·안내문·매니저/스텝을 정하며, 모든 게시판에 일괄 적용되는 기본 설정(일반·신고 정책·보안·알림·개인정보 보유기간)을 다룬다. 그누보드7 게시판 환경설정 + community 의 `board_defaults`·게시판 설정을 roboyam 개념(목록 기반 등급, 게시판 매니저·스텝, 권한 코드, **비회원 글쓰기 있음**)으로 옮겼다.
**참고(읽기 전용)**: community `D:\project\community\workspace\community` — `docs/api-board-settings.md`·`docs/api-content.md` 3절·`docs/data-model.md`·`docs/plans/board-menu-03-data-structure.md`, `server/shared/.../db/migration/shared/V3__board.sql`·`V10__rename_db_functions.sql`. 그누보드7 게시판 모듈 `config/settings/defaults.json`·`config/board.php`(limits).

## 화면 (기준 코드 — 칸 이름·범위·기본값의 원천)
- `/boards`(목록·만들기) · `/boards/[slug]`(게시판 설정) · `/boards/defaults/{general|report|security|notifications|privacy}`.
- 기준 코드: `apps/admin-web/app/composables/board/api/useBoardApi.ts`(머리말 = API 후보 전체) · `useBoardApi.types.ts`(요청·응답 모양) · `utils/util-board.ts`(`SPECS`·`TAB_SPECS` = 칸별 범위·기본값·필요 권한, slug·이름·말머리 검사). 목업(`mock/board-mock.ts` 등)은 서버 연결 뒤 지워졌고 서버 규칙의 원천은 서버 코드다.

## 권한
조회 `board.read` · 만들기·기본 정보·말머리·켜고 끄기·순서 `board.manage` · 정책(기본 설정·게시판별 정책·안내문·쓰기 잠금) `board.settings` · 매니저/스텝 지정 `board.staff.manage` · 스텝 한도·인원 상한 `board.staff.limit` · 삭제 `board.delete`. 저장 한 번에 여러 부분이 바뀌면 **바뀐 부분의 권한만** 본다(목업과 같음).

## 데이터 구조 (결정)
- `boards`(게시판 정보, 파티션 없음) — **ID 는 Snowflake `bigint`**(`SnowflakeIdGenerator`), API 에서는 문자열. `slug` 는 `^[a-z][a-z0-9-]*$` 2~40자·유일·만든 뒤 변경 불가·예약어 `defaults`·`order`·`new`·`writable`(공개 API `/api/boards/writable` 과 겹침). 이름 1~40자, 대소문자 무시 유일.
- 정책 칸은 게시판 행에 **NULL 허용**(NULL = 기본값 따름), 유효값 = 게시판 값 ?? 기본값. 기본값은 단일 행 표(`id = 1` CHECK).
- 쓰기 자격 값: `GUEST`(비회원 포함) · 등급 키(등급 목록) · `STAFF`, 댓글은 `OFF` 추가. 핫딜·직접홍보 종류는 `GUEST` 불가, 핫딜은 일반 등급 미만 불가.
- 글·댓글 표는 이번에 **만들기만** 한다(글쓰기 API 는 범위 밖). 글 수·말머리 사용 수·삭제 가능 판정·개인정보 대상 수가 이 표를 읽는다.
  - `posts`: **`id` RANGE 월 파티션**(Snowflake 시각 = ID 범위). 조각은 DB 함수가 지난달~6개월 뒤까지 만들고(어드바이저리 락, 있으면 건너뜀) 앱 시작 때와 매일 배치로 부른다 — **조각 없는 달의 글은 저장 실패**. 글 조각은 떼거나 지우지 않는다. 게시판 기준(LIST) 파티션·게시판별 표는 쓰지 않는다(근거: community board-menu-03).
  - `comments`: **`post_id` HASH 64**. 답글 1단계(`parent_id`, `reply_to_id`).
  - 비회원 칸: 글·댓글에 작성자 이름·비밀번호 해시·작성 IP(회원 글도 IP). 파티션 표를 가리키는 외래 키는 두지 않는다.
- 말머리 `board_prefixes`(게시판별 고유, 사용 중 최대 6) + 공통 말머리 4개(질문·후기·잡담·공지[운영진 전용]) 게시판별 켜고 끄기. 글이 있는 말머리는 삭제 불가(`PREFIX_IN_USE`).
- 매니저·스텝 지정 표(게시판·회원·역할·지정 시각·지정한 운영자). 규칙은 `docs/domain/operator-permissions.md` 「게시판 매니저 · 스텝」(자격·인원·한 회원 최대 5게시판·하루 한도).
- 설치 때 고른 프리셋(`installation.board_preset`)으로 기본 게시판을 만든다. 이미 설치된 곳은 게시판이 하나도 없을 때 한 번 적용한다.
- **가젯 노출 금지**(2026-10-24, shared `V69`): `boards.gadget_exclusions varchar(16)[] NOT NULL DEFAULT '{}'` — 고른 홈 가젯에 이 게시판 글을 내지 않는다. 가젯 목록의 원천은 shared `BoardGadget`(`POPULAR` 인기글 · `LATEST` 최신글, 이름 번들 `board.gadget.<KEY>`). 게시판마다의 값(기본 설정에서 물려받지 않음, `diffCount` 에 세지 않음). 새 게시판은 `kind` DEAL·PROMO 면 `{POPULAR}`, 그 밖은 빈 배열(관리자 만들기·설치 프리셋 같은 `BoardCreator`), 기존 DEAL·PROMO 는 V69 가 `{POPULAR}` 로 채웠다. API: 게시판 GET/PUT 응답 `gadgetExclusions: string[]`(목록 순서) + `gadgets: [{key, label}]`(고를 수 있는 전부, 요청 언어), PUT 요청 `gadgetExclusions`(null = 그대로, 중복은 하나로, 모르는 값 `400 VALIDATION fields.gadgetExclusions`). 바꾸면 `board.settings` 권한·`BOARD_UPDATE` 감사(`gadgetExclusions` before/after). 공개 쪽 반영은 게시판 사본 주기(15초)만큼 늦을 수 있다. 통합 검색은 가젯이 아니다(정책 칸 `searchable`).

## API
`useBoardApi.ts` 머리말 목록 그대로(`/api/admin/boards…`, `ApiResponse`, 입력 오류 `400 VALIDATION` + `fields`). 화면 주소가 slug 라 `GET /api/admin/boards/by-slug/{slug}` 를 둔다. 감사 기록: `BOARD_DEFAULTS_UPDATE`(하위 화면 포함)·`BOARD_CREATE`·`BOARD_UPDATE`·`BOARD_ACTIVE`·`BOARD_ORDER`·`BOARD_DELETE`·`BOARD_STAFF_APPOINT`·`BOARD_STAFF_REMOVE`·`BOARD_PRIVACY_RETENTION_UPDATE`·`BOARD_PRIVACY_RUN` — 바뀐 값만 남긴다.

## 배치 (이번 범위)
- `board-post-partitions`(매일) — 월 조각 유지.
- `board-privacy-retention`(매일 03:30) — 마스터 스위치가 켜졌을 때만 축별(작성 IP·비회원 정보[익명화/삭제]·삭제된 원문·신고 기록·처리 기록) 처리. 신고·처리 기록 표가 아직 없으면 그 축은 대상 0. 「지금 실행」은 저장된 값으로 한 번.

## 범위 밖 (후속)
신고 처리 화면·제재·포인트 백엔드. 공개 사이트 게시판 화면·글/댓글 쓰기·추천·조회수·신고 접수·금지어 적용은 공개 게시판 계약(`board-public-contract.md`)에서 구현됐다.

## 세션 정리 결정 (2026-10-22)
여러 세션(게시판 관리·공개 게시판·메뉴 관리)이 따로 정한 것을 하나로 맞췄다.
- **공개 내비게이션 순서의 주인은 메뉴(main 그룹)**다(`menu-contract.md`). 게시판의 `sort_order` 는 게시판 목록 API(`GET /api/boards`·`/writable`)·글쓰기 게시판 고르기·메뉴를 못 받았을 때의 폴백 메뉴·새 게시판의 처음 위치에만 쓰인다. 관리자 `/boards` 에서 순서를 바꿔도 메뉴 순서는 바뀌지 않으며, 화면 안내 문구와 메뉴 관리 링크가 이를 알린다.
- **새 게시판의 메뉴 등록**: 만들 때 main 그룹의 자동 추가 폴더 끝에 들어간다. 자동 추가 폴더가 없으면 메뉴에 들어가지 않는다(알림은 후속).
- **게시판 삭제**: 글·댓글과 함께 그 글의 추천(`post_likes`·`comment_likes`)·조회(`post_views`) 행도 지운다. **신고(`reports`)는 증거라 지우지 않고** 개인정보 보유기간 배치(`board-privacy-retention`)의 신고 기록 축이 정리한다.
- **숨김·삭제의 뜻**은 공개 게시판의 상태 모델이 정본이다 — 숨김은 `hidden_at` 이 있고 `hidden_until` 이 없거나 미래일 때, 예약 삭제 시각이 지나면 삭제로 본다. 회원 활동 수 집계·승급 판정도 이 뜻을 쓴다(`roles-contract.md` 「세션 정리」).

## 미확정 사항
1. 개인정보 IP 보유 하한 90일은 권장값(통신비밀보호법 로그 보관 기간 원문 미확인 — 법무 확인).
2. 신고 사유에 스텝 전용 사유(투자 권유·게시판 주제 불일치)를 넣을지.
3. 회원 알림 보관 90일의 자리(지금 게시판 알림 설정).
4. 게시판 삭제: 지금은 글 0개만. 글이 있는 게시판의 "삭제 예약 → 배치 정리" 는 사용자가 원하면 후속.
5. ~~공개 사이트 게시판 주소 모양~~ → `/boards/{slug}` 로 확정(2026-10-22, `board-public-contract.md`).

## 서버 구현 결과 (2026-10-22) — 화면 연결은 후속
**DB** shared `V42__boards.sql`: `boards`(정책 칸 NULL = 기본값) · `board_prefixes`(공통 4개는 게시판마다 `common_key` 행, 켜고 끄기만) · `board_staff` · `board_defaults` + 하위 화면별 행 하나 표 4개(`board_report|security|notification|privacy_settings`, 범위는 CHECK) · `posts`(id 월 RANGE) · `comments`(post_id HASH 64) · `installation.board_preset_applied_at`. `R__batch_17`(월 조각 함수 + DB 배치 `board-post-partitions` 00:20) · `R__batch_18`(개인정보 판정 함수 + DB 배치 `board-privacy-retention` 03:30). 앱 시작 때 api·admin 모두 조각을 맞춘다(`BoardPartitionBootstrap`).
**API** `useBoardApi.ts` 머리말 그대로(JSON 키 = `useBoardApi.types.ts`). 목업과 다른 점:
- `PREFIX_LIMIT` 은 `400 VALIDATION fields.prefixes` 가 아니라 **409 `PREFIX_LIMIT`**(+`fields.prefixes`). `BOARD_NAME_DUPLICATE` 는 저장에서도 409(+`fields.name`). 검사 순서: 권한(바뀐 부분) → 400 → 409.
- `PREFIX_IN_USE` 문구에 말머리 이름을 싣지 않는다. 새 오류 `409 BOARD_PRIVACY_RUNNING`(배치가 도는 중). 모르는 `axis` 는 `400 VALIDATION fields.axis`.
- 카드형은 보기 전환이 서버에서 늘 false. 핫딜 글쓰기는 첫 등급(새싹) 불가(`policy.writeMin`). `prefixes` 를 빼고 보내면 고유 말머리는 그대로.
- 후보 검색 `q`: 닉네임 일부 · 이메일 **전체 일치** · 회원 ID. 최대 20명. 자격 `BUSINESS` 는 판정하지 않는다(사업자 칸 없음).
- `staff[].appointedBy` 는 운영자 이름(모르면 빈 글자). 개인정보 대상 보기 `id` = `post-<id>`·`comment-<id>`, 댓글 `title` = 본문 앞 40자.
- 설정 저장에서 바뀐 칸이 없으면 `updatedAt/By` 가 그대로다.
**설치 프리셋**: 설치 트랜잭션에서 기본 게시판을 만들고, 이미 설치된 곳은 api 기동 때 게시판이 0개면 한 번(멱등, 표시 칸). 소모임 게시판은 만들지 않음. 고유 말머리 이름이 공통 이름과 같으면 공통을 켠다(자유 「잡담」, 공지 「공지」).
**미확정(추가)**: 등급을 지우면 그 키를 쓰는 `write_min`·`auto_hide_min_grade` 가 남는다(등급 저장 쪽 정리 필요). 금지어·신고·알림 설정은 저장만.

## 에디터·이미지 (2026-10-23, 서버 구현) — 공개 쪽 상세는 `board-public-contract.md` 「에디터·이미지」
- **정책 칸 3개**(게시판 `boards` NULL = 기본값 따름, `board_defaults` NOT NULL — shared `V56__board_editor.sql`): `postEditor` `NONE|SIMPLE|ADVANCED`(기본 `SIMPLE`) · `imageUploadAllowed`(V42 에 **이미 있던 칸** 그대로, 기본 `true` — 새로 만들지 않았다) · `commentEditor` `NONE|SIMPLE`(기본 `NONE`). 권한 `board.settings`, 칸 범위 `BoardFields`(`POLICY_FIELDS` 차례 `writeMin, commentMin, postEditor, imageUploadAllowed, commentEditor, …`).
- **관리자 API 모양은 기존 정책 칸과 같다**: 게시판 GET/PUT 의 `policy.<칸>`(저장값, null = 기본값 따름) + `defaults.<칸>`. 기존 응답에 `effective` 는 없다 — 유효값은 화면이 `policy ?? defaults` 로 계산한다(계약 초안의 `effective.<칸>` 은 만들지 않았다). 기본 설정 GET/PUT 은 `defaults.<칸>` + 따로 정한 게시판 `overrides.<칸>`(계약 초안의 `overridden` 이 아니라 기존 이름 `overrides`). 기본 설정 PUT 은 기존 칸처럼 **두 칸도 필수**(빠지면 `400 fields.postEditor`). 잘못된 값 `400 VALIDATION fields.postEditor`·`fields["policy.postEditor"]`.
- **이미지 유효값 = 글 에디터 유효값이 `ADVANCED` 이고 이미지 칸 유효값이 true 일 때만 true**(사용자 확정 2026-10-23). `NONE`·`SIMPLE` 이면 저장값과 무관하게 false — 저장값은 그대로 저장한다(화면은 고급이 아니면 칸을 비활성으로 보이되 값은 보낸다). 평문 본문의 이미지 주소 규칙은 예전처럼 저장값(에디터와 무관)을 본다(`EffectiveBoard.imageSetting`).
- 감사: 게시판 저장은 기존 `BOARD_UPDATE`(계약 초안의 `BOARD_SETTINGS_UPDATE` 는 로보얌에 없다), 기본 설정은 `BOARD_DEFAULTS_UPDATE` — 바뀐 칸만 `before/after` 에 들어간다.
- `file-orphan-cleanup`(Java 배치, admin) 이 글 이미지도 정리한다 — 올린 뒤 24시간 안 쓴(또는 글에서 뺀) 이미지 + 지운·예약 삭제 시각이 지난·행이 없어진 글의 이미지. 지우기 전에 행을 잠가 상태를 다시 본다.

## 운영자 숨김 공개 표시 (2026-10-24, 서버 구현) — 상세는 `popular-highlight-contract.md` 「서버 구현 결과」
- **정책 칸 `staffHiddenNotice`**(불리언, 게시판 `boards.staff_hidden_notice` NULL = 기본값 따름, `board_defaults` NOT NULL **기본 false** — shared `V77__board_staff_hidden_notice.sql`): 운영자가 숨긴 댓글임을 공개 화면에 밝힐지. 켜면 공개 댓글 응답의 운영자 숨김(`hidden_reason` STAFF) 자리에 `hiddenBy: "STAFF"`, 끄면 null(신고 숨김과 구별되지 않음). 관리자 API·권한(`board.settings`)·감사·`overrides`·`diffCount` 는 다른 정책 칸과 같다(`POLICY_FIELDS` 차례 `commentGuestDelayMinutes` 다음).
- **정책 칸 4개**(2026-10-24 사용자 — `popular-highlight-contract.md` 「결정」, shared `V78__debate_neutral_dislike_pin_request.sql`; 게시판 `boards` NULL = 기본값 따름, 관리자 API·권한 `board.settings`·감사 `BOARD_UPDATE`·`BOARD_DEFAULTS_UPDATE`·`overrides`·`diffCount` 는 다른 정책 칸과 같다. `POLICY_FIELDS` 차례 `… staffHiddenNotice, commentDislikeEnabled, commentLikeLabel, commentDislikeLabel, pinRequestReviewers, managerMax …`):
  - `commentDislikeEnabled`(불리언, 기본 **false**) — 댓글 비추천을 받는가. 끄면 공개 댓글의 `dislikeCount`·`disliked` 가 null, 비추천 요청 `409 COMMENT_DISLIKE_DISABLED`(쌓인 수는 보존).
  - `commentLikeLabel`·`commentDislikeLabel`(글자 1~10, 앞뒤 공백을 떼고 비면 null) — 댓글 추천·비추천 단추 이름. 기본 설정의 null = 화면 기본 이름(「추천」·「비추천」), 게시판의 null = 기본 설정 따름(그래서 기본 설정에 이름을 두면 게시판에서 「기본 이름」으로 되돌릴 수는 없다 — 같은 글자를 적는다). 기본 설정 PUT 에서도 **비워 둘 수 있다**(다른 칸과 달리 필수 아님). 범위 밖·제어 문자 `400 fields.<칸>`·`fields["policy.<칸>"]`(문구 `board.field.label`). 칸 종류 `BoardFields.Kind.LABEL`.
  - `pinRequestReviewers`(문자열 배열 — `POST_AUTHOR` · `BOARD_MANAGER` · `OPERATOR` 중 겹치지 않게, **빈 배열 = 의견 고정 요청 끔**, 기본 `["OPERATOR"]`) — 하이라이트 토론의 의견 고정 요청을 누가 확인하는가(`popular-highlight-contract.md` 「의견 고정 요청」). 응답·저장 차례는 위 차례로 정리. 목록 밖 값·겹침 `400 fields.<칸>`(`board.field.choice`). 기본 설정 PUT 에서는 필수(빈 배열은 된다). DB 는 쉼표로 이은 글자(`varchar(64)`, 빈 글자 = 끔). 칸 종류 `BoardFields.Kind.MULTI`.
  - 공개 쪽: 비추천 켬·두 이름은 댓글 목록 응답 `reactions: { dislikeEnabled, likeLabel, dislikeLabel }`(유효값). 확인자 목록은 공개 응답에 싣지 않는다(서버가 판정 — 확인 권한자에게만 `debate.pendingPinRequests`). admin-web `util-board.ts` `SPECS`·`BOARD_POLICY_FIELDS` 에 네 칸을 더해야 한다(화면 세션 몫). → 화면 반영(2026-10-24): 기본 설정 › 일반 「댓글 추천·의견 고정 요청」 구획 · 게시판 설정 같은 구획(칸 종류 `label`·`multi`), 공개 댓글 줄의 추천·비추천 이름 — 상세는 `popular-highlight-contract.md` 「화면 구현 결과」.
