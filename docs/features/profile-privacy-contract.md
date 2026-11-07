# 프로필 공개 범위 계약 (**화면 확정 2026-11-07 · 서버 구현·화면 연결 완료** — develop 병합 전)

**의도**: 회원이 내 프로필을 공개/비공개로 고른다. 비공개면 다른 사람에게 닉네임·아바타·등급만 보이고 한 줄 소개·가입일·활동 수치·작성글·댓글·소모임 목록은 숨긴다. 공개면 글 보기 본문 아래에 작성자 한 줄 소개가 나온다.
이전 결정(`member-activity-contract.md`·`member-mypage-contract.md` 「한 줄 소개·가입일은 공개 프로필에 보인다」)은 **공개(기본값)일 때**의 뜻으로 남는다.

## 화면 (user-web)
| 경로 | 내용 |
|---|---|
| `/settings/profile` | 닉네임·한 줄 소개 아래 「프로필 공개」 구획 — 라디오 공개(기본) / 비공개 + 각 설명, 아래 안내(게시판의 글·댓글은 그대로 · 운영진은 볼 수 있음). 같은 저장 줄로 저장. 정지 중이면 다른 칸처럼 막힘 |
| `/users/{id}` (남이 봄, 비공개) | 머리(아바타·닉네임·뱃지·메모 칩·⋯·쪽지 보내기)는 그대로, 소개·가입일 자리에 🔒「비공개」 한 줄, 그 아래 수치·탭·목록 대신 「비공개 프로필이에요 — {닉네임}님이 프로필을 공개하지 않았어요. 쓴 글과 댓글은 게시판에서 볼 수 있어요.」 |
| `/users/{id}` (본인, 비공개) | 모두 보이고 머리 아래 안내 띠 「비공개 프로필이에요 — 다른 사람에게는 닉네임·아바타·등급만 보여요」 + 「공개 설정 바꾸기」(→ `/settings/profile`) |
| `/posts/{id}` | 본문·추천 줄 아래 작성자 카드(아바타·닉네임·뱃지·한 줄 소개·「프로필 보기 ›」) — 회원 글이고 프로필 공개이고 소개가 있을 때만. 사업자 핫딜 글·비회원·탈퇴·숨김 글은 없음. 차단으로 접힌 글은 함께 접힌다 |
| `/settings/profile` 한 줄 소개 도움말 | 「프로필을 공개하면 내 프로필과 내가 쓴 글 아래에 보여요.」 |

## 정책 (2026-11-07 확정 — 사용자 「서버 이어서 진행」, 추천안대로)
1. **늘 보임**: 닉네임·아바타·테두리·닉네임 색·등급/운영자/사업자 뱃지, 게시판의 글·댓글 자체(작성자 이름 포함), 차단·메모·쪽지 보내기(쪽지는 쪽지 수신 설정이 따로 정한다).
2. **비공개면 숨김(본인 외 모두 — 비회원·회원·게시판 매니저 포함)**: 한 줄 소개, 가입일, 활동 수치(글·댓글·받은 추천), 작성글·댓글·소모임 탭 목록, 글 보기의 작성자 한 줄 소개.
3. **본인**은 늘 다 본다. **운영자**는 관리자 앱 회원 상세에서 늘 본다(공개 사이트에서는 예외 없음 — 운영자 회원 계정도 남과 같다).
4. 기본값 **공개**(기존 회원은 지금처럼 보인다).

## API
- `GET/PUT /api/me/profile` 에 `visibility: 'PUBLIC'|'PRIVATE'` 칸(요청은 선택 — 없으면 그대로). 닉네임·소개와 같은 저장, 정지 중 `403 ACCOUNT_RESTRICTED`(같은 요청이라).
- `GET /api/users/{id}/profile` 에 `visibility`. 비공개이고 본인이 아니면 `bio`·`joinedAt`·`stats` 가 null.
- `GET /api/users/{id}/posts|comments|groups` — 비공개이고 본인이 아니면 `403 PROFILE_PRIVATE`.
- 글 상세 `author.bio`(글 상세만, 목록·댓글에는 싣지 않음) — 회원·공개·소개 있음일 때만 값, 그 밖 null.
- 저장: shared 마이그레이션 V160(V157 아바타 상점·V158·V159 운영 주체 단계가 씀 — 합치기 전 develop 최신 번호를 다시 확인) `profiles.profile_visibility varchar(16) not null default 'PUBLIC'`.

