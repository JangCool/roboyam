# 광고·사업자 핫딜 일시 정지 / 중단 계약 (화면 확정 2026-11-02)

**의도**: 진행 중인 날짜형 자리(배너·한 줄 광고·핫딜 노출)와 사업자 핫딜 글을 광고주 사정으로 **잠깐 멈추거나(일시 정지)**, **끝내고 남은 날을 돌려받게(중단)** 한다. 지금까지는 「환불 요청」(승인 전까지 노출 계속)·운영자 「긴급 내림」(사고용)뿐이었다.
**정본·근거**: 사용자 확정안 2026-11-02(A·B·C) + 화면 확정·중도 해지 수수료 결정(2026-11-02). 신청·결제·환불 흐름 정본은 `biz-application-contract.md`, 긴급 내림·되돌리기는 `ad-contract.md`, 사업자 핫딜 글은 `biz-hotdeal-contract.md`. 이 문서는 정지·중단만.
**커머스 규칙 적용**: 돈이 움직이므로 `docs/domain/commerce.md` 18절 — 아래 「규칙」은 모두 테스트로 막는다.

## 화면 경로
- user-web `/business/applications/{id}`(일시 정지·다시 켜기·중단하고 환불 요청, 남은 정지 횟수, 환불 예상액 내역) · 글 보기 딜 카드 사업자 [일시 정지]·「중단·환불은 신청 건에서 ›」 · 정지·중단된 사업자 핫딜 글 직접 진입 안내(404 아님) · 상태 Tag「일시 정지」「정지한 채 끝남」「중단」(까닭 없는 내림은 「내려짐」) · 신청 화면 환불 규정 동의 문구(수수료).
- admin-web `/ad-reviews`·`/hotdeal-reviews`(정지·중단 표시, 운영자 대신 정지·다시 켜기·중단 + 요청 경로·메모, 환불 심사 내역) · `/ads`(「정지 중」「중단」 상태·서랍) · `/hotdeals`(「판매자 정지 중」「판매자 중단」 + 숨긴 기록) · `/ads/placements?tab=refund`(중도 해지 수수료 비율).

## 상태 모델 (세션 정리 결정)
- 정지·중단은 **기존 `TAKEN_DOWN` + 새 내림 까닭**이다(TAKEN_DOWN 이 이미 자리 유지·공개 노출 제외·핫딜 글 숨김 고리·되돌림 고리를 가진다).
  - `ADVERTISER_PAUSE` — [다시 켜기] → `SCHEDULED`(보상 없음, 끝 시각 그대로).
  - `ADVERTISER_STOP` — 되돌리기 없음. 일할 환불 `REQUESTED`(종류 **`STOP_PRORATED`**)가 같이 생긴다(환불액 0원이면 환불 줄 없음). 승인 → `CANCELED`(까닭 `ADVERTISER_STOP` 은 남김 — 화면 문구), 거절 → `TAKEN_DOWN` 그대로.
  - 기존 `EMERGENCY`·`SUSPENDED` 그대로. 관리자 [되돌리기](연장/일할 보상)는 `EMERGENCY` 만 — 광고주 요청 건엔 보상 선택지 없음.
- 정지·중단할 수 있는 것: **진행 중**(`SCHEDULED`, 시작 ≤ 지금 < 끝)·열린 환불 없음. 사업자 핫딜 글(`HOTDEAL_POST`)은 글이 게시(`POSTED`)되고 딜이 `ACTIVE`·`FLAGGED` 일 때만(품절로 종료·기간 종료·게시 실패·운영 숨김 뒤에는 없음). 신청 건 단위로 글 + 그 건의 노출 자리를 함께 멈춘다.
- 시작 전은 지금처럼 [취소·환불 요청] = **전액 환불**(수수료 없음). 진행 중 [취소·환불 요청](노출 계속 + 심사)은 화면에서 [중단하고 환불 요청]으로 바꾸고, 서버의 진행 중 `refund-request` 는 **중단과 같은 처리**(바로 내림 + `STOP_PRORATED` + 수수료)로 한다 — 수수료 없는 옛 길을 남기지 않는다.
- 정지된 채 끝 시각이 지나면 상태는 그대로(`TAKEN_DOWN`·`ADVERTISER_PAUSE`) 두고 화면이 「정지한 채 끝남 — 환불 없음」으로 읽는다(`endDate` 와 지금 비교). [다시 켜기]·중단은 끝 뒤 409.

