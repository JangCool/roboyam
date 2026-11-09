# 캐시 관리 계약 (cache-admin)

> 상태(2026-11-09): 화면 목업 확정(2026-11-08) → **서버 구현 끝**(브랜치 claude/cache-server, 아래 「서버 구현 결과」). 화면 연결(`// MOCK:` → 실제 API)·메뉴 「(준비 중)」 제거·권한 카탈로그 `featureReady` 는 다음 단계(프론트).

## 의도
공개 사이트 응답을 서버 메모리 캐시(Spring Cache + Caffeine)로 감싸 DB 읽기를 줄인다. 운영자는 관리자 화면에서 캐시마다 TTL을 바꾸고, 갱신이 안 된 것 같으면 「지금 갱신」으로 모든 서버의 캐시를 비운다. 각 캐시가 공개 사이트 어디에 쓰이는지는 실제 공개 화면 미리보기 위에 표시한다.

## 화면
- 관리자 `/settings/caches`(설정 모드 › 배치·로그 묶음). 목업 API `apps/admin-web/app/composables/cache/api/useCacheAdminApi.ts`(`// MOCK:`), 응답 모양 `useCacheAdminApi.types.ts`가 API 계약 초안이다.
- 요약(서버 수, 확인 주기, 갱신 대기 수, 전체 갱신) · 거르기(분류 공개 화면/가젯/설정 사본/내부 규칙, 적용 서버, 검색) · 목록(TTL, 회원별 처리, 항목 수, 적중률, 마지막 갱신, 상태 정상/갱신 대기, 지금 갱신, TTL 변경, 이름 아래 「쓰이는 곳」 한 줄) · 캐시하지 않는 가젯 안내 상자.
- 상세 서랍 `?cache=<key>`: 서버별 상태(반영된 세대, 마지막 비움, 적중/누락), 갱신 기록(수동/전체/자동(저장)), TTL 변경 이력, 「쓰이는 곳」(화면 영역/동작/서버 내부 + 미리보기), 관련 관리 화면 링크, 가젯이면 「이 가젯 유형을 놓은 곳」.
- 미리보기 대화상자: 공개 사이트 iframe `{공개 origin}/?adminPreview=1&cachePreview=1`. 쪽 고르기·경로 직접 입력, 「모든 영역 보기」, 캐시 고르기, 고른 영역 강조(주황), 영역 클릭 → 캐시 선택. 글·상품 상세 쪽은 예시 경로 규칙 + 최신 예시 id(지금은 기존 관리자 API로 고름, 서버 단계에서 캐시 API의 `sampleId`로 대체). 캐시 미리보기에서는 사이트 팝업을 숨기고 `site.popups`를 골랐을 때만 띄운다.
- user-web: 영역 요소에 `data-cache-area="<키> [<키>…]"`(원천 `apps/user-web/app/utils/util-cache-area.ts`), 겹침 층 `components/shell/CacheAreaOverlay.vue`. 메시지 `roboyam:cache-preview`(관리자→) / `roboyam:cache-preview-pick`·`roboyam:cache-preview-areas`(→관리자). **캐시 키 이름 = `data-cache-area` 값**이어야 한다.

## 캐시 목록(25)과 기본값
| 분류 | 키 | 기본 TTL | 회원별 처리 | 서버 |
|---|---|---|---|---|
| 공개 화면 | `home.layout` · `pages.layout` · `boards.layout` | 300초 | 공용 | api |
| 공개 화면 | `home.latest` 60초 · `home.popular` 120초 · `menu.public` 300초 | | 공용 + 회원 덧입힘 | api |
| 가젯 | `gadget.POST_LIST` 60 · `HIGHLIGHT` 120 · `GALLERY` 60 · `BOARD_CARDS` 120 · `HOT_DEALS` 60 | | 공용 + 회원 덧입힘 | api |
| 가젯 | `gadget.GROUPS` 300 · `gadget.GROUP_BUY` 60 | | 공용 | api |
| 설정 사본 | `site.info` · `site.noticeBar` · `site.popups` · `terms.public` 20초, `notice.global` 15초 | | 공용 | api |
| 설정 사본 | `board.catalog` 15초 · `stats.collection` 30초 | | 공용 | api·admin |
| 내부 규칙 | `commerce.rateLimits` · `hotdeal.rules` · `hotdeal.settings` · `notification.templates` · `point.policy` 15초 | | 공용 | api·admin |

