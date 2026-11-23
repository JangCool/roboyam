package com.community.ad.service;

import com.community.ad.dto.AdAdminView;
import com.community.ad.dto.AdPlacements;
import com.community.ad.dto.AdSenseSettings;
import com.community.ad.dto.AdSlotAdminView;
import com.community.ad.dto.AdSlotTakedownRequest;
import com.community.ad.dto.AdStatsView;
import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.ad.repository.master.AdAdvertiserStore.AdvertiserRow;
import com.community.ad.repository.master.AdLedgerStore;
import com.community.ad.repository.master.AdPlacementStore;
import com.community.ad.repository.master.AdStatsStore;
import com.community.ad.repository.master.AdStatsStore.StatRow;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.CreativeRow;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizFields;
import com.community.bizapplication.service.BizPeriods;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고 자리 관리(admin-web {@code /ads/slots}, API {@code /api/admin/ads/slots}·{@code /api/admin/ads/takedown}, docs/features/ad-slot-manager-contract.md 「API」).
 * 광고는 종류(상품)마다 관리된다 — 종류는 일정표 유형({@link AdAdminService#TYPES}) 그대로. 권한(조회 {@code ad.read} · 내림 {@code ad.takedown})은 컨트롤러가 본다.
 *
 * <ul>
 *   <li><b>요약</b>: {@code running}·{@code limit} 은 일정표 유형 요약({@link AdAdminService#summary}) 그대로. 애드센스 FILLS = 「광고 표시」 단계 + 게시자 ID + 그 종류의 디스플레이 단위
 *       ID(배너 가젯 다섯 · 날개), 아니면 EMPTY, 한 줄·핫딜 노출 자리는 NA(계약). 나가는 곳은 노출 자리 설정을 따른다.</li>
 *   <li><b>목록</b>: 지금 자리를 차지한 건({@link AdLedgerStore#live} — 일정표 요약과 같은 대상) 중 보이는 상태가 진행 중 · 예약 · 결제·입금 대기 · 내림인 건. 거르기
 *       {@code AWAITING_DEPOSIT} 은 결제 대기도 함께. 7일 성과는 광고 성과 일별 집계({@link AdStatsStore#rows} — 같은 방문자 하루 1번, 봇 제외)의 오늘 포함 최근 7일(KST) 합.</li>
 *   <li><b>여러 건 내림</b>: 건마다 단건 긴급 내림({@link AdAdminService#takedownOne} → shared {@link AdOperatorService#takedown}, 자기 트랜잭션)을 따로 부른다 — 이 서비스는
 *       트랜잭션을 열지 않는다(열면 건마다 트랜잭션이 바깥에 참여해 한 건의 실패가 앞서 내린 건까지 되돌린다). 실패한 건은 그 오류의 문구로 {@code failed} 에 담는다.</li>
 * </ul>
 */
@Service
public class AdSlotAdminService {

    private static final Logger log = LoggerFactory.getLogger(AdSlotAdminService.class);

    /** 한 번에 내리는 최대 건수 */
    static final int TAKEDOWN_MAX = 50;
    /** 7일 성과 — 오늘 포함 */
    static final int RECENT_DAYS = 7;
    /** 거르기 값(빈 값 = 전체) */
    static final List<String> FILTERS = List.of("RUNNING", "SCHEDULED", "AWAITING_DEPOSIT", "TAKEN_DOWN");
    /** 보이는 상태의 차례 — 진행 중 → 예약 → 결제·입금 대기 → 내림(그 밖은 목록에 들지 않는다) */
    static final Map<String, Integer> ORDER = Map.of("RUNNING", 0, "SCHEDULED", 1, "AWAITING_PAYMENT", 2, "AWAITING_DEPOSIT", 2, "TAKEN_DOWN", 3);
    /** 가젯 자리에 들어가는 종류(배너 다섯 · 한 줄) — 홈 · 가젯 페이지 · 게시판 가젯 */
    static final Set<BizProduct> GADGET = Set.of(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER, BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER,
            BizProduct.HALF_BANNER, BizProduct.TEXT_LINE);
    /** 애드센스 디스플레이 단위가 있는 종류(배너 가젯 다섯 · 날개) */
    static final Set<BizProduct> ADSENSE = Set.of(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER, BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER,
            BizProduct.HALF_BANNER, BizProduct.WING_BANNER);
    /** 예시·시험용 호스트 — 이 이름이거나 그 하위 */
    static final List<String> EXAMPLE_DOMAINS = List.of("example.com", "example.org", "example.net", "localhost");
    /** 예시·시험용 최상위 이름(RFC 2606·6761) */
    static final List<String> EXAMPLE_TLDS = List.of("example", "test", "invalid", "localhost");

    private final AdAdminService ads;
    private final AdLedgerStore ledger;
    private final AdAdvertiserStore advertisers;
    private final AdPlacementStore placements;
    private final AdStatsStore stats;
    private final BizApplicationReader reader;
    private final Messages messages;

    public AdSlotAdminService(AdAdminService ads, AdLedgerStore ledger, AdAdvertiserStore advertisers, AdPlacementStore placements, AdStatsStore stats,
                              BizApplicationReader reader, Messages messages) {
        this.ads = ads;
        this.ledger = ledger;
        this.advertisers = advertisers;
        this.placements = placements;
        this.stats = stats;
        this.reader = reader;
        this.messages = messages;
    }

    // ── 요약 ────────────────────────────────────────────────────────────────

    /** 종류마다 요약(일정표 유형 차례) */
    @Transactional(readOnly = true)
    public AdSlotAdminView.SummaryView summary() {
        Instant now = reader.now();
        AdPlacements setting = placements.current();
        AdSenseSettings adsense = placements.adsense();
        List<AdSlotAdminView.Summary> types = new ArrayList<>();
        for (BizProduct type : AdAdminService.TYPES) {
            AdAdminView.TypeSummary s = ads.summary(type, ledger.live(type, now), now);
            List<String> where = places(type, setting);
            types.add(new AdSlotAdminView.Summary(type.name(), s.running(), s.limit(), adsense(type, where, adsense), where));
        }
        return new AdSlotAdminView.SummaryView(types);
    }

    /**
     * 빈 자리를 애드센스가 채우는가 — 그 종류가 나가는 자리({@code places})마다 공개 화면과 같은 규칙으로 본다(계약 「미확정 → 추천 확정」 2026-11-23).
     * <ul>
     *   <li>애드센스 틀이 있는 자리: 배너 가젯(HOME·PAGE·BOARD) = 그 종류 단위, 한 줄 광고 가젯(HOME·PAGE·BOARD — 2026-11-23 사용자) = 목록 한 줄과 같은 인피드,
     *       글 보기 중간 = {@code POST_INLINE} 단위(종류와 상관없이), 목록 한 줄 = 인피드 단위 + 레이아웃 키, 날개 = 날개 단위로 <b>한쪽만</b>(오른쪽 먼저 — 양쪽을 켜면
     *       한쪽은 빈다). 핫딜 자리는 틀이 없다.</li>
     *   <li>틀 있는 자리가 하나도 없으면 NA. 「광고 표시」 단계 + 게시자 ID 가 아니거나 채우는 자리가 0이면 EMPTY, 나가는 자리를 다 채우면 FILLS, 일부만이면 PARTIAL.</li>
     * </ul>
     */
    static String adsense(BizProduct type, List<String> places, AdSenseSettings settings) {
        boolean on = AdSenseSettings.SHOW.equals(settings.mode()) && settings.activeClient().isPresent();
        AdSenseSettings.Units u = settings.units();
        int capable = 0;
        int filled = 0;
        boolean feed = on && u.boardList().slot() != null && u.boardList().layoutKey() != null;
        boolean wingFilled = false;
        for (String place : places) {
            switch (place) {
                case "HOME", "PAGE", "BOARD" -> {
                    if (type == BizProduct.TEXT_LINE) {
                        capable++;
                        filled += feed ? 1 : 0; // 한 줄 광고 가젯 — 장부 0건이면 목록 한 줄 인피드 하나
                    } else if (ADSENSE.contains(type)) {
                        capable++;
                        filled += on && u.display(type.name()) != null ? 1 : 0;
                    }
                }
                case "POST_INLINE" -> {
                    capable++;
                    filled += on && u.display("POST_INLINE") != null ? 1 : 0;
                }
                case "BOARD_LIST" -> {
                    capable++;
                    filled += feed ? 1 : 0;
                }
                case "WING_LEFT", "WING_RIGHT" -> {
                    capable++;
                    if (on && !wingFilled && u.display("WING_BANNER") != null) {
                        wingFilled = true; // 날개 애드센스는 한쪽만
                        filled++;
                    }
                }
                default -> {
                    // 핫딜 노출 자리 — 애드센스 틀 없음
                }
            }
        }
        if (capable == 0) {
            return "NA";
        }
        if (filled == 0) {
            return "EMPTY";
        }
        return filled == places.size() ? "FILLS" : "PARTIAL";
    }

    /** 이 종류가 나가는 노출 자리(광고 성과 자리 이름 차례) — 글 보기 중간은 설정한 유형만, 날개는 켠 쪽만, 목록 한 줄은 켰을 때만 */
    static List<String> places(BizProduct type, AdPlacements setting) {
        List<String> out = new ArrayList<>();
        if (GADGET.contains(type)) {
            out.addAll(List.of("HOME", "PAGE", "BOARD"));
        }
        if (setting.postInline().enabled() && type.name().equals(setting.postInline().adType())) {
            out.add("POST_INLINE");
        }
        if (type == BizProduct.TEXT_LINE && setting.boardList().enabled()) {
            out.add("BOARD_LIST");
        }
        if (type == BizProduct.WING_BANNER) {
            if (setting.wings().left()) {
                out.add("WING_LEFT");
            }
            if (setting.wings().right()) {
                out.add("WING_RIGHT");
            }
        }
        if (type == BizProduct.HOTDEAL_TOP || type == BizProduct.HOTDEAL_CATEGORY) {
            out.add(type.name());
        }
        return out;
    }

    // ── 목록 ────────────────────────────────────────────────────────────────

    /** 그 종류의 광고 카드 — 유형 400 {@code fields.type}, 거르기 400 {@code fields.status} */
    @Transactional(readOnly = true)
    public AdSlotAdminView.ListView list(String typeText, String statusText) {
        BizFields f = BizFields.start();
        BizProduct type = f.oneOf("type", typeText, s -> {
            BizProduct p = BizProduct.valueOf(s);
            return AdAdminService.TYPES.contains(p) ? p : null;
        });
        String status = BizFields.strip(statusText);
        if (status != null && !status.equals("ALL") && !FILTERS.contains(status)) {
            f.add("status", "bizapp.value.invalid");
        }
        f.throwIfAny();
        String filter = status == null || status.equals("ALL") ? null : status;
        Instant now = reader.now();
        record Live(AppRow app, String status) {
        }
        List<Live> live = new ArrayList<>();
        for (AppRow a : ledger.live(type, now)) {
            String s = BizApplicationReader.adminStatus(a, now);
            if (ORDER.containsKey(s)) {
                live.add(new Live(a, s));
            }
        }
        int running = 0;
        int scheduled = 0;
        int awaiting = 0;
        int takenDown = 0;
        for (Live l : live) {
            switch (filterOf(l.status())) {
                case "RUNNING" -> running++;
                case "SCHEDULED" -> scheduled++;
                case "AWAITING_DEPOSIT" -> awaiting++;
                default -> takenDown++;
            }
        }
        AdSlotAdminView.Counts counts = new AdSlotAdminView.Counts(live.size(), running, scheduled, awaiting, takenDown);
        List<Live> picked = live.stream().filter(l -> filter == null || filter.equals(filterOf(l.status())))
                .sorted(Comparator.comparingInt((Live l) -> ORDER.get(l.status())).thenComparing(l -> l.app().startAt()).thenComparingLong(l -> l.app().id()))
                .toList();
        List<AppRow> apps = picked.stream().map(Live::app).toList();
        List<Long> ids = apps.stream().map(AppRow::id).toList();
        Map<Long, CreativeRow> creatives = ids.isEmpty() ? Map.of() : reader.creatives(ids);
        Map<Long, AdvertiserRow> advs = advertiserRows(apps);
        Map<Long, AdStatsView.Counts> recent = recent(ids, now);
        LocalDate today = ServiceTime.dateOf(now);
        List<AdSlotAdminView.Card> items = new ArrayList<>();
        for (Live l : picked) {
            AppRow a = l.app();
            AdAdvertiserService.Option o = AdAdminService.advertiserOf(a, a.advertiserId() == null ? null : advs.get(a.advertiserId()));
            CreativeRow c = creatives.get(a.id());
            Map<String, Object> content = c == null || c.content() == null ? Map.of() : c.content();
            AdStatsView.Counts n = recent.getOrDefault(a.id(), AdStatsView.Counts.ZERO);
            String link = str(content.get("linkUrl"));
            AdAdminView.Row row = AdAdminService.row(type, a, o, now);
            items.add(new AdSlotAdminView.Card(row.id(), type.name(), title(type, a, content, o), l.status(), row.takedownCause(),
                    type == BizProduct.TEXT_LINE ? null : str(content.get("imageUrl")), type == BizProduct.TEXT_LINE ? str(content.get("text")) : null, row.period(),
                    remainingDays(a, now, today), n.impressions(), n.clicks(), new AdSlotAdminView.Advertiser(o.id(), o.name(), o.kind()), row.paymentMethod(),
                    AdAdminService.amountOf(a), row.domain(), link, exampleUrl(link)));
        }
        return new AdSlotAdminView.ListView(type.name(), items, counts);
    }

    /** 보이는 상태 → 거르기 값(결제 대기는 입금 대기 묶음) */
    static String filterOf(String status) {
        return "AWAITING_PAYMENT".equals(status) ? "AWAITING_DEPOSIT" : status;
    }

    /** 카드 제목 — 한 줄은 광고 글, 핫딜 노출 자리는 딜 제목, 배너는 대체 글 — 없으면 건 제목, 그것도 없으면 광고주 이름 */
    static String title(BizProduct type, AppRow a, Map<String, Object> content, AdAdvertiserService.Option advertiser) {
        String key = type == BizProduct.TEXT_LINE ? "text" : type.placement() ? "title" : "altText";
        String t = BizFields.strip(str(content.get(key)));
        if (t == null) {
            t = BizFields.strip(a.title());
        }
        return t != null ? t : advertiser.name();
    }

    /** 오늘(KST)부터 끝 날짜까지(끝 포함) — 끝났으면 0 */
    static int remainingDays(AppRow a, Instant now, LocalDate today) {
        if (!a.endAt().isAfter(now)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(today, BizPeriods.endDate(a.endAt())) + 1;
        return (int) Math.max(0, days);
    }

    /** 건마다 오늘 포함 최근 7일(KST) 노출·클릭 — 광고 성과 일별 집계 합(버전·자리 합침) */
    private Map<Long, AdStatsView.Counts> recent(List<Long> ids, Instant now) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        LocalDate to = ServiceTime.dateOf(now);
        Map<Long, List<StatRow>> byApp = new HashMap<>();
        for (StatRow r : stats.rows(ids, to.minusDays(RECENT_DAYS - 1L), to)) {
            byApp.computeIfAbsent(r.applicationId(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, AdStatsView.Counts> out = new HashMap<>();
        byApp.forEach((id, rows) -> out.put(id, AdStatsCalc.total(rows)));
        return out;
    }

    /**
     * 링크가 예시·시험용 주소인가 — 호스트가 example.com · example.org · example.net · localhost 이거나 그 하위, 또는 최상위 이름이 {@code .example}·{@code .test}·
     * {@code .invalid}·{@code .localhost}. 호스트를 읽을 수 없는 링크(상대 주소 등)는 false.
     */
    static boolean exampleUrl(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        String host;
        try {
            host = new URI(link.strip()).getHost();
        } catch (URISyntaxException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        for (String d : EXAMPLE_DOMAINS) {
            if (h.equals(d) || h.endsWith("." + d)) {
                return true;
            }
        }
        int dot = h.lastIndexOf('.');
        return dot >= 0 && EXAMPLE_TLDS.contains(h.substring(dot + 1));
    }

    // ── 여러 건 내림 ────────────────────────────────────────────────────────

    /**
     * 여러 건 긴급 내림 — {@code ids} 1~50(같은 ID 는 한 번, 400 {@code fields.ids}), 사유는 단건과 같은 검사(400 {@code fields.reason}). 건마다 단건 긴급 내림을 따로 커밋하고, 실패한 건
     * (없는 건 404 · 내릴 수 없는 상태 409 등)은 그 오류 문구로 {@code failed} 에 담는다. <b>트랜잭션을 열지 않는다</b>(클래스에도 없다).
     */
    public AdSlotAdminView.TakedownResult takedown(AdminActor actor, AdSlotTakedownRequest body) {
        AdSlotTakedownRequest b = body == null ? new AdSlotTakedownRequest(null, null, null) : body;
        BizFields f = BizFields.start();
        Set<String> ids = new LinkedHashSet<>();
        if (b.ids() != null) {
            b.ids().stream().filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            f.add("ids", "ad.slot.ids.required");
        } else if (ids.size() > TAKEDOWN_MAX) {
            f.add("ids", "ad.slot.ids.max", TAKEDOWN_MAX);
        }
        String reason = f.text("reason", b.reason(), 200); // 단건 긴급 내림(AdOperatorService#takedown)과 같은 검사 — 여기서 먼저 막아 건마다 같은 400 이 나지 않게
        f.throwIfAny();
        boolean notify = Boolean.TRUE.equals(b.notifyAdvertiser());
        int done = 0;
        List<AdSlotAdminView.Failure> failed = new ArrayList<>();
        for (String id : ids) {
            try {
                ads.takedownOne(actor, id, reason, notify);
                done++;
            } catch (CommonException e) {
                failed.add(new AdSlotAdminView.Failure(id, message(e)));
            } catch (RuntimeException e) {
                // 예상하지 못한 오류(DB 끊김 등)도 그 건만 실패로 — 앞서 내린 건은 이미 커밋되어 500 으로 끝내면 결과를 잃는다
                log.error("광고 여러 건 내림 중 한 건 실패: id={}", id, e);
                failed.add(new AdSlotAdminView.Failure(id, messages.get(new MessageRef("error." + ErrorCode.INTERNAL.name()))));
            }
        }
        return new AdSlotAdminView.TakedownResult(done, failed);
    }

    /** 오류 응답의 {@code message} 와 같은 문구 — 칸 오류는 첫 칸 문구 */
    private String message(CommonException e) {
        Map<String, MessageRef> fields = e.getFields();
        if (fields != null && !fields.isEmpty()) {
            return messages.get(fields.values().iterator().next());
        }
        return messages.get(e.getMessageRef());
    }

    private Map<Long, AdvertiserRow> advertiserRows(List<AppRow> apps) {
        List<Long> ids = apps.stream().map(AppRow::advertiserId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Collections.emptyMap() : advertisers.findAll(ids);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