## 규칙 (테스트 대상)
1. **정지 횟수**: 한 건에 3번(설정 `roboyam.biz.pause-limit`, 기본 3). 정지할 때 센다. 넘으면 409 **`BIZ_PAUSE_LIMIT`**. 사업자·운영자 대신 처리 모두 같은 셈.
2. **정지**: 바로 공개 노출에서 빠진다(공개 `/api/ads` 는 `SCHEDULED` 만 내므로 그대로, 사본 20초). 자리는 계속 그 광고주 몫(자리를 차지하는 상태에 `TAKEN_DOWN` 이 이미 있음). 정지 중 노출·클릭 집계 0(나가지 않는 건의 집계 줄은 버린다 — 기존 규칙).
3. **다시 켜기**: `ADVERTISER_PAUSE` 이고 끝 전일 때만, 승인 없이 바로 `SCHEDULED`. 소재는 지금 승인된 버전 그대로. 멈춘 시간은 `paused_minutes` 에 더하고 **끝 시각은 늘리지 않는다**. 핫딜 글은 다시 보이고(글 마감 = 신청 기간 끝 그대로) 노출 자리도 다시 나간다.
4. **중단**: 진행 중이거나 정지 중(끝 전)일 때. 바로 `TAKEN_DOWN`·`ADVERTISER_STOP`. 일할 기준 시각 `refund_from` = 정지 중 전환이면 **멈춘 시각**, 아니면 중단 시각. 되돌리기 없음(다시 하려면 새 신청).
5. **중단 환불액**(가정 — 법무 확인 전):
   - 대상 금액 `base` = 결제액(온라인 결제액 · 수동은 입금 확인 금액) × (대상 줄 공급가 합 / 모든 줄 공급가 합), 원 미만 버림. 광고·노출만 건 = 결제액 전부. **사업자 핫딜 글 = 노출 자리 줄만**(게시비 `HOTDEAL_POST` 줄은 돌려주지 않는다) — 노출 없는 글이면 0.
   - 일할 = floor(base × 남은 날 / 총 일수) — 총 일수 = 기간의 한국 날짜 수, 쓴 날 = 시작 날짜부터 `refund_from` 의 한국 날짜까지(그날 포함). 이미 승인한 환불(DOWNTIME 등)을 넘지 않게 `min(일할, 결제액 − 이미 환불)`.
   - **중도 해지 수수료** = floor(일할 × 수수료율 / 100), **환불 예상액 = 일할 − 수수료**. 수수료율은 그 신청 건에 고정된 값(아래 7).
   - 운영자는 심사 때 0 ≤ 승인액 ≤ 결제액 − 이미 환불 안에서 고칠 수 있다(기존). 제안 내역(일할·비율·수수료·예상액·기준 시각)은 환불 줄에 저장한다.
