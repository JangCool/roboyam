# 체험단 · 사용기 구입 경로 계약 (시안 확정 2026-11-17 · 구현 2026-11-17)

**의도**: 광고주가 제품을 대고 회원이 사용기를 쓰는 체험단을 광고 상품으로 연다. 리뷰어 칭호가 있으면 먼저 신청하고 더 많이 뽑힌다. 사용기는 어떻게 구했는지(체험단·직접 구매·협찬·대여)를 구분해 표시하고, 직접 구매는 구매 인증을 받을 수 있다.
**관련 계약**: `member-title-contract.md`(리뷰어 단계) · `point-board-tier-contract.md`(핵심 글 포인트) · `ad-contract.md`·`ad-advertiser-contract.md`·`ad-price-rules-contract.md`(광고 상품·가격 기간표) · `address-book-contract.md`(배송지).
**시안**: 대화 시안 「3 / 3 · 체험단 · 사용기 구입 경로」 다시 그린 것(2026-11-17).
**돈·법 권장안 확정**(사용자 결정 2026-11-17 — 가정이 아니라 이 안으로 간다): 아래 「광고 표시」·「미반납」·「제공형 상한」.

## 돈
- 「체험단」 광고 상품을 하나 더한다. 처음 가격 **0원**, 실적이 쌓이면 가격 기간표로 유료 전환. 제품은 광고주가 댄다. 사이트는 제품값·보증금을 받지 않는다(결제·정산 없음).
- 값 매기는 단위는 **둘 다 지원**(사용자 결정 2026-11-17) — 상품의 가격 기간표 줄마다 고른다.
  - **모집 건당**: 모집 하나에 정한 금액(예: 30만원 — 5명이든 10명이든 같음).
  - **인원당**: 뽑힌 사람 1명당 금액(예: 1명당 5만원 → 5명 25만원). 금액은 뽑기를 마친 인원으로 셈한다.
  - 기본 단위는 인원당. 0원인 지금도 단위 칸은 둔다.
- **대신 발송 비용**(운영자가 대신 보낼 때만): 가격 기간표에 「1명당 발송비」 칸 — 기본 0원, 건당·인원당 어느 단위든 더해진다.

## 광고 표시
- 모집 글: 「광고」 딱지 + 광고주 이름. 모집 글은 게시판 글이 아니라 체험단 페이지에만.
- 체험단·협찬 사용기: ① 제목 앞 말머리 **[체험단]**(협찬은 「협찬」 칩) ② 본문 맨 위 표시 상자 ③ 글 목록 칩. 셋 다 자동, 글쓴이가 지울 수 없다.
- 표시 문구는 체험단 설정에서 고친다. 기본: 「이 글은 {광고주}로부터 제품을 무상으로 {제공/대여}받아 작성한 글입니다」.

## 제공형 · 대여형
- 제공형(받아서 가짐)은 **제품값 5만원 이하**만(설정값). 넘으면 대여형으로만 모집할 수 있다(모집 만들기에서 제공형을 막음).
- 대여형 반납: 반납 배송비는 광고주 부담(수거·착불). 「반납 받음」은 광고주 또는 운영자가 체크.

## 미반납
- 반납 기한 당일 알림 → 7일 뒤 다시 알림 → **14일 지나면 미반납 확정**(일수는 설정).
- 확정되면: 체험단 신청 **영구 금지** · 리뷰어 칭호 회수 · 회원 제재 목록에 올림. 제품값은 **광고주가 회원에게 직접 청구**(신청 때 동의받음). 사이트는 청구·보증금에 끼지 않는다.
- 사용기 기한(기본 14일)을 넘기면 신청 **90일 금지**, 두 번이면 리뷰어 칭호 정지(설정).

## 모집 · 신청 · 뽑기
- 모집 만들기: 광고주·상품 · 제품값 · 형태 · **사용기 게시판**(「체험단 사용기 받기」 켠 게시판 중, 기본 사용기) · 인원 · 리뷰어 몫(%, 기본 60) · 리뷰어 먼저 신청(금 48시간 · 은 24시간 · 동은 모두와 함께) · 사용기·반납 기한 · 보내는 쪽(광고주 — 회원 동의 / 운영자가 대신).
- 신청: **본인인증 회원만**, AI 회원·운영팀 계정 불가. 받을 곳 = 배송지 관리에서 고름. 동의 = 광고주에게 이름·연락처·주소 제공(발송·수거용, 반납 확인 뒤 파기 — 운영자가 대신 보내면 이 동의 대신 「미반납이 확정되면 이름·연락처만 광고주에게 제공」 동의) + 미반납 시 광고주 제품값 청구.
- 뽑기: 광고주는 후보를 추천만, **최종 선정은 운영자**. 리뷰어 몫과 일반 몫을 따로 센다. 신청자 줄에 칭호 · 사용기 수 · 평균 추천 · 약속 지킨 비율.

## 대여형 파손 (확정 2026-11-17)
- **사진 비교가 기준**: 보내기 전 사진 ↔ 반납받은 뒤 사진. 운영자가 대신 보내면 입고 사진·반납 사진(운영자), 광고주가 보내면 **발송 전 사진(필수 — 없으면 발송 처리 안 됨)**·반납 사진(광고주가 올림).
- **정상 사용 마모는 파손이 아니다**(작은 흠집·소모품 닳음·먼지). 청구도 불이익도 없다.
- 절차: 운영자 또는 광고주가 사진을 붙여 「파손 신고」 → 회원에게 사진과 함께 알림 → 회원 **7일 안에 이의 제기**(받을 때부터 그랬다·택배 파손 등) → 운영자가 판정.
| 판정 | 청구 | 사이트 불이익 |
|---|---|---|
| 정상 마모 | 없음 | 없음 |
| 택배 파손 | 택배사에(보낸 쪽이 처리) | 없음 |
| 회원 과실 | 광고주가 회원에게 **실제 수리비 한도**(수리 불가면 제품값 한도) | 체험단 신청 **90일 금지** |
| 고의 · 파손을 숨기고 반납 | 위와 같음 | **영구 금지** + 리뷰어 칭호 회수(미반납과 같은 수준) |
- 회원 과실·고의로 확정됐을 때만 광고주에게 회원 이름·연락처·판정·사진을 넘긴다. 청구와 입금은 광고주와 회원이 직접 하고 사이트는 돈에 끼지 않는다. 청구 한도는 모집 글과 신청 동의에 적고, 광고주는 그보다 많이 청구할 수 없다.
- 신청 동의(대여형만): 「정상 사용 마모는 청구하지 않습니다. 과실이나 고의로 파손되면 광고주가 수리비(수리 불가 시 제품값) 한도에서 청구할 수 있고, 이때 이름과 연락처가 광고주에게 제공됩니다.」
- 판정은 감사 기록에 남고, 관리자 「확인할 일」 목록에 「파손 신고」 종류로 들어간다.

