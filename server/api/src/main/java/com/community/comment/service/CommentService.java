package com.community.comment.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.ContentBody;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.AlreadyLikedException;
import com.community.board.exception.BlockedWordException;
import com.community.board.exception.CannotLikeOwnException;
import com.community.board.exception.NotAuthorException;
import com.community.board.service.AuthorViews;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.board.service.GuestPasswords;
import com.community.board.service.PublicBoardService;
import com.community.board.service.RichText;
import com.community.captcha.CaptchaVerifier;
import com.community.comment.dto.request.CommentUpdateRequest;
import com.community.comment.dto.request.CommentWriteRequest;
import com.community.comment.dto.response.CommentLikeResult;
import com.community.comment.dto.response.CommentListResponse;
import com.community.comment.dto.response.CommentThreadView;
import com.community.comment.dto.response.CommentView;
import com.community.comment.dto.response.ReplyToView;
import com.community.comment.entity.CommentEntity;
import com.community.comment.exception.CommentDailyLimitException;
import com.community.comment.exception.CommentHiddenException;
import com.community.comment.exception.CommentNotFoundException;
import com.community.comment.exception.CommentTooFastException;
import com.community.comment.repository.master.CommentRepository;
import com.community.comment.repository.master.CommentRepositoryQDSL;
import com.community.membernotification.dto.CommentNotice;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.post.dto.WriteRateData;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostHiddenException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.post.service.WriterPromotion;
import com.community.user.entity.ProfileEntity;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 댓글 — 목록·쓰기·고치기·지우기·추천.
 *
 * <ul>
 *   <li><b>목록</b>: 원댓글(정렬 OLDEST·LATEST) + 그 아래 답글(오래된순, 지운 답글은 뺀다). 지운 원댓글은 답글이 있을 때만 자리를 남긴다({@code DELETED}, 본문·작성자 이름 없음).
 *       신고로 숨긴 댓글은 {@code HIDDEN}. 비회원이 보면 최근 {@code commentGuestDelayMinutes} 분 안의 댓글은 {@code DELAYED}(본문을 보내지 않는다) — 단 같은 IP 가 쓴 댓글은
 *       본인 것으로 보고 바로 보인다(목업의 「이 브라우저가 방금 쓴 댓글」을 IP 로 가늠한다 — 가정). 베스트는 추천 {@code bestCommentMinLikes} 이상·보이는 댓글, 추천 많은 순 최대 3개.</li>
 *   <li><b>쓰기 순서</b>(목업 {@code mockCommentCreate}): 글(404·403 숨김) → 게시판(403 꺼짐) → 댓글 판정(401·403 까닭) → 칸(본문 1~2,000자, 링크·이미지, 비회원, 답글 대상)
 *       → 금지어 → 자동입력 방지 → 작성 간격({@code 429 COMMENT_TOO_FAST}) → 하루 한도({@code 429 COMMENT_DAILY_LIMIT}). 답글은 1단계({@code parentId} = 원댓글).</li>
 *   <li>글의 보이는 댓글 수({@code posts.comment_count})는 쓰기·지우기·숨김이 같은 트랜잭션에서 원자적으로 고친다.</li>
 *   <li>회원 댓글은 저장이 커밋된 뒤 활동 수 조건 즉시 승급을 판정한다({@link WriterPromotion} — 별도 트랜잭션, 실패해도 댓글쓰기는 성공).</li>
 *   <li>같은 트랜잭션의 마지막에 댓글 포인트와 회원 알림({@code COMMENT}·{@code REPLY})을 낸다 — 둘 다 세이브포인트로 격리돼 실패해도 댓글쓰기는 성공한다. 지우면 받은 포인트를
 *       0P 까지 회수하고, 추천이 인정된 추천 문턱을 채우면 작성자에게 추천 보너스를 준다({@link PointService}).</li>
 * </ul>
 */
@Service
public class CommentService {

