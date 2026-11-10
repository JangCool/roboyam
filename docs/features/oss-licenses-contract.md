# 오픈소스 라이선스 고지 계약 (사용자 확정 2026-11-10)

## 의도
MIT·Apache·BSD 등 무료 오픈소스도 저작권 고지와 라이선스 전문을 함께 밝히는 것이 조건이다. 사이트에 실제로 들어가는 오픈소스(공개 화면·관리자 화면 번들, 서버 런타임 의존성)의 라이선스를 빌드 때 모아 공개 화면에 보이고, 자동으로 못 잡는 것은 관리자가 직접 더한다.

## 화면
- **공개** `/licenses`(user-web `pages/licenses.vue`, 비회원도) — 푸터 「약관·정책」 링크 묶음 맨 끝 「오픈소스 라이선스」(같은 탭, 개인 3열·사업자·모바일 모양 모두).
  약관 화면과 같은 흰 카드 + `PageTitleHead`(설명 = 안내 문구, 없으면 기본 문구 「{사이트}은 아래 오픈소스 소프트웨어를 사용합니다. …」 — `{사이트}` 와 뒤 조사는 `UtilFooterNotice.render`).
  거르기 칩(전체 N · 공개 화면 · 관리자 화면 · 서버 · 글꼴·아이콘) + 이름 찾기(이름·라이선스) + 라이선스 종류별 개수 한 줄(많은 차례 8개 + 그 밖).
  목록: 이름 · 버전 · 쓰는 곳 · 라이선스 칩, 줄을 누르면 펼침(`aria-expanded`) — 쓰는 곳·라이선스 · 저작권 고지 · NOTICE · 라이선스 전문(고정폭, 줄바꿈 그대로, 24줄 넘으면 접힌 높이 + [전문 펼치기]) · 원본 저장소·라이선스 안내(새 탭, `rel=noopener`).
  맨 아래 「마지막으로 모은 날」. 수백 줄 대비: 펼친 줄만 전문을 그리고 줄마다 `content-visibility: auto`.
  정적 파일을 못 받으면 오류 화면(다시 시도), 서버 API 만 못 받으면 기본 문구 + 자동 목록만.
- **관리자** `/settings/oss-licenses`(설정 > 운영·약관, 약관 다음 — 키 `ossLicenses`, 아이콘 `pi pi-code`) — ① 안내 문구(여러 줄, 비우면 기본 문구, 구획 안 저장 줄) ② 직접 추가 항목 표(추가·고치기 대화상자 · 공개 화면에 보이기 스위치 · 지우기, 이름순) ③ 자동 수집 목록(읽기 전용 — 개수·마지막으로 모은 날·찾기, 20줄씩).

## 필드·검증(서버 `OssNotices` = V181 제약, 모아서 400 `VALIDATION`)
| 칸 | 규칙 | 오류 키 |
|---|---|---|
| 안내 문구 `intro` | 여러 줄, 1000자, 비우면 null | `fields.intro` |
| 이름 `name` | 필수, 100자 | `fields.name` |
| 버전 `version` | 선택, 50자 | `fields.version` |
| 라이선스 `license` | 필수(SPDX 또는 자유 글), 100자 | `fields.license` |
| 저작권 고지 `copyright` | 선택, 여러 줄 2000자 | `fields.copyright` |
| 라이선스 전문 `licenseText` | 선택, 100000자, 줄바꿈 유지 | `fields.licenseText` |
| 원본 링크 `sourceUrl` | 선택, `http(s)://` · 공백 없음 · 500자 | `fields.sourceUrl` |
| 묶음 `category` | `USER`·`ADMIN`·`SERVER`·`FONT_ICON`·`OTHER` | `fields.category` |
| 숨김 `hidden` | 만들 때 비우면 보임, 고칠 때 비우면 지금 값 | `fields.hidden`(숨기기 API) |
글자 수는 코드 포인트(DB `char_length`). 한 줄 칸은 앞뒤 공백, 여러 줄 칸은 줄 끝 공백·앞뒤 빈 줄을 뗀다.

