# 광고주 관리 계약 (화면 확정 2026-11-03)

**의도**: 운영자가 광고주를 한곳에서 본다 — 직접 등록한 광고주(계정 없음)와 사업자 광고주(사업자 신원 1:1)의 연락처(가린 값·평문 열람)·광고 건·성과·결제 합계·기록. 직접 등록 광고주는 만들기·고치기·보관·삭제한다.
**정본·근거**: 광고주 표·연락처 암호화·평문 열람은 `ad-contract.md` 「서버 구현 결정 — V115」, 사업자 상태는 `business-contract.md`, 성과 숫자는 `ad-performance-contract.md`. 목업 설계 노트는 세션 공유 폴더 `ad-advertisers-mockup-spec.md`.
**사용자 결정(2026-11-03)**: 서랍·목록 강조는 광고 심사 가운데 칸과 같은 토큰(`UtilBizApplication.EMPH` — 받는 돈 주 색 굵게 · 환불 빨강 · 대기 주황 · 진행 초록 · 일자 하늘색) · 아래 「규칙」 1·6·9 는 사용자가 추천안으로 확정.

## 화면 경로
- admin-web `/ad-advertisers` 「광고주 관리」(메뉴 「광고」 넷째 — 광고 성과 다음·광고 기본 설정 앞, `pi pi-id-card`, `ad.read`).
- 목록: 종류 탭(전체·사업자·직접 등록 + 건수) · 상태(사용 중 기본·보관·보관 포함 전체) · 찾기(이름·등록번호 끝자리) · 정렬(최근 광고일 기본 내림 · 누적 결제 · 이름 가나다). 줄 = 이름+종류 · 가린 연락처 · 진행/예약/대기 건 수 · 누적 결제 · 최근 광고일 · 상태 꼬리표.
- 상세 서랍(`?id=`): 기본(연결된 사업자 → `/businesses?id=` · 연락처 가린 값 + [연락처 보기] · 건 수 · 메모) · 광고 건(최근 50, 줄 → `/ads?type=&id=`) · 성과(최근 30일 — `AdStatsTotals`·`AdStatsChart`·유형별, 「광고 성과에서 자세히」 → `/ads/performance?advertiserId=&from=&to=`) · 결제(결제 = 온라인 + 수동, 환불, 결제 − 환불) · 기록(연락처 열람 최근 20 · 처리 기록). 맨 아래(`ad.manage`): [고치기] · [보관]/[보관 풀기] · [삭제].
- 만들기·고치기 대화상자 `AdAdvertiserFormDialog`: 연락처 칸은 평문을 미리 채우지 않는다(가린 값 자리 글자 — 비우면 그대로 · 입력하면 바꾸기 · [지우기]).