- 설정 사본·내부 규칙 12개는 지금 서버에 직접 만든 메모리 사본(`TTL_SECONDS`)으로 있다 — 구현 때 **모두 같은 틀로 옮긴다**(사용자 결정).
- 가젯 데이터 캐시의 항목 키 = 가젯 설정 지문. 같은 설정이면 홈·가젯 페이지·게시판 레이아웃이 한 항목을 함께 쓴다. `QUICK_LINKS`·`NOTICE_TEXT`는 배치 캐시에 포함, `AD`·`AD_TEXT_LINE`은 노출 집계·순환 때문에 캐시 안 함, `BOARD_BODY`는 해당 없음.
- 회원마다 다른 값(차단·메모 표시, 보는 사람 기준 메뉴 거름)은 캐시하지 않는다 — 공용 부분만 캐시하고 꺼낸 뒤 회원 기준으로 덧입힌다.
- 허용 범위(목업): 공개 화면·가젯 5초~1시간, 설정 사본 5초~10분, 내부 규칙 5초~5분.

## 정책
- **켜기/끄기**(사용자 결정): 전체 캐시 스위치 + 캐시별 스위치. 캐시별 스위치는 전체가 켜져 있을 때만 바꿀 수 있고(꺼져 있으면 잠김, 저장된 값은 유지했다가 전체를 켜면 그대로 적용), 실제 적용 = 전체 켜짐 AND 캐시 켜짐. 꺼지면 그 서버는 항목을 비우고 요청마다 원본(DB)을 읽는다. 끌 때만 확인 창(전체는 DB 부하 경고). 꺼진 캐시는 「지금 갱신」 잠김·전체 갱신에서 건너뜀, TTL은 바꿀 수 있음. 켜기/끄기도 DB 값이라 서버가 몇 초 안에 따라가고(반영 전 「갱신 대기」), 설정 변경 이력(TTL·캐시 켬/끔·전체 켬/끔)에 남는다.
- **TTL은 관리자 화면에서 바꾼다**(사용자 결정). 바꾼 값은 DB에 두고 각 서버가 몇 초 안에 따라 적용한다.
- 갱신(비우기): 관리자 서버가 DB의 캐시별 세대 번호를 올리고, api·admin 서버가 3초마다 확인해 비운다 → 「즉시」가 아니라 「몇 초 안에」. 아직 반영 안 한 서버가 있으면 「갱신 대기」.
- 관리자 화면에서 게시판·가젯·메뉴·설정 등을 저장하면 관련 캐시를 자동으로 비운다(기록 「자동(저장)」).
- 서버별 상태(반영 세대, 항목 수, 적중/누락)는 각 서버가 주기적으로 DB에 보고한다(관리자 서버는 api 메모리를 직접 못 본다).
- 권한: 지금은 최고 관리자 전용(준비 중). 구현 때 `cache.read`(보기)·`cache.manage`(TTL 변경·갱신)를 서버 `Permission`에 넣고 레지스트리를 그 권한으로 바꾼다(프론트 카탈로그에는 `featureReady: false`로 이미 있음). 갱신·TTL 변경은 감사 기록.

## API 후보
- `GET /api/admin/caches` — 서버 목록, 확인 주기, 캐시 목록(`CacheView`), 캐시 안 하는 가젯, 미리보기 쪽 후보, 공개 주소
- `GET /api/admin/caches/{key}` — 상세(서버별 상태, 갱신 기록, TTL 이력, 가젯 놓은 곳, 예시 `sampleId`), 없으면 404 `CACHE_NOT_FOUND`
- `POST /api/admin/caches/{key}/refresh` · `POST /api/admin/caches/refresh` — 202
- `PUT /api/admin/caches/{key}/ttl` `{ttlSeconds}` — 범위 밖 400 `VALIDATION fields.ttlSeconds`
- `PUT /api/admin/caches/settings` `{enabled}` — 전체 스위치 → 개요. `PUT /api/admin/caches/{key}/enabled` `{enabled}` → `CacheView`, 전체가 꺼져 있으면 409 `CACHE_GLOBAL_DISABLED`. 꺼진 캐시 갱신은 409 `CACHE_DISABLED`
- 응답 칸: 개요 `globalEnabled`, `CacheView.enabled`·`effectiveEnabled`, 서버별 `appliedEnabled`, 상세 `settingChanges[]`(kind TTL|ENABLED|GLOBAL_ENABLED)
- 「쓰이는 곳」(`usages[]`: kind AREA|ACTION|SERVER, label, description, path|예시 경로 규칙, loginRequired)·`adminLinks[]`는 캐시 정의(코드)에 적는 고정 정보. 가젯의 화면 영역 줄은 배치에서 센다.

