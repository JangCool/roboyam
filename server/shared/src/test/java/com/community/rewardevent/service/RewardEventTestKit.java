package com.community.rewardevent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.repository.master.AuditLogRepository;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.membernotification.repository.master.MemberNotificationWriter;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.operator.repository.master.OperatorMemberAccountStore;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.repository.master.PointPolicyQueries;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.service.AvatarCatalog;
import com.community.pointshop.service.PointShopRefundService;
import com.community.pointshop.service.UserItemService;
import com.community.restriction.repository.master.MemberRestrictionRepository;
import com.community.restriction.repository.master.MemberRestrictionStore;
import com.community.restriction.service.MemberRestrictionService;
import com.community.rewardevent.dto.RewardBulkConfig;
import com.community.rewardevent.dto.RewardCommentDrawConfig;
import com.community.rewardevent.dto.RewardFirstComeConfig;
import com.community.rewardevent.dto.RewardLineDto;
import com.community.rewardevent.dto.RewardOperator;
import com.community.rewardevent.dto.RewardSelectionConfig;
import com.community.rewardevent.dto.RewardTierDto;
import com.community.rewardevent.dto.request.RewardEventSaveRequest;
import com.community.rewardevent.dto.request.RewardRecipientAddRequest;
import com.community.rewardevent.dto.response.RewardEventDetailView;
import com.community.rewardevent.repository.master.RewardEventStore;
import com.community.rewardevent.repository.master.RewardMemberQueries;
import com.community.rewardevent.repository.master.RewardRecipientStore;
import com.community.testsupport.BoardFixture;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MutableTestClock;
import com.community.testsupport.PointInvariants;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import com.community.zconfiguration.message.Messages;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보상 이벤트 시험의 바탕 — 실제 PostgreSQL({@link CommerceTestDatabase}) 위에서 포인트·포인트 상점·로보티콘 저장소와 보상 이벤트 서비스를 손으로 조립한다(스프링 문맥 없음 —
 * {@code PointShopTestKit} 과 같은 방식). 저장소는 JPA·QueryDSL({@link JpaTestContext#create}), 서비스의 {@code @Transactional} 은 {@link JpaTestContext#transactional} 로 입힌다.
 * 세이브포인트({@link IsolatedSideWork})는 운영과 같은 엔티티 관리자를 쓴다. 감사는 실제 표에 남긴다(기록 탭 시험이 읽는다). DB 는 다른 시험과 같이 쓴다 — 회원·품목·팩·이벤트를 시험마다 새로 만든다.
 */
final class RewardEventTestKit {

    private static final int NODE_ID = 931;
    private static final AtomicInteger SEQ = new AtomicInteger();

    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final MutableTestClock clock;
    final SnowflakeIdGenerator ids;
    final PointService points;
    final PointPolicy policy;
    final IsolatedSideWork isolated;
    final RewardEventStore eventStore;
    final RewardRecipientStore recipientStore;
    final RewardMemberQueries memberQueries;
    final UserItemStore userItemStore;
    final EmoticonAcquisitionStore acquisitionStore;
    final RewardEventSupport support;
    final RewardEventViews views;
    final RewardLineGranter granter;
    final RewardEventEditService edits;
    final RewardEventQueryService queries;
    final RewardSelectionService selection;
    final RewardGrantService grants;
    final RewardFirstComeService firstCome;
    final RewardRunWorker worker;
    final PointShopRefundService refunds;
    final UserItemService userItems;
    final BoardFixture boards;
    final List<Object> memberEmails = Collections.synchronizedList(new ArrayList<>());
    /** 고액 거부 감사 대역(관리자 서버의 AccessDeniedRecorder 자리) — 부른 횟수 */
    final AtomicInteger largeDenied = new AtomicInteger();
    final long operator;

    RewardEventTestKit() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(JpaTestContext.transactions());
        clock = new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        ids = new SnowflakeIdGenerator(NODE_ID);
        ObjectProvider<Clock> clocks = new StaticListableBeanFactory(Map.of("clock", clock)).getBeanProvider(Clock.class);
        MemberRestrictionService restrictions = new MemberRestrictionService(new MemberRestrictionStore(JpaTestContext.query(),
                JpaTestContext.context().getBean(MemberRestrictionRepository.class), dataSource), null, null, ids, null, clocks);
        isolated = new IsolatedSideWork(dataSource, JpaTestContext.entityManager());
        policy = new PointPolicy(new PointPolicyQueries(dataSource));
        points = new PointService(new PointLedgerWriter(dataSource), policy, isolated, ids, restrictions, clocks);
        AuditLogService audit = new AuditLogService(JpaTestContext.context().getBean(AuditLogRepository.class), ids);
        MemberNotificationPublisher memberPublisher = new MemberNotificationPublisher(new MemberNotificationWriter(dataSource), isolated, ids, memberEmails::add);
        eventStore = JpaTestContext.create(RewardEventStore.class);
        recipientStore = JpaTestContext.create(RewardRecipientStore.class);
        memberQueries = JpaTestContext.create(RewardMemberQueries.class);
        PointShopItemStore itemStore = JpaTestContext.create(PointShopItemStore.class);
        userItemStore = JpaTestContext.create(UserItemStore.class);
        EmoticonPackStore packStore = JpaTestContext.create(EmoticonPackStore.class);
        acquisitionStore = JpaTestContext.create(EmoticonAcquisitionStore.class);
        OperatorMemberAccountService operatorAccounts = new OperatorMemberAccountService(JpaTestContext.create(OperatorMemberAccountStore.class), null, clocks);
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        Messages messages = new Messages(source, new StandardEnvironment(), "messages/messages");
        support = new RewardEventSupport(eventStore, recipientStore, audit);
        views = new RewardEventViews(eventStore, recipientStore, memberQueries);
        granter = new RewardLineGranter(recipientStore, memberQueries, points, itemStore, userItemStore, packStore, acquisitionStore, isolated, ids, memberPublisher);
        edits = JpaTestContext.transactional(new RewardEventEditService(eventStore, recipientStore, memberQueries, itemStore, packStore, policy, support, views, ids, clocks));
        queries = JpaTestContext.transactional(new RewardEventQueryService(eventStore, recipientStore, memberQueries, views, itemStore, policy, operatorAccounts,
                JpaTestContext.query(), messages, clocks));
        selection = JpaTestContext.transactional(new RewardSelectionService(eventStore, recipientStore, memberQueries, operatorAccounts, support, views, ids, clocks));
        grants = new RewardGrantService(eventStore, recipientStore, memberQueries, granter, support, queries, views, ids, JpaTestContext.transactions(), clocks);
        firstCome = new RewardFirstComeService(eventStore, recipientStore, memberQueries, granter, support, operatorAccounts, isolated, ids, clocks);
        worker = new RewardRunWorker(eventStore, grants, JpaTestContext.transactions());
        PointShopPurchaseStore purchaseStore = JpaTestContext.create(PointShopPurchaseStore.class);
        PointShopMemberStore shopMembers = JpaTestContext.create(PointShopMemberStore.class);
        NotificationPublisher operatorPublisher = new NotificationPublisher(null, null, ids, null, null, dataSource) {
            @Override
            public int publish(AdminNotificationEvent event, NotificationContext context) {
                return 1;
            }
        };
        refunds = JpaTestContext.transactional(new PointShopRefundService(itemStore, purchaseStore, userItemStore, shopMembers, points, audit, operatorPublisher,
                memberPublisher, ids, clocks));
        userItems = new UserItemService(userItemStore, itemStore, shopMembers, purchaseStore, new AvatarCatalog(), clocks);
        boards = new BoardFixture(JpaTestContext.context());
        operator = newOperator();
    }

    // ── 운영자 ──────────────────────────────────────────────────────────────

    long newOperator() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "reop-" + id + "@test.local", "re" + (id % 1_000_000_000L));
        return id;
    }

    /** 고액 권한이 있는(또는 없는) 운영자 */
    RewardOperator op(boolean canLarge) {
        return new RewardOperator(operator, "127.0.0.1", canLarge, largeDenied::incrementAndGet);
    }

    RewardOperator op() {
        return op(true);
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /** 이메일 인증을 마친 정상 회원 + 프로필(가입 시각 = 지금) */
    long member() {
        return member(clock.instant());
    }

    /** 가입 시각을 고른 회원 — 일괄 조건 시험이 가입일 범위로 자기 회원만 고른다 */
    long member(Instant createdAt) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO users (id, email, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at, created_at, updated_at) "
                + "VALUES (?, ?, now(), 'v1', now(), now(), ?, now())", id, "re-" + id + "@test.local", Timestamp.from(createdAt));
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, "r" + base36.substring(Math.max(0, base36.length() - 11)));
        return id;
    }

    void status(long userId, String status) {
        jdbc.update("UPDATE users SET status = ? WHERE id = ?", status, userId);
    }

    void freeze(long userId) {
        jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'POINT_FREEZE', '시험 동결', ?, 'AUTO', "
                + "'REWARD_TEST', ?)", ids.nextId(), userId, Timestamp.from(clock.instant().minusSeconds(60)), Timestamp.from(clock.instant()));
    }

    void unfreeze(long userId) {
        jdbc.update("DELETE FROM member_restrictions WHERE user_id = ?", userId);
    }

    /** 운영자 회원 계정으로 잇는다 */
    void operatorAccount(long userId) {
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", newOperator(), userId);
    }

    // ── 품목 · 팩 · 글 ──────────────────────────────────────────────────────

    private long item(String kind, String usage, String color, Integer duration) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO point_shop_items (id, code, kind, usage_type, name, description, color, price_point, duration_days, active) "
                + "VALUES (?, ?, ?, ?, ?, '시험 품목', ?, 100, ?, true)", id, "re-" + Long.toString(id, 36), kind, usage, "보상 " + (SEQ.incrementAndGet()), color, duration);
        return id;
    }

    long permanentColor() {
        return item("NICKNAME_COLOR", "PERMANENT", "#112233", null);
    }

    long periodColor(int days) {
        return item("NICKNAME_COLOR", "PERIOD", "#223344", days);
    }

    long ticket() {
        return item("NICKNAME_CHANGE_TICKET", "CONSUMABLE", null, null);
    }

    long avatarItem() {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE kind = 'PROFILE_AVATAR' ORDER BY id LIMIT 1", Long.class);
    }

    /** 판매 중 팩(운영 팩이면 creator null · 작가 팩이면 가격 0 또는 유료) */
    long pack(Long creatorId, int price) {
        long id = ids.nextId();
        long rev = ids.nextId();
        tx.executeWithoutResult(s -> {
            boolean wasOpen = Boolean.TRUE.equals(jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class));
            if (price > 0) { // 유료 팩은 열린 가격 단계만(트리거) — 이 트랜잭션 동안만 유료 판매를 켜 둔다(아래에서 되돌린다)
                jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = true, price_tiers = CASE WHEN ? = ANY (price_tiers) THEN price_tiers ELSE array_append(price_tiers, ?) END WHERE id = 1",
                        price, price);
            }
            jdbc.update("INSERT INTO emoticon_packs (id, source, creator_id, status, price, live_revision_id, published_at, created_at, updated_at) "
                    + "VALUES (?, ?, ?, 'APPROVED', ?, ?, now(), now(), now())", id, creatorId == null ? "OFFICIAL" : "CREATOR", creatorId, price, rev);
            jdbc.update("INSERT INTO emoticon_pack_revisions (id, pack_id, name, created_at) VALUES (?, ?, ?, now())", rev, id, "팩" + (SEQ.incrementAndGet() % 1000));
            if (price > 0) {
                jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ? WHERE id = 1", wasOpen);
            }
        });
        return id;
    }

    /** 글 + 회원 댓글들(작성 시각 = 지금) — 글 ID */
    long post(long authorId, long... commenters) {
        long board = ids.nextId();
        boards.board(board, "re" + Long.toString(board, 36), "보상시험" + SEQ.incrementAndGet(), 900);
        long post = ids.nextId();
        boards.post(post, board, authorId, "이벤트 글", "댓글 달고 받아 가세요");
        for (long c : commenters) {
            boards.comment(ids.nextId(), post, board, c, "참여해요");
        }
        return post;
    }

    long boardOf(long postId) {
        return jdbc.queryForObject("SELECT board_id FROM posts WHERE id = ?", Long.class, postId);
    }

    // ── 이벤트 만들기 ───────────────────────────────────────────────────────

    static RewardLineDto points(int amount) {
        return new RewardLineDto("new-" + SEQ.incrementAndGet(), "POINT", amount, null, null, null, null, null, null, null, null, null, null);
    }

    static RewardLineDto item(long itemId, Integer quantity) {
        return new RewardLineDto("new-" + SEQ.incrementAndGet(), "SHOP_ITEM", null, Long.toString(itemId), null, null, null, null, quantity, null, null, null, null);
    }

    static RewardLineDto packLine(long packId) {
        return new RewardLineDto("new-" + SEQ.incrementAndGet(), "EMOTICON_PACK", null, null, null, null, null, null, null, Long.toString(packId), null, null, null);
    }

    static RewardLineDto manual(String name) {
        return new RewardLineDto("new-" + SEQ.incrementAndGet(), "MANUAL", null, null, null, null, null, null, null, null, null, name, "운영자가 따로 보냄");
    }

    static RewardTierDto tier(String name, int quota, RewardLineDto... lines) {
        return new RewardTierDto("new-" + SEQ.incrementAndGet(), name, quota, List.of(lines));
    }

    RewardEventSaveRequest selection(RewardTierDto... tiers) {
        return new RewardEventSaveRequest("당첨 이벤트 " + SEQ.incrementAndGet(), "", clock.instant().minus(Duration.ofHours(1)), clock.instant().plus(Duration.ofDays(3)), "",
                "SELECTION", null, new RewardSelectionConfig(List.of("MANUAL"), null), null, true, List.of(tiers));
    }

    RewardEventSaveRequest commentDraw(long postId, RewardTierDto... tiers) {
        Instant now = clock.instant();
        return new RewardEventSaveRequest("댓글 이벤트 " + SEQ.incrementAndGet(), "", now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(3)), "", "SELECTION", null,
                new RewardSelectionConfig(List.of("MANUAL", "COMMENT_DRAW"), new RewardCommentDrawConfig("/boards/x/" + postId, null, null, now.minus(Duration.ofHours(1)),
                        now.plus(Duration.ofHours(1)), true)), null, true, List.of(tiers));
    }

    RewardEventSaveRequest firstCome(String trigger, int limit, RewardLineDto... lines) {
        return new RewardEventSaveRequest("선착순 " + SEQ.incrementAndGet(), "", clock.instant().minus(Duration.ofHours(1)), clock.instant().plus(Duration.ofDays(3)), "",
                "FIRST_COME", new RewardFirstComeConfig(trigger, limit), null, null, true, List.of(new RewardTierDto("new-" + SEQ.incrementAndGet(), "선착순", limit,
                List.of(lines))));
    }

    RewardEventSaveRequest bulk(RewardBulkConfig bulk, RewardLineDto... lines) {
        return new RewardEventSaveRequest("일괄 " + SEQ.incrementAndGet(), "", clock.instant().minus(Duration.ofHours(1)), clock.instant().plus(Duration.ofDays(3)), "", "BULK",
                null, null, bulk, true, List.of(new RewardTierDto("new-" + SEQ.incrementAndGet(), "전체", 0, List.of(lines))));
    }

    long create(RewardEventSaveRequest body) {
        return Long.parseLong(edits.create(op(), body).id());
    }

    /** 당첨자 선정 이벤트를 만들고 회원을 넣고 시작·마감까지 — 이벤트 ID */
    long closedSelection(RewardEventSaveRequest body, long... members) {
        long id = create(body);
        RewardEventDetailView d = edits.start(op(), id);
        if (members.length > 0) {
            List<String> memberIds = new ArrayList<>();
            for (long m : members) {
                memberIds.add(Long.toString(m));
            }
            selection.addRecipients(op(), id, new RewardRecipientAddRequest(d.tiers().get(0).id(), memberIds));
        }
        edits.close(op(), id);
        return id;
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /** {free, paid, debt, balance} */
    long[] balance(long userId) {
        List<long[]> rows = jdbc.query("SELECT free_balance, paid_balance, revoke_debt, balance FROM point_balances WHERE user_id = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, userId);
        return rows.isEmpty() ? new long[] {0, 0, 0, 0} : rows.get(0);
    }

    void assertInvariants(long... userIds) {
        assertThat(PointInvariants.violations(jdbc, userIds)).as("포인트 불변식").isEmpty();
    }

    /** 원장 줄(사유별) — 회원 */
    List<Map<String, Object>> ledger(long userId, String reason) {
        return jdbc.queryForList("SELECT * FROM point_ledger WHERE user_id = ? AND reason = ? ORDER BY id", userId, reason);
    }

    /** 받는 사람 상태 */
    String recipientStatus(long eventId, long userId) {
        List<String> rows = jdbc.queryForList("SELECT status FROM reward_event_recipients WHERE event_id = ? AND user_id = ?", String.class, eventId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 그 회원의 그 이벤트 줄 결과(줄 차례) — status·reason·revoked_points */
    List<Map<String, Object>> results(long eventId, long userId) {
        return jdbc.queryForList("SELECT x.* FROM reward_event_line_results x JOIN reward_event_lines l ON l.id = x.line_id WHERE x.event_id = ? AND x.user_id = ? "
                + "ORDER BY l.position", eventId, userId);
    }

    String eventStatus(long eventId) {
        return jdbc.queryForObject("SELECT status FROM reward_events WHERE id = ?", String.class, eventId);
    }

    /** 이벤트의 감사 행위(차례대로) */
    List<String> audits(long eventId) {
        return jdbc.queryForList("SELECT action FROM audit_logs WHERE target_type = 'REWARD_EVENT' AND target_id = ? ORDER BY occurred_at, id", String.class,
                Long.toString(eventId));
    }

    /** 회원의 원장 상계를 흉내 낼 미회수 빚 */
    void debt(long userId, long amount) {
        inTx(() -> {
            PointLedgerWriter writer = new PointLedgerWriter(dataSource);
            PointLedgerWriter.Balance b = writer.lockBalance(userId, clock.instant());
            writer.saveBalance(userId, b.free(), b.paid(), b.debt() + amount, clock.instant());
            return null;
        });
    }

    /** 충전(유상) 포인트를 흉내 낸다 — PointShopTestKit 과 같은 방식 */
    void chargePaid(long userId, long amount) {
        inTx(() -> {
            Instant now = clock.instant();
            PointLedgerWriter writer = new PointLedgerWriter(dataSource);
            PointLedgerWriter.Balance balance = writer.lockBalance(userId, now);
            long line = ids.nextId();
            writer.insertEntry(line, userId, PointKind.EARN, PointReason.CHARGE.name(), (int) amount, 0, amount, balance.free() + balance.paid() + amount, null, null, null,
                    null, now);
            long lot = ids.nextId();
            writer.insertLot(new PointLedgerWriter.NewLot(lot, userId, PointLotKind.PAID, amount, null, PointReason.CHARGE.name(), null, null, line, null, now));
            writer.insertEntryLot(line, lot, amount);
            writer.saveBalance(userId, balance.free(), balance.paid() + amount, balance.debt(), now);
            return null;
        });
    }
}
