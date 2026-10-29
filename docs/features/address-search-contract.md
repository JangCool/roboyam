# 주소 검색 계약 (**화면 확정 2026-10-30 · 서버·연결 2026-10-30**)

**의도**: 회원 화면의 「우편번호 찾기」를 다음(카카오) 우편번호에만 기대지 않게 한다. 다음은 무료 서비스라 보장 수준이 없고 도메인을 옮긴 전례가 있어, 관리자가 재배포 없이
**다음만 / 자체 주소 DB 만 / 다음 먼저(못 불러오면 자체 DB)** 를 고르고, 자체 DB 는 행정안전부 도로명주소 파일을 올려 만든다. pionworld2 `core.address`(develop `2825d1f`)를 옮겼다.

## 화면 (기준 코드)
- user-web 공용 주소 입력 `components/address/AddressInput.vue`(+ `AddressSearchPanel.vue`) — 시트 안에 다음 위젯을 끼우거나(팝업 아님) 자체 DB 검색 칸을 보인다. 늘 「직접 입력」이 있다.
  값 `{ postcode, address1, address2 }`(공구 배송지 `GroupBuyAddressFields` 와 같은 이름). 설정은 「우편번호 찾기」를 열 때마다 새로 받고(`useAddressSearch` — 관리자가 바꾸면 다음 열기부터 따른다), 못 받으면 앞서 받은 값, 없으면 다음 기본값으로 계속한다.
  공구 주문서·내 주문의 배송지(`GroupBuyAddressFields`)가 이 부품을 쓴다(받는 분·전화·메모만 공구 몫, 2b-1 의 `usePostcodeSearch` 는 지웠다).
  개발 확인 화면 `/dev/address`(개발 모드만, 운영은 404).
- admin-web `설정 › 시스템 › 주소 검색` = `/settings/address`(`pages/settings/address.vue`) — ① 제공처(다음만 · 자체 DB 만 · 다음 먼저 중 고르기) ② 주소 DB(상태 · 가져올 곳 「내 컴퓨터에서 올리기」/「서버 폴더의 파일」 · 진행).
  ③ 적재 이력은 제목 옆 「적재 이력」 단추가 여는 오른쪽 서랍(`SettingHistoryDrawer` — components.md 「설정 변경 이력은 서랍」, 처음 열 때 받는다). 메뉴는 로그인 보안 다음.

## 데이터 원천
- 행정안전부 도로명주소 개발자센터(business.juso.go.kr) › 주소 DB 의 **「도로명주소 한글 전체분」 zip(필수)** + **「영문 전체분」 zip(선택)**, 매달 새로 나온다. 풀지 않고 그대로 올린다.
  zip 안의 `rnaddrkor_*.txt`(도로명 24칸) · `jibun_rnaddrkor_*.txt`(관련지번 14칸) · `rneng_*.txt`(영문 17칸)만 읽고 나머지 항목은 건너뛴다. 헤더 없음 · `|` 구분 · CP949.
- 원본은 저장소에 두지 않는다(시험은 코드가 만든 작은 표본 zip — `AddressSamples`).

## 제공처와 다음 우편번호의 관계 (정본)
- **함께 쓴다.** 기본은 `DAUM`(다음만). `INTERNAL` 은 다음을 부르지 않는다. `DAUM_FALLBACK_INTERNAL` 은 다음 스크립트를 8초 안에 못 불러오면 자체 DB 검색 칸으로 바꾼다
  (다음 화면이 뜬 뒤의 결과 품질로는 바꾸지 않는다). `DAUM` 에서 스크립트를 못 불러오면 직접 입력을 연다.
- 자체 DB 를 쓰는 제공처인데 DB 가 비었으면 관리자 화면은 경고만 하고 막지 않는다 — 회원 검색은 503 `ADDRESS_DB_NOT_LOADED` 로 직접 입력을 권한다.
- 다음 스크립트 주소는 설정값(비우면 공식 `https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js`).