## 구현 메모(나중)
- 코드는 Spring Cache 추상(`@Cacheable`·`CacheManager`)만 쓴다. 런타임 TTL 변경은 Caffeine `policy().expireAfterWrite()`로 하되, 이 부분은 「TTL 적용기」 하나에 가둔다(원격 전환 대비, 아래 참고).
- DB: 캐시 설정(TTL)·세대 번호·서버 보고·갱신 기록·TTL 이력 표 → shared 마이그레이션(번호는 병합 직전 develop에서 다음 번호 확인, 2026-11-08 기준 V175~).
- 서버 메뉴 레지스트리는 지금 `superOnly`로 넣어 두었다. 이미 관리자 메뉴 구성을 저장한 DB에서는 「메뉴에 없는 화면」에 나오므로 메뉴 관리 › 관리자 메뉴에서 넣어야 보인다.

## 미확정 → 세션 정리 결정(2026-11-09, 목업 값으로 정함)
1. 줄마다 「지금 갱신」에는 확인 창이 없다(전체 갱신·끄기에만 — 화면 몫).
2. 세대 번호는 캐시별(`cache_settings.generation`).
3. TTL 을 바꿔도 캐시를 비우지 않는다(Caffeine `expireAfterWrite` 를 바꾸면 담긴 항목도 넣은 시각 기준으로 새 TTL 을 따른다).
4. 허용 범위: 공개 화면·가젯 5초~1시간, 설정 사본 5초~10분, 내부 규칙 5초~5분(서버 `CacheKind`).
5. `cache.manage` 는 MANAGE 단계(AUDIT_BATCH).
6. 미리보기 쪽 후보(`previewPages`)와 예시 번호(`sample.sampleId`)는 서버가 준다.

## 서버 구현 결과(2026-11-09)
- **틀**: Spring Cache + Caffeine(`spring-boot-starter-cache`·`caffeine`, 버전은 Boot BOM). 업무 코드는 `CacheManager`·`Cache` 만 쓴다. TTL 적용·켜기/끄기·비우기·통계는 `zconfiguration.cache.CacheControl` 하나에 가두고 구현은 `CaffeineCacheControl`(같은 객체가 `CacheManager`) — 원격 전환 때 이 구현만 바꾼다. 꺼진 캐시·이 서버가 두지 않는 캐시는 `NoOpCache`(원본을 읽는다). 캐시 정의 25개는 코드 `CacheCatalog`(이름·대상·쓰이는 곳·관련 화면은 번들 `cache.def.*`·`cache.link.*`).
- **DB(shared V175)**: `cache_settings`(캐시별 ttl NULL=기본·enabled·generation·changed_at — 25행 미리 넣음) · `cache_global_settings`(1행) · `cache_node_states`(서버×캐시 보고, PK node_id+cache_key) · `cache_refresh_logs`(MANUAL·AUTO_SAVE, source_code·source_detail·operator_id·generation) · `cache_setting_changes`(TTL·ENABLED·GLOBAL_ENABLED, 전체는 cache_key NULL).
- **동기화**(`CacheSync`, api·admin 모두): 3초마다 질의 2개(설정 전부 · 전체 스위치)로 세대(바뀌면 비움)·TTL·실제 켜짐(전체 AND 캐시, 끄면 비움)을 따라간다. 10초마다·바뀐 것을 반영한 직후 서버 상태를 `ON CONFLICT` 한 묶음으로 보고. 노드 ID = `roboyam.cache.node-name` 또는 `<api|admin>-<NODE_ID>`. 화면은 60초 넘게 보고 없는 서버를 빼고, 1시간 넘은 줄은 지운다. 띄운 직후 한 번 바로 맞춘다.
- **갱신**(`CacheRefresher`): 행 쓰기 잠금(키 차례)으로 세대 +1 + 기록, 부르는 쪽 트랜잭션에 참여, 커밋 뒤 이 서버는 바로 비운다. 자동(저장)은 커밋 직전에 한꺼번에(캐시 행 잠금이 늘 업무 잠금 뒤·키 차례), 한 트랜잭션에서 같은 캐시는 한 번만.
- **자동(저장)**: 관리자 저장의 감사 기록(`AuditLogService#record` → `AuditRecorded` 이벤트, 같은 트랜잭션)을 `CacheAutoRefresh`(admin)가 작업 이름 → 계기(`CacheSaveSource`)로 바꿔 올린다(홈·가젯 페이지·게시판 레이아웃·테마·메뉴·사이트 기본·운영 주체·공지 띠·팝업·약관·공지·게시판·게시판 기본·등급·하이라이트·인기글·글 숨김/삭제 등). 설정 사본·내부 규칙(포인트 정책·요청 한도·통계 수집·핫딜 규칙/설정·알림 문구)은 예전 `invalidate()` 자리에서 서비스가 직접 올린다. 회원 새 글은 TTL.
- **사본 12개 이전**: `TTL_SECONDS`·직접 만든 메모리 사본을 지우고 `Cache.get(key, loader)` 로(기본 TTL 은 예전 값). `invalidate()` 는 「이 서버만 비우기」로 남겼다(시험용, 운영 저장은 세대 올리기).
  - 달라진 점: 예전의 「다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시」는 표준 Spring Cache 로는 같은 결과를 낼 수 없어 없앴다 — 이제 읽기에 실패하면 담지 않고 그 요청이 실패하며 다음 요청이 바로 다시 읽는다. 「처음부터 못 읽으면 안전한 값」이던 통계 수집(꺼짐)·전체 공지(빈 목록)는 실패할 때마다 그 값을 돌려준다(담지 않음). 로더 예외는 `Cache.ValueRetrievalException` 에 싸여 나온다.
