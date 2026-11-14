# 구글 애드센스 계약

사업자 광고 신청을 아직 받을 수 없는 개인 운영 단계에서도 광고 자리를 채우기 위해 구글 애드센스를 붙인다.
**장부 광고(사업자 신청·운영자 등록)가 늘 먼저**이고, 그 자리에 진행 중인 장부 광고가 없고 광고 단위 ID가 있을 때만 애드센스를 그린다. 위치·개수는 기존 광고 자리 설정을 그대로 따른다.
화면 확정 2026-11-10. 광고 일반 규칙은 `ad-contract.md`.

## 화면
- 관리자: 광고 › 광고 기본 설정 › 「구글 애드센스」 탭 `/ads/placements?tab=adsense` — 기존 자리 설정 저장(`PUT /api/admin/ads/placements`)에 함께 저장된다.
- 공개(user-web): 배너 가젯 네 종(WIDE·STRIP·SQUARE·SMALL), 날개 160×600(`WING_BANNER`), 글 보기 글과 댓글 사이(`POST_INLINE`), **목록형(LIST) 게시판** 목록 한 줄 인피드(`BOARD_LIST`, 기본·다음식·네이버식 테마). 카드형·갤러리형·핫딜 목록에는 넣지 않는다.

## 필드·검증 (`adsense` 묶음)
| 필드 | 값 | 검증(400 키) |
|---|---|---|
| `mode` | `OFF` 끔 · `VERIFY` 사이트 확인만 · `SHOW` 광고 표시. 기본 `OFF` | 모르는 값 `fields.adsense.mode` |
| `client` | 게시자 ID `ca-pub-` + 숫자 16자리, 없으면 null | `VERIFY`·`SHOW` 면 필수·형식 `fields.adsense.client` |
| `adtest` | 시험 표시(`data-adtest="on"`), 기본 false | — |
| `units.{WIDE_BANNER,STRIP_BANNER,SQUARE_BANNER,SMALL_BANNER,WING_BANNER,POST_INLINE}` | 숫자 10자리, 없으면 null | `fields.adsense.units.<자리>` |
| `units.BOARD_LIST.slot` · `.layoutKey` | 숫자 10자리 · `^[-+]?[0-9a-z]+([-+][0-9a-z]+)+$`(대소문자 무시) | 둘 다 있거나 둘 다 비어야 함 `fields.adsense.units.BOARD_LIST.slot` / `.layoutKey` |

- `OFF` 여도 입력해 둔 게시자 ID·단위 ID는 저장해 둔다(형식이 틀리면 거절).
- 저장은 기존 자리 설정과 같은 감사(`AD_PLACEMENT_UPDATE`)·변경 이력(`/api/admin/ads/placements/history`)에 남는다.

## 단계별 동작
| 단계 | index.html 머리 | `/ads.txt` | 공개 `adsense` 묶음 | 화면 |
|---|---|---|---|---|
| `OFF` | 넣지 않음 | 404 | null | 애드센스 없음(장부 광고 없는 자리는 지금처럼 숨김) |
| `VERIFY` | 애드센스 스크립트 + `google-adsense-account` 메타 | 한 줄 | `mode`·`client`·`adtest`, `units` 빈 묶음 | 광고 단위를 그리지 않음 |
| `SHOW` | 같음 | 한 줄 | 위 + 단위 ID가 있는 자리만 `units` | 장부 광고 없는 자리에 단위를 그림 |

- 머리 스크립트: `<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client={client}" crossorigin="anonymous"></script>` + `<meta name="google-adsense-account" content="{client}">`. 공개 화면 HTML(api `SitePageHandler`/`IndexHtmlRenderer`)에만 넣는다. 관리자 미리보기 iframe 응답에는 넣지 않는다.
- `/ads.txt`: `google.com, pub-{숫자16}, DIRECT, f08c47fec0942fa0` 한 줄, `text/plain`.
- 승인은 구글 애드센스의 사이트 심사(콘솔 사이트 상태 「준비됨」). 콘솔의 「자동 광고」는 꺼 두라고 화면에서 안내한다.

## 정책
- 애드센스를 넣지 않는 곳: 19금 게시판·19금 글(「19금 게시판에도 광고」 설정과 상관없이), 광고 숨김 게시판, 소모임(게시판 종류 `GROUP`), 비밀글·잠금 화면, 관리자 미리보기.
- 노출·클릭은 우리 광고 성과에 집계하지 않는다(애드센스 콘솔에서 본다).
- 광고가 오지 않으면(`data-ad-status="unfilled"`) 자리를 접고, 광고 차단기로 스크립트가 5초 안에 처리하지 않으면(또는 단위가 숨겨지면) 자리를 접는다.