## 운영자가 대신 보낼 때 (확정 2026-11-17)
1. **입고**: 광고주가 뽑힌 인원만큼(+ 예비 1개 권장) 제품을 사이트 운영 주소로 한 번에 보낸다. 운영자가 받으면 모집 상세 「입고·발송」에 받은 수량과 **상태 사진**을 남긴다(사진 없이는 「입고 완료」를 누를 수 없다). 수량이 모자라면 광고주에게 알리고 그만큼 뽑기 인원을 줄이거나 기다린다.
2. **발송**: 운영자가 회원마다 송장 번호를 넣으면 회원에게 알림이 가고 「받음」은 회원이 누른다(7일 안에 안 누르면 송장 기준 배송 완료로 본다). 회원 주소는 광고주에게 가지 않는다 — 신청 때 광고주 제공 동의를 받지 않는다.
3. **대여형 반납**: 회원은 사이트 운영 주소로 반납한다(운영자가 수거 신청). 운영자가 받으면 「반납 받음」과 상태 사진. 미반납 규칙은 위와 같다(청구는 광고주가 하되, 이때는 광고주에게 회원 이름과 연락처만 넘긴다 — 신청 때 이 경우만 동의를 받는다).
4. **광고주에게 돌려보내기**: 모집이 끝나면(반납 기한 + 미반납 확정까지) 반납된 제품과 남은 예비 제품을 광고주에게 한 번에 반송하고 송장 번호를 남긴다. 반송 배송비는 광고주 부담(착불).
5. **보관 기한**: 입고 뒤 60일 안에 모집이 끝나지 않거나 광고주가 남은 제품을 찾아가지 않으면 착불 반송한다. 사이트는 판매·폐기하지 않는다.
6. **파손·분실 책임**: 입고 때 이미 파손 = 광고주 책임 · 입고 사진 뒤 사이트 보관 중 파손 = 사이트 책임 · 택배 구간 = 택배사 책임 · 회원 사용 중 파손 = 아래 「대여형 파손」 절차.
- 관리자 모집 상세에 탭 **「입고·발송」**: 입고 수량·사진 · 회원별 송장·받음 · 반납 받음·사진 · 광고주 반송 송장. 보내는 쪽이 「광고주」인 모집은 이 탭 대신 광고주가 송장을 넣는다.

## 사용기 쓰는 곳
- 체험단 전용 게시판은 만들지 않는다. 모집마다 고른 게시판(기본 사용기)에 쓴다.
- 내 체험단 「쓰러 가기」 → 지정 게시판 글쓰기가 체험단 연결·말머리·표시 상자를 넣은 채 열린다. 일반 글쓰기에서도 뽑힌 체험단이 있으면 「뽑힌 체험단 사용기로 쓰기」를 고를 수 있다. **연결된 글만** 기한 안에 쓴 것으로 센다.
- 체험단 사용기도 그 게시판 등급 포인트를 그대로 받는다.
- 체험단 페이지(`/trials` 후보, 메뉴 바로 가기): 모집 중 · 진행 중 · 끝난 모집, 끝난 모집 아래에 그 모집의 사용기 모아 보기.

## 구입 경로 (사용기)
- 게시판 설정 「구입 경로 받기」: 끔 / 고를 수 있음 / **꼭 골라야 함**(사용기 게시판 기본). 「체험단 사용기 받기」 스위치도 게시판 설정에.
- 글쓰기 「어떻게 구했나요」: 내 돈으로 샀어요(+ 구매 인증하기) · 업체에서 받았어요(협찬) · 빌려서 써 봤어요 · 뽑힌 체험단 사용기로 쓰기. 체험단 연결 글은 자동 고정.
- 글 목록 칩: 체험단 · 구매 인증 · 직접 구매 · 협찬. 거르기: 전체 · **구매 인증만** · **체험단 빼고**.
- **구매 인증**: 주문 내역·영수증 사진(비공개 — 확인하는 사람만 봄, 이름·주소·카드번호 가리라고 안내). 확인 = 그 게시판 매니저 또는 운영자. 확인되면 「구매 인증」 칩 + 보너스 **100P**(설정). 사진은 확인 뒤 **30일** 지나면 파기.
- **협찬 숨김**: 업체에서 받고 「내 돈으로 샀어요」를 고르면 표시 의무 위반이라고 글쓰기에서 안내. 신고 사유 「협찬 숨김」 추가 → 운영자 확인 시 협찬 표시를 강제로 붙이고 제재 대상.

## 관리자 화면
- 체험단 설정(사이트 한 번): 제공형 상한 · 표시 문구 · 반납 독촉 일수 · 사용기 기한 넘김 불이익 · 구매 인증 보너스·사진 보관 일수.
- 모집 목록·모집 만들기·뽑기 — 광고 메뉴 아래(`/ads/trials` 후보).
- **확인할 일** 목록 하나: 구매 인증 · 반납 확인 · 파손 신고 · 협찬 숨김 신고 · 칭호 신청(칭호 계약과 같은 목록).

## 권한 (서버 `Permission` 후보)
- 체험단 모집·뽑기 = 광고 권한(`ad.manage`) 또는 새 `trial.manage`. 설정 = `ad.settings` 또는 `trial.settings`. 구매 인증 확인 = 운영자 + 게시판 매니저(자기 게시판).

