# 회원 핫딜(1차) 계약

**상태: 화면 확정·서버 구현·두 앱 실제 API 연결 2026-11-01(브랜치 `claude/hotdeal-member-backend-100b0f`, develop 미병합) — 다음은 실제 호출 확인·QA.** 정본 설계 `docs/plans/biz-ad-hotdeal/03-hotdeal.md`(1-1·1-3·3·5-1·5-3 「1차」·7-1·7-2), 보강 검토 `05-hotdeal-review.md`(**부딪히면 05 가 정본**), 메뉴 `04-ia-review.md` §1-2·§4.

## 의도
- 사업자 없이 완결되는 **회원 핫딜**: DEAL 게시판(`boards.kind = DEAL`) 글에 딜 정보(가격·몰·링크·배송·마감·재고)를 붙이고, 회원이 품절·가격 변경을 알려 오래된 딜을 걸러 낸다.
- 운영자는 핫딜 글 종료·재개, 링크 규칙(차단·제휴·대형몰), 위장 홍보 의심 후보(회원·판매처)를 본다.
- 사업자 핫딜·입점 상품·노출권·끌올·쿠폰 코드는 2차 이후(이번 범위 밖). 공개 광고의 핫딜 두 자리(`HOTDEAL_TOP`·`HOTDEAL_CATEGORY`, `ad-contract.md`)는 이번에 손대지 않는다.

## 사용자 결정
**2026-11-01 (1차 범위)**
- **중복 딜은 안내만**: 글쓰기에 「진행 중인 같은 딜 N건」(그래도 등록 가능, 줄마다 가격 차), 글 보기에 「같은 딜」 묶음(가격 차). 모든 글에 `product_url_key`.
- **담당**: 게시판 매니저·스텝은 그 게시판에서, `hotdeal.manage` 는 모든 핫딜 게시판(담당 범위 기능 새로 안 만듦).
- **상품 이미지는 1차에 링크 OG 자동 가져오기**: 실패·없음 상태 있음, 글쓴이가 바꾸거나 지울 수 있음.
- **목록은 가격 줄 목록형** 기본, 기존 게시판 레이아웃(목록·카드·갤러리) 선택도 유지.

**2026-11-01 (보강 — 05 §1)**
- **D1 OG 이미지는 우리 서버에 사본 저장**: 가로 600px JPEG 한 장(품질을 낮춰 ≤300KB, 투명 배경은 흰 바탕; 원본이 WebP 면 ≤300KB·가로 ≤1200 일 때만 메타데이터를 떼고 그대로, 넘으면 이미지 「없음」 — 2026-11-01 정리, 의존성 없이 WebP 인코딩 불가), 원본 주소 보존(저작권 요청 때 지움), 딜 종료·글 삭제 90일 뒤 정리. 외부 이미지 직접 링크 금지.
- **D2 작성자 종료 되돌리기**: 작성자가 직접 종료한 글만, 종료 뒤 **24시간 안 1번** 다시 진행 중. 자동 종료(AUTO)·운영진 종료는 운영진만 재개.
- **D3 링크를 다른 몰로 바꾸는 수정은 허용 + 표시·알림**: 딜 칸 수정 이력 6개월(`post_deal_revisions`, 회원·사업자 공통), 카드 「링크 바뀜」·「가격 수정 N회」, 호스트가 바뀌면 운영자 알림.
- **D4 가격 허위 신고는 종료 요청과 합침**: `DEAL_FALSE_PRICE` 신고 = 종료 요청 `PRICE_CHANGED` 1건(같은 회원 1회), 자동 숨김 셈 제외. `DEAL_HIDDEN_AD` 는 자동 숨김 셈 포함.
- **D5 제휴 링크 방식은 설정으로 켜고 끔**: `affiliateMode` = `MEMBER`(개인 제휴 허용, **기본** — 초기 운영) / `SITE`(사이트 제휴 — 개인 제휴 받지 않음, 사람이 몰리면 전환). `SITE` 면 「사이트 제휴 몰」 표(몰 도메인·제휴 프로그램 이름·변환 방식·켬/끔)로 구매 링크를 바꾼다. 구매하기는 늘 서버 경유 `/api/deals/{postId}/go`(302).
- 추천안 채택(바꿀 수 있음): **종료 요청 자격 = 게시판 쓰기 등급(`writeMin`, 기본 일반) + 이메일 인증 + 정지 아님** · **회원 하루 핫딜 글 5건·같은 판매처 연속 10분 간격** · **마감 없는 딜 30일 뒤 기간 종료**.

