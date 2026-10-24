package com.community.debate.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardStaffEntity;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.board.service.BoardFields;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.debate.entity.DebatePinRequestEntity;
import com.community.debate.repository.master.DebatePinRequestRepository;
import com.community.post.entity.PostEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 의견 고정 요청을 누가 확인하는가 — 게시판 정책 {@code pinRequestReviewers}(유효값) 중 회원 쪽: {@code POST_AUTHOR}(그 글을 쓴 회원) · {@code BOARD_MANAGER}(그 게시판의
 * 매니저 — {@code board_staff} 역할 MANAGER, 스텝은 아니다). {@code OPERATOR} 는 관리자 앱에서 {@code highlight.manage} 로 확인한다. 자기 요청은 자기가 확인할 수 없다.
 */
@Component
public class PinReviewers {

    private final BoardStaffRepository staff;
    private final DebatePinRequestRepository requests;
    private final CommentRepository comments;

    public PinReviewers(BoardStaffRepository staff, DebatePinRequestRepository requests, CommentRepository comments) {
        this.staff = staff;
        this.requests = requests;
        this.comments = comments;
    }

    /** 이 회원이 이 글의 고정 요청을 확인할 수 있는가(회원 쪽) */
    public boolean canReview(EffectiveBoard b, PostEntity post, Long userId) {
        if (userId == null) {
            return false;
        }
        List<String> who = b.pinRequestReviewers();
        if (who.contains(BoardFields.PIN_REVIEWER_POST_AUTHOR) && userId.equals(post.getAuthorId())) {
            return true;
        }
        return who.contains(BoardFields.PIN_REVIEWER_BOARD_MANAGER)
                && staff.findByBoardId(b.id()).stream().anyMatch(s -> BoardStaffEntity.MANAGER.equals(s.getRole()) && userId.equals(s.getUserId()));
    }

    /** 새 요청을 알릴 회원(글쓴이·게시판 매니저 — 정책에 든 쪽만, 겹치지 않게) */
    public List<Long> memberReviewers(EffectiveBoard b, PostEntity post) {
        List<Long> out = new ArrayList<>();
        List<String> who = b.pinRequestReviewers();
        if (who.contains(BoardFields.PIN_REVIEWER_POST_AUTHOR) && post.getAuthorId() != null) {
            out.add(post.getAuthorId());
        }
        if (who.contains(BoardFields.PIN_REVIEWER_BOARD_MANAGER)) {
            staff.findByBoardId(b.id()).stream().filter(s -> BoardStaffEntity.MANAGER.equals(s.getRole())).map(BoardStaffEntity::getUserId)
                    .filter(id -> !out.contains(id)).forEach(out::add);
        }
        return out;
    }

    /** 운영자도 확인하는가(관리자 알림을 낼지) */
    public boolean operatorsReview(EffectiveBoard b) {
        return b.pinRequestReviewers().contains(BoardFields.PIN_REVIEWER_OPERATOR);
    }

    /**
     * 확인할 수 있는 회원에게 이 글의 대기 요청 수(내 요청·지우거나 숨긴 의견의 요청은 빼고) — 확인할 수 없으면 null.
     *
     * @param byId 그 글의 댓글(이미 읽었으면 — 없으면 null, 요청마다 읽는다)
     */
    public Integer pendingFor(EffectiveBoard b, PostEntity post, Long userId, Instant now, Map<Long, CommentEntity> byId) {
        if (!canReview(b, post, userId)) {
            return null;
        }
        return pending(post.getId(), userId, now, byId).size();
    }

    /** 확인할 대기 요청(오래된 것부터) — 내 요청·지우거나 숨긴 의견의 요청은 뺀다 */
    public List<DebatePinRequestEntity> pending(long postId, Long userId, Instant now, Map<Long, CommentEntity> byId) {
        List<DebatePinRequestEntity> out = new ArrayList<>();
        for (DebatePinRequestEntity r : requests.findByPostIdAndStatusOrderByIdAsc(postId, DebatePinRequestEntity.PENDING)) {
            if (r.getRequesterId().equals(userId)) {
                continue;
            }
            CommentEntity c = byId != null ? byId.get(r.getCommentId()) : comments.findByPostIdAndId(postId, r.getCommentId()).orElse(null);
            if (c != null && !c.isDeleted() && !c.isHiddenAt(now)) {
                out.add(r);
            }
        }
        return out;
    }
}
