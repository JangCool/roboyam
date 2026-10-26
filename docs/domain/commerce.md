# 커뮤니티 커머스 (포인트 상점 · 현금 충전 · 공구)

> **상태: 개정안 — 1-2절 결정 D1~D13 과 주요 정책·기술 항목(본문에 [확정 2026-10-23] 표시)을 사용자와 검토해 확정(2026-10-23). 표시가 [개정]·[추가] 로만 남은 세부는 제안 상태. [법무] 항목은 오픈 전 확인.** 사용자가 준 원안을 community 프로젝트(`D:\project\community\workspace\community`)의 확정 계약과 roboyam 현재 구현에 맞춰 고치고 빠진 부분을 채웠다. 코드는 아직 이 문서대로 바뀌지 않았다.
> 표기: **[확정]** 원안에서 그대로 유지하는 정책 · **[개정]** 원안을 바꾼 것(이유를 붙임) · **[추가]** 원안에 없던 것 · **[결정 필요]** 사용자 답이 필요한 것(1-2절에 모음) · **[법무]** 오픈 전 전문가 확인.
> 정책 숫자는 모두 설정값(DB 설정 행 또는 정책 표)으로 두고 하드코딩하지 않는다. 표의 숫자는 기본값이다.

---

## 1. 검토 결과

### 1-1. 원안과 달라진 큰 줄기

| # | 주제 | 원안 | 개정 | 이유·근거 |
|---|---|---|---|---|
| 1 | 포인트 저장 | 새 `point_account`·`point_lot`·`point_ledger` | [확정 2026-10-23] **기존 V51(`point_balances`·`point_ledger`·`point_settings`)을 확장**: 잔액을 무상/유상으로 나누고 `point_lots`·`point_ledger_lots`를 더한다 | roboyam 은 V51 로 원장·잔액·`PointService` 한 길이 이미 구현·연결돼 있다(`docs/features/point-contract.md`). 갈아엎으면 화면·관리자 API·정책 사본을 다시 만들어야 한다 |
| 2 | 활동 적립 규칙 | 고정 숫자(글 10P·추천 1P/개·하루 200P) | **현재 `point_settings` 가 정본, 추천은 문턱형 유지 [확정 D7]**. 원안의 조건(최소 글자 수·작성 간격·자기 글 댓글 제외·하루 총 한도·연속 출석)은 설정 칸으로 **추가** | 추천 1개당 지급은 community 가 품앗이·다계정 조작 때문에 기각하고 **문턱형**을 골랐고 roboyam 도 문턱형으로 구현했다 |
| 3 | 본인인증 범위 | 적립·구매·충전·공구 모두 필수 | **현금이 움직이는 일(충전·공구 결제·유상 포인트 환불)과 가입 포인트만 필수**. 활동 적립·무상 포인트 상점은 지금처럼 이메일 인증 + 정상 회원 | 지금 roboyam 은 본인인증이 전혀 없다(휴대폰·CI·생년월일 칸 없음). 커뮤니티 활동 전체에 본인인증을 걸면 가입·활동 장벽이 크다. 포인트는 선물·양도가 없고 공구 결제는 본인인증이 필수라, 다계정으로 모은 포인트를 공구에 쓸 길이 없다(12-1) |
| 4 | 공구 방식 | 최소 수량 성사형만 | **같은 모델에서 `min_qty = 0` 을 허용**해 community 의 「기간·수량 한정 특가(선착순)」도 표현 | community 공구는 목표 인원 없는 선착순 특가다. 성사형은 대량 환불이 따라오는 가장 위험한 흐름이라 공구마다 고르게 한다 |
| 5 | 마감 판정 시점 · 선점 시간 | `end_at` 순간의 확정 수량, 결제 중인 건은 이후 즉시 취소 · 선점 10분 | **선점 5분 [확정 D9]**, **`CLOSING`(마감 처리 중) 상태 추가**: 마감 시각에 새 선점만 멈추고, 이미 선점한 결제가 끝날 때까지(최대 선점 시간 + 여유 2분) 기다린 뒤 판정. 마감 무렵 수량이 풀리면 최대 30분 연장(10-2) | 마감 직전에 결제한 회원의 돈을 바로 되돌리는 일을 없앤다 |
| 6 | 결제 실패·이탈 | 실패 즉시 FAILED + 참여 EXPIRED | **선점 시간 안에서는 다시 결제 가능**(상태 유지), 선점 만료 워커(15-4)와 조회 시 정리(10-4)가 정리. **시간이 지난 뒤 도착한 결제는 원안대로 무조건 자동 취소** [확정 2026-10-23] | 결제창 실패 한 번에 수량을 잃지 않게 하되, 시간 규칙은 예외 없이 지킨다 |
| 7 | 결제 검증 실패 | 포트원 취소 API 로 즉시 취소 | [확정 D10] 즉시 자동 취소를 **시도**하되 같은 취소 대기열(`payment_cancels`)로 재시도, 끝내 실패하면 **결제 이상 목록**(관리자) | community 는 금액 불일치를 `NEEDS_REFUND`(수동 환불)로 굳혔다. 자동 취소가 사용자에게 낫지만 PG 호출은 실패할 수 있어 재시도·노출이 필요하다 |
| 8 | 결제 → 도메인 연결 | `PaymentCompleted` 이벤트(`@TransactionalEventListener`) | [확정 2026-10-23] 공용 결제 주문표 하나·확정 코드 하나·취소 대기열 하나 + **결제 확정 트랜잭션 안에서 동기 핸들러**(`PaymentOrderHandler`, order_type 별)를 부르고, 알림만 커밋 후 이벤트 | 「PAID 인데 포인트·참여가 안 생김」을 막으려면 같은 트랜잭션이어야 하고, 검증 결과(수락/거절)를 받아야 자동 취소를 정할 수 있다. 결제 모듈은 인터페이스만 알아 도메인을 모르는 원칙은 그대로 |
| 9 | 관리자 역할 | 고정 역할 SUPER_ADMIN·OPERATOR·CS·FINANCE | roboyam 의 **권한 코드(`Permission`) + 직무 역할(합집합)** 에 매핑. 역할명 `OPERATOR` 는 쓰지 않는다 | roboyam 에서 「운영자」는 관리자 앱 계정 전체(`operators`)를 뜻해 이름이 겹친다. `commerce.*` 권한 코드가 이미 예약돼 있다(`featureReady=false`) |
| 10 | 인증 방식 | 토큰 기반(나중 SSO 대비) | **현재 세션 방식 유지**(Spring Session JDBC, `SESSION`·`ADMIN_SESSION`) | 지금 필요가 없고, CSRF·세션 정책이 이미 세션 기준으로 갖춰져 있다. 분리할 때 OIDC 로 붙인다 |
| 11 | 배치 | ShedLock | [확정 2026-10-23] **자체 배치 유지**(Spring Batch·Quartz 안 씀) + 이력·재실행 보강(단계·건별 실패·체크포인트·실패 지점부터 다시·파라미터 실행), 관리자 서버에서 돌되 나중에 별도 배치 프로젝트로 뺄 수 있는 구조, pg_cron 기본·돈/외부 호출은 Java — 15절 | roboyam 은 ShedLock 없이 같은 목적의 장치가 있다(`BatchLockService`) |
| 12 | 배송지 | 평문 스냅샷 | **암호화 저장 · 기본 가림 · 사유를 남기는 열람 · 배송 완료 6개월 뒤 파기** | community 확정 규칙. 개인정보 유출 피해를 줄인다 |
| 13 | 메일 | 알림 채널 하나 | [확정 2026-10-23] 결제·환불·성사·발송 메일은 **유실되지 않는 발송 대기열(outbox) + 재시도 + 발송 기록**. **커머스 메일만** 이 방식으로 하고 기존 메일(비밀번호 재설정 등)은 그대로 둔다 | 지금 roboyam 메일은 커밋 후 스레드 풀이고 대기열이 차면 버린다·재시도 없음. 결제 확인 통지는 법적 의무(전자상거래법 계약 내용 통지)라 버려지면 안 된다 |
| 14 | 관리자 2FA | 전원 필수 | **최고 관리자가 관리자 설정에서 켜고 끈다. 켜면 운영자 전원 필수** + 유예·기기 신뢰·루트 복구 [확정 D8] | 지금 2FA 가 전혀 없다(새로 만든다). community 도 TOTP·운영자 전원 필수로 정했다(`docs/plans/mfa-01-strategy.md`) |
| 15 | 테스트 | Testcontainers 필수 | 원안 시나리오를 **완료 기준**으로 유지, 실행은 QA 요청 때(`CLAUDE.md`). Testcontainers 도입은 의존성 추가라 별도 승인 | 지금 테스트는 1개뿐이고 Testcontainers 의존성이 없다 |

### 1-2. 결정 기록 (모두 확정 2026-10-23)

| ID | 질문 | 추천 | 다른 선택 |
|---|---|---|---|
| ~~D1~~ | 공구 방식 | **확정 2026-10-23**: 둘 다 지원(`min_qty = 0` = 선착순) + 발송 시점 축 분리(`fulfillment`) + 성사형 기간 상한 14일 — 10-1·10-2·10-3 | — |
| ~~D2~~ | 로드맵 순서 | **확정 2026-10-23**: 포인트·포인트 상점 → 2a 결제 모듈 단독 검증 → 2b 공구 → 3 충전(법무 확인 후). 유상 포인트 사용처 = 포인트 상점 품목(2026-10-26: 꾸미기 아이템에서 품목 전부로 넓힘) + 공구 결제 일부 — 2-3 | — |
| ~~D3~~ | 본인인증 범위 | **확정 2026-10-23**: 충전·공구 결제·유상 환불·가입 포인트만 필수, 탈퇴 후 같은 사람 재인증 30일 제한 — 12-1 | — |
| ~~D4~~ | 기존 포인트를 무상 lot 으로 바꿀 때 유효기간 | **확정 2026-10-23**: 전환일부터 1년. 오픈 전(시험 데이터뿐)이라 약관 변경 고지는 필요 없고 약관에 처음부터 「무상 1년」을 넣는다 — 5-4 | — |
| ~~D5~~ | 공구 포인트 결제에 쓸 수 있는 포인트 | **확정 2026-10-23**: 공구별로 `유상만`(기본) / `유상 + 무상`(프로모션) 선택, 무상은 참여 1건당 상한 — 11-2 | — |
| ~~D6~~ | 디지털 아이템 환불 | **확정 2026-10-23**: 어떤 포인트로 샀든 미사용·7일 이내면 회원 셀프 환불 — 7-3 | — |
| ~~D13~~ | 회원 화면의 포인트 표시 | **확정 2026-10-23**: 합계 + 내역(「충전 포인트 / 적립 포인트」, 소멸 예정). 충전을 열기 전에는 「포인트」 하나만 — 2-2 | — |
| ~~D7~~ | 추천 받음 적립 | **확정 2026-10-23**: roboyam 현재 문턱형 유지(새싹보다 높은 등급의 추천만 인정) — 6-2 | — |
| ~~D8~~ | 관리자 2FA | **확정 2026-10-23**: 앱 OTP(TOTP), 켜면 운영자 전원 필수(14일 유예). **켜기·끄기와 값은 관리자 설정(DB)에서 최고 관리자만** — 프로퍼티·환경 변수에 두지 않는다. 기기 신뢰 1~12개월(월 단위, 기본 1개월), 복구 코드 + 서버 초기화 명령 — 13-3 | — |
| ~~D9~~ | 공구 마감 판정 | **확정 2026-10-23**: `CLOSING`(마감 처리 중)을 거쳐 판정 + 선점 시간 기본 5분(설정 3~30분) — 10-2 | — |
| ~~D10~~ | 결제 검증 실패 | **확정 2026-10-23**: 자동 취소를 취소 대기열(`payment_cancels`)로 즉시 시도 + 재시도(최대 5회) + 최종 실패 시 결제 이상 목록·운영자 알림 — 8-2·8-3 | — |
| ~~D11~~ | 성사 뒤·발송 전 취소 | **확정 2026-10-23**: 취소 요청 → 운영자 승인 → 전액 환불(이미 발송했으면 반품으로) [법무] — 10-3·10-5 | — |
| ~~D12~~ | 유상 포인트 유효기간 | **확정 2026-10-23**: 소멸 없음(원안). 돈을 내고 산 포인트가 사라지는 분쟁 자체를 없앤다. 탈퇴 때는 환불 또는 포기 동의(17절), 휴면 전환으로도 지우지 않는다 | — |

---

## 2. 범위 · 용어 · 로드맵

### 2-1. 범위 [확정]
| 구분 | 내용 |
|---|---|
| 이 프로젝트 범위 | 포인트 상점, 현금 충전, 공구 |
| 범위 밖 | 상시 판매형 이커머스(재고·물류·CS·정산·프로모션이 커지면 결제 모듈을 들고 별도 프로젝트로) · 회원 주최 공구(통신판매중개) · 택배사 API 연동 · 장바구니 · 쿠폰 엔진 |

### 2-2. 용어
| 용어 | 코드 | 뜻 |
|---|---|---|
| 포인트 상점 [확정 2026-10-26] | `PointShop`(표 `point_shop_*`, 패키지 `com.community.pointshop`, 권한 `pointshop.*`) | 포인트(적립·충전)로 사는 **디지털 아이템**(닉네임 색·프로필 테두리·닉네임 변경권 등). 산 즉시 지급 → 보유·장착. **「상점」이라고만 쓰지 않는다** — 공동구매도 사는 곳이라 헷갈린다. 공동구매와 메뉴를 묶지 않는다(따로 둔다) |
| 공구 | `GroupBuy` | 공동구매 모집 1건(기간, 최소/최대 수량, 상태). 현금(PG)으로 사는 **실물 상품** — 포인트 상점과 데이터·흐름·법이 달라 따로 둔다(같이 쓰는 것은 포인트 원장·결제 모듈뿐). "캠페인"은 쓰지 않는다 |
| 포인트 표시 [확정 D13] | — | 안에서는 무상/유상을 늘 나눠 관리한다(환불·소멸·회수·회계·법이 다르다). 회원 화면은 **합계를 크게, 아래에 「충전 포인트 N · 적립 포인트 N(그중 N 은 M월 D일 소멸 예정)」**. 충전 기능(3단계)을 열기 전에는 유상이 없으므로 「포인트」 하나만 보인다. 내역 줄에도 어느 쪽에서 나갔는지 표시 |
| 공구 참여 | `GroupBuyParticipation` | 회원이 공구에 넣은 주문 1건 |
| 무상 포인트 | `FREE` | 활동 보상·이벤트·충전 보너스·운영 지급. **회원 화면 이름: 적립 포인트** |
| 유상 포인트 | `PAID` | 현금으로 충전한 포인트. **실제 결제로만 생긴다**(운영자가 만들 수 없다). **회원 화면 이름: 충전 포인트** |
| 결제 주문 | `PaymentOrder` | PG 결제 1건(충전·공구 공용) |
| 운영자 | — | 관리자 앱 계정 전체(`operators`). 원안의 역할 `OPERATOR` 는 쓰지 않고 **공구 기획·주문·배송·고객 지원·정산 직무 역할**로 나눈다(13-1·13-2) [확정 2026-10-23] |
| 회원 ID | `user_id` | roboyam 회원 표는 `users`(Snowflake `bigint`). 원안의 `member_id` 대신 `user_id` 로 쓴다 [개정] |

### 2-3. 로드맵 [확정 D2 — 2026-10-23]
| 단계 | 내용 | 이유 |
|---|---|---|
| 0 | 공통 기반: 본인인증 연결점, 멱등 요청 표, 메일 outbox, 필드 암호화, 운영자 2FA, 기능 제한(제재) | 1~3단계가 모두 기댄다 |
| 1 | 포인트 확장(무상/유상 분리 구조, lot, 소멸) + 포인트 상점 | 유상이 아직 없어도 구조를 먼저 바꿔 둔다 |
| 2a | **결제 모듈 단독**: `payment_orders`·확정 코드·웹훅·취소 워커·READY 정리·대사를 Fake 게이트웨이와 포트원 테스트 채널로 검증 | 원안이 충전을 먼저 둔 이점(결제를 단순한 상태에서 다지기)을 법적 쟁점 없이 얻는다 |
| 2b | 공구(현금 결제) | 일반 통신판매라 법적 경로가 분명하고 직접 매출, community 계약이 상세하다 |
| 3 | 현금 충전 + 유상 포인트 환불 | 선불전자지급수단·포인트 청약철회·보유 한도·탈퇴 처리 등 법무 확인과 탈퇴 기능 설계가 끝난 뒤 |
| 3+ | 공구 포인트 결제(구현, 플래그 꺼짐) | 매출 안정 후 |

- **유상 포인트 사용처 [확정 D2]**: **포인트 상점 품목 전부**(꾸미기 아이템·닉네임 변경권 — 2026-10-26 사용자 확정으로 「꾸미기 아이템」에서 넓힘, 차감은 적립 먼저 → 충전) + 공구 결제 일부(11절, 켤 때). 그 밖의 유료 기능은 지금 범위가 아니다.
- 두 곳 모두 **운영자 자신이 파는 것**이라 충전 포인트는 선불전자지급수단(발행인 외 제3자에게서 사는 데 쓰이는 것)에 해당하지 않는다(20-1 L8). 제3자 상품(회원 주최 공구·입점 판매)에 쓰게 하면 다시 검토한다.

---

## 3. 아키텍처 원칙 (roboyam 에 맞춤)

- **모듈러 모놀리스** [확정] — 다만 roboyam 의 실제 구조로 옮긴다 [개정]:
  - 도메인 코드(엔티티·저장소·서비스·DTO)는 `server/shared` 의 패키지 `com.community.point`(있음) · `com.community.pointshop` · `com.community.payment` · `com.community.groupbuy`. 컨트롤러만 `server/api`(회원)와 `server/admin`(운영자)에 둔다. `admin` 은 모듈이 아니라 앱이다.
  - 마이그레이션은 api·admin 이 함께 쓰므로 **shared 스트림**(2026-10-23 기준 마지막 V56 — 작업 때 다시 확인, 번호 빈 구간과 병행 세션을 확인하고 정한다).
- **의존은 단방향** [확정]: `pointshop → point`, `groupbuy → payment`, `groupbuy → point`, `point`(충전) `→ payment`. `payment` 는 다른 도메인을 모른다.
  - 결제 결과를 도메인에 넘기는 방법 [개정]: `payment` 가 정의한 인터페이스 `PaymentOrderHandler`(`supports(orderType)`, `onPaid(...) → ACCEPT | REJECT(사유)`, `onCanceled(...)`)를 각 도메인이 구현한다. **결제 확정과 같은 트랜잭션**에서 동기로 부른다. 알림·메일만 `AFTER_COMMIT` 이벤트로 보낸다.
  - 커뮤니티 활동 → 포인트 적립 [개정]: 지금처럼 **업무 트랜잭션 안의 직접 호출 + `IsolatedSideWork`(세이브포인트)** 를 유지한다. 이벤트로 바꾸더라도 동기·같은 트랜잭션이어야 한다(`AFTER_COMMIT` 비동기로 바꾸면 커밋 후 서버가 죽을 때 적립이 사라지고 재처리 장치가 따로 필요하다). 분리 계획이 생기면 outbox 로 바꾼다.