## 확정 가정 (2026-11-01 화면 확정 — 03 §6 추천안과 목업에서 정한 것, 서버도 이대로)
- 단축 주소 기본 차단 시드: `bit.ly`·`han.gl`·`vo.la`·`me2.do`·`t.co`·`goo.gl`·`tinyurl.com`·`url.kr`(BLOCK, 메모 「단축 주소」). `coupa.ng` 는 차단이 아니라 **제휴**(공식 단축 여부는 「미확정」). 제휴 네트워크 도메인(링크프라이스·애드픽·텐핑 등) 제휴 시드. 리디렉션을 따라간 최종 호스트까지 판정(서버 규칙 2).
- 종료 요청: 서로 다른 회원 **3명** → 의심(FLAGGED) + 작성자 알림 → 작성자가 「아직 진행 중」을 안 누르면 **12시간** 뒤 자동 종료 → 의심 구간 요청자에게 보상(포인트 정책 `dealEndRequestPoints` 기본 3P · 하루 지급 5회, 사유 `DEAL_END_REQUEST`). 회원당 하루 요청 20회 + 같은 작성자 글에 7일 3회. 사유 3개(`SOLD_OUT`·`PRICE_CHANGED`·`LINK_DEAD`), 자유 글 없음. 작성자는 자기 글에 요청 못 함. 요청자 신원은 누구에게도 안 보임(개수·사유별 개수만).
- keep(「아직 진행 중」)은 글마다 최대 2번, 넘으면 「운영자 확인 중」(운영자 알림).
- 쓰기 자격: 일반 등급 이상(새싹 불가) + 이메일 인증. 비회원 불가.
- 위장 홍보 후보: **회원 후보** — 같은 회원이 7일 안에 같은 판매처(대형몰 제외)를 3건 이상. **판매처 후보** — 같은 비대형몰을 30일 안에 서로 다른 회원 3명 이상이 올리고, 그 회원들의 다른 핫딜 비율이 20% 미만. 운영자 목록만, 자동 제재 없음.
- 의심 글은 목록에서 아래로, 「종료 숨기기」 기본 켬.
- 신고 사유 추가 2개: `DEAL_FALSE_PRICE`(가격·정보가 사실과 달라요 → 종료 요청으로 접수, D4) · `DEAL_HIDDEN_AD`(광고·홍보를 숨겼어요) — 핫딜 글에서만 보임.
- **제휴 방식 전환 때 기존 글**: `SITE` 로 바꾸면 기존 개인 제휴 글은 글은 남기고, 구매 링크 클릭 때만 개인 제휴 파라미터를 떼고 사이트 제휴로 바꾼다(사이트 제휴 몰이 아니면 제휴를 떼고 원래 상품 링크). `MEMBER` 로 되돌리면 다시 원래(작성자) 링크. **저장된 `productUrl` 은 바꾸지 않는다**.
- `SITE` 모드 쓰기: 「제휴 링크예요」 체크 숨김, 링크가 AFFILIATE(개인 제휴 — 제휴 네트워크 단축·추적 링크 포함, 최종 호스트까지)로 판정되면 칸 오류 「지금은 개인 제휴 링크를 받지 않아요 — 일반 상품 링크를 넣어 주세요」(`fields."deal.productUrl"`, 코드 `DEAL_MEMBER_AFFILIATE_OFF`). `SITE` 면 `affiliate` 는 false 로 저장.
- **고치기에서 링크를 그대로 두면** `SITE` 여도 개인 제휴 칸 오류 없음. 차단도 같은 규칙 — 링크를 그대로 두면 지금 규칙이 차단이어도 저장 + `warnings[]`(`DEAL_LINK_BLOCKED`), **차단 도메인으로 바꾸면 칸 오류**. `warnings[]` 는 글쓰기 응답 `PostWriteResult.warnings` · 고치기 응답 `PostDetail.warnings`.
- **쓰기 화면이 `affiliateMode` 를 아는 곳**: 핫딜 게시판 응답 `BoardDetail.dealAffiliateMode`(없으면 MEMBER — 링크를 넣기 전부터 체크를 숨김)로 먼저 알고, link-check 응답 `DealLinkCheck.affiliateMode` 가 오면 그 값을 우선. link-check 에 `siteAffiliate: { programName } | null`.
- **「가격 수정 N회」 = 딜가·정상가를 바꾼 수정 줄 수**(배송비 제외). 「링크 바뀜」 = 호스트가 바뀐 적 있음.
- **공개 화면 상한·경고값**(금액 상한·할인율 경고·마감 없는 딜 기간): 서버 단계에서 핫딜 게시판 응답에 핫딜 설정 공개 칸(`priceMaxWon`·`discountWarnPercent`·`autoExpireDays`)으로 내리고 화면은 그 값으로 미리 검사(목업은 기본값 1억·90%·30일 상수). 최종 판정은 서버.
- 제휴 표기(공정위 추천·보증 심사지침 2024-12 개정 — 조건부 표현 금지·첫 부분 표시): 목록 줄·카드 제목 앞 배지 「제휴·수수료」, 카드 첫 줄 「제휴 링크 — 이 링크로 구매하면 작성자가 판매자로부터 수수료를 받습니다」, 글쓴이 체크 문구도 단정형. 카드 아래 「로보얌은 판매자가 아니에요. 가격·재고는 판매처 기준이에요」. **사이트 제휴**(`SITE` 이고 사이트 제휴 몰): 배지 「제휴」 + 카드 첫 줄 「로보얌 제휴 링크 — 이 링크로 구매하면 로보얌이 판매자로부터 수수료를 받습니다」(단정형, ko·en). `MEMBER` 모드 개인 제휴 글은 개인 제휴 문구 그대로.

## 데이터 모양 (목업 공통 — 두 앱이 같은 이름을 쓴다)
```
DealStatus     = ACTIVE | ENDED                         // 저장
DealView       = ACTIVE | FLAGGED | ENDED | EXPIRED     // 읽을 때 계산: FLAGGED = 종료 요청 ≥ 문턱이고 keep 전, EXPIRED = endsAt 지남(마감 없으면 등록 + autoExpireDays)
DealEndedBy    = AUTHOR | MANAGER | AUTO                // ENDED 일 때
DealStock      = UNKNOWN | IN_STOCK | LOW | SOLD_OUT    // SOLD_OUT 저장 = 작성자 종료
DealEndReason  = SOLD_OUT | PRICE_CHANGED | LINK_DEAD
DealPriceBasis = LISTED | COUPON | CARD | MEMBER | OVERSEAS
DomainRule     = BLOCK | AFFILIATE | null(대형몰만)

PostDeal { source: MEMBER, storeName(≤40), productUrl(≤1000), productHost, finalHost|null, normalPrice|null, dealPrice,
  discountRate|null(읽을 때 계산 — normal > deal 일 때 내림 정수 %), shippingFee|null(0 무료, null 모름),
  couponNote|null(≤100), priceBasisKind: DealPriceBasis|null, dutyIncluded(OVERSEAS 일 때), priceBasis|null(메모 ≤100),
  endsAt|null, autoExpireAt|null(마감 없을 때 계산), stockState, affiliate,
  image: { url(우리 서버 사본), source: OG | UPLOAD, imageId?, originUrl? } | null,
  status, view, endedBy|null, endedAt|null, reopenUntil|null(작성자 되돌리기 기한 — AUTHOR 종료이고 아직 안 썼을 때만),
  endRequests: { total, bySoldOut, byPriceChanged, byLinkDead, mine: DealEndReason|null },
  flaggedAt|null, autoEndAt|null(flaggedAt+유예), keepCount, keepMax,
  revision: { priceEdits, linkChanged, lastEditedAt|null }, hot(추천 ≥ hotLikeThreshold), sameDealCount,
  buyUrl(`/api/deals/{postId}/go` — 서버 경유), affiliateKind: MEMBER | SITE | null(지금 방식으로 계산한 표시 종류), siteProgramName|null }
AffiliateMode  = MEMBER | SITE
SiteAffiliateStore { id, domain(정규화), programName(≤40), transform: { type: TEMPLATE, template(`{url}` 자리표시자 1개 필수) } | { type: QUERY, query(붙일 쿼리 `k=v&…`) }, enabled }
```
- `startsAt` 은 1차에서 뺀다.
- 링크 판정(`link-check`): 소문자·스킴·www.·포트·경로를 뗀 호스트, **접미사 일치·더 구체적인 규칙 우선**, 리디렉션 체인의 모든 호스트에 적용(차단 > 제휴). 결과 `{ host, finalHost, rule: BLOCK|AFFILIATE|null, shortener, common, storeName, og: { status: OK|NONE|FAILED|OFF, imageUrl, title, storeName }, sameDeals: [{postId, title, dealPrice, shippingFee, createdAt}] }`.
- 할인율·제휴 강제·종료 판정은 서버가 정한다(화면은 미리보기만).
- 가젯 `HOT_DEALS` 설정(공동구매 가젯 ⚙ 방식): `boards`(핫딜 게시판 slug 목록, 비우면 모든 핫딜 게시판) · `sort` `LATEST`(기본)·`POPULAR`·`ENDING_SOON` · `count` 1~10(기본 5). 진행 중만(의심 제외), 비면 영역을 그리지 않음, 「핫딜 더 보기 →」. 줄 = 목록 줄과 같은 모양.

