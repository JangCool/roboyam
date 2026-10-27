# 설치 마법사 계약 (화면 확정 2026-10-20)

**의도**: 서버를 처음 띄우면 "설치 전" 상태이고, 브라우저 `/install` 에서 설치 토큰으로 인증한 뒤 루트 계정·사이트·메일·소셜 키를 한 번에 설치한다. 로그인 없이 쓰는 구간이라 보안(토큰·잠금·세션·CSRF)이 핵심이다.
**상세 규칙의 출처**: community 의 `docs/api-install.md` (`D:\project\community\workspace\community\docs\api-install.md`) — 이 문서는 그것과 같다. 아래는 roboyam 에서 달라지는 점과 범위만 적는다.

## 화면
- 경로 `/install` (`apps/user-web`, 셸 없음). 단계: 토큰·점검 → 루트 계정 → 사이트 → 메일(건너뛰기·연결 확인) → 소셜(건너뛰기) → 기본 게시판 구성 → 확인 → 설치 → 완료(`/login`).
- 필드·검증은 화면 코드가 기준이다: `apps/user-web/app/utils/util-install.ts`, `util-install-messages.ts`, `composables/install/api/useInstallApi.types.ts`. 서버는 같은 규칙을 다시 검사한다(root 이메일·닉네임 2~12·비밀번호 8~64 영문+숫자, site 이름 1~50·공개 주소 `http(s)://호스트[:포트]`·슬로건 1~100, mail·social 입력 규칙).

## API (모두 `/api/system/installation/…`, 응답 `Cache-Control: no-store`)
아래 JSON·오류 모양은 공통 응답 래퍼의 `data`/`error` 내용이다(`.claude/guides/backend/exception.md`).

`GET status` · `POST verify-token` · `GET checks` · `GET board-presets` · `POST mail-check` · `POST complete`. 요청/응답 모양·오류 코드·검사 순서는 `composables/install/api/useInstallApi.types.ts` 와 community `api-install.md` 와 같다.
- 오류: `503 NOT_INSTALLED`(설치 전 다른 `/api/**`) · `409 ALREADY_INSTALLED` · `401 INSTALL_AUTH_REQUIRED` · `401 INSTALL_TOKEN_INVALID` · `429 INSTALL_TOKEN_LOCKED`(+`retryAfterSeconds`, `Retry-After`) · `400 VALIDATION`(`fields` 경로) · `409 EMAIL_TAKEN`/`NICKNAME_TAKEN` · `503 CRYPTO_KEY_MISSING`.

## 정책
- 설치 토큰: 환경 변수 `ROBOYAM_INSTALL_TOKEN`(`roboyam.install.token`). 없으면 설치 전 기동 때 무작위 24자(혼동 글자 제외)를 만들어 **로그에 한 번만** 출력한다. DB 에는 SHA-256 해시만 둔다(`installation.token_hash`). 비교는 상수 시간. local 프로파일 기본값은 `roboyam-demo-token`(화면 목업과 같은 값).
- 토큰 잠금: IP 당 5회 연속 실패 시 10분 잠금(5번째 응답까지 401, 그 뒤 429, 잠긴 동안은 맞는 토큰도 429). 화면 목업의 60초는 데모용이다.
- 인증은 서버 세션(Spring Session JDBC, 30분 유휴). 인증 성공 때 세션 ID 를 바꾼다(세션 고정 방지). 설치가 끝나면 세션과 토큰 해시를 없앤다. CSRF 쿠키 `XSRF-TOKEN`/헤더 규약은 화면의 `useApiClient` 와 맞춘다.
- 설치 전 가드: `/api/system/installation/**` 외 모든 `/api/**` 는 `503 NOT_INSTALLED`. 설치 후 마법사 API 는 `status` 만 열려 있고 나머지는 `409 ALREADY_INSTALLED`. 설치 상태는 메모리 캐시, DB 를 읽지 못하면 설치 전으로 본다.
- `complete` 는 한 트랜잭션(루트 계정 ADMIN·이메일 인증됨 + 프로필 + 사이트 + 메일 + 소셜 + 완료 표시)이고 실패하면 아무것도 남지 않는다. 두 번 호출돼도 한 번만 설치된다(`installation` 행 잠금).
- 비밀번호·메일 비밀번호·소셜 시크릿·토큰은 로그·오류·`toString` 에 남기지 않는다. 메일 비밀번호·소셜 시크릿은 AES-256-GCM(`SecretCipher`, 키 `ROBOYAM_CRYPTO_KEY`)으로 암호화해 저장하고, 키가 없으면 그 값이 들어올 때 `503 CRYPTO_KEY_MISSING`(입력 오류보다 먼저).
- 점검 `checks`: `DATABASE` · `MIGRATIONS`(shared 스트림 기준) · `EXTENSIONS`(pg_trgm 함수 호출) · `CRYPTO_KEY`(막지 않음) · `DB_LOCALE`(막지 않음).
- `mail-check`: SMTP 접속·인증까지만, 메일은 보내지 않는다. 접속 5초·읽기 10초 제한, `STARTTLS` 는 평문으로 물러서지 않는다. 응답은 `ok`/`reason` 만.

