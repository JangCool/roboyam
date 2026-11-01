# 사업자 핫딜 게시(2차) 계약 — 초안

**상태: 범위 확정 2026-11-02(사용자 「추천안대로 진행」) · 두 앱 화면 목업 완료(서버 없음) — 화면 확정 대기.** 신청·심사·결제·예약 흐름은 `biz-application-contract.md` 가 정본이고, 핫딜 글·딜 칸·종료 요청·링크 규칙은 `hotdeal-contract.md`(회원 핫딜 1차) 가 정본이다. 이 문서는 **예약된 사업자 핫딜이 실제 글로 보이는 고리**만 다룬다.

## 의도
- 돈 받은 만큼 실제로 보여 준다: 예약 확정된 `HOTDEAL_POST` 건은 **시작 시각에 핫딜 게시판에 사업자 글로 올라가고**, 노출을 산 건은 그 글이 핫딜 노출 두 자리에 보인다.
- 그 최소 범위로 V121 에서 닫은 세 상품(`HOTDEAL_POST`·`HOTDEAL_TOP`·`HOTDEAL_CATEGORY`) 신청을 다시 연다.

## 사용자 결정 (2026-11-02 — 범위 제안 추천안 채택)
1. **게시 실패**(시작 시각에 게시판 꺼짐·링크가 지금 규칙으로 차단·사업자 정지 등): 글을 만들지 않고 처리 기록 `POST_FAILED` + 운영자 알림. 운영자는 기존 [취소(전액 환불)] 로 정리. 「다시 게시」 단추는 미룸. 사업자 화면은 「운영팀이 확인 중이에요」.
2. **종료 요청은 받는다**(계약 「2차 맞물림」). 종료 요청 자동 종료·사업자 직접 종료 모두 **환불 없음**, 노출 자리도 같이 빠진다(기간은 흐름).
3. **노출 자리 이름**: `HOTDEAL_TOP`(사업자 값 `HOME_TOP`)은 실제로 **핫딜 게시판 첫 쪽 위(말머리 안 고름)** 에 나온다 → 화면 이름 「홈 상단」 → **「핫딜 게시판 상단」**(값·상품 이름은 그대로). 홈 화면 노출은 미룸.
4. **말머리 상단**: 신청 때 **글의 말머리**를 고르고, `HOTDEAL_CATEGORY` 는 그 말머리 목록 위에 나온다(말머리 목록은 핫딜 게시판 기본 말머리 세션 결과를 따른다).
5. **이미지**: 직접 올리기 1장 선택(기존 `POST /api/business/ad-images` 재사용), 없으면 기본 아이콘. OG 자동 가져오기는 미룸.
6. **게시 뒤 수정 없음**: 사업자는 종료만. 가격이 바뀌면 종료 뒤 새 신청.

