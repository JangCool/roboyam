# 움직이는 아바타·아바타 올리기·변경권·목록 아바타 계약 (화면 목업 2026-11-06 — **화면 확정 2026-11-06**(「Confirm, do server」) · 서버 구현 2026-11-06 — 미커밋, 「서버 구현 결과」)

**의도**: 게시판 목록의 닉네임 왼쪽에 작은 아바타를 보인다. 포인트 상점에 움직이는 아바타를 더해 정지 유료 아바타처럼 사서 프로필로 쓴다. 움직이는 아바타와 움직이는 닉네임 이미지는 동시에 쓰지 못하고 하나만 쓴다(사용자 지시 2026-11-06). **사용자 본뜻은 「변경권」**: 변경권을 사면 직접 만든 그림(정지·움직이는 GIF)을 아바타로 올린다 — 카탈로그 움직이는 아바타도 「괜찮다」고 해 둘 다 둔다. 커머스 영역이라 서버 단계에서 아래 규칙을 모두 테스트로 막는다(commerce.md 18절).

## 화면 경로 (user-web, 목업)
| 경로 | 바뀐 것 |
|---|---|
| `/boards/{slug}` 목록형(기본·Daum·Naver 테마) | 닉네임 왼쪽 20px 아바타(`PostAuthorLabel avatar` — 장착 테두리 포함, 비회원·탈퇴는 이니셜). 데스크톱 표에서는 아바타도 프로필 링크 안. **움직이는 아바타는 목록에서 정지 그림**(닉네임 이미지 GIF 와 같은 규칙) |
| `/point-shop?kind=PROFILE_AVATAR` | 무료 다음 첫 칩 「움직이는 아바타」(목업 8종, 3,000P 영구), 칸 왼쪽 위 「움직임」 표시, 미리보기 「움직임」 꼬리표 + 하나만 안내 |
| `/settings/profile` 아바타 고르기 · `/me/items` 아바타 구획 · 상점 「바로 프로필로 쓰기」 | 고르기 전 확인을 `useAvatarChoice().confirmChoose` 하나로(환불 불가 + 하나만 충돌을 대화상자 하나에) |
| `/settings/avatar-upload`(새, 설정 메뉴 「아바타 올리기」) | 아바타 변경권 장 수·예약 표시·상점 링크 · 신청(PNG·JPG·WebP·GIF, 정사각형 ±2%, 96px 이상, 300KB 이하, 움직이는 WebP 거절 — 원 안 미리보기 + 글 보기·글 목록 크기) · 승인 대기(예약 안내, 취소 → 반환) · 반려(사유, 반환 안내) · 내가 올린 아바타(사용 중/보관, 다시 쓰기는 변경권 없음, 지우기는 반환 없음) · 개발 스위치(포인트 넉넉히·운영자 승인/반려) |
| `/point-shop`(전체·변경권 탭) · `/point-shop/{id}` · `/me/items` | 변경권 두 품목 「닉네임 이미지 변경권」(2,000P)·「아바타 변경권」(3,000P) — 소모형, 상세에 안내 다섯 줄, 사면 「…하러 가기」. 내 아이템 변경권 카드에 「신청에 예약됨」 꼬리표·쓰는 곳 링크 |
| 프로필 설정 아바타 고르기 | 「내가 올린 아바타」 칸(승인된 한 장) · 「직접 만든 그림 올리기」 링크 |
| admin `/members/avatar-uploads`(새, 회원 메뉴 「아바타 올리기 심사」 — 닉네임 이미지 심사 다음) | 닉네임 이미지 심사와 같은 틀: 탭(심사 대기 수)·닉네임 검색·쪽 표·회원 서랍. 원형 미리보기(움직임 표시, 누르면 원본 + 공개 사이트 자리 크기 프로필 112·글 보기 24·글 목록 20=정지) · 변경권 칸(예약됨/사용/반환) · 승인된 그림의 「지금 프로필 아바타 / 다른 아바타를 쓰는 중 / 새 그림으로 바뀜」 · 대기 중 움직이는 GIF 가 회원의 움직이는 닉네임 이미지를 쉬게 하면 회원 칸에 안내 · 승인은 바로, 반려(변경권 반환 안내)·해제(지금 그림만, 반환 없음 안내)는 사유 1~200자. 권한 후보 = 닉네임 이미지와 같은 `nickname-image.read`·`.manage`. 목업 7건(메모리) |
| `/settings/nickname-image` | 신청 폼에 닉네임 이미지 변경권 줄(0장이면 신청 막음·상점 링크), 대기 카드에 예약 안내, 해제 확인에 「변경권은 돌려받지 않아요」 · 움직이는 닉네임 이미지 + 움직이는 아바타면 「쉬는 중」(지우지 않음) + [닉네임 이미지 다시 쓰기]. 움직이는 아바타를 쓰는 중 GIF 를 고르면 안내 줄. 개발 전용 스위치(목업 그림을 적용 중으로) |
| 글 보기 머리·공개 프로필·`/me`·상점 미리보기 | 움직인다. 움직임 줄이기(`prefers-reduced-motion`)면 `<picture>` 로 정지 그림 |