## API 후보
- 공개: `GET /api/trials` · `GET /api/trials/{id}` · `POST /api/trials/{id}/applications` · `GET /api/me/trials` · 글쓰기 `purchaseSource: TRIAL|SELF|SPONSORED|BORROWED` + `trialId` · `POST /api/posts/{id}/purchase-proof`(사진).
- 관리자: `GET/POST/PUT /api/admin/trials` · `GET /api/admin/trials/{id}/applications` · `POST .../select` · `POST /api/admin/trials/participants/{id}/returned` · `GET/PUT /api/admin/trials/settings` · 확인할 일 `GET /api/admin/review-queue`(칭호와 공용).

## 미확정
- 없음.

## 구현 결정 (2026-11-17 — 사용자 「체험단까지 다 끝내고 푸시」 지시로 화면·서버를 함께 만든다)
1. **광고 상품**: `BizProduct` 에 `TRIAL`(공개 이름 「체험단」)을 더하고 `ck_bpr_product` 를 다시 만든다(선례 V219). 요금표·가격 기간표에 `price_unit` `PER_RECRUIT|PER_PERSON`(기본 PER_PERSON) · `shipping_fee_per_person`(기본 0) 칸을 더한다 — TRIAL 만 쓰고 다른 상품은 null. 처음 요금표 줄 0원. 모집 = 광고 신청 `biz_applications` 의 새 kind `TRIAL` 한 건과 1:1(`trials.biz_application_id`), 금액은 뽑기를 마친 뒤 확정(인원당이면 뽑힌 인원 × 단가 + 대신 발송이면 인원 × 발송비).
2. **표**(새 표엔 엔티티): `trials`(광고주·광고 신청·상품 이름·제품값·형태 GIVE|LEND·사용기 게시판·인원·리뷰어 몫 %·리뷰어 먼저 신청 시간(금·은)·신청 시작/마감·사용기 기한 일수·반납 기한 일수·보내는 쪽 ADVERTISER|OPERATOR·상태 DRAFT|RECRUITING|SELECTING|RUNNING|CLOSED|CANCELLED·설명·대표 그림) · `trial_applications`(모집·회원·배송지 사본(암호문, 공동구매 참여와 같은 방식 — `DataKeyColumns` 등록)·동의 종류·광고주 추천·상태 APPLIED|SELECTED|NOT_SELECTED|CANCELLED·몫 REVIEWER|GENERAL) · `trial_participants`(뽑힌 한 줄: 송장·보낸 때·받음(회원/자동)·사용기 글·사용기 기한·반납 기한·반납 받음 때·누가·약속 지킴 여부·미반납 확정 때) · `trial_stock_receipts`(운영자 입고: 수량·사진·때·광고주 반송 송장) · `trial_photos`(입고·발송 전·반납 사진 — 미디어 저장소 이미지 키, 종류·대상) · `trial_damage_reports`(신고자·사진·회원 이의·판정 NORMAL_WEAR|COURIER|MEMBER_FAULT|INTENTIONAL·판정 운영자) · `purchase_proofs`(글·사진(비공개 — `DataCipher.encryptBytes` bytea, 사업자 서류와 같은 방식)·상태 PENDING|APPROVED|REJECTED·확인자·파기 때) · `trial_settings`(한 행: 제공형 상한 50000·표시 문구·반납 독촉 0/7/14일·사용기 기한 기본 14일·기한 넘김 금지 90일·두 번이면 리뷰어 정지·구매 인증 보너스 100P·사진 보관 30일·입고 보관 60일).
3. **신청 금지** = 기능 제한(`member_restrictions`)의 새 종류 `TRIAL_BAN`(막는 기능 `TRIAL_APPLY`, 영구 = until null, 자동 규칙 `TRIAL_UNRETURNED`·`TRIAL_REVIEW_LATE`·`TRIAL_DAMAGE_FAULT`·`TRIAL_DAMAGE_INTENTIONAL`). 「회원 제재 목록에 올림」 = 이 기능 제한 목록에 오르는 것(계정 정지 `member_sanctions` 와 섞지 않는다).
4. **게시판 칸**: `purchaseSourceMode` `OFF|OPTIONAL|REQUIRED`(사용기 게시판 `review` 는 REQUIRED, 기본 OFF) · `trialReviewsEnabled`(사용기 게시판 켬, 기본 끔). 덮어쓰기 칸.
5. **글**: `posts.purchase_source` `TRIAL|SELF|SPONSORED|BORROWED`|null · `posts.trial_participant_id`. 글쓰기 요청 `purchaseSource`·`trialParticipantId`(내 뽑힌 체험단만, 연결하면 TRIAL 고정). 체험단 글은 서버가 제목 앞 말머리를 강제로 단다 — 게시판 말머리에 공통 키 `TRIAL`(「체험단」, 스텝 전용 아님) 행이 없으면 만들어 쓴다. 글 응답에 `purchase: { source, proof: 'NONE'|'PENDING'|'APPROVED', disclosure: string|null }`(표시 상자 문구 — 체험단·협찬이면 채움, 글쓴이가 못 지움), 목록 줄에 `purchaseSource`·`purchaseProofApproved`. 목록 거르기 `purchase=PROOF|NO_TRIAL`.
6. **신고 사유** `SPONSOR_HIDDEN`(「협찬 숨김」, 구입 경로를 받는 게시판 글에만) — 운영자가 확인하면 글을 SPONSORED 로 바꾸고 표시를 붙인다.
7. **확인할 일 목록** 하나 = `GET /api/admin/review-queue?type=&status=` — 종류 `TITLE_APPLICATION`(칭호 신청, `title.review`) · `PURCHASE_PROOF` · `RETURN_CHECK` · `DAMAGE_REPORT` · `SPONSOR_HIDDEN`(`trial.review`). 관리자 화면은 칭호 관리의 「확인할 일」 탭과 같은 목록 컴포넌트를 쓴다(체험단 메뉴에도 같은 목록). 구매 인증은 게시판 매니저도 자기 게시판 것을 회원 앱 스텝 화면 「구매 인증」 탭에서 확인(`GET /api/boards/{slug}/staff/purchase-proofs` · `POST …/{id}/approve|reject`).
8. **광고주 화면**: 회원 앱 사업자 센터 `/business/trials`(광고주 = 사업자 신원 `business_profiles` 로 이어진 `ad_advertisers`) — 내 모집 · 신청자 보고 후보 추천 · (보내는 쪽 광고주일 때) 발송 전 사진(필수)·송장 입력·반납 받음·반납 사진 · 파손 신고. 광고주에게 보이는 회원 주소·연락처는 동의 범위 안(보내는 쪽 광고주 + 뽑힘)만, 반납 확인(제공형은 받음) 뒤 파기.
9. **공개 API**: `GET /api/trials?status=RECRUITING|RUNNING|CLOSED` · `GET /api/trials/{id}`(끝난 모집이면 사용기 모아 보기 `reviews[]`) · `POST /api/trials/{id}/applications` `{ addressId, consents[] }`(본인인증·AI/운영팀 불가·신청 금지·리뷰어 먼저 신청 창) · `DELETE …/applications/mine` · `GET /api/me/trials`(신청·뽑힘·받음 버튼·쓰러 가기·반납 안내·이의 제기) · `POST /api/me/trials/participants/{id}/received` · `POST /api/me/trials/damage-reports/{id}/objection` · `POST /api/posts/{id}/purchase-proof`(사진 multipart).
10. **관리자 API**(`trial.manage`·`trial.settings`·`trial.review`): `GET/POST/PUT /api/admin/trials` · `GET /api/admin/trials/{id}/applications` · `POST /api/admin/trials/{id}/select` `{ applicationIds }`(리뷰어 몫·일반 몫 따로 셈, 인원 넘으면 400) · 입고·발송 `POST /api/admin/trials/{id}/stock` · `PUT /api/admin/trials/participants/{id}/shipment` · `POST …/returned` · `POST /api/admin/trials/{id}/return-shipment` · 파손 `POST /api/admin/trials/participants/{id}/damage-reports` · `POST /api/admin/trials/damage-reports/{id}/decide` · 구매 인증 `POST /api/admin/purchase-proofs/{id}/approve|reject` · `GET/PUT /api/admin/trials/settings`. 관리자 메뉴 「광고 › 체험단」(`/ads/trials`)·「광고 › 체험단 설정」.
11. **배치**(admin, 매일 00:20 KST `trial-daily`): 받음 자동(송장 뒤 7일) · 사용기 기한 넘김(→ 90일 금지, 두 번째면 리뷰어 칭호 정지 = 칭호 회수 사유 `TRIAL_REVIEW_LATE`) · 반납 독촉 알림(기한 당일·7일 뒤) · 미반납 확정(14일 → 영구 금지 + 리뷰어 회수 + 광고주에게 이름·연락처 제공 기록) · 파손 이의 기한(7일) 지나면 운영자 확인할 일로 · 구매 인증 사진 파기(확인 뒤 30일) · 입고 보관 60일 알림 · 반납 확인 뒤 주소 사본 파기.
12. **리뷰어 단계** = 약속 지킨 횟수(기한 안 연결 사용기 + 대여형이면 반납까지) 3·10·30 → 동·은·금. 칭호 `member_titles` 의 리뷰어 단계를 그때 올린다(체험단 서비스가 칭호 서비스를 부른다). 리뷰어 몫·먼저 신청은 이 단계로 판정.
13. **포인트**: 구매 인증 승인 = 원장 새 사유 `PURCHASE_PROOF`(EARN, 대상 글, 글마다 한 번, 하루 총 한도 밖) — 경험치에 더함. 체험단 사용기는 게시판 등급 포인트 그대로.
14. **알림**(회원): `TRIAL_SELECTED`·`TRIAL_NOT_SELECTED`·`TRIAL_SHIPPED`·`TRIAL_REVIEW_DUE`·`TRIAL_RETURN_DUE`·`TRIAL_UNRETURNED`·`TRIAL_DAMAGE_REPORTED`·`TRIAL_DAMAGE_DECIDED`·`PURCHASE_PROOF_APPROVED`·`PURCHASE_PROOF_REJECTED`. 감사 기록: 모집 만들기·뽑기·발송·반납·파손 판정·구매 인증 판정·개인정보 제공(광고주) — 개인정보 제공은 개인정보 보관 분류.

