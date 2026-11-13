# 목록 썸네일 계약 (초안 2026-11-13 — 사용자 확인 전)

## 의도
글 목록·가젯이 지금은 글의 첫 이미지(본문용, 긴 변 최대 2048px)를 그대로 불러온다. 글 이미지를 올릴 때 **목록 전용 작은 그림 하나**를 서버(Java)에서 함께 만들어 R2 에 두고, 목록은 그것을 쓴다.
Cloudflare 이미지 변환은 쓰지 않는다(고유 변환 월 5,000개 한도·외부 의존). 늘어나는 비용은 R2 저장 용량 약 15% 정도(1년 뒤에도 월 몇백 원)이고, CPU·메모리는 지금 서버 사양으로 충분하다.

## 범위
- 대상: 글 본문 이미지(`FilePurpose.POST_IMAGE`)만. 목록 썸네일은 모두 `PostThumbnails`(api)를 거친다 — 확인된 호출 `PostFeedRows`·`PostQueryService`(게시판 목록·최신글·피드·가젯·팁 등).
- 범위 밖: 소모임·사업자 광고·하이라이트·공동구매·팝업 이미지, 프로필(이미 256px 이하로 만듦), YouTube 썸네일(`hqdefault` 그대로), 본문 이미지 크기(지금 긴 변 2048 — 바꾸지 않는다).
- **화면은 고치지 않는다** — 응답의 `thumbnailUrl` 이 가리키는 파일만 작은 것으로 바뀐다.

## 썸네일 규격 (결정 제안)
화면이 그리는 자리는 모두 가로가 긴 칸(16:9·16:10·4:3·3:2·5:2)이고 `object-cover` 로 채운다. 가장 작은 곳은 64px(목록 줄), 큰 곳은 갤러리·카드 칸(약 180~350px)과 대표 카드(약 380~520px)다.
- **800×450 상자를 덮을 만큼** 비율을 지켜 줄인다(배율 = max(800/가로, 450/세로), 1보다 크면 줄이지 않는다 — 키우지 않음).
- 그 뒤 가로 800·세로 1200 을 넘는 쪽은 잘라낸다 — 가로는 가운데, 세로는 **위쪽**을 남긴다(긴 캡처 화면 대비).
- 형식: 투명이 없으면 JPEG 품질 0.80, 투명이 있으면 PNG. 메타데이터는 넣지 않는다(지금 `ImageCodec` 다시 인코딩과 같음).
- 움직이는 GIF: 첫 장면으로 만든다(목록에서는 멈춘 그림).
- 원본이 이미 상자보다 작으면(예: 600×400) 그 크기 그대로 JPEG/PNG 로 하나 만든다(규칙을 하나로 — 「썸네일이 있으면 늘 작은 파일」).
- 규격 숫자는 설정 `irobit.post-image.thumb-*`(가로·세로·최대 세로·품질)로 둔다.

## 저장·데이터
- 키: `post-image/{년월}/{원본과 같은 32자}-t.{jpg|png}` — 원본과 짝을 알아보기 쉽게. 1년 immutable 캐시는 원본과 같다.
- 장부: 파일 표에 `thumbnail_key`(null 허용) 칸 추가 — **shared V206**. null = 썸네일 없음 → 목록은 원본 주소를 쓴다(지금 동작 그대로).
- 올리기 순서(`PostImageUploads.store`): 처리 → 원본 `put` → 썸네일 `put` → 장부 저장. 썸네일 `put` 이 실패하면 원본도 지우고 지금과 같은 503(`STORAGE_UNAVAILABLE`). 장부 저장이 실패하면 둘 다 지운다.
- 썸네일 **만들기**가 실패하면(그림을 읽지 못하는 경우 — 원본 처리는 통과한 뒤라 드묾) 올리기는 성공시키고 `thumbnail_key` 만 비운다(로그 경고).
- 지우기: 글 이미지 파일을 지우는 곳 — `FileOrphanCleanupJob`(올리다 만 파일·버려진 파일 정리), `PostImageUploads.deleteQuietly`(실패 정리) — 에서 썸네일도 함께 지운다. 버킷 삭제는 멱등.
- 읽기: `FileRepositoryQDSL.firstImageKeys` 가 `coalesce(thumbnail_key, storage_key)` 를 돌려준다. 다른 곳은 바꾸지 않는다.

