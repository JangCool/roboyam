# 커머스 2b 계약 — 공구(현금 결제) (ERD·패키지 **승인 2026-10-28** — 「지금까지 확정된 상태로 진행」, 추천안 전부 포함)

**의도**: 운영자가 직접 파는 실물 공동구매를 연다 — 공구 등록·모집·참여(선점 → 2a 결제 모듈로 결제)·마감 판정·발주·발송·취소·반품·환불·배송지 보호·Q&A 까지. 성사형(`min_qty > 0`)과 선착순(`min_qty = 0`)을 같은 모델로, 발송 시점(`fulfillment`)은 따로 고른다(D1).
정책 원천은 `docs/domain/commerce.md` 10절(공구)·4·12·13·14·15·17·18·19·20절이고, 결제는 `commerce-payment-contract.md`(`PaymentOrderHandler`·`payment_cancels`·확정 코드), 0단계 장치(본인인증·기능 제한·멱등·속도 제한·메일 대기열·필드 암호화·워커 기록·시험 DB)는 `commerce-foundation-contract.md`. 이 문서는 2b 에서 정할 구조와 결정만 적는다.
**테스트**: commerce.md 와 이 문서의 규칙은 모두 테스트로 막고 통과해야 끝난다(CLAUDE.md 커머스 예외, commerce.md 18절 — P3·P3b·P15·P16·P16b~e·P25·P26·P33~P42·P35 와 불변식 + 아래 「새 규칙 → 테스트」 G1~G12).
**범위 밖(2b)**: 공구 포인트 결제(11절 — 3+ 단계, 표 열만 둔다) · 회원 주최 공구 · 택배사 API · 쿠폰 · 매출·정산 화면과 세금계산서 요청·발행 표시(16-2 — 21-1 「4」) · 리뷰·구매 확정 단추(community 에도 없다) · 저장된 배송지 불러오기 · 1:1 문의(roboyam 에 없다 — 공구 밖 문의는 사업자 정보의 전화·이메일).
**community 대조(2026-10-28)**: `D:\project\community\workspace\community\docs\` 의 `api-commerce.md`(C)·`api-commerce-admin.md`(A)·`api-deal-shop.md`(S)·`features.md` 23절·`decisions.md`(D 450~513)와 맞춰 봤다. 큰 줄기는 같고 roboyam 이 더 엄격하다(늦은 결제는 살리지 않고 자동 취소 — community 는 재고가 있으면 살림). 에스크로·영업일·고객 택배비 보상·계좌 송금은 community 에 없어 roboyam 설계가 정본이다. 가져온 것은 결정 13~22 와 화면 목록에 적었다.
**사용자 결정(2026-10-28)**: 결정 15(반품 처리 방법 선택)·17(추가 입고 = 늘리기 + 추가 차수)·21(공구 Q&A)·23(한 주문에 여러 옵션)·24(차수 사이 1인 한도는 공구별 선택, 기본 독립)·25(다시 판매·추가 입고도 마감 연장 조건)·26(Q&A 개인정보 자동 비밀글 + 안내 · 신고 대상 · 3년 보관 · 답변 수정 이력). 그 뒤 처음 보는 검토자 점검(같은 날)에서 나온 빈 곳은 「수량·상태 규칙」·「Q&A 규칙」에 메웠다. 두 번째 검토(Fable, 같은 날 — 주문 줄 전환·결제 모듈 경계 중심)의 빈 곳과 사용자 결정 42~45 는 「2차 검토 보강」에 메웠다. 최종 검토(Fable 넷 — 트랜잭션·재고·금액·법/실제 공구 관행, 같은 날)와 사용자 결정 52~54(발송 지연은 통지 + 취소권 · 일부 옵션 공급 불가는 답이 없으면 나머지만 받기 · 하자·오배송만 교환)는 「최종 검토 보강」에 메웠다.

## 작업 단위와 순서 (제안)
| 단위 | 내용 | 담당 | 상태 |
|---|---|---|---|
| 2b-1 화면 목업 | 아래 「화면 목록」(Q&A 제외) | frontend | **화면 확정 2026-10-28**(결정 87~89·옵션 종류 코드 반영 뒤 사용자 확정, 브랜치 `claude/commerce-2b-1-groupbuy-screens-677185` 4cf01bf8) — **develop 병합 2026-10-29**(2b-4 브랜치에 합쳐 함께 들어옴, 테마 작업 커밋 뒤) |
| 2b-2 공구 핵심 서버 | V101, 공구·옵션·배송비 정책·수정 이력, 주문·주문 줄(자격·멱등·선점)·`GroupBuyPaymentHandler`·선점 만료·이탈 제한, 상태 워커(모집 시작·마감·연장·판정·불발 환불), 본인 취소, 추가 입고·줄이기·추가 차수, 불변식, 규칙 테스트 | backend | **구현 완료**(2026-10-28, 브랜치 `claude/commerce-2b-2-groupbuy-core-d751c0` — 아래 「2b-2 구현 결과」) — **develop 병합 2026-10-28**(11420d86 까지, V101). 병합 뒤 확인(메인 작업 트리를 임시 폴더에 복사해 실행 — 띄운 서버의 jar 를 덮지 않게): shared `com.community.groupbuy.*`·`com.community.payment.*` **224 통과**, admin `GroupBuyStateSchedulerTest`·`AuditRetentionAdminTest`·`com.community.payment.*` **25 통과**, api `IdentityHttpTest`·`com.community.payment.*` **13 통과**, 실패 0 |
| 2b-3 발송·요청·환불 서버 | V102, 발주·준비중·발송·배송완료(일괄 부분 성공)·에스크로 송장 등록, 택배사, 배송지 열람·수정·명단 내보내기, 취소·반품 요청(줄·수량 단위)·처리 방법·`GroupBuyRefundCalculator`·보상액·운영 환불, 규칙 테스트 | backend | **구현 완료**(2026-10-28, 브랜치 `claude/festive-cori-faed64` — 아래 「2b-3 구현 결과」) — **develop 병합 2026-10-28**(V102, 7a7cd545) · 보강 V103(f60a5033) **develop 병합 2026-10-29** |
| 2b-4 설정·알림 + 관리자 API + 연결 | V104(2b-3 보강이 V103 을 썼다), 판매 스위치·체크리스트·사업자 정보·공구 설정, 회원 알림·메일(계약 통지 포함)·관심·수량 풀림·연장·차수 알림, 운영자 알림·기한 경고, 개인정보 파기 pg_cron, 관리자·회원 API 정리, 화면 연결 | backend → frontend | **구현 완료**(2026-10-28, 브랜치 `claude/commerce-2b-4-groupbuy-api-9e8cb4` 57cb2f3d — 아래 「2b-4 구현 결과」, 2b-1 화면 브랜치를 합쳐 연결) — **develop 병합 2026-10-29**(2b-1 화면 포함) |
| 2b-5 실제 확인 | 포트원 테스트 채널로 공구 결제·불발 환불·발송 후 반품 환불 사람 확인(공개 주소가 필요한 웹훅·에스크로는 dev 서버) | 사용자 + 세션 | **로컬 확인 2026-10-29**(아래 「2b-5 로컬 실제 확인」) — 웹훅·에스크로는 dev 서버에서 |
| 2b-6 공구 Q&A · 리뷰 | V106(V105 는 테마·레이아웃이 먼저 썼다 — 2026-10-29) 하나에 Q&A 와 리뷰, 아래 「Q&A 규칙」·「리뷰 규칙」 — 화면 목업 → 확정 → 서버 → 연결 | frontend → backend | **화면 확정·서버·연결 2026-10-29**(상세 탭 구조·리뷰·Q&A 함께 — 아래 「2b-6 결과」) — **develop 병합 2026-10-29**(2ebae0b4, 병합본 테스트 shared 15·admin 9·api 7·두 앱 타입 검사 통과, 실제 API 로 문의 쓰기·관리자 목록 확인) |
순서: 2b-1 ∥ 2b-2 → 화면 확정 → 2b-3 → 2b-4 → 2b-5, 2b-6 은 2b-4 다음(구매 흐름을 막지 않는다). 2b-2 는 화면과 무관한 서버 핵심이라 2a 와 같이 화면 확정 전에 병행한다(승인 필요). 세션마다 자기 worktree.

## 화면 목록 (2b-1)
- **회원(user-web)**
  - `/group-buys` — 탭 진행·예정·종료, 종료 사유(`SOLD_OUT` 다 팔림 · `TIME_UP` 기간 끝 · `CLOSED` 운영 종료), 예정 공구 가격 미리 공개, 마감 임박·남은 수량 적음 표시(남은 10% 이하 또는 5개 이하), 성사형 진행률, 「N차」 표시. (C 72·78, D 450)
  - `/group-buys/{id}` — 이미지·설명·옵션별 남은 수량, 배송비·반품 비용 금액 고지(10-8), 연장 안내, 「추가 입고 N개」·「N차 공구」 표시, **다 팔림과 「잠시 구매 불가(다른 분이 결제 중)」 구분**(공구·옵션 단위), 수량 풀림·마감 임박 알림 신청, **다 팔려 끝난 공구엔 「다음 차수 알림 받기」**(결정 17), 하단 사업자 정보(공개 info API), 단추 상태는 서버가 정한 `actions`·`purchase.reason`, Q&A(2b-6). (C 92·157, D 450)
  - `/group-buys/{id}/order` — **옵션 여러 줄 × 수량**(결정 23), 배송지(우편번호 찾기, 실패 시 직접 입력), 동의 3종, 본인인증 게이트, 결제대금예치 고지, **견적과 합계가 다르면 결제창 대신 「금액이 바뀌었어요」 확인** → 결제창 → 결과. (D 513)
  - `/me/orders`·`/me/orders/{orderNo}` — 주문 줄·결제·배송 조회, 가린 배송지·전체 보기·수정(기한은 결정 30), 본인 취소, 취소·반품 요청(**줄·수량을 골라**, 환불 예정 금액 미리 보기, 하자·오배송 사진 최대 3장), 요청 철회, 환불 진행 중/완료 표시(결정 16). (C 175·178·181)
- **관리자(admin-web)** — 묶음 「공구」
  - 공구 목록·등록·수정(모집 뒤에는 설명·이미지만)·모집 시작·조기 마감·**중단**(확인 창: 「환불 N명 · 총 N원 / 이미 발송 M명은 그대로」 — 결정 29)·수정 이력·**「추가 차수로 열기」**(결정 17·31)
  - **수량 화면**: 공구·옵션별 총량·확정·선점·남음·반품·다시 판매, 선점 목록(남은 시간), 수량 변동 내역, **추가 입고·줄이기(옵션 지정)**. (A 6절)
  - 주문: 주문 번호·닉네임·회원 번호로 찾기(배송지는 암호문이라 찾지 못한다는 안내), 상태 탭 건수, 상세(주문 줄, 배송지 열람 사유, 운영 메모, 이력), 발주·준비중·발송(화면 안 표로 택배사·송장 일괄 입력, 200건, 행 단위 결과)·송장 정정·배송완료(일괄), **운영 환불**(결정 32), 배송 명단 내보내기. (A 95·120·121·123·140)
  - 취소·반품 요청 대기열: 경과 영업일, 환불 계산 미리 보기, 승인·거절·회수 확인·보상액, **줄마다 처리 방법과 수량**(다시 판매 n · 폐기 · 공급처 반송 · 보관 — 결정 15).
  - 공구 Q&A 대기열(2b-6): 답변 안 한 질문, 답변·수정(이력), 숨김, 신고 처리.
  - 대시보드 커머스 칸(19절): 준비 대기·열린 요청·결제 이상·곧 만료될 선점·남은 수량 적은 공구·**발송 뒤 N일 지난 SHIPPED**(배송완료 처리 누락 — 청약철회 기한이 시작되지 않는다)·답변 안 한 Q&A. (A 211)
  - `/settings/commerce` 구획: 배송비 정책(기간별 줄·왕복 경고) · 택배사 · 사업자 정보 · 판매 스위치·체크리스트 · 공구 설정.

## 2b-1 화면 목업 (2026-10-28 — 목업 데이터, 화면 확인 대기)
모든 서버 호출은 `composables/groupbuy/api/use…Api.ts`·`composables/commerce/api/use…Api.ts` 한 겹에 `// MOCK:`/`// REAL:` 로 나란히 있다. 금액·남은 수량·환불 미리 보기는 서버가 계산한 값(목업)을 보여 주기만 한다. 오류·빈 목록·금액 바뀜 같은 경우는 `?mock=` 스위치로 연다(목록은 각 목업 파일 머리). 요청·응답 전체 모양은 각 `*.types.ts` 에 서버 DTO 후보로 적었다.

### 화면 경로
- **user-web**: `/group-buys`(탭 `?phase=`) · `/group-buys/{id}` · `/group-buys/{id}/order` · `/me/orders` · `/me/orders/{orderNo}` · 사이트 푸터(`AppFooter.vue` — 판매 스위치 켜짐 + 사업자 정보가 다 찼을 때만 `CommerceBusinessInfo`, 공구 상세 하단도 같은 컴포넌트).
- **admin-web**: 보통 모드 새 묶음 「공구」(결제 묶음 앞, 아이콘 `pi pi-truck`) — `/group-buys`(목록)·`/group-buys/new`·`/group-buys/{id}`(등록·수정·운영 동작) · `/group-buy-stock?id=`(수량) · `/group-buy-orders`(주문) · `/group-buy-requests`(취소·반품 요청) · 대시보드 `/` 커머스 일곱 칸 · `/settings/commerce` 에 구획 다섯(판매 스위치·체크리스트 / 사업자 정보 / 배송비 정책 / 택배사 / 공구 설정). Q&A(2b-6)는 만들지 않았다.

### 구현한 동작 (요점)
- **결제창 공용 컴포저블** `apps/user-web/app/composables/payment/usePaymentCheckout.ts`: `/dev/payment` 의 가짜 결제창·포트원 SDK·창 닫기 판정(`UtilPortone`)·리디렉션 복원·완료 요청을 옮겼다(`/dev/payment` 는 script 만 바꿈, 동작·testid 그대로). 공구용 감싸기 `useGroupBuyCheckout` — 주문 결제·결제 다시 열기·반품비 부족분 결제가 같이 쓰고 리디렉션은 `/me/orders/{orderNo}`(결정 13).
- **주문서**(결정 59): 옵션 여러 줄 × 수량(같은 옵션 두 줄 불가), 옵션·수량·동의 기본 선택 없음, 판매조건·배송비·반품비·발송 예정일·청약철회 제한·정상가 기준을 처음부터, 선불카드 안내, 에스크로 고지(체크 칸 없음 — 결정 4), 우편번호 찾기(다음 우편번호 서비스를 시트 안에 바로 심음 — 실패하면 직접 입력), 견적은 서버 값만(줄을 바꾸면 다시 물음) → 본인인증 게이트 → 주문(`expectedAmount` + 멱등 키) → 409 `GROUP_BUY_AMOUNT_CHANGED` 면 결제창 대신 확인 창 → 결제창, 선점 남은 시간·결제 다시 열기(`GROUP_BUY_HOLD_EXPIRED`).
- **상세**: 다 팔림 ↔ 잠시 구매 불가 구분(공구·옵션), 알림 신청 `RESTOCK`(옵션 단위)·`CLOSING_SOON`·`NEXT_ROUND`, 연장 안내·연장 중, 추가 입고 N개·N차, 발송 지연 공지, 단추는 서버 `actions`·`purchase.reason` 만 본다.
- **내 주문 상세**: 가린 배송지 → 전체 보기 → 수정(서버 `actions`), 본인 즉시 취소·취소 요청·반품 요청(줄·수량, 줄마다 사유, 하자·오배송 사진 3장 + 환불/교환 — 고른 줄이 모두 하자·오배송일 때만 교환), **회수 방식별 환불 미리 보기 나란히**, 마이너스면 「직접 발송으로 바꾸기」/「부족분 결제하고 접수」(결정 68), 요청 철회·요청 상태·거절 사유 문구, 환불 진행 표시(결정 16), 발송 지연 「바로 전액 취소」(52), 공급 불가 「나머지만 받기 / 전체 취소」 + 기한(53).
- **관리자 공구**: DRAFT 는 전체 저장(`PUT`), 모집 뒤엔 요약·사양·설명·이미지만(`PATCH`, 나머지 잠김 표시), 정상가 기준·「최저가」·「한정」·「개봉하면 반품 불가」·「재판매」 문구 경고, 모집 시작·조기 마감·발주·추가 차수(복사 범위·경고·막힘 이유)·발송 지연 공지·옵션 공급 불가(서버 미리 보기)·중단(서버 미리 보기 「환불 N명·총 N원 / 이미 발송 M명」)·수정 이력 서랍.
- **수량**: 공구·옵션별 최대·처음·추가 입고·확정·선점·남음·반품·다시 판매·하한, 선점 목록(남은 초), 이동 내역(아홉 종류), 추가 입고·줄이기(옵션·사유, `GROUP_BUY_QTY_BELOW_FLOOR`).
- **주문**: 번호·닉네임·회원 번호 찾기(배송지 검색 불가 안내), 상태 탭 건수, 준비중·발송(표 안 택배사·송장, 200건)·배송완료 일괄 + 줄 단위 결과, 발주·배송 명단 내보내기(사유), 상세 서랍(배송지 열람 사유 → 남은 시간 뒤 가림, 열람 기록, 환불 기록, 메모, 이력, 송장 정정, 운영 환불 — 발송 전 줄·수량 / 배송완료 금액만 / SHIPPED 막힘, 사업자 귀책, 고액 문턱 안내·승인 대기).
- **요청 대기열**: 경과 영업일 경고(2)·심각(3), 줄마다 사유·수량·사진, `preview_amount` ↔ 다시 계산한 값 비교(72), 승인·거절(사유 코드 + 근거 사진, 제한 문구 없는 공구의 `USED_VALUE_LOSS` 경고)·회수 송장·회수 확인(반환받은 날)·완료(줄마다 처리 방법별 수량, `RESELL` 은 RECRUITING + 단순 변심 줄만)·보상액·교환 재발송·환불로 바꾸기·반품비 추가 결제 상태.
- **설정**: 판매 스위치(켜기 최고 관리자 + 체크리스트, 사유) · 사업자 정보(결정 18 칸, 등록번호 체크섬) · 배송비 정책(시작된 줄 잠금·새 줄·왕복 경고) · 택배사(시드 6, 템플릿 검사, 이력 있는 코드 삭제 불가) · 공구 설정(결정 10 값 + 휴일 + 즉시 발송 기본 3영업일 + 공급 불가 선택 기한). 기존 「요청 제한」 구획은 내용 그대로, 저장 줄만 구획 안 `SaveBar` 로(구획마다 저장).

### 목업 가정 (서버 단계에서 확정)
- 예정 공구는 상태 이름이 계약에 없어 user-web 목업은 `status` + 서버가 주는 `phase`(`UPCOMING`·`ONGOING`·`ENDED`)로 탭을 가른다. 관리자 「모집 시작」은 DRAFT → RECRUITING 이고 `start_at` 이 미래면 회원 화면은 「예정」.
- **운영자 조기 마감의 `close_reason` = `EARLY_CLOSED`**(사용자 결정 2026-10-28 — 결정 79 에 더함). 두 앱 목업이 이 값을 쓴다(관리자 「조기 마감」, user-web 「조기 마감」 딱지·안내). 중단은 `CANCELED`(「운영 종료」).
- 반품비 부족분 결제 전 요청 상태를 `AWAITING_PAYMENT` 로 두었다(결정 68 「접수 전」 — 이름 미정).
- 「마감 임박」은 서버 `closingSoon`(목업 24시간), 「남은 수량 적음」(≤ 5개 또는 ≤ 10%)만 화면 판정. 선점 「5분」은 문구에 박혀 있다 — 상세 응답에 `holdMinutes` 를 받는 것을 권한다.
- 결제창은 목업에서 늘 가짜 게이트웨이이고 공구 목업이 가짜 결제·완료 요청을 흉내 낸다(REAL 줄은 `usePaymentApi().complete`). 결제 시작 응답에 **구매자 정보 `customer{fullName,phoneNumber,email}`** 를 새로 제안(KG이니시스가 요구 — 서버가 배송지·회원 정보로 채움).
- 모집 뒤 고칠 수 있는 칸은 요약·사양·설명·이미지(제목·청약철회 제한 문구는 잠금). 공구 설정의 최소~최대는 선점 시간 외에 목업이 정했다(서버가 `ranges` 로 준다).
- 배송 명단은 한 번 쓰는 내려받기 주소(`downloadUrl`)로 받는다(`useApiClient` 가 파일 응답을 받지 않음).
- 관리자 메뉴 권한은 조회 `commerce.product.read`(공구·수량)·`commerce.order.read`(주문·요청)로 잡았다(결정 35 에 조회 코드 없음 — 판단). 권한 코드는 프론트 카탈로그에 모두 있고 `featureReady=false` 다 — 서버 `Permission` 에서 켜고 카탈로그를 맞춘다. 서버 `AdminScreenRegistry` 에 네 줄(`groupBuys`·`groupBuyStock`·`groupBuyOrders`·`groupBuyRequests`, 같은 주소·권한·아이콘·차례)을 더해야 한다.
- **우편번호 찾기**(사용자 결정 2026-10-28 — pionworld2 `useDaumPostcode` 방식): 다음(카카오) `postcode.v2.js`(키 없음)를 한 번만 불러 `AppSheet`(모바일 아래 시트) 안 상자에 `embed()` 로 바로 심는다(팝업 차단 회피) — `composables/groupbuy/usePostcodeSearch.ts`. 도로명 주소를 먼저 채우고, 못 불러오면 직접 입력. 서버 일 없음(우편번호·주소는 주문 요청의 배송지 칸 그대로). 사이트 푸터는 데스크톱만 보인다(`DefaultShell` 의 `max-md:hidden`) — 결정 56 「사이트 전체」를 채우려면 모바일 자리를 정해야 한다(테마·셸 작업과 맞춰). 공정위 사업자정보공개 링크는 [확인 필요] 그대로 넣지 않았다.

### 백엔드에 필요한 API 후보 (JSON 카멜 표기, 상태 이름은 계약 그대로)
| 쪽 | 주소 | 요청 → 응답·오류 |
|---|---|---|
| 회원 | `GET /api/group-buys?phase&page` · `GET /api/group-buys/{id}` | 목록 줄(`stockState` AVAILABLE·TEMP_SOLD_OUT·SOLD_OUT, `remainingQty`, `closingSoon`, `roundNo`, `closeReason`) / 상세(+ `images`·`descriptionHtml`·`normalPriceBasis`·`shipPlannedAt`·`shipWithinBusinessDays`·`shipping{fee,freeShippingOver,returnFee,returnFeeMode}`·`withdrawalRestriction`·`addedQty`·`options[]`(옵션별 `remainingQty`·`stockState`)·`delayNotice`·`purchase{reason}`·`actions`·`watches`·`myOrderNo`) · 404 `GROUP_BUY_NOT_FOUND` |
| 회원 | `POST /api/group-buys/{id}/quote` · `POST /api/group-buys/{id}/orders`(멱등 키) | `{items[{optionId,qty}]}` → 줄별 금액·`itemsAmount`·`shippingFee`·`totalAmount` / `{items, address, agreements, expectedAmount}` → `{orderNo, amount, reservedUntil, payment{orderType,orderRefId,expiresAt,checkout,customer}}` · 409 `GROUP_BUY_AMOUNT_CHANGED`(현재 금액 포함)·`NOT_RECRUITING`·`SOLD_OUT`·`OPTION_SOLD_OUT`·`LIMIT_EXCEEDED`·`ALREADY_JOINED` · 403 `SALES_CLOSED` |
| 회원 | `PUT`·`DELETE /api/group-buys/{id}/watches` · `GET /api/commerce/info` | `{kind, optionId}` → 204 / `{salesOpen, ready, business{…결정 18 칸}}` |
| 회원 | `GET /api/me/orders` · `GET /api/me/orders/{orderNo}` | 목록(`attention` PAY·SUPPLY_CHOICE·DELAY·RETURN_FEE) / 상세(줄 `canceledQty`·`returnedQty`·`selectableQty`, 가린 배송지, `shipment`, `delayNotice`, `supplyChoice`, `requests[]`, `refunds[]`(progress IN_PROGRESS·COMPLETED·DELAYED·MANUAL, method), `returnUntil`·`defectReturnUntil`, `actions`) |
| 회원 | `POST /api/me/orders/{orderNo}/payment` · `GET`·`PUT …/address` | 결제 다시 열기(409 `GROUP_BUY_HOLD_EXPIRED`) / 전체 배송지·수정(409 `GROUP_BUY_ADDRESS_LOCKED` — 새 코드) |
| 회원 | `POST …/cancel/preview` · `…/cancel` · `…/requests/preview` · `…/requests` · `…/requests/{id}/payment` · `…/requests/{id}/withdraw` · `…/delay-cancel` · `…/supply-choice` · `…/photos` | 줄·수량 → 환불 나눔 / 요청 미리 보기는 `byMethod{COMPANY_PICKUP, CUSTOMER_SEND}` 각각 `refundAmount`(음수 가능)·나눔 + `feeBearer`·`returnAddress` / 요청 → `{request, returnFeePayment}`(마이너스면 결제 시작) · 409 `GROUP_BUY_REQUEST_OPEN`·`RETURN_WINDOW_CLOSED`·`AMOUNT_CHANGED` |
| 관리자 | `GET /api/admin/group-buys`(+`counts`) · `form-options` · `POST`·`PUT /{id}`(DRAFT) · `PATCH /{id}/content` · `POST images` · `GET /{id}/revisions` | 409 `GROUP_BUY_LOCKED_FIELD`·`VERSION_CONFLICT` · product.manage(+price) |
| 관리자 | `POST /{id}/start` · `/close-early` · `/ordered` · `/rounds` · `GET /{id}/cancel-preview` + `POST /{id}/cancel` · `POST /{id}/delay-notices` · `GET /{id}/supply-failure-preview` + `POST /{id}/supply-failures` | 409 `ROUND_EXISTS`·`ROUND_SOURCE_INVALID`·`SHIPPING_POLICY_MISSING` · 중단 미리 보기 `refundOrders·refundAmount·expireOrders·shippedOrders` · groupbuy.cancel / order.manage + refund.approve |
| 관리자 | `GET /{id}/stock` · `/stock/movements` · `POST /{id}/stock/adjust` | `{optionId?, direction ADD·REDUCE, qty, reason}` · 409 `QTY_BELOW_FLOOR`(floor)·`OPTION_UNLIMITED` · stock.adjust |
| 관리자 | `GET /api/admin/group-buy-orders`(`alert` SHIP_OVERDUE·DELIVERY_STALE) · `/{id}` · `/carriers` · `POST prepare`·`ship`(≤ 200 줄)·`deliver` · `/{id}/address-reveal` · `/memo` · `/tracking` · `/refund-preview` + `/refunds` · `POST export` | 일괄은 `{succeeded, failed, results[{orderId, ok, code, message}]}` · 운영 환불 `{state QUEUED·AWAITING_APPROVAL}`, 409 `REFUND_SHIPPED`·`PAYMENT_UNRECONCILED` · 내보내기 `{fileName,rowCount,downloadUrl}`(export + address.reveal) |
| 관리자 | `GET /api/admin/group-buy-requests`(`elapsedBusinessDays`·`deadlineLevel`) · `/{id}` · `POST approve`·`reject`·`pickup`·`receive`·`complete`(`dispositions[]`·`compensationAmount`·`expectedAmount`)·`exchange-ship`·`convert-to-refund` · `photos` | 409 `REFUND_AMOUNT_CHANGED`·`NOT_RESELLABLE`·`REJECT_REASON_NOT_ALLOWED`·`REQUEST_NOT_OPEN` · order.manage(+refund.approve) |
| 관리자 | `GET /api/admin/commerce/dashboard` · `shipping-policies` · `carriers` · `business-info` · `sales`(+ `checklist/{key}`) · `group-buy-settings` | 대시보드 일곱 칸(권한 없는 칸 null) · 409 `COMMERCE_SHIPPING_POLICY_OVERLAP`·`_STARTED`·`COMMERCE_CARRIER_IN_USE`·`COMMERCE_SALES_NOT_READY` · settings(판매 켜기는 최고 관리자) |
- 목업이 새로 정한 오류 코드(위 「감사·오류 코드 후보」에 없던 것): `GROUP_BUY_VERSION_CONFLICT`·`SHIPPING_POLICY_MISSING`·`NOT_DRAFT`·`NOT_CANCELABLE`·`NOT_ORDERABLE`·`OPTION_UNLIMITED`·`ORDER_NOT_PREPARABLE`·`ORDER_NOT_SHIPPABLE`·`TRACKING_INVALID`·`REFUND_SHIPPED`·`EXPORT_NOT_READY`·`REFUND_AMOUNT_CHANGED`·`REJECT_REASON_NOT_ALLOWED`·`REQUEST_NOT_OPEN`·`ADDRESS_ERASED`·`ADDRESS_LOCKED`·`ORDER_STATE`(모두 `GROUP_BUY_` 접두) · `COMMERCE_SHIPPING_POLICY_OVERLAP`·`_STARTED`·`COMMERCE_CARRIER_IN_USE`·`COMMERCE_SALES_NOT_READY`. 2b-2·2b-3 이 서버 코드를 정할 때 이 목록과 맞춘다.

