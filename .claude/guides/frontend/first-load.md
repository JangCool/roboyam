# 첫 로딩 (`first-load.md`)

공개 사이트(user-web) 첫 화면을 빨리 띄우는 장치와, 화면·서버를 고칠 때 지킬 것. 사용자 결정 2026-11-14(측정: 운영 본문 다 참 2.9초 → 0.9초, 느린 회선 6.0초 → 3.3초).

## 장치 네 가지

| 장치 | 하는 일 | 코드 |
|---|---|---|
| 첫 화면 데이터(initial state) | api 서버가 `index.html` 을 내릴 때 첫 화면 GET API 응답들을 `<script id="irb-boot" type="application/json">` 으로 함께 싣는다. 화면은 그 주소를 처음 부를 때 서버에 묻지 않고 쓴다 | 서버 `site/boot/BootPreloadRenderer` · `SitePageHandler` · `IndexHtmlRenderer.withBoot`, 화면 `utils/util-boot-data.ts` · `useApiClient` |
| 조각 미리 받기 | 첫 화면 데이터로 테마·홈 가젯 종류를 알아, 앱이 뜨자마자 테마 셸·홈 틀·가젯 코드를 동시에 받기 시작한다 | `plugins/spa-warmup.client.ts` · `UtilTheme.preload` · `UtilGadgetRegistry.preload` |
| 링크 미리 받기 | `<NuxtLink>` 는 마우스를 올리거나 누를 때만 그 페이지 코드를 받는다(보일 때 받지 않는다) | `nuxt.config.ts` `experimental.defaults.nuxtLink` |
| 스플래시 | JS 가 오기 전부터 로고·진행 막대·단계 점, 첫 화면이 다 그려진 뒤 한 번에 걷힌다 | `app/spa-loading-template.html` · `utils/util-boot-splash.ts` · `plugins/spa-splash.client.ts` |

## 첫 화면 데이터 규칙

- **실은 응답은 한 번만, 페이지를 연 뒤 15초 안에만 쓴다.** 서버가 응답을 만든 언어가 화면 언어와 다르면 쓰지 않는다. 없거나 못 쓰면 평소처럼 서버에 묻는다 — 목록이 어긋나도 화면은 깨지지 않고 그 호출 하나가 왕복할 뿐이다.
- **주소(질의 포함)가 화면이 만드는 것과 같아야 한다.** 비교는 경로 + 이름순으로 정렬한 질의(인코딩 차이 무관).
- **서버 목록 = `BootPreloadRenderer` 의 「주소 → 서비스 호출」.** 응답은 그 API 컨트롤러가 부르는 서비스를 그대로 불러 `ApiResponse.success` 로 감싼다. 컨트롤러 안에만 있던 조립은 함께 쓰는 곳으로 옮겨 두 곳에 같은 코드를 두지 않는다(예 `LoginSession.current`, `CommerceInfoController.Info.of`).
- **싣는 것:** 첫 화면에서 바로 보이는 영역이 부르는 GET 읽기 API 만. 모든 화면 공통(셸·메뉴·공지 띠·광고 자리 등) + 홈(`/`) 전용. 로그인이 필요한 것은 로그인한 요청일 때만.
- **싣지 않는 것:** 화면 아래쪽·다른 화면에서만 쓰는 API, 쓰기 요청, 머리글을 읽는 요청(`onHeaders`) — HTML 이 커지고 서버가 매 요청 만든다.
- 관리자 미리보기(`?adminPreview=1`)·설치 전·HEAD 요청에는 싣지 않는다. 실으면 HTML 은 `Cache-Control: private, no-cache`(로그인한 사람의 정보가 들 수 있다).

## 고칠 때 할 일

| 바꾸는 것 | 할 일 |
|---|---|
| 관리자 화면에서 홈 배치·가젯 바꾸기, 화면 모양만 바꾸기 | 없음 |
| 새 가젯 종류 | 원래대로 서버 `GadgetKinds`·`GadgetService` + 화면 레지스트리 한 줄(미리 받기는 자동) |
| **첫 화면 영역에 새 GET 호출 추가, 기존 호출의 주소·질의 변경**(예 홈 광고 `types=`, 메뉴 묶음 이름) | 서버 `BootPreloadRenderer` 목록에 같은 주소로 한 줄 더하거나 고친다 |
| 첫 화면 영역에서 호출을 뺌 | 목록에서도 뺀다 |

## 확인 — 콘솔 알림

페이지를 연 뒤 15초가 지나면 콘솔에 한 번 `[첫 화면 데이터]` 로 알린다 — **실린 게 없어 서버에 물은 주소**(`missed` — 첫 화면 영역이면 목록에 더할 후보)와 **실었는데 쓰지 않은 주소**(`unused` — 뺄 후보).

- `console.debug` 라 평소에는 보이지 않는다 — Chrome 개발자 도구 콘솔의 수준을 「상세(Verbose)」로 바꾼다.
- **api 서버가 내려준 화면에서만** 나온다(jar 로 띄운 화면·운영). 개발 서버(`pnpm dev`, 3100)는 `index.html` 을 Nuxt 가 내려 첫 화면 데이터가 없으므로 알림도 없다.
- 로그인한 사람의 알림·쪽지 안 읽은 수(`/api/notifications/unread-count` · `/api/dm/unread-count`)처럼 사람마다 다르고 주기적으로 다시 묻는 주소는 `missed` 에 나와도 그대로 둔다.