## roboyam 에서 달라지는 점 (이번 범위의 결정)
1. **기본 게시판 구성(프리셋)** — (처음 결정. 게시판 생성은 그 뒤 `BoardPresetApplier` 로 구현됐고, 메뉴·소모임 분류까지 넓히는 것은 아래 「프리셋 구조 (2026-10-27)」가 정본이다.) 게시판·메뉴 표가 아직 없다. 그래서 `board-presets` 는 목록(프리셋 JSON 데이터 `install-presets/neutral.json`·`roboyam.json` 의 이름·설명·게시판 이름·말머리 이름)만 내려주고, `complete` 는 `boardPreset` 을 검증(모르는 값 `400`, 생략 시 `NEUTRAL`)해서 선택값만 `installation.board_preset` 에 기록한다(새 마이그레이션 `V6`). **게시판을 실제로 만드는 적용은 게시판 기능을 구현할 때 한다.** 슬로건 비워 두면 프리셋 `siteTagline` 을 쓰는 규칙은 지킨다.
2. **언어**: 프리셋 JSON 의 이름·설명·게시판·말머리 이름은 `name`/`description` 에 영문 `nameEn`/`descriptionEn` 을 짝으로 두고, `board-presets` 는 요청 언어(`Accept-Language`)가 `en` 이면 영문, 아니면 기본값을 같은 모양(문자열)으로 준다(설치 시점의 정적 데이터라 서버가 골라 준다). 설치 때 저장되는 사이트 슬로건(`siteTagline`)은 운영 데이터라 지금은 한국어 한 값이다(언어 정책은 미확정). 인증 메일 본문은 요청한 사람의 언어로 만든다.
3. 쓰지 않는 것: `installation.home_rules_seed`, 프리셋 `seedTexts`(홈·핫딜 시드 문구), `/api/system/info`.
3. 환경 변수 이름: `ROBOYAM_INSTALL_TOKEN`, `ROBOYAM_CRYPTO_KEY`, `NODE_ID`(Snowflake 서버 번호 0~1023, V2 의 ID 는 앱이 발급한다).
4. 로그인·로그아웃·내 정보 API 는 이번 범위가 아니다(루트 계정이 만들어져 나중에 로그인될 수 있으면 된다).

## 미확정 / 다음 작업
- docker·installer 템플릿에 `ROBOYAM_INSTALL_TOKEN`(설치기가 생성·출력)과 `NODE_ID` 를 다시 넣는다 — 백엔드 완료 후 메인에서 처리.
- 루트 계정의 `terms_version` 은 자리표시자 `'install'` 로 저장한다 — 약관 기능이 생길 때 정한다.
- 프리셋 적용(게시판 생성/삭제), 사이트 이름 기본값(`SAMPLE`), 화면의 `?mock=` 연동 교체(`// MOCK:` → `// REAL:`).

