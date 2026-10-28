package com.community.groupbuy.service;

import com.community.commerce.image.GroupBuyImageUploader;
import com.community.groupbuy.dto.CancelSummary;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.RoundCreated;
import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyAdminView;
import com.community.groupbuy.entity.Fulfillment;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyNoticeStore;
import com.community.groupbuy.repository.master.GroupBuyProductStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.repository.master.ShippingPolicyStore;
import com.community.media.service.ImageStorage;
import com.community.payment.dto.response.PaymentMemberView;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 공구·수량 화면(admin-web {@code useGroupBuyAdminApi}) — 업무는 shared {@link GroupBuyAdminService}·{@link FulfillmentService}·{@link GroupBuySupplyService} 가 하고
 * 여기는 화면 모양과 권한별 칸(공급가는 {@code commerce.product.price}·{@code commerce.settlement.read} 만 — 결정 89)을 맡는다.
 */
@Service
public class GroupBuyAdminFacade {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<GroupBuyStatus> CANCELABLE = EnumSet.of(GroupBuyStatus.RECRUITING, GroupBuyStatus.CLOSING, GroupBuyStatus.CLOSED_SUCCESS,
            GroupBuyStatus.ORDERED, GroupBuyStatus.SHIPPING);
    private static final Set<GroupBuyStatus> DELAY_BLOCKED = EnumSet.of(GroupBuyStatus.DRAFT, GroupBuyStatus.CANCELED, GroupBuyStatus.CLOSED_FAILED,
            GroupBuyStatus.COMPLETED);

    private final JdbcTemplate jdbc;
    private final GroupBuyStore groups;
    private final GroupBuyProductStore products;
    private final GroupBuyNoticeStore noticeStore;
    private final ShippingPolicyStore policies;
    private final GroupBuyAdminService admin;
    private final FulfillmentService fulfillment;
    private final GroupBuySupplyService supply;
    private final GroupBuyNoticeCatalog catalog;
    private final GroupBuyImageUploader uploader;
    private final ImageStorage storage;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyAdminFacade(DataSource dataSource, GroupBuyStore groups, GroupBuyProductStore products, GroupBuyNoticeStore noticeStore, ShippingPolicyStore policies,
                               GroupBuyAdminService admin, FulfillmentService fulfillment, GroupBuySupplyService supply, GroupBuyNoticeCatalog catalog,
                               GroupBuyImageUploader uploader, ImageStorage storage, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.groups = groups;
        this.products = products;
        this.noticeStore = noticeStore;
        this.policies = policies;
        this.admin = admin;
        this.fulfillment = fulfillment;
        this.supply = supply;
        this.catalog = catalog;
        this.uploader = uploader;
        this.storage = storage;
        this.clocks = clocks;
    }

    // ── 목록·폼·카탈로그 ────────────────────────────────────────────────────

    public GroupBuyAdminView.ListResponse list(String status, String q, int page, int size) {
        int s = Math.min(100, Math.max(1, size));
        int p = Math.max(0, page);
        StringBuilder where = new StringBuilder(" FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND (g.title ILIKE ? OR p.name ILIKE ? OR g.id::text = ?)");
            String like = "%" + q.strip().replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
            args.add(q.strip());
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (GroupBuyStatus st : GroupBuyStatus.values()) {
            counts.put(st.name(), 0L);
        }
        jdbc.query("SELECT g.status, count(*)" + where + " GROUP BY g.status", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        }, args.toArray());
        if (status != null && !status.isBlank()) {
            where.append(" AND g.status = ?");
            args.add(AdminIds.value(GroupBuyStatus.class, status, "status").name());
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add(p * s);
        List<Long> ids = jdbc.queryForList("SELECT g.id" + where + " ORDER BY g.updated_at DESC, g.id DESC LIMIT ? OFFSET ?", Long.class, pageArgs.toArray());
        List<GroupBuyAdminView.Row> rows = new ArrayList<>();
        for (long id : ids) {
            groups.find(id).ifPresent(g -> rows.add(row(g)));
        }
        return new GroupBuyAdminView.ListResponse(rows, total == null ? 0 : total, counts);
    }

    public GroupBuyAdminView.FormOptions formOptions() {
        Instant now = now();
        Map<String, List<ShippingPolicyStore.PolicyRow>> byCode = new LinkedHashMap<>();
        policies.all().forEach(r -> byCode.computeIfAbsent(r.code(), k -> new ArrayList<>()).add(r));
        List<GroupBuyAdminView.PolicyChoice> choices = new ArrayList<>();
        byCode.forEach((code, rows) -> {
            ShippingPolicyStore.PolicyRow current = rows.stream().filter(r -> !r.effectiveFrom().isAfter(now) && (r.effectiveTo() == null || r.effectiveTo().isAfter(now)))
                    .findFirst().orElse(null);
            ShippingPolicyStore.PolicyRow upcoming = rows.stream().filter(r -> r.effectiveFrom().isAfter(now)).findFirst().orElse(null);
            String name = (current != null ? current : rows.get(rows.size() - 1)).name();
            choices.add(new GroupBuyAdminView.PolicyChoice(code, name, line(current), line(upcoming)));
        });
        GroupBuyStore.Settings s = groups.settings();
        return new GroupBuyAdminView.FormOptions(choices, s.shipWithinBusinessDays(), new GroupBuyAdminView.PeriodMaxDays(s.demandMaxDays(), s.firstComeMaxDays()));
    }

