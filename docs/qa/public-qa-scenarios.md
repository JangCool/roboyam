# 공개 사이트 QA 시나리오 (2026-11-17)

> 사용자 요청: 공개 사이트(user-web) 전체를 **회원별**로 시험한다. 대상은 **등급별**(11단계)과 **계정 유형별**(비회원·미인증·일반·사업자·홍보 전용·성인/미성년·매니저/스텝·정지·기능 제한·탈퇴 진행·운영자 회원 계정)이다. 각 회원이 사이트의 메뉴와 화면을 쓸 때 화면이 제대로 보이는지, 기능이 동작하는지 본다. 에이전트가 사용자의 Chrome(Claude in Chrome)에서 직접 로그인해 확인한다.

## 0. 범위와 우선순위
| 순위 | 묶음 | 내용 |
|---|---|---|
| **P0** | A. 자격 행렬 | 회원마다 글쓰기·댓글·추천·신고·쪽지·소모임 개설·출석·포인트 게임·상점·핫딜·19금·성인 게시판이 **규칙대로 허용/거절**되는가. 거절은 맞는 코드와 안내 문구로 되는가 |
| **P0** | B. 화면 순회 | 모든 공개 화면(3절)을 회원 유형마다 열어 본다. 화면이 깨지지 않는가, 콘솔 오류·5xx 가 없는가, 로그인 필요 화면이 `/login?next=` 로 가는가 |
| **P0** | C. 등급 | 11등급 회원마다 등급 표시(`/me/grade`·프로필)가 맞는가. 등급 경계(견습↔정식: 핫딜·소모임 개설, 고수↔대가: 닉네임 이미지, 새싹: 새 회원 제한)가 맞게 동작하는가 |
| P1 | D. 유형별 흐름 | 가입→메일 인증, 사업자 신청→승인→사업자 센터, 성인 인증, 매니저 처리, 정지 안내·이의 제기, 탈퇴 신청→취소, 운영자 회원 계정 |
| P1 | E. 기능 흐름 | 글 쓰기·고치기·지우기, 댓글, 추천, 신고, 검색, 알림, 쪽지, 소모임, 상점 교환, 로보티콘, 룰렛·로또, 설정 화면 저장 |
| P2 | F. 모바일 폭 | 375px 에서 하단 탭·메뉴 서랍·글 목록·글쓰기 |

범위 밖: 실제 PG 결제(공동구매 주문 결제·충전), 소셜 로그인 외부 연동, 운영 빌드 성능·og 메타, 관리자 화면(관리자 QA 로 끝남).

## 1. 환경
| 항목 | 값 |
|---|---|
| 공개 화면 | http://localhost:3100 (run-develop dev, develop `24c319f1`) |
| 서버 | api 8910 · admin 8920 (jar, develop `0525d9de` 로 만든 것) |
| DB | `irobit-local-postgres`(5420). QA 전 백업: 컨테이너 `/tmp/irobit-before-public-qa-20261117.dump` |
| 메일 | Mailpit http://localhost:8035 |
| 브라우저 | 사용자 Chrome + Claude in Chrome. 동시에 두 회원이 필요하면 `localhost`·`127.0.0.1`·`[::1]` 로 쿠키를 나눈다 |
| 관리자 조작 | 회원 준비(사업자 승인·정지·매니저 지정 등)는 관리자 API 를 운영자 QA_ROOT 로 부른다 |

## 2. 시험 회원
비밀번호는 모두 같고 `docs/qa/public-qa-accounts.local.md`(git 제외)에만 적는다. 이메일은 `pub-*@qa.local`.

| 키 | 등급 | 유형·준비 방법 |
|---|---|---|
| GUEST | — | 비회원(로그아웃 상태) |
| G01~G11 | 새싹·견습·정식·숙련·정예·고수·달인·명인·대가·전설·신화 | 일반 회원, 인증됨. SQL 로 만들고 등급·레벨·XP·가입 일수를 그 등급 조건에 맞춤(`grade_manual=true`) |
| SIGNUP | 새싹 | 화면 `/signup` 으로 실제 가입 → Mailpit 링크로 인증 |
| UNVERIFIED | 새싹 | 이메일 미인증 |
| BIZ | 정식 | 화면 `/business/apply` 로 신청 → 관리자 API 로 국세청 확인·승인 |
| PROMO | 정식 | 홍보 전용 계정(`user_roles` 에 `ROLE_MEMBER_BUSINESS`, API 없음 — SQL) |
| ADULT / MINOR | 정식 | `/settings/identity` 가짜 본인인증(생년 1990 / 2010) |
| MANAGER / STAFF | 고수 / 정식 | 관리자 API 로 자유게시판(`free`) 매니저·스텝 지정 |
| SUSPENDED | 정식 | 관리자 API 로 7일 정지 |
| RESTRICTED | 정식 | 관리자 API 로 기능 제한(포인트 동결) |
| WITHDRAWING | 정식 | 화면에서 탈퇴 신청(진행 중) → 취소까지 |
| OPERATOR | 정식 | 운영자 QA_CONTENT 와 같은 이메일로 가입·인증 → 관리자 API 로 운영자 회원 계정 연결 |

