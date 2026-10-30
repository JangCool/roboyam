# 광고 일정표 계약 (화면 확정 2026-10-30 — 목업, 서버 없음)

**의도**: 운영자가 광고 유형별로 언제 자리가 비었는지 보고, 광고 건(사업자 신청 건에서 확정된 것 + 운영자 수동 등록)을 한 장부에서 관리한다. 긴급 내림·되돌리기로 사고에 대응한다.
**정본·근거**: `docs/plans/biz-ad-hotdeal/00-overview.md` ★ 절(★-2 수동 결제·A1~A3·F5), 분석 `02-ad.md`. 신청·결제 흐름은 `biz-application-contract.md` 가 정본이다(광고 건 = 신청 건이 `SCHEDULED` 이후인 것).
**프론트 기준**: admin-web `pages/ads/index.vue`, `components/ad/**`(`AdCalendar`·`AdBookingDrawer`·`AdManualDrawer`·`AdAdvertiserDialog`), `composables/ad/api/useAdApi.ts`(+types·mock).

## 화면 경로
- 관리자 `/ads` — 유형 탭 7개(넓은·띠·네모·작은 배너·한 줄·핫딜 홈 상단·핫딜 말머리 상단) → 요약 띠(한도·진행·예약·결제·입금 대기) → **한 달 큰 달력**(하모니카3 월 보기 짜임: 칸마다 「남은/한도」 알약 + 그날 걸친 광고 건 이름·상태 색 점, 최대 3 + 「+N건 더」, 건을 누르면 상세, 오늘 이후 빈 곳을 누르면 그 날짜로 수동 등록) → 광고 건 표(상태·결제 방식·비중) → 상세 Drawer(기본·소재 버전·일정·결제·처리 기록). 메뉴 묶음 「광고」(`ad.read`).
- 공개 노출 컴포넌트(`AdSlot`·가젯 `AD`·`AD_TEXT_LINE`·글 중간)는 이번 목업 범위 밖 — 02 §7-7 이 설계 정본.

## 규칙
- 유형별 동시 한도(설정값). 날짜별 남은 칸 = 한도 − 그날 자리를 차지한 건(결제·입금 대기 포함, 취소·만료 제외). 달력은 안내이고 저장 때 서버가 다시 판정(409 `BIZ_SLOT_FULL`).
- **수동 등록**: 광고주(사업자 또는 계정 없는 광고주 — [새 광고주]) → 유형·비중(1~10)·기간 → 소재(이미지는 JPG·PNG·WEBP, 링크 https·단축 URL 거부) → **계약서 사본(필수)** → 금액·**입금 확인** → [예약 확정]. 계약서·입금 둘 다 없으면 버튼이 꺼지고 서버도 막는다.
- **긴급 내림**: 진행·예약 건, 사유 필수, 광고주 메일 선택, 「다른 서버 사본 최대 20초 남을 수 있어요」. **되돌리기**: 기간이 남았을 때만, 보상 「중단 시간만큼 끝 시각 연장 / 일할 환불」 선택(F5).
- 소재 버전: 교체는 새 버전(옛 파일 삭제 금지), 노출 집계는 버전 단위(A2). 광고 코드(스크립트)는 받지 않는다(A1 — 3차, CSP 와 함께).
- 공개 노출: 「광고」 표시 + `rel="sponsored nofollow noopener"`, 같은 시간 여러 건은 비중 가중 무작위, 사본 20초. 1차에 최소 일별 노출·클릭 집계(00 D9).

## 권한·감사
- `ad.read`(화면) · `ad.manage`(수동 등록·광고주) · `ad.takedown`(SENSITIVE, 긴급 내림) · **추가** `ad.restore`(SENSITIVE, 되돌리기 — 목업은 임시로 `ad.takedown`) · `ad.contact.reveal`(광고주 연락처 평문). featureReady 켬, `AdminScreenRegistry` 에 「광고」 묶음.
- 감사: `AD_MANUAL_CREATE`·`AD_ADVERTISER_CREATE`·`AD_TAKEDOWN`·`AD_RESTORE`(보상 방식 포함)·`AD_CREATIVE_REPLACE`·`AD_CONTACT_REVEAL`.

## API 후보
목업 머리말이 정본 후보 — `useAdApi.ts`: `GET /api/admin/ads/calendar?type&month`(그 달 날짜별 남은 칸 + 유형 요약) · `GET /api/admin/ads?type&status&q&page&size` · `GET /api/admin/ads/{id}` · `GET /api/admin/ads/availability` · 광고주 `GET/POST /api/admin/ads/advertisers` · `POST /api/admin/ads/contract-files` · `POST /api/admin/ads/manual` · `POST /api/admin/ads/{id}/takedown` · `POST …/restore`.
- 달력은 지금 목록 API 를 `size=500` 으로 따로 불러 칸에 이름을 그린다. 서버에서는 `calendar` 응답에 그 달에 걸친 건(이름·상태·기간)을 함께 내려 한 번에 받게 한다.

## 저장 (제안)
광고 건은 `biz_applications`(`biz-application-contract.md`)를 그대로 장부로 쓴다 — 수동 등록도 같은 표(`source = OPERATOR`). 추가: `ad_advertisers`(계정 없는 광고주, 연락처 암호문, 사업자 1:1 연결), `ad_type_settings`(유형별 한도·하루 단가), `ad_daily_stats`(일별 노출·클릭).

## 미확정
- 한도·하루 단가 초기값(목업 가정: 넓은 배너 2 · 핫딜 홈 상단 3 · 말머리 상단 2).
- 유형 설정·자리 관리 화면(02 §7-2·7-3)은 이번 목업에 없음 — 백엔드 전에 필요하면 추가.