## API
- 관리자 `GET`·`PUT /api/admin/ads/placements` — 본문·응답에 `adsense` 묶음 추가(위 표 모양, `units.BOARD_LIST` 는 `{slot, layoutKey}`).
- 공개 `GET /api/ads/placements` — `adsense` 추가: `OFF` 면 null, 아니면 `{mode, client, adtest, units}`(`units` 는 값 있는 자리만, `BOARD_LIST` 는 둘 다 있을 때만).
- 공개 `GET /ads.txt`(api).

## 서버 구현(2026-11-10)
- DB: shared `V176__ad_adsense.sql` — `ad_placement_settings` 에 `adsense_mode`(기본 `OFF`, CHECK) · `adsense_client` · `adsense_adtest`(기본 false) · `adsense_{wide,strip,square,small,wing}_banner`·`adsense_post_inline` · `adsense_board_list_slot`·`adsense_board_list_layout_key`(varchar(64)). 형식·짝 CHECK 는 마지막 지킴이. 설치 프리셋·데모 팩은 적힌 칸만 쓰므로 새 칸은 기본값(끔).
- 모양 정본: shared `ad/dto/AdSenseSettings`(관리자 `adsense` 묶음 = 이 레코드, 공개는 `publicView()`). 관리자 `PUT` 의 `adsense` 는 선택(빠지면 그대로) · `adtest` 비면 false · `units` 비면 모두 null. 레이아웃 키는 최대 64자(넘으면 `fields.adsense.units.BOARD_LIST.layoutKey`). 인피드 한쪽만 있으면 빈 쪽 키로 400(문구 `ad.placement.adsense.inFeedPair`).
- 감사·이력 칸: `adsense.mode`·`adsense.client`·`adsense.adtest`·`adsense.units.<자리>`·`adsense.units.BOARD_LIST.slot|layoutKey`(값 없으면 null). 처음 저장(CREATE)은 값이 없는 칸(빈 게시자 ID·단위 ID)을 남기지 않는다.
- 공개 `GET /api/ads/placements` 응답 레코드는 api `PublicAdView.Placements`(네 묶음 + `adsense`) — 요청마다 읽는다(사본 없음).
- 머리: api `SitePageHandler` 가 요청마다 애드센스 칸을 읽어(`AdSenseSiteService`) 켜져 있으면 `IndexHtmlRenderer.withAdSense` 로 `</head>` 바로 앞에 메타·스크립트를 더한다(바탕 사본 지문 + 게시자 ID 로 캐시, ETag 도 바뀜). 관리자 미리보기(`?adminPreview=1`)에는 넣지 않는다. 읽기가 실패하면 애드센스 없이 내린다.
- `/ads.txt`: api `AdsTxtController` — `text/plain;charset=UTF-8`, `Cache-Control: no-cache`, 꺼져 있으면 본문 없는 404(화면 index.html 로 바뀌지 않음).
- 화면 연결 끝(2026-11-10): admin-web `useAdApi`·user-web `usePublicAdApi` 가 서버 `adsense` 를 그대로 쓴다 — 목업(`mock/adsense-mock.ts` 두 앱, `?mock=adsense*` 자리표시) 제거, 화면 검사에 레이아웃 키 64자 상한 추가.

## 정리한 결정(2026-11-10)
- **레이아웃 키 64자**: 구글이 길이를 공개하지 않는다. 실제 인피드 키는 `-fb+5w+4e-db+86` 꼴로 15~20자 안팎이라 넉넉한 상한(칸 크기)으로 둔 것이고, 형식 검사(정규식)가 본 검사다.
- **캐시하지 않음**: 공개 자리 설정·머리 주입·`/ads.txt` 는 요청마다 한 행(PK)을 읽는다 — 캐시 관리 계약의 「캐시하지 않는 것: 광고」와 같다. 저장하면 다음 요청부터 반영된다.
- **유럽 방문자 동의 창**: 사이트에서 만들지 않는다. 애드센스 콘솔 「개인정보 보호 및 메시지 › 유럽 규정」 메시지를 켠다(구글 인증 동의 관리 도구). 켜지 않으면 유럽 방문자에게는 광고가 제한되거나 나가지 않을 뿐 사이트 동작에는 영향이 없다. 관리자 탭 「애드센스 준비 안내」가 이 안내를 한다.
- **개인정보처리방침**: 관리자 약관 양식(`apps/admin-web/app/utils/terms-templates/privacy.ts`) 6절 「광고와 맞춤형 광고(행태정보)」가 애드센스 쿠키·수집 주체(구글)·국외 처리·거부 방법을 이미 담고 있다(terms-operator-contract 결정). 사이트에 실제로 게시한 처리방침이 이 양식 6절을 포함하는지는 운영자가 확인한다.

## 미확정
- 법무 검토(처리방침 6절 문구의 최종 확인)는 운영자 몫으로 남는다.
