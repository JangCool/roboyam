package com.community.comment.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.ContentBody;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.ContentRules;
import com.community.board.service.RichText;
import com.community.comment.entity.CommentEntity;
import com.community.comment.exception.CommentDailyLimitException;
import com.community.comment.exception.CommentTooFastException;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.repository.master.CommentRepositoryQDSL;
import com.community.post.dto.WriteRateData;
import com.community.post.entity.ContentFormat;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.service.PostContentFields;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 새 댓글·답글 쓰기 판정 중 공개 사이트(api {@code CommentService})와 관리자 서버에서 도는 AI 회원 쓰기({@code AiContentWriter})가 같이 쓰는 것 — 답글 대상, 본문 칸 검사,
 * 작성 간격·하루 한도(작성자 단위 잠금 아래). 2026-11-10 AI 실행기를 admin 으로 옮기며 {@code CommentService} 에서 그대로 떼어 냈다(규칙이 두 군데로 갈라지지 않게).
 */
@Component
public class CommentWriteRules {

    private final CommentRepository comments;
    private final CommentRepositoryQDSL commentQueries;
    private final PostCounterQueries counters;

    public CommentWriteRules(CommentRepository comments, CommentRepositoryQDSL commentQueries, PostCounterQueries counters) {
        this.comments = comments;
        this.commentQueries = commentQueries;
        this.counters = counters;
    }

    /**
     * 답글 대상 — 원댓글({@code parentId}, 1단계)과 가리킨 댓글({@code replyToId} — 원댓글 또는 그 아래 답글). 틀리면 {@code fields.parentId}.
     *
     * @param parent 원댓글 행(원댓글이 아니거나 틀렸으면 null)
     */
    public record ReplyTarget(Long parentId, Long replyToId, CommentEntity parent) {
    }

    /**
     * 답글 대상 판정 — {@code parentText} 가 비면 원댓글(대상 없음). 원댓글은 같은 글의 지우지 않은 원댓글이어야 하고, {@code replyToText} 는 그 원댓글이거나 그 아래 답글이어야 한다.
     * 답글이 의견인지(원댓글이 의견인지)를 정하므로 입장보다 먼저 본다.
     */
    public ReplyTarget replyTarget(long postId, String parentText, String replyToText, Map<String, MessageRef> fields) {
        Long parentId = null;
        Long replyToId = null;
        CommentEntity parentRow = null;
        if (parentText != null && !parentText.isBlank()) {
            parentId = PostContentFields.parseId(parentText);
            Optional<CommentEntity> parent = parentId == null ? Optional.empty() : comments.findByPostIdAndId(postId, parentId);
            if (parent.isEmpty() || parent.get().getParentId() != null || parent.get().isDeleted()) {
                fields.put("parentId", new MessageRef("board.comment.parent"));
            } else {
                parentRow = parent.get();
                if (replyToText != null && !replyToText.isBlank()) {
                    replyToId = PostContentFields.parseId(replyToText);
                    Long parentKey = parentId;
                    Optional<CommentEntity> target = replyToId == null ? Optional.empty() : comments.findByPostIdAndId(postId, replyToId);
                    if (target.isEmpty() || !(target.get().getId().equals(parentKey) || parentKey.equals(target.get().getParentId()))) {
                        fields.put("parentId", new MessageRef("board.comment.parent"));
                    }
                }
            }
        }
        return new ReplyTarget(parentId, replyToId, parentRow);
    }

