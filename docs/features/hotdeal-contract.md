# 회원 핫딜(1차) 계약 — 초안

**상태: 화면 목업 진행 중(서버 없음). 화면 확정 전 초안이라 바뀔 수 있다.** 정본 설계 `docs/plans/biz-ad-hotdeal/03-hotdeal.md`(1-1·1-3·3·5-1·5-3 「1차」·7-1·7-2), 메뉴 `04-ia-review.md` §1-2·§4.

## 의도
- 사업자 없이 완결되는 **회원 핫딜**: DEAL 게시판(`boards.kind = DEAL`) 글에 딜 정보(가격·몰·링크·배송·마감·재고)를 붙이고, 회원이 품절·가격 변경을 알려 오래된 딜을 걸러 낸다.
- 운영자는 핫딜 글 종료·재개, 링크 규칙(차단·제휴·대형몰), 위장 홍보 의심 후보를 본다.
- 사업자 핫딜·입점 상품·노출권·끌올·쿠폰 코드는 2차 이후(이번 범위 밖). 공개 광고의 핫딜 두 자리(`HOTDEAL_TOP`·`HOTDEAL_CATEGORY`, `ad-contract.md`)는 이번에 손대지 않는다.

## 결정 (사용자 2026-11-01)
- **중복 딜은 안내만**: 글쓰기에 「진행 중인 같은 딜 N건」(그래도 등록 가능), 글 보기에 「같은 딜」 묶음. 모든 글에 `product_url_key`.
- **담당**: 게시판 매니저·스텝은 그 게시판에서, `hotdeal.manage` 는 모든 핫딜 게시판(담당 범위 기능 새로 안 만듦).
- **상품 이미지는 1차에 링크 OG 자동 가져오기**: 링크를 붙이면 서버가 OG 이미지·몰 이름을 가져와 채움, 실패·없음 상태 있음, 글쓴이가 바꾸거나 지울 수 있음.
- **목록은 가격 줄 목록형** 기본(딜가 굵게·정상가 취소선·할인율·몰·배지), 기존 게시판 레이아웃(목록·카드·갤러리) 선택도 유지.

## 목업 가정 (03 §6 추천안 — 확인 필요)
- 단축 URL 기본 차단 시드: `bit.ly`·`han.gl`·`coupa.ng`·`vo.la`·`me2.do`·`t.co`·`goo.gl`·`tinyurl.com`·`url.kr`(BLOCK, 메모 「단축 주소」). 최종 URL 풀기는 2차.
- 종료 요청: 서로 다른 회원 **3명** → 의심(FLAGGED) + 작성자 알림 → 작성자가 「아직 진행 중」을 안 누르면 **12시간** 뒤 자동 종료 → 요청자에게 **3P**(사유 `DEAL_END_REQUEST`). 회원당 하루 20회. 사유 3개(`SOLD_OUT`·`PRICE_CHANGED`·`LINK_DEAD`), 자유 글 없음. 작성자는 자기 글에 요청 못 함. 요청자 신원은 누구에게도 안 보임(개수·사유별 개수만).
- 쓰기 자격: 일반 등급 이상(새싹 불가 — 관리자 게시판 화면이 이미 DEAL 의 첫 등급을 쓰기에서 뺌) + 이메일 인증. 비회원 불가.
- 위장 홍보 후보: 같은 회원이 **7일** 안에 같은 판매처(대형몰 제외)를 **3건** 이상 → 후보(운영자 목록만, 자동 제재 없음).
- 의심 글은 목록에서 아래로(정렬 키 뒤), 「종료 숨기기」 기본 켬.
- 신고 사유 추가 2개: `DEAL_FALSE_PRICE`(가격·정보가 사실과 달라요) · `DEAL_HIDDEN_AD`(광고·홍보를 숨겼어요) — 핫딜 글에서만 보임.

