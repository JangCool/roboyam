package com.community.pointshop.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.pointshop.dto.request.PointShopAvatarScope;
import com.community.pointshop.dto.request.PointShopAvatarTarget;
import com.community.pointshop.dto.request.PointShopItemSaveRequest;
import com.community.pointshop.dto.response.PointShopAdminItemListView;
import com.community.pointshop.dto.response.PointShopAdminItemView;
import com.community.pointshop.dto.response.PointShopAvatarBulkResultView;
import com.community.pointshop.dto.response.PointShopAvatarCategorySummaryView;
import com.community.pointshop.dto.response.PointShopAvatarSummaryView;
import com.community.pointshop.dto.response.PointShopAvatarTotalsView;
import com.community.pointshop.dto.response.PointShopItemListView;
import com.community.pointshop.dto.response.PointShopItemMineView;
import com.community.pointshop.dto.response.PointShopItemView;
import com.community.pointshop.dto.response.PointShopSettingsView;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopSaleState;
import com.community.pointshop.entity.PointShopSettingEntity;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.ProfileBorderStyle;
import com.community.pointshop.entity.UserItemStatus;
import com.community.pointshop.exception.PointShopItemNotFoundException;
import com.community.pointshop.repository.master.PointShopItemStore;
import com.community.pointshop.repository.master.PointShopItemStore.ItemRow;
import com.community.pointshop.repository.master.PointShopItemStore.ItemValues;
import com.community.pointshop.repository.master.PointShopItemStore.SettingsRow;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.pointshop.repository.master.UserItemStore.HoldingRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 상점 품목 — 회원 목록·상세, 관리자 품목 만들기·고치기, 아바타 요약·묶음 가격·묶음 판매, 포인트 상점 설정(docs/features/commerce-stage1-contract.md 「B」·「1-2 화면 목업」).
 *
 * <ul>
 *   <li><b>회원 목록</b>({@link #memberList}): 판매 끝(끝 지남·사용 안 함)은 빼고, 거르기가 없으면 아바타(306종 — {@code GET /api/avatars})도 뺀다. 순서 → ID. 상세({@link #memberItem})는 판매가 끝나도 열린다.
 *       {@code mine} 은 내 보유(영구 보유·안 쓴 소모형 장수·기간제의 지금 끝).</li>
 *   <li><b>관리자 품목</b>({@link #create}·{@link #update}): 칸 검사는 계약 차례로 모아 {@code 400 VALIDATION fields.<칸>}. 종류·코드는 만든 뒤 못 바꾼다. 아바타 품목은 만들 수 없고
 *       (V86 시드) 지울 수 없으며 이름·영문명·가격·순서·사용 여부만 고친다. 감사(일반 운영): 만들기 {@code after}, 고치기 바뀐 칸만 {@code before}/{@code after}(바뀐 것이 없으면 남기지 않는다).
 *       색·테두리를 고치면 산 사람의 표시도 바로 바뀐다(가격·이름은 구매 사본 그대로 — 7-1).</li>
 *   <li><b>아바타 묶음</b>({@link #avatarPrice}·{@link #avatarSale}): 범위 {@code ALL}(유료 전체)·{@code CATEGORIES}(유료 카테고리)·{@code ITEMS}(아바타 품목 1~400). 무료 카테고리·아바타가 아닌 품목은
 *       400, 없는 품목 번호가 섞이면 404 {@code POINT_SHOP_ITEM_NOT_FOUND}(아무것도 안 바뀜). 값이 다른 줄만 바꾸고 {@code matched}·{@code changed} 를 돌려준다. 감사(일반 운영).</li>
 *   <li><b>설정</b>({@link #settings}·{@link #updateSettings}): 셀프 환불 기한 1~30일. 감사(일반 운영, 전→후).</li>
 * </ul>
 * 권한({@code pointshop.read}·{@code pointshop.manage})은 관리자 컨트롤러 선언이 한다. 감사의 행위자·IP 는 부르는 쪽이 넘긴다.
 */
@Service
@Transactional(readOnly = true)
public class PointShopCatalogService {

    public static final int PRICE_MIN = 1;
    public static final int PRICE_MAX = 100_000;
    public static final int DURATION_MIN = 1;
    public static final int DURATION_MAX = 365;
    public static final int NAME_MAX = 40;
    public static final int DESCRIPTION_MAX = 500;
    public static final int AVATAR_BULK_MAX = 400;
    public static final Pattern CODE = Pattern.compile("^[a-z0-9-]{3,40}$");
    public static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /** 감사 대상 종류 */
    public static final String TARGET_ITEM = "POINT_SHOP_ITEM";
    public static final String TARGET_AVATARS = "POINT_SHOP_AVATARS";
    public static final String TARGET_SETTINGS = "POINT_SHOP_SETTINGS";

    private final PointShopItemStore items;
    private final UserItemStore userItems;
    private final PointShopMemberStore members;
    private final AvatarCatalog avatars;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public PointShopCatalogService(PointShopItemStore items, UserItemStore userItems, PointShopMemberStore members, AvatarCatalog avatars, AuditLogService audit,
                                   SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.items = items;
        this.userItems = userItems;
        this.members = members;
        this.avatars = avatars;
        this.audit = audit;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /**
     * 회원 품목 목록 — {@code GET /api/point-shop/items?kind=}.
     *
     * @param kind 이 종류만(null = 아바타를 뺀 전부)
     */
    public PointShopItemListView memberList(long userId, PointShopItemKind kind) {
        Instant now = now();
        Map<Long, PointShopItemMineView> mine = mineOf(userId, now);
        List<PointShopItemView> list = items.list(kind, kind == null ? PointShopItemKind.PROFILE_AVATAR : null).stream()
                .map(row -> memberView(row, mine.getOrDefault(row.id(), PointShopItemMineView.NONE), now))
                .filter(view -> view.saleState() != PointShopSaleState.ENDED).toList();
        return new PointShopItemListView(items.refundDays(), members.balance(userId), list);
    }

    /**
     * 회원 품목 상세 — {@code GET /api/point-shop/items/{id}}(판매가 끝나도 연다).
     *
     * @throws PointShopItemNotFoundException 없는 품목(404)
     */
    public PointShopItemView memberItem(long userId, long itemId) {
        ItemRow row = items.find(itemId).orElseThrow(PointShopItemNotFoundException::new);
        return memberItemView(userId, row, now());
    }

    /** 그 회원이 보는 품목 하나(구매 결과에도 쓴다) */
    PointShopItemView memberItemView(long userId, ItemRow row, Instant now) {
        return memberView(row, mineOf(userId, now).getOrDefault(row.id(), PointShopItemMineView.NONE), now);
    }

    private PointShopItemView memberView(ItemRow row, PointShopItemMineView mine, Instant now) {
        return new PointShopItemView(Long.toString(row.id()), row.code(), row.kind(), row.usageType(), row.name(), row.nameEn(), row.description(), row.descriptionEn(),
                row.color(), row.borderStyle(), row.avatarId(), row.avatarId() == null ? null : AvatarCatalog.URL_PREFIX + row.avatarId() + ".svg", row.price(),
                row.durationDays(), row.stock(), row.saleStartAt(), row.saleEndAt(), saleState(row, now), mine);
    }

    static PointShopSaleState saleState(ItemRow row, Instant now) {
        return PointShopSaleState.of(row.active(), row.saleStartAt(), row.saleEndAt(), row.stock(), now);
    }

    /** 품목별 내 보유 — 영구 보유 · 안 쓴 소모형 장수 · 기간제의 지금 끝(회수된 줄은 빼고) */
    private Map<Long, PointShopItemMineView> mineOf(long userId, Instant now) {
        Map<Long, boolean[]> owned = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Instant> ends = new HashMap<>();
        for (HoldingRow h : userItems.holdings(userId)) {
            switch (h.usageType()) {
                case PERMANENT -> owned.put(h.itemId(), new boolean[] {true});
                case CONSUMABLE -> {
                    if (h.status() == UserItemStatus.OWNED) {
                        counts.merge(h.itemId(), 1, Integer::sum);
                    }
                }
                case PERIOD -> {
                    if (h.expiresAt() != null && h.expiresAt().isAfter(now)) {
                        ends.merge(h.itemId(), h.expiresAt(), (a, b) -> a.isAfter(b) ? a : b);
                    }
                }
            }
        }
        Set<Long> keys = new LinkedHashSet<>(owned.keySet());
        keys.addAll(counts.keySet());
        keys.addAll(ends.keySet());
        Map<Long, PointShopItemMineView> out = new HashMap<>();
        for (Long id : keys) {
            out.put(id, new PointShopItemMineView(owned.containsKey(id), counts.getOrDefault(id, 0), ends.get(id)));
        }
        return out;
    }

    // ── 관리자 품목 ─────────────────────────────────────────────────────────

    /**
     * 관리자 품목 목록 — {@code GET /api/admin/point-shop/items?kind=&excludeKind=}(「품목」 탭은 {@code excludeKind=PROFILE_AVATAR}, 「아바타」 탭은 {@code kind=PROFILE_AVATAR}).
     */
    public PointShopAdminItemListView adminItems(PointShopItemKind kind, PointShopItemKind excludeKind) {
        List<ItemRow> rows = items.list(kind, excludeKind);
        Map<Long, Integer> sold = items.soldCounts(rows.stream().map(ItemRow::id).toList());
        return new PointShopAdminItemListView(rows.stream().map(row -> adminView(row, sold.getOrDefault(row.id(), 0))).toList());
    }

    /** @throws PointShopItemNotFoundException 없는 품목(404) */
    public PointShopAdminItemView adminItem(long itemId) {
        ItemRow row = items.find(itemId).orElseThrow(PointShopItemNotFoundException::new);
        return adminView(row, items.soldCounts(List.of(itemId)).getOrDefault(itemId, 0));
    }

    /**
     * 품목을 만든다 — 아바타는 만들 수 없다({@code fields.kind}).
     *
     * @throws ValidationException 칸 검사({@code fields.code|kind|usageType|name|nameEn|description|descriptionEn|color|borderStyle|avatarId|pricePoint|durationDays|stock|saleEndAt|active|sortOrder})
     */
    @Transactional
    public PointShopAdminItemView create(PointShopItemSaveRequest request, long operatorId, String ip) {
        ItemValues values = validate(request, null);
        long id = ids.nextId();
        Instant now = now();
        items.insert(id, values, operatorId, now);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.POINT_SHOP_ITEM_CREATE, AuditResult.SUCCESS).target(TARGET_ITEM, Long.toString(id))
                .with("code", values.code()).with("kind", values.kind().name()).with("after", fields(values)));
        return adminItem(id);
    }

    /**
     * 품목을 고친다 — 종류·코드(아바타는 아바타 ID 도)는 그대로여야 하고, 아바타는 이름·영문명·가격·순서·사용 여부만 바꿀 수 있다.
     *
     * @throws PointShopItemNotFoundException 없는 품목(404)
     * @throws ValidationException            칸 검사({@link #create} 와 같은 칸)
     */
    @Transactional
    public PointShopAdminItemView update(long itemId, PointShopItemSaveRequest request, long operatorId, String ip) {
        ItemRow current = items.findForUpdate(itemId).orElseThrow(PointShopItemNotFoundException::new);
        ItemValues values = validate(request, current);
        Map<String, Object> before = fields(valuesOf(current));
        Map<String, Object> after = fields(values);
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        for (String field : after.keySet()) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                changedBefore.put(field, before.get(field));
                changedAfter.put(field, after.get(field));
            }
        }
        if (!changedAfter.isEmpty()) {
            items.update(itemId, values, operatorId, now());
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.POINT_SHOP_ITEM_UPDATE, AuditResult.SUCCESS).target(TARGET_ITEM, Long.toString(itemId))
                    .with("code", current.code()).with("kind", current.kind().name()).with("before", changedBefore).with("after", changedAfter));
        }
        return adminItem(itemId);
    }

    private PointShopAdminItemView adminView(ItemRow row, int sold) {
        String category = row.avatarId() == null ? null : avatars.find(row.avatarId()).map(AvatarCatalog.Avatar::category).orElse(null);
        return new PointShopAdminItemView(Long.toString(row.id()), row.code(), row.kind(), row.usageType(), row.name(), row.nameEn(), row.description(),
                row.descriptionEn(), row.color(), row.borderStyle(), row.avatarId(), category, row.price(), row.durationDays(), row.stock(), row.saleStartAt(),
                row.saleEndAt(), row.active(), row.sortOrder(), sold, row.createdAt(), row.createdByName(), row.updatedAt(), row.updatedByName());
    }

    /** 칸 검사 — {@code current} 가 null 이면 만들기. 칸 차례 = 요청 선언 차례, 칸마다 첫 위반만 */
    private ItemValues validate(PointShopItemSaveRequest r, ItemRow current) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        boolean creating = current == null;
        String code = r.code() == null ? null : r.code().strip();
        if (creating) {
            if (code == null || !CODE.matcher(code).matches()) {
                bad.put("code", new MessageRef("pointshop.item.code.format", 3, 40));
            } else if (code.startsWith(AvatarCatalog.ITEM_CODE_PREFIX)) {
                bad.put("code", new MessageRef("pointshop.item.code.reserved"));
            } else if (items.codeExists(code)) {
                bad.put("code", new MessageRef("pointshop.item.code.taken"));
            }
        } else if (!current.code().equals(code)) {
            bad.put("code", new MessageRef("pointshop.item.code.fixed"));
        }
        String kindText = strip(r.kind());
        PointShopItemKind kind = enumOf(PointShopItemKind.class, kindText);
        if (kindText != null && kind == null) {
            bad.put("kind", new MessageRef("pointshop.item.kind.invalid"));
        } else if (creating) {
            if (kind == null) {
                bad.put("kind", new MessageRef("pointshop.item.kind.required"));
            } else if (kind == PointShopItemKind.PROFILE_AVATAR) {
                bad.put("kind", new MessageRef("pointshop.item.kind.avatar"));
            }
        } else if (kind != current.kind()) {
            bad.put("kind", new MessageRef("pointshop.item.kind.fixed"));
        }
        PointShopItemKind effective = creating ? kind : current.kind();
        boolean avatar = !creating && effective == PointShopItemKind.PROFILE_AVATAR; // 아바타는 고치기만 있다(만들기는 위에서 거절)

        String usageText = strip(r.usageType());
        PointShopUsageType usage = enumOf(PointShopUsageType.class, usageText);
        if (usageText != null && usage == null) {
            bad.put("usageType", new MessageRef("pointshop.item.usageType.invalid"));
        } else if (usage == null) {
            bad.put("usageType", new MessageRef("pointshop.item.usageType.required"));
        } else if (effective != null && !effective.allows(usage)) {
            bad.put("usageType", new MessageRef("pointshop.item.usageType.kind"));
        }
        String name = strip(r.name());
        if (name == null || name.length() > NAME_MAX) {
            bad.put("name", new MessageRef("pointshop.item.name.length", 1, NAME_MAX));
        }
        String nameEn = strip(r.nameEn());
        if (nameEn != null && nameEn.length() > NAME_MAX) {
            bad.put("nameEn", new MessageRef("pointshop.item.nameEn.length", NAME_MAX));
        }
        String description = strip(r.description());
        if (description == null || description.length() > DESCRIPTION_MAX) {
            bad.put("description", new MessageRef("pointshop.item.description.length", 1, DESCRIPTION_MAX));
        }
        String descriptionEn = strip(r.descriptionEn());
        if (descriptionEn != null && descriptionEn.length() > DESCRIPTION_MAX) {
            bad.put("descriptionEn", new MessageRef("pointshop.item.descriptionEn.length", DESCRIPTION_MAX));
        }
        // 종류를 모르면(없음·모르는 이름) 종류에 딸린 칸은 묻지 않는다 — 종류 칸 오류 하나로
        String color = strip(r.color());
        if (effective == PointShopItemKind.NICKNAME_COLOR) {
            if (color == null || !COLOR.matcher(color).matches()) {
                bad.put("color", new MessageRef("pointshop.item.color.format"));
            }
        } else if (color != null && effective != null) {
            bad.put("color", new MessageRef("pointshop.item.field.notAllowed"));
        }
        String border = strip(r.borderStyle());
        if (effective == PointShopItemKind.PROFILE_BORDER) {
            if (!ProfileBorderStyle.known(border)) {
                bad.put("borderStyle", new MessageRef("pointshop.item.borderStyle.invalid"));
            }
        } else if (border != null && effective != null) {
            bad.put("borderStyle", new MessageRef("pointshop.item.field.notAllowed"));
        }
        String avatarId = strip(r.avatarId());
        if (avatar) {
            if (!Objects.equals(avatarId, current.avatarId())) {
                bad.put("avatarId", new MessageRef("pointshop.item.avatarId.fixed"));
            }
        } else if (avatarId != null && effective != null) {
            bad.put("avatarId", new MessageRef("pointshop.item.field.notAllowed"));
        }
        Integer price = r.pricePoint();
        if (price == null || price < PRICE_MIN || price > PRICE_MAX) {
            bad.put("pricePoint", new MessageRef("pointshop.item.price.range", PRICE_MIN, PRICE_MAX));
        }
        Integer duration = r.durationDays();
        if (usage == PointShopUsageType.PERIOD) {
            if (duration == null || duration < DURATION_MIN || duration > DURATION_MAX) {
                bad.put("durationDays", new MessageRef("pointshop.item.duration.range", DURATION_MIN, DURATION_MAX));
            }
        } else if (duration != null) {
            bad.put("durationDays", new MessageRef("pointshop.item.duration.periodOnly"));
        }
        Integer stock = r.stock();
        if (stock != null && stock < 0) {
            bad.put("stock", new MessageRef("pointshop.item.stock.min"));
        } else if (stock != null && avatar) {
            bad.put("stock", new MessageRef("pointshop.item.field.notAllowed"));
        }
        if (avatar && (r.saleStartAt() != null || r.saleEndAt() != null)) {
            bad.put(r.saleStartAt() != null ? "saleStartAt" : "saleEndAt", new MessageRef("pointshop.item.field.notAllowed"));
        } else if (r.saleStartAt() != null && r.saleEndAt() != null && !r.saleEndAt().isAfter(r.saleStartAt())) {
            bad.put("saleEndAt", new MessageRef("pointshop.item.sale.order"));
        }
        if (r.active() == null) {
            bad.put("active", new MessageRef("pointshop.item.active.required"));
        }
        if (r.sortOrder() == null || r.sortOrder() < 0) {
            bad.put("sortOrder", new MessageRef("pointshop.item.sortOrder.min"));
        }
        if (avatar && bad.isEmpty()) {
            // 아바타 한 줄 편집은 이름·영문명·가격·순서·사용 여부만 — 나머지 칸은 지금 값 그대로여야 한다
            Map<String, Object> fixed = new LinkedHashMap<>();
            fixed.put("usageType", usage);
            fixed.put("description", description);
            fixed.put("descriptionEn", descriptionEn);
            Map<String, Object> was = Map.of("usageType", current.usageType(), "description", current.description(),
                    "descriptionEn", current.descriptionEn() == null ? "" : current.descriptionEn());
            for (Map.Entry<String, Object> field : fixed.entrySet()) {
                Object now = field.getValue() == null ? "" : field.getValue();
                if (!now.equals(was.get(field.getKey()))) {
                    bad.put(field.getKey(), new MessageRef("pointshop.item.avatar.fixed"));
                }
            }
        }
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        return new ItemValues(creating ? code : current.code(), effective, usage, name, nameEn, description, descriptionEn, color, border,
                creating ? null : current.avatarId(), price, duration, stock, r.saleStartAt(), r.saleEndAt(), r.active(), r.sortOrder());
    }

    private static ItemValues valuesOf(ItemRow row) {
        return new ItemValues(row.code(), row.kind(), row.usageType(), row.name(), row.nameEn(), row.description(), row.descriptionEn(), row.color(), row.borderStyle(),
                row.avatarId(), row.price(), row.durationDays(), row.stock(), row.saleStartAt(), row.saleEndAt(), row.active(), row.sortOrder());
    }

    /** 감사에 담을 바꿀 수 있는 칸(이름 → 값 — 시각은 ISO 문자열) */
    private static Map<String, Object> fields(ItemValues v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("usageType", v.usageType().name());
        map.put("name", v.name());
        map.put("nameEn", v.nameEn());
        map.put("description", v.description());
        map.put("descriptionEn", v.descriptionEn());
        map.put("color", v.color());
        map.put("borderStyle", v.borderStyle());
        map.put("pricePoint", v.price());
        map.put("durationDays", v.durationDays());
        map.put("stock", v.stock());
        map.put("saleStartAt", v.saleStartAt() == null ? null : v.saleStartAt().toString());
        map.put("saleEndAt", v.saleEndAt() == null ? null : v.saleEndAt().toString());
        map.put("active", v.active());
        map.put("sortOrder", v.sortOrder());
        return map;
    }

    /** 글자 → 값(비었거나 모르는 이름이면 null) */
    private static <E extends Enum<E>> E enumOf(Class<E> type, String text) {
        if (text == null) {
            return null;
        }
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(text)) {
                return value;
            }
        }
        return null;
    }

    private static String strip(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    // ── 아바타(결정 14) ─────────────────────────────────────────────────────

    /** 카테고리별 요약 — {@code GET /api/admin/point-shop/avatars/summary} */
    public PointShopAvatarSummaryView avatarSummary() {
        Map<String, ItemRow> byAvatar = items.avatarItems();
        List<PointShopAvatarCategorySummaryView> categories = new ArrayList<>();
        int freeCount = 0;
        int paidCount = 0;
        int forSaleAll = 0;
        IntSummaryStatistics allPrices = new IntSummaryStatistics();
        for (AvatarCatalog.Category category : avatars.categories()) {
            if (category.free()) {
                freeCount += category.count();
                categories.add(new PointShopAvatarCategorySummaryView(category.id(), true, category.count(), 0, 0, null, null));
                continue;
            }
            paidCount += category.count();
            List<ItemRow> rows = avatars.all().stream().filter(a -> a.category().equals(category.id())).map(a -> byAvatar.get(a.id())).filter(Objects::nonNull).toList();
            int forSale = (int) rows.stream().filter(ItemRow::active).count();
            forSaleAll += forSale;
            IntSummaryStatistics prices = rows.stream().mapToInt(ItemRow::price).summaryStatistics();
            rows.forEach(row -> allPrices.accept(row.price()));
            categories.add(new PointShopAvatarCategorySummaryView(category.id(), false, category.count(), forSale, category.count() - forSale,
                    rows.isEmpty() ? null : prices.getMin(), rows.isEmpty() ? null : prices.getMax()));
        }
        return new PointShopAvatarSummaryView(categories, new PointShopAvatarTotalsView(freeCount + paidCount, freeCount, paidCount, forSaleAll, paidCount - forSaleAll,
                allPrices.getCount() == 0 ? null : allPrices.getMin(), allPrices.getCount() == 0 ? null : allPrices.getMax()));
    }

    /**
     * 아바타 묶음 가격 — {@code PUT /api/admin/point-shop/avatars/price}. 이미 산 사람의 가격(구매 사본)은 그대로다.
     *
     * @throws ValidationException 범위·가격({@code fields.scope|categories|itemIds|price})
     */
    @Transactional
    public PointShopAvatarBulkResultView avatarPrice(PointShopAvatarTarget target, Integer price, long operatorId, String ip) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        List<Long> matched = resolveTarget(target, bad);
        if (price == null || price < PRICE_MIN || price > PRICE_MAX) {
            bad.put("price", new MessageRef("pointshop.item.price.range", PRICE_MIN, PRICE_MAX));
        }
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        int changed = items.setAvatarPrice(matched, price, operatorId, now());
        audit.record(bulkEvent(AuditActions.POINT_SHOP_AVATAR_PRICE, target, matched.size(), changed, operatorId, ip).with("price", price));
        return new PointShopAvatarBulkResultView(target.scope(), matched.size(), changed, price, null);
    }

    /**
     * 아바타 묶음 판매 켜기·끄기 — {@code POST /api/admin/point-shop/avatars/sale}. 판매를 꺼도 이미 산 사람은 계속 고를 수 있다.
     *
     * @throws ValidationException 범위·값({@code fields.scope|categories|itemIds|active})
     */
    @Transactional
    public PointShopAvatarBulkResultView avatarSale(PointShopAvatarTarget target, Boolean active, long operatorId, String ip) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        List<Long> matched = resolveTarget(target, bad);
        if (active == null) {
            bad.put("active", new MessageRef("pointshop.item.active.required"));
        }
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        int changed = items.setAvatarActive(matched, active, operatorId, now());
        audit.record(bulkEvent(AuditActions.POINT_SHOP_AVATAR_SALE, target, matched.size(), changed, operatorId, ip).with("active", active));
        return new PointShopAvatarBulkResultView(target.scope(), matched.size(), changed, null, active);
    }

    private AuditEvent bulkEvent(String action, PointShopAvatarTarget target, int matched, int changed, long operatorId, String ip) {
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, action, AuditResult.SUCCESS).target(TARGET_AVATARS, target.scope().name())
                .with("scope", target.scope().name()).with("matched", matched).with("changed", changed);
        if (target.scope() == PointShopAvatarScope.CATEGORIES) {
            event = event.with("categories", List.copyOf(target.categories()));
        } else if (target.scope() == PointShopAvatarScope.ITEMS) {
            event = event.with("itemCount", target.itemIds().size());
        }
        return event;
    }

    /** 범위 → 아바타 품목 ID(차례 = 카탈로그 차례·요청 차례). 위반은 {@code bad} 에 */
    private List<Long> resolveTarget(PointShopAvatarTarget target, Map<String, MessageRef> bad) {
        if (target == null || target.scope() == null) {
            bad.put("scope", new MessageRef("pointshop.avatar.scope.required"));
            return List.of();
        }
        Map<String, ItemRow> byAvatar = items.avatarItems();
        List<String> categories = target.categories() == null ? List.of() : target.categories();
        List<String> itemIds = target.itemIds() == null ? List.of() : target.itemIds();
        switch (target.scope()) {
            case ALL -> {
                if (!categories.isEmpty()) {
                    bad.put("categories", new MessageRef("pointshop.avatar.scope.extra"));
                }
                if (!itemIds.isEmpty()) {
                    bad.put("itemIds", new MessageRef("pointshop.avatar.scope.extra"));
                }
                return avatars.paid().stream().map(a -> byAvatar.get(a.id())).filter(Objects::nonNull).map(ItemRow::id).toList();
            }
            case CATEGORIES -> {
                if (!itemIds.isEmpty()) {
                    bad.put("itemIds", new MessageRef("pointshop.avatar.scope.extra"));
                }
                if (categories.isEmpty()) {
                    bad.put("categories", new MessageRef("pointshop.avatar.categories.required"));
                    return List.of();
                }
                for (String id : categories) {
                    var category = avatars.category(id);
                    if (category.isEmpty()) {
                        bad.put("categories", new MessageRef("pointshop.avatar.categories.unknown"));
                        return List.of();
                    }
                    if (category.get().free()) {
                        bad.put("categories", new MessageRef("pointshop.avatar.categories.free"));
                        return List.of();
                    }
                }
                Set<String> wanted = Set.copyOf(categories);
                return avatars.paid().stream().filter(a -> wanted.contains(a.category())).map(a -> byAvatar.get(a.id())).filter(Objects::nonNull).map(ItemRow::id)
                        .toList();
            }
            case ITEMS -> {
                if (!categories.isEmpty()) {
                    bad.put("categories", new MessageRef("pointshop.avatar.scope.extra"));
                }
                if (itemIds.isEmpty() || itemIds.size() > AVATAR_BULK_MAX) {
                    bad.put("itemIds", new MessageRef("pointshop.avatar.itemIds.size", 1, AVATAR_BULK_MAX));
                    return List.of();
                }
                Set<Long> avatarItemIds = new java.util.HashSet<>();
                byAvatar.values().forEach(row -> avatarItemIds.add(row.id()));
                Set<Long> out = new LinkedHashSet<>();
                for (String raw : itemIds) {
                    Long id = parseId(raw);
                    if (id == null) {
                        throw new PointShopItemNotFoundException(); // 숫자가 아닌 번호는 없는 품목과 같다
                    }
                    out.add(id);
                }
                Set<Long> existing = new java.util.HashSet<>();
                items.byIds(out).forEach(row -> existing.add(row.id()));
                if (!existing.containsAll(out)) {
                    throw new PointShopItemNotFoundException(); // 없는 품목이 섞였다 — 404, 아무것도 바꾸지 않는다(계약)
                }
                if (!avatarItemIds.containsAll(out)) {
                    bad.put("itemIds", new MessageRef("pointshop.avatar.itemIds.invalid"));
                    return List.of();
                }
                return List.copyOf(out);
            }
            default -> throw new IllegalStateException("모르는 범위: " + target.scope());
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || !raw.matches("\\d{1,19}")) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── 설정 ────────────────────────────────────────────────────────────────

    /** {@code GET /api/admin/point-shop/settings} */
    public PointShopSettingsView settings() {
        SettingsRow row = items.settings();
        return new PointShopSettingsView(row.refundDays(), PointShopSettingEntity.DEFAULT_REFUND_DAYS, row.updatedAt(), row.updatedByName());
    }

    /**
     * 셀프 환불 기한을 바꾼다 — 이미 산 구매도 바로 새 값으로 판정한다. 같은 값이면 감사 없이 그대로.
     *
     * @throws ValidationException 1~30 이 아니다({@code fields.refundDays})
     */
    @Transactional
    public PointShopSettingsView updateSettings(Integer refundDays, long operatorId, String ip) {
        if (refundDays == null || refundDays < PointShopSettingEntity.REFUND_DAYS_MIN || refundDays > PointShopSettingEntity.REFUND_DAYS_MAX) {
            throw ValidationException.of("refundDays", new MessageRef("pointshop.settings.refundDays.range", PointShopSettingEntity.REFUND_DAYS_MIN,
                    PointShopSettingEntity.REFUND_DAYS_MAX));
        }
        int before = items.lockRefundDays();
        if (before != refundDays) {
            items.updateRefundDays(refundDays, operatorId, now());
            audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.POINT_SHOP_SETTINGS_UPDATE, AuditResult.SUCCESS).target(TARGET_SETTINGS, "1")
                    .with("before", Map.of("refundDays", before)).with("after", Map.of("refundDays", refundDays)));
        }
        return settings();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
