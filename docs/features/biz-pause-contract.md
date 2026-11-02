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