6. **수수료 없는 것**: 시작 전 취소(전액) · 긴급 내림 되돌리기 `DOWNTIME` · 사업자 정지 훅 일할(`SUSPEND_PRORATED`) · 게시 실패 건 운영자 취소(전액).
7. **수수료율 설정·고정**: 관리자 설정 0~30 정수 %(기본 10) — 「광고 기본 설정」 `/ads/placements?tab=refund`(조회 `ad.read`·저장 `ad.settings`, 광고·사업자 핫딜 공통, 변경 이력·감사는 자리 설정과 같은 관례). **신청 건을 낼 때(사업자 신청·운영자 등록) 그때 비율을 신청 건에 고정 저장**한다 — 사업자가 동의한 규정의 비율이고, 뒤에 설정이 바뀌어도 그 건(과 이미 낸 환불 요청)은 그대로.
8. **환불 규정 버전**: 동의 문구에 진행 중 중단 수수료를 넣고 버전을 `2026-10-30` → **`2026-11-02`** 로 올린다. 서버가 지금 버전을 알고, 새 신청이 다른 버전이면 400 `fields.agreedRefundPolicyVersion`(화면은 「새로 고침해 주세요」). **이미 `2026-10-30` 에 동의한 건은 수수료율 0%로 고정**(수수료가 없는 규정에 동의했으므로 — 마이그레이션이 기존 줄을 0 으로 채움). 운영자 등록 건(동의 기록 없음)은 등록 때 설정 비율(가정 — 오프라인 계약서에 같은 비율을 적는다).
9. **사업자 핫딜 글 숨김**: 정지 = 글 숨김 까닭 `SELLER_PAUSED`, 중단 = `SELLER_STOPPED`(운영 숨김 `STAFF` 와 구분). 목록·검색·가젯·인기글에서 빠지는 것은 기존 숨김과 같다. 글 단건 공개 조회는 404 대신 **403 `DEAL_SELLER_HIDDEN`** + extras `{ state: PAUSED|STOPPED, boardSlug, boardName, boardNameEn, applicationId(보는 사람이 그 사업자일 때만) }` — 본문·댓글·딜 칸을 싣지 않고 조회수도 올리지 않는다. 다시 켜기는 까닭이 `SELLER_PAUSED` 이고 숨긴 시각이 `biz_deal_posts.hidden_at` 과 같을 때만 푼다(기존 되돌림 규칙과 같은 방식 — 운영자가 따로 숨긴 글은 안 풂). 노출 자리 멈춤 까닭 `PAUSED`(정지)·`POST_HIDDEN`(중단).
10. 운영자 화면에선 숨겨도 글·종료 요청·신고가 다 보이고 처리 기록에 누가 언제(`POST_HIDDEN` note = 까닭). 댓글 단 회원에게 알림 없음, 그 회원의 「내 활동」 댓글 목록은 그대로(숨김 글의 기존 동작을 따른다 — 바꾸지 않음).
11. **운영자 대신 처리**: 요청 경로 `PHONE`·`EMAIL`·`OTHER` 필수 + 메모 1~200자 필수(400 `fields.channel`·`fields.memo`). 권한 = 건의 도메인 처리 권한(광고 `ad.manage`·핫딜 `hotdeal.manage`), 중단 건의 환불 승인·거절은 기존 `deal-order.refund`.

## 데이터 모양 (화면 목업이 정한 이름 — 서버 DTO 가 정본)
```
BizTakedownCause ∪ ADVERTISER_PAUSE | ADVERTISER_STOP        BizPauseChannel = PHONE | EMAIL | OTHER
공개 상세 += takedownCause, pause{ used, limit, pausedAt, pausedBy: ME|OPERATOR, channel, pausedMinutes, stoppedAt, refundFrom, proratableTotal,
                                  feeRate }        // pause 는 결제 뒤 날짜형 자리·사업자 핫딜 글에만, 아니면 null
공개 요약 += takedownCause
관리자 상세 += pause{ …같음, actor(표시 이름), byOperator }, takedown += channel, memo, by · 큐 줄·일정표 줄 += takedownCause
환불(공개·관리자) += type STOP_PRORATED, proratedAmount, feeRate, feeAmount, basisAt          // 환불 예상액 = proposedAmount
BizDealStopReason ∪ PAUSED · 처리 기록 action ∪ PAUSED | RESUMED | STOPPED
  note: 운영자 대신 = `PHONE|EMAIL|OTHER · 메모`(공개 응답은 경로만) · STOPPED 정지 중 전환 = 앞에 `FROM_PAUSE` · RESUMED = `MIN:<멈춘 분>` 토큰
  POST_HIDDEN·POST_RESTORED note = 까닭(ADVERTISER_PAUSE|ADVERTISER_STOP) · REFUND_REQUESTED note = ADVERTISER_STOP
관리자 핫딜 글 줄·상세 += sellerHidden{ cause: ADVERTISER_PAUSE|ADVERTISER_STOP, at, by, byOperator } | null
공개 글 deal.business += applicationId(작성자에게만)
공개 GET /api/business/refund-policy → { version: '2026-11-02', stopFeeRate }           // 신청 화면 동의 문구가 비율을 보인다
관리자 자리 설정 += refund{ stopFeeRate 0~30 }                                           // 이력 칸 refund.stopFeeRate
```

## API
- 공개: `POST /api/business/applications/{id}/pause` · `POST …/{id}/resume` · `POST …/{id}/stop { reason 1~300 }` → 상세. 409 `BIZ_PAUSE_LIMIT`·`BIZ_APPLICATION_STATE_CHANGED`. `GET /api/business/refund-policy`.
- 관리자: `POST /api/admin/biz-applications/{id}/pause|resume|stop { channel, memo }` → 상세. 감사 `BIZ_APP_PAUSE`·`BIZ_APP_RESUME`(일반) · `BIZ_APP_STOP`(돈 — 5년). 자리 설정 `GET/PUT /api/admin/ads/placements` 에 `refund` 묶음.
- 알림: 사업자 `BIZ_APPLICATION` 덧붙임 kind `PAUSED`·`RESUMED`·`STOPPED`(운영자 대신 처리면 「운영팀이 요청을 받아…」), 중단의 환불 요청은 기존 운영자 알림 `AD_REFUND_REQUESTED`·`HOTDEAL_REFUND_REQUESTED`.