## 필드·검증
- 제공처 `DAUM | INTERNAL | DAUM_FALLBACK_INTERNAL` · 스크립트 주소 `https://` 로 시작, 공백 없음, 500자까지(빈 값 = 공식 주소) · 갱신 권고 일수 정수 7~365(기본 60).
  저장은 세 칸을 모두 보고 틀린 칸을 모아 400 `VALIDATION`(`fields.provider`·`scriptUrl`·`staleWarnDays`). 바뀐 칸만 감사.
- 「갱신 권고」 = 마지막 적재가 끝난 지 권고 일수보다 오래됐거나 올린 적이 없음.
- 올리기: 파일 1~2개, 각각 `.zip` · 빈 파일 아님 · 300MB(300×1024² 바이트) 이하, 글자 집합 `MS949 | UTF-8`(기본 MS949) — 400 `fields.files`·`fields.charset`.
- 서버 폴더: 배포 설정 `ROBOYAM_ADDRESS_IMPORT_DIR`(비우면 꺼짐 — 화면은 「가져올 곳」을 보이지 않는다). 폴더 **바로 아래**의 숨기지 않은 `.zip` 만 목록에 나오고(링크로 폴더 밖을 가리키는 것 제외),
  요청은 경로가 아니라 목록의 **이름** 1~2개를 보낸다(목록에 없으면 400 `fields.fileNames` — `../` 로 폴더 밖을 고를 수 없다). 크기 한도 없음, 빈 파일은 안 된다, 적재 뒤에도 지우지 않는다.
  도커: dev `compose.yml`·prod `compose.admin.yml` 의 admin 에 `ADDRESS_IMPORT_DIR`(호스트 폴더, 없으면 이름 붙은 볼륨)을 `/var/lib/roboyam/address-import` 에 읽기 전용으로 붙였다.