## 3. 화면 순회 목록 (B)
| 묶음 | 경로 | 진입 |
|---|---|---|
| 홈·피드 | `/` `/latest` `/popular` `/boards` `/menu` `/p/community` `/tips` `/search?q=` | 누구나 |
| 게시판·글 | `/boards/free` `/boards/hotdeal` `/boards/notice` `/boards/anonymous` `/boards/video` `/boards/frontback` `/posts/{id}` `/write?board=free` | 누구나(성인 전용은 성인만) |
| 게시판 운영 | `/boards/free/actions` `/boards/free/sanctions` | 매니저·스텝·운영자 회원 |
| 소모임 | `/groups` `/groups/me` `/groups/new` `/groups/{id}` | 목록 누구나, 나머지 로그인 |
| 공동구매 | `/group-buys` `/group-buys/{id}` `/me/orders` `/me/reviews` | 목록 누구나 |
| 상점·로보티콘·게임 | `/point-shop` `/point-shop/{id}` `/me/items` `/robotikon` `/robotikon/{id}` `/robotikon/guide` `/robotikon/studio` `/me/robotikon` `/games/roulette` `/games/lotto` | 상점·제작·게임은 로그인 |
| 내 정보 | `/me` `/me/points` `/me/grade` `/users/{id}` `/notifications` `/messages` `/messages/new` | 로그인(프로필은 누구나) |
| 설정 | `/settings/profile` `display` `view` `notifications` `messages` `blocks` `memos` `addresses` `avatar-upload` `nickname-image` `identity` `account` `account/email` `account/password` `account/withdraw` | 로그인 |
| 사업자 | `/business` `/business/apply` `/business/applications` `/business/ads/new` `/business/deals/new` | 로그인 |
| 계정 | `/login` `/signup` `/password/forgot` `/verify-email` `/sanction` `/sanction/appeal` | 비회원 / 로그인 |
| 약관·소개 | `/terms` `/privacy` `/policy` `/about` `/licenses` | 누구나 |

**화면 하나를 볼 때**: (1) 화면이 뜨고 주요 내용이 보인다 (2) 콘솔 error 없음 (3) 네트워크 5xx 없음, 4xx 는 그 회원에게 맞는 거절일 때만 (4) 로그인 필요 화면은 비회원이면 `/login?next=` 로 간다 (5) 자격이 없는 버튼은 숨기거나 끄고 안내한다.

## 4. 자격 행렬 (A)
회원마다 아래 동작을 화면(버튼·안내)과 API 응답 둘 다로 본다. ○ 허용, × 거절(기대 코드).

| 동작 | GUEST | UNVERIFIED | G01 새싹 | G02 견습 | G03+ 정식 이상 | PROMO | SUSPENDED | WITHDRAWING |
|---|---|---|---|---|---|---|---|---|
| 자유게시판 글·댓글 | × LOGIN_REQUIRED(GUEST 게시판이 아니면) | × EMAIL_NOT_VERIFIED | ○ (링크 2·이미지 3 한도) | ○ | ○ | × BUSINESS_ACCOUNT_RESTRICTED | × ACCOUNT_RESTRICTED | × |
| 공지 게시판 글 | × | × | × STAFF_ONLY | × | × | × | × | × |
| 핫딜 글(`writeMin=REGULAR`) | × | × | × | × GRADE_TOO_LOW | ○ | × | × | × |
| 익명 게시판 글 | × LOGIN_REQUIRED | × | ○ | ○ | ○ | × | × | × |
| 추천 | × | ? | ○(보너스 인정 안 됨) | ○ | ○ | × | × | × |
| 신고 | × | ? | ○ | ○ | ○ | ○ | × | × |
| 쪽지 보내기(가입 7일 뒤) | × | × | ○ | ○ | ○ | × | × | × |
| 소모임 개설(`min_grade=REGULAR`) | × | × | × GROUP_GRADE_TOO_LOW | × | ○ | × | × | × |
| 출석 | × | × | ○ | ○ | ○ | × | × | × |
| 포인트 게임 | × | ? | ○ | ○ | ○ | × | × | × |
| 닉네임 이미지 신청(대가 이상) | × | × | × | × | 대가부터 ○ | — | × | × |

