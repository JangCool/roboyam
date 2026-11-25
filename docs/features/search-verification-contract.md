# 검색 엔진 소유 확인 코드 — 계약

관리자가 검색 엔진(네이버·구글·빙) 소유 확인 코드를 코드 수정 없이 넣으면, 공개 사이트 첫 HTML `<head>`에 메타 태그로 나간다. 2026-11-25 네이버 서치어드바이저 등록 때문에 만들었다.

- 화면: 관리자 설정 › 일반(`/settings/general`) 맨 아래 「검색 엔진 등록」(`SiteSearchVerificationSettings`, anchor `search-verification`). 권한 조회 `site.read`, 저장 `site.manage`.
- 칸: 네이버 → `naver-site-verification`, 구글 → `google-site-verification`, 빙 → `msvalidate.01`. 모두 선택 입력이다. 사이트에서 복사한 `<meta …>` 태그를 통째로 붙여 넣으면 화면이 `content`만 뽑고, 다른 서비스 태그는 거절한다.
- 검사: 앞뒤 공백을 떼고 빈 값은 null(태그를 넣지 않음)로 본다. 값은 `^[A-Za-z0-9_-]{1,100}$`이고, 어긋나면 400 VALIDATION `fields.naver|google|bing`.
- API: `GET`/`PUT /api/admin/site/search-verification` → `{ naver, google, bing }`(null 가능). 설정 행이 없으면 GET은 404.
- 저장: `site_settings.search_verify_{naver,google,bing}`(shared V271). 값이 바뀐 때만 감사 기록 `SITE_SEARCH_VERIFICATION_UPDATE`(SITE/1, 바뀐 칸만 before/after)을 남기고 「사이트 설정 변경」 알림을 보낸다.
- 공개 사이트: `PublicSiteSnapshot`에 실어 `IndexHtmlRenderer.fill`이 `</head>` 앞에 값이 있는 것만 넣는다(이스케이프, 머리 사본과 함께 캐시, 지문에 포함). 저장하면 `site.info` 캐시 갱신(`CacheAutoRefresh` `SITE_SETTINGS_SAVE`)으로 모든 api 서버가 몇 초 안에 반영한다. 관리자 미리보기 응답에도 들어간다(해 없음).
- 구글은 Search Console을 도메인(DNS TXT) 방식으로 확인했다면 비워 둬도 된다. 다음(Daum)은 메타 방식이 아니라 넣지 않았다.
- 시험 후보(아직 없음): admin GET/PUT 권한·400·null 정리·바뀐 때만 감사 / api `fill`이 값 있는 태그만 넣고 이스케이프하는지.
