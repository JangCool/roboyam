# 광고 성과 계약 (화면 확정 2026-11-02)

**의도**: 공개 노출 API 가 쌓는 일별 노출·클릭(`ad_daily_stats`)을 광고주(사업자)와 운영자가 본다. 사업자는 자기 건의 성과를, 운영자는 기간·유형·광고주별 합계와 건별 순위를 본다. 돈은 움직이지 않는다(읽기 전용).
**정본·근거**: 집계 규칙은 `ad-contract.md` 「공개 노출 서버 구현 결정」(같은 방문자·소재 버전·자리 KST 하루 1번, 봇 제외, 20초 사본), 정지·내림은 `biz-pause-contract.md`(정지 중 노출 0), 핫딜 노출 자리는 `biz-hotdeal-contract.md`. 목업 설계 노트(세션 공유 폴더 `ad-performance-mockup-spec.md`).
**사용자 결정(2026-11-02)**: 차트는 **ECharts 「노출 막대(왼쪽 축) + 클릭 선(오른쪽 축)」** 로 두 앱 통일(공유 레이어 `@roboyam/chart`, `echarts` 6.1.0) · 사업자 센터 「진행 중 광고」 카드에 **내려진 건(긴급 내림·사업자 정지·서비스 정지 — 까닭 `EMERGENCY`·`SUSPENDED`)도 「내려짐」 표시로 넣는다**(광고주 중단 `ADVERTISER_STOP`·끝난 건은 뺀다).

## 화면 경로
- user-web `/business/applications/{id}` 「성과」 칸(`#performance` — 합계 노출·클릭·클릭률 · 날짜별 차트(정지 날 주황·내림 날 빨강 띠, 오늘은 흐리게 「집계 중」) · 자리별 · 소재 버전별 · 멈춘 구간). 보이는 건 = 노출 자리가 있고(배너·한 줄·노출 자리 있는 핫딜 글·노출만 추가) 결제 뒤 시작한 건. 시작 전은 안내 한 줄, 시작 전 취소·결제 전·노출 없는 핫딜 글은 칸 없음.
- user-web `/business` 「진행 중 광고」 카드(최대 5 — 제목·상태 Tag·최근 7일 노출·클릭·클릭률·미니 막대, 누르면 상세 `#performance`). 없으면 카드 없음.
- admin-web `/ads/performance` 「광고 성과」(메뉴 「광고」 넷째 — 광고 일정표 다음·광고 기본 설정 앞, `pi pi-chart-bar`): 거르기 기간(기본 최근 7일·오늘 포함)·유형·광고주(주소 쿼리 `from`·`to`·`type`·`advertiserId`·`sort`·`dir`) · 합계 띠(노출·클릭·클릭률·노출된 건 수) · 날짜별 추이 · 유형별·자리별 표 · 광고주 순위 10(누르면 그 광고주로 거름) · 건별 표(정렬 노출·클릭·클릭률, 쪽) → 줄 = `/ads?type=&id=` 일정표 서랍.
- admin-web 일정표 서랍 `AdBookingDrawer` 「성과」 탭(일정 다음 — 한 건 성과, 탭을 처음 열 때 한 번 받음).
- 화면 문구: 「같은 방문자는 하루 1번만 세요」 안내 · 「{시각} 기준 · 오늘은 집계 중」. 클릭률 = 클릭 ÷ 노출(화면 계산, 노출 0 이면 「—」, 소수 한 자리 %). 클릭은 그날 노출 여부를 보지 않아 드물게 100% 를 넘을 수 있다 — 그대로 보인다.