## 프리셋 구조 (2026-10-27 사용자 확정 — 구현 전)
**의도**: 설치 직후 공개 사이트가 「홈 + 게시판 한 폴더」만 보이던 것을, 프리셋이 고른 구조(게시판 + 공개 메뉴 + 소모임 분류)로 바로 쓸 수 있게 한다. 지금 만든 기능(인기글·최신글·소모임·포인트 상점)을 설치 직후부터 찾아 들어갈 수 있어야 한다.

**지금(바꾸기 전)**: 프리셋은 일반 게시판만 만든다(`BoardPresetApplier` — `kind: GROUP` 은 「소모임 기능 전까지」 건너뜀). 공개 메뉴 `main` 은 V43 시드(HOME + 자동 추가 폴더 「게시판」)에 게시판이 자동 추가될 뿐이고, `mypage` 그룹 항목은 V49 가 모두 숨겼다.

### 1. 공개 메뉴 `main` — 프리셋 JSON 의 `menu` 가 정한다
- 프리셋 JSON 에 `menu`(트리)를 더한다. 설치(`complete`)가 게시판을 만든 **뒤 같은 트랜잭션에서** `main` 그룹 트리를 이 정의로 바꾼다(V43 시드 노드를 지우고 새로 넣음, 그룹 `version` +1). 게시판 항목은 프리셋의 `slug` 로 가리킨다(만들지 못한 게시판 항목은 뺀다).
- **ROBOYAM**:
  ```
  홈(HOME, 모바일 탭) · 인기글(POPULAR, 모바일 탭) · 최신글(LATEST)
  📁 주제      — AI·테크 · 로봇 · 산업·주식 · 게임 · 스포츠
  📁 커뮤니티  — 영상·짤 · 자유게시판          ← autoAdd(새 게시판은 여기 끝에)
  📁 소모임    — 소모임 둘러보기(GROUPS_ALL, 모바일 탭) · 내 소모임(GROUPS_MINE, audience MEMBER)
  공지(BOARD notice)
  ```
- **NEUTRAL** [가정 → 확정, 사용자 2026-10-27]: 홈(모바일 탭) · 인기글(모바일 탭) · 최신글 · 📁 「게시판」(autoAdd, 만든 게시판 전부). 소모임 게시판이 없어 소모임 항목은 두지 않는다.
- 검색은 헤더에 있어 메뉴에 두지 않는다. 포인트 상점은 메인 메뉴에 두지 않는다(아래 3).
- 한도는 메뉴 계약 그대로(폴더 깊이 ≤3, 그룹 노드 ≤80, 모바일 탭 ≤10, autoAdd 폴더 하나). 소모임 게시판은 메뉴에 넣지 않는다(`board-contract.md` 「소모임 게시판」).

### 2. 소모임 게시판·분류 — 설치 때 만든다
- 프리셋의 `kind: GROUP` 게시판을 만들고, `groupCategoryName` 마다 **소모임 분류**(`group_categories`, 프리셋 차례 = `sort_order`, 이름 키 = 공백 빼고 소문자 — 지금 admin `GroupCategoryNames.key` 와 같은 규칙, api 설치도 쓰도록 shared 로 옮긴다)를 만들어 그 게시판을 잇는다(`group_category_boards`). 그래서 설치 직후 바로 소모임을 열 수 있다.
- ROBOYAM 분류 5개: AI·테크 · 로봇 · 산업·주식 · 게임 · 스포츠(각각 같은 이름의 「… 소모임」 게시판 하나). 산업·주식 소모임 게시판은 투자 고지를 그대로 켠다.
- **소모임 게시판 말머리는 공개 게시판과 다르게** — 공통 다섯 + 주제별 하나, **「공지」 말머리 없음**:

  | 게시판 | 공통(5) | 주제별(1) |
  |---|---|---|
  | AI·테크 소모임 | 가입인사 · 질문 · 후기 · 잡담 · 자료 | 프로젝트 |
  | 로봇 소모임 | 〃 | 제작기 |
  | 산업·주식 소모임 | 〃 | 스터디 |
  | 게임 소모임 | 〃 | 파티모집 |
  | 스포츠 소모임 | 〃 | 직관모임 |

  - 질문·후기·잡담은 시스템 공통 말머리를 켠다(지금 적용 규칙 그대로). 가입인사·자료·주제별은 고유 말머리(활성 한도 6 안).
  - 이유: 소모임 글은 멤버끼리의 활동이라 콘텐츠 분류(뉴스·해설)가 맞지 않고, 공개 전환된 글이 최신글·검색에 나올 때 공개 게시판 글처럼 보이지 않게. **「공지」를 빼는 이유**: 운영진 전용 말머리는 그 게시판 스텝만 고를 수 있어(`PostWriteService` 의 `actor.staffOf(boardId)`) 소모임 매니저는 쓸 수 없다 — 매니저 공지는 후속(소모임 글 고정 등)으로 따로 정한다.
  - 산업·주식 소모임에 종목 추천류 말머리는 두지 않는다.

