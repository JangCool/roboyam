package com.community.debate.service;

import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.debate.entity.DebatePinRequestEntity;
import com.community.debate.exception.PinRequestException;
import com.community.debate.repository.master.DebatePinRequestRepository;
import com.community.debate.repository.master.DebateQueries;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.post.repository.master.PostRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 의견 고정 요청의 승인·거절 — 공개 사이트(글쓴이·게시판 매니저)와 관리자(운영자)가 같은 규칙을 쓴다. <b>부르는 쪽 트랜잭션에 참여한다</b>(요청 행은 잠가서 넘긴다).
 *
 * <ul>
 *   <li>대기 중이 아니면 409 {@code PIN_REQUEST_DECIDED}.</li>
 *   <li>승인: 의견이 지금 보여야 한다(지우거나 숨겼으면 409 {@code OPINION_UNAVAILABLE}) → {@code debate_pins} 에 고정(이미 고정이면 그대로 — 고정 시각은 처음 것).
 *       토론이 닫혔어도 승인할 수 있다(운영자의 직접 고정과 같다).</li>
 *   <li>거절: 사유 0~{@value DebatePinRequestEntity#REASON_MAX}자(앞뒤 공백을 떼고 비면 null — 길이는 부르는 쪽이 {@code fields.reason} 로 막는다).</li>
 *   <li>요청한 회원에게 회원 알림 {@code OPINION_PIN_REVIEWED}(끌 수 없음, 거절 사유와 함께).</li>
 * </ul>
 */
@Component
public class DebatePinDecisions {

    private final DebatePinRequestRepository requests;
    private final DebateQueries queries;
    private final CommentRepository comments;
    private final PostRepository posts;
    private final MemberNotificationPublisher notices;

    public DebatePinDecisions(DebatePinRequestRepository requests, DebateQueries queries, CommentRepository comments, PostRepository posts,
                              MemberNotificationPublisher notices) {
        this.requests = requests;
        this.queries = queries;
        this.comments = comments;
        this.posts = posts;
        this.notices = notices;
    }

    /**
     * @param locked       쓰기 잠금으로 읽은 요청
     * @param byKind       {@link DebatePinRequestEntity#BY_MEMBER} · {@link DebatePinRequestEntity#BY_OPERATOR}
     * @param by           확인한 회원 ID 또는 운영자 ID
     * @param pinOperatorId {@code debate_pins.pinned_by} 에 남길 운영자(회원이 승인하면 null)
     */
    public DebatePinRequestEntity decide(DebatePinRequestEntity locked, boolean approve, String reason, String byKind, long by, Long pinOperatorId, Instant now) {
        if (!locked.isPending()) {
            throw PinRequestException.conflict(PinRequestException.DECIDED);
        }
        if (approve) {
            CommentEntity c = comments.findByPostIdAndId(locked.getPostId(), locked.getCommentId()).orElse(null);
            if (c == null || c.isDeleted() || c.isHiddenAt(now)) {
                throw PinRequestException.conflict(PinRequestException.UNAVAILABLE);
            }
            queries.pin(locked.getCommentId(), locked.getPostId(), pinOperatorId, now);
        }
        String why = approve || reason == null || reason.isBlank() ? null : reason.strip();
        locked.decide(approve, why, byKind, by, now);
        requests.flush();
        String title = posts.findById(locked.getPostId()).map(p -> p.getTitle()).orElse(null);
        notices.opinionPinReviewed(locked.getRequesterId(), locked.getId(), locked.getPostId(), locked.getCommentId(),
                approve ? DebatePinRequestEntity.APPROVED : DebatePinRequestEntity.REJECTED, why, title);
        return locked;
    }

    /** 운영자가 의견을 직접 고정했다 — 그 의견의 대기 요청이 있으면 승인으로 닫는다(요청한 회원에게 알림) */
    public void closeOnDirectPin(long commentId, long operatorId, Instant now) {
        requests.findFirstByCommentIdAndStatus(commentId, DebatePinRequestEntity.PENDING).flatMap(r -> requests.findWithLockById(r.getId()))
                .filter(DebatePinRequestEntity::isPending)
                .ifPresent(r -> decide(r, true, null, DebatePinRequestEntity.BY_OPERATOR, operatorId, operatorId, now));
    }
}
