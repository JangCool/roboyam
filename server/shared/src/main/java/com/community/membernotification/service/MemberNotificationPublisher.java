package com.community.membernotification.service;

import com.community.membernotification.dto.CommentNotice;
import com.community.membernotification.dto.MemberEmailRequested;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.repository.master.MemberNotificationWriter;
import com.community.membernotification.repository.master.MemberNotificationWriter.EventSwitch;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 알림 발행기 — 사건이 일어난 서비스가 <b>업무 트랜잭션 안에서</b>(업무 변경을 끝낸 뒤 마지막에) 한 줄로 부른다. docs/features/member-notification-contract.md.
 *
 * <ul>
 *   <li><b>트랜잭션</b>: 업무 트랜잭션에 참여한다 — 업무가 롤백되면 알림도 메일도 없다. 행 만들기는 {@link IsolatedSideWork}(JDBC 세이브포인트) 안에서 하므로 <b>알림 실패가 업무를 깨지 않는다</b>
 *       (발행은 던지지 않는다 — 실패는 ERROR 로그만). 부르는 쪽은 try-catch 로 감싸지 않는다.</li>
 *   <li><b>받는 사람</b>: 탈퇴·삭제되지 않은 회원. <b>내가 일으킨 일은 나에게 알리지 않는다</b>(내 글에 내가 단 댓글, 내 댓글에 내가 단 답글). 글쓴이는 <b>새 댓글만</b> 받고
 *       남들끼리 이어 가는 답글은 받지 않는다 — 답한 댓글의 작성자가 글쓴이 본인이어도 {@code REPLY} 하나만 간다(community 규약).</li>
 *   <li><b>설정</b>: 회원 이벤트 설정({@link MemberEvent} · {@code member_event_settings} — 관리자 이벤트 › 회원)에서 그 이벤트가 사용 중이어야 하고, 앱 알림({@code in_app})이 켜져 있으면
 *       알림 행을, 이메일({@code email})이 켜져 있으면 <b>커밋 뒤 회원 알림 메일</b>({@link MemberNotificationEmailSender} — 이메일 인증된 회원만, 조용한 시간 제외)을 낸다. 줄이 없으면 코드의 처음 값.
 *       회원 개인 끄기는 아직 없다(회원 설정 화면이 없다 — 계약 「미확정」).</li>
 *   <li><b>묶기</b>: 같은 회원·같은 글의 <b>안 읽은</b> {@code COMMENT} 가 묶음 시간({@code group_window_minutes}, 0 = 묶지 않음) 안에 있으면 새 줄을 만들지 않고 그 줄의 묶인 수를 늘린다
 *       (일으킨 사람·가리키는 댓글은 가장 최근 것). <b>묶인 댓글의 메일은 처음 한 통뿐</b>이다 — 묶음에 더해질 때는 다시 보내지 않는다. 같은 글의 댓글 쓰기는 글 행 갱신(댓글 수)으로
 *       줄을 서므로 동시 중복이 없다. 앱 알림이 꺼져 있으면 묶을 줄이 없어 댓글마다 한 통이다.</li>
 *   <li><b>개인정보</b>: 이메일·IP·비밀번호는 싣지 않는다. 비회원 작성자는 그때 이름만 남긴다. 문구는 저장하지 않고 읽을 때 요청 언어로 그린다({@link MemberNotificationMessages}).</li>
 * </ul>
 */