### 3. 포인트 상점 — 마이페이지 + 헤더 포인트 표시
- 공개 메인 메뉴에는 두지 않는다. 들어가는 길은 이미 있는 것 그대로: 헤더 프로필 드롭다운의 포인트 줄(잔액)·「포인트 상점」 줄, 모바일 서랍의 포인트·포인트 상점, `/me`.
- 메뉴 그룹 `mypage` 에 포인트 상점(`MY_SHOP`)을 내 포인트(`MY_POINTS`) 다음에 넣는다(설치·새 DB 모두 — 마이그레이션).
- 충전(3단계)이 생기면 마이페이지 안을 「포인트」 폴더(내 포인트 · 충전 · 상점 · 내 아이템)로 묶는 것을 그때 정한다.

### 4. 마이페이지 메뉴 숨김 풀기 (모든 설치 — 새 마이그레이션)
- V49 가 「화면 없음」으로 숨긴 `mypage` 항목 중 **지금 user-web 에 화면이 있는 것**을 다시 보이게 한다: MY_HOME · MY_GRADE · MY_POINTS · MESSAGES · NOTIFICATIONS · SETTINGS_PROFILE · SETTINGS_ACCOUNT · SETTINGS_VIEW · SETTINGS_BLOCKS · NOTIFICATION_SETTINGS. 예약 글(MY_SCHEDULED, `/scheduled` 화면 없음)과 공동구매 그룹은 숨긴 채 둔다. 바뀐 그룹 `version` +1.
- 참고: user-web 은 아직 `mypage` 메뉴 그룹을 읽지 않는다(헤더 드롭다운·서랍·`/me` 의 고정 링크). 이 정리는 관리자 메뉴 화면과 이후 연결을 위한 것이다.

### 범위·미확정
- 바꾸는 곳: 프리셋 JSON 두 개(`menu`·소모임 말머리), 설치(`InstallationInstaller`·`BoardPresetApplier` — 메뉴 트리·소모임 분류), shared 마이그레이션 하나(마이페이지 숨김 풀기 + `MY_SHOP`). user-web·admin-web 화면은 바꾸지 않는다(헤더는 다른 세션 작업 중).
- 이미 설치된 곳(`applyOnceIfMissing`)에는 메뉴·분류를 새로 적용하지 않는다 — 운영자가 이미 구성했을 수 있다. 로컬은 DB 를 지우고 다시 설치해 확인한다.
- 미확정: 내 아이템(`/me/items`)의 메뉴 화면 키(지금 BUILTIN 에 없다), 소모임 매니저 공지 방식. (NEUTRAL 메뉴는 위 가정대로 확정 — 사용자 2026-10-27)