## 서버 구현 결과 (2026-11-17, shared `V241__trials.sql` · admin `V12__admin_menu_trials.sql`)
- 「구현 결정」과 다른 점
  - 신청 동의는 jsonb 목록 `consents`(PROVIDE_CONTACT·UNRETURNED_CONTACT·PRODUCT_CHARGE·DAMAGE_CHARGE). 필요한 목록은 보내는 쪽·형태로 정해지고 보낸 목록이 정확히 같아야 한다(아니면 400). 광고주 발송 = PROVIDE_CONTACT(+ 대여형이면 PRODUCT_CHARGE·DAMAGE_CHARGE), 운영자 발송 = 대여형만 UNRETURNED_CONTACT·PRODUCT_CHARGE·DAMAGE_CHARGE, 제공형은 없음.
  - `trial_damage_reports.status` REPORTED·OBJECTED·AWAITING(이의 기한 지남)·DECIDED 칸, 협찬 숨김 확인 상태 표 `trial_sponsor_reviews` 를 더했다.
  - `posts` 가 파티션 표라 `trial_participant_id` 유일 색인 대신 `trial_participants.review_post_id` 유일 색인 + 줄 잠금으로 「줄 하나에 글 하나」를 막는다.
  - 모집 상태 바꾸기 = `PUT /api/admin/trials/{id}` 본문 `{ action: PUBLISH|CLOSE_APPLY|CLOSE|CANCEL }`.
  - 광고 신청 건(kind TRIAL)은 늘 SUBMITTED·운영자 등록, 뽑기 뒤 견적 칸만 굳힌다. 광고 심사 큐에 안 보이고 상세는 404.
  - `TRIAL_REVIEW_DUE` 알림은 미리 알림이 아니라 기한을 넘겼을 때. 표시 문구 자리표시 `{광고주}`·`{제공/대여}`.
  - 광고주의 발송·반납 받음·파손 신고는 보내는 쪽이 ADVERTISER 인 모집만(아니면 409 `TRIAL_WRONG_SHIPPER`).
