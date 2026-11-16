# 포인트 게임(룰렛 · 로또 번호 뽑기) 계약

## 의도
적립(무상) 포인트가 쌓이기만 하고 쓸 곳이 적어서 만드는 소진처. 사이트는 돈·실물 상금을 주지 않는다 — 룰렛은 적립 포인트로 돌려 적립 포인트로 받고, 로또는 실제 로또를 사기 전에 번호만 뽑아 준다(당첨 번호를 만들거나 비교하지 않음, 당첨 인증 게시판 없음). 화면 확정 2026-11-16(목업 브랜치 `claude/point-games` 380f779e·72664161).

## 화면 경로
- 회원: `/games/roulette`, `/games/lotto` — 로그인 필요, 내 정보 틀(`meMenu`). 사업자 전용 계정은 포인트가 없어 「사업자 계정」 빈 화면.
- 입구: 내 정보 맨 위 칸(`MeShopGameLinks` — 포인트 상점·로보티콘 상점 / 룰렛·로또 번호), 헤더 계정 메뉴(구분선으로 묶어 「화면 설정」 바로 위).
- 홈 가젯: 아이로빗 홈 오른쪽 광고 아래(백엔드 단계에서 가젯 종류 등록 — 아래 API 후보).
- 관리자: 포인트 상점 설정(`/point-shop/settings`) 안 「포인트 게임」 칸 — 복권 사업자 이름 · 당첨 확인 링크.

## 규칙
| 항목 | 값 |
|---|---|
| 쓰는 포인트 | **적립(FREE) 포인트만**. 충전(PAID) 포인트는 절대 빠지지 않는다(차감 순서 「무상 먼저」에 기대지 않고 무상 lot 에서만 뺀다). 모자라면 `409 POINT_INSUFFICIENT` |
| 룰렛 | 1회 50P, 하루 5회(한국 시간 0시 초기화). 확률(만분율): 꽝 7000 · 10P 1200 · 20P 800 · 30P 500 · 50P 300 · 100P 150 · 500P 50 → 평균 돌려받는 양 19.6%. 결과는 서버가 정한다(화면은 그 칸에 멈추게만). 차감·추첨·지급이 한 트랜잭션 |
| 룰렛 당첨 | 적립 포인트로 지급(새 무상 lot, 보통 적립과 같은 소멸 규칙). 활동 적립 하루 한도(`dailyTotalCap`)에 넣지 않는다. 포인트 랭킹(활동 적립 기준)에도 넣지 않는다 |
| 로또 뽑기 | 1~45 중 6개. 1세트 20P · 5세트 한 번에 80P. 하루 횟수 제한 없음. 한 번에 뽑은 세트끼리는 겹치지 않게 |
| 뽑는 방법 | 자동(`AUTO` — 고정·제외 없음) · 반자동(`SEMI_AUTO` — 고정 1~5개 필수, 제외 선택, 고정·제외 겹침 금지, 45 − 고정 − 제외 ≥ 6 − 고정) |
| 내 번호 | 회원별 저장, 한 회원 50세트. 가득 차면 뽑기 막음(`409 LOTTO_STORAGE_FULL`, 자동 삭제 없음). 지우기 가능(쓴 포인트 안 돌려줌). 각 세트에 뽑은 방법·시각 |
| 당첨 확인 링크 | 관리자 설정 `lotteryProviderName`(1~30자) · `lotteryCheckUrl`(`https://` 절대 주소, 300자 이하). 기본 「동행복권」 `https://dhlottery.co.kr`. 사업자가 바뀔 수 있어 설정값 |
| 공통 거절 | 사업자 전용 계정 `403 BUSINESS_ACCOUNT_RESTRICTED`, 정지 회원 `403 ACCOUNT_RESTRICTED`, 포인트 동결 회원은 사용 불가 |
| 포인트 내역 | 사유 코드 새로: 게임 사용(룰렛 돌리기·로또 뽑기) · 게임 당첨(룰렛). 내역 화면에 이름이 보여야 한다 |

## 권한
- 관리자 설정: 기존 `pointshop.read` / `pointshop.manage`.

## API 후보
- `GET /api/me/point-games/roulette` → `cost, dailyLimit, usedToday, prizes[{key, points, chanceBp}], freeBalance, resetsAt`
- `POST /api/me/point-games/roulette/spins` → `{spinId, prizeKey, won, freeBalance, usedToday}` · `409 POINT_GAME_DAILY_LIMIT` · `409 POINT_INSUFFICIENT`
- `GET /api/me/point-games/lotto` → `setCost, bundleSize, bundleCost, freeBalance, storageLimit, draws[{id, numbers, mode, drawnAt}](최신이 위), checkLink{providerName, url}|null`
- `POST /api/me/point-games/lotto/draws` 요청 `{sets: 1|5, mode, fixed[], excluded[]}` → `{draws, freeBalance}` · `400 VALIDATION fields.sets|mode|fixed|excluded` · `409 LOTTO_STORAGE_FULL` · `409 POINT_INSUFFICIENT`
- `DELETE /api/me/point-games/lotto/draws/{id}` → 204(이미 없어도 204, 남의 것은 404)
- `GET/PUT /api/admin/point-games/settings` → `{lotteryProviderName, lotteryCheckUrl, defaults, updatedAt, updatedBy}`
- 홈 가젯 종류 `POINT_GAMES`(룰렛·로또 두 입구 + 오늘 남은 룰렛 횟수 정도). 아이로빗 홈 기본 배치에서 오른쪽 「광고」(SQUARE_BANNER) 바로 아래.

## 미확정 사항
- 룰렛 값·확률·횟수 관리자 설정 — 지금은 서버 상수(나중에 설정으로).
- 게임에서 받은 적립 포인트를 공구 무상 포인트 프로모션(`PAID_AND_FREE`)에서 뺄지 — 지금은 구분하지 않음(프로모션을 켤 때 다시 본다).
- `commerce.md` 「랜덤박스는 팔지 않는다」 원칙: 포인트 게임은 판매가 아니고 적립 포인트·비환금 보상만이라 예외로 둔다(이 계약이 정본).
- 법무 확인: 사행성(게임산업법) 해당 여부 — 운영 전 확인.
