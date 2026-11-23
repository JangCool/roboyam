# 광고 자리 관리 — 계약

## 의도
「광고 일정·등록」(달력 + 탭)으로는 신고·요청이 온 광고를 바로 찾아 내리기 어렵다(사용자 2026-11-23). 실제 공개 화면 미리보기에서 **광고 자리를 눌러** 그 자리에 걸린 광고를 보고 바로 내리거나 등록한다.
기존 「광고 일정·등록」·광고 서랍은 그대로 두고, 이 화면은 자리 기준으로 찾아 들어가는 입구다.

## 화면 경로·메뉴
- admin-web `/ads/slots` 「광고 자리 관리」 — 메뉴 「광고」 묶음의 **광고 심사 바로 다음**(menu-contract 「관리자 메뉴 정리」). 메뉴 등록(서버 `AdminScreenRegistry` + `UtilAdminMenu.ITEMS`)은 서버 단계에서 같이 한다 — 목업 단계는 주소로 연다.

## 화면 (시안 확정 2026-11-23)
- **미리보기(전체 폭)**: 실제 공개 화면을 iframe 으로(테마 `ThemeLivePreview`·캐시 `CacheLiveFrame` 과 같은 방식). 위에 화면 고르기(홈 · 게시판 목록 · 글 보기 · 가젯 페이지 — 게시판·글·페이지는 하나 고르기)와 기기(데스크톱 · 모바일).
  광고 자리만 강조 상자 + 이름표(종류 이름 · 진행 중 수), **빈 자리는 점선**. 나머지 화면은 흐리게. 자리를 누르면 드로어가 열린다.
- **드로어(오른쪽, 고정 영역 아님 · 바탕을 막지 않는다)**: 광고 상세는 **같은 드로어 안에서 바뀐다**(두 겹으로 쌓지 않음, 머리에 뒤로 화살표). 처리 이력·계약·결제 같은 깊은 내용은 기존 광고 서랍(`AdBookingDrawer`)으로 연다.
  - 위: 종류 이름 + [광고 등록](기존 `AdManualDrawer`) · 이 종류가 나가는 곳(자리 목록) · 진행 중 / 한도 · 애드센스 상태(빈 자리를 애드센스가 채우는가) · 상태 거르기(전체 · 진행 중 · 예약 · 입금 대기 · 내림) · 체크하면 「고른 N건 내리기」.
  - 카드(낮게): 제목 한 줄(**truncate**) + 상태 태그 → 섬네일 전체 폭(종류별 높이 · 한 줄 광고는 섬네일 없음) → 네 칸 한 줄(기간 · 남은 날 · 노출 · 클릭률) → 광고주 + [상세] · [내리기].
  - 상세: 섬네일 → 기본 정보 세 칸 격자(광고주 · 기간 · 상태 · 결제 방식 · 금액 · 나가는 곳 …) → 성과(최근 7일 노출·클릭·클릭률) → 링크(예시 주소면 경고) → [기존 서랍에서 열기] · [내리기].
- **내리기**: 사유 필수(5~200자, 기존 긴급 내림과 같다) · 광고주 메일 선택. 여러 건이면 사유 하나로 한 번에.

## 권한·정책
- 보기 `ad.read` · 내리기 `ad.takedown`(없으면 단추 없음) · 등록 `ad.manage`.
- 광고는 **자리마다가 아니라 종류마다** 관리된다 — 같은 종류의 자리를 누르면 같은 목록(드로어 위 「나가는 곳」으로 알린다). 한 자리 전용 광고는 미정.
- 장부 광고가 먼저, 빈 자리만 애드센스(adsense-contract). 날개 애드센스는 한쪽만.

## API (목업 뒤 확정 2026-11-23 — 모양의 원천 `apps/admin-web/app/composables/ad/api/useAdSlotApi.types.ts`)
사용자 지시 「목업 끝나면 바로 서버 구현」(2026-11-23) — 화면 확인 없이 서버로 넘어갔다.
- `GET /api/admin/ads/slots`(`ad.read`) → `{ types: [{ type, running, limit, adsense: FILLS|PARTIAL|EMPTY|NA, places: AdStatPlacement[] }] }` — 종류는 광고 일정표의 `AdType`(= `BizProduct`) 차례. `running`·`limit` 은 일정표 요약(`AdTypeSummary`)과 같은 셈.
  `adsense`: 나가는 자리(`places`)마다 공개 화면 규칙으로 본다 — 아래 「구현 결과」.
- `GET /api/admin/ads/slots/{type}?status=`(`ad.read`) → `{ type, items: AdSlotCard[], counts: { ALL, RUNNING, SCHEDULED, AWAITING_DEPOSIT, TAKEN_DOWN } }`.
  - 대상: 진행 중 · 예약 · 결제·입금 대기 · 내림(끝남·취소·심사 대기는 뺀다). 차례 = 진행 중 → 예약 → 대기 → 내림, 같은 상태는 시작이 빠른 차례.
  - **거르기 `AWAITING_DEPOSIT` 은 `AWAITING_PAYMENT`(온라인 결제 대기)도 함께 센다**(세션 결정 — 일정표 요약의 `awaitingPayment` 「결제·입금 대기」와 같은 묶음, 둘 다 자리를 잡고 있다). 카드의 `status` 는 실제 값 그대로.
  - 카드 칸: `id, type, title, status, takedownCause, imageUrl, text, period, remainingDays, impressions7d, clicks7d, advertiser{id,name,kind}, paymentMethod, amount, domain, linkUrl, exampleUrl`. 7일 성과는 광고 성과와 같은 셈(같은 방문자 하루 1번, 봇 제외 — 오늘 포함 최근 7일). `exampleUrl` 은 서버가 링크 호스트를 보고 정한다(example.com · example.org · example.net 과 그 하위, `.example` · `.test` · `.invalid` · `localhost`).