### 핫딜 설정 (`deal_settings` 한 행, 바뀐 칸 감사)
| 구획 | 칸 | 범위 | 기본 |
|---|---|---|---|
| 종료 요청 | `endRequestThreshold` | 1~20 | 3 |
| 종료 요청 | `endGraceHours` | 0~168(0=자동 종료 안 함) | 12 |
| 종료 요청 | `endRequestDailyLimit` | 1~100 | 20 |
| 종료 요청 | `endRequestPerAuthorLimit` / `endRequestPerAuthorDays` | 0~20(0=없음) / 1~30 | 3 / 7 |
| 종료 요청 | `keepMaxCount` | 0~10(0=없음) | 2 |
| 글쓰기 | `memberDailyPostLimit` | 0~50(0=없음) | 5 |
| 글쓰기 | `sameStoreIntervalMinutes` | 0~1440(0=없음) | 10 |
| 글쓰기 | `autoExpireDays` | 0~365(0=끔) | 30 |
| 가격 | `priceMaxWon` | 1,000,000~1,000,000,000 | 100,000,000 |
| 가격 | `discountWarnPercent` | 50~99 | 90 |
| 인기 표시 | `hotLikeThreshold` | 1~100 | 10 |
| 위장 홍보(회원) | `suspicionMinPosts` / `suspicionWindowDays` | 0~50(0=끔) / 1~90 | 3 / 7 |
| 위장 홍보(판매처) | `storeSuspicionMembers` / `storeSuspicionWindowDays` / `storeSuspicionOtherRatioPercent` | 0~20(0=끔) / 1~90 / 0~100 | 3 / 30 / 20 |
| 상품 이미지 | `ogFetchEnabled` | 켬/끔 | 켬 |
| 제휴 링크 | `affiliateMode` | `MEMBER`·`SITE` | `MEMBER` — 바꿀 때 확인 창(「바꾸면 새 글부터 개인 제휴 링크를 받지 않고, 구매 링크는 사이트 제휴로 바뀌어요」 + 영향 글 수: 진행 중 개인 제휴 글 N · 그중 사이트 제휴 몰 M) |
| 제휴 링크 | 사이트 제휴 몰 표 `siteAffiliateStores[]` | `SITE` 일 때만 보임 | 비어 있음 |
- 보상 포인트·하루 지급 횟수는 포인트 정책(`dealEndRequestPoints`·`dealEndRequestDailyLimit`) 소유 — 핫딜 설정은 읽기만 + 「포인트 정책 열기」.
- OG 보안 값(시간 제한·크기·동시 수)은 설정 화면에 두지 않는다(서버 규칙 1).

## 화면 경로
- user-web
  - DEAL 게시판 목록 `/boards/{slug}` — 가격 줄 목록형. 정렬 최신·인기·마감 임박, 「종료 숨기기」, 말머리, 쇼핑몰 칩(`?store=`, 상위 8몰 + 전체). 목업 게시판 slug `hotdeal`.
  - 핫딜 쓰기·고치기 `/write?board=hotdeal` (`&from={postId}` = 끝난 딜에서 새 딜 올리기 — 링크·몰·이미지·제목 미리 채움, 가격·마감 비움). 모바일은 단계 입력.
  - 글 보기 `/posts/{id}` — 본문 위 딜 카드.
  - 가젯 `HOT_DEALS` — 홈·가젯 페이지·게시판 레이아웃.
- admin-web 핫딜 묶음(사이드바 「핫딜」, 핫딜 심사 다음)
  - 핫딜 글 `/hotdeals`(서랍에 딜 칸 수정 이력 탭) · 링크 규칙 `/hotdeals/domains` · 의심 후보 `/hotdeals/suspicions`(회원·판매처) · 핫딜 설정 `/hotdeals/settings`.
  - 홈·가젯 페이지·게시판 레이아웃 편집기에 `HOT_DEALS` 가젯과 ⚙ 설정. 신고 사건 서랍에 핫딜 조치 사유 2개 + [핫딜 종료].