- 추가 API: `GET /api/me/trials/writable?board=`(사용기로 쓸 뽑힌 체험단) · 매니저 `GET /api/boards/{slug}/staff/purchase-proofs/{id}/photo` · 관리자 `POST /api/admin/trials/images`·`POST /api/admin/trials/{id}/photos?kind=` · `POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm|dismiss`.
- 권한: 모집·입고·발송 `trial.manage` 또는 `ad.manage` · 파손 판정·구매 인증·협찬 숨김 `trial.review` · 설정 `trial.settings` 또는 `ad.settings` · 확인할 일 목록 `title.review`·`title.read`·`trial.review` 중 하나. V241 이 `trial.manage`·`trial.review` 를 ad.manage 가진 역할에, `trial.settings` 를 ad.settings 가진 역할에 준다. 리뷰어 칭호 조건에 `PURCHASE_PROOFS ≥1` 을 더했다.
- 가정: 받음 자동 = 송장 + 7일, 파손 이의 기한 7일·과실 금지 90일 고정, 대여형 주소 사본 파기 = 반납 7일 뒤(열린 파손 없을 때), 이름·연락처 넘긴 줄은 30일 뒤. 리뷰어 몫이 차면 남는 리뷰어 신청자는 일반 몫. 체험단 말머리 행은 첫 사용기 때 서버가 만든다.
- 못 한 것: 유료 체험단 결제 흐름(금액 > 0 이면 견적만) · 협찬 숨김 확인 뒤 자동 제재 없음 · 입고 부족 광고주 알림 없음 · 운영자 주소 열람 감사 없음 · 입고·반납·파손 사진은 공개 저장소(구매 인증 사진만 암호문) · 섞인 목록(최신·인기) 줄엔 구입 경로 칸 없음 · 데모 팩 미갱신.
- 시험: api `TrialApiTest` 16건 · admin `TrialAdminHttpTest` 5건 통과.
- 화면 연결(2026-11-17): 확인할 일의 「반납 확인」은 목록에서 바로 처리하지 않고 모집 상세 입고·발송 탭으로 보낸다(반납 사진 필수). 반납 처리는 `trial.manage` 몫으로 둔다(`trial.review` 만 가진 운영자는 볼 수만 있음). 글 고치기에도 구입 경로 칸(체험단 연결 글은 고정). 모집 만들기 서랍은 게시판·광고주·설정을 못 읽으면(권한 403) 빈 후보 + 안내로 그린다.

## 유료 결제 연결 (서버 구현 2026-11-17, shared `V242__trial_payment.sql` 후불 + `V244__trial_prepaid.sql` 선결제 기본 — 점검 결정 D1·D7·D9 금액·L5)
- **결제 때 — 선결제가 기본**(사용자 결정 2026-11-17: 「선결제 차액 환불 없어야지 체험단 모집인데 다모일지 안모일지 어떻게 알어. 기존 후불제도 유지하는것도 좋지만 선결제가 우선이되어야지」). 모집 칸 `paymentTiming` `PREPAID`(기본)|`POSTPAID` — 관리자 만들기·고치기 본문(빠지면 만들기 선결제·고치기 그대로, 잘못된 값 400 `fields.paymentTiming`). 바꾸기는 **초안에서만**(아니면 409 `TRIAL_STATE_INVALID`, 신청자가 있으면 409 `TRIAL_APPLICANTS_LOCKED`), 선결제를 마쳤으면 409 `TRIAL_PREPAID_LOCKED`. 선결제 → 후불이면 견적을 0 으로 거둔다(승인 뒤면 심사 대기로 되돌린 뒤, 처리 기록 `TRIAL_REQUOTED` 0 · 닫힌 건이면 새 건) · 후불 → 선결제면 바로 견적.
- **선결제(PREPAID)**:
  - 견적 = **모집 인원**으로 — 인원당 단가 × 모집 인원 · 건당 단가, 운영자 대신 발송이면 + 1명당 발송비 × 모집 인원. **저장(만들기·고치기) 때** 굳혀 금액 > 0 이면 광고 심사 큐 「심사 대기」(처리 기록 `TRIAL_QUOTED`, 다시 셈 `TRIAL_REQUOTED`) → 승인 → 기존 온라인/수동 결제 → `SCHEDULED`. 모집 `amount` 는 뽑기 전에도 있다.
  - **결제가 끝나기 전 공개(`PUBLISH`) 409 `TRIAL_PREPAY_REQUIRED`**. 금액 0 이면 결제 없이 공개(결제 흐름 밖 — 큐·처리 기록 없음).
  - 결제 전 고치기: 모집 인원·보내는 쪽이 바뀌면 다시 셈 — 심사 대기는 그 자리, 승인 뒤(결제·입금 대기, 입금 기록 없음)는 열린 결제를 닫고 심사 대기로 되돌린 뒤. 금액이 같으면 견적을 건드리지 않는다.
  - **결제 뒤**(예약 · 입금 기록 있는 입금 대기)에는 금액에 닿는 칸(광고주·제품·제품값·형태·모집 인원·보내는 쪽·결제 방식) 변경 409 **`TRIAL_PREPAID_LOCKED`**(제목·기간·설명 등은 된다). 0원으로 연 모집도 금액이 바뀌는 고치기는 같은 409.
  - **차액 환불 없음**: 덜 모이거나 덜 뽑혀도, 운영자가 뽑힌 줄을 취소해도 금액 그대로 — 뽑기 때 견적을 다시 굳히지 않고 뽑힌 줄 취소도 다시 셈하지 않는다. 결제가 끝났으니 입고·발송은 막지 않는다.
  - 결제 뒤 모집 취소: 공개 전이든 뒤든 발송 전이면 기존 전액 환불(일할 없음) · 발송이 있으면 409 `TRIAL_SHIPPING_STARTED`(기존 그대로).
  - **공개 전 광고 쪽 닫힘**(결제 기한 만료·반려·광고 쪽 운영자 취소·환불): 모집은 **초안 그대로**(취소하지 않는다) — 결제 상태 `EXPIRED`·`REJECTED`·`CANCELED` 로 보이고 **「결제 다시 요청」** `PUT /api/admin/trials/{id}` `{ "action": "REQUEST_PAYMENT" }`(초안·선결제·신청 건이 닫혔을 때만, 아니면 409 `TRIAL_STATE_INVALID`) = **새 광고 신청 건**을 열어 지금 결제 건으로 잇고 모집 인원으로 견적(감사 `TRIAL_PAYMENT_REQUEST`). 닫힌 건이 있는 동안 고치기는 금액만 두고 새 건에 굳힌다. 예전 건은 그대로 남고 이력 표 `trial_biz_applications` 로 어느 모집의 건인지 찾는다(관리자 신청 건 상세 `content.currentPayment` false). 공개 뒤 광고 쪽 닫힘은 아래 후불과 같이 모집 취소.
  - V244 전에 만든 행(로컬 데이터뿐)도 선결제 기본 — 금액 없는 초안은 결제 상태 `AWAITING_QUOTE_REQUEST`(저장하거나 `REQUEST_PAYMENT` 로 견적), 이미 연 금액 없는 모집은 뽑기 때 후불처럼 굳힌다.
