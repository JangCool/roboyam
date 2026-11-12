# 배송지 관리(회원 주소록) 계약 (**화면 확정 2026-11-05**)

**의도**: 공동구매를 주문할 때마다 배송지를 새로 쓰지 않게, 회원이 「내 설정」에 배송지를 여러 개 두고 하나를 기본으로 정한다. 주문서는 기본 배송지로 시작하고 저장된 배송지 중에서 고른다.
주문의 배송지는 지금처럼 주문마다 암호문 사본(`commerce.md` 10-9)이다 — 주소록을 고치거나 지워도 이미 낸 주문은 바뀌지 않는다.

## 화면 (기준 코드)
- user-web `/settings/addresses`(`pages/settings/addresses.vue`) — 「내 설정」 메뉴 `UtilSettings.ENTRIES` 의 쪽지 수신 다음. 목록(기본이 맨 위 · 「기본」 딱지 · 별칭/받는 분 · 전화 · 주소 · 메모),
  「배송지 추가」(`n/10`, 10개면 꺼짐) · 수정 · 삭제(확인) · 「기본으로」. 추가·수정은 시트 — 별칭 + `GroupBuyAddressFields`(공용 주소 입력 `AddressInput`) + 「기본 배송지로 정하기」(첫 배송지는 켜진 채 잠김).
- 공구 주문서 `/group-buys/{id}/order` 「3. 배송지」 — 저장된 배송지가 있으면 **카드 모드**(기본 배송지, 없으면 첫 줄) + 「배송지 변경」 시트(라디오 · 「새 주소 입력」 · 「배송지 관리」 링크) + 이번 주문의 배송 메모 칸(저장된 메모로 미리 채움).
  없거나 「새 주소 입력」이면 **입력 모드** — 지금 칸들 + 「배송지 목록에 저장」(주소록이 비면 기본 체크, 10개면 꺼짐 · 안내). 주소록을 못 받으면 입력 모드로 열고 저장 체크는 감춘다(주문은 막지 않음).
  카드 모드에서 서버가 `address.*` 칸 오류를 주면 카드 아래 오류 + 「이 배송지 고치기」(그 값을 채운 입력 모드).

## 필드·검증
- 별칭 `label` 0~20자(비우면 null) · 받는 분 `recipient` 1~30자 · 전화 `phone` 숫자 9~11자리(하이픈 허용) · 우편번호 `postcode` 숫자 5자리 · 주소 `address1` 1~100자 · 상세 `address2` 0~100자 · 메모 `memo` 0~50자.
  주문 배송지(`GroupBuyJoinService`)와 같은 규칙을 한 곳에서 쓴다. 틀린 칸을 모아 400 `VALIDATION` `fields.<칸>`.
- 한 회원 **최대 10개** — 넘으면 409 `ADDRESS_BOOK_FULL`.

## 권한·정책 (사용자 결정 2026-11-05)
- 저장은 주문 배송지와 같은 `DataCipher`(`IROBIT_DATA_KEY`) 암호문, 우편번호만 평문. 키가 없으면 주소록 API 는 503(주문과 같게).
- **회원 본인 화면에는 전체 값을 그대로** 보인다(가리지 않음). 운영자 화면·API 에는 주소록이 없다. 로그·감사·알림에 평문을 싣지 않는다.
- 첫 배송지는 자동 기본. 기본은 언제나 하나(배송지가 있으면 꼭 하나). 기본을 지우면 남은 것 중 가장 최근(`updated_at`) 것이 기본. 기본을 끄는 길은 다른 배송지를 기본으로 정하는 것뿐.
- 목록 차례: 기본 → `updated_at` 최근 순.
- 지우면 바로 삭제(보관 없음). **탈퇴하면 즉시 삭제.** 오래 안 써도 자동 삭제 없음.
- 주문서에서 새 주소를 저장하는 시점은 **주문을 만들 때**(주문 생성 트랜잭션, 결제 확정을 기다리지 않음). 10개가 차 있으면 저장만 건너뛰고 주문은 계속한다.
- 로그인 필요(남의 배송지는 404).

## API 후보
| 요청 | 응답 · 오류 |
|---|---|
| `GET /api/me/addresses` | `{items[{id(문자열), label\|null, recipient, phone, postcode, address1, address2, memo, isDefault, updatedAt}], max}` |
| `POST /api/me/addresses` `{label, recipient, phone, postcode, address1, address2, memo, makeDefault}` | 항목 · 400 · 409 `ADDRESS_BOOK_FULL` |
| `PUT /api/me/addresses/{id}` (같은 본문) | 항목 · 404 `ADDRESS_NOT_FOUND` |
| `DELETE /api/me/addresses/{id}` | 204 · 404 |
| `PUT /api/me/addresses/{id}/default` | 204 · 404 |
| 공구 주문 `POST /api/group-buys/{id}/orders` | 배송지는 `addressId` + `address.memo`(카드 모드) **또는** `address`(입력 모드) + `saveAddress`. 남의·없는 `addressId` 는 400 `fields.addressId` |