    /** 작성 간격({@code 429 COMMENT_TOO_FAST}) → 하루 한도({@code 429 COMMENT_DAILY_LIMIT}) — 작성자 단위 잠금 아래(부르는 쪽 트랜잭션 안). 작성자 열쇠는 회원 ID, 비회원은 IP */
    public void checkRate(BoardCatalogSnapshot s, Long authorId, String ip, Instant now) {
        counters.lockWriter(writerKey(authorId, ip));
        int gap = s.defaults().commentGapSeconds();
        int daily = s.defaults().commentDailyLimit();
        Instant since = now.minus(Duration.ofDays(1));
        WriteRateData rate = commentQueries.rate(authorId, ip, since, SnowflakeIdGenerator.floorIdAt(since));
        if (gap > 0 && rate.lastAt() != null) {
            long elapsedMs = Duration.between(rate.lastAt(), now).toMillis();
            if (elapsedMs < gap * 1000L) {
                throw new CommentTooFastException(gap, Math.max(1, (gap * 1000L - elapsedMs + 999) / 1000));
            }
        }
        if (rate.countInWindow() >= daily) {
            throw new CommentDailyLimitException(daily);
        }
    }

    /** AI 회원 실행기(ai-members-contract 「쓰기 제한 면제」) — 작성 간격·하루 한도는 보지 않고 같은 작성자 잠금만 잡는다(같은 회원의 쓰기와 줄을 선다) */
    public void lockWithoutLimits(long authorId) {
        counters.lockWriter(writerKey(authorId, null));
    }

    private static String writerKey(Long authorId, String ip) {
        return "comment:" + (authorId != null ? "m:" + authorId : "g:" + ip);
    }

    /** 링크·이미지 규칙의 작성자 구분 — 비회원 · 새 회원 규칙 대상 · 회원 */
    public static ContentRules.Writer writer(BoardActor actor, EffectiveBoard b, BoardCatalogSnapshot s) {
        return actor.guest() ? ContentRules.Writer.GUEST
                : BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
    }

    /**
     * 본문 형식 + 본문 1~{@code max}자(HTML 은 보이는 글자 — 일반 댓글 {@link ContentRules#COMMENT_MAX}, 입장 배지가 붙는 댓글은 토론 의견 한도)
     * + 링크·이미지(댓글은 이미지 불가). 형식이 빠지면 TEXT, 모르는 형식·댓글 에디터 NONE 게시판의 HTML 은
     * {@code fields.contentFormat}. HTML 은 댓글 허용 목록(간단 + 영상 표식 1개까지)으로 정화한다(이미지 표식은 목록에 없어 사라진다). 영상만 있는 댓글은 글자가 없어도 받는다.
     */
    public static ContentBody checkContent(EffectiveBoard b, String formatText, String raw, ContentRules.Writer writer, BoardSecurityValues sec, int max,
                                           Map<String, MessageRef> fields) {
        ContentFormat format = ContentRules.format(formatText);
        if (format == null || (format == ContentFormat.HTML && !b.commentHtml())) {
            fields.put("contentFormat", new MessageRef(format == null ? "board.content.format.unknown" : "board.content.format.notAllowed"));
            return ContentBody.text(raw);
        }
        if (format == ContentFormat.HTML && raw != null && raw.length() > ContentRules.COMMENT_HTML_MAX * 2) {
            fields.put("content", new MessageRef("board.comment.html.tooLong"));
            return ContentBody.text("");
        }
        ContentBody content = format == ContentFormat.HTML ? ContentBody.html(raw, RichText.Level.COMMENT) : ContentBody.text(raw);
        int length = ContentRules.length(content.plain());
        // 영상만 있는 댓글(영상 주소를 붙여 넣은 것)은 보이는 글자가 없어도 받는다
        if ((length < 1 && content.videos() == 0) || length > max) {
            fields.put("content", new MessageRef("board.comment.length", String.format("%,d", max)));
        } else if (content.isHtml() && content.content().length() > ContentRules.COMMENT_HTML_MAX) {
            fields.put("content", new MessageRef("board.comment.html.tooLong"));
        } else if (content.videos() > ContentRules.COMMENT_VIDEO_MAX) {
            fields.put("content", new MessageRef("board.comment.video.limit", ContentRules.COMMENT_VIDEO_MAX));
        } else {
            ContentRules.checkCommentLinksImages(content, writer, sec, fields);
        }
        return content;
    }
}