### 추가분 — 상품정보제공고시 (2026-10-28, 결정 87 — 정본은 develop 의 계약 「상품정보제공고시」 절)
- **관리자** `/group-buys/{id}`·`/group-buys/new` 에 「고시 양식」 구획(`GroupBuyNoticeEditor`·`GroupBuyNoticeForm`, 앵커 `gb-notice`): 양식 더하기(40개 품목 `code` 찾아 고르기) → 그 품목 항목 칸(묶음 줄 `group` 소제목 · 괄호 설명 `description` 도움말 · `conditional` 이면 「해당 없으면 제공 불가 + 사유」 안내 · 품목 `scope`·`warrantyDisadvantageDisclosure` 안내), 일반원칙 2~7 원문 접어 보기. 적용 옵션이 빈 양식 = 기본 양식(공구 전체 — 옵션이 있으면 하나까지), 기본 양식이 없으면 모든 옵션이 어느 양식에 들어야 함(빠진 옵션 이름 표시). 항목마다 값 / 「제공 불가」 + 사유, 날짜 대체 표기·KC 인증번호/면제는 **저장 안 하는 입력 도우미**, 「상세페이지 참조」 경고, 판매자 추가 항목(이름 1~40·내용 1~1,000·20개·순서·지우기·고시 항목/같은 양식 이름 겹침 안내). 모집 시작 409 → 빈 항목 표시·첫 문제로 이동. DRAFT 는 `PUT`, 모집 뒤엔 `PATCH …/content` 에 `notices`(이력 `NOTICE`). 추가 차수에 양식 그대로 복사.
- **회원** `/group-buys/{id}` 상품 설명 다음에 「상품정보제공고시」(`GroupBuyProductNotices`): 양식마다 제목·「품목 (12) 소형전자」·적용 범위·항목 표(묶음 줄이 있으면 묶음 이름을 앞에, 제공 불가는 「제공 불가 — 사유」, `KC_CERT` 딱지) 아래 「판매자 추가 정보」, 양식이 둘 이상이면 접기·「옵션별로 보기」 칩(옵션 이름으로 양식을 찾음), 출처 한 줄(`noticeNo`). 모바일은 「상품정보제공고시 보기」 → `AppSheet`. 주문서 `/group-buys/{id}/order` 판매 조건 아래 같은 링크 → 시트(고른 옵션의 양식을 먼저, 「주문하는 상품」 딱지).
- **API(2b-2 정본에 맞춤)**: `GET /api/admin/group-buys/notice-catalog` → 서버 파일 `notice-catalog-2023-01-01.json` 모양 그대로(`version·title·noticeNo·effectiveDate·transcribedAt·source·transcriptionNote·itemKinds·principles·categories[{no, code, name, scope?, warrantyDisadvantageDisclosure?, items[{no, key, group?, name, description, kind TEXT·DATE_LIKE·KC_CERT·QUALITY_WARRANTY, required, conditional?}]}]`) · 저장 `notices[{categoryCode, title, optionNames, values[{itemKey, value, unavailable, unavailableReason} | {custom: true, itemKey: null, name, value, sortOrder}]}]` · 관리자 응답 `notices[{categoryCode, title, optionNames, values, customItems[{name, value, sortOrder}]}]` · 400 `fields.notices[i].title|categoryCode|optionNames|values[j].unavailableReason|values[j].value|customItems[k].name|customItems[k].value` · 409 `GROUP_BUY_NOTICE_INCOMPLETE` extras `{noNotice, notices[{index, title, categoryCode, missingKeys[]}], unassignedOptions[]}` · 회원 `notices[{categoryCode, categoryNo, categoryName, title, optionNames, rows[{itemKey, no, group, name, description, kind, value, unavailable, unavailableReason}], customItems, catalogVersion, noticeNo}]`(서버가 품목·항목 이름을 붙여 준다 — 가정, 목록 응답엔 없음).
- **목업 가정**: 두 앱 목업이 서버 카탈로그 사본(`…/api/mock/notice-catalog-2023-01-01.json`, 서버 붙으면 지움)을 import 한다. `dateMode`·`kcMode` 는 서버 모양에 없어 화면 도우미로만. `unassignedOptions` = 기본 양식이 없을 때 어느 양식에도 안 든 옵션 이름(화면 해석). 409 `index` 는 0부터(`notices[i]` 와 같다고 가정), 400 `customItems[k]` 의 k 는 요청의 추가 항목 차례(빈 줄 포함)라고 가정. 화면은 적용 옵션을 옵션 줄 키로 들고 저장 때 이름으로 바꾼다(DRAFT 의 저장 전 새 옵션도 이름이 있으면 고를 수 있음). 예시 품목은 로봇(관리자 gb-101~106 · 회원 101~108) + 햅쌀·사과/감귤·고등어/전복·김치·티셔츠·러닝화(관리자 gb-107~112 · 회원 109~114, 이미지는 SVG 데이터 주소).
- **볼 곳**: 관리자 `/group-buys/gb-102`(양식 둘·「상세페이지 참조」 경고) · `gb-105`(DRAFT 일부 비움 → 모집 시작 409) · `gb-108`(옵션마다 양식, 기본 양식 없음) · `gb-109`(DRAFT 다 채움 → 모집 시작 됨) · `gb-110`·`gb-111`·`gb-112`(옵션 여럿 · 기본 양식 하나) · `/group-buys/new` · `?mock=notice-incomplete`·`?mock=notice-catalog-fail` / 회원 `/group-buys/102`·`110`·`111`(옵션 칩) · `109`·`112`(묶음 줄)·`113`·`114` · `/group-buys/110/order`(감귤을 고르고 「상품정보제공고시 보기」). ⚠ 아래 결정 88 반영으로 고시 적용 대상은 「옵션」이 아니라 단품(`skuNames`)이다.

### 추가분 — 옵션 축 · 단품 · 추가 구성상품, 상세 화면에서 고르기 (2026-10-28, 결정 88 — 정본은 develop 의 계약, 서버 모양은 2b-2 커밋 9c23966a)
- **회원 상세**: 축을 차례로 고르기(앞 축 전엔 뒤 축 꺼짐, 판매 안 함 비활성·품절 흐리게 + 「다 팔림 / 잠시 구매 불가」) → 마지막 축은 단품 줄(가격·남은 수량·알림 종·「담기」) → 「선택한 상품」(`GroupBuyPickedLines` — 같은 단품 +1, 수량 ±·빼기, 상한 = 단품 남은 수량과 1인 한도(MAIN 합)) → 「추가 구성상품」(본 상품 없으면 담기 꺼짐, 마지막 MAIN 을 빼면 「추가 구성상품도 뺄까요?」) → 서버 견적(`GroupBuyQuoteSummary`) → 「구매하기」. 축 없는 상품은 본 상품 한 줄이 처음부터 담긴다. 예정·종료·막힘 상태는 「상품별 남은 수량」만.
- **회원 주문서**: 고르기를 없애고 확인만(「상품 다시 고르기」 → 상세). 고른 목록은 주소 쿼리 `?items=skuId:qty,…`(최대 50줄)로 넘기고 상세도 고를 때마다 같은 쿼리로 주소를 고친다(새로고침·로그인 왕복 유지, sessionStorage 안 씀). 없거나 깨진 목록은 안내 후 상세로. **내 주문**: 줄 이름 = 단품 이름, 추가상품 「추가 구성」 딱지, 본 상품을 모두 취소·반품 고르면 「추가 구성상품도 함께 고르기」, 본인 취소 409 `GROUP_BUY_ADDON_ONLY {skuIds}` → 확인 뒤 함께 다시 보냄.
- **관리자 등록**: 「옵션」 구획(`GroupBuyAxesEditor` — 축 3개까지, 값 쉼표 입력, 조합 표 자동(이름 미리 보기·판매가·추가금·한도·판매 스위치), 일괄 입력 도우미, 판매 조합 0개·조합 200 초과 경고) · 「추가 구성상품」 구획(`GroupBuyAddonsEditor` — 이미지·이름·가격·한도(필수)·순서, 20개, 이름 겹침 표시) · 모집 뒤 두 구획 잠김. 고시 적용 대상은 단품 목록, 추가 구성상품 양식은 그 상품 하나만, 양식 없는 추가상품 배지·409 `unassignedSkus`. 수량 화면은 MAIN 만 공구 합계에 든다는 안내 + 추가상품 따로 줄, 판매 끈 조합은 조정 없음. 주문·요청·환불·처리 방법·단품 공급 불가는 「단품」 표시(`GroupBuySkuLabel`).
- **서버 모양(2b-2)에 맞춘 것**: 상세 `availability{…, axes[{axisId,name,values[{valueId,value}]}], skus[{skuId,kind,name,valueIds,unitPrice,remaining,soldOut,temporarilyUnavailable,onSale}]}` · 견적·주문 `items[{skuId,qty}]`(추가상품만·같은 단품 두 줄·수량 0 → 400 `items`, 409 `GROUP_BUY_SKU_NOT_ON_SALE`·`GROUP_BUY_SKU_SOLD_OUT{skuId,temporary}`) · 본인 취소 `lines[{skuId,qty}]` · 관리자 저장 `axes[{name,values}]`·`skus[{values,extraPrice,maxQty,onSale}]`·`addons[{name,price,maxQty,imageKey}]`(409 `GROUP_BUY_LOCKED_FIELD` field `options`) · 수량 조정 `skuId`(409 `GROUP_BUY_SKU_UNLIMITED`) · 고시 `skuNames`/`skuIds`·`unassignedSkus`.
- **세션 정리 결정(2b-2 답 — 2b-2 계약 「구조 변경」 절, 커밋 b8dd56fa)**: 상세의 단품 알림 칸 `skuWatches{watchable[], watching[]}`·신청 `{kind, skuId}` 는 목업 모양 그대로(API 는 2b-4) · 내 주문·요청·공급 불가 줄의 `skuId`·`skuName`·`kind` 그대로 · **취소 요청·반품 요청·두 미리 보기 줄도 `skuId`**(주문 안 단품 줄은 하나뿐 — 목업 반영함) · 반품도 본 상품을 모두 고르고 추가상품을 남기면 409 `GROUP_BUY_ADDON_ONLY {skuIds}`(2b-3, 화면은 먼저 「함께 고르기」를 묻는다) · 추가 구성상품 판매 여부 칸 없음(늘 판매, 모집 뒤 잠금) · `addons.maxQty` null = 한도 없음(관리자 칸은 비우면 「한도 없음」 — 반영함) · 축 없는 공구의 MAIN 이름 = 상품 이름 · 모집 뒤 판매 끈 조합 다시 켜기 불가(409 `GROUP_BUY_LOCKED_FIELD` options) · 글자 상한은 서버 값 **축 이름 30 · 값 30 · 추가상품 이름 100**(반영함).
- **볼 곳**: 회원 `/group-buys/113`(티셔츠 색상 × 사이즈 — 화이트/S 판매 안 함, 블랙/M 잠시 구매 불가) · `114`(러닝화 사이즈) · `102`(로봇 키트 + 여분 배터리·충전 독, 고시 3장) · `109`(쌀 중량) · `110`(과일 종류 × 중량) · `103`(색상 + 마감 임박) · `111`·`112`·`107`(읽기 전용) · 주문서 `/group-buys/113/order?items=11302:1,11306:2`(오류: `?items=11301:1` 판매 안 함 · `11305:1` 잠시 품절 · `/group-buys/102/order?items=10251:1` 추가상품만 · `?items=abc` 깨짐) · `/me/orders/G2026102800012`(반품 — 본체만 고르면 「추가 구성상품도 함께」) · `/me/orders/G2026102300007`(단품 공급 불가) / 관리자 `/group-buys/gb-109`(DRAFT 편집)·`/group-buys/new` · `gb-111`·`gb-102`·`gb-108`(모집 중 잠김) · `/group-buy-stock?id=gb-102`·`gb-111`·`gb-108` · `/group-buy-orders?groupBuyId=gb-102`·`gb-111` · `/group-buy-requests`(ri-6 충전 독 반품). 두 앱 목업 데이터와 ID 는 서로 다르다.

### 추가분 — 상품 마스터 · 카테고리 · 공급가 (2026-10-28, 결정 89 — 정본은 develop 의 계약)
- **관리자**: 「공구」 묶음에 **상품** `/group-buy-products`(목록 — 카테고리·검색) · `/group-buy-products/{id}`(이름·카테고리(아래 단 필수)·요약·사양·설명·이미지·정상가 근거·청약철회 제한·옵션 축/조합 표(기본 공급가·판매 여부)·고시 양식, 쓰는 공구 목록, 모집 중 공구가 쓰면 구성 잠금 표시, 「이 상품으로 공구 만들기」 → `/group-buys/new?productId=`) · **카테고리** `/group-buy-categories`(2단·차례·숨김·더하기·지우기 — 상품이 있으면 못 지움, 통째 저장). 공구 등록은 「⓪ 상품」 고르기 → 설명·이미지·고시·축을 상품에서 채우고 읽기 전용(「상품에서 고치기」 링크), 조합 표는 **단품별 공구 설정**(추가금·한도·판매 여부·공급가), 추가 구성상품은 **다른 상품의 단품 고르기**(이름·이미지·고시는 그 상품 것 + 가격·한도·공급가). 공급가 칸은 `commerce.product.price`·`commerce.settlement.read` 일 때만 보이고 고치기는 `commerce.product.price`(모집 뒤에도 — `PATCH …/content` 의 `supplyPrices`). 판매가 < 공급가면 줄에 「역마진」, 저장·모집 시작 전 확인 창. 모집 시작 409 `GROUP_BUY_SUPPLY_PRICE_MISSING`(extras `skuNames`) 목업.
- **회원**: `/group-buys` 진행·예정·종료 탭 아래 카테고리 칩(위 단 → 아래 단 칩 한 줄 더, `?category=`, 위 단 = 아래 단 포함). 공급가는 회원 응답에 없다.
- **서버 모양(2b-2 1d5b48ab)에 맞춘 것**: 상품 저장 `ProductDraft{categoryId, name, summary, spec, description, normalPrice, normalPriceBasis, normalPriceSource, withdrawalRestriction, axes[{name, values}], skus[{values, defaultSupplyPrice, onSale}], images[]}`(+ 목업은 `notices` 를 같은 요청에 — 2b-2 확인 필요) · 공구 저장 `GroupBuyDraft{productId, title, groupPrice, shippingPolicyCode, minQty, maxQty, maxQtyPerUser, startAt, endAt, extensionMode, fulfillment, shipPlannedAt, shipWithinBusinessDays, returnPolicy, roundLimitShared, skus[{productSkuId, extraPrice, maxQty, onSale, supplyPrice}], addons[{productSkuId, price, maxQty, supplyPrice}], marginConfirmed}` · 409 `GROUP_BUY_SUPPLY_PRICE_MISSING {skuIds}`·`GROUP_BUY_NEGATIVE_MARGIN {skuIds}`(화면이 이름으로 바꿔 확인 창 → `marginConfirmed: true` 로 다시)·`GROUP_BUY_PRODUCT_SKU_IN_USE {skuNames}`·404 `GROUP_BUY_PRODUCT_NOT_FOUND` · 모집 뒤 공구 수정은 공급가만(`PATCH …/content {supplyPrices, marginConfirmed, version}`) · 상품은 모집 중 공구가 쓰면 이름·정상가·청약철회 제한·옵션 구성 잠금(`409 GROUP_BUY_LOCKED_FIELD` field `name|normalPrice|withdrawalRestriction|options`), 설명·이미지·카테고리·고시는 언제든 — 화면도 같은 칸만 잠근다. 정상가는 상품 칸(공구 화면은 읽기 전용).
- **아직 목업 가정**: 카테고리 API `GET·PUT /api/admin/group-buys/categories`(통째, 새 줄 `tempKey`·`parent`) · 상품 목록 `?q=&categoryId=` · `GET /api/admin/group-buy-products/addon-sources?except=` · 모집 시작에도 `marginConfirmed`(`POST …/start {marginConfirmed}`) · 409 의 `skuIds` 는 저장 때 상품 단품 ID, 저장된 공구는 공구 단품 ID 로 봄 · 회원 `GET /api/group-buys/categories` → `[{id, name, children[{id, name}]}]`, 목록 `?category=`·줄 `categoryId`.
- **목업 가정**: 상품 목업은 공구 목업에서 만든다(공구 gb-1xx ↔ 상품 pd-1xx, gb-102 의 추가 구성상품 둘 = pd-201·pd-202). 기본 공급가 = 판매가의 약 62%. 볼 곳: 관리자 `/group-buy-products` · `/group-buy-products/pd-102`(모집 중 → 구성 잠금) · `/group-buy-products/pd-105`(공급가 빔) · `/group-buy-categories` · `/group-buys/gb-105`(모집 시작 → 공급가 409) · `/group-buys/gb-109`(역마진 확인 창) · `/group-buys/new` / 회원 `/group-buys?category=ct-food`.

### 추가분 — 상품 단품 목록이 정본 (사용자 결정 2026-10-28 — 화면 확인 때, 결정 88 의 「축·값 → 조합 자동 생성」을 바꾼다)
- **결정**: 옵션도 단품이다. 상품 아래에 **파는 아이템(단품)을 한 줄씩 직접 등록**하고(이름 · 판매 여부 · 기본 공급가), 같은 상품의 단품들이 회원 화면에서 옵션으로 보인다. 단품이 하나면 옵션 없는 상품. **옵션 이름(속성, 선택 0~3 — 예 색상·사이즈)** 을 두면 단품마다 그 값을 적고, 회원 상세는 옵션 이름 차례로 고른다(앞을 고르면 없는 조합은 회색 — 서버가 단품의 값으로 축을 만들어 지금 회원 응답 `availability.axes`·`skus[].valueIds` 모양 그대로 준다). 조합을 만들고 끄는 대신 파는 것만 등록한다. 추가 구성상품 = 다른 상품의 단품(그대로).
- **화면**: 관리자 상품 「단품(옵션)」 구획(`GroupBuyProductSkusEditor` — 옵션 이름 칩, 단품 표(옵션 값 열 · 이름(비우면 「블랙 / M」) · 기본 공급가 · 판매), 줄 더하기·복사·차례·빼기, 같은 이름·같은 값 묶음 막기, 모집 중 공구가 쓰면 잠금). 공구 화면·회원 화면은 그대로(상품 단품마다 공구 설정).
- **서버 제안(2b-2 에 알림)**: 상품 저장 `ProductDraft` 의 `axes[{name, values}]`·`skus[{values, defaultSupplyPrice, onSale}]` 를 `optionNames: string[]` · `skus[{skuId|null, name, values[](optionNames 차례), defaultSupplyPrice, onSale}]` 로. 상세는 `optionNames` + `skus[{skuId, name, values, valueIds, defaultSupplyPrice, onSale}]` + 서버가 만든 `axes`(읽기 전용). 검사: 단품 ≥ 1 · 이름 겹침 · 옵션 이름이 있으면 값 모두 필수 · 같은 값 묶음 두 줄 금지 → `400 skus`/`optionNames`. 공구가 쓰는 단품을 빼면 `409 GROUP_BUY_PRODUCT_SKU_IN_USE {skuNames}`, 모집 중이면 구성 변경 `409 LOCKED_FIELD options`.
- **2b-2 반영(6125b751)**: 제안 모양 그대로 확정. 다른 점 — ① 옵션 이름 없이 이름으로 가리는 단품 여럿 가능(예 쌀 10kg·20kg, `valueIds` 빔 — 회원 상세는 한 목록에서 고른다, 목업 109) ② 기존 단품은 `skuId` 로 가리켜 이름·값을 바꿔도 같은 ID ③ 축 값 차례 = 단품 목록에 처음 나온 차례 ④ 추가 구성상품 이름 = 단품 하나뿐인 상품이면 상품 이름, 아니면 「상품 이름 (단품 이름)」. 기본 공급가·판매 여부는 모집 중에도 고친다(잠금은 옵션 이름·단품 추가/삭제·이름·값만). 단품 1~200개, 남의 상품 `skuId` 는 `400 skus`.
- **옵션 이름은 코드(옵션 종류)로 고른다**(사용자 결정 2026-10-28 — 추천안 B): 관리자 「공구 › 옵션 종류」(`/group-buy-option-types`)에서 코드(영문 대문자 2~20, 만든 뒤 바꾸지 않음)·이름·숨김·차례를 관리하고(쓰는 상품이 있으면 못 지움, 이름을 바꾸면 쓰는 상품의 옵션 이름도 따라감), 상품은 옵션 종류 코드 0~3개를 고른다. **값은 상품마다 자유 입력**이고 그 종류로 이미 쓴 값을 자동완성으로 권한다. API 제안: `GET·PUT /api/admin/group-buys/option-types` → `[{code, name, sortOrder, hidden, productCount, usedValues[]}]`, 상품 저장 `optionTypeCodes: string[]`(2b-2 의 `optionNames` 를 대신 — 숨긴·없는 코드 `400 optionTypeCodes`), 상세는 `optionTypeCodes` + `optionNames`(서버가 붙임). 회원 응답은 그대로(축 이름 = 종류 이름).
- **상품 ID · 단품 ID 는 서버 자동 채번, 따로 둔다**(사용자 2026-10-28): 단품 표는 `product_id`(FK) · `id` 두 열이고 **단품 ID 에 상품 ID 를 붙이지 않는다**(예 `P111-01` 같은 합친 값 금지). 운영자가 넣는 상품 코드·단품 코드 칸은 두지 않는다. 화면은 상품 ID·단품 ID 를 읽기 전용으로 보이고 새 줄은 「저장하면 자동」. 목업 단품 ID 는 상품과 무관한 번호(5001~).
- **옵션 유형 정리**: 조합형(색상 × 사이즈)·단독형(중량)·묶음(1kg × 2)은 모두 단품 한 줄씩. 추가 구성상품은 다른 상품의 단품. **입력형 옵션(각인 문구·메시지 등)은 단품이 아니라 주문 줄에 적는 글이라 지금 범위 밖**(주문 제작은 결정 55 로 2b 미지원).

## 마이그레이션 계획 (가번호 — develop 에 합칠 때 다시 확인, 지금 shared 마지막 V100 — 2a 보강 `payment_cancel_one_sending`)
| 가번호 | 스트림 | 내용 | 단위 |
|---|---|---|---|
| V101 `group_buy_core` | shared | `shipping_policies`(기간 배제 제약 + 적용 시작 줄 불변 트리거) · `group_buys` · `group_buy_images` · `group_buy_options` · `group_buy_revisions` · `group_buy_participations`(주문) · **`group_buy_participation_items`(주문 줄)** · `group_buy_qty_movements` · `group_buy_status_history` · `participation_status_history` · `group_buy_watches` · `group_buy_settings`(한 줄 — `point_shop_settings` 모양) | 2b-2 |
| V102 `group_buy_fulfillment` | shared | `commerce_carriers`(6개 시드) · `group_buy_shipments` · `group_buy_requests`(+ `received_at` 반환받은 날 — 3영업일 기한 시작) · **`group_buy_request_items`**(요청 줄 — 주문 줄·수량·**사유**·`refund_amount`) · ~~`group_buy_refunds`~~(결정 36 — 2b-2 가 V101 로 당겼다, 「2b-2 구현 결과」) · **`group_buy_return_dispositions`**(요청 줄·처리 방법·수량) · `group_buy_request_photos` · `commerce_address_views` · `commerce_bank_transfers`(결정 6) | 2b-3 |
| V103 `group_buy_defect_known_on` | shared | `group_buy_requests.defect_known_on`(업체 부담 반품의 「하자를 안 날」 — 2b-3 보강, 사용자 지시 2026-10-28) | 2b-3 보강 |
| V104 `commerce_sales_switch` | shared | 판매 스위치·체크리스트·사업자 정보(`commerce_sales_settings` 등), `R__batch_32_groupbuy_privacy_cleanup`(배송지 마스킹·수정 이력 6개월·요청 3년·만료 주문 배송지 — 결정 34) | 2b-4 |
| V106 `group_buy_qna_review`(V105 는 테마·레이아웃 `site_theme_layout` 이 먼저 썼다 — 2026-10-29) | shared | Q&A `group_buy_questions` · `group_buy_answers` · `group_buy_answer_revisions` · 리뷰 `group_buy_reviews`(주문 줄마다 하나) · `group_buy_review_photos` · `group_buy_review_revisions` · `group_buy_review_replies`, 신고 대상 `GROUP_BUY_QUESTION`·`GROUP_BUY_REVIEW`(기존 `reports` 의 대상 종류 CHECK), 파기 배치에 Q&A 3년 더하기 | 2b-6 |
- Java 워커(카탈로그 없음, `worker_heartbeats`): `groupbuy-state`(1분 — 선점 만료·모집 시작·마감·연장·판정, 다 팔림 마감의 안전망), 운영자 기한 점검은 19절 점검 워커가 생길 때까지 2b-4 가 1시간 워커로.
- 열은 commerce.md 10-1 표가 바탕이고, 바뀌거나 더하는 것:
  - **주문과 주문 줄**(결정 23): `group_buy_participations` 는 주문 한 건(회원·배송지·배송비·금액·결제·상태·`order_no`·`admin_memo`·`refunded_amount`(상품·배송비 환불 누계)·`compensation_amount`(PG 로 돌려준 고객 택배비 보상 누계 — 결정 39)·`point_refunded`(11절 포인트 결제 대비, 2b 에서는 0)·`reshipping_fee_charged`). 옵션·수량·단가는 `group_buy_participation_items`(주문·옵션·`qty`·`unit_price`·`item_amount`·`canceled_qty`·`returned_qty`·`restocked_qty`, CHECK `0 ≤ restocked_qty ≤ returned_qty`, `canceled_qty + returned_qty ≤ qty`, 유일 `(participation_id, option_id)` — 같은 옵션 두 줄 금지). 옵션이 없는 공구는 줄 하나(옵션 null, 그 주문에 한 줄).
  - 수량 이동 줄에 `participation_item_id`(주문 줄)를 더한다.
  - 주문 CHECK `0 ≤ refunded_amount ≤ amount`(community 는 이 CHECK 로 이중 환불을 막았다 — D 512).
  - `group_buys`: `version`(설명·이미지 편집의 낙관 잠금 — 수량 열은 조건부 UPDATE 로 바꾸고 `version` 을 올리지 않는다), `closing_at`, `summary`·`spec`(평문 ≤ 5,000), `round_root_id`(1차 공구 ID, 1차는 자기 자신)·`round_no`(유일 `(round_root_id, round_no)`)·`round_limit_shared`(결정 24)·`added_qty`, CHECK `min_qty ≤ max_qty`.
  - `group_buy_revisions` 에 수량 사본(`max_qty`·`added_qty`, 옵션별 최대)을 더한다 — 추가 입고도 표시 기록. 주문은 결제 때의 `revision_no` 를 남긴다(낙관 잠금 `version` 과 다른 번호).
  - 수량 이동 종류: RESERVE·RELEASE·CONFIRM·CANCEL·RETURN(반품 기록, 수량 불변)·RESTOCK(다시 판매, 확정 수량 감소)·ADD(추가 입고, 최대 증가)·REDUCE(줄이기, 최대 감소). 이동 줄마다 `option_id` 와 공구·옵션 양쪽의 이후 값.
  - `group_buy_watches.kind` 에 `NEXT_ROUND`(다음 차수 알림 — 1차 공구 기준) 를 더한다.
  - 참여 `hold_expire_count` 는 두지 않고 `participation_status_history` 의 최근 24시간 EXPIRED 중 원인 `HOLD_TIMEOUT` 만 센다(결정 28).
- 요청 사진은 `group_buy_request_photos`(요청 ID 없이 먼저 올리고 붙인다) — 붙지 않은 사진은 24시간 뒤 정리(C 195).
- **최종 검토로 더하는 열·표**(결정 51~86):
  - V101 `group_buys`: `ship_planned_at`·`ship_within_business_days`·`initial_max_qty`·`close_reason`·`normal_price_basis`·`normal_price_source`·`withdrawal_restriction` · 옵션 `initial_max_qty` · 주문 `point_amount`·`cash_amount`·`shipped_shipping_fee` · `group_buy_watches.option_id` · **`group_buy_delay_notices`**(공구·이전/새 예정일·사유·운영자·시각) · 부분 유일 색인·복합 FK(결정 66).
  - V102 `group_buy_shipments`: `kind`(`ORIGINAL`·`EXCHANGE`)·`escrow_logistics_at`·`escrow_completed_at` · **`group_buy_escrow_jobs`** · 요청 `preview_amount`·`resolution`(`REFUND`·`EXCHANGE`)·`reject_reason_code` + 근거 사진 · `group_buy_refunds` 의 `method`·금액 나눔 열(결정 75)·원인 `SUPPLY_FAILURE`·`DELAY` · **`group_buy_supply_failures`**(공구·옵션·사유·기한) · **`group_buy_supply_choices`**(주문·선택 `KEEP_REST`/`CANCEL_ALL`·기한·정한 시각·기본 적용 여부).
  - 수량 이동 종류에 `EXCHANGE_OUT`(교환 출고 — 판매 수량 불변, 기록만).
  - 결제 모듈: `PaymentOrderType.GROUP_BUY_RETURN_FEE`(반품 비용 부족분 추가 결제 — 결정 68) 와 그 처리기(공구 패키지), `payment_orders.order_type` CHECK 에 더한다(V102).

## 패키지 (`groupbuy → payment`, `groupbuy → point` 단방향 — `payment` 는 공구를 모른다)
- shared `com.community.groupbuy`:
  - `entity`·`dto` — `GroupBuyStatus`·`ParticipationStatus`·`Fulfillment`·`ExtensionMode`·`ReturnFeeMode`·`QtyMovementKind`·`RequestType`·`RequestStatus`·`CollectionMethod`·`ReturnDisposition`·`ExpireCause`
  - `repository` — 수량·상태 전이는 JDBC(조건부 UPDATE, `SKIP LOCKED`), 관리자 조회는 JPA + QueryDSL
  - `service` — `GroupBuyAdminService`(등록·수정·모집 시작·조기 마감·중단·추가 차수) · `ShippingPolicyService` · `GroupBuyQuoteService`(견적 — 주문 때 다시 계산) · **`GroupBuyJoinService`**(자격 → 선점 → 금액 → 배송지 암호화 → 주문 PENDING → `PaymentOrderService.create`) · **`GroupBuyQtyService`**(RESERVE·RELEASE·CONFIRM·CANCEL·RETURN·RESTOCK·ADD·REDUCE — 수량을 바꾸는 유일한 길, 공구 행 → 옵션 행 차례로 잠금, 이동 줄 하나씩) · `GroupBuyStateWorker.runOnce` · `GroupBuyJudgeService`(성사·불발 — 불발은 주문 REFUNDED + CANCEL 이동 + `PaymentCancelService` 전액) · `ParticipationCancelService` · `FulfillmentService`(발주·준비중·발송·배송완료) · `GroupBuyRequestService` · **`GroupBuyRefundCalculator`**(한 곳 — 요청·승인 화면이 같은 값, 재부과 배송비 누계로 차액만) · `AddressRevealService` · `GroupBuyInvariantCheck`
  - `handler` — `GroupBuyPaymentHandler implements PaymentOrderHandler`(`GROUP_BUY` — `onPaid`: 8-6 표 판정 → JOINED/IMMEDIATE 면 CONFIRMED + CONFIRM(+ 다 팔리면 같은 트랜잭션에서 CLOSING — 결정 27), 아니면 REJECT(`GROUP_BUY_CLOSED`·`HOLD_EXPIRED`) · `onCanceled`: **표시·알림만**(환불 기록 `group_buy_refunds` 를 완료로, 환불 완료 알림·메일) — 환불 누계는 취소를 넣을 때 이미 올렸다(결정 38), 자동 취소(`anomaly != null`)는 회원 안내만(결정 40). 결제 행을 쥔 채 불리므로 **공구 행을 `FOR UPDATE` 하지 않는다**(결정 37))
  - `qna`(2b-6) — 질문·답변·답변 이력·숨김·개인정보 감지
- api `groupbuy.controller`: 목록·상세·견적·주문(`MoneyRequestGuard` — 멱등 키·속도 제한)·결제 다시 열기·내 주문·본인 취소·요청·배송지 보기/수정·관심 신청·Q&A.
- admin `groupbuy.controller`: 공구·수량·주문·발주/발송·요청·배송지 열람·명단 내보내기·배송비 정책·택배사·설정·Q&A, `batch.service.GroupBuyStateScheduler`(1분, `roboyam.batch.enabled` 서버만 — `PaymentCancelScheduler` 모양), 관리자 이미지 올리기(결정 2).