## 규칙 (서버가 판정 — 테스트로 막을 것)
0. **변경권(사용자 확정 2026-11-06 — community `docs/api-nickname-image.md` 방식)**: 닉네임 이미지·아바타 올리기 모두 신청마다 변경권 1장. 신청하면 그 신청에 **예약**(같은 트랜잭션, 동시 신청에도 한 장만) → **승인되면 사용** · **반려·본인 취소·새 신청으로 대체**되면 같은 장 **반환**(대체는 예약된 장을 그대로 넘겨받아 한 장 더 들지 않는다) · 적용 뒤 해제·지우기는 반환 없음. 예약된 장은 쓸 수도 환불할 수도 없다. 안 쓰고 예약되지 않은 장은 소모형 환불 규칙(기한 안 셀프 환불) 그대로. 변경권이 없으면 파일 처리 전에 `409 …_TICKET_REQUIRED`(맨 위 `ticketCount`·`ticketPrice`).
0-1. **아바타 올리기 신청 조건 = 이메일 인증·정지 아님 + 변경권뿐**(닉네임 이미지의 관리자 신청 조건은 걸지 않는다 — 사용자 확정). 닉네임 이미지는 지금 신청 조건 위에 변경권이 더 든다(사용자 확정).
0-2. **아바타 올리기 승인** = 「내가 올린 아바타」 한 장(새로 승인되면 바뀐다)이 되고 바로 프로필 아바타로 쓴다(나중에 고른 쪽이 남는다 — 움직이는 GIF 면 움직이는 닉네임 이미지는 쉰다). 다른 아바타로 바꿨다가 다시 쓸 때 변경권 없음. 서버가 움직이는 GIF 의 첫 장면 정지 그림을 만든다.
1. **움직이는 꾸미기는 하나만** — 움직이는 아바타 ↔ 움직이는 닉네임 이미지(정지 그림 `still` 이 있는 GIF). 정지 닉네임 이미지 + 움직이는 아바타, 움직이는 닉네임 이미지 + 정지 아바타는 함께 쓸 수 있다.
2. **나중에 고른 쪽이 남는다**: 움직이는 아바타를 고르면 닉네임 이미지는 **쉰다**(작성자 표시에 글자 닉네임). 지우지 않고 다시 쓸 때 심사하지 않는다. 쉬는 중은 따로 저장하지 않는다 — 「움직이는 아바타 사용 중 && 그림이 움직임」에서 나온다.
3. 닉네임 이미지 **다시 쓰기**, 또는 움직이는 아바타 사용 중에 움직이는 GIF 가 **승인**되면 아바타는 **마지막으로 쓴 정지 아바타**(없으면 `basic-default`)로 돌아간다 → 서버가 마지막 정지 아바타를 기억해야 한다.
4. 사기는 막지 않는다(충돌은 고를 때만). 환불·정지 중 고르기 금지·환불 신청 대기 중 고르기 금지는 정지 유료 아바타와 같다(1-2 규칙 그대로).
5. 목록 줄에서는 움직이는 아바타도 정지 그림(움직임 0) — 글 보기·댓글·프로필에서만 움직인다.

