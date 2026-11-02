package com.community.ad.service;

import com.community.ad.dto.AdAdminView;
import com.community.ad.dto.AdAdvertiserAdminRequest;
import com.community.ad.dto.AdAdvertiserAdminView;
import com.community.ad.repository.master.AdAdvertiserAdminStore;
import com.community.ad.repository.master.AdAdvertiserAdminStore.AppLite;
import com.community.ad.repository.master.AdAdvertiserAdminStore.BusinessRow;
import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizFields;
import com.community.business.service.BusinessFields;
import com.community.business.service.BusinessPii;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 광고주 관리(admin-web {@code /ad-advertisers}, API {@code /api/admin/ad-advertisers}, docs/features/ad-advertiser-contract.md) — 목록·상세 만들기. 쓰기(만들기·고치기·보관·삭제)는
 * shared {@link AdAdvertiserService}, 권한은 컨트롤러가 본다(조회 {@code ad.read} · 처리 {@code ad.manage}).
 *
 * <ul>
 *   <li><b>한 줄</b>(규칙 1·2): 직접 등록 광고주 {@code adv-<id>} + 사업자 {@code biz-<신원>}(광고주 줄이 붙었거나 · 신청 건이 있거나 · AD 서비스 줄이 있는 신원). 건의 묶음 키는
 *       {@link AppLite#key()}.</li>
 *   <li><b>건 수·돈·최근 광고일</b>(규칙 3~5): 묶음 키의 모든 신청 건. 결제·환불은 신청 건 상세 {@code refundSummary} 와 같은 원천.</li>
 *   <li><b>연락처</b>(규칙 8): 가린 값만 — 사업자는 광고 담당자 칸이 하나라도 있으면 그것, 없으면 신원 대표자·연락처·이메일을 풀어 가린다(데이터 키가 없으면 비운다).</li>
 * </ul>
 */
@Service
public class AdAdvertiserAdminService {

    static final int MAX_PAGE_SIZE = 100;
    static final int BOOKINGS = 50;
    static final int VIEWS = 20;
    static final int HISTORY = 100;
    static final List<String> SORTS = List.of("lastAdAt", "paidTotal", "name");
    private static final Set<String> WAITING = Set.of("SUBMITTED", "AWAITING_PAYMENT", "AWAITING_DEPOSIT");
    /** 결제 뒤 건(예약·진행·종료·내림) — 최근 광고일 재료 */
    private static final Set<String> PAID_PHASE = Set.of("SCHEDULED", "TAKEN_DOWN");

    private final AdAdvertiserAdminStore store;
    private final AdAdvertiserStore advertiserStore;
    private final AdAdvertiserService advertisers;
    private final BizApplicationStore apps;
    private final BizApplicationReader reader;
    private final DataCipher cipher;
    private final Messages messages;

    public AdAdvertiserAdminService(AdAdvertiserAdminStore store, AdAdvertiserStore advertiserStore, AdAdvertiserService advertisers, BizApplicationStore apps,
                                    BizApplicationReader reader, DataCipher cipher, Messages messages) {
        this.store = store;
        this.advertiserStore = advertiserStore;
        this.advertisers = advertisers;
        this.apps = apps;
        this.reader = reader;
        this.cipher = cipher;
        this.messages = messages;
    }

    /** 묶음 키 하나의 합 */
    record Agg(int running, int scheduled, int waiting, int total, long paid, long online, long manual, long refunded, Instant lastAdAt) {

        AdAdvertiserAdminView.Counts counts() {
            return new AdAdvertiserAdminView.Counts(running, scheduled, waiting, total);
        }

        int open() {
            return running + scheduled + waiting;
        }
    }

    /** 목록 재료 한 줄 — 직접 등록이면 {@code row}, 사업자면 {@code biz} + 붙은 광고주 줄({@code row}, 없을 수 있다) */
    private record Entry(String id, String kind, String name, AdvertiserRow row, BusinessRow biz, boolean archived, Agg agg, Instant createdAt) {
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    /**
     * 목록 — 종류 {@code kind}(BUSINESS·ADVERTISER) · 상태 {@code state}(ACTIVE 기본·ARCHIVED·ALL — 직접 등록에만 걸리고 사업자는 늘 ACTIVE 쪽) · 찾기 {@code q}(이름·상호 포함,
     * 숫자면 등록번호 끝자리) · 정렬 {@code sort}(lastAdAt 기본·paidTotal·name)·{@code dir}(desc 기본) · 쪽 {@code page} 0부터·{@code size} 기본 20 최대 100. 틀리면 400
     * {@code fields.<칸>}. 최근 광고일이 없으면 방향과 상관없이 뒤, 같으면 만든 시각 내림.
     */
    public AdAdvertiserAdminView.Page list(String kindText, String stateText, String q, String sortText, String dirText, String pageText, String sizeText) {
        BizFields f = BizFields.start();
        String kind = BizFields.strip(kindText) == null ? null : f.oneOf("kind", kindText, s -> s.equals("BUSINESS") || s.equals("ADVERTISER") ? s : null);
        String state = BizFields.strip(stateText) == null ? "ACTIVE"
                : f.oneOf("state", stateText, s -> s.equals("ACTIVE") || s.equals("ARCHIVED") || s.equals("ALL") ? s : null);
        String sort = BizFields.strip(sortText) == null ? "lastAdAt" : f.oneOf("sort", sortText, s -> SORTS.contains(s) ? s : null);
        String dir = BizFields.strip(dirText) == null ? "desc" : f.oneOf("dir", dirText, s -> s.equals("asc") || s.equals("desc") ? s : null);
        int page = number(f, "page", pageText, 0, 0, Integer.MAX_VALUE);
        int size = number(f, "size", sizeText, 20, 1, MAX_PAGE_SIZE);
        String query = BizFields.strip(q);
        if (query != null && query.length() > 60) {
            f.add("q", "bizapp.field.tooLong", 60);
        }
        f.throwIfAny();
        Instant now = reader.now();

        Map<String, List<AppLite>> byKey = new HashMap<>();
        for (AppLite a : store.apps()) {
            String key = a.key();
            if (key != null) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
            }
        }
        Map<Long, AdvertiserRow> linked = new HashMap<>();
        List<Entry> entries = new ArrayList<>();
        for (AdvertiserRow r : store.advertisers()) {
            if (r.businessProfileId() != null) {
                linked.put(r.businessProfileId(), r);
                continue;
            }
            String id = "adv-" + r.id();
            entries.add(new Entry(id, "ADVERTISER", r.name(), r, null, r.archivedAt() != null, aggregate(byKey.getOrDefault(id, List.of()), now), r.createdAt()));
        }
        for (BusinessRow b : store.businesses()) {
            String id = "biz-" + b.id();
            entries.add(new Entry(id, "BUSINESS", b.companyName(), linked.get(b.id()), b, false, aggregate(byKey.getOrDefault(id, List.of()), now), b.createdAt()));
        }

        List<Entry> filtered = entries.stream().filter(e -> switch (state) {
            case "ARCHIVED" -> e.archived();
            case "ALL" -> true;
            default -> !e.archived();
        }).filter(e -> matches(e, query)).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", (long) filtered.size());
        counts.put("BUSINESS", filtered.stream().filter(e -> e.kind().equals("BUSINESS")).count());
        counts.put("ADVERTISER", filtered.stream().filter(e -> e.kind().equals("ADVERTISER")).count());
        List<Entry> rows = new ArrayList<>(filtered.stream().filter(e -> kind == null || kind.equals(e.kind())).toList());
        rows.sort(order(sort, "asc".equals(dir)));
        int from = (int) Math.min(rows.size(), (long) page * size);
        List<AdAdvertiserAdminView.Row> items = rows.subList(from, Math.min(rows.size(), from + size)).stream().map(this::row).toList();
        return new AdAdvertiserAdminView.Page(items, rows.size(), counts);
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    /** 상세 — 별칭 {@code adv-}(사업자에 붙은 광고주 줄)는 {@code biz-} 로 돌려준다. 없거나 목록에 나오지 않는 사업자는 404 {@code AD_ADVERTISER_NOT_FOUND} */
    public AdAdvertiserAdminView.Detail detail(String idText) {
        AdAdvertiserService.Target t = advertisers.resolve(idText);
        Instant now = reader.now();
        Entry e;
        List<AppLite> list;
        if (t.business()) {
            BusinessRow b = store.business(t.profileId()).orElseThrow(BizApplicationException::advertiserNotFound);
            list = store.businessApps(b.id());
            e = new Entry("biz-" + b.id(), "BUSINESS", b.companyName(), t.advertiser(), b, false, aggregate(list, now), b.createdAt());
        } else {
            AdvertiserRow r = t.advertiser();
            list = store.advertiserApps(r.id());
            e = new Entry("adv-" + r.id(), "ADVERTISER", r.name(), r, null, r.archivedAt() != null, aggregate(list, now), r.createdAt());
        }
        AdAdvertiserAdminView.Row row = row(e);

        // 가린 값과 원천
        AdAdvertiserAdminView.Masked masked;
        String source;
        if (e.row() != null && e.row().hasContact()) {
            masked = new AdAdvertiserAdminView.Masked(e.row().contactNameMasked(), e.row().contactPhoneMasked(), e.row().emailMasked());
            source = "ADVERTISER";
        } else if (e.biz() != null) {
            masked = new AdAdvertiserAdminView.Masked(mask(BusinessPii.OWNER, e.biz().ownerNameEnc(), BusinessFields::maskName),
                    mask(BusinessPii.PHONE, e.biz().phoneEnc(), BusinessFields::maskPhone), mask(BusinessPii.EMAIL, e.biz().emailEnc(), BusinessFields::maskEmail));
            source = masked.contactName() != null || masked.contactPhone() != null || masked.email() != null ? "BUSINESS" : null;
        } else {
            masked = new AdAdvertiserAdminView.Masked(null, null, null);
            source = null;
        }

        List<AdAdvertiserAdminView.Booking> bookings = bookings(list, now);
        Agg g = e.agg();
        AdAdvertiserAdminView.Money money = new AdAdvertiserAdminView.Money(g.paid(), g.online(), g.manual(), g.refunded());
        Long advertiserId = e.row() == null ? null : e.row().id();
        String unknown = messages.get(new MessageRef("business.history.actor.UNKNOWN"));
        List<AdAdvertiserAdminView.View> views = advertiserStore.views(advertiserId, e.biz() == null ? null : e.biz().id(), VIEWS).stream()
                .map(v -> new AdAdvertiserAdminView.View(v.at(), v.operatorName() == null ? unknown : v.operatorName(), v.reason())).toList();
        List<AdAdvertiserAdminView.Event> history = advertiserId == null ? List.of() : advertiserStore.events(advertiserId, HISTORY).stream()
                .map(v -> new AdAdvertiserAdminView.Event(v.at(), v.action(), v.actorName() == null ? unknown : v.actorName(), v.fields())).toList();
        boolean direct = e.kind().equals("ADVERTISER");
        return new AdAdvertiserAdminView.Detail(row.id(), row.kind(), row.name(), row.contactMasked(), row.business(), row.state(), row.counts(), row.paidTotal(),
                row.lastAdAt(), row.createdAt(), masked, source, e.row() == null ? null : e.row().memo(), bookings, money, views, history,
                direct && g.total() == 0, direct && g.open() == 0);
    }

    // ── 처리 ────────────────────────────────────────────────────────────────

    public AdAdvertiserAdminView.Detail create(AdminActor actor, AdAdvertiserAdminRequest.Create body) {
        AdAdvertiserAdminRequest.Create b = body == null ? new AdAdvertiserAdminRequest.Create(null, null, null, null, null) : body;
        AdvertiserRow created = advertisers.createRow(op(actor), new AdAdvertiserService.Create(b.name(), b.contactName(), b.contactPhone(), b.email(), b.memo(), null));
        return detail("adv-" + created.id());
    }

    public AdAdvertiserAdminView.Detail update(AdminActor actor, String id, AdAdvertiserAdminRequest.Update body) {
        AdAdvertiserAdminRequest.Update b = body == null ? new AdAdvertiserAdminRequest.Update(null, null, null, null, null) : body;
        String key = advertisers.update(op(actor), id, new AdAdvertiserService.Update(b.name(), b.contactName(), b.contactPhone(), b.email(), b.memo()));
        return detail(key);
    }

    public AdAdvertiserAdminView.Detail setArchived(AdminActor actor, String id, boolean archived) {
        return detail(advertisers.setArchived(op(actor), id, archived));
    }

    public void delete(AdminActor actor, String id) {
        advertisers.delete(op(actor), id);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    /** 묶음 키의 건 수(규칙 3) · 돈(규칙 4) · 최근 광고일(규칙 5) */
    static Agg aggregate(List<AppLite> list, Instant now) {
        int running = 0;
        int scheduled = 0;
        int waiting = 0;
        long paid = 0;
        long online = 0;
        long manual = 0;
        long refunded = 0;
        Instant last = null;
        for (AppLite a : list) {
            if ("SCHEDULED".equals(a.status())) {
                if (a.startAt().isAfter(now)) {
                    scheduled++;
                } else if (a.endAt().isAfter(now)) {
                    running++;
                }
            } else if (WAITING.contains(a.status())) {
                waiting++;
            }
            if (a.paid() != null) {
                paid += a.paid();
                if ("ONLINE".equals(a.paymentMethod())) {
                    online += a.paid();
                } else {
                    manual += a.paid();
                }
                refunded += a.refunded();
            }
            if (PAID_PHASE.contains(a.status()) && !a.startAt().isAfter(now) && (last == null || a.startAt().isAfter(last))) {
                last = a.startAt();
            }
        }
        return new Agg(running, scheduled, waiting, list.size(), paid, online, manual, refunded, last);
    }

    /** 찾기 — 이름(상호) 포함(대소문자 무시) · 숫자 1~4자리면 사업자 등록번호 끝자리 */
    private static boolean matches(Entry e, String q) {
        if (q == null) {
            return true;
        }
        if (e.name() != null && e.name().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return e.biz() != null && q.matches("[0-9]{1,4}") && e.biz().bizNoLast4() != null && e.biz().bizNoLast4().endsWith(q);
    }

    /** 정렬 — 최근 광고일(없으면 방향과 상관없이 뒤) · 누적 결제 · 이름(가나다), 같으면 만든 시각 내림 → ID */
    private static Comparator<Entry> order(String sort, boolean asc) {
        Comparator<Entry> tie = Comparator.comparing(Entry::createdAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Entry::id);
        Comparator<Entry> value = switch (sort) {
            case "paidTotal" -> Comparator.comparingLong(e -> e.agg().paid());
            case "name" -> {
                Collator collator = Collator.getInstance(Locale.KOREAN);
                yield (x, y) -> collator.compare(Objects.toString(x.name(), ""), Objects.toString(y.name(), ""));
            }
            default -> null;
        };
        if (value == null) {
            Comparator<Instant> by = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
            return Comparator.comparing((Entry e) -> e.agg().lastAdAt() == null).thenComparing(e -> e.agg().lastAdAt(), Comparator.nullsLast(by)).thenComparing(tie);
        }
        return (asc ? value : value.reversed()).thenComparing(tie);
    }

    private AdAdvertiserAdminView.Row row(Entry e) {
        AdAdvertiserAdminView.Business business = e.biz() == null ? null : new AdAdvertiserAdminView.Business(Long.toString(e.biz().id()), e.biz().companyName(),
                BusinessFields.maskBizNo(e.biz().bizNoLast4()), e.biz().status(), e.biz().adService());
        return new AdAdvertiserAdminView.Row(e.id(), e.kind(), e.name(), contactMasked(e), business, e.archived() ? "ARCHIVED" : "ACTIVE", e.agg().counts(), e.agg().paid(),
                e.agg().lastAdAt(), e.createdAt());
    }

    /** 목록의 가린 연락처 — 광고 담당자 칸(연락처 → 없으면 이메일) · 사업자이고 담당자 칸이 없으면 신원 연락처 → 이메일 */
    private String contactMasked(Entry e) {
        if (e.row() != null && (e.biz() == null || e.row().hasContact())) {
            return e.row().contactPhoneMasked() != null ? e.row().contactPhoneMasked() : e.row().emailMasked();
        }
        if (e.biz() == null) {
            return null;
        }
        String phone = mask(BusinessPii.PHONE, e.biz().phoneEnc(), BusinessFields::maskPhone);
        return phone != null ? phone : mask(BusinessPii.EMAIL, e.biz().emailEnc(), BusinessFields::maskEmail);
    }

    /** 사업자 신원 칸을 풀어 가린다 — 칸이 없거나 데이터 키가 없으면 null */
    private String mask(DataPurpose purpose, String cipherText, java.util.function.UnaryOperator<String> masker) {
        if (cipherText == null || !cipher.isConfigured()) {
            return null;
        }
        String plain = cipher.decrypt(purpose, cipherText);
        return plain == null || plain.isBlank() ? null : masker.apply(plain);
    }

    /** 광고 건 — 장부 유형 줄이 있는 건만(일정표 서랍으로 가는 줄), 시작이 늦은 것 먼저 최대 50 */
    private List<AdAdvertiserAdminView.Booking> bookings(List<AppLite> list, Instant now) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, AppLite> lite = new HashMap<>();
        list.forEach(a -> lite.put(a.id(), a));
        List<AppRow> rows = apps.findAll(lite.keySet());
        Map<Long, BizProduct> typeOf = new HashMap<>();
        reader.summaries(rows).forEach(s -> {
            BizProduct type = BizApplicationReader.capacityProduct(s.items());
            if (type != null) {
                typeOf.put(s.app().id(), type);
            }
        });
        List<Long> advertiserIds = rows.stream().map(AppRow::advertiserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, AdvertiserRow> advs = advertiserIds.isEmpty() ? Map.of() : advertiserStore.findAll(advertiserIds);
        return rows.stream().filter(a -> typeOf.containsKey(a.id()))
                .sorted(Comparator.comparing(AppRow::startAt).reversed().thenComparing(Comparator.comparingLong(AppRow::id).reversed())).limit(BOOKINGS)
                .map(a -> {
                    AdAdminView.Row r = AdAdminService.row(typeOf.get(a.id()), a, AdAdminService.advertiserOf(a, a.advertiserId() == null ? null : advs.get(a.advertiserId())),
                            now);
                    AppLite l = lite.get(a.id());
                    return AdAdvertiserAdminView.Booking.of(r, l.paid() == null ? 0 : l.paid(), l.refunded());
                }).toList();
    }

    private static int number(BizFields f, String field, String text, int fallback, int min, int max) {
        String s = BizFields.strip(text);
        if (s == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(s);
            if (n >= min && n <= max) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 오류
        }
        f.add(field, "bizapp.value.invalid");
        return fallback;
    }

    private static BizOperator op(AdminActor actor) {
        return new BizOperator(actor.operatorId(), actor.ip());
    }
}