## 세션 정리 결정 (제안 — 추천안, 사용자 결정 표시한 것은 확정)
1. **화면 경로(회원)**: `MenuBuiltin` 그대로 `/group-buys`(목록) · `/group-buys/{id}`(상세) · `/group-buys/{id}/order`(주문서 → 결제창) · `/me/orders`(내 주문) · `/me/orders/{orderNo}`(상세 — 취소·반품 요청·배송지). 결제창은 `/dev/payment` 의 SDK·창 닫기 판정(`UtilPortone`)을 공용 컴포저블로 옮겨 쓴다.
2. **이미지·설명**: 상세 설명은 공용 편집기 `@roboyam/rich-editor`(admin-web 이미 의존 — 글 본문과 같은 서버 정화를 거친다) + 평문 `summary`·`spec`. community 는 XSS 를 피하려 평문 + 줄바꿈만 받았지만(C 39), roboyam 은 이미 정화된 편집기가 있고 상품 설명에 본문 이미지가 필요하다. 수정 이력은 정화된 HTML 사본. 대표 이미지는 최대 8장 · JPEG·PNG·WebP 정지 이미지 · 5MB · 다시 인코딩해 EXIF 제거 · 긴 변 1600px·썸네일 400px · 순서 바꾸기(community A 70 규칙). 관리자 이미지 올리기 API 를 새로 두고 저장은 기존 미디어 저장소(`roboyam.storage`). 차수끼리 같은 미디어를 쓰므로 미디어는 쓰는 공구가 하나도 없을 때만 지운다.
3. **관리자 메뉴**: 보통 모드 새 묶음 「공구」(결제 묶음 앞) — 공구 · 주문 · 취소·반품 요청 · Q&A. 배송비 정책·택배사·사업자 정보·판매 스위치·공구 설정은 `/settings/commerce` 에 구획으로 더한다(0-5 계약대로). 권한은 2b 에서 쓰는 것만 `featureReady=true`(결정 35).
4. **에스크로**: `PaymentGateway` 에 「배송 정보 등록·수정」을 더하고 발송 처리 때 에스크로 결제 건이면 부른다(가짜 PG 는 기록만). 이니시스 테스트 채널의 에스크로 적용 범위는 2b-5 에서 확인하고, 그 전에는 주문서에 「결제대금예치 선택 가능」 고지만.
5. **현금영수증**: 카드·간편결제만 받으므로(4절) 주문서 입력은 두지 않고 표 열만 둔다. community 도 입력칸은 뒀지만 「SDK 현금영수증 인자는 가상계좌·계좌이체용이라 카드 결제에는 싣지 않는다」로 정했다(D 513). 승인되면 commerce.md 10-6 1번의 「현금영수증 선택」을 고친다.
6. **보상액이 남은 결제액을 넘는 부분**: 9-3 의 계좌 송금을 2b-3 에서 공용 표 `commerce_bank_transfers`(계좌 암호문 — `ROBOYAM_DATA_KEY`, 「송금 완료」 후 계좌 삭제)로 먼저 만들고 3단계 충전 환불이 이어 쓴다.
7. **배송완료**: 택배사 API 가 범위 밖이라 운영자가 처리(일괄 가능). 자동 배송완료는 두지 않는다(청약철회 7일의 시작점이라 추측하지 않는다). 처리 누락은 대시보드 「발송 뒤 N일 지난 SHIPPED」로 잡는다.
8. **영업일**: 주말 + 관리자가 넣는 휴일 목록(`group_buy_settings` 의 휴일 날짜 배열, 설정 화면에서 편집). 공휴일 API 는 쓰지 않는다. 2·3영업일 기한 경고(P35)와 환불 기한 표시가 같이 쓴다.
9. **공구 COMPLETED**: 선착순 즉시 발송은 마감됐고 수량에 들어간 주문(결정 28)이 모두 DELIVERED·CANCELED·REFUNDED 이면. 마감 후 발주는 `ORDERED → SHIPPING`(첫 발송 처리 때) `→ COMPLETED`(같은 조건). **판단은 배송완료 트랜잭션에서 하지 않고 1분 워커의 조건부 sweep**(`… WHERE NOT EXISTS 진행 중 주문`) — 마지막 두 주문이 동시에 배송완료돼 서로 못 보는 경쟁을 피한다(최종 검토). 반품 요청은 COMPLETED 뒤에도 받는다(공구 상태가 아니라 주문·요청 기준).
10. **공구 설정(설정값)**: 선점 시간 5분(3~30) · 성사형 기간 상한 14일 · 선착순 30일 · 연장 최대 30분 · 연장 판단 창 5분 · 판정 여유 2분 · 고객 택배비 보상 상한 10,000원 · 고액 환불 문턱 100,000원 · 배송지 열람 화면 유지 10분 · 휴일 목록 · 만료 주문 배송지 보관 30일. 바꿀 때 감사(전→후).
11. **택배사·local 시드**: 택배사는 **마이그레이션 시드(전 환경)** 로 6개(`CJ`·`HANJIN`·`LOTTE`·`EPOST`·`LOGEN`·`KDEXP`) + 조회 주소 템플릿(`https://`, `{no}` 한 번). 목록은 통째로 저장, 발송 이력이 있는 코드는 지우지 못하고 끄기만, 송장번호는 숫자·영문 6~30자로 정리(community A 118·178). local 시드는 판매 스위치 켜짐·체크리스트 전부 체크·시험 사업자 정보·배송비 정책 `STANDARD`(3,000 · 무료 50,000 · 반품 3,000 · 편도) + 시험 공구 넷(성사형 모집 중 · 선착순 즉시 발송(옵션 셋) · 마감 임박 · 다 팔려 끝난 1차) — `seed/` 와 `PortOneLocalSeeder` 모양(local 만).
12. **운영자 알림**: 예약된 `COMMERCE_ORDER_ANOMALY`(공구 결제 REJECT·불변식 불일치) · `COMMERCE_REFUND_REQUESTED`(취소·반품 요청 — WARNING, 2영업일 경고 · 3영업일 CRITICAL) + 새 `COMMERCE_GROUPBUY_CANCELED`(중단)·`COMMERCE_SALES_TOGGLE`·`COMMERCE_GROUPBUY_QUESTION`(새 질문·신고 — INFO, 묶기). 받는 사람 = `commerce.order.manage`·`commerce.refund.approve` 보유자.
13. **주문 번호**: 주문마다 `G` + KST 날짜 `yyyyMMdd` + 그날 일련 5자리(예 `G2026102800012`, 하루 99,999건 — 넘으면 자리를 늘린다). 회원 주소 `/me/orders/{orderNo}`, 결제창 주문명·메일·고객 응대에 쓴다. 포트원 `redirectUrl` 은 쿼리 없이 주문 상세로 돌아온다(D 513). (community 는 4자리 시퀀스 — C 43)
14. **에스크로 구매 확정**: 에스크로 결제 건은 배송완료 뒤 청약철회 기간(7일)이 열린 요청 없이 지나면 `escrow/complete` 를 부른다(하루 한 번, 실패는 재시도·결제 이상). 송장 정정 때는 `PATCH` 로 배송 정보를 다시 보낸다. 실제 동작은 2b-5 에서 확인.
15. **반품 상품 처리**(사용자 결정): 반품 완료 때 운영자가 **요청 줄마다 처리 방법과 수량**을 고른다(필수, 합 = 그 줄의 반품 수량 — 예 「2개 다시 판매 + 1개 폐기」).
    - **다시 판매**(`RESELL`): 공구가 **모집 중**(`RECRUITING`)이고 **사유가 단순 변심**(`CHANGE_OF_MIND`)일 때만. 하자·오배송은 서버가 거절(`GROUP_BUY_NOT_RESELLABLE`). 그 옵션·공구의 확정 수량을 줄여(이동 `RESTOCK`, 조건 `status='RECRUITING'`) 남은 수량이 늘고, 수량 풀림 알림. 반품은 배송완료 뒤에만 생기므로 실제로는 선착순 즉시 발송 공구에서만 고를 수 있다 — 화면은 다른 공구에서 이 선택지를 숨긴다.
    - **폐기·공급처 반송·보관**(`DISCARD`·`RETURN_TO_SUPPLIER`·`KEEP`): 공구 수량은 그대로, 이동 `RETURN` 으로 기록만.
    - 수량 화면에 판매·반품·다시 판매를 나눠 보인다. 성사 판정은 뒤집지 않는다(10-4). (community 는 전체 반품만 자동 재입고 — A 138)
16. **환불 진행 표시**: 주문은 같은 트랜잭션에서 REFUNDED·CANCELED 로 바뀌지만 돈은 취소 워커가 보낸다. 회원·관리자 화면은 `group_buy_refunds` 에 남긴 `payment_cancel_id`(결정 36)로 결제 취소 상태를 읽어 「환불 진행 중 · 카드사 반영까지 3~7영업일」/「환불 완료(날짜)」/「환불 지연 — 확인 중」(GAVE_UP)/「운영자가 직접 처리함」(MANUAL)을 나눠 보인다. MANUAL 은 지금 `onCanceled` 가 오지 않으므로 결제 모듈의 `PaymentOrderHandler` 에 기본 메서드 `onCancelResolvedManually(…)` 를 더하고 `PaymentAdminActionService` 「수동 처리 완료」가 같은 트랜잭션에서 부른다(결제 모듈은 여전히 도메인을 모른다 — 2b-3 에서 결제 모듈을 조금 고친다). 공구 처리기는 환불 기록을 완료로 두고 회원에게 「환불 처리됨(운영자 확인)」 알림·메일. community 의 「취소됐지만 환불 실패」 상태(A 141)를 이 표시로 대신한다.
17. **모집 뒤 수량 조정 · 추가 입고**(사용자 결정 — commerce.md 10-2 를 고쳤다, 권한 `commerce.stock.adjust`):
    - 「품절」은 둘이다. **잠시 품절**(확정 + 선점 = 최대, 공구는 모집 중 — 선점이 5분 안에 풀릴 수 있다)과 **다 팔림**(확정 = 최대 → 그 자리에서 `CLOSING` → 판정, 결정 27). 옵션도 같다 — 옵션 하나가 다 팔려도 공구는 그 옵션만 「다 팔림」이고, **모든 옵션이 다 팔리거나 공구 최대에 닿으면** 공구가 마감된다.
    - **늘리기(추가 입고)**: 공구가 `RECRUITING` 이고 다 팔리기 전(잠시 품절 포함)에만 — 조건부 UPDATE `status='RECRUITING' AND current_qty < max_qty`. 옵션을 지정하면 옵션 최대와 공구 최대를 한 트랜잭션에서(공구 행 → 옵션 행 차례). 이동 `ADD`, `added_qty` 누계, 상세에 「추가 입고 N개」(줄이기 뒤에는 순증가분만), 수정 이력·감사(돈 관련), 수량 풀림 알림. 사유 필수.
    - **줄이기**: 품절·불량·입고 부족. `RECRUITING` 이고 하한 = max(`확정 + 선점`, 성사형이면 `min_qty`). 이동 `REDUCE`, 사유 필수, 감사. 줄인 결과 확정 = 최대이면 같은 트랜잭션에서 CLOSING.
    - **다 팔린 뒤 추가 입고 = 추가 차수 공구**: 결정 31.
    - 모집 뒤 가격·기간·옵션 구성·배송비는 지금처럼 바꿀 수 없다.
18. **사업자 정보와 공개 API**: 칸은 상호·대표자·사업자등록번호(체크섬)·통신판매업 신고번호·신고 기관·주소·전화·이메일 + **운영시간·개인정보 관리책임자·호스팅 사업자·배송/환불 정책 문구(≤ 3,000)**(community A 195). 평문 저장. 회원 공개 `GET /api/commerce/info`(값 + `ready`) — 공구 화면 하단. 판매 켜기 자동 조건에 community 의 「공개된 공구 1개 이상」은 넣지 않는다(공구를 열기 전에 판매를 켜 두는 순서를 막지 않게).
19. **설정 읽기**: 선점 시간·배송비·문턱처럼 돈과 시각에 닿는 값은 **그 트랜잭션 안에서 DB 의 지금 값**을 읽는다(캐시 금지 — community A 176·S 290). 화면 표시용만 캐시.
20. **내보내기 안전**: 배송 명단 CSV 는 `= + - @` 로 시작하는 칸 앞에 `'` 를 붙인다(수식 주입 — community A 163).
21. **공구 Q&A**(사용자 결정): 별도 단위 2b-6 — 아래 「Q&A 규칙」.
22. **관리자 날짜 필터**: 한국 날짜, 양 끝 포함(community D 640).
23. **한 주문에 여러 옵션**(사용자 결정): 주문서에서 옵션 여러 줄 × 수량을 한 번에 산다. 결제·배송비·배송지·주문 번호는 주문 단위, 수량·선점·반품·다시 판매는 줄 단위. 선점은 줄마다 옵션 행 + 공구 행을 한 트랜잭션에서(하나라도 모자라면 전체 실패, 잠금은 공구 행 → 옵션 ID 오름차순). 배송비·무료배송 기준은 주문 전체 상품 금액으로. 공구당 **진행 중 주문은 1건**(PENDING_PAYMENT~SHIPPED) — 더 사려면 배송완료 뒤 새 주문이거나 취소 후 다시 주문(결정 28).
24. **차수 사이 1인 한도**(사용자 결정): 공구마다 `round_limit_shared` 를 고른다(기본 꺼짐 = 차수마다 따로). 켜면 같은 `round_root_id` 의 모든 차수에서 산 수량을 합쳐 센다.
25. **연장 조건 넓힘**(사용자 결정 — commerce.md 10-2 를 고친다): `DEMAND` 공구는 마감 전 5분 안에 선점 만료뿐 아니라 **다시 판매(RESTOCK)·추가 입고(ADD)로 수량이 늘었을 때**도 연장한다(수량 풀림 알림을 받은 회원이 들어오기 전에 마감되지 않게).
26. **Q&A 관리**(사용자 결정): 개인정보 자동 비밀글 + 작성자 안내 · 신고 대상 · 단순 문의도 3년 보관 · 답변 수정 이력 — 「Q&A 규칙」.

## 수량·상태 규칙 (검토 보강 2026-10-28)
27. **다 팔림은 그 자리에서 마감**: 결제 확정(`onPaid` 의 CONFIRM)·줄이기(REDUCE)가 확정 = 최대를 만들면 **같은 트랜잭션**에서 `RECRUITING → CLOSING`(`closing_at`, 상태 이력, 종료 사유 `SOLD_OUT`). 1분 워커(10-7 ③)는 안전망으로만 남는다. 추가 입고·다시 판매는 `status='RECRUITING' AND current_qty < max_qty` 조건부라 마감과 경쟁해도 둘 중 하나만 이긴다.
28. **수량에 들어가는 주문과 불변식**: 결제 확정(CONFIRM)된 주문 줄의 `qty − canceled_qty − restocked_qty` 가 「판매 수량」이다. 발송 전 취소·불발·중단 환불은 CANCEL 이동 + `canceled_qty`, 반품은 `returned_qty`(폐기·반송·보관이면 판매 수량 그대로 — 이미 팔려 나간 것), 다시 판매는 RESTOCK + `restocked_qty`. **불변식**: 공구·옵션별 `current_qty` = Σ 판매 수량, `reserved_qty` = Σ PENDING_PAYMENT 줄 수량, 이동 줄 마지막 값 = 현재 값(commerce.md 18절을 이 식으로 고친다). **1인 한도**는 재고 기준과 다르다 — 그 회원의 Σ `qty − canceled_qty − returned_qty`(반품은 처리 방법과 관계없이 뺀다 — 돌려준 것은 그 회원이 가진 것이 아니다, 10-4 「한도는 취소·환불 수량을 뺀다」) + 진행 중 선점(+ 결정 24 합산). 판매 수량(재고 기준)은 폐기·보관한 반품을 빼지 않는다 — 다시 팔 수 없는 물건이라 남은 수량으로 돌아오면 안 된다. **진행 중 주문**(부분 유일) = PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED. **만료 원인**(`ExpireCause`): `HOLD_TIMEOUT`(선점 시간 초과 — 이탈 횟수에 센다)·`GROUP_BUY_CANCELED`·`PAYMENT_FAILED_CLOSED` 등 — 이탈 제한(`GROUP_BUY_BAN`)은 `HOLD_TIMEOUT` 만 센다.
29. **중단**: 공구 상태 전이 뒤 주문마다 새 트랜잭션에서(결정 37 — 교착 방지) ① PENDING_PAYMENT → EXPIRED(원인 `GROUP_BUY_CANCELED`) + RELEASE + 연결된 `payment_orders` READY → EXPIRED(뒤늦은 결제는 늦은 결제로 자동 취소) ② **발송 전**(JOINED·CONFIRMED·PREPARING) → REFUNDED + CANCEL + 전액 취소 ③ **이미 발송**(SHIPPED·DELIVERED)은 그대로 정상 흐름(반품은 요청으로). 확인 창에 「환불 N명 · 총 N원 / 이미 발송 M명」.
30. **배송지 수정·명단 내보내기 기한은 주문 기준**: 수정은 마감 후 발주 공구면 공구 ORDERED 전, 즉시 발송 공구면 주문 PREPARING 전. 명단 내보내기는 「발송 전 주문(CONFIRMED·PREPARING)」 단위 — 마감 후 발주 공구는 ORDERED 뒤, 즉시 발송 공구는 언제든(commerce.md 10-5·10-9 를 고친다).
31. **추가 차수**: 원본은 모집이 끝난 공구(CLOSING 이후 — 다 팔림·기간 끝·완료, 중단 제외). 같은 루트에 DRAFT·RECRUITING 차수가 이미 있으면 거절(동시 클릭은 유일 `(round_root_id, round_no)` 가 막는다). `round_root_id` = 1차 공구, `round_no` = 그 루트의 최대 + 1. 복사: 설명·이미지·`summary`·`spec`·옵션 이름·추가금·방식(성사형/선착순·발송 시점·연장 방식)·`round_limit_shared`. 새로 넣음: 수량·기간·가격(복사값을 채워 두되 DRAFT 라 고칠 수 있다). 배송비는 모집 시작 때 정책. 복사할 때 「이전 차수의 날짜·수량·배송비 문구가 설명에 남아 있는지 확인하세요」 경고, 모집 시작 때 수정 이력 첫 판. 모집을 시작하면 루트의 모든 차수의 `NEXT_ROUND`·`RESTOCK`·`CLOSING_SOON` 신청자에게 한 번 알림(같은 회원 한 번), 신청은 새 차수로 옮기지 않는다.
32. **운영 환불**(요청 없이, `commerce.refund.approve` · 고액 문턱 `commerce.refund.large`, 사유 필수): 발송 전 주문(JOINED·CONFIRMED·PREPARING)은 「발송 전 취소」와 같다(CANCELED + CANCEL 이동, 줄·수량 지정 가능). 배송완료 주문은 **금액만 돌려주는 환불**(회수 없음 — 보상·가격 조정)로 수량 이동 없이 `refunded_amount` 만, 누계가 결제액이 되면 REFUNDED. SHIPPED 주문은 거절(배송완료 뒤 또는 반품 요청으로).
33. **연속 부분 반품의 배송비**: 주문에 `reshipping_fee_charged`(지금까지 재부과한 배송비) — `GroupBuyRefundCalculator` 는 「남은 수량 기준 배송비 − 이미 재부과한 것」의 차액만 뺀다. 마지막 남은 것을 반품하면 전체 반품 규칙(편도·왕복)으로 다시 맞춘다.
34. **배송지 파기 보강**: 결제하지 못한 주문(EXPIRED)은 만료 30일 뒤(설정값) 배송지 암호문 삭제. 불발·중단 환불(REFUNDED)은 commerce.md 10-9 「취소 후 6개월」.
35. **권한**(commerce.md 13-2 에 더한다): 공구 중단 `commerce.groupbuy.cancel`(이미 있음 — 2b 에서 `featureReady=true`) · 추가 입고·줄이기 `commerce.stock.adjust` · 추가 차수 만들기 `commerce.product.manage` · 반품 처리 방법(다시 판매 포함) `commerce.order.manage`(반품 완료 처리와 같은 사람) · Q&A 답변·숨김·신고 처리 `commerce.order.manage`, Q&A 조회 `commerce.order.read`.

## 2차 검토 보강 (Fable 검토 2026-10-28 — 결제 코드와 대조해 확인)
36. **환불 기록 표 `group_buy_refunds`**: 공구 쪽에서 돈을 돌려주는 모든 일 — 요청 승인·본인 즉시 취소·불발·중단·운영 환불 — 을 한 줄씩: 주문, 원인(`REQUEST`·`USER_CANCEL`·`FAILED`·`CANCELED`·`ADMIN`), 요청 ID(있으면), 상품·배송비 환불액, 보상액, `payment_cancel_id`(`PaymentCancelService.request` 가 돌려준 번호), 요청 키 `GB_REFUND:{id}`, 상태(진행 중·완료·지연·수동). 줄별 금액은 `group_buy_request_items.refund_amount`(요청) 또는 이 표의 줄 내역(jsonb, 요청 없는 환불). 결제 모듈은 바꾸지 않는다(취소 대기열 요청 키·결과로 연결). `CancelKind` 매핑: 본인 취소 `USER_CANCEL` · 불발·중단·반품 `REFUND` · 운영 환불 `ADMIN`.
37. **잠금 차례 — 교착 막기**(코드 확인: 결제 확정·취소 완료는 `payment_orders` 행을 잠근 채 처리기를 부르고, 취소 넣기는 안에서 `payment_orders` 행을 잠근다):
    - `onPaid`·`onCanceled` 안에서는 공구·옵션 행을 `FOR UPDATE` 하지 않고 **조건부 UPDATE 만** 쓴다(수량 확정 `UPDATE … SET current_qty = current_qty + :q, reserved_qty = reserved_qty - :q WHERE … AND status IN (…)`, 다 팔림 마감도 조건부).
    - 공구 행을 잠그는 일(중단·판정·추가 입고·줄이기·추가 차수)은 **결제 행을 잠그는 일과 같은 트랜잭션에 넣지 않는다**: 중단·불발 판정은 ① 공구 상태 전이만 짧은 트랜잭션 ② 주문마다 새 트랜잭션에서 「주문 조건부 전이 → 수량 CANCEL → 환불 기록 → 취소 넣기」(조건부라 다시 돌려도 한 번 — 워커가 이어 받는다). commerce.md 10-7 「활성 참여마다 같은 트랜잭션」을 이렇게 고친다.
    - 주문 하나 안에서는 주문 행 → (조건부) 공구·옵션 수량 → 결제 행 차례. **단, 이 차례는 결제가 PAID 인(취소를 넣는) 흐름에만**. 결제가 아직 READY 인 주문을 닫는 흐름(선점 만료·결제 다시 열기·중단의 결제 중 주문·조회 시 정리·REJECT)은 **결제 행을 먼저**: `UPDATE payment_orders SET status='EXPIRED' WHERE id=:p AND status='READY'`(0 행이어도 계속) → 주문 조건부 전이 → 이긴 쪽만 RELEASE. 결제 확정이 결제 행 → 주문 차례이므로 같은 순서가 된다(최종 검토 — 코드 `PaymentConfirmService.settle`·`PaymentOrderStore.expireOpenReady` 로 확인).
    - 어드바이저리 잠금 `pg_advisory_xact_lock`(키 `gb:{공구}:{회원}`, 트랜잭션 범위)은 **모든 행 잠금보다 먼저**: A → G → O(ID 오름차순, 한 줄씩 — `IN (…)` 한 문장은 스캔 순서로 잠근다) → X.
    - 판정 ①은 `SELECT … FOR UPDATE` 로 공구 행을 잠근 뒤 판단·전이(또는 WHERE 에 `current_qty < min_qty AND …` 를 넣어 재평가되게) — 마지막 결제 확정과 겹쳐 성사 수량인데 불발이 되는 일을 막는다.
38. **환불 누계는 취소를 넣을 때 올린다**: `refunded_amount` 는 취소를 대기열에 넣는 트랜잭션에서 조건부로 올린다(`WHERE refunded_amount + :amt <= amount`) — 공구가 상한의 정본이다. PG 가 GAVE_UP·MANUAL 이어도 같은 금액을 두 번 넣지 못한다. 「남은 결제액」= `cash_amount − refunded_amount − compensation_amount`(최종 검토 — 보상을 빼지 않으면 공구는 통과하고 결제 모듈이 409 로 되돌린다), CHECK `refunded_amount + compensation_amount ≤ cash_amount`, 조건부 UPDATE 도 둘을 합쳐 비교.
39. **보상액은 따로 센다**: PG 취소에 함께 담은 고객 택배비 보상은 `compensation_amount`, 계좌 송금분은 `commerce_bank_transfers`. 불변식: `payment_orders.paid_amount − canceled_amount` = `amount − refunded_amount − compensation_amount`(+ 진행 중 취소분 — 넣은 뒤 완료 전). 매출 집계는 상품 환불과 보상을 가른다.
40. **자동 취소의 `onCanceled`**: 늦은 결제·도메인 거절(`anomaly != null`)의 취소 완료는 주문 금액·상태를 건드리지 않고 회원 안내(commerce.md 8-2 5 문구)만.
41. **선점과 결제 유효 시각을 같게**: 결제 주문 `expires_at = reserved_until`(`PaymentOrderService.create` 의 유효 시간 = `reserved_until − 지금`). 「결제 다시 열기」도 같은 끝 시각 — 선점을 늘리지 않는다, 남은 시간이 없으면 `GROUP_BUY_HOLD_EXPIRED`.
42. **발송 전 일부 줄 취소의 배송비**(사용자 결정 — commerce.md 10-3 D11 을 고친다): 주문 전체 취소면 전액. **일부 줄만 취소해 무료배송 기준 아래로 떨어지면 남은 상품 기준으로 배송비를 다시 계산**해 환불액에서 뺀다(반품비 없음, `reshipping_fee_charged` 에 누계) — 발송 후 부분 반품과 같은 계산(`GroupBuyRefundCalculator` 한 길). 본인 즉시 취소도 줄·수량 단위.
43. **나눠서 모두 반품하면 앞서 뺀 배송비를 돌려준다**(사용자 결정): 마지막 반품으로 주문 전체가 반품되면 **처음 배송비 처리만** 한 번에 전체 반품한 결과와 같게 맞춘다 — 편도면 처음 배송비 0 이 되도록 그동안 재부과한 배송비(`reshipping_fee_charged`)를 그 환불에 더해 돌려주고, 왕복이면 처음 배송비를 한 번만 받은 것으로 맞춘다. 반품비는 실제로 보낸 택배(요청)마다 받는다(나눠 보내면 택배비가 실제로 여러 번 든다). **이 환급은 발송 뒤 반품으로 생긴 재부과에만** — 발송 전 일부 취소(결정 42)로 재부과한 배송비는 그 택배를 실제로 보내는 데 쓴 배송비라 「처음 배송비」로 본다(발송 처리 때 `shipped_shipping_fee = shipping_fee + 그때까지의 재부과` 를 고정하고, 계산기는 이 값으로 「낸 주문 / 무료배송 주문」을 판단한다 — 최종 검토). 그다음은 10-8 기준 표 그대로: **단순 변심 전체 반품이면 처음 배송비를 돌려주지 않고(낸 주문), 하자·오배송 전체 반품이면 돌려준다(업체 부담)**. 처음부터 1개만 배송비를 내고 산 회원과 같은 결과다(재검토 2026-10-28 — 앞 문장이 하자도 돌려주지 않는 것으로 읽혀 고침). G7 기대값: 25,000 × 3 무료배송(편도, 업체회수, 반품비 3,000) — 1개 반품 22,000(남은 50,000 ≥ 기준) → 1개 반품 19,000(재부과 3,000 + 반품비 3,000) → 마지막 1개 25,000 − 3,000(반품비) + 3,000(재부과 돌려줌) = 25,000. 합계 66,000 = 75,000 − 반품비 3,000 × 3.
44. **반품 요청은 줄마다 사유**(사용자 결정): 한 요청(한 택배) 안에 줄마다 사유(단순 변심·하자·오배송·기타)와 수량. 반품 배송비·보상액은 요청당 한 번 — 하자·오배송 줄이 하나라도 있으면 반품 배송비는 운영자 부담. 줄별 환불액은 줄 사유로 계산(하자 줄은 재계산 없이 전액, 변심 줄은 10-8 표). 주문당 열린 요청 1개는 그대로.
45. **모집 끝난 뒤 집계 중(CLOSING) 본인 즉시 취소 허용**(사용자 결정): 성사형 JOINED 주문은 CLOSING 중에도 본인이 바로 취소할 수 있다(청약철회). 판정은 판정 시점의 수량으로 — 이 취소로 불발이 될 수도 있다. 판정과 취소는 주문 조건부 전이로 하나만 이긴다.
46. **주문 상태는 줄 전체로**: 주문 CANCELED 는 Σ(`qty − canceled_qty`) = 0(발송 전 전부 취소), REFUNDED 는 Σ(`qty − canceled_qty − returned_qty`) = 0(받은 뒤 전부 반품 — 반품비가 남아 환불 누계가 결제액에 못 닿아도) 또는 운영 환불 누계가 남은 결제액을 모두 쓴 때(최종 검토: 금액 조건만으로는 반품비 때문에 영원히 REFUNDED 가 되지 않았다). REFUNDED·CANCELED 주문에는 운영 환불을 더 넣지 못한다. 일부 줄만 취소·반품되면 상태는 그대로이고 줄 수량만 바뀐다. 「진행 중 주문 1건」·COMPLETED 조건(결정 9)이 이 정의를 쓴다.
47. **발송은 주문 한 번에**: 줄마다 따로 발송하지 않는다(`group_buy_shipments` 는 주문당). 줄 하나라도 열린 요청이 있으면 주문 전체를 발송하지 못한다(10-3).
48. **옵션 전부 다 팔림도 마감**: 모든 옵션에 한도가 있고 모두 확정 = 한도이면(옵션 한도 합 < 공구 최대여도) 결정 27 과 같이 그 자리에서 CLOSING(종료 사유 `SOLD_OUT`). 옵션 줄이기 하한은 그 옵션의 확정 + 선점.
49. **중단·종료 공구의 남은 일**: 공구 CANCELED 뒤에도 이미 발송된 주문의 배송완료·반품 요청·환불은 계속된다. 마감 후 발주 공구의 `SHIPPING → CANCELED`(발송 일부만 된 뒤 남은 수량을 못 보낼 때)를 허용한다(commerce.md 10-2 전이 목록에 더한다) — 발송 전 주문만 환불(결정 29).
50. **판정 경쟁**: 마감 시각의 연장 판단과 마지막 1개 확정은 둘 다 `status='RECRUITING'` 조건부라 하나만 이긴다(확정이 이기면 SOLD_OUT 마감, 연장이 이기면 연장 중 판매).

## 최종 검토 보강 (2026-10-28 — 트랜잭션·재고·금액·법/관행 네 관점, 코드·법령 사본으로 대조)
### 법·실제 공구 운영
51. **발송 예정일(공급시기 약정)** — 전자상거래법 제15조 ①: 선지급식은 공급시기를 따로 약정하지 않으면 대금 지급일부터 3영업일 안에 공급 조치. 그래서:
    - 성사형·마감 후 발주 공구는 `ship_planned_at`(발송 예정일)이 필수, 선착순 즉시 발송 공구는 `ship_within_business_days`(결제 후 N영업일, 기본 3 — 공구 설정값). 모집 시작 뒤 잠금 필드.
    - 상세·주문서·계약 통지 메일에 「발송 예정일」, 주문서 동의 「구매 조건」에 포함.
    - 예정일·기한을 넘긴 발송 전 주문은 대시보드·운영자 알림(경고, 19절 알람에 더함).