- **후불(POSTPAID)** — 아래 V242 규칙 그대로(뽑기 뒤 뽑힌 인원으로 견적 · 결제 전 입고·발송 409 · 결제 전 뽑힌 줄 취소는 다시 셈 · 광고 쪽 닫힘 → 모집 취소).
- **흐름(후불)**: 뽑기 = 견적 굳힘(인원당 단가 × 인원 · 건당 단가, 운영자 대신 발송이면 + 1명당 발송비 × 인원). 신청 건 줄 = 상품 줄 `TRIAL` + 발송비 줄 `TRIAL_SHIPPING`(새 품목, 요금표 행은 받기 꺼짐·단가 0 — 금액 원천은 TRIAL 행). 금액 > 0 이면 건 `user_id` = 광고주의 사업자 회원(없으면 null — 수동만) · 처리 기록 `TRIAL_QUOTED` · 운영자 알림 `AD_REVIEW_PENDING` → 광고 심사 큐 「심사 대기」. 0원·뽑기 전(견적 0)은 큐·건수·상세(404) 밖. (선결제도 줄·회원·알림·큐는 같다)
- **승인**(`POST /api/admin/biz-applications/{id}/approve`): 견적 그대로(`supply` 는 비우거나 견적 공급가 — 다르면 400 `fields.supply`), 기간·자리·일할 검사 없음, `deadlineAt` 비우면 승인 + `irobit.biz.payment-hours`(72h) / `deposit-days`(7일). 온라인은 건 회원이 있어야(400 `fields.method`) + 운영 주체 판매 단계(409 `BIZ_OPERATOR_STAGE`). 자기 승인 규칙은 운영자 등록 건과 같다. 그 뒤 결제·입금·확정은 기존 흐름.
- **결제 전 발송 막기**: 금액 > 0 인데 건이 `SCHEDULED` 가 아니면 입고·발송(운영자·광고주) 409 **`TRIAL_PAYMENT_PENDING`**.
- **모집 취소**: 준비~진행 중(발송 전)만 — 발송(송장·받음·사용기 연결)이 있으면 409 **`TRIAL_SHIPPING_STARTED`**(모집을 끝내고 광고 심사 추가 환불). 결제 전 = 건 취소(입금 기록 있으면 수동 전액 환불) · 결제 뒤 = 전액 환불(일할 없음). 신청 대기 = 안 뽑힘 알림 · 뽑힌 줄 = 취소 + 알림 `TRIAL_CANCELLED_BY_OPERATOR` · 주소 파기 · 감사 `TRIAL_CANCEL`.
- **끝냄(L5)**: 결제 전 건(0원 포함) = 건 취소(`CANCELED`, 처리 기록 note `TRIAL_CLOSED`) · 결제 끝 건 = 상태 그대로 `closed_at` + 처리 기록 `TRIAL_CLOSED`(관리자 상태 `ENDED`·큐 「종료」, 사업자 묶음 `ENDED`; 끝나기 전은 `RUNNING`·큐 「진행」). 끝내 보내지 않은 줄 주소 파기. 하루 배치 자동 끝남도 같다.
- **광고 쪽 → 모집**: 결제 기한 만료·반려·운영자 취소(결제 뒤면 발송 전만)·환불 승인 → 모집 취소(선결제 초안은 취소하지 않고 그대로 — 위 「결제 다시 요청」). 사업자는 체험단 건을 취소·소재 바꾸기·환불 요청할 수 없다(409 `BIZ_APPLICATION_STATE_CHANGED`) — 결제만. 시작·종료 임박 알림·대시보드 광고 예약 수에서 뺀다.
- **사업자 정지·탈퇴(D7)**: 준비·모집 중·뽑는 중 모집 = 취소(건도 취소, 정지 계획과 무관), 진행 중 = 그대로. 체험단 건은 광고 건 셈·처리·탈퇴 막기에서 빠지고 `impact`·`counts` 에 `trialRecruiting`·`trialRunning` 으로 따로.
- **뽑힌 줄 취소(D9)** `POST /api/admin/trials/participants/{id}/cancel` `{ reason 1~200 }`(`trial.manage`·`ad.manage`) → 모집 상세. 진행 중 · 아무것도 시작 안 된 줄만(409 `TRIAL_SHIPPING_STARTED`·`TRIAL_PARTICIPANT_CANCELLED`·`TRIAL_STATE_INVALID`). 주소 파기 · 알림 `TRIAL_CANCELLED_BY_OPERATOR` · 뽑힌 수 다시 셈 · 금액은 선결제면 늘 그대로, 후불은 결제 전(심사 대기 · 입금 기록 없는 결제·입금 대기 — 승인 뒤면 심사 대기로 되돌림, 처리 기록 `TRIAL_REQUOTED`)만 다시 셈, 결제 뒤 그대로 · 감사 `TRIAL_PARTICIPANT_CANCEL`. 취소한 줄은 발송·사용기 연결·자동 끝남 판정에서 빠진다.
- **응답**: 모집 상세(관리자·광고주) `paymentTiming` · `payment { timing, required, status, shippingAllowed, publishAllowed, requestAllowed, bizApplicationId, bizApplicationNo, bizStatus, supply, vat, total, method, deadlineAt, paidAt, payOnline }` — `status` = `BEFORE_SELECTION`(후불 뽑기 전) · `AWAITING_QUOTE_REQUEST`(선결제 견적 전 — V244 전 초안) · `NOT_REQUIRED` · `AWAITING_APPROVAL` · `AWAITING_PAYMENT` · `AWAITING_DEPOSIT` · `PAID` · `CANCELED`(후불은 반려·만료도 이 값) · `EXPIRED`·`REJECTED`(선결제만). `publishAllowed` = 선결제 공개 가능(0원·결제 끝, 후불은 늘 true) · `requestAllowed` = 선결제 초안의 「결제 (다시) 요청」 가능. 목록 줄 `paymentStatus`·`paymentTiming` · `counts.cancelled` · 뽑힌 줄 `cancelledAt`·`cancelReason` · 내 체험단 `participation.cancelledAt`. 관리자 신청 건 상세 `content`(type TRIAL · trialId · trialTitle · trialStatus · productName · form · shipper · selectedCount · priceUnit · unitPrice · shippingFeePerPerson · amount · **paymentTiming · recruitCount · currentPayment**) · `quote.lines[].unit` POST|PERSON. 사업자 신청 건 상세 `trial { trialId, title, status, productName, form, shipper, selectedCount, priceUnit, unitPrice, shippingFeePerPerson, amount, paymentTiming, recruitCount }`(선결제 결제는 이 신청 건 화면에서도, 광고주 체험단 화면에서도 — 아래 「후속 고치기」). 사업자 관리 `impact`·서비스 카드 `impact` 에 `trialRecruiting`·`trialRunning`.
- **시험**: api `TrialPrepaidTest` 5(선결제) · `TrialPaymentTest` 6(후불 — 모집을 POSTPAID 로 만든다) · admin `TrialPaymentAdminHttpTest` 2(후불 1 + 선결제 1) · `DashboardAdGadgetStoreJpaTest`(체험단 제외) · 기존 `TrialApiTest`(뽑기 금액 시험은 POSTPAID).

