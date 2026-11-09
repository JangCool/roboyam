# 올린 이미지 저장소 — local | r2 | s3

## 의도
앱 서버가 여러 대(WAS1·WAS2)여도 같은 이미지를 보게, 올린 이미지를 버킷에 둔다. 저장소 방식은 설정에서 `local`(서버 디스크) · `r2`(Cloudflare R2 — 운영 확정) · `s3`(AWS S3, MinIO 등 다른 S3 호환) 세 이름으로 고른다.
`r2`·`s3` 는 S3 API 하나로 만든 같은 구현이고 기본값만 다르다. local·dev 는 로컬 디스크가 기본이고 설정만으로 버킷을 붙여 시험할 수 있다. 화면·API·DB 는 바뀌지 않는다(서버 설정·내보내기만).

## 구조
- `ImageStorage`(shared `com.community.media.service`) — 구현 `LocalDiskImageStorage` · `S3ImageStorage`(r2·s3 공용). 빈은 `StorageConfig` 가 `roboyam.storage.type` 으로 고른다.
- 장부·업무 표에는 **키**(`post-image/202611/<32자>.webp` 등)만 둔다. 모든 구현이 같은 키를 쓴다 → 로컬 폴더를 그대로 버킷에 올리면 옮겨진다(DB 변경·마이그레이션 없음).
- 예외: 글 본문 HTML·공동구매 설명 HTML 에는 저장할 때의 공개 주소(`src="/media/…"`)가 박혀 있다 → `/media/**` 는 r2·s3 에서도 계속 연다.

## 방식별 기본값 (세션 정리 결정 2026-11-09 — 사용자 승인)
| 방식 | endpoint | region | 키 | 쓰는 곳 |
|---|---|---|---|---|
| `local` | — | — | — | local·dev 기본, 서버 한 대 구성 |
| `r2` | 비우면 `https://<계정 ID>.r2.cloudflarestorage.com`(계정 ID 필수) | 비우면 `auto` | R2 API 토큰 **필수** | **prod 기본** |
| `s3` | 비우면 AWS 기본 주소(MinIO 는 직접 + path-style) | **필수**(AWS `ap-northeast-2`, MinIO `us-east-1`) | 비우면 AWS 기본 자격 증명 체인(EC2 IAM 역할) | AWS 로 옮길 때·MinIO |

직접 준 endpoint·region 은 늘 이긴다.

## 설정 (`roboyam.storage.*`, 환경 변수)
| 칸 | 환경 변수 | 기본 | 설명 |
|---|---|---|---|
| `type` | `ROBOYAM_STORAGE_TYPE` | local·dev `local`, prod `r2`(`roboyam-shared-prod.yml`) | `local` \| `r2` \| `s3` — 어느 프로필이든 이 값이 먼저 |
| `local-path` | `ROBOYAM_STORAGE_PATH` | `/var/lib/roboyam/media`(local 프로필 `~/.roboyam/media`) | local 폴더 |
| `r2.account-id` | `ROBOYAM_STORAGE_R2_ACCOUNT_ID` | — | r2: endpoint 를 만든다 |
| `s3.endpoint` | `ROBOYAM_STORAGE_S3_ENDPOINT` | 위 표 | 버킷 공통(r2·s3) |
| `s3.region` | `ROBOYAM_STORAGE_S3_REGION` | 위 표 | |
| `s3.bucket` | `ROBOYAM_STORAGE_S3_BUCKET` | — (필수) | |
| `s3.access-key` · `s3.secret-key` | `ROBOYAM_STORAGE_S3_ACCESS_KEY` · `_SECRET_KEY` | 위 표 | 둘 다 주거나 둘 다 비운다. 비밀 키는 `ENC(...)`(ROBOYAM_CRYPTO_KEY — `EncryptedPropertyPostProcessor` 가 모든 설정에 적용) |
| `s3.path-style` | `ROBOYAM_STORAGE_S3_PATH_STYLE` | false | MinIO true |
| `s3.public-base-url` | `ROBOYAM_STORAGE_S3_PUBLIC_BASE_URL` | 비움 | 새 이미지 주소의 머리(R2 사용자 도메인) |

