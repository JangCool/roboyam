package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.ad.dto.AdPlacements;
import com.community.ad.dto.PublicAdView;
import com.community.ad.repository.master.AdPlacementStore;
import com.community.ad.repository.master.PublicAdStore;
import com.community.ad.service.AdOperatorService;
import com.community.ad.service.PublicAdService;
import com.community.ad.service.PublicAdService.Visitor;
import com.community.bizapplication.BizApplicationApiTestBase;
import com.community.bizapplication.service.BizPeriods;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공개 광고 노출 규칙(docs/features/ad-contract.md 「공개 노출 목업」 확정 결정 2026-11-01 · 「공개 노출 쿼리 후보」, commerce.md 18절 — 신청 건 장부·돈과 이어져 같은 취급).
 *
 * <ul>
 *   <li>목록: 예약 확정(SCHEDULED)·기간 안·최신 <b>승인된</b> 소재 버전만 — 승인 안 된 새 버전은 안 나가고, 긴급 내림은 빠진다. 서버 사본 20초(시계로 시험).</li>
 *   <li>게시판 맥락(날개 배너 포함): 「광고 숨김」·소모임·꺼진·없는 게시판은 늘 빈 목록, 19금 게시판은 자리 설정 {@code adult.enabled} 가 꺼지면 빈 목록·켜면 나온다
 *       (숨김·소모임이 우선 — 사용자 결정 2026-11-01 2차), 보통 게시판은 나온다.</li>
 *   <li>하루 1번: 같은 방문자·버전·자리는 한국 하루 노출 1번·클릭 1번 — 다른 자리·다른 버전·다음 날은 다시 센다, 회원과 비회원은 따로, 봇·빈 User-Agent 는 세지 않는다,
 *       20건 초과 400, 나가지 않는 건·지금 버전이 아닌 줄·모르는 자리는 그 줄만 버린다, 해시 키가 없으면 세지 않는다.</li>
 * </ul>
 * 공유 시험 DB 라 광고는 먼 미래의 겹치지 않는 기간({@link #freshPeriod})으로 잡고 시계를 그 기간 안으로 옮겨 「지금 나가는 건」을 만든다.
 */
class PublicAdServiceTest extends BizApplicationApiTestBase {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0";

    @Autowired
    private PublicAdService ads;
    @Autowired
    private PublicAdStore publicStore;
    @Autowired
    private AdPlacementStore placementStore;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private PublicBoardService boards;
    @Autowired
    private AdOperatorService operations;
    @Autowired
    private ObjectProvider<Clock> clocks;

    private final List<Long> createdBoards = new ArrayList<>();

    @BeforeEach
    void freshCache() {
        ads.invalidate();
    }

    @AfterEach
    void cleanUp() {
        createdBoards.forEach(id -> jdbc.update("DELETE FROM boards WHERE id = ?", id));
        createdBoards.clear();
        jdbc.update("DELETE FROM ad_placement_settings");
        catalog.invalidate();
        ads.invalidate();
    }

    // ── 준비 ───────────────────────────────────────────────────────────────

    /** 예약 확정된 광고(온라인 결제) — 기간 {@code p}. 시계는 아직 지금 */
    private long scheduled(String publicType, BizPeriods.Period p) {
        long user = approvedBusiness();
        long id = "TEXT_LINE".equals(publicType) ? submitTextLine(user, 0, 2, "ONLINE") : submitBanner(user, publicType, 0, 2, "ONLINE");
        approve(id, "ONLINE", p);
        pay(user, id);
        return id;
    }

    /** 시계를 기간 시작 한 시간 뒤로 */
    private void inside(BizPeriods.Period p) {
        clock.set(p.start().plus(Duration.ofHours(1)));
    }

    private List<PublicAdView.Ad> list(String types) {
        return ads.list(types, null).items();
    }

    private static List<String> ids(List<PublicAdView.Ad> items) {
        return items.stream().map(PublicAdView.Ad::id).toList();
    }

    private PublicAdView.Ad find(String types, long id) {
        return list(types).stream().filter(a -> a.id().equals(Long.toString(id))).findFirst().orElse(null);
    }

    private long board(String kind, boolean allowAdult, boolean adsHidden, boolean active) {
        long id = ids.nextId();
        String slug = "ad" + Long.toString(id, 36);
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order, kind, allow_adult, ads_hidden, active) VALUES (?, ?, ?, 900, ?, ?, ?, ?)", id, slug,
                "광고판 " + slug.substring(slug.length() - 6), kind, allowAdult, adsHidden, active);
        createdBoards.add(id);
        return id;
    }

    private String slug(long boardId) {
        return jdbc.queryForObject("SELECT slug FROM boards WHERE id = ?", String.class, boardId);
    }

    private long[] stats(long id, int version, LocalDate day, String placement) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT impressions, clicks FROM ad_daily_stats WHERE application_id = ? AND version = ? AND day = ? "
                + "AND placement = ?", id, version, java.sql.Date.valueOf(day), placement);
        return rows.isEmpty() ? new long[] {0, 0} : new long[] {((Number) rows.get(0).get("impressions")).longValue(), ((Number) rows.get(0).get("clicks")).longValue()};
    }

    private int impress(Visitor v, long id, int version, String placement) {
        return ads.impressions(v, new PublicAdView.Impressions(List.of(new PublicAdView.Impression(Long.toString(id), version, placement))));
    }

    private static Visitor guest(String ip) {
        return new Visitor(null, ip, UA);
    }

    // ── 목록 ───────────────────────────────────────────────────────────────

    @Test
    void 예약_확정_기간_안_최신_승인_버전만_나가고_모양은_계약대로() {
        BizPeriods.Period p = freshPeriod(3);
        long banner = scheduled("WIDE", p);
        long text = scheduled("TEXT_LINE", p);
        long pending = submitBanner(approvedBusiness(), "WIDE", 0, 2, "ONLINE");
        approve(pending, "ONLINE", p); // 결제 대기 — 자리는 차지하지만 나가지 않는다
        long before = scheduled("WIDE", new BizPeriods.Period(p.end(), p.end().plus(Duration.ofDays(1)))); // 다음 기간(반열림)

        clock.set(p.start().minus(Duration.ofMinutes(1)));
        assertThat(ids(list("WIDE_BANNER,TEXT_LINE"))).doesNotContain(Long.toString(banner), Long.toString(text));

        inside(p);
        ads.invalidate();
        List<PublicAdView.Ad> items = list("WIDE_BANNER,TEXT_LINE");
        assertThat(ids(items)).contains(Long.toString(banner), Long.toString(text)).doesNotContain(Long.toString(pending), Long.toString(before));
        PublicAdView.Ad b = find("WIDE_BANNER", banner);
        assertThat(b.type()).isEqualTo("WIDE_BANNER");
        assertThat(b.version()).isEqualTo(1);
        assertThat(b.text()).isNull();
        assertThat(b.linkUrl()).isEqualTo("https://shop.example.com/b");
        assertThat(b.altText()).isEqualTo("가을 특가 배너");
        assertThat(b.weight()).isEqualTo(5);
        assertThat(b.deal()).isNull();
        assertThat(b.advertiserName()).isEqualTo("시험상사");
        PublicAdView.Ad t = find("TEXT_LINE", text);
        assertThat(t.imageUrl()).isNull();
        assertThat(t.text()).isEqualTo("가을 특가 한 줄 광고");
        assertThat(t.altText()).as("대체 글이 없으면 문구").isEqualTo("가을 특가 한 줄 광고");
        // 유형으로 거른다
        assertThat(ids(list("TEXT_LINE"))).contains(Long.toString(text)).doesNotContain(Long.toString(banner));

        // 끝 시각 = 나가지 않는다(반열림)
        clock.set(p.end());
        ads.invalidate();
        assertThat(ids(list("WIDE_BANNER,TEXT_LINE"))).doesNotContain(Long.toString(banner), Long.toString(text)).contains(Long.toString(before));
    }

    @Test
    void 승인_안_된_새_버전은_나가지_않고_승인되면_그_버전이_나간다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("TEXT_LINE", p);
        jdbc.update("INSERT INTO biz_creative_versions (id, application_id, version, content, created_by_type, created_by, created_at) "
                + "VALUES (?, ?, 2, '{\"text\":\"새 문구\",\"linkUrl\":\"https://shop.example.com/v2\"}'::jsonb, 'MEMBER', NULL, now())", ids.nextId(), id);
        inside(p);
        PublicAdView.Ad ad = find("TEXT_LINE", id);
        assertThat(ad.version()).isEqualTo(1);
        assertThat(ad.text()).isEqualTo("가을 특가 한 줄 광고");

        jdbc.update("UPDATE biz_creative_versions SET approved_at = now() WHERE application_id = ? AND version = 2", id);
        ads.invalidate();
        ad = find("TEXT_LINE", id);
        assertThat(ad.version()).isEqualTo(2);
        assertThat(ad.text()).isEqualTo("새 문구");
    }

    @Test
    void 긴급_내림은_빠지고_서버_사본은_20초_뒤에_반영된다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("SMALL", p);
        inside(p);
        assertThat(find("SMALL_BANNER", id)).isNotNull();

        operations.takedown(operator, id, "긴급 내림 시험", false);
        assertThat(find("SMALL_BANNER", id)).as("사본 안(20초 전)은 그대로").isNotNull();
        clock.set(clock.instant().plus(Duration.ofSeconds(19)));
        assertThat(find("SMALL_BANNER", id)).isNotNull();
        clock.set(clock.instant().plus(Duration.ofSeconds(1)));
        assertThat(find("SMALL_BANNER", id)).as("20초가 지나면 다시 읽는다").isNull();
    }

    @Test
    void 핫딜_노출_두_유형은_늘_빈_목록이고_모르는_유형은_400_fields_types() {
        assertThat(list("HOTDEAL_TOP,HOTDEAL_CATEGORY")).isEmpty();
        for (String bad : new String[] {null, "", "WIDE", "WIDE_BANNER,NOPE", "HOTDEAL_POST", "WIDE_BANNER,"}) {
            assertThatThrownBy(() -> ads.list(bad, null)).isInstanceOfSatisfying(ValidationException.class,
                    e -> assertThat(e.getFields()).containsOnlyKeys("types"));
        }
        assertThatThrownBy(() -> ads.list("WIDE_BANNER,STRIP_BANNER,SQUARE_BANNER,SMALL_BANNER,WING_BANNER,TEXT_LINE,HOTDEAL_TOP,HOTDEAL_CATEGORY,WIDE_BANNER", null))
                .as("9개").isInstanceOf(ValidationException.class);
        assertThat(ads.list("WIDE_BANNER,STRIP_BANNER,SQUARE_BANNER,SMALL_BANNER,WING_BANNER,TEXT_LINE,HOTDEAL_TOP,HOTDEAL_CATEGORY", null).items()).isNotNull();
    }

    @Test
    void 날개_배너도_목록에_나온다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("WING", p);
        inside(p);
        assertThat(find("WING_BANNER", id)).isNotNull().extracting(PublicAdView.Ad::type).isEqualTo("WING_BANNER");
    }

    // ── 게시판 맥락 ───────────────────────────────────────────────────────────

    /** 자리 설정 「19금 게시판에도 광고 보이기」 — 나머지 칸은 기본값 */
    private void adultOption(boolean enabled) {
        AdPlacements d = AdPlacements.DEFAULTS;
        placementStore.save(new AdPlacements(d.postInline(), d.wings(), d.boardList(), new AdPlacements.Adult(enabled)), operator.operatorId(), clock.instant());
    }

    @Test
    void 광고_숨김_19금_소모임_꺼진_없는_게시판은_빈_목록이고_보통_게시판은_나온다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("TEXT_LINE", p);
        long normal = board("GENERAL", false, false, true);
        long hidden = board("GENERAL", false, true, true);
        long adult = board("GENERAL", true, false, true);
        long group = board("GROUP", false, false, true);
        long off = board("GENERAL", false, false, false);
        catalog.invalidate();
        jdbc.update("DELETE FROM ad_placement_settings"); // 저장한 적 없음 = 19금 끔
        inside(p);

        assertThat(ids(ads.list("TEXT_LINE", slug(normal)).items())).contains(Long.toString(id));
        assertThat(ads.list("TEXT_LINE", slug(hidden)).items()).isEmpty();
        assertThat(ads.list("TEXT_LINE", slug(adult)).items()).as("기본(19금 끔)").isEmpty();
        assertThat(ads.list("TEXT_LINE", slug(group)).items()).isEmpty();
        assertThat(ads.list("TEXT_LINE", slug(off)).items()).isEmpty();
        assertThat(ads.list("TEXT_LINE", "no-such-board-" + id).items()).isEmpty();
        // board 를 주지 않는 호출(홈·가젯·날개)은 판정이 없다
        assertThat(ids(ads.list("TEXT_LINE", null).items())).contains(Long.toString(id));
        // 공개 게시판 상세에 adsHidden
        assertThat(boards.board(slug(hidden), null).adsHidden()).isTrue();
        assertThat(boards.board(slug(normal), null).adsHidden()).isFalse();
    }

    @Test
    void 날개_배너도_board_를_주면_같은_판정_숨김_게시판은_빈_목록() {
        BizPeriods.Period p = freshPeriod(3);
        long wing = scheduled("WING", p);
        long normal = board("GENERAL", false, false, true);
        long hidden = board("GENERAL", false, true, true);
        long group = board("GROUP", false, false, true);
        catalog.invalidate();
        adultOption(true); // 옵션과 무관
        inside(p);

        assertThat(ids(ads.list("WING_BANNER", slug(normal)).items())).contains(Long.toString(wing));
        assertThat(ads.list("WING_BANNER", slug(hidden)).items()).isEmpty();
        assertThat(ads.list("WING_BANNER", slug(group)).items()).isEmpty();
        assertThat(ids(ads.list("WING_BANNER", null).items())).as("홈·가젯 페이지(board 없음)").contains(Long.toString(wing));
    }

    @Test
    void 십구금_게시판은_자리_설정_옵션이_꺼지면_빈_목록_켜면_나오고_바로_반영된다() {
        BizPeriods.Period p = freshPeriod(3);
        long text = scheduled("TEXT_LINE", p);
        long wing = scheduled("WING", p);
        long adult = board("GENERAL", true, false, true);
        catalog.invalidate();
        inside(p);

        adultOption(false);
        assertThat(ads.list("TEXT_LINE", slug(adult)).items()).isEmpty();
        assertThat(ads.list("WING_BANNER", slug(adult)).items()).as("날개 포함").isEmpty();

        adultOption(true); // 목록 사본(20초)과 따로 — 자리 설정은 그때 읽는다
        assertThat(ids(ads.list("TEXT_LINE", slug(adult)).items())).contains(Long.toString(text));
        assertThat(ids(ads.list("WING_BANNER", slug(adult)).items())).contains(Long.toString(wing));

        adultOption(false);
        assertThat(ads.list("WING_BANNER,TEXT_LINE", slug(adult)).items()).isEmpty();
    }

    @Test
    void 십구금_옵션을_켜도_광고_숨김과_소모임_게시판은_빈_목록() {
        BizPeriods.Period p = freshPeriod(3);
        scheduled("TEXT_LINE", p);
        scheduled("WING", p);
        long adultHidden = board("GENERAL", true, true, true);
        long adultGroup = board("GROUP", true, false, true);
        long group = board("GROUP", false, false, true);
        catalog.invalidate();
        adultOption(true);
        inside(p);

        assertThat(ads.list("WING_BANNER,TEXT_LINE", slug(adultHidden)).items()).as("adsHidden 우선").isEmpty();
        assertThat(ads.list("WING_BANNER,TEXT_LINE", slug(adultGroup)).items()).isEmpty();
        assertThat(ads.list("WING_BANNER,TEXT_LINE", slug(group)).items()).isEmpty();
    }

    // ── 하루 1번 ───────────────────────────────────────────────────────────

    @Test
    void 같은_방문자_버전_자리는_하루_노출_1번_다른_자리_다른_버전_다음_날은_다시_센다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("STRIP", p);
        inside(p);
        LocalDate today = ServiceTime.today(clock);
        Visitor v = guest("203.0.113.10");

        assertThat(impress(v, id, 1, "HOME")).isEqualTo(1);
        assertThat(impress(v, id, 1, "HOME")).isZero();
        assertThat(stats(id, 1, today, "HOME")[0]).isEqualTo(1);
        // 한 요청 안의 같은 줄도 한 번
        assertThat(ads.impressions(v, new PublicAdView.Impressions(List.of(new PublicAdView.Impression(Long.toString(id), 1, "PAGE"),
                new PublicAdView.Impression(Long.toString(id), 1, "PAGE"))))).isEqualTo(1);
        assertThat(stats(id, 1, today, "PAGE")[0]).isEqualTo(1);

        // 다른 버전(새 버전 승인) — 다시 센다, 옛 버전 줄은 이제 버린다
        jdbc.update("INSERT INTO biz_creative_versions (id, application_id, version, content, created_by_type, created_by, created_at, approved_at) "
                + "SELECT ?, application_id, 2, content, 'MEMBER', NULL, now(), now() FROM biz_creative_versions WHERE application_id = ? AND version = 1", ids.nextId(), id);
        ads.invalidate();
        assertThat(impress(v, id, 2, "HOME")).isEqualTo(1);
        assertThat(impress(v, id, 1, "BOARD")).as("지금 버전이 아니면 버린다").isZero();
        assertThat(stats(id, 2, today, "HOME")[0]).isEqualTo(1);
        assertThat(stats(id, 1, today, "BOARD")[0]).isZero();

        // 다음 날(한국 날짜) — 다시 센다
        clock.set(clock.instant().plus(Duration.ofDays(1)));
        assertThat(impress(v, id, 2, "HOME")).isEqualTo(1);
        assertThat(stats(id, 2, today.plusDays(1), "HOME")[0]).isEqualTo(1);
        assertThat(stats(id, 2, today, "HOME")[0]).isEqualTo(1);
    }

    @Test
    void 회원과_비회원은_따로_세고_회원은_IP가_바뀌어도_같은_방문자() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("SQUARE", p);
        inside(p);
        LocalDate today = ServiceTime.today(clock);
        long member = newMember();

        assertThat(impress(guest("198.51.100.7"), id, 1, "HOME")).isEqualTo(1);
        assertThat(impress(new Visitor(member, "198.51.100.7", UA), id, 1, "HOME")).as("같은 IP·UA 라도 회원은 따로").isEqualTo(1);
        assertThat(impress(new Visitor(member, "192.0.2.99", "Other UA/1.0"), id, 1, "HOME")).as("회원은 IP·UA 와 상관없이 한 사람").isZero();
        assertThat(impress(new Visitor(newMember(), "198.51.100.7", UA), id, 1, "HOME")).as("다른 회원").isEqualTo(1);
        assertThat(impress(guest("198.51.100.8"), id, 1, "HOME")).as("다른 IP 비회원").isEqualTo(1);
        assertThat(impress(new Visitor(null, "198.51.100.7", UA + " Edg/130"), id, 1, "HOME")).as("다른 UA 비회원").isEqualTo(1);
        assertThat(stats(id, 1, today, "HOME")[0]).isEqualTo(5);
        // 원본 IP·UA·회원 ID 는 판정 표에 없다
        assertThat(jdbc.queryForList("SELECT visitor_hash FROM ad_view_marks WHERE application_id = ?", String.class, id))
                .allSatisfy(h -> assertThat(h).hasSize(64).doesNotContain("198.51").doesNotContain(Long.toString(member)));
    }

    @Test
    void 클릭도_같은_방문자_버전_자리는_하루_1번() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("TEXT_LINE", p);
        inside(p);
        LocalDate today = ServiceTime.today(clock);
        Visitor v = guest("203.0.113.20");
        String adId = Long.toString(id);

        assertThat(ads.click(v, adId, new PublicAdView.Click(1, "BOARD_LIST"))).isTrue();
        assertThat(ads.click(v, adId, new PublicAdView.Click(1, "BOARD_LIST"))).isFalse();
        assertThat(ads.click(v, adId, new PublicAdView.Click(1, "POST_INLINE"))).as("다른 자리").isTrue();
        assertThat(ads.click(guest("203.0.113.21"), adId, new PublicAdView.Click(1, "BOARD_LIST"))).as("다른 방문자").isTrue();
        // 노출과 클릭은 따로 센다
        assertThat(impress(v, id, 1, "BOARD_LIST")).isEqualTo(1);
        assertThat(stats(id, 1, today, "BOARD_LIST")).containsExactly(1, 2);
        // 맞지 않는 클릭은 버린다
        assertThat(ads.click(v, adId, new PublicAdView.Click(9, "HOME"))).isFalse();
        assertThat(ads.click(v, adId, new PublicAdView.Click(1, "SIDEBAR"))).isFalse();
        assertThat(ads.click(v, "not-a-number", new PublicAdView.Click(1, "HOME"))).isFalse();
        assertThat(ads.click(v, adId, null)).isFalse();
        clock.set(clock.instant().plus(Duration.ofDays(1)));
        assertThat(ads.click(v, adId, new PublicAdView.Click(1, "BOARD_LIST"))).as("다음 날").isTrue();
    }

    @Test
    void 봇과_빈_User_Agent_는_세지_않는다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("WIDE", p);
        inside(p);
        for (String ua : new String[] {null, "", "  ", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", "Yeti/1.1 (NHN Corp.; crawler)",
                "facebookexternalhit/1.1", "curl/8.4.0", "python-requests/2.31", "Mozilla/5.0 HeadlessChrome/120.0"}) {
            assertThat(impress(new Visitor(null, "203.0.113.30", ua), id, 1, "HOME")).as(String.valueOf(ua)).isZero();
            assertThat(ads.click(new Visitor(null, "203.0.113.30", ua), Long.toString(id), new PublicAdView.Click(1, "HOME"))).isFalse();
        }
        assertThat(stats(id, 1, ServiceTime.today(clock), "HOME")).containsExactly(0, 0);
    }

    @Test
    void 노출은_한_번에_20건까지_넘으면_400_fields_items_나가지_않는_줄은_그_줄만_버린다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("TEXT_LINE", p);
        long other = scheduled("SMALL", p);
        inside(p);
        LocalDate today = ServiceTime.today(clock);
        Visitor v = guest("203.0.113.40");

        List<PublicAdView.Impression> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(new PublicAdView.Impression(Long.toString(id), 1, "HOME"));
        }
        assertThatThrownBy(() -> ads.impressions(v, new PublicAdView.Impressions(many))).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields()).containsOnlyKeys("items"));
        assertThat(stats(id, 1, today, "HOME")[0]).isZero();
        assertThat(ads.impressions(v, new PublicAdView.Impressions(many.subList(0, 20)))).as("20건은 받는다").isEqualTo(1);

        long notRunning = submitTextLine(approvedBusiness(), 0, 2, "ONLINE");
        List<PublicAdView.Impression> mixed = new ArrayList<>();
        mixed.add(new PublicAdView.Impression(Long.toString(notRunning), 1, "PAGE")); // 나가지 않는 건(심사 대기)
        mixed.add(new PublicAdView.Impression(Long.toString(other), 3, "PAGE")); // 없는 버전
        mixed.add(new PublicAdView.Impression(Long.toString(other), 1, "NOWHERE")); // 모르는 자리
        mixed.add(new PublicAdView.Impression("abc", 1, "PAGE"));
        mixed.add(new PublicAdView.Impression(null, null, null));
        mixed.add(null);
        mixed.add(new PublicAdView.Impression(Long.toString(other), 1, "PAGE")); // 이 줄만 센다
        assertThat(ads.impressions(v, new PublicAdView.Impressions(mixed))).isEqualTo(1);
        assertThat(stats(other, 1, today, "PAGE")[0]).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_daily_stats WHERE application_id = ?", Integer.class, notRunning)).isZero();
        // 빈 본문은 아무것도 하지 않는다
        assertThat(ads.impressions(v, null)).isZero();
        assertThat(ads.impressions(v, new PublicAdView.Impressions(null))).isZero();
    }

    @Test
    void 해시_키가_없으면_세지_않는다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduled("TEXT_LINE", p);
        inside(p);
        PublicAdService noKey = new PublicAdService(publicStore, placementStore, catalog, new BlindIndex(""), clocks);
        assertThat(noKey.impressions(guest("203.0.113.50"), new PublicAdView.Impressions(List.of(new PublicAdView.Impression(Long.toString(id), 1, "HOME")))))
                .isZero();
        assertThat(noKey.click(new Visitor(newMember(), "203.0.113.50", UA), Long.toString(id), new PublicAdView.Click(1, "HOME"))).isFalse();
        assertThat(stats(id, 1, ServiceTime.today(clock), "HOME")).containsExactly(0, 0);
    }
}