## 점검 고치기 (서버 구현 2026-11-17, shared `V243__trial_audit_fix.sql` — 점검 결정 D2·D3·D4·D5·D6·D8·D10·D11·D12·D13·D14)
- **협찬 숨김(D2)**: 확인하면 글은 SPONSORED 고정 — 글쓴이가 고치기로 다른 값을 보내면 400 `fields.purchaseSource`(같은 값·칸 없음은 그대로). 글 응답 `purchase.locked`(체험단 연결 글·협찬 고정 글 true). `POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm` 본문 `{ note?, sanction? }`(`sanction` = 신고 처리와 같은 `{ kind, skipStep, confirmNickname, reason }`) → **200** `data` = 곁들인 제재 줄(없으면 null) — 제재 권한(`sanction.manage`) 없으면 403·전부 롤백. 그 글의 열린 신고 사건(신고가 모두 협찬 숨김일 때만)도 함께 닫는다: 확인 = 조치 **`SPONSOR_LABEL`**(신고 ACCEPTED) · 무시 = DISMISSED. 반대로 신고 관리에서 사건을 닫으면 확인할 일 줄도 닫힌다(사유 코드 SPONSOR_HIDDEN 으로 조치 = 협찬 고정 확인, 그 밖 조치·무시 = 무시). 신고 관리 조치·무시의 사유 코드에 SPONSOR_HIDDEN 을 고를 수 있다(그 사유 신고가 든 글 사건).
- **사진 비공개(D3)**: `trial_photos.content`(다시 인코딩한 JPEG 의 `DataCipher` 바이트 암호문, 용도 `trial.photo`, `DataKeyColumns` 등록) — `image_key` 칸 없앰(옛 줄은 「파기됨」, 마이그레이션 주석). 올리기 응답 `{ id, kind, key: null, url }`, 사진 `{ id, kind, url, createdAt, purged }` — `url` = 관리자 `/api/admin/trials/photos/{id}`(`trial.manage`·`ad.manage`·`trial.review`) · 광고주 `/api/business/trials/photos/{id}`(자기 모집의 붙은 사진 + 자기가 올린 붙기 전 사진) · 회원 `/api/me/trials/photos/{id}`(내 파손 신고 사진만). 바이트·`no-store` · 404 `TRIAL_PHOTO_NOT_FOUND` · 410 `TRIAL_PHOTO_PURGED`(url null). 보관: 모집 끝·취소 뒤 `trial_settings.photo_keep_days`(설정 `photoKeepDays`, 기본 90, 1~365) — 그 모집에 판정 안 된 파손 신고가 있으면 판정 뒤 같은 일수, 하루 배치가 바이트를 지운다(줄은 남는다).
- **운영자 주소(D4)**: 관리자 모집 상세 뽑힌 줄은 가린 값(`recipient` 홍*동 · `phone` 010-****-5678 · `address1` 앞 두 어절 · `address2`·`memo` null · **`addressMasked: true`**). `POST /api/admin/trials/participants/{id}/address/reveal` `{ reason 5~200 }`(권한 **`trial.address.reveal`** — V243 이 `commerce.address.reveal`·`trial.manage` 가진 역할에 줌) → `{ recipient, phone, postcode, address1, address2, memo, hideAt }`(10분) · 열람 기록 `trial_address_views` · 감사 `TRIAL_ADDRESS_REVEAL`(개인정보). 400 `fields.reason` · 409 `TRIAL_WRONG_SHIPPER`(광고주 발송 모집) · 410 `TRIAL_ADDRESS_PURGED`.
- **광고주 자격(D5)**: 사업자 신원 승인(재심사 중 허용) + 광고 서비스 승인 + 광고주 보관 아님 — 아니면 모든 광고주 화면 API 403 **`TRIAL_ADVERTISER_BLOCKED`**.
- **고치기(D6)**: 신청자가 있으면 광고주·형태·보내는 쪽 바꾸기·제품값 올리기 409 **`TRIAL_APPLICANTS_LOCKED`**(내리기는 된다 — 형태·보내는 쪽도 예전 `TRIAL_STATE_INVALID` 에서 이 코드로).
- **탈퇴(D8)**: 대여형을 보냈는데 반납·미반납 확정 전(취소 아님)이면 남은 일 **`TRIAL_RETURN`**(라벨 = 모집 제목, 링크 `/me/trials`) — 공구 진행 중 주문처럼 탈퇴가 예약으로 기다린다. 완료 때 신청 배송지 사본 즉시 파기 · 구매 인증 사진 파기(대기 인증은 자동 반려).
- **D9**: 결제 연결에서 끝남(뽑힌 줄 취소·끝낼 때 주소 파기). 보내지 않은 줄은 운영자가 취소하기 전까지 자동 끝남을 막는다(가정 — 아래).
- **입고 부족(D10)**: 입고 수량 < 뽑힌 인원(취소 뺀)이면 광고주 회원 알림 **`TRIAL_STOCK_SHORT`**(누르면 `/business/trials/{id}`) + 관리자 알림 **`TRIAL_STOCK_SHORTAGE`**(문구 대상 코드가 회원 알림과 겹치지 않게 이름이 다르다).
- **섞인 목록(D11)**: 최신·인기·홈 가젯·하이라이트·소모임 탭 등 섞인 목록 줄(`PostFeedRowView`)에도 `purchaseSource`·`purchaseProofApproved`.
- **반납 받음(D12)**: `trial.manage`·`ad.manage` 만(`trial.review` 403).
- **작은 것(D13)**: L1 리뷰어 승인 = 그때 센 약속 지킴 단계 · L2 시스템 회수 사유는 번들 문구(`title.revoke.system.*`)로 응답 · L3 구입 경로가 SELF 가 아니면 구매 인증 칩 숨김(목록·상세·`purchase=PROOF` 거르기, 포인트 회수 없음), 지운 글의 인증 승인 409 **`PURCHASE_PROOF_POST_DELETED`**, 대기 인증은 작성자 삭제 때·하루 배치가 자동 반려·파기 · L4 체험단 사용기를 작성자가 지우면 연결을 끊고 「지킴」을 되돌려 리뷰어 단계를 다시 정함(낮출 수 있다) · L7 파손 신고는 반납 받은 뒤만(409 **`TRIAL_NOT_RETURNED`**), 반송 송장은 진행 중·끝난 모집만(409 `TRIAL_STATE_INVALID`) · M4 반납 독촉·주소 파기·사진 파기 후보는 때가 된 것만 거르고 ID 커서로 끝까지, 구매 인증 파기·자동 반려는 묶음이 빌 때까지 되풀이 · L8 확인할 일 대기 수는 종류마다 센 질의 한 번.
- **가정**: 광고주 보관 검사는 서비스에 두었지만 사업자 신원에 이어진 광고주는 보관할 수 없다(V131). 협찬 숨김 확인에서 신고 사건은 신고가 모두 협찬 숨김일 때만 닫는다(섞이면 신고 관리에서). 발송 안 된 뽑힌 줄은 자동 끝남을 막는다 — 운영자가 취소(D9)하거나 직접 끝낸다.
- **시험**: api `TrialAuditFixTest` 13 · admin `TrialAuditFixAdminHttpTest` 9 · 기존 `TrialApiTest`(파손 = 반납 뒤·운영자 가린 주소로 기대값 갱신)·`TrialAdminHttpTest`(협찬 확인 200).

