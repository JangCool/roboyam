# 회원 알림(공개 사이트) 계약 (**서버 구현 2026-10-23** — 화면 연결은 다음 단계)

**의도**: 공개 사이트 회원이 종 아이콘·알림 패널에서 「내 글에 댓글」「내 댓글에 답글」「포인트 회수」「신고로 내 글·댓글이 숨겨짐」을 받는다. 관리자 알림(`notification-contract.md`)과 표·API·종류가 모두 따로다.
**참고(읽기 전용)**: community `docs/api-notification.md`(`NotificationItem`·필터·읽음 규약). 프론트 기준 `apps/user-web/app/composables/notification/api/useNotificationApi.ts`(셸: `unreadCount`·`recent`).

## 종류 (서버 `MemberNotificationType` 이 원천 — 실제로 발행되는 것만)
| `type` | 언제 | 받는 사람 | `category` | 회원이 끄기 |
|---|---|---|---|---|
| `COMMENT` | 내 글에 새 **원댓글** | 글쓴 회원 | comment | 운영자가 정함 |
| `REPLY` | 내 댓글에 **답글**(`replyToId` 의 댓글, 없으면 원댓글) | 그 댓글의 작성 회원 | comment | 운영자가 정함 |
| `POINTS_REVOKED` | 운영 조정 차감이 실제로 반영됨(`point-contract.md`) | 그 회원 | system | 끌 수 없음 |
| `POST_HIDDEN` · `COMMENT_HIDDEN` | 신고 자동 숨김으로 **보이던** 글·댓글이 새로 숨겨짐(`ReportService#autoHide`) | 작성 회원 | system | 끌 수 없음 |
- **운영자 끄기(2026-10-23 바뀜)**: 모든 종류가 회원 이벤트 설정(`member_event_settings` — 관리자 이벤트 › 회원, `message-template-contract.md` 「서버 구현 결과(회원 이벤트·이메일 확장)」)의 `enabled && inApp` 일 때만 만든다. 예전의 게시판 기본 설정 `<종류>.site`(옛 칸 `member_defaults`)는 V58 이 옮겼고 더 이상 읽지 않는다.
- 내가 일으킨 일은 알리지 않는다(내 글에 내 댓글, 내 댓글에 내 답글). 글쓴이는 새 원댓글만 받고 남들끼리의 답글은 받지 않는다 — 답한 댓글의 작성자가 글쓴이여도 `REPLY` 하나만. 비회원 글·댓글의 작성자는 받지 않는다. 탈퇴·삭제된 회원은 받지 않는다(정지 회원은 받는다).
- **묶기**: 같은 회원·같은 글의 **안 읽은** `COMMENT` 가 `board_notification_settings.group_window_minutes`(기본 10분, 0 = 묶지 않음) 안에 있으면 새 줄 대신 그 줄의 `groupCount` +1, 일으킨 사람·댓글은 가장 최근 것(만든 시각·차례는 그대로).
- 신고 숨김 알림에는 신고자·사유 코드를 싣지 않는다. 권리침해 임시조치면 `hiddenUntil`(다시 보이는 시각)과 「임시로 숨겨졌어요」 문구.
- 발행: 업무 트랜잭션 안(마지막), JDBC 세이브포인트 격리(`IsolatedSideWork`) — **알림 실패가 댓글쓰기·신고·조정을 깨지 않는다**(검증: 알림 INSERT 를 막는 트리거를 걸어도 댓글 201).

## API (api 서버, 로그인 필요 401, 본인 것만 — 경로에 회원 ID 없음, `Cache-Control: no-store`, 쓰기는 CSRF)
- `GET /api/notifications?filter=all|comment|system&size=1~50(기본 20)&cursor=` → `MemberNotificationPage`
- `GET /api/notifications/unread-count` → `{ count }`(상한 100, 거르기 무관)
- `POST /api/notifications/{id}/read` → 204(이미 읽음도 204). 남의·없는·형식 틀린 ID `404 NOTIFICATION_NOT_FOUND`
- `POST /api/notifications/read-all[?filter=]` → `{ updated }`
- 오류: `400 VALIDATION` `fields.filter`·`fields.size`·`fields.cursor`.

```ts
interface MemberNotificationPage { items: MemberNotificationItem[]; hasNext: boolean; nextCursor: string | null; unreadCount: number; retentionDays: number }
interface MemberNotificationItem {
  id: string; type: 'COMMENT'|'REPLY'|'POINTS_REVOKED'|'POST_HIDDEN'|'COMMENT_HIDDEN'; category: 'comment'|'system';
  read: boolean; createdAt: string;
  title: string;          // 서버가 요청 언어로 푼 한 줄(예: "mem3님이 내 글에 댓글을 달았어요", 묶이면 "mem3님 외 1개의 댓글이 내 글에 달렸어요")
  body: string | null;    // 댓글 앞 60자 · 숨겨진 글 제목/댓글 발췌 · "운영 조정: 사유"
  link: string | null;    // 공개 사이트 경로 — COMMENT·REPLY·COMMENT_HIDDEN 은 글이 보일 때 /posts/{id}, 그 밖 null(읽음 처리만)
  actor: { kind: 'MEMBER'|'GUEST'; memberId: string | null; name: string; withdrawn: boolean } | null; // 댓글·답글만. 탈퇴면 "탈퇴한 회원"
  post: { id: string; title: string | null; available: boolean } | null;  // 지움·숨김·꺼진 게시판이면 available=false, title=null
  commentId: string | null; commentPreview: string | null;  // 댓글이 지워졌거나 숨겨졌거나 글을 볼 수 없으면 preview null
  groupCount: number;     // 1 = 묶이지 않음
  points: { kind: 'ADMIN_ADJUST'; amount: number; note: string | null } | null;
  hiddenUntil: string | null;
}
```
- 글·댓글·일으킨 사람은 **읽을 때의 현재 값**(닉네임 변경을 따라감). 이메일·IP·비밀번호는 어디에도 없다. 셸의 `recent` = `GET /api/notifications?size=8` 의 `items`(`MockNotification.text` → `title`, `time` → `createdAt` 를 화면이 상대 시간으로, `unread` → `!read`).