## 필드·검증 (글쓰기)
- 상품 링크 필수 `http(s)`, ≤1000자, BLOCK 이면 등록 불가(`fields."deal.productUrl"`). **수정 저장 때는 늘 현재 규칙으로 재판정 — 차단이어도 저장은 허용하고 `warnings[]`**.
- 쇼핑몰 이름 필수 1~40자(판정 결과로 자동 채움, 고칠 수 있음).
- 제목: 「상품명만 적어요 · 쇼핑몰·가격은 아래 칸이 목록에 보여 줘요」. `[` 로 시작하거나 `원)` 로 끝나면 「자동으로 붙어요 — 지울까요? [지우기]」(막지 않음).
- 딜가 필수 0 이상 정수(원) = 쿠폰 적용 뒤 실제 결제가(배송비 제외). 정상가 선택, 있으면 딜가 이상. 딜가·정상가·배송비 ≤ `priceMaxWon`. 배송비: 무료(0) · 모름(null) · 금액.
- 가격 기준 선택지 `priceBasisKind` + 메모. **0원 딜이면 가격 기준 필수**, 할인율 ≥ `discountWarnPercent` 면 경고 + 가격 기준 필수. 해외 직구면 「관부가세 포함」 체크.
- 마감 선택(지금 이후 ~ 365일), 비우면 「`autoExpireDays`일 뒤 기간 종료로 바뀌어요」. 재고 기본 UNKNOWN.
- 제휴 표시: AFFILIATE(최종 호스트 포함)면 켜고 잠금, 아니면 글쓴이가 체크(단정형 문구).
- 이미지: OG 사본(없음·실패·꺼짐 안내), 글쓴이가 지우거나 직접 올린 이미지로 바꿈.
- 오류는 `400 VALIDATION fields."deal.*"` 로 다른 글 칸 오류와 함께. 한도: `429 DEAL_DAILY_POST_LIMIT`(하루 글 수) · `429 DEAL_SAME_STORE_TOO_SOON`(같은 판매처 간격, 남은 분).

## 권한·정책
- 관리자: 조회 `hotdeal.read`, 종료·재개·의심 후보 무시·설정 `hotdeal.manage`, 링크 규칙 `hotdeal.domain.manage`(지금 카탈로그 `featureReady=false` — 서버에서 켜야 함). 게시판 매니저·스텝은 그 게시판 핫딜 글의 종료·재개만.
- 회원: 작성자 종료(ACTIVE·FLAGGED → ENDED, AUTHOR), 작성자 되돌리기(D2 — 24시간 안 1번), 「아직 진행 중」(FLAGGED → ACTIVE, `keepMaxCount` 까지 — 「가격·재고를 고쳐 주세요」 안내). 재개는 운영진만(의심·요청 리셋). 재개했는데 기간이 지났으면 작성자에게 「마감을 고쳐 주세요」.
- 감사(모두 일반 분류): `HOTDEAL_END`·`HOTDEAL_REOPEN`·`HOTDEAL_DOMAIN_CREATE/UPDATE/DELETE`·`HOTDEAL_SUSPICION_DISMISS`·`HOTDEAL_SETTINGS_UPDATE`.

## 서버 단계 고정 규칙 (05 §4 — 서버 구현 때 **테스트로 막을 대상**)
1. **OG 가져오기 보안**: 기존 `OutboundTargetGuard`·`SafeHttpClient` 재사용, 사설망 늘 거절, 리디렉션 최대 3회·매 단계 재검사, `text/html` 만·1MB·`<head>` 까지, 연결 2s·읽기 4s·전체 6s, 전용 풀 동시 4, 회원 분당 10회, 캐시 24시간(실패 10분).
2. **최종 URL 판정**: 리디렉션 체인의 모든 호스트에 링크 규칙 적용(차단 우선 > 제휴), 응답 `finalHost`.
3. **같은 딜 키**: 스킴·호스트 소문자·www·기본 포트·조각·끝 `/` 제거 + 추적 파라미터 제거(`utm_*`·`fbclid`·`gclid`·`igshid`·`ref`·`tag`·`aff*`·`spm`·`_trkparms`·`sub_id`) + 규칙 `keyParams` 가 있으면 그 쿼리만 → 정렬 → SHA-256. 같은 딜 = 공개·진행 중(ACTIVE·FLAGGED)만.
4. 종료 요청 자격(쓰기 등급·인증·정지 아님), 중복 요청 409 는 한도를 쓰지 않음, 하루 = 한국 0시, 같은 작성자 기간 한도.
5. 보상: 의심 구간 요청자에게만, 종료 경로 셋(작성자·운영진·자동) 모두, 지급 하루 5회(포인트 정책).
6. 재고 `SOLD_OUT` 저장 = 작성자 종료. 탈퇴 작성자의 진행 중 딜 = 작성자 종료, 정지 회원의 딜은 그대로. 탈퇴 요청자의 요청 줄은 지우되 이미 의심이 된 상태는 유지.
7. 수정 저장 때 늘 현재 규칙으로 링크 재판정 — 차단이면 저장 허용 + `warnings[]` + 관리자 목록 「차단 도메인 진행 중」 거르기.
8. 집계(가젯·같은 딜·인기글)는 공개 글만, 인기글은 종료·기간 종료 제외(의심은 포함), 핫딜 게시판은 19금 끔 고정, 광고 숨김은 핫딜 가젯과 무관.
9. 알림: 회원 — 종료 요청 문턱·자동 종료·운영진 종료(사유)·자동 기간 종료 1회. 운영자 — 위장 홍보 후보(`HOTDEAL_SUSPECTED_PROMO` 예약 켬)·keep 한도 초과·링크 호스트 바뀜.
10. 2차 맞물림: 사업자 글은 종료 요청 받음·위장 홍보 집계 제외·인기글 제외·수정 이력 같은 표, `deal_settings` 같은 행에 2차 칸.
11. D2 되돌리기: AUTHOR 종료만, 종료 뒤 24시간 안, 글마다 1번(`409 DEAL_REOPEN_EXPIRED`·`DEAL_REOPEN_USED`). D4 `DEAL_FALSE_PRICE` 신고 = `PRICE_CHANGED` 요청 1건(같은 회원 1회, 자동 숨김 셈 제외).

