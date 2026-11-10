package com.community.post.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.ContentBody;
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
import com.community.debate.entity.PostDebateEntity;
import com.community.debate.service.PostDebateWriter;
import com.community.group.service.GroupPostGate;
import com.community.hotdeal.dto.response.DealWarningView;
import com.community.hotdeal.service.DealWriter;
import com.community.permission.entity.MemberPermission;
import com.community.poll.service.PollRules;
import com.community.poll.service.PollWriter;
import com.community.emoticon.service.EmoticonGuard;
import com.community.point.service.PointService;
import com.community.post.dto.request.PostUpdateRequest;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostDeleteResult;
import com.community.post.dto.response.PostDetailView;
import com.community.post.dto.response.PostLikeResult;
import com.community.post.dto.response.PostProtectionView;
import com.community.post.dto.response.PostWriteResult;
import com.community.post.entity.PostEntity;
import com.community.post.exception.ImageUploadNotAllowedException;
import com.community.post.exception.PostHiddenException;
import com.community.post.exception.PostNotFoundException;
import com.community.post.exception.PostProtectedException;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostContentFields.Cleaned;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글 쓰기·고치기·지우기·비밀번호 확인·추천.
 *
 * <p><b>쓰기 검사 순서</b>(user-web 목업 {@code mockPostCreate} 와 같다): 게시판(404·403 꺼짐) → 쓰기 판정({@link BoardAccessPolicy} — 로그인 필요는 401, 나머지 403 까닭 코드)
 * → 칸(제목, 본문 형식 {@code contentFormat}, 본문 길이[HTML 은 보이는 글자], 말머리, 19금, 링크·이미지 수, 비회원 이름·비밀번호·자동입력 방지 토큰 — {@code 400 VALIDATION})
 * → (HTML) 본문 이미지가 쓸 수 있는 것인지({@code 400 fields.content}) → 이미지 금지 게시판의 새 이미지({@code 422 IMAGE_UPLOAD_NOT_ALLOWED})
 * → 로보티콘 자격({@code 409 EMOTICON_NOT_OWNED} — 토큰 개수 21개부터는 칸 검사의 {@code fields.content}, {@link EmoticonGuard}) → 금지어({@code 400 BLOCKED_WORD}, 가림 방식이면 가려 저장)
 * → 자동입력 방지 확인(비회원, 트랜잭션 밖 외부 요청 — 실패는 {@code fields.guest.captchaToken}) → 작성 간격({@code 429 POST_TOO_FAST}) → 하루 한도({@code 429 POST_DAILY_LIMIT})
 * → 같은 글 반복({@code 409 DUPLICATE_POST}). 본문 {@code debate} 가 있으면 칸 검사에서 회원 + 게시판 {@code debateEnabled} 를 보고({@code fields.debate}) 저장과 같은 트랜잭션에서
 * 글의 찬반 토론을 연다(글쓴이가 연 토론 — docs/features/post-debate-contract.md). 본문 {@code poll} 도 같은 자리에서 보고({@link PollWriter} — 회원 + 게시판
 * {@code pollEnabled}, {@code fields.poll}·{@code fields."poll.*"}) 같은 트랜잭션에서 붙인다. 고치기의 {@code poll} 은 빠지면 그대로 · null 이면 빼기 · 값이면 만들기·고치기이고
 * 첫 표가 든 투표를 바꾸면 409 POLL_LOCKED 로 글도 고치지 않는다(docs/features/post-poll-contract.md). 속도 판정과 저장은 작성자 단위 어드바이저리 잠금 아래 한 트랜잭션이다(같은 사람의 동시 요청이 한도를 넘지 못하게).
 * 작성자 열쇠는 회원 ID, 비회원은 작성 IP 다. 하루 = 최근 24시간(목업과 같음).
 *
 * <p><b>핫딜</b>({@link DealWriter}): 핫딜 게시판({@code kind DEAL})이면 본문 {@code deal} 필수(아니면 거절) — 쓰기 자격(일반 등급 이상 + 이메일 인증)은 게시판 판정 다음에, 칸
 * 검사({@code fields."deal.*"})는 다른 칸과 함께, 하루 글 수·같은 판매처 간격(429)은 저장 트랜잭션의 작성자 잠금 아래에서 본다. 고치기의 {@code deal} 은 빠지면 그대로(링크만 지금
 * 규칙으로 다시 판정해 {@code warnings}), 값이면 고치기(이력·가격 수정 셈·판매처 바뀜 알림). docs/features/hotdeal-contract.md.
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
    private final PostCounterQueries counters;
    private final PostQueryService queries;
    private final GuestPasswords passwords;
    private final CaptchaVerifier captcha;
    private final SnowflakeIdGenerator ids;
    private final WriterPromotion promotion;
    private final PointService points;
    private final PostImages postImages;
    private final PostDebateWriter debates;
    private final PollWriter polls;
    private final GroupPostGate groupGate;
    private final DealWriter deals;
    private final EmoticonGuard emoticons;
    private final AdultContentGate adultGate;
    private final SecretPostUnlocks unlocks;
    /** 쓰기 막힘·게시판 제재·비밀글·작성 간격·하루 한도·같은 글 반복 — shared 한 곳(관리자 서버의 AI 회원 쓰기와 같은 코드) */
    private final PostWriteRules rules;
    private final TransactionTemplate tx;

    public PostWriteService(BoardCatalog catalog, BoardActorResolver actors, PostRepository posts, PostCounterQueries counters,
                            PostQueryService queries, GuestPasswords passwords, CaptchaVerifier captcha, SnowflakeIdGenerator ids, WriterPromotion promotion,
                            PointService points, PostImages postImages, PostDebateWriter debates, PollWriter polls, GroupPostGate groupGate,
                            DealWriter deals, EmoticonGuard emoticons, AdultContentGate adultGate, SecretPostUnlocks unlocks, PostWriteRules rules,
                            PlatformTransactionManager txManager) {
        this.rules = rules;
        this.unlocks = unlocks;
        this.adultGate = adultGate;
        this.emoticons = emoticons;
        this.groupGate = groupGate;
        this.deals = deals;
        this.postImages = postImages;
        this.debates = debates;
        this.polls = polls;
        this.promotion = promotion;
        this.points = points;
        this.catalog = catalog;
        this.actors = actors;
        this.posts = posts;
        this.counters = counters;
        this.queries = queries;
        this.passwords = passwords;
        this.captcha = captcha;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }


    public PostWriteResult create(PostWriteRequest body, Long userId, String ip) {
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = PublicBoardService.requireActive(s, body.boardSlug());
        BoardActor actor = actors.resolve(userId, s);
        // 핫딜 게시판은 쓰기 자격(일반 등급 이상 + 이메일 인증 + 정지 아님)까지 같은 판정이 본다 — 새싹이면 필요한 등급을 오류 본문에
        requireWritable(BoardAccessPolicy.writeBlocked(b, actor, s), b, s);
        requireNotRestricted(actor, b.id()); // 게시판 제재의 쓰기 제한(board-sanction-contract 결정 5)
        DealWriter.requireWriter(b, actor, s);
        BoardSecurityValues sec = s.security();

        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ContentRules.Writer writer = actor.guest() ? ContentRules.Writer.GUEST
                : BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
        Cleaned c = checkFields(b, actor.staffOf(b.id()), writer, null, body.prefixId(), body.title(), body.contentFormat(), body.content(), body.adult(), sec,
                fields);
        requireAdultWriter(c, userId, fields);
        String guestName = null;
        if (actor.guest()) {
            ContentRules.checkGuest(body.guest(), sec, fields);
            guestName = body.guest() == null ? null : ContentRules.normalize(body.guest().name());
        }
        if (body.debate() != null && (actor.guest() || !b.debateEnabled())) {
            fields.put("debate", new MessageRef("debate.open.notAllowed")); // 글과 함께 토론 열기 — 회원 + 게시판 debateEnabled 일 때만
        }
        PollRules.Valid poll = polls.checkForCreate(body.poll(), actor.guest(), b, Instant.now(), fields); // 회원 + 게시판 pollEnabled, 칸 오류는 poll.*
        // 핫딜 게시판이면 딜 필수(칸 오류는 deal.*), 아니면 거절 — 새 링크의 리디렉션 체인은 여기(트랜잭션 밖)서 가져온다
        DealWriter.Checked deal = deals.checkForCreate(body.deal(), b, actor.guest() ? null : actor.userId(), fields);
        EmoticonGuard.countInto(c.body().content(), "content", fields); // 로보티콘 토큰 20개까지(입력 오류와 함께 — emoticon-contract 목업 5차)
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Long authorId = actor.guest() ? null : actor.userId();
        if (!c.body().imageIds().isEmpty()) {
            postImages.check(c.body().imageIds(), authorId, null); // 없거나 남의 이미지 — 400 fields.content
            if (!b.imageUploadAllowed()) {
                throw new ImageUploadNotAllowedException(); // 새 글의 이미지는 모두 새 이미지다
            }
        }
        emoticons.requireOwned(authorId, c.body().content()); // 가지지 않은 팩의 로보티콘 409 — 입력 오류 다음, 금지어·속도 제한 앞
        Optional<String> word = ContentRules.blockedWord(sec, c.title(), c.body().wordsHaystack(), guestName);
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        String title = ContentRules.mask(sec, c.title());
        ContentBody content = c.body().masked(sec);
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
        boolean secret = secretOnCreate(b, body.secret());
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            rules.checkRate(s, authorId, ip, title, content.plain(), now, false);
            String stored = content.isHtml() ? postImages.bind(content.content(), content.imageIds(), authorId, id, false, now) : content.content();
            PostEntity created = PostEntity.create(id, b.id(), c.prefixId(), authorId, name, hash, ip, title, content.format(), stored, content.plain(), c.adult(),
                    c.hasImage(), now);
            created.initOps(secret, b.statusEnabled()); // 비밀글 · 처음 처리 상태(상태를 쓰는 게시판이면 RECEIVED) — V166
            if (b.anonymous() && authorId != null) {
                created.markAnonymous(); // 익명 게시판 — 쓸 때의 게시판 설정(V184, anonymous-board-contract 정책 2). 비회원은 위 판정이 401 로 막았다
            }
            posts.persist(created);
            emoticons.recordUse(authorId, c.body().content()); // 산 팩의 「처음 쓴 시각」 — 저장과 같은 트랜잭션(emoticon-contract S3, 그사이 환불·빼기면 409 로 되돌린다)
            if (body.debate() != null) {
                debates.apply(id, true, body.debate().neutralEnabled(), body.debate().stanceChangeAllowed(), PostDebateEntity.BY_MEMBER, authorId, now);
            }
            if (poll != null) {
                polls.create(id, poll, now);
            }
            if (deal != null) {
                deals.create(id, b.id(), authorId, deal, deals.now()); // 하루 글 수 · 같은 판매처 간격(429)도 작성자 잠금 아래에서
            }
            if (authorId != null) {
                // 실패·조건 미달·상한이어도 글은 그대로 올라간다(커머스 1-3 — 게시판 pointMode·최소 글자 수 스위치)
                points.earnPost(new PointService.PostEarn(authorId, id, content.plain(), b.pointsEnabled(), b.pointMinLengthApplied()));
            }
        });
        promotion.afterWrite(authorId); // 커밋 뒤 — 회원 글이면 활동 수 조건 즉시 승급(실패해도 글쓰기는 성공)
        return new PostWriteResult(Long.toString(id), deal == null ? List.of() : deal.warnings());
    }

    /**
     * 소모임 글 쓰기의 결과.
     *
     * @param pointsEarned 이 글로 받은 포인트(지급이 없으면 0 — 일반 글과 하루 상한을 함께 쓴다)
     */
    public record GroupWrite(long id, long pointsEarned) {
    }

    /**
     * 소모임 글 — 부르는 쪽({@code GroupPostService})이 트랜잭션을 열고 소모임 행을 잠근 뒤 멤버·쓰기 자격을 보고 부른다(이 메서드는 그 트랜잭션 안에서 돈다).
     * 글 칸·이미지·금지어·작성 간격·하루 한도·같은 글 반복은 일반 글과 같고, 게시판 쓰기 정책(등급·잠금·운영진만)은 받지 않는다. 19금·찬반 토론은 받지 않는다
     * ({@code fields.adult}·{@code fields.debate}), 투표는 게시판 {@code pollEnabled} 일 때. 비공개 소모임이면 비공개 글, 공개 소모임이면 비멤버에게도 보이는 글로 만든다(V94).
     * 커밋 뒤 승급 판정은 부르는 쪽이 {@link #afterGroupWrite} 로.
     *
     * @param b           소모임 게시판(꺼져 있어도 쓴다 — 소모임은 멈추지 않는다)
     * @param publicGroup 공개 소모임인가(잠근 소모임 행의 값)
     */
    public GroupWrite createInGroup(long groupId, boolean publicGroup, EffectiveBoard b, BoardActor actor, PostWriteRequest body, String ip, BoardCatalogSnapshot s) {
        BoardSecurityValues sec = s.security();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        ContentRules.Writer writer = BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
        Cleaned c = checkFields(b, actor.staffOf(b.id()), writer, null, body.prefixId(), body.title(), body.contentFormat(), body.content(), null, sec, fields);
        if (Boolean.TRUE.equals(body.adult())) {
            fields.put("adult", new MessageRef("group.post.adult.notAllowed"));
        }
        if (body.debate() != null) {
            fields.put("debate", new MessageRef("group.post.debate.notAllowed"));
        }
        PollRules.Valid poll = polls.checkForCreate(body.poll(), false, b, Instant.now(), fields);
        deals.checkForCreate(body.deal(), b, actor.userId(), fields); // 소모임 게시판은 핫딜이 아니다 — deal 을 보내면 fields.deal
        EmoticonGuard.countInto(c.body().content(), "content", fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long authorId = actor.userId();
        if (!c.body().imageIds().isEmpty()) {
            postImages.check(c.body().imageIds(), authorId, null);
            if (!b.imageUploadAllowed()) {
                throw new ImageUploadNotAllowedException();
            }
        }
        emoticons.requireOwned(authorId, c.body().content()); // 일반 글과 같은 자리(입력 오류 → 자격 → 금지어·속도 제한)
        Optional<String> word = ContentRules.blockedWord(sec, c.title(), c.body().wordsHaystack());
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        String title = ContentRules.mask(sec, c.title());
        ContentBody content = c.body().masked(sec);
        long id = ids.nextId();
        Instant now = Instant.now();
        rules.checkRate(s, authorId, ip, title, content.plain(), now, false);
        String stored = content.isHtml() ? postImages.bind(content.content(), content.imageIds(), authorId, id, false, now) : content.content();
        PostEntity created = PostEntity.createInGroup(id, groupId, b.id(), c.prefixId(), authorId, ip, title, content.format(), stored, content.plain(), c.hasImage(),
                publicGroup, now);
        created.initOps(secretOnCreate(b, body.secret()), b.statusEnabled()); // 소모임 게시판의 비밀글 · 처음 처리 상태(V168 — 일반 게시판과 같은 규칙)
        posts.persist(created);
        emoticons.recordUse(authorId, c.body().content()); // 일반 글과 같다(부르는 쪽 트랜잭션 — 소모임 행을 잠근 그 트랜잭션)
        if (poll != null) {
            polls.create(id, poll, now);
        }
        // 실패·조건 미달·상한이어도 글은 그대로 올라간다(일반 글과 같은 규칙 — 커머스 1-3 의 게시판 pointMode·최소 글자 수 스위치)
        long earned = points.earnPost(new PointService.PostEarn(authorId, id, content.plain(), b.pointsEnabled(), b.pointMinLengthApplied()));
        return new GroupWrite(id, earned);
    }

    /** 소모임 글 커밋 뒤 — 활동 수 조건 즉시 승급(실패해도 글쓰기는 성공) */
    public void afterGroupWrite(long authorId) {
        promotion.afterWrite(authorId);
    }

    /**
     * 새 글의 비밀글 여부(V166 · V168) — 규칙은 shared {@link PostWriteRules#secretOnCreate}(관리자 서버의 AI 회원 쓰기와 같은 코드).
     */
    static boolean secretOnCreate(EffectiveBoard b, Boolean requested) {
        return PostWriteRules.secretOnCreate(b, requested);
    }

    /**
     * 고친 글의 비밀글 여부(V166 · V168) — 게시판 ALWAYS 면 true, OPTIONAL 이면 요청 값(빠지거나 null 이면 지금 값), OFF 면 지금 값 그대로
     * (게시판 설정을 나중에 꺼도 이미 쓴 비밀글은 비밀로 남는다). 비회원 글도 같다.
     */
    static boolean secretOnUpdate(EffectiveBoard b, PostEntity post, Boolean requested) {
        if (b.secretAlways()) {
            return true;
        }
        if (b.secretOptional() && requested != null) {
            return requested;
        }
        return post.isSecret();
    }

    /** 글 칸 검사 — 규칙은 shared {@link PostContentFields#check}(관리자 공지 쓰기와 같은 규칙) */
    private static Cleaned checkFields(EffectiveBoard b, boolean staff, ContentRules.Writer writer, Long currentPrefixId, String prefixText, String titleText,
                                       String formatText, String contentText, Boolean adultFlag, BoardSecurityValues sec, Map<String, MessageRef> fields) {
        return PostContentFields.check(b, staff, writer, currentPrefixId, prefixText, titleText, formatText, contentText, adultFlag, sec, fields);
    }

    /**
     * 19금 글은 성인(로그인 + 본인인증 + 만 19세 이상)만 쓴다 — 볼 수 없는 글을 쓰지 않게(adult-content-contract). 성인 전용 말머리로 19금이 됐으면 {@code fields.prefixId},
     * 직접 골랐으면 {@code fields.adult}.
     */
    private void requireAdultWriter(Cleaned c, Long userId, Map<String, MessageRef> fields) {
        if (c.adult() && !fields.containsKey("adult") && !fields.containsKey("prefixId") && !adultGate.adult(userId)) {
            fields.put(c.adultByPrefix() ? "prefixId" : "adult", new MessageRef("board.content.adult.identity"));
        }
    }

    public PostDetailView update(String idText, PostUpdateRequest body, Long userId, String ip) {
        Instant start = Instant.now();
        PostEntity post = queries.requireVisible(idText, start);
        BoardCatalogSnapshot s = catalog.snapshot();
        // 소모임 글: 비공개 글은 지금 멤버인 작성자만 열 수 있다. 비회원 비밀글은 비밀번호가 글쓴이임을 보이므로 비밀글 판정 대신 바로 아래 비밀번호 확인(V168)
        GroupPostGate.Resolved gate = groupGate.resolve(s, post, userId, !post.isGuest());
        EffectiveBoard b = gate.board();
        if (post.isHiddenAt(start)) {
            throw new PostHiddenException();
        }
        requireOwner(post, body.password(), userId, ip);
        deals.requireNotBusiness(post.getId()); // 사업자 핫딜 글은 작성자도 고칠 수 없다(403 DEAL_BUSINESS_POST_LOCKED)
        PostProtectionView protection = PostQueryService.protection(b, post);
        if (protection.editBlocked()) {
            throw new PostProtectedException(false, protection.threshold());
        }
        BoardActor actor = actors.resolve(userId, s);
        ContentRules.Writer writer = post.isGuest() ? ContentRules.Writer.GUEST
                : BoardAccessPolicy.newMemberRules(b, actor, s) ? ContentRules.Writer.NEW_MEMBER : ContentRules.Writer.MEMBER;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        BoardSecurityValues sec = s.security();
        Cleaned c = checkFields(b, actor.staffOf(b.id()), writer, post.getPrefixId(), body.prefixId(), body.title(), body.contentFormat(), body.content(),
                body.adult(), sec, fields);
        requireAdultWriter(c, userId, fields);
        PollWriter.Change pollChange = polls.checkForUpdate(body.poll(), post, b, start, fields); // 칸 빠짐 = 그대로 · null = 빼기 · 값 = 만들기·고치기
        DealWriter.Change dealChange = deals.checkForUpdate(body.deal(), post, b, fields); // 핫딜 — 칸 빠짐 = 그대로(링크만 다시 판정) · 값 = 고치기, 종료된 딜은 409 DEAL_ENDED
        if (post.isGroupPost() && c.adult()) {
            fields.put("adult", new MessageRef("group.post.adult.notAllowed")); // 소모임 글은 19금이 될 수 없다
        }
        EmoticonGuard.countInto(c.body().content(), "content", fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        List<Long> imageIds = c.body().imageIds();
        if (!imageIds.isEmpty()) {
            postImages.check(imageIds, post.getAuthorId(), post.getId()); // 글쓴이가 올린 새 이미지 또는 이 글이 쓰던 이미지만
            Set<Long> existing = postImages.attachedTo(post.getId());
            if (!b.imageUploadAllowed() && imageIds.stream().anyMatch(id -> !existing.contains(id))) {
                throw new ImageUploadNotAllowedException(); // 이미지 금지 게시판 — 이미 있던 이미지는 둘 수 있고 새 이미지만 막는다
            }
        }
        emoticons.requireOwned(post.getAuthorId(), c.body().content()); // 고칠 때도 본문 전체를 다시 본다(예전에 쓴 토큰이어도 — 팩을 뺐으면 409)
        Optional<String> word = ContentRules.blockedWord(sec, c.title(), c.body().wordsHaystack());
        if (word.isPresent()) {
            throw new BlockedWordException(word.get());
        }
        String title = ContentRules.mask(sec, c.title());
        ContentBody content = c.body().masked(sec);
        return tx.execute(status -> {
            Instant now = Instant.now();
            PostEntity locked = posts.findWithLockById(post.getId()).filter(p -> !p.isGoneAt(now)).orElseThrow(PostNotFoundException::new);
            if (locked.isHiddenAt(now)) {
                throw new PostHiddenException();
            }
            if (PostQueryService.protection(b, locked).editBlocked()) {
                throw new PostProtectedException(false, b.commentProtectMin());
            }
            polls.apply(locked.getId(), pollChange, now); // 첫 표가 든 투표를 바꾸거나 빼면 409 POLL_LOCKED — 글도 고치지 않는다(같은 트랜잭션)
            String stored;
            if (content.isHtml()) {
                stored = postImages.bind(content.content(), content.imageIds(), locked.getAuthorId(), locked.getId(), true, now);
            } else {
                postImages.releaseAll(locked.getId());
                stored = content.content();
            }
            // 글 이미지 맞추기 다음 — 올린 딜 이미지를 다시 붙이고, 딜 칸이 바뀌면 이력·셈·알림(D3). 차단 링크를 그대로 두면 경고(규칙 7)
            List<DealWarningView> warnings = deals.apply(locked, dealChange, deals.now());
            locked.edit(c.prefixId(), title, content.format(), stored, content.plain(), c.adult(), c.hasImage(), now);
            locked.changeSecret(secretOnUpdate(b, locked, body.secret())); // 비밀글 — OPTIONAL 이면 글쓴이가 고르고, ALWAYS 는 비밀, OFF 는 그대로(V166)
            posts.flush();
            emoticons.recordUse(locked.getAuthorId(), c.body().content()); // 고친 본문의 산 팩도 「사용함」(이미 찍혔으면 그대로)
            return queries.viewAfterEdit(locked, gate, s, userId, now, warnings);
        });
    }

    /**
     * 지우기 — 보호 중이면 게시판의 방식대로: BLOCK {@code 409 POST_PROTECTED} · DELAY 예약 삭제(이미 예약돼 있으면 같은 시각) · ALLOW 바로. 숨긴 글도 작성자는 지울 수 있다.
     */
    public PostDeleteResult delete(String idText, String password, Long userId, String ip) {
        Instant start = Instant.now();
        PostEntity post = queries.requireVisible(idText, start);
        BoardCatalogSnapshot s = catalog.snapshot();
        EffectiveBoard b = groupGate.resolveIgnoringAdult(s, post, userId).board(); // 본인인증 전에 쓴 19금 글도 작성자는 지운다
        requireOwner(post, password, userId, ip);
        deals.requireNotBusiness(post.getId()); // 사업자 핫딜 글은 작성자도 지울 수 없다(403 DEAL_BUSINESS_POST_LOCKED)
        return tx.execute(status -> {
            Instant now = Instant.now();
            PostEntity locked = posts.findWithLockById(post.getId()).filter(p -> !p.isGoneAt(now)).orElseThrow(PostNotFoundException::new);
            PostProtectionView protection = PostQueryService.protection(b, locked);
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

    /**
     * 비회원 비밀글 열람(V168 — {@code POST /api/posts/{id}/secret-unlock}) — 비밀번호가 맞으면 이 방문자에게 그 글을 1시간 열람하게 한다({@link SecretPostUnlocks}, 서버 세션).
     * 없거나 지운 글 404 · 비회원 비밀글이 아니면 400 {@code SECRET_UNLOCK_NOT_GUEST} · 비밀번호 확인은 {@link #verifyPassword} 와 같은 검사·같은 시도 제한
     * (403 {@code PASSWORD_MISMATCH} · 429 {@code PASSWORD_ATTEMPTS_EXCEEDED} — 대상 열쇠 {@code post:<id>} 를 함께 센다). 트랜잭션 밖(bcrypt).
     */
    public void unlockSecret(String idText, String password, String ip) {
        Instant now = Instant.now();
        PostEntity post = queries.requireVisible(idText, now);
        if (!post.isGuest() || !post.isSecret()) {
            throw new com.community.post.exception.SecretUnlockNotGuestException();
        }
        passwords.require(post.getGuestPasswordHash(), password, ip, "post:" + post.getId());
        unlocks.grant(post.getId(), now);
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
            EffectiveBoard board = groupGate.resolve(s, post, userId).board(); // 소모임 비공개 글은 멤버만 추천한다
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
            points.likeBonus(post.getAuthorId(), PointService.REF_POST, post.getId(), userId, board.pointsEnabled());
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

    /** 쓰기 판정 결과를 오류로 — 로그인 필요는 401, 나머지는 403 까닭 코드(규칙은 shared {@link PostWriteRules} — 관리자 서버의 AI 회원 쓰기와 같은 코드) */
    public static void requireWritable(String reason) {
        PostWriteRules.requireWritable(reason);
    }

    /**
     * 게시판 제재로 이 게시판에서 쓰기 제한 중이면 403 {@code BOARD_WRITE_RESTRICTED}(+{@code until}) — 새 글·댓글·답글만 막는다(수정·삭제·추천은 막지 않는다, 결정 5).
     * 규칙은 shared {@link PostWriteRules#requireNotRestricted}.
     */
    public static void requireNotRestricted(BoardActor actor, long boardId) {
        PostWriteRules.requireNotRestricted(actor, boardId);
    }

    /** 새 글 쓰기 판정 — {@code GRADE_TOO_LOW} 면 오류 본문에 필요한 등급({@code minGrade}·{@code minGradeLabel} — 핫딜 게시판은 첫 등급 바로 위)을 싣는다 */
    public static void requireWritable(String reason, EffectiveBoard b, BoardCatalogSnapshot s) {
        PostWriteRules.requireWritable(reason, b, s);
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
