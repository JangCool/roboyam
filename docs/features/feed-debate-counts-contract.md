# 섞인 목록·하이라이트의 찬반 수 + 하이라이트 작성자 옵션 + 개인 단계 푸터 (계약)

찬반 토론 글(post-debate-contract)의 입장 수를 글 상세에 들어가지 않고도 최신글·인기글·하이라이트 줄에서 보이게 한다. 하이라이트 줄의 메타를 추천·댓글·작성자 중심으로 바꾸고 작성자 표시를 가젯 옵션으로 둔다. 사용자 시안 확정 2026-11-10.

## 화면 경로
- `/latest` · `/popular` · `/search` · `/users/{id}` 작성글 — `components/feed/FeedPostItem.vue`
- 홈·가젯 페이지의 하이라이트 가젯 — `components/gadget/GadgetHighlight.vue` · `GadgetHighlightMeta.vue` · `GadgetDebateBand.vue`(/tips 「이번 주 추천」은 따로 그린 화면이라 이번 범위 밖)
- 관리자 홈 편집·가젯 페이지 편집 ⚙ 설정(하이라이트) — admin-web `GadgetSettingsDialog.vue`
- 푸터 — `components/shell/AppFooter.vue`

## API
- 섞인 목록 줄(`PostFeedRowView` — 최신글·인기글·검색 `SearchRowView`·회원 작성글·홈 가젯·하이라이트 `post`·팁 `TipsPostView`)에 `debate: { pro, con, neutral | null } | null`.
- **게시판 글 목록 줄**(`GET /api/boards/{slug}/posts` 의 `PostRowView`)에도 같은 `debate`(2026-11-12 사용자 「게시판 목록에 찬반중이 안 나온다」) — 같은 `PostFeedRows#debates`(쪽마다 질의 두 번), 숨긴 글·읽을 수 없는 비밀글은 null. 화면: 데스크톱은 날짜 뒤·추천 앞(`BoardPostRowDesktop` `stats-lead`), 모바일은 추천·조회 앞(세로 구분선), 네이버 테마는 윗줄 숫자 앞.
  - 토론 글 = 토론이 열려 있거나(`post_debates` 행) 이미 표가 있는 글(글 상세 토론 구획이 보이는 기준 `UtilDebate.visible` 과 같다). 그 밖·숨긴 글은 null.
  - 수 = 입장을 고른 사람 수(`post_stances`, 글 상세 `debate.votes` 와 같은 `DebateQueries#votesOf`).
  - 열린 토론이 중립을 받지 않으면(`neutralEnabled = false`) `neutral` null. 닫힌 토론(표만 남음)은 글 상세처럼 중립을 받는 것으로 본다.
  - 쪽마다 한 번에 센다(`PostFeedRows#debates` — `post_debates` 질의 한 번 + 입장 묶어 세기 한 번, N+1 없음). 인기글·최신글·가젯 캐시 줄에도 실린다(캐시 주기만큼 늦을 수 있다).
- 하이라이트 가젯 설정 `showAuthor`(boolean, 기본 true — 칸이 없는 옛 가젯도 true). 서버 `GadgetConfigs`(읽기 기본값)·`GadgetPatches`(검사: boolean 아니면 `homeLayout.gadget.config.option`). 마이그레이션 없음.

## 결정
- 색은 칩이 아니라 글자 색: 찬 = 파랑(`stance-pro-text`) · 반 = 빨강(`stance-con-text`) · 중 = 흐린 회색. 화면 읽기는 「찬성 4, 반대 9, 중립 2」 한 문장(`feed.debateSummary`).
- 글 줄: 조회수를 보인다(섞인 목록도). 칩 위치 「제목 위」는 윗줄 오른쪽 찬·반·중 → 세로 구분선 → 추천 → 조회, 「제목 앞」·「닉네임 아래」는 메타 줄(글쓴이 다음).
- 하이라이트: 조회수는 넣지 않는다. 사진 안 위쪽 폭 전체 띠(반투명 검정 + 밝은 글자) — 큰 사진은 이름표 포함 「찬 0 · 반 3 · 중 3」, 작은 사진은 색 숫자만(99 넘으면 「99+」) + 전체 수 툴팁(데스크톱 마우스 올림 · 터치는 띠를 누름(글로 가지 않음) · 키보드 초점). 사진을 그리지 않는 줄은 메타 줄에 글자 색으로.
- 하이라이트 메타 = 게시판 이름 → 추천 → 댓글(말풍선+숫자) → 오른쪽 끝 [아바타 · 닉네임] · 날짜. 로보얌 오른쪽 작은 목록 줄만 추천을 제목 줄 오른쪽 끝으로(제목은 추천 칸 앞에서 두 줄 말줄임). 작은 목록 썸네일 폭 80 → 88px.
- `showAuthor` 를 끄면 아바타·닉네임만 숨기고 날짜는 남긴다.

## 푸터
- 사업자 정보가 없을 때(운영 주체 개인 단계) 데스크톱(md 이상)은 가로 3열: 1열 사이트 이름 · 문의 이메일(있을 때) · © / 2열 「약관·정책」 + 이용약관 · 개인정보처리방침(굵게) · 운영정책 / 3열 「고객 지원」 + footer 메뉴 링크(없으면 열 없음). 사업자 정보가 있을 때와 모바일은 예전 모양.

## 미확정
- 닫힌 토론에 표가 남은 글도 찬·반·중을 보인다(글 상세와 같은 기준) — 숨기기를 원하면 `PostFeedRows#debates` 의 조건만 바꾼다.
- /tips 「이번 주 추천」은 하이라이트 가젯과 다른 화면이라 찬반 표시를 넣지 않았다(서버 줄에는 `debate` 가 온다).