## 규칙
1. **광고주 키(한 줄로 합치기)**: 사업자가 걸린 것은 모두 사업자 하나 = 한 줄, ID `biz-<사업자 신원 id>`. 신청 건의 묶음 키 = 건의 `business_profile_id` → 없으면 건 광고주(`advertiser_id`)의 `business_profile_id` → 없으면 `adv-<advertiser_id>`(직접 등록). 사업자에 붙은 광고주 줄(`ad_advertisers.business_profile_id`)의 옛 ID `adv-<id>` 는 **별칭** — 상세·열람·성과 거르기에서 받아 같은 사업자로 푼다(응답 ID 는 `biz-`). 그래서 사업자 광고주에 광고 담당자를 저장해 광고주 줄이 새로 생겨도 **ID 가 바뀌지 않는다**(링크·북마크 유지).
2. **목록에 나오는 사업자**: 광고주 줄이 붙었거나, 신청 건(종류 무관 — 광고·핫딜)이 하나라도 있거나, `AD` 서비스 줄이 있는 사업자 신원. 상태 거르기(사용 중/보관)는 직접 등록에만 걸리고 사업자는 늘 「사용 중」 쪽.
3. **건 수**(묶음 키의 모든 신청 건): 진행 = `SCHEDULED` 이고 시작 ≤ 지금 < 끝 · 예약 = `SCHEDULED` 이고 시작 > 지금 · 대기 = `SUBMITTED`·`AWAITING_PAYMENT`·`AWAITING_DEPOSIT` · 전체 = 모든 상태(취소 포함).
4. **돈**(VAT 포함, 원): 결제 = 결제 뒤 건의 결제 금액(온라인 결제 완료 금액 + 수동 입금 확인 금액 — 신청 건 상세 `refundSummary.paid` 와 같은 값의 합), 온라인/수동은 결제 방식별 합. 환불 = 승인·완료된 환불 금액 합(`refundSummary.refunded` 의 합). 목록 「누적 결제」 = 결제(환불을 빼지 않는다). 서랍 결제 − 환불은 화면 계산.
5. **최근 광고일** = 결제 뒤 건(예약·진행·종료·내림) 중 **이미 시작한** 건의 가장 늦은 시작 시각(앞으로 시작할 예약은 빼서 미래 날짜가 나오지 않는다). 없으면 null — 정렬에서 방향과 상관없이 뒤.
6. **보관·삭제**(직접 등록만): 삭제 = 신청 건이 하나도 없을 때만(아니면 409 `AD_ADVERTISER_HAS_BOOKINGS`) — 광고주 줄·열람 기록·처리 기록을 지운다(감사 로그는 남는다). 보관 = 끝나지 않은 건(진행·예약·대기)이 없을 때만(409 `AD_ADVERTISER_HAS_OPEN_BOOKINGS`), 보관 풀기는 언제나. 보관하면 운영자 등록의 광고주 찾기(`GET /api/admin/ads/advertisers?q`)에서 빠지고 보관된 광고주로 운영자 등록하면 400 `fields.advertiserId`. 사업자 광고주는 보관·삭제 없음(사업자 정지·탈퇴를 따른다 — 409 `AD_ADVERTISER_BUSINESS`).
7. **고치기**: 빠진 칸 그대로 · 빈 글자 지우기 · 값은 바꾸기(검사는 만들기와 같음 — 이름 1~60·유일, 담당자 30, 연락처 숫자 9~11, 이메일 형식 120, 메모 300). 사업자 광고주는 `name` 을 보내면 400 `fields.name`(이름 = 상호), 담당자·연락처·이메일 = **광고 담당자**(광고주 줄 칸 — 없으면 고칠 때 붙은 광고주 줄을 만든다, 이름 = 상호 스냅샷). 바뀐 칸이 없으면 기록·감사 없이 그대로 돌려준다.
8. **연락처**: 응답에는 가린 값만. 직접 등록 = 광고주 줄 칸. 사업자 = 광고 담당자 칸이 하나라도 있으면 그것(`contactSource=ADVERTISER`), 없으면 사업자 신원의 대표자·연락처·이메일 가린 값(`BUSINESS`). 평문은 `POST /api/admin/ads/advertisers/{id}/reveal`(`ad.contact.reveal`)로만 — 사유 5~200자, 열람 기록(`ad_advertiser_views` — 사업자는 `business_profile_id` 로) + 감사 `AD_CONTACT_REVEAL`(개인정보), `hideAt` 10분. 사업자 신원 연락처를 낼 때도 같은 기록이다(사업자 화면 `business.reveal` 과 별개).
9. **파기**: 보관한 직접 등록 광고주는 **마지막 광고 끝 + 5년**(건이 없으면 보관 시각 + 5년 — 지금은 건 없는 광고주는 지우므로 드묾)이 지나면 담당자·연락처·이메일 암호문·가린 값을 비우고 `contact_purged_at` 을 찍는다(계약서 사본 5년 보존과 맞춤). 열람 기록·처리 기록은 남는다(평문 없음). DB 배치 `R__batch_35_ad_advertiser_contact_purge`(매일 — 기존 배치 체계, 배치 화면에 나옴). 파기된 광고주를 보관 풀어도 연락처는 비어 있다.
10. **처리 기록**: 만들기·고치기(바뀐 칸 이름만 — 값은 남기지 않음)·보관·보관 풀기 + 감사(`AD_ADVERTISER_CREATE`·`_UPDATE`·`_ARCHIVE`·`_UNARCHIVE`·`_DELETE`, 대상 `AD_ADVERTISER`). 사업자 광고주의 처리 기록은 그 사업자의 광고주 줄 기록.
11. **성과**: 기존 `GET /api/admin/ads/stats?from&to&advertiserId` — `biz-<id>` 거르기 = 그 사업자의 **모든** 건(신청 건 + 사업자를 고른 운영자 등록 건 + 연결된 광고주 줄의 건), `adv-<연결된 id>` 도 같은 사업자로. `topAdvertisers` 도 같은 키로 묶는다. 광고 일정표·성과 `Row.advertiser.id` 도 같은 키(사업자 건 = `biz-`). 운영자 등록 서랍의 광고주 찾기 ID 는 지금 그대로(`adv-` 연결 광고주를 고르면 `advertiser_id` 도 붙는다).

## API (`/api/admin/ad-advertisers`, 응답 no-store — admin-web `useAdAdvertiserApi.ts`·`.types.ts` 가 모양 정본)
- `GET ?kind=BUSINESS|ADVERTISER&state=ACTIVE|ARCHIVED|ALL&q&sort=lastAdAt|paidTotal|name&dir=asc|desc&page&size(≤100)` → `{ items[Row], total, counts{ALL,BUSINESS,ADVERTISER} }`(`ad.read`). 모르는 값 400 `fields.<칸>`.
- `GET /{id}` → Detail(`ad.read`, 없으면 404 `AD_ADVERTISER_NOT_FOUND`, 별칭 `adv-` 는 `biz-` 로 돌려준다).
- `POST /` `{name,contactName,contactPhone,email,memo}` → Detail(`ad.manage`) · `PATCH /{id}` → Detail · `POST /{id}/archive`·`/unarchive` → Detail · `DELETE /{id}` → 204.
- 평문·성과는 기존 API(규칙 8·11).

## 권한·정책
- `ad.read` 화면·목록·상세 · `ad.manage` 만들기·고치기·보관·삭제 · `ad.contact.reveal`(SENSITIVE) 평문. 새 권한 없음.

## 미확정
- 직접 등록 광고주를 나중에 사업자와 잇기(연결) — 화면 없음(필요하면 후속).
- 파기 배치 기본 시각(매일 04:10 가정 — 배치 화면에서 바꿀 수 있음).