52. **발송 지연 공지**(사용자 결정 — 통지 + 취소권만): 운영자 「발송 지연 공지」(새 예정일·사유 필수) → `group_buy_delay_notices` 에 남기고 발송 전 주문 전원에게 앱 + 메일(제15조 ② 지체 없는 통지). 그때부터 그 주문들은 **승인 없이 즉시 전액 취소**할 수 있다(성사 뒤·준비중이어도 — 사업자 사정이므로 배송비 재부과 없음). 보상(포인트 등)은 두지 않는다 — 나중에 붙일 자리만(지연 공지 행에 보상 칸 없음). 수정 이력에 예정일 변경을 남긴다.
53. **일부 옵션 공급 불가**(사용자 결정 — 답이 없으면 나머지만 받기):
    - 관리자 「옵션 공급 불가 처리」: 옵션 지정 → 그 옵션의 발송 전 줄 전부 CANCEL + 환불(원인 `SUPPLY_FAILURE` — **배송비 재계산 없음**, 사업자 귀책) + 그 옵션 최대를 확정 수량으로 줄임(줄이기 하한 예외, 이동 `REDUCE`) + 모집 중이면 그 옵션 판매 중지. 감사 `GROUPBUY_SUPPLY_FAILURE`.
    - 그 옵션 말고도 줄이 남은 주문의 회원에게 앱 + 메일로 「나머지만 받기 / 전체 취소」를 묻는다(`group_buy_supply_choices`, 기한 3영업일 설정값). 전체 취소를 고르면 남은 줄도 전액(처음 배송비 포함) 환불, **기한까지 답이 없으면 나머지만 받기**. 기한 전에는 그 주문을 발송하지 않는다.
    - 공급 불가로 줄이 모두 빠진 주문은 전액(배송비 포함) 환불.
    - 운영 환불(결정 32)에 사유 「사업자 귀책」 표시를 두고, 귀책이면 계산기가 재부과·반품비를 적용하지 않는다.
54. **교환 — 하자·오배송만**(사용자 결정): 하자·오배송·배송 중 훼손 반품 요청은 회원이 「환불」 또는 「교환」을 고른다(소비자분쟁해결기준 「교환 또는 구입가 환급」). 교환은 회수 확인 뒤 **같은 주문에 재발송**(`group_buy_shipments.kind = EXCHANGE`, 환불 0, 판매 수량 그대로 — 재고 소진은 「교환 출고」 이동으로 기록, 반품비·재발송비 운영자 부담). 공구가 끝났거나 교환할 물건이 없으면 운영자가 환불로 바꾼다(회원 알림). 단순 변심 교환은 받지 않고 「환불 뒤 다시 주문」 안내. 교환 출고분은 에스크로 배송 정보도 다시 등록.
55. **청약철회 제한 표시·반품 거절 근거** — 제17조 ②⑥⑤:
    - 공구별 `withdrawal_restriction`(제한 사유 안내 문구, 선택) — 상세·주문서·계약 메일에 표시(⑥ 「쉽게 알 수 있는 곳에 명확히 표시」하지 않으면 제한할 수 없다).
    - 반품 거절은 사유 코드 필수(`CUSTOMER_DAMAGE`·`USED_VALUE_LOSS`·`TIME_VALUE_LOSS`·`OUT_OF_WINDOW`·`ALREADY_SHIPPED`) + 운영자 근거 사진 최대 3장 — ⑤ 다툼은 사업자가 증명. 계약·청약철회 기록으로 5년 보관.
    - 등록 화면 경고: 「개봉하면 반품 불가」처럼 법보다 넓게 막는 문구, 「재판매 금지·리셀 의심 시 취소」 문구(공정위 2023 시정 사례)는 쓰지 않는다. 주문 제작 공구(제17조 ②6호 — 서면 동의 필요)는 2b 에서 지원하지 않는다.
56. **사업자 정보는 사이트 전체에** — 제10조 ①: 판매 스위치가 켜져 있으면 user-web 공통 푸터에 상호·대표자·주소(불만 처리 장소)·전화·이메일·사업자등록번호·통신판매업 신고번호·호스팅 사업자·약관 링크. 공구 화면 하단에는 결정 18 그대로. [확인 필요 — 공정위 사업자정보공개 페이지 링크 의무]
57. **계약 통지 메일·주문서 항목 보강** — 제13조 ② 6·8호, 시행령 제20조 2호: 환불 지연배상금(연 15%) 조건, 판매조건 제한(모집 기간·총 수량·1인 한도·차수·성사 조건), 발송 예정일(결정 51), 청약철회 제한 안내(결정 55), 분쟁 처리 안내(1372 소비자상담센터·한국소비자원·전자문서·전자거래분쟁조정위원회).
58. **정상가(할인율) 근거** — 표시·광고 고시(종전거래가격 — 판 적 없는 가격을 정가로 쓰면 위반, 공정위 2025~2026 과징금 사례): `normal_price_basis`(공식 판매처가·직전 차수 공구가·시가·종전 판매가) + `normal_price_source`(내부 메모·URL), 상세에 「정상가 기준: ○○」. 「최저가」 문구 금지 안내. 차수 공구는 직전 차수 공구가가 기준이 될 수 있다. 「한정」은 시스템 문구로 쓰지 않고 「총 N개」만(운영자 설명에 「한정」이 있으면 추가 입고 때 경고).
59. **주문서 화면 규칙**(다크패턴 금지 — 제21조의2, 시행령 제9조): 옵션·수량 기본 선택 없음, 동의 체크·결제대금예치 선택 기본 체크 없음, 배송비·반품비는 처음부터 금액으로. 「선불·기프트·지역화폐 카드는 부분 취소가 안 돼 계좌로 환불될 수 있어요」 안내.
60. **Q&A 답변 기한** — 설정값 「답변 기한 2영업일」, 대시보드 「기한 지난 Q&A」(관행, 법정 의무 아님).

### 트랜잭션·워커
61. **환불은 별도 워커**: 불발·중단의 주문별 환불(결정 37 ②)은 상태 워커 안에서 하지 않고 워커 키 `groupbuy-refund`(1분, 바퀴당 상한 50건, `SKIP LOCKED`)가 「CLOSED_FAILED·CANCELED 공구의 비종결 주문」을 sweep 한다 — 대량 환불이 마감·만료 처리를 막지 않고, 중간에 멈춘 일도 이 워커가 이어 받는다. 각 주문은 **지금 주문 상태를 다시 잠가 읽고** 분기(그 사이 결제 중 → 확정으로 바뀐 주문도 환불). 즉시 PG 시도는 결제 모듈이 커밋 뒤에 하므로 워커 바퀴당 상한으로 시간 제한.
62. **`onPaid` 는 예외를 던지지 않는다**: 상태 불일치·한도·유일 충돌 같은 도메인 사정은 모두 REJECT 로 바꾼다(예외는 DB 장애 같은 인프라 오류뿐 — 예외면 결제 확정이 영원히 재시도된다). REJECT 경로는 주문 PENDING → EXPIRED(원인 `REJECTED_CLOSED`·`LATE_PAYMENT`) + RELEASE 를 **주문 조건부 전이가 이긴 쪽만**(선점 만료 워커와 이중 RELEASE 방지). 이 만료는 이탈 횟수에 세지 않는다(돈을 낸 회원). 「선점 시간 안」 판단은 지금 시각이 아니라 포트원 `paidAt` 과 비교(결제 계약 결정 4 와 같다).
63. **결제 다시 열기**: 남은 선점이 1분 미만이면 `GROUP_BUY_HOLD_EXPIRED`(결제 모듈의 최소 유효 시간 1분 — `PaymentOrderService.MIN_VALID`). 아직 유효한 READY 결제가 있으면 새로 만들지 않고 그 결제창 정보를 돌려준다(두 번 눌러도 앞 결제가 늦은 결제가 되지 않게).
64. **에스크로 호출 위치**: 배송 정보 등록·구매 확정은 트랜잭션 밖(커밋 뒤)에서 부르고, 결과는 `group_buy_shipments.escrow_logistics_at`·`escrow_completed_at` 에 조건부로 기록, 실패는 재시도 대기열(작은 표 `group_buy_escrow_jobs` — 시도·다음 시도·마지막 오류). 포트원의 「이미 등록·완료」 응답은 성공으로. 구매 확정 전 부분 취소가 PG 에서 막히는지는 2b-5 확인 — 막히면 반품 환불은 계좌 송금(결정 67).
65. **불변식 검사는 한 SQL**(같은 스냅샷): 공구·옵션 수량, 주문 줄 합, 결제 금액(결정 39 — `anomaly IS NULL` 인 받아들인 결제만, 진행 중 취소분을 뺀 식)을 한 문장으로.
66. **부분 유일 색인 명시**(어드바이저리 잠금 뒤 마지막 방어선): 진행 중 주문 `(group_buy_id, user_id) WHERE status IN (PENDING_PAYMENT…SHIPPED)` → `GROUP_BUY_ALREADY_JOINED` · 열린 요청 `(participation_id) WHERE status IN (REQUESTED, APPROVED)` → `GROUP_BUY_REQUEST_OPEN` · 옵션 없는 줄 `UNIQUE NULLS NOT DISTINCT (participation_id, option_id)`(postgres 15+ — 시험 DB 18) · 줄의 옵션이 같은 공구 것인지 복합 FK `(option_id, group_buy_id)`.

### 금액
67. **PG 로 못 돌려주는 상품 환불은 계좌 송금으로 전환**: `group_buy_refunds.method`(`PG_CANCEL`·`BANK_TRANSFER`·`NONE`). 취소가 GAVE_UP 이고 PG 가 영구 거절(취소 기간 지남·부분 취소 불가 카드·에스크로 확정 뒤)이면 운영자 「계좌 송금으로 전환」 — 취소 줄 MANUAL + `commerce_bank_transfers` 행 + 환불 기록 method 변경을 한 트랜잭션, 감사(돈 관련). 결정 6 의 보상 초과분과 같은 표.
68. **반품 환불이 마이너스가 되면 회원이 고른다**(사용자 결정 2026-10-28 — 운영자가 떠안지 않는다): 단순 변심 반품은 반품 비용이 회원 부담이라(제18조 ⑨) 반품비·재부과 배송비가 상품값보다 크면 그 부족분도 회원 몫이다.
    - 반품 신청 화면이 회수 방식별 결과를 미리 보인다 — 예(47,000 + 5,000 무료배송, B 5,000 변심 반품): 업체회수 −1,000 / 직접 발송 +2,000.
    - 결과가 마이너스인 방식을 고르면 **부족분을 카드로 추가 결제해야 접수**된다(결제 모듈 새 주문 종류 `GROUP_BUY_RETURN_FEE` — 대상 = 반품 요청, 금액 = 부족분, 선점 없음, 유효 30분. 결제 확정 때 요청이 접수(REQUESTED)되고, 결제하지 않으면 요청은 접수 전 상태로 남았다가 하루 뒤 닫힌다). 직접 발송으로 바꿔 플러스가 되면 추가 결제 없이 그 금액을 환불한다. 직접 발송도 마이너스면(아주 싼 상품) 직접 발송 + 추가 결제.
    - 요청이 철회·거절되거나 승인 때 다시 계산한 값이 달라지면 추가 결제의 남는 몫을 그 결제에서 부분 취소한다(같은 환불 기록 표, 원인 `RETURN_FEE_REFUND`).
    - 환불액이 정확히 0 이면 취소를 넣지 않고 환불 기록만 완료(method `NONE`).
    - 이렇게 받은 돈까지 실제로 받은 반품비·재부과로 `return_fee`·`reshipping_fee`·`reshipping_fee_charged` 에 남긴다 — 나중에 마지막 반품에서 재부과를 돌려줄 때 이 값을 쓴다. 하자·오배송은 빼는 것이 없어 마이너스가 되지 않는다.
    - [법무 확인] 회수 전에 반품 비용 부족분을 먼저 결제하게 하는 것이 청약철회 방해로 보이지 않는지 — 직접 발송이라는 비용 없는 길을 늘 함께 열어 두는 것으로 대비한다.
69. **불발·중단·지연 취소·공급 불가의 「전액」** = 그 주문의 남은 결제액(`cash_amount − refunded_amount − compensation_amount`) — 앞서 일부 취소가 있었으면 그만큼 뺀다. 이들이 처리되면 그 주문의 열린 반품·취소 요청은 자동 종료(`SUPERSEDED`).
70. **고액 문턱은 운영자가 금액을 정하는 환불에만**(사용자 결정 2026-10-28 — 로봇 공구는 상품 하나가 백만 원대라 금액 기준이면 거의 모든 반품이 고액이 되어 승인이 밀리고 3영업일 환급 기한을 넘길 위험): 고액 기준은 환불을 나눠 돌려주는 장치가 아니라 **누가 승인하느냐**다 — 환불은 금액과 관계없이 늘 PG 취소 한 번에 나간다.
    - **서버가 계산한 환불은 금액과 관계없이 일반 권한**(`commerce.refund.approve`·`commerce.order.manage`)으로 바로: 발송 전 취소 승인, 반품 승인(계산기 값 그대로), 공급 불가, 지연 취소, 불발·중단. 회원 본인 취소·늦은 결제 자동 취소는 승인 자체가 없다.
    - **운영자가 금액을 직접 넣는 환불에만** `commerce.refund.large`: 요청 없는 운영 환불(결정 32), 고객 택배비 보상, 계산값을 고쳐 승인하는 경우, 계좌 송금 전환 중 금액을 바꾸는 경우. 그 주문의 이런 환불 **누계**가 문턱(설정값, 기본 100,000원, **0 = 끔**) 이상이 되는 건부터(쪼개서 피하지 못하게). 권한이 없으면 승인 대기로 올려 권한자에게.
    - 모든 환불은 감사(돈 관련, 5년). commerce.md 13-2 「고액 공구 환불(1회 100,000원 이상)」을 이 뜻으로 고친다.
71. **운영 환불 뒤 반품**: 운영 환불도 줄별 금액으로 나눠 기록(금액 비례, 원 미만 버림·남는 원은 마지막 줄) → 계산기는 「줄 금액 − 줄 기환불」로 계산하고 최종은 `min(계산, 남은 결제액)`. 열린 요청이 있는 주문에는 운영 환불을 막는다.
72. **미리 보기 ↔ 승인 · 견적 ↔ 주문**: 요청에 `preview_amount` 저장 — 승인 때 다시 계산한 값이 다르면 두 값과 사유를 보이고 운영자가 확인 뒤 확정. 주문 API 는 화면이 본 금액(`expectedAmount`)을 **비교용으로만** 받아 다르면 409 `GROUP_BUY_AMOUNT_CHANGED`(청구액은 늘 서버 계산).
73. **콘솔 직접 취소가 있는 결제**: 그 결제에 열린 대사 이슈 `CANCEL_MISMATCH` 가 있으면 공구 환불 승인을 막는다(409 `GROUP_BUY_PAYMENT_UNRECONCILED` — 대사 처리 뒤 승인). 결제 모듈 후속(2a): 재시도 때 포트원 잔액(`amount.total − amount.cancelled`)을 쓰는 보강 [확인 필요].
74. **MANUAL 의 실제 처리 방법**(결제 모듈 소폭 — 2b-3, 결정 16 과 함께): 「수동 처리 완료」에 방법을 받는다 — 콘솔 취소면 그 취소 ID 를 줄에 붙이고 취소 누계까지 올린다(SUCCEEDED 와 같게 — 대사 이슈가 매일 다시 열리지 않게), 계좌 송금이면 결정 67. 불변식·대사는 송금분을 따로 뺀다.
75. **매출을 나눠 셀 수 있게**: `group_buy_refunds` 에 `item_refund`·`shipping_refund`·`return_fee`(−)·`reshipping_fee`(−)·`reshipping_refund`(+)·`compensation_pg`·`compensation_bank`·`completed_at`. 매출 결제 총액은 받아들인 결제만, 자동 취소(AUTO_REJECT)는 환불이 아니다. 불변식에 「주문별 완료 환불의 PG 분 합 = 취소 누계」를 더한다.
76. **포인트 결제 대비 열**(11절, 3+ 단계): 주문에 `point_amount`(0)·`cash_amount`(= amount) 를 지금 두고, 남은 결제액·불변식·고액 문턱을 처음부터 `cash_amount` 로 쓴다. `amount ≥ 1` CHECK.

### 재고
77. **한도 없는 옵션도 옵션 행을 늘 갱신**: 조건만 `max_qty IS NULL OR current_qty + reserved_qty + :q <= max_qty`. 옵션 CHECK `0 ≤ current, 0 ≤ reserved, (max IS NULL OR current + reserved ≤ max)`. 불변식: 옵션 있는 공구는 Σ옵션 current = 공구 current, Σ옵션 reserved = 공구 reserved(옵션 최대 합과 공구 최대는 관계 없음 — 어느 쪽이 커도 된다).
78. **옵션 전부 다 팔림의 동시 확정**: `onPaid` 의 마감 UPDATE 를 한 문장으로 — `UPDATE group_buys SET status='CLOSING', close_reason='SOLD_OUT' … WHERE id=? AND status='RECRUITING' AND (current_qty = max_qty OR NOT EXISTS (옵션 중 max IS NULL 또는 current < max))`. 두 옵션의 마지막 1개가 동시에 확정돼 서로 못 보는 경우는 1분 워커 안전망이 같은 조건으로 잡는다(10-7 ③에 옵션 조건을 더한다).
79. **종료 사유 열** `group_buys.close_reason`(`SOLD_OUT` 다 팔림 · `TIME_UP` 기간 끝 · **`EARLY_CLOSED` 운영자 조기 마감**(사용자 결정 2026-10-28) · `CANCELED` 중단) — CLOSING 전이와 같은 문장에서 기록. 다 팔림 뒤 CLOSING 중 취소로 수량이 줄어도 표시·「다음 차수 알림 받기」·목록 탭은 이 열로.
80. **처음 수량과 추가 입고 표시**: `initial_max_qty`(모집 시작 때 고정, 옵션도) — 「추가 입고 N개」 = max(0, `max_qty − initial_max_qty`), `added_qty` = ADD 합. 불변식 `max_qty = initial_max_qty + ΣADD − ΣREDUCE`(이동 줄 기준, 모집 시작 이후만). 옵션 추가 입고·줄이기는 **같은 수만큼 공구 최대도** 움직인다. 한도 없는 옵션에는 옵션 ADD/REDUCE 를 거절(공구 ADD 로).
81. **모집 중 본인 취소로 풀린 수량도 「수량 풀림」**: 알림(신청자)과 DEMAND 연장 조건(결정 25)에 CANCEL 을 더한다. 알림은 공구·옵션 둘 다 남은 수량 > 0 일 때만(헛알림 방지). 연장 판단은 이동 줄 `kind IN (RELEASE, CANCEL, RESTOCK, ADD) AND created_at ∈ [end_at − 5분, 판단 시각]` — 색인 `(group_buy_id, created_at)`.
82. **이동 줄의 이후 값은 조건부 UPDATE … RETURNING 으로**(따로 SELECT 하면 다른 트랜잭션 값이 섞인다). 「마지막」은 `id` 순. CONFIRM·RELEASE 조건에 `reserved_qty >= :q` 를 넣고, 0 행이면 REJECT + `COMMERCE_ORDER_ANOMALY`(불변식이 이미 깨진 상태).
83. **`returned_qty` 는 반품 완료(회수 확인) 때** 처리 방법과 같은 트랜잭션에서 올린다. 요청 때 줄 수량 ≤ `qty − canceled_qty − returned_qty` 검사, 승인·완료 때 다시 검사.
84. **옵션별 남은 수량·품절 판정**: 옵션 남은 = min(옵션 `max − current − reserved`, 공구 `max − current − reserved`)(옵션 최대가 없으면 공구 값). 옵션 다 팔림 = 옵션 `current = max`(공구가 모집 중이면 추가 입고·다시 판매로 되살아날 수 있다고 안내), 잠시 품절 = 옵션 또는 공구의 `current + reserved = max`. `group_buy_watches` 에 `option_id`(선택)를 더해 옵션 품절 알림도 받는다.
85. **조회 때 정리는 본인 주문만**(10-4 「참여·견적 판정 때 지난 PENDING 정리」): 남의 주문까지 그 자리에서 만료시키면 잠금이 넓어진다 — 나머지는 워커.
86. **1인 한도 세부**: `round_limit_shared` 면 지금 차수의 `max_qty_per_user` 로 루트 전체를 센다(루트당 모집 중 차수는 하나라 어드바이저리 키는 공구 단위로 충분). `max_qty_per_user` null = 제한 없음 허용.

### 상품정보제공고시 (사용자 지적 2026-10-28 — 화면 확인 때)
87. **상품정보제공고시**(공정위 「전자상거래 등에서의 상품 등의 정보제공에 관한 고시」 — 통신판매업자는 품목별 필수 정보를 표시해야 하고, 누락·허위면 과태료[근거 조문·금액은 구현 때 원문 확인]). 공구 상세에 표로 보인다.
    - **근거 원문(2026-10-28 Chrome 으로 확인)**: 국가법령정보센터 행정규칙 「전자상거래 등에서의 상품 등의 정보제공에 관한 고시」 — [시행 2023. 1. 1.] 공정거래위원회고시 제2022-15호. 품목 **40개**: (1) 의류 (2) 구두/신발 (3) 가방 (4) 패션잡화 (5) 침구류/커튼 (6) 가구 (7) 영상가전 (8) 가정용 전기제품 (9) 계절가전 (10) 사무용기기 (11) 광학기기 (12) 소형전자 (13) 휴대형 통신기기 (14) 내비게이션 (15) 자동차용품 (16) 의료기기 (17) 주방용품 (18) 화장품 (19) 귀금속/보석/시계류 (20) 농수축산물 (21) 가공식품 (22) 건강기능식품 (23) 어린이제품 (24) 악기 (25) 스포츠용품 (26) 서적 (27) 호텔/펜션 예약 (28) 여행패키지 (29) 항공권 (30) 자동차 대여 (31) 물품대여(정수기·비데·공기청정기 등) (32) 물품대여(서적·유아용품·행사용품 등) (33)~(38) 디지털 콘텐츠·상품권/쿠폰 등[이름은 원문에서 확인] (39) 기타 용역 (40) 기타 재화. **품목별 항목 표는 원문 페이지에 이미지로만 있다** — 카탈로그를 만들 때 이미지(또는 첨부파일)를 읽어 옮기고, 옮긴 날짜·고시 번호를 카탈로그에 적는다. 참고 안내: 11번가 「상품정보제공고시 가이드」(38개 품목 시절 자료 — 운영 원칙 참고용).
    - **일반원칙(원문 Ⅱ)을 규칙으로**: ② 상품과 가장 유사한 품목을 고르고, 없으면 (39)/(40) ③ **제공할 수 없는 항목은 비우지 않고 구체적 사유**(+ 그에 준하는 정보)를 적는다 — 빈 항목·내용 없는 항목은 고시 위반 ④ 제조연월(일)·유통기한·사용기한·품질유지기한·소비기한은 「가장 빠른 날짜」·「범위」·「주문일·발송일 기준 기간」으로 대신 적을 수 있다 ⑤ 품질보증기준은 구체적 피해보상기준 또는 「○○○보상규정에 따라 보상」 + 그 규정을 알 수 있는 방법, (10) 중 노트북·(11)~(14)는 소비자분쟁해결기준보다 불리하면 그 사실·내용 ⑥ KC 인증정보는 KC 마크 + 식별부호(인증번호·신고번호), 식별부호가 없으면 인증 사실, **KC 면제 품목은 면제 사실** ⑦ **부수 상품**(예비품·추가 부속품·기능 추가 부속물)은 기본 상품 정보만, **별개 상품을 옵션으로 함께 팔면 상품마다 구분해 따로** 제공.
    - **데이터 — 품목별 양식을 여러 장**(사용자 지시 2026-10-28 「품목별로 분류해서 고시 양식 별도로 기입」): 표 `group_buy_notices`(공구·차례·품목 코드·제목(예 「본체」·「충전기」)·적용 옵션 ID 목록(비면 공구 전체 = 기본 상품)·카탈로그 버전) + `group_buy_notice_values`(양식·항목 키·값 또는 「제공 불가」 + 사유). 공구는 기본 양식 하나가 필수이고, 별개 상품 옵션이 있으면 그 옵션용 양식을 더한다(부수 상품 옵션은 기본 양식으로 충분 — 등록 화면 안내). 한 옵션은 양식 하나에만 속한다.
    - **카탈로그는 서버가 정본**: 40개 품목과 항목(키·이름·필수·종류 — 글·날짜 대체 표기·KC 인증·품질보증 등 원칙 ④⑤⑥ 이 붙는 항목 표시·안내 문구)을 데이터 파일 `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json` 하나로 두고 `GroupBuyNoticeCatalog` 가 읽는다(고시 개정 시 새 버전 파일, 기존 공구는 만든 때 버전 유지). 관리자 화면은 서버가 준 카탈로그로 양식을 그린다.
    - **규칙**: 모집 시작 때 모든 양식의 모든 항목이 「값」 또는 「제공 불가 + 사유」로 차야 하고, 옵션이 있으면 모든 옵션이 어느 양식에 속해야 한다(발송 예정일과 같은 검사 — 409 `GROUP_BUY_NOTICE_INCOMPLETE`, 빈 양식·항목을 함께 돌려준다). 「상세페이지 참조」만 적은 항목은 막지 않되 경고(원칙 ③ 취지 — 항목마다 직접 적기 권장). 모집 뒤에도 고칠 수 있다(정보 정정 — `group_buy_revisions` 에 사본, 표시·광고 기록 6개월). 추가 차수 복사 대상.
    - **KC 인증**: 전기·전자·어린이 제품 등 인증 대상 품목은 고시 항목의 인증 정보(KC 인증번호 등)를 필수로 두고 화면에 그대로 보인다[인증 대상 여부는 품목·상품마다 운영자 책임 — 등록 화면 안내].
    - **화면**: 회원 상세에 「상품정보제공고시」 — 양식마다 제목·품목 이름·항목 표(「제공 불가」 항목은 사유를 그대로), 옵션을 고르면 그 옵션의 양식을 펼친다. 모바일은 「상품정보제공고시 보기」 단추로 열어도 된다(11번가 안내). 주문서에서 열어 볼 수 있는 링크. 관리자 공구 등록·수정에 「고시 양식」 구획 — 양식 더하기(품목 고르기 → 그 품목 항목 칸이 생김)·적용 옵션 고르기·항목마다 값 / 「제공 불가」 체크 + 사유·날짜 항목은 대체 표기 고르기·KC 항목은 인증번호/면제 사실·빈 항목 표시·「상세페이지 참조」 경고·부수 상품/별개 상품 안내.
    - **판매자 추가 항목**(사용자 지시 2026-10-28): 양식마다 고시 항목 뒤에 판매자가 「항목 이름 + 내용」을 자유롭게 더할 수 있다(예: 「원산지 세부」·「보관 방법」·「호환 기종」·「사이즈 표」). `group_buy_notice_values` 에 `custom = true` 줄(항목 키 없음, 이름 1~40자·내용 1~1,000자 평문, 양식당 최대 20개, 차례 지정). 선택 사항이라 모집 시작 검사 대상이 아니고(빈 줄은 저장하지 않는다), 고시 항목과 같은 이름은 막는다(고시 항목을 대신하지 못하게). 회원 화면은 고시 항목 표 아래에 「판매자 추가 정보」로 이어서 보인다. 수정 이력·차수 복사 대상.
    - **예시(목업·local 시드) 품목을 넓힌다**(사용자 지시 2026-10-28 — 로봇 전자제품만 있으면 안 된다): 쌀·과일·생선(20 농수축산물), 김치·소스 등(21 가공식품), 티셔츠(1 의류), 운동화(2 구두/신발)에 로봇 키트(8·12·23)를 섞고, 각 공구의 고시 양식을 그 품목으로 채운다(신선식품은 날짜 항목 대체 표기 예시 포함).
    - **테스트 G43**: 항목이 빈 채 모집 시작 → 거절(빈 양식·항목 목록) · 추가 항목 이름이 고시 항목과 같으면 거절 · 추가 항목 20개 초과 거절 · 「제공 불가」는 사유 없으면 거절 · 어느 양식에도 속하지 않은 옵션이 있으면 거절 · 모집 뒤 수정은 수정 이력에 남음 · 추가 차수에 양식 복사 · 카탈로그 버전 고정.

### 옵션 축·단품·추가 구성상품 · 상세 화면에서 고르기 (사용자 결정 2026-10-28 — 화면 확인 때)
88. **일반 이커머스와 같은 구조로 바꾼다**(결정 23·28·48·77·84 등에서 「옵션」이라 쓴 수량·가격 단위는 이제 **단품**이다):
    - **옵션 축과 값**: 공구마다 축 0~3개(예 색상·사이즈), 축마다 값(예 블랙·화이트 / S·M·L). 표 `group_buy_option_axes`(공구·차례·이름) · `group_buy_option_values`(축·차례·값).
    - **단품(SKU)**: 실제로 파는 단위. 표 `group_buy_skus`(이전 `group_buy_options` 를 대신한다): 공구·종류(`MAIN` 본 상품 / `ADDON` 추가 구성상품)·축 값 조합(MAIN — 축이 있으면 조합마다 하나, 운영자가 필요 없는 조합은 판매 끄기)·이름(MAIN 은 「블랙 / M」 자동, ADDON 은 상품 이름)·가격(MAIN = 공구가 + 추가금, ADDON = 자기 가격)·한도 수량(`max_qty` null = 공구 한도만)·`current_qty`·`reserved_qty`·`initial_max_qty`·판매 여부·차례. 축이 없는 상품(쌀 등)은 MAIN 단품 하나.
    - 주문 줄·수량 이동·반품·다시 판매·추가 입고/줄이기·옵션 공급 불가(결정 53 → 「단품 공급 불가」)·품절 표시·수량 풀림 알림(`group_buy_watches.sku_id`)은 모두 **단품 단위**. 모집 뒤 축·값·조합 구성은 잠금 필드(가격·옵션 구성 수정 불가 — 10-2).
    - **추가 구성상품(ADDON)**(고시 원칙 ⑦ 의 「별개 상품」): 상세 화면 「추가 구성상품」 구역에서 본 상품과 함께 담는다. **단독 구매 불가** — 주문에 MAIN 단품 줄이 하나 이상 있어야 하고, 본 상품 줄을 모두 취소·반품하면 남은 추가상품 줄도 함께 취소 대상으로 묻는다. 공구 최대 수량·성사 판정(`min_qty`)·1인 한도는 **MAIN 단품 수량만** 센다(추가상품은 자기 한도만). 무료배송 기준 금액에는 포함. 고시 양식은 ADDON 단품마다 따로 필수(MAIN 단품들은 기본 양식).
    - **고르는 곳은 상세 화면**(사용자 지적 — 일반 쇼핑몰처럼): 상세에서 축을 차례로 고르면(앞 축을 고르면 뒤 축의 품절·판매 안 함 값은 회색, 단품마다 남은 수량·추가금 표시) 「선택한 상품」 목록에 줄로 쌓이고 줄마다 수량 조절·삭제, 추가 구성상품도 같은 목록에, 합계는 서버 견적 → 「구매하기」. 주문서는 **고른 목록을 확인만** 하고(바꾸려면 상세로 돌아가기) 배송지·동의·결제만 받는다. 장바구니는 범위 밖이므로 고른 목록은 화면 상태(새로고침 대비 주소 쿼리 또는 세션 저장)로 주문서에 넘긴다. 선점은 지금처럼 「결제하기」 때.
    - 관리자 등록: 「옵션」 구획에서 축·값 입력 → 조합 표가 생기고 조합마다 추가금·한도·판매 여부 입력, 「추가 구성상품」 구획에서 이름·가격·한도·이미지, 고시 양식 적용 대상은 단품 목록에서 고른다.
    - **테스트 G44**: 축 2개 조합 생성·판매 끈 조합 주문 거절 · 단품별 한도·품절 · 추가상품 단독 주문 거절 · 성사 판정·1인 한도는 MAIN 수량만 · 본 상품 전부 취소 때 추가상품 처리 · 고시 양식 ADDON 필수.