## 규칙 (서버 단계에서 테스트로 막을 대상 — 커머스 18절)
1. 게시 = 배치가 `SCHEDULED` 이고 `start_at ≤ 지금` 인 `HOTDEAL_POST` 건 중 글이 없는 것을 골라 글 + `post_deals(source=BUSINESS)` 를 만든다. 신청 건 ↔ 글 1:1(유일 — 다시 돌아도 두 번 만들지 않음). `onScheduled` 는 시작이 이미 지난 건만 바로 게시(아니면 배치 몫).
2. 작성자 = 신청한 사업자 회원, 게시판·말머리 = 신청 값, 상호 = 신청 건 스냅샷. 딜 마감(`ends_at`) = 신청 기간 끝 → 끝 처리는 기존 「기간 종료(EXPIRED)」 계산(배치 없음).
3. 게시 직전에 다시 판정: 게시판 있음·켬·핫딜 종류, 링크(회원 핫딜 `DealLinkJudge` — 차단이면 실패), 사업자 승인·정지 아님. 하나라도 아니면 규칙 1 대신 `POST_FAILED`(사유 `BOARD_CLOSED`·`LINK_BLOCKED`·`BUSINESS_SUSPENDED`·`OTHER`) + 운영자 알림, 글 없음.
4. `onClosed`(취소·환불·긴급 내림·사업자 정지 내림) = 글 숨김(`POST_HIDDEN`), 되돌리기 = 다시 보임(`POST_RESTORED`). 숨김은 글 삭제가 아니다.
5. 사업자 글 규칙: 종료 요청 받음 · 위장 홍보 집계 제외 · 인기글 제외 · 「광고」 표시 + 상호 · 게시 뒤 사업자 고치기 없음(공개 글 수정 API 가 `source=BUSINESS` 를 거절) · 운영진 종료·재개는 회원 글과 같음.
6. 사업자 종료(`DEAL_ENDED_BY_BUSINESS`)·종료 요청 자동 종료(`DEAL_AUTO_ENDED`)는 신청 건 상태를 바꾸지 않고 **환불이 없다**. 노출 자리는 딜이 진행 중(ACTIVE·FLAGGED)이고 글이 보일 때만 나간다.
7. 노출: 공개 `/api/ads` 의 `HOTDEAL_TOP`·`HOTDEAL_CATEGORY` = 노출 줄이 예약·기간 안 + 그 신청 건의 글이 진행 중·공개 → `deal` 을 글에서 채움. `HOTDEAL_CATEGORY` 는 요청 `prefix` 가 글의 말머리와 같을 때만. `board` 는 글의 게시판과 같을 때만.
8. 신청 검사 교체: 링크는 회원 핫딜 링크 규칙(`deal_domains`)으로, 재고 값은 `IN_STOCK`·`LOW`·`UNKNOWN`(옛 PLENTY·LIMITED 는 이미 들어온 건만 읽기), `boardId` 필수(받는 핫딜 게시판), 게시판에 말머리가 있으면 `prefixId` 필수, `CATEGORY_TOP` 이면 `prefixId` 필수.
9. 다시 열기: 새 마이그레이션이 세 상품 `accepting=true`(V121 고치지 않음).
10. 게시 실패(`POST_FAILED`) 건은 시작 시각이 지났어도 운영자 취소 = **전액 환불**(`FULL_AUTO` — 온라인 PG 취소, 수동은 송금 대기). 실패 건은 노출도 나가지 않는다.

## 데이터 모양 (두 앱 목업 공통)
```
BizDealBoard { id, slug, name, prefixes: { id, name }[] }         // GET /api/business/deal-products 의 boards[]
deal(신청 본문) += boardId, prefixId|null, imageFileId|null, stock: IN_STOCK|LOW|UNKNOWN
BizDealPost {
  status: WAITING | POSTED | FAILED | HIDDEN, postId|null, boardSlug, boardName, prefixName|null, publishAt,
  view: ACTIVE|FLAGGED|ENDED|EXPIRED|null, endedBy: AUTHOR|MANAGER|AUTO|null,
  endRequests: { total, bySoldOut, byPriceChanged, byLinkDead }|null,
  failReason: BOARD_CLOSED|LINK_BLOCKED|BUSINESS_SUSPENDED|OTHER|null,
  placement: { product: HOTDEAL_TOP|HOTDEAL_CATEGORY, status: WAITING|SHOWING|STOPPED|ENDED, stopReason: DEAL_ENDED|POST_HIDDEN|null }|null
}                                                                   // 신청 건 상세(공개·관리자)의 post
PostDeal += source: MEMBER|BUSINESS, business: { name }|null        // 공개 글·목록 줄
관리자 핫딜 글 줄·상세 += source, business: { name, applicationId }|null, 거르기 source=MEMBER|BUSINESS
처리 기록 action += POSTED, POST_FAILED, POST_HIDDEN, POST_RESTORED, DEAL_ENDED_BY_BUSINESS, DEAL_AUTO_ENDED
```

## 화면 경로
- user-web: `/business/deals/new`(게시판·말머리·이미지·재고·정책 안내) · `/business`(핫딜 카드 열림) · `/business/applications/[id]`(「게시된 글」 칸 + [품절로 종료]) · 핫딜 게시판 목록·글 보기(사업자 글 「광고」·상호, 고치기 없음) · 핫딜 노출 두 자리.
- admin-web: `/hotdeal-reviews`(미리보기 게시판·말머리·이미지, 「게시」 칸, 실패 안내) · `/hotdeals`(출처 거르기·「사업자·광고」·[핫딜 심사에서 열기]) · `/ads` 핫딜 탭 이름.