## 후속 고치기 (서버 2026-11-17, 마이그레이션 없음)
- **광고주 화면의 공개 전 선결제 모집**: `GET /api/business/trials`·`GET /api/business/trials/{id}` 에 초안도 싣는다 — 선결제 · 견적 금액 > 0 · 지금 결제 건이 심사 대기·결제 대기·입금 대기·결제 끝·만료·반려일 때만(`status` = `DRAFT` → 화면 「공개 전」). 0원·견적 전(`AWAITING_QUOTE_REQUEST`)·후불 초안·결제 건이 취소(`CANCELED`)된 초안은 여전히 숨김(목록에 없고 상세 404 `TRIAL_NOT_FOUND`). 상세 `trial.payment` 는 같은 모양 — `payOnline` 이면 `POST /api/business/applications/{payment.bizApplicationId}/payment` 로 온라인 결제. 초안의 처리(사진 올리기·발송·추천 등)는 그대로 404.
- **협찬 숨김 줄 제재 미리보기**: 확인할 일 `SPONSOR_HIDDEN` 줄 `refs` 에 `anonymous`("true"|"false") · `sanctionable`(작성 회원이 있고 파기 안 됨) · `anonymousPostId`(익명 글이고 sanctionable 일 때만 = 글 ID). 익명 글은 `member` 없이 `GET /api/admin/sanctions/preview?anonymousPostId=` (신고 사건 작성자 카드의 봉인 미리보기와 같은 API) → 확인 본문 `sanction` 은 닉네임 확인 없이(서버가 봉인 제재). 회원 글은 `member.id` 로 `?userId=`.
- **데모 설치 칭호 기간**: 설치기가 `member_titles.period`('YYYY-MM'·'YYYY')·`title_monthly_candidates.month` 를 팩 전체에 같은 달 수·해 수만큼 옮긴다(팩 만든 시각과 옮긴 시각의 한국 시간 연월 차이). 줄마다 받은 때로 셈하지 않는다 — 이웃 달 줄이 한 달로 모여 유일 색인에 걸릴 수 있어서.
- **설치 프리셋 체험단 말머리**: `irobit.structure.json` 사용기(slug review) 게시판에 공통 말머리 `TRIAL`(「체험단」, 켜짐, 스텝 전용 아님, sort 20) 줄. 첫 체험단 사용기 때 서버는 이 줄을 그대로 쓴다. 구조를 넣지 못했을 때의 목록(irobit.json)에는 넣지 않는다(내보내기 스크립트도 뺀다).
- **시험**: api `TrialPrepaidTest` +1 · admin `TrialAuditFixAdminHttpTest` +1 · api `DemoPeriodShiftTest` 4 · `TrialPrefixPresetTest` 1.
