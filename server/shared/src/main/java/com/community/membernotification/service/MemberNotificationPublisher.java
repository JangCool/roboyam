package com.community.membernotification.service;

import com.community.membernotification.dto.CommentNotice;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.repository.master.MemberNotificationWriter;
import com.community.membernotification.repository.master.MemberNotificationWriter.BoardNotifyRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 알림 발행기 — 사건이 일어난 서비스가 <b>업무 트랜잭션 안에서</b>(업무 변경을 끝낸 뒤 마지막에) 한 줄로 부른다. docs/features/member-notification-contract.md.
 *
 * <ul>
 *   <li><b>트랜잭션</b>: 업무 트랜잭션에 참여한다 — 업무가 롤백되면 알림도 없다. 행 만들기는 {@link IsolatedSideWork}(JDBC 세이브포인트) 안에서 하므로 <b>알림 실패가 업무를 깨지 않는다</b>
 *       (발행은 던지지 않는다 — 실패는 ERROR 로그만). 부르는 쪽은 try-catch 로 감싸지 않는다.</li>
 *   <li><b>받는 사람</b>: 탈퇴·삭제되지 않은 회원. <b>내가 일으킨 일은 나에게 알리지 않는다</b>(내 글에 내가 단 댓글, 내 댓글에 내가 단 답글). 글쓴이는 <b>새 댓글만</b> 받고
 *       남들끼리 이어 가는 답글은 받지 않는다 — 답한 댓글의 작성자가 글쓴이 본인이어도 {@code REPLY} 하나만 간다(community 규약).</li>
 *   <li><b>끄기</b>: 게시판 기본 설정 「알림」의 그 종류({@link MemberNotificationType#boardSettingKind()}) {@code site} 가 꺼져 있으면 만들지 않는다. 회원 개인 끄기는 아직 없다(계약 「미확정」).
 *       포인트 회수·신고로 숨김은 끌 수 없는 계정 알림이다.</li>
 *   <li><b>묶기</b>: 같은 회원·같은 글의 <b>안 읽은</b> {@code COMMENT} 가 묶음 시간({@code group_window_minutes}, 0 = 묶지 않음) 안에 있으면 새 줄을 만들지 않고 그 줄의 묶인 수를 늘린다
 *       (일으킨 사람·가리키는 댓글은 가장 최근 것). 같은 글의 댓글 쓰기는 글 행 갱신(댓글 수)으로 줄을 서므로 동시 중복이 없다.</li>
 *   <li><b>개인정보</b>: 이메일·IP·비밀번호는 싣지 않는다. 비회원 작성자는 그때 이름만 남긴다. 문구는 저장하지 않고 읽을 때 요청 언어로 푼다.</li>
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

    public MemberNotificationPublisher(MemberNotificationWriter writer, IsolatedSideWork isolated, SnowflakeIdGenerator ids) {
        this.writer = writer;
        this.isolated = isolated;
        this.ids = ids;
    }

    /** 댓글이 저장됐다 — 원댓글이면 글쓴이에게 {@code COMMENT}, 답글이면 답한 댓글의 작성자에게 {@code REPLY}. 만든(또는 묶은) 알림 수를 돌려준다 */
    public int commentWritten(CommentNotice notice) {
        return isolated.run("member-notification:comment", () -> {
            Instant now = Instant.now();
            if (notice.reply()) {
                Long target = notice.replyTargetAuthorId();
                if (!deliverable(target, notice.commenterId(), MemberNotificationType.REPLY)) {
                    return 0;
                }
                writer.insert(ids.nextId(), target, MemberNotificationType.REPLY.name(), notice.commenterId(), guestName(notice), notice.postId(), notice.commentId(),
                        null, now);
                return 1;
            }
            Long target = notice.postAuthorId();
            if (!deliverable(target, notice.commenterId(), MemberNotificationType.COMMENT)) {
                return 0;
            }
            int window = writer.boardSettings().map(BoardNotifyRow::groupWindowMinutes).orElse(0);
            if (window > 0) {
                var grouped = writer.groupable(target, notice.postId(), now.minus(Duration.ofMinutes(window)));
                if (grouped.isPresent()) {
                    writer.addToGroup(grouped.get(), notice.commenterId(), guestName(notice), notice.commentId());
                    return 1;
                }
            }
            writer.insert(ids.nextId(), target, MemberNotificationType.COMMENT.name(), notice.commenterId(), guestName(notice), notice.postId(), notice.commentId(),
                    null, now);
            return 1;
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
            if (!writer.receivable(authorId)) {
                return 0;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("snapshot", cut(snapshot, SNAPSHOT_MAX));
            if (until != null) {
                payload.put("until", until.toString());
            }
            MemberNotificationType type = commentId == null ? MemberNotificationType.POST_HIDDEN : MemberNotificationType.COMMENT_HIDDEN;
            writer.insert(ids.nextId(), authorId, type.name(), null, null, postId, commentId, MAPPER.writeValueAsString(payload), Instant.now());
            return 1;
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
            if (!writer.receivable(userId)) {
                return 0;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kind", kind);
            payload.put("amount", amount);
            payload.put("note", note);
            writer.insert(ids.nextId(), userId, MemberNotificationType.POINTS_REVOKED.name(), null, null, null, null, MAPPER.writeValueAsString(payload), Instant.now());
            return 1;
        }, 0);
    }

    /** 받는 사람이 있고, 일으킨 사람이 아니고, 받을 수 있는 회원이고, 게시판 알림 설정에서 그 종류가 켜져 있다 */
    private boolean deliverable(Long target, Long actor, MemberNotificationType type) {
        if (target == null || Objects.equals(target, actor)) {
            return false;
        }
        if (!siteEnabled(type)) {
            log.debug("게시판 알림 설정에서 꺼진 종류라 만들지 않는다 type={}", type);
            return false;
        }
        return writer.receivable(target);
    }

    /** 게시판 기본 설정 「알림」에서 그 종류의 사이트 알림이 켜져 있는가 — 줄이 없거나 읽지 못하면 켜진 것으로 본다(기본값이 켜짐) */
    private boolean siteEnabled(MemberNotificationType type) {
        if (type.boardSettingKind() == null) {
            return true;
        }
        String json = writer.boardSettings().map(BoardNotifyRow::memberDefaults).orElse(null);
        if (json == null || json.isBlank()) {
            return true;
        }
        List<Map<String, Object>> rows = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
        });
        return rows.stream().filter(row -> type.boardSettingKind().equals(row.get("kind"))).findFirst()
                .map(row -> !Boolean.FALSE.equals(row.get("site"))).orElse(true);
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