## API 후보
- 공개: `GET /api/business/deal-products`(+`boards`) · `POST /api/business/applications/deals`(본문 `deal` 칸 추가) · `GET /api/business/applications/{id}`(+`post`) · **`POST /api/business/applications/{id}/deal/end`** → `BizDealPost`(409 `DEAL_ENDED`) · `GET /api/ads`(핫딜 `deal` 채움) · 공개 글·목록 `deal.source`·`deal.business`.
- 관리자: 신청 건 상세 소재 `board`·`prefix`·`imageUrl` + `post` · `GET /api/admin/hotdeals?source=` · 운영자 알림 `HOTDEAL_POST_FAILED`(`hotdeal.manage`, 링크 `/hotdeal-reviews?id=`).
- 배치: `biz-hotdeal-publish`(1분, 규칙 1~3). 사업자 알림(`BIZ_APPLICATION` 덧붙임 kind): 게시됨·게시 실패·종료 요청 문턱(기존 딜 알림 재사용 가능)·자동 종료.

## user-web 목업 결과 (2026-11-02, 타입 검사 통과 · 브라우저 확인은 개발자 몫)
- 스위치(없으면 실제 동작 그대로, 목업 ID `mock-bizdeal`·`mock-bizdeal-2` 는 스위치 없이도 목업):
  - `/business?mock=bizdeal-open`(핫딜 카드 열림) · `/business/deals/new?mock=bizdeal-open` + `bizdeal-one-board`(게시판 1개 — 칸 숨김)·`bizdeal-no-prefix`(말머리 없음 — 말머리 상단 못 고름)·`bizdeal-submit-fail`(500). 제출 → 목업 건 「심사 중」.
  - `/business/applications/mock-bizdeal?mock=` `bizdeal-review`·`bizdeal-before`·`bizdeal-posted`(기본)·`bizdeal-flagged`·`bizdeal-ended`(자동)·`bizdeal-ended-me`(사업자)·`bizdeal-ended-manager`·`bizdeal-expired`·`bizdeal-failed`(+`bizdeal-fail-link`·`-suspended`·`-other`)·`bizdeal-hidden`(긴급 내림)·`bizdeal-hidden-canceled`(취소·환불)·`bizdeal-no-placement`·`bizdeal-end-error`(409).
  - 핫딜 목록 `/boards/<핫딜 slug>?mock=bizdeal-board`(첫 쪽·거르기 없을 때 맨 위 사업자 글 2줄) · 글 보기 `/posts/mock-bizdeal`·`mock-bizdeal-2`(`?board=slug`, `bizdeal-mine` = 보는 사람이 그 사업자) · 노출 `?mock=bizdeal-placement`(딜 2건, 목업 노출·클릭은 서버로 안 보냄).
- 화면이 새로 정한 것:
  - 더한 칸(모두 선택): 신청 건 소재 `deal.imageUrl` · `BizDealPost.autoEndAt`(의심 자동 종료 시각 — 없으면 「12시간 안에」) · `BizDealPost.endedAt`. 옛 응답 호환으로 `DealProduct.boards`·`detail.post`·`PostDeal.business` 는 없을 수 있음(게시판이 없으면 칸 숨김).
  - 재고 `IN_STOCK|LOW|UNKNOWN`, 옛 `PLENTY|LIMITED` 문구는 남김.
  - 사업자 글 작성자 자리 = `deal.business.name` + 「사업자」 뱃지(프로필 링크·등급·장식 없음, 접기·차단 판정은 원래 `memberId`). 「광고」 배지는 목록 줄·카드·가젯·딜 카드 배지 맨 앞, 카드 첫 줄 「광고 — {상호}에서 비용을 내고 올린 사업자 핫딜이에요」.
  - 사업자 글은 작성자에게도 **[고치기]·[지우기] 둘 다 숨김**(⚠ 지우기는 사용자 확인 필요) · 끝나도 [이 상품 새 딜 올리기] 없음 · 작성자 [딜 종료]는 남기고 확인 문구 「환불 없음 · 노출도 멈춤」 · 작성자 되돌리기(D2) 없음(`reopenUntil` null).
  - 신청 화면: 게시판·말머리·이미지는 ③단계. 말머리 없는 게시판이면 「말머리 상단」 막고 안내, 게시판을 바꾸면 말머리 비움(말머리 상단이었으면 「노출 없음」으로). 칸 오류 `fields."deal.boardId"`·`"deal.prefixId"` 는 ③단계로.
  - 신청 건 상세: 게시 실패면 상태 칸 제목 「운영팀이 확인 중이에요」. [품절로 종료] 성공은 `post` 만 바꿈(처리 기록은 다시 열 때), 409 `DEAL_ENDED` 는 토스트 + 다시 읽기.
  - 목업 글 `hot=false`(인기 제외), `buyUrl` 은 상품 주소 그대로(실서버는 `/api/deals/{postId}/go`).