    /** 고시 카탈로그 — 서버 파일 모양 그대로(지금 버전) */
    public JsonNode noticeCatalog() {
        ClassPathResource file = new ClassPathResource("groupbuy/notice-catalog-" + catalog.current().version() + ".json");
        try (InputStream in = file.getInputStream()) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("고시 카탈로그를 읽지 못했다", e);
        }
    }

    public GroupBuyAdminView.ImageUpload uploadImage(MultipartFile file) {
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        GroupBuyImageUploader.Uploaded up = uploader.upload("product", data);
        return new GroupBuyAdminView.ImageUpload(up.key(), up.url(), up.thumbUrl(), up.width(), up.height());
    }

    // ── 상세·등록·수정 ──────────────────────────────────────────────────────

    public GroupBuyAdminView.Detail detail(AdminActor actor, String idText) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        GroupBuyRow g = groups.find(id).orElseThrow(GroupBuyException::notFound);
        boolean showSupply = actor.root() || actor.can(Permission.COMMERCE_PRODUCT_PRICE) || actor.can(Permission.COMMERCE_SETTLEMENT_READ);
        List<SkuRow> skus = groups.skus(id);
        Map<Long, Long> productSkuToGroupSku = new HashMap<>();
        skus.forEach(s -> productSkuToGroupSku.put(s.productSkuId(), s.id()));
        List<GroupBuyAdminView.RoundRef> rounds = jdbc.query("SELECT g.id, g.round_no, g.status, COALESCE(NULLIF(g.title, ''), p.name) FROM group_buys g "
                + "JOIN group_buy_products p ON p.id = g.product_id WHERE g.round_root_id = ? ORDER BY g.round_no", (rs, i) -> new GroupBuyAdminView.RoundRef(
                Long.toString(rs.getLong(1)), rs.getInt(2), rs.getString(3), rs.getString(4)), g.roundRootId());
        boolean roundOpen = groups.roundOpen(g.roundRootId());
        boolean roundSource = GroupBuyStatus.ROUND_SOURCE.contains(g.status());
        GroupBuyAdminView.Actions actions = new GroupBuyAdminView.Actions(g.status() == GroupBuyStatus.DRAFT && g.publishedAt() == null,
                g.status() == GroupBuyStatus.RECRUITING, CANCELABLE.contains(g.status()),
                Fulfillment.AFTER_CLOSE.name().equals(g.fulfillment()) && g.status() == GroupBuyStatus.CLOSED_SUCCESS, roundSource && !roundOpen,
                !roundSource ? "SOURCE_INVALID" : roundOpen ? "ROUND_EXISTS" : null, !DELAY_BLOCKED.contains(g.status()), CANCELABLE.contains(g.status()),
                g.status() == GroupBuyStatus.RECRUITING && (actor.root() || actor.can(Permission.COMMERCE_STOCK_ADJUST)));
        Integer preShip = jdbc.queryForObject("SELECT count(*) FROM group_buy_participations WHERE group_buy_id = ? AND status IN ('JOINED', 'CONFIRMED', 'PREPARING')",
                Integer.class, id);
        String createdBy = g.createdBy() == null ? "" : jdbc.query("SELECT operator_name FROM operators WHERE id = ?", (rs, i) -> rs.getString(1), g.createdBy()).stream()
                .findFirst().orElse("");
        return new GroupBuyAdminView.Detail(Long.toString(g.id()), g.status().name(), g.closeReason(), g.roundNo(), Long.toString(g.roundRootId()), rounds, g.title(),
                Long.toString(g.productId()), g.productName(), categoryPath(g.categoryId()), nz(g.summary()), nz(g.spec()), nz(g.description()), images(g.productId()),
                g.normalPrice() == null ? 0 : g.normalPrice(), g.groupPrice(), g.normalPriceBasis(), nz(g.normalPriceSource()), axes(groups.axes(id)),
                skus.stream().map(s -> sku(g, s, showSupply)).toList(), g.minQty(), g.fulfillment(), g.maxQty(), g.initialMaxQty(), g.addedQty(), g.currentQty(),
                g.reservedQty(), g.maxQtyPerUser(), g.roundLimitShared(), g.startAt(), g.endAt(), g.extensionMode(), g.extendedUntil(), g.closingAt(),
                g.shippingPolicyCode(), g.shippingFee() == null ? null : new GroupBuyAdminView.ShippingSnapshot(g.shippingFee(), g.freeShippingOver(),
                g.returnShippingFee() == null ? 0 : g.returnShippingFee(), g.returnFeeMode()), g.shipPlannedAt(), g.shipWithinBusinessDays(),
                nz(g.withdrawalRestriction()), preShip == null ? 0 : preShip, g.version(), g.revisionNo(), notices(g, productSkuToGroupSku), actions, delayNotices(g),
                supplyFailures(g, skus), createdBy, g.createdAt(), g.updatedAt());
    }

    public GroupBuyAdminView.Detail create(AdminActor actor, GroupBuyAdminRequest.Save body) {
        long id = admin.create(draft(body), actor.operatorId(), actor.ip());
        return detail(actor, Long.toString(id));
    }

    public GroupBuyAdminView.Detail update(AdminActor actor, String idText, GroupBuyAdminRequest.Save body) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        admin.update(id, body == null || body.version() == null ? -1 : body.version(), draft(body), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    /** 모집 뒤 고칠 수 있는 것 = 공급가만(결정 89) — 지금 판매 조건에 공급가만 바꿔 같은 수정 길로 보낸다(잠긴 칸은 그대로라 통과) */
    public GroupBuyAdminView.Detail content(AdminActor actor, String idText, GroupBuyAdminRequest.Content body) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        GroupBuyRow g = groups.find(id).orElseThrow(GroupBuyException::notFound);
        Map<Long, Long> prices = new HashMap<>();
        if (body != null && body.supplyPrices() != null) {
            for (GroupBuyAdminRequest.SupplyPrice sp : body.supplyPrices()) {
                prices.put(AdminIds.field(sp.skuId(), "supplyPrices"), sp.supplyPrice());
            }
        }
        List<GroupBuyDraft.SkuDraft> mains = new ArrayList<>();
        List<GroupBuyDraft.AddonDraft> addons = new ArrayList<>();
        for (SkuRow s : groups.skus(id)) {
            Long price = prices.containsKey(s.id()) ? prices.get(s.id()) : s.supplyPrice();
            if (s.main()) {
                mains.add(new GroupBuyDraft.SkuDraft(s.productSkuId(), s.extraPrice(), s.initialMaxQty() != null ? s.initialMaxQty() : s.maxQty(), s.onSale(), price));
            } else {
                addons.add(new GroupBuyDraft.AddonDraft(s.productSkuId(), s.addonPrice(), s.initialMaxQty() != null ? s.initialMaxQty() : s.maxQty(), price));
            }
        }
        GroupBuyDraft d = new GroupBuyDraft(g.productId(), g.title(), g.groupPrice(), g.shippingPolicyCode(), g.minQty(),
                g.initialMaxQty() == null ? g.maxQty() : g.initialMaxQty(), g.maxQtyPerUser(), g.startAt(), g.endAt(), g.extensionMode(), g.fulfillment(), g.shipPlannedAt(),
                g.shipWithinBusinessDays(), g.returnPolicy(), g.roundLimitShared(), mains, addons, body != null && Boolean.TRUE.equals(body.marginConfirmed()));
        admin.update(id, body == null || body.version() == null ? -1 : body.version(), d, actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    public GroupBuyAdminView.Page<GroupBuyAdminView.Revision> revisions(String idText, int page, int size) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        int s = Math.min(50, Math.max(1, size));
        int p = Math.max(0, page);
        Long total = jdbc.queryForObject("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ?", Long.class, id);
        List<GroupBuyAdminView.Revision> items = jdbc.query("SELECT r.revision_no, r.change_kind, r.title, r.summary, r.description, r.images::text, r.max_qty, r.added_qty, "
                + "r.skus::text, r.notices::text, o.operator_name, r.created_at, (SELECT g.ship_planned_at FROM group_buys g WHERE g.id = r.group_buy_id) "
                + "FROM group_buy_revisions r LEFT JOIN operators o ON o.id = r.edited_by WHERE r.group_buy_id = ? ORDER BY r.revision_no DESC, r.created_at DESC "
                + "LIMIT ? OFFSET ?", (rs, i) -> revision(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
                rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getTimestamp(12).toInstant(),
                rs.getDate(13) == null ? null : rs.getDate(13).toLocalDate()), id, s, p * s);
        return new GroupBuyAdminView.Page<>(items, total == null ? 0 : total);
    }

    // ── 운영 동작 ───────────────────────────────────────────────────────────

    public GroupBuyAdminView.Detail start(AdminActor actor, String idText, GroupBuyAdminRequest.Start body) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        GroupBuyRow g = groups.find(id).orElseThrow(GroupBuyException::notFound);
        admin.publish(id, g.version(), body != null && Boolean.TRUE.equals(body.marginConfirmed()), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    public GroupBuyAdminView.Detail closeEarly(AdminActor actor, String idText, GroupBuyAdminRequest.Reason body) {
        admin.closeEarly(AdminIds.parse(idText, GroupBuyException::notFound), body == null ? null : body.reason(), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    public GroupBuyAdminView.CancelPreview cancelPreview(String idText) {
        return cancelView(admin.cancelPreview(AdminIds.parse(idText, GroupBuyException::notFound)));
    }

    public GroupBuyAdminView.Detail cancel(AdminActor actor, String idText, GroupBuyAdminRequest.Reason body) {
        admin.cancel(AdminIds.parse(idText, GroupBuyException::notFound), body == null ? null : body.reason(), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    public GroupBuyAdminView.Detail ordered(AdminActor actor, String idText) {
        fulfillment.markOrdered(AdminIds.parse(idText, GroupBuyException::notFound), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    /**
     * 추가 차수 — 새 DRAFT. 화면은 기간을 보내지 않으므로 처음 값으로 내일 같은 시각 시작 · 7일 모집 · (마감 후 발주면) 마감 7일 뒤 발송 예정을 넣는다(DRAFT 라 고칠 수 있다 — 가정).
     */
    public GroupBuyAdminView.RoundCreated round(AdminActor actor, String idText) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        Instant start = now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofDays(7));
        LocalDate ship = ServiceTime.dateOf(end).plusDays(7);
        RoundCreated r = admin.createRound(id, start, end, ship, actor.operatorId(), actor.ip());
        return new GroupBuyAdminView.RoundCreated(Long.toString(r.groupBuyId()), r.roundNo());
    }

    public GroupBuyAdminView.Detail delayNotice(AdminActor actor, String idText, GroupBuyAdminRequest.DelayNotice body) {
        admin.announceDelay(AdminIds.parse(idText, GroupBuyException::notFound), body == null ? null : body.newPlannedAt(), body == null ? null : body.reason(),
                actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    /** 공급 불가 미리 보기 — 그 단품의 발송 전 줄·환불액·전액 환불 주문·선택을 묻는 주문(서비스와 같은 판정, 바꾸지 않는다) */
    public GroupBuyAdminView.SupplyFailurePreview supplyFailurePreview(String idText, String skuIdText) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        long skuId = AdminIds.field(skuIdText, "skuId");
        SkuRow sku = groups.skus(id).stream().filter(s -> s.id() == skuId).findFirst().orElseThrow(GroupBuyException::notFound);
        int cancelLines = 0;
        long refund = 0;
        int full = 0;
        int choice = 0;
        for (Map<String, Object> o : jdbc.queryForList("SELECT p.id, p.cash_amount - p.refunded_amount - p.compensation_amount AS remaining FROM group_buy_participations p "
                + "WHERE p.group_buy_id = ? AND p.status IN ('JOINED', 'CONFIRMED', 'PREPARING') AND EXISTS (SELECT 1 FROM group_buy_participation_items i "
                + "WHERE i.participation_id = p.id AND i.sku_id = ? AND i.qty - i.canceled_qty - i.returned_qty > 0)", id, skuId)) {
            long pid = ((Number) o.get("id")).longValue();
            List<Map<String, Object>> lines = jdbc.queryForList("SELECT i.sku_id, s.kind, i.qty - i.canceled_qty - i.returned_qty AS held, i.unit_price "
                    + "FROM group_buy_participation_items i JOIN group_buy_skus s ON s.id = i.sku_id WHERE i.participation_id = ?", pid);
            cancelLines++;
            boolean mainLeft = lines.stream().anyMatch(l -> ((Number) l.get("sku_id")).longValue() != skuId && "MAIN".equals(l.get("kind"))
                    && ((Number) l.get("held")).intValue() > 0);
            if (!mainLeft) {
                full++;
                refund += ((Number) o.get("remaining")).longValue();
            } else {
                choice++;
                refund += lines.stream().filter(l -> ((Number) l.get("sku_id")).longValue() == skuId)
                        .mapToLong(l -> ((Number) l.get("held")).longValue() * ((Number) l.get("unit_price")).longValue()).sum();
            }
        }
        Instant deadline = GroupBuyBusinessDays.endOfBusinessDays(now(), groups.settings().supplyChoiceBusinessDays(), groups.settings().holidays());
        return new GroupBuyAdminView.SupplyFailurePreview(Long.toString(skuId), sku.name(), sku.kind().name(), cancelLines, refund, full, choice, deadline);
    }

    public GroupBuyAdminView.Detail supplyFailure(AdminActor actor, String idText, GroupBuyAdminRequest.SupplyFailure body) {
        supply.markFailure(AdminIds.parse(idText, GroupBuyException::notFound), AdminIds.field(body == null ? null : body.skuId(), "skuId"),
                body == null ? null : body.reason(), actor.operatorId(), actor.ip());
        return detail(actor, idText);
    }

    // ── 수량 ────────────────────────────────────────────────────────────────

    public GroupBuyAdminView.Stock stock(AdminActor actor, String idText) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        GroupBuyRow g = groups.find(id).orElseThrow(GroupBuyException::notFound);
        List<SkuRow> skus = groups.skus(id);
        Map<Long, int[]> returned = new HashMap<>();
        jdbc.query("SELECT sku_id, sum(returned_qty), sum(restocked_qty) FROM group_buy_participation_items WHERE group_buy_id = ? GROUP BY sku_id", rs -> {
            returned.put(rs.getLong(1), new int[] {rs.getInt(2), rs.getInt(3)});
        }, id);
        int totalReturned = skus.stream().filter(SkuRow::main).mapToInt(s -> returned.getOrDefault(s.id(), new int[2])[0]).sum();
        int totalRestocked = skus.stream().filter(SkuRow::main).mapToInt(s -> returned.getOrDefault(s.id(), new int[2])[1]).sum();
        int groupRemaining = Math.max(0, g.maxQty() - g.currentQty() - g.reservedQty());
        int floor = Math.max(g.currentQty() + g.reservedQty(), g.minQty());
        GroupBuyAdminView.StockLine total = new GroupBuyAdminView.StockLine(null, null, GroupBuyMemberLikeNames.title(g), true, g.maxQty(), g.initialMaxQty(), g.addedQty(),
                g.currentQty(), g.reservedQty(), groupRemaining, totalReturned, totalRestocked, g.currentQty() >= g.maxQty(), g.currentQty() + g.reservedQty() >= g.maxQty(),
                floor);
        List<GroupBuyAdminView.StockLine> lines = skus.stream().map(s -> {
            int[] r = returned.getOrDefault(s.id(), new int[2]);
            Integer max = s.maxQty();
            int skuRemaining = max == null ? Integer.MAX_VALUE : Math.max(0, max - s.currentQty() - s.reservedQty());
            int remaining = s.main() ? Math.min(skuRemaining, groupRemaining) : (max == null ? -1 : skuRemaining);
            int added = max == null || s.initialMaxQty() == null ? 0 : Math.max(0, max - s.initialMaxQty());
            return new GroupBuyAdminView.StockLine(Long.toString(s.id()), s.kind().name(), s.name(), s.onSale(), max, s.initialMaxQty(), added, s.currentQty(),
                    s.reservedQty(), remaining, r[0], r[1], max != null && s.currentQty() >= max, max != null && s.currentQty() + s.reservedQty() >= max,
                    s.currentQty() + s.reservedQty());
        }).toList();
        Instant now = now();
        List<GroupBuyAdminView.Hold> holds = jdbc.query("SELECT pa.order_no, pa.user_id, pr.nickname, (u.id IS NULL OR u.status = 'WITHDRAWN' OR u.deleted_at IS NOT NULL), "
                + "pa.reserved_until, (SELECT string_agg(s.name || ' × ' || i.qty, ', ' ORDER BY s.kind DESC, s.sort_order) FROM group_buy_participation_items i "
                + "JOIN group_buy_skus s ON s.id = i.sku_id WHERE i.participation_id = pa.id), (SELECT sum(i.qty) FROM group_buy_participation_items i "
                + "WHERE i.participation_id = pa.id) FROM group_buy_participations pa LEFT JOIN users u ON u.id = pa.user_id LEFT JOIN profiles pr ON pr.user_id = pa.user_id "
                + "WHERE pa.group_buy_id = ? AND pa.status = 'PENDING_PAYMENT' AND pa.reserved_until > ? ORDER BY pa.reserved_until", (rs, i) -> new GroupBuyAdminView.Hold(
                rs.getString(1), member(rs.getLong(2), rs.getString(3), rs.getBoolean(4)), nz(rs.getString(6)), rs.getInt(7), rs.getTimestamp(5).toInstant()),
                id, Timestamp.from(now));
        return new GroupBuyAdminView.Stock(new GroupBuyAdminView.StockGroupBuy(Long.toString(g.id()), GroupBuyMemberLikeNames.title(g), g.roundNo(), g.status().name(),
                g.minQty(), g.fulfillment()), total, lines, holds, g.status() == GroupBuyStatus.RECRUITING && (actor.root() || actor.can(Permission.COMMERCE_STOCK_ADJUST)));
    }

    public GroupBuyAdminView.Page<GroupBuyAdminView.Movement> movements(String idText, String kind, int page, int size) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        int s = Math.min(100, Math.max(1, size));
        int p = Math.max(0, page);
        StringBuilder where = new StringBuilder(" FROM group_buy_qty_movements m LEFT JOIN group_buy_skus s ON s.id = m.sku_id LEFT JOIN group_buy_participations pa "
                + "ON pa.id = m.participation_id LEFT JOIN operators o ON o.id = m.actor_id WHERE m.group_buy_id = ?");
        List<Object> args = new ArrayList<>(List.of(id));
        if (kind != null && !kind.isBlank()) {
            where.append(" AND m.kind = ?");
            args.add(kind);
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        args.add(s);
        args.add(p * s);
        GroupBuyRow g = groups.find(id).orElseThrow(GroupBuyException::notFound);
        List<GroupBuyAdminView.Movement> items = jdbc.query("SELECT m.id, m.kind, s.name, s.kind, m.qty, m.current_after, m.reserved_after, m.max_after, m.sku_current_after, "
                + "m.sku_reserved_after, m.sku_max_after, pa.order_no, o.operator_name, m.reason, m.created_at, m.actor_id" + where
                + " ORDER BY m.id DESC LIMIT ? OFFSET ?", (rs, i) -> new GroupBuyAdminView.Movement(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getObject(6) == null ? g.currentQty() : rs.getInt(6), rs.getObject(7) == null ? g.reservedQty() : rs.getInt(7),
                rs.getObject(8) == null ? g.maxQty() : rs.getInt(8), (Integer) rs.getObject(9), (Integer) rs.getObject(10), (Integer) rs.getObject(11), rs.getString(12),
                rs.getString(13) != null ? rs.getString(13) : rs.getObject(16) == null ? "SYSTEM" : "MEMBER", rs.getString(14), rs.getTimestamp(15).toInstant()),
                args.toArray());
        return new GroupBuyAdminView.Page<>(items, total == null ? 0 : total);
    }

    public GroupBuyAdminView.Stock adjust(AdminActor actor, String idText, GroupBuyAdminRequest.StockAdjust body) {
        long id = AdminIds.parse(idText, GroupBuyException::notFound);
        GroupBuyAdminRequest.StockAdjust b = body == null ? new GroupBuyAdminRequest.StockAdjust(null, null, null, null) : body;
        Long skuId = AdminIds.optional(b.skuId(), "skuId");
        int qty = b.qty() == null ? 0 : b.qty();
        if ("ADD".equals(b.direction())) {
            admin.addStock(id, skuId, qty, b.reason(), actor.operatorId(), actor.ip());
        } else if ("REDUCE".equals(b.direction())) {
            admin.reduceStock(id, skuId, qty, b.reason(), actor.operatorId(), actor.ip());
        } else {
            throw ValidationException.of("direction", new MessageRef("groupbuy.value.invalid"));
        }
        return stock(actor, idText);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 공구 이름 = 공구 제목(없으면 상품 이름) */
    static final class GroupBuyMemberLikeNames {
        static String title(GroupBuyRow g) {
            return g.title() == null || g.title().isBlank() ? g.productName() : g.title();
        }
    }

    private GroupBuyAdminView.Row row(GroupBuyRow g) {
        Integer orders = jdbc.queryForObject("SELECT count(*) FROM group_buy_participations WHERE group_buy_id = ? AND status <> 'EXPIRED'", Integer.class, g.id());
        List<GroupBuyStore.ImageRow> images = products.images(g.productId());
        return new GroupBuyAdminView.Row(Long.toString(g.id()), GroupBuyMemberLikeNames.title(g), g.productName(), g.roundNo(), Long.toString(g.roundRootId()),
                g.status().name(), g.closeReason(), g.fulfillment(), g.minQty(), g.maxQty(), g.currentQty(), g.reservedQty(), g.groupPrice(), g.startAt(), g.endAt(),
                g.extendedUntil(), images.isEmpty() ? null : storage.publicUrl(thumb(images.get(0))), orders == null ? 0 : orders, g.updatedAt());
    }

    static String thumb(GroupBuyStore.ImageRow i) {
        return i.thumbKey() == null ? i.imageKey() : i.thumbKey();
    }

    List<GroupBuyAdminView.Image> images(long productId) {
        return products.images(productId).stream().map(i -> new GroupBuyAdminView.Image(i.imageKey(), storage.publicUrl(i.imageKey()), storage.publicUrl(thumb(i)))).toList();
    }

    String categoryPath(long categoryId) {
        return jdbc.query("SELECT CASE WHEN pc.name IS NULL THEN c.name ELSE pc.name || ' › ' || c.name END FROM group_buy_categories c "
                + "LEFT JOIN group_buy_categories pc ON pc.id = c.parent_id WHERE c.id = ?", (rs, i) -> rs.getString(1), categoryId).stream().findFirst().orElse("");
    }

    static List<GroupBuyAdminView.OptionAxis> axes(List<GroupBuyStore.AxisRow> axes) {
        return axes.stream().map(a -> new GroupBuyAdminView.OptionAxis(Long.toString(a.id()), a.name(), a.sortOrder(), a.values().stream()
                .map(v -> new GroupBuyAdminView.OptionValue(Long.toString(v.id()), v.value(), v.sortOrder())).toList())).toList();
    }

    private GroupBuyAdminView.Sku sku(GroupBuyRow g, SkuRow s, boolean showSupply) {
        return new GroupBuyAdminView.Sku(Long.toString(s.id()), s.kind().name(), s.valueIds().stream().map(String::valueOf).toList(), s.name(), s.extraPrice(),
                s.unitPrice(g.groupPrice()), s.maxQty(), s.initialMaxQty(), s.currentQty(), s.reservedQty(), s.onSale(), null, null, s.sortOrder(),
                Long.toString(s.productSkuId()), showSupply ? s.supplyPrice() : null);
    }

    /** 공구의 고시(상품 + 추가 구성상품의 상품) — 상품 단품 ID 를 공구 단품 ID 로(없는 단품은 상품 단품 ID 그대로) */
    private List<GroupBuyAdminView.Notice> notices(GroupBuyRow g, Map<Long, Long> productSkuToGroupSku) {
        List<Long> productIds = new ArrayList<>(List.of(g.productId()));
        jdbc.queryForList("SELECT DISTINCT ps.product_id FROM group_buy_skus s JOIN group_buy_product_skus ps ON ps.id = s.product_sku_id WHERE s.group_buy_id = ? "
                + "AND s.kind = 'ADDON'", Long.class, g.id()).forEach(p -> {
            if (!productIds.contains(p)) {
                productIds.add(p);
            }
        });
        List<GroupBuyAdminView.Notice> out = new ArrayList<>();
        for (long productId : productIds) {
            noticeStore.notices(productId).forEach(n -> out.add(notice(n, productSkuToGroupSku)));
        }
        return out;
    }

    static GroupBuyAdminView.Notice notice(GroupBuyNoticeStore.NoticeRow n, Map<Long, Long> skuMap) {
        return new GroupBuyAdminView.Notice(n.categoryCode(), n.title(), n.skuIds().stream().map(id -> Long.toString(skuMap.getOrDefault(id, id))).toList(), n.skuNames(),
                n.values().values().stream().map(v -> new GroupBuyAdminView.NoticeValue(v.itemKey(), v.value() == null ? "" : v.value(), v.unavailable(),
                        v.unavailableReason() == null ? "" : v.unavailableReason())).toList(),
                n.customItems().stream().map(c -> new GroupBuyAdminView.NoticeCustomItem(c.name(), c.value(), c.sortOrder())).toList());
    }

    private List<GroupBuyAdminView.DelayNotice> delayNotices(GroupBuyRow g) {
        return jdbc.query("SELECT n.id, n.previous_planned_at, n.new_planned_at, n.reason, o.operator_name, n.created_at, (SELECT count(*) FROM group_buy_participations p "
                + "WHERE p.group_buy_id = n.group_buy_id AND p.joined_at <= n.created_at AND p.status IN ('JOINED', 'CONFIRMED', 'PREPARING', 'CANCELED', 'REFUNDED')) "
                + "FROM group_buy_delay_notices n LEFT JOIN operators o ON o.id = n.operator_id WHERE n.group_buy_id = ? ORDER BY n.created_at DESC",
                (rs, i) -> new GroupBuyAdminView.DelayNotice(Long.toString(rs.getLong(1)), rs.getDate(2) == null ? null : rs.getDate(2).toLocalDate(),
                        rs.getDate(3).toLocalDate(), rs.getString(4), rs.getInt(7), nz(rs.getString(5)), rs.getTimestamp(6).toInstant()), g.id());
    }

    private List<GroupBuyAdminView.SupplyFailure> supplyFailures(GroupBuyRow g, List<SkuRow> skus) {
        Map<Long, SkuRow> byId = new HashMap<>();
        skus.forEach(s -> byId.put(s.id(), s));
        return jdbc.query("SELECT f.id, f.sku_id, f.reason, f.choice_until, o.operator_name, f.created_at, "
                + "(SELECT count(*) FROM group_buy_participation_items i WHERE i.sku_id = f.sku_id AND i.canceled_qty > 0), "
                + "(SELECT COALESCE(sum(r.amount), 0) FROM group_buy_refunds r WHERE r.group_buy_id = f.group_buy_id AND r.cause = 'SUPPLY_FAILURE'), "
                + "(SELECT count(*) FROM group_buy_supply_choices c WHERE c.supply_failure_id = f.id), "
                + "(SELECT count(*) FROM group_buy_supply_choices c WHERE c.supply_failure_id = f.id AND c.choice IS NULL) "
                + "FROM group_buy_supply_failures f LEFT JOIN operators o ON o.id = f.operator_id WHERE f.group_buy_id = ? ORDER BY f.created_at DESC", (rs, i) -> {
            SkuRow s = byId.get(rs.getLong(2));
            return new GroupBuyAdminView.SupplyFailure(Long.toString(rs.getLong(1)), Long.toString(rs.getLong(2)), s == null ? "" : s.name(),
                    s == null ? "MAIN" : s.kind().name(), rs.getString(3), rs.getInt(7), rs.getLong(8), rs.getInt(9), rs.getInt(10), rs.getTimestamp(4).toInstant(),
                    nz(rs.getString(5)), rs.getTimestamp(6).toInstant());
        }, g.id());
    }

    private GroupBuyAdminView.Revision revision(int no, String kind, String title, String summary, String description, String imagesJson, int maxQty, int addedQty,
                                                String skusJson, String noticesJson, String operator, Instant at, LocalDate shipPlannedAt) {
        String screenKind = switch (kind) {
            case "STOCK_ADD", "STOCK_REDUCE" -> "STOCK";
            case "SHIP_DELAY" -> "SHIP_DATE";
            default -> kind;
        };
        List<GroupBuyAdminView.SkuMax> skuMax = new ArrayList<>();
        JsonNode skus = JSON.readTree(skusJson == null ? "[]" : skusJson);
        skus.forEach(n -> skuMax.add(new GroupBuyAdminView.SkuMax(n.path("name").asString(""), n.path("kind").asString("MAIN"),
                n.path("maxQty").isNull() || n.path("maxQty").isMissingNode() ? null : n.path("maxQty").asInt())));
        List<GroupBuyAdminView.Notice> notices = new ArrayList<>();
        JSON.readTree(noticesJson == null ? "[]" : noticesJson).forEach(n -> {
            List<GroupBuyAdminView.NoticeValue> values = new ArrayList<>();
            n.path("values").properties().forEach(e -> values.add(new GroupBuyAdminView.NoticeValue(e.getKey(), e.getValue().path("value").asString(""),
                    e.getValue().path("unavailable").asBoolean(false), e.getValue().path("reason").asString(""))));
            List<GroupBuyAdminView.NoticeCustomItem> custom = new ArrayList<>();
            int k = 0;
            for (JsonNode c : n.path("customItems")) {
                custom.add(new GroupBuyAdminView.NoticeCustomItem(c.path("name").asString(""), c.path("value").asString(""), k++));
            }
            List<String> names = new ArrayList<>();
            n.path("skus").forEach(x -> names.add(x.asString("")));
            notices.add(new GroupBuyAdminView.Notice(n.path("categoryCode").asString(""), n.path("title").asString(""), List.of(), names, values, custom));
        });
        int imageCount = JSON.readTree(imagesJson == null ? "[]" : imagesJson).size();
        List<String> changed = switch (screenKind) {
            case "STOCK" -> List.of("maxQty");
            case "SHIP_DATE" -> List.of("shipPlannedAt");
            case "NOTICE" -> List.of("notices");
            case "CONTENT" -> List.of("description");
            default -> List.of();
        };
        return new GroupBuyAdminView.Revision(no, screenKind, changed, title, nz(summary), nz(description), imageCount, maxQty, addedQty, skuMax, shipPlannedAt, notices,
                nz(operator), at);
    }

    private static GroupBuyAdminView.CancelPreview cancelView(CancelSummary s) {
        return new GroupBuyAdminView.CancelPreview(s.refundOrders(), s.refundAmount(), s.pendingOrders(), s.shippedOrders());
    }

    private static GroupBuyAdminView.PolicyLine line(ShippingPolicyStore.PolicyRow r) {
        return r == null ? null : new GroupBuyAdminView.PolicyLine(r.baseFee(), r.freeShippingOver(), r.returnFee(), r.returnFeeMode(), r.effectiveFrom());
    }

    static PaymentMemberView member(long userId, String nickname, boolean withdrawn) {
        return new PaymentMemberView(withdrawn ? null : Long.toString(userId), nickname == null ? "" : nickname, withdrawn);
    }

    static GroupBuyDraft draft(GroupBuyAdminRequest.Save body) {
        GroupBuyAdminRequest.Save b = body == null
                ? new GroupBuyAdminRequest.Save(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) : body;
        List<GroupBuyDraft.SkuDraft> skus = new ArrayList<>();
        if (b.skus() != null) {
            for (GroupBuyAdminRequest.SkuInput s : b.skus()) {
                skus.add(new GroupBuyDraft.SkuDraft(AdminIds.field(s.productSkuId(), "skus"), s.extraPrice() == null ? 0 : s.extraPrice(), s.maxQty(),
                        !Boolean.FALSE.equals(s.onSale()), s.supplyPrice()));
            }
        }
        List<GroupBuyDraft.AddonDraft> addons = new ArrayList<>();
        if (b.addons() != null) {
            for (GroupBuyAdminRequest.AddonInput a : b.addons()) {
                addons.add(new GroupBuyDraft.AddonDraft(AdminIds.field(a.productSkuId(), "addons"), a.price() == null ? 0 : a.price(), a.maxQty(), a.supplyPrice()));
            }
        }
        long productId = b.productId() == null ? -1 : AdminIds.field(b.productId(), "productId");
        return new GroupBuyDraft(productId, b.title(), b.groupPrice() == null ? 0 : b.groupPrice(), b.shippingPolicyCode(), b.minQty() == null ? 0 : b.minQty(),
                b.maxQty() == null ? 0 : b.maxQty(), b.maxQtyPerUser(), b.startAt(), b.endAt(), b.extensionMode(), b.fulfillment(), b.shipPlannedAt(),
                b.shipWithinBusinessDays(), b.returnPolicy(), Boolean.TRUE.equals(b.roundLimitShared()), skus, addons, Boolean.TRUE.equals(b.marginConfirmed()));
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    Optional<GroupBuyRow> find(long id) {
        return groups.find(id);
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }

    /** 발송 전 주문 상태(목록 거르기) */
    static final Set<ParticipationStatus> PRE_SHIP = ParticipationStatus.PAID_BEFORE_SHIPPING;
}