### 서버 구현 결과 (2026-10-27, 커밋 전)
- **바꾼 파일**: 프리셋 `server/api/src/main/resources/install-presets/{roboyam,neutral}.json`(`menu` · ROBOYAM 소모임 게시판 말머리) · `installation/dto/BoardPreset`(`menu`·`MenuEntry`·`Board.groupCategoryName`) · `installation/service/BoardPresetCatalog`(기동 때 `groupCategoryName`·`menu` 검사 — 틀리면 앱이 뜨지 않음) · `BoardPresetApplier`(설치용 `applyAtInstall` — 일반 게시판 → 소모임 게시판 → 분류 만들기·잇기 → 메뉴, `applyOnceIfMissing` 은 일반 게시판만) · 새 `PresetMenuApplier`(`main` 트리 교체) · `InstallationInstaller`(`applyAtInstall` 호출). `GroupCategoryNames` 를 admin → shared(`com.community.group.service`, 같은 패키지라 admin 코드는 그대로)로 옮기고 공개 메서드로.
- **`menu` JSON 형식**: 노드 배열(최상위 차례대로). `{"type":"FOLDER","label","nameEn","autoAdd","collapsible","collapsed","children":[…]}` · `{"type":"BUILTIN","key":"<MenuBuiltin>"}` · `{"type":"BOARD","slug":"<이 프리셋의 일반 게시판>"}`. 항목 공통 선택 칸: `label`·`nameEn`(비우면 대상 이름) · `mobileTab` · `audience`(비우면 `ALL`, `GRADE_MIN` 불가 — 등급 키가 설치마다 다름) · `visible`(비우면 true). 켜짐 칸은 생략 = 꺼짐. `menu` 가 없으면 메뉴를 바꾸지 않는다.
- **메뉴 교체**: `main` 그룹 행 잠금 → 관리자 저장과 같은 검사기(`MenuTreeValidator`) → 지금 노드(V43 시드 + 게시판 만들기가 자동 추가한 항목)를 지우고 새로 넣음 → `version` +1(저장한 사람 = 루트 운영자). 없는 게시판 항목은 빼고 INFO 로그. 검사에 걸리면(기동 검사가 막으므로 없어야 함) 설치는 그대로 성공하고 메뉴만 바꾸지 않는다(WARN).
- **소모임 분류**: 소모임 게시판 프리셋 순서대로 `groupCategoryName` 의 분류를 만든다(이름 키가 같은 분류가 있으면 그것에 잇는다, `sort_order` = 지금 마지막 + 10 단위, `updated_by` = 루트 운영자). 할당 순서는 분류 끝 다음. 게시판을 만들지 못했으면(같은 주소·이름이 이미 있으면) 분류도 만들지 않는다. 설치 중에는 관리자와 겨룰 일이 없어 admin 의 분류 잠금(`GroupCategoryLock`)은 잡지 않는다.
- **마이그레이션**: shared `V99__mypage_menu_unhide.sql` — mypage 의 10개 항목 숨김 풀기(MY_SCHEDULED·group-buy 는 그대로) · `MY_SHOP`(MEMBER)을 `MY_POINTS` 바로 다음(같은 부모의 뒤 형제 순서 +1, `MY_POINTS` 가 없으면 최상위 끝; 이미 있거나 노드 80개면 넣지 않음) · 바뀌었으면 mypage `version` +1. 노드 ID 는 V43 과 같은 Snowflake 모양(서버 번호 1023, 겹치면 +1).
- **계약과 다른 점**: ① 소모임 게시판 말머리의 표시 차례 — 게시판 만들기(`BoardCreator`)가 공통 말머리(질문·후기·잡담·공지[꺼짐])를 앞에, 고유 말머리를 뒤에 두므로 실제 차례는 질문·후기·잡담 → 가입인사·자료·주제별이다(표의 「가입인사 · 질문 · …」 차례가 아님). ② 「소모임 둘러보기」는 `GROUPS_ALL` 의 기본 이름(「전체 소모임」) 대신 항목 이름 `소모임 둘러보기`/`Browse clubs` 로 넣었다. ③ 폴더 영문명: 주제 `Topics` · 커뮤니티 `Community` · 소모임 `Clubs` · 게시판 `Boards`. 폴더는 모두 접기 없음.