## API 후보
- 공개(server/api)
  - `POST/PUT /api/posts` 본문 `deal?: DealInput { storeName, productUrl, normalPrice, dealPrice, shippingFee, couponNote, priceBasisKind, dutyIncluded, priceBasis, endsAt, stockState, affiliate, image }` · 응답에 `warnings: [{code, message}]`(예 `DEAL_LINK_BLOCKED`) · 글 상세·목록 줄에 `deal`(+ `viewCount`).
  - `GET /api/deals/link-check?url=&board=` · `GET /api/deals/prefill?from={postId}`(끝난 딜에서 새 딜).
  - `POST /api/posts/{id}/deal/end` · `POST /api/posts/{id}/deal/reopen`(작성자 되돌리기) · `POST /api/posts/{id}/deal/keep` · `POST /api/posts/{id}/deal/end-requests {reason}` · `GET /api/posts/{id}/deal/same`.
  - 게시판 목록 `?sort=latest|popular|ending&hideEnded=true&prefix=&store=` + 쇼핑몰 칩 목록(`stores: [{name, count}]` 상위 8).
  - 가젯 `HOT_DEALS` 의 `data`.
  - `GET /api/deals/{postId}/go` → **302** 외부 상품 주소(클릭 시점의 `affiliateMode`·사이트 제휴 몰로 변환, 클릭 집계, `rel="nofollow sponsored noopener"` 는 화면 링크에 그대로). 공개·진행 중이 아니면 원래 링크 또는 글로(서버 판단). `/api` 아래라 개발 프록시·실서버 라우팅을 따로 두지 않는다. 글 상세·목록 줄 `deal.buyUrl` 로 이 주소를 내린다.
  - 공개 설정 전달: 핫딜 게시판 응답에 핫딜 설정 공개 칸(`dealAffiliateMode`·`priceMaxWon`·`discountWarnPercent`·`autoExpireDays`) + link-check 응답 `affiliateMode`·`siteAffiliate`.
  - 오류: `429 DEAL_DAILY_POST_LIMIT`·`DEAL_SAME_STORE_TOO_SOON`·`DEAL_END_REQUEST_DAILY_LIMIT`·`DEAL_END_REQUEST_AUTHOR_LIMIT` · `403 DEAL_END_REQUEST_OWN`·`DEAL_END_REQUEST_NOT_ALLOWED`(자격) · `409 DEAL_END_REQUEST_EXISTS`·`DEAL_ENDED`·`DEAL_NOT_FLAGGED`·`DEAL_KEEP_LIMIT`·`DEAL_REOPEN_EXPIRED`·`DEAL_REOPEN_USED`.
- 관리자(server/admin)
  - `GET /api/admin/hotdeals?view&board&q&createdFrom&createdTo&blockedActive&page` (+ `counts`) · `GET /{postId}`(`endedByName`·`endReason`·`keptAt`·`keepCount`·`publicUrl`·`sameDeals`) · `GET /{postId}/revisions` → `[{id, at, actor: {kind: AUTHOR|MANAGER, name}, changes: [{field, before, after}], hostChanged}]` · `POST /{postId}/end {reason?}` · `POST /{postId}/reopen {reason?}` · `GET /api/admin/hotdeals/boards`.
  - `GET/POST/PUT/DELETE /api/admin/hotdeal-domains` — 줄 `{ domain, rule, common, keyParams: string[], note }` · `GET /api/admin/hotdeal-domains/impact?domain=` → 차단으로 바꿀 때 「진행 중 글 N건」.
  - `GET /api/admin/hotdeal-suspicions?status&kind=MEMBER|STORE` · `POST /{id}/dismiss {note?}` — 판매처 후보 줄 `{ kind: STORE, host, storeName, memberCount, otherRatioPercent, windowDays, posts[] }`.
  - `GET/PUT /api/admin/hotdeal-settings`(위 표) · `GET /api/admin/hotdeal-settings/affiliate-impact?mode=` → `{ memberAffiliateActive, siteStoreMatched }`.
  - 사이트 제휴 몰 `GET/POST/PUT/DELETE /api/admin/hotdeal-site-affiliates` — 도메인 유일(`409 HOTDEAL_SITE_AFFILIATE_DUPLICATE` + `fields.domain`), `fields.template`(`{url}` 없음)·`fields.query`·`fields.programName`. 감사 `HOTDEAL_SITE_AFFILIATE_CREATE/UPDATE/DELETE`, 방식 전환은 `HOTDEAL_SETTINGS_UPDATE` 에 바뀐 칸으로.
  - 신고 사건 조치 `action: DEAL_END`(핫딜 종료) + 사유 `DEAL_FALSE_PRICE`·`DEAL_HIDDEN_AD`.
  - 오류: `409 HOTDEAL_STATE_CHANGED`·`HOTDEAL_DOMAIN_DUPLICATE`(+`fields.domain`) · `404 HOTDEAL_DOMAIN_NOT_FOUND`·`HOTDEAL_SUSPICION_NOT_FOUND`.
- DB(shared 스트림 — 번호는 서버 세션이 develop 최신 확인 뒤): `post_deals` · `post_deal_revisions` · `deal_domains`(+`key_params`) · `deal_end_requests` · `deal_suspicions`(+`kind`) · `deal_settings` · OG 이미지 사본(저장 위치·정리 배치).
- 배치(api): 의심 유예 지남 → 자동 종료 + 보상 · 마감 없는 딜 기간 종료 알림 1회 · OG 사본 90일 정리.
- 서버에서 같이 고칠 것: `AdminScreenRegistry` 핫딜 묶음 4줄(`hotdeals`·`hotdealDomains`·`hotdealSuspicions`·`hotdealSettings`, `hotdealReviews` 다음) · `Permission.HOTDEAL_DOMAIN_MANAGE` featureReady · `HomeGadgetKinds.HOT_DEALS` · `ReportReasons` 2개(핫딜 글에서만, 일반 글이면 400) · 공개 게시판 응답 `kind` · 관리자 글 목록 줄 `deal: boolean` · `AdminNotificationEvent.HOTDEAL_SUSPECTED_PROMO` 켬 + keep 한도·호스트 바뀜.