## 미확정
- 마이그레이션 번호는 합치기 직전 develop 을 다시 본다(지금 후보 shared V139).
- 내 주문 상세의 배송지 수정(`/me/orders/{orderNo}`)에서 주소록 고르기는 이번 범위 밖.

## 서버 구현 결과 (2026-11-05)
- **마이그레이션** shared `V139__member_addresses.sql` — `member_addresses`(받는 분·전화·주소·상세·메모 `*_enc` 암호문, `label`·`postcode` 평문, `is_default`, `created_at`·`updated_at`, `users` FK `ON DELETE CASCADE`) · 부분 유일 색인 `ux_member_addresses_default (user_id) WHERE is_default` · `ix_member_addresses_user (user_id, updated_at DESC, id DESC)`. 합치기 직전 번호 다시 확인.
- **코드** shared `com.community.addressbook` — `AddressBookService`(규칙 전부, 용도 `member.address.{recipient,phone,line1,line2,memo}`) · `MemberAddressStore`(JDBC 행 레코드 — 부분 유일 색인 때문에 「앞 기본 끄기 → 켜기」 SQL 차례를 직접 정한다) · `AddressFieldRules`(칸 규칙 한 곳 — 공구 주문서 `GroupBuyJoinService`·내 주문 배송지 수정 `AddressRevealService` 도 이것을 쓴다). api `AddressBookController`.
- **동시성** 모든 쓰기는 회원 단위 어드바이저리 잠금 `hashtext('ab:<회원>')` 아래 — 10개 한도·기본 하나를 동시 요청에도 지킨다.
- **API** 표 그대로. `POST` 는 201, `PUT` 은 200 + 항목, `DELETE`·`PUT …/default` 는 204. 응답 `Cache-Control: no-store`. 경로 ID 가 숫자가 아니어도 404 `ADDRESS_NOT_FOUND`. 키 없음 503 `DATA_KEY_MISSING`(목록·추가·수정·삭제·기본 모두).
- **주문 요청** `POST /api/group-buys/{id}/orders` 에 `addressId`(글자, 선택)·`saveAddress`(선택, 기본 false). 카드 모드 `{items, addressId, address:{memo}, agreements, expectedAmount}` — 메모는 요청 값만(비우면 메모 없음, 저장된 메모를 대신 쓰지 않는다). 입력 모드 `{items, address:{…전체}, saveAddress:true, …}` — 주문 생성 트랜잭션 안에서 저장(별칭 없음, 비어 있었으면 기본, 10개면 건너뜀, 주문 실패면 저장 없음, 멱등 재전송은 업무가 다시 돌지 않아 한 번). `addressId` 가 있으면 `saveAddress` 는 무시. 응답은 그대로.
- **칸 규칙 통일로 주문 쪽이 좁아졌다** — 예전 주문 검사(받는 분 50·전화 `[0-9-]{9,15}`·우편번호 5~6자리·주소/상세/메모 200)는 화면 `maxlength`(30·13·5·100·100·50)보다 넓었다. 이제 계약 값 그대로. 내 주문 배송지 수정 오류도 첫 칸 하나 대신 틀린 칸을 모두(`address.memo` 가 `address.address2` 로 나가던 것 바로잡음). 칸 문구 키 `address.field.*`(예전 `groupbuy.order.address.invalid` 는 지움).
- **탈퇴** 완료 때 `WithdrawalEraseStore.deleteOwnRows` 가 `member_addresses` 를 지운다.
- **시험** `AddressFieldRulesTest`(shared, 12) · `AddressBookHttpTest`(api, 주소록 13 + 주문 연결 7). 규칙 → 시험 대응은 시험 클래스 머리 주석.
- **데이터 키 교체** 배치(`DataKeyColumns`)에 주소록 5칸(`member-address-*`)과 공구 주문 배송지 5칸(`groupbuy-address-*`)을 올렸다(같은 때 빠져 있던 입금 계좌·사업자 신원·광고주 연락처 칸도). 「교체 → 예전 키 비움 → 주소록 목록 그대로」·스키마의 `*_enc`/`*_cipher` 칸 누락 감시는 `DataKeyRotationJobTest`.