## 데이터 모양 (목업 공통 — 두 앱이 같은 이름을 쓴다)
```
DealStatus    = ACTIVE | ENDED                         // 저장
DealView      = ACTIVE | FLAGGED | ENDED | EXPIRED     // 읽을 때 계산: FLAGGED = 종료 요청 ≥ 문턱이고 keep 전, EXPIRED = endsAt 지남
DealEndedBy   = AUTHOR | MANAGER | AUTO                // ENDED 일 때
DealStock     = UNKNOWN | IN_STOCK | LOW | SOLD_OUT
DealEndReason = SOLD_OUT | PRICE_CHANGED | LINK_DEAD
DomainRule    = BLOCK | AFFILIATE | null(대형몰만)

PostDeal { source: MEMBER, storeName(≤40), productUrl(≤1000), productHost, normalPrice|null, dealPrice,
  discountRate|null(읽을 때 계산 — normal > deal 일 때 정수 %), shippingFee|null(0 무료, null 모름),
  couponNote|null(≤100), priceBasis|null(≤100), startsAt|null, endsAt|null, stockState, affiliate,
  image: { url, source: OG | UPLOAD } | null, status, view, endedBy|null, endedAt|null,
  endRequests: { total, bySoldOut, byPriceChanged, byLinkDead, mine: DealEndReason|null },
  flaggedAt|null, autoEndAt|null(flaggedAt+유예), sameDealCount }
```
- 링크 판정(`link-check`): 소문자·스킴·www.·포트·경로를 뗀 호스트, **접미사 일치·더 구체적인 규칙 우선**(`link.coupang.com` 에 규칙이 있으면 `coupang.com` 보다 먼저). 결과 `{ host, rule: BLOCK|AFFILIATE|null, common, storeName(호스트→이름 표), og: { status: OK|NONE|FAILED, imageUrl, title, storeName }, sameDeals: [{postId, title, dealPrice, createdAt}] }`.
- 할인율·제휴 강제·종료 판정은 서버가 정한다(화면은 미리보기만).
- 가젯 `HOT_DEALS` 설정(공동구매 가젯 ⚙ 방식): `boards`(핫딜 게시판 slug 목록, 비우면 모든 핫딜 게시판) · `sort` `LATEST`(기본)·`POPULAR`·`ENDING_SOON` · `count` 1~10(기본 5). 진행 중(ACTIVE·FLAGGED 중 의심 제외)만, 비면 영역을 그리지 않음. 줄 = 목록 줄과 같은 모양(이미지·제목·딜가·정상가·할인율·몰·배지).

## 화면 경로
- user-web
  - DEAL 게시판 목록 `/boards/{slug}` — 게시판 `kind` 가 DEAL 이면 가격 줄 목록형. 정렬 최신·인기·마감 임박, 「종료 숨기기」, 말머리. 목업 게시판 slug `hotdeal`.
  - 핫딜 쓰기·고치기 `/write?board=hotdeal` — 링크 → 판정·제휴 자동 표시·OG → 가격·배송·마감·재고 → 제목·본문. 모바일은 단계 입력.
  - 글 보기 `/posts/{id}` — 본문 위 딜 카드(구매하기 `rel="nofollow sponsored noopener"`, 품절·가격 변경 알리기, 작성자 종료·「아직 진행 중」, 의심·종료 표시, 같은 딜 묶음).
  - 가젯 `HOT_DEALS` — 홈·가젯 페이지·게시판 레이아웃.
- admin-web 핫딜 묶음(사이드바 「핫딜」, 핫딜 심사 다음)
  - 핫딜 글 `/hotdeals` · 링크 규칙 `/hotdeals/domains` · 의심 후보 `/hotdeals/suspicions` (+ 핫딜 설정 `/hotdeals/settings`).
  - 홈·가젯 페이지·게시판 레이아웃 편집기에 `HOT_DEALS` 가젯과 ⚙ 설정.