## 목업 구현 결과 (2026-11-01)
- 1차 커밋: user-web `eb08a133` · admin-web `730da368`. 타입 검사 두 앱 통과, 브라우저 확인은 개발자 몫.
- `?mock=` — user-web(앞에 `deal-`): `deal-empty`·`deal-error`·`deal-flagged`·`deal-ended`·`deal-expired`·`deal-sent`·`deal-blocked`·`deal-affiliate`·`deal-og-none`·`deal-og-failed`·`deal-same`·`deal-sprout`·`deal-guest`·`deal-unverified`·`deal-card`·`deal-gallery`·`deal-gadget-off`. admin-web: `empty`·`error`·`detail-error`·`slow`·`forbidden`·`conflict`·`stale`·`save-error`. 쉼표로 여럿. (보강 스위치는 아래 「보강 목업」.)
- 목업 글: `/posts/mock-deal-mine`(내 글)·`mock-deal-2`·`mock-deal-3`(같은 딜·제휴)·`mock-deal-6`(의심)·`mock-deal-7`·`8`(종료)·`mock-deal-9`(기간 종료). 목업 게시판 `hotdeal` 은 실서버 같은 slug 를 가린다.
- 화면이 새로 정한 것(2026-11-01 확정)
  - 게시판 응답 `kind` 없으면 옛 칸 `dealEnabled` 로 판정(기존 핫딜 광고 자리 유지용).
  - 목록 차례 진행 중 → 의심 → 끝남, 마감 임박은 마감 없는 글 뒤.
  - 고치기: ENDED 만 딜 칸 읽기 전용, EXPIRED 는 고칠 수 있게 두고 「마감을 고쳐 주세요」. 처음과 같은 마감은 다시 검사 안 함. 딜 칸은 바꿨을 때만 보냄.
  - 끝난 딜 카드에도 [구매하기]는 남김(알리기·종료만 감춤). 쓰기의 같은 딜 링크는 새 탭.
  - 종료 요청 응답 = 새 `PostDeal`.
  - 관리자 상태 우선순위 ENDED > EXPIRED > FLAGGED > ACTIVE. [종료]는 진행·의심·기간 지남, [재개]는 ENDED 만(의심·요청 리셋). 사유·메모 선택 200자.
  - 내 포인트 내역은 서버 `reason` 문구를 그대로 보이므로 「핫딜 종료 알림 보상」은 서버 번들에.
- 못 한 것: 관리자 가젯 편집기 미리보기(user-web iframe 이라 user-web 쪽이 그림) · 내비게이션 메뉴에 목업 게시판 · 핫딜 목록의 테마 슬롯 등록 · 목업 글 댓글 쓰기(실서버로 가서 실패).

### 보강 목업 (05 §2, 커밋 user-web `a3c56c04` · admin-web `515268fb`)
- 새 `?mock=` — user-web: `deal-daily-limit`·`deal-store-interval`·`deal-keep-limit`·`deal-reopen`·`deal-reopen-expired`·`deal-reopen-used`·`deal-link-changed`·`deal-price-edited`·`deal-save-warning`·`deal-redirect`·`deal-og-off`·`deal-request-denied`·`deal-author-limit`(`deal-ended` 는 이제 1시간 전 종료라 되돌리기도 보임). admin-web: `revisions-empty`·`revisions-error`·`link-changed`·`impact-zero`·`store-suspicion`·`report-deal`.
- 목업 글 13~19 추가(해외 직구 HOT·임박, 0원 회원가, 품절 종료, 몰 11개, `coupa.ng` 리디렉션). 관리자 시드 `cheap-robot.shop` 차단(진행 중 2건), 판매처 후보 3건, 이력 있는 글 6건.
- 화면이 새로 정한 것(2026-11-01 확정)
  - prefill 응답 `DealPrefill { fromPostId, title, storeName, productUrl, image }`.
  - 오류 `extras`: `DEAL_DAILY_POST_LIMIT {limit}` · `DEAL_SAME_STORE_TOO_SOON {remainingMinutes, intervalMinutes, retryAfterSeconds}` · `DEAL_END_REQUEST_AUTHOR_LIMIT {limit, days}`. 칸 오류 키 `fields."deal.priceBasisKind"`.
  - `?store=` 값은 쇼핑몰 이름, 칩 개수는 말머리·종료 숨기기 적용 뒤·몰 거르기 전.
  - 「내일 마감」 = 기기 달력 내일이고 12시간 넘게 남음. (고치기 차단 경고·가격 수정 셈·공개 화면 상한은 「확정 가정」.)
  - 되돌리기 때 재고가 품절이었으면 「모름」으로. 가격 허위 신고(204) 뒤 화면이 카드 `endRequests` 를 직접 고침.
  - 가젯 「더 보기」 갈 곳: 서버 `more.to` → 설정 `boards[0]` → 첫 줄의 게시판.
  - 관리자: 이력 응답 `{ items, retentionMonths }`, 회원 후보 판매처 칸 `domain` → **`host`**(판매처 후보와 맞춤), 판매처 후보 글은 상세 `DealSuspicionDetail.posts`(≤30) + `members[]`(회원별 이 판매처 글 수·다른 핫딜 수), 목록 `kindCounts`, 핫딜 목록 줄 `blockedHost`·응답 `blockedActiveCount`, impact `{ domain, activeCount, samples[≤3] }`, 설정 응답 `rewardDailyLimit`(읽기 전용), `keyParams` 최대 10개·추적 파라미터 거절(`fields.keyParams`).
  - 신고 사건 `ReportCaseItem.deal?: { view }`(핫딜 글 여부·딜 상태 — 없으면 목업이 핫딜 사유 신고 유무로 추정), `DEAL_END` 처리 글 1~200자(작성자에게 가는 종료 사유, 글은 숨기지 않음), 일반 글이면 `409 REPORT_ACTION_INVALID`.
- 못 한 것: 종료 요청 자격 미달을 미리 보이지 못함(보는 사람 자격 칸 없음 — 누른 뒤 토스트) · 하루 글 한도·같은 몰 간격은 스위치로만 · 되돌리기 남은 시간 매분 갱신 안 함 · 회원 관리는 주소로 회원을 못 열어 「회원 제재로 가기」는 `/members` 까지 · 목업 [핫딜 종료] 신고 조치는 새로고침하면 사라짐.

