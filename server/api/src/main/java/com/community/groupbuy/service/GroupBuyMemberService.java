package com.community.groupbuy.service;

import com.community.board.dto.BoardActor;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.groupbuy.dto.Availability;
import com.community.groupbuy.dto.GroupBuyBuyer;
import com.community.groupbuy.dto.JoinRequest;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.dto.Quote;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.groupbuy.dto.request.GroupBuyMemberRequest;
import com.community.groupbuy.dto.response.GroupBuyMemberView;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.WatchKind;
import com.community.groupbuy.exception.GroupBuyBlockedException;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyNoticeStore;
import com.community.groupbuy.repository.master.GroupBuyProductStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.identity.exception.IdentityRequiredException;
import com.community.identity.exception.IdentityUnderageException;
import com.community.media.service.ImageStorage;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.response.PaymentCheckoutView;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.permission.entity.MemberPermission;
import com.community.restriction.exception.MemberRestrictedException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import com.community.groupbuy.feedback.FeedbackView;
import com.community.groupbuy.feedback.GroupBuyReviewService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구 회원 API(목록·카테고리·상세·견적·주문·관심 신청) — 계약 「2b-1 화면 목업 › API 후보」·결정 17·58·84·88·89. 업무 규칙은 shared 서비스가 지키고 여기는 화면 모양으로 옮긴다.
 *
 * <ul>
 *   <li>보이는 공구: 공개된({@code published_at}) 공구. 탭 — 예정(공개된 DRAFT) · 진행(모집 중·집계 중) · 종료(그 밖, 중단 포함). 숨긴 카테고리의 상품은 빠진다.</li>
 *   <li>단추 상태 {@code purchase.reason}: 모집 상태 → 품절 → 로그인 → 진행 중 주문 → 자격(정지·이메일·권한·본인인증·만 19세·참여 제한·판매 스위치) → 1인 한도 차례로 첫 사유.</li>
 *   <li>「마감 임박」 = 모집 중이고 마감까지 24시간 안(1시간 점검 워커의 마감 임박 알림과 같은 창).</li>
 * </ul>
 */
@Service
public class GroupBuyMemberService {

    public static final int PAGE_SIZE = 20;
    private static final Duration CLOSING_SOON = GroupBuyDeadlineWatch.CLOSING_SOON;
    /** 회원에게 보이는 공구(공개됨 · 숨긴 카테고리와 그 아래 제외) — 뒤에 탭 조건을 잇는다 */
    private static final String VISIBLE_FROM = " FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id JOIN group_buy_categories c ON c.id = p.category_id "
            + "LEFT JOIN group_buy_categories pc ON pc.id = c.parent_id WHERE g.published_at IS NOT NULL AND NOT c.hidden AND NOT COALESCE(pc.hidden, false) AND ";
    /** 진행 탭·홈 가젯 차례 — 마감(연장 중이면 연장 끝) 이른 순 */
    private static final String DEADLINE_ORDER = "COALESCE(g.extended_until, g.end_at), g.id";