## 서버에 필요한 데이터 (후보)
- 품목 종류 `NICKNAME_IMAGE_TICKET`·`AVATAR_UPLOAD_TICKET`(CONSUMABLE) — `user_items` 에 예약 칸(예약 시각·신청 ID, community `used_at`·`used_ref` 방식), 내 아이템 줄 `reserved`. 상점 「변경권」 탭은 세 변경권 종류를 함께.
- 아바타 올리기: `GET·POST·DELETE /api/me/avatar-upload`·`DELETE /api/me/avatar-upload/request`(닉네임 이미지와 같은 모양 — `ticketCount`·`ticketPrice`·`request`·`current`·`inUse`), 관리자 `GET /api/admin/avatar-uploads`·`POST …/{id}/approve|reject|revoke`(오류 `404 AVATAR_UPLOAD_NOT_FOUND`·`409 AVATAR_UPLOAD_NOT_PENDING|NOT_ACTIVE`) — 화면 목업 있음. 서버 단계에 더할 것: `AdminScreenRegistry` 한 줄(`avatarUploads`), 운영자 알림 `AVATAR_UPLOAD_PENDING`(닉네임 이미지 대기 알림과 같은 방식), 회원 알림(승인·반려·해제), 감사 행위, 대시보드 심사 대기 가젯(닉네임 이미지 가젯과 합칠지). 카탈로그에 내가 올린 아바타 줄(`itemId: null`, 상점에 없음).
- 닉네임 이미지: `GET` 에 `ticketCount`·`ticketPrice`, 신청에 변경권 예약, 반려·취소·대체 반환.
- 카탈로그 `GET /api/avatars`: 카테고리 `animated`, 줄 `animated`·`stillUrl`. 아바타 품목은 그대로 `PROFILE_AVATAR`(PERMANENT) — 움직이는 아바타도 품목 행 + 카탈로그 자산(움직이는 파일 + 정지 그림, 크기·프레임 상한).
- 작성자(`PostAuthor`)·공개 프로필·`MyProfile`·내 아이템 줄: `avatarStillUrl`(움직이는 것만), 내 아이템 줄 `animated`.
- 프로필: 마지막 정지 아바타(예 `profiles.last_still_avatar`).
- 닉네임 이미지: `POST /api/me/nickname-image/resume`(다시 쓰기 → 아바타 정지로) · 승인 처리에서 움직이는 아바타 되돌리기 · (작성자 표시에 닉네임 이미지를 보이게 되면) 쉬는 중이면 안 보임.

## 목업 가정·한계
- 변경권 값 2,000P(닉네임 이미지 — community 기본)·3,000P(아바타)는 가정. 아바타 그림 규격(300KB·96px·정사각형)도 가정.
- 닉네임 이미지 변경권은 서버가 몰라서 장 수·예약을 이 기기에서 맞춘다(신청 성공 → 예약, 취소 → 반환, 다시 읽을 때 반려 → 반환·승인 → 사용). 자격 미달 회원에게는 신청 폼이 안 보여 변경권 줄도 안 보인다.
- 아바타 올리기 그림은 data URL 로 이 기기에 저장(서버에 올리지 않는다). 내 글에만 보인다.
- 목업 자산: `apps/user-web/public/avatars/motion/`(움직이는 SVG 8종 + 정지 그림 8 + 닉네임 이미지 목업 2). `public/avatars/roboyam/`(카탈로그 328 시험 대상)과 따로 뒀다.
- 목업 상태: `composables/avatar/api/mock/motion-avatar-mock.ts`(회원별 localStorage). 사기는 잔액을 실제로 줄이지 않는다. 내 글에만 보인다(서버는 모른다). 서버가 붙으면 이 파일과 `// MOCK:` 줄을 지운다(`useAvatarApi`·`usePointShopApi`·`useMyItemsApi`·`useMyDecoration`·`useAvatarChoice`·닉네임 이미지 화면).
- 가격 3,000P·카테고리 하나(「움직이는 아바타」)는 가정.