- `POST /api/admin/ads/takedown`(`ad.takedown`, CSRF) `{ ids[1~50], reason 5~200, notifyAdvertiser }` → `{ takenDown, failed: [{ id, message }] }` — 건마다 기존 단건 긴급 내림(`POST /api/admin/ads/{id}/takedown`)과 같은 규칙·감사·메일·캐시 무효화. 한 건이 실패해도 나머지는 내린다(건마다 따로 처리), 실패 문구는 서버 오류 문구 그대로.
- 공개 사이트 미리보기 표시: user-web `adminPreview=1&adSlotPreview=1` — `data-ad-slot`(종류)·`data-ad-slot-empty` 요소 위 상자, 메시지 `irobit:ad-slot-preview` { labels, active } / `…-pick` { slotType } / `…-areas` { counts, empty }.
- 메뉴: 화면 키 `adSlots` `/ads/slots` 「광고 자리 관리」(`menu.adSlots`, `pi pi-th-large`, `ad.read`) — 광고 묶음의 광고 심사 바로 다음(서버 `AdminScreenRegistry`·화면 `UtilAdminMenu.ITEMS` 같이). 저장한 메뉴 구성이 있을 때 새 화면이 어떻게 보이는지는 기존 셸 규칙 그대로(새 규칙을 만들지 않는다).

## 구현 결과 (2026-11-23)
- 서버 admin `AdSlotAdminController`·`AdSlotAdminService`(일정표 요약 `AdAdminService.summary`·금액·단건 내림 `takedownOne` 을 떼어 함께 쓴다, 7일 성과 `ad_daily_stats`). DB 변경 없음. 메뉴는 `AdminScreenRegistry` + admin 마이그레이션 V13(저장한 메뉴 구성의 「광고 심사」 뒤에 넣는다, 선례 V12).
- `adsense`(미확정 → 추천 확정 2026-11-23): 나가는 자리마다 본다 — 배너 가젯(HOME·PAGE·BOARD) = 그 종류 단위, 한 줄 광고 가젯(HOME·PAGE·BOARD) = 목록 한 줄 인피드(사용자 「0건일 때만 인피드 하나」 2026-11-23, adsense-contract), 글 보기 중간 = `POST_INLINE` 단위(종류 상관없이), 목록 한 줄 = 인피드 단위 + 레이아웃 키, 날개 = 날개 단위로 **한쪽만**(양쪽을 켜면 한쪽은 빈다). 핫딜 자리는 애드센스 틀이 없다. 틀 있는 자리가 없으면 NA, 「광고 표시」 단계 + 게시자 ID 가 아니거나 채우는 자리가 0이면 EMPTY, 다 채우면 FILLS, 일부만이면 PARTIAL(「일부 자리만 애드센스가 채워요」). 그래서 한 줄은 인피드(+ 글 중간이면 글 중간 단위)가 있으면 FILLS, 없으면 EMPTY 이고 NA 가 되지 않는다. `places` 는 노출 자리 설정을 따라 바뀐다(글 중간·목록 한 줄·날개 좌우를 켠 경우만).
- 카드 `advertiser.id`·`linkUrl` 은 null 일 수 있다. 제목은 한 줄 = 글, 핫딜 = 딜 제목, 배너 = 대체 글 → 건 제목 → 광고주 이름. `exampleUrl` 은 `*.localhost` 도 참.
- 여러 건 내리기: 서비스·컨트롤러는 트랜잭션을 열지 않고 건마다 기존 `AdOperatorService.takedown`(자기 트랜잭션·감사·알림·커밋 뒤 메일). 서버 사유 검사는 기존 단건과 같은 1~200자(5자 하한은 화면 대화상자 — 단건과 같은 나눔, 추천 확정). 예상 못 한 오류는 그 건만 `error.INTERNAL` 로 실패.
- 광고주 메일(추천 확정): 단건과 같이 요청 안에서 건마다 보내고 결과(`takedown.mail` SENT·FAILED·NO_ADDRESS)를 그 건에 남긴다. 메일 대기열(`MailOutbox`)은 커머스 회원 메일용(받는 주소 = `users.email`)이라 계정 없는 광고주에 맞지 않고, 결과 기록 방식도 바뀌므로 쓰지 않는다. 최대 50건이라 느릴 수 있음을 받아들인다(처리 중에는 화면이 내리기 단추를 막는다).
- 미리보기에서 애드센스는 그리지 않는다(기존 관리자 미리보기 규칙) — 애드센스가 채울 자리도 빈 점선. 목록 한 줄·핫딜 상단은 광고가 있을 때만 상자가 보인다.

## 미확정 → 추천 확정 (사용자 「미확정 추천상태로 처리해」 2026-11-23)
- 한 줄 광고의 `adsense` → 자리마다 판정 + PARTIAL 추가(위 「구현 결과」). 이어서 한 줄 광고 가젯도 장부 0건이면 인피드 하나로 채우게 했다(사용자).
- 여러 건 내림의 광고주 메일 → 단건과 같이 동기 발송·결과 기록 유지(위).
- 한 자리 전용 광고 · 자리별 한도 → 만들지 않는다. 광고는 종류 단위로 관리하고 드로어 「나가는 곳」으로 알린다(장부·일정표·신청 흐름이 모두 종류 단위라 바꾸면 범위가 크다). 필요해지면 새 기능으로 따로 계약한다.
- 모바일 미리보기의 날개(데스크톱 전용) → 미리보기에 상자를 그리지 않는다. 미리보기 아래 종류 카드에서 날개를 연다(지금 구현 그대로).