## 권한·정책
- 보기 `site.read`, 바꾸기 `site.manage`(운영 주체·팝업과 같은 계열 — 약관 전용 `terms.*` 는 쓰지 않았다).
- 감사(일반 운영 1년, 대상 `OSS_NOTICE`): `OSS_NOTICE_INTRO_UPDATE`(from/to, 바뀐 때만) · `OSS_NOTICE_ENTRY_CREATE`(after) · `OSS_NOTICE_ENTRY_UPDATE`(바뀐 칸 before/after) · `OSS_NOTICE_ENTRY_HIDDEN`(from/to) · `OSS_NOTICE_ENTRY_DELETE`(before). 전문은 길어 글자 수·지문만 싣는다.
- 공개 사본: 캐시 `oss.notice`(설정 사본, 기본 60초, api) — 위 감사 기록을 보고 `CacheAutoRefresh`(계기 `OSS_NOTICE_SAVE`)가 같은 트랜잭션에서 세대를 올린다. 응답 `Cache-Control: no-cache` + `ETag`.

## API
- 공개(api): `GET /api/site/oss-licenses` → `{ intro: string|null, entries: [{ id, name, version, license, copyright, licenseText, sourceUrl, category }] }`(숨기지 않은 것, 이름순 — 대소문자 무시).
- 관리자(admin, 모두 `no-store`):
  - `GET /api/admin/oss-licenses` → `{ intro, introUpdatedAt, entries: [{ id, name, version, license, copyright, licenseText, sourceUrl, category, hidden, updatedAt }] }`
  - `PUT /intro { intro }` → 같은 모양 · `POST /entries` → 201 한 줄 · `PUT /entries/{id}` → 200 · `PUT /entries/{id}/hidden { hidden }` → 200 · `DELETE /entries/{id}` → 204
  - 404 `OSS_NOTICE_ENTRY_NOT_FOUND`
- 자동 수집 목록은 API 가 아니라 각 앱의 정적 파일 `/oss-licenses.json`(두 앱 `public/` 에 같은 내용 — 아바타·이모티콘 그림처럼 두 벌).
- DB(shared **V181**): `oss_notice_settings`(한 줄 id=1, `intro`) · `oss_notice_entries`.

## 자동 수집 방법
```
JAVA_HOME=<JDK 25> node scripts/collect-licenses.mjs            # 두 화면 빌드(nuxt generate) + 서버 의존성 → 두 앱 public/oss-licenses.json
JAVA_HOME=<JDK 25> node scripts/collect-licenses.mjs --skip-build  # 지난 번들 목록(node_modules/.cache/roboyam-oss/*.json) 재사용
```
- **화면**: 환경 변수 `ROBOYAM_OSS_SCAN` 이 있을 때만 두 앱 `nuxt.config.ts` 가 `scripts/oss-licenses/nuxt-bundle-scan.mjs` 모듈을 더하고, 클라이언트 빌드가 읽은 모듈 경로 중 `node_modules/<패키지>` 만 적는다 — devDependencies·빌드 도구·워크스페이스 패키지(`@roboyam/*`)는 저절로 빠지고 그 패키지가 끌어온 바깥 의존성은 잡힌다. 번들 밖에서 실리는 것은 스크립트의 `EXTRA_USAGE`(Tabler 아이콘 그림 → 공개 화면, Tailwind preflight CSS → 두 화면).
  package.json 의 `license`(「SEE LICENSE IN 파일」이면 그 파일 제목)·`repository`, 폴더의 LICENSE·COPYING·NOTICE 파일. 라이선스 파일이 없으면 SPDX 표준 문구(`scripts/oss-licenses/license-texts.mjs`, Apache-2.0 은 모은 전문 하나를 빌림)로 대신하고 `textSource: template`.