@Component
public class MemberNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(MemberNotificationPublisher.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 이름 칸 길이(V50) */
    private static final int NAME_MAX = 40;
    /** 덧붙임에 남기는 제목·발췌 최대 글자 */
    static final int SNAPSHOT_MAX = 60;

    private final MemberNotificationWriter writer;
    private final IsolatedSideWork isolated;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;

    public MemberNotificationPublisher(MemberNotificationWriter writer, IsolatedSideWork isolated, SnowflakeIdGenerator ids, ApplicationEventPublisher events) {
        this.writer = writer;
        this.isolated = isolated;
        this.ids = ids;
        this.events = events;
    }

    /** 댓글이 저장됐다 — 원댓글이면 글쓴이에게 {@code COMMENT}, 답글이면 답한 댓글의 작성자에게 {@code REPLY}. 만든(또는 묶은) 알림 수를 돌려준다 */
    public int commentWritten(CommentNotice notice) {
        return isolated.run("member-notification:comment", () -> {
            Instant now = Instant.now();
            MemberNotificationType type = notice.reply() ? MemberNotificationType.REPLY : MemberNotificationType.COMMENT;
            Long target = notice.reply() ? notice.replyTargetAuthorId() : notice.postAuthorId();
            if (target == null || Objects.equals(target, notice.commenterId())) {
                return 0;
            }
            EventSwitch on = switchOf(type);
            if (!on.delivers() || !writer.receivable(target)) {
                return 0;
            }
            int made = 0;
            if (on.inApp()) {
                if (type == MemberNotificationType.COMMENT) {
                    int window = writer.groupWindowMinutes().orElse(0);
                    if (window > 0) {
                        var grouped = writer.groupable(target, notice.postId(), now.minus(Duration.ofMinutes(window)));
                        if (grouped.isPresent()) {
                            writer.addToGroup(grouped.get(), notice.commenterId(), guestName(notice), notice.commentId());
                            return 1; // 묶인 댓글 — 메일은 처음 한 통만(다시 보내지 않는다)
                        }
                    }
                }
                writer.insert(ids.nextId(), target, type.name(), notice.commenterId(), guestName(notice), notice.postId(), notice.commentId(), null, now);
                made = 1;
            }
            if (on.email()) {
                requestEmail(target, MemberNotificationMessages.Facts.comment(type, guestName(notice), cut(notice.postTitle(), 200), cut(notice.excerpt(), SNAPSHOT_MAX), 1),
                        notice.commenterId(), notice.postId());
            }
            return made;
        }, 0);
    }

    /**
     * 신고로 글·댓글이 자동으로 숨겨졌다 — 작성 회원에게(비회원 글이면 부르지 않는다). 숨겨진 글은 열 수 없으므로 그때의 제목(댓글이면 발췌)을 덧붙임에 남긴다.
     *
     * @param commentId 댓글이면 그 ID, 글이면 null
     * @param snapshot  글 제목 또는 댓글 앞부분(잘라서 남긴다)
     * @param until     임시조치(권리침해)처럼 기간이 끝나면 다시 보이는 숨김이면 그 시각, 아니면 null
     */
    public int contentHidden(long authorId, long postId, Long commentId, String snapshot, Instant until) {
        return isolated.run("member-notification:hidden", () -> {
            MemberNotificationType type = commentId == null ? MemberNotificationType.POST_HIDDEN : MemberNotificationType.COMMENT_HIDDEN;
            EventSwitch on = switchOf(type);
            if (!on.delivers() || !writer.receivable(authorId)) {
                return 0;
            }
            String cut = cut(snapshot, SNAPSHOT_MAX);
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("snapshot", cut);
                if (until != null) {
                    payload.put("until", until.toString());
                }
                writer.insert(ids.nextId(), authorId, type.name(), null, null, postId, commentId, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                // 숨긴 글은 열 수 없다 — 댓글 숨김이면 그 글로 간다(알림함 링크와 같다)
                requestEmail(authorId, MemberNotificationMessages.Facts.hidden(type, cut, until != null), null, commentId == null ? null : postId);
            }
            return made;
        }, 0);
    }

    /**
     * 포인트가 회수됐다(실제로 깎인 양이 있을 때만 부른다).
     *
     * @param kind   {@code ADMIN_ADJUST}(운영 조정) — 글·댓글 삭제 회수 알림({@code POST_DELETED}·{@code COMMENT_DELETED})은 운영진 삭제가 생길 때 더한다
     * @param amount 실제로 회수된 포인트(양수)
     * @param note   회원에게 공개되는 사유(운영 조정) — 없으면 null
     */
    public int pointsRevoked(long userId, String kind, long amount, String note) {
        if (amount <= 0) {
            return 0;
        }
        return isolated.run("member-notification:points", () -> {
            EventSwitch on = switchOf(MemberNotificationType.POINTS_REVOKED);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("kind", kind);
                payload.put("amount", amount);
                payload.put("note", note);
                writer.insert(ids.nextId(), userId, MemberNotificationType.POINTS_REVOKED.name(), null, null, null, null, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.points(kind, amount, note == null || note.isBlank() ? null : note), null, null);
            }
            return made;
        }, 0);
    }

    /**
     * 쪽지를 받았다 — 받는 회원에게 {@code DM}(보낸 회원 = 일으킨 사람, 덧붙임 {@code threadId}). <b>같은 대화의 안 읽은 쪽지 알림이 이미 있으면 새로 만들지 않는다</b>
     * (안 읽은 쪽지가 쌓이는 동안 알림이 여러 개 생기지 않게 — 대화를 읽으면 그 알림도 읽음이 되고 다음 쪽지부터 다시 알린다, community {@code api-dm.md}).
     * 알림에는 쪽지 내용을 싣지 않는다(닉네임만). 보내기 트랜잭션의 마지막에 부른다.
     */
    public int dmReceived(long recipientId, long senderId, long threadId) {
        if (recipientId == senderId) {
            return 0;
        }
        return isolated.run("member-notification:dm", () -> {
            EventSwitch on = switchOf(MemberNotificationType.DM);
            if (!on.delivers() || !writer.receivable(recipientId)) {
                return 0;
            }
            if (on.inApp() && writer.unreadDmExists(recipientId, threadId)) {
                return 0; // 같은 대화의 안 읽은 알림이 남아 있다 — 메일도 다시 보내지 않는다
            }
            int made = 0;
            if (on.inApp()) {
                writer.insert(ids.nextId(), recipientId, MemberNotificationType.DM.name(), senderId, null, null, null,
                        MAPPER.writeValueAsString(Map.of("threadId", Long.toString(threadId))), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(recipientId, MemberNotificationMessages.Facts.dm(null), senderId, null, "/messages/" + threadId);
            }
            return made;
        }, 0);
    }

    /**
     * 닉네임 그림 검토 결과 — 신청한 회원에게 {@code NICKNAME_IMAGE_REVIEWED}(끌 수 없는 처리 알림). 본인의 취소·해제에는 부르지 않는다.
     *
     * @param result {@code APPROVED} · {@code REJECTED} · {@code REVOKED}
     * @param reason 반려·해제 사유(운영자가 적은 문구 그대로), 승인이면 null
     */
    public int nicknameImageReviewed(long userId, long requestId, String result, String reason) {
        return isolated.run("member-notification:nickname-image", () -> {
            EventSwitch on = switchOf(MemberNotificationType.NICKNAME_IMAGE_REVIEWED);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("result", result);
                payload.put("reason", reason);
                payload.put("requestId", Long.toString(requestId));
                writer.insert(ids.nextId(), userId, MemberNotificationType.NICKNAME_IMAGE_REVIEWED.name(), null, null, null, null, MAPPER.writeValueAsString(payload),
                        Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.nicknameReviewed(result, reason), null, null, "/settings/nickname-image");
            }
            return made;
        }, 0);
    }

    /** 회원 알림 메일 — 커밋 뒤 발송기가 보낸다. 세이브포인트 일의 <b>마지막</b>에 부른다(뒤에서 실패해 행이 되돌려졌는데 메일만 나가는 일이 없게) */
    private void requestEmail(long userId, MemberNotificationMessages.Facts facts, Long actorUserId, Long postId) {
        events.publishEvent(new MemberEmailRequested(userId, facts, actorUserId, postId));
    }

    /** 글이 아닌 화면을 가리키는 메일({@code path} = 사이트 안 경로) */
    private void requestEmail(long userId, MemberNotificationMessages.Facts facts, Long actorUserId, Long postId, String path) {
        events.publishEvent(new MemberEmailRequested(userId, facts, actorUserId, postId, path));
    }

    /**
     * 회원 이벤트 설정(관리자 설정 › 이벤트 › 회원) — 사용 여부 · 앱 알림 · 이메일. 줄이 없으면 코드의 처음 값({@link MemberEvent}).
     */
    private EventSwitch switchOf(MemberNotificationType type) {
        MemberEvent event = MemberEvent.of(type);
        EventSwitch on = writer.eventSetting(event.code()).orElse(new EventSwitch(event.defaultEnabled(), event.defaultInApp(), event.defaultEmail()));
        if (!on.delivers()) {
            log.debug("회원 이벤트 설정에서 꺼진 이벤트라 만들지 않는다 event={}", event);
        }
        return on;
    }

    private static String guestName(CommentNotice notice) {
        return notice.commenterId() != null ? null : cut(notice.commenterGuestName(), NAME_MAX);
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= max ? flat : flat.substring(0, flat.offsetByCodePoints(0, max - 1)) + "…";
    }
}
