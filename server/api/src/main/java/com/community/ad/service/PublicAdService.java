package com.community.ad.service;

import com.community.ad.dto.AdPlacements;
import com.community.ad.dto.PublicAdView;
import com.community.ad.repository.master.AdPlacementStore;
import com.community.ad.repository.master.PublicAdStore;
import com.community.ad.repository.master.PublicAdStore.Running;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.bizapplication.entity.BizProduct;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataPurpose;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 공개 광고 노출(user-web {@code AdSlot}·광고 가젯·날개·글 보기·목록 한 줄 — docs/features/ad-contract.md 「공개 노출 목업」 확정 결정 2026-11-01).
 *
 * <ul>
 *   <li><b>목록</b>: 나가는 건({@link PublicAdStore#running})의 서버 사본을 {@value #CACHE_SECONDS}초 둔다(서버마다 메모리) — 긴급 내림·되돌리기는 상태로 반영되어 최대
 *       {@value #CACHE_SECONDS}초 늦을 수 있다. 핫딜 노출 두 유형은 게시된 핫딜 글이 아직 없어 늘 빈 목록이다.</li>
 *   <li><b>게시판 맥락</b>({@code board} 를 준 호출 — 날개 배너도 게시판·글 보기 화면에서는 붙인다): 없는 게시판 · 꺼진 게시판 · 소모임 게시판 · 「광고 숨김」이면 빈 목록,
 *       19금 게시판은 자리 설정 「19금 게시판에도 광고 보이기」가 꺼져 있으면 빈 목록.</li>
 *   <li><b>집계</b>: 같은 방문자 · 같은 광고 버전 · 같은 자리는 한국 하루 노출 1번 · 클릭 1번. 방문자 = 로그인 회원 ID, 아니면 IP + User-Agent — 날짜와 함께 서버 해시 키
 *       ({@link BlindIndex}, 용도 {@code ad.visitor})로 HMAC 해 저장한다(원본 IP 저장 없음). 키가 없거나 봇·빈 User-Agent 면 세지 않는다(응답은 같다).
 *       나가지 않는 건·그 건의 지금 버전이 아닌 줄·모르는 자리는 그 줄만 버린다(판정은 같은 사본).</li>
 * </ul>
 */
@Service
public class PublicAdService {

    private static final Logger log = LoggerFactory.getLogger(PublicAdService.class);

    static final int CACHE_SECONDS = 20;
    /** 노출 한 번에 받는 줄 수 */
    public static final int IMPRESSIONS_MAX = 20;
    /** 공개 유형 — 광고 종류 여섯 + 핫딜 노출 자리 둘(받는 이름 = 상품 이름) */
    public static final List<BizProduct> TYPES = List.of(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER, BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER,
            BizProduct.WING_BANNER, BizProduct.TEXT_LINE, BizProduct.HOTDEAL_TOP, BizProduct.HOTDEAL_CATEGORY);
    /** 사본에 담는 상품 — 핫딜 노출 자리는 글이 있어야 해서 아직 담지 않는다 */
    private static final List<BizProduct> SERVED = TYPES.stream().filter(BizProduct::adProduct).toList();
    public static final List<String> PLACEMENTS = List.of("HOME", "PAGE", "BOARD", "POST_INLINE", "HOTDEAL_TOP", "HOTDEAL_CATEGORY", "WING_LEFT", "WING_RIGHT",
            "BOARD_LIST");
    static final DataPurpose VISITOR = DataPurpose.of("ad.visitor");
    /** 세지 않는 User-Agent — 검색 로봇·미리보기·감시·명령줄 도구 */
    private static final Pattern BOT = Pattern.compile("(?i)(bot|crawl|spider|slurp|scrap|facebookexternalhit|embedly|preview|headless|phantomjs|lighthouse|pingdom|uptime"
            + "|monitor|curl|wget|python|java/|go-http|okhttp|axios|node-fetch|httpclient|libwww|postman|insomnia)");
    private static final int UA_MAX = 512;

    /** 방문자 — 로그인 회원이면 {@code memberId}, 아니면 IP + User-Agent */
    public record Visitor(Long memberId, String ip, String userAgent) {
    }

    private record Snapshot(Instant loadedAt, List<Running> rows, Map<Long, Running> byId) {
    }

    private final PublicAdStore store;
    private final AdPlacementStore placements;
    private final BoardCatalog catalog;
    private final BlindIndex index;
    private final Clock clock;
    private volatile Snapshot snapshot;

    public PublicAdService(PublicAdStore store, AdPlacementStore placements, BoardCatalog catalog, BlindIndex index, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.placements = placements;
        this.catalog = catalog;
        this.index = index;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 목록 ───────────────────────────────────────────────────────────────

    /**
     * @param typesText 쉼표 구분 1~8개 — 비었거나 모르는 이름이면 400 {@code fields.types}
     * @param board     게시판 맥락 호출의 게시판 slug(아니면 null)
     */
    public PublicAdView.Page list(String typesText, String board) {
        Set<BizProduct> types = types(typesText);
        if (board != null && !board.isBlank() && !boardAllows(board.strip())) {
            return new PublicAdView.Page(List.of());
        }
        List<PublicAdView.Ad> items = new ArrayList<>();
        for (Running r : current().rows()) {
            if (types.contains(r.product())) {
                items.add(view(r));
            }
        }
        return new PublicAdView.Page(items);
    }

    /** 노출 자리 설정(저장한 적 없으면 기본값) */
    public AdPlacements placements() {
        return placements.current();
    }

    /** 사본을 버린다(다음 읽기가 다시 읽는다) — 시험·운영 도구 */
    public void invalidate() {
        snapshot = null;
    }

    /**
     * 게시판 맥락 광고(날개 포함 — 유형별 예외 없음)를 그 게시판에 내는가 — 없거나 꺼졌거나 소모임·「광고 숨김」이면 늘 아니다. 19금 게시판은 자리 설정
     * {@code adult.enabled} 가 켜졌을 때만 낸다(사용자 결정 2026-11-01 2차 — 자리 설정은 사본 없이 그때 읽어 바로 반영).
     */
    boolean boardAllows(String slug) {
        EffectiveBoard b = catalog.snapshot().board(slug).orElse(null);
        if (b == null || !b.active() || BoardFields.KIND_GROUP.equals(b.kind()) || b.adsHidden()) {
            return false;
        }
        return !b.allowAdult() || placements.current().adult().enabled();
    }

    private static Set<BizProduct> types(String text) {
        Set<BizProduct> out = new LinkedHashSet<>();
        String[] parts = text == null ? new String[0] : text.split(",", -1);
        boolean bad = parts.length == 0 || parts.length > TYPES.size();
        for (String part : parts) {
            BizProduct p = TYPES.stream().filter(t -> t.name().equals(part.strip())).findFirst().orElse(null);
            if (p == null) {
                bad = true;
            } else {
                out.add(p);
            }
        }
        if (bad || out.isEmpty()) {
            throw ValidationException.of("types", new MessageRef("ad.public.types", String.valueOf(TYPES.size())));
        }
        return out;
    }

    private static PublicAdView.Ad view(Running r) {
        Map<String, Object> c = r.content();
        boolean banner = r.product().banner();
        String text = banner ? null : string(c.get("text"));
        String alt = string(c.get("altText"));
        return new PublicAdView.Ad(Long.toString(r.applicationId()), r.product().name(), r.version(), banner ? string(c.get("imageUrl")) : null, text,
                string(c.get("linkUrl")), alt != null ? alt : text, r.weight(), null, r.advertiserName());
    }

    private static String string(Object v) {
        return v == null ? null : v.toString();
    }

    private Snapshot current() {
        Instant now = clock.instant();
        Snapshot s = snapshot;
        if (s != null && !now.isBefore(s.loadedAt()) && Duration.between(s.loadedAt(), now).getSeconds() < CACHE_SECONDS) {
            return s;
        }
        synchronized (this) {
            s = snapshot;
            if (s != null && !now.isBefore(s.loadedAt()) && Duration.between(s.loadedAt(), now).getSeconds() < CACHE_SECONDS) {
                return s;
            }
            List<Running> rows = store.running(SERVED, now);
            Map<Long, Running> byId = new HashMap<>();
            rows.forEach(r -> byId.put(r.applicationId(), r));
            s = new Snapshot(now, rows, byId);
            snapshot = s;
            return s;
        }
    }

    // ── 집계 ───────────────────────────────────────────────────────────────

    /** 노출 — 0~{@value #IMPRESSIONS_MAX}줄(넘으면 400 {@code fields.items}). 센 줄 수를 돌려준다(응답에는 싣지 않는다) */
    public int impressions(Visitor visitor, PublicAdView.Impressions body) {
        List<PublicAdView.Impression> items = body == null || body.items() == null ? List.of() : body.items();
        if (items.size() > IMPRESSIONS_MAX) {
            throw ValidationException.of("items", new MessageRef("ad.public.items", String.valueOf(IMPRESSIONS_MAX)));
        }
        String hash = visitorHash(visitor);
        if (hash == null || items.isEmpty()) {
            return 0;
        }
        LocalDate day = ServiceTime.today(clock);
        int counted = 0;
        for (PublicAdView.Impression it : items) {
            if (it != null && countable(it.adId(), it.version(), it.placement()) && store.count('I', day, hash, Long.parseLong(it.adId().strip()), it.version(),
                    it.placement())) {
                counted++;
            }
        }
        return counted;
    }

    /** 클릭 — 맞지 않으면 조용히 버린다. 셌으면 true */
    public boolean click(Visitor visitor, String adId, PublicAdView.Click body) {
        if (body == null) {
            return false;
        }
        String hash = visitorHash(visitor);
        if (hash == null || !countable(adId, body.version(), body.placement())) {
            return false;
        }
        return store.count('C', ServiceTime.today(clock), hash, Long.parseLong(adId.strip()), body.version(), body.placement());
    }

    /** 지금 나가는 건 · 그 건의 지금 버전 · 아는 자리인가 */
    private boolean countable(String adId, Integer version, String placement) {
        if (adId == null || version == null || placement == null || !PLACEMENTS.contains(placement)) {
            return false;
        }
        long id;
        try {
            id = Long.parseLong(adId.strip());
        } catch (NumberFormatException e) {
            return false;
        }
        Running r = current().byId().get(id);
        return r != null && r.version() == version;
    }

    /** 세지 않을 방문자(키 없음 · 봇 · 빈 User-Agent)면 null */
    String visitorHash(Visitor visitor) {
        if (visitor == null || !index.isConfigured()) {
            return null;
        }
        String ua = visitor.userAgent() == null ? "" : visitor.userAgent().strip();
        if (ua.isEmpty() || BOT.matcher(ua).find()) {
            return null;
        }
        if (ua.length() > UA_MAX) {
            ua = ua.substring(0, UA_MAX);
        }
        String who = visitor.memberId() != null ? "m:" + visitor.memberId() : "g:" + (visitor.ip() == null ? "" : visitor.ip()) + "\n" + ua;
        try {
            return index.hash(VISITOR, ServiceTime.today(clock) + "|" + who);
        } catch (RuntimeException e) {
            log.warn("광고 방문자 해시를 만들지 못해 세지 않는다: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
