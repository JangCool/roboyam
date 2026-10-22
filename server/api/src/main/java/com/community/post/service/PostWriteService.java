package com.community.post.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.EffectiveBoard;
import com.community.board.exception.AlreadyLikedException;
import com.community.board.exception.BlockedWordException;
import com.community.board.exception.BoardWriteBlockedException;
import com.community.board.exception.CannotLikeOwnException;
import com.community.board.exception.NotAuthorException;
import com.community.board.exception.PasswordMismatchException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.board.service.ContentRules;
import com.community.board.service.GuestPasswords;
import com.community.board.service.PublicBoardService;
import com.community.captcha.CaptchaVerifier;
import com.community.permission.entity.MemberPermission;
import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.post.dto.WriteRateData;
import com.community.post.dto.request.PostUpdateRequest;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostDeleteResult;
import com.community.post.dto.response.PostDetailView;
import com.community.post.dto.response.PostLikeResult;
import com.community.post.dto.response.PostProtectionView;
import com.community.post.dto.response.PostWriteResult;
import com.community.post.entity.PostEntity;
import com.community.post.exception.DuplicatePostException;
import com.community.post.exception.PostDailyLimitException;
import com.community.post.exception.PostHiddenException;
import com.community.post.exception.PostNotFoundException;
import com.community.post.exception.PostProtectedException;
import com.community.post.exception.PostTooFastException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글 쓰기·고치기·지우기·비밀번호 확인·추천.
 *
 * <p><b>쓰기 검사 순서</b>(user-web 목업 {@code mockPostCreate} 와 같다): 게시판(404·403 꺼짐) → 쓰기 판정({@link BoardAccessPolicy} — 로그인 필요는 401, 나머지 403 까닭 코드)
 * → 칸(제목·본문 길이, 말머리, 19금, 링크·이미지 수, 비회원 이름·비밀번호·자동입력 방지 토큰 — {@code 400 VALIDATION}) → 금지어({@code 400 BLOCKED_WORD}, 가림 방식이면 가려 저장)
 * → 자동입력 방지 확인(비회원, 트랜잭션 밖 외부 요청 — 실패는 {@code fields.guest.captchaToken}) → 작성 간격({@code 429 POST_TOO_FAST}) → 하루 한도({@code 429 POST_DAILY_LIMIT})
 * → 같은 글 반복({@code 409 DUPLICATE_POST}). 속도 판정과 저장은 작성자 단위 어드바이저리 잠금 아래 한 트랜잭션이다(같은 사람의 동시 요청이 한도를 넘지 못하게).
 * 작성자 열쇠는 회원 ID, 비회원은 작성 IP 다. 하루 = 최근 24시간(목업과 같음).
 *
 * <p>bcrypt(비회원 비밀번호 해시·확인)와 캡차 확인은 느리거나 외부 요청이라 트랜잭션 밖에서 하고, 저장은 {@link TransactionTemplate} 으로 짧게 연다.
 *
 * <p>회원 글은 저장이 커밋된 뒤 활동 수 조건 즉시 승급을 판정한다({@link WriterPromotion} — 별도 트랜잭션, 실패해도 글쓰기는 성공).
 *
 * <p><b>포인트</b>({@link PointService}): 회원 글 저장 트랜잭션의 마지막에 글 포인트, 지우기(바로·예약 모두 — 본인 요청 시점)에 받은 포인트 회수, 추천이 인정된 추천 문턱을 채우면 작성자에게
 * 추천 보너스. 모두 세이브포인트로 격리돼 실패해도 글쓰기·지우기·추천은 성공한다.
 */
@Service
public class PostWriteService {

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final PostRepository posts;
    private final PostRepositoryQDSL postQueries;
    private final PostCounterQueries counters;
    private final PostQueryService queries;
    private final GuestPasswords passwords;
    private final CaptchaVerifier captcha;
    private final SnowflakeIdGenerator ids;
    private final WriterPromotion promotion;
    private final PointService points;
    private final TransactionTemplate tx;

