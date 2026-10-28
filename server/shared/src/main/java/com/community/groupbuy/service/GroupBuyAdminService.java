package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.groupbuy.dto.CancelSummary;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.RoundCreated;
import com.community.groupbuy.dto.StockChange;
import com.community.groupbuy.entity.ExtensionMode;
import com.community.groupbuy.entity.Fulfillment;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.ImageRow;
import com.community.groupbuy.repository.master.ShippingPolicyStore;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공구 운영(관리자 — 계약 「패키지」 GroupBuyAdminService, commerce.md 10-2, 결정 17·29·31·51·52·80). 권한(공구 관리·중단 {@code commerce.groupbuy.cancel}·수량
 * {@code commerce.stock.adjust}·차수 {@code commerce.product.manage})과 설명 HTML 정화는 관리자 컨트롤러(2b-4)가 본다.
 *
 * <ul>
 *   <li><b>등록·수정</b>: DRAFT(공개 전)는 모든 칸. 「모집 시작」(공개) 뒤에는 설명·이미지(요약·사양 포함)만 — 나머지 칸이 다르면 409 {@code GROUP_BUY_LOCKED_FIELD}(10-2).
 *       마감 후 발주 공구는 발송 예정일 필수, 즉시 발송 공구는 결제 후 N영업일(결정 51). 낙관 잠금 {@code version}.</li>
 *   <li><b>모집 시작</b>({@link #publish}): 기간 상한(성사형 14일·선착순 30일 — 설정)·배송비 정책을 확인하고 공개. 시작 시각이 이미 됐으면 그 자리에서 RECRUITING
 *       ({@link #startNow} — 그 시각에 유효한 정책 줄 사본·처음 수량·수정 이력 1, 추가 차수면 이전 차수 신청자에게 알림 이벤트). 아니면 상태 워커가 시작 시각에.</li>
 *   <li><b>중단</b>({@link #cancel}): 공구 행을 잠그고 CANCELED(종료 사유 CANCELED)만 짧게 — 주문마다의 환불·결제 중 주문 닫기는 {@code groupbuy-refund} 워커(결정 29·37·61).</li>
 *   <li><b>추가 입고·줄이기</b>: {@link GroupBuyQtyService} + 수정 이력(수량 사본)·감사(돈 관련).</li>
 *   <li><b>추가 차수</b>({@link #createRound}): 원본은 모집이 끝난 공구(중단 제외), 같은 루트에 DRAFT·RECRUITING 차수가 있으면 거절, 동시 두 번은 유일 (루트, 차수)가 막는다.</li>
 * </ul>
 */
@Service
public class GroupBuyAdminService {

    public static final int TITLE_MAX = 100;
    public static final int TEXT_MAX = 5000;
    public static final int DESCRIPTION_MAX = 200_000;

    public static final int IMAGES_MAX = 8;
    public static final int REASON_MIN = 2;
    public static final int REASON_MAX = 200;
    static final Set<String> PRICE_BASES = Set.of("OFFICIAL_STORE", "PREVIOUS_ROUND", "MARKET", "PREVIOUS_SALE");

    private final GroupBuyStore groups;
    private final ShippingPolicyStore policies;
    private final GroupBuyQtyService qty;
    private final GroupBuyNoticeService notices;
    private final com.community.groupbuy.repository.master.GroupBuyNoticeStore noticeStore;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate separate;
    private final Clock clock;

    public GroupBuyAdminService(GroupBuyStore groups, ShippingPolicyStore policies, GroupBuyQtyService qty, GroupBuyNoticeService notices,
                                com.community.groupbuy.repository.master.GroupBuyNoticeStore noticeStore, AuditLogService audit, SnowflakeIdGenerator ids,
                                ApplicationEventPublisher events, DataSource dataSource, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.groups = groups;
        this.policies = policies;
        this.qty = qty;
        this.notices = notices;
        this.noticeStore = noticeStore;
        this.audit = audit;
        this.ids = ids;
        this.events = events;
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(transactions);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 등록·수정 ───────────────────────────────────────────────────────────

    /** 공구 등록(DRAFT, 1차) — 새 공구 ID */
    public long create(GroupBuyDraft draft, Long operatorId, String ip) {
        GroupBuyStore.Settings settings = groups.settings();
        Validated v = validate(draft, settings);
        GroupBuyStore.NewGroupBuy g = v.group();
        return tx.execute(status -> {
            Instant now = now();
            long id = ids.nextId();
            groups.insert(id, g, id, 1, operatorId, now);
            insertSkus(id, v.plan());
            replaceImages(id, draft.images(), now);
            record(operatorId, AuditActions.GROUPBUY_CREATE, id, ip, Map.of("title", g.title(), "roundNo", 1));
            return id;
        });
    }

    /**
     * 공구 수정 — 공개 전 DRAFT 는 모든 칸, 공개(모집 시작) 뒤에는 설명·이미지·요약·사양만.
     *
     * @throws GroupBuyException {@code VERSION_CONFLICT}(다른 사람이 먼저 고쳤다) · {@code LOCKED_FIELD}(잠긴 칸이 다르다)
     */
    public void update(long id, int version, GroupBuyDraft draft, Long operatorId, String ip) {
        GroupBuyStore.Settings settings = groups.settings();
        Validated v = validate(draft, settings);
        GroupBuyStore.NewGroupBuy g = v.group();
        tx.executeWithoutResult(status -> {
            Instant now = now();
            GroupBuyRow row = groups.find(id).orElseThrow(GroupBuyException::notFound);
            if (row.version() != version) {
                throw GroupBuyException.versionConflict();
            }
            if (row.publishedAt() == null) {
                if (!groups.updateDraft(id, version, g, now)) {
                    throw GroupBuyException.versionConflict();
                }
                java.util.Map<Long, List<String>> noticeLinks = noticeStore.skuNamesByNotice(id); // 단품 ID 가 바뀌므로 양식의 적용 단품은 이름으로 다시 잇는다
                groups.deleteSkuStructure(id);
                insertSkus(id, v.plan());
                notices.relinkSkus(id, noticeLinks);
                replaceImages(id, draft.images(), now);
                record(operatorId, AuditActions.GROUPBUY_UPDATE, id, ip, Map.of("stage", "DRAFT"));
                return;
            }
            String locked = v.plan().sameAs(groups.axes(id), groups.skus(id)) ? lockedFieldChanged(row, g) : "options";
            if (locked != null) {
                throw GroupBuyException.lockedField(locked);
            }
            Optional<Integer> revision = groups.updateContent(id, version, g.summary(), g.spec(), g.description(), now);
            if (revision.isEmpty()) {
                throw GroupBuyException.versionConflict();
            }
            replaceImages(id, draft.images(), now);
            if (row.status() != GroupBuyStatus.DRAFT) {
                groups.snapshotRevision(ids.nextId(), id, revision.get(), "CONTENT", operatorId, now);
            }
            record(operatorId, AuditActions.GROUPBUY_UPDATE, id, ip, Map.of("stage", "CONTENT", "revisionNo", revision.get()));
        });
    }

    /**
     * 모집 시작(공개) — 기간 상한·배송비 정책을 확인한다. 시작 시각이 이미 됐으면 그 자리에서 RECRUITING.
     *
     * @throws GroupBuyException   {@code INVALID_STATE}(이미 공개) · {@code VERSION_CONFLICT} · {@code SHIPPING_POLICY_NOT_FOUND} · {@code NOTICE_INCOMPLETE}(상품정보제공고시 — 결정 87)
     * @throws ValidationException 기간이 상한을 넘거나 마감이 지났다
     */
    public void publish(long id, int version, Long operatorId, String ip) {
        tx.executeWithoutResult(status -> {
            Instant now = now();
            GroupBuyRow row = groups.find(id).orElseThrow(GroupBuyException::notFound);
            if (row.status() != GroupBuyStatus.DRAFT || row.publishedAt() != null) {
                throw GroupBuyException.invalidState();
            }
            GroupBuyStore.Settings settings = groups.settings();
            Instant from = row.startAt().isAfter(now) ? row.startAt() : now;
            if (!row.endAt().isAfter(from)) {
                throw ValidationException.of("endAt", new MessageRef("groupbuy.admin.period.past"));
            }
            int capDays = row.minQty() > 0 ? settings.demandMaxDays() : settings.firstComeMaxDays();
            if (Duration.between(from, row.endAt()).compareTo(Duration.ofDays(capDays)) > 0) {
                throw ValidationException.of("endAt", new MessageRef("groupbuy.admin.period.tooLong"));
            }
            if (policies.effective(row.shippingPolicyCode(), from).isEmpty()) {
                throw GroupBuyException.shippingPolicyNotFound();
            }
            notices.assertComplete(id); // 상품정보제공고시 — 모든 양식의 모든 항목이 값 또는 제공 불가 + 사유, 모든 옵션이 어느 양식에(결정 87)
            if (!groups.publish(id, version, now)) {
                throw GroupBuyException.versionConflict();
            }
            record(operatorId, AuditActions.GROUPBUY_START, id, ip, Map.of("startAt", row.startAt().toString(), "endAt", row.endAt().toString()));
        });
        GroupBuyRow published = groups.find(id).orElseThrow();
        if (!published.startAt().isAfter(now())) {
            startNow(id);
        }
    }

    /**
     * DRAFT → RECRUITING(새 트랜잭션) — 공개됐고 시작 시각이 된 공구만. 그 시각에 유효한 배송비 정책 줄을 복사하고(10-2) 처음 수량을 고정하고 수정 이력 1을 남긴다.
     * 추가 차수면 루트의 이전 차수 신청자(같은 회원 한 번)에게 알림 이벤트(결정 31). 정책 줄이 없으면 시작하지 않는다(false — 운영자가 정책을 넣으면 다음 바퀴에).
     */
    public boolean startNow(long id) {
        Boolean started = separate.execute(status -> {
            Instant now = now();
            Optional<GroupBuyRow> found = groups.find(id);
            if (found.isEmpty() || found.get().status() != GroupBuyStatus.DRAFT || found.get().publishedAt() == null || found.get().startAt().isAfter(now)) {
                return false;
            }
            GroupBuyRow row = found.get();
            Optional<ShippingPolicyStore.PolicyRow> policy = policies.effective(row.shippingPolicyCode(), now);
            if (policy.isEmpty()) {
                return false;
            }
            if (!groups.start(id, policy.get(), now)) {
                return false;
            }
            groups.fixSkuInitial(id);
            groups.snapshotRevision(ids.nextId(), id, 1, "START", null, now);
            groups.insertStatusHistory(ids.nextId(), id, GroupBuyStatus.DRAFT, GroupBuyStatus.RECRUITING, "START", null, now);
            events.publishEvent(new GroupBuyEvents.StatusChanged(id, GroupBuyStatus.DRAFT.name(), GroupBuyStatus.RECRUITING.name(), "START"));
            if (row.roundNo() > 1) {
                events.publishEvent(new GroupBuyEvents.RoundStarted(row.roundRootId(), id, row.roundNo(), groups.roundWatchers(row.roundRootId(), id)));
            }
            return true;
        });
        return Boolean.TRUE.equals(started);
    }

    /** 조기 마감 — RECRUITING → CLOSING(closing_at = 지금, 종료 사유 EARLY_CLOSED) */
    public void closeEarly(long id, String reason, Long operatorId, String ip) {
        String why = requireReason(reason);
        tx.executeWithoutResult(status -> {
            Instant now = now();
            if (!groups.closeEarly(id, now)) {
                throw GroupBuyException.notRecruiting();
            }
            groups.insertStatusHistory(ids.nextId(), id, GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING, "EARLY_CLOSE", operatorId, now);
            record(operatorId, AuditActions.GROUPBUY_CLOSE_EARLY, id, ip, Map.of("reason", why));
            events.publishEvent(new GroupBuyEvents.StatusChanged(id, GroupBuyStatus.RECRUITING.name(), GroupBuyStatus.CLOSING.name(), "EARLY_CLOSE"));
        });
    }

    // ── 중단 ────────────────────────────────────────────────────────────────

    /** 중단 확인 창의 숫자(결정 29) — 바꾸지 않는다 */
    public CancelSummary cancelPreview(long id) {
        groups.find(id).orElseThrow(GroupBuyException::notFound);
        return summary(id);
    }

    /**
     * 중단 ① — 공구 행을 잠그고 CANCELED 로만 짧게(결정 37). 발송 전 주문 환불·결제 중 주문 닫기는 환불 워커가 주문마다 새 트랜잭션에서 한다(②). 이미 발송된 주문은 그대로(③).
     *
     * @throws GroupBuyException {@code INVALID_STATE} — 중단할 수 없는 상태(DRAFT·판정 불발·완료·이미 중단)
     */
    public CancelSummary cancel(long id, String reason, Long operatorId, String ip) {
        String why = requireReason(reason);
        return tx.execute(status -> {
            Instant now = now();
            GroupBuyRow row = groups.lock(id).orElseThrow(GroupBuyException::notFound);
            if (!GroupBuyStatus.CANCELABLE.contains(row.status())) {
                throw GroupBuyException.invalidState();
            }
            CancelSummary summary = summary(id);
            if (!groups.cancel(id, now)) {
                throw GroupBuyException.invalidState();
            }
            groups.insertStatusHistory(ids.nextId(), id, row.status(), GroupBuyStatus.CANCELED, "OPERATOR_CANCEL", operatorId, now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", why);
            detail.put("from", row.status().name());
            detail.put("refundOrders", summary.refundOrders());
            detail.put("refundAmount", summary.refundAmount());
            detail.put("shippedOrders", summary.shippedOrders());
            record(operatorId, AuditActions.GROUPBUY_CANCEL, id, ip, detail);
            events.publishEvent(new GroupBuyEvents.StatusChanged(id, row.status().name(), GroupBuyStatus.CANCELED.name(), "OPERATOR_CANCEL"));
            return summary;
        });
    }

    private CancelSummary summary(long id) {
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE status IN ('JOINED', 'CONFIRMED', 'PREPARING')), "
                + "coalesce(sum(cash_amount - refunded_amount - compensation_amount) FILTER (WHERE status IN ('JOINED', 'CONFIRMED', 'PREPARING')), 0), "
                + "count(*) FILTER (WHERE status IN ('SHIPPED', 'DELIVERED')), count(*) FILTER (WHERE status = 'PENDING_PAYMENT') "
                + "FROM group_buy_participations WHERE group_buy_id = ?", (rs, i) -> new CancelSummary(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)), id);
    }

    // ── 수량 ────────────────────────────────────────────────────────────────

    /** 추가 입고(결정 17·80) — 사유 필수, 수정 이력(수량 사본)·감사 */
    public StockChange addStock(long id, Long skuId, int count, String reason, Long operatorId, String ip) {
        String why = requireReason(reason);
        requirePositive(count);
        return tx.execute(status -> {
            Instant now = now();
            GroupBuyRow before = groups.find(id).orElseThrow(GroupBuyException::notFound);
            StockChange change = qty.add(id, skuId, count, why, operatorId, now);
            groups.snapshotRevision(ids.nextId(), id, groups.nextRevision(id, now), "STOCK_ADD", operatorId, now);
            record(operatorId, AuditActions.GROUPBUY_STOCK_ADD, id, ip, stockDetail(skuId, count, why, before.maxQty(), change.maxQty()));
            return change;
        });
    }

    /** 줄이기(결정 17·48·80) — 사유 필수, 줄인 결과 다 팔리면 그 자리에서 CLOSING */
    public StockChange reduceStock(long id, Long skuId, int count, String reason, Long operatorId, String ip) {
        String why = requireReason(reason);
        requirePositive(count);
        return tx.execute(status -> {
            Instant now = now();
            GroupBuyRow before = groups.find(id).orElseThrow(GroupBuyException::notFound);
            StockChange change = qty.reduce(id, skuId, count, why, operatorId, now);
            groups.snapshotRevision(ids.nextId(), id, groups.nextRevision(id, now), "STOCK_REDUCE", operatorId, now);
            record(operatorId, AuditActions.GROUPBUY_STOCK_REDUCE, id, ip, stockDetail(skuId, count, why, before.maxQty(), change.maxQty()));
            return change;
        });
    }

    private static Map<String, Object> stockDetail(Long skuId, int count, String reason, int before, int after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("skuId", skuId == null ? "" : skuId.toString());
        detail.put("qty", count);
        detail.put("reason", reason);
        detail.put("before", Map.of("maxQty", before));
        detail.put("after", Map.of("maxQty", after));
        return detail;
    }

    // ── 추가 차수 ───────────────────────────────────────────────────────────

    /**
     * 추가 차수 공구(결정 31) — 설명·이미지·요약·사양·옵션 축·값·조합·추가 구성상품(추가금·가격·처음 한도)·가격·방식·배송비 정책·차수 한도 합산을 복사한 새 DRAFT. 기간·발송 예정일은 새로 받는다.
     *
     * @throws GroupBuyException {@code ROUND_SOURCE_INVALID}(모집이 끝나지 않았거나 중단) · {@code ROUND_EXISTS}(루트에 DRAFT·RECRUITING 차수가 있다 — 동시 두 번 포함)
     */
    public RoundCreated createRound(long sourceId, Instant startAt, Instant endAt, LocalDate shipPlannedAt, Long operatorId, String ip) {
        try {
            return tx.execute(status -> {
                Instant now = now();
                GroupBuyRow source = groups.find(sourceId).orElseThrow(GroupBuyException::notFound);
                if (!GroupBuyStatus.ROUND_SOURCE.contains(source.status())) {
                    throw GroupBuyException.roundSourceInvalid();
                }
                long root = source.roundRootId();
                if (groups.roundOpen(root)) {
                    throw GroupBuyException.roundExists();
                }
                int roundNo = groups.maxRoundNo(root) + 1;
                GroupBuySkuPlan.DraftParts parts = GroupBuySkuPlan.draftParts(groups.axes(sourceId), groups.skus(sourceId));
                GroupBuyDraft draft = new GroupBuyDraft(source.title(), source.productName(), source.summary(), source.spec(), source.description(), source.normalPrice(),
                        source.normalPriceBasis(), source.normalPriceSource(), source.groupPrice(), source.shippingPolicyCode(), source.minQty(),
                        source.initialMaxQty() == null ? source.maxQty() : source.initialMaxQty(), source.maxQtyPerUser(), startAt, endAt, source.extensionMode(),
                        source.fulfillment(), shipPlannedAt, source.shipWithinBusinessDays(), source.withdrawalRestriction(), source.returnPolicy(),
                        source.roundLimitShared(), parts.axes(), parts.skus(), parts.addons(),
                        groups.images(sourceId).stream().map(i -> new GroupBuyDraft.ImageRef(i.imageKey(), i.thumbKey())).toList());
                Validated v = validate(draft, groups.settings());
                GroupBuyStore.NewGroupBuy g = v.group();
                long id = ids.nextId();
                groups.insert(id, g, root, roundNo, operatorId, now);
                insertSkus(id, v.plan());
                notices.copy(sourceId, id, now); // 고시 양식도 복사 — 카탈로그 버전 그대로(결정 87)
                replaceImages(id, draft.images(), now);
                record(operatorId, AuditActions.GROUPBUY_ROUND_CREATE, id, ip, Map.of("sourceId", Long.toString(sourceId), "roundNo", roundNo));
                return new RoundCreated(id, root, roundNo, true);
            });
        } catch (DuplicateKeyException e) {
            throw GroupBuyException.roundExists(); // 동시 두 번 — 유일 (루트, 차수)
        }
    }

    // ── 발송 지연 공지 ───────────────────────────────────────────────────────

    /**
     * 발송 지연 공지(결정 52) — 새 예정일·사유를 남기고 수정 이력을 하나 올린다. 그때부터 그 전에 결제한 발송 전 주문은 승인 없이 전액 취소할 수 있다
     * ({@link ParticipationCancelService}). 회원 앱·메일 통지는 2b-4.
     */
    public void announceDelay(long id, LocalDate newPlannedAt, String reason, Long operatorId, String ip) {
        String why = requireReason(reason);
        if (newPlannedAt == null) {
            throw ValidationException.of("newPlannedAt", new MessageRef("groupbuy.admin.shipPlan.required"));
        }
        tx.executeWithoutResult(status -> {
            Instant now = now();
            GroupBuyRow row = groups.find(id).orElseThrow(GroupBuyException::notFound);
            if (row.status() == GroupBuyStatus.DRAFT || row.status() == GroupBuyStatus.CANCELED || row.status() == GroupBuyStatus.CLOSED_FAILED
                    || row.status() == GroupBuyStatus.COMPLETED) {
                throw GroupBuyException.invalidState();
            }
            groups.insertDelayNotice(ids.nextId(), id, row.shipPlannedAt(), newPlannedAt, why, operatorId, now);
            if (Fulfillment.AFTER_CLOSE.name().equals(row.fulfillment())) {
                groups.changeShipPlan(id, newPlannedAt, now);
            }
            groups.snapshotRevision(ids.nextId(), id, groups.nextRevision(id, now), "SHIP_DELAY", operatorId, now);
            record(operatorId, AuditActions.GROUPBUY_DELAY_NOTICE, id, ip, Map.of("from", String.valueOf(row.shipPlannedAt()), "to", newPlannedAt.toString(), "reason", why));
        });
    }

    // ── 검사 ────────────────────────────────────────────────────────────────

    /** 등록 값 검사 — 칸마다 400. 즉시 발송 공구의 발송 영업일이 비면 공구 설정 기본값 */
    Validated validate(GroupBuyDraft d, GroupBuyStore.Settings settings) {
        Map<String, MessageRef> f = new LinkedHashMap<>();
        if (blankOrLonger(d.title(), TITLE_MAX)) {
            f.put("title", new MessageRef("groupbuy.admin.title.invalid"));
        }
        if (blankOrLonger(d.productName(), TITLE_MAX)) {
            f.put("productName", new MessageRef("groupbuy.admin.productName.invalid"));
        }
        if (longer(d.summary(), TEXT_MAX) || longer(d.spec(), TEXT_MAX) || longer(d.description(), DESCRIPTION_MAX)) {
            f.put("description", new MessageRef("groupbuy.admin.text.tooLong"));
        }
        if (d.groupPrice() < 1) {
            f.put("groupPrice", new MessageRef("groupbuy.admin.price.invalid"));
        }
        if (d.normalPrice() != null && (d.normalPrice() < 1 || d.normalPriceBasis() == null || !PRICE_BASES.contains(d.normalPriceBasis()))) {
            f.put("normalPriceBasis", new MessageRef("groupbuy.admin.normalPrice.basisRequired")); // 정상가는 근거가 있어야 쓴다(결정 58)
        }
        if (d.shippingPolicyCode() == null || !d.shippingPolicyCode().matches("[A-Z][A-Z0-9_]{1,29}")) {
            f.put("shippingPolicyCode", new MessageRef("groupbuy.admin.shippingPolicy.invalid"));
        }
        if (d.maxQty() < 1 || d.minQty() < 0 || d.minQty() > d.maxQty() || (d.maxQtyPerUser() != null && d.maxQtyPerUser() < 1)) {
            f.put("maxQty", new MessageRef("groupbuy.admin.qty.invalid"));
        }
        if (d.startAt() == null || d.endAt() == null || !d.endAt().isAfter(d.startAt())) {
            f.put("endAt", new MessageRef("groupbuy.admin.period.invalid"));
        }
        String extension = d.extensionMode() == null ? ExtensionMode.DEMAND.name() : d.extensionMode();
        if (!extension.equals(ExtensionMode.DEMAND.name()) && !extension.equals(ExtensionMode.ALWAYS.name())) {
            f.put("extensionMode", new MessageRef("groupbuy.admin.extensionMode.invalid"));
        }
        Integer shipDays = null;
        if (Fulfillment.IMMEDIATE.name().equals(d.fulfillment())) {
            if (d.minQty() != 0) {
                f.put("fulfillment", new MessageRef("groupbuy.admin.fulfillment.immediateNeedsFirstCome")); // 성사 전에 보낼 수 없다(D1)
            }
            shipDays = d.shipWithinBusinessDays() == null ? settings.shipWithinBusinessDays() : d.shipWithinBusinessDays();
            if (shipDays < 1 || shipDays > 30) {
                f.put("shipWithinBusinessDays", new MessageRef("groupbuy.admin.shipDays.invalid"));
            }
        } else if (Fulfillment.AFTER_CLOSE.name().equals(d.fulfillment())) {
            if (d.shipPlannedAt() == null) {
                f.put("shipPlannedAt", new MessageRef("groupbuy.admin.shipPlan.required")); // 공급시기 약정(전자상거래법 제15조 ① — 결정 51)
            } else if (d.endAt() != null && d.shipPlannedAt().isBefore(ServiceTime.dateOf(d.endAt()))) {
                f.put("shipPlannedAt", new MessageRef("groupbuy.admin.shipPlan.beforeEnd"));
            }
        } else {
            f.put("fulfillment", new MessageRef("groupbuy.admin.fulfillment.invalid"));
        }
        if (longer(d.withdrawalRestriction(), 1000) || longer(d.returnPolicy(), 2000)) {
            f.put("withdrawalRestriction", new MessageRef("groupbuy.admin.text.tooLong"));
        }
        GroupBuySkuPlan plan = d.productName() == null || d.productName().isBlank() ? null : GroupBuySkuPlan.of(d, f); // 옵션 축·값·단품·추가 구성상품(결정 88)
        List<GroupBuyDraft.ImageRef> images = d.images() == null ? List.of() : d.images();
        if (images.size() > IMAGES_MAX || images.stream().anyMatch(i -> i == null || blankOrLonger(i.imageKey(), 300) || longer(i.thumbKey(), 300))) {
            f.put("images", new MessageRef("groupbuy.admin.images.invalid"));
        }
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
        return new Validated(new GroupBuyStore.NewGroupBuy(d.title().strip(), d.productName().strip(), trimToNull(d.summary()), trimToNull(d.spec()), d.description(), d.normalPrice(),
                d.normalPrice() == null ? null : d.normalPriceBasis(), trimToNull(d.normalPriceSource()), d.groupPrice(), d.shippingPolicyCode(), d.minQty(), d.maxQty(),
                d.maxQtyPerUser(), d.startAt().truncatedTo(ChronoUnit.MICROS), d.endAt().truncatedTo(ChronoUnit.MICROS), extension, d.fulfillment(),
                Fulfillment.AFTER_CLOSE.name().equals(d.fulfillment()) ? d.shipPlannedAt() : null, shipDays, trimToNull(d.withdrawalRestriction()),
                trimToNull(d.returnPolicy()), d.roundLimitShared()), plan);
    }

    /** 공개 뒤 잠긴 칸 중 바뀐 칸 이름(없으면 null) — 가격·기간·배송비·방식·수량(수량은 추가 입고·줄이기로만). 옵션 구성은 {@link GroupBuySkuPlan#sameAs} */
    static String lockedFieldChanged(GroupBuyRow row, GroupBuyStore.NewGroupBuy g) {
        if (!row.title().equals(g.title())) return "title";
        if (!row.productName().equals(g.productName())) return "productName";
        if (row.groupPrice() != g.groupPrice()) return "groupPrice";
        if (!Objects.equals(row.normalPrice(), g.normalPrice()) || !Objects.equals(row.normalPriceBasis(), g.normalPriceBasis())) return "normalPrice";
        if (!row.shippingPolicyCode().equals(g.shippingPolicyCode())) return "shippingPolicyCode";
        if (row.minQty() != g.minQty()) return "minQty";
        if ((row.initialMaxQty() == null ? row.maxQty() : row.initialMaxQty()) != g.maxQty() && row.maxQty() != g.maxQty()) return "maxQty";
        if (!Objects.equals(row.maxQtyPerUser(), g.maxQtyPerUser())) return "maxQtyPerUser";
        if (!row.startAt().equals(g.startAt()) || !row.endAt().equals(g.endAt())) return "period";
        if (!row.extensionMode().equals(g.extensionMode())) return "extensionMode";
        if (!row.fulfillment().equals(g.fulfillment())) return "fulfillment";
        if (!Objects.equals(row.shipPlannedAt(), g.shipPlannedAt()) || !Objects.equals(row.shipWithinBusinessDays(), g.shipWithinBusinessDays())) return "shipPlan";
        if (!Objects.equals(row.withdrawalRestriction(), g.withdrawalRestriction()) || !Objects.equals(row.returnPolicy(), g.returnPolicy())) return "returnPolicy";
        if (row.roundLimitShared() != g.roundLimitShared()) return "roundLimitShared";
        return null;
    }

    /** 검사를 마친 등록 값 — 공구 칸과 옵션 계획 */
    record Validated(GroupBuyStore.NewGroupBuy group, GroupBuySkuPlan plan) {
    }

    /** 축·값·단품을 넣는다(값 이름 → ID 로 조합을 잇는다) */
    private void insertSkus(long groupBuyId, GroupBuySkuPlan plan) {
        List<Map<String, Long>> valueIds = new java.util.ArrayList<>();
        for (int i = 0; i < plan.axes.size(); i++) {
            GroupBuySkuPlan.PlannedAxis axis = plan.axes.get(i);
            long axisId = ids.nextId();
            groups.insertAxis(axisId, groupBuyId, i, axis.name());
            Map<String, Long> byValue = new java.util.HashMap<>();
            for (int j = 0; j < axis.values().size(); j++) {
                long valueId = ids.nextId();
                groups.insertValue(valueId, axisId, groupBuyId, j, axis.values().get(j));
                byValue.put(axis.values().get(j), valueId);
            }
            valueIds.add(byValue);
        }
        for (int i = 0; i < plan.skus.size(); i++) {
            GroupBuySkuPlan.PlannedSku s = plan.skus.get(i);
            List<Long> combination = new java.util.ArrayList<>();
            for (int a = 0; a < s.valueNames().size(); a++) {
                combination.add(valueIds.get(a).get(s.valueNames().get(a)));
            }
            groups.insertSku(ids.nextId(), groupBuyId, s.kind(), combination, s.name(), s.extraPrice(), s.addonPrice(), s.maxQty(), s.onSale(), s.imageKey(), i);
        }
    }

    private void replaceImages(long groupBuyId, List<GroupBuyDraft.ImageRef> images, Instant now) {
        List<GroupBuyDraft.ImageRef> list = images == null ? List.of() : images;
        groups.replaceImages(groupBuyId, list.stream().map(i -> new ImageRow(i.imageKey().strip(), trimToNull(i.thumbKey()), 0)).toList(),
                list.stream().map(i -> ids.nextId()).toList(), now);
    }

    private void record(Long operatorId, String action, long groupBuyId, String ip, Map<String, Object> detail) {
        audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, action, AuditResult.SUCCESS, "GROUP_BUY",
                Long.toString(groupBuyId), ip, detail));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.strip().length() < REASON_MIN || reason.strip().length() > REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("groupbuy.admin.reason.invalid"));
        }
        return reason.strip();
    }

    private static void requirePositive(int count) {
        if (count < 1 || count > 100_000) {
            throw ValidationException.of("qty", new MessageRef("groupbuy.admin.qty.invalid"));
        }
    }

    private static boolean blankOrLonger(String value, int max) {
        return value == null || value.isBlank() || value.strip().length() > max;
    }

    private static boolean longer(String value, int max) {
        return value != null && value.length() > max;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