    static final String VISIBLE = "VISIBLE";
    static final String DELETED = "DELETED";
    static final String HIDDEN = "HIDDEN";
    static final String DELAYED = "DELAYED";
    /** 베스트 댓글 최대 수(가정) */
    static final int BEST_MAX = 3;

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostRepository posts;
    private final PostQueryService postQueries;
    private final CommentRepository comments;
    private final CommentRepositoryQDSL commentQueries;
    private final PostCounterQueries counters;
    private final AuthorViews authors;
    private final GuestPasswords passwords;
    private final CaptchaVerifier captcha;
    private final SnowflakeIdGenerator ids;
    private final WriterPromotion promotion;
    private final PointService points;
    private final MemberNotificationPublisher notices;
    private final ViewerBlocks viewerBlocks;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public CommentService(BoardCatalog catalog, BoardActorResolver actors, PostRepository posts, PostQueryService postQueries, CommentRepository comments,
                          CommentRepositoryQDSL commentQueries, PostCounterQueries counters, AuthorViews authors, GuestPasswords passwords,
                          CaptchaVerifier captcha, SnowflakeIdGenerator ids, WriterPromotion promotion, PointService points, MemberNotificationPublisher notices,
                          ViewerBlocks viewerBlocks, PlatformTransactionManager txManager) {
        this.viewerBlocks = viewerBlocks;
        this.promotion = promotion;
        this.points = points;
        this.notices = notices;
        this.catalog = catalog;
        this.actors = actors;
        this.posts = posts;
        this.postQueries = postQueries;
        this.comments = comments;
        this.commentQueries = commentQueries;
        this.counters = counters;
        this.authors = authors;
        this.passwords = passwords;
        this.captcha = captcha;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    // ── 목록 ──────────────────────────────────────────────────────────────

    /** 보는 사람 — 댓글 상태·내 것·추천 판정 */
    private record Viewer(Long userId, String ip, boolean guest, int delayMinutes, Set<Long> liked, ViewerBlocks.Lens lens) {
    }

    public CommentListResponse list(String postIdText, String sort, Long userId, String ip) {
        return readTx.execute(status -> {
            Instant now = Instant.now();
            PostEntity post = postQueries.requireVisible(postIdText, now);
            BoardCatalogSnapshot s = catalog.snapshot();
            EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
            if (post.isHiddenAt(now)) {
                throw new PostHiddenException();
            }
            List<CommentEntity> all = comments.findByPostIdOrderByIdAsc(post.getId());
            Viewer viewer = new Viewer(userId, ip, userId == null, b.commentGuestDelayMinutes(),
                    userId == null || all.isEmpty() ? Set.of() : counters.likedComments(post.getId(), userId),
                    all.isEmpty() ? ViewerBlocks.Lens.none() : viewerBlocks.lens(userId, all.stream().map(CommentEntity::getAuthorId).toList()));
            Map<Long, ProfileEntity> members = authors.members(all.stream().map(CommentEntity::getAuthorId).toList());
            Map<Long, CommentEntity> byId = new HashMap<>();
            all.forEach(c -> byId.put(c.getId(), c));

            List<CommentEntity> tops = new ArrayList<>(all.stream().filter(c -> c.getParentId() == null).toList());
            if ("LATEST".equals(sort)) {
                tops.sort(Comparator.comparing(CommentEntity::getId, Comparator.reverseOrder()));
            }
            Map<Long, List<CommentEntity>> replies = new HashMap<>();
            all.stream().filter(c -> c.getParentId() != null && !c.isDeleted()).forEach(c -> replies.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c));

            List<CommentThreadView> threads = new ArrayList<>();
            for (CommentEntity top : tops) {
                List<CommentEntity> children = replies.getOrDefault(top.getId(), List.of());
                if (top.isDeleted() && children.isEmpty()) {
                    continue; // 지운 원댓글은 답글이 있을 때만 자리를 남긴다
                }
                threads.add(CommentThreadView.of(view(top, viewer, byId, members, s, now),
                        children.stream().map(c -> view(c, viewer, byId, members, s, now)).toList()));
            }
            int minLikes = s.defaults().bestCommentMinLikes();
            List<CommentView> best = all.stream()
                    .filter(c -> !c.isDeleted() && !c.isHiddenAt(now) && c.getLikeCount() >= minLikes)
                    .map(c -> view(c, viewer, byId, members, s, now))
                    .filter(v -> VISIBLE.equals(v.state()))
                    .sorted(Comparator.comparingInt(CommentView::likeCount).reversed().thenComparing(CommentView::id))
                    .limit(BEST_MAX)
                    .toList();
            return new CommentListResponse(threads, best, post.getCommentCount());
        });
    }

