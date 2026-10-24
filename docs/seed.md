# 시험 데이터 (seed)

로컬 개발 DB 에 community 에서 쓰던 시험 계정·글·댓글·추천을 채운다 — **community 의 그 계정(이메일·비밀번호)으로 로보얌 공개 사이트에 로그인**하기 위한 것이다.

| 파일 | 역할 |
|---|---|
| `seed/seed.json` | **데이터**(community 에서 넘어온 그대로). 계정 13개(베테랑 2개 포함 — 2026-10-24 로보얌에서 더함) · 글 6개 · 댓글 7개 · 추천 16개. 그 밖의 칸(투표·임시저장·공동구매 등)은 로보얌에 아직 없는 기능이라 로더가 건너뛴다 |
| `scripts/seed-local.mjs` | **로더**(Node 내장 `fetch` + `docker exec … psql`, 의존성 없음). 서버 코드는 바꾸지 않았다 |

## 쓰는 법

```bash
cd docker/local && docker compose up -d     # 로컬 DB(roboyam-local-postgres)
# 공개 서버(8910)를 띄우고, 공개 사이트의 /install 에서 설치 마법사를 먼저 마친다 — 로더는 설치하지 않는다
node scripts/seed-local.mjs                 # 또는 pnpm seed:local
```

- `pnpm seed:local` 은 같은 명령의 별칭이다. pnpm 은 실행 전에 의존성을 확인하므로 **`node_modules` 가 없는 새 체크아웃(worktree)에서는 먼저 프론트 의존성 설치가 돈다** — 그런 곳에서는 `node scripts/seed-local.mjs` 를 쓴다.

- **설치 후에만 돈다.** 설치 전 DB 면 아무것도 바꾸지 않고 멈춘다(설치 마법사를 마치고 다시 돌린다).
- **계정 단계는 늘 멱등이다** — 같은 이메일이 이미 있으면 건너뛴다(비밀번호·상태도 고치지 않는다). 몇 번을 돌려도 된다.
- **콘텐츠 단계(글·댓글·추천)는 DB 에 글이 하나라도 있으면 건너뛴다**(종료 코드 0, 안내만). 덧붙이려면 `--force` — 같은 글이 한 벌 더 생긴다.
  단 서버의 「같은 글 반복」 규칙(보안 설정 `duplicate_post_window_minutes`, 기본 10분) 안에서는 같은 회원의 같은 제목·본문 글이 `409 DUPLICATE_POST` 로 거절되어 그 글과 거기 딸린 댓글·추천은 「못 한 것」으로 나온다(서버 규칙을 우회하지 않는다).
- 로그인은 `e2e.author@example.com` 같은 이메일과 **seed.json 의 `password`** 로 한다(모든 계정이 같은 값, 시험용). 로더는 이 값을 출력하지 않는다.
- 관리자 화면(admin-web)은 **설치 때 만든 루트 계정으로** 들어간다. 시드의 `e2e.admin` 은 로보얌에서는 일반 회원일 뿐이다(운영자는 `operators` 표의 별도 계정 — 로더는 운영자를 만들지 않는다).

### 환경 변수(전부 선택)

| 변수 | 기본 | 뜻 |
|---|---|---|
| `SEED_API` | `http://localhost:8910` | 공개 서버. `localhost`·`127.0.0.1`·`[::1]` 만 허용 |
| `SEED_DB_CONTAINER` | `roboyam-local-postgres` | DB 컨테이너 |
| `SEED_DB_NAME` | `roboyam` | SQL 대상 DB. **`SEED_API` 를 기본과 다르게 주면 반드시 준다** |
| `SEED_DB_USER` | `roboyam` | DB 사용자 |

다른 서버·DB 에서 시험할 때(예: 일회용 DB 에 붙인 9091 서버):

```powershell
$env:SEED_API = "http://localhost:9091"; $env:SEED_DB_NAME = "roboyam_seedtest"; node scripts/seed-local.mjs
```

## 안전장치 (community 로더와 같은 수준)

아래에 걸리면 **아무것도 바꾸기 전에** 멈춘다(종료 코드 2).

- 서버 주소가 localhost 가 아니다.
- `SEED_API` 를 기본과 다르게 줬는데 `SEED_DB_NAME` 이 없다 — 로더의 SQL 이 기본 DB(개발 DB)에 닿는 사고를 막는다. 일부러 기본 DB 면 `SEED_DB_NAME=roboyam` 으로 명시한다.
- 서버의 설치 상태(`GET /api/system/installation/status`)와 SQL 대상 DB 의 설치 상태(`installation.status`)가 다르다 — 서버가 붙은 DB 와 `SEED_DB_NAME` 이 다르다는 뜻이다.
- 설치 전 DB 다.
- 서버가 보여 주는 게시판(`GET /api/boards`)이 SQL 대상 DB 의 `boards` 에 없다(서버와 DB 가 다르다는 두 번째 확인).
- DB 가 모든 SQL 을 로그에 남기는 설정이다(`log_statement = all` 또는 `log_min_duration_statement = 0`) — bcrypt 해시를 DB 안에서 만들어 비밀번호가 SQL 한 줄에 실리기 때문이다.