### 제휴 방식 목업 (D5, 커밋 user-web `de9764d0` · admin-web `59cca827`)
- 새 `?mock=` — user-web: `deal-site-affiliate`(SITE — 쓰기 체크 숨김·개인 제휴 링크 칸 오류·쿠팡·11번가 글 「제휴」) · `deal-site-affiliate-nostore`(SITE 인데 사이트 제휴 몰 없음 → 제휴 표시 없음). admin-web: `site-affiliate`(SITE 로 시작) · `site-affiliate-empty`(몰 표 빔) · `affiliate-impact-zero`(영향 0건), 기존 `conflict` 는 몰 저장에도(409), `forbidden` 이면 몰 표 보기만.
- 화면이 새로 정한 것(2026-11-01 확정)
  - `siteAffiliate` 가 있으면 쓰기에 단정형 안내 「구매 링크는 로보얌 제휴 링크로 바뀝니다」. (`affiliateMode` 받는 곳·고치기 규칙은 「확정 가정」.)
  - SITE 에서 개인 제휴 링크 칸 오류는 판정 즉시 보임. 서버는 `400 VALIDATION fields."deal.productUrl"` 또는 단독 코드 `DEAL_MEMBER_AFFILIATE_OFF` 둘 다 같은 칸에.
  - **구매 링크 주소 결정(2026-11-01)**: `/api/deals/{postId}/go`(302). 목업은 그 주소를 받는 서버가 없어 `buyUrl` 에 저장된 상품 링크를 바로 넣는다(`// REAL:` 주석에 실주소).
  - 관리자: 방식 선택은 초안에 들어가고 **[저장] 때 확인 창**(방향별 문구 + 영향 글 수 + 켠 몰 0개 경고 + 함께 저장할 다른 칸 수). 사이트 제휴 몰 표는 초안이 SITE 면 저장 전에도 보이고 바로 저장(설정 저장과 별개). impact 응답 `{ mode, memberAffiliateActive, siteStoreMatched, siteStoreActive(켠 몰로 가는 진행 중 글 전체) }`. 상세·링크 규칙 목록 응답 `affiliateMode`. 몰 줄에 `updatedBy`·`updatedAt`. 변환: TEMPLATE 은 `{url}` 자리에 `encodeURIComponent(원래 주소)`, QUERY 는 쿼리를 붙이되 같은 이름이면 바꿈. 틀 ≤1000자·쿼리 ≤300자, 새 몰은 켠 채로, 켬/끔은 줄 전체 PUT, 몰 표 조회·편집 `hotdeal.manage`. 몰 판정은 최종 호스트 우선·접미사·긴 도메인 우선·켠 몰만. 코드 `404 HOTDEAL_SITE_AFFILIATE_NOT_FOUND`, `fields.affiliateMode`.
- 서버 후보: `deal_settings.affiliate_mode`, 사이트 제휴 몰 표, 클릭 때 변환(`SiteAffiliateLinker` 후보)·집계.

## 서버 구현 결정 (2026-11-01)
- OG 사본(D1 정리): 가로 600px JPEG ≤300KB·투명 배경 흰 바탕, WebP 원본은 ≤300KB·가로 ≤1200 이면 메타데이터만 떼어 그대로·넘으면 「없음」 — 의존성 없이 WebP 인코딩 불가라서, 인코딩은 `DealOgImageEncoder` 한 곳(나중에 WebP 로 바꿀 자리).
- `/api/deals/{postId}/go`: 글 없음·비공개·지금 차단 호스트 → 302 `/posts/{postId}`, 종료·기간 종료 → 저장된 원래 링크(변환·집계 없음), 진행 중 → 클릭 시점 방식으로 변환 + 집계(`click_count`, KST 하루 같은 방문자·같은 글 1번, 봇 UA 제외).
- 보상 원장 ref = 종료 요청 줄(`ref_type DEAL_END_REQUEST`) — 원장 유일 `(reason, ref_type, ref_id)` 로는 「ref = 글」이면 한 글의 여러 요청자에게 줄 수 없다.
- 관리자 알림 링크는 admin-web 서랍 쿼리에 맞춘다: 글 `/hotdeals?view=ALL&deal={postId}`, 의심 후보 `/hotdeals/suspicions?suspicion={id}`.
- 두 앱의 목업(`// MOCK:`·목업 저장소)과 위 「목업 구현 결과」의 `?mock=` 스위치·목업 글·목업 게시판 `hotdeal` 은 **모두 지웠다**(실 API 경로를 가로채지 않게) — 그 절들은 화면 확정 기록으로만 남긴다.
- 커밋: 서버 `b3b1ed5e`(1단계)·`ce4a9ff9`(2단계)·`1ea5706f`(3단계), 화면 admin-web `0d82aca0`·user-web `707729dc`·`0e607d82`·`80bca201`.
- 게시판 매니저·스텝 종료·재개(사용자 결정 2026-11-01, 메모 4절): 공개 `POST /api/posts/{id}/deal/moderate/end {reason}`(1~200 필수)·`/moderate/reopen {reason?}` → 새 `PostDeal` — 운영진 종료·재개와 같은 길(shared `DealModeration`), 그 글 게시판의 매니저·스텝 활성 회원만(`403 DEAL_MODERATE_FORBIDDEN`, 요청마다 다시 판정) · 글 상세 `dealModeration {role, canEnd, canReopen} | null` · 감사 `HOTDEAL_END`·`HOTDEAL_REOPEN` 행위자 새 종류 `MEMBER`(V122 감사 CHECK 에 추가, 관리자 감사 조회는 회원 닉네임) + detail `boardId`·`role`·`reason` · 딜 줄 `ended_by_role`(OPERATOR·BOARD_MANAGER·BOARD_STAFF, V122) → 관리자 상세 `endedByRole`(+ AUTHOR)·`endedByName`(운영자 이름 / 회원 닉네임).
- 관리자 게시판 만들기(`POST /api/admin/boards`)가 `kind: "DEAL"` 도 받는다(직접홍보 `PROMO` 는 여전히 `400 fields.kind`, `groupCategoryId` 는 소모임만 — `400 fields.groupCategoryId`, 종류는 만든 뒤 못 바꿈). 설치 프리셋과 같은 shared `BoardCreator` 길이라 19금 끔·가젯 노출 금지 `POPULAR`·비회원 글쓰기 없음은 같고, 글쓰기 최소 자격만 게시판 칸에 **첫 등급 바로 위 등급**(보통 `REGULAR`, 등급이 하나 이하면 `STAFF`)으로 정해 만든다 — 만든 직후 상세를 그대로 저장해도 `board.policy.dealGrade` 에 걸리지 않는다. 레이아웃은 요청값(LIST·CARD·GALLERY). 마이그레이션 없음.