## 기동 정책 (결정)
- r2·s3 인데 설정이 틀리면(버킷·지역·계정 ID 없음, 키 한쪽만, r2 인데 키 없음, 주소 형식) **어느 프로필이든 기동을 멈춘다**(로그에 칸 이름만, 값·비밀은 안 싣는다). 근거: 조용히 로컬로 물러나면 서버 여러 대가 서로 다른 곳에 쓰는 문제가 아무도 모르게 돌아온다. 설치기·로드밸런서가 readiness 로 바로 알아챈다.
- 버킷에 **닿지 못하는 것**(네트워크·권한)은 경고만 남기고 뜬다 — 올리기·읽기가 503 `STORAGE_UNAVAILABLE`(로컬 폴더를 못 만들 때와 같다). 잠깐의 장애로 앱 전체가 안 뜨는 일을 막는다.
- prod 에서 `public-base-url` 이 비면 경고(앱이 이미지를 대신 내보내 송신량이 는다).
- SDK: AWS SDK for Java v2 `s3` + URLConnection HTTP 클라이언트(Apache·Netty 제외), 체크섬은 필요할 때만(R2·MinIO 호환).

## 내보내기 (결정)
- 새 이미지 주소 = `public-base-url` 이 있으면 `<공개 주소>/<키>`(브라우저가 Cloudflare CDN 에서 바로 — 앱 송신 없음), 없으면 `/media/<키>`.
- `/media/**`(api·admin 둘 다): local 은 폴더, r2·s3 는 **버킷에서 읽어 내보낸다**(`BucketMediaResolver` — 공개 주소가 있어도 연다: 예전 본문의 `/media/…` 호환). 응답 1년 immutable 캐시라 앞단 Cloudflare 가 캐시한다. `/media/default/**`(jar 안 기본 이미지)는 그대로.
- 객체에 Content-Type(확장자) + `Cache-Control: public, max-age=31536000, immutable` 을 붙인다(키에 버전 토큰 — 바뀌면 주소가 바뀐다).
- 올리기 한도·처리 관문·`file-orphan-cleanup`·기본 이미지는 저장소와 무관하게 같다(모두 `ImageStorage` 만 부른다). 삭제는 버킷도 멱등.

## 로컬 → 버킷 옮기기
`rclone copy /var/lib/roboyam/media r2:<버킷> --exclude '.upload-*' --header-upload 'Cache-Control: public, max-age=31536000, immutable'` 후 `ROBOYAM_STORAGE_TYPE=r2` 재기동(installer-native/README.md 「올린 이미지」). local·dev 에서 R2·MinIO 시험도 같은 환경 변수만 준다.

## 미확정
- 실제 R2 연결 시험 안 함(키 필요) — 첫 설치 때 확인: 기동 로그 「이미지 저장소: r2 버킷」, 로고 올리기 → 두 서버에서 보이기, 공개 주소 `cf-cache-status`.
- 본문 HTML 에 박힌 주소: 버킷 + 공개 주소 전환 뒤 새 글은 `https://media…`, 예전 글은 `/media/…`(앱 경유). 예전 본문을 새 주소로 다시 쓰는 일괄 변환은 하지 않았다(필요하면 배치로).
- 화면이 이미지를 `fetch`·canvas 로 읽는 곳이 있으면 버킷 공개 도메인에 CORS 가 필요하다(확인 안 함 — `<img>` 는 필요 없다).
- 외부 공개 주소로 바로 링크하므로 지운 이미지가 CDN 캐시에 최대 1년 남을 수 있다(로컬도 브라우저 캐시는 같다). 즉시 지워야 하면 Cloudflare 캐시 지우기.