### 상품 마스터 · 카테고리 · 공급가 (사용자 결정 2026-10-28 — 대형 이커머스 ERD 비교 뒤)
89. **상품과 공구를 나눈다**(같은 상품을 차수·조건을 바꿔 반복해서 판다):
    - **상품** `group_buy_products`: 이름·요약·사양·설명·이미지·정상가 근거·청약철회 제한·**카테고리**·**고시 양식**(결정 87 — 상품에 붙음)·옵션 축/값·**상품 단품** `group_buy_product_skus`(조합·기본 공급가·판매 여부). 관리자 「상품」 화면(`commerce.product.manage`, 조회 `commerce.product.read`).
    - **공구** = 상품 하나 + 판매 조건(공구가·기간·최소/최대·1인 한도·배송비·발송 예정일·연장 방식). **공구 단품** `group_buy_skus` = 상품 단품마다 이 공구의 추가금·한도·판매 여부·**공급가**·수량 열(확정·선점·처음). 추가 구성상품 = 다른 상품의 단품을 ADDON 으로 연결(고시는 그 상품 것). 공구 등록은 「상품 고르기 → 단품별 설정 표」.
    - 모집 시작 때 상품 내용(설명·이미지·고시·단품 이름)의 사본을 공구 수정 이력에 남긴다(표시·광고 기록). 상품의 설명·이미지·고시를 고치면 진행 중 공구에도 바로 보이고(정보 정정) 그 공구 이력에 사본. 상품의 옵션 축·단품 구성은 그 상품을 쓰는 공구가 모집 중이면 잠금(새 단품은 다음 공구부터). 추가 차수 = 같은 상품으로 새 공구(조건만 복사).
    - **카테고리** `group_buy_categories`: 2단·관리자 관리·차례·숨김, 상품마다 하나 필수, 회원 목록 필터.
    - **공급가 필수**: 상품 단품의 기본 공급가(선택)를 공구 단품으로 복사해 고친다. **모집 시작 때 모든 공구 단품에 공급가**(0 은 명시 입력만, 빈 값은 409 `GROUP_BUY_SUPPLY_PRICE_MISSING`). 판매가 < 공급가면 역마진 경고 후 확인해야 진행. 보는 권한 `commerce.product.price` 또는 `commerce.settlement.read`, 회원 응답에는 없음. 모집 뒤에도 고칠 수 있고 감사(돈 관련).
    - **테스트 G45**: 상품 하나로 공구 둘 · 모집 시작 사본 · 모집 중 상품 단품 구성 잠금 · 공급가 빈 값 거절·0 허용·역마진 확인 · 공급가는 회원 응답에 없음 · 카테고리 필터.

## Q&A 규칙 (2b-6 — 사용자 결정 2026-10-28)
- **표**: `group_buy_questions`(공구·회원·본문 평문 ≤ 1,000자·비밀글·`forced_secret`(개인정보로 자동 전환)·상태 OPEN/ANSWERED·숨김·지운 시각·보관 만료일) · `group_buy_answers`(질문·운영자·본문·시각) · `group_buy_answer_revisions`(답변 이전 내용·고친 운영자·시각).
- **누가·언제**: 질문은 로그인한 정상 회원(본인인증 없이). 공개된 공구(모집 예정·중·종료) 어디서나, 중단(CANCELED)된 공구는 닫는다. 정지 회원은 못 쓴다(글쓰기와 같다), 공구 참여 제한(`GROUP_BUY_BAN`)은 질문을 막지 않는다. 질문 쓰기 속도 제한(기존 글쓰기 제한 모양).
- **보기**: 공개 질문은 누구나, 비밀글은 작성자와 운영자만. Q&A 는 공구마다 따로이고 상세에 「이전 차수 문의 보기」 링크.
- **개인정보 자동 비밀글**(사용자 결정): 본문에 전화번호·이메일·주소 모양이 보이면 서버가 비밀글로 바꿔 저장하고(`forced_secret`) 응답에 그 사실을 싣는다. 작성 화면은 「연락처·주소가 보여 다른 회원에게 보이지 않게 비밀글로 바꿨어요」를 안내하고, 쓰는 동안에도 「연락처·주소는 쓰지 마세요」를 보인다.
- **작성자**: 답변 전에는 고치거나 지울 수 있다. 답변 뒤에는 고칠 수 없고, 공개 → 비밀글 전환과 지우기만. 지워도 **보관 기간 동안 감춘 채 남는다**.
- **운영자**: 답변·답변 수정(이전 내용은 `group_buy_answer_revisions` — 사용자 결정), 숨김(사유 5~200자·감사 `GROUPBUY_QNA_HIDE`), 비밀글 열람·관리자 Q&A 목록 조회는 개인정보 열람 감사(`GROUPBUY_QNA_VIEW`, 2년). 답변이 달리면 작성자에게 앱 알림, 새 질문·신고는 운영자 알림(결정 12).
- **신고**(사용자 결정): 기존 신고 기능(글·댓글)에 대상 종류 `GROUP_BUY_QUESTION` 을 더한다. 처리는 운영자 숨김과 같은 길.
- **보관**(사용자 결정): 단순 문의도 소비자 문의 기록으로 보고 **마지막 답변일(답변이 없으면 작성일)부터 3년** 보관 후 파기(commerce.md 17-3 「문의 3년」). 탈퇴 회원의 질문은 「탈퇴한 회원」으로 표시. 개인정보 처리방침에 항목을 더한다. [법무 확인 — 단순 상품 문의가 「소비자 불만·분쟁 처리 기록」에 드는지. 보수적으로 3년]
- 하자·오배송 환불은 Q&A 가 아니라 반품 요청(하자)으로 안내.

## 리뷰 규칙 (2b-6 — 2026-10-29 추가, **사용자 확정 2026-10-29**)
- **누가·언제**: 그 단품을 산 회원. 주문 줄이 배송완료된 뒤부터 배송완료일 + 90일까지, 주문 줄(단품)마다 하나. 전부 취소·반품된 줄은 쓰지 못한다. 쓸 수 있는지는 서버가 줄마다 정한다(`WRITABLE`·`WRITTEN`·`EXPIRED`·`NONE`).
- **내용**: 별점 1~5(필수) · 글 10~1,000자 · 사진 5장까지(반품 요청 사진과 같은 방식 — 먼저 올리고 ID 로 붙인다) · 산 단품 이름은 주문 줄 사본을 자동 표시.
- **작성자**: 작성 30일 안에 고치기(이전 내용은 `group_buy_review_revisions`), 언제든 지우기(같은 줄에 다시 쓰지 못한다 — 가정).
- **운영자**: 공식 답글 하나(수정 가능 — 회원 화면에 「답글 수정됨」), 숨김은 **사유 코드로만**(욕설·개인정보·광고·무관한 내용·기타 + 메모, 기타는 메모 필수 5~200자), 감사 `GROUPBUY_REVIEW_HIDE`·`GROUPBUY_REVIEW_REPLY_EDIT`. **낮은 별점은 숨김 사유가 아니다**(표시광고법·공정위 소비자 리뷰 지침 — 부정 리뷰 임의 삭제 금지). 숨긴 리뷰는 작성자에게 「숨김 처리됨 + 사유」로 보이고 평균·수에서 빠진다. 숨김 풀기는 사유 5~200자. 숨기면 열린 신고도 처리된다. 권한은 Q&A 와 같게(조회 `commerce.order.read`, 답글·숨김 `commerce.order.manage`).
- **신고**: 기존 `reports` 에 대상 `GROUP_BUY_REVIEW`(사유는 글·댓글 체계).
- **노출**: 공구 상세에 평균 별점·리뷰 수·별점 분포, 정렬(최신·별점 높은·낮은·사진 먼저), 사진 리뷰만. 목록 카드에는 평균과 수만. **같은 상품(상품 마스터)의 다른 차수 리뷰도 함께** 보이고 카드마다 「n차 구매」.
- **보상**: 없음(정책 미확정 — 사용자 결정 대기). 나중에 포인트 등 보상을 넣으면 **「대가를 받은 리뷰」 표시 의무**(표시광고법·공정위 추천·보증 심사지침)가 생긴다 — 리뷰에 보상 여부 열을 두고 화면에 표시해야 한다. 지금 화면은 「리뷰를 쓴 대가로 받은 혜택은 없어요」 한 줄.
- **회원 화면**: 내 주문 상세의 배송완료 줄에 「리뷰 쓰기」(기한 표시)·「리뷰 보기」, 내 리뷰 `/me/reviews`(상단 메뉴 서랍에 링크). **관리자 화면**: `/group-buy-reviews`(공개·신고 있음·숨김·전체 탭, 별점·사진·답글 없음 필터) + 상세 서랍(답글·작성자 수정 이력·신고·숨김).
- 목업 가정: 답글 수정은 이력을 남기지 않는다(시각·사람만), 탈퇴 회원은 「탈퇴한 회원」, 숨긴 남의 리뷰는 회원 목록에서 아예 빠진다(Q&A 는 자리 글을 남긴다 — 리뷰는 평균에서도 빠지므로).

## 새 규칙 → 테스트 (commerce.md 18절에 없는 것 — 구현 단위가 쓴다)
| # | 시나리오 | 기대 결과 | 단위 |
|---|---|---|---|
| G1 | 다시 판매: 모집 중 + 단순 변심 / CLOSING 뒤 / 오배송 / 폐기 | 확정 감소·알림 / `GROUP_BUY_NOT_RESELLABLE` / 거절 / 수량 그대로 | 2b-3 |
| G2 | 마지막 1개 결제 확정 직후(워커 전) 추가 입고·다시 판매 | 거절 — 이미 CLOSING | 2b-2 |
| G3 | 잠시 품절(확정 + 선점 = 최대)에서 추가 입고 → 선점 만료 | 추가분과 풀린 수량 모두 판매 | 2b-2 |
| G4 | 줄이기 하한(확정 + 선점, 성사형 `min_qty`) · 줄여서 확정 = 최대 | 하한 아래 거절 / 즉시 CLOSING | 2b-2 |
| G5 | 옵션 공구의 추가 입고·줄이기·다시 판매·동시 선점(여러 줄) | 공구·옵션 수량 함께 맞음, 교착 없음, 줄 하나 모자라면 주문 전체 실패 | 2b-2 |
| G6 | 부분 반품 다시 판매 뒤 · 전체 반품 폐기 뒤 · 불발 환불 뒤 · 중단 뒤 불변식 | 판매 수량 식과 일치 | 2b-2·2b-3 |
| G7 | 25,000 × 3 무료배송에서 1개씩 세 번 반품 | 재부과 배송비를 두 번 빼지 않고 마지막은 전체 반품 규칙 | 2b-3 |
| G8 | 추가 차수: 복사 범위·새 배송비·동시 두 번 클릭·이전 신청자 한 번 알림·한도 독립/합산 | 표대로, 차수 하나만 생김 | 2b-2·2b-4 |
| G9 | 결제창이 열린 동안 중단 → 그 뒤 결제 도착 | 주문 EXPIRED(`GROUP_BUY_CANCELED`)·선점 해제·늦은 결제 자동 취소·이탈 횟수 안 셈 | 2b-2 |
| G10 | 즉시 발송 공구: 배송 명단 내보내기·배송지 수정 기한 · 발송된 주문이 있는 공구 중단 | 주문 기준 기한 / 발송 전만 환불 | 2b-3 |
| G11 | Q&A: 비밀글 열람 권한·답변 전후 수정·지워도 3년 보관·개인정보 자동 비밀글과 안내·숨김 감사·신고·속도 제한·정지 회원 | 규칙대로 | 2b-6 |
| G11R | 리뷰(가정): 산 회원·배송완료 뒤 90일·줄마다 하나·모두 취소·반품 줄 거절 · 별점·글·사진 검증 · 30일 수정·이력·삭제 · 숨김 사유 코드만(별점 사유 없음)·감사·작성자에게 사유 · 숨긴 리뷰는 평균·수에서 빠짐 · 신고 · 같은 상품 다른 차수 묶음 | 규칙대로 | 2b-6 |
| G12 | 마감 4분 전 다시 판매·추가 입고(`DEMAND`) | 30분 연장 | 2b-2·2b-3 |
| G13 | 25,000 × 3 무료배송 발송 전 2개 취소 | 47,000 환불(배송비 재계산 3,000), `reshipping_fee_charged` 3,000 | 2b-2 |
| G14 | 한 요청에 하자 줄 + 변심 줄 · 하자 A 먼저 → 변심 B 마지막(편도·왕복) | 반품비 운영자 부담 · 줄별 금액 · 결정 43 맞춤 | 2b-3 |
| G15 | 보상액이 든 PG 취소 뒤 · 보상 > 남은 결제액 | 불변식(결정 39) · 넘는 부분 계좌 송금 | 2b-3 |
| G16 | 옵션 한도 합 < 공구 최대에서 옵션 모두 다 팔림 | 즉시 CLOSING(SOLD_OUT) | 2b-2 |
| G17 | 결제 확정 ∥ 중단 · 판정 · 반품 승인 동시 | 교착 없음, 확정이 이기면 중단 단계 ②가 그 주문을 환불 | 2b-2·2b-3 |
| G18 | 결제 다시 열기 · 자동 취소의 `onCanceled` · MANUAL 처리 | 선점 안 늘어남 · `refunded_amount` 그대로 · 공구 완료 표시·메일 | 2b-2·2b-3 |
| G19 | 일부 줄 취소 뒤 주문 상태 · 전부 취소 뒤 CANCELED · 그 뒤 새 주문 | 결정 46 대로 | 2b-2 |
| G20 | 1인 한도: 폐기 반품은 빼고 금액만 운영 환불은 그대로 · `round_limit_shared` · CLOSING 중 본인 취소로 불발 | 결정 28·24·45 대로 | 2b-2·2b-3 |
| G21 | 휴일이 낀 2·3영업일 경고 · CSV 수식 주입 · 같은 금액 두 번 환불 시도(GAVE_UP 중) | 휴일 건너뜀 · `'` 붙음 · 두 번째 거절(결정 38) | 2b-3·2b-4 |
| G22 | 웹훅 확정 ∥ 선점 만료 워커 · ∥ 결제 다시 열기 · ∥ 중단의 결제 중 주문 | 교착 없음(결제 행 먼저 — 결정 37), 둘 중 하나만 이김, RELEASE 한 번 | 2b-2 |
| G23 | 판정 ① ∥ 마지막 결제 확정(확정 = min_qty) | 성사(불발 아님) | 2b-2 |
| G24 | 불발 300건 · 환불 워커 중간에 서버 종료 | 상태 워커 주기 유지, 다음 바퀴가 이어서 끝냄, 중복 환불 없음 | 2b-2 |
| G25 | `onPaid` 도메인 사정(한도·유일 충돌·상태 불일치) | 예외 없이 REJECT, 주문 EXPIRED·RELEASE 한 번, 이탈 안 셈 | 2b-2 |
| G26 | 남은 선점 50초에 결제 다시 열기 · 유효한 READY 가 있는데 다시 열기 | `GROUP_BUY_HOLD_EXPIRED` · 같은 결제 돌려줌 | 2b-2 |
| G27 | 옵션 A·B 마지막 1개 동시 확정 · 한도 없는 옵션 구매 | 1분 안에 CLOSING(SOLD_OUT) · 옵션 행 수량 맞음 | 2b-2 |
| G28 | 모집 중 본인 취소로 풀림(마감 3분 전) | 수량 풀림 알림 · DEMAND 연장 | 2b-2 |
| G29 | 추가 입고·줄이기 이력 · 옵션 추가 입고가 공구 최대도 움직임 · 「추가 입고 N개」 표시 | 결정 80 식과 일치 | 2b-2 |
| G30 | 전체 반품(반품비 남음) 뒤 주문 상태 · 그 뒤 운영 환불 시도 | REFUNDED · 거절 | 2b-3 |
| G31 | 47,000 + 5,000 무료배송에서 B 변심 반품: 업체회수 / 직접 발송 / 추가 결제 안 함 / 접수 뒤 거절 | 1,000 추가 결제해야 접수 · 2,000 환불 · 접수 안 됨(하루 뒤 닫힘) · 추가 결제 1,000 취소 | 2b-3 |
| G32 | 발송 전 2/3 취소(재부과 3,000) 뒤 발송 · 남은 1개 변심 반품(편도·왕복, 업체회수) / 직접 발송 / 하자 반품 | 22,000(처음 배송비 3,000 돌려주지 않음 + 반품비) / 25,000 / 28,000(처음 배송비까지) — 처음부터 1개를 28,000에 산 회원과 같음 | 2b-3 |
| G33 | 하자 부분 반품 + 보상 4,000 뒤 운영 환불 · 남은 결제액 | 보상 뺀 남은 결제액으로 막힘(결정 38) | 2b-3 |
| G34 | 1,200,000원 반품 승인(계산기 값) · 운영 환불 3만원 + 8만원(쪼개기) · 문턱 0 | 일반 권한으로 바로 PG 취소 한 번 · 두 번째부터 고액 권한 · 모두 일반 권한 | 2b-3 |
| G35 | GAVE_UP(취소 기간 지남) → 계좌 송금 전환 · MANUAL(콘솔 취소 ID) | 송금 대기·감사 · 취소 누계 반영, 대사 이슈 다시 안 열림 | 2b-3 |
| G36 | 콘솔 직접 취소(CANCEL_MISMATCH 열림) 결제에 반품 승인 | `GROUP_BUY_PAYMENT_UNRECONCILED` | 2b-3 |
| G37 | 발송 예정일 필수(성사형) · 예정일 지남 경고 · 발송 지연 공지 뒤 준비중 주문 즉시 취소 | 저장 거절 · 경고 · 전액(재부과 없음) | 2b-2·2b-4 |
| G38 | 옵션 공급 불가: 그 옵션만 산 주문 · 섞인 주문(답 없음 / 전체 취소 선택) | 전액(배송비 포함) · 나머지만 발송·재부과 없음 / 전액 | 2b-3 |
| G39 | 하자 교환 · 교환할 물건 없음 | 재발송·환불 0·반품비 운영자 · 환불로 전환 알림 | 2b-3 |
| G40 | 반품 거절 사유 코드·근거 사진 없이 거절 · 청약철회 제한 문구 없는 공구에서 「사용으로 가치 감소」 거절 | 거절 · 거절(⑥ 표시 없으면 제한 못 함) | 2b-3 |
| G41 | 판매 스위치 켜짐 → 푸터 사업자 정보 · 꺼짐 | 표시 · 숨김 | 2b-4 |
| G42 | 주문서 기본 선택 없음 · `expectedAmount` 다름 | 동의·옵션 미선택이면 거절 · 409 `GROUP_BUY_AMOUNT_CHANGED` | 2b-2 |

## 감사·오류 코드 후보
- 감사 행위: `GROUPBUY_CREATE`·`GROUPBUY_UPDATE`·`GROUPBUY_START`·`GROUPBUY_CLOSE_EARLY`(일반) · `GROUPBUY_CANCEL`·`GROUPBUY_REFUND`·`GROUPBUY_REQUEST_APPROVE`·`GROUPBUY_STOCK_ADD`·`GROUPBUY_STOCK_REDUCE`·`GROUPBUY_RETURN_DISPOSE`·`GROUPBUY_ROUND_CREATE`·`COMMERCE_SALES_TOGGLE`·`COMMERCE_SHIPPING_POLICY_*`(돈 관련) · `GROUPBUY_ADDRESS_REVEAL`·`GROUPBUY_SHIPPING_EXPORT`·`GROUPBUY_ORDER_VIEW`·`GROUPBUY_QNA_VIEW`(개인정보 열람) · `GROUPBUY_QNA_HIDE`·`GROUPBUY_QNA_ANSWER_EDIT`(일반).
- 오류 코드: `GROUP_BUY_NOT_FOUND`(404) · `GROUP_BUY_NOT_RECRUITING`(409) · `GROUP_BUY_SOLD_OUT`(409) · `GROUP_BUY_OPTION_SOLD_OUT`(409) · `GROUP_BUY_LIMIT_EXCEEDED`(409) · `GROUP_BUY_ALREADY_JOINED`(409) · `GROUP_BUY_SALES_CLOSED`(403) · `GROUP_BUY_HOLD_EXPIRED`(409) · `GROUP_BUY_LOCKED_FIELD`(409 — 모집 시작 뒤 가격·기간·옵션 구성·배송비 수정) · `GROUP_BUY_QTY_BELOW_FLOOR`(409 — 줄이기 하한) · `GROUP_BUY_NOT_RESELLABLE`(409) · `GROUP_BUY_ROUND_EXISTS`(409) · `GROUP_BUY_ROUND_SOURCE_INVALID`(409) · `GROUP_BUY_REQUEST_OPEN`(409) · `GROUP_BUY_RETURN_WINDOW_CLOSED`(409) · `GROUP_BUY_QUESTION_ANSWERED`(409 — 답변 뒤 수정) · `GROUP_BUY_QUESTION_CLOSED`(409 — 중단된 공구) · `DATA_KEY_MISSING`(503).

## 2a 보강(고침, 2026-10-28)
포트원 취소 호출을 결제 주문 행 잠금 밖으로 옮기고, 한 결제에 보내는 중인 취소는 하나(V100)로 줄을 세웠다 — 공구 불발로 취소가 몰려도 확정·웹훅이 PG 응답을 기다리지 않는다. 상세는 `commerce-payment-contract.md` 「2a 보강 — 취소 호출을 잠금 밖으로」.

## 2b-2 구현 결과 (2026-10-28 — 브랜치 `claude/commerce-2b-2-groupbuy-core-d751c0`, develop 병합 전)
- **마이그레이션** shared `V101__group_buy_core.sql`(develop shared 마지막 V100 다음 — 합칠 때 다시 확인). 표: `group_buy_settings`(1행) · `shipping_policies`(배제 제약 `btree_gist` + 적용 시작 줄 불변 트리거) · `group_buys` · `group_buy_images` · `group_buy_options` · `group_buy_revisions` · `group_buy_status_history` · `group_buy_participations` · `group_buy_participation_items` · `participation_status_history` · `group_buy_qty_movements` · **`group_buy_refunds`** · `group_buy_watches` · `group_buy_delay_notices` · `group_buy_order_counters`. 결정 66 의 부분 유일(진행 중 주문)·`NULLS NOT DISTINCT`·복합 FK, 결정 38·76 의 금액 CHECK, 결정 51 의 발송 시기 CHECK.
- **코드**: shared `com.community.groupbuy` — `entity`(상태·종류 열거) · `dto`(주문·견적·남은 수량·등록 값·이벤트 `GroupBuyEvents`) · `exception`(`GroupBuyException` 한 클래스 + `GroupBuyBlockedException`) · `repository.master`(JDBC: `GroupBuyStore`·`GroupBuyQtyStore`·`ParticipationStore`·`GroupBuyRefundStore`·`ShippingPolicyStore`·`GroupBuyMemberStore`) · `service`(`GroupBuyAdminService`·`ShippingPolicyService`·`GroupBuyQuoteService`·`GroupBuyJoinService`·`GroupBuyQtyService`·`HoldExpiryService`·`ParticipationCancelService`·`GroupBuyRefundService`·`GroupBuyStateWorker`·`GroupBuyRefundWorker`·`GroupBuyInvariantCheck`·`GroupBuyEligibility`·`GroupBuyPricing`·`GroupBuySavepoint`·`GroupBuySalesSwitch`) · `handler.GroupBuyPaymentHandler`. admin `batch.service.GroupBuyStateScheduler`(워커 둘, `roboyam.groupbuy.state-poll-millis`·`refund-poll-millis` 기본 1분, `roboyam.batch.enabled` 서버만). 결제 모듈 소폭: `PaymentOrderService.expireOpen`(READY 닫기 — 결제 행 먼저)·`openCheckout`(유효한 READY 다시 돌려주기), `PaymentOrderStore.findOpenReady`. 감사 행위 10개(`GROUPBUY_*`·`COMMERCE_SHIPPING_POLICY_CREATE` — 돈 관련 5·일반 5) + 대상 `GROUP_BUY`·`SHIPPING_POLICY`, 번들 ko·en.
- **정한 세부**(계약에 없던 것 — 추천안으로 정함):
  1. **환불 기록 표 `group_buy_refunds` 를 V102 에서 V101 로 당겼다** — 2b-2 의 불발·중단 환불·본인 취소가 쓴다. `request_id` 는 FK 없이(요청 표는 V102). 2b-3 은 열만 쓴다.
  2. **「모집 시작」 = 공개(`published_at`)**: 운영자가 누르면 공개되고(시작 전이면 「예정」), 시작 시각에 상태 워커가 DRAFT → RECRUITING(그 시각의 배송비 정책 사본·처음 수량·수정 이력 1). 시작 시각이 이미 지났으면 누른 자리에서. **공개 뒤에는 설명·이미지·요약·사양만** 고칠 수 있다(가격을 미리 공개하므로 공개 시점부터 잠근다).
  3. **결제 다시 열기**: 결정 63 대로 유효한 READY 가 있으면 같은 결제를 돌려주되, 화면이 「결제창 실패 뒤」(`afterFailure`)라고 알리면 새 결제 번호를 만든다(commerce.md 8-1 — 포트원은 실패한 결제 ID 재사용을 보장하지 않는다). 둘 다 선점 끝은 그대로.
  4. **`onPaid` 거절 사유 코드**: `GROUP_BUY_ORDER_NOT_FOUND`·`_ORDER_NOT_PENDING`·`_PAYMENT_MISMATCH`·`_HOLD_EXPIRED`·`_AMOUNT_MISMATCH`·`_CLOSED`(판정·중단 뒤)·`_QTY_ANOMALY`(선점 부족 — 이벤트 `anomaly=true`)·`_CONFLICT`(DB 제약 충돌). 받아들이는 일은 JDBC 세이브포인트 안이라 지면 되돌리고 만료 + RELEASE(조건부, 이긴 쪽만).
  5. **만료 원인** `ExpireCause`: `HOLD_TIMEOUT`(이탈로 셈) · `GROUP_BUY_CANCELED` · `PAYMENT_FAILED_CLOSED`(불발 공구의 결제 중 주문) · `REJECTED_CLOSED` · `LATE_PAYMENT` · `REJECTED_ANOMALY`.
  6. **조기 마감**은 종료 사유 `EARLY_CLOSED`(closing_at = 누른 시각, 상태 이력 사유 `EARLY_CLOSE`) — 2b-1 화면 세션이 알린 사용자 결정(2026-10-28, 결정 79 고침: 종료 사유 `SOLD_OUT`·`TIME_UP`·`EARLY_CLOSED`·`CANCELED` 넷, 2b-1 커밋 f883ae80)을 따랐다. `close_reason` 은 varchar(15). 연장은 상태 이력 `RECRUITING → RECRUITING`(사유 `EXTENDED`).
  7. **발송 지연 공지 뒤 즉시 취소**(결정 52)는 공지 전에 결제한 발송 전 주문의 **주문 전체**(줄을 골라도 전체), 원인 `DELAY`, 재부과 없음, 상태 CANCELED.
  8. 불발·중단 환불의 주문 상태는 REFUNDED, 본인 취소·지연 취소는 CANCELED(결정 46). 금액 나눔: 전액 = 남은 결제액 → 상품(가진 수량) + 나머지(앞서 재부과한 배송비는 `reshipping_refund`, 그 밖은 `shipping_refund`).
  9. 배송지 암호 용도 `groupbuy.address.{recipient,phone,line1,line2,memo}`(DataCipher), 우편번호만 평문.
  10. 판매 스위치는 인터페이스 `GroupBuySalesSwitch` + 기본 구현 `DefaultGroupBuySalesSwitch`(늘 열림) — **2b-4 가 V104 설정을 읽는 구현으로 바꾼다(바꿈 — 「2b-4 구현 결과」)**(회원 주문 API 도 2b-4 라 지금 주문이 들어올 길은 없다).
  11. 회원·운영자 알림·메일은 `GroupBuyEvents`(주문 확정·거절·선점 만료·수량 풀림·차수 시작(받는 사람 중복 없이)·환불 완료·자동 취소 완료·상태 변경)를 업무 트랜잭션 안에서 발행만 한다 — 받는 곳은 2b-4. `COMMERCE_ORDER_ANOMALY` 운영자 알림은 아직 `reserved()` 그대로(이벤트 `OrderRejected.anomaly`).
  12. 관리자 목록 조회용 JPA 엔티티·QueryDSL 은 만들지 않았다(쓰는 화면 API 가 2b-4) — 이번에는 모두 JDBC 행 레코드.
