package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditEvent;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonImageStore;
import com.community.emoticon.repository.master.EmoticonImageStore.ImageRow;
import com.community.emoticon.dto.request.EmoticonPayoutAccountRequest;
import com.community.emoticon.repository.master.EmoticonContractStore;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonPriceEventStore;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonSettlementStore;
import com.community.emoticon.service.EmoticonSettlementCalculator;
import com.community.emoticon.service.EmoticonSettlementService;
import com.community.emoticon.handler.EmoticonPackPaymentHandler;
import com.community.emoticon.service.EmoticonContractService;
import com.community.emoticon.service.EmoticonCreatorWithdrawal;
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.emoticon.service.EmoticonPurchaseNotices;
import com.community.holiday.repository.master.PublicHolidayStore;
import com.community.holiday.service.PublicHolidayService;
import com.community.identity.dto.IdentityProviderKind;
import com.community.identity.repository.master.IdentityStore;
import com.community.identity.service.IdentityVerificationService;
import com.community.zconfiguration.crypto.DataCipher;
import java.time.LocalDate;
import java.util.Base64;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReviewStore;
import com.community.emoticon.repository.master.EmoticonSettingsStore;
import com.community.emoticon.service.EmoticonAcquireService;
import com.community.emoticon.service.EmoticonContent;
import com.community.emoticon.service.EmoticonGuard;
import com.community.emoticon.service.EmoticonImageService;
import com.community.emoticon.service.EmoticonImageValidator;
import com.community.emoticon.service.EmoticonPackAdminService;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonSettingsService;
import com.community.emoticon.service.EmoticonShopService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.media.config.MediaProperties;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageGate;
import com.community.media.service.LocalDiskImageStorage;
import com.community.membernotification.repository.master.MemberNotificationWriter;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.operator.repository.master.OperatorMemberAccountStore;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.restriction.repository.master.MemberRestrictionStore;
import com.community.restriction.service.MemberRestrictionService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import com.community.testsupport.PointInvariants;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로보티콘 시험의 바탕 — 실제 PostgreSQL({@link CommerceTestDatabase}) 위에서 로보티콘 서비스·포인트({@link PointService})를 <b>손으로</b> 조립한다(포인트 상점 {@code PointShopTestKit} 과
 * 같은 방식 — 스프링 문맥 없음). 트랜잭션은 {@link DataSourceTransactionManager}(서비스·저장소의 JDBC 가 같은 연결), 시계는 {@link MutableTestClock}, 그림 저장소는 임시 폴더.
 * 감사·운영자 알림은 모으는 대역, 회원 알림은 실제 발행기(표 {@code member_notifications}). DB 는 다른 시험과 같이 쓴다 — 회원·팩은 시험마다 새로, 설정 행을 바꾸면 되돌린다.
 */
final class EmoticonTestKit {

    /** 다른 시험 클래스의 발급기와 겹치지 않는 서버 번호 */
    private static final int NODE_ID = 913;
    private static final AtomicInteger SEQ = new AtomicInteger();

    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final DataSourceTransactionManager txManager;
    final MutableTestClock clock;
    final SnowflakeIdGenerator ids;
    final PointService points;
    final List<AuditEvent> audits = Collections.synchronizedList(new ArrayList<>());
    /** 낸 운영자 알림(이벤트 · 맥락) */
    final List<Map.Entry<AdminNotificationEvent, NotificationContext>> operatorNotices = Collections.synchronizedList(new ArrayList<>());
    final EmoticonPackStore packStore;
    final EmoticonReadStore reads;
    final EmoticonSettingsService settings;
    final EmoticonImageService images;
    final EmoticonContent content;
    final EmoticonShopService shop;
    final EmoticonAcquireService acquire;
    final EmoticonStudioService studio;
    final EmoticonReviewService reviews;
    final EmoticonPackAdminService packs;
    final EmoticonGuard guard;
    final EmoticonContractService contracts;
    final EmoticonPayoutService payouts;
    final IdentityStore identities;
    /** S3 — 구매 기록 · 보유 · 결제 처리기(결제 모듈 없이 처리기를 바로 부른다) */
    final EmoticonPurchaseStore purchases;
    final EmoticonAcquisitionStore acquisitionStore;
    final EmoticonPackPaymentHandler handler;
    /** S4 — 작가 월 정산 */
    final EmoticonSettlementStore settlementStore;
    final EmoticonSettlementCalculator settlementCalculator;
    final EmoticonSettlementService settlements;
    /** 후속 결정 — 공휴일 표 · 구매 알림 */
    final PublicHolidayService holidays;
    final EmoticonPurchaseNotices purchaseNotices;
    /** 시험용 데이터 키(버전 1) */
    final DataCipher cipher;
    /** 운영자 회원 계정 판정(운영자 회원 계정 연결 2026-11-05) */
    final OperatorMemberAccountService operatorAccounts;
    /** 탈퇴 작가의 팩(후속 결정 6) — 탈퇴 완료 트랜잭션이 부르는 그 서비스 */
    final EmoticonCreatorWithdrawal creatorWithdrawal;
    final long operator;