그리고: 계정을 만든 뒤 **모든 시드 계정으로 공개 API 에 실제 로그인**해 본다. 방금 만든 계정이 로그인되지 않으면(서버가 다른 DB 를 보거나 해시가 맞지 않으면) 콘텐츠 단계를 하지 않고 종료 코드 1 로 끝낸다.
비밀번호는 psql 명령 인자가 아니라 표준 입력으로 넘기고(프로세스 목록에 보이지 않게), 그 SQL 의 오류는 문장 내용 없이 오류 종류만 출력한다.

## 로더가 하는 일

### 1) 계정 — SQL (로보얌에는 공개 이메일 가입 API 가 없다)

`seed.json` 의 `users` 전부를 회원으로 만든다. 공개 가입(소셜 가입 완료, `SocialAccountWriter.createMember`)과 같은 모양이다.

| 표·칸 | 값 |
|---|---|
| `users.id` | Snowflake(서버 번호 1023 — 앱 서버의 `NODE_ID` 와 겹치지 않게) |
| `users.email` | 소문자 |
| `users.password_hash` | pgcrypto `crypt(password, gen_salt('bf', 10))` = `$2a$10$…` — 서버 `BCryptPasswordEncoder`(강도 10)와 호환. pgcrypto 확장이 없으면 그 트랜잭션 안에서만 만들어 해시를 받은 뒤 되돌린다(DB 에 확장이 남지 않는다) |
| `users.role` | `USER`(로보얌 회원은 `USER` 뿐 — `role: ADMIN` 도 회원) |
| `users.status` | `seed.json` 의 `status`(`e2e.banned` = `SUSPENDED` — 로그인은 되고 응답의 `restriction` 과 쓰기·추천 판정에서 막힌다) |
| `users.created_at` | 지금 − `ageDays` 일(가입 일수 — 등급 승급 조건 `grades.min_days`·새 회원 규칙에 쓰인다) |
| `users.email_verified_at` | `emailVerified` 면 가입 시각, 아니면 NULL(`e2e.unverified` — 이메일 미인증이라 글·댓글 쓰기가 막힌다) |
| 필수 동의 `terms_version`·`terms_agreed_at`·`age_confirmed_at` | `v1`(공개 가입과 같은 약관 버전) · 가입 시각 · 가입 시각 |
| `profiles` | 닉네임 · 등급 — 시드에 `grade`(등급 키, 예 `VETERAN`)가 있고 그 등급이 있으면 그것(`grade_manual = true`), 없으면 기본 등급(`grades` 의 level 1, 기본 `SEEDLING`)·`grade_manual = false` |

소셜 연결(`user_identities`)·역할(`user_roles` — 일반 회원·등급 역할은 파생이라 행이 없다)·포인트 잔액은 만들지 않는다(공개 가입도 만들지 않는다). 닉네임을 다른 회원이 이미 쓰고 있으면(대소문자 무시) 그 계정은 만들지 않고 알린다.

### 2) 글·댓글·추천·지우기 — 공개 API (그 회원으로 로그인해서)

서버 규칙·카운터·포인트 적립·회원 알림이 그대로 적용된다. 순서: 글 → 댓글 → 추천 → 지우기(`p6`, 추천이 쌓인 뒤 글쓴이가 지운다).

- **게시판 규칙**: 시드 글의 게시판(`board` slug)이 있으면 그대로 쓴다. 없으면 **`free` 로 옮긴다**(`free` 도 없으면 그 글은 건너뛴다). **`notice` 로는 옮기지 않는다**(중립 설치의 공지는 운영진 전용 쓰기). 쓰기 전에 그 회원의 게시판 판정(`viewer.canWrite`)을 보고 쓸 수 없으면 까닭과 함께 건너뛴다.
  지금 시드 글 6개는 모두 `ai`(ROBOYAM 예시 구성) 게시판이라 **중립 설치(`free`·`notice`)에서는 6개 모두 `free` 로 간다.** ROBOYAM 예시 구성으로 설치한 DB 면 `ai` 에 그대로 간다.
- **말머리 규칙**: 옮긴 게시판에 같은 이름의 말머리가 있고 운영진 전용이 아니면 그것, 아니면 말머리 없이 쓴다(`free` 에는 `뉴스` 가 없어 말머리 없이).
- **답글**: 로보얌 답글은 1단계다 — `parentId` = 답한 댓글의 원댓글, `replyToId` = 답한 댓글.
- **작성 간격**: 로보얌은 작성 속도를 따로 표에 두지 않고 `posts`·`comments` 의 `created_at` 최댓값으로 판정한다(기본 글 60초·댓글 10초). `429 POST_TOO_FAST`·`COMMENT_TOO_FAST` 를 받으면 그 시드 회원의 최근(1시간 안) 글·댓글의 `created_at`·`updated_at` 을 응답의 `retryAfterSeconds` + 1초만큼 앞으로 당기고 다시 쓴다. 하루 한도(`*_DAILY_LIMIT`, 기본 글 30·댓글 200)는 비우지 않는다(시드 양으로는 닿지 않는다).
- 끝에 시드 회원별 포인트 잔액·회원 알림 수를 출력한다.