- **규칙 → 테스트**(shared `com.community.groupbuy.*` 80개 + admin `GroupBuyStateSchedulerTest` 3개, 실제 PostgreSQL `CommerceTestDatabase`):

  | 규칙 | 테스트 |
  |---|---|
  | 10-4·결정 23 선점(공구 행 → 옵션 ID 오름차순), 줄 하나 모자라면 주문 전체 실패, 한도 없는 옵션도 수량 열 갱신(77) — G5 | `GroupBuyJoinTest.G5_여러_줄_중_…` · `GroupBuyStockTest.G5_여러_줄_동시_선점과_…` |
  | 결정 41 선점 끝 = 결제 유효 시각, 배송지 암호문, 주문 번호(13) | `GroupBuyJoinTest.주문하면_…` · `GroupBuySchemaTest.주문_번호는_…` |
  | 결정 59·72 동의 3종·줄 필수, 화면 금액 다르면 409 — G42 | `GroupBuyJoinTest.G42_…`(둘) |
  | 10-5 자격(권한·본인인증·19세·정지·참여 제한·판매 스위치) | `GroupBuyJoinTest.자격_권한_…` |
  | 진행 중 주문 1건(어드바이저리 잠금 + 부분 유일 66), 동시 두 번 → 하나 · 전부 취소 뒤 새 주문 — G19 | `GroupBuyJoinTest.진행_중_주문은_…_G19` · `같은_회원이_동시에_…` · `GroupBuySchemaTest.진행_중_주문은_…` |
  | 1인 한도(결정 28 — 취소·반품 뺌, 금액만 환불은 셈)·차수 합산(24·86) — G20·G8 | `GroupBuyJoinTest.한_사람_한도는_…` · `반품은_처리_방법과_…_G20` · `차수_사이_한도는_…_G8_G20` |
  | 잠시 품절 / 다 팔림 구분(17·84), 마지막 하나 동시 → 하나만 | `GroupBuyJoinTest.마지막_하나를_…` · `다_팔리면_temporary_false_…` |
  | 조회 때 정리는 본인 주문만(85) | `GroupBuyJoinTest.본인의_지난_선점은_…` |
  | 결제 확정 → JOINED / 즉시 발송 CONFIRMED(10-3) | `GroupBuyPaymentTest.성사형은_결제되면_…` |
  | 다 팔림 즉시 CLOSING(27)·옵션 전부 다 팔림(48·78)·한도 없는 옵션 — G16 | `GroupBuyPaymentTest.결정27_…` · `G16_…` · `한도_없는_옵션이_…` |
  | P3 결제 중 마감 → 받고 판정 포함 | `GroupBuyPaymentTest.P3_…` |
  | P3b 판정 뒤 결제 → REJECT·자동 취소·만료·RELEASE 한 번·이탈 안 셈, 자동 취소 `onCanceled` 는 금액 불변 — G18 일부 | `GroupBuyPaymentTest.P3b_…` |
  | P15 선점 만료 직후 결제 → 살리지 않음(결제 행 먼저 닫음 — 37) | `GroupBuyPaymentTest.P15_…` |
  | P16 결제창 실패 뒤 재결제 — 같은 주문, 선점 안 늘어남 | `GroupBuyPaymentTest.P16_…` |
  | 결정 63 다시 열기 1분 미만 거절·유효한 READY 그대로 — G26 | `GroupBuyPaymentTest.G26_…` |
  | 결정 62 `onPaid` 도메인 사정(상태 불일치·불변식 깨짐·DB 제약 충돌) 예외 없이 REJECT — G25 | `GroupBuyPaymentTest.G25_…` |
  | 선점 반복 이탈 24시간 3번 → 공구 참여 제한, HOLD_TIMEOUT 만 셈(10-5·12-3·28) | `GroupBuyPaymentTest.선점_반복_이탈_…` |
  | 웹훅 확정 ∥ 선점 만료 · ∥ 다시 열기 — 교착 없음·하나만 이김·RELEASE 한 번 — G22 | `GroupBuyPaymentTest.G22_…`(둘) |
  | 결제창 열린 동안 중단 → 만료·해제·늦은 결제 자동 취소·이탈 안 셈 — G9 | `GroupBuyPaymentTest.G9_…` |
  | 결제 확정 ∥ 중단 — 교착 없음, 확정이 이기면 환불 워커가 환불 — G17(2b-2 몫) | `GroupBuyPaymentTest.G17_…` |
  | 추가 입고·다시 판매는 다 팔림 뒤 거절(이미 CLOSING) — G2 | `GroupBuyStockTest.G2_…` |
  | 잠시 품절에서 추가 입고 → 선점 만료 → 모두 판매 — G3 | `GroupBuyStockTest.G3_…` |
  | 줄이기 하한(확정 + 선점·성사형 최소)·줄여서 다 팔리면 즉시 CLOSING — G4 | `GroupBuyStockTest.G4_…` · `성사형_최소_수량_…` |
  | 옵션 추가 입고·줄이기가 공구 최대도 움직임, 한도 없는 옵션 거절, `max = 처음 + ΣADD − ΣREDUCE`, 「추가 입고 N개」, 수정 이력·감사 — G5·G29 | `GroupBuyStockTest.G5_G29_…` · `사유가_없거나_…` |
  | P16b·P16c·P16d 연장, 결제 중 선점이 남으면 연장, 추가 입고·본인 취소 풀림도 연장(25·81) — G12·G28 | `GroupBuyStateWorkerTest.P16b_…` · `P16c_…` · `P16d_…` · `DEMAND_마감_시각에_…` · `G12_…` · `G28_…` |
  | 예정 공구는 시작 시각에 워커가 모집 시작·그때 배송비 복사 | `GroupBuyStateWorkerTest.예정_공구는_…` |
  | 판정(성사 → CONFIRMED 일괄, 불발 → 환불 워커가 전액·CANCEL·환불 기록 완료) | `GroupBuyStateWorkerTest.판정_성사면_…` |
  | P25 성사 뒤 최소 아래로 내려가도 성사 유지 | `GroupBuyStateWorkerTest.P25_…` |
  | 결정 45 집계 중 본인 즉시 취소 → 불발 가능 — G20 일부 | `GroupBuyStateWorkerTest.결정45_G20_…` |
  | 판정 ∥ 마지막 확정 → 성사(판정 ① 공구 행 잠금 — 37) — G23 | `GroupBuyStateWorkerTest.G23_…` |
  | 두 옵션 마지막 하나 동시 확정 → 워커가 CLOSING(78) — G27 | `GroupBuyStateWorkerTest.G27_…` |
  | COMPLETED sweep(결정 9) | `GroupBuyStateWorkerTest.즉시_발송_성사_공구는_…` |
  | 불발 300건 — 상태 워커는 환불하지 않음, 환불 워커 바퀴당 50·이어 받기·중복 없음(61) — G24 | `GroupBuyStateWorkerTest.G24_…` |
  | 발송 전 2/3 취소 47,000 · 재부과 3,000 · 나머지 취소는 남은 결제액 전부(42·46·69) — G13·G19 | `ParticipationCancelTest.G13_…` |
  | 배송비 낸 주문은 재부과 없음 · 한 줄 취소 · 남의 주문 404 · 가진 수량 초과 400 | `ParticipationCancelTest` 나머지 |
  | 성사형은 모집 중 즉시 취소·성사 뒤 요청으로 · 즉시 발송은 준비중 전까지(10-3 표) | `ParticipationCancelTest.성사형_JOINED_…` · `즉시_발송은_…` |
  | 지연 공지 뒤 준비중 주문 즉시 전액·재부과 없음(52) — G37 일부 | `ParticipationCancelTest.G37_…` |
  | 발송 예정일 필수·즉시 발송은 선착순만·정상가 근거(51·58·D1) — G37 일부 | `GroupBuyAdminTest.G37_…` · `즉시_발송의_발송_영업일은_…` |
  | 모집 시작 = 배송비 사본·처음 수량·이력 1·감사, 기간 상한·정책 없음 거절 | `GroupBuyAdminTest.모집_시작은_…` · `기간이_설정_상한을_…` |
  | 모집 뒤 설명·이미지만(LOCKED_FIELD)·수정 이력·낙관 잠금, DRAFT 는 모두 | `GroupBuyAdminTest.모집_뒤에는_…` · `공개_전_DRAFT_는_…` |
  | 추가 차수: 끝난 공구만·복사 범위·동시 두 번 하나·DRAFT 차수 있으면 거절·중단 공구 거절·시작 때 이전 신청자 한 번 — G8 | `GroupBuyAdminTest.G8_…` · `중단된_공구에서는_…` |
  | 중단 ① 공구만 → ② 환불 워커(발송 전만 환불·결제 중 닫음·발송된 주문 그대로·다시 돌려도 한 번) — 29 | `GroupBuyAdminTest.결정29_…` · `조기_마감은_…` |
  | 종료 사유 넷(`SOLD_OUT`·`TIME_UP`·`EARLY_CLOSED`·`CANCELED`) — 조기 마감은 `EARLY_CLOSED`(결정 79 고침) | `GroupBuyAdminTest.조기_마감은_…_EARLY_CLOSED` · `GroupBuySchemaTest.종료_사유는_…` |
  | P16e 배송비 예약 인상 · 지난·앞선 예약 거절 · 적용 시작 줄 불변·기간 겹침 금지(DB) | `GroupBuyAdminTest.P16e_…` · `지난_시각이나_…` · `적용이_시작된_…_DB` |
  | V101 제약(부분 유일·NULLS NOT DISTINCT·복합 FK·금액·수량·방식 CHECK) | `GroupBuySchemaTest` |
  | 불변식 한 SQL — 판매 수량 식(다시 판매·폐기 반품)·이동 줄 마지막 값·결제 금액(39)·옵션 합(77)·최대 식(80) — G6(2b-2 몫) | `GroupBuySchemaTest.G6_…` + 각 시험 끝의 `assertInvariants` |
  | 워커 「언제 돌릴지」 — 배치를 켠 서버만, 둘 따로, 워커 기록·가린 오류 | admin `GroupBuyStateSchedulerTest` |
- **실행**(이 PC 셸에 `JAVA_HOME` 이 없어 지정): `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2 ./gradlew :server:shared:test --tests 'com.community.groupbuy.*'` → **80개 통과**(조기 마감 종료 사유를 고친 뒤 다시 돌려 통과, G24 약 70초) · `./gradlew :server:admin:test --tests 'com.community.batch.service.GroupBuyStateSchedulerTest'` → **3개 통과**. 바꾼 곳 회귀 확인: shared `AuditRetentionTest`(6)·`PaymentOrderServiceTest`(7)·`PaymentCompletionTest`(7), admin `Commerce2aCatalogTest`(6)·`Commerce04AdminCatalogTest`(5)·`AuditRetentionAdminTest`(6 — admin 앱 문맥이 공구 빈과 함께 뜸), api `IdentityHttpTest`(6 — api 앱 문맥) 모두 통과. 실패 0. 전체 테스트는 돌리지 않았다.
- **아직 테스트로 막지 못한 규칙과 이유**
  - P26(같은 멱등 키·다른 본문 409)·속도 제한 `GROUPBUY_JOIN` — 장치는 0-5 시험이 막고 있고, 공구 주문 API 에 `MoneyRequestGuard` 를 붙이는 것은 컨트롤러와 함께 2b-4.
  - 권한 코드(`commerce.groupbuy.cancel`·`commerce.stock.adjust`·`commerce.product.manage`)와 설명 HTML 정화 — 관리자 컨트롤러(2b-4). 권한 `featureReady` 도 그때.
  - 수량 풀림 알림(공구·옵션 둘 다 남을 때만·10분에 한 번)·차수 시작 알림 발송·환불 완료 메일·`COMMERCE_ORDER_ANOMALY` 운영자 알림 — 이벤트만 시험했다(발송은 2b-4).
  - G6·G17·G20 중 반품·요청 승인 몫, G10·G21 — 2b-3.
  - 예정일·발송 기한 지난 주문 경고(51·P35) — 2b-4 점검 워커.

### 2b-2 추가 — 상품정보제공고시 서버 (2026-10-28, 결정 87 — develop 계약의 「### 상품정보제공고시」 절이 정본, 이 브랜치에서는 참조만)
- **카탈로그 데이터** `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json` — 공정거래위원회고시 제2022-15호(2023-01-01 시행) Ⅲ-1 품목별 정보 **40개 품목 · 379개 항목**. 국가법령정보센터 본문 iframe 의 항목 표 이미지 40장을 Claude in Chrome 으로 확대해 2026-10-28 에 옮겼다(파일 머리에 고시 번호·시행일·옮긴 날짜·출처 URL·일반원칙 2~7 원문). **읽기 어려운 글자는 없어 `unverified` 항목은 없다.** 항목마다 `no`(원문 번호, 하위는 `7-1` 등 + `group` 에 묶음 줄)·`key`(영문 — 같은 뜻은 품목이 달라도 같은 키)·`name`·`description`(이름 + 괄호 설명 = 원문 한 줄)·`kind`(`TEXT`·`DATE_LIKE` 원칙 ④·`KC_CERT` 원칙 ⑥·`QUALITY_WARRANTY` 원칙 ⑤)·`required`(모두 true — 원칙 ③)·`conditional`(원문에 「…에 한함」 등 — 해당 없으면 「제공 불가 + 사유」). 품목 `scope`(37·38 의 적용 범위 문구)·`warrantyDisadvantageDisclosure`(원칙 ⑤ 후단 — 10 노트북·11~14). 가운데점은 `·` 로 통일.
- **V101 에 더함**: `group_buy_notices`(공구·차례·품목 코드·제목·카탈로그 버전) · `group_buy_notice_skus`(적용 단품 — **단품 유일**: 한 단품은 양식 하나에만, 복합 FK 로 같은 공구 단품만. 결정 88 로 `group_buy_notice_options` 를 바꿨다 — 아래 「구조 변경」) · `group_buy_notice_values`(항목 키·값 / 제공 불가 + 사유 — CHECK: 제공 불가면 값 없이 사유 필수) · `group_buy_revisions.notices`(jsonb 사본) + 이력 종류 `NOTICE`.
- **코드**: `GroupBuyNoticeCatalog`(`classpath*:groupbuy/notice-catalog-*.json` 을 모두 읽고 가장 늦은 시행일이 지금 버전) · `GroupBuyNoticeService`(저장·완성 검사·복사·옵션 다시 잇기) · `GroupBuyNoticeStore` · `NoticeDraft` · 오류 409 `GROUP_BUY_NOTICE_INCOMPLETE`(추가 값 `noNotice`·`notices[{index,title,categoryCode,missingKeys}]`·`unassignedOptions`) · 번들 ko·en.
- **정한 세부**
  1. 양식의 적용 옵션은 **옵션 이름**으로 받는다(DRAFT 수정은 옵션을 지우고 다시 넣어 ID 가 바뀐다 — 다시 넣은 뒤 이름으로 다시 잇는다).
  2. 적용 옵션이 빈 양식 = 공구 전체(기본 상품) — 다른 양식에 없는 옵션을 덮는다(부수 상품 옵션은 여기, 원칙 ⑦). 옵션이 있는 공구에서 기본 양식은 하나까지. 기본 양식이 없으면 모든 옵션이 어느 양식에 이름으로 들어 있어야 한다.
  3. 저장은 통째로. 같은 자리·같은 품목의 양식은 카탈로그 버전을 유지하고, 새 양식·품목을 바꾼 양식은 지금 버전. 추가 차수는 버전 그대로 복사.
  4. 완성 검사는 모집 시작(공개) 때 + **공개 뒤 저장 때도**(공개된 고시를 비울 수 없다). 공개 전 DRAFT 는 빈 채 저장할 수 있다. 「값」은 공백만이면 빈 것으로 본다.
  5. 공개 뒤 저장은 모집 중 이후면 수정 이력 번호를 올리고 사본(`NOTICE`), 모집 시작 이력에도 고시 사본이 들어간다.
  6. 「상세페이지 참조」만 적은 값은 저장은 하고 경고 목록을 돌려준다(`DetailPageWarning`).
  7. 판매 화면용 조회(회원 상세 표)·관리자 API 는 2b-4.
- **테스트 G43**(`GroupBuyNoticeTest` 9개): 카탈로그 40개 품목·키 겹침 없음·원칙 ④⑤⑥ 표시 / 양식 없음·빈 항목·공백 값이면 모집 시작 거절(빈 양식·항목 목록), 「제공 불가 + 사유」는 채운 것 / 제공 불가 사유 없으면 저장 거절 + DB CHECK / 어느 양식에도 없는 옵션이면 거절, 기본 양식이 나머지를 덮음 / 한 옵션은 양식 하나에만(저장 검사 + DB 유일)·기본 양식 하나·없는 옵션·품목·항목 거절 / 모집 뒤 수정은 이력 사본, 비우는 수정은 거절 / 추가 차수 복사·카탈로그 버전 고정(시험 전용 옛 버전 `src/test/resources/groupbuy-test/notice-catalog-2022-01-01.json` — 실제 고시가 아니다)·옛 버전 항목으로 검사 / DRAFT 옵션 다시 넣기 뒤 이름으로 다시 잇기 / 「상세페이지 참조」 경고.
- **실행**: `./gradlew :server:shared:test --tests 'com.community.groupbuy.*'` → **89개 통과**(기존 80 + G43 9 — 시험 키트의 `open()` 이 모집 시작 전에 기본 양식을 채우게 바꿈) · admin `AuditRetentionAdminTest`(6)·`GroupBuyStateSchedulerTest`(3), api `IdentityHttpTest`(6) 통과(두 앱 문맥이 카탈로그를 jar 안에서 읽어 뜬다). 실패 0.
- **확인 필요**: 과태료 근거 조문·금액(전자상거래법 제13조 ② 위반 — 계약 결정 87 의 [원문 확인])은 이번에 확인하지 않았다.
- **판매자 추가 항목**(사용자 지시 2026-10-28 — develop 계약 결정 87 「판매자 추가 항목」 줄):
  - V101 `group_buy_notice_values` 를 고쳤다 — `id` PK, `custom`(추가 항목이면 true), `item_key`(고시 항목만), `custom_name`(추가 항목만, 1~40자), `sort_order`. CHECK `ck_gbnv_kind`: 고시 항목은 키 필수·이름 없음, 추가 항목은 키 없음·이름·내용(1~1,000자) 필수·「제공 불가」 없음. 부분 유일: 고시 항목 (양식, 키) · 추가 항목 (양식, 이름) · (양식, 차례).
  - 저장(`NoticeDraft.values[]` 에 `custom=true, name, value, sortOrder` 줄 — `NoticeDraft.Value.custom(...)`): 이름·내용이 모두 빈 줄은 저장하지 않는다, 반쪽 줄·길이 초과는 400, **양식당 20개**, 같은 양식 안 이름 겹침 거절, **고시 항목과 같은 이름은 거절**(공백을 빼고 비교, 「크기, 무게」처럼 쉼표로 나뉜 각 이름도 — 정한 세부), 차례는 `sortOrder`(비면 넣은 차례)로 0부터 다시 매긴다. 평문으로 저장하고 화면이 글자 그대로 보인다.
  - 완성 검사 대상이 아니다(선택). 수정 이력 사본(`notices[].customItems`)·추가 차수 복사 대상.
  - 테스트(G43 보강 3개): 차례대로 저장·빈 줄 제외·완성 검사 밖·이력 사본 / 고시 항목과 같은 이름·21개·이름 겹침·반쪽 줄·41자 거절 + DB CHECK / 차수 복사. `--tests 'com.community.groupbuy.*'` → **92개 통과**, 실패 0.
- **local 시드의 예시 품목 넓히기**(결정 87 「예시(목업·local 시드) 품목을 넓힌다」 — 쌀·과일·생선(20)·김치·소스(21)·티셔츠(1)·운동화(2) + 로봇 키트, 각 공구의 고시 양식을 그 품목으로): **2b-2 범위가 아니라 하지 않았다** — local 시드(결정 11)는 판매 스위치·사업자 정보(V104)와 함께 2b-4 가 만든다. 그때 이 품목들로 공구와 고시 양식을 채운다(신선식품은 `DATE_LIKE` 항목에 일반원칙 ④ 대체 표기 예시).

### 2b-2 구조 변경 — 옵션 축·단품(SKU)·추가 구성상품 (2026-10-28, 결정 88 — develop 계약 「### 옵션 축·단품·추가 구성상품」이 정본)
위 절들에서 「옵션」이라 쓴 수량·가격 단위는 이제 **단품**이다(주문 줄·수량 이동·추가 입고/줄이기·품절·관심 신청·고시 적용 대상).
- **V101 고침**(아직 병합 전이라 같은 파일을 고쳤다): `group_buy_options` → `group_buy_option_axes`(공구·차례·이름, 유일 (공구, 차례)·(공구, 이름)) · `group_buy_option_values`(축·차례·값, 유일 (축, 차례)·(축, 값)) · `group_buy_skus`(공구·`kind` MAIN/ADDON·`value_ids bigint[]`(축 차례)·이름·`extra_price`(MAIN)·`addon_price`(ADDON ≥ 1)·`max_qty`/`initial_max_qty`/`current_qty`/`reserved_qty`·`on_sale`·`image_key`·차례 — CHECK 종류별 가격·수량, 유일 (공구, 이름)·MAIN 의 (공구, 조합)). 주문 줄 `sku_id NOT NULL`·유일 (주문, 단품)·복합 FK(같은 공구 단품만). 수량 이동 `sku_id` + `sku_*_after`, 공구 쪽 `*_after` 는 **추가상품만 움직인 줄이면 NULL**(CHECK 로 둘 중 하나는 있다). 관심 신청 `sku_id`(NULLS NOT DISTINCT 유일). 고시 `group_buy_notice_skus`. 수정 이력 `option_axes`·`skus` jsonb(예전 `option_max` 대신).
- **API 모양**(2b-4 컨트롤러가 그대로 싣는다):
  - 상세 `Availability`: `remaining`·`soldOut`·`temporarilyUnavailable`·`addedQty` + `axes[{axisId, name, values[{valueId, value}]}]` + `skus[{skuId, kind(MAIN|ADDON), name, valueIds[](축 차례), unitPrice, remaining(null = 제한 없음 — ADDON 한도 없음), soldOut, temporarilyUnavailable, onSale}]`. MAIN 남은 = min(단품 남은, 공구 남은), ADDON 은 자기 한도만.
  - 견적·주문 요청: `items[{skuId, qty}]`(예전 `lines[{optionId, qty}]`) — 줄마다 그 공구 단품, 같은 단품 두 줄·수량 0 은 400, **MAIN 줄이 하나 이상**(아니면 400 `items` — `groupbuy.order.main.required`), 판매 안 함은 409 `GROUP_BUY_SKU_NOT_ON_SALE`, 품절은 409 `GROUP_BUY_SKU_SOLD_OUT`(`skuId`·`temporary`). 견적 `items[{skuId, kind, name, qty, unitPrice, itemAmount}]`.
  - 관리자 등록 값 `GroupBuyDraft`: `axes[{name, values[]}]`(0~3개, 값 1~30개, 조합 ≤ 200) · `skus[{values[](축 차례 값 이름), extraPrice, maxQty, onSale}]`(적지 않은 조합은 추가금 0·한도 없음·판매, 축이 없으면 `values` 가 빈 줄 하나로 MAIN 한도) · `addons[{name, price, maxQty, imageKey}]`(≤ 20, 이름은 조합 이름과도 겹치면 안 된다). 오류 칸 `axes`·`skus`·`addons`. 판매하는 조합이 하나는 있어야 한다.
  - 추가 입고·줄이기 `skuId`(null = 공구 최대만). 결과 `StockChange.skuMaxQty`. 한도 없는 단품은 409 `GROUP_BUY_SKU_UNLIMITED`.
  - 고시 양식 `NoticeDraft.skuNames[]`(예전 `optionNames`), 응답 `skuIds`·`skuNames`, 미완성 오류 `unassignedSkus`(예전 `unassignedOptions`).
  - 본인 취소 `lines[{skuId, qty}]`, 본 상품을 모두 취소하면서 추가상품을 남기면 409 `GROUP_BUY_ADDON_ONLY`(`skuIds` — 함께 취소하라고 묻는 화면).
- **정한 세부**
  1. MAIN 단품 이름 = 축 값을 「 / 」로 이은 것(축이 없으면 상품 이름). 단품 이름은 공구 안에서 유일 — 고시 양식·DRAFT 수정 뒤 다시 잇기·추가 차수 복사가 이름으로 가리킨다.
  2. **공구 행의 `current/reserved/max` = MAIN 단품만**(최대·성사 판정·다 팔림·1인 한도 — 결정 88). 추가상품은 자기 행만 움직이고, 추가상품 입고·줄이기는 공구 최대·`added_qty` 를 바꾸지 않는다. 다 팔림 = 공구 확정 = 최대 또는 판매 중인 MAIN 단품이 모두 한도까지 팔림(추가상품 품절은 공구를 닫지 않는다).
  3. 잠금 차례: 어드바이저리 → 공구 행(MAIN 수량 합, 한 문장) → 단품 행 ID 오름차순. 추가상품만 있는 수량 변경은 없으므로(MAIN 필수) 공구 행이 늘 먼저다.
  4. 무료배송 기준 금액 = 추가상품을 포함한 상품 금액 합.
  5. 본인 취소에서 MAIN 이 0 이 되는데 추가상품이 남으면 거절(409 `GROUP_BUY_ADDON_ONLY`) — 화면이 「추가 구성상품도 함께 취소할까요?」로 묻고 함께 보내게(결정 88 「함께 취소 대상으로 묻는다」). 불발·중단·지연 취소는 늘 주문 전체라 해당 없음. 반품(2b-3)도 같은 규칙을 쓴다.
  6. 고시: 기본 양식(적용 단품 없음)은 **MAIN 만** 덮는다. 추가상품은 **자기 양식이 따로 있어야** 완성이고(`unassignedSkus`), 추가상품을 가리키는 양식은 그 단품 하나만(MAIN·다른 추가상품과 섞으면 400 `skuNames`).
  7. 모집 뒤 잠금 `options`: 축 이름·값·차례, 조합의 추가금·한도(처음 한도나 지금 한도 중 하나와 같으면 같다고 본다 — 추가 입고로 바뀐 한도를 화면이 그대로 보내도 통과)·판매 여부, 추가상품 이름·가격·한도·종류가 같아야 한다.
  8. 불변식 검사: 공구 = Σ MAIN 줄 판매 수량, 단품마다 = 그 단품 줄, Σ MAIN 단품 = 공구, 공구 마지막 이동은 공구 값을 적은 줄, 최대 식은 공구 값을 적은 ADD·REDUCE 만.
  9. 관리자 「조합 표」의 이미지(MAIN 단품 이미지)는 이번에 받지 않았다 — 열 `image_key` 만 두고 추가상품만 쓴다.
- **테스트 G44**(`GroupBuySkuTest` 10개): 축 2개 → 6조합·이름·추가금·한도·판매 끔과 상세의 축·값·`valueIds` / 판매 끈 조합 견적·주문 409 / 단품 한도: 결제 중이면 잠시 구매 불가·다 팔리면 그 단품만 품절, 공구는 모집 중 / 추가상품만 담으면 견적·주문 400 / 공구 최대·1인 한도는 MAIN 만·무료배송 기준엔 추가상품 포함 / 성사 판정은 MAIN 만(추가상품 10개 있어도 불발) / 본 상품 모두 취소하며 추가상품 남기면 409 `ADDON_ONLY`·함께 보내면 주문 전체 취소 / 고시: 기본 양식은 추가상품을 덮지 않음·추가상품 양식에 둘 이상·MAIN 과 섞기 400 / 모집 뒤 추가상품 가격·조합 판매·축 값 바꾸면 `LOCKED_FIELD options`, 추가상품 입고·줄이기는 공구 최대 그대로 / 등록 검사(축 4개·조합 225·값 중복·없는 조합·판매 조합 없음·추가상품 이름 겹침·가격 0, 축 없으면 MAIN 하나). 기존 시험 92개는 단품 구조로 고쳤다(시험 키트: `option(name, extra, max)` = 축 하나 「옵션」의 값, `axis`·`combo`·`addon`·`mainSku`, `fillNotice` 는 추가상품마다 양식을 더 채운다).
- **실행**: `./gradlew :server:shared:test --tests 'com.community.groupbuy.*'` → **102개 통과**(기존 92 + G44 10) · admin `GroupBuyStateSchedulerTest`(3)·`AuditRetentionAdminTest`(6)·api `IdentityHttpTest`(6) 통과(문맥 로드 확인).
- **아직**: 관리자 등록·상세 API·화면 연결(2b-4), 반품의 추가상품 규칙(2b-3), local 시드 예시 품목(2b-4 — 위 절).
- **세션 정리 결정 — 2b-1 목업 질문(2026-10-28)**: ① 상세 단품 알림 상태 `skuWatches{watchable, watching}`·신청 `{kind, skuId}` 는 목업 모양 그대로(표 `group_buy_watches.sku_id` 가 받친다, API 는 2b-4). ② 주문 줄 칸 `skuId`·`skuName`·`kind` 그대로. ③ 취소·반품 요청·미리 보기 줄은 **`skuId`**(주문 안에서 단품 줄은 하나 — 유일 (주문, 단품). 본인 취소가 이미 `skuId`). ④ 반품도 같은 규칙 — 본 상품을 모두 반품하며 추가상품을 남기면 409 `GROUP_BUY_ADDON_ONLY`(2b-3 이 같은 검사를 쓴다). ⑤ 추가상품 판매 여부 스위치 없음(늘 판매, 모집 뒤 구성 잠금), `addons.maxQty` null = 한도 없음 허용(화면도 비우면 무제한). ⑥ 축 없는 공구의 MAIN 이름 = 상품 이름, 모집 뒤 판매 끈 조합 다시 켜기 불가(`LOCKED_FIELD options`). ⑦ 글자 상한 정본 = 서버: 축 이름 30·값 30·추가상품 이름 100(MAIN 이름은 값 셋 + 「 / 」 ≤ 96).

### 2b-2 구조 변경 — 상품 마스터·카테고리·공급가 (2026-10-28, 결정 89 — develop 계약 「### 상품 마스터 · 카테고리 · 공급가」가 정본)
위 절들의 「공구의 설명·이미지·옵션 축·고시」는 이제 **상품** 것이다(공구 = 상품 하나 + 판매 조건).
- **V101 고침**: `group_buy_categories`(2단 — 2단 제한은 앱, 이름 유일 (부모, 이름), 차례·숨김) · `group_buy_products`(카테고리 필수·이름·요약·사양·설명·정상가·근거·청약철회 제한·version) · `group_buy_product_images` · 옵션 축/값은 `product_id` · `group_buy_product_skus`(조합·이름·`default_supply_price`·`on_sale`). `group_buys` 는 `product_id` + 판매 조건만(이름·설명·정상가·청약철회 제한 열 삭제, 조회는 상품을 이어 읽어 `GroupBuyRow` 모양 그대로). 공구 단품 `group_buy_skus` 에 `product_sku_id`(유일 (공구, 상품 단품))·`supply_price`(≥ 0, null = 아직), `image_key` 삭제. 고시 `group_buy_notices.product_id`·`group_buy_notice_skus(notice_id, product_id, product_sku_id)`. 수정 이력에 `product_name`, 고시 사본은 공구 상품 + 추가 구성상품의 상품.
- **코드**: `GroupBuyProductService`(카테고리 등록·수정·회원 목록 필터 `publicList`, 상품 등록·수정) · `GroupBuyProductStore` · `ProductDraft`. `GroupBuyNoticeService` 는 상품 단위(`save(productId, …)`·`assertComplete(productId)`). 감사 `GROUPBUY_PRODUCT_SAVE`·`GROUPBUY_CATEGORY_SAVE`(일반)·`GROUPBUY_SUPPLY_PRICE`(돈 관련). 오류 `GROUP_BUY_SUPPLY_PRICE_MISSING`·`GROUP_BUY_NEGATIVE_MARGIN`(둘 다 `skuIds`)·`GROUP_BUY_PRODUCT_NOT_FOUND`·`GROUP_BUY_PRODUCT_SKU_IN_USE`(`skuNames`), 고시 미완성 오류에 `productId`.
- **API 모양**(2b-4 가 싣는다):
  - 상품 등록 값 `ProductDraft{categoryId, name, summary, spec, description, normalPrice, normalPriceBasis, normalPriceSource, withdrawalRestriction, optionTypeCodes[], skus[{skuId|null, name, values[](optionTypeCodes 차례), defaultSupplyPrice, onSale}], images[]}` — **단품 목록이 정본**·**옵션은 옵션 종류 코드로**(아래 「세션 정리 결정」). 관리자 상세(2b-4)는 `optionTypeCodes` + `optionNames`(서버가 붙인 지금 이름) + `skus[{skuId, name, values, valueIds, defaultSupplyPrice, onSale}]` + 서버가 만든 `axes`(읽기 전용). 옵션 종류 `GET·PUT /api/admin/group-buys/option-types` → `[{code, name, sortOrder, hidden, productCount, usedValues[]}]`(2b-4).
  - 공구 등록 값 `GroupBuyDraft{productId, title, groupPrice, shippingPolicyCode, minQty, maxQty, maxQtyPerUser, startAt, endAt, extensionMode, fulfillment, shipPlannedAt, shipWithinBusinessDays, returnPolicy, roundLimitShared, skus[{productSkuId, extraPrice, maxQty, onSale, supplyPrice}], addons[{productSkuId, price, maxQty, supplyPrice}], marginConfirmed}`(결정 88 의 `axes`·`skus[{values…}]`·`addons[{name…}]` 를 대신). 모집 시작 `marginConfirmed`(역마진 확인).
  - 회원 응답(상세·견적)은 바뀌지 않았다 — 공급가는 싣지 않는다.
