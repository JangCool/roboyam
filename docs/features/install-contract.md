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
1. **기본 게시판 구성(프리셋)**: 게시판·메뉴 표가 아직 없다. 그래서 `board-presets` 는 목록(프리셋 JSON 데이터 `install-presets/neutral.json`·`roboyam.json` 의 이름·설명·게시판 이름·말머리 이름)만 내려주고, `complete` 는 `boardPreset` 을 검증(모르는 값 `400`, 생략 시 `NEUTRAL`)해서 선택값만 `installation.board_preset` 에 기록한다(새 마이그레이션 `V6`). **게시판을 실제로 만드는 적용은 게시판 기능을 구현할 때 한다.** 슬로건 비워 두면 프리셋 `siteTagline` 을 쓰는 규칙은 지킨다.
2. **언어**: 프리셋 JSON 의 이름·설명·게시판·말머리 이름은 `name`/`description` 에 영문 `nameEn`/`descriptionEn` 을 짝으로 두고, `board-presets` 는 요청 언어(`Accept-Language`)가 `en` 이면 영문, 아니면 기본값을 같은 모양(문자열)으로 준다(설치 시점의 정적 데이터라 서버가 골라 준다). 설치 때 저장되는 사이트 슬로건(`siteTagline`)은 운영 데이터라 지금은 한국어 한 값이다(언어 정책은 미확정). 인증 메일 본문은 요청한 사람의 언어로 만든다.
3. 쓰지 않는 것: `installation.home_rules_seed`, 프리셋 `seedTexts`(홈·핫딜 시드 문구), `/api/system/info`.
3. 환경 변수 이름: `ROBOYAM_INSTALL_TOKEN`, `ROBOYAM_CRYPTO_KEY`, `NODE_ID`(Snowflake 서버 번호 0~1023, V2 의 ID 는 앱이 발급한다).
4. 로그인·로그아웃·내 정보 API 는 이번 범위가 아니다(루트 계정이 만들어져 나중에 로그인될 수 있으면 된다).

## 미확정 / 다음 작업
- docker·installer 템플릿에 `ROBOYAM_INSTALL_TOKEN`(설치기가 생성·출력)과 `NODE_ID` 를 다시 넣는다 — 백엔드 완료 후 메인에서 처리.
- 루트 계정의 `terms_version` 은 자리표시자 `'install'` 로 저장한다 — 약관 기능이 생길 때 정한다.
- 프리셋 적용(게시판 생성/삭제), 사이트 이름 기본값(`SAMPLE`), 화면의 `?mock=` 연동 교체(`// MOCK:` → `// REAL:`).