    public PostWriteService(BoardCatalog catalog, BoardActorResolver actors, PostRepository posts, PostRepositoryQDSL postQueries, PostCounterQueries counters,
                            PostQueryService queries, GuestPasswords passwords, CaptchaVerifier captcha, SnowflakeIdGenerator ids, WriterPromotion promotion,
                            PointService points, PlatformTransactionManager txManager) {
        this.promotion = promotion;
        this.points = points;
        this.catalog = catalog;
        this.actors = actors;
        this.posts = posts;
        this.postQueries = postQueries;
        this.counters = counters;
        this.queries = queries;
        this.passwords = passwords;
        this.captcha = captcha;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 검사를 마친 글 칸 */
    private record Cleaned(Long prefixId, String title, String content, boolean adult, boolean hasImage) {
    }

    public PostWriteResult create(PostWriteRequest body, Long userId, String ip) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, body.boardSlug());
        BoardActor actor = actors.resolve(userId, s);
        requireWritable(BoardAccessPolicy.writeBlocked(b, actor, s));
        BoardSecurityValues sec = s.security();

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ContentRules.Writer writer = actor.guest() ? ContentRules.Writer.GUEST
                : BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
        Cleaned c = checkFields(b, actor.staffOf(b.id()), writer, null, body.prefixId(), body.title(), body.content(), body.adult(), sec, fields);
        String guestName = null;
        if (actor.guest()) {
            ContentRules.checkGuest(body.guest(), sec, fields);
            guestName = body.guest() == null ? null : ContentRules.normalize(body.guest().name());
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Optional<String> word = ContentRules.blockedWord(sec, c.title(), c.content(), guestName);
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        String title = ContentRules.mask(sec, c.title());
        String content = ContentRules.mask(sec, c.content());
        String name = ContentRules.mask(sec, guestName);

        String passwordHash = null;
        if (actor.guest()) {
            if (Boolean.TRUE.equals(sec.guestCaptchaRequired()) && !captcha.verify(body.guest().captchaToken(), ip)) {
                throw ValidationException.of("guest.captchaToken", new MessageRef("board.guest.captcha.failed"));
            }
            passwordHash = passwords.hash(body.guest().password());
        }

        long id = ids.nextId();
        String hash = passwordHash;
        Long authorId = actor.guest() ? null : actor.userId();
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            checkRate(s, authorId, ip, title, content, now);
            posts.persist(PostEntity.create(id, b.id(), c.prefixId(), authorId, name, hash, ip, title, content, c.adult(), c.hasImage(), now));
            if (authorId != null) {
                points.earn(authorId, PointReason.POST, id); // 실패·상한이어도 글은 그대로 올라간다
            }
        });
        promotion.afterWrite(authorId); // 커밋 뒤 — 회원 글이면 활동 수 조건 즉시 승급(실패해도 글쓰기는 성공)
        return new PostWriteResult(Long.toString(id));
    }

    /** 작성 간격 → 하루 한도 → 같은 글 반복(작성자 단위 잠금 아래) */
    private void checkRate(BoardCatalogSnapshot s, Long authorId, String ip, String title, String content, Instant now) {
        counters.lockWriter("post:" + (authorId != null ? "m:" + authorId : "g:" + ip));
        int gap = s.defaults().postGapSeconds();
        int daily = s.defaults().postDailyLimit();
        WriteRateData rate = postQueries.rate(authorId, ip, now.minus(Duration.ofDays(1)));
        if (gap > 0 && rate.lastAt() != null) {
            long elapsedMs = Duration.between(rate.lastAt(), now).toMillis();
            if (elapsedMs < gap * 1000L) {
                throw new PostTooFastException(gap, Math.max(1, (gap * 1000L - elapsedMs + 999) / 1000));
            }
        }
        if (rate.countInWindow() >= daily) {
            throw new PostDailyLimitException(daily);
        }
        int window = s.security().duplicatePostWindowMinutes();
        if (window > 0 && postQueries.duplicate(authorId, ip, now.minus(Duration.ofMinutes(window)), title, content)) {
            throw new DuplicatePostException(window);
        }
    }

    /**
     * 글 칸 검사 — 제목·본문 길이, 말머리(이 게시판의 켜진 말머리, 운영진 전용은 매니저·스텝만 — 이미 그 글에 붙은 말머리는 그대로 둘 수 있다), 19금, 링크·이미지.
     *
     * @param currentPrefixId 고치기면 지금 글의 말머리, 쓰기면 null
     */
    private static Cleaned checkFields(EffectiveBoard b, boolean staff, ContentRules.Writer writer, Long currentPrefixId, String prefixText, String titleText,
                                       String contentText, Boolean adultFlag, BoardSecurityValues sec, Map<String, MessageRef> fields) {
        String title = ContentRules.normalize(titleText).replace('\n', ' ');
        int titleLength = ContentRules.length(title);
        if (titleLength < ContentRules.TITLE_MIN || titleLength > ContentRules.TITLE_MAX) {
            fields.put("title", new MessageRef("board.content.title.length", ContentRules.TITLE_MIN, ContentRules.TITLE_MAX));
        }
        String content = ContentRules.normalize(contentText);
        int contentLength = ContentRules.length(content);
        if (contentLength < ContentRules.CONTENT_MIN || contentLength > ContentRules.CONTENT_MAX) {
            fields.put("content", new MessageRef("board.content.body.length", ContentRules.CONTENT_MIN, String.format("%,d", ContentRules.CONTENT_MAX)));
        }
        Long prefixId = null;
        if (prefixText != null && !prefixText.isBlank()) {
            prefixId = PostQueryService.parseId(prefixText);
            Optional<BoardPrefixInfo> prefix = b.prefix(prefixId);
            boolean keep = prefixId != null && prefixId.equals(currentPrefixId);
            if (prefix.isEmpty() || (!keep && !prefix.get().active())) {
                fields.put("prefixId", new MessageRef("board.content.prefix.unknown"));
            } else if (!keep && prefix.get().staffOnly() && !staff) {
                fields.put("prefixId", new MessageRef("board.content.prefix.staffOnly"));
            }
        }
        boolean adult = Boolean.TRUE.equals(adultFlag);
        if (adult && !b.allowAdult()) {
            fields.put("adult", new MessageRef("board.content.adult"));
        }
        if (!fields.containsKey("content")) {
            ContentRules.checkLinksImages(content, writer, b.imageUploadAllowed(), sec, "content", fields);
        }
        return new Cleaned(prefixId, title, content, adult, ContentRules.imageCount(content) > 0);
    }

    public PostDetailView update(String idText, PostUpdateRequest body, Long userId, String ip) {
        Instant start = Instant.now();
        PostEntity post = queries.requireVisible(idText, start);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
        if (post.isHiddenAt(start)) {
            throw new PostHiddenException();
        }
        requireOwner(post, body.password(), userId, ip);
        PostProtectionView protection = PostQueryService.protection(b, post.getCommentCount());
        if (protection.editBlocked()) {
            throw new PostProtectedException(false, protection.threshold());
        }
        BoardActor actor = actors.resolve(userId, s);
        ContentRules.Writer writer = post.isGuest() ? ContentRules.Writer.GUEST
                : BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        BoardSecurityValues sec = s.security();
        Cleaned c = checkFields(b, actor.staffOf(b.id()), writer, post.getPrefixId(), body.prefixId(), body.title(), body.content(), body.adult(), sec, fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Optional<String> word = ContentRules.blockedWord(sec, c.title(), c.content());
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        String title = ContentRules.mask(sec, c.title());
        String content = ContentRules.mask(sec, c.content());
        return tx.execute(status -> {
            Instant now = Instant.now();
            PostEntity locked = posts.findWithLockById(post.getId()).filter(p -> !p.isGoneAt(now)).orElseThrow(PostNotFoundException::new);
            if (locked.isHiddenAt(now)) {
                throw new PostHiddenException();
            }
            if (PostQueryService.protection(b, locked.getCommentCount()).editBlocked()) {
                throw new PostProtectedException(false, b.commentProtectMin());
            }
            locked.edit(c.prefixId(), title, content, c.adult(), c.hasImage(), now);
            posts.flush();
            return queries.viewAfterEdit(locked, b, s, userId, now);
        });
    }

    /**
     * 지우기 — 보호 중이면 게시판의 방식대로: BLOCK {@code 409 POST_PROTECTED} · DELAY 예약 삭제(이미 예약돼 있으면 같은 시각) · ALLOW 바로. 숨긴 글도 작성자는 지울 수 있다.
     */
    public PostDeleteResult delete(String idText, String password, Long userId, String ip) {
        Instant start = Instant.now();
        PostEntity post = queries.requireVisible(idText, start);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, post.getBoardId());
        requireOwner(post, password, userId, ip);
        return tx.execute(status -> {
            Instant now = Instant.now();
            PostEntity locked = posts.findWithLockById(post.getId()).filter(p -> !p.isGoneAt(now)).orElseThrow(PostNotFoundException::new);
            PostProtectionView protection = PostQueryService.protection(b, locked.getCommentCount());
            if (protection.active() && "BLOCK".equals(protection.deleteMode())) {
                throw new PostProtectedException(true, protection.threshold());
            }
            PostDeleteResult result;
            if (protection.active() && "DELAY".equals(protection.deleteMode())) {
                result = new PostDeleteResult(locked.scheduleDelete(now.plus(Duration.ofMinutes(protection.delayMinutes())), now));
            } else {
                locked.delete(now);
                result = new PostDeleteResult(null);
            }
            if (locked.getAuthorId() != null) {
                // 본인 삭제(예약 삭제도 요청 시점에) — 받은 글 포인트·추천 보너스를 0P 까지 회수한다. 본인이 지운 것이라 알림은 없다
                points.revokeContent(locked.getAuthorId(), PointService.REF_POST, locked.getId());
            }
            return result;
        });
    }

    /** 비회원 글 비밀번호 확인 — 회원 글이면 늘 {@code 403 PASSWORD_MISMATCH} */
    public void verifyPassword(String idText, String password, String ip) {
        PostEntity post = queries.requireVisible(idText, Instant.now());
        if (!post.isGuest()) {
            throw new PasswordMismatchException();
        }
        passwords.require(post.getGuestPasswordHash(), password, ip, "post:" + post.getId());
    }

    /** 추천 — 회원만(비회원 401), 정지 중 403, 내 글 400, 이미 추천 409. 취소 없음 */
    public PostLikeResult like(String idText, Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        BoardCatalogSnapshot s = catalog.snapshot();
        BoardActor actor = actors.resolve(userId, s);
        requireReactable(actor);
        return tx.execute(status -> {
            Instant now = Instant.now();
            PostEntity post = queries.requireVisible(idText, now);
            PublicBoardService.requireActive(s, post.getBoardId());
            if (post.isHiddenAt(now)) {
                throw new PostHiddenException();
            }
            if (userId.equals(post.getAuthorId())) {
                throw new CannotLikeOwnException(false);
            }
            if (!counters.insertPostLike(post.getId(), userId)) {
                throw new AlreadyLikedException(false);
            }
            int likes = counters.incrementPostLikes(post.getId()); // 글 행 갱신 — 같은 글의 추천이 여기서 줄을 선다
            points.likeBonus(post.getAuthorId(), PointService.REF_POST, post.getId(), userId);
            return new PostLikeResult(likes, true);
        });
    }

    /** 추천·신고를 할 수 있는 회원인가 — 비회원 401, 정지 403 ACCOUNT_RESTRICTED, 역할 권한 없음 403 BOARD_RESTRICTED */
    public static void requireReactable(BoardActor actor) {
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.restricted()) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.ACCOUNT_RESTRICTED);
        }
        if (!actor.can(MemberPermission.POST_REACT.code())) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.BOARD_RESTRICTED);
        }
    }

    /** 쓰기 판정 결과를 오류로 — 로그인 필요는 401, 나머지는 403 까닭 코드 */
    public static void requireWritable(String reason) {
        if (reason == null) {
            return;
        }
        if (BoardAccessPolicy.LOGIN_REQUIRED.equals(reason)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        throw new BoardWriteBlockedException(reason);
    }

    /** 고치거나 지울 수 있는가 — 비회원 글은 비밀번호(시도 제한), 회원 글은 쓴 회원만(아니면 403 FORBIDDEN) */
    private void requireOwner(PostEntity post, String password, Long userId, String ip) {
        if (post.isGuest()) {
            passwords.require(post.getGuestPasswordHash(), password, ip, "post:" + post.getId());
            return;
        }
        if (userId == null || !userId.equals(post.getAuthorId())) {
            throw new NotAuthorException();
        }
    }
}