- 못 한 것: 글 보기 목업은 게시판 정보를 실서버에서 받음(그 slug 핫딜 게시판 필요) · 신청 화면은 승인 사업자 계정 필요 · `/business/applications` 목록·센터 홈 「최근 신청 건」에는 목업 건 없음(주소로 직접) · 목업 상태는 SPA 메모리(새로 고침하면 처음으로).

## admin-web 목업 결과 (2026-11-02, 타입 검사 통과 · 브라우저 확인은 개발자 몫)
- 스위치 — `/hotdeal-reviews?mock=` `bizdeal-before`·`bizdeal-posted`·`bizdeal-ended`·`bizdeal-auto-ended`·`bizdeal-expired`·`bizdeal-failed`·`bizdeal-hidden`·`bizdeal-refunded`·`bizdeal-all`(목업 건은 제 상태 큐 맨 위, 번호 `mock-bizdeal-<이름>` — 예 `/hotdeal-reviews?mock=bizdeal-all&queue=LIVE&id=mock-bizdeal-failed`) · `/hotdeals?mock=bizdeal-board`(또는 `bizdeal-all`, 사업자 글 4줄: 진행·사업자 종료·자동 종료·기간 지남). 스위치가 없으면 실제 동작 그대로, 새 칸이 없으면 감춤. 도구 `app/composables/common/mock/mock-util.ts`(admin-web 에 다시 둠).
- 화면이 새로 정한 것:
  - **게시 실패 건 취소**: 「게시」 칸 안에만 [취소(전액 환불)] → 기존 `POST …/cancel`. ⚠ 서버는 지금 운영자 취소를 「시작 전 예약」만 받는다 → **`POST_FAILED` 건은 시작이 지나도 운영자 취소 = 전액 환불(`FULL_AUTO`)** 로 받아야 한다(규칙 10 으로 더함). 확인 창 문구 `cancelPostFailedBody`.
  - 공개 글 링크는 화면이 공개 사이트 origin + `/posts/{postId}` 로 만든다(모르면 감춤) — 서버가 `publicUrl` 을 주면 그쪽으로.
  - 상태 조합(가정): 사업자 종료·자동 종료 = 신청 건 `RUNNING` + 글 `ENDED` + 노출 `STOPPED/DEAL_ENDED` · 기간 끝 = `ENDED` + `EXPIRED` + 노출 `ENDED` · 게시 실패 = 노출 `WAITING` · 긴급 내림 = `TAKEN_DOWN` + 글 `HIDDEN` + 노출 `STOPPED/POST_HIDDEN`.
  - 처리 기록 note: `POST_FAILED` = 실패 사유 코드, `DEAL_ENDED_BY_BUSINESS` = 사업자가 적은 글(있으면), 나머지 없음.
  - 핫딜 글 출처 = 줄 `source` 우선(`row.source ?? deal.source`), 출처 거르기는 서버가 줄에 `source` 를 줄 때(또는 주소에 `source=`)만 보임. 재고는 옛 `PLENTY|LIMITED` 도 같은 문구로 읽음.
- 못 한 것: [핫딜 심사에서 열기]로 들어가면 상세는 열리지만 목록 탭은 「심사 대기」 그대로(큐를 모름).

## 서버 규모 어림 (범위 제안 때)
약 25~30파일·3,000~3,500줄(테스트 15~25개 포함) — 회원 핫딜 1차(249파일·약 15,000줄)의 1/5 정도.

## 뒤로 미룸
상단 구획(24시간) · 끌올 · 쿠폰 코드 · 게시 뒤 사업자 수정·수정 이력 화면 · 사업자용 노출·클릭 리포트 · 게시된 글에 노출만 따로 붙이기(`HOTDEAL_PLACEMENT` 공개 신청) · 「내 핫딜」 목록 페이지 · OG 자동 이미지 · 홈 화면 노출 · 「다시 게시」.

## 미확정
- 사업자 글 게시 시각이 목록 차례에서 어디에 서는지(가정: 글 시각 = 게시 시각, 회원 글과 같은 최신순).
- 종료 요청 문턱·keep 한도를 사업자 글에도 같은 값으로 쓸지(가정: 같은 `deal_settings`).
- 하루 한도·같은 판매처 간격(회원 규칙)은 사업자 글에 적용하지 않음(가정 — 심사·결제가 대신함).
- 게시 실패 뒤 운영자가 원인을 고치면 다시 게시할 길(지금은 취소·환불만).