## 이미 올라간 이미지 채우기
- admin 배치 하나 추가 — 「글 이미지 썸네일 만들기」(**R__batch_44**, 기본 **꺼짐**, 관리자 배치 화면에서 수동 실행). 이미지를 디코딩해야 해서 pg_cron 이 아니라 admin(Java) 배치다(api 에 배치를 두지 않는 규칙).
- `IN_USE` 이고 `thumbnail_key` 가 null 인 글 이미지를 id 순으로 조금씩(한 번에 100장 정도, 처리 관문 `ImageGate` 아래) 만든다. 다시 돌려도 이미 있는 것은 건너뛴다.
- 사람이 적은 시간에 돌린다. 데모 팩 이미지는 다시 만든 데모 팩에서 썸네일이 함께 생긴다(설치 때 로더가 같은 올리기 경로를 쓰는지 구현 때 확인).

## API
- 바뀌는 계약 없음. 목록 응답의 `thumbnailUrl` 이 썸네일 주소(없으면 원본)를 가리킨다.

## 결정 (사용자 2026-11-13 — 제안대로)
1. **WebP 로 올린 이미지** — TwelveMonkeys `imageio-webp`(순수 Java, **읽기 전용**, BSD-3) 를 shared 의존성에 추가해 WebP 도 썸네일을 만든다. 썸네일은 **WebP 가 아니라 JPEG/PNG**(WebP 쓰기는 네이티브 libwebp 가 필요해 하지 않는다). 움직이는 WebP 는 첫 장면.
   - WebP **원본** 처리는 그대로(컨테이너만 정리, 줄이지 않음). 다른 이미지 처리(로고·아바타·닉네임 이미지·핫딜 OG 등)의 받는 형식·동작도 바꾸지 않는다 — WebP 읽기는 썸네일 만들기에서만 쓴다.
   - 오픈소스 라이선스 고지에 TwelveMonkeys 를 더한다.
2. **큰 자리**(홈 하이라이트 대표 16:9·5:2 띠, 팁 대표 카드, 모바일 한 칸 카드) — 처음엔 모두 썸네일을 쓴다. 흐리면 그 자리만 원본을 쓰게 응답에 칸을 더한다(그때 화면도 고침).
3. 규격 800×450 상자 덮기 · 최대 세로 1200 · JPEG 0.80.

## 2단계 — 대표 썸네일 바꾸기 (사용자 결정 2026-11-13, 화면 목업 먼저 → 확정 후 서버는 1단계와 묶어 구현)
- **글 단위**: 글 하나에 대표 썸네일을 정하면 게시판 목록·최신글·모든 가젯에 같이 반영된다(하이라이트는 자기 대표 이미지가 있으면 그것이 먼저 — 지금 그대로).
- **고르는 곳**: 글에 붙은 이미지 중에서 고르기 · 새 그림 올리기 · 「썸네일 없음」(목록에 그림을 안 보임) · 「자동」(첫 이미지 — 지금 동작).
- **누가**: 글쓴이(공개 사이트 글쓰기·고치기 화면) + 운영자(관리자 게시글 관리 `/posts` 글 상세 창). 운영자가 정하면 운영자 것이 이긴다.
- **글쓴이 고르기는 게시판 옵션**(사용자 결정 2026-11-13): 게시판 설정 「글쓴이가 대표 이미지 고르기」 — **기본 꺼짐**. 켠 게시판에서만 글쓰기·고치기에 「대표 이미지」 줄이 보인다. 꺼진 게시판은 자동(첫 이미지). 운영자 교체는 게시판 옵션과 관계없이 늘 된다.
  - 옵션을 껐을 때 이미 글쓴이가 고른 글은 그대로 둔다(목업 가정 — 확정 때 다시 본다).
- 고른·올린 그림도 1단계와 같은 방식으로 작은 썸네일을 만든다(목록은 늘 작은 파일).
- 목업 가정(화면 확정 때 다시 본다): 운영자가 정한 동안 글쓴이 화면은 잠김 안내만 보이고 못 바꾼다 · 운영자 「자동으로 되돌리기」는 **운영자 설정과 글쓴이 설정을 모두 지우고 첫 이미지로** 돌리며 잠금을 푼다(사용자 결정 2026-11-13 — 운영자가 바꾼 문제 그림이 되살아나지 않게) · 소모임 글쓰기는 범위 밖 · 올리기 한도는 글 이미지와 같다(10MB, JPEG·PNG·WebP·GIF).