    private final JdbcTemplate jdbc;
    private final GroupBuyStore groups;
    private final GroupBuyProductStore products;
    private final GroupBuyNoticeStore noticeStore;
    private final GroupBuyNoticeCatalog catalog;
    private final ParticipationStore participations;
    private final GroupBuyQuoteService quotes;
    private final GroupBuyJoinService joins;
    private final HoldExpiryService holds;
    private final GroupBuyEligibility eligibility;
    private final GroupBuyWatchService watches;
    private final GroupBuyReviewService reviews;
    private final BoardActorResolver actors;
    private final BoardCatalog boards;
    private final ImageStorage storage;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyMemberService(DataSource dataSource, GroupBuyStore groups, GroupBuyProductStore products, GroupBuyNoticeStore noticeStore, GroupBuyNoticeCatalog catalog,
                                 ParticipationStore participations, GroupBuyQuoteService quotes, GroupBuyJoinService joins, HoldExpiryService holds, GroupBuyEligibility eligibility,
                                 GroupBuyWatchService watches, BoardActorResolver actors, BoardCatalog boards, ImageStorage storage, ObjectProvider<Clock> clocks,
                                 GroupBuyReviewService reviews) {
        this.reviews = reviews;
        this.jdbc = new JdbcTemplate(dataSource);
        this.groups = groups;
        this.products = products;
        this.noticeStore = noticeStore;
        this.catalog = catalog;
        this.participations = participations;
        this.quotes = quotes;
        this.joins = joins;
        this.holds = holds;
        this.eligibility = eligibility;
        this.watches = watches;
        this.actors = actors;
        this.boards = boards;
        this.storage = storage;
        this.clocks = clocks;
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GroupBuyMemberView.ListPage list(String phase, String category, int page) {
        String tab = phase == null ? "ONGOING" : phase;
        String where = switch (tab) {
            case "UPCOMING" -> "g.status = 'DRAFT'";
            case "ONGOING" -> "g.status IN ('RECRUITING', 'CLOSING')";
            case "ENDED" -> "g.status NOT IN ('DRAFT', 'RECRUITING', 'CLOSING')";
            default -> throw ValidationException.of("phase", new MessageRef("groupbuy.member.phase.invalid"));
        };
        String order = switch (tab) {
            case "UPCOMING" -> "g.start_at, g.id";
            case "ONGOING" -> DEADLINE_ORDER;
            default -> "g.closing_at DESC NULLS LAST, g.updated_at DESC, g.id DESC";
        };
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(VISIBLE_FROM).append(where);
        if (category != null && !category.isBlank()) {
            long categoryId = id(category, "category");
            sql.append(" AND (c.id = ? OR c.parent_id = ?)");
            args.add(categoryId);
            args.add(categoryId);
        }
        int p = Math.max(0, page);
        Long total = jdbc.queryForObject("SELECT count(*)" + sql, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(PAGE_SIZE);
        pageArgs.add(p * PAGE_SIZE);
        List<Long> ids = jdbc.queryForList("SELECT g.id" + sql + " ORDER BY " + order + " LIMIT ? OFFSET ?", Long.class, pageArgs.toArray());
        return new GroupBuyMemberView.ListPage(listItems(ids), p, PAGE_SIZE, total == null ? 0 : total);
    }

    /**
     * 홈 공구 가젯({@code GROUP_BUY}) — 모집 중({@code RECRUITING})인 공개 공구 {@code count} 개. 거름(공개 · 숨긴 카테고리 제외)·차례(마감 이른 순)·줄 모양은 목록 진행 탭과 같고,
     * 집계 중({@code CLOSING})은 살 수 없어 뺀다. 판매 스위치로 거르지 않는다(목록과 같다).
     */
    @Transactional(readOnly = true)
    public List<GroupBuyMemberView.ListItem> recruiting(int count) {
        List<Long> ids = jdbc.queryForList("SELECT g.id" + VISIBLE_FROM + "g.status = 'RECRUITING' ORDER BY " + DEADLINE_ORDER + " LIMIT ?", Long.class, count);
        return listItems(ids);
    }

    /** 목록 줄 — 차례는 {@code ids} 그대로, 리뷰 평균·수(2b-6)는 상품마다 한 번에 */
    private List<GroupBuyMemberView.ListItem> listItems(List<Long> ids) {
        Instant now = now();
        List<GroupBuyMemberView.ListItem> items = new ArrayList<>();
        Map<Long, Long> productOf = new HashMap<>();
        for (long gid : ids) {
            groups.find(gid).ifPresent(g -> {
                productOf.put(gid, g.productId());
                items.add(listItem(g, quotes.availability(gid), groups.skus(gid), now));
            });
        }
        Map<Long, FeedbackView.Stats> stats = reviews.stats(new ArrayList<>(productOf.values()));
        items.replaceAll(it -> it.withReviews(stats.getOrDefault(productOf.get(Long.parseLong(it.id())), new FeedbackView.Stats(null, 0))));
        return items;
    }

    /** 회원 목록 필터 — 2단, 숨긴 카테고리(와 그 아래)는 뺀다 */
    @Transactional(readOnly = true)
    public List<GroupBuyMemberView.CategoryNode> categories() {
        List<GroupBuyProductStore.CategoryRow> rows = products.categories();
        List<GroupBuyMemberView.CategoryNode> out = new ArrayList<>();
        for (GroupBuyProductStore.CategoryRow top : rows) {
            if (top.parentId() != null || top.hidden()) {
                continue;
            }
            List<GroupBuyMemberView.CategoryNode.Child> children = rows.stream().filter(c -> top.id() == (c.parentId() == null ? -1 : c.parentId()) && !c.hidden())
                    .map(c -> new GroupBuyMemberView.CategoryNode.Child(Long.toString(c.id()), c.name())).toList();
            out.add(new GroupBuyMemberView.CategoryNode(Long.toString(top.id()), top.name(), children));
        }
        return out;
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    /** @param userId 로그인했으면 회원 번호(없으면 null — 단추는 「로그인하고 주문하기」). 본인의 지난 선점 정리는 자기 트랜잭션(결제 행 먼저 — 결정 37) */
    public GroupBuyMemberView.Detail detail(String idText, Long userId) {
        long id = idOrNotFound(idText);
        GroupBuyRow g = visible(id);
        Instant now = now();
        if (userId != null && g.status() == GroupBuyStatus.RECRUITING) {
            holds.expireOwnOverdue(userId, id); // 조회 때 정리는 본인 주문만(결정 85)
        }
        Availability a = quotes.availability(id);
        List<SkuRow> skus = groups.skus(id);
        GroupBuyMemberView.ListItem base = listItem(g, a, skus, now);
        GroupBuyStore.Settings settings = groups.settings();
        Optional<ParticipationStore.ParticipationRow> mine = userId == null ? Optional.empty() : participations.inProgress(id, userId);
        String reason = purchaseReason(g, a, userId, mine.isPresent());
        List<GroupBuyWatchService.Watch> my = userId == null ? List.of() : watches.mine(userId, id);
        Map<String, Boolean> watchFlags = new LinkedHashMap<>();
        for (WatchKind k : WatchKind.values()) {
            watchFlags.put(k.name(), my.stream().anyMatch(w -> w.kind() == k && w.skuId() == null));
        }
        List<String> watching = my.stream().filter(w -> w.kind() == WatchKind.RESTOCK && w.skuId() != null).map(w -> Long.toString(w.skuId())).toList();
        GroupBuyMemberView.Actions actions = new GroupBuyMemberView.Actions(reason == null, watches.allowed(g, a, WatchKind.RESTOCK, now),
                watches.allowed(g, a, WatchKind.CLOSING_SOON, now), watches.allowed(g, a, WatchKind.NEXT_ROUND, now));
        GroupBuyMemberView.Shipping shipping = shipping(g);
        return new GroupBuyMemberView.Detail(base.id(), base.title(), base.thumbnailUrl(), base.status(), base.phase(), base.closeReason(), base.fulfillment(),
                base.groupPrice(), base.priceFrom(), base.normalPrice(), base.minQty(), base.maxQty(), base.currentQty(), base.remainingQty(), base.stockState(),
                base.startAt(), base.endAt(), base.extendedUntil(), base.closingSoon(), base.roundNo(), base.categoryId(), images(g), nz(g.description()), g.summary(),
                g.spec(), g.normalPriceBasis(), g.shipPlannedAt(), g.immediate() ? g.shipWithinBusinessDays() : null, shipping, g.withdrawalRestriction(),
                g.extensionMode(), settings.extensionMinutes(), Math.max(0, g.initialMaxQty() == null ? 0 : g.maxQty() - g.initialMaxQty()), g.maxQtyPerUser(),
                g.roundLimitShared(), settings.holdMinutes(), availability(a),
                new GroupBuyMemberView.SkuWatches(watches.watchableSkus(g, a).stream().map(String::valueOf).toList(), watching), notices(g, skus), delayNotice(id),
                new GroupBuyMemberView.Purchase(reason), actions, watchFlags, mine.map(ParticipationStore.ParticipationRow::orderNo).orElse(null));
    }

    // ── 견적·주문 ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GroupBuyMemberView.Quote quote(String idText, GroupBuyMemberRequest.Quote body) {
        long id = idOrNotFound(idText);
        visible(id);
        Quote q = quotes.quote(id, lines(body == null ? null : body.items(), "items"));
        Long remaining = q.freeShippingOver() == null || q.itemsAmount() >= q.freeShippingOver() ? null : q.freeShippingOver() - q.itemsAmount();
        return new GroupBuyMemberView.Quote(q.items().stream().map(l -> new GroupBuyMemberView.QuoteItem(Long.toString(l.skuId()), l.kind(), l.name(), l.qty(),
                l.unitPrice(), l.itemAmount())).toList(), q.itemsAmount(), q.shippingFee(), q.amount(), remaining);
    }

    /** 주문 — 멱등 키·속도 제한은 컨트롤러의 {@code MoneyRequestGuard}. 회원 권한 {@code commerce.purchase} 는 회원 역할로 판정 */
    @Transactional
    public GroupBuyMemberView.OrderCreated order(long userId, String idText, GroupBuyMemberRequest.Order body) {
        long id = idOrNotFound(idText);
        visible(id);
        GroupBuyMemberRequest.Order b = body == null ? new GroupBuyMemberRequest.Order(null, null, null, null) : body;
        GroupBuyMemberRequest.Address a = b.address() == null ? new GroupBuyMemberRequest.Address(null, null, null, null, null, null) : b.address();
        ShippingAddress address = new ShippingAddress(a.recipient(), a.phone(), a.postcode(), a.address1(), a.address2(), a.memo());
        GroupBuyMemberRequest.Agreements ag = b.agreements() == null ? new GroupBuyMemberRequest.Agreements(false, false, false) : b.agreements();
        JoinResult r = joins.join(buyer(userId), new JoinRequest(id, lines(b.items(), "items"), address, Boolean.TRUE.equals(ag.purchaseTerms()),
                Boolean.TRUE.equals(ag.refundPolicy()), Boolean.TRUE.equals(ag.privacy()), b.expectedAmount()));
        return new GroupBuyMemberView.OrderCreated(r.orderNo(), r.amount(), r.reservedUntil(),
                paymentStart("GROUP_BUY", r.participationId(), r.orderNo(), r.checkout(), userId, address.recipient(), address.phone()));
    }

    public GroupBuyBuyer buyer(long userId) {
        BoardActor actor = actors.resolve(userId, boards.snapshot());
        return new GroupBuyBuyer(userId, actor.can(MemberPermission.COMMERCE_PURCHASE.code()));
    }

    /** 결제창 대상 — 구매자 정보는 포트원(KG이니시스)일 때만 배송지 이름·전화 + 회원 이메일로 채운다 */
    public GroupBuyMemberView.PaymentStart paymentStart(String orderType, long refId, String orderNo, PaymentCheckout checkout, long userId, String name, String phone) {
        GroupBuyMemberView.Customer customer = null;
        if (checkout.provider() == PaymentGatewayKind.PORTONE) {
            String email = jdbc.query("SELECT email FROM users WHERE id = ?", (rs, i) -> rs.getString(1), userId).stream().filter(java.util.Objects::nonNull).findFirst().orElse("");
            customer = new GroupBuyMemberView.Customer(nz(name), nz(phone), nz(email));
        }
        return new GroupBuyMemberView.PaymentStart(orderType, Long.toString(refId), checkout.expiresAt(), PaymentCheckoutView.of(checkout), orderNo, customer);
    }

    // ── 관심 신청 ───────────────────────────────────────────────────────────

    @Transactional
    public void watch(long userId, String idText, GroupBuyMemberRequest.Watch body, boolean on) {
        long id = idOrNotFound(idText);
        WatchKind kind;
        try {
            kind = body == null || body.kind() == null ? null : WatchKind.valueOf(body.kind());
        } catch (IllegalArgumentException e) {
            kind = null;
        }
        Long skuId = body == null || body.skuId() == null || body.skuId().isBlank() ? null : id(body.skuId(), "skuId");
        if (on) {
            watches.watch(userId, id, kind, skuId);
        } else {
            watches.unwatch(userId, id, kind, skuId);
        }
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    GroupBuyRow visible(long id) {
        return groups.find(id).filter(g -> g.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
    }

    GroupBuyMemberView.ListItem listItem(GroupBuyRow g, Availability a, List<SkuRow> skus, Instant now) {
        List<Long> prices = skus.stream().filter(s -> s.main() && s.onSale()).map(s -> s.unitPrice(g.groupPrice())).distinct().sorted().toList();
        long price = prices.isEmpty() ? g.groupPrice() : prices.get(0);
        String stock = a.soldOut() ? "SOLD_OUT" : a.temporarilyUnavailable() ? "TEMP_SOLD_OUT" : "AVAILABLE";
        boolean closingSoon = g.status() == GroupBuyStatus.RECRUITING && g.deadline() != null && !g.deadline().isAfter(now.plus(CLOSING_SOON));
        return new GroupBuyMemberView.ListItem(Long.toString(g.id()), title(g), thumbnail(g), g.status().name(), phase(g), g.closeReason(), g.fulfillment(), price,
                prices.size() > 1, g.normalPrice(), g.minQty(), g.maxQty(), g.currentQty(), Math.max(0, a.remaining()), stock, g.startAt(), g.endAt(), g.extendedUntil(),
                closingSoon, g.roundNo(), Long.toString(g.categoryId()), null);
    }

    static String phase(GroupBuyRow g) {
        return switch (g.status()) {
            case DRAFT -> "UPCOMING";
            case RECRUITING, CLOSING -> "ONGOING";
            default -> "ENDED";
        };
    }

    static String title(GroupBuyRow g) {
        return g.title() == null || g.title().isBlank() ? g.productName() : g.title();
    }

    String thumbnail(GroupBuyRow g) {
        List<GroupBuyStore.ImageRow> images = products.images(g.productId());
        return images.isEmpty() ? null : storage.publicUrl(images.get(0).thumbKey() == null ? images.get(0).imageKey() : images.get(0).thumbKey());
    }

    private List<GroupBuyMemberView.Image> images(GroupBuyRow g) {
        return products.images(g.productId()).stream().map(i -> new GroupBuyMemberView.Image(storage.publicUrl(i.imageKey()), g.productName())).toList();
    }

    private static GroupBuyMemberView.Shipping shipping(GroupBuyRow g) {
        return new GroupBuyMemberView.Shipping(g.shippingFee() == null ? 0 : g.shippingFee(), g.freeShippingOver(), g.returnShippingFee() == null ? 0 : g.returnShippingFee(),
                g.returnFeeMode() == null ? "ONE_WAY" : g.returnFeeMode());
    }

    private static GroupBuyMemberView.Availability availability(Availability a) {
        return new GroupBuyMemberView.Availability(Long.toString(a.groupBuyId()), Math.max(0, a.remaining()), a.soldOut(), a.temporarilyUnavailable(), a.addedQty(),
                a.axes().stream().map(x -> new GroupBuyMemberView.Axis(Long.toString(x.axisId()), x.name(), x.values().stream()
                        .map(v -> new GroupBuyMemberView.AxisValue(Long.toString(v.valueId()), v.value())).toList())).toList(),
                a.skus().stream().map(s -> new GroupBuyMemberView.Sku(Long.toString(s.skuId()), s.kind(), s.name(), s.valueIds().stream().map(String::valueOf).toList(),
                        s.unitPrice(), s.remaining(), s.soldOut(), s.temporarilyUnavailable(), s.onSale())).toList());
    }

    private GroupBuyMemberView.DelayNotice delayNotice(long groupBuyId) {
        return jdbc.query("SELECT previous_planned_at, new_planned_at, reason, created_at FROM group_buy_delay_notices WHERE group_buy_id = ? ORDER BY created_at DESC LIMIT 1",
                (rs, i) -> new GroupBuyMemberView.DelayNotice(rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate(), rs.getString(3),
                        rs.getTimestamp(4).toInstant()), groupBuyId).stream().findFirst().orElse(null);
    }

    /** 고시 양식 — 공구 상품 + 추가 구성상품의 상품. 양식의 상품 단품 ID 를 이 공구의 단품 ID 로 바꾼다(없는 단품은 뺀다) */
    private List<GroupBuyMemberView.Notice> notices(GroupBuyRow g, List<SkuRow> skus) {
        Map<Long, SkuRow> byProductSku = new HashMap<>();
        skus.forEach(s -> byProductSku.put(s.productSkuId(), s));
        List<Long> productIds = new ArrayList<>();
        productIds.add(g.productId());
        for (SkuRow s : skus) {
            if (!s.main()) {
                products.sku(s.productSkuId()).map(GroupBuyProductStore.ProductSkuRow::productId).filter(p -> !productIds.contains(p)).ifPresent(productIds::add);
            }
        }
        List<GroupBuyMemberView.Notice> out = new ArrayList<>();
        for (long productId : productIds) {
            for (GroupBuyNoticeStore.NoticeRow n : noticeStore.notices(productId)) {
                GroupBuyNoticeCatalog.Version version = catalog.version(n.catalogVersion()).orElse(catalog.current());
                Optional<GroupBuyNoticeCatalog.Category> category = version.category(n.categoryCode());
                List<SkuRow> applied = n.skuIds().stream().map(byProductSku::get).filter(java.util.Objects::nonNull).toList();
                if (!n.skuIds().isEmpty() && applied.isEmpty()) {
                    continue; // 이 공구가 팔지 않는 단품만 가리키는 양식
                }
                List<GroupBuyMemberView.NoticeRow> rows = new ArrayList<>();
                category.ifPresent(c -> c.items().forEach(item -> {
                    GroupBuyNoticeStore.ValueRow v = n.values().get(item.key());
                    rows.add(new GroupBuyMemberView.NoticeRow(item.key(), item.no(), item.group(), item.name(), item.description(), item.kind().name(),
                            v == null || v.value() == null ? "" : v.value(), v != null && v.unavailable(), v == null || v.unavailableReason() == null ? "" : v.unavailableReason()));
                }));
                out.add(new GroupBuyMemberView.Notice(n.categoryCode(), category.map(GroupBuyNoticeCatalog.Category::no).orElse(0),
                        category.map(GroupBuyNoticeCatalog.Category::name).orElse(n.categoryCode()), n.title(),
                        applied.stream().map(s -> Long.toString(s.id())).toList(), applied.stream().map(SkuRow::name).toList(), rows,
                        n.customItems().stream().map(c -> new GroupBuyMemberView.NoticeCustomItem(c.name(), c.value(), c.sortOrder())).toList(), version.version(),
                        version.noticeNo()));
            }
        }
        return out;
    }

    private String purchaseReason(GroupBuyRow g, Availability a, Long userId, boolean hasOrder) {
        if (g.status() == GroupBuyStatus.DRAFT) {
            return "NOT_STARTED";
        }
        if (g.status() != GroupBuyStatus.RECRUITING) {
            return "NOT_RECRUITING";
        }
        if (a.soldOut()) {
            return "SOLD_OUT";
        }
        if (a.temporarilyUnavailable()) {
            return "TEMP_SOLD_OUT";
        }
        if (userId == null) {
            return "LOGIN_REQUIRED";
        }
        if (hasOrder) {
            return "ALREADY_JOINED";
        }
        try {
            eligibility.assertCanJoin(buyer(userId));
        } catch (IdentityRequiredException e) {
            return "IDENTITY_REQUIRED";
        } catch (IdentityUnderageException e) {
            return "MINOR";
        } catch (MemberRestrictedException | GroupBuyBlockedException e) {
            return "BANNED";
        } catch (GroupBuyException e) {
            return GroupBuyException.SALES_CLOSED.equals(e.getErrorCode()) ? "SALES_CLOSED" : "NOT_RECRUITING";
        } catch (CommonException e) {
            return "LOGIN_REQUIRED";
        }
        if (g.maxQtyPerUser() != null) {
            List<Long> scope = g.roundLimitShared() ? groups.roundIds(g.roundRootId()) : List.of(g.id());
            if (participations.limitUsed(userId, scope) >= g.maxQtyPerUser()) {
                return "LIMIT_REACHED";
            }
        }
        return null;
    }

    /** 주문 줄 — 글자 ID·수량. 형식이 틀리면 400 {@code fields.<칸>} */
    static List<OrderLine> lines(List<GroupBuyMemberRequest.Line> lines, String field) {
        if (lines == null) {
            return List.of();
        }
        List<OrderLine> out = new ArrayList<>();
        for (GroupBuyMemberRequest.Line l : lines) {
            if (l == null || l.qty() == null) {
                throw ValidationException.of(field, new MessageRef("groupbuy.order.items.invalid"));
            }
            out.add(new OrderLine(id(l.skuId(), field), l.qty()));
        }
        return out;
    }

    static long id(String text, String field) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw ValidationException.of(field, new MessageRef("groupbuy.id.invalid"));
        }
    }

    static long idOrNotFound(String text) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw GroupBuyException.notFound();
        }
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }
}