## 규칙
1. **원천**: `ad_daily_stats`(건·버전·KST 날짜·자리) 합. 새 표·마이그레이션 없음(읽기만). 오늘 = KST 오늘, 「오늘」 숫자는 계속 는다.
2. **한 건 기간**: 요청 `from`·`to`(KST 날짜, 끝 포함)는 건의 기간 날짜(시작 날짜 ~ 끝 날짜 — `[startAt, endAt)` 의 KST 날짜) ∩ 오늘까지로 자른다. 빠지면 시작 날짜 ~ min(끝 날짜, 오늘). 형식이 틀리거나 `from > to` 면 400 `fields.from`. 시작 전(오늘 < 시작 날짜)이면 `days` 빈 목록·합계 0(`range` = 시작 날짜 하루). `days` 는 범위 안 모든 날짜(0 포함).
3. **날짜 상태**: 그날(KST) 일부라도 광고주 일시 정지 구간이 걸치면 `PAUSED`, 그 밖의 내림(`EMERGENCY`·`SUSPENDED`·`ADVERTISER_STOP`) 구간이 걸치면 `TAKEN_DOWN`(둘 다면 `TAKEN_DOWN`), 아니면 `SHOWING`.
4. **멈춘 구간 `downtimes`**: 처리 기록(`biz_application_events`)의 내림·정지·중단 시작(`TAKEN_DOWN`·`PAUSED`·`STOPPED`)과 끝(`RESTORED`·`RESUMED`·취소)을 짝지어 `{from, to|null, cause}` — 지금 내려져 있으면 `to = null`. 범위와 겹치는 것만, 오래된 차례. 짝이 안 맞는 기록은 상태 칸(`takedown_at`·`takedown_cause`·`paused_at`)으로 보정.
5. **소재 버전 `byVersion`**: 승인된 버전 전부(0 포함, 버전 차례), `approvedAt`, `current` = 최신 승인 버전. 집계 줄이 있는데 승인 기록이 없는 버전도 줄로 낸다(`approvedAt` null).
6. **자리 `byPlacement`**: 0 인 자리는 빼고 노출 많은 차례. 자리 값 = V118 CHECK 아홉(`HOME`·`PAGE`·`BOARD`·`POST_INLINE`·`BOARD_LIST`·`WING_LEFT`·`WING_RIGHT`·`HOTDEAL_TOP`·`HOTDEAL_CATEGORY`).
7. **사업자 범위**: 본인(로그인 회원의 사업자 신원) 건만 — 남의 건·없는 건은 기존 신청 건 상세와 같은 404. 노출 자리 줄이 없는 건(노출 없는 핫딜 글)도 404 가 아니라 빈 결과(화면이 칸을 그리지 않음). 운영자 등록 건(`user_id` 없음)은 사업자 화면에 나타나지 않는다(기존).
8. **사업자 센터 요약**: 본인 건 중 노출 자리 줄이 있고 끝 시각 > 지금이며 시작 ≤ 지금인 건 — `SCHEDULED`(진행 중) · `TAKEN_DOWN` 까닭 `ADVERTISER_PAUSE`(정지 중)·`EMERGENCY`·`SUSPENDED`(내려짐). `ADVERTISER_STOP`·끝난 건·취소는 뺀다. 최대 5, 시작 늦은 차례. `last7` = 오늘 포함 최근 7일(KST) 합, `spark` = 그 7일 날짜별(오래된 차례, 기간 밖 날은 0·`SHOWING`).
9. **운영자 합계**(`ad.read`): 범위 `from`·`to` 필수(KST 날짜, 끝 포함), `from > to` 400 `fields.from`, 길이 최대 366일 400 `fields.to`. 거르기 `type` = 장부 유형 8(`WIDE_BANNER`·`STRIP_BANNER`·`SQUARE_BANNER`·`SMALL_BANNER`·`WING_BANNER`·`TEXT_LINE`·`HOTDEAL_TOP`·`HOTDEAL_CATEGORY` — 모르면 400 `fields.type`)과 같은 상품 자리 줄이 있는 건, `advertiserId` = 일정표 광고주 ID(`adv-<id>` · `biz-<사업자 신원 id>` — 모양이 틀리면 400 `fields.advertiserId`). 핫딜 노출 자리 건은 그 신청 건(`HOTDEAL_POST`·`HOTDEAL_PLACEMENT`)의 집계이고 유형은 노출 자리 상품. `bookings` = 범위 안 노출 > 0 인 건 수. `byType` = 유형별(건 수 = 그 유형에서 노출 > 0 인 건), `topAdvertisers` = 노출 차례 10. `advertiser` = 거른 광고주 `{id,name,kind}`(거르지 않았으면 null, 없는 광고주 404 아님 — null).
10. **운영자 건별**: 범위와 기간이 겹치고 시작한(`startAt ≤ 지금`) 장부 건 중 결제 뒤 상태(`SCHEDULED`·`TAKEN_DOWN`·승인 뒤 `CANCELED`) + 범위 합(0 포함). 줄 = 일정표 `Row`(상태·까닭·광고주·기간 그대로) + `impressions`·`clicks`. `sort=impressions|clicks|ctr`(기본 impressions) · `dir=asc|desc`(기본 desc) · 클릭률 정렬에서 노출 0 은 방향과 상관없이 뒤 · 같으면 id 내림차순 · `page`(0부터)·`size`(기본 20, 최대 100).
11. **운영자 한 건**: 장부 건이면 규칙 2~6 그대로(`ad.read` — 핫딜 노출 자리 건 포함). 장부 유형 줄이 없는 건은 기존 상세처럼 404.
12. 응답은 모두 `Cache-Control: no-store`. 개인정보 없음(광고주 이름·가린 값만 — 기존 Row 와 같음). 감사 기록 없음(조회).