- **정한 세부**
  1. 공구 단품 설정을 적지 않은 상품 단품은 추가금 0·한도 없음·판매 여부와 공급가는 상품 단품 기본값. 추가 구성상품 = **다른 상품**의 단품(같은 상품 단품은 400 `addons`), 이름 = 축 없는 상품이면 상품 이름, 아니면 「상품 이름 (조합)」.
  2. 상품 수정: 설명·이미지·카테고리·기본 공급가·기본 판매 여부는 언제든 — 진행 중 공구(모집 중~발송 중)마다 수정 이력 사본(`CONTENT`), 고시 저장은 `NOTICE` 사본. 이름·정상가·청약철회 제한·옵션 구성은 그 상품을 쓰는(공구 상품 또는 추가 구성상품) 공구가 「모집 중」(공개된 예정·모집 중·집계 중)이면 409 `LOCKED_FIELD`. 모집 시작은 관련 상품 행을 잠가 구성 바꾸기와 하나만 이긴다.
  3. 구성 바꾸기는 같은 이름의 축 값·같은 조합의 상품 단품 ID 를 유지(공구 단품·고시 연결이 그대로). 축 이름이 바뀌면 모두 다시 만든다. 공개된 공구가 가리키는 상품 단품을 없애면 409 `PRODUCT_SKU_IN_USE`. **공개 전 DRAFT 공구는 상품 구성을 따라간다**(없앤 단품은 빠지고 더한 단품은 기본값으로).
  4. 공급가: DRAFT 는 비워도 저장, 모집 시작 때 모든 공구 단품(MAIN·ADDON, 판매 끈 조합 포함)에 있어야 한다(0 명시 허용). 판매가 < 공급가면 `marginConfirmed` 없이 409 `NEGATIVE_MARGIN`(모집 시작 감사에 `negativeMarginSkus`). 모집 뒤 공구 수정은 공급가만 — 비우기 거절, 판매가보다 크게 올리면 확인, 단품마다 `GROUPBUY_SUPPLY_PRICE` 감사. 수정 이력 사본에는 공급가를 넣지 않는다.
  5. 추가 차수 = 같은 상품으로 새 DRAFT(판매 조건·공급가 복사, 고시 복사 없음 — 같은 상품의 양식을 그대로 쓴다). 상품에 새로 더한 단품은 기본값으로 들어간다.
  6. 회원 목록 `publicList(categoryId)`: 공개된 공구(중단 제외), 최상위를 고르면 그 아래 단도, 숨긴 카테고리(또는 숨긴 부모 아래)는 뺀다. 목록 응답 모양은 2b-4.
- **세션 정리 결정 — 상품 단품 목록이 정본(2b-1 사용자 결정 2026-10-28, 2b-1 계약 「추가분 — 상품 단품 목록이 정본」)**: 「축·값 → 조합 자동 생성」 대신 운영자가 단품을 한 줄씩(1~200개, 이름 유일 — 단품 하나 = 옵션 없는 상품, 옵션이 없어도 이름으로 가리는 단품 여럿 가능) 등록하고, 옵션 이름(0~3개)을 두면 단품마다 값 모두 필수·같은 값 묶음 두 줄 금지(400 `skus`/`optionNames`). 서버가 단품의 값으로 축·값(처음 나온 차례)을 만들어 `valueIds` 를 채운다 — 회원 상세 `availability`·공구 등록 값·추가 구성상품은 그대로. 고칠 때 기존 단품은 `skuId` 로 가리키고(이름·값을 바꿔도 같은 ID — 공구 단품·고시 연결 유지, 두 단품 이름 맞바꾸기 가능), 목록에 없는 기존 단품은 지운다(공개된 공구가 가리키면 409 `PRODUCT_SKU_IN_USE`). 단품의 값이 바뀌면 공구 MAIN 단품의 조합 사본도 고친다(이름 사본은 공개 전 DRAFT 공구만). 추가 구성상품 이름은 단품이 하나뿐인 상품이면 상품 이름, 아니면 「상품 이름 (단품 이름)」. DB: `ux_gbps_combination` 은 값이 있는 단품만.
- **세션 정리 결정 — 옵션 종류 코드(2b-1 사용자 결정 2026-10-28)**: 표 `group_buy_option_types`(코드 PK — 영문 대문자로 시작 2~20자, 만든 뒤 불변 · 이름 1~30 유일 · 차례 · 숨김). 상품 축은 `option_type_code`(FK — 이름 열 없음, 이름은 종류 것이라 바꾸면 쓰는 상품·회원 상세·수정 이력 사본이 따라간다). 상품 저장은 `optionTypeCodes`(0~3, 없는·숨긴·겹친 코드 400 `optionTypeCodes` — 이 상품이 이미 쓰는 숨긴 종류는 그대로 둘 수 있다). 옵션 종류 저장은 목록 통째(`saveOptionTypes` — PUT, 없던 코드 만들기·이름/차례/숨김 고치기·빠진 코드 지우기, 쓰는 상품이 있는 종류를 빼면 409 `GROUP_BUY_OPTION_TYPE_IN_USE {codes}`)와 한 줄(`saveOptionType`). 감사 `GROUPBUY_OPTION_TYPE_SAVE`(일반). 목록 조회는 쓰는 상품 수·그 종류로 쓴 값(가나다 — 자동완성용). 상품·단품 ID 는 서버 채번(`product_id`·`id` 두 열), 운영자 입력 코드는 두지 않는다.
- **테스트 G45**(`GroupBuyProductTest` 8개 — 단품 목록 정본·옵션 종류 시험 2개 더함): 상품 하나로 공구 둘(단품 수량·한도·가격은 공구마다) / 모집 시작 사본(상품 이름·설명·이미지·축·단품·고시, 공급가 없음) / 모집 중 상품 이름·구성 잠금(추가 구성상품으로 쓰여도), 설명 정정은 바로 보임, 중단 뒤 값 더하기 가능·가리키는 단품 지우기 409 / 공급가 빈 값 409·0 허용·역마진 확인 / 공급가는 회원 응답 레코드에 없음 / 카테고리 2단·필터·숨김·카테고리 필수. 기존 시험은 상품 구조로 고쳤다(시험 키트 `Spec` 이 상품을 만들고 `option`·`axis`·`addon` 은 상품 칸, 고시는 `kit.productOf(gb)`, G43 추가 차수 시험은 「같은 상품의 양식을 그대로 쓴다」로, DRAFT 다시 잇기 시험은 「상품 단품 ID 유지」로 바꿈, G44 고시 시험은 「추가 구성상품의 상품에도 양식」으로).
- **실행**: `./gradlew :server:shared:test --tests 'com.community.groupbuy.*'` → **110개 통과**(102 + G45 8) · admin `GroupBuyStateSchedulerTest`(3)·`AuditRetentionAdminTest`(6)·api `IdentityHttpTest`(6) 통과.
- **아직**: 상품·카테고리·공급가 관리자 API 와 권한(`commerce.product.manage`·`commerce.product.read`·`commerce.product.price`/`commerce.settlement.read` 로 공급가 보기)·회원 목록 응답(2b-4), 상품 목록 화면 조회(JPA·QueryDSL — 2b-4).

## 2b-3 구현 결과 (2026-10-28 — 브랜치 `claude/festive-cori-faed64`, develop 병합 전)
- **마이그레이션** shared `V102__group_buy_fulfillment.sql`(develop shared 마지막 V101 다음 — 합칠 때 다시 확인): `commerce_carriers`(6개 시드 — 조회 주소 `https://` + `{no}` 한 번 CHECK) · `group_buy_shipments`(ORIGINAL 주문당 하나 · EXCHANGE 요청당 하나, 송장 CHECK, 에스크로 시각·`escrow_applicable`) · `group_buy_escrow_jobs`(LOGISTICS·COMPLETE, 끝나지 않은 일 (발송, 종류) 부분 유일) · `group_buy_requests`(+ 열린 요청 부분 유일 — AWAITING_FEE 포함, 반품비 부족분 결제 칸, 거절 사유 CHECK) · `group_buy_request_items`(사유·`refund_amount`·`prior_refund_offset`) · `group_buy_return_dispositions` · `group_buy_request_photos`(MEMBER·OPERATOR) · `commerce_address_views`(REVEAL·EXPORT, 사유 5~200 CHECK) · `commerce_bank_transfers`(계좌 암호문, SENT 면 지운 시각 CHECK) · `group_buy_supply_failures` · `group_buy_supply_choices` · `group_buy_admin_refunds`(고액 승인 대기, 주문당 하나) · `group_buy_refunds` 에 `fee_covered`·`fee_refund`·`operator_amount`(합 CHECK 고침) · 주문 줄 `exchanged_qty` · 결제 `payment_orders.order_type` 에 `GROUP_BUY_RETURN_FEE`, `payment_cancels.manual_method`(CONSOLE_CANCEL·BANK_TRANSFER). 발송 지연 공지 표는 V101 에 이미 있다.
- **결제 모듈 소폭**: `PaymentOrderType.GROUP_BUY_RETURN_FEE` · `PaymentOrderHandler.onCancelResolvedManually`(기본 빈 메서드, 결정 16) · `PaymentCancelManualService`(수동 처리 한 길 — 콘솔 취소면 취소 ID + 취소 누계까지, 계좌 송금이면 누계 그대로, 처리기를 같은 트랜잭션에서 부름 — 결정 74) · `PaymentAdminActionService.manualCancel(…, method, pgCancelId, …)` 겹쳐 받기(기존 서명은 「표시만」 그대로 — 관리자 화면에 방법 고르기는 2b-4) · `PaymentCancelStore.succeededAmount` 에 콘솔 수동 포함 · `PaymentGateway.registerEscrowLogistics`·`completeEscrow`(포트원: 결제 조회 본문의 `escrow` 로 에스크로 여부 판단 → `POST·PATCH /payments/{id}/escrow/logistics`·`POST …/escrow/complete`, 4xx 중 `ALREADY…` 는 성공 — 실제 응답은 2b-5 확인, 가짜 PG 는 기록만) · `EscrowLogistics`·`EscrowResult`.
- **코드**(shared `com.community.groupbuy`): `GroupBuyRefundCalculator`(순수 계산 한 곳 — `cancel`·`returnRefund`) · `FulfillmentService`(발주·준비중·발송(≤ 200, 줄마다 트랜잭션·행 결과)·배송완료·송장 정정·택배사 저장) · `GroupBuyEscrowWorker`(커밋 밖 호출·임대·재시도 표 1분→5분→30분→2시간→6시간, 6번째 실패 GAVE_UP + 이벤트, 배송완료 7일 뒤 열린 요청 없으면 구매 확정) · `AddressRevealService`(가린 값·본인 보기/수정·운영자 열람·명단 CSV) · `GroupBuyRequestService`(회원 미리 보기·요청·철회·부족분 결제 다시 열기, 운영자 승인·회수 송장·회수 확인·완료·교환 재발송·환불 전환·거절, 미결제 요청 닫기, 사진, 처리 기한 단계) · `GroupBuyAdminRefundService`(운영 환불·승인 대기) · `GroupBuySupplyService` · `BankTransferService` · `GroupBuyBusinessDays` · `GroupBuyFulfillmentWorker`(워커 키 `groupbuy-fulfillment` — admin `GroupBuyStateScheduler.fulfillmentTick`, `roboyam.groupbuy.fulfillment-poll-millis`) · `GroupBuyAudit` · `handler.GroupBuyReturnFeeHandler`·`GroupBuyManualRefunds` · 저장소 `FulfillmentStore`·`GroupBuyRequestStore`·`BankTransferStore`. `ParticipationCancelService` 는 계산기를 쓰게 바꾸고 `cancelLines`(취소 요청 승인·운영 환불·공급 불가가 같이 쓰는 한 길)·`supersedeOpenRequests` 를 더했다. `GroupBuyRefundService.Parts` 에 반품비·PG 보상·부족분 메움, `Extra`(요청 ID·계좌 보상·운영자 금액), `refundReturnFee`. 감사 행위 10개(`GROUPBUY_REFUND`·`_REQUEST_APPROVE`·`_REQUEST_REJECT`·`_RETURN_DISPOSE`·`_SUPPLY_FAILURE`·`_BANK_TRANSFER` 돈 관련 · `_ADDRESS_REVEAL`·`_SHIPPING_EXPORT` 개인정보 · `_FULFILLMENT`·`COMMERCE_CARRIER_SAVE` 일반) + 오류 코드 24개, 번들 ko·en. 이벤트 `EscrowGaveUp`·`RequestChanged`·`SupplyChoiceAsked`·`RefundResolvedManually`(받는 곳은 2b-4).
- **정한 세부**(계약에 없던 것 — 추천안으로 정함)
  1. 요청 상태 `AWAITING_FEE → REQUESTED → APPROVED → RECEIVED → COMPLETED` / `REJECTED`·`WITHDRAWN`·`SUPERSEDED`·`EXPIRED`. 부족분 결제 전(AWAITING_FEE)도 「열린 요청」이다 — 회수 방식을 바꾸려면 철회 뒤 다시. 철회는 회수 확인 전까지.
  2. 요청 줄 사유 `CHANGE_OF_MIND`·`DEFECT`·`WRONG_ITEM`·`DAMAGED`(배송 중 훼손)·`OTHER` — 업체 부담은 앞 셋 다음 둘(하자·오배송·훼손), **기타는 회원 부담**으로 본다.
  3. 반품 기한: 단순 변심·기타 줄이 하나라도 있으면 배송완료(한국 날짜) + 7일, 모두 업체 부담 줄이면 + 3개월. 3개월 뒤는 아래 「2b-3 보강」 16.
  4. 「전체 반품」= 이 요청으로 주문의 남은 줄(추가상품 포함)이 모두 빠질 때. 업체 부담 전체 반품은 처음 배송비 + 발송 전·뒤 재부과까지 모두 돌려준다(G32 하자 28,000). 회원 부담 전체는 편도·고객발송이면 발송 뒤 재부과만 돌려주고, 왕복 + 업체회수면 처음 배송비를 한 번만 받은 것으로(이미 받은 재부과와 차액).
  5. 반품 배송비·재부과 몫은 요청 줄 중 회원 부담 줄(없으면 마지막 줄)의 `refund_amount` 에 싣는다(결정 44 「하자 줄은 전액」).
  6. 결과가 음수면 부족분 = −결과로 `GROUP_BUY_RETURN_FEE` 결제(30분, 같은 요청에 다시 열기 가능), 요청은 하루 뒤 EXPIRED(결제창도 결제 행 먼저 닫음). 완료 때 낸 부족분이 메운 몫은 `fee_covered`(주문 결제 환불액에 더해 합이 맞는다), 남는 몫은 그 결제에서 부분 취소(`RETURN_FEE_REFUND`). 다시 계산한 부족분이 낸 돈보다 크면 재부과 → 반품비 차례로 실제 받은 만큼만 남긴다.
  7. 발송 전 취소 요청 거절은 「이미 발송함」 + 택배사·송장 — 같은 트랜잭션에서 발송 처리(열린 요청이 발송을 막으므로 거절이 먼저).
  8. 반품 거절은 근거 사진 1~3장 필수, `USED_VALUE_LOSS`·`TIME_VALUE_LOSS` 는 상품 `withdrawal_restriction` 이 있고 업체 부담 요청이 아닐 때만, 반품에 `ALREADY_SHIPPED` 금지. 받아 둔 부족분은 거절·철회·닫힘·SUPERSEDED 때 모두 돌려준다.
  9. 보상액은 하자·오배송·훼손 + 고객발송 반품만, 상한 = 공구 설정 `compensation_cap`. PG 몫 = min(보상, 남은 결제액 − 이번 환불), 넘는 몫 `commerce_bank_transfers(GROUP_BUY_COMPENSATION)`. 보상은 고액 문턱 누계(`operator_amount`)에 든다 — 넘으면 403 `GROUP_BUY_REFUND_LARGE_REQUIRED`(완료를 고액 권한자가). 계산값을 고쳐 승인하는 기능은 두지 않았다(필요하면 운영 환불로).
  10. 운영 환불: 발송 전 = 줄·수량(비면 전체) 취소와 같은 계산(사업자 귀책이면 재계산 없음) · 배송완료 = 금액만(1 ~ 남은 결제액, 줄 비례 나눔 `amountOnly`). 고액 문턱(누계 ≥ 문턱, 0 = 끔)에 닿고 권한이 없으면 `group_buy_admin_refunds` 승인 대기(`AWAITING_APPROVAL`) → 고액 권한자 `approvePending`(그때 다시 계산해 금액이 다르면 409). 운영 환불 줄 몫은 그 줄의 다음 반품 환불에서 먼저 뺀다(`prior_refund_offset`).
  11. 공급 불가: ① 기록 + 판매 중지 ② 주문마다 새 트랜잭션 — 그 단품 줄 전부 취소(재계산 없음), 본 상품이 모두 빠지면 주문 전체(배송비 포함) ③ 단품 최대 = max(확정 + 선점, 1), MAIN 이면 공구 최대도 같은 수만큼(최소 수량 아래로는 DB CHECK 가 막아 그만큼만). 선택 기한 = 설정 영업일의 마지막 날 끝(KST), 기한 전 미응답 주문은 발송 거절, 기한 뒤 워커가 KEEP_REST(defaulted).
  12. 배송지 수정 기한: 즉시 발송 = 결제 중·CONFIRMED, 마감 후 발주 = 결제 중·JOINED·CONFIRMED 이고 공구가 RECRUITING·CLOSING·CLOSED_SUCCESS. 명단 CSV 는 UTF-8 BOM·늘 큰따옴표, `= + - @`·탭·CR 로 시작하면 `'`. 열람 감사 detail 은 사유·주문 번호만.
  13. 에스크로 결제 여부는 발송 줄 `escrow_applicable` 에 처음 호출 결과로 남긴다(아니면 SKIPPED, 구매 확정 대상에서 빠짐). 송장 정정·교환 재발송은 LOGISTICS 를 다시(이미 등록됐으면 PATCH).
  14. 수동 처리 「계좌 송금」은 결제 모듈 → 공구 처리기가 환불 기록 방법을 BANK_TRANSFER(진행 중 그대로) + 송금 대기 줄 — 송금 완료 때 기록 COMPLETED. 공구 쪽 `BankTransferService.convertRefund` 가 같은 길을 부른다(금액 변경 전환은 받지 않음). 불변식은 BANK_TRANSFER 수동 취소를 「아직 돌려줄 몫」에서 뺀다.
  15. 불변식 검사에 `RETURN_FEE_BALANCE`(부족분 결제 − 취소 = 낸 금액 − 돌려준 금액)를 더하고, `PAYMENT_BALANCE` 는 계좌 송금으로 돌린 취소를 뺀다. 다시 판매·폐기·교환 출고 이동 줄은 단품 이후 값을 조건부 UPDATE … RETURNING 으로(기록만 하는 이동도 같은 문장).
- **2b-3 보강**(사용자 지시 보강 2026-10-28 — 같은 브랜치, 마이그레이션 shared `V103__group_buy_defect_known_on.sql`)
  16. **「안 날부터 30일」**(전자상거래법 제17조 ③): 배송완료 + 3개월이 지나도 요청 줄이 모두 업체 부담(하자·오배송·훼손 — 제17조 ③ 이 「표시·광고·계약과 다른 경우」 전부라 셋 다)이면, 회원이 적은 「하자를 안 날」(`RequestInput.defectKnownOn`)이 오늘(한국 날짜)부터 30일 안일 때 받는다. 안 날이 미래이거나 배송완료 날 전이면 400(`groupbuy.request.defectKnownOn.invalid`), 적지 않았거나 31일째면 409 `RETURN_WINDOW_CLOSED`. 단순 변심·기타 줄이 있으면 안 날과 관계없이 7일이고 안 날은 남기지 않는다. 안 날은 `group_buy_requests.defect_known_on`(업체 부담 반품만 — CHECK)에 남아 운영자가 본다. 운영자는 그 날짜가 사실과 다르면 기존 사유 코드 `OUT_OF_WINDOW` + 근거 사진으로 거절하고, 거절 감사 detail 에 `defectKnownOn` 이 남는다(새 사유 코드는 두지 않았다).
  17. **공급 불가 뒤 결제 중 주문**(결정 53): 결제 확정(`onPaid`)이 단품 수량을 확정하는 UPDATE **뒤에** `group_buy_supply_failures` 를 묻고, 그 단품이 있으면 `REJECT`(`GROUP_BUY_SUPPLY_FAILED`, 주문 만료 `REJECTED_CLOSED` + 선점 해제 → 자동 취소, 예외 없음). 공급 불가 기록 트랜잭션도 같은 단품 행을 잠그므로(판매 중지 UPDATE) 그쪽이 먼저면 결제 확정이 기다렸다가 기록을 보고, 결제 확정이 먼저면 공급 불가는 그 커밋을 기다린 뒤 결제된 주문으로 그 줄을 취소한다 — 빠지는 틈이 없다. 공급 불가 처리 때 결제 중 주문을 함께 닫지는 **않았다**: 닫으려면 결제 행을 먼저 잠가야 하는데(결정 37) 공급 불가는 주문 행부터 도는 흐름이라, 결제 확정의 판정과 선점 만료 워커에 맡긴다.
  18. **G17 동시성**: 같은 주문에서 ① 발송 전 취소 요청 승인 ∥ 운영 환불(전체) ② 반품 완료(환불 단계) ∥ 운영 환불(금액 — 둘 합이 남은 결제액을 넘게) ③ 발송 전 취소 요청 승인 ∥ 공구 중단 환불 워커를 연결 여럿으로 동시에, 이기는 쪽이 바뀌게 4번씩 돌린다. 모두 주문 행 → 요청 행 차례로 잠그므로 교착 없이 한쪽이 기다렸다가 새 상태로 판정한다. 운영 환불은 열린 요청이 있으면 409 `REQUEST_OPEN` 이라 먼저 잡으면 지고, 늦게 잡으면 요청이 끝난 상태에서 남은 결제액 안만 처리한다. 중단 워커가 먼저면 전체 환불 + 열린 요청 SUPERSEDED, 승인이 먼저면 1개 취소 뒤 워커가 나머지.
  19. 계약 2b-3 행·마이그레이션 계획: 2b-4 는 V104, 2b-6 Q&A 는 V105 로 한 칸씩 밀었다(그 뒤 V105 를 테마·레이아웃이 써서 Q&A 는 V106 — 2026-10-29).
- **규칙 → 테스트**(shared, 실제 PostgreSQL `CommerceTestDatabase` — 새 시험 5개 클래스 52개)

  | 규칙 | 테스트 |
  |---|---|
  | 10-8 기준 표 P36·P37·P37b·P37c·P37d·P38·P38b·P39·P40·P41·P42 | `GroupBuyRefundCalculatorTest` 해당 이름 |
  | G7(세 번 반품 22,000·19,000·25,000, 재부과 두 번 빼지 않음, 왕복 맞춤) | `GroupBuyRefundCalculatorTest.G7_…`(둘) · `GroupBuyRequestTest.G7_G6_…`(실제 요청 흐름·REFUNDED·PG 누계 66,000) |
  | G14(하자 + 변심 한 요청 — 반품비 운영자, 줄별 금액, 결정 43 맞춤 편도·왕복) | `GroupBuyRefundCalculatorTest.G14_…` · `GroupBuyRequestTest.G14_…` |
  | G31(업체회수 −1,000 부족분 결제해야 접수 · 고객발송 2,000 · 미결제 하루 뒤 닫힘·결제창 닫힘 · 접수 뒤 거절이면 1,000 취소) · 결정 68 | `GroupBuyRefundCalculatorTest.G31_…` · `GroupBuyRequestTest.G31_…` |
  | G32(발송 전 2/3 취소 뒤 남은 1개 22,000·25,000·28,000) · 결정 42·53 | `GroupBuyRefundCalculatorTest.G32_…` · `결정42_…` |
  | 결정 71(운영 환불 몫을 반품에서 뺌·남은 결제액 이하) · G33 | `GroupBuyRefundCalculatorTest.결정71_…` · `GroupBuyAdminRefundTest.G33_결정71_…` |
  | P33·P34 반품 기한 | `GroupBuyRequestTest.P33_P34_…` |
  | 보강 16 「안 날부터 30일」(3개월 뒤 하자·오배송 받음 · 안 날 없음·31일째 409 · 미래·배송 전 400 · 변심은 7일 · 안 날 저장 · 운영자 `OUT_OF_WINDOW` 거절 감사에 날짜) | `GroupBuyRequestTest.P33_보강_…` |
  | G1 다시 판매(모집 중 + 단순 변심 · 하자 거절 · CLOSING 뒤 거절 · 보관/폐기 수량 그대로 · 합 ≠ 줄 수량) · 결정 15 · G6 | `GroupBuyRequestTest.G1_…` |
  | G15·P41 보상(PG 안이면 함께·보상 누계 / 넘는 몫 계좌 송금·송금 완료 뒤 계좌 삭제 · 상한 · 변심엔 없음) | `GroupBuyRequestTest.P41_G15_…` · `G15_보상이_…` |
  | G30 전체 반품 뒤 REFUNDED · 그 뒤 운영 환불 거절 · 결정 46 | `GroupBuyRequestTest.G7_G6_…` |
  | G36·결정 73 대사 이슈 열린 결제 승인 거절 | `GroupBuyRequestTest.G36_…` |
  | G40·결정 55 거절 사유 코드·근거 사진·청약철회 제한 안내 | `GroupBuyRequestTest.G40_…` |
  | G39·결정 54 교환 재발송·환불 0·교환 출고·에스크로 다시·환불 전환(미리 보기 다시 계산) · 변심 교환 거절 | `GroupBuyRequestTest.G39_…` |
  | 10-3 발송 전 취소 요청 승인(줄·재부과)·열린 요청 하나·「이미 발송함」 거절은 송장으로 발송 | `GroupBuyRequestTest.발송_전_취소_요청은_…` |
  | 결정 72 미리 보기 ↔ 승인 | `GroupBuyRequestTest.결정72_…` |
  | 결정 88 반품도 추가상품만 남기기 금지 · 철회 뒤 다시 요청 | `GroupBuyRequestTest.반품도_…` |
  | G20 반품은 처리 방법과 관계없이 1인 한도에서 빠짐 | `GroupBuyRequestTest.G20_…` |
  | 결정 69 지연 취소 전액이면 열린 요청 SUPERSEDED | `GroupBuyRequestTest.결정69_…` |
  | G34·결정 70 계산 환불은 일반 권한·PG 한 번, 운영 환불 누계 문턱·승인 대기·문턱 0 | `GroupBuyAdminRefundTest.G34_…` |
  | 결정 32 운영 환불(발송 전 = 취소·배송 중 거절·배송완료 금액만·REFUNDED·남은 결제액 초과 거절) | `GroupBuyAdminRefundTest.결정32_…` |
  | G35·G21(GAVE_UP 중 같은 금액 다시 — 결정 38)·결정 67 계좌 송금 전환·송금 완료 | `GroupBuyAdminRefundTest.G35_G21_…` |
  | G35·G18·결정 16·74 콘솔 수동 처리 — 취소 누계 반영·환불 MANUAL·이벤트·대사 SUCCEEDED 합 | `GroupBuyAdminRefundTest.G35_G18_…` |
  | G38·결정 53 공급 불가(그 단품만 산 주문 전액 · 섞인 주문 그 줄만 재부과 없음 · 판매 중지·최대 줄임 · 기한 전 발송 거절 · 전체 취소 · 기한 뒤 나머지만) | `GroupBuySupplyTest.G38_…` |
  | 보강 17 공급 불가 뒤 그 단품을 품은 결제 중 주문 결제 → REJECT(`GROUP_BUY_SUPPLY_FAILED`)·자동 취소·만료·선점 해제, 다른 단품만이면 받음 · 불변식 | `GroupBuySupplyTest.G38_공급_불가_뒤_…` |
  | 보강 18 G17 승인 ∥ 운영 환불 · 반품 완료 ∥ 운영 환불 · 승인 ∥ 공구 중단 환불 워커(동시 4번씩 — 교착 없음 · 환불 기록 합 = 누계 = 성공한 PG 취소 합 ≤ 결제액 · 남은 결제액 + 1 거절 · 불변식) | `GroupBuyRequestConcurrencyTest`(셋) |
  | G21·P35 영업일(주말·휴일)·2영업일 경고·3영업일 심각·요청 처리 기한 단계 | `GroupBuySupplyTest.G21_P35_…` · `P35_…` |
  | 결정 11 택배사 시드·이력 있으면 지우기 금지·주소 규칙 · 송장 정리 | `GroupBuyFulfillmentTest.택배사는_…` · `송장은_…` |
  | 결정 47·43·10-3 발송 일괄 행 결과(열린 요청·택배사·송장) · 처음 배송비 고정 | `GroupBuyFulfillmentTest.발송_일괄은_…` |
  | 발주 → 준비중 → 발송(SHIPPING) → 배송완료 → COMPLETED(결정 7·9) | `GroupBuyFulfillmentTest.마감_후_발주_공구는_…` |
  | 결정 64 에스크로 커밋 밖·재시도 표·송장 정정 PATCH · 6번 실패 GAVE_UP · 결정 14 구매 확정 7일·열린 요청이면 안 함 | `GroupBuyFulfillmentTest.에스크로_…`(둘) · `결정14_…` |
  | 10-9 가린 값·운영자 열람(사유·기록·감사에 평문 없음·10분)·본인 보기 | `GroupBuyFulfillmentTest.가린_값과_…` |
  | G10·결정 30 배송지 수정·명단 기한(즉시 발송 / 마감 후 발주) · 발송된 주문이 있는 공구 중단 | `GroupBuyFulfillmentTest.G10_…`(셋) |
  | G21·결정 20 CSV 수식 주입 | `GroupBuyFulfillmentTest.G21_…` |
  | 불변식(판매 수량·이동 줄·결제 잔액(계좌 송금 제외)·반품비 결제 잔액) | 위 시험마다 끝의 `assertInvariants` |
  | 워커 「언제 돌릴지」 — 발송·요청 워커 | admin `GroupBuyStateSchedulerTest`(고침) |
- **실행**(JAVA_HOME 지정): `./gradlew :server:shared:test --tests 'com.community.groupbuy.*' --tests 'com.community.payment.*' --tests 'com.community.audit.*'` → **278개 통과**(공구 158 = 2b-2 106 + 새 52, 결제·감사 포함, 실패 0) · `./gradlew :server:admin:test --tests 'com.community.batch.service.GroupBuyStateSchedulerTest' --tests '*AuditRetentionAdminTest' --tests 'com.community.payment.*' :server:api:test --tests '*IdentityHttpTest' --tests 'com.community.payment.*'` → admin **25 통과**·api **13 통과**(두 앱 문맥이 새 빈과 함께 뜬다), 실패 0. 전체 테스트는 돌리지 않았다.
- **보강 실행**(같은 명령) → shared **283개 통과**(공구 163 = 158 + 보강 5: `GroupBuySupplyTest` 1 · `GroupBuyRequestTest` 1 · `GroupBuyRequestConcurrencyTest` 3) · admin **25**·api **13 통과**(V103 과 함께 문맥이 뜬다), 실패 0. 전체 테스트는 돌리지 않았다.
- **아직 테스트로 막지 못한 규칙과 이유**(2b-3 보강 뒤 — 2b-4 몫과 2b-5 실제 확인만 남는다)
  - 포트원 에스크로·부분 취소 실제 동작(2b-5), 회원·관리자 컨트롤러·권한 코드(`commerce.order.manage`·`commerce.refund.approve`·`.large`·`commerce.address.reveal`·`commerce.export`)·`MoneyRequestGuard`·알림·메일·P35 운영자 알림 발송(이벤트만) — 2b-4.
  - 결제 관리자 화면의 「수동 처리 방법」 고르기(서비스는 받음) — 2b-4 컨트롤러.
  - 회원 반품 API 의 「하자를 안 날」 입력(`defectKnownOn`)과 관리자 요청 상세의 그 날짜 표시 — 서비스·DB 는 받음, 2b-4 컨트롤러·화면.