## 서버 구현 결과 (2026-11-13 — 커밋 전, 프론트 MOCK 은 아직 그대로)
- **DB**: shared **V206** — `files.thumbnail_key` + 부분 색인 `ix_files_thumbnail_missing` · 파일 용도 `POST_THUMBNAIL`(CHECK 다시 만듦 — 직전 V178) · `post_thumbnails`(post_id 키, mode IMAGE·UPLOAD·NONE, set_by AUTHOR·OPERATOR, file_id, set_by_id — 행 없음 = 자동) · `boards.author_thumbnail_enabled`(NULL = 기본값) · `board_defaults.author_thumbnail_enabled`(NOT NULL DEFAULT false). **V207** — 오픈소스 고지 직접 추가 항목 「TwelveMonkeys ImageIO」(SERVER, BSD-3-Clause).
- **썸네일 만들기**: shared `PostThumbnailMaker`(규격 위 그대로, 설정 `irobit.post-image.thumb-width·thumb-height·thumb-max-height·thumb-quality`) · `PostImageUploads` 가 원본 put → 썸네일 put(실패 시 원본도 지우고 503) → 장부. WebP 는 TwelveMonkeys `imageio-webp` 3.15.3(runtimeOnly, 썸네일 만들기의 `ImageCodec#decodeFirstFrame` 만 이름 "webp" 로 고른다). `ImageCodec` 은 읽기 스트림을 직접 만든다(imageio-core 가 등록하는 InputStream 스트림 플러그인과 무관하게 예전과 같은 스트림).
- **채우기 배치**: Java 배치 `post-image-thumbnail-backfill`(카탈로그 `POST_IMAGE_THUMBNAIL_BACKFILL`, 기본 꺼짐 — 켜면 매일 04:15) — Java 배치라 `R__` 파일은 없다.
- **목록 결정**: shared `PostThumbnailChoices#listUrls`(api `PostThumbnails` 가 위임) — 질의 최대 4번(선택·파일·첫 이미지·YouTube).
- **API**
  - 공개 `GET /api/boards/{slug}` → `authorThumbnailEnabled`(유효 정책).
  - 공개 `POST /api/images/thumbnails`(multipart `file`) → 201 `{ id, url, width, height }` — `/api/images` 와 같은 검사·속도 제한·하루 한도(함께 센다). `url` = 목록 썸네일 주소. ⚠ 목업 REAL 줄은 `/api/images` 였다 — 용도를 나누려고 주소를 따로 둠.
  - 공개 `POST /api/posts`·`PUT /api/posts/{id}` 의 `thumbnail?: { mode, imageId?, fileId? }` — 회원 글 + 게시판 정책 켬일 때만(아니면 무시). 400 `fields.thumbnail`(모르는 방식·본문에 없는 이미지·쓸 수 없는 올린 그림) · 409 `THUMBNAIL_LOCKED`(운영자가 정한 글 — 고치기 전체 저장 안 됨).
  - 공개 글 응답 `thumbnail: { mode, setBy, imageId, fileId, url } | null` — 글쓴이 본인(회원 글·소모임 글 아님)에게만.
  - 관리자 `POST /api/admin/files` `purpose=POST_THUMBNAIL`(post.manage, 10MB·JPEG·PNG·WebP·GIF) → `{ fileId }`.
  - 관리자 `PUT /api/admin/posts/{id}/thumbnail { mode, imageId?, fileId? }`(post.manage) → 204. 400 `fields.mode`·`fields.imageId`·`fields.fileId`, 404 `POST_NOT_FOUND`. AUTO = 행 삭제(잠금 해제). 감사 `POST_THUMBNAIL`(조치 기록 표에는 남기지 않음).
  - 관리자 글 상세 `thumbnail: { mode, setBy, imageId, fileId, url, autoUrl, autoFrom, images[{ imageId, url }] } | null`(비공개 소모임·원문 파기 글 null). 고른 이미지를 본문에서 빼면 `mode AUTO`(운영자가 정한 것이면 `setBy OPERATOR` 유지).
- **반영 시점**: 게시판 목록은 바로, 최신글(`home.latest` 60초)·가젯 데이터(`gadget.*` 60~120초)는 캐시 TTL 안 — 글 고치기와 같다.

## 미확정
- 홈 하이라이트의 `imageOf(h)` 는 하이라이트 자체 이미지를 먼저 쓰는지 확인 안 함(하이라이트 대표 이미지는 범위 밖).
- 사업자 광고·소모임·공동구매 이미지에도 같은 방식을 넓힐지는 나중에.
- 같은 이미지 중복 저장 막기(해시)는 하지 않는다 — 키가 무작위라 지금 구조와 맞지 않고 이득이 작다.