## 화면 확정 때 굳힌 것(2026-11-06 — 목업 가정을 그대로 확정)
- 목록에서 움직이는 아바타는 **정지 그림**(닉네임 이미지 GIF 와 같은 규칙). 값: 닉네임 이미지 변경권 2,000P · 아바타 변경권 3,000P · 움직이는 아바타 8종 3,000P 영구(관리자가 바꿀 수 있다). 올리기 규격: PNG·JPG·WebP·GIF, 정사각형 ±2%, 96px 이상, 300KB 이하, GIF 30장면 이하, 움직이는 WebP 거절, 서버가 256px 이하로 맞추고 움직이는 GIF 는 원본 + 첫 장면 PNG 정지 그림. 권한: 아바타 올리기 심사 = 닉네임 이미지 심사와 같은 `nickname-image.read`·`.manage`. 회원 쪽 막힘: 이메일 인증 전 `403 EMAIL_NOT_VERIFIED`·정지 `403 ACCOUNT_RESTRICTED`(닉네임 이미지와 같다). 마이그레이션은 shared **V157**(profile-privacy 는 V158).

## API 계약(서버 단계 — 화면 목업의 `// MOCK:` 모양 그대로)
- **상점**: 품목 종류 `NICKNAME_IMAGE_TICKET`·`AVATAR_UPLOAD_TICKET`(CONSUMABLE). `GET /api/point-shop/items?kind=NICKNAME_CHANGE_TICKET` 는 「변경권」 탭이라 세 변경권을 함께 준다. 사기·환불은 기존 주소 그대로. `MyItemView.reserved`(예약된 장 — 장착·환불 불가, 셀프 환불 `409 POINT_SHOP_REFUND_NOT_ALLOWED`). 관리자 품목 추가의 종류에 두 변경권.
- **카탈로그** `GET /api/avatars`: 카테고리 `animated`, 줄 `animated`·`stillUrl`. 움직이는 아바타 8종 = 카탈로그 자산(`server/shared/.../avatars/manifest.json` + 두 앱 `public/avatars/motion/<id>.svg`·`<id>-still.svg`)과 품목 행. 승인된 내가 올린 아바타는 카테고리 `upload` 줄 하나(`id: upload-mine`, `itemId: null`, 상점에 없음, 고르기 `PUT /api/me/profile/avatar { avatarId: "upload-mine" }`).
- **표시**: `PostAuthor`·공개 프로필·`MyProfile` 에 `avatarStillUrl`(움직이는 아바타만, 그 밖 null). 내 아이템 줄 `animated`·`avatarStillUrl`.
- **아바타 올리기(api)**: `GET /api/me/avatar-upload` → `{ ticketCount, ticketPrice, reserved, request: { id, status: PENDING|REJECTED, url, stillUrl, animated, createdAt, rejectReason } | null, current: { url, stillUrl, animated, approvedAt } | null, inUse, limits }` · `POST /api/me/avatar-upload`(multipart `file`, 201 같은 모양, `409 AVATAR_UPLOAD_TICKET_REQUIRED` 맨 위 `ticketCount`·`ticketPrice`, 파일은 `400 VALIDATION fields.file`) · `DELETE /api/me/avatar-upload/request`(대기 취소 — 반환) · `DELETE /api/me/avatar-upload`(지우기 — 반환 없음, 쓰는 중이면 마지막 정지 아바타).
- **아바타 올리기(admin)**: `GET /api/admin/avatar-uploads?status=&keyword=&page=&size=` → `{ items[AvatarUploadView], total, page, size, pendingCount }` · `POST …/{id}/approve` · `POST …/{id}/reject { reason }` · `POST …/{id}/revoke { reason }`(사유 1~200자). 줄 모양은 admin-web `useAvatarUploadApi.types.ts`.
- **닉네임 이미지**: `GET /api/me/nickname-image` 에 `ticketCount`·`ticketPrice`(+ 대기 신청 `request.ticketReserved`), 신청에 예약·`409 NICKNAME_IMAGE_TICKET_REQUIRED`, 반려·취소·대체 반환. `POST /api/me/nickname-image/resume` → 내 프로필(아바타를 마지막 정지 아바타로 — 움직이는 아바타를 쓰는 중일 때만 의미). 이 규칙 전에 올라온 대기 신청은 예약한 장이 없어 돌려줄 것도 없다.
- **알림·감사·메뉴**: 운영자 알림 `AVATAR_UPLOAD_PENDING`, 회원 알림 `AVATAR_UPLOAD_REVIEWED`(승인·반려·해제, 사유), 감사 행위 승인·반려·해제(일반 운영), `AdminScreenRegistry` `avatarUploads`(`/members/avatar-uploads`, `nickname-image.read`).