## 2b-4 구현 결과 (2026-10-28 — 브랜치 `claude/commerce-2b-4-groupbuy-api-9e8cb4`, 2b-1 화면 브랜치를 합쳐 연결, develop 병합 전)
- **마이그레이션** shared `V104__commerce_sales_switch.sql`(V103 은 2b-3 보강 몫으로 비움): `commerce_sales_settings`(1행 — 공구 판매 스위치, 설치 직후 꺼짐) · `commerce_sales_checklist`(운영 체크 여섯 — 누가·언제) · `commerce_business_info`(1행 — 결정 18 열두 칸, 등록번호 모양 CHECK) · 회원 알림 종류 `GROUP_BUY`(CHECK — V94 목록 ∪) · `group_buy_requests.deadline_alert_level`(P35 단계마다 한 번) · 회원 권한 `commerce.purchase` 를 `pointshop.use` 를 가진 역할에 같이 줌(V90 `group.create` 방식) · **2b-2 보강** `ux_group_buy_skus_combination` 을 값이 있는 MAIN 단품만으로(옵션 이름 없이 이름으로 가리는 단품 여럿 — 쌀 10kg·20kg — 을 공구에 담지 못하던 빈 곳, local 시드 시험이 잡음). 반복 `R__batch_32_groupbuy_privacy_cleanup.sql`(배치 `groupbuy-privacy-cleanup`, 매일 04:35). Q&A(2b-6)는 V106(V105 는 테마·레이아웃).
- **코드**
  - shared `com.community.commerce.sales` — `CommerceSalesStore`·`CommerceSalesService`(켜기 = 최고 관리자 + 자동 조건 넷(사업자 정보·PG·켜진 택배사·본인인증) + 체크 여섯, 끄기 = 최고 관리자 또는 `commerce.settings`, 사유 5~200자, 감사 `COMMERCE_SALES_TOGGLE` + 운영자 알림, 공개 정보 `publicInfo`, 등록번호 체크섬)·`BusinessInfo`·`CommerceSalesException`(`COMMERCE_SALES_NOT_READY {missing}`·`_ROOT_ONLY`). `DefaultGroupBuySalesSwitch` 는 이제 DB 의 지금 값(결정 19). `com.community.commerce.image.GroupBuyImageUploader`(JPEG·PNG·WebP 정지 5MB → 다시 인코딩·EXIF 제거·1600/400px, 키 `groupbuy/<용도>/<년월>/<번호>.jpg` = 화면의 미디어 ID).
  - shared `com.community.groupbuy` — `GroupBuyNotifications`(이벤트 → 회원 알림 `GROUP_BUY`·커머스 메일 대기열·운영자 알림, 업무 트랜잭션 안·결제 행 잠금 안에서도 공구 행을 잠그지 않음) · `GroupBuyDeadlineWatch`(1시간 — P35·결정 51·마감 임박, 워커 키 `groupbuy-deadline`, admin `GroupBuyStateScheduler.deadlineTick`·`roboyam.groupbuy.deadline-poll-millis`) · `GroupBuyShipDeadlines`(발송 기한 한 식 — 대시보드·주문 거르기·워커) · `GroupBuySettingsService`(범위·처음 값의 정본, 바뀐 칸만 전→후 감사 `GROUPBUY_SETTINGS_UPDATE`) · `GroupBuyWatchService` · `GroupBuyDescriptionHtml`(상품 설명 정화 — `GroupBuyProductService` 저장이 늘 거친다). 이벤트 `Extended`(상태 워커 연장)·`DelayAnnounced`(발송 지연 공지) 더함. `ShippingPolicyService.all`·`deleteScheduled`(시작 전 줄만, 409 `COMMERCE_SHIPPING_POLICY_STARTED`, 감사 돈 관련) · `GroupBuyAdminRefundService.preview`(운영 환불 확인 창 — 막는 조건·계산은 `refund` 와 같음). 회원 알림·필수 메일: `MemberNotificationType.GROUP_BUY`·`MemberEvent.GROUP_BUY`(끌 수 없음) · `RequiredMail.GROUP_BUY_ORDER_CONFIRMED`(계약 내용 통지 — 결정 57)·`_REFUND_COMPLETED`·`_ORDER_NOTICE`(발송 지연·공급 불가·자동 취소) + 문구 카탈로그·기본 문구. 운영자 알림: `COMMERCE_ORDER_ANOMALY`·`COMMERCE_REFUND_REQUESTED` 예약 풀림, 새 `COMMERCE_SHIP_OVERDUE`·`COMMERCE_GROUPBUY_CANCELED`·`COMMERCE_SALES_TOGGLE`. 감사 7개(`COMMERCE_SALES_TOGGLE`·`GROUPBUY_SETTINGS_UPDATE`·`COMMERCE_SHIPPING_POLICY_DELETE` 돈 관련 · `GROUPBUY_ORDER_VIEW` 개인정보 · `COMMERCE_SALES_CHECKLIST`·`COMMERCE_BUSINESS_INFO_UPDATE`·`GROUPBUY_ORDER_MEMO` 일반). 공개 API 경로를 보안 설정에 더함(목록·카테고리·상세·견적·`/api/commerce/info`).
  - api `com.community.groupbuy` — `GroupBuyController`(`/api/group-buys` — 주문은 `MoneyRequestGuard` `GROUPBUY_JOIN`)·`MyGroupBuyOrderController`(`/api/me/orders` — 본인 취소·지연 취소·요청은 `MoneyRequestGuard` `REFUND_REQUEST`)·`CommerceInfoController` + `GroupBuyMemberService`·`MyGroupBuyOrderService`·DTO. 회원 알림함이 `GROUP_BUY` 를 그린다.
  - admin `com.community.groupbuy` — 컨트롤러 다섯(`/api/admin/group-buys`(+카테고리·옵션 종류·이미지·수량)·`/group-buy-products`·`/group-buy-orders`·`/group-buy-requests`·`/api/admin/commerce/{dashboard,shipping-policies,carriers,business-info,sales,group-buy-settings}`) + 파사드 다섯 + DTO, `AdminScreenRegistry` 공구 묶음(결제 묶음 앞, 화면과 같은 키·주소·권한·아이콘·차례), `Permission` 12개 `featureReady=true`(결정 35 — `product.read/manage/price`·`stock.adjust`·`order.read/manage`·`refund.approve/large`·`address.reveal`·`groupbuy.cancel`·`export`·`settings`, 화면 카탈로그도), 결제 「수동 처리 완료」에 방법(`CONSOLE_CANCEL` + 포트원 취소 번호 / `BANK_TRANSFER` / 표시만 — 결정 74) 받기, `config.GroupBuyLocalSeeder`(local 프로필 + 배치를 켠 서버).
  - 화면: 두 앱의 공구·커머스 API 한 겹을 실제 호출로 바꾸고 목업 파일을 지웠다(화면 모양 그대로). user-web 공구 타입의 ID 를 글자로(스노플레이크 — JS 정밀도), 정상가 근거 값 `PREVIOUS_ROUND`·`PREVIOUS_SALE`(서버와 같게), 요청 상태 `RECEIVED`·`EXPIRED`·사유 `DAMAGED` 더함, 쪽 번호는 0부터(화면 그대로 — 서버도 0부터). admin-web 결제 이상 화면 「수동 처리」에 방법 고르기(SelectButton + 콘솔 취소 번호 칸, i18n ko·en).
- **정한 세부**(계약에 없던 것 — 가정, 보고에 올림)
  1. 회원 알림은 종류 하나 `GROUP_BUY` + 덧붙임 `kind`(주문 확인·자동 취소·선점 만료·수량 풀림·마감 임박·연장·차수 시작·환불 완료·운영자 처리 환불·발송 지연·공급 불가 선택·성사·불발·중단·요청 상태) — 회원이 끌 수 없는 거래 알림. 메일은 법·돈에 닿는 것만 커머스 메일 대기열로(계약 통지·환불 완료·주문 안내), 관심 알림 메일은 회원 이벤트 설정의 이메일을 따른다.
  2. 메일 변수의 글(판매 조건·발송 문구)은 사이트 기본 언어(한국어)로 만든다 — 대기열은 변수 값을 그대로 쓴다.
  3. 수량 풀림: 단품 신청은 단품·공구 남은 수량이 둘 다 > 0(추가상품은 단품만), 공구 단위 신청은 공구 남은 > 0, 같은 신청은 `last_notified_at` 조건부 갱신으로 10분에 한 번. 연장 알림 = 마감 임박·수량 풀림 신청자. 마감 임박 = 모집 마감 24시간 안(회원 화면 「마감 임박」과 같은 창), 신청마다 한 번.
  4. P35 경고: 요청·단계(WARNING → CRITICAL)마다 한 번(`deadline_alert_level` 조건부). 발송 기한(결정 51): 마감 후 발주 = 발송 예정일 다음 날 0시(KST), 즉시 발송 = 결제(확정)부터 N영업일 끝, 그 뒤 발송 지연 공지가 있으면 새 예정일 다음 날. 경고는 하루 한 건(묶음 키 = KST 날짜).
  5. 개인정보 파기: 배송지 = 배송완료·취소/환불 뒤 180일, 만료 주문 30일(공구 설정), 열린 요청이 있으면 미룸 · 수정 이력 = 다음 판이 생긴 지 180일 지난 판(공구의 마지막 판은 남김) · 요청 = 끝난 지 3년 지난 요청의 회원 사유 글(`memo`)·회원 사진 줄(운영자 거절 근거·금액은 청약철회 기록으로 남김). 지운 사진 줄의 파일은 저장소에 남는다(고아 파일 정리 몫).
  6. 추가 차수: 화면이 기간을 보내지 않아 처음 값 = 내일 같은 시각 시작 · 7일 모집 · (마감 후 발주면) 마감 7일 뒤 발송 예정(DRAFT 라 고친다). 회수 확인의 반환받은 날 = 처리한 시각(3영업일 기한이 늦춰지지 않게). 교환 재발송의 돌려받은 물건 = 줄마다 「공급처 반송」. 배송 명단 = 한 번 쓰는 `data:` 주소(서버에 파일을 남기지 않음).
  7. 대시보드: 준비 대기 = CONFIRMED, 곧 만료될 선점 = 2분 안, 남은 수량 적음 = 모집 중 공구의 남은 ≤ 5 또는 ≤ 10%, 배송완료 누락 = 발송 뒤 7일 지난 SHIPPED. 칸마다 볼 권한(주문 `commerce.order.read` · 수량 `commerce.product.read` · 결제 `commerce.payment.read`)이 없으면 null.
  8. 관리자 권한 입구: 요청 승인 `commerce.refund.approve`, 완료(처리 방법 + 환불) `order.manage` + `refund.approve`, 공급 불가 `order.manage` + `refund.approve`, 발주·발송 지연 공지 `order.manage`, 모집 뒤 공급가 `product.price`, 대시보드는 셋 중 하나. 공급가는 `product.price`·`settlement.read` 가진 운영자에게만 실린다.
  9. 판매 켜기 자동 조건의 PG = `PaymentGateway.ready()`, 본인인증 = 업체 설정으로 시작 정보를 만들 수 있음. 회원 공개 정보의 사업자 정보는 판매 켜짐 + 필수 여덟 칸(등록번호 체크섬 포함)일 때만.
  10. local 시드: 사업자 정보·체크 여섯을 채운 뒤 **켜는 조건 검사를 그대로 통과해야** 켠다. 예시 품목 여덟(햅쌀·사과/감귤·고등어·김치·소스·티셔츠·러닝화·로봇 키트)과 각 품목 고시 양식을 다 채우고, 공구 여덟(성사형 모집 중 셋 · 선착순 즉시 발송 둘(과일 옵션 넷·티셔츠 색상 × 사이즈) · 마감 임박 · 조기 마감으로 끝난 1차 · 공개된 예정). 「다 팔려 끝난 1차」는 결제 없이 만들 수 없어 조기 마감(`EARLY_CLOSED`)으로 대신했다.
- **규칙 → 테스트**(실제 PostgreSQL `CommerceTestDatabase`)

  | 규칙 | 테스트 |
  |---|---|
  | 결정 57 계약 내용 통지(주문 줄·금액·판매 조건·발송·판매자) · P27 메일은 결제 확정 트랜잭션의 대기열에 한 통(다시 처리해도 한 통) | shared `GroupBuyNotificationsTest.결제가_확정되면_…_P27` |
  | 결정 16 환불 완료 알림·메일 | `GroupBuyNotificationsTest.환불이_끝나면_…` |
  | 결정 12·40 불변식 거절 = 운영자 주문 이상(주문마다 묶음), 도메인 거절은 회원 안내만·메일 한 통 | `GroupBuyNotificationsTest.불변식이_깨진_거절은_…` |
  | 결정 81 수량 풀림 — 공구·단품 둘 다 남을 때만·같은 신청 10분에 한 번(선점 만료 RELEASE 흐름 포함) | `GroupBuyNotificationsTest.수량_풀림은_…` |
  | 연장 알림(마감 임박·수량 풀림 신청자, 같은 회원 한 번) · 마감 임박 알림(24시간 안, 한 번) | `GroupBuyNotificationsTest.모집이_연장되면_…` · `마감_임박_알림은_…` |
  | **G8**(2b-4 몫) 추가 차수 모집 시작 → 루트의 이전 신청자에게 같은 회원 한 번, 새 차수로 가는 길 | `GroupBuyNotificationsTest.G8_…` |
  | 결정 52 발송 지연 공지 → 공지 전에 결제한 발송 전 주문 전원 앱 + 메일(발송된 주문 제외) · 결정 53 공급 불가 선택 앱 + 메일 · 중단 회원·운영자 알림 · 요청 접수 운영자 알림(본인 철회는 회원 알림 없음) | `GroupBuyNotificationsTest.발송_지연_공지는_…` · `공급_불가로_…` · `중단은_…` · `요청이_접수되면_…` |
  | **G21·P35**(2b-4 몫) 휴일이 낀 처리 기한 — 휴일 건너뛰어 2영업일 경고·3영업일 심각, 단계마다 한 번 | `GroupBuyNotificationsTest.G21_P35_…` |
  | **G37**(2b-4 몫)·결정 51 발송 기한 지난 발송 전 주문 경고(하루 한 건) · 발송 지연 공지의 새 예정일이 기한 | `GroupBuyNotificationsTest.G37_…` |
  | 20절 판매 스위치 — 설치 직후 꺼짐 · 켜기는 최고 관리자 · 자동 조건 + 체크리스트 · 감사(전→후·사유)·운영자 알림 · 끄기는 설정 권한자도 · 사유 | `CommerceSalesTest.설치_직후에는_…` · admin `GroupBuyAdminHttpTest.판매_켜기는_…` |
  | 결정 19 판매 스위치는 트랜잭션 안의 DB 값(캐시 없음) · 꺼지면 주문 403 `SALES_CLOSED`·단추 `SALES_CLOSED` | `CommerceSalesTest.판매_스위치_구현은_…` · api `GroupBuyHttpTest.판매가_꺼지면_…` |
  | **G41** 공개 정보는 판매 켜짐 + 필수 칸일 때만 사업자 정보 | `CommerceSalesTest.G41_…` · api `GroupBuyHttpTest.G41_…` |
  | 결정 18 사업자등록번호 체크섬·칸 길이·전자우편, 바뀐 칸 이름만 감사 | `CommerceSalesTest.사업자등록번호는_…` · admin `GroupBuyAdminHttpTest.판매_켜기는_…`(400 `values.businessNo`) |
  | 결정 10 공구 설정 범위·바뀐 칸만 전후 감사·휴일 정리·업무가 바로 읽음 | `CommerceSalesTest.공구_설정은_…` |
  | 결정 2 상품 설명 정화(스크립트·이벤트·iframe·style 제거, 이미지·링크 새 탭) · 남이 올리지 않은 이미지 키 거절 | `CommerceSalesTest.상품_설명은_…` · admin `GroupBuyAdminHttpTest.결정2_…` |
  | 결정 34·10-9 개인정보 파기(배송완료·취소 180일·만료 30일·열린 요청 미룸·거래 기록 남김 / 수정 이력 / 요청 3년) | shared `GroupBuyPrivacyCleanupBatchTest`(4) |
  | **P26** 같은 멱등 키·다른 본문 409, 키 없음 400, 같은 본문은 처음 응답 · `GROUPBUY_JOIN` 1분 5번 넘으면 429 | api `GroupBuyHttpTest.P26_…` · `주문_요청_제한_…` |
  | 공개 API(로그인 없이 목록·상세, 글자 ID, 공급가 없음) · 남의 주문 404 · 관심 신청은 서버가 허락한 때만 | api `GroupBuyHttpTest.목록과_상세는_…` · `P26_…` · `관심_신청은_…` |
  | 결정 35 권한(조회·관리·중단·수량 조정·환불·준비중·내보내기 = export + address.reveal 둘 다·고액 승인 대기 = refund.large·설정) · `featureReady` · 메뉴 서버=화면 | admin `GroupBuyAdminHttpTest.결정35_…`(둘) · `CommercePermissionsAndMenuTest`(고침) · `Commerce2aCatalogTest`(공구 묶음 = 포인트 상점과 결제 사이) |
  | 대시보드 칸은 볼 권한이 있는 칸만 | admin `GroupBuyAdminHttpTest.대시보드_…` |
  | 결정 74 결제 수동 처리 방법(콘솔 취소면 취소 번호 필수·모르는 방법 400) | admin `GroupBuyAdminHttpTest.결정74_…` |
  | 1시간 기한 점검 「언제 돌릴지」(배치를 켠 서버만·워커 기록) | admin `GroupBuyStateSchedulerTest.기한_점검은_…` |
  | 결정 11·87 local 시드(조건 검사를 통과해 켬·예시 품목 여덟 고시 완성·공구 여덟) + 2b-2 보강(이름으로 가리는 단품 여럿) | admin `GroupBuyLocalSeederTest` |
- **실행**(JAVA_HOME 지정): `./gradlew :server:shared:test --tests 'com.community.groupbuy.*' --tests 'com.community.payment.*' --tests 'com.community.audit.*' --tests 'com.community.notificationtemplate.*' --tests 'com.community.membernotification.*' --tests 'com.community.notification.*' --tests 'com.community.batch.*' --tests 'com.community.commerce.*' --tests 'com.community.point.PointExpiryNoticeJobTest' --tests 'com.community.pointshop.PointShopRefundRequestTest' :server:admin:test --tests 'com.community.groupbuy.*' --tests 'com.community.batch.service.GroupBuyStateSchedulerTest' --tests 'com.community.commerce.*' --tests '*AuditRetentionAdminTest' --tests 'com.community.payment.*' --tests 'com.community.menu.*' --tests 'com.community.pointshop.PointShopAdminMenuTest' :server:api:test --tests 'com.community.groupbuy.*' --tests '*IdentityHttpTest' --tests 'com.community.payment.*' --tests 'com.community.membernotification.*'` → shared **408** · admin **82** · api **21** 통과(합계 511, 실패 0). 새 시험: shared `GroupBuyNotificationsTest` 13 · `CommerceSalesTest` 6 · `GroupBuyPrivacyCleanupBatchTest` 4, admin `GroupBuyAdminHttpTest` 6 · `GroupBuyLocalSeederTest` 1 · `GroupBuyStateSchedulerTest` +1, api `GroupBuyHttpTest` 6(나머지는 바꾼 곳 회귀 — 공구 2b-2·2b-3 시험 전부 포함). 마지막에 더한 고액 승인 대기 API 는 admin `com.community.groupbuy.*` 를 다시 돌려 7 통과. 화면: `apps/user-web`·`apps/admin-web` `nuxt typecheck` 오류 0. 전체 테스트·E2E 는 돌리지 않았다.
- **아직 테스트로 막지 못한 규칙과 이유**
  - 포트원 실제 동작(결제·불발 대량 환불·부분 취소·에스크로·웹훅) — 2b-5.
  - (2b-3 보강 세션 몫 — 이 브랜치가 고치지 않기로 한 곳) 공급 불가 때 결제 중(PENDING) 주문의 그 단품 줄 — `GroupBuySupplyService`.
- **후속·확인 필요**: 고액 운영 환불 「승인 대기」의 승인·거절 **화면 단추**(API `POST /api/admin/group-buy-orders/{id}/refunds/pending/{pendingId}/approve|reject` — `commerce.refund.large` 는 더했다, 확정 화면에 단추가 없어 화면은 두지 않았다) · 사이트 푸터 모바일 자리(2b-1 가정 그대로) · 공정위 사업자정보공개 링크 의무[확인 필요].

## 2b-5 로컬 실제 확인 (2026-10-29 — 포트원 테스트 채널 KG이니시스 V2 카드, api 결제만 포트원·본인인증은 가짜)
결제는 사용자가 결제창에서 했고, 그 뒤 처리는 세션이 Chrome(회원 3100·관리자 3200)으로 했다. 결과는 `payment_orders`·`payment_cancels`·`group_buy_participations` 로 대조했다.

| 확인 | 주문 | 결과 |
|---|---|---|
| 공구 결제(두 줄 42,800원) | G2026102900002 | PAID, 주문 확정 |
| **부분 취소 연속 2회**(회원 발송 전 취소: 한 줄 19,900 → 남은 줄 + 배송비 22,900) | 〃 | PG 취소 2건 모두 SUCCEEDED(시도 1회), 결제 PARTIAL_CANCELED → CANCELED 42,800, 주문 CANCELED·환불 누계 42,800 |
| 공구 중단 → 환불 워커(마감 후 발주형 69,000원) | G2026102900003 | PG 취소 REFUND 69,000 SUCCEEDED, 결제 CANCELED, 주문 REFUNDED |
| 발송 → 배송완료 → 단순 변심 반품(업체 회수) → 승인 → 회수 확인 → 완료(보관 1) | G2026102900004 | 환불 21,900(상품 24,900 − 반품비 3,000), PG 부분 취소 SUCCEEDED, 결제 PARTIAL_CANCELED 21,900 |

- **확인하며 고친 것**(모두 테스트로 막음 · 아래 커밋):
  1. 회원 화면 주소 `?items=` 의 단품 ID 를 12자리까지만 받아(실제 눈송이 ID 18자리) 주문서가 「고른 상품을 확인할 수 없어요」 — 19자리까지(`UtilGroupBuy.parseItems`). 화면 코드라 테스트 없음(타입 검사만).
  2. 포트원 결제창이 `DataCloneError` 로 안 열림 — 구매자 정보가 반응형 Proxy 였다. 공용 `usePaymentCheckout` 이 평범한 객체로 복사해 넘긴다(프론트 가이드 2-3 에 규칙). 실제 결제창으로 확인.
  3. 반품 요청 500(NPE) — 새 요청은 처리 시각·회수 송장이 null 인데 그 한 줄을 `findFirst` 했다. 같은 모양(관리자 주문 메모·이메일 없는 회원)도 함께 고침. api `GroupBuyHttpTest.반품_요청은_처리_시각과_회수_송장이_아직_없어도_만들어지고_내_주문에_보인다`.
  4. 가짜 게이트웨이의 `/api/dev/payments/{id}/fake-checkout` 이 시험 결제(`PAYMENT_TEST`)만 받아 가짜 모드에서 공구 결제가 늘 404 — 본인의 결제 주문이면 종류와 관계없이 받는다(가짜 게이트웨이가 아니면 지금처럼 409). 위 api 시험이 공구 주문을 이 길로 결제한다.
- **실행한 테스트**: api `com.community.groupbuy.*`·`com.community.payment.*` 14 통과, shared `com.community.payment.*` 114 통과, admin 컴파일, user-web 타입 검사.
- **남은 확인**(공개 주소가 필요해 dev 서버에서): 웹훅(Transaction.Paid·Cancelled), 에스크로 배송 등록·구매 확정, 네이버페이·카카오페이 부분 취소, PG 가맹점 취소 한도.
- **화면 개선**(사용자 지시 2026-10-29): 회원 취소·반품 창에서 줄을 고르면 수량 칸이 비어 「+」를 한 번 더 눌러야 했다 — 처음 고를 때 수량 1로 채운다.

## 승인 (2026-10-28)
사용자: 「지금까지 확정된 상태로 다음 단계 진행해」 — 결정 1~86·Q&A 규칙·작업 단위·화면 목록·마이그레이션 계획을 추천안 그대로 승인, 2b-2 는 화면 확정 전 병행. 결정 43 은 지금 규칙 유지(나눠 모두 반품해도 한 번에 반품한 것과 같게 — 판매자 손해가 더 커지지 않는다, 처음 배송비까지 받으려면 그 공구의 배송비 정책을 왕복으로).

## 미확정 (승인 때 정할 것 — 승인으로 닫힘, 아래 확인 필요만 남는다)
- 결정 1~14·16·18~20·22·27~36·39~41·46~50·51·55~86 중 정책 판단이 든 것, 작업 단위·화면 목록·마이그레이션 계획(15·17·21·23~26·42~45·52~54 는 사용자 결정 2026-10-28, 37·38·61~66 은 결제 코드로 확인한 필수 규칙, 51·55~59 는 법령 사본으로 확인한 의무).
- [확인 필요 — 2b-5·법무] ~~이니시스 V2 카드 부분 취소 연속 2회~~(2b-5 로컬 확인 2026-10-29 — 됨), 네이버페이·카카오페이 부분 취소 연속 2회, 에스크로 구매 확정 전·후 부분 취소, PG 가맹점 취소 한도(불발 대량 환불), 공정위 사업자정보공개 링크 의무, 소비자분쟁해결기준·표시광고 고시 현행 원문(최종 검토는 법령 사본·판결 인용으로 확인 — law.go.kr 본문은 못 읽음).
- 2b-2 를 화면 확정 전에 병행할지(2a 와 같이).
- [확인 필요] 「한정 N개」 문구를 쓴 공구에 추가 입고·다음 차수를 열 때 표시·광고 문제 — 등록 화면에 「한정」 표현 안내를 두고 법무 확인 목록(commerce.md 20-2)에 더한다.

## 2b-6 결과 (2026-10-29 — 화면 확정 · 서버 · 연결, 브랜치 `claude/commerce-2b-6-groupbuy-qna-794c53`)
사용자: 「모두 확정」 — 공구 상세 탭 구조(상품상세·상품평·상품문의·배송/교환/반품 안내, 붙는 탭·scrollspy·해시), 리뷰 머리(네이버 쇼핑 모양 — 별점 분포 없음, 「리뷰 제공 기준 ⓘ」), 리뷰·Q&A 화면과 「리뷰 규칙」 가정 그대로.
- **마이그레이션** shared `V106__group_buy_qna_review.sql`: Q&A 셋(`group_buy_questions`·`group_buy_answers`·`group_buy_answer_revisions`) · 리뷰 넷(`group_buy_reviews` — `item_id` 유일, 지워도 줄은 남는다 · `_photos` · `_revisions` · `_replies`) · 신고 `group_buy_ugc_reports`. 반복 `R__batch_32` 에 ④ 문의 보관 만료 파기.
- **세션 정리 결정 — 신고 저장소**: 계약의 「기존 `reports` 대상 종류 CHECK 에 더한다」 대신 공구 신고 표 `group_buy_ugc_reports` 에 둔다. 기존 `reports`·`report_cases` 는 `board_id` 가 꼭 있어야 하고 신고 관리 화면이 게시판 기준이라, 공구 질문·리뷰를 넣으면 화면·사건 모델을 함께 고쳐야 한다. 회원 쪽 길은 같다(공용 `POST /api/reports`, 대상 `GROUP_BUY_QUESTION`·`GROUP_BUY_REVIEW` — `ReportService` 가 회원 상태·신고 켜짐·사유를 같은 규칙으로 검사한 뒤 넘긴다). 처리는 Q&A·리뷰 상세의 숨김(열린 신고 RESOLVED). 신고 간격·하루 한도는 아직 걸지 않았다(대상마다 한 번만).
- **서버** shared `com.community.groupbuy.feedback`: `GroupBuyQnaService`(쓰기 간격 30초 — 가정, 개인정보 모양 `PersonalInfo` — 전화·이메일·「구/군 + 로/길 + 번지」) · `GroupBuyReviewService` · `GroupBuyUgcReportService` · `FeedbackView` · `FeedbackException`. api `GroupBuyFeedbackController`(경로는 user-web API 주석 그대로) · 공구 목록 카드 `reviews`(평균 소수 첫째 자리·수). admin `GroupBuyFeedbackAdminController` · 관리자 메뉴 「공구 › Q&A」·「리뷰」(`AdminScreenRegistry`, `commerce.order.read`).
- **감사** `GROUPBUY_QNA_VIEW`(개인정보 열람) · `GROUPBUY_QNA_HIDE`·`GROUPBUY_QNA_ANSWER_EDIT`·`GROUPBUY_REVIEW_HIDE`·`GROUPBUY_REVIEW_REPLY_EDIT`(일반). **알림** 회원 `GROUP_BUY` 종류 `QNA_ANSWERED`·`REVIEW_REPLIED`(첫 답변·답글만) · 운영자 `COMMERCE_QNA_RECEIVED`·`COMMERCE_QNA_REPORTED`·`COMMERCE_REVIEW_REPORTED`.
- **테스트** shared `GroupBuyFeedbackTest` 15건(G11·G11R) 통과 · 함께 돌린 것: `GroupBuyPrivacyCleanupBatchTest`·`GroupBuyNotificationsTest`·`GroupBuySchemaTest`·admin `AuditRetentionAdminTest`·`CommercePermissionsAndMenuTest`·`GroupBuyAdminHttpTest`·api `GroupBuyHttpTest` 통과. 전체 회귀는 돌리지 않았다.
- **화면 연결**: user-web·admin-web 의 Q&A·리뷰 API 목업을 지우고 실제 주소로(타입 검사 통과). 메인 서버(8910·8920)는 이 브랜치 jar 가 아니라 **실제 호출 확인은 병합 뒤 서버를 다시 띄워야 한다**.
- **미확정 · 다음**: 공구 참여 제한(`GROUP_BUY_BAN`)이 질문을 막지 않는다는 규칙은 코드(정지·탈퇴만 막음)로만 지켜지고 시험은 없다 · 「이전 차수 문의 보기」 시험 없음 · 지운 리뷰 본문 보관 기간 미정(지금은 남긴다) · 붙지 않은 리뷰 사진 정리 배치 없음 · 대시보드 「기한 지난 Q&A」 칸 미연결 · 홈 GROUP_BUY 가젯은 공구 개발 끝난 뒤.

## 홈 `GROUP_BUY` 가젯 연결 (2026-10-29 — 브랜치 `claude/home-groupbuy-gadget-64ac1e`)
- **서버**: `GroupBuyMemberService.recruiting(count)` — 회원 목록(`GET /api/group-buys`)과 같은 거름(공개 · 숨긴 카테고리 제외)·차례(진행 탭 — 마감(연장 끝) 이른 순)·줄 모양(`ListItem`, 리뷰 평균·수 포함)에서 **모집 중(`RECRUITING`)만**(집계 중 `CLOSING` 은 살 수 없어 뺀다). 목록과 쿼리 앞부분(`VISIBLE_FROM`·`DEADLINE_ORDER`)·줄 만들기(`listItems`)를 같이 쓴다. 홈 가젯 data = `{items, salesOpen}` — 판매 스위치는 목록처럼 거르지 않고 값만 싣는다.
- **화면**: `HomeGadgetGroupBuyCard` 가 `GroupBuyListItem` 을 그린다(상세 주소는 목록처럼 `UtilGroupBuy.detailPath(id)` — 응답에 주소 칸은 없다). 판매가 꺼지면 가젯 위에 「지금은 주문을 받지 않아요」.
- **테스트** api `HomeGadgetGroupBuyTest` 4건(모집 중만·마감 순·줄 모양 / 공개 전·집계 중·숨긴 카테고리 제외 / `count` 1~10·기본 5 / 판매 꺼짐) 통과, `GroupBuyHttpTest` 7건 함께 통과.