    private CommentView view(CommentEntity c, Viewer viewer, Map<Long, CommentEntity> byId, Map<Long, ProfileEntity> members, BoardCatalogSnapshot s,
                             Instant now) {
        String state;
        if (c.isDeleted()) {
            state = DELETED;
        } else if (c.isHiddenAt(now)) {
            state = HIDDEN;
        } else if (delayed(c, viewer, now)) {
            state = DELAYED;
        } else {
            state = VISIBLE;
        }
        PostAuthorView author = authors.of(c.getAuthorId(), c.getGuestName(), c.getAuthorIp(), members, s).withMemo(viewer.lens().memoOf(c.getAuthorId()));
        ReplyToView replyTo = null;
        if (c.getReplyToId() != null) {
            CommentEntity target = byId.get(c.getReplyToId());
            if (target != null) {
                String name = target.isDeleted() ? "" : authors.of(target.getAuthorId(), target.getGuestName(), null, members, s).name();
                replyTo = new ReplyToView(Long.toString(target.getId()), name);
            }
        }
        return new CommentView(Long.toString(c.getId()), Long.toString(c.getPostId()), c.getParentId() == null ? null : Long.toString(c.getParentId()), replyTo,
                DELETED.equals(state) ? AuthorViews.blank(author) : author, VISIBLE.equals(state) ? c.getContent() : null, c.getContentFormat().name(), state,
                c.getCreatedAt(),
                c.getEditedAt(), c.getLikeCount(), viewer.liked().contains(c.getId()), viewer.userId() != null && viewer.userId().equals(c.getAuthorId()),
                VISIBLE.equals(state) ? viewer.lens().blockedOf(c.getAuthorId(), RichText.plainOf(c.getContentFormat(), c.getContent())) : null);
    }

    /** 비회원에게 아직 보이지 않는 댓글인가 — 같은 IP 가 쓴 비회원 댓글은 본인 것으로 본다 */
    private static boolean delayed(CommentEntity c, Viewer viewer, Instant now) {
        if (!viewer.guest() || viewer.delayMinutes() <= 0) {
            return false;
        }
        if (c.isGuest() && viewer.ip() != null && viewer.ip().equals(c.getAuthorIp())) {
            return false;
        }
        return c.getCreatedAt().isAfter(now.minus(Duration.ofMinutes(viewer.delayMinutes())));
    }

    // ── 쓰기 ──────────────────────────────────────────────────────────────