## 미확정
- **중도 해지 수수료(10%)·일 단위 일할·정지 3회·정지 중 기간 불연장**은 법무 확인 전 가정(전자상거래법·약관규제법 — B2B 광고 위약금 한도). 위약금(수수료 외)은 범위 밖.
- 정지 중 건의 자리: 끝까지 그 광고주 몫(빈자리로 다른 신청을 받지 않음). 중단 건은 환불 심사 동안 `TAKEN_DOWN` 이라 자리를 계속 차지한다(승인 → `CANCELED` 때 반납) — 바로 놓을지는 후속.
- 정지 중 건의 「예약·진행」 큐 소속·목록 묶음은 기존 `TAKEN_DOWN` 판정을 따른다. 중단 건은 열린 환불이 있어 「환불 심사」 탭에만 든다(한 건 한 큐).

## 서버 구현 결정 (2026-11-02 — V127 · 배치 없음)
요청·응답 모양 전체는 서버 DTO 가 정본이다 — api `bizapplication/dto/BizMemberView`(`Pause`·`Refund`·`RefundPolicy`·`Summary/Detail.takedownCause`), admin `bizapplication/dto/BizAdminView`(`Pause`·`Takedown`·`Refund`·`Row/Detail.takedownCause`)·`BizAdminRequest.PauseAction`, admin `ad/dto/AdAdminView`(`Row/Detail.takedownCause`·`Detail.pause`·`Takedown.channel/memo/pause`)·`AdPlacementAdmin`(`refund`), admin `hotdeal/dto/response/HotdealAdminView.SellerHidden`, shared `hotdeal/dto/response/DealBusinessView.applicationId`. 아래는 규칙만.
- **마이그레이션** shared `V127__biz_pause_stop.sql`(배치 없음 — `R__batch_35` 안 씀): `biz_applications.takedown_cause` varchar(10→20) + CHECK ∪ `ADVERTISER_PAUSE`·`ADVERTISER_STOP` · 새 칸 `pause_count`(0~100)·`paused_at`·`paused_minutes`·`takedown_actor`(MEMBER|OPERATOR)·`takedown_channel`(PHONE|EMAIL|OTHER)·`takedown_memo`(≤200)·`stopped_at`·`refund_from`·`stop_fee_rate`(0~30, 기존 줄 0 · 기본값 없음 → 새 줄은 넣는 쪽이 반드시 정함) + CHECK(광고주 내림 ⇒ actor, 경로 ⇔ 메모 · 경로 ⇒ OPERATOR, 정지 중 ⇒ `paused_at`, 중단 ⇒ `stopped_at`·`refund_from ≤ stopped_at`) · `biz_refunds.type` ∪ `STOP_PRORATED` + `prorated_amount`·`fee_rate`·`fee_amount`·`basis_at`(CHECK: STOP_PRORATED ⇔ 넷 다 있음 · `proposed = prorated − fee`) · `posts.hidden_reason` ∪ `SELLER_PAUSED`·`SELLER_STOPPED`(V80 넷 그대로) · `ad_placement_settings.stop_fee_rate`(기본 10, 0~30). ⚠ develop 에 합치기 직전 마지막 번호를 다시 본다.
- **엔진** shared `BizPauseService`(사업자·운영자 대신 공용) · 전이표 `ADVERTISER_PAUSE`(SCHEDULED→TAKEN_DOWN)·`ADVERTISER_RESUME`(TAKEN_DOWN→SCHEDULED)·`ADVERTISER_STOP`(SCHEDULED·TAKEN_DOWN→TAKEN_DOWN). 정지·중단할 수 있는 종류 = `AD_BANNER`·`AD_TEXT_LINE`·`HOTDEAL_POST`(공개 신청이 없는 `HOTDEAL_PLACEMENT` 는 409). 사업자 핫딜 글은 게시됨 · 지운 글 아님 · 딜 ACTIVE·FLAGGED · (진행 중이면) 글이 보일 때만 — 정지 중 건의 중단은 글 숨김 상태를 보지 않는다. 정지 한도 설정 `roboyam.biz.pause-limit`(기본 3, `BizApplicationProperties.pauseLimit`) — 정지 UPDATE 조건에도 `pause_count < 한도`. 한도를 다 써도 중단은 된다.
- **다시 켜기**: `paused_minutes += floor((지금 − paused_at)/분)`, 끝 시각·자리 줄 그대로, 내림 칸을 비운다. ★ 사업자가 직접 켤 때는 자격을 다시 본다(정지된 사업자 409 `BUSINESS_NOT_APPROVED` — 가정, 운영자 대신은 보지 않음). 관리자 [되돌리기]·사업자 해제 훅의 `store.restore` 는 까닭 `SUSPENDED`·`EMERGENCY` 만(조건에도 걸었다) — 광고주 요청 건 409.
- **중단 환불**: `base = BizPricing.base(결제액, 대상 줄 공급가, 모든 줄 공급가)`(BigInteger 버림 — 핫딜 글은 `HOTDEAL_POST` 줄 제외) → `BizPricing.stop` = `prorate(base, 시작, 끝, refund_from)` → `min(일할, 결제액 − 승인한 환불 합)` → 수수료 `floor(일할 × stop_fee_rate / 100)` → 예상액 > 0 이면 `STOP_PRORATED` REQUESTED(+ 처리 기록 `REFUND_REQUESTED` note `ADVERTISER_STOP` + 운영자 알림 `AD_/HOTDEAL_REFUND_REQUESTED`), 0 이면 환불 줄 없음(내림 `ADVERTISER_STOP` 그대로). ★ 정지 중 전환이면 그 정지의 멈춘 분도 `paused_minutes` 에 더한다. 환불 승인 → `CANCELED`(까닭·중단 칸 남음), 거절 → `TAKEN_DOWN` 그대로(다시 요청·중단 409).
- **진행 중 `refund-request`** = 중단(정지 중이면 정지 중 전환 — `BizPauseService.stopFromRefundRequest`). 시작 전은 그대로 전액(`FULL_AUTO`, 수수료 칸 null). 수수료가 붙는 종류는 `STOP_PRORATED` 하나 — `DOWNTIME`·`SUSPEND_PRORATED`·`FULL_AUTO` 는 내역 칸 null.
- **수수료율 고정**: 사업자 신청(`BizApplicationMemberService.create`)·운영자 등록(`AdOperatorService.createManual`)이 그때 `AdPlacementStore.stopFeeRate()`(행 없으면 10)를 `stop_fee_rate` 에 넣는다. 환불 규정 버전 상수 `BizApplicationMemberService.REFUND_POLICY_VERSION = 2026-11-02` — 형식이 맞고 다르면 400 `fields.agreedRefundPolicyVersion`(문구 `bizapp.refundPolicy.outdated`), 둘 다 비면 기존 `fields.agree`. 공개 `GET /api/business/refund-policy` → `{ version, stopFeeRate }`(로그인 필요).
- **운영자 대신 처리**: `POST /api/admin/biz-applications/{id}/pause|resume|stop` 본문 `{ channel, memo, reason? }` — 입구 `ad.manage|hotdeal.manage`, 서비스가 건의 도메인 처리 권한(403 + 거부 감사). 400 `fields.channel`(없음·모름)·`fields.memo`(1~200)·`fields.reason`(중단 사유 300자 초과). ★ `reason` 은 선택이고 메모를 사유로 옮기지 않는다(공개 `takedownReason`·`refund.reason` 에 메모가 나가지 않게 — 운영자 중단에 사유가 없으면 null). 감사 `BIZ_APP_PAUSE`·`BIZ_APP_RESUME`(일반)·`BIZ_APP_STOP`(돈 5년 — `channel`·`memo`·`fromPause`·`refundFrom`·`prorated`·`feeRate`·`fee`·`proposed`). 상세 `takedown.by` = 운영자 이름 · 사업자 닉네임(없으면 상호).
- **처리 기록 note**: PAUSED = (운영자) `경로 · 메모` · RESUMED = `MIN:<분>`[` · 경로 · 메모`] · STOPPED = [`FROM_PAUSE · `][`경로 · 메모`](사업자 직접 중단은 null 또는 `FROM_PAUSE`) · `POST_HIDDEN`·`POST_RESTORED` = `ADVERTISER_PAUSE|ADVERTISER_STOP`. 공개 처리 기록은 경로 토큰까지만 남기고 메모를 자른다(`MyBizApplicationService.publicNote`).
- **알림**: ★ 사업자 `BIZ_APPLICATION` kind = `PAUSED`·`RESUMED`·`STOPPED`(사업자 직접) / `PAUSED_BY_OPERATOR`·`RESUMED_BY_OPERATOR`·`STOPPED_BY_OPERATOR`(「운영팀이 요청을 받아…」 — 문구를 kind 로 가르려고 따로 둠). STOPPED 의 {0} = 환불 예상액.
- **응답 칸**: 공개 상세·요약 `takedownCause`(내림·중단 뒤 취소의 까닭, 없으면 칸 없음) · 공개 상세 `pause{used,limit,pausedAt,pausedBy:ME|OPERATOR,channel,pausedMinutes,stoppedAt,refundFrom,proratableTotal,feeRate}`(결제 뒤 정지 가능 종류 — 예약·내림, 광고주 까닭으로 닫힌 취소 — 아니면 null) · 관리자 `pause` 는 + `actor`·`byOperator` · ★ `proratableTotal` = 결제액 기준(수동은 입금 확인 금액 — 목업의 「확정 합계」와 온라인은 같다) · 환불 `type` 공개 FULL_AUTO|PRORATED_REVIEW|STOP_PRORATED(옛 PRORATED·SUSPEND_PRORATED·DOWNTIME 은 PRORATED_REVIEW), 관리자 이름 그대로 + `proratedAmount`·`feeRate`·`feeAmount`·`basisAt` · 노출 자리 `stopReason` PAUSED(판매자 정지 숨김)·POST_HIDDEN(그 밖 숨김 — 중단 포함) · 일정표 상세는 `pause` 를 맨 위와 `takedown.pause` 둘 다에 싣는다.
- **핫딜 글 숨김 고리**(`BizHotdealPosting.onSellerHidden`·`onSellerShown`, 구현 `BizHotdealPublisher`): 정지 = 보이는 글을 `SELLER_PAUSED` 로 숨기고 `biz_deal_posts.hidden_at` 에 시각 · 중단 = 보이는 글 또는 이 흐름이 정지로 숨긴 글을 `SELLER_STOPPED` 로(시각 새로) · 운영자·신고가 숨긴 글은 건드리지 않는다 · 다시 켜기 = 까닭 `SELLER_PAUSED` 이고 숨긴 시각이 `hidden_at` 과 같을 때만 푼다. 공개 `GET /api/posts/{id}` 만 403 **`DEAL_SELLER_HIDDEN`** extras `{state, boardSlug, boardName, boardNameEn, applicationId(작성자만 — 아니면 칸 없음)}`(조회수 안 올림 · 본문·딜 없음, 예외 `api post/exception/DealSellerHiddenException`) — 댓글·투표 등 다른 글 API 는 기존 403 `POST_HIDDEN`. 공개 글 `deal.business.applicationId` = 보는 사람이 작성자일 때만. 관리자 핫딜 글 줄·상세 `sellerHidden{cause,at,by,byOperator}`(`BizDealPostStore.sellerHidden`). 목록·검색·가젯·인기글은 기존 숨김 판정(`hidden_at`) 그대로 — 바꾸지 않았다.
- **자리 설정**: `GET/PUT /api/admin/ads/placements` 에 `refund{stopFeeRate}` — ★ PUT 의 `refund` 는 선택(빠지면 지금 값 그대로 — 화면이 붙기 전 요청과 맞춤), 보내면 0~30 정수 필수(400 `fields.refund.stopFeeRate`, 문구 `ad.placement.stopFeeRate`). 감사 `AD_PLACEMENT_UPDATE` 와 이력의 열째 칸 `refund.stopFeeRate`(처음 저장 CREATE 는 열 칸). 공개 `GET /api/ads/placements`(`AdPlacements`)에는 싣지 않는다.
- **테스트**: api `BizPauseStopTest`(22 — 규칙 1·2·3·4·5·6·7·8·9·11 · 되돌리기 409 · 열린 환불 409 · 원 미만 버림) · admin `BizPauseAdminHttpTest`(4 — 권한·검사·응답 칸·일정표·감사·환불 승인 권한 · 정지 한도 409 · 수수료율 0~30·이력·감사) + 갱신 `BizApplicationRefundTest`(진행 중 요청 = 중단)·`AdOperatorLedgerTest`(DOWNTIME 뒤 중단 제안)·`BizHotdealPostingTest`(규칙 4 — 노출 자리 건으로)·`BizApplicationRulesUnitTest`(전이표)·`AdPlacementHistoryHttpTest`(CREATE 열 칸)·환불 규정 버전 상수를 쓰는 시험 바탕(`POLICY`)·`BusinessDeleteHttpTest`(직접 INSERT 에 `stop_fee_rate`). `--tests` — api `bizapplication.*`·`ad.*`·`hotdeal.*` 288 · shared `bizapplication.*`·`audit.*`·`ad.*` 22 · admin `bizapplication.*`·`ad.*`·`hotdeal.*`·`business.*`·`AuditRetentionAdminTest` 77 통과.
- **남은 점(미확정)**: 정지 중 건에 사업자 정지 훅이 오면 그대로 둔다(훅은 예약 건만 다룸 — 정지 중이라 노출은 이미 없음, 해제 뒤에도 정지 중) · 게시판 글 목록에서 판매자 숨김 글은 기존 숨김 줄처럼 「숨김」 자리 표시(공개 까닭은 `REPORTED` 로 보임 — 기존 운영 숨김과 같은 동작) · 탈퇴 판정은 끝나지 않은 정지·중단 건을 진행 중으로 센다(기존 `TAKEN_DOWN` 판정).