- **공개 캐시**: 공용 값만 담고 꺼낸 뒤 보는 사람 몫을 덧입힌다 — 글 줄은 작성자 메모·차단·딜의 내 종료 요청(`PostFeedRows#overlay`, 질의 수는 예전과 같거나 적다), 소모임 가젯은 내 상태, 메뉴는 보는 사람 판정. 항목 키: `home.layout`·`boards.layout`=테마, `pages.layout`=주소|테마(없는 페이지도 담음), `home.latest`=쪽 크기|성인 여부(**첫 쪽만**, 커서 쪽은 캐시 안 함), `home.popular`=기간|쪽|크기, `menu.public`=그룹 코드, `gadget.*`=SHA-256(성인 여부|쪽에 따른 대상 게시판|가젯 설정 JSON).
  - 캐시하지 않는 것: 관리자 테마 미리보기(`theme` 파라미터 — 배치·가젯 모두 우회), 광고 2종·본문 칸, 메뉴의 공동구매 판매 스위치와 공동구매 가젯의 `salesOpen`(돈 기능 — 요청마다 지금 값), 성인 여부 판정·메뉴 보는 사람 판정(회원별).
  - 달라진 점: 소모임 가젯(「지금 뜨는 소모임」 무작위 고르기)이 TTL(300초) 동안 같은 고르기를 보인다 → 회원별 처리를 `SHARED_WITH_MEMBER_OVERLAY` 로 둠(내 상태). 갤러리 가젯은 줄에 회원별 칸이 없어 `SHARED`.
- **관리자 API**(server/admin `CacheAdminController`, `Cache-Control: no-store`): 위 「API 후보」 그대로. 오류 `CACHE_NOT_FOUND`(404) · `CACHE_DISABLED`·`CACHE_GLOBAL_DISABLED`(409) · `VALIDATION fields.ttlSeconds`(400). 감사 `CACHE_REFRESH`·`CACHE_REFRESH_ALL`·`CACHE_TTL_CHANGE`·`CACHE_ENABLED_CHANGE`·`CACHE_GLOBAL_ENABLED_CHANGE`(대상 `CACHE`). 권한 `Permission.CACHE_READ`·`CACHE_MANAGE`(AUDIT_BATCH, READ·MANAGE, featureReady true), 레지스트리 `caches` = `cache.read`.
- **남은 것**: 갱신 기록·설정 변경 이력 정리 배치 없음(쌓이기만 한다) · 소모임·공동구매 관리자 저장은 자동 비우기에 안 넣음(TTL) · 회원 공개 사이트의 스텝 숨김(api)은 TTL.

## 원격 캐시 전환 시 고려할 점 (Hazelcast · MongoDB · Redis)
지금은 사이트 규모가 작아 로컬(Caffeine) + DB 세대 번호 폴링으로 간다. 사이트가 커지면 원격 캐시로 바꿀 예정이고, 그때 아래를 확인한다.

