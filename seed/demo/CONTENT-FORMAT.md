# seed/demo/content/*.json 형식 (데모 데이터 원고)

로더 `scripts/seed-demo.mjs` 가 읽는다. 글쓴이·시각·추천 수·조회수는 로더가 정한다(원고에 적지 않는다).
배열 순서 = 오래된 글 → 최근 글 (로더가 지난 21일에 순서대로 흩는다).

```jsonc
{
  "board": "ai",                       // 게시판 slug
  "posts": [
    {
      "key": "ai-01",                  // 파일 안에서 유일, <slug>-<두 자리>
      "title": "제목(2~60자)",
      "prefix": "생성형AI·LLM",         // 그 게시판 말머리 이름 그대로(없으면 생략)
      "paragraphs": ["문단", "문단"],   // 평문. 로더가 <p> 로 감싼다. 빈 문자열 금지
      "images": [                      // 선택, 0~3장. 로더가 자리표시 이미지를 그려 본문 맨 앞(또는 imageAt 위치)에 넣는다
        { "kind": "photo", "caption": "이미지 안에 쓸 짧은 글(20자 안)", "scene": "무엇이 보이는지 한 줄" }
      ],
      "imageAt": 1,                    // 선택: 이미지를 몇 번째 문단 뒤에 넣을지(기본 0 = 맨 앞)
      "youtube": "dQw4w9WgXcQ",        // 선택: 허용 목록의 ID만
      "popularity": "hot",             // hot | normal | quiet — 추천·조회·댓글 양의 기준
      "adult": false,                  // 영상·짤 「전방·후방」 글만 true
      "comments": [
        { "text": "댓글 {emo}", "replies": ["답글", "답글"] },
        { "text": "영상 댓글", "youtube": "9bZkp7q19f0" }
      ]
    }
  ]
}
```

- `kind`: `photo`(사진 느낌) · `meme`(짤 — 위아래 큰 글자) · `screenshot`(화면 캡처 느낌) · `product`(제품 사진) · `chart`(그래프) · `illustration`(그림).
- 댓글 `text` 안의 `{emo}` 는 로더가 로보티콘 토큰(`:emo-ID:`)으로 바꾼다. 댓글 `youtube` 는 그 댓글 끝에 영상 카드로 붙는다(댓글 하나에 1개).
- 핫딜 글은 `deal` 을 더한다: `{ "storeName": "쿠팡", "productUrl": "https://...", "normalPrice": 129000, "dealPrice": 89000, "shippingFee": 0, "couponNote": "카드 10% 할인", "state": "active|ended|soldout" }`.

## 운영 게시판 원고 — `notice.json` · `bugs.json` · `requests.json` (게시판 글 단계가 아니라 따로 단계가 읽는다)

- 위 형식에 칸을 더한다(다른 원고에는 쓰지 않는다):
  - `daysAgo`(공지만): 며칠 전에 쓴 글로 둘지 — 그날 한국 시각 9~17시. 없으면 오늘. 버그·요청은 다른 게시판처럼 지난 21일에 흩는다.
  - `pinned`(공지만): 상단 고정 — 게시판 고정 한도(`pinnedMax`, 기본 2) 안에서.
  - `secret`: 비밀글(버그 신고 — 게시판 비밀글 `OPTIONAL`). 그 글의 댓글은 글쓴이·운영자만 단다(다른 사람은 읽지도 못한다).
  - `status`: 처리 상태를 차례로 바꾼다 — `[{ "to": "CHECKING" }, { "to": "DONE", "note": "운영진 메모(1~200자, 한 줄)" }]`. 값은 `RECEIVED`·`CHECKING`·`IN_PROGRESS`·`DONE`·`ON_HOLD`·`REJECTED`·`DUPLICATE`(새 글은 `RECEIVED`).
  - 댓글·답글 `by`: `"operator"` = 데모 운영자의 운영자 회원 계정(「로보얌운영팀」) · `"author"` = 글쓴이. 답글은 글자 또는 `{ "text": "…", "by": "operator" }`. `by` 가 없는 댓글·답글은 다른 데모 회원이 단다(이 세 원고에서는 글쓴이 몫 35% 규칙을 쓰지 않는다).
- 공지 본문·댓글의 `{date:N}` 은 로더가 「실행한 날 + N일」의 한국 날짜(`11월 18일(수)`)로 바꾼다. **제목에는 쓰지 않는다** — 같은 제목이면 건너뛰는 판정이 날마다 달라진다.
- 사이트 전체 데모(공지 띠·팝업·보상 이벤트·소모임 일정·공동구매 문의)는 `seed/demo/site.json` — 칸 설명은 그 파일의 `_readme`·`_comment`.