    public CommentView create(String postIdText, CommentWriteRequest body, Long userId, String ip) {
        Instant start = Instant.now();
        PostEntity post = postQueries.requireVisible(postIdText, start);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
        if (post.isHiddenAt(start)) {
            throw new PostHiddenException();
        }
        BoardActor actor = actors.resolve(userId, s);
        PostWriteService.requireWritable(BoardAccessPolicy.commentBlocked(b, actor, s));
        BoardSecurityValues sec = s.security();

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ContentBody content = checkContent(b, body.contentFormat(), body.content(), writer(actor, b, s), sec, fields);
        String guestName = null;
        if (actor.guest()) {
            ContentRules.checkGuest(body.guest(), sec, fields);
            guestName = body.guest() == null ? null : ContentRules.normalize(body.guest().name());
        }
        Long parentId = null;
        Long replyToId = null;
        if (body.parentId() != null && !body.parentId().isBlank()) {
            parentId = PostQueryService.parseId(body.parentId());
            Optional<CommentEntity> parent = parentId == null ? Optional.empty() : comments.findByPostIdAndId(post.getId(), parentId);
            if (parent.isEmpty() || parent.get().getParentId() != null || parent.get().isDeleted()) {
                fields.put("parentId", new MessageRef("board.comment.parent"));
            } else if (body.replyToId() != null && !body.replyToId().isBlank()) {
                replyToId = PostQueryService.parseId(body.replyToId());
                Long parentKey = parentId;
                Optional<CommentEntity> target = replyToId == null ? Optional.empty() : comments.findByPostIdAndId(post.getId(), replyToId);
                if (target.isEmpty() || !(target.get().getId().equals(parentKey) || parentKey.equals(target.get().getParentId()))) {
                    fields.put("parentId", new MessageRef("board.comment.parent"));
                }
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Optional<String> word = ContentRules.blockedWord(sec, content.wordsHaystack(), guestName);
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        ContentBody masked = content.masked(sec);
        String name = ContentRules.mask(sec, guestName);
        String passwordHash = null;
        if (actor.guest()) {
            if (Boolean.TRUE.equals(sec.guestCaptchaRequired()) && !captcha.verify(body.guest().captchaToken(), ip)) {
                throw ValidationException.of("guest.captchaToken", new MessageRef("board.guest.captcha.failed"));
            }
            passwordHash = passwords.hash(body.guest().password());
        }

        long id = ids.nextId();
        Long authorId = actor.guest() ? null : actor.userId();
        Long parent = parentId;
        Long replyTo = replyToId;
        String hash = passwordHash;
        CommentView created = tx.execute(status -> {
            Instant now = Instant.now();
            checkRate(s, authorId, ip, now);
            CommentEntity comment = CommentEntity.create(id, post.getId(), b.id(), parent, replyTo, authorId, name, hash, ip, masked.format(), masked.content(),
                    now);
            comments.persist(comment);
            counters.addCommentCount(post.getId(), 1);
            Map<Long, CommentEntity> byId = new HashMap<>();
            if (replyTo != null) {
                comments.findByPostIdAndId(post.getId(), replyTo).ifPresent(t -> byId.put(t.getId(), t));
            }
            List<Long> memberIds = new ArrayList<>();
            memberIds.add(authorId);
            byId.values().forEach(t -> memberIds.add(t.getAuthorId()));
            // 쓴 사람에게는 공개 지연 없이 보인다
            Viewer self = new Viewer(userId, ip, false, 0, Set.of(), ViewerBlocks.Lens.none());
            CommentView view = view(comment, self, byId, authors.members(memberIds), s, now);
            // 같은 트랜잭션의 마지막에: 댓글 포인트(회원만) · 회원 알림 — 둘 다 실패해도 댓글쓰기는 그대로다(세이브포인트로 격리)
            if (authorId != null) {
                points.earn(authorId, PointReason.COMMENT, id);
            }
            Long replyTargetAuthor = null;
            if (parent != null) {
                CommentEntity target = replyTo != null ? byId.get(replyTo) : comments.findByPostIdAndId(post.getId(), parent).orElse(null);
                replyTargetAuthor = target == null ? null : target.getAuthorId();
            }
            notices.commentWritten(new CommentNotice(post.getId(), post.getAuthorId(), id, authorId, name, replyTargetAuthor, parent != null, post.getTitle(),
                    masked.plain()));
            return view;
        });
        promotion.afterWrite(authorId); // 커밋 뒤 — 회원 댓글이면 활동 수 조건 즉시 승급(실패해도 댓글쓰기는 성공)
        return created;
    }

    /** 작성 간격 → 하루 한도(작성자 단위 잠금 아래) */
    private void checkRate(BoardCatalogSnapshot s, Long authorId, String ip, Instant now) {
        counters.lockWriter("comment:" + (authorId != null ? "m:" + authorId : "g:" + ip));
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

    private static ContentRules.Writer writer(BoardActor actor, EffectiveBoard b, BoardCatalogSnapshot s) {
        return actor.guest() ? ContentRules.Writer.GUEST
                : BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
    }

    /**
     * 본문 형식 + 본문 1~2,000자(HTML 은 보이는 글자) + 링크·이미지(댓글은 이미지 불가). 형식이 빠지면 TEXT, 모르는 형식·댓글 에디터 NONE 게시판의 HTML 은
     * {@code fields.contentFormat}. HTML 은 간단 에디터 허용 목록으로 정화한다(이미지 표식은 간단 목록에 없어 사라진다).
     */
    private static ContentBody checkContent(EffectiveBoard b, String formatText, String raw, ContentRules.Writer writer, BoardSecurityValues sec,
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
        ContentBody content = format == ContentFormat.HTML ? ContentBody.html(raw, RichText.Level.SIMPLE) : ContentBody.text(raw);
        int length = ContentRules.length(content.plain());
        if (length < 1 || length > ContentRules.COMMENT_MAX) {
            fields.put("content", new MessageRef("board.comment.length", String.format("%,d", ContentRules.COMMENT_MAX)));
        } else if (content.isHtml() && content.content().length() > ContentRules.COMMENT_HTML_MAX) {
            fields.put("content", new MessageRef("board.comment.html.tooLong"));
        } else {
            ContentRules.checkCommentLinksImages(content, writer, sec, fields);
        }
        return content;
    }

    // ── 고치기·지우기·추천 ─────────────────────────────────────────────────

    /** 댓글 — 형식이 틀리거나 없거나 지웠거나 글이 사라졌으면 404 */
    private CommentEntity requireComment(String idText, Instant now) {
        Long id = PostQueryService.parseId(idText);
        Long postId = id == null ? null : commentQueries.postIdOf(id).orElse(null);
        CommentEntity comment = postId == null ? null : comments.findByPostIdAndId(postId, id).orElse(null);
        if (comment == null || comment.isDeleted()) {
            throw new CommentNotFoundException();
        }
        PostEntity post = posts.findById(postId).orElse(null);
        if (post == null || post.isGoneAt(now)) {
            throw new CommentNotFoundException();
        }
        return comment;
    }

    private void requireOwner(CommentEntity c, String password, Long userId, String ip) {
        if (c.isGuest()) {
            passwords.require(c.getGuestPasswordHash(), password, ip, "comment:" + c.getId());
            return;
        }
        if (userId == null || !userId.equals(c.getAuthorId())) {
            throw new NotAuthorException();
        }
    }

    public CommentView update(String idText, CommentUpdateRequest body, Long userId, String ip) {
        Instant start = Instant.now();
        CommentEntity comment = requireComment(idText, start);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, comment.getBoardId());
        if (comment.isHiddenAt(start)) {
            throw new CommentHiddenException();
        }
        requireOwner(comment, body.password(), userId, ip);
        BoardActor actor = actors.resolve(userId, s);
        ContentRules.Writer writer = comment.isGuest() ? ContentRules.Writer.GUEST : writer(actor, b, s);
        BoardSecurityValues sec = s.security();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ContentBody content = checkContent(b, body.contentFormat(), body.content(), writer, sec, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Optional<String> word = ContentRules.blockedWord(sec, content.wordsHaystack());
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        ContentBody masked = content.masked(sec);
        return tx.execute(status -> {
            Instant now = Instant.now();
            CommentEntity locked = comments.findWithLockByPostIdAndId(comment.getPostId(), comment.getId()).filter(c -> !c.isDeleted())
                    .orElseThrow(CommentNotFoundException::new);
            if (locked.isHiddenAt(now)) {
                throw new CommentHiddenException();
            }
            locked.edit(masked.format(), masked.content(), now);
            Map<Long, CommentEntity> byId = new HashMap<>();
            if (locked.getReplyToId() != null) {
                comments.findByPostIdAndId(locked.getPostId(), locked.getReplyToId()).ifPresent(t -> byId.put(t.getId(), t));
            }
            List<Long> memberIds = new ArrayList<>();
            memberIds.add(locked.getAuthorId());
            byId.values().forEach(t -> memberIds.add(t.getAuthorId()));
            Set<Long> liked = userId == null ? Set.of() : counters.likedComments(locked.getPostId(), userId);
            return view(locked, new Viewer(userId, ip, false, 0, liked, ViewerBlocks.Lens.none()), byId, authors.members(memberIds), s, now);
        });
    }

    /** 지우기 — 답글이 있는 원댓글은 목록에 자리만 남는다. 보이던 댓글이면 글의 댓글 수를 하나 줄인다 */
    public void delete(String idText, String password, Long userId, String ip) {
        Instant start = Instant.now();
        CommentEntity comment = requireComment(idText, start);
        PublicBoardService.requireActive(catalog.snapshot(), comment.getBoardId());
        requireOwner(comment, password, userId, ip);
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            CommentEntity locked = comments.findWithLockByPostIdAndId(comment.getPostId(), comment.getId()).filter(c -> !c.isDeleted())
                    .orElseThrow(CommentNotFoundException::new);
            boolean wasVisible = !locked.isHiddenAt(now);
            locked.delete(now);
            if (wasVisible) {
                counters.addCommentCount(locked.getPostId(), -1);
            }
            if (locked.getAuthorId() != null) {
                points.revokeContent(locked.getAuthorId(), PointService.REF_COMMENT, locked.getId()); // 본인 삭제 — 받은 포인트를 0P 까지 회수(알림 없음)
            }
        });
    }

    /** 추천 — 회원만, 정지 403, 내 댓글 400, 이미 추천 409. 취소 없음 */
    public CommentLikeResult like(String idText, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        PostWriteService.requireReactable(actors.resolve(userId, s));
        return tx.execute(status -> {
            Instant now = Instant.now();
            CommentEntity comment = requireComment(idText, now);
            PublicBoardService.requireActive(s, comment.getBoardId());
            if (comment.isHiddenAt(now)) {
                throw new CommentHiddenException();
            }
            if (userId.equals(comment.getAuthorId())) {
                throw new CannotLikeOwnException(true);
            }
            if (!counters.insertCommentLike(comment.getId(), comment.getPostId(), userId)) {
                throw new AlreadyLikedException(true);
            }
            int likes = counters.incrementCommentLikes(comment.getPostId(), comment.getId()); // 댓글 행 갱신 — 같은 댓글의 추천이 여기서 줄을 선다
            points.likeBonus(comment.getAuthorId(), PointService.REF_COMMENT, comment.getId(), userId);
            return new CommentLikeResult(likes, true);
        });
    }
}