## 미확정
- 지금 작성자 표시에는 닉네임 이미지가 없다(서버 응답에 칸 없음 — 기존 결정). 하나만 규칙은 상태로만 지켜지고, 화면에 닉네임 이미지가 보이는 곳은 닉네임 이미지 설정의 미리보기뿐.
- 카드형·갤러리형 목록·댓글·홈 가젯에는 목록 아바타를 넣지 않았다.

## 서버 구현 결과 (2026-11-06 — 미커밋, worktree `claude/animated-avatar`)
- **마이그레이션** shared `V157__motion_avatar_upload_tickets.sql`(develop V156 다음 — profile-privacy 는 V158 로 미룸). 배치 없음.
  품목 종류 CHECK ∪ `NICKNAME_IMAGE_TICKET`·`AVATAR_UPLOAD_TICKET`(소모형만) · 시드 `nickname-image-ticket` 2,000P·`avatar-upload-ticket` 3,000P · 움직이는 아바타 8종 품목(3,000P, sort 307~314) ·
  `user_items.reserved_at`·`ref_type`·`ref_id`(예약 = OWNED + 셋 채움, 반환 = 셋 지움, 사용 = USED + `reserved_at` 만 지움 · 신청 하나에 장 하나 `ux_user_items_ref` · 쓴 장의 신청은 못 바꿈 — `user_items_guard` 다시 만듦) ·
  `avatar_upload_requests`(V72 상태 모델, users 외래키 없음) · `profiles.last_still_avatar`(지금 고른 아바타로 채움) · 회원 알림 CHECK ∪ `AVATAR_UPLOAD_REVIEWED`.
- **카탈로그** `avatars/manifest.json` 336(정지 328 + `motion` 8, 카테고리 `motion` 은 hobby 다음·`animated: true`, 줄은 맨 뒤). 그림 주소 `/avatars/motion/<id>.svg`·`-still.svg`.
- **shared**: `pointshop` — `UploadTicketUse`·`UploadTicketService`(예약·넘김·반환·사용·값)·`AvatarLooks`(고른 값 → 그림·정지 그림·움직임, 여러 회원 한 번)·`ProfileAvatarService`(upload-mine 고르기·마지막 정지 아바타 기억·`fallBackToStill`·`yieldToNicknameImage`)·`PointShopRules`(`RESERVED`).
  `avatarupload` — `AvatarUploadStore`(JDBC)·`AvatarUploadService`(회원: 상태·관문·변경권 선검사·속도·신청·취소·지우기)·`AvatarUploadReviewService`(관리자: 목록·승인·반려·해제)·DTO·예외.
  알림 `AdminNotificationEvent.AVATAR_UPLOAD_PENDING`·`MemberEvent/MemberNotificationType.AVATAR_UPLOAD_REVIEWED`, 감사 `AVATAR_UPLOAD_APPROVE|REJECT|REVOKE`(일반 운영). 표시 칸 `AuthorLook.avatarStillUrl`·`GroupPersonView.avatarStillUrl`.