## 화면 연결 결정 (2026-11-02)
- **목업 정리**: 두 앱의 정지·중단 목업(`composables/*/api/mock/pause-mock.ts` 넷, user-web `board/api/mock/bizdeal-post-mock.ts`, 목업 ID `mock-ad-pause`·`mock-deal-pause`·`mock-bizdeal*`, `?mock=pause-*`·`bizdeal-mine`)과 admin-web `common/mock/mock-util.ts`(쓰는 목업 없음 — 서랍의 `mockQuery` 도 뺌)를 지웠다. user-web `mock-util.ts` 는 adult·sanction 목업이 써서 남긴다. 모두 실제 API(`useBusinessApi.pause|resume|stop|refundPolicy`, 관리자 `useBizApplicationApi.pause|resume|stop`).
- **타입**: 서버 DTO 이름 그대로 — 늘 오는 칸은 필수로(`takedownCause`·`pause`·`takedown.channel|memo|by`·`AdTakedown.pause`·`sellerHidden`·`deal.business.applicationId`), 환불 줄 `proratedAmount`·`feeRate`·`feeAmount`·`basisAt`, `pause.feeRate`(관리자는 + `pausedBy`), `SellerHidden.by` 는 null 가능. 사업자 알림은 서버가 제목·본문을 그려 화면이 kind 를 가르지 않는다(`*_BY_OPERATOR` 화면 변경 없음).
- **수수료 화면**: 예상액은 서버 `BizPricing.stop` 과 같은 식(일할 floor → 수수료 floor(일할 × `pause.feeRate` / 100) → 일할 − 수수료). user-web 은 `min(일할, 결제액 − 이미 환불)` 을 모르므로 생략, admin-web 은 결제액(입금 확인 금액 · 없으면 승인 확정 금액) − 승인한 환불로 상한을 둔다. 요청 뒤에는 환불 줄 값을 그대로 보인다(내역 「일할 − 수수료(n%) = 예상액」, 0%면 수수료 줄 없이 일할만). 관리자 정지 칸에 「중도 해지 수수료 n% — 신청 때 고정」 한 줄.
- **진행 중 [취소·환불 요청]**: user-web 은 시작 전(`UPCOMING`)에만 연다 — 진행 중 요청은 서버가 중단으로 처리하므로 옛 일할 창으로 가지 않게(정지할 수 없는 진행 중 핫딜 글은 단추 없음).
- **운영자 대신 중단**: 창에 「중단 사유(선택, 300자 — 사업자에게 보임)」 칸을 더했다(비우면 안 보냄, 메모는 옮기지 않음, 400 `fields.reason` 은 그 칸 아래).
- **규정 버전**: 신청 화면 상수 `REFUND_POLICY_VERSION = 2026-11-02`, 동의 풀이는 `GET /api/business/refund-policy` 의 비율을 넣고 실패하면 비율 없는 문구, 400 `fields.agreedRefundPolicyVersion` 이면 「환불 규정이 바뀌었어요 — 새로 고침해 주세요」.
- **수수료 설정**: `/ads/placements?tab=refund` 다섯째 탭(0~30 정수, 기본 10) — 같은 저장 막대·`PUT` 한 번(`refund` 늘 보냄), 칸 오류 `fields.refund.stopFeeRate`, 이력 칸 `refund.stopFeeRate`(값 「n%」), 미리보기는 예시 계산(남은 일할 100,000원 → 수수료 → 환불).
- **남은 점**: 화면 실제 호출 확인(서버 띄워 두 앱 정지·재개·중단·환불 승인·수수료 저장)과 시각 리뷰는 개발자 몫 · 운영자 [대신 중단]은 진행 중(열린 환불 없음)·정지 중 건 모두에 둔다(서버가 둘 다 받음 — 전화로 「끝내 주세요」 대응).