### 서버 구현 결과
- 1·2단계의 API 모양·빈칸 결정은 세션 공유 메모 `hotdeal-member-api.md` 0~3절(서버 DTO 가 정본). 2단계(2026-11-01): 작성자 종료·되돌리기·keep·종료 요청 API, 보상, 신고 연결, 가젯 `HOT_DEALS`, 인기글 제외, 회원 알림 5종, Java 배치 4개(`hotdeal-auto-end`·`hotdeal-expiry-notice`·`hotdeal-cleanup`·`hotdeal-suspicion`).
- 3단계(관리자)가 부를 shared 진입점: 운영진 종료 `DealEndService#endByManager(postId, actorId, reason)`(보상·작성자 알림 포함) · 운영진 재개 `DealEndService#reopenByManager(postId)`(의심·keep·요청 리셋 + 「마감을 고쳐 주세요」) · 결과 `Outcome`(`STATE_CHANGED` → `409 HOTDEAL_STATE_CHANGED`) · 알림 `DealNotices` · 탈퇴 `DealWithdrawal#withdraw`(탈퇴 API 가 생기면).
- 배치의 실행 서버: 계약은 「배치(api)」였지만 배치 인프라(`_backend-core.md` 「배치」)대로 작업은 shared `com.community.hotdeal.batch`, 예약·「지금 실행」은 관리자 서버(`roboyam.batch.enabled`)가 돌린다.
- 3단계(관리자, 2026-11-01): `/api/admin/hotdeals`(boards·목록·상세·revisions·end·reopen) · `hotdeal-domains`(+impact) · `hotdeal-suspicions`(+dismiss) · `hotdeal-settings`(+affiliate-impact) · `hotdeal-site-affiliates` · 신고 사건 `DEAL_END`·`deal: {view}` · 관리자 글 줄 `deal: boolean` · `AdminScreenRegistry` 4줄 · 감사 10개(모두 일반). 딜 보기 계산은 shared `DealViewAssembler`(공개·관리자 같은 판정), 담당 판정은 shared `DealModeration`. V122 에 신고 사건 조치 CHECK `DEAL_END` 추가(로컬 DB 미적용 확인). 공개 손질: 핫딜 쓰기 자격(회원·정지 아님·이메일 인증·새싹 아님)을 `BoardAccessPolicy.writeBlocked` 에 넣어 게시판 응답 `viewer.writeBlockedReason`·`/api/boards/writable` 이 미리 막고, 글쓰기 `GRADE_TOO_LOW` 에 `minGrade`·`minGradeLabel`(핫딜은 첫 등급 바로 위 등급).
- 3단계 결정(세션 정리): 게시판 매니저·스텝은 회원이라 관리자 앱에 들어오지 않는다 — 관리자 종료·재개 API 는 `hotdeal.manage` 만, 매니저·스텝 판정(그 게시판만)은 shared `DealModeration.Actor.boardMember` 에 두고, 공개 사이트가 매니저·스텝 API(`/deal/moderate/end`·`reopen`)로 부른다(2026-11-01 서버 — 화면 연결은 user-web). 신고 조치 `DEAL_END` 는 신고 처리 권한(`report.manage`)이 담당을 대신한다. 핫딜 설정 조회는 `hotdeal.read`(메뉴 항목은 `hotdeal.manage`).

## 미확정
- 「의심 구간」의 뜻(2단계 가정): 의심이 된 회차의 요청(문턱을 채운 요청 포함) + 의심인 동안의 요청, 「아직 진행 중」으로 반박된 회차는 보상하지 않음. 운영진이 재개한 딜에 같은 회원이 다시 요청하면 새 요청이라 다시 보상받을 수 있다.
- 글에 붙지 않은 OG 사본의 정리 기한 7일(2단계 가정). 위장 홍보 열린 후보가 조건에서 벗어나도 저절로 닫지 않음. 대형몰 `smartstore.naver.com` 이 common 이라 스마트스토어 판매자는 위장 홍보 집계에서 빠진다.
- 같은 딜 판정에 쿼리 문자열을 어디까지 남길지 — 규칙별 `keyParams` 로 정하되 기본 몰 목록은 운영하며 채움.
- 종료 요청 자격·하루 글 5건·같은 몰 10분·마감 없는 딜 30일은 추천안 채택값(바꿀 수 있음).
- 3단계 가정: 관리자 핫딜 목록은 지운 글(예약 삭제 지남 포함)을 빼고 숨긴 글은 넣는다 · 링크 규칙 `note` 100자(DB 200).
- 매니저·스텝 API 가정(2026-11-01): 대상 판정은 다른 공개 상태 API 와 같아 숨긴 글은 `403 POST_HIDDEN`(숨긴 글의 딜은 관리자 앱에서) · 관리자 감사 조회의 행위자 이름 검색은 지금 운영자 이름만 찾는다(회원 행위자는 ID 로 찾기) · admin-web 감사 화면 타입 `AuditActorType` 에 `MEMBER` 추가는 화면 몫.
- `coupa.ng` 가 쿠팡 공식 단축 주소인지(지금은 제휴 시드). 사이트 제휴 몰 표 첫 줄들(어느 몰·프로그램과 계약할지)은 운영하며 채움 — 시드 없음.

## 법무 확인 (05 §5)
- 제휴 표시 문구·위치(지침 원문, 플랫폼 배지가 작성자 의무를 채우는지).
- OG 이미지 사본의 저작권·삭제 요청 절차.
- 「판매자가 아님」 고지와 이용약관 핫딜 조항.
- 회원 글 수정 이력 6개월 자율 보존 표기.
- 위장 홍보 집계에 IP·기기 정보를 쓸지(1차는 안 씀).
- 0원 딜·체험단 모집이 광고인지.
- **사이트 제휴(D5)**: 사이트 제휴일 때 표시 의무자 = 로보얌(표시 문구·위치), 각 제휴 프로그램 약관이 커뮤니티 게시글 링크 변환·재작성을 허용하는지, 회원이 올린 링크를 사이트 제휴로 바꾸는 것에 대한 이용약관 고지.