## 권한·메뉴
- 운영자 세 API·화면 = `ad.read`(새 권한 없음). 서버 `AdminScreenRegistry` 「광고」 묶음에 `adPerformance` `/ads/performance` `menu.adPerformance` `pi pi-chart-bar` `Permission.AD_READ` 를 광고 일정표 다음에. admin 메뉴 마이그레이션 없음(광고 기본 설정 추가 때와 같음 — 저장된 메뉴에 새 화면이 붙는 방식 확인).
- 사업자 두 API = 기존 사업자 신청 건 API 와 같은 로그인·본인 검사.
- CSV 내려받기는 후속(운영자만).

## API
- 사업자 `GET /api/business/applications/{id}/stats?from&to` → `AdApplicationStats` · `GET /api/business/ads/stats-summary` → `{ asOf, items[{ id, title, kind, adType, placement, status, phase, takedownCause, startDate, endDate, last7{impressions,clicks}, spark[{date,impressions,clicks,state}] }] }`(칸 이름·값은 공개 신청 건 요약 `BizMemberView.Summary` 와 같은 뜻).
- 운영자 `GET /api/admin/ads/{id}/stats?from&to` → `AdApplicationStats` · `GET /api/admin/ads/stats?from&to&type&advertiserId` → `{ asOf, range{from,to}, advertiser|null, totals{impressions,clicks,bookings}, days[{date,impressions,clicks}], byType[{type,impressions,clicks,bookings}], byPlacement[{placement,impressions,clicks}], topAdvertisers[{advertiser{id,name,kind},impressions,clicks,bookings}] }` · `GET /api/admin/ads/stats/bookings?from&to&type&advertiserId&sort&dir&page&size` → `{ items[Row + impressions + clicks], total }`. 글자 경로 `/stats` 가 `/{id}` 보다 먼저 잡히는지 시험으로 확인.
- `AdApplicationStats` = `{ range{from,to}, asOf, totals{impressions,clicks}, days[{date,impressions,clicks,state: SHOWING|PAUSED|TAKEN_DOWN}], byPlacement[{placement,impressions,clicks}], byVersion[{version,approvedAt,current,impressions,clicks}], downtimes[{from,to,cause}] }`.

## 미확정
- CSV 내려받기(운영자) · 운영자 범위 최대 366일(가정) · 오늘 숫자 지연 안내 문구만(서버 캐시 없음 — 매 요청 합).
- 큰 기간·많은 건에서 합계 쿼리 비용(지금은 `ix_ads_day` + PK) — 느리면 후속으로 일 단위 요약 표.