추가로 볼 회원:
- **ADULT**: 성인 게시판(`frontback`)이 메뉴·목록에 보이고 글이 열린다. 19금 글 열람과 쓰기 ○.
- **MINOR**: `frontback` 은 404 처럼, 19금 글은 403 `POST_ADULT_ONLY`(UNDERAGE).
- **본인인증 안 한 회원**: 19금 글에서 본인인증 대화상자(IDENTITY).
- **MANAGER/STAFF**: `free` 에서 남의 글 숨기기·삭제, 댓글 삭제, 게시판 제재 ○. 다른 게시판에서는 ×. 처리 내역 화면 ○. 공지·고정은 매니저만.
- **RESTRICTED(포인트 동결)**: 포인트 쓰는 기능만 막히고(`MEMBER_RESTRICTED`) 글쓰기는 된다.
- **OPERATOR**: 「운영자」 뱃지, 모든 게시판에서 운영진 글쓰기, QA_CONTENT 권한만큼 숨기기·삭제.
- **BIZ(승인)**: 글에 「사업자」 뱃지, 사업자 센터. 광고·핫딜 신청은 그 서비스가 승인돼야 한다.

## 5. 등급 시나리오 (C)
| # | 대상 | 확인 |
|---|---|---|
| C-1 | G01~G11 | 로그인 → `/me/grade` 의 등급 이름·레벨·다음 등급 조건, `/me`·공개 프로필 등급 뱃지(도트 그림) |
| C-2 | G01~G11 | 메뉴: 최소 등급이 걸린 메뉴가 있으면 등급에 따라 보이고 숨는지(없으면 기록만) |
| C-3 | G02 견습 / G03 정식 | 핫딜 글쓰기 버튼·`/write?board=hotdeal` 거절 안내, 소모임 만들기 버튼 꺼짐/켜짐 |
| C-4 | G06 고수 / G09 대가 | `/settings/nickname-image` 신청 가능 여부 |
| C-5 | G01 새싹 | 글에 링크 3개·이미지 4개 넣으면 거절, 매니저 지정 대상 아님 |
| C-6 | 글 줄 | 글 목록·댓글 작성자 줄에 등급이 안 나오는지(설계: 프로필에서만) |

## 6. 유형별·기능 흐름 (D·E)
| # | 대상 | 흐름 |
|---|---|---|
| D-1 | SIGNUP | `/signup` 가입 → 바로 로그인·인증 배너 → Mailpit 링크 → 인증 완료·가입 포인트 지급 → 온보딩 |
| D-2 | 회원 | 로그아웃·로그인, 틀린 비밀번호 안내, `/password/forgot` → 메일 → 재설정 → 새 비밀번호로 로그인 |
| D-3 | BIZ | `/business/apply` 신청(등록증 그림·번호) → 관리자 승인 → 사업자 센터·뱃지 → 핫딜·광고 신청 화면 |
| D-4 | ADULT/MINOR | `/settings/identity` 가짜 인증 → 성인 게시판·19금 글 |
| D-5 | MANAGER/STAFF | 남의 글 숨기기 → 처리 내역 → 게시판 제재 → 제재 내역 |
| D-6 | SUSPENDED | 정지 배너, `/sanction` 내용, 이의 제기 접수, 쓰기 버튼 거절 안내 |
| D-7 | WITHDRAWING | `/settings/account/withdraw` 신청 → 배너·막힘 → 취소 → 정상 |
| D-8 | OPERATOR | `/settings/account` 운영자 카드, 탈퇴 409, 뱃지 |
| E-1 | 정식 회원 | 글 쓰기(말머리·태그·그림) → 목록·상세 → 고치기 → 지우기 |
| E-2 | 두 회원 | 댓글·답글 → 상대에게 알림 → 추천·비추천 → 신고 |
| E-3 | 회원 | 검색(글·태그), 알림 읽음, 쪽지 보내기·받기·차단 |
| E-4 | 정식 회원 | 소모임 가입 신청·글쓰기, 소모임 만들기 |
| E-5 | 회원 | 출석, 룰렛·로또, 상점 품목 교환(포인트), 로보티콘 보기 |
| E-6 | 회원 | 설정 화면마다 바꿔 저장 → 다시 열어 유지 확인 → 되돌림 |
| F-1 | 회원 | 375px: 하단 탭 이동, 메뉴 서랍, 글 목록·상세·글쓰기 |

## 7. 진행 방식
- 결과는 `docs/qa/public-qa-log.md` 에 한 줄씩 적는다: `항목 | 회원 | 결과(통과/실패/보류) | 증거 | 조치`.
- 시험 글·댓글은 제목에 `[QA]` 를 붙이고, 끝나면 지운다(지운 글은 기록에 남긴다).
- 버그를 찾으면 원인을 확인하고 기록한다. 고치는 건 사용자 확인 뒤에 한다(이번 요청은 시험).
- 브라우저 확인 창(confirm)은 확장을 멈추게 하므로, 확인 창이 뜨는 동작은 API 로 대신하거나 미리 알린다.
