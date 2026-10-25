package com.community.membernotification.dto.response;

import java.time.Instant;

/**
 * 회원 알림 한 건 — community {@code NotificationItem} 을 roboyam 으로 옮긴 모양(docs/features/member-notification-contract.md). ID 는 문자열(Snowflake).
 * {@code title}·{@code body} 는 서버가 요청 언어({@code Accept-Language})로 푼 문장이다(관리자 알림과 같은 방식). 나머지 칸은 화면이 직접 그릴 때 쓴다.
 *
 * @param type           {@code COMMENT} · {@code REPLY} · {@code POINTS_REVOKED} · {@code POST_HIDDEN} · {@code COMMENT_HIDDEN} · {@code DM}(쪽지 받음 — {@code actor} 는 보낸 회원, {@code link} 는 그 대화방) · {@code NICKNAME_IMAGE_REVIEWED}(닉네임 그림 승인·반려·해제 — {@code body} 는 사유) · {@code OPINION_PIN_REQUESTED}(내가 확인할 의견 고정 요청 — {@code actor} 는 요청한 회원,
 *                       {@code link} 는 그 글) · {@code OPINION_PIN_REVIEWED}(내 고정 요청 승인·거절 — {@code body} 는 거절 사유, {@code link} 는 그 글)
 *                       · {@code POST_REMOVED}(운영자가 내 글을 지움 — {@code title} 에 글 제목, {@code body} 는 사유, {@code link} 없음). 운영자가 숨긴 {@code POST_HIDDEN} 은 {@code body} 가 「글 제목 · 사유」
 *                       · {@code COMMENT_REMOVED}(운영자가 내 댓글을 지움 — {@code title} 에 댓글 앞부분, {@code body} 는 사유, {@code link} 는 그 글) · {@code REPORT_RESULT}(내 신고의 처리 결과 —
 *                       {@code title} 이 조치됨/위반 아님, {@code body} 는 신고 당시 제목·앞부분, {@code link} 는 볼 수 있는 그 글). 신고 관리의 운영자 숨김·삭제 사유는 「사유 코드 이름: 글」
 * @param category       {@code comment}(댓글·답글) · {@code system} · {@code dm}(쪽지 — 거르기 {@code all} 에만 나온다)
 * @param title          한 줄 문장(예: 「고양이집사님이 내 글에 댓글을 달았어요」)
 * @param body           둘째 줄 — 댓글 앞부분 · 숨겨진 글의 제목 · 회수 사유. 없으면 null
 * @param link           누르면 갈 공개 사이트 경로(예: {@code /posts/123}). 갈 곳이 없으면 null(읽음 처리만)
 * @param actor          일으킨 사람 — 댓글·답글만, 그 밖은 null
 * @param post           가리키는 글 — 포인트 회수는 null
 * @param commentId      가리키는 댓글(묶인 알림이면 가장 최근 댓글) — 댓글·답글·댓글 숨김만
 * @param commentPreview 그 댓글의 앞 60자(공백은 한 칸) — 지웠거나 숨겼거나 글을 볼 수 없으면 null
 * @param groupCount     묶인 댓글 수(1 이면 묶이지 않음)
 * @param points         포인트 회수 알림만, 그 밖은 null
 * @param hiddenUntil    숨김이 기간 뒤 풀리는 경우(권리침해 임시조치) 그 시각, 그 밖은 null
 */
public record MemberNotificationItemView(String id, String type, String category, boolean read, Instant createdAt, String title, String body, String link,
                                         NotificationActorView actor, NotificationPostRefView post, String commentId, String commentPreview, int groupCount,
                                         NotificationPointsView points, Instant hiddenUntil) {
}
