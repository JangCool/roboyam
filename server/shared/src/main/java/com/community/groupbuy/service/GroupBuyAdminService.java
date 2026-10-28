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
import com.community.groupbuy.repository.master.GroupBuyProductStore;
import com.community.groupbuy.repository.master.GroupBuyProductStore.ProductRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 *   <li><b>등록·수정</b>: 공구 = 상품 하나 + 판매 조건(결정 89 — 설명·이미지·고시는 상품 것, {@link GroupBuyProductService}). DRAFT(공개 전)는 모든 칸.
 *       「모집 시작」(공개) 뒤에는 공급가만(돈 관련 감사, 역마진이면 확인) — 나머지 칸이 다르면 409 {@code GROUP_BUY_LOCKED_FIELD}(10-2).
 *       마감 후 발주 공구는 발송 예정일 필수, 즉시 발송 공구는 결제 후 N영업일(결정 51). 낙관 잠금 {@code version}.</li>
 *   <li><b>모집 시작</b>({@link #publish}): 기간 상한(성사형 14일·선착순 30일 — 설정)·배송비 정책·고시(공구 상품과 추가 구성상품의 상품)·
 *       <b>모든 단품의 공급가</b>(빈 값 409 {@code GROUP_BUY_SUPPLY_PRICE_MISSING}, 판매가 &lt; 공급가는 확인해야 — 409 {@code GROUP_BUY_NEGATIVE_MARGIN})를 확인하고 공개. 시작 시각이 이미 됐으면 그 자리에서 RECRUITING
 *       ({@link #startNow} — 그 시각에 유효한 정책 줄 사본·처음 수량·수정 이력 1, 추가 차수면 이전 차수 신청자에게 알림 이벤트). 아니면 상태 워커가 시작 시각에.</li>
 *   <li><b>중단</b>({@link #cancel}): 공구 행을 잠그고 CANCELED(종료 사유 CANCELED)만 짧게 — 주문마다의 환불·결제 중 주문 닫기는 {@code groupbuy-refund} 워커(결정 29·37·61).</li>
 *   <li><b>추가 입고·줄이기</b>: {@link GroupBuyQtyService} + 수정 이력(수량 사본)·감사(돈 관련).</li>
 *   <li><b>추가 차수</b>({@link #createRound}): 원본은 모집이 끝난 공구(중단 제외), 같은 루트에 DRAFT·RECRUITING 차수가 있으면 거절, 동시 두 번은 유일 (루트, 차수)가 막는다.</li>
 * </ul>
 */
@Service
public class GroupBuyAdminService {

    public static final int TITLE_MAX = 100;
    public static final int REASON_MIN = 2;
    public static final int REASON_MAX = 200;

    private final GroupBuyStore groups;
    private final GroupBuyProductStore products;
    private final ShippingPolicyStore policies;
    private final GroupBuyQtyService qty;
    private final GroupBuyNoticeService notices;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate separate;
    private final Clock clock;

    public GroupBuyAdminService(GroupBuyStore groups, GroupBuyProductStore products, ShippingPolicyStore policies, GroupBuyQtyService qty, GroupBuyNoticeService notices,
                                AuditLogService audit, SnowflakeIdGenerator ids, ApplicationEventPublisher events, DataSource dataSource,
                                PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.groups = groups;
        this.products = products;
        this.policies = policies;
        this.qty = qty;
        this.notices = notices;
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
            record(operatorId, AuditActions.GROUPBUY_CREATE, id, ip, Map.of("title", g.title(), "productId", Long.toString(g.productId()), "roundNo", 1));
            return id;
        });
    }

    /**
     * 공구 수정 — 공개 전 DRAFT 는 모든 칸, 공개(모집 시작) 뒤에는 공급가만(결정 89 — 돈 관련 감사, 판매가 &lt; 공급가면 {@code marginConfirmed} 필요).
     *
     * @throws GroupBuyException {@code VERSION_CONFLICT}(다른 사람이 먼저 고쳤다) · {@code LOCKED_FIELD}(잠긴 칸이 다르다) · {@code SUPPLY_PRICE_MISSING}(공급가를 비웠다)
     *                           · {@code NEGATIVE_MARGIN}
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
                groups.deleteSkus(id);
                insertSkus(id, v.plan());
                record(operatorId, AuditActions.GROUPBUY_UPDATE, id, ip, Map.of("stage", "DRAFT"));
                return;
            }
            List<SkuRow> saved = groups.skus(id);
            String locked = GroupBuySkuPlan.sameAs(v.plan(), saved) ? lockedFieldChanged(row, g) : "options";
            if (locked != null) {
                throw GroupBuyException.lockedField(locked);
            }
            if (!groups.touch(id, version, now)) {
                throw GroupBuyException.versionConflict();
            }
            changeSupplyPrices(row, saved, v.plan(), draft.marginConfirmed(), operatorId, ip);
        });
    }

    /** 모집 뒤 공급가 고침(결정 89) — 단품마다 돈 관련 감사. 비울 수 없고, 새 값이 판매가보다 크면 확인해야 한다 */
    private void changeSupplyPrices(GroupBuyRow row, List<SkuRow> saved, List<GroupBuySkuPlan.PlannedSku> plan, boolean marginConfirmed, Long operatorId, String ip) {
        Map<Long, GroupBuySkuPlan.PlannedSku> byProductSku = new java.util.HashMap<>();
        plan.forEach(p -> byProductSku.put(p.productSkuId(), p));
        List<String> missing = new java.util.ArrayList<>();
        List<String> negative = new java.util.ArrayList<>();
        List<SkuRow> changed = new java.util.ArrayList<>();
        for (SkuRow s : saved) {
            Long next = byProductSku.get(s.productSkuId()).supplyPrice();
            if (Objects.equals(next, s.supplyPrice())) {
                continue;
            }
            if (next == null) {
                missing.add(Long.toString(s.id()));
            } else if (next > s.unitPrice(row.groupPrice())) {
                negative.add(Long.toString(s.id()));
            }
            changed.add(s);
        }
        if (!missing.isEmpty()) {
            throw GroupBuyException.supplyPriceMissing(missing);
        }
        if (!negative.isEmpty() && !marginConfirmed) {
            throw GroupBuyException.negativeMargin(negative);
        }
        for (SkuRow s : changed) {
            Long next = byProductSku.get(s.productSkuId()).supplyPrice();
            groups.updateSupplyPrice(s.id(), row.id(), next);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("skuId", Long.toString(s.id()));
            detail.put("before", s.supplyPrice() == null ? "" : s.supplyPrice());
            detail.put("after", next);
            detail.put("marginConfirmed", negative.contains(Long.toString(s.id())));
            record(operatorId, AuditActions.GROUPBUY_SUPPLY_PRICE, row.id(), ip, detail);
        }
    }

    /**
     * 모집 시작(공개) — 기간 상한·배송비 정책을 확인한다. 시작 시각이 이미 됐으면 그 자리에서 RECRUITING.
     *
     * @throws GroupBuyException   {@code INVALID_STATE}(이미 공개) · {@code VERSION_CONFLICT} · {@code SHIPPING_POLICY_NOT_FOUND} · {@code NOTICE_INCOMPLETE}(상품정보제공고시 — 결정 87)
     * @throws ValidationException 기간이 상한을 넘거나 마감이 지났다
     */
    public void publish(long id, int version, Long operatorId, String ip) {
        publish(id, version, false, operatorId, ip);
    }

    /**
     * 모집 시작(공개) — {@code marginConfirmed}: 판매가 &lt; 공급가인 단품을 운영자가 확인했다(결정 89).
     *
     * @throws GroupBuyException {@code SUPPLY_PRICE_MISSING}(공급가가 빈 단품) · {@code NEGATIVE_MARGIN}(확인 전 역마진) 외 {@link #publish(long, int, Long, String)} 와 같다
     */
    public void publish(long id, int version, boolean marginConfirmed, Long operatorId, String ip) {
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
            List<SkuRow> skus = groups.skus(id);
            java.util.SortedSet<Long> productIds = new java.util.TreeSet<>();
            productIds.add(row.productId());
            for (SkuRow s : skus) {
                if (!s.main()) {
                    products.sku(s.productSkuId()).ifPresent(p -> productIds.add(p.productId()));
                }
            }
            productIds.forEach(p -> products.lock(p)); // 상품 구성 바꾸기와 하나만(결정 89 — 모집 중 상품 단품 구성 잠금)
            for (long productId : productIds) {
                notices.assertComplete(productId); // 상품정보제공고시 — 공구 상품과 추가 구성상품의 상품마다(결정 87·88·89)
            }
            List<String> missing = skus.stream().filter(s -> s.supplyPrice() == null).map(s -> Long.toString(s.id())).toList();
            if (!missing.isEmpty()) {
                throw GroupBuyException.supplyPriceMissing(missing); // 0 은 명시 입력만(결정 89)
            }
            List<String> negative = skus.stream().filter(s -> s.supplyPrice() > s.unitPrice(row.groupPrice())).map(s -> Long.toString(s.id())).toList();
            if (!negative.isEmpty() && !marginConfirmed) {
                throw GroupBuyException.negativeMargin(negative);
            }
            if (!groups.publish(id, version, now)) {
                throw GroupBuyException.versionConflict();
            }
            record(operatorId, AuditActions.GROUPBUY_START, id, ip, Map.of("startAt", row.startAt().toString(), "endAt", row.endAt().toString(), "negativeMarginSkus",
                    negative));
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
     * 추가 차수 공구(결정 31·89) — 같은 상품으로 새 DRAFT(판매 조건만 복사: 제목·단품 설정(추가금·처음 한도·판매 여부·공급가)·추가 구성상품·가격·방식·배송비 정책·
     * 차수 한도 합산). 상품에 새로 더한 단품은 상품 기본값으로 들어간다. 기간·발송 예정일은 새로 받는다.
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
                GroupBuySkuPlan.DraftParts parts = GroupBuySkuPlan.draftParts(groups.skus(sourceId));
                GroupBuyDraft draft = new GroupBuyDraft(source.productId(), source.title(), source.groupPrice(), source.shippingPolicyCode(), source.minQty(),
                        source.initialMaxQty() == null ? source.maxQty() : source.initialMaxQty(), source.maxQtyPerUser(), startAt, endAt, source.extensionMode(),
                        source.fulfillment(), shipPlannedAt, source.shipWithinBusinessDays(), source.returnPolicy(), source.roundLimitShared(), parts.skus(),
                        parts.addons(), false);
                Validated v = validate(draft, groups.settings());
                GroupBuyStore.NewGroupBuy g = v.group();
                long id = ids.nextId();
                groups.insert(id, g, root, roundNo, operatorId, now);
                insertSkus(id, v.plan()); // 설명·이미지·고시는 같은 상품 것(결정 89)
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
     * ({@link ParticipationCancelService}). 회원 앱·메일 통지는 이벤트 {@code DelayAnnounced} 를 받는 2b-4 {@code GroupBuyNotifications}.
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
            events.publishEvent(new GroupBuyEvents.DelayAnnounced(id, newPlannedAt, why));
        });
    }

    // ── 검사 ────────────────────────────────────────────────────────────────

    /** 등록 값 검사 — 칸마다 400. 즉시 발송 공구의 발송 영업일이 비면 공구 설정 기본값 */
    Validated validate(GroupBuyDraft d, GroupBuyStore.Settings settings) {
        Map<String, MessageRef> f = new LinkedHashMap<>();
        Optional<ProductRow> product = products.find(d.productId());
        if (product.isEmpty()) {
            f.put("productId", new MessageRef("groupbuy.admin.product.invalid"));
        }
        if (blankOrLonger(d.title(), TITLE_MAX)) {
            f.put("title", new MessageRef("groupbuy.admin.title.invalid"));
        }
        if (d.groupPrice() < 1) {
            f.put("groupPrice", new MessageRef("groupbuy.admin.price.invalid"));
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
        if (longer(d.returnPolicy(), 2000)) {
            f.put("returnPolicy", new MessageRef("groupbuy.admin.text.tooLong"));
        }
        // 공구 단품·추가 구성상품(결정 88·89)
        List<GroupBuySkuPlan.PlannedSku> plan = product.isEmpty() ? null : GroupBuySkuPlan.group(d, d.productId(), products.skus(d.productId()),
                skuId -> products.sku(skuId).flatMap(s -> products.find(s.productId()).map(p -> new GroupBuySkuPlan.AddonSource(s, p.name(),
                        products.skus(p.id()).size() == 1))), f);
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
        return new Validated(new GroupBuyStore.NewGroupBuy(d.productId(), d.title().strip(), d.groupPrice(), d.shippingPolicyCode(), d.minQty(), d.maxQty(),
                d.maxQtyPerUser(), d.startAt().truncatedTo(ChronoUnit.MICROS), d.endAt().truncatedTo(ChronoUnit.MICROS), extension, d.fulfillment(),
                Fulfillment.AFTER_CLOSE.name().equals(d.fulfillment()) ? d.shipPlannedAt() : null, shipDays, trimToNull(d.returnPolicy()), d.roundLimitShared()), plan);
    }

    /** 공개 뒤 잠긴 칸 중 바뀐 칸 이름(없으면 null) — 상품·가격·기간·배송비·방식·수량(수량은 추가 입고·줄이기로만). 단품 구성은 {@link GroupBuySkuPlan#sameAs} */
    static String lockedFieldChanged(GroupBuyRow row, GroupBuyStore.NewGroupBuy g) {
        if (row.productId() != g.productId()) return "productId";
        if (!row.title().equals(g.title())) return "title";
        if (row.groupPrice() != g.groupPrice()) return "groupPrice";
        if (!row.shippingPolicyCode().equals(g.shippingPolicyCode())) return "shippingPolicyCode";
        if (row.minQty() != g.minQty()) return "minQty";
        if ((row.initialMaxQty() == null ? row.maxQty() : row.initialMaxQty()) != g.maxQty() && row.maxQty() != g.maxQty()) return "maxQty";
        if (!Objects.equals(row.maxQtyPerUser(), g.maxQtyPerUser())) return "maxQtyPerUser";
        if (!row.startAt().equals(g.startAt()) || !row.endAt().equals(g.endAt())) return "period";
        if (!row.extensionMode().equals(g.extensionMode())) return "extensionMode";
        if (!row.fulfillment().equals(g.fulfillment())) return "fulfillment";
        if (!Objects.equals(row.shipPlannedAt(), g.shipPlannedAt()) || !Objects.equals(row.shipWithinBusinessDays(), g.shipWithinBusinessDays())) return "shipPlan";
        if (!Objects.equals(row.returnPolicy(), g.returnPolicy())) return "returnPolicy";
        if (row.roundLimitShared() != g.roundLimitShared()) return "roundLimitShared";
        return null;
    }

    /** 검사를 마친 등록 값 — 공구 칸과 공구 단품 계획 */
    record Validated(GroupBuyStore.NewGroupBuy group, List<GroupBuySkuPlan.PlannedSku> plan) {
    }

    /** 공구 단품을 넣는다 */
    private void insertSkus(long groupBuyId, List<GroupBuySkuPlan.PlannedSku> plan) {
        for (int i = 0; i < plan.size(); i++) {
            GroupBuySkuPlan.PlannedSku s = plan.get(i);
            groups.insertSku(ids.nextId(), groupBuyId, s.kind(), s.productSkuId(), s.valueIds(), s.name(), s.extraPrice(), s.addonPrice(), s.maxQty(), s.onSale(),
                    s.supplyPrice(), i);
        }
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