### 건너뛰는 것(로보얌에 아직 없는 기능 — 실행할 때 목록으로 출력한다)

투표(`p4`·`p5` 의 `poll` — 글은 투표 없이 쓴다) · 운영자 역할(`e2e.admin`) · 임시저장(`drafts`) · 공동구매(`commerce`) · 광고 직접 배너(`adBanners`) · 핫딜 입점(`hotdealBusiness`) · 직접홍보·끌올(`promoBump`) · 홈 가젯(`homeGadgets`) · ROBOYAM 운영 예시(`roboyamConcept`) · 영상·짤 시험 글(`galleryPosts`).

## 계정

비밀번호는 모두 **seed.json 의 `password`** 다.

| 키 | 이메일 | 닉네임 | 상태 | 이메일 인증 | 가입 일수 | 시드에서의 쓰임 |
|---|---|---|---|---|---|---|
| `e2e.author` | `e2e.author@example.com` | e2eauthor | 정상 | ○ | 6 | 글쓴이(글 5 · 댓글 2) |
| `e2e.l1`~`e2e.l5` | `e2e.l1@example.com` ~ `e2e.l5@example.com` | e2el1 ~ e2el5 | 정상 | ○ | 6 | 댓글·추천 |
| `e2e.admin` | `e2e.admin@example.com` | e2eadmin | 정상 | ○ | 6 | 글 1(community 에서는 최고 관리자 — **로보얌에서는 회원**) |
| `e2e.banned` | `e2e.banned@example.com` | e2ebanned | 정지(`SUSPENDED`) | ○ | 6 | 로그인은 되고 쓰기·추천이 막힌다 |
| `e2e.user1` | `e2e.user1@example.com` | 로봇덕후 | 정상 | ○ | 1 | |
| `e2e.newbie` | `e2e.newbie@example.com` | e2enewbie | 정상 | ○ | 1 | 가입 1일 |
| `e2e.unverified` | `e2e.unverified@example.com` | e2eunver | 정상 | ✕ | 1 | 이메일 미인증 |

관리자 화면: **설치 마법사에서 만든 루트 계정**(operators). 시드 계정으로는 관리자 앱에 로그인할 수 없다.

## 결과 (2026-10-23, 일회용 DB 에서 확인)

같은 컨테이너의 일회용 DB 에 develop 의 공개 서버(9091)를 붙여 **중립 설치**(`free`·`notice`) 뒤 돌렸다. 로더는 약 6초.

- 계정 11개 만듦 · **11개 모두 로그인 성공**(`e2e.banned` 는 `restriction.status = SUSPENDED`, `e2e.unverified` 는 `emailVerified = false`). 해시는 `$2a$10$…`, 동의 시각 = 가입 시각, 등급 `SEEDLING`. 운영자는 설치 때의 루트 1명 그대로. pgcrypto 확장은 남지 않았다.
- 글 6(모두 `ai` → `free`, 말머리 없이) · 댓글 7(답글 1) · 추천 16/16 · 글 1 지움.
- 포인트: `e2eauthor` 90P(글 5×20 + 댓글 2×5 − 지운 글 20), 댓글 쓴 회원 5P, `e2eadmin` 20P. **추천 보너스는 없다** — 서버 규칙상 「인정되는 추천」은 기본 등급보다 높은 정상 회원의 추천인데 시드 회원은 모두 새싹(가입 6일 < 일반 등급 7일)이라서다(`PointLedgerWriter.recognizedLiker`).
- 회원 알림: `e2eauthor` 에 댓글 알림 1줄(4건 묶음) + 답글 알림 1. 서버 로그 ERROR 0, 서버·DB 로그·로더 출력에 비밀번호 없음.
- 두 번째 실행: 계정 전부 「이미 있음」·로그인 11/11, 콘텐츠는 「글이 이미 6개 있어 건너뜀」(종료 0). `--force` 를 같은 10분 안에 돌리면 `409 DUPLICATE_POST` 로 같은 글이 거절된다(위 「쓰는 법」).
- 안전장치: `SEED_DB_NAME` 없이 다른 서버 주소 → 멈춤, localhost 아님 → 멈춤, 설치 전 서버 + 설치된 DB(서버와 다른 DB) → 멈춤, 설치 전 DB → 멈춤.
- 화면(Nuxt)에서 눈으로 본 확인은 하지 않았다.

### 베테랑 계정 추가 (2026-10-24)
- `e2e.veteran1`(베테랑일호)·`e2e.veteran2`(베테랑이호) — 가입 120일·이메일 인증·등급 `VETERAN`. 닉네임 이미지 신청(기본 조건 「베테랑 이상」)처럼 높은 등급이 필요한 흐름을 시험할 때 쓴다. 이미 있는 DB 에서도 로더를 다시 돌리면 이 둘만 새로 만든다.