- 검색어: 앞뒤 공백을 지우고 80자까지, 괄호·쉼표는 띄어쓰기로 보고 낱말로 나눈다(같은 낱말 한 번, 6개까지). 띄어쓰기를 뺀 글자가 2자 미만이면 400 `fields.keyword`.
  **모든 낱말이 들어 있는 줄**(낱말마다 `search_text ILIKE '%낱말%'`, `% _ \` 는 글자 그대로). `search_text` = 도로명 주소 + 읍면동(+리) + 건물 이름 + 우편번호 + 관련지번 + 영문.
  차례는 파일 차례(`id` — 시·도, 도로 순). 한 쪽 1~30줄(기본 10), `total` 은 1000 까지만 센다. 질의가 3초를 넘으면 400 `fields.keyword`(「더 자세히」).

## 권한·정책
- 관리자: 조회 `site.read`(메뉴 `addressSearch`), 저장·올리기 `site.manage`. 새 권한 코드 없음(서버 `Permission` 그대로).
- 회원 API 는 **로그인 없이**(`SecurityConfig` permitAll — 비회원 공구 견적·가입에서도 쓴다). 검색은 **IP 하나 1분 60번**(이 서버 메모리, 넘으면 429 `ADDRESS_SEARCH_RATE_LIMITED` + `Retry-After`).
- 감사: `ADDRESS_SEARCH_SETTINGS_UPDATE`(대상 `ADDRESS_SEARCH/settings`, before/after) · `ADDRESS_DB_IMPORT`(대상 `ADDRESS_SEARCH/<적재 ID>`, 성공 files·entries·jibun·english / 실패 reason 키).

## API
| 서버 | 요청 | 응답 · 오류 |
|---|---|---|
| api | `GET /api/address/config` | `{provider, scriptUrl}` · `Cache-Control: no-cache` |
| api | `GET /api/address/search?keyword&page&size` | `{items[{postcode, roadAddress, jibunAddress, buildingName, roadAddressEn\|null}], total, page, size}` · 400 · 429 · 503 `ADDRESS_DB_NOT_LOADED` |
| admin | `GET`·`PUT /api/admin/address/settings` | `{provider, scriptUrl\|null, defaultScriptUrl, staleWarnDays, staleWarnDaysMin, staleWarnDaysMax, db{loadedAt, sourceLabel, entryCount, englishCount, stale}, updatedAt, updatedBy}` |
| admin | `POST /api/admin/address/imports`(multipart `files`, `charset`) | 202 `{jobId}` · 400 · 409 `ADDRESS_IMPORT_RUNNING` · 413 `FILE_TOO_LARGE` |
| admin | `GET /api/admin/address/imports/current` | 돌고 있는 작업 또는 `data` 없음 |
| admin | `GET /api/admin/address/imports/{jobId}` | `{jobId, phase, totalRows(-1 = 모름), processedRows, done, failed, failedPhase, message, fileNames, startedAt, startedBy}` · 404 `ADDRESS_IMPORT_JOB_NOT_FOUND` |
| admin | `GET /api/admin/address/imports/history?page&size` | `{items[{id(문자열), loadedAt, sourceLabel, entryCount, englishCount, loadedBy}], total, page, size}` — 끝난 적재만, 최신이 위 |
| admin | `GET /api/admin/address/server-files` | `{enabled, files[{name, size, modifiedAt}]}` — 경로는 내보내지 않는다 |
| admin | `POST /api/admin/address/imports/server` `{fileNames, charset}` | 202 `{jobId}` · 400 `fields.fileNames`·`fields.charset` · 409 `ADDRESS_IMPORT_RUNNING` |

## 적재 규칙
- 사이트에 **하나씩**(`address_db_loads` 부분 유일 색인 — 동시에 눌러도 하나만 `RUNNING`). 받은 관리자 서버가 가상 스레드에서 돌린다 — Java 배치(`BatchJob`)로 하지 않았다
  (올린 파일이 그 서버에만 있고 예약·재실행이 없는 한 번짜리 작업이라, 배치 요청 표가 파일 없는 서버로 보낼 수 있다).
- 한 트랜잭션: 스테이징 비우기·색인 지우기 → 줄 수 세기(COUNTING) → 도로명·지번·영문을 스테이징에 JDBC 배치로(PARSING) → 새 건수가 지금 DB 의 절반 아래면 멈춤 →
  관리번호로 지번(쉼표로 묶음)·영문을 20만 줄씩 붙이기(MERGING) → GIN 색인 만들기 · 이름 세 번 바꾸기로 맞바꾸기(SWAPPING, `lock_timeout` 15s) → `DONE` + 감사.
  어디서 멈추든 되돌려져 **지금 주소 DB 는 그대로**고, 실패는 따로 트랜잭션에서 `FAILED`(사유 키 · 멈춘 단계) + 감사.
- 멈추는 경우(사유 문구 키 `address.import.*`): 칸 수가 모자란 줄(`badLine` — 파일·줄), 고른 글자 집합으로 못 읽음(`badCharset` — 깨진 글자로 넣지 않는다), zip 이 아님(`badZip`),
  도로명 파일 없음(`noRoadFile`), 건수 급감(`dropped`), 서버 재시작(`interrupted` — 다시 뜬 서버가 자기 `RUNNING` 줄을 닫는다), 그 밖(`unknown`). 관리번호가 빈 줄만 건너뛴다.
- 진행 줄 수는 그 서버 메모리(1.5초마다 묻는 값), 단계는 적재 줄에도 남긴다.
- 업로드 한도: 관리자 앱의 컨테이너 multipart 한도를 파일 300MB · 요청 610MB 로 올리고, **주소 DB 올리기가 아닌 multipart 는 `AddressUploadLimitFilter` 가 11MB 에서 413**(예전과 같은 한도).
  화면은 이 요청만 30분을 기다린다(`useApiClient` 의 `timeoutMs`).

## pionworld2 에서 옮긴 것 · 바꾼 것
- 옮긴 것: 제공처 세 가지와 단일 설정 줄, 파일 레이아웃·표기 규칙(참고항목·법정리·지하/공중/수상·영문 순서), 스테이징 + RENAME 맞바꾸기, 관련지번 사전 집계·덩어리 UPDATE·붙이기 전 ANALYZE,
  건수 급감 멈춤, pg_trgm GIN 부분 일치, 비동기 적재 + 작업 번호 폴링, 300MB 한도.
- 바꾼 것: 마이그레이션 4개 → `V109` 하나(지번·검색 칸 처음부터 text, 영문 칸 포함, 지번·영문 스테이징 UNLOGGED) · 적재 이력 `address_db_meta` 대신 `address_db_loads`(진행 중·실패도 한 줄,
  서버 재시작 뒤에도 상태가 남음, 실패 사유는 문구 키라 언어마다) · 적재마다 스테이징 색인을 지우고 다 넣은 뒤 새로 만든다 · 칸이 모자란 줄·글자 집합이 틀린 파일은 건너뛰지 않고 멈춘다 ·
  검색은 낱말 AND · 우편번호도 찾는다 · `total` 1000 상한 · 3초 질의 한도 · IP 한도 · 단일 txt 올리기는 받지 않는다(zip 만) · 응답 이름은 우리 화면 모양(`postcode`·`items`·`total`).

## 서버 구현 결과
- 마이그레이션 `server/shared/.../db/migration/shared/V109__address_search.sql`(develop 마지막 V108 다음 — 합치기 직전에 다시 본다).
- shared `com.community.address` — 엔티티(설정·적재 줄) · `AddressEntryQueries`(JDBC 검색) · `AddressSearchPolicy`(한도·검사) · `AddressSearchService`.
- api `com.community.address` — `AddressController` · `AddressSearchRateLimiter`. admin `com.community.address` — `AddressSettingsController` · `AddressSettingsService` ·
  `AddressImportService`(시작·서버 폴더에서 시작·진행·이력·재시작 정리) · `AddressServerFiles`(서버 폴더 목록·이름 확인) · `AddressImportWorker`(적재) · `AddressFileReader`/`AddressFileParser` · `AddressUploadLimitFilter`. `AdminScreenRegistry` 한 줄.
- 시험(`--tests 'com.community.address.*'`, DB 없음 — Testcontainers 는 커머스 시험 전용): 검색어·설정 칸 규칙, 파서 표기 규칙, zip 읽기(종류 판정·덩어리·글자 집합·깨진 zip·도로명 없음),
  올리기 검사, 업로드 한도 필터, IP 한도, 서버 폴더(목록 거르기·폴더 밖 이름 거절·지우지 않음) — 39개 통과. 메뉴 차례 `AdminScreenRegistryLoginSecurityTest` 통과.
  적재·검색 SQL(V109 · 스테이징 → 붙이기 → 색인 → 맞바꾸기 · 낱말 AND · `%`/`_` 글자 그대로 · GIN 색인 사용 · 동시 적재 막힘)은 임시 postgres:18 컨테이너에서 손으로 돌려 확인했다(시험 코드로는 남기지 않음).

## 미확정
- 실제 「한글 전체분」(약 600만 줄)으로 걸리는 시간·디스크(스테이징 + 색인이 잠깐 두 벌)는 재지 않았다.

## 세션 정리 결정 (2026-10-30)
- 공구 배송지는 공용 주소 입력을 쓴다 — 우편번호 찾기의 정본은 이 계약(제공처 설정)이고, 공구 계약은 이곳을 가리킨다.
- 「지정 경로에서 적재」 = 서버 폴더(`ROBOYAM_ADDRESS_IMPORT_DIR`)의 zip 을 이름으로 고르기(위 「필드·검증」).
- 적재 이력은 설정 변경 이력과 같은 자리(제목 옆 서랍)에 둔다.
- 회원 화면은 우편번호 찾기를 열 때마다 설정을 받는다(바꾼 제공처가 바로 따른다).
- 관리자 서버가 여러 대면 진행 줄 수는 적재를 받은 서버에서만 보이고 다른 서버는 단계만 보인다 — 관리자 앱은 한 대로 운영하므로 그대로 둔다.
- `pg_trgm` 은 V1 이 이미 만든다(V109 는 확장을 만들지 않는다) — 설치형·dev·prod 에서 따로 할 일이 없다.
