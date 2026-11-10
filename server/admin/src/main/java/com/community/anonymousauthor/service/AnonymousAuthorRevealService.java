package com.community.anonymousauthor.service;

import com.community.anonymousauthor.dto.request.AnonymousAuthorRevealRequest;
import com.community.anonymousauthor.dto.response.AnonymousAuthorRevealView;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.permission.dto.AdminActor;
import com.community.post.service.AnonymousAuthorLookup;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 익명 글·댓글 작성자 열람(봉인 열람 — docs/features/anonymous-board-contract.md 정책 10). 권한 {@code anonymous-author.reveal} 은 컨트롤러 선언이 본다.
 *
 * <ul>
 *   <li>사유(고르기) + 메모(1~{@value #MEMO_MAX}자) 없이는 열리지 않는다 — 칸 오류 400. 대상은 글·댓글 중 하나({@link AnonymousAuthorLookup}) — 없으면 404,
 *       익명이 아니면 409 {@code NOT_ANONYMOUS}.</li>
 *   <li>열람마다 같은 트랜잭션에서 감사 {@code ANONYMOUS_AUTHOR_REVEAL}(대상 글·댓글 · 사유 · 메모 — 회원 ID 는 싣지 않는다)과 관리자 알림 {@code ANONYMOUS_AUTHOR_REVEALED}
 *       (열람 권한자 중 연 사람 말고 — 사유 코드만)을 남긴 뒤에만 회원 요약을 준다. 감사가 실패하면 열리지 않는다.</li>
 *   <li>탈퇴로 작성자 정보가 지워졌으면 닉네임 null · {@code withdrawn true}(화면 「탈퇴한 회원」).</li>
 * </ul>
 */
@Service
public class AnonymousAuthorRevealService {

    private static final Logger log = LoggerFactory.getLogger(AnonymousAuthorRevealService.class);

    /** 사유 코드(화면 고르기) */
    public static final List<String> REASONS = List.of("LEGAL_REQUEST", "RIGHTS_REPORT", "ABUSE", "OTHER");
    public static final int MEMO_MAX = 500;

    private final AnonymousAuthorLookup lookup;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AuditLogService audit;
    private final NotificationPublisher notifications;

    public AnonymousAuthorRevealService(AnonymousAuthorLookup lookup, UserRepository users, ProfileRepository profiles, AuditLogService audit,
                                        NotificationPublisher notifications) {
        this.lookup = lookup;
        this.users = users;
        this.profiles = profiles;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public AnonymousAuthorRevealView reveal(AdminActor actor, AnonymousAuthorRevealRequest body) {
        String reason = body == null || body.reason() == null ? null : body.reason().strip().toUpperCase(Locale.ROOT);
        String memo = body == null || body.memo() == null ? "" : body.memo().strip();
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (reason == null || !REASONS.contains(reason)) {
            invalid.put("reason", new MessageRef("anonymous.reveal.reason"));
        }
        int memoLength = memo.codePointCount(0, memo.length());
        if (memoLength < 1 || memoLength > MEMO_MAX) {
            invalid.put("memo", new MessageRef("anonymous.reveal.memo", MEMO_MAX));
        }
        if (body == null || !AnonymousAuthorLookup.given(body.postId(), body.commentId())) {
            invalid.put("postId", new MessageRef("anonymous.target.one"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        AnonymousAuthorLookup.Found found = lookup.resolve(body.postId(), body.commentId(), "postId", "commentId");

        AuditEvent event = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.ANONYMOUS_AUTHOR_REVEAL, AuditResult.SUCCESS);
        event = found.commentId() != null ? event.target("COMMENT", Long.toString(found.commentId())).with("postId", Long.toString(found.postId()))
                : event.target("POST", Long.toString(found.postId()));
        audit.record(event.with("boardId", Long.toString(found.boardId())).with("reason", reason).with("memo", memo));
        notifications.publish(AdminNotificationEvent.ANONYMOUS_AUTHOR_REVEALED, NotificationContext.empty().actor(actor.operatorId()).args(reason));

        UserEntity user = users.findById(found.userId()).orElse(null);
        ProfileEntity profile = profiles.findById(found.userId()).orElse(null);
        boolean withdrawn = user == null || user.isGone() || profile == null;
        log.info("익명 작성자 열람 postId={} commentId={} reason={} by operatorId={}", found.postId(), found.commentId(), reason, actor.operatorId());
        return new AnonymousAuthorRevealView(Long.toString(found.userId()), withdrawn ? null : profile.getNickname(),
                user == null ? "WITHDRAWN" : user.getStatus().name(), withdrawn);
    }
}