- **api**: `AvatarUploadController`(`/api/me/avatar-upload`)·`MyAvatarUploadService`·`AvatarUploadProcessor`, 닉네임 이미지 변경권·`POST /api/me/nickname-image/resume`, `avatarStillUrl` — `PostAuthorView`·`PublicProfileView`·`MyProfileView`·`DmPartnerView`.
- **admin**: `AvatarUploadAdminController`(`/api/admin/avatar-uploads`, `nickname-image.read`·`.manage`), 닉네임 이미지 승인 = 변경권 사용·반려 = 반환·움직이는 그림 승인 = 움직이는 아바타 되돌림, `AdminScreenRegistry.avatarUploads`, 감사 대상 `AVATAR_UPLOAD`.
- **구현하며 정한 것**(계약에 없던 것 — 보수적으로, **①·② 사용자 확정 2026-11-06 「Keep both」**): ① 예약된 변경권은 **예외 환불도 409**(`POINT_SHOP_REFUND_NOT_ALLOWED blockedBy RESERVED`, 문구 따로) — 신청을 반려하면 풀린다. ② 아바타 올리기에도 닉네임 이미지와 같은 **속도 제한(10초·하루 10번)** — 대체가 같은 장을 넘겨받아 한 장으로 계속 올릴 수 있어서. 반려 뒤 재신청 대기(72시간)는 두지 않았다. ③ 판정 차례: 이메일 → 정지 → 변경권 → 파일(빈·300KB·형식) → 속도 → 처리(장면 → 정사각형 → 96px). ④ 관리자 「승인」 탭은 승인된 뒤 바뀐·지운 그림(REMOVED)도 `status: APPROVED`·`current: false`·`url: null` 로 보인다(목업과 같다). 변경권 칸: 대기 RESERVED · 승인·해제 USED · 반려 RETURNED. ⑤ 마지막 정지 아바타는 정지 카탈로그 아바타 또는 정지 그림의 `upload-mine` 만 기억하고, 되돌릴 때 지금 고를 수 있는지(무료·가진 정지 유료·정지 그림인 승인된 올린 그림) 다시 보고 아니면 `basic-default`. ⑥ WebP·움직이는 GIF 원본은 줄이지 않는다(JDK 디코더 없음·움직임 보존 — 닉네임 이미지와 같다), 정지 PNG·JPG·GIF 한 장·움직이는 GIF 의 정지 그림만 256px 이하로. ⑦ 아바타 올리기 GET 은 로그인만(관문 없음), 신청·취소·지우기는 관문. ⑧ `upload-mine` 줄 이름은 「내가 올린 아바타」(카탈로그 이름처럼 한글 고정), 카테고리 `upload` 는 승인된 그림이 있을 때만 무료 다음에. ⑨ 탈퇴 정리: 올리기 신청 줄·파일 지움, 예약된 장은 예약을 풀며 회수.