## Chrome 확인 뒤 고침 (2026-11-02 — develop e1b8f99d 확인 결과)
- **판매자 숨김 글은 게시판·핫딜 목록에서 아예 뺀다**: 공개 `GET /api/boards/{slug}/posts` 의 `PostRepositoryQDSL.page`·`dealPage` 에 `notSellerHidden`(숨김 까닭 `SELLER_PAUSED`·`SELLER_STOPPED` 이 아님) — 쪽 건수·`totalCount` 도 같이. 신고·운영 숨김 글은 지금처럼 「숨겨진 글」 줄로 남는다. 통합 검색·게시판 안 검색·가젯 `HOT_DEALS`·인기글·작성자 글 목록·쇼핑몰 칩·이전/다음 글은 원래 숨김 글을 모두 빼므로 그대로(시험으로 확인). 운영자 화면(admin 쿼리)은 바꾸지 않음. 시험 `BizPauseStopTest` 「규칙9_판매자_숨김_글은_목록_검색_가젯_쇼핑몰_칩_작성자_글에서_아예_빠지고…」(고치기 전 실패 확인).
- **사업자 신청 건 처리 기록 note**: user-web `UtilBusinessMessages.pauseNoteToken` 이 정지·재개·중단·글 숨김·다시 보임·환불 요청·내림·환불 거절 줄의 토큰을 번역한다 — `ADVERTISER_PAUSE`·`ADVERTISER_STOP`·`EMERGENCY` 는 「일시 정지해서」「중단해서」「운영팀 긴급 조치」(글 다시 보임 + 정지는 「다시 켜서」), 정지·재개·중단 줄에서는 제목과 겹쳐 뺀다. 그 밖의 코드 모양 토큰(대문자·밑줄)은 숨기고 사유 글은 그대로. user-web 에 화면 단위 시험 도구가 없어 타입 검사로만 확인.
- **광고 일정표 합계**(2026-11-02 — 「정지 중 건이 어디에도 안 세짐」): `GET /api/admin/ads/calendar` 의 `summary` 에 `paused`(광고주 일시 정지)·`takenDown`(그 밖의 내림 — 광고주 중단 환불 심사 중·긴급 내림·사업자 정지)을 더했다. 셈 기준 = 자리를 차지하는 상태(한도 판정과 같음) 중 끝나지 않은 건 — `TAKEN_DOWN` 은 까닭과 상관없이 되돌리거나 환불 승인(→ `CANCELED`, 자리 반납) 전까지 자리를 잡으므로 센다. admin-web `/ads` 요약 띠 「정지 중」(주황)·「내림·중단」(빨강) 두 칸(핫딜 노출 탭도 같은 화면). 시험 `BizPauseAdminHttpTest` 「일정표_합계는…」.
- **결제창 구매자 칸**(2026-11-02 — 포트원 「phoneNumber 필드가 NON_EMPTY_STRING 조건을 만족하지 않습니다」): 공개 `POST /api/business/applications/{id}/payment` 응답(`BizMemberView.PaymentStart`)에 `customer{ fullName = 사업자 상호, phoneNumber = 신원 연락처 평문의 숫자만, email = 신원 이메일 }`(본인 건만 — 남의 건은 기존 404, 응답 no-store, 공동구매 `PaymentStart.customer` 와 같은 모양, 결제 수단과 상관없이 싣는다). user-web 은 `target.customer` 를 쓰고 없을 때만 회원 값. 연락처의 숫자가 없으면(가정 — DB CHECK `ck_bp_pii` 가 칸을 비우지 못해 실제로는 파기된 신원·잘못된 값뿐) 결제 주문을 만들기 전에 400 **`BIZ_CONTACT_MISSING`**(「사업자 정보에 연락처를 넣어 주세요」). 시험 `BizApplicationHttpTest`(구매자 칸·no-store·남의 건 404 · 연락처 없음 400 + 결제 주문 없음).