## 화면 연결 (2026-11-07)
- 목업(`profile-visibility-mock.ts`·`usePostAuthorBio`·스위치 `?mock=profile-private|author-bio`)을 지우고 실제 API 로 바꿨다. 글 보기 작성자 카드는 글 상세 `author.bio` 만 쓴다.

## 결정
- **글 보기 작성자 카드**(2026-11-06 사용자 「이대로 두기」): 위치는 추천 줄 아래, 모양은 아바타·닉네임·뱃지·한 줄 소개·「프로필 보기 ›」 그대로.

- **정책 1~4 · 정지 중 처리 · 댓글 옆 소개**(2026-11-07 사용자 「서버 이어서 진행」 — 추천안대로): 위 정책 그대로. 정지 중에는 공개 범위도 다른 프로필 칸처럼 바꿀 수 없다(같은 저장 `403 ACCOUNT_RESTRICTED`). 한 줄 소개는 글 보기 본문 아래에만(댓글 옆에는 없음).

## 미확정
- 없음

## 서버 구현 결과 (2026-11-07)
- **마이그레이션**: shared `V160__profile_visibility.sql` — `profiles.profile_visibility varchar(16) not null default 'PUBLIC'` + `ck_profiles_profile_visibility`(PUBLIC|PRIVATE). 가번호 — 합치기 직전 develop 의 shared 최신 번호를 다시 본다. 엔티티 `ProfileEntity.visibility`(enum `ProfileVisibility`, `changeVisibility`·`isPrivate`).
- **`GET/PUT /api/me/profile`**: 응답 `visibility: 'PUBLIC'|'PRIVATE'`(늘 채움). 요청 `visibility` 선택(없으면 그대로), 대소문자·공백까지 정확히 두 값만 — 그 밖은 `400 VALIDATION fields.visibility`(문구 `profile.visibility.invalid`, 닉네임·소개 칸 오류와 함께 모임). 정지 중 `403 ACCOUNT_RESTRICTED`가 칸 검사보다 먼저. 공개 범위만 바꾸는 저장은 닉네임 변경 횟수·변경권·이력을 쓰지 않음.
- **`GET /api/users/{id}/profile`**: `visibility` 추가. 비공개이고 본인이 아니면(비회원 포함) `bio`·`joinedAt`·`stats` 가 null(수치는 세지도 않음). 닉네임·등급·아바타·장식·뱃지·차단·메모는 그대로.
- **`GET /api/users/{id}/posts|comments|groups`**: 비공개이고 본인이 아니면 `403 PROFILE_PRIVATE`(「비공개 프로필이에요.」, `ProfilePrivateException`). 순서: 400(size·cursor) → 404 `PROFILE_NOT_FOUND` → 403.
- **글 상세 `author.bio`**: `PostAuthorView` 끝에 `bio` 칸 — 글 상세(`GET /api/posts/{id}`, 고친 뒤 응답 포함)만 채운다(`AuthorViews#detailBio`, 이미 읽은 프로필 + 회원 상태 기본 키 한 번). 회원 글·작성자 정상(탈퇴·탈퇴 진행 중 아님)·공개·소개 있음일 때만 값, 그 밖 null. 목록·댓글·프로필 작성글 줄 등은 늘 `bio: null`.
- 화면 타입(`useProfileApi.types.ts`·`useUserProfileApi.types.ts`·`usePostApi.types.ts`)과 같은 모양 — 다른 점 없음.
- 시험: `server/api/.../profile/ProfileVisibilityHttpTest`(10개).
