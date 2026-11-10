package com.community.aimember.service;

import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftKind;
import com.community.aimember.entity.AiDraftStatus;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.repository.master.AiDraftRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.BlockedWordException;
import com.community.board.service.BoardCatalog;
import com.community.comment.dto.request.CommentWriteRequest;
import com.community.comment.service.CommentService;
import com.community.group.service.GroupPostService;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.service.PostWriteService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 승인된(관리자 「게시」 · 바로 게시) AI 생성물을 <b>기존 쓰기 경로</b>로 등록한다(ai-members-contract 정책 9) — 글 {@link PostWriteService#create} · 소모임 글
 * {@link GroupPostService#create} · 댓글·답글 {@link CommentService#create}. 그래서 금칙어·제재·게시판 쓰기 판정·작성 간격·캐시·알림(회원에게 단 답글은 보통 댓글 알림)이
 * 사람 회원과 같게 돈다. 단 <b>쓰기 제한 면제</b>(계약 「서버 구현 결과」): 사이트 작성 간격·하루 한도는 보지 않고(실행기가 양을 정한다), 관리자가 그 계정의 쓸 곳으로 고른
 * 게시판이면 게시판 최소 쓰기·댓글 등급도 보지 않는다 — 금칙어·제재·게시판 막힘·잠금은 그대로. 작성자는 AI 회원의 회원 ID, 작성 IP 는 서버 자신({@value #SERVER_IP} — 사람 IP 가 아니다, 보유기간 배치가 지운다).
 *
 * <p>막히면(금칙어 · 쓰기 막힘 · 글·댓글이 사라짐 · 사이트 한도 · 형식) 그 줄은 {@code FAILED} + 까닭 코드로 남고 다시 시도하지 않는다(정책 11 — 짧게만). 전체 정지 중에는
 * 등록하지 않는다(승인 줄은 기다린다). 등록과 상태 기록은 따로 커밋된다 — 실행기는 잠금으로 한 곳에서만 돌므로 겹치지 않는다.
 */
@Component
public class AiDraftPublisher {

    private static final Logger log = LoggerFactory.getLogger(AiDraftPublisher.class);

    static final String SERVER_IP = "127.0.0.1";
    static final int BATCH = 20;

    private final AiDraftRepository drafts;
    private final AiMemberProfileRepository accounts;
    private final AiMemberQueries queries;
    private final BoardCatalog catalog;
    private final PostWriteService posts;
    private final GroupPostService groupPosts;
    private final CommentService comments;
    private final TransactionTemplate tx;
    private final AiMemberSettingsRepository settings;

    public AiDraftPublisher(AiDraftRepository drafts, AiMemberProfileRepository accounts, AiMemberQueries queries, BoardCatalog catalog, PostWriteService posts,
                            GroupPostService groupPosts, CommentService comments, PlatformTransactionManager txManager, AiMemberSettingsRepository settings) {
        this.settings = settings;
        this.drafts = drafts;
        this.accounts = accounts;
        this.queries = queries;
        this.catalog = catalog;
        this.posts = posts;
        this.groupPosts = groupPosts;
        this.comments = comments;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 승인 줄을 오래된 것부터 한 묶음 등록한다 — 등록한 수 */
    public int publishApproved() {
        Boolean stopped = tx.execute(status -> settings.findById(AiMemberSettingsEntity.ID).map(AiMemberSettingsEntity::isStopped).orElse(true));
        if (Boolean.TRUE.equals(stopped)) {
            return 0; // 전체 정지 중에는 승인 줄도 등록하지 않는다(계약 정책 11) — 계정 켜짐·키와는 상관없이 승인 줄은 등록한다
        }
        List<Long> ids = tx.execute(status -> queries.approvedIds(BATCH));
        int done = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            if (publishOne(id)) {
                done++;
            }
        }
        return done;
    }

    /** 한 줄 — 등록했으면 true */
    public boolean publishOne(long id) {
        AiDraftEntity d = tx.execute(status -> drafts.findById(id).orElse(null));
        if (d == null || d.getStatus() != AiDraftStatus.APPROVED) {
            return false;
        }
        AiMemberProfileEntity account = tx.execute(status -> accounts.findById(d.getAiMemberId()).orElse(null));
        if (account == null) {
            finish(id, null, null, "TARGET_GONE");
            return false;
        }
        long userId = account.getUserId();
        try {
            if (d.getKind() == AiDraftKind.POST) {
                if (d.getGroupId() != null) {
                    PostWriteRequest body = new PostWriteRequest(null, null, d.getTitle(), "TEXT", d.getBody(), null, null, null, null);
                    String postId = groupPosts.createByAiRunner(Long.toString(d.getGroupId()), userId, body, SERVER_IP).id();
                    finish(id, Long.parseLong(postId), null, null);
                } else {
                    EffectiveBoard board = d.getBoardId() == null ? null : catalog.snapshot().board(d.getBoardId()).orElse(null);
                    if (board == null) {
                        finish(id, null, null, "TARGET_GONE");
                        return false;
                    }
                    PostWriteRequest body = new PostWriteRequest(board.slug(), null, d.getTitle(), "TEXT", d.getBody(), null, null, null, null);
                    String postId = posts.createByAiRunner(body, userId, SERVER_IP, account.getBoardIds().contains(board.id())).id();
                    finish(id, Long.parseLong(postId), null, null);
                }
            } else {
                String parent = d.getKind() == AiDraftKind.REPLY && d.getParentCommentId() != null ? Long.toString(d.getParentCommentId()) : null;
                String replyTo = d.getKind() == AiDraftKind.REPLY && d.getReplyToCommentId() != null && !d.getReplyToCommentId().equals(d.getParentCommentId())
                        ? Long.toString(d.getReplyToCommentId()) : null;
                CommentWriteRequest body = new CommentWriteRequest("TEXT", d.getBody(), parent, replyTo, null, null, null);
                boolean gradeExempt = d.getGroupId() == null && d.getBoardId() != null && account.getBoardIds().contains(d.getBoardId());
                String commentId = comments.createByAiRunner(Long.toString(d.getPostId()), body, userId, SERVER_IP, gradeExempt).id();
                finish(id, d.getPostId(), Long.parseLong(commentId), null);
            }
            return true;
        } catch (BlockedWordException e) {
            finish(id, null, null, "BLOCKED_WORD");
        } catch (ValidationException e) {
            finish(id, null, null, "INVALID");
        } catch (CommonException e) {
            finish(id, null, null, reasonOf(e));
        } catch (RuntimeException e) {
            log.error("AI 생성물을 등록하지 못했다 draftId={} 원인={}", id, e.getClass().getSimpleName());
            finish(id, null, null, "ERROR");
        }
        return false;
    }

    private static String reasonOf(CommonException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatus().value());
        if (status == null) {
            return "ERROR";
        }
        return switch (status) {
            case NOT_FOUND, GONE -> "TARGET_GONE";
            case TOO_MANY_REQUESTS, CONFLICT -> "SITE_LIMIT";
            case UNAUTHORIZED, FORBIDDEN -> "WRITE_BLOCKED";
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> "INVALID";
            default -> "ERROR";
        };
    }

    private void finish(long id, Long postId, Long commentId, String failReason) {
        tx.executeWithoutResult(status -> drafts.findWithLockById(id).ifPresent(d -> {
            if (d.getStatus() != AiDraftStatus.APPROVED) {
                return;
            }
            if (failReason == null) {
                d.published(d.getKind() == AiDraftKind.POST ? postId : null, commentId, Instant.now().truncatedTo(ChronoUnit.MICROS));
            } else {
                d.fail(failReason);
            }
        }));
        if (failReason != null) {
            log.info("AI 생성물을 등록하지 않았다 draftId={} 까닭={}", id, failReason);
        }
    }
}