## 필드·검증 (글쓰기)
- 상품 링크 필수 `http(s)`, ≤1000자, BLOCK 이면 등록 불가(`fields."deal.productUrl"`).
- 쇼핑몰 이름 필수 1~40자(판정 결과로 자동 채움, 고칠 수 있음).
- 딜가 필수 0 이상 정수(원). 정상가 선택, 있으면 딜가 이상. 배송비: 무료(0) · 모름(null) · 금액.
- 마감 선택(지금 이후 ~ 365일), 재고 기본 UNKNOWN.
- 제휴 표시: AFFILIATE 도메인이면 켜고 잠금, 아니면 글쓴이가 체크.
- 이미지: OG 로 채움(없음·실패 안내), 글쓴이가 지우거나 직접 올린 이미지로 바꿈.
- 오류는 `400 VALIDATION fields."deal.*"` 로 다른 글 칸 오류와 함께.

## 권한·정책
- 관리자: 조회 `hotdeal.read`, 종료·재개·의심 후보 무시·설정 `hotdeal.manage`, 링크 규칙 `hotdeal.domain.manage`(지금 카탈로그 `featureReady=false` — 서버에서 켜야 함). 게시판 매니저·스텝은 그 게시판 핫딜 글의 종료·재개만.
- 회원: 작성자 종료(ACTIVE·FLAGGED → ENDED, AUTHOR), 「아직 진행 중」(FLAGGED → ACTIVE, 요청 리셋 아님 — `end_flag_cleared_at`). 재개는 운영진만(의심·요청 리셋). 재개했는데 기간이 지났으면 작성자에게 「마감을 고쳐 주세요」 안내.
- 감사: `HOTDEAL_END`·`HOTDEAL_REOPEN`·`HOTDEAL_DOMAIN_CREATE/UPDATE/DELETE`·`HOTDEAL_SUSPICION_DISMISS`·`HOTDEAL_SETTINGS_UPDATE`.

## API 후보
- 공개(server/api): `POST/PUT /api/posts` 본문 `deal?: DealInput` · 글 상세·목록 줄에 `deal` · `GET /api/deals/link-check?url=&board=` · `POST /api/posts/{id}/deal/end` · `POST /api/posts/{id}/deal/keep` · `POST /api/posts/{id}/deal/end-requests {reason}` · `GET /api/posts/{id}/deal/same` · 게시판 목록 `?sort=latest|popular|ending&hideEnded=true&prefix=` · 가젯 `HOT_DEALS` 의 `data`.
- 관리자(server/admin): `GET /api/admin/hotdeals?view&board&q&page` · `GET /{postId}` · `POST /{postId}/end {reason?}` · `POST /{postId}/reopen {reason?}` · `GET/POST/PUT/DELETE /api/admin/hotdeal-domains` · `GET /api/admin/hotdeal-suspicions?status` · `POST /{id}/dismiss {note?}` · `GET/PUT /api/admin/hotdeal-settings`.
- DB(shared 스트림 — 번호는 서버 세션이 develop 최신 확인 뒤): `post_deals` · `deal_domains` · `deal_end_requests` · `deal_suspicions` · `deal_settings`.
- 배치(api): 의심 유예 지남 → 자동 종료 + 요청자 포인트.
- 알림: 회원 `DEAL_END_REQUESTED`(작성자에게, 문턱 도달 때 한 번).

## 미확정
- OG 가져오기: 허용 도메인·크기 제한·캐시·이미지 저장 위치(외부 주소 그대로 vs 서버 사본), 실패 재시도.
- 같은 딜 판정 범위(진행 중만 vs 최근 N일), `product_url_key` 정규화에서 쿼리 문자열을 어디까지 남길지(상품 ID 가 쿼리에 있는 몰).
- 종료 요청 포인트를 누구에게 주나 — 자동 종료 때만 vs 작성자·운영진 종료 때도(커뮤니티는 둘 다).
- 가젯 `HOT_DEALS` 범위: 핫딜 게시판 고르기(비우면 전체).
