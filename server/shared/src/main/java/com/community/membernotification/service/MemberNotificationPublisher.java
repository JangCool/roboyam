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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
 *       그다음 회원 개인 설정({@code member_notification_preferences} — {@code PUT /api/me/notification-settings}, 줄이 없으면 켬)을 더한다: 앱 알림 = 운영자 앱 알림 && 회원 앱 알림,
 *       메일 = 운영자 이메일 && 회원 이메일. 회원이 끌 수 없는 이벤트(코드 잠금 {@code MemberEventLockReason} 또는 운영자 「회원이 끌 수 있음」 꺼짐)는 개인 설정을 무시한다.</li>
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
            EventSwitch on = switchOf(type, target);
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
            EventSwitch on = switchOf(type, authorId);
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
     * 운영자가 글을 숨겼다(관리자 게시글 관리 「작성자에게 알림」) — 작성 회원에게 {@code POST_HIDDEN}(덧붙임 {@code by STAFF}·{@code reason}). 비회원 글이면 부르지 않는다.
     *
     * @param title  글 제목(잘라서 남긴다)
     * @param reason 운영자가 적은 사유
     */
    public int postHiddenByStaff(long authorId, long postId, String title, String reason) {
        return hiddenByStaff(authorId, postId, null, title, reason, null);
    }

    /**
     * 운영자가 글·댓글을 숨겼다 — 작성 회원에게 {@code POST_HIDDEN}·{@code COMMENT_HIDDEN}(덧붙임 {@code by STAFF}·{@code reason}·있으면 {@code reasonCode}). 비회원 것이면 부르지 않는다.
     * 관리자 신고 관리의 「숨기기」·「자동 숨김 확정」은 사유 코드와 글을 함께 싣는다(문구는 「코드 이름: 글」).
     *
     * @param commentId  댓글이면 그 ID, 글이면 null
     * @param snapshot   글 제목 또는 댓글 앞부분(잘라서 남긴다)
     * @param reason     운영자가 적은 글(없으면 null)
     * @param reasonCode 조치 사유 코드(신고 사유 체계, 없으면 null)
     */
    public int hiddenByStaff(long authorId, long postId, Long commentId, String snapshot, String reason, String reasonCode) {
        return isolated.run("member-notification:staff-hidden", () -> {
            MemberNotificationType type = commentId == null ? MemberNotificationType.POST_HIDDEN : MemberNotificationType.COMMENT_HIDDEN;
            EventSwitch on = switchOf(type, authorId);
            if (!on.delivers() || !writer.receivable(authorId)) {
                return 0;
            }
            String cut = cut(snapshot, SNAPSHOT_MAX);
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("snapshot", cut);
                payload.put("by", MemberNotificationMessages.BY_STAFF);
                payload.put("reason", reason);
                if (reasonCode != null) {
                    payload.put("reasonCode", reasonCode);
                }
                writer.insert(ids.nextId(), authorId, type.name(), null, null, postId, commentId, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                // 숨긴 글은 열 수 없다 — 댓글 숨김이면 그 글로 간다(알림함 링크와 같다)
                requestEmail(authorId, MemberNotificationMessages.Facts.hiddenByStaff(type, cut, reason, reasonCode), null, commentId == null ? null : postId);
            }
            return made;
        }, 0);
    }

    /**
     * 운영자가 글을 지웠다(관리자 게시글 관리) — 작성 회원에게 {@code POST_REMOVED}(끌 수 없는 처리 알림, 덧붙임 {@code snapshot}·{@code reason}·회수한 포인트 {@code revokedPoints}).
     * 지운 글은 열 수 없으므로 메일에도 링크가 없다. 비회원 글이면 부르지 않는다.
     *
     * @param revokedPoints 함께 회수한 포인트(없으면 0)
     */
    public int postRemoved(long authorId, long postId, String title, String reason, long revokedPoints) {
        return removedByStaff(authorId, postId, null, title, reason, null, revokedPoints);
    }

    /**
     * 운영자가 글·댓글을 지웠다 — 작성 회원에게 {@code POST_REMOVED}·{@code COMMENT_REMOVED}(끌 수 없는 처리 알림, 덧붙임 {@code snapshot}·{@code reason}·있으면 {@code reasonCode}·
     * {@code revokedPoints}). 지운 글은 링크가 없고, 지운 댓글은 그 글로 간다. 비회원 것이면 부르지 않는다.
     *
     * @param commentId     댓글이면 그 ID, 글이면 null
     * @param snapshot      글 제목 또는 댓글 앞부분(잘라서 남긴다)
     * @param reasonCode    조치 사유 코드(신고 사유 체계 — 관리자 신고 관리, 없으면 null)
     * @param revokedPoints 함께 회수한 포인트(없으면 0)
     */
    public int removedByStaff(long authorId, long postId, Long commentId, String snapshot, String reason, String reasonCode, long revokedPoints) {
        return isolated.run("member-notification:removed", () -> {
            MemberNotificationType type = commentId == null ? MemberNotificationType.POST_REMOVED : MemberNotificationType.COMMENT_REMOVED;
            EventSwitch on = switchOf(type, authorId);
            if (!on.delivers() || !writer.receivable(authorId)) {
                return 0;
            }
            String cut = cut(snapshot, SNAPSHOT_MAX);
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("snapshot", cut);
                payload.put("reason", reason);
                if (reasonCode != null) {
                    payload.put("reasonCode", reasonCode);
                }
                payload.put("revokedPoints", revokedPoints);
                writer.insert(ids.nextId(), authorId, type.name(), null, null, postId, commentId, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(authorId, MemberNotificationMessages.Facts.removed(type, cut, reason, reasonCode), null, commentId == null ? null : postId);
            }
            return made;
        }, 0);
    }

    /**
     * 내 신고가 처리됐다(관리자 신고 관리 — 사건을 조치·무시로 닫음) — 신고한 회원에게 {@code REPORT_RESULT}(덧붙임 {@code result}·{@code targetType}·{@code snapshot}).
     * 게시판 신고 정책 {@code notifyReporterOnResult} 가 켜졌을 때만 부른다. 처리 사유·작성자는 싣지 않는다.
     *
     * @param postId     대상 글(댓글 신고면 그 댓글의 글) — 소모임 신고면 null
     * @param commentId  댓글 신고면 그 ID, 글 신고면 null
     * @param groupId    소모임 신고면 그 소모임 ID(덧붙임 {@code groupId} — 누르면 그 소모임), 그 밖 null
     * @param targetType {@code POST} · {@code COMMENT} · {@code GROUP}
     * @param snapshot   신고 당시 글 제목·댓글 앞부분·소모임 이름(잘라서 남긴다)
     * @param result     {@link MemberNotificationMessages#REPORT_ACTIONED} · {@link MemberNotificationMessages#REPORT_NO_VIOLATION}
     */
    public int reportResult(long reporterId, Long postId, Long commentId, Long groupId, String targetType, String snapshot, String result) {
        return isolated.run("member-notification:report-result", () -> {
            EventSwitch on = switchOf(MemberNotificationType.REPORT_RESULT, reporterId);
            if (!on.delivers() || !writer.receivable(reporterId)) {
                return 0;
            }
            String cut = cut(snapshot, SNAPSHOT_MAX);
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("result", result);
                payload.put("targetType", targetType);
                payload.put("snapshot", cut);
                if (groupId != null) {
                    payload.put("groupId", Long.toString(groupId));
                }
                writer.insert(ids.nextId(), reporterId, MemberNotificationType.REPORT_RESULT.name(), null, null, postId, commentId,
                        MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(reporterId, MemberNotificationMessages.Facts.reportResult(result, targetType, cut), null, null);
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
            EventSwitch on = switchOf(MemberNotificationType.POINTS_REVOKED, userId);
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
            EventSwitch on = switchOf(MemberNotificationType.DM, recipientId);
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
            EventSwitch on = switchOf(MemberNotificationType.NICKNAME_IMAGE_REVIEWED, userId);
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

    /**
     * 의견 고정 요청이 왔다 — 확인할 수 있는 회원(글쓴이·게시판 매니저)에게 {@code OPINION_PIN_REQUESTED}. 요청한 회원 자신에게는 보내지 않는다.
     *
     * @param postTitle 글 제목(메일 문구)
     * @param excerpt   의견 앞부분(메일 문구)
     */
    public int opinionPinRequested(long recipientId, long requesterId, long postId, long commentId, String postTitle, String excerpt) {
        if (recipientId == requesterId) {
            return 0;
        }
        return isolated.run("member-notification:pin-request", () -> {
            EventSwitch on = switchOf(MemberNotificationType.OPINION_PIN_REQUESTED, recipientId);
            if (!on.delivers() || !writer.receivable(recipientId)) {
                return 0;
            }
            int made = 0;
            if (on.inApp()) {
                writer.insert(ids.nextId(), recipientId, MemberNotificationType.OPINION_PIN_REQUESTED.name(), requesterId, null, postId, commentId, null, Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(recipientId, MemberNotificationMessages.Facts.pinRequested(null, cut(postTitle, SNAPSHOT_MAX), cut(excerpt, SNAPSHOT_MAX)), requesterId,
                        postId);
            }
            return made;
        }, 0);
    }

    /**
     * 의견 고정 요청 결과 — 요청한 회원에게 {@code OPINION_PIN_REVIEWED}(끌 수 없는 처리 알림).
     *
     * @param result {@code APPROVED} · {@code REJECTED}
     * @param reason 거절 사유(없으면 null)
     */
    public int opinionPinReviewed(long requesterId, long requestId, long postId, long commentId, String result, String reason, String postTitle) {
        return isolated.run("member-notification:pin-reviewed", () -> {
            EventSwitch on = switchOf(MemberNotificationType.OPINION_PIN_REVIEWED, requesterId);
            if (!on.delivers() || !writer.receivable(requesterId)) {
                return 0;
            }
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("result", result);
                payload.put("reason", reason);
                payload.put("requestId", Long.toString(requestId));
                writer.insert(ids.nextId(), requesterId, MemberNotificationType.OPINION_PIN_REVIEWED.name(), null, null, postId, commentId,
                        MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(requesterId, MemberNotificationMessages.Facts.pinReviewed(result, reason, cut(postTitle, SNAPSHOT_MAX)), null, postId);
            }
            return made;
        }, 0);
    }

    /**
     * 기능 제한이 걸렸다(commerce.md 12-3) — 어떤 기능·언제까지·공개 사유. <b>운영 메모는 받지도 싣지도 않는다.</b>
     *
     * @param kind     {@code POINT_FREEZE} · {@code GROUP_BUY_BAN} · {@code CHARGE_BAN}
     * @param until    끝나는 순간(무기한이면 null)
     * @param reason   회원에게 공개하는 사유(자동 부여면 null — 읽을 때 규칙 문구)
     * @param autoRule 자동 부여 규칙(운영자 부여면 null)
     */
    public int featureRestricted(long userId, String kind, Instant until, String reason, String autoRule) {
        return isolated.run("member-notification:restricted", () -> {
            EventSwitch on = switchOf(MemberNotificationType.FEATURE_RESTRICTED, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("kind", kind);
                payload.put("until", until == null ? null : until.toString());
                payload.put("reason", reason);
                payload.put("autoRule", autoRule);
                writer.insert(ids.nextId(), userId, MemberNotificationType.FEATURE_RESTRICTED.name(), null, null, null, null, MAPPER.writeValueAsString(payload),
                        Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.restricted(kind, until, reason, autoRule), null, null);
            }
            return made;
        }, 0);
    }

    /**
     * 무상 포인트가 곧 소멸된다(소멸 예정 알림 배치 {@code point-expiry-notice}, commerce.md 14) — {@code POINTS_EXPIRING}(덧붙임 {@code amount}·{@code date}·{@code days}).
     * 배치 덩어리 트랜잭션 안에서 부른다(같은 트랜잭션에서 묶음에 알린 시각을 적는다). 누르면 내 포인트.
     *
     * @param amount 알림 기간 안 소멸 예정 합(양수)
     * @param date   가장 가까운 소멸일(한국 날짜)
     * @param days   알림 기간(일)
     */
    public int pointsExpiring(long userId, long amount, LocalDate date, int days) {
        if (amount <= 0) {
            return 0;
        }
        return isolated.run("member-notification:points-expiring", () -> {
            EventSwitch on = switchOf(MemberNotificationType.POINTS_EXPIRING, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("amount", amount);
                payload.put("date", date.toString());
                payload.put("days", days);
                writer.insert(ids.nextId(), userId, MemberNotificationType.POINTS_EXPIRING.name(), null, null, null, null, MAPPER.writeValueAsString(payload),
                        Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.expiring(amount, date, days), null, null, "/me/points");
            }
            return made;
        }, 0);
    }

    /**
     * 공동구매 알림(커머스 2b-4 — {@code GROUP_BUY}). 덧붙임 {@code kind}·{@code groupBuyId}·{@code title}·{@code amount}·{@code note}·{@code path}. 끌 수 없는 거래 알림.
     * 업무 트랜잭션 안에서 부른다(세이브포인트 — 실패해도 업무를 깨지 않는다).
     *
     * @param kind  종류(번들 {@code member.notification.GROUP_BUY.kind.<kind>})
     * @param note  주문 번호·날짜 같은 덧붙임(없으면 null)
     * @param path  누르면 갈 사이트 안 경로(메일 버튼도 같은 곳)
     * @param email 회원 알림 메일도 낼까(회원 이벤트 설정의 이메일이 켜져 있을 때만 — 꼭 가야 하는 메일은 커머스 메일 대기열이 따로 보낸다)
     */
    public int groupBuy(long userId, String kind, long groupBuyId, String title, long amount, String note, String path, boolean email) {
        return isolated.run("member-notification:group-buy", () -> {
            EventSwitch on = switchOf(MemberNotificationType.GROUP_BUY, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(title, SNAPSHOT_MAX);
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("kind", kind);
                payload.put("groupBuyId", Long.toString(groupBuyId));
                payload.put("title", name);
                payload.put("amount", amount);
                if (note != null) {
                    payload.put("note", note);
                }
                payload.put("path", path);
                writer.insert(ids.nextId(), userId, MemberNotificationType.GROUP_BUY.name(), null, null, null, null, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (email && on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.groupBuy(kind, name, amount, note), null, null, path);
            }
            return made;
        }, 0);
    }

    /**
     * 사업자 신원 알림(V111 — {@code BUSINESS}). 덧붙임 {@code kind}·{@code companyName}·{@code note}(반려·정지 사유). 끌 수 없는 계정 알림, 누르면 사업자 센터.
     * 업무 트랜잭션 안에서 부른다(세이브포인트 — 실패해도 업무를 깨지 않는다). 메일은 회원 이벤트 설정의 이메일이 켜져 있을 때만.
     *
     * @param kind 종류(번들 {@code member.notification.BUSINESS.kind.<kind>})
     * @param note 반려·정지 사유(없으면 null)
     */
    public int business(long userId, String kind, String companyName, String note) {
        return isolated.run("member-notification:business", () -> {
            EventSwitch on = switchOf(MemberNotificationType.BUSINESS, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(companyName, SNAPSHOT_MAX);
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("kind", kind);
                payload.put("companyName", name);
                if (note != null) {
                    payload.put("note", note);
                }
                writer.insert(ids.nextId(), userId, MemberNotificationType.BUSINESS.name(), null, null, null, null, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.business(kind, name, note), null, null, "/business");
            }
            return made;
        }, 0);
    }

    /**
     * 사업자 신청 건 알림(V112 — {@code BIZ_APPLICATION}). 덧붙임 {@code kind}·{@code applicationId}·{@code title}·{@code amount}·{@code note}·{@code path}. 끌 수 없는 거래 알림,
     * 누르면 신청 건 상세. 업무 트랜잭션 안에서 부른다(세이브포인트 — 실패해도 업무를 깨지 않는다). 메일은 회원 이벤트 설정의 이메일이 켜져 있을 때만.
     *
     * @param kind 종류(번들 {@code member.notification.BIZ_APPLICATION.kind.<kind>})
     * @param note 기한·사유 같은 덧붙임(없으면 null)
     */
    public int bizApplication(long userId, String kind, long applicationId, String title, long amount, String note) {
        return isolated.run("member-notification:biz-application", () -> {
            EventSwitch on = switchOf(MemberNotificationType.BIZ_APPLICATION, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(title, SNAPSHOT_MAX);
            String path = "/business/applications/" + applicationId;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("kind", kind);
                payload.put("applicationId", Long.toString(applicationId));
                payload.put("title", name);
                payload.put("amount", amount);
                if (note != null) {
                    payload.put("note", note);
                }
                payload.put("path", path);
                writer.insert(ids.nextId(), userId, MemberNotificationType.BIZ_APPLICATION.name(), null, null, null, null, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.bizApplication(kind, name, amount, note), null, null, path);
            }
            return made;
        }, 0);
    }

    /**
     * 정지 중에 낸 포인트 상점 환불 신청이 결정됐다(V87, commerce-stage1-contract 「1-2 정지 중 환불 신청」) — 수락 {@code POINT_SHOP_REFUND_APPROVED}(돌려준 포인트) ·
     * 거절 {@code POINT_SHOP_REFUND_REJECTED}(사유). 끌 수 없는 처리 알림, 덧붙임 {@code requestId}·{@code purchaseId}·{@code itemName}·{@code points}|{@code reason}. 누르면 내 아이템.
     * 결정 트랜잭션 안에서 부른다(세이브포인트 — 실패해도 결정을 깨지 않는다).
     */
    public int pointShopRefundDecided(long userId, long requestId, long purchaseId, boolean approved, String itemName, long points, String reason) {
        return isolated.run("member-notification:point-shop-refund", () -> {
            MemberNotificationType type = approved ? MemberNotificationType.POINT_SHOP_REFUND_APPROVED : MemberNotificationType.POINT_SHOP_REFUND_REJECTED;
            EventSwitch on = switchOf(type, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(itemName, SNAPSHOT_MAX);
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("requestId", Long.toString(requestId));
                payload.put("purchaseId", Long.toString(purchaseId));
                payload.put("itemName", name);
                if (approved) {
                    payload.put("points", points);
                } else {
                    payload.put("reason", reason);
                }
                writer.insert(ids.nextId(), userId, type.name(), null, null, null, null, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, approved ? MemberNotificationMessages.Facts.refundApproved(name, points) : MemberNotificationMessages.Facts.refundRejected(name, reason),
                        null, null, "/me/items");
            }
            return made;
        }, 0);
    }

    /**
     * 소모임 알림(GROUP_* — V90, community api-groups.md 7절) — 덧붙임 {@code groupId}·{@code groupName}(+{@code reason} 반려·거절·비활성 사유,
     * {@code applicantId} 가입 신청). 끌 수 없는 알림(회원 이벤트 잠금). 일으킨 사람({@code actorUserId})은 가입 신청한 회원·처리한 모임장 — 그 사람 본인에게는 보내지 않는다.
     * 업무 트랜잭션 안에서 부른다(세이브포인트 — 실패해도 업무를 깨지 않는다).
     *
     * @param path 메일 버튼이 가리킬 사이트 안 경로(알림함의 링크와 같은 곳)
     */
    public int group(MemberNotificationType type, long userId, long groupId, String groupName, String reason, Long actorUserId, String path) {
        if (actorUserId != null && actorUserId == userId) {
            return 0;
        }
        return isolated.run("member-notification:group", () -> {
            EventSwitch on = switchOf(type, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(groupName, SNAPSHOT_MAX);
            String why = reason == null || reason.isBlank() ? null : reason;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("groupId", Long.toString(groupId));
                payload.put("groupName", name);
                if (why != null) {
                    payload.put("reason", why);
                }
                if (type == MemberNotificationType.GROUP_JOIN_REQUEST && actorUserId != null) {
                    payload.put("applicantId", Long.toString(actorUserId));
                }
                writer.insert(ids.nextId(), userId, type.name(), type == MemberNotificationType.GROUP_JOIN_REQUEST ? actorUserId : null, null, null, null,
                        MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.group(type, name, why, null), type == MemberNotificationType.GROUP_JOIN_REQUEST ? actorUserId : null,
                        null, path);
            }
            return made;
        }, 0);
    }

    /**
     * 내 공개 소모임 글이 비공개로 되돌려졌다(GROUP_POST_UNPUBLISHED — V91, community api-groups.md 8절) — 받는 사람은 글쓴이, 일으킨 사람은 처리자(모임장·되돌리기를 맡은 매니저).
     * 덧붙임 {@code groupId}·{@code groupName}·{@code title}·{@code actorRole}(LEADER · MANAGER). 끌 수 없는 처리 알림. 글쓴이 본인이 되돌리면 부르지 않는다(여기서도 막는다).
     * 공개 전환 트랜잭션 안에서 부른다(세이브포인트 — 실패해도 되돌리기를 깨지 않는다).
     */
    public int groupPostUnpublished(long authorId, long groupId, String groupName, long postId, String title, long actorUserId, String actorRole) {
        if (actorUserId == authorId) {
            return 0;
        }
        return isolated.run("member-notification:group-post-unpublished", () -> {
            EventSwitch on = switchOf(MemberNotificationType.GROUP_POST_UNPUBLISHED, authorId);
            if (!on.delivers() || !writer.receivable(authorId)) {
                return 0;
            }
            int made = 0;
            String name = cut(groupName, SNAPSHOT_MAX);
            String postTitle = cut(title, SNAPSHOT_MAX);
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("groupId", Long.toString(groupId));
                payload.put("groupName", name);
                payload.put("title", postTitle);
                payload.put("actorRole", actorRole);
                writer.insert(ids.nextId(), authorId, MemberNotificationType.GROUP_POST_UNPUBLISHED.name(), actorUserId, null, postId, null,
                        MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(authorId, MemberNotificationMessages.Facts.groupPostUnpublished(name, postTitle, actorRole), null, postId);
            }
            return made;
        }, 0);
    }

    /**
     * 소모임 매니저 알림(V92 — community api-groups.md 7절 「매니저 알림 5종」) — 덧붙임 {@code groupId}·{@code groupName}(+{@code permissions} 맡은 일 키 배열 — 임명·맡은 일 바뀜,
     * {@code actorId} 일으킨 매니저 — 사퇴·매니저 강퇴, {@code targetId} 내보낸 멤버 — 매니저 강퇴). 끌 수 없는 알림. 일으킨 사람({@code actorUserId} — 임명·해임은 모임장,
     * 사퇴·강퇴는 그 매니저)은 알림 행의 일으킨 사람 칸에 남고 그 사람 본인에게는 보내지 않는다. 업무 트랜잭션 안에서 부른다(세이브포인트).
     *
     * @param permissions 맡은 일 키(고정 순서) — 임명·맡은 일 바뀜만, 그 밖 null
     * @param targetId    내보낸 멤버 — 매니저 강퇴만
     * @param targetName  내보낸 멤버의 지금 닉네임(메일 문구 — 알림함은 읽을 때 다시 읽는다)
     * @param path        메일 버튼이 가리킬 사이트 안 경로(알림함의 링크와 같은 곳)
     */
    public int groupManager(MemberNotificationType type, long userId, long groupId, String groupName, long actorUserId, List<String> permissions, Long targetId,
                            String targetName, String path) {
        if (actorUserId == userId) {
            return 0;
        }
        return isolated.run("member-notification:group-manager", () -> {
            EventSwitch on = switchOf(type, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(groupName, SNAPSHOT_MAX);
            String keys = permissions == null ? null : String.join(",", permissions);
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("groupId", Long.toString(groupId));
                payload.put("groupName", name);
                if (permissions != null) {
                    payload.put("permissions", List.copyOf(permissions));
                }
                if (type == MemberNotificationType.GROUP_MANAGER_RESIGNED || type == MemberNotificationType.GROUP_KICKED_BY_MANAGER) {
                    payload.put("actorId", Long.toString(actorUserId));
                }
                if (targetId != null) {
                    payload.put("targetId", Long.toString(targetId));
                }
                writer.insert(ids.nextId(), userId, type.name(), actorUserId, null, null, null, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.groupManager(type, name, null, keys, targetName), actorUserId, null, path);
            }
            return made;
        }, 0);
    }

    /**
     * 소모임 일정 알림(GROUP_EVENT — V93, community api-groups.md 9절) — 받는 사람은 그 소모임의 멤버 한 명(부르는 쪽이 멤버마다), 일으킨 사람은 일을 한 모임장·일정 관리 매니저
     * (그 사람 본인에게는 보내지 않는다). 덧붙임 {@code groupId}·{@code groupName}·{@code eventId}·{@code title}(일정 제목)·{@code eventAction}(CREATED · UPDATED · CANCELED)
     * ·{@code reason}(취소 사유 — 적었을 때만). <b>회원이 끌 수 있다</b>(개인 설정의 「소모임 일정」). 업무 트랜잭션 안에서 부른다(세이브포인트 — 실패해도 일정 일을 깨지 않는다).
     */
    public int groupEvent(long userId, long groupId, String groupName, long eventId, String eventTitle, String action, String reason, long actorUserId) {
        if (actorUserId == userId) {
            return 0;
        }
        return isolated.run("member-notification:group-event", () -> {
            EventSwitch on = switchOf(MemberNotificationType.GROUP_EVENT, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(groupName, SNAPSHOT_MAX);
            String title = cut(eventTitle, SNAPSHOT_MAX);
            String why = reason == null || reason.isBlank() ? null : reason;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("groupId", Long.toString(groupId));
                payload.put("groupName", name);
                payload.put("eventId", Long.toString(eventId));
                payload.put("title", title);
                payload.put("eventAction", action);
                if (why != null) {
                    payload.put("reason", why);
                }
                writer.insert(ids.nextId(), userId, MemberNotificationType.GROUP_EVENT.name(), actorUserId, null, null, null, MAPPER.writeValueAsString(payload),
                        Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.groupEvent(name, title, action, why), actorUserId, null,
                        "/groups/" + groupId + "/events/" + eventId);
            }
            return made;
        }, 0);
    }

    /**
     * 소모임 글 공개 알림(V94 — community api-groups.md 11-8) — {@code GROUP_PUBLISH_REQUESTED}(받는 사람 = 모임장·「글 공개 처리·되돌리기」 매니저, 일으킨 사람 = 요청한 작성자)
     * · {@code GROUP_PUBLISH_APPROVED} · {@code GROUP_PUBLISH_REJECTED} · {@code GROUP_POST_PUBLISHED} · {@code GROUP_POST_MEMBERS_ONLY} · {@code GROUP_POST_RELEASED}
     * (받는 사람 = 글쓴이, 일으킨 사람 = 처리자). 덧붙임 {@code groupId}·{@code groupName}·{@code title}·{@code actorId}(+{@code actorRole} LEADER · MANAGER — 요청 알림 제외,
     * {@code reason} 거절 사유 — 적었을 때만). 끌 수 없는 알림. 일으킨 사람 본인에게는 보내지 않는다. 업무 트랜잭션 안에서 부른다(세이브포인트).
     *
     * @param path 메일 버튼이 가리킬 사이트 안 경로 — null 이면 그 글
     */
    public int groupPublish(MemberNotificationType type, long userId, long groupId, String groupName, long postId, String title, long actorUserId, String actorRole,
                            String reason, String path) {
        if (actorUserId == userId) {
            return 0;
        }
        return isolated.run("member-notification:group-publish", () -> {
            EventSwitch on = switchOf(type, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(groupName, SNAPSHOT_MAX);
            String postTitle = cut(title, SNAPSHOT_MAX);
            String why = reason == null || reason.isBlank() ? null : reason;
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("groupId", Long.toString(groupId));
                payload.put("groupName", name);
                payload.put("title", postTitle);
                payload.put("actorId", Long.toString(actorUserId));
                if (actorRole != null) {
                    payload.put("actorRole", actorRole);
                }
                if (why != null) {
                    payload.put("reason", why);
                }
                writer.insert(ids.nextId(), userId, type.name(), actorUserId, null, postId, null, MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                MemberNotificationMessages.Facts facts = MemberNotificationMessages.Facts.groupPublish(type, name, postTitle, actorRole, why, null);
                if (path == null) {
                    requestEmail(userId, facts, actorUserId, postId);
                } else {
                    requestEmail(userId, facts, actorUserId, null, path);
                }
            }
            return made;
        }, 0);
    }

    /**
     * 소모임 공개 범위가 바뀌었다(GROUP_VISIBILITY_CHANGED — V94, community api-groups.md 11-8) — 받는 사람은 멤버 한 명(부르는 쪽이 멤버마다, 바꾼 모임장 제외),
     * 덧붙임 {@code groupId}·{@code groupName}·{@code actorId}·{@code visibility}(바뀐 값 PUBLIC · PRIVATE). 끌 수 없는 알림. 업무 트랜잭션 안에서 부른다(세이브포인트).
     */
    public int groupVisibilityChanged(long userId, long groupId, String groupName, long actorUserId, String visibility) {
        if (actorUserId == userId) {
            return 0;
        }
        return isolated.run("member-notification:group-visibility", () -> {
            EventSwitch on = switchOf(MemberNotificationType.GROUP_VISIBILITY_CHANGED, userId);
            if (!on.delivers() || !writer.receivable(userId)) {
                return 0;
            }
            int made = 0;
            String name = cut(groupName, SNAPSHOT_MAX);
            if (on.inApp()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("groupId", Long.toString(groupId));
                payload.put("groupName", name);
                payload.put("actorId", Long.toString(actorUserId));
                payload.put("visibility", visibility);
                writer.insert(ids.nextId(), userId, MemberNotificationType.GROUP_VISIBILITY_CHANGED.name(), actorUserId, null, null, null,
                        MAPPER.writeValueAsString(payload), Instant.now());
                made = 1;
            }
            if (on.email()) {
                requestEmail(userId, MemberNotificationMessages.Facts.groupVisibilityChanged(name, visibility), actorUserId, null, "/groups/" + groupId);
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
     * 받는 회원에게 나가는 것 — 회원 이벤트 설정(관리자 설정 › 이벤트 › 회원)의 사용 여부 · 앱 알림 · 이메일(줄이 없으면 코드의 처음 값 {@link MemberEvent})에
     * 회원 개인 설정({@code member_notification_preferences}, 줄이 없으면 둘 다 켬)을 더한다. 회원이 끌 수 없는 이벤트(코드 잠금 또는 운영자 「회원이 끌 수 있음」 꺼짐)는
     * 개인 설정을 보지 않는다. 운영자 설정이 이미 아무것도 내보내지 않으면 개인 설정을 읽지 않는다.
     */
    private EventSwitch switchOf(MemberNotificationType type, long userId) {
        MemberEvent event = MemberEvent.of(type);
        EventSwitch site = writer.eventSetting(event.code())
                .orElse(new EventSwitch(event.defaultEnabled(), event.defaultInApp(), event.defaultEmail(), event.defaultMemberCanTurnOff()));
        EventSwitch on = new EventSwitch(site.enabled(), site.inApp(), site.email(), !event.locked() && site.memberCanTurnOff());
        if (!on.delivers()) {
            log.debug("회원 이벤트 설정에서 꺼진 이벤트라 만들지 않는다 event={}", event);
            return on;
        }
        if (on.memberCanTurnOff()) {
            on = on.withMemberChoice(writer.memberChoice(userId, event.code()).orElse(null));
            if (!on.delivers()) {
                log.debug("회원이 개인 설정에서 끈 이벤트라 만들지 않는다 event={}", event);
            }
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
