package com.community.dm.service;

import com.community.block.repository.master.BlockRepositoryQDSL;
import com.community.block.service.BlockService;
import com.community.block.service.SocialMembers;
import com.community.block.service.SocialPaging;
import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.dm.dto.response.DmCanSendView;
import com.community.dm.dto.response.DmLastMessageView;
import com.community.dm.dto.response.DmMessageView;
import com.community.dm.dto.response.DmPartnerView;
import com.community.dm.dto.response.DmQuotaView;
import com.community.dm.dto.response.DmRecipientListView;
import com.community.dm.dto.response.DmRecipientView;
import com.community.dm.dto.response.DmRoomView;
import com.community.dm.dto.response.DmRulesView;
import com.community.dm.dto.response.DmSendResultView;
import com.community.dm.dto.response.DmSenderStateView;
import com.community.dm.dto.response.DmSettingsView;
import com.community.dm.dto.response.DmThreadHeaderView;
import com.community.dm.dto.response.DmThreadItemView;
import com.community.dm.dto.response.DmThreadListView;
import com.community.dm.dto.response.MyDmView;
import com.community.dm.exception.DmQuotaExceededException;
import com.community.dm.exception.DmRateLimitedException;
import com.community.dm.exception.DmSendBlockedException;
import com.community.dm.exception.DmThreadNotFoundException;
import com.community.dm.repository.master.DmQueries;
import com.community.grade.entity.GradeEntity;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.permission.entity.MemberPermission;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쪽지 — 회원 1:1(로그인한 본인 것만). docs/features/member-social-contract.md 「쪽지」·user-web {@code useDmApi.ts}. 읽기·삭제·수신 설정은 이메일 인증 전·정지 중에도 되고
 * <b>보내기만</b> 막힌다. 규칙 값은 {@link DmRules}(서버 상수).
 *
 * <ul>
 *   <li><b>보낼 자격(나 때문)</b> — 이 차례로 처음 걸린 하나: 이메일 인증 전 {@code EMAIL_NOT_VERIFIED} → 정지·영구 정지({@code users.status}) {@code ACCOUNT_RESTRICTED} →
 *       사업자 회원 역할 {@code BUSINESS_RESTRICTED} → 회원 역할 권한 {@code message.send} 없음 {@code NOT_PERMITTED}(로보얌 — 게시판 글쓰기의 {@code post.write} 판정과 같은
 *       {@link BoardActorResolver} 를 쓴다) → 가입 7일 전 {@code TOO_NEW}(+ {@code availableFrom}) → 오늘 20통 {@code DAILY_LIMIT}. 제재·본인인증·기능 제한 모델은 따로 두지 않는다
 *       (커머스 0-4 몫 — 지금 있는 값만 본다).</li>
 *   <li><b>받는 사람 때문</b>: 탈퇴 {@code RECIPIENT_WITHDRAWN}(대화방만) → 차단 관계(어느 쪽이든) {@code UNAVAILABLE} → 수신 거부 {@code RECIPIENT_REFUSES} → 하루 한도 →
 *       새 상대 5명 {@code NEW_PARTNER_LIMIT}(대화가 한 번도 없는 상대일 때만).</li>
 *   <li><b>보내기</b>({@code POST /api/users/{id}/dm}) 오류 차례는 {@code useDmApi.ts} 머리말 그대로(+ {@code 403 DM_NOT_PERMITTED} 를 사업자 다음에). 내 한도 줄을 잠근 뒤
 *       간격(3초)·하루·새 상대를 보고 저장·한도 차감·대화 갱신·알림을 <b>한 트랜잭션</b>으로 한다(실패하면 한도 그대로).</li>
 *   <li><b>대화 삭제</b>는 내 쪽지함에서만, 양쪽이 모두 지우면 원문을 지운다. 대화를 읽음·지움·답장하면 이 대화의 내 쪽지 알림({@code DM})도 읽음이다.</li>
 *   <li>상대 표시는 읽을 때의 현재 값(탈퇴면 「탈퇴한 회원」·{@code id: null}), {@code memo} 는 내가 남긴 메모.</li>
 * </ul>
 */
@Service
public class DmService {

    private static final Logger log = LoggerFactory.getLogger(DmService.class);
    private static final String ROLE_USER = "USER";

    private final DmQueries queries;
    private final BlockRepositoryQDSL blocks;
    private final SocialMembers members;
    private final UserRepository users;
    private final BoardActorResolver actors;
    private final BoardCatalog catalog;
    private final MemberNotificationPublisher notices;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;