1. **추상화를 지금부터 지킨다** — 업무 코드에서 Caffeine API를 직접 부르지 않고 Spring Cache 추상만 쓴다. 그러면 `CacheManager` 교체로 끝난다. TTL 런타임 변경·통계 수집·비우기는 구현마다 달라서 「캐시 운영」 인터페이스(TTL 적용, 통계 읽기, 비우기) 하나로 감싸 두고 구현만 바꾼다.
2. **값 직렬화** — 원격은 값을 직렬화해 네트워크로 보낸다. 캐시 값은 불변 DTO(record)만 담고 JPA 엔티티·지연 로딩 프록시·`Optional` 같은 것은 넣지 않는다. 직렬화는 Jackson(JSON) 등 클래스 변경에 덜 깨지는 방식으로, 키에 값 형식 버전을 넣어 배포 중 옛 형식 역직렬화 실패를 피한다(롤링 배포 때 새·옛 서버가 섞인다).
3. **지연과 장애** — 로컬은 나노초, 원격은 밀리초다. 자주 읽는 작은 값(설정 사본·내부 규칙)은 2단(L1 Caffeine near cache + L2 원격)을 검토한다. 원격이 죽으면 DB로 바로 읽는 쪽으로 떨어지고(fail-open) 타임아웃은 짧게, 만료 순간 몰림(스탬피드)은 `sync=true`나 잠금으로 막는다.
4. **무효화 방식이 바뀐다** — 원격 하나에 모이면 한 번 비우면 모든 서버에 반영된다. 세대 번호 폴링은 L1(near cache) 무효화용으로만 남기거나 저장소 기능으로 바꾼다: Redis Pub/Sub, Hazelcast near cache 무효화·Topic, MongoDB Change Streams. 관리자 화면의 「서버별 반영 세대」 칸은 L1을 쓸 때만 의미가 있으니 그때 다시 정한다.
5. **비우기 비용** — Redis에서 `KEYS`로 접두어 삭제는 금지(전체 막힘). `SCAN`+`UNLINK`도 느리다. 키에 세대 번호를 넣어(`캐시이름:형식버전:세대:인자지문`) 세대만 올리는 「논리적 비우기」가 가장 싸다 — 옛 키는 TTL로 저절로 사라진다. 지금의 세대 번호 설계를 그대로 키에 쓰면 된다.
6. **TTL 차이** — Redis: 키마다 TTL, 캐시별 설정 가능(관리자 변경은 새로 넣는 항목부터). Hazelcast: 맵 설정 time-to-live, 실행 중 변경은 동적 설정 제약이 있어 항목마다 TTL을 주며 넣는 방식이 안전. MongoDB: TTL 인덱스 삭제가 약 60초 주기라 짧은 TTL(15~60초)이 부정확 → 만료 시각 칸을 읽을 때 직접 확인해야 한다.
7. **통계 칸이 바뀐다** — 항목 수·적중률은 지금 서버별 Caffeine `recordStats` 값이다. 원격은 저장소 전체 값이고 캐시(접두어)별 항목 수를 세는 것은 비싸다(Redis는 `SCAN` 필요). 적중/누락은 앱에서 계측(Micrometer)으로 세고, 항목 수 칸은 빼거나 근사값으로 바꾼다.
8. **회원별 값은 계속 캐시하지 않는다** — 공용 + 덧입힘 구조는 원격에서도 그대로. 원격에 회원별 값을 넣으면 키 수·메모리가 폭증하고 개인정보가 저장소에 남는다.
9. **운영** — 메모리 상한과 축출 정책(Redis `allkeys-lru` 등), 인증·TLS, 백업 불필요(캐시는 지워져도 되는 데이터) 명시, 모니터링(메모리·지연·연결 수). 세션·요청 한도 카운터 같은 다른 용도와 같은 저장소를 쓰면 키 접두어·DB 번호로 나누고 축출 정책이 서로 해치지 않게 한다.
10. **시험** — 캐시 구현을 바꿔도 같은 동작(만료, 비우기, TTL 변경, 장애 시 DB로 떨어짐)인지 같은 시험을 Testcontainers(Redis/Hazelcast)로 돌린다.

선택 메모(비용을 걱정하는 1인 운영 기준, 앱 서버 2대 이상 전제):
| 후보 | 장점 | 단점 |
|---|---|---|
| Redis | 가장 흔한 표준, 자료·도구 많음, Spring 지원 성숙, 앱 서버나 DB 서버에 직접 설치하면 추가 비용 작음 | 별도 프로세스 운영, ElastiCache는 비용 |
| Hazelcast(내장) | 앱 안에 묶여 별도 서버 없음, near cache 내장 | 앱 메모리를 나눠 씀, 서버끼리 찾기(AWS discovery) 설정, 앱 재시작 때 데이터 이동 |
| MongoDB | 이미 Mongo를 운영 중이면 재사용 | 캐시 용도로는 TTL 정밀도·지연이 나쁨 — **비권장** |

→ 일반적으로 Redis, 별도 서버를 두기 싫으면 Hazelcast 내장. MongoDB는 캐시 전용으로는 고르지 않는다.