    EmoticonTestKit() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        txManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txManager);
        clock = new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        ids = new SnowflakeIdGenerator(NODE_ID);
        ObjectProvider<Clock> clocks = new StaticListableBeanFactory(Map.of("clock", clock)).getBeanProvider(Clock.class);
        MemberRestrictionService restrictions = new MemberRestrictionService(new MemberRestrictionStore(dataSource), null, null, ids, null, clocks);
        points = new PointService(new PointLedgerWriter(dataSource), new PointPolicy(dataSource), new IsolatedSideWork(dataSource, noFlush()), ids, restrictions, clocks);
        AuditLogService audit = new AuditLogService(null, null) {
            @Override
            public void record(AuditEvent event) {
                audits.add(event);
            }
        };
        NotificationPublisher operatorPublisher = new NotificationPublisher(null, null, null, null, ids, null, null, dataSource) {
            @Override
            public int publish(AdminNotificationEvent event, NotificationContext context) {
                operatorNotices.add(Map.entry(event, context));
                return 1;
            }
        };
        MemberNotificationPublisher memberPublisher = new MemberNotificationPublisher(new MemberNotificationWriter(dataSource), new IsolatedSideWork(dataSource, noFlush()),
                ids, event -> {
                });
        LocalDiskImageStorage storage;
        try {
            storage = new LocalDiskImageStorage(Files.createTempDirectory("emoticon-test"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        packStore = new EmoticonPackStore(dataSource);
        EmoticonReviewStore reviewStore = new EmoticonReviewStore(dataSource);
        EmoticonImageStore imageStore = new EmoticonImageStore(dataSource);
        EmoticonAcquisitionStore acquisitions = new EmoticonAcquisitionStore(dataSource);
        reads = new EmoticonReadStore(dataSource);
        contracts = new EmoticonContractService(new EmoticonContractStore(dataSource), ids, clocks);
        settings = new EmoticonSettingsService(new EmoticonSettingsStore(dataSource), contracts, audit, clocks);
        EmoticonPayoutStore payoutStore = new EmoticonPayoutStore(dataSource);
        EmoticonPriceEventStore priceEvents = new EmoticonPriceEventStore(dataSource);
        cipher = new DataCipher("1:" + Base64.getEncoder().encodeToString(new byte[32]), "");
        identities = new IdentityStore(dataSource);
        EmoticonImageValidator validator = new EmoticonImageValidator(new ImageCodec(new ImageGate(new MediaProperties(2, 10, 24))));
        images = new EmoticonImageService(validator, imageStore, storage, ids, dataSource, txManager, clocks);
        content = new EmoticonContent(packStore, imageStore, storage);
        // 운영자 회원 계정 판정(힘이 살아 있나만 — 권한 거울은 읽지 않으므로 역할 저장소 없이)
        operatorAccounts = new OperatorMemberAccountService(new OperatorMemberAccountStore(dataSource), null, clocks);
        shop = new EmoticonShopService(packStore, reads, acquisitions, content, operatorAccounts);
        acquire = new EmoticonAcquireService(packStore, acquisitions, reads, settings, content, points, ids, operatorAccounts, clocks);
        purchases = new EmoticonPurchaseStore(dataSource);
        settlementStore = new EmoticonSettlementStore(dataSource);
        holidays = new PublicHolidayService(new PublicHolidayStore(dataSource));
        settlementCalculator = new EmoticonSettlementCalculator(settlementStore, payoutStore, settings, holidays);
        studio = new EmoticonStudioService(packStore, reviewStore, imageStore, reads, settings, content, operatorPublisher, images, contracts, payoutStore, priceEvents,
                purchases, settlementCalculator, identities, ids, clocks);
        reviews = new EmoticonReviewService(reviewStore, packStore, reads, settings, content, audit, memberPublisher, contracts, payoutStore, priceEvents, ids, clocks);
        packs = new EmoticonPackAdminService(packStore, reviewStore, imageStore, reads, settings, content, audit, memberPublisher, images, contracts, payoutStore, priceEvents,
                purchases, ids, clocks);
        payouts = new EmoticonPayoutService(payoutStore, identities, studio, cipher, audit, clocks);
        settlements = new EmoticonSettlementService(settlementStore, settlementCalculator, payoutStore, payouts, reads, packStore, content, audit, ids, clocks);
        guard = new EmoticonGuard(reads, acquisitions, purchases, operatorAccounts, clocks);
        acquisitionStore = acquisitions;
        purchaseNotices = new EmoticonPurchaseNotices(memberPublisher);
        handler = new EmoticonPackPaymentHandler(purchases, packStore, acquisitions, settings, purchaseNotices, ids, clocks);
        creatorWithdrawal = new EmoticonCreatorWithdrawal(packStore, reviewStore, payoutStore, settlements, audit, clocks);
        operator = newOperator();
    }

    private static EntityManager noFlush() {
        return (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(), new Class<?>[] {EntityManager.class}, (proxy, method, args) -> switch (method.getName()) {
            case "flush" -> null;
            case "toString" -> "noFlushEntityManager";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    void inTx(Runnable work) {
        tx.executeWithoutResult(status -> work.run());
    }

    // ── 사람 ────────────────────────────────────────────────────────────────

    /** 이메일 인증을 마친 정상 회원 — 등급 {@code grade}(새싹 SEEDLING · 일반 REGULAR …) */
    long member(String grade) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO users (id, email, email_verified_at, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, now(), 'v1', now(), now())", id,
                "emo-" + id + "@test.local");
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname, grade_key) VALUES (?, ?, ?)", id, "e" + base36.substring(Math.max(0, base36.length() - 11)), grade);
        return id;
    }

    /** 이메일 인증을 마친 일반 등급 회원(인정되는 받기를 한다) */
    long member() {
        return member("REGULAR");
    }

    long newOperator() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "emoop-" + id + "@test.local", "emoop" + (id % 10_000_000_000L));
        return id;
    }

    void unverify(long userId) {
        jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", userId);
    }

    void suspend(long userId) {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", userId);
    }

    // ── 본인인증 · 정산 정보 · 계약 · 유료 설정(S2) ────────────────────────────

    /** 본인인증한 회원으로 만든다 — 인증 이름 {@code name}(null 이면 이름 없음 = V143 전에 인증한 회원) */
    void verify(long userId, String name) {
        identities.insertVerification(userId, String.format("%064x", userId), null, LocalDate.of(1990, 1, 1), IdentityProviderKind.FAKE, "emo-fake:" + userId,
                clock.instant());
        if (name != null) {
            identities.saveNameCipher(userId, cipher.encrypt(IdentityVerificationService.NAME, name));
        }
    }

    /** 본인인증(이름 홍길동) + 정산 정보(개인 · 국민) — 유료 희망 신청 준비 */
    void registerPayout(long userId) {
        verify(userId, "홍길동");
        inTx(() -> payouts.save(userId, new EmoticonPayoutAccountRequest("INDIVIDUAL", null, "KB", "123-456-78901234", "홍길동")));
    }

    /** 상품화 계약(약관 ROBOTIKON_CONTRACT) 새 판 — 시행일 {@code effective}. 판 번호 = 마지막 + 1(약관 표는 다른 시험과 같이 쓰므로 시험은 돌려받은 판을 쓴다) */
    int publishContract(LocalDate effective) {
        Integer last = jdbc.queryForObject("SELECT coalesce(max(version), 0) FROM terms_versions WHERE kind = 'ROBOTIKON_CONTRACT'", Integer.class);
        int version = last + 1;
        jdbc.update("INSERT INTO terms_versions (id, kind, version, body, effective_date, change_note, published_at) VALUES (?, 'ROBOTIKON_CONTRACT', ?, ?, ?, NULL, now())",
                ids.nextId(), version, CONTRACT_BODY.replace("{v}", Integer.toString(version)), effective);
        return version;
    }

    /** 오늘 시행하는 새 판(바로 지금 판) — 사이트 표시 시간대의 오늘(시드 Asia/Seoul) */
    int publishContract() {
        return publishContract(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
    }

    static final String CONTRACT_BODY = "<h2>로보티콘 상품화 계약 v{v}</h2><ul><li>사이트가 판매자예요.</li><li>정산 기준의 70%를 받아요 &amp; 매달 정산해요.</li></ul>"
            + "<p><strong>제1조(목적)</strong> 이 계약은 판매 조건을 정한다.</p><p>제2조<br>둘째 줄</p>";

    /** 유료 판매 켜기·가격 단계를 잠시 바꾼다 — 끝나면 되돌린다(되돌릴 단계 밖의 유료 팩은 무료로 돌려 다른 시험에 남기지 않는다) */
    void withPaid(boolean enabled, List<Integer> tiers, Runnable work) {
        Map<String, Object> before = jdbc.queryForMap("SELECT paid_sales_enabled, price_tiers::text AS tiers FROM emoticon_settings WHERE id = 1");
        setPaid(enabled, tiers);
        try {
            work.run();
        } finally {
            jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE price > 0"); // 유료 팩은 로보티콘 시험만 만든다 — 다른 시험의 단계 검사(쓰는 단계)에 남기지 않는다
            jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ?, price_tiers = ?::integer[] WHERE id = 1", before.get("paid_sales_enabled"), before.get("tiers"));
        }
    }

    /** 유료 판매 켜짐 · 기본 단계(1,500 · 2,500 · 3,500) */
    void withPaid(Runnable work) {
        withPaid(true, List.of(1500, 2500, 3500), work);
    }

    /** 유료 판매 켜기·가격 단계를 바로 바꾼다({@link #withPaid} 안에서) */
    void setPaid(boolean enabled, List<Integer> tiers) {
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ?, price_tiers = ?::integer[] WHERE id = 1", enabled,
                "{" + String.join(",", tiers.stream().map(String::valueOf).toList()) + "}");
    }

    /** 설정 행 전체(가격 단계는 글자 {@code {1500,2500,3500}} — {@link #restoreSettings} 로 되돌린다) */
    Map<String, Object> settingsRow() {
        return jdbc.queryForMap("SELECT reward_per_acquire, reward_daily_cap, item_min, item_max, pending_limit, paid_sales_enabled, fee_rate_percent, settlement_day, "
                + "payout_minimum, refund_days, price_tiers::text AS price_tiers FROM emoticon_settings WHERE id = 1");
    }

    void restoreSettings(Map<String, Object> was) {
        jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE price > 0");
        jdbc.update("UPDATE emoticon_settings SET reward_per_acquire = ?, reward_daily_cap = ?, item_min = ?, item_max = ?, pending_limit = ?, paid_sales_enabled = ?, "
                        + "fee_rate_percent = ?, settlement_day = ?, payout_minimum = ?, refund_days = ?, price_tiers = ?::integer[] WHERE id = 1", was.get("reward_per_acquire"),
                was.get("reward_daily_cap"), was.get("item_min"), was.get("item_max"), was.get("pending_limit"), was.get("paid_sales_enabled"), was.get("fee_rate_percent"),
                was.get("settlement_day"), was.get("payout_minimum"), was.get("refund_days"), was.get("price_tiers"));
    }

    /** 유료 희망 신청 본문(동의 셋 + 계약 판) */
    static EmoticonStudioSaveRequest paidBody(String name, ImageRow cover, List<ImageRow> items, Integer contractVersion) {
        EmoticonStudioSaveRequest b = body(name, cover, items);
        return new EmoticonStudioSaveRequest(b.name(), b.description(), b.tags(), b.coverImageId(), b.emoticons(), "PAID", null, true, true, true, contractVersion);
    }

    /** 유료 희망 새 팩 심사 요청 — 정산 정보·계약 동의를 갖춘 작가 */
    EmoticonStudioView.Pack submitPaid(long creator, String name, int contractVersion) {
        return inTx(() -> studio.submit(creator, null, paidBody(name, uploadCover(creator), uploads(creator, 8), contractVersion)));
    }

    void approve(long packId, int price) {
        long review = pendingReview(packId);
        inTx(() -> reviews.approve(operator, "127.0.0.1", Long.toString(review), new EmoticonReviewDecisionRequest(price, null)));
    }

    int price(long packId) {
        return jdbc.queryForObject("SELECT price FROM emoticon_packs WHERE id = ?", Integer.class, packId);
    }

    // ── 그림 · 팩 ───────────────────────────────────────────────────────────

    /** 작가가 로보티콘 그림 하나를 올린다(지문이 겹치지 않게) */
    ImageRow upload(long userId) {
        return images.uploadByMember(userId, TestImages.animated("u" + SEQ.incrementAndGet() + "-" + ids.nextId()), EmoticonImageKind.EMOTICON);
    }

    ImageRow uploadCover(long userId) {
        return images.uploadByMember(userId, TestImages.cover(0x100000 + SEQ.incrementAndGet()), EmoticonImageKind.COVER);
    }

    /** 그림 {@code n} 개를 올린다 */
    List<ImageRow> uploads(long userId, int n) {
        List<ImageRow> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(upload(userId));
        }
        return out;
    }

    /** 저장·신청 본문(동의 셋 모두 체크, 무료) */
    static EmoticonStudioSaveRequest body(String name, ImageRow cover, List<ImageRow> items) {
        List<EmoticonStudioSaveRequest.Item> list = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            list.add(new EmoticonStudioSaveRequest.Item(Long.toString(items.get(i).id()), "표" + (i + 1)));
        }
        return new EmoticonStudioSaveRequest(name, "시험 팩이에요.", List.of("시험"), cover == null ? null : Long.toString(cover.id()), list, "FREE", null, true, true, true,
                null);
    }

    EmoticonStudioView.Pack submitNew(long creator, String name) {
        return inTx(() -> studio.submit(creator, null, body(name, uploadCover(creator), uploads(creator, 8))));
    }

    /** 대기 심사 건 ID */
    long pendingReview(long packId) {
        return jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ? AND status = 'PENDING'", Long.class, packId);
    }

    void approve(long packId) {
        long review = pendingReview(packId);
        inTx(() -> reviews.approve(operator, "127.0.0.1", Long.toString(review), new EmoticonReviewDecisionRequest(0, null)));
    }

    /** 작가가 새 무료 팩을 내고 운영자가 승인한다 — 팩 ID */
    long approvedPack(long creator) {
        long id = Long.parseLong(submitNew(creator, "시험 팩 " + (SEQ.incrementAndGet() % 1000)).id());
        approve(id);
        return id;
    }

    /** 상점 판의 로보티콘 ID(차례대로) */
    List<Long> liveEmoticons(long packId) {
        return jdbc.queryForList("SELECT i.emoticon_id FROM emoticon_packs p JOIN emoticon_revision_items i ON i.revision_id = p.live_revision_id WHERE p.id = ? "
                + "ORDER BY i.position", Long.class, packId);
    }

    /** 마지막으로 낸 판의 로보티콘 ID(차례대로) */
    List<Long> liveEmoticonsOrSubmitted(long packId) {
        return jdbc.queryForList("SELECT i.emoticon_id FROM emoticon_revision_items i WHERE i.revision_id = (SELECT id FROM emoticon_pack_revisions WHERE pack_id = ? "
                + "AND version IS NOT NULL ORDER BY version DESC LIMIT 1) ORDER BY i.position", Long.class, packId);
    }

    /** 그 회원의 마지막 회원 알림 — {type, kind, packId, note} */
    Map<String, Object> lastNotice(long userId) {
        return jdbc.queryForMap("SELECT type, payload->>'kind' AS kind, payload->>'packId' AS pack_id, payload->>'note' AS note FROM member_notifications WHERE user_id = ? "
                + "ORDER BY id DESC LIMIT 1", userId);
    }

    String status(long packId) {
        return jdbc.queryForObject("SELECT status FROM emoticon_packs WHERE id = ?", String.class, packId);
    }

    int acquireCount(long packId) {
        return jdbc.queryForObject("SELECT acquire_count FROM emoticon_packs WHERE id = ?", Integer.class, packId);
    }

    /** 작가가 받은 받기 보상 합계 */
    long rewards(long creator) {
        return jdbc.queryForObject("SELECT coalesce(sum(delta), 0) FROM point_ledger WHERE user_id = ? AND reason = 'EMOTICON_CREATOR_REWARD'", Long.class, creator);
    }

    long balance(long userId) {
        List<Long> rows = jdbc.queryForList("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    void assertInvariants(long... userIds) {
        assertThat(PointInvariants.violations(jdbc, userIds)).as("포인트 불변식(commerce.md 5-5)").isEmpty();
    }

    int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    /** 설정 행을 잠시 바꾼다(한 행 — 끝나면 되돌린다) */
    void withSettings(int reward, int cap, int pendingLimit, Runnable work) {
        Map<String, Object> before = jdbc.queryForMap("SELECT reward_per_acquire, reward_daily_cap, pending_limit FROM emoticon_settings WHERE id = 1");
        jdbc.update("UPDATE emoticon_settings SET reward_per_acquire = ?, reward_daily_cap = ?, pending_limit = ? WHERE id = 1", reward, cap, pendingLimit);
        try {
            work.run();
        } finally {
            jdbc.update("UPDATE emoticon_settings SET reward_per_acquire = ?, reward_daily_cap = ?, pending_limit = ? WHERE id = 1", before.get("reward_per_acquire"),
                    before.get("reward_daily_cap"), before.get("pending_limit"));
        }
    }

    // ── 동시에 ──────────────────────────────────────────────────────────────

    /** 일을 한꺼번에 출발시킨다(스레드마다 연결·트랜잭션 따로) — 결과 또는 던진 예외 */
    static List<Object> runTogether(List<Callable<Object>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> job : jobs) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return job.call();
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