## 서버 구현 결정 (2026-11-02)
계약과 다르게·더 정한 것만. 마이그레이션 없음(shared 마지막 V130 확인 — 읽기만, 인덱스도 기존 PK·`ix_ads_day`).
- **DTO 정본**: 한 건 `AdApplicationStats` = shared `ad/dto/AdStatsView.Application`(두 앱 공용) · 사업자 요약 = api `bizapplication/dto/BizAdStatsView.Summary` · 운영자 합계·건별 = admin `ad/dto/AdStatsAdminView.Overview`·`BookingPage`(건별 줄은 일정표 `Row` 칸을 펼친 `BookingRow` + `impressions`·`clicks`). 계산 shared `ad/service/AdStatsCalc`(DB 없는 순수 계산)·`AdStatsService`, 읽기 shared `ad/repository/master/AdStatsStore`, 운영자 쿼리 admin `AdStatsAdminStore`·`AdStatsAdminService`, 사업자 api `MyBizAdStatsService`. 사업자 두 API 는 `MyBizApplicationController`, 운영자 세 API 는 `AdAdminController`(`/stats`·`/stats/bookings` 글자 경로가 `/{id}` 보다 먼저 잡힘 — 시험 확인).
- **400 칸**: 한 건(두 앱)은 규칙 2 글자 그대로 — `from`·`to` 어느 쪽 형식이 틀려도·`from > to` 도 `fields.from`. 운영자 합계·건별은 빠짐·형식 오류를 그 칸(`fields.from`·`fields.to`)에, 그 밖 `fields.type`·`fields.advertiserId`·`fields.sort`·`fields.dir`·`fields.page`·`fields.size`. 문구는 기존 키(`bizapp.field.required`·`bizapp.period.invalid`·`bizapp.period.range`(366)·`bizapp.value.invalid`) — 새 문구 없음.
- **한 건 범위**: 요청이 건 기간과 겹치지 않으면 빈 결과에 `range` = 요청 그대로(빠진 쪽은 기본값). 시작 전·노출 없는 핫딜 글은 `range` = 시작 날짜 하루, 목록 다섯 모두 빈 목록(`byVersion` 도). **승인 뒤 취소된 건의 끝 날짜 = min(끝 날짜, 닫힌 날)**(취소 뒤 날이 「노출 중 0」으로 보이지 않게 — 더 정함).
- **멈춘 구간 짝짓기**: 시작 `TAKEN_DOWN`(까닭 = note — 긴급 내림·사업자 정지가 까닭 이름을 note 에 남긴다, 모르면 EMERGENCY)·`PAUSED`·`STOPPED`, 끝 `RESTORED`·`RESUMED`·`CANCELED`·`FORFEITED`. 열린 구간에 새 시작이 오면(정지 → 중단) 그 순간 닫고 새로 연다. 환불 승인(`REFUND_APPROVED` → 취소 상태)은 끝 기록으로 보지 않고 아래 보정이 닫힌 시각에 닫는다. 보정: 지금 내림인데 열린 구간이 없으면 `taken_down_at` 부터 `to = null`(까닭은 상태 칸 — 열린 구간이 있어도 까닭은 상태 칸을 따른다), 내림이 아닌데 열려 있으면 `closed_at`(없으면 `updated_at`)에 닫는다.
- **버전·자리**: `current` = 최신 승인 버전(승인 기록 없이 집계만 있는 버전은 `current=false`·`approvedAt=null`). 자리·유형·광고주 줄은 노출 → 클릭 → 이름(ID) 차례.
- **운영자 합계**: `byType`·`byPlacement`·`topAdvertisers` 는 노출·클릭 둘 다 0 인 줄을 뺀다(화면 타입 주석·목업과 같음). 범위는 오늘을 넘어도 자르지 않는다(미래 날 0). 건 유형 = 핫딜 게시가 아닌 상품 줄 중 이름 차례 첫째(`capacityProduct` 와 같음) — 노출 없는 핫딜 글은 합계·건별에 나오지 않고 한 건은 404. 광고주 묶음 = 일정표 `Row` 의 광고주 ID: `advertiser_id` 가 있으면 `adv-`, 없고 사업자 신원이 있으면 `biz-` — 그래서 **`biz-<id>` 거르기 = 광고주 연결이 없는 그 신원의 건**. ID 없는(이름만) 광고주 건은 합계엔 들고 `topAdvertisers` 에서만 빠진다. 거른 `biz-` 광고주 이름 = 신원 상호.
- **운영자 건별**: 클릭률 정렬은 곱셈 비교(소수 오차 없음), `total` = 거른 뒤 전체 수, 쪽이 넘치면 빈 `items`. 상태 `CANCELED` 는 `approved_at` 있는 것만(규칙 10 글자 그대로 — 결제 전 승인 취소도 시작했으면 0 으로 든다).
- **사업자 요약**: 본인 = `user_id`(기존 상세 `snapshotOwned` 와 같음), 시작 늦은 차례 → id 내림차순, `last7` = `spark` 합(기간 밖 날은 0·SHOWING).
- **메뉴**: 서버 「광고」 묶음 차례 `adReviews` → `ads` → `adPerformance` → `adPlacements`(계약의 「넷째」와 달리 셋째 — 화면 `util-admin-menu.ts` 차례와 같은 일정표 다음·광고 기본 설정 앞). 메뉴 마이그레이션 없음(관리자 셸은 저장된 구성을 읽지 않는다 — `AdminMenuService`).
- **기존 코드 손댐(범위 안)**: `AdAdminService.row`·`MyBizApplicationService.summary` 를 package `static` 으로(건별 줄·요약 줄 재사용), 메뉴 카탈로그 시험 `BizApplicationAdminCatalogTest` 갱신.
- **시험**(`--tests` 선별, 2026-11-02 실행): shared `AdStatsCalcTest` 9/9(범위 자르기·짝짓기·보정·날짜 상태·0 채우기·자리·버전·구간) · api `BizAdStatsHttpTest` 3/3(본인 건·남의 건/없는 건 404·401·시작 전·PAUSED/TAKEN_DOWN 날·구간 `to=null`·범위 자르기·400 `fields.from`·no-store·노출 없는 핫딜 글 빈 결과·요약 규칙 8 포함/제외·최대 5·차례·7일) · admin `AdStatsAdminHttpTest` 4/4(403·400 칸·366일·합계·유형/광고주 거르기·건별 정렬(클릭률 노출 0 뒤)·쪽·시작한 건만·한 건·취소 건 끝 날짜·경로·메뉴) · 회귀 확인 admin `BizApplicationAdminCatalogTest` 8/8·`AdAdminHttpTest` 2/2·`AdPlacementAdminHttpTest` 5/5, api `BizApplicationHttpTest` 5/5.