- **다른 모듈 표에 FK 를 걸지 않는다** [확정] — 회원은 `user_id` 로만 참조. `payment_orders` 의 대상은 `order_type + order_ref_id`(다형 참조) — 무결성은 애플리케이션과 대사 배치가 지킨다.
- **인증** [개정]: 현재 세션 방식 유지(1-1 #10).
- **공통 규칙**
  - 금액·포인트는 정수(원, P), **1P = 1원** [확정]. 열 타입은 `bigint`(합계 넘침 방지) [추가].
  - 시각은 `timestamptz`(UTC) 저장, 「날짜」 기준(하루 한도·소멸일·청약철회 기한)은 KST [확정]. roboyam 은 KST 상수가 클래스마다 흩어져 있다 — 커머스는 공용 KST 유틸 하나를 쓴다 [추가].
  - **돈·수량·상태는 서버가 정한다** [추가 — community]: 요청은 대상 ID·수량·배송지만 받고 금액을 받지 않는다. 화면 표시용 견적 API 는 따로 두되 주문 때 다시 계산한다.
  - **상태 전이는 한 문장 조건부 UPDATE**(`... WHERE id=? AND status=?`) — 이긴 쪽만 후속 처리 [추가]. 허용되지 않은 전이는 도메인 예외 [확정].
  - **멱등** [확정 2026-10-23]: 원안은 「상태를 바꾸는 모든 API 에 idempotency key」. 지금 공통 장치가 없으므로 두 겹으로 정한다.
    1. **자연 키 유일 제약**(기본): 원장 `(reason, ref_type, ref_id)`(있음), 공구의 활성 참여 `(group_buy_id, user_id)`, 결제 `payment_id`, 웹훅 `webhook-id`, 충전 적립 `CHARGE:{id}` 등.
    2. **클라이언트 멱등 키**(돈이 움직이는 회원 API 에만 — 충전 요청·공구 참여·포인트 상점 구매·환불 신청): 헤더 `Idempotency-Key` → 표 `idempotency_requests(user_id, key, endpoint, request_hash, status, response_code, response_body, created_at)`, 유일 `(user_id, key)`, 같은 키·다른 본문은 `409`, 24시간 보관.

---

## 4. 정책 요약표

| 항목 | 값 | 표기 |
|---|---|---|
| 포인트 차감 순서 | 무상 먼저(만료 임박순, 같으면 오래된 순) → 유상(오래된 충전 순) | [확정] |
| 무상 포인트 유효기간 | 적립일부터 1년 | [확정] |
| 기존 포인트 전환 | 전환일부터 1년짜리 무상 lot 하나 | [확정 D4] |
| 유상 포인트 유효기간 | 소멸 없음(휴면 전환으로도 지우지 않는다) | [확정 D12] |
| 만료된 lot 복원 | 같은 종류 새 lot, 유효기간 30일 | [확정] |
| 운영자 수동 지급 | **무상만**. 1회 ±10,000P(현재 값), 초과(최대 ±100,000P)는 고액 조정 권한 `point.adjust.large` | [확정 2026-10-23] |
| 디지털 아이템 환불 | 미사용(한 번도 장착·사용 안 함) + 구매 후 7일 이내, 어떤 포인트로 샀든, 회원 셀프 환불 | [확정 D6] |
| 유상 포인트 환불 | 충전 건별. 결제일 포함 7일 이내 남은 양 전액 · 이후 10% 공제(원 미만 버림) · 쓴 보너스는 환불액에서 뺀다 · 7일 이내·보너스 미사용은 자동 승인 · 카드 취소 기간이 지난 건은 계좌 송금 | [확정 2026-10-23][법무] |
| 1인 유상 포인트 보유 한도 · 월 충전 한도 | 설정값(기본 **보유 30만P** · **월 10만원, 설정 최대도 10만원**) | [확정 2026-10-23][법무] |
| PG | 포트원 V2 | [확정] |
| 결제 수단 | 카드·간편결제만(가상계좌 제외) | [확정] |
| 공구 결제 | 참여 시 즉시 결제, 불발·중단 시 전액 환불 | [확정] |
| 공구 수량 선점 | **5분**(설정 3~30), 「결제하기」를 누른 순간부터 | [개정 D9 — 원안 10분] |
| 공구 포인트 결제 | 구현, 기본 꺼짐. 결제액의 30%·최소 100P. 공구별 `유상만`(기본) / `유상 + 무상`(프로모션, 무상 참여당 상한) | [확정 D5] |
| 본인인증 | 휴대폰 본인인증, 1인 1계정. 충전·공구 결제·유상 환불·가입 포인트에만 필수. 탈퇴 후 같은 사람 재인증 30일 제한 | [확정 D3] |
| 미성년자 | 만 19세 미만은 충전·공구 결제 불가(본인인증 생년월일 기준) | [확정] |
| 관리자 2FA | 앱 OTP. 최고 관리자가 설정에서 켜고 끔, 켜면 운영자 전원 필수(14일 유예), 기기 신뢰 기본 1개월(1~12개월) | [확정 D8] |
| 판매 스위치 | 공구·충전 각각 켜기/끄기(DB 설정, 설치 직후 꺼짐). 켜기는 최고 관리자만·자동 조건 + 체크리스트 전부 체크 필요, 끄기는 `commerce.settings` 권한자도(20절). **local 은 시드가 조건을 채우고 켜진 상태로 넣는다** | [확정 2026-10-23 — local] |

---

## 5. 포인트 모듈 (기존 V51 확장)

### 5-1. 지금 있는 것 (V51, 고치지 않는다)
- `point_settings`(1행, 14칸), `point_balances(user_id PK, balance ≥ 0)`, `point_ledger(id, user_id, kind EARN|USE|REVOKE, reason, delta, balance_after, note, ref_type POST|COMMENT, ref_id, actor_operator_id, created_at)`, 유일 `(reason, ref_type, ref_id) WHERE ref_id IS NOT NULL`, **UPDATE 를 막는 트리거**(추가만).
- 모든 쓰기는 `PointService` 한 길: 잔액 행 `INSERT … ON CONFLICT DO NOTHING` → `SELECT … FOR UPDATE` 잠금 후 원장 + 잔액.

### 5-2. 새 마이그레이션으로 더하는 것 [확정 2026-10-23 — 지금 표를 넓혀서 쓴다]
- **결정**: 원안처럼 포인트 표를 새로 만들지 않고 V51 표를 넓힌다. 원안과 최종 표 구성은 이름만 다르고 같으며, 지금 roboyam 의 설계 선택이 관리상 더 낫다 — 잔액 행 잠금 하나로 통일(낙관적 락 안 씀), 「사유 + 대상」 유일 규칙(임의 멱등 키 대신), 원장 수정 금지 트리거, 원장 줄마다 이후 잔액, 하루 한도는 원장에서 셈(집계 표 없음), 정책 표·`PointService` 한 길·관리자 화면·API.
- 표 이름은 지금 것(`point_balances`·`point_ledger`)을 유지한다. 오픈 전이라 V51 은 고치지 않고 새 번호 마이그레이션 하나로 목표 모양으로 바꾼다(시험 데이터는 D4 규칙으로 옮김). 넓히면서 군더더기를 남기지 않는다(아래 `balance` 자동 계산 칸).

| 표 | 바뀌는 것 |
|---|---|
| `point_balances` | `free_balance`·`paid_balance`(≥0) 추가, `balance` 는 **DB 가 자동 계산하는 합계 칸**(`GENERATED ALWAYS AS (free_balance + paid_balance) STORED`)으로 바꿔 두 값이 어긋날 수 없게 — 기존 화면·정렬·필터 호환. **`revoke_debt`(≥0, 미회수 포인트) 추가** |
| `point_lots` (신규) | id, user_id, kind(FREE/PAID), original_amount, remaining_amount(CHECK 0 ≤ remaining ≤ original), expires_at(null = 소멸 없음), source_reason, source_ref_type, source_ref_id, restored_from_lot_id(복원 lot), created_at. 색인 `(user_id, kind, expires_at) WHERE remaining_amount > 0` |
| `point_ledger` | `free_delta`·`paid_delta` 추가(합 = `delta`). `kind` 에 `CANCEL_USE`·`EXPIRE`·`REFUND` 추가. `ref_type` 에 `POINT_SHOP_PURCHASE`·`POINT_CHARGE`·`GB_PARTICIPATION`·`LOT` 추가 |
| `point_ledger_lots` (신규, 원안 `point_ledger_detail`) | ledger_id, lot_id, amount(±). 원장 1줄이 어느 lot 에 얼마 반영됐는지 |
| `point_revoke_shortfalls` (신규) | user_id, ref_type, ref_id, requested, applied, created_at — 잔액 부족으로 덜 회수된 양(어뷰징 리포트용) |

- `point_daily_earn`(원안)은 만들지 않는다 [개정]: 지금처럼 그날(KST) 원장 줄을 잔액 행 잠금 아래에서 센다. 하루 **총** 한도도 같은 방식(그날 활동 EARN 합). 느려지면 그때 집계 표를 더한다.

### 5-3. 원장 종류 · 사유
| kind | 부호 | reason(예) | 비고 |
|---|---|---|---|
| EARN | + | POST · COMMENT · LIKE_BONUS · ATTENDANCE · ATTENDANCE_STREAK · SIGNUP · POPULAR · CHARGE(유상) · CHARGE_BONUS(무상) · ADMIN_ADJUST(+) | 기존 사유 유지 |
| USE | − | POINT_SHOP_PURCHASE · GB_POINT_USE | |
| CANCEL_USE | + | POINT_SHOP_REFUND · GB_POINT_RESTORE | 원래 lot 으로 |
| REVOKE | − | POST_REVOKE · COMMENT_REVOKE · LIKE_BONUS_REVOKE · CHARGE_BONUS_REVOKE · ADMIN_ADJUST(−) · REVOKE_DEBT_OFFSET(미회수분 상계) | **무상만** |
| EXPIRE | − | EXPIRE · WITHDRAW_FORFEIT | ref = lot |
| REFUND | − | CHARGE_REFUND | 유상 환불 |
| EARN(복원) | + | CHARGE_REFUND_RESTORE(유상, 원래 lot) · CHARGE_BONUS_RESTORE(무상, 원래 lot — 만료됐으면 30일 새 lot) | 충전 환불의 PG 취소가 최종 실패해 운영자가 「포인트 복원」을 고를 때(9-3) |

### 5-4. 규칙
- 원장은 고치거나 지우지 않는다. 정정은 반대 방향 줄 [확정, 이미 트리거로 강제]. 예외는 보관 기간이 끝난 줄의 파기와 결제 이력이 없는 탈퇴 회원의 무상 원장 정리(17-3)뿐이다.
- `point_balances`·`point_lots`·`point_ledger`·`point_ledger_lots` 는 한 트랜잭션 [확정]. 잠금은 지금처럼 **잔액 행 `FOR UPDATE`, 항상 트랜잭션의 마지막에** [개정 — 낙관적 락은 쓰지 않는다].
- 잔액 부족은 실패, 음수 불가 [확정].
- 차감 순서는 `PointDeductionPolicy` 로 분리 [확정]. 무상: `expires_at` 오름차순 → `created_at`. 유상: `created_at`(FIFO).
- **회수(REVOKE)는 무상만** [확정 2026-10-23]: 적립한 그 lot 에서 먼저, 모자라면 다른 무상 lot(만료 임박순), 그래도 모자라면 0 까지만 한다. **유상 lot 은 회수 대상이 아니다**(돈 주고 산 것을 가져가는 분쟁을 피한다. 어뷰징 회원의 유상 포인트는 회수가 아니라 포인트 동결 + 환불 절차로 다룬다).
- **미회수분은 다음 적립에서 먼저 갚는다** [확정 2026-10-23]: 모자란 양을 `point_balances.revoke_debt` 에 더하고 `point_revoke_shortfalls` 에도 기록(리포트용)한다. 이후 무상 포인트가 들어오면(활동 적립·인기글 보상·운영 지급 — **충전 보너스는 제외**) 같은 트랜잭션에서 EARN 줄 다음에 REVOKE `REVOKE_DEBT_OFFSET` 줄로 빚만큼 빼고 `revoke_debt` 를 줄인다. 잔액은 음수가 되지 않는다. 회원 포인트 화면에 「미회수 N P — 다음 적립에서 차감돼요」를 보인다. 반복하는 회원은 어뷰징 리포트 → 제재.
- `CANCEL_USE` 는 원래 USE 의 `point_ledger_lots` 를 보고 같은 lot 에 되돌린다. 원래 lot 이 만료됐으면 같은 종류의 새 lot(30일) [확정]. 유상 lot 은 만료가 없으므로 늘 원래 lot.
- **운영 조정(ADMIN_ADJUST)은 무상만** [확정 2026-10-23]: +는 무상 lot(1년), −는 무상에서만. 유상은 결제·환불로만 바뀐다.
- 유상 환불(REFUND)은 그 충전 건 유상 lot 의 `remaining_amount` 안에서만 [확정].
- **돈 흐름(USE·CANCEL_USE·REFUND·충전 EARN)은 격리하지 않는다**(`@Transactional(MANDATORY)`, 실패하면 업무도 실패). 활동 적립·회수만 지금처럼 `IsolatedSideWork` [추가].
- 포인트 동결(`POINT_FREEZE`, 13절) 회원은 적립·사용·충전 불가. 환불 신청은 가능(돈을 묶어 두지 않는다) [개정].
- **기존 잔액 전환** [확정 D4 — 2026-10-23]: 마이그레이션이 회원마다 무상 lot 하나(`remaining = balance`, `expires_at = 전환일 + 1년`, `source_reason = MIGRATION`)를 만든다. 오픈 전이라 시험 데이터만 해당하고, 약관에는 처음부터 「무상 포인트 1년」을 넣는다. 원장 줄은 만들지 않는다(원장 합 = 잔액은 이미 성립). 전환 이전 글의 회수는 원래 lot 을 몰라 「다른 무상 lot」 규칙으로 처리한다.

### 5-5. 불변식 [확정 + 보강]
- 회원별 원장 합 = `point_balances.balance` = 남은 lot 합. **종류별로도**(원장 `free_delta` 합 = `free_balance` = 무상 lot 합, 유상도 같다).
- 원장 각 줄의 `point_ledger_lots` 합 = 그 줄의 `delta`(전환 이전 줄 제외).
- 매일 배치가 검사하고 어긋나면 운영자 알림(심각).

---

## 6. 무상 포인트 적립 규칙

### 6-1. 현재 roboyam 설정을 정본으로 [개정 — D7]
지금 `point_settings` 의 칸(글·댓글 포인트와 하루 상한, 추천 보너스 문턱·포인트·하루 횟수, 출석, 인기글 보상, 가입 포인트 등)과 규칙(`docs/features/point-contract.md`)을 그대로 쓴다. 숫자는 설정값이고 원안 숫자는 **기본값 후보**일 뿐이다.

### 6-2. 원안에서 가져와 설정 칸으로 더하는 것 [확정 2026-10-23 — 모두 설정값, 0 = 끔]
- 현재 기본값으로 활동만 하루 최대 약 240P(글 20×5 + 댓글 5×20 + 추천 보너스 10×3 + 출석 10)라 하루 총 한도 200P 는 실제로 걸린다.
- 조건에 걸려도 글·댓글은 그대로 올라가고 포인트만 0 이다. 글자 수는 서식 태그·공백을 뺀 실제 글자로 센다.
- 하루 총 한도에 걸치는 적립은 **남은 만큼만** 준다(예: 190P 에서 글 20P → 10P). 개별 하루 상한(글 5개 등)은 지금처럼 횟수로 센다.
- **게시판별 예외**: 게시판 설정에 「적립 최소 글자 수 적용」 스위치(기본 켬). 사진 게시판처럼 이미지 위주 게시판은 끈다.
| 설정 칸 | 기본 | 뜻 |
|---|---|---|
| `postMinLength` | 50 | 본문(태그 제외 글자) 이 미만이면 적립 없음 |
| `commentMinLength` | 10 | 댓글 글자 수 미만이면 적립 없음 |
| `commentOwnPostExcluded` | true | 자기 글에 단 댓글은 적립 없음 |
| `commentOncePerPost` | true | 같은 글에는 댓글 적립 1회 |
| `writeIntervalSeconds` | 30 | 직전에 **포인트를 받은** 글·댓글과의 간격이 미만이면 적립 없음 |
| `dailyTotalCap` | 200 | 활동 적립 하루 총 한도(글·댓글·추천 보너스·출석 합산, KST). 인기글 보상·운영 조정·충전 보너스·가입 포인트는 제외 |
| `attendanceStreakDays`·`attendanceStreakBonus` | 7 · 20 | 연속 출석 보너스(출석 기능을 만들 때) |
| `signupPoints` | 100 | **본인인증 완료 시 평생 1회**(멱등 `SIGNUP:{user_id}`) |

- 추천 받음은 **문턱형 유지** [확정 D7 — 2026-10-23]: 인정되는 추천은 작성자가 아닌 정상 회원 중 첫 등급(새싹, 기본 승급 조건 = 가입 7일 + 글·댓글 10개)보다 높은 회원의 것뿐이라, 부계정으로 추천을 몰아주려면 부계정마다 그 조건을 채워야 한다. 무상 포인트를 공구 프로모션에 쓸 수 있게 된 만큼(D5) 이 방어가 더 중요하다. 원안의 「본인 추천 불가」는 이미 지켜지고, 「같은 회원 추천은 하루 1회」는 문턱형에서는 같은 글에 한 번뿐이라 별도 규칙이 필요 없다.
- 적립하지 않는 경우 [확정 + roboyam 규칙]: 비회원·정지·탈퇴, 하루 한도 초과, 위 조건 미달, 포인트 동결. 본인인증 전 적립 금지는 **두지 않는다**(D3) — 단 `signupPoints` 만 본인인증 조건.
- 하루 한도는 「지급한 횟수」, 지운 글도 센다(roboyam 현재 규칙).
- **포인트 랭킹은 무료(적립) 포인트 기준** [확정 2026-10-23]: 랭킹을 만들게 되면 기준은 **기간 안에 활동으로 받은 적립 포인트 누적량**(글·댓글·추천 보너스·출석·인기글 보상, 회수분은 뺀다)이다. 보유 잔액(충전 포인트가 섞여 「돈 쓴 순위」가 된다)·**충전 보너스**(돈을 내고 받은 것)·운영 지급은 넣지 않는다. 돈으로 순위·노출을 사지 않는다는 원칙(7-1)과 같다.
- 회수 [확정 + 5-4]: 글·댓글이 삭제되면(본인·운영자·스텝 모두) 받은 양만 무상에서 회수, 0 바닥, 미회수분은 `point_revoke_shortfalls`. 숨김은 회수하지 않는다(roboyam 현재 규칙).

---

## 7. 포인트 상점 (1단계)

### 7-1. 표 [확정 2026-10-23]
| 표 | 주요 열 |
|---|---|
| `point_shop_items` | id, kind(NICKNAME_COLOR·PROFILE_BORDER·TICKET… — community 품목 참고), **usage_type(PERMANENT 영구 기본 / CONSUMABLE 소모형 / PERIOD 기간제)**, code(유일·불변), name, description, price_point, duration_days(기간제만), stock(null=무제한), sale_start_at, sale_end_at, active, sort_order, 종류별 칸(CHECK) |
| `point_shop_purchases` | id, user_id, item_id, **가격·이름 스냅샷**, status(COMPLETED/REFUNDED), refund_notice_agreed_at, ledger_id, created_at, refunded_at |
| `user_items` (원안 `member_inventory`) | id, user_id, item_id, purchase_id, status(OWNED/USED/EXPIRED/REVOKED), starts_at, expires_at(기간제만), **first_used_at**(처음 적용·사용한 시각 — 환불 판정, 지우지 않는다) |
| `user_equipped` | (user_id, slot) PK, user_item_id — 적용(장착)은 상태가 아니라 자리(슬롯) 표로 |

- **아이템 유형** [확정 2026-10-23]: **영구가 기본**(한 번 사면 계속 보유, 이미 가졌으면 다시 살 수 없다). 소모형(교환권 — 쓰면 사라지고 여러 장 살 수 있다)과 기간제(산 날부터 N일 — 기간 한정 이벤트 아이템 등, 품목을 만들 때 고르는 선택지)를 둔다.
- **적용(장착)** = 산 아이템을 지금 내 프로필에 적용해 두는 것(예: 닉네임 색을 여러 개 가져도 닉네임에는 한 색만). 영구·기간제 꾸미기 아이템에 있고, 교환권·이모티콘 팩처럼 가지고 있으면 그냥 쓰는 아이템에는 없다.
- 원안의 `EQUIPPED` 상태는 빼고 자리 표로 바꾼다 [확정]: ① 자리마다 하나만 적용(DB 가 보장) ② 처음 적용·사용한 시각을 남기고 지우지 않아, 적용했다 풀어도 「사용함」이 남는다 — 써 보고 환불하는 것을 막는다(원안 상태 방식은 풀면 OWNED 로 돌아가 이 판정이 깨진다).
- 기간제 아이템을 기간 중에 또 사면 새 줄이 **앞 줄 끝에서 시작**한다(`starts_at` = 앞 줄 `expires_at`). 기간이 끝나면 배치 없이 읽을 때 적용 해제로 본다.
- 운영자가 품목을 고치면: **가격은 산 때 값 그대로**(구매 기록 스냅샷), **색·모양 같은 표시값은 현재 값**을 따른다(색을 고치면 산 사람의 닉네임 색도 바뀐다 — 관리자 화면에 안내).
- 순위·노출에 영향을 주는 아이템, 랜덤박스는 팔지 않는다 [추가 — community 원칙].

### 7-2. 구매 (한 트랜잭션) [확정 + 보강]
1. 자격: 로그인 · 이메일 인증 · 정상 회원 · 회원 권한 `pointshop.use` · 포인트 동결 아님.
2. 판매 기간·`active`·재고 확인, 영구 아이템 중복 확인.
3. 재고 차감 `UPDATE point_shop_items SET stock = stock - 1 WHERE id=? AND stock > 0`(무제한 생략).
4. 포인트 USE(ref `POINT_SHOP_PURCHASE`) — 가격은 서버가 품목에서 읽는다.
5. `point_shop_purchases`·`user_items` 생성. 멱등 키(3절) 사용.

### 7-3. 환불 [확정 D6 — 2026-10-23]
- 조건: `first_used_at` 없음 + 구매 후 7일 이내 + 환불 고지 동의 기록(`refund_notice_agreed_at`). **무상·유상 어느 포인트로 샀든 같은 규칙**(유상 포인트가 생기면 미사용 디지털 콘텐츠는 청약철회 대상일 수 있다 [법무]).
- 「미사용」 정의 — 품목마다 처음 사용한 시각(`first_used_at`)을 남기고, 있으면 환불 불가:

  | 품목 | 미사용 = |
  |---|---|
  | 닉네임 색상·프로필 테두리 | 한 번도 장착하지 않음 |
  | 교환권(닉네임 변경 등) | 쓰지 않음 |
  | 이모티콘 팩 | 팩의 이모티콘을 한 번도 쓰지 않음 |
  | 프로필 아바타 | 프로필로 설정한 적 없음 |
- **영구·기간제 모두 같은 규칙** [확정 2026-10-23]: 쓰지 않았고 7일 이내면 환불, 한 번이라도 적용·사용하거나 7일이 지나면 환불 불가. 기간제 아이템의 기간은 구매 순간부터 흐르지만, 7일 안에 쓰지 않고 환불하면 전액 돌려준다.
- (참고) 「디지털 아이템은 지급 즉시 사용 개시로 보고 사전 고지로 철회를 막는다」는 해석(community 방식 — 환불 불가)도 있으나 법적으로 확실하지 않아, 지금은 위 규칙으로 두고 오픈 전 법무 결과에 따라 바꾼다 [법무].
- 처리(한 트랜잭션): 아이템 REVOKED · 포인트 CANCEL_USE(원래 lot, 만료됐으면 30일 새 lot) · 재고 복원 · 구매 REFUNDED.
- **회원 셀프 환불**: 내 아이템 화면에서 조건에 맞으면 「환불」 버튼, 누르면 서버가 다시 판정해 즉시 처리. 조건 밖은 운영자(권한 `pointshop.refund`)가 예외 처리.
- 구매 확인 대화상자에 「사용(장착)하면 환불할 수 없어요 · 7일 안에 쓰지 않으면 환불할 수 있어요」를 보이고 동의 시각을 남긴다.
- **미리보기 필수** [확인 — 20-1 L6]: 사용 후 환불을 막으려면 철회 불가 사실 표시와 함께 시험 사용(미리보기 등)을 줘야 한다. 꾸미기 아이템은 상점에서 **내 닉네임·프로필에 적용한 모습을 미리 볼 수 있게**, 이모티콘 팩은 팩 안 이모티콘을 미리 보이게 한다. 미리보기를 줄 수 없는 품목은 내용 정보를 충분히 적는다.
- 운영자가 품목 가격·이름을 바꿔도 산 구매는 스냅샷 그대로.

### 7-4. 제외 [확정]
장바구니, 배송, 쿠폰·프로모션 엔진, 선물·양도.

---

## 8. 결제 모듈 (포트원 V2)

### 8-1. 표 [개정]
| 표 | 주요 열 |
|---|---|
| `payment_orders` | id, **payment_id**(포트원 paymentId, 서버 생성 `pay_` + 무작위 32자, 유일), user_id, order_type(POINT_CHARGE/GROUP_BUY), order_ref_id, order_name, amount(서버 계산 청구액), currency, status(READY/PAID/PARTIAL_CANCELED/CANCELED/EXPIRED), **paid_amount**(PG 가 알려 준 실제 결제액), **canceled_amount**(CHECK ≤ paid_amount), anomaly(null/AMOUNT_MISMATCH/DOMAIN_REJECTED/LATE_PAYMENT), pay_method, pg_provider, pg_tx_id, receipt_url, expires_at, paid_at, created_at, updated_at |
| `payment_cancels` | id, payment_order_id, amount, reason(내부), kind(USER_CANCEL/REFUND/AUTO_REJECT/ADMIN/CHARGE_REFUND), status(REQUESTED/SUCCEEDED/FAILED), pg_cancel_id, attempts, next_attempt_at, last_error, requested_by, created_at, completed_at — **모든 PG 취소의 재시도 대기열** |
| `payment_events` | id, payment_order_id, kind, provider_event_id(웹훅 `webhook-id`, 부분 유일), detail(개인정보 없음), created_at |
| `payment_webhook_logs` | id, webhook_id, event_type, target, result(PROCESSED/IGNORED/REJECTED/RETRY), received_at — 90일 보관 [추가 — community] |

- 원안의 `FAILED` 는 `EXPIRED`(선점·유효 시간 안에 결제되지 않음)로 바꾼다 [개정]: 결제창 실패는 참여·충전 상태를 바꾸지 않고 선점 시간 안에 다시 결제할 수 있다.
- **재결제마다 새 `payment_id`** [확인 2026-10-23]: 포트원 문서는 「이미 결제 완료된 paymentId 로 요청하면 실패」만 밝히고 실패한 ID 의 재사용은 보장하지 않는다. 그래서 다시 결제할 때마다 새 결제 주문(`payment_orders`)을 만들고 이전 READY 는 EXPIRED 로 닫는다 — 참여·충전 한 건에 결제 시도가 여러 개일 수 있고 **PAID 는 하나만**(부분 유일 `(order_type, order_ref_id) WHERE status IN ('PAID','PARTIAL_CANCELED')`). 닫은 시도에 늦게 PAID 가 오면 늦은 결제 규칙(8-2 5)대로 자동 취소.
- 원안의 공구 전용 `refund_job` 은 `payment_cancels`(현금)로 합친다 [개정]. 포인트 복원은 내부 일이라 실패할 일이 없으니 도메인 상태 전이와 같은 트랜잭션에서 즉시 한다.

### 8-2. 결제 흐름 [확정 + 보강]
1. 서버가 도메인 주문(충전·참여)과 `payment_orders`(READY)를 같은 트랜잭션에서 만든다. `payment_id`·금액·`expires_at` 은 서버가 정한다.
2. 화면은 응답의 값 그대로 브라우저 SDK `requestPayment` 를 부른다(카드·간편결제만 노출, 금액을 다시 계산하지 않는다).
3. 화면이 완료 API 를 부른다. 서버는 **`payment_id` 가 이 주문의 것인지** 확인하고 포트원 **단건 조회**(`GET /payments/{paymentId}`)로만 판단한다.
4. **확정 코드 하나**(`PaymentConfirmService.confirm`)를 완료 API·웹훅·READY 정리 배치가 함께 쓴다:

| 포트원 조회 결과 | 처리 |
|---|---|
| PAID 아님(READY·PAY_PENDING·FAILED·없음) | 그대로 READY. 완료 API 는 `409 PAYMENT_NOT_PAID`(다시 시도 가능) |
| PAID, 금액·통화 일치 | `READY|EXPIRED → PAID` 조건부 UPDATE, 이긴 쪽만 → 핸들러 `onPaid` |
| ─ 핸들러 ACCEPT | 도메인 확정(포인트 적립·참여 JOINED). 커밋 후 알림 |
| ─ 핸들러 REJECT(공구 마감·자격 상실 등) | `anomaly = DOMAIN_REJECTED` + `payment_cancels`(AUTO_REJECT, 전액) 생성 → 취소 워커 |
| PAID, 금액·통화 불일치 | `anomaly = AMOUNT_MISMATCH`, `paid_amount` 기록, 도메인 확정 없음, 전액 자동 취소 요청 |
| 포트원 연결 실패 | `503`(완료 API) / 웹훅은 `503` 으로 재전송 유도 |

5. **늦은 결제** [확정 2026-10-23 — 원안]: 선점·유효 시간이 지나 EXPIRED 가 된 뒤 PAID 가 오면 **예외 없이 REJECT → 자동 취소**(`anomaly = LATE_PAYMENT`). 숨은 여유 시간도 두지 않는다 — 결제는 5분 안에 끝나는 것이 정상이고, 늦은 것은 회원이 다른 일을 하다 놓친 경우로 본다. 회원 알림: 「결제 시간이 지나 결제를 취소했어요 · 카드사 반영까지 며칠 걸릴 수 있어요」. (community 의 「수량이 남으면 살리기」는 쓰지 않는다.)
6. 웹훅·완료 API 중 먼저 온 쪽이 처리, 나중 것은 조건부 UPDATE 에 져서 같은 결과를 돌려준다 [확정].
7. READY 정리 배치(10분): `expires_at` + 여유 시간이 지난 READY 를 포트원 조회 → PAID 면 5번(늦은 결제 → 자동 취소), 아니면 EXPIRED [확정].

### 8-3. 취소·환불 실행 [추가 — community `DealRefundService` 방식]
- 결제 주문 행 잠금 → `payment_cancels` 를 REQUESTED 로 먼저 남김 → 포트원 `POST /payments/{id}/cancel` 에 `amount` 와 **`currentCancellableAmount`**(이중 취소 방지) 전달 → 성공(`SUCCEEDED`)이면 `canceled_amount` 조건부 증가(`WHERE canceled_amount + :amt <= paid_amount`) · 상태 갱신 · 도메인 `onCanceled`.
- 실패·`REQUESTED`(접수만)는 FAILED 로 두고 지수 백오프(1·5·30·120·720분) 최대 5회 [확정]. 최종 실패는 **결제 이상 목록**(관리자)과 운영자 알림(`COMMERCE_REFUND_*`).
- PG 호출 속도 제한: 워커는 한 번에 설정 개수(기본 20건)·건 사이 간격을 둔다 [확정].

### 8-4. 웹훅 [확정 + 보강]
- **서명 검증이 먼저**(Standard Webhooks: `webhook-id`·`webhook-timestamp`·`webhook-signature`, HMAC-SHA256, 상수 시간 비교, 시각 오차 5분). 실패 → `400`, 아무것도 바꾸지 않고 포트원 조회도 하지 않는다.
- `webhook-id` 는 `payment_events.provider_event_id` 유일로 한 번만.
- `Transaction.Paid` → 본문을 믿지 않고 조회 후 확정 코드. `Transaction.Cancelled`·`PartialCancelled` → 우리가 요청한 취소면 그 줄을 SUCCEEDED 로 맞추고, 아니면(포트원 콘솔 직접 취소) 상태를 바꾸지 않고 기록 + 대사 이슈.
- 우리 결제가 아니거나 모르는 이벤트 → `200`(무시). 결제 전·포트원 연결 실패 → `503`(재전송).

### 8-5. 구현 메모 [확정 + community]
- **구매안전서비스(에스크로)** [확인 — 20-1 L10]: 공구는 결제 뒤 배송하는 선지급식 통신판매라, 신용카드가 아닌 결제(체크카드·계좌 기반 간편결제 등)는 회원이 고르면 결제대금예치를 제공해야 한다. PG 의 구매안전서비스를 계약해 적용하고, 주문서에 「결제대금예치 이용을 선택할 수 있어요」를 고지한다(신용카드·상점·충전은 제외). 통신판매업 신고에도 구매안전서비스 이용 확인증이 필요하다. **에스크로 결제 건은 발송 처리 때 택배사·송장을 PG 에 등록**(포트원 `POST /payments/{paymentId}/escrow/logistics`, 수정 `PATCH`)하고, 구매 확정은 포트원 `escrow/complete` 로 알린다(22절). 에스크로를 적용할 결제 수단 범위는 채널 설정으로 도입 때 확인.
- `PaymentGateway` 인터페이스(조회·취소·서명 검증) 아래 포트원 구현과 Fake. **dev·prod 에서 Fake 면 기동을 막는다.** 본인인증도 같다 — 인터페이스 아래 포트원 구현과 가짜 구현, dev·prod 에서 가짜면 기동을 막는다(12-1).
- 설정(환경 변수): API Secret·Webhook Secret(비밀), Store ID·Channel Key(공개 값). 비어 있어도 앱은 뜨고 결제 시작만 `503`.
- 승인/매입 분리(「승인 전 거절」)는 채널 지원 여부를 도입 때 포트원 문서로 확인 [확정].
- 간편결제 중 비동기 결제(`PAY_PENDING`)·가상계좌는 다루지 않는다(PAID 만 확정).

### 8-6. 도메인별 결제 검증 (`onPaid`)
| order_type | 검증 |
|---|---|
| POINT_CHARGE | 충전 상품 스냅샷 금액 일치, 충전 건 PENDING, 회원 자격(본인인증·19세·동결 아님)·보유/월 한도 |
| GROUP_BUY | 공구 `RECRUITING` 또는 `CLOSING`(선점이 마감 전에 잡힌 건), 참여 `PENDING_PAYMENT` 이고 선점 시간 안, 금액 = 참여 현금액 |

---

## 9. 포인트 충전 (3단계 — D2)

### 9-1. 표
| 표 | 주요 열 |
|---|---|
| `point_charge_products` | id, price(원), paid_point, bonus_point(무상), active, sort_order — 가격 ≠ 유상 포인트 가능(보너스는 무상으로만) |
| `point_charges` | id, user_id, product_id, **상품 스냅샷**(price·paid_point·bonus_point), payment_order_id, status(PENDING/COMPLETED/EXPIRED/REFUND_REQUESTED/REFUNDED), paid_lot_id, bonus_lot_id, completed_at, created_at |
| `point_charge_refunds` | id, charge_id(유일 — 충전 건당 한 번), paid_remaining, fee, bonus_used, refund_amount, **method(PG_CANCEL / BANK_TRANSFER)**, status(REQUESTED/APPROVED/DONE/FAILED/REJECTED), payment_cancel_id, **bank_name·account_no_enc·holder_enc**(계좌 송금일 때만, 송금 완료 후 지움), transferred_at·transferred_by, requested_at, handled_by, handled_at |

### 9-0. 한도와 상품 [확정 2026-10-23 — 모두 설정값]
| 한도 | 기본 | 기준 |
|---|---|---|
| 1인 충전 포인트 보유 한도 | **30만P**(월 한도 3개월치) [확정 2026-10-23] | 지금 가진 충전(유상) 포인트 합. 적립 포인트는 넣지 않는다. 쓰는 곳 기준(꾸미기 아이템 500~2,000P, 공구 포인트 결제 30% — 100만원 공구여도 30만P)으로 충분하다. 갚을 잔액·사고 규모를 작게 유지하려는 **자체 정책**이다(법정 보유 한도는 전자금융업자 대상이라 우리에게 적용되지 않는다 — 20-1 L9) |
| 월 충전 한도 | **10만원**(설정 범위도 최대 10만원) [확정 2026-10-23] | 이번 달(KST 1일~말일) 결제가 확정된 충전 금액 합. 환불해도 줄지 않는다. 커뮤니티 포인트에 큰 금액은 필요 없다 |
- 근거: 커뮤니티 포인트에 필요한 만큼만 둔다 — 도용 카드 피해·대량 환불 규모 제한. 충전 포인트는 운영자 자신의 상품에만 쓰이므로 선불전자지급수단이 아니고 법정 보유 한도도 적용되지 않는다(20-1 L8·L9).
- **제약(고정)**: 충전 포인트는 **운영자가 직접 파는 것(포인트 상점·운영자 직판 공구)에만** 쓴다. 회원 주최 공구·입점 판매 등 제3자 상품에 쓰게 하면 선불전자지급수단 등록 문제가 생긴다(L8).
- 넘는 충전 상품은 버튼을 비활성화하고 「이번 달 N원 더 충전할 수 있어요」. 충전 요청 때와 결제 확정(`onPaid`) 때 두 번 확인하고, 동시 충전으로 확정 때 넘으면 REJECT → 자동 취소.
- 충전 상품은 운영자(`pointshop.manage`)가 관리. 예: 10,000원 → 충전 10,000P / 50,000원 → 충전 50,000P + 적립 보너스 2,500P(보너스는 적립 포인트, 1년).

### 9-2. 흐름 [확정]
1. 자격 확인(본인인증·만 19세 이상·동결 아님·보유/월 한도) → `point_charges`(PENDING) + `payment_orders`(POINT_CHARGE, 30분).
2. 8-2 결제 흐름.
3. `onPaid` ACCEPT → 같은 트랜잭션에서 유상 lot(소멸 없음) EARN `CHARGE` + 무상 lot(1년) EARN `CHARGE_BONUS`, 멱등 `CHARGE:{id}` → COMPLETED.
4. 커밋 후 알림·메일(결제 확인 통지).

### 9-3. 환불(청약철회·약관 환불) [확정 2026-10-23]
- 단위: **충전 건별**, 그 충전 건 유상 lot 의 남은 양(`paid_remaining`) 전부를 한 번에.
- `fee` = 결제일 포함 7일째 자정(KST)까지면 0, 지나면 `floor(paid_remaining × 10%)` [법무].
- `bonus_used` = 그 충전 건 보너스 lot 에서 USE 로 나간 양 − CANCEL_USE 로 돌아온 양(만료로 사라진 양은 「사용」이 아니다).
- `refund_amount = max(0, paid_remaining − fee − bonus_used)`.
- 예(50,000원 → 충전 50,000P + 보너스 2,500P): 3일째 안 씀 → 50,000원 · 3일째 3,000P 씀(적립 먼저라 보너스 2,500 + 충전 500) → 49,500 − 2,500 = 47,000원 · 10일째 안 씀 → 45,000원. 남은 보너스는 모두 회수.
- **환불 방법**: 원래 결제 수단으로 PG 부분/전액 취소. **PG·카드사 취소 가능 기간이 지난 충전 건**(충전 포인트는 소멸이 없어 오래된 건의 환불이 올 수 있다 — 기간은 PG 계약 때 확인)은 **회원 계좌로 송금**: 그 건만 신청 화면에서 은행·계좌번호·예금주를 받아 암호화 저장, 운영자가 송금 후 「송금 완료」 처리(감사), 완료 뒤 계좌 정보 삭제.
- 처리 순서(돈이 두 번 나가지 않게):
  1. 신청(회원) → 7일 이내·보너스 미사용이면 자동 승인, 그 밖은 운영자 승인(`point.refund.approve`). 고액 문턱(기본 100,000원) 이상은 고액 환불 권한 `point.refund.large` 가 필요하다.
  2. 승인 트랜잭션: 유상 lot 남은 양 REFUND(−), 보너스 lot 남은 양 REVOKE(`CHARGE_BONUS_REVOKE`), 충전 `REFUND_REQUESTED`, `payment_cancels`(CHARGE_REFUND, `refund_amount`) 생성.
  3. 취소 워커가 PG 부분/전액 취소 → 성공 시 REFUNDED. 계좌 송금 건은 운영자의 「송금 완료」로 REFUNDED.
  4. 최종 실패 시 포인트는 **되돌리지 않고** 결제 이상 목록에서 운영자가 재시도 또는 「포인트 복원」(5-3 의 `CHARGE_REFUND_RESTORE`·`CHARGE_BONUS_RESTORE` 줄)을 고른다.
- **자동 승인과 고액 문턱의 우선순위** [확정 2026-10-23]: 7일 이내·보너스 미사용 건은 **금액과 관계없이 자동 승인**한다 — 청약철회는 법정 권리이고 환급 기한이 3영업일이라(20-1 L4) 승인 대기로 늦추지 않는다. 고액 문턱(`point.refund.large`)은 운영자 승인이 필요한 건(7일 이후·보너스 사용)에만 적용한다. 문턱은 공구와 같은 100,000원으로 **그대로 둔다** [확정 2026-10-23] — 월 충전 한도(10만원)와 7일 이후 10% 공제 때문에 지금 값으로는 충전 환불에 거의 걸리지 않는다(일반 승인 권한 `point.refund.approve` 로 처리된다). 한도를 올리게 되면 다시 본다.
- `refund_amount = 0` 이어도 포인트 정리는 한다(회원에게 미리 계산을 보여 주고 동의를 받는다).

---

## 10. 공구 (2단계 — D2)

### 10-1. 표 [개정]
| 표 | 주요 열 |
|---|---|
| `group_buys` | id, title, description, product_name, images, normal_price, group_price, **shipping_policy_code**(배송비 정책) + 모집 시작 때 고정한 사본 **shipping_fee, free_shipping_over, return_shipping_fee, return_fee_mode** [확정 2026-10-23], min_qty(**0 = 선착순**, D1), **fulfillment(IMMEDIATE 즉시 발송 / AFTER_CLOSE 마감 후 발주, CHECK `fulfillment = 'AFTER_CLOSE' OR min_qty = 0`)**, max_qty, max_qty_per_user, current_qty, reserved_qty, start_at, end_at, **extension_mode(DEMAND 기본 / ALWAYS), extended_until(null = 연장 안 됨)** [확정 2026-10-23], status, host_type(OPERATOR만), point_payment_allowed(false), allowed_point_kinds(PAID/PAID_AND_FREE, 기본 PAID), free_point_cap_per_participation(null), return_policy(반품 배송비 규칙 문구), version, created_by, created_at, updated_at. CHECK `0 ≤ current_qty`, `0 ≤ reserved_qty`, `current_qty + reserved_qty ≤ max_qty` |
| `shipping_policies` [확정 2026-10-23] | id, code(정책 이름 키, 예 `STANDARD`), name, base_fee, free_shipping_over(null = 없음), return_fee, **return_fee_mode(ONE_WAY 편도 기본 / ROUND_TRIP 왕복)**, effective_from, effective_to(null = 계속), created_by, created_at. 같은 code 의 기간은 겹치지 않는다(배제 제약). **적용이 시작된 줄은 고치거나 지우지 않는다**(트리거) — 바꿀 때는 새 줄을 더한다 |
| `group_buy_options` | id, group_buy_id, name, extra_price, max_qty(null), **current_qty, reserved_qty** [추가 — 옵션 한도를 지키려면 옵션 단위 수량도 필요] |
| `group_buy_participations` | id, group_buy_id, option_id, user_id, qty, **unit_price·item_amount·shipping_fee**(스냅샷 — 반품 배송비·무료배송 기준·편도/왕복은 공구가 모집 시작 때 고정한 사본을 쓴다), **group_buy_revision**(결제 때의 상세 버전, 10-2) [추가], amount(= 상품 + 배송비), point_amount, cash_amount, status, payment_order_id, reserved_until, **배송지 암호문**(recipient_enc·phone_enc·address1_enc·address2_enc·memo_enc, postcode 평문), address_masked_at, cash_receipt_type·cash_receipt_no_enc, ordered_at·shipped_at·delivered_at, refunded_amount, created_at. 부분 유일 `(group_buy_id, user_id) WHERE status IN (활성 상태)` |
| `group_buy_requests` [추가] | 취소·반품 요청: id, participation_id, type(CANCEL/RETURN), reason_code(CHANGE_OF_MIND/DEFECT/WRONG_ITEM/OTHER), reason_text, photos, status(REQUESTED/APPROVED/COMPLETED/REJECTED/WITHDRAWN), **return_qty**(부분 반품 수량), **collection_method(COMPANY_PICKUP 업체회수 / CUSTOMER_SEND 고객발송)**, **pickup_carrier·pickup_tracking_no**(업체회수 송장), **customer_shipping_compensation**(고객발송 + 업체 부담 때 보상액) [확정 2026-10-23], refund_amount, return_shipping_fee, admin_note, handled_by, handled_at, created_at. 참여당 열린 요청 1개 |
| `group_buy_qty_movements` [추가] | group_buy_id, option_id, participation_id, kind(RESERVE/RELEASE/CONFIRM/CANCEL/ADJUST), qty, current_after, reserved_after, actor, created_at — **수량을 바꾸는 모든 길이 한 줄**(community `commerce_stock_movements`) |
| `group_buy_shipments` | participation_id, carrier_code, tracking_no, status, shipped_at, delivered_at |
| `commerce_carriers` [추가] | code, name, tracking_url_template, active, sort_order |
| `group_buy_watches` | group_buy_id, user_id, **kind(CLOSING_SOON 마감 임박 / RESTOCK 수량 풀림)** [확정 2026-10-23], last_notified_at, created_at. 유일 `(group_buy_id, user_id, kind)` |
| `group_buy_status_history` · `participation_status_history` | 원안 그대로 [확정] |
| `commerce_address_views` [추가] | participation_id, operator_id, reason, viewed_at, expires_at — 배송지 열람 기록 |

### 10-2. 공구 상태 [확정 D9 — 2026-10-23]
```
DRAFT → RECRUITING → CLOSING → CLOSED_SUCCESS → ORDERED → SHIPPING → COMPLETED
                                             ↘ COMPLETED (fulfillment = IMMEDIATE — 참여가 모두 끝나면, 공구 단위 발주·배송 단계 없음)
                             ↘ CLOSED_FAILED  (current_qty < min_qty → 전액 환불)
RECRUITING / CLOSING / CLOSED_SUCCESS / ORDERED → CANCELED (운영 중단·발주 실패·품절 → 전액 환불)
```
- **마감 자동 연장** [확정 2026-10-23]: 선점이 결제 없이 만료돼 수량이 마감 무렵에 다시 풀리면, 품절을 보고 돌아선 회원이 억울하게 못 사는 일이 생긴다. 그래서 `end_at` 에 아래 조건이면 모집(`RECRUITING`)을 **최대 30분**(설정값) 더 이어 간다(`extended_until = end_at + 30분`). 연장 중에는 새 선점·구매가 모두 되고, **수량이 다 팔리면(확정 기준 `current_qty = max_qty`) 그 자리에서 종료**(→ `CLOSING`). 연장은 원래 마감에서 한 번, 합계 최대 30분이다.

  | 공구별 `extension_mode` | 연장하는 경우 |
  |---|---|
  | `DEMAND`(기본) | 마감 시각에 결제 중인 선점이 남아 있거나, 마감 전 5분 안에 선점이 만료돼 수량이 풀렸을 때 — 사려는 사람이 있었던 경우만 |
  | `ALWAYS`(특별한 경우 — 운영자가 고름) | 마감 시각에 수량이 남아 있으면 무조건 |
  - 연장 방식은 모집 전(`DRAFT`)에 정하고 모집 시작 뒤에는 바꾸지 않는다(10-2 「모집 시작 뒤 기간 수정 불가」와 같은 이유). 공구 상세에 처음부터 「마감 무렵 수량이 풀리면 최대 30분 연장될 수 있어요」를 표시하고, 연장되면 「마감 연장 · 남은 수량 판매 중 · 최대 10:30까지」로 바꾼다.
  - 연장이 끝나면(또는 다 팔리면) 아래 `CLOSING` → 판정으로 이어진다.
- **수량 풀림 알림** [확정 2026-10-23]: 수량이 없을 때(품절 표시) 공구 상세에 「수량이 풀리면 알림 받기」 버튼(`group_buy_watches.kind = RESTOCK`). 선점 만료로 수량이 생기면 신청자에게 앱 알림을 보낸다 — 같은 회원에게 공구당 10분에 한 번까지(`last_notified_at`), 받는 순서는 정하지 않는다(먼저 사는 사람이 산다). 구매하면 신청은 끝난다.
- `CLOSING`: `end_at`(연장됐으면 `extended_until`) 도달 또는 `current_qty = max_qty`(조기 마감). 새 선점 금지, 남은 `PENDING_PAYMENT` 의 결제는 받는다. **남은 선점이 0 이 되거나 「CLOSING 에 들어간 시각(마감 시각, 연장됐으면 `extended_until`, 조기 마감이면 그 시각) + 선점 시간 + 여유(2분)」이 지나면** 판정(기본값이면 늦어도 마감 7분 뒤). 예: 10시 마감, 9:58 선점 → 10:02 결제 완료는 수락되어 수량에 들어간다. 회원 화면은 그동안 「마감됐어요 · 결과 집계 중」. 판정 이후에 도착한 결제는 자동 취소(P3b).
- 판정: `current_qty ≥ min_qty` 면 성사(JOINED → CONFIRMED 일괄), 아니면 불발. `min_qty = 0` 이면 늘 성사.
- 모집 시작 뒤에는 가격·수량·기간·옵션·배송비를 바꿀 수 없다. 설명·이미지만 수정 [확정]. 오탈자 수준이 아닌 변경은 CANCELED 후 재등록.
- **공구 상세(표시·광고) 수정 이력을 6개월 보관**한다 — 설명·이미지를 고칠 때마다 이전 내용을 `group_buy_revisions`(공구, 버전, 제목·설명·이미지 목록 사본, 고친 운영자, 시각)에 남긴다(전자상거래법 시행령 제6조: 표시·광고 기록 6개월 — 20-1 L14). 참여는 결제 때의 버전 번호를 기록한다.
- 공구 기간 상한: 설정(기본 **성사형 14일 [확정 D1]**·선착순 30일) — 성사형은 결제금을 오래 묶고 불발 시 대량 환불이 생긴다.
- **배송비 정책과 예약 변경** [확정 2026-10-23]:
  - 배송비는 공구에 직접 입력하지 않고 `shipping_policies`(정책 이름별·기간별 줄)에서 고른다. 인상이 예정되면 운영자가 **적용 시작일을 정해 새 줄을 미리 등록**하고, 그날부터 새로 모집을 시작하는 공구에 자동으로 쓰인다. 공구 상세·목록에 「YYYY년 M월 D일부터 배송비 N원」 예정 안내를 띄울 수 있다.
  - 이력: 적용이 시작된 줄은 불변(새 줄로만 바꾼다) + 감사 `COMMERCE_SHIPPING_POLICY_*`(전→후). 관리 권한 `commerce.settings`.
  - **진행 중 공구는 모집 시작 시점의 배송비로 고정**: `DRAFT → RECRUITING` 전이 때 그 시각에 유효한 정책 줄의 값을 공구에 복사하고(`shipping_fee`·`free_shipping_over`·`return_shipping_fee`·`return_fee_mode`), 끝날 때까지 바꾸지 않는다. 같은 공구 안에서는 모두 같은 배송비. 인상분이 부담되면 새 공구로 다시 연다.
  - 참여(주문)에는 배송비를 다시 복사해 두고(10-1), 반품 배송비·무료배송 기준·편도/왕복은 공구의 고정 사본을 쓴다 — 공구 안에서 바뀌지 않으므로 결제·환불 계산은 늘 회원이 살 때 본 금액이다.
- **공구 방식 두 축 [확정 D1]** — 성사 조건(`min_qty`)과 발송 시점(`fulfillment`)은 따로 고른다:

  | | 즉시 발송 `IMMEDIATE`(재고 보유) | 마감 후 발주 `AFTER_CLOSE` |
  |---|---|---|
  | 성사형 `min_qty > 0` | 불가(성사 전에 보낼 수 없다) | 원안: 성사 → 발주 → 일괄 발송 |
  | 선착순 `min_qty = 0` | community 특가: 결제 확정 즉시 참여 CONFIRMED → 건별 발송, 공구의 ORDERED·SHIPPING 단계 없음(마감되면 바로 COMPLETED 쪽으로) | 예약 판매: 최소 수량 없이 모인 만큼 마감 후 발주 |
- 성사형 모집 중 본인 취소는 허용한다(마감 직전 취소로 불발될 수 있다 — 막으면 청약철회와 부딪힌다).
- 카드 취소 시 PG 수수료 환급 여부는 PG 계약 조건에 따른다 — 성사형 불발 비용과 직결되므로 계약 때 확인 [확인 필요].

### 10-3. 참여 상태 [개정]
```
PENDING_PAYMENT → JOINED → CONFIRMED → PREPARING → SHIPPED → DELIVERED
PENDING_PAYMENT → EXPIRED   (선점 만료 — 결제창 실패는 만료 전까지 재시도 가능)
JOINED → CANCELED            (RECRUITING 중 본인 취소 — 즉시 전액)
CONFIRMED / PREPARING → CANCELED (발송 전 취소 — 아래 표, 전액, D11)
JOINED / CONFIRMED / PREPARING → REFUNDED (불발·중단 — 발주 실패·품절 포함)
DELIVERED → (반품 요청 승인·회수 확인) → REFUNDED(전액) 또는 DELIVERED + refunded_amount(부분)
```
- 반품·취소 「요청」은 상태가 아니라 `group_buy_requests` 로 다룬다(요청 철회·거절이 참여 상태를 흔들지 않게) [개정 — community].
- 발주(ORDERED)는 공구 단위, 준비중·발송·배송완료는 참여 단위. `PREPARING`(준비중)은 운영자가 포장을 시작했다는 표시다(일괄 처리 가능).
- **발송 전 취소 [확정 D11 — 2026-10-23]** — 발송 전 취소는 배송비가 들지 않으므로 늘 전액 환불:

  | 시점 | 성사형(`AFTER_CLOSE`) | 선착순 즉시 발송(`IMMEDIATE`) |
  |---|---|---|
  | 모집 중 / 결제 직후 | 본인 **즉시 취소**(JOINED) | 준비중 전까지 본인 **즉시 취소**(CONFIRMED) |
  | 성사 후 ~ 발송 전 | **취소 요청 → 운영자 승인**(CONFIRMED·PREPARING) | 준비중 이후 **취소 요청 → 운영자 승인** |
  | 발송 후 | 반품 요청(배송완료 후 7일) | 같음 |
  - 요청 단계는 운영자가 이미 포장·발송했는지 확인하려는 것이다. 운영자는 「이미 발송함」(송장 입력)으로만 거절할 수 있고, 그때 회원은 반품 흐름으로 간다. 열린 취소 요청이 있는 참여는 발송 처리할 수 없다(먼저 요청을 처리).
  - 성사형에서 발주 뒤 취소된 수량은 운영자 재고로 남는다(법적으로 막기 어려운 비용). 공구 상세에 「발주 후 취소는 승인 후 환불, 처리에 며칠 걸릴 수 있어요」 안내.
  - 원안의 「마감 뒤 취소 불가, 받은 뒤 반품만」은 받기 전 청약철회를 막는 것으로 볼 여지가 있어 바꿨다 [법무].
- `fulfillment = IMMEDIATE`(선착순·재고 보유) 공구는 결제 확정 때 `PENDING_PAYMENT → JOINED → CONFIRMED` 를 한 번에 거쳐 바로 발송할 수 있다(공구 마감·발주를 기다리지 않는다). 이때 회원 본인 취소는 위 「발송 전 취소」 표를 따른다(준비중 전 즉시, 이후 요청).

### 10-4. 수량 관리 [확정 + 옵션 보강]
- 선점(참여 요청 트랜잭션):
  ```sql
  UPDATE group_buys SET reserved_qty = reserved_qty + :qty
   WHERE id = :id AND status = 'RECRUITING'
     AND current_qty + reserved_qty + :qty <= max_qty
  ```
  옵션에 한도가 있으면 같은 트랜잭션에서 옵션 행도 같은 식으로. 영향 행 0 이면 실패. 이동 줄 RESERVE.
- 1인 한도·중복 참여는 같은 트랜잭션에서 `(공구, 회원)` 어드바이저리 잠금 아래 검사 [추가 — community]. 한도는 취소·환불 수량을 뺀다.
- 결제 확정: `reserved -= qty, current += qty`(CONFIRM). 만료·실패: `reserved -= qty`(RELEASE). 본인 취소·발송 전 취소: `current -= qty`(CANCEL) — **성사 뒤 취소로 `current_qty` 가 `min_qty` 아래로 내려가도 성사는 뒤집지 않는다**.
- 선점 만료를 배치에만 기대지 않는다: 참여·견적 판정 때 `reserved_until` 이 지난 PENDING 은 만료로 보고 그 자리에서 정리한다 [추가].

### 10-5. 참여 정책
| 항목 | 값 | 표기 |
|---|---|---|
| 중복 참여 | 공구당 활성 참여 1건. 수량 변경은 취소 후 재참여 | [확정] |
| 1인 한도 | `max_qty_per_user` | [확정] |
| 모집 중 본인 취소 | 허용(수량 반환 + 전액 결제 취소) | [확정] |
| 성사 뒤·발송 전 취소 | 취소 요청 → 운영자 승인 → 전액 환불(10-3 표) | [확정 D11][법무] |
| 선점 시간 | 5분(설정 3~30). 주문서 작성 중에는 선점하지 않고 「결제하기」를 누를 때 시작 — 결제창 시간만 덮는다(결제는 보통 1~3분, 한 번 실패 후 재시도 여유) | [개정 D9] |
| 선점 반복 이탈 | 24시간 안 3회 만료 → 24시간 공구 참여 제한(`GROUP_BUY_BAN`, 자동) | [확정] |
| 배송지 수정 | 발주(ORDERED) 전까지, 회원 본인·CS | [확정] |
| 청약철회 | **단순 변심**: 배송완료(운영자가 바꾼 날)부터 7일, 이미 낸 처음 배송비는 돌려주지 않고 반품 배송비를 뺀다(업체회수 — 고객발송이면 반품비 없음). 무료배송 전체 반품은 **편도(기본)면 면제된 처음 배송비를 받지 않고, 왕복이면 받는다**. 부분 반품은 남은 수량 기준 배송비 재계산. 전체 규칙은 10-8 기준 표. **하자·오배송(표시·계약과 다름)**: 배송완료부터 **3개월**, 회원이 안 날부터 30일 안, 전액·반품 배송비 운영자 부담(전자상거래법 제17조 ③·제18조 ⑩ — 20-1 L2). **환불은 반환받은 날(발송 전 취소는 취소한 날)부터 3영업일 안**(L4) | [확정 2026-10-23] |
| 참여 자격 | 로그인 · 정상 회원 · 회원 권한 `commerce.purchase` · 본인인증 · 만 19세 이상 · 공구 제한 없음 · 공구 판매 스위치 켜짐 | [개정] |
| 정지 회원 | 새 참여만 막고 진행 중 주문(결제 확정·취소·반품·조회)은 된다 | [추가 — community] |

### 10-6. 참여 흐름 (현금 결제) [확정 + 보강]
1. 요청 본문: 공구 ID·옵션·수량·배송지·현금영수증 선택·동의 3종(구매 조건·환불 안내·개인정보). **금액은 받지 않는다**.
2. 자격·한도 → 선점 → 금액 계산(`unit × qty`, 배송비·조건부 무료) → 배송지 암호화 → 참여 PENDING_PAYMENT(`reserved_until` = 지금 + 선점 시간 5분) → `payment_orders`(GROUP_BUY, `cash_amount`).
3. 8-2 결제 흐름. 확정되면 JOINED(`IMMEDIATE` 공구는 CONFIRMED 까지 한 번에 — 10-3) + 수량 확정 + 알림·메일(주문 확인 = 계약 내용 통지).
4. 선점 만료 → 참여 EXPIRED + RELEASE + **연결된 `payment_orders` READY → EXPIRED(같은 트랜잭션)** + 이탈 횟수 + (사용자가 직접 취소한 경우가 아니면) 알림. 그 뒤 PAID 가 오면 EXPIRED → PAID 로 바뀐 뒤 `anomaly = LATE_PAYMENT` 로 자동 취소된다(8-2 5).

### 10-7. 마감 처리 [개정 — D9]
- 1분 주기: `RECRUITING` 이고 ① `end_at` 이 지났으면 연장 조건(10-2)을 보고 `extended_until` 을 정하거나 `CLOSING` 으로 ② 연장 중이면 `extended_until` 이 지났을 때 `CLOSING` 으로 ③ `current_qty = max_qty` 이면 바로 `CLOSING` 으로. `CLOSING` 이고 판정 조건(10-2)이 된 공구 → 판정.
- 불발·중단: 활성 참여마다 같은 트랜잭션에서 참여 REFUNDED + 포인트 복원(있으면) + `payment_cancels`(전액) 생성. 현금은 취소 워커가 처리.
- 판정·전이는 조건부 UPDATE 라 두 번 돌아도 한 번만 [확정].

### 10-8. 성사 이후 예외 [확정 + 보강]
- 발주 실패·품절: 공구 CANCELED → 전액 환불(10-7 재사용).
- 반품 [확정 2026-10-23]: 요청(사유 선택, 요청 화면에 **환불 예정 금액을 미리** 표시) → 승인(회수 안내) → 회수 확인 후 완료(환불 실행). 부분 환불은 `payment_cancels` 한 줄씩, 누적이 결제액과 같아지면 REFUNDED. 사진은 하자·오배송만 최대 3장.
- 환불 금액 계산(서버 한 곳 `GroupBuyRefundCalculator`, 요청 화면과 승인 화면이 같은 값):
  - **기준 표** [확정 2026-10-23 — 실제 운영 중인 이커머스 반품 배송비 표(회수 방식 × 부담 주체 × 전체/부분 × 처음 배송비 유형)를 따른다]. 세 가지를 보고 정한다:
    - **부담 주체**: 고객(단순 변심) / 업체(하자·오배송).
    - **회수 방식**(`group_buy_requests.collection_method`): `COMPANY_PICKUP` **업체회수**(우리가 택배 회수를 접수 — 반품 택배비는 우리가 먼저 낸다) / `CUSTOMER_SEND` **고객발송**(고객이 직접 택배로 보낸다 — 택배비를 고객이 직접 낸다).
    - **처음 배송비 유형**: 낸 주문 / 무료배송 주문. 「반품 배송비」 = 정책의 `return_fee`, 「처음 배송비」 = 정책의 `base_fee`(공구 고정 사본).

    | 처음 배송비 | 회수 | 부담 | 전체/부분 | 반품 배송비 차감 | 처음 배송비 처리 |
    |---|---|---|---|---|---|
    | 낸 주문 | 업체회수 | 고객 | 전체·부분 | 뺀다 | **돌려주지 않는다** |
    | 낸 주문 | 업체회수 | 업체 | 전체 | 없음 | 돌려준다 |
    | 낸 주문 | 업체회수 | 업체 | 부분 | 없음 | 그대로(남은 상품이 쓴다) |
    | 낸 주문 | 고객발송 | 고객 | 전체·부분 | 없음(고객이 이미 냄) | 돌려주지 않는다 |
    | 낸 주문 | 고객발송 | 업체 | 전체 | 없음 + **고객이 낸 택배비 보상** | 돌려준다 |
    | 낸 주문 | 고객발송 | 업체 | 부분 | 없음 + **고객이 낸 택배비 보상** | 그대로 |
    | 무료배송 | 업체회수 | 고객 | 전체 | 뺀다 | **편도(기본): 받지 않는다 / 왕복: 받는다** |
    | 무료배송 | 업체회수 | 고객 | 부분 | 뺀다 | 남은 상품 기준 재계산(기준 아래면 받는다) |
    | 무료배송 | 업체회수 | 업체 | 전체·부분 | 없음 | 받지 않는다 |
    | 무료배송 | 고객발송 | 고객 | 전체 | 없음 | 받지 않는다(편도·왕복 모두) |
    | 무료배송 | 고객발송 | 고객 | 부분 | 없음 | 남은 상품 기준 재계산(기준 아래면 받는다) |
    | 무료배송 | 고객발송 | 업체 | 전체·부분 | 없음 + **고객이 낸 택배비 보상** | 받지 않는다 |

  - **배송비를 낸 주문의 단순 변심**: 이미 낸 처음 배송비는 돌려주지 않고 반품 배송비만 추가로 받는다 — 편도·왕복과 관계없다(실무 확인: 「고객 변심이면 반품비만 받는다」는 이미 낸 처음 배송비 위에 반품비만 더 받는다는 뜻).
    - 예(업체회수): 39,000 + 3,000 = 42,000 결제 → 42,000 − 3,000(처음, 돌려주지 않음) − 3,000(반품) = **36,000**. 고객발송이면 42,000 − 3,000(처음) = **39,000**.
  - **무료배송 주문의 단순 변심 · 전체 반품(업체회수)** — 배송비 정책의 `return_fee_mode`:
    - **`ONE_WAY` 편도(기본)**: 반품 배송비만 뺀다 — 50,000 − 3,000 = **47,000**. 면제된 처음 배송비를 반품 때 받는 것은 법에 명시된 허용 규정이 없어(위약금으로 볼 여지 — 20-2 1번) 기본으로 둔다.
    - **`ROUND_TRIP` 왕복(선택)**: 면제된 처음 배송비도 받는다 — 50,000 − 3,000 − 3,000 = **44,000**. **금액까지 미리 고지한 공구에만** 쓴다. 관리자 배송비 정책 화면에서 왕복을 고르면 「법에 명시된 허용 규정은 없어요 · 반품 비용을 금액까지 미리 알려야 해요」 경고를 보인다. 실제 운영 표가 이 방식이다.
    - 고객발송이면 편도·왕복 모두 처음 배송비를 받지 않고 반품비도 빼지 않는다 — **50,000 전액**(고객이 택배비를 직접 냈다).
  - **단순 변심 · 부분 반품**(2개 중 1개) — 무료배송 주문은 **남은 수량 기준으로 배송비를 다시 계산**한다: 남은 상품 금액(단가 × 남은 수량, 옵션 추가금 포함)이 무료배송 기준 이상이면 0, 아래로 떨어지면 처음 배송비를 **남은 상품의 배송비로 부과**해 환불액에서 뺀다(회수 방식·편도/왕복과 관계없다). 업체회수면 반품 배송비도 따로 뺀다.
    - 예(개당 25,000 · 기준 50,000 · 배송비 3,000 · 반품비 3,000, 업체회수): 2개 무료배송 중 1개 반품 → 남은 25,000 < 기준 → 25,000 − 3,000(재부과) − 3,000 = **19,000** / 3개(75,000) 중 1개 반품 → 남은 50,000 ≥ 기준 → 25,000 − 3,000 = **22,000**. 고객발송이면 각각 22,000 / 25,000.
    - 실제로 받아 가는 상품에 대한 배송비라 위약금 성격이 아니다. 운영자가 승인할 때 최종 금액을 확정(남은 결제액 이하).
  - **하자·오배송(업체 부담)**: 반품비·재계산 없이 해당 상품 금액을 돌려주고, 전체 반품이면 낸 처음 배송비도 돌려준다. **신청 기한은 배송완료부터 3개월·안 날부터 30일**(L2).
    - **고객발송이었으면 고객이 낸 택배비를 보상**한다: 운영자가 영수증 등을 확인해 **보상액**(`group_buy_requests.customer_shipping_compensation`)을 입력하고 — **상한은 관리자가 커머스 설정에서 지정한다**(권한 `commerce.settings`, 기본 10,000원, 바꿀 때 감사 전→후 — 실수로 큰 금액을 넣는 것을 막는 장치) [확정 2026-10-23] — 환불할 때 함께 돌려준다 — 보상액을 더한 환불액이 남은 결제액 안이면 PG 취소에 함께 담고, 넘는 부분(예: 전액 환불 + 보상)은 PG 로 돌려줄 수 없으므로 계좌 송금(9-3 과 같은 방식, 송금 후 계좌 정보 삭제). 실무 표의 「수기 처리」를 시스템 기록·감사로 남기는 것이다.
  - **회수 방식 정하기**: 회원이 반품 요청할 때 고른다(기본 업체회수). 업체회수면 운영자가 승인 때 택배 회수를 접수한다(택배사 회수 API 연동은 범위 밖 — 운영자가 택배사에 접수하고 회수 송장을 입력). 고객발송이면 회원에게 반품 주소·「택배비는 직접 내고, 하자·오배송이면 보상해 드려요」 안내.
  - 공구 상세·주문서·반품 요청 화면에 반품 비용을 **금액까지** 미리 적는다(시행령 제20조 1호, 20-1 L3). 예: 편도 「단순 변심 반품 배송비 3,000원(무료배송 주문도 같음) · 이미 낸 배송비는 돌려드리지 않아요 · 일부만 반품해 무료배송 기준(50,000원) 아래가 되면 배송비 3,000원을 환불액에서 빼요 · 직접 보내시면 반품 배송비는 빼지 않아요」 / 왕복 「무료배송 주문을 단순 변심으로 모두 반품하면 왕복 배송비 6,000원(처음 3,000원 + 반품 3,000원)을 빼요」.
  - **쿠폰 사용 주문(후속 메모)**: 쿠폰 기능은 지금 범위 밖이다(7-4·2-1). 무료배송 쿠폰을 만들게 되면 실제 운영 표를 따른다 — 쿠폰으로 면제된 배송비는 반품 때 다시 계산하지 않고, 업체회수·고객 부담이면 반품 배송비만 빼며, **업체 부담 전체 반품이면 반품 완료 때 쿠폰을 돌려준다**(고객발송이면 고객이 낸 택배비 보상은 위와 같다).
- 반품 배송비: 공구가 고른 배송비 정책의 `return_fee`(기본 3,000원)를 모집 시작 때 고정한다(10-2). 단순 변심은 회원 부담, 하자·오배송은 운영자 부담 — 공구 상세에 금액 표시 [확정].
- **처리 기한 3영업일** [확인 — 20-1 L4]: 발송 전 취소는 취소 요청한 날, 반품은 상품을 돌려받은 날부터 3영업일 안에 환불(PG 취소 요청)까지 마친다. 늦으면 연 15% 지연배상금이 붙는다. 그래서 취소·반품 요청이 **2영업일 동안 처리되지 않으면 운영자 알림**(경고), 3영업일째는 심각(19절에 더함). PG 취소가 재시도 중이어도 「취소 요청을 PG 에 보낸 시점」을 기록해 기한 준수를 보인다.

### 10-9. 배송지 · 개인정보 [추가 — community]
- 저장은 AES-256-GCM 암호문(키 버전 접두사). **암호화 키는 DB 비밀번호용 `ROBOYAM_CRYPTO_KEY` 와 따로 둔다** [확정 2026-10-23] — 새 환경 변수(예: `ROBOYAM_DATA_KEY`), 한 키가 새도 피해가 나뉘고 교체 주기를 따로 가져갈 수 있다. 설치 스크립트가 새 키를 만들어 주고, 설치 안내에 **「키를 잃으면 저장된 배송지·환불 계좌를 영영 읽을 수 없다 — 백업 필수」** 를 넣는다. 암호문마다 키 버전을 붙여 키를 교체할 수 있게 한다(새 키로 다시 암호화하는 배치). 같은 키를 환불 계좌(9-3)·현금영수증 번호에도 쓴다. 키가 없으면 참여·계좌 환불 신청만 `503`.
- 화면·목록·로그·감사 detail·알림에 평문을 싣지 않는다. 기본 표시는 가린 값(`홍*동`, `010-****-5678`, 주소 앞 두 어절).
- **회원 본인** [확정 2026-10-23]: 주문 화면도 가린 값으로 보이고, 본인이 「전체 보기」를 누르면 표시한다. 발주 전까지는 본인이 수정할 수 있다.
- 운영자 열람 [확정 2026-10-23]: 권한 `commerce.address.reveal` + 사유 5~200자 → 열람 기록·감사를 같은 트랜잭션에서 남긴 뒤에만 값을 준다. **10분**(설정값) 뒤 화면이 다시 가린다. 주문 상세에 열람 기록(누가·언제·사유).
- **배송 명단 내보내기**(발주·택배 접수용, 원안 「참여자 엑셀」) [개정]: 권한 `commerce.export` + `commerce.address.reveal` 둘 다, 사유 입력, `ORDERED` 공구만, 배송에 필요한 칸만, 감사에 건수·사유(평문 없음). 매출용 CSV 에는 개인정보 열이 없다.
- 배송 완료 6개월(취소는 취소 후 6개월) 지나면 암호문을 지우고 `address_masked_at` 기록 [확정 2026-10-23]. 주문 금액·상태·날짜 같은 거래 기록은 법정 기간 동안 남긴다. 열린 요청이 있으면 미룬다. 기간은 설정·[법무].

### 10-10. 주최자 [확정]
1차는 `host_type = OPERATOR` 만. 회원 주최는 정산·판매자 검증·분쟁 처리가 붙으므로 범위 밖(열만 둔다).

---

## 11. 공구 포인트 결제 (구현, 기본 꺼짐)

### 11-1. 켜지는 조건 [확정]
전역 설정 `groupbuy.point-payment.enabled = false`(기본) **그리고** 공구별 `point_payment_allowed = false`(기본).

### 11-2. 규칙
| 항목 | 값 | 표기 |
|---|---|---|
| 한도 | 결제 금액의 최대 30% | [확정] |
| 최소 사용 | 100P | [확정] |
| 쓸 수 있는 포인트 | 공구별 `allowed_point_kinds`: `PAID`(기본) / `PAID_AND_FREE`(프로모션) | [확정 D5] |
| 무상 사용 상한 | 공구별 `free_point_cap_per_participation`(예: 3,000P). `PAID_AND_FREE` 일 때만 | [확정 D5] |
| 전액 포인트 결제 | 한도상 불가. 한도를 100% 로 바꾸면 PG 없이 바로 JOINED | [확정] |

- `amount = point_amount + cash_amount`, `payment_orders.amount = cash_amount` [확정].
- 무상을 허용한 공구도 차감 순서는 「무상 먼저」, 단 무상은 상한까지만 쓰고 나머지는 유상 [확정 D5].
- **무상 포인트 공구 사용은 상시가 아니라 프로모션으로 켠다** [확정 D5]: 무상 사용분은 할인(운영자 부담)이라 공구·기간 단위로 비용을 통제하고, 무상 포인트가 늘 현금처럼 쓰여 소멸·회수가 분쟁이 되는 것을 피한다. 다계정 위험은 크지 않다 — 포인트는 선물·양도가 없고 공구 결제는 본인인증(1인 1계정)이 필수라, 공구에 쓰이는 무상 포인트는 인증한 그 사람이 자기 계정에서 하루 총 한도 안에서 모은 것뿐이다.

### 11-3. 흐름 [확정]
1. 선점 + 포인트 USE(ref `GB_PARTICIPATION`)를 한 트랜잭션.
2. 현금 결제(8-2).
3. 만료·이탈·REJECT: 선점 해제 + 포인트 CANCEL_USE 를 한 트랜잭션.

### 11-4. 환불 [확정]
- 전액: 현금은 `payment_cancels`, 포인트는 CANCEL_USE(만료 lot 은 새 lot 30일).
- 부분: **현금 먼저** 환불하고 남는 금액만 포인트로 복원.

### 11-5. 회계 [확정]
무상 사용분은 할인, 유상 사용분은 선수금 대체. `point_ledger_lots` 의 lot 종류로 집계한다. 도입 때 한도 비율을 다시 본다.

---

## 12. 자격 · 본인인증 · 어뷰징 · 기능 제한

### 12-1. 본인인증 [확정 D3 — 2026-10-23]
- 포트원 통합 본인인증(휴대폰). 결과(`verifiedCustomer`)로 CI·DI·이름·생년월일·성별·통신사를 받는다. **DI 는 다날·KCP 는 항상 주고 KG이니시스는 주지 않는다** → 본인인증 PG 는 다날 또는 KCP 로 고른다(22절, 포트원 문서 확인 2026-10-23).
  - **CI**(연계정보): 인증기관이 주민번호를 바탕으로 만든 한 사람당 하나뿐인 값. 어느 사이트에서 인증해도 같다 → 1인 1계정의 근거.
  - **DI**(중복가입확인정보): 사이트(가맹점)마다 다르게 만들어진다고 알려진 값. 우리 사이트 안 중복 확인에는 DI 만으로 충분할 수 있고 노출 위험이 더 작다 → 포트원 응답에 있으면 **DI 를 중복 확인 키로**, 없으면 CI 해시.
- 저장: `user_identity`(user_id PK, identity_hash(DI 또는 CI 의 HMAC, 유일), birth_date, verified_at, provider_tx_id). **CI·DI 원문·이름·휴대폰 번호는 저장하지 않는다**(필요해지면 암호문). 이미 다른 계정에 묶인 값이면 거절(1인 1계정).
- 필요한 곳: 충전, 공구 참여, 유상 포인트 환불 신청, 가입 포인트 지급. 그 밖의 활동은 이메일 인증 + 정상 회원(지금 규칙).
- 인증 시점: 첫 충전·첫 공구 참여 직전에 인증 화면을 띄운다. 설정에서 미리 할 수도 있다.
- 본인인증도 인터페이스 아래 포트원 구현과 가짜 구현(local·시험 전용)을 둔다. **dev·prod 에서 가짜 구현이면 기동을 막는다**(결제 Fake 와 같다 — 8-5).
- 만 19세 판정: 결제 시점에 생년월일로 계산(만 나이). 19세 생일이 지나면 다시 인증하지 않아도 된다.
- **탈퇴 후 같은 사람 재인증 30일 제한**(설정값): 탈퇴할 때 `identity_hash` 와 탈퇴 시각만 별도 표(`identity_withdrawals` — 이메일·소셜 계정 재가입 제한과 같은 표, 17-2)에 남기고 30일 뒤 배치가 지운다. 탈퇴·재가입으로 가입 포인트·공구 참여 제한을 되풀이하지 못하게. [법무 — 개인정보처리방침에 보관 근거·기간]

### 12-2. 어뷰징 대응 [확정 + 조정]
| 위협 | 대응 |
|---|---|
| 다중 계정 | 현금 관련은 본인인증 1인 1계정. 포인트 선물·양도 없음 → 다른 계정에서 모은 포인트는 공구에 쓸 길이 없다. 무상 공구 사용은 프로모션·상한으로만(11-2) |
| 글·댓글 파밍 | 최소 글자 수, 같은 글 댓글 1회, 작성 간격, 하루 총 한도(6-2) |
| 추천 주고받기 | 문턱형 + 「인정된 추천」(첫 등급인 새싹보다 높은 등급의 정상 회원만 — 6-2, roboyam 현재 규칙) |
| 공구 선점 반복 이탈 | 24시간 3회 → 24시간 참여 제한 |
| 미성년자 결제 | 본인인증 생년월일, 19세 미만 충전·공구 결제 불가 |
| 결제 사기·도용 | 회원당 1분 요청 속도 제한(12-4) [확정 2026-10-23] |

### 12-3. 기능 제한 [확정 2026-10-23]
- 계정 전체를 정지하지 않고 **특정 기능만 막는 제재**다. roboyam 에는 아직 제재 표가 없으므로(`users.status` 의 SUSPENDED/BANNED 만) **커머스 0단계(공통 기반)에서 기능 제한 표를 먼저 만든다**: `member_restrictions(id, user_id, type(POINT_FREEZE/GROUP_BUY_BAN/CHARGE_BAN), reason(회원에게 공개), admin_memo(비공개), starts_at, until(null=무기한), source(AUTO/OPERATOR), created_by, lifted_by, lifted_at, created_at)`. 나중에 제재 기능(경고·정지·이의제기)을 만들 때 같은 관리 화면에서 함께 다루고 이의제기 흐름도 같이 쓴다.

  | 종류 | 막는 것 | 그래도 되는 것 |
  |---|---|---|
  | `POINT_FREEZE` 포인트 동결 | 포인트 적립·사용·충전 | **충전 포인트 환불 신청**(회원 돈을 묶어 두지 않는다) |
  | `GROUP_BUY_BAN` 공구 참여 제한 | 새 공구 참여 | 이미 결제한 주문의 배송·취소·반품·조회 |
  | `CHARGE_BAN` 충전 제한 | 새 충전 | 기존 포인트 사용·환불 |
- 부여: **자동** — 24시간 안에 선점 3번 만료 → 24시간 `GROUP_BUY_BAN`(10-5). **수동** — `sanction.manage` 권한 운영자가 사유·기간(또는 무기한)을 정해 부여·해제. 모두 감사.
- 회원: 걸리면 알림(어떤 기능·언제까지·사유 — 운영 메모는 싣지 않는다). 막힌 기능을 쓰려 하면 「공구 참여가 제한돼 있어요 · 10월 25일 14:00까지」 같은 안내.
- 계정 정지(쓰기 전체 차단)와 기능 제한은 따로 걸 수 있다.

### 12-4. 돈 관련 요청 속도 제한 [확정 2026-10-23]
| 요청 | 한도(회원별 1분) |
|---|---|
| 공구 참여(수량 선점) | 5번 |
| 충전 요청 | 3번 |
| 포인트 상점 구매 | 10번 |
| 환불 신청(아이템·충전·반품) | 3번 |
- **DB 로 센다**(서버가 여러 대여도 전체 합으로 정확히): 표 `request_rate_counters(user_id, action, window_start(분 단위), count)`, 요청마다 `INSERT … ON CONFLICT (user_id, action, window_start) DO UPDATE SET count = count + 1 RETURNING count` 한 문장. 한도를 넘으면 `429` + 몇 초 뒤 다시 할 수 있는지(`Retry-After`).
  - 세는 쓰기는 업무 트랜잭션과 **따로**(먼저, 별도 커밋) 한다 — 업무가 실패해 되돌아가도 시도 횟수는 남아야 반복 시도를 막는다.
  - 표는 `UNLOGGED`(기록 로그를 남기지 않아 빠름 — DB 가 비정상 종료되면 비워지지만 1분짜리 카운터라 문제없다). 지난 창은 pg_cron 정리 배치가 지운다(15-5).
  - 1분 고정 창이라 창 경계에서 잠깐 최대 2배까지 몰릴 수 있다 — 남용 방지용 1차 방어이고, 진짜 한도(월 충전·1인 수량·중복 참여·같은 요청 번호)는 각 도메인 규칙이 지킨다.
- **값은 관리자 화면에서 바꾼다**(프로퍼티 아님): 커머스 설정의 「요청 제한」 구획, 요청 종류별 1~100(0 = 제한 없음), 권한 `commerce.settings`, 저장 시 감사(전→후). 서버들은 설정 사본을 최대 15초 안에 따라온다(`PointPolicy` 와 같은 방식).
- 제한하지 않는 것: 결제 완료 확인(같은 결제를 여러 번 확인해도 결과가 같다), PG 웹훅(서명 검증으로 보호).
- 기존 서버별 속도 제한(글 이미지·소셜 로그인 등)은 이번 범위가 아니라 그대로 둔다.
- 일일 리포트: 적립 상위, 회수 미달 누적(`point_revoke_shortfalls`), 3일 연속 하루 총 한도 도달 [확정].

---

## 13. 관리자

### 13-1. 원안 역할 → roboyam 직무 역할 [개정]
역할 이름을 코드에 하드코딩하지 않는다 [확정]. 아래는 **시드 역할 후보**이고 실제 권한은 역할에 준 권한 코드의 합집합이다(`docs/domain/operator-permissions.md`).

| 원안 | roboyam 시드 역할 후보 (역할 유형) |
|---|---|
| SUPER_ADMIN | 루트 계정(역할 아님) 또는 「종합 관리」 역할 |
| OPERATOR | 「공구 기획」(상품·공구 기획) + 「주문·배송」 |
| CS | 「고객 지원」 — 주문·배송 조회·송장·배송지 수정, 회원 조회, 제재 |
| FINANCE | 「정산·증빙」 + 환불·포인트 조정 권한 |

### 13-2. 권한표 → 권한 코드 [개정]
`✓` = 이미 `Permission` 에 있음(대부분 `featureReady=false`), `+` = 새로 더할 후보.

| 기능 | 권한 코드 | 원안 허용 역할 |
|---|---|---|
| 공구 등록·모집 시작·조기 마감 | ✓ `commerce.product.manage` | SUPER·OPERATOR |
| 가격·배송비 입력(DRAFT) | ✓ `commerce.product.price` | SUPER·OPERATOR |
| 공구 중단(전액 환불 발생) | + `commerce.groupbuy.cancel` (SENSITIVE) | SUPER·FINANCE |
| 발주 처리·발송·송장 | ✓ `commerce.order.manage` | SUPER·OPERATOR·CS |
| 수량 조정 | ✓ `commerce.stock.adjust` | — |
| 배송지 열람·수정(발주 전) | ✓ `commerce.address.reveal` | SUPER·CS |
| 배송 명단 내보내기 | ✓ `commerce.export` + ✓ `commerce.address.reveal` | SUPER·OPERATOR |
| 공구 환불(전액·부분)·재처리 | ✓ `commerce.refund.approve` | SUPER·FINANCE |
| 공구 설정·사업자 정보·배송비 정책·**판매 끄기** | ✓ `commerce.settings` | SUPER |
| **판매 켜기** | 루트만(권한 코드 없음, 서비스가 막음) | SUPER |
| 매출·정산 조회 | ✓ `commerce.settlement.read` | SUPER·FINANCE |
| 결제 주문 조회 | + `commerce.payment.read` | 전원 |
| 대사 불일치 처리 | + `commerce.reconcile.manage` (SENSITIVE) | SUPER·FINANCE |
| 포인트 상점 품목·충전 상품 관리 | + `pointshop.read`·`pointshop.manage` (등급·포인트 유형) | SUPER·OPERATOR |
| 포인트 상점 구매 예외 환불 | + `pointshop.refund` (SENSITIVE) | SUPER·FINANCE |
| 회원 원장 조회 | ✓ `point.read` | 전원 |
| 포인트 수동 지급·회수(무상만) | ✓ `point.adjust` | SUPER·FINANCE |
| 1회 ±10,000P 초과 조정(최대 ±100,000P) | + `point.adjust.large` (SENSITIVE) | SUPER |
| 고액 공구 환불(1회 100,000원 이상) | + `commerce.refund.large` (SENSITIVE) | SUPER |
| 고액 충전 포인트 환불(1회 100,000원 이상) | + `point.refund.large` (SENSITIVE) | SUPER |
| 유상 포인트 환불 승인 | + `point.refund.approve` (SENSITIVE) | SUPER·FINANCE |
| 회원 기능 제한 | ✓ `sanction.manage` | SUPER·CS |
| 탈퇴 회원 연락용 이메일 열람(17-2) | + `member.withdrawn.contact` (SENSITIVE) | SUPER·CS |
| 운영자·역할 관리 | ✓ `operator.manage`·`role.manage`·`role.grant` | SUPER |

- **고액 처리는 「최고 관리자만」이 아니라 별도 권한으로** [확정 2026-10-23]: 문턱을 넘는 일은 그 일의 고액 권한(`point.adjust.large`·`commerce.refund.large`·`point.refund.large`)을 가진 운영자가 처리한다. 최고 관리자는 늘 모든 권한을 가지므로 할 수 있고, 다른 운영자에게는 역할로 준다. 고액 권한이 없는 운영자는 승인 대기로 올려 두고 권한자에게 넘긴다. 문턱 금액은 설정값(포인트 조정 ±10,000P, 환불 100,000원, 0 = 모든 건이 고액). 원안 「5만P 초과는 SUPER 만」을 이 방식으로 바꿨다.
- **예외 — 최고 관리자만** [확정 2026-10-23]: 사이트 전체 스위치인 **판매 켜기**(20절)와 **관리자 2차 인증 켜기/끄기**(13-3)는 권한으로 위임하지 않는다.
- **공구 중단**(참여자 전원 전액 환불): `commerce.groupbuy.cancel` 권한자. 금액이 커도 고액 권한을 따로 요구하지 않는다(품절·발주 실패처럼 급히 멈춰야 할 때 기다리지 않게). 확인 창에 「참여 N명 · 총 환불 N원」을 보이고 사유 필수, 감사·운영자 알림.
- **시드 역할**(최고 관리자가 운영자에게 나눠 줌): 공구 기획(상품·공구 만들기·수정) · 주문·배송(발주·발송·송장·반품) · 고객 지원(주문 조회·배송지 수정·회원 제재) · 정산·증빙(매출·환불·포인트 조정·결제 불일치). 고액 권한은 시드 역할에 넣지 않고 최고 관리자가 골라 준다.
- 검사는 roboyam 방식 그대로: 컨트롤러 `@RequirePermission`(선언 없으면 거절), 요청 내용에 따른 세부 검사는 서비스 `AdminActor.can()` [개정 — 원안 `@PreAuthorize` 대신].
### 13-3. 관리자 2차 인증 [확정 D8 — 2026-10-23]
- **방식**: 앱 OTP(TOTP, RFC 6238 — 구글 OTP·Microsoft Authenticator 등 아무 앱). SMS·우회 코드는 없다.
- **켜고 끄기**: 관리자 앱 설정(「로그인 보안」, DB 설정 행)에서 **최고 관리자(루트)만** 바꾼다. **프로퍼티·환경 변수로 두지 않는다**(환경마다 강제하지 않는다 — local 개발 편의는 local 시드가 꺼진 값으로 넣는 것으로 처리). 다른 운영자에게 위임하는 권한 코드는 만들지 않는다.
  - 켤 때: 루트 본인이 OTP 를 먼저 등록해 둬야 켤 수 있다(잠김 방지). 켠 시각부터 유예가 시작된다.
  - 끌 때: 확인 대화상자 + 감사(`MFA_SETTINGS_UPDATE`, 전→후) + 운영자 알림(심각도 높음).
- **대상**: 켜져 있으면 **운영자 전원 필수**. 아직 등록하지 않은 운영자는 **유예 14일**(설정값) 동안 로그인할 때마다 등록 안내, 유예가 끝나면 로그인 직후 강제 등록 화면.
- **기기 신뢰**: 「이 기기에서 다시 묻지 않기」 허용. 기간은 최고 관리자가 **월 단위 1~12개월** 중 설정, **기본 1개월(30일)**. 비밀번호 변경·OTP 재등록·계정 잠금 때 그 운영자의 신뢰 기기를 모두 끊는다.
- **복구**: 등록 때 복구 코드 10개(한 번씩, 해시 저장). 루트가 폰·복구 코드를 모두 잃으면 **서버 접근자만 쓰는 초기화 명령**(설치 스크립트 쪽)으로 그 계정의 OTP 를 지우고 감사에 남긴다. 다른 운영자의 OTP 초기화는 최고 관리자가 화면에서 한다(감사).
- **값의 위치**: 사용 여부·유예 일수·기기 신뢰 개월·실패 한도는 모두 같은 설정 화면, 바꿀 때마다 감사에 전→후.
- **처음 값**: 설치 직후 **꺼짐** [확정 2026-10-23] — 최고 관리자가 준비되면 켠다(켜는 순간 14일 유예 시작). local 시드도 꺼짐. 자동 테스트용으로 local 시드 운영자에 고정 OTP 비밀키를 둘 수 있다(local 시드 파일에만).
- 구현 참고: community `docs/plans/mfa-01-strategy.md`·`mfa-02-plan.md`(비밀번호 확인 뒤 세션을 바로 만들지 않고 「2차 대기」로 두는 자리, 표 구성).

### 13-4. 감사 [개정]
- 기존 `audit_logs`(actor·action·result·target·ip·request_id·detail JSON ≤ 4000자)를 쓴다. 원안의 `before`·`after` 열 대신 **detail 에 바뀐 칸만 `{before, after}`** 로 넣는다(개인정보·평문 배송지·키 없음).
- 돈이 움직이는 일(환불·조정·대사 처리·충전 환불 승인)과 열람·내보내기는 **서비스가 직접** 남긴다(실패도). 나머지는 인터셉터.
- **개인정보 접속기록** [확인 — 20-1 L16]: 안전성 확보조치 기준의 접속기록 대상은 「개인정보처리시스템에 접속한 기록」 전체다. 그래서 펼쳐 보기·내려받기뿐 아니라 **관리자 앱에서 개인정보가 보이는 화면(회원 목록·상세, 주문 목록·상세 등)의 조회도** 누가·언제·무엇을(대상)·어디서(IP) 남기고 2년 보관한다. 다운로드 점검 주기·방법은 내부 관리계획으로 정하고(운영 문서), 접속기록 표는 수정·삭제를 막는다(감사 원장처럼).
- **보관 기간** [확정 2026-10-23]: 지금은 전부 365일 뒤 지운다(pg_cron `audit-log-retention`). 감사 줄을 남길 때 행동마다 보관 분류를 붙이고, 같은 정리 배치가 분류별 기간으로 지운다.

  | 분류 | 예 | 보관 | 하한 |
  |---|---|---|---|
  | 일반 운영 | 설정 변경, 게시판·메뉴 관리 | 1년(지금 그대로) | — |
  | 개인정보 열람·내려받기 | 배송지 열람, 배송 명단 내려받기, 회원 이메일 전체 보기(`MEMBER_VIEW` emailRevealed) | **2년** | 1년 |
  | 돈 관련 | 환불 실행, 포인트 수동 조정, 대사 불일치 처리, 판매 켜기/끄기, 충전 환불 승인·송금 완료 | **5년** | 5년 |
  - 개인정보 열람은 개인정보 안전성 확보조치 기준의 접속기록 보관(최소 1년, 규모가 크면 2년 — 법무 확인), 돈 관련은 거래 기록 보관(5년)과 맞춘다. 법정 보관의 본체는 결제·주문·환불 표 자체이고, 감사는 「누가 했나」를 함께 확인하려는 것이다.
  - 기간은 관리자 화면에서 바꾸되 **하한보다 짧게는 못 줄인다**. 바꾸면 감사(전→후).

### 13-5. 관리자 화면 [확정 + 보강]
- 공구: 목록·등록·상태 전환·참여자(상태 필터)·발주·발송(일괄 부분 성공, 행 단위 결과)·수량 변동 내역·선점 현황
- 요청·환불: 취소·반품 요청 대기열, 결제 이상 목록(자동 취소 실패·금액 불일치·늦은 결제), 재처리
- 포인트: 회원 원장(무상/유상 구분), 수동 지급·회수, 충전 환불 승인, 어뷰징 리포트, 기능 제한
- 포인트 상점: 품목·충전 상품·구매 내역·예외 환불
- 결제: 결제 주문 조회, 웹훅 수신 내역, PG 설정 상태(값은 안 보이고 설정 여부만), 대사 불일치
- 설정: 판매 스위치·사업자 정보·택배사·오픈 체크리스트

---

## 14. 알림

| 이벤트 | 대상 | 채널 | 표기 |
|---|---|---|---|
| 공구 참여 완료(결제 확인) | 참여자 | 앱 + **메일(필수, 계약 내용 통지 — 판매자·상품·가격과 지급·배송 방법과 시기·청약철회 기한과 방법·환불 조건·분쟁 처리·약관 링크·결제대금예치 선택 안내를 담는다, 20-1 L12)** | [확인] |
| 마감 임박(24시간 전) | 관심 신청자 | 앱 | [확정] |
| 수량 풀림(품절 중 선점 만료) | 「수량 풀림 알림」 신청자 | 앱(공구당 10분에 한 번) | [확정 2026-10-23] |
| 마감 연장 | 수량 풀림·마감 임박 신청자 | 앱 | [확정 2026-10-23] |
| 공구 성사 / 불발·중단 + 환불 진행(카드사 반영 기간 안내) | 참여자 | 앱 + 메일 | [확정] |
| 환불 완료(부분 포함) | 회원 | 앱 + 메일(돈이 돌아가는 일이라 메일 대기열로 — 1-1 #13) | [확정 2026-10-23] |
| 발송(택배사·송장) / 배송완료(청약철회 7일 안내) | 참여자 | 앱 + 메일 | [확정 + 추가] |
| 선점 만료 | 참여자 | 앱 | [추가] |
| 요청 승인·거절 | 회원 | 앱 | [추가] |
| 충전 완료(결제 확인) | 회원 | 앱 + **메일** | [개정] |
| 포인트 소멸 예정(30일 전) | 회원 | 앱 | [확정] |
| 운영자: 결제 이상·환불 최종 실패·불변식 불일치·대사 불일치 | 운영자 | 관리자 알림(+ Slack·Mattermost 채널) | [개정] |

- 회원 앱 알림은 기존 `member_notifications`(종류 추가). 환불 사유·운영 메모는 알림에 싣지 않는다 [추가 — community].
- 메일은 **outbox 표**(`mail_outbox`: 수신자·템플릿 키·인자·상태·시도·다음 시도·마지막 오류·보낸 시각)에 업무 트랜잭션에서 넣고, 워커가 보내며 재시도한다 [확정 2026-10-23 — 커머스 메일만, 기존 메일은 그대로]. 발송 기록이 원안의 `notification_log` 를 겸한다.
- 운영자 알림은 기존 `AdminNotificationEvent`(예약된 `COMMERCE_ORDER_ANOMALY`·`COMMERCE_REFUND_REQUESTED` 사용 + 필요한 것 추가). 원안의 Slack 은 이미 있는 채널 연동(V41)으로.
- 알림톡·SMS 는 발송 인터페이스만 추상화해 둔다 [확정].

---

## 15. 배치 · 스케줄러 [확정 2026-10-23]

### 15-1. 결정
- **Spring Batch·Quartz 는 쓰지 않는다.** 지금 roboyam 자체 배치(관리자 서버 `BatchScheduler` — 스프링 `CronExpression`·`ThreadPoolTaskScheduler`, 설정 원천 `batch_jobs`, 서버 여럿이어도 어드바이저리 잠금 + `batch_runs` 예약 시각 유일로 한 번만)와 pg_cron DB 배치를 쓴다. 대신 Spring Batch 를 쓰려던 이유(실패 건수·실패 지점부터 재실행·선택 실행·자세한 이력)를 **15-3 으로 보강**한다. 수백만 건 파일 처리·복잡한 단계 흐름이 생기면 그때 Spring Batch 를 다시 검토한다.
- **실행 위치는 관리자 서버(B안)**, 단 나중에 별도 배치 프로젝트(`server/batch`)로 뺄 수 있게 15-2 구조를 지킨다. 배포 설정 `roboyam.batch.enabled`(인스턴스 역할 — 운영 정책이 아니라 「이 서버가 배치를 도는가」라 배포 설정이 맞다)로 배치를 켤 인스턴스를 고른다 → 같은 관리자 이미지를 「배치 전용」으로 하나 더 띄우면 사실상 배치 서버. 분리 시점: 배치가 관리자 화면을 느리게 하거나 배치만 따로 배포·확장해야 할 때.

### 15-2. 나중에 뺄 수 있게 하는 구조
| 자리 | 두는 것 | 분리할 때 |
|---|---|---|
| `server/shared` — `com.community.batch.core` | 작업 인터페이스(`BatchJob`: 키·단계 목록·실행 파라미터 검증·`run(context)`), 실행 기록·체크포인트·실패 건 기록 서비스, 덩어리 처리 도우미, 실행 요청 표 처리 | 그대로 |
| `server/shared` — 도메인별 `com.community.<도메인>.batch` | 각 작업 구현(예: `point.batch.PointExpireJob`, `payment.batch.ReconciliationJob`) — 도메인 서비스만 부른다 | 그대로 |
| `server/admin` — `com.community.batch`(지금 자리) | 스케줄러·워커 등록(「언제 돌릴지」), 배치 화면 API | 스케줄러·워커 등록만 `server/batch` 로 옮긴다. 관리자에는 화면 API 만 남는다 |
- **「지금 실행」·재실행은 DB 요청 표로 한다**: 화면은 `batch_run_requests`(작업 키·파라미터·요청 운영자·종류[NOW/RESUME/RETRY_FAILED]·상태)에 줄을 넣고, 배치를 켠 인스턴스가 집어 실행한다. 같은 서버든 다른 서버든 같은 방식이라 분리할 때 새 장치가 필요 없다(지금의 「요청 스레드에서 끝날 때까지 기다림」을 이 방식으로 바꾼다 — 화면은 요청 뒤 상태를 다시 읽는다).
- 작업 코드는 관리자 화면 코드(컨트롤러·화면 DTO)를 참조하지 않는다. 의존 방향: `admin → shared(batch.core·도메인 batch)`.

### 15-3. 이력·재실행 보강 (Spring Batch 대신)
| Spring Batch 가 주던 것 | 자체 배치에서 |
|---|---|
| 실행 이력(JobExecution) | `batch_runs`(있음)에 칸 추가: **실행 파라미터**(JSON — 기준일·대상 ID 등), **읽은 건수·처리 건수·실패 건수·건너뛴 건수**, **이어 받은 실행**(`resumed_from_run_id`), **마지막 체크포인트** |
| 단계 이력(StepExecution) | `batch_run_steps`(run_id, 단계 이름, 순서, 상태, 읽음·처리·실패·건너뜀 건수, 시작·끝, 체크포인트) — 여러 단계 작업(예: 판정 = 대상 선정 → 환불 생성 → 알림)이 어디까지 갔는지 |
| 건별 실패(Skip 기록) | `batch_run_failures`(run_id, 단계, 대상 종류, 대상 ID, 오류 코드, 메시지(비밀값·개인정보 가림), 시도 횟수, 해결 여부·해결한 실행, 시각) — **어떤 건이 왜 실패했는지 화면에서 본다**. 한 건 실패가 전체를 멈추지 않게 건별로 격리하고, 실패 수가 설정 한도를 넘으면 그 실행을 FAIL 로 끝낸다 |
| 실패 지점부터 재시작(restart) | 덩어리마다 저장 확정 + **체크포인트**(마지막으로 끝낸 덩어리의 정렬 키) 기록 → 화면의 **「실패 지점부터 다시」**: 같은 파라미터로 그 체크포인트 다음부터. 작업은 멱등(조건부 상태 변경·「아직 처리 안 한 것 전부」)이라 처음부터 다시 돌려도 안전하다 |
| 실패 건만 재처리 | 화면의 **「실패 건만 다시」**: 그 실행의 미해결 `batch_run_failures` 대상만 다시 처리, 성공하면 해결 표시 |
| 선택 실행(JobParameters) | 화면의 **파라미터 지정 실행**: 작업마다 받을 수 있는 파라미터를 정의(예: 대사 기준일, 특정 공구 ID, 특정 회원) → 검증 후 요청 표로 실행. 실행할 **단계를 골라** 돌릴 수도 있다(예: 판정의 알림 단계만) |
| 재시도(Retry) | 외부 호출 실패 건은 시도 횟수·다음 시도 시각을 남기고 다음 실행이 다시 집는다(취소 대기열·메일 대기열과 같은 규칙) |
| 이력 보관 | 기존 `batch-log-cleanup` 이 `batch_runs`·`batch_run_steps`·`batch_run_failures` 를 같이 정리(기간 설정값) |
- 덩어리 크기(기본 500)·실패 한도·재시도 간격은 작업별 설정값.
- **성능 규칙** [확정 2026-10-23]:
  - **성공 건은 한 줄씩 기록하지 않는다** — 건수만 센다. 이력 쓰기는 덩어리마다 한 번(단계의 건수·체크포인트 갱신, 덩어리 저장 확정과 같은 트랜잭션)이다. 예: 10만 건 / 덩어리 500 = 이력 갱신 200번.
  - **건별 기록은 실패한 건만**이고, 실패 한도(기본 100건)를 넘으면 그 실행을 FAIL 로 멈추므로 대량 장애에도 실패 기록이 무한히 쌓이지 않는다.
  - **평소에는 덩어리째 처리**(한 번의 UPDATE·INSERT 묶음). 덩어리가 실패했을 때만 그 덩어리를 한 건씩 다시 돌려 실패한 건을 찾아낸다(Spring Batch 의 skip 과 같은 방식). 정상 경로에 건별 처리 비용이 없다.
  - pg_cron 정리 배치(파기·보관 정리)는 지금처럼 집합 단위 SQL(덩어리 5000)이고 건별 실패 기록을 쓰지 않는다 — 덩어리 실패만 기록한다.
  - 외부 호출(PG 조회·취소)은 원래 건마다 호출이라 건별 격리가 추가 비용이 아니다.
- **작업을 짜는 규칙 3가지**: ① 덩어리 단위로 처리하고 덩어리마다 저장 확정 ② 「그날 몫」이 아니라 「아직 처리 안 한 것 전부」를 대상으로 해 하루를 놓쳐도 다음 실행이 메꾼다(예: 소멸 = 소멸일이 지났는데 남은 묶음 전부, 대사 = 마지막으로 대사한 날 다음부터 어제까지) ③ 실패 건은 시도 횟수·다음 시도 시각을 남긴다.
- 배치 화면에 더하는 것: 실행 상세(단계별 진행·건수), 실패 건 목록, 「실패 지점부터 다시」·「실패 건만 다시」·파라미터 지정 실행 단추(권한 `batch.run`).

### 15-4. 짧은 주기 워커(30초~5분)
- 선점 만료·공구 상태 전이(모집 시작·마감·연장·판정)·PG 취소 재시도·메일 발송(30초)·수량 풀림 알림·업무 알람 점검(5분, 19절)은 배치 화면에 올리지 않고 스프링 `@Scheduled` 워커로 돈다(기존 `BatchRunWatcher` 방식 — `FOR UPDATE SKIP LOCKED` 로 서버끼리 나눠 잡음). 매분 실행 기록이 쌓이지 않게 **실행마다 기록하지 않고** `worker_heartbeats`(워커 키, 마지막 성공 시각, 마지막 처리 건수, 마지막 오류)만 갱신한다. 건별 실패는 각 대기열 표(`payment_cancels`·`mail_outbox`)에 남는다.
- 배치를 켠 인스턴스에서만 돈다(`roboyam.batch.enabled`). 기대 주기의 3배 동안 성공이 없으면 운영자 알림. 선점 만료는 회원이 화면을 볼 때도 그 자리에서 정리되므로(10-4) 워커가 잠시 멈춰도 판매가 틀어지지 않는다.

### 15-5. pg_cron 과 Java 의 분담
원칙: **pg_cron 이 기본**(앱 서버가 모두 내려가도 DB 가 돌린다). 다만 ① PG·메일 같은 외부 호출이 있거나 ② 포인트·돈 규칙을 움직이는 일은 Java — 돈 규칙을 SQL 과 Java 두 곳에 두지 않는다(등급 승급은 두 곳에 있어 함께 고치는 부담이 있다).

| 일 | 어디 | 주기 |
|---|---|---|
| 배송지 마스킹·개인정보·기록 파기(배송 완료 6개월 배송지, 재가입 제한 해시 30일, 공구 상세 수정 이력 6개월, 취소·반품 요청·문의 3년, 거래 기록·탈퇴 회원 연락용 이메일 5년) | pg_cron | 매일 |
| 멱등 요청·웹훅 로그·워커 기록 보관 정리 | pg_cron | 매일 |
| 요청 속도 카운터 지난 창 정리 | pg_cron | 매시 |
| 포인트 불변식 검증(읽기만 — 어긋나면 FAIL 로 끝나 기존 배치 실패 알림이 알린다) | pg_cron | 매일 |
| 어뷰징 리포트 집계 | pg_cron | 매일 |
| 공구 상태 전이(모집 시작·마감·연장 판단·판정 — 판정은 환불·포인트 복원) | Java 워커 | 1분 |
| 선점 만료(수량 복원, 포인트 썼으면 복원) | Java 워커 | 1분 |
| PG 취소 재시도(`payment_cancels`) | Java 워커 | 1분 |
| 메일 발송 대기열 | Java 워커 | 30초 |
| 수량 풀림 알림 | Java 워커 | 1분 |
| 업무 알람 점검(19절) | Java 워커 | 5분 |
| 미확정(READY) 결제 정리 — PG 조회 | Java 배치 | 10분 |
| 포인트 소멸 | Java 배치 | 매일 00:10 KST |
| 소멸 예정 알림 · 마감 임박 알림 | Java 배치 | 매일 · 1시간 |
| PG 대사 — PG 조회 | Java 배치 | 매일 |
- 공구 모집 시작·마감 전환은 상태만 바꾸는 일이지만, 판정과 같은 1분 워커가 한 번에 처리한다(pg_cron 에 1분 주기 배치를 두면 매분 실행 기록이 쌓이고, 같은 공구의 상태를 두 곳에서 바꾸게 된다).

---

## 16. 정산 · 대사

### 16-1. PG 대사 (매일) [확정]
1. 전일 포트원 결제·취소 내역 조회(목록 API 이름·필터는 **포트원 문서로 확인**).
2. `payment_orders`·`payment_cancels` 와 `payment_id` 로 대조.
3. 불일치는 `reconciliation_issues(id, type, payment_id, payment_order_id, detail, status, resolved_by, resolved_at, created_at)`:
   포트원 PAID ↔ 우리 READY/EXPIRED · 우리 PAID ↔ 포트원 없음 · 금액 불일치 · 취소 불일치(콘솔 직접 취소 포함).
4. 자동 보정하지 않고 운영자가 처리(`commerce.reconcile.manage`) [확정].

### 16-2. 매출 집계 [확정 + 정의]
- 공구별: 결제 총액(결제일 기준)·환불 총액(환불 완료일 기준)·순매출, 현금/포인트 구분.
- 충전: 유상 충전액(보너스는 매출 아님), 환불액.
- 월말 미사용 유상 포인트 잔액(선수금).
- 현금영수증·매출전표는 포트원·PG 기능. 세금계산서는 요청 시 수동 발행 [추가 — community].
- 매출 CSV: 개인정보 열 없음, 수식 주입 방어, 기간 상한, 내보내기 감사 [추가].

---

## 17. 회원 탈퇴 · 개인정보

> roboyam 에는 아직 탈퇴 기능이 없다. 아래는 탈퇴 기능을 만들 때 지켜야 할 커머스 쪽 조건이다. `point-contract.md` 가정 5(「탈퇴 시 포인트 소멸」)에 이 절이 정본이라고 적어 두었다(2026-10-23).

### 17-1. 탈퇴 전 검증 [확정 2026-10-23]
- 막는 것: 진행 중 참여(PENDING_PAYMENT·JOINED·CONFIRMED·PREPARING·SHIPPED), 배송완료 후 청약철회 기한(7일) 안, 열린 취소·반품 요청, 끝나지 않은 환불(`payment_cancels` REQUESTED/FAILED), 진행 중 충전 환불. 화면 문구 예: 「진행 중인 공동구매 주문이 있어 탈퇴할 수 없어요 · 배송 완료 7일 뒤 탈퇴할 수 있어요」.
- **정지·기능 제한 중에는 탈퇴할 수 없다** — 탈퇴 후 재가입으로 제재를 피하지 못하게.
- 하자·오배송 반품은 배송완료부터 3개월까지 가능하지만(10-5), **탈퇴를 3개월씩 막지 않는다** — 막는 기간은 단순 변심 기한(7일)까지다. 탈퇴 뒤에 하자 문의가 오면 분리 보관한 연락용 이메일(17-2)로 고객센터가 처리하고, 환불은 거래 기록의 결제 ID 로 PG 취소한다.
- 충전 포인트가 남아 있으면 탈퇴 화면에서 고른다:
  - **「환불받고 탈퇴」**: 충전 환불 신청(9-3 — 7일 안 전액, 지나면 10% 공제). 환불이 끝나면 탈퇴가 완료된다.
  - **「포기하고 탈퇴」**: 「충전 포인트 N P 를 포기합니다」 동의(시각 기록) 후 바로 탈퇴.

### 17-2. 탈퇴 처리 [확정 2026-10-23]
- 적립 포인트 EXPIRE(`WITHDRAW_FORFEIT`), 충전 포인트는 환불 완료 또는 포기 시 EXPIRE, 산 아이템 소멸.
- **회원 행은 지우지 않고 익명화**한다: 이메일·닉네임 등 개인정보를 지우고 「탈퇴한 회원」으로 표시(글·댓글이 그 번호로 남는다). 개인정보가 없는 껍데기 행이라 계속 둬도 된다.
  - 지금 `point_balances`·`point_ledger`·`member_notifications` 는 회원 행 삭제와 함께 지워지게(`ON DELETE CASCADE`) 연결돼 있다 → 회원 행을 실제로 지우지 않는 것을 원칙으로 하고, 원장의 연결은 커머스 1단계 마이그레이션에서 삭제 연쇄가 없도록 바꾼다(새 커머스 표는 3절 원칙대로 FK 를 걸지 않는다).
- **원장·결제·주문·환불 기록은 법정 보관 기간 동안 남긴다** — 금액·상태·날짜만 남고 누구인지는 알 수 없다. 단, 결제 이력이 없는 회원의 무상 원장은 보관 의무가 없어 탈퇴 때 정리한다(17-3). community 의 「탈퇴 시 원장 삭제」는 유상 포인트가 있는 순간 쓸 수 없다.
- 배송지·연락처·현금영수증 번호는 즉시 지운다.
- **최소 식별정보 분리 보관** [확정 2026-10-23]: 결제 이력이 있는 회원은 탈퇴할 때 **이메일 하나만** 암호화해 `withdrawn_member_contacts`(익명화된 회원 번호, 이메일 암호문, 탈퇴 시각, 파기 예정일 = 마지막 거래일 + 5년)에 옮긴다. 분쟁·세무 문의 때 그 거래의 회원에게 연락할 수단이다(전자상거래법 제6조 ②은 탈퇴해도 식별정보를 보존할 수 있게 하고, 보존할 때는 다른 개인정보와 분리 — 20-1 L15).
  - 분리 방식: 별도 표 + 일반 화면·쿼리에서 접근 금지(논리적 분리). 열람은 전용 권한(`member.withdrawn.contact`, SENSITIVE) + 사유 입력 + 감사(개인정보 열람 분류, 2년). 파기 예정일이 지나면 정리 배치가 지운다.
  - 이름·배송지·연락처 등 그 밖의 정보는 남기지 않는다(실명은 애초에 저장하지 않는다 — 12-1). 결제자 확인이 필요하면 거래 기록의 결제 ID 로 PG 기록을 조회한다.
  - DB 백업에는 탈퇴 전 데이터가 남는다 — 백업 보관 기간을 운영 문서에 정한다.
- **재가입 제한 30일** [확정 2026-10-23]: 같은 이메일·같은 소셜 계정·같은 본인인증 값은 탈퇴 후 30일 동안 다시 가입·인증할 수 없다. 이를 위해 이메일·소셜 계정 식별값·본인인증 값의 **해시만** `identity_withdrawals`(종류, 해시, 탈퇴 시각)에 30일 보관하고 배치가 지운다. 30일 뒤 재가입은 **완전히 새 계정**(포인트·아이템·등급·글 이어지지 않음, 새싹부터). 영구 재사용 금지는 하지 않는다(아이디가 이메일이라 그 사람이 자기 이메일로 영영 가입할 수 없게 된다).

### 17-3. 보관·파기 [확정 2026-10-23][법무]
| 대상 | 언제 지우나 |
|---|---|
| 개인정보(이메일·닉네임·배송지·연락처 등) | 탈퇴 즉시 (결제 이력이 있으면 이메일만 분리 보관 — 아래 줄) |
| 탈퇴 회원 연락용 이메일(분리 보관) | 마지막 거래일부터 5년 뒤 |
| 거래 기록(포인트 원장·결제·주문·환불) | **마지막 거래일부터 5년 뒤** 배치가 자동 삭제(기간 설정값). 결제 이력이 없는 회원(커뮤니티 활동만)은 보관 의무가 없어 탈퇴 때 무료 포인트 기록까지 바로 정리 |
| 회원 행 껍데기(개인정보 없음) | 지우지 않음(글·댓글이 참조) |
| 재가입 제한용 해시 | 30일 뒤 |
| 공구 상세 수정 이력(`group_buy_revisions` — 표시·광고 기록) | 6개월 뒤 |
| 취소·반품 요청(`group_buy_requests`)·문의(소비자 불만·분쟁 처리 기록) | 3년 뒤 |
- **법정 보존 기간 [확인 — 전자상거래법 시행령 제6조, 20-1 L14]**: 표시·광고 6개월(공구 상세 수정 이력), 계약·청약철회 5년, 대금결제·재화공급 5년, 소비자 불만·분쟁 처리 3년(취소·반품 요청·문의). 탈퇴 회원의 보존 기록은 다른 개인정보와 **분리 보관**(L15). 탈퇴 회원 식별정보는 이메일만 암호화해 마지막 거래일부터 5년 분리 보관(17-2).
- 배송지 스냅샷은 10-9 기준으로 마스킹·파기.

---

## 18. 테스트 기준 (완료 기준)

- **[확정 2026-10-23 — 사용자 지시] 이 문서와 커머스 계약(`docs/features/commerce-*-contract.md`)에 적힌 규칙은 모두 테스트 코드로 막고, 그 테스트가 통과해야 그 단계가 끝난다.** 사용자 원문: 「커뮤니티 커머스 설계에서 작성된 규칙에 관한건 다 테스트코드로 반영이되어야하고 통과 필수야」. 커머스 작업에서는 이것이 `CLAUDE.md` 「테스트와 검증」의 「QA 요청 전에는 테스트를 쓰거나 돌리지 않는다」를 대신한다.
  - 규칙 하나에 테스트 하나 이상. 구현하는 세션이 바꾼 범위의 테스트를 쓰고 `--tests` 로 골라 돌린다(전체 테스트는 여전히 요청 때만). 실패한 채로 단계를 끝내지 않는다.
  - 완료 보고에는 「규칙 → 테스트」 대응표, 실행 명령, 통과·실패 수, 아직 테스트로 막지 못한 규칙과 그 이유를 적는다.
  - 원안 16장의 시나리오와 아래 표도 이 규칙에 포함된다.
  - 잠금·동시성·조건부 UPDATE·`SKIP LOCKED`·트리거처럼 실제 PostgreSQL 이 있어야 하는 규칙은 Testcontainers(PostgreSQL)로 막는다. **사용자 승인 2026-10-23 — 커머스 테스트에만 쓴다**(다른 기능의 테스트에 퍼뜨리지 않는다). 공용 시험 DB 는 shared 의 testFixtures `com.community.testsupport.CommerceTestDatabase`(`postgres:18`, 앱과 같은 스트림 이관 — 사용법은 `docs/features/commerce-foundation-contract.md` 「시험 기반」).
- 원안 C1~C6, P1~P14, 불변식은 유지. 원안 시나리오 중 선점 10분·결제 실패 즉시 FAILED·마감 순간 판정을 전제로 한 것은 이 문서 기준(선점 5분·EXPIRED·CLOSING 거쳐 판정)으로 읽는다. 개정으로 바뀌거나 더해지는 것:

| # | 시나리오 | 기대 결과 |
|---|---|---|
| P3 (개정) | 결제 진행 중 `end_at` 도달 → 선점 시간 안에 결제 완료 | `CLOSING` 이라 결제 수락, 판정에 포함 |
| P3b | 판정이 끝난 뒤 결제 완료 | REJECT → 자동 취소, 참여 EXPIRED |
| P15 | 선점 만료 직후 결제 도착, 수량 남음 | 자동 취소, 참여 EXPIRED 유지(살리지 않는다) |
| P16 | 결제창 실패 후 선점 시간 안 재결제 | 같은 참여로 성공 |
| P16e | 선착순(`min_qty = 0`) 12-20~01-10 공구, 01-01 배송비 인상 예약 | 공구 끝까지 3,000원, 01-01 이후 시작한 공구는 3,500원 |
| P16b | `DEMAND` 공구, 마감 3분 전 선점 만료로 1개 풀림 | 마감 시각에 30분 연장, 연장 중 그 1개 판매되면 즉시 종료 |
| P16c | `DEMAND` 공구, 마감 시각에 수량 남았지만 최근 선점·만료 없음 | 연장하지 않음 |
| P16d | `ALWAYS` 공구, 마감 시각에 수량 남음 | 30분 연장, 끝나면 CLOSING |
| P17 | 자동 취소 PG 실패 5회 | FAILED, 결제 이상 목록·운영자 알림, 도메인은 확정 안 됨 |
| P18 | 콘솔에서 직접 취소(웹훅 Cancelled) | 상태 불변 + 대사 이슈 |
| P19 | 충전 환불: 보너스 일부 사용, 8일째 | `refund = remaining − 10% − bonus_used`, 보너스 잔여 REVOKE |
| P20 | 운영 조정으로 유상 포인트 지급 시도 | 거부(무상만) |
| P21 | 회수 시 무상 부족·유상 있음 | 무상만 0 까지, 유상 불변, shortfall 기록, `revoke_debt` 증가 |
| P21b | 미회수 20P 가 있는 회원이 글로 20P 적립 | EARN +20 · REVOKE_DEBT_OFFSET −20, `revoke_debt` 0, 하루 한도는 20P 사용으로 센다 |
| P22 | 기존 잔액 전환 후 불변식 | 원장 합 = 잔액 = lot 합(종류별) |
| P23 | 웹훅 서명 틀림 | 400, 포트원 조회 없음, 상태 불변 |
| P24 | 다른 주문의 `paymentId` 로 완료 API | 거절 |
| P25 | 성사 뒤 취소로 `current_qty < min_qty` | 성사 유지 |
| P26 | 같은 `Idempotency-Key`·다른 본문 | 409 |
| P27 | 메일 서버 장애 중 결제 확정 | 확정 성공, outbox 에 남아 복구 후 발송 |
| P28 | 충전 포인트 남은 회원이 「포기하고 탈퇴」 | 포기 동의 기록, 유상·무상 EXPIRE, 원장 보존·회원 익명화 |
| P29 | 탈퇴 10일 뒤 같은 이메일로 가입 | 거절(30일 제한) |
| P30 | 정지 중 탈퇴 시도 | 거절 |
| P31 | 월 한도 10만원 중 8만원 충전한 회원이 두 기기에서 동시에 2만원 충전 | 하나만 확정, 나머지 자동 취소 |
| P32 | PG 취소 가능 기간이 지난 충전 건 환불 | 계좌 정보 받아 송금 대기, 「송금 완료」 후 REFUNDED·계좌 정보 삭제 |
| P33 | 배송완료 40일째 하자 반품 요청(하자를 안 지 10일) | 접수(3개월·30일 안), 전액·반품 배송비 운영자 부담 |
| P34 | 배송완료 40일째 단순 변심 반품 요청 | 거절(7일 지남) |
| P36 | 무료배송(50,000원) 주문 단순 변심 전부 반품, 업체회수, 편도 정책 | 47,000원 환불(반품 배송비 3,000원만 뺌) |
| P37 | 같은 주문, 왕복 정책 | 44,000원 환불(처음 3,000 + 반품 3,000) |
| P37b | 배송비 낸 주문(39,000 + 3,000) 단순 변심 전부 반품, 업체회수(편도·왕복 같음) | 36,000원(처음 배송비 돌려주지 않음 + 반품비) |
| P37c | 같은 주문, 고객발송 | 39,000원(처음 배송비 돌려주지 않음, 반품비 없음) |
| P37d | 무료배송(50,000) 단순 변심 전부 반품, 고객발송, 왕복 정책 | 50,000원 전액(처음 배송비 받지 않음, 반품비 없음) |
| P38 | 개당 25,000 무료배송 2개 중 1개 단순 변심 반품, 업체회수 | 남은 25,000 < 기준 → 19,000원(배송비 재부과 3,000 + 반품비 3,000) |
| P38b | 같은 경우, 고객발송 | 22,000원(배송비 재부과 3,000만) |
| P39 | 개당 25,000 무료배송 3개 중 1개 단순 변심 반품, 업체회수 | 남은 50,000 ≥ 기준 → 22,000원(반품비만) |
| P40 | 무료배송 2개 중 1개 하자 반품 | 25,000원 전액(재계산·반품비 없음) |
| P41 | 배송비 낸 주문 하자 전체 반품, 고객발송, 고객이 택배비 4,000원 냄 | 결제액 전액(처음 배송비 포함) + 보상 4,000원 |
| P42 | 배송비 낸 주문 하자 부분 반품, 업체회수 | 해당 상품 금액만(처음 배송비 그대로) |
| P35 | 발송 전 취소 요청이 2영업일 미처리 | 운영자 경고 알림, 3영업일째 심각 |

- 불변식 추가: 공구·옵션별 `current_qty`·`reserved_qty` = 참여 합, `payment_orders.paid_amount − canceled_amount` = 연결 도메인의 현금 금액(원안 유지), 수량 이동 줄의 마지막 값 = 현재 수량.

---

## 19. 모니터링 · 알람 [확정 2026-10-23]

- **지표(응답 시간·처리량·JVM·DB 등)는 별도 모니터링 툴의 에이전트를 서버에 심어 수집한다.** 원안 17-1 의 Micrometer 업무 지표(`point.earn`·`payment.complete.*` 등)는 앱에서 따로 만들지 않는다. 지금 actuator 는 로드밸런서 상태 점검용으로만 연다.
- **업무 알람은 DB 점검 + 기존 운영자 알림**: 5분마다 도는 점검 워커(15-4)가 DB 를 조회해 조건에 걸리면 기존 운영자 알림(`AdminNotificationEvent` — 관리자 화면 알림·Slack/Mattermost 채널·심각도·권한별 받는 사람, 예약된 `COMMERCE_ORDER_ANOMALY`·`COMMERCE_REFUND_REQUESTED` 사용 + 필요한 종류 추가)으로 보낸다. 같은 원인은 기존 묶기 기능으로 정해진 간격에 한 번만.

| 알람 | 알아내는 방법 | 수준 |
|---|---|---|
| 포인트 불변식 불일치 | 매일 검사 배치가 FAIL | 심각 |
| 환불·자동 취소 최종 실패 | `payment_cancels` 최종 실패 건 | 심각 |
| 결제 확정 실패가 많음 | 최근 5분 결제 중 확정 실패 비율 10% 초과 | 심각 |
| 웹훅 서명 실패 급증 | `payment_webhook_logs` 최근 10분 REJECTED 건수 | 심각 |
| 워커·배치 멈춤 | `worker_heartbeats`·`batch_runs` 마지막 성공이 기대 주기 ×3 초과 | 심각 |
| 미확정 결제가 쌓임 | 30분 넘은 READY 가 시간당 20건 초과 | 경고 |
| 메일 발송 실패 누적 | `mail_outbox` 실패 건수 | 경고 |
| 취소·반품 환불 기한 임박 | 처리 안 된 요청이 2영업일 경과(경고)·3영업일 도달(심각) — 법정 환급 기한 3영업일(20-1 L4) | 경고·심각 |
| 대사 불일치 | 대사 결과 불일치 1건 이상 | 매일 요약 |
| 어뷰징 의심 | 3일 연속 하루 총 한도 도달 회원 | 매일 요약 |
- **문턱 값**(10%·20건·10분 등)은 관리자 화면에서 바꾼다(감사 전→후).
- **관리자 대시보드에 커머스 칸**: 결제 이상 N건, 환불 실패 N건, 처리 대기 주문·요청 N건, 워커 상태.

---

## 20. 법적 · 운영 체크 (현금 결제 오픈 전) [확정 + 추가]

시스템이 강제하는 것(판매 스위치를 켜는 조건 — 자동 항목) [추가 — community]:
- 사업자 정보 필수 칸(상호·대표자·사업자등록번호·통신판매업 신고번호·**신고 기관**·주소·전화·이메일)을 공구·충전 화면 하단에 표시(전자상거래법 제13조 ① — 20-1 L11)
- PG 키 4종 설정, 택배사 1개 이상(공구), 본인인증 설정

**local(개발 PC)에서는 판매가 열려 있어야 한다** [확정 2026-10-23]: 별도 프로퍼티나 「local 이면 검사 생략」 코드를 두지 않고, **local 시드가 조건을 실제로 채운다** — 가짜 결제 게이트웨이(Fake PG, local·시험 전용이라 PG 조건 통과)·가짜 본인인증·시험용 사업자 정보·택배사·체크리스트 체크·판매 스위치 켜짐. 그래서 local 에서도 켜는 조건 검사 코드가 그대로 돌고 시험된다. dev 서버는 PG·본인인증 **테스트 채널 키**를 넣고 운영자가 켠다. Fake PG·가짜 본인인증은 dev·prod 에서 기동을 막는다(8-5·12-1).

**판매 스위치 규칙** [확정 2026-10-23]:
- **켜기는 최고 관리자(루트)만**, 위 자동 항목이 모두 통과하고 **아래 수동 체크리스트(운영 체크 항목)가 모두 체크돼 있어야** 켤 수 있다(community 는 기록만 했지만 여기서는 막는다 — 신고·약관을 빠뜨린 채 여는 실수 방지). 체크는 누가·언제 했는지 기록.
- **끄기는 최고 관리자 또는 `commerce.settings` 권한자** — 문제가 생기면 바로 멈출 수 있게. 끄면 새 구매만 막히고 진행 중 주문(배송·취소·환불)은 그대로.
- 켜기·끄기 모두 감사(`COMMERCE_SALES_TOGGLE`, 전→후·사유) + 운영자 알림.
- 운영 체크 항목: 사업자등록 · 통신판매업 신고 · 이용약관 · 개인정보처리방침(배송지·결제·본인인증 항목) · PG 계약·웹훅 주소 등록 · 반품 받을 주소 확인.

### 20-1. 법령 원문으로 확인한 것 [확인 2026-10-23]
국가법령정보센터의 현행 원문을 직접 읽어 확인했다. 전자상거래법(법률 제21312호, 시행 2026-07-21), 같은 법 시행령(대통령령 제36507호, 시행 2026-07-21), 전자금융거래법(법률 제22048호, 시행 2026-10-02), 개인정보보호법(시행 2026-09-11), 개인정보의 안전성 확보조치 기준(개인정보보호위원회고시 제2026-9호)이다. 공정위 온라인게임 표준약관(제10069호)은 법제처 「찾기쉬운 생활법령정보」의 정리로 확인했다(표준약관은 권고). **법률 자문이 아니고 원문 문구로 확실한 것만 적는다.**

| # | 확인한 근거 | 내용 | 이 문서에 미치는 것 |
|---|---|---|---|
| L1 | 전자상거래법 제17조 ①1 | 청약철회는 계약 내용 서면(주문 확인)을 받은 날부터 7일. **공급이 늦으면 공급받은 날부터 7일** | 공구 7일은 배송완료부터(10-5) — 맞다. 공급 전에도 철회할 수 있으므로 **발송 전 취소를 받는 것(D11)이 법 규정과 같다** |
| L2 | 제17조 ③, 제18조 ⑩ | 표시·광고·계약과 다르게 이행됐으면 **공급받은 날부터 3개월, 안 날부터 30일** 안에 철회 가능, 반환 비용은 사업자 부담 | **하자·오배송 반품은 7일에 묶지 않는다** — 10-5·10-8 을 고쳤다 |
| L3 | 제18조 ⑨ | 단순 변심(제17조 ①) 철회의 반환 비용은 소비자 부담, 위약금·손해배상 청구 불가 | 단순 변심 반품 배송비 회원 부담 — 맞다. **기본(편도)은 면제된 처음 배송비를 받지 않는다. 왕복 정책을 고르고 금액을 미리 고지한 공구만 받는다**(법 문구는 「반환에 필요한 비용」뿐 — 20-2 1번 정함, 10-8). 가격 외 반품 비용은 금액까지 미리 고지(시행령 제20조 1호) |
| L4 | 제18조 ② · 시행령 제21조의3 | 환급은 **3영업일 안**(재화는 반환받은 날, 공급 전 철회·디지털콘텐츠는 철회한 날부터). 늦으면 연 15% 지연배상금 | 취소·반품 승인 흐름에 **3영업일 처리 기한**을 둔다(10-8) |
| L5 | 제18조 ③ | 카드 등으로 결제했으면 결제업자에게 청구 정지·취소를 요청 | PG 취소로 환불(8-3) — 맞다 |
| L6 | 제17조 ②5·⑥ · 시행령 제21조의2 | 디지털콘텐츠는 **제공이 시작되면** 철회를 막을 수 있으나, **철회 불가 사실을 명확히 표시하고 시험 사용(미리보기 등)을 제공**해야 한다. 가분적 콘텐츠는 제공이 시작되지 않은 부분은 철회 가능 | 상점의 「미사용·7일 환불, 사용하면 환불 불가」(D6)는 법보다 넓게 보호한다 — 맞다. 단 **구매 전 고지 + 미리보기는 필수**(7-3) |
| L7 | 온라인게임 표준약관 제30조 ① (생활법령정보) | 유상 캐시 환불 때 잔액의 **10% 이내** 공제(잔액 1만원 이내면 일정 금액), 청약철회는 7일·3영업일 환급 | 충전 환불 「7일 안 전액, 이후 10% 공제」(9-3)는 표준약관 범위 안 — 맞다 |
| L8 | 전자금융거래법 제2조 14호 · 제28조 ③1가 | 선불전자지급수단 = **발행인 외의 제3자**에게서 재화·용역을 사는 데 쓰이는 것. 하나의 가맹점에서만 쓰이면 등록 면제 | **충전 포인트가 운영자 자신이 파는 것(상점·운영자 직판 공구)에만 쓰이면 선불전자지급수단에 해당하지 않아 등록이 필요 없다.** 회원 주최 공구·입점 판매처럼 제3자 상품에 쓰게 하면 다시 검토한다(제약으로 고정 — 9-0) |
| L9 | 전자금융거래법 제23조 | 발행권면 최고한도는 금융위가 **전자금융업자 등에게** 정하는 한도 | 우리는 대상이 아니므로 「법정 보유 한도 200만원」은 적용되지 않는다. 30만P 는 자체 정책(9-0 고침) |
| L10 | 전자상거래법 제24조 ②③, 제13조 ②10 | **선지급식 통신판매**는 소비자가 고르면 결제대금예치(에스크로) 또는 소비자피해보상보험을 제공해야 하고, 그 선택 가능 사실을 고지해야 한다. **신용카드 결제·정보통신망으로 전송되는 재화(디지털)는 제외** | 성사형만이 아니라 **모든 공구**(결제 후 배송 = 선지급)가 대상. 신용카드는 제외지만 체크카드·계좌 기반 간편결제는 제외가 아니다 → **PG 의 구매안전서비스(에스크로)를 계약해 비신용카드 결제에 적용**(8-5). 상점·충전은 디지털이라 제외. 통신판매업 신고 때도 구매안전서비스 이용 확인증을 낸다(서울시 안내) |
| L11 | 전자상거래법 제13조 ① | 표시 의무: 상호·대표자, 주소·전화·이메일, **통신판매업 신고번호와 신고 기관** | 사업자 정보 칸에 「신고 기관」 추가(20절 자동 항목) |
| L12 | 제13조 ② | 계약 체결 전 거래조건 고지, 체결 뒤 **공급할 때까지 계약 내용 서면 교부**(판매자·상품·가격·지급·공급 방법과 시기·청약철회 기한과 방법·환불 조건·분쟁 처리·약관·결제대금예치 선택 등) | 결제 확인 메일(14절)에 이 항목을 담는다. 메일 대기열(유실 없음)이 필요한 근거 |
| L13 | 제13조 ③ | 미성년자와 계약할 때 법정대리인 동의 없으면 취소할 수 있다는 고지 의무 | 만 19세 미만 결제 차단(12-1)이라 해당 계약이 생기지 않는다 |
| L14 | 시행령 제6조 ① | 보존 기간: **표시·광고 6개월, 계약·청약철회 5년, 대금결제·재화공급 5년, 소비자 불만·분쟁 처리 3년** | 거래 기록 5년(17-3) — 맞다. **분쟁 처리(요청·문의) 기록 3년, 공구 상세(표시·광고)의 수정 이력 6개월**을 더한다(10-2·17-3) |
| L15 | 법 제6조 ② · 시행령 제6조 ②3 · 개인정보보호법 제21조 ③ | 거래기록과 **거래 주체 식별정보(성명·주소·이메일)는 동의를 철회(탈퇴)해도 보존할 수 있다**. 보존할 때는 **다른 개인정보와 분리** | 탈퇴 회원 거래 기록은 분리 보관. 식별정보는 이메일 하나만 암호화해 5년 분리 보관으로 정함(17-2, 20-2 2번) |
| L16 | 안전성 확보조치 기준 제8조 | 개인정보처리시스템 **접속기록 1년 이상**, 정보주체 5만 명 이상·고유식별/민감정보 처리면 **2년 이상**. 다운로드 점검 주기·방법을 내부 관리계획으로, 접속기록 위변조 방지 | 개인정보 열람 감사 2년(13-4) — 기준 이상. **관리자 앱에서 개인정보가 보이는 화면 접속 전체**를 접속기록 대상으로 본다(13-4 에 더함) |
| L17 | 개인정보보호법 제21조 ① | 보유기간이 지나면 지체 없이 파기 | 재가입 제한 해시 30일 보관은 **보유기간을 정해 고지(처리방침)하는 것을 전제로** 그 기간 뒤 파기(17-2) |

### 20-2. 법령 문구만으로 확정하지 못한 것 (전문가 확인)
1. ~~무료배송으로 면제된 처음 배송비를 단순 변심 반품 환불에서 빼도 되는지~~ → **정함(2026-10-23)**: 기본은 편도(빼지 않음). 배송비 정책에서 왕복을 고르고 금액을 미리 고지한 공구만 뺀다. 부분 반품은 실무 규칙대로 남은 수량 기준 재계산(10-8). 왕복을 쓰는 것은 근거 미확인 위험을 감수하는 선택이다. 추가 조사 기록:
   - 확실한 것: 단순 변심 반품의 「반환에 필요한 비용」은 소비자 부담, 위약금·손해배상은 청구 불가(법 제18조 ⑨ — 공정위 소비자24 질의회신도 같은 취지). **가격 외에 소비자가 추가로 부담할 교환·반품 비용은 내용과 금액을 미리 고지하고 계약 내용에 적어야 한다**(시행령 제20조 1호).
   - 확인 못 한 것: 「면제된 처음 배송비」를 반품 때 받아도 된다는 법 조문·공정위 해석·분쟁조정 결정은 찾지 못했다. 소비자원 분쟁조정 사례 중 판매자가 「최초 무료배송료 + 반송료」를 빼려 해 다툼이 된 건이 있으나 결론은 확인 못 했다.
   - 업계: 오픈마켓 판매자들은 무료배송 상품의 단순 변심 반품에 왕복(처음 배송비 + 반품 배송비)을 받는 경우가 흔하다(실무 안내·판매자 커뮤니티). 공식 정책 문서로는 확인 못 했다.
   - 위험: 무료배송이 계약 조건이었으므로 그 배송비를 반품 때 받는 것을 위약금으로 볼 여지가 있다(언론 인용 해석).
2. ~~탈퇴 회원 거래 기록에 식별정보를 얼마나 남길지~~ → **정함(2026-10-23)**: 이메일 하나만 암호화해 5년 분리 보관(17-2). 논리적 분리(별도 표 + 접근 통제)로 충분한지는 법령·해설서 원문으로 확인하지 못했다 — 쇼핑몰 처리방침에는 「탈퇴 시 30일 분리 보관(혜택 중복 방지)」·「CI 1년 분리 보관(부정 방지)」처럼 분리 보관을 운영하는 예가 있다(코오롱몰). 최종 확인은 전문가.
3. **배송지가 「재화 공급에 관한 기록」(5년)에 들어가는지** — 다시 확인(2026-10-23): 법령은 기록의 범주만 정하고 배송지를 명시하지 않는다. 대형 쇼핑몰 처리방침(코오롱몰) 중에는 배송 정보 보유 기간을 「재화 공급 완료 및 결제·정산 완료 시까지」로 두고, 5년 보존 항목은 「계약·청약철회, 대금결제 및 재화 등의 공급기록」으로만 적은 곳이 있다 — 배송지를 5년 의무로 보지 않는 실무가 있다는 근거다. 그래서 **배송 완료 6개월 뒤 파기(10-9)를 유지**하되, 공급 사실(배송완료일·택배사·송장번호)은 5년 거래 기록으로 남긴다. 최종 확인은 여전히 전문가.
4. **세무** — 다시 확인(2026-10-23): 국세청 서면법규과-823(2014.08.06)은 **자사에서만 쓰는 게임머니는 이용자가 결제한 때가 공급시기**이고 그 결제금액으로 세금계산서·영수증을 발급하며, **경품 등으로 무상 지급한 게임머니는 부가세 과세 대상이 아니다**라고 회신했다. 우리 충전 포인트도 자사 상품에만 쓰이므로 **충전 결제 때 매출·부가세를 잡고 영수증(카드 결제는 카드 전표, 현금성 결제는 현금영수증 — PG 발급)을 낸다**, 무료(적립) 포인트 지급은 매출이 아니다. 남은 확인: 충전 포인트로 **공구 실물을 결제할 때**(11절, 기본 꺼짐) 충전 때 이미 과세된 부분과 공구 매출이 겹치지 않게 처리하는 방법 — 켜기 전에 세무사 확인.
5. 이용약관·개인정보처리방침 문안 자체(포인트 유효기간·차감 순서·환불 수수료·반품 배송비, 배송지·본인인증 정보·보관 기간·처리 위탁).

출처: [전자상거래법](https://www.law.go.kr/법령/전자상거래등에서의소비자보호에관한법률) · [같은 법 시행령](https://www.law.go.kr/법령/전자상거래등에서의소비자보호에관한법률시행령) · [전자금융거래법](https://www.law.go.kr/법령/전자금융거래법) · [개인정보보호법](https://www.law.go.kr/법령/개인정보보호법) · [개인정보의 안전성 확보조치 기준](https://www.law.go.kr/행정규칙/개인정보의안전성확보조치기준) · [생활법령정보 — 온라인 게임 계약해지 및 분쟁해결](https://easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=2858&ccfNo=2&cciNo=1&cnpClsNo=2) · [서울시 경제분야 — 통신판매업 신고](https://news.seoul.go.kr/economy/archives/95)

---

## 21. 구현 순서 · 진행 방식

### 21-1. 순서 [개정 — D2]
0. 공통(구조·세션 정리 결정·진행 상태: `docs/features/commerce-foundation-contract.md` — 본인인증 표 이름은 `identity_verifications` 로 정했다): 본인인증(`user_identity`), 멱등 요청 표, 메일 outbox, 필드 암호화, 운영자 2FA, 기능 제한 표, 권한 코드 추가, **배치 보강**(15-2 구조 정리 — 작업 core 를 shared 로, 실행 요청 표, 15-3 단계·실패 건·체크포인트·재실행, 15-4 워커 기록)
1. 포인트: lot·원장 확장·차감 정책·전환 마이그레이션·소멸·불변식 → 포인트 상점(품목·구매·인벤토리·장착·환불)
2a. 결제 모듈 단독: `payment_orders`·포트원 연동·확정 코드·웹훅·취소 워커·READY 정리·대사(Fake + 포트원 테스트 채널)
2b. 공구: 공구·옵션·참여·선점·마감·배송·요청·배송지
3. 충전: 충전 상품·적립·환불
3+. 공구 포인트 결제(플래그 꺼짐)
4. 관리자 화면 보강, 대사, 모니터링

### 21-2. 진행 방식 [확정 + 프로젝트 규칙]
- 단계마다 **ERD 와 패키지 구조를 먼저 제안 → 승인 후 구현** [확정].
- UI 가 있는 단계는 프로젝트 규칙대로 **화면(목업) → 사용자 확정 → `docs/features/<기능>-contract.md` → 백엔드 → 연결** 순서.
- 정책 숫자는 설정값 또는 정책 표 [확정].

---

## 22. 확인하지 못한 것 (지어내지 않았다)
- 포트원 V2 — **공식 문서로 확인(2026-10-23)**:
  - 결제 다건 조회 `GET /payments`(페이지 기반, 기준 시각 `CREATED_AT`·`STATUS_CHANGED_AT`) → PG 대사(16-1)에 쓴다.
  - 에스크로: 배송 정보 등록 `POST /payments/{paymentId}/escrow/logistics`·수정 `PATCH`·구매 확정 `POST …/escrow/complete` → **에스크로 결제 건은 발송 처리 때 택배사·송장을 PG 에 등록**해야 한다(8-5·10-8 발송 흐름에 더함).
  - 취소: `currentCancellableAmount` 는 「취소 가능 잔액이 일치할 때만 취소」(이중 취소 방지 — 8-3 과 같다). **취소 가능 기간: 카드 통상 1년(카드사별 상이), 카카오페이 카드 1년·머니 5년, 네이버페이 1095일, 가상계좌 제한 없음** → 기간이 지난 충전 환불은 계좌 송금(9-3)이 필요하다는 판단이 맞다.
  - 본인인증 `verifiedCustomer` 에 `ci`·`di`·`birthDate`(YYYY-MM-DD). **DI 는 다날·KCP 는 항상 제공, KG이니시스는 미제공. CI 는 다날은 계약 시 신청, KCP 는 가맹점 설정** → DI 를 중복 확인 키로 쓰려면(12-1) **본인인증 PG 를 다날 또는 KCP 로** 고른다.
  - `paymentId`: 「이미 결제 완료된 paymentId 는 재사용 불가」만 확인 → 재결제마다 새 ID(8-1).
- 포트원 V2 — 아직 확인 못 함: 채널별 승인/매입 분리 지원, 현금영수증 요청 인자, 에스크로를 쓰는 결제 수단 범위(채널 설정).
- 법령 문구로 확정하지 못한 법무 항목은 20-2 에 모았다(선불전자지급수단·보존 기간·청약철회 기한은 20-1 에서 원문으로 확인함).
- PG·카드사별 결제 취소(부분 취소) 가능 기간 — 이 기간이 지난 충전 환불은 계좌 송금(9-3). PG 계약 때 확인.
- 지표 수집용 모니터링 툴(에이전트) 종류 — 운영 인프라를 정할 때.

## 참고한 문서
- community: `docs/api-commerce.md`·`api-commerce-admin.md`(공구), `api-deal-shop.md`(결제 게이트웨이·웹훅·환불), `api-point.md`·`api-like-bonus.md`(포인트), `api-shop.md`(아이템샵), `api-sanction.md`(제재), `features.md` 23절, `product.md`, `decisions.md`.
- roboyam: `docs/features/point-contract.md`, `docs/domain/operator-permissions.md`, `docs/features/{batch,notification,member-notification,mail-settings}-contract.md`, `Permission`·`MemberPermission`, V51·V50·V7·V10 마이그레이션.