    public DmService(DmQueries queries, BlockRepositoryQDSL blocks, SocialMembers members, UserRepository users, BoardActorResolver actors, BoardCatalog catalog,
                     MemberNotificationPublisher notices, Messages messages, SnowflakeIdGenerator ids) {
        this.queries = queries;
        this.blocks = blocks;
        this.members = members;
        this.users = users;
        this.actors = actors;
        this.catalog = catalog;
        this.notices = notices;
        this.messages = messages;
        this.ids = ids;
    }

    /** 나 때문에 못 보내는 이유(하루 한도 제외) — 보낼 수 있으면 {@code reason} null */
    private record SenderCheck(String reason, Instant availableFrom) {
    }

    // ── 내 상태·배지·수신 설정 ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MyDmView me(long userId) {
        Instant now = Instant.now();
        DmQueries.State state = queries.state(userId);
        SenderCheck check = senderCheck(userId, now);
        LocalDate day = DmRules.today(now);
        String reason = check.reason();
        if (reason == null && state.sentOn(day) >= DmRules.DAILY_LIMIT) {
            reason = "DAILY_LIMIT";
        }
        DmSenderStateView sender = new DmSenderStateView(reason == null, reason, check.availableFrom());
        return new MyDmView(state.acceptDm(), sender, quota(state, now), queries.unreadThreads(userId, DmRules.UNREAD_CAP),
                new DmRulesView(DmRules.MIN_ACCOUNT_AGE_DAYS, DmRules.BODY_MAX, DmRules.RETENTION_DAYS));
    }

    @Transactional(readOnly = true)
    public int unreadCount(long userId) {
        return queries.unreadThreads(userId, DmRules.UNREAD_CAP);
    }

    @Transactional(readOnly = true)
    public DmSettingsView settings(long userId) {
        return new DmSettingsView(queries.state(userId).acceptDm());
    }

    /** 수신 설정(멱등) — 불리언이 아니면(빠짐 포함) 400 {@code fields.acceptDm} */
    @Transactional
    public DmSettingsView updateSettings(long userId, Object acceptDm) {
        if (!(acceptDm instanceof Boolean accept)) {
            throw ValidationException.of("acceptDm", new MessageRef("dm.acceptDm.invalid"));
        }
        queries.setAccept(userId, accept, Instant.now());
        return new DmSettingsView(accept);
    }

    // ── 쪽지함·대화방 ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DmThreadListView threads(long userId, Integer size, String cursor) {
        int limit = SocialPaging.size(size, DmRules.INBOX_SIZE);
        Long before = SocialPaging.cursor(cursor);
        List<DmQueries.InboxRow> rows = queries.inbox(userId, before, limit + 1);
        boolean more = rows.size() > limit;
        List<DmQueries.InboxRow> page = more ? rows.subList(0, limit) : rows;
        List<Long> partnerIds = page.stream().map(r -> r.box().partnerId()).distinct().toList();
        Map<Long, DmQueries.Person> people = queries.people(partnerIds);
        Map<Long, String> memos = blocks.memosAmong(userId, partnerIds);
        Map<Long, Integer> unread = queries.unreadCounts(userId, page.stream().map(r -> r.box().threadId()).toList());
        BoardCatalogSnapshot s = catalog.snapshot();
        Locale locale = messages.locale();
        List<DmThreadItemView> items = new ArrayList<>();
        for (DmQueries.InboxRow row : page) {
            DmQueries.Box box = row.box();
            DmLastMessageView last = new DmLastMessageView(Long.toString(box.lastMessageId()), row.lastSenderId() == userId, DmRules.preview(row.lastBody()),
                    row.lastAt());
            items.add(new DmThreadItemView(Long.toString(box.threadId()), partner(people.get(box.partnerId()), memos, s, locale), last,
                    unread.getOrDefault(box.threadId(), 0)));
        }
        String next = more ? Long.toString(page.get(page.size() - 1).box().lastMessageId()) : null;
        return new DmThreadListView(items, more, next);
    }

    /** 대화방 — 읽음 처리는 하지 않는다(화면이 보여 준 뒤 {@link #markRead}) */
    @Transactional(readOnly = true)
    public DmRoomView room(long userId, String threadText, Integer size, String cursor) {
        DmQueries.Box box = requireBox(userId, threadText);
        int limit = SocialPaging.size(size, DmRules.ROOM_SIZE);
        Long before = SocialPaging.cursor(cursor);
        Instant now = Instant.now();
        List<DmQueries.MessageRow> rows = queries.messages(box.threadId(), box.visibleAfter(), before, limit + 1);
        boolean more = rows.size() > limit;
        List<DmQueries.MessageRow> page = more ? rows.subList(0, limit) : rows;
        DmQueries.Person person = queries.people(List.of(box.partnerId())).get(box.partnerId());
        DmQueries.State state = queries.state(userId);
        SenderCheck check = senderCheck(userId, now);
        String reason = reasonFor(userId, box.partnerId(), person == null || person.gone(), Optional.of(box), state, check, now);
        Map<Long, String> memos = blocks.memosAmong(userId, List.of(box.partnerId()));
        DmThreadHeaderView header = new DmThreadHeaderView(Long.toString(box.threadId()), partner(person, memos, catalog.snapshot(), messages.locale()),
                reason == null, reason, "TOO_NEW".equals(reason) ? check.availableFrom() : null, box.partnerSent());
        List<DmMessageView> items = page.stream().map(m -> message(m, userId)).toList();
        String next = more ? Long.toString(page.get(page.size() - 1).id()) : null;
        return new DmRoomView(header, items, more, next, quota(state, now));
    }

    /** 지금까지 온 것을 읽음으로(멱등) — 이 대화의 쪽지 알림도 읽음 */
    @Transactional
    public void markRead(long userId, String threadText) {
        DmQueries.Box box = requireBox(userId, threadText);
        queries.markRead(userId, box.threadId());
        queries.markDmNotificationsRead(userId, box.threadId(), Instant.now());
    }

    /** 내 쪽지함에서 지운다(멱등) — 양쪽이 모두 지웠으면 원문을 지운다 */
    @Transactional
    public void remove(long userId, String threadText) {
        DmQueries.Box box = requireBox(userId, threadText);
        queries.lockThread(box.threadId());
        queries.clear(userId, box.threadId());
        int purged = queries.purgeIfBothCleared(box.threadId());
        queries.markDmNotificationsRead(userId, box.threadId(), Instant.now());
        if (purged > 0) {
            log.info("양쪽 쪽지함에서 지운 대화의 쪽지 원문을 지웠다 threadId={} 쪽지={}", box.threadId(), purged);
        }
    }

    // ── 받는 사람·보내기 ──────────────────────────────────────────────────

    /** 받는 사람 찾기 — 검색어 2~16자(앞뒤 공백 제거), 최대 10명. 나·탈퇴한 회원·차단 관계는 나오지 않는다 */
    @Transactional(readOnly = true)
    public DmRecipientListView recipients(long userId, String raw) {
        String q = raw == null ? "" : raw.strip();
        int length = q.codePointCount(0, q.length());
        if (length < DmRules.SEARCH_MIN || length > DmRules.SEARCH_MAX) {
            throw ValidationException.of("q", new MessageRef("dm.search.length", String.valueOf(DmRules.SEARCH_MIN), String.valueOf(DmRules.SEARCH_MAX)));
        }
        List<DmQueries.RecipientRow> rows = queries.recipients(userId, q.toLowerCase(Locale.ROOT), DmRules.RECIPIENT_LIMIT);
        Map<Long, String> memos = blocks.memosAmong(userId, rows.stream().map(DmQueries.RecipientRow::id).toList());
        BoardCatalogSnapshot s = catalog.snapshot();
        Locale locale = messages.locale();
        List<DmRecipientView> items = rows.stream()
                .map(r -> new DmRecipientView(partner(new DmQueries.Person(r.id(), r.nickname(), r.gradeKey(), false), memos, s, locale),
                        r.threadId() == null ? null : Long.toString(r.threadId())))
                .toList();
        return new DmRecipientListView(items);
    }

    /** 이 회원에게 보낼 수 있나(읽기만 — 한도를 쓰지 않는다). 없는·탈퇴한 회원 404 {@code PROFILE_NOT_FOUND} */
    @Transactional(readOnly = true)
    public DmCanSendView canSend(long userId, String targetText) {
        SocialMembers.Member target = members.require(targetText);
        Instant now = Instant.now();
        DmQueries.State state = queries.state(userId);
        Optional<DmQueries.Box> box = target.id() == userId ? Optional.empty() : queries.boxWith(userId, target.id());
        SenderCheck check = senderCheck(userId, now);
        String reason = reasonFor(userId, target.id(), false, box, state, check, now);
        Instant availableFrom = "TOO_NEW".equals(reason) ? check.availableFrom() : null;
        return new DmCanSendView(reason == null, reason, availableFrom, box.map(b -> Long.toString(b.threadId())).orElse(null), box.isEmpty(),
                box.map(DmQueries.Box::partnerSent).orElse(false), quota(state, now));
    }

    /** 쪽지 보내기 — 오류 차례는 클래스 머리말. 저장·한도 차감·대화 갱신·알림이 한 트랜잭션 */
    @Transactional
    public DmSendResultView send(long userId, String targetText, String raw) {
        String body = DmRules.normalize(raw);
        String problem = DmRules.bodyProblem(body);
        if (problem != null) {
            throw ValidationException.of("body", new MessageRef(problem, String.valueOf(DmRules.BODY_MAX)));
        }
        SocialMembers.Member target = members.require(targetText);
        if (target.id() == userId) {
            throw DmSendBlockedException.self();
        }
        Instant start = Instant.now();
        SenderCheck check = senderCheck(userId, start);
        if (check.reason() != null) {
            throw senderError(check.reason());
        }
        if (blocks.blockedEitherWay(userId, target.id())) {
            throw DmSendBlockedException.forbidden("DM_UNAVAILABLE");
        }
        if (!queries.acceptsDm(target.id())) {
            throw DmSendBlockedException.forbidden("DM_RECIPIENT_REFUSES");
        }
        Optional<DmQueries.Box> box = queries.boxWith(userId, target.id());
        if (!box.map(DmQueries.Box::partnerSent).orElse(false) && DmContactDetector.has(body)) {
            throw DmSendBlockedException.contactNotAllowed();
        }

        Instant now = Instant.now();
        DmQueries.State state = queries.lockState(userId, now);
        if (state.lastSentAt() != null) {
            Duration since = Duration.between(state.lastSentAt(), now);
            if (since.compareTo(DmRules.SEND_INTERVAL) < 0) {
                long wait = DmRules.SEND_INTERVAL.minus(since).toMillis();
                throw new DmRateLimitedException(Math.max(1, (wait + 999) / 1000));
            }
        }
        LocalDate day = DmRules.today(now);
        int sent = state.sentOn(day);
        int partners = state.newPartnersOn(day);
        if (sent >= DmRules.DAILY_LIMIT) {
            throw DmQuotaExceededException.daily(DmRules.DAILY_LIMIT, DmRules.secondsToReset(now));
        }
        boolean newPartner = box.isEmpty();
        if (newPartner && partners >= DmRules.NEW_PARTNER_LIMIT) {
            throw DmQuotaExceededException.newPartners(DmRules.NEW_PARTNER_LIMIT, DmRules.secondsToReset(now));
        }

        long threadId;
        boolean created = false;
        if (box.isPresent()) {
            threadId = box.get().threadId();
        } else {
            long fresh = ids.nextId();
            created = queries.createThread(fresh, userId, target.id(), now);
            if (created) {
                threadId = fresh;
            } else {
                // 상대가 같은 순간 먼저 대화를 만들었다 — 새 상대가 아니다
                threadId = queries.boxWith(userId, target.id()).orElseThrow(() -> new IllegalStateException("만들어진 대화를 찾지 못했다")).threadId();
                newPartner = false;
            }
        }
        queries.lockThread(threadId);
        long messageId = ids.nextId();
        queries.insertMessage(messageId, threadId, userId, body, now);
        queries.afterSend(threadId, userId, messageId, now);
        int sentAfter = sent + 1;
        int partnersAfter = partners + (newPartner ? 1 : 0);
        queries.recordSend(userId, day, sentAfter, partnersAfter, now);
        queries.markDmNotificationsRead(userId, threadId, now); // 답장했다는 것은 읽었다는 뜻이다
        notices.dmReceived(target.id(), userId, threadId);
        log.info("쪽지를 보냈다 threadId={} messageId={} from={} to={} 새대화={}", threadId, messageId, userId, target.id(), created);

        DmQuotaView quota = quota(new DmQueries.State(state.acceptDm(), day, sentAfter, partnersAfter, now), now);
        return new DmSendResultView(Long.toString(threadId), created, new DmMessageView(Long.toString(messageId), true, body, now), quota);
    }

    // ── 판정 ───────────────────────────────────────────────────────────────

    /** 나 때문에 못 보내는 이유(하루 한도 제외) — 지금 있는 값(users 표·회원 역할)만 본다 */
    private SenderCheck senderCheck(long userId, Instant now) {
        UserEntity user = users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        BoardActor actor = actors.resolve(userId, catalog.snapshot());
        if (!user.isEmailVerified()) {
            return new SenderCheck("EMAIL_NOT_VERIFIED", null);
        }
        if (actor.restricted()) {
            return new SenderCheck("ACCOUNT_RESTRICTED", null);
        }
        if (actor.business()) {
            return new SenderCheck("BUSINESS_RESTRICTED", null);
        }
        if (!actor.can(MemberPermission.MESSAGE_SEND.code())) {
            return new SenderCheck("NOT_PERMITTED", null);
        }
        Instant availableFrom = user.getCreatedAt() == null ? null : user.getCreatedAt().plus(Duration.ofDays(DmRules.MIN_ACCOUNT_AGE_DAYS));
        if (availableFrom != null && now.isBefore(availableFrom)) {
            return new SenderCheck("TOO_NEW", availableFrom);
        }
        return new SenderCheck(null, null);
    }

    /**
     * 이 상대에게 지금 보낼 수 있는가 — 이유 하나(보낼 수 있으면 null). 차례: SELF → 나 때문 → RECIPIENT_WITHDRAWN → UNAVAILABLE → RECIPIENT_REFUSES → DAILY_LIMIT → NEW_PARTNER_LIMIT.
     *
     * @param box 두 사람 사이의 대화(없으면 새 상대)
     */
    private String reasonFor(long userId, long partnerId, boolean partnerGone, Optional<DmQueries.Box> box, DmQueries.State state, SenderCheck check,
                             Instant now) {
        if (partnerId == userId) {
            return "SELF";
        }
        if (check.reason() != null) {
            return check.reason();
        }
        if (partnerGone) {
            return "RECIPIENT_WITHDRAWN";
        }
        if (blocks.blockedEitherWay(userId, partnerId)) {
            return "UNAVAILABLE";
        }
        if (!queries.acceptsDm(partnerId)) {
            return "RECIPIENT_REFUSES";
        }
        LocalDate day = DmRules.today(now);
        if (state.sentOn(day) >= DmRules.DAILY_LIMIT) {
            return "DAILY_LIMIT";
        }
        if (box.isEmpty() && state.newPartnersOn(day) >= DmRules.NEW_PARTNER_LIMIT) {
            return "NEW_PARTNER_LIMIT";
        }
        return null;
    }

    private static CommonException senderError(String reason) {
        return switch (reason) {
            case "EMAIL_NOT_VERIFIED" -> DmSendBlockedException.forbidden("EMAIL_NOT_VERIFIED");
            case "ACCOUNT_RESTRICTED" -> DmSendBlockedException.forbidden("ACCOUNT_RESTRICTED");
            case "BUSINESS_RESTRICTED" -> DmSendBlockedException.forbidden("BUSINESS_ACCOUNT_RESTRICTED");
            case "NOT_PERMITTED" -> DmSendBlockedException.forbidden("DM_NOT_PERMITTED");
            case "TOO_NEW" -> DmSendBlockedException.forbidden("DM_SENDER_TOO_NEW", String.valueOf(DmRules.MIN_ACCOUNT_AGE_DAYS));
            default -> throw new IllegalStateException("모르는 쪽지 이유: " + reason);
        };
    }

    // ── 보기 ───────────────────────────────────────────────────────────────

    private DmQueries.Box requireBox(long userId, String threadText) {
        Long threadId = SocialPaging.id(threadText);
        if (threadId == null) {
            throw new DmThreadNotFoundException();
        }
        return queries.box(userId, threadId).orElseThrow(DmThreadNotFoundException::new);
    }

    private static DmQuotaView quota(DmQueries.State state, Instant now) {
        LocalDate day = DmRules.today(now);
        int sent = state.sentOn(day);
        int partners = state.newPartnersOn(day);
        return new DmQuotaView(DmRules.DAILY_LIMIT, sent, Math.max(0, DmRules.DAILY_LIMIT - sent), DmRules.NEW_PARTNER_LIMIT, partners,
                Math.max(0, DmRules.NEW_PARTNER_LIMIT - partners), DmRules.secondsToReset(now));
    }

    private static DmMessageView message(DmQueries.MessageRow m, long userId) {
        return new DmMessageView(Long.toString(m.id()), m.senderId() == userId, m.body(), m.createdAt());
    }

    /** 상대 표시 — 탈퇴·없음은 「탈퇴한 회원」(id 없음, 첫 등급), 메모는 내가 남긴 것 */
    private DmPartnerView partner(DmQueries.Person person, Map<Long, String> memos, BoardCatalogSnapshot s, Locale locale) {
        if (person == null || person.gone()) {
            String base = GradeEntity.BASE_KEY;
            return new DmPartnerView(null, messages.get(new MessageRef("member.notification.actor.withdrawn"), locale), base, BlockService.label(s, base), ROLE_USER,
                    true, null);
        }
        return new DmPartnerView(Long.toString(person.id()), person.nickname(), person.gradeKey(), BlockService.label(s, person.gradeKey()), ROLE_USER, false,
                memos.get(person.id()));
    }
}