## 저장·보관
- shared `V50__member_notifications.sql`: `member_notifications`(받는 회원·종류·일으킨 회원/비회원 이름·글·댓글·묶인 수·`payload` jsonb·읽은 시각). 색인: `(user_id, id DESC)`, 안 읽은 것 부분 색인, `read_at`, `created_at`.
- DB 배치 `member-notification-cleanup`(`R__batch_20`, 매일 04:00 KST, `BatchCatalog`): ① 탈퇴·삭제 회원의 알림 ② 읽은 지 30일(`batch_params.read_retention_days`) ③ 만든 지 회원 알림 보관 일수(`board_notification_settings.member_notification_retention_days`, 관리자 게시판 기본 설정 「알림」, 기본 90) ④ 회원당 500건(`per_user_cap`) 초과(읽은 것 오래된 것부터). 목록 응답의 `retentionDays` 가 ③ 값이다.

## 가정
1. 문구는 서버가 푼다(관리자 알림과 같은 방식) — community 처럼 화면이 `type`+칸으로 만들 수도 있게 구조 칸을 함께 준다.
2. 끄기는 회원 이벤트 설정의 사용·앱 알림만 본다(이메일 칸·조용한 시간은 회원 이메일 발송이 없어 적용 지점 없음 — 2026-10-23 게시판 기본 설정 `site` 에서 옮김).
3. 신고 숨김은 「운영 처리」와 달리 시스템 자동 조치라 끌 수 없는 계정 알림으로 둔다(`OPERATOR_ACTION`·`REPORT_RESULT` 설정과 연결하지 않음).

## 미확정·후속
1. **회원 개인 알림 끄기**는 이번 범위 밖. 표가 필요하면 `member_notification_mutes(user_id, kind, created_at, PK(user_id, kind))` — `kind` 는 게시판 설정 종류(`COMMENT_ON_POST`…)와 같은 이름, `memberCanTurnOff=false` 종류는 거절. API 후보 `GET/PUT /api/notifications/settings`(community 모양).
2. 관리자 회원 상세 `settings.inbox`: `settings` 가 통째로 null 이고 admin-web `MemberSettings` 의 다른 칸(`display` 등)이 non-null 타입이라 inbox 만 채울 수 없어 채우지 않았다 — 타입을 칸별 nullable 로 바꾸거나 `inbox` 를 바깥 칸으로 옮기면 `member_notifications` 로 `{totalCount, unreadCount}` 를 바로 채울 수 있다.
3. 쪽지(`DM`)·인기글(`POPULAR_REWARD`)·멘션은 기능이 없어 종류를 만들지 않았다. 운영자·스텝이 글을 지우는 기능이 생기면 `POINTS_REVOKED` 의 `POST_DELETED`·`COMMENT_DELETED` 와 처리 알림을 더한다.
4. pg_cron 이 없으면 정리 배치가 돌지 않는다(목록에 「사용 불가」) — 쌓이기만 한다.

## 프론트 연결 결과 (2026-10-23)
- user-web `useNotificationApi`: `unreadCount`·`list`·`recent`(`size=8` 의 `items`)·`markRead`·`markAllRead` 실제 호출. 타입은 서버 DTO 이름(`MemberNotificationItem`·`MemberNotificationPage`) — `MockNotification`·`notification/api/mock/` 삭제.
- 알림 패널(데스크톱 종): 문장은 서버 `title`/`body` 그대로(묶인 댓글·탈퇴한 회원·임시 숨김 문구 포함), 시각은 `time.ago`. 한 줄을 누르면 읽음(낙관적 + 배지 갱신) 뒤 `link` 로 이동 — `link` 는 앱 안 경로(`/…`, `//` 제외)만 링크로, 없으면 읽음만 하고 패널에 남는다. 바닥 줄 「모두 읽음」(안 읽은 것이 있을 때만). 비로그인은 호출하지 않는다(배지 `refresh` 가 로그인 확인).
- 알림 화면(`/notifications`)·알림 설정 화면이 없어 패널의 「알림 모두 보기」·설정 단추는 감췄다. 모바일 하단 탭 「알림」은 여전히 없는 `/notifications` 로 간다(셸 범위 밖 — 알림 화면을 만들 때 해결).
- 쪽지 배지: 쪽지 기능이 없어 헤더 쪽지 아이콘(`AppMessageLink`)·프로필 메뉴의 쪽지함 줄·`useDmBadge`·`useDmApi`·쪽지 문구 키를 걷어 냈다(되살릴 때는 이 커밋 전 기록).