- **서버**: Gradle 내장 `:server:api:dependencies`·`:server:admin:dependencies --configuration runtimeClasspath`(shared 포함)의 좌표 → Gradle 캐시의 POM(라이선스 이름 → SPDX, 없으면 부모 POM)·jar 안 `META-INF/LICENSE`·`NOTICE`. 플러그인·새 의존성 없음.
- 글꼴·아이콘 묶음: `primeicons`·`@primeicons/*`·`@tabler/icons`. 싣는 글꼴 파일은 없다(시스템 글꼴만 — `main.css` 의 font-family). PrimeIcons 글꼴은 primeicons 패키지 안이다.
- 같은 전문은 `texts` 에 한 번만 싣는다. 항목 한 줄씩 써서 커밋 차이가 읽힌다.
- **조건이 까다로운 라이선스**(GPL·LGPL·EPL·MPL·CDDL 등)·사용자 정의 라이선스·흔한 허용형이 아닌 것·모르는 것은 끝에 경고로 따로 출력한다(빼거나 바꾸지 않는다).
- **언제 다시 돌리나**: 화면·서버 의존성을 바꾼 커밋에서 함께 돌려 JSON 을 커밋한다. 배포 빌드에 넣을 자리는 `installer/make-package.sh` 머리의 「먼저 pnpm --filter user-web generate → bootJar」 앞(수집이 generate 를 함께 한다) — 아직 넣지 않았다.

## 첫 수집 결과(2026-11-10, develop 7f96682e 기준)
- 262개 — 공개 화면 107 · 관리자 화면 108 · 서버 153 · 글꼴·아이콘 4. 전문 89종(418 KB), 표준 문구로 대신 18, 전문 없음 4(logback 2 · aspectjweaver · reactive-streams — 안내 주소만).
- 분포: Apache-2.0 128 · MIT 97 · PrimeUI License 13 · BSD-3-Clause 9 · EPL 이 낀 것 8(이중 라이선스 7 + EPL-2.0 단독 1) · Broadcom Foundation License 2 · 그 밖 5(MIT OR Apache-2.0 · 0BSD · CC0-1.0 OR BSD-2-Clause · BSD-2-Clause · MIT-0).

## 미확정
1. **PrimeVue 5 · PrimeIcons 8 · @primeuix/* · @primeui/license-manager(13개)는 MIT 가 아니라 상용 「PrimeUI License」**(커뮤니티 무료 조건: 연매출 100만 달러·개발자 5명·직원 10명·투자 300만 달러 미만, 라이선스 키 필요)다 — 고지 문구가 아니라 사용 조건 문제라 사업자 단계 전환 때 법무 확인이 필요하다.
2. EPL·GPL·LGPL 이 낀 서버 의존성 8개(logback 2 · jakarta.annotation/mail/persistence/transaction · angus-mail · aspectjweaver)는 대부분 허용형(EPL-2.0·BSD-3-Clause·Classpath 예외)을 고를 수 있는 이중 라이선스다. 수정 없이 jar 로 싣는 지금은 고지로 충분하다는 가정 — 법무 확인.
3. spring-session 4.1.1 의 POM 은 「Broadcom Foundation License」라 적지만 jar 안 전문은 Apache-2.0 이다 — POM 이름 그대로 보인다.
4. 서버 런타임 바깥(JDK·PostgreSQL·pg_cron·Docker 이미지 등 설치기가 까는 것)은 넣지 않았다 — 필요하면 직접 추가 항목으로.
5. 배포 빌드에서 자동으로 다시 모을지(설치 패키지 스크립트·CI) — 지금은 손으로 돌려 커밋.

## 고지 범위 줄이기 · 푸터 묶음 (2026-11-10)
- 고지는 배포하는 코드만 — 브라우저로 내려가는 화면 번들 패키지만 싣는다. 서버 라이브러리는 이용자에게 배포되지 않아 기본으로 싣지 않는다(설치형으로 서버 프로그램을 나눠 줄 때만 `collect-licenses.mjs --with-server`).
- 같은 패키지의 여러 버전은 한 줄로 합치고(버전 쉼표), 고지를 요구하지 않는 라이선스(0BSD·MIT-0·CC0-1.0·Unlicense)는 뺀다. 262개 → 104개.
- 공개 `/licenses` 거르기 칩은 항목이 없는 묶음을 숨긴다(「서버」는 직접 추가 항목이 있을 때만).
- 푸터: 오픈소스 라이선스를 약관 묶음에서 빼 「오픈소스」 묶음(데스크톱 개인 모양 4열). 묶음 제목은 진하게, 개인정보처리방침은 굵기 대신 주 색상 글자(시안 B).