### 규칙 → 테스트 (모두 실제 PostgreSQL — `CommerceTestDatabase`)
| 규칙 | 테스트 |
|---|---|
| 0 변경권 예약(장 수에서 빠짐·상점 `mine.count`·`reserved`)·예약 장 셀프·예외 환불 불가(RESERVED) | shared `UploadTicketTest.신청하면_한_장이_…`, admin `AvatarUploadAdminHttpTest.대기_신청에_예약된_…`, api `AvatarUploadHttpTest.정지_그림은_…` |
| 0 동시 신청에도 한 장(SKIP LOCKED · 동시 두 신청 → 대기 하나·예약 하나) | `UploadTicketTest.장이_하나면_동시에_…`·`동시_신청_둘은_…` |
| 0 대체 = 같은 장 넘겨받음(닉네임 이미지·아바타) | `UploadTicketTest.새_신청이_대기_신청을_…`, api `NicknameImageTicketHttpTest.신청하면_한_장을_…` |
| 0 반려·본인 취소 = 반환(다시 환불 가능) | `UploadTicketTest.본인_취소와_반려는_…`, api `NicknameImageTicketHttpTest`·`AvatarUploadHttpTest`, admin `NicknameImageTicketAdminHttpTest`·`AvatarUploadAdminHttpTest.반려는_…` |
| 0 승인 = 사용 · 해제·지우기 반환 없음 · 쓴 장의 신청 고정(트리거) | `UploadTicketTest.승인되면_…`, admin `NicknameImageTicketAdminHttpTest.승인은_…` |
| 0 변경권 없으면 파일 처리 전 409 + `ticketCount`·`ticketPrice`(조건 검사 뒤) · 이 규칙 전 대기 신청은 예약 없음 | `UploadTicketTest.장이_없으면_…`, api `NicknameImageTicketHttpTest.변경권이_없으면_…`·`이_규칙_전에_…`, `AvatarUploadHttpTest.이메일_인증_정지_다음_…` |
| 0 예약 칸 CHECK·유일 색인·탈퇴 회수 | `UploadTicketTest.예약_칸_CHECK_…`·`탈퇴_정리는_…` |
| 0 상점 「변경권」 탭 = 세 변경권 · 관리자 품목 추가 종류(소모형만) · 값 = 파는 품목 첫째 | `UploadTicketTest.상점_변경권_탭은_…`, `AvatarCatalogTest.움직이는_아바타_품목은_…` |
| 0-1 신청 조건 = 이메일 인증·정지 아님 + 변경권뿐 | shared `AvatarUploadFlowTest.신청_조건은_…`, api `AvatarUploadHttpTest.이메일_인증_정지_다음_…` |
| 파일 규격(빈·형식·정사각형 ±2%·96px·300KB·GIF 30장면·움직이는 WebP 거절·256px·움직이는 GIF 원본 + 정지 PNG·정지 WebP·JPG·속도 429) | api `AvatarUploadHttpTest.파일_규격을_…`·`정지_그림은_…`·`움직이는_GIF_는_…` |
| 0-2 승인 = 내가 올린 아바타 한 장 + 바로 프로필 아바타 · 새 승인이면 앞 그림 교체 · 다시 쓰기 변경권 없음 · 해제·지우기 = 마지막 정지 아바타 | `AvatarUploadFlowTest`(4개), api `AvatarUploadHttpTest.움직이는_GIF_는_…`, admin `AvatarUploadAdminHttpTest`(2개) |
| 심사 권한(read·manage)·상태 전이(이미 처리 409 · 없음 404 · 사유 400)·감사 일반 운영·회원 알림·운영자 알림 | admin `AvatarUploadAdminHttpTest`(4개), `AvatarUploadFlowTest.심사는_…`, `MotionAvatarAdminCatalogTest`(3개) |
| 1·2 하나만 — 쉬는 중은 저장하지 않고 판정(`paused`) · 대기 중 움직이는 GIF 의 `pausesNicknameImage` | api `NicknameImageTicketHttpTest.움직이는_닉네임_이미지와_…`, `AvatarUploadFlowTest.대기_중인_움직이는_GIF_는_…` |
| 3 마지막 정지 아바타(고를 때 기억·움직이는 것은 기억 안 함·되돌리기·고를 수 없으면 기본) · resume · 움직이는 GIF 승인 시 되돌림 | `MotionAvatarTest`(2개), api `NicknameImageTicketHttpTest.움직이는_…`, admin `NicknameImageTicketAdminHttpTest.움직이는_그림을_…` |
| 4 움직이는 아바타 = 정지 유료와 같은 사기·고르기·환불(고르면 환불 불가) | `MotionAvatarTest.움직이는_아바타는_정지_유료_…` |
| 5·표시 `avatarStillUrl`(글 보기·목록·댓글·공개 프로필·내 프로필·내 아이템, 정지·비회원 null, 목록 한 번에) | api `AuthorDecorationHttpTest.움직이는_아바타는_…`, `AvatarUploadHttpTest.움직이는_GIF_는_…`, `MotionAvatarTest` |
| 카탈로그 336 = 시드 314 = 두 앱 svg(정지 328 · motion 8×2) · `animated`·`stillUrl`·`upload-mine` 줄 | `AvatarCatalogTest`(6개), `ProfileAvatarTest`, `MotionAvatarTest.카탈로그의_…`, admin `PointShopAvatarAdminHttpTest`·`PointShopItemAdminHttpTest` |
| 메뉴 `avatarUploads` = admin-web 사본 · 감사 분류 · 두 언어 문구 | admin `MotionAvatarAdminCatalogTest`, shared `AuditRetentionTest` |

**아직 테스트로 막지 못한 것**: ① 쪽지 상대·소모임 사람 칸의 `avatarStillUrl` 은 컴파일만. ② 보안 필터 단의 401·CSRF(api 시험은 필터 없이). ③ 실제 화면 연결·시각 확인(목업 → 실제 호출은 프론트 단계). ④ 관리자 대시보드 심사 대기 가젯(범위 밖 — 닉네임 이미지 가젯과 합칠지 후보).
