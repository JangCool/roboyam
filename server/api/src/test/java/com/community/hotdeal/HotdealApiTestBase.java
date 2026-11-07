package com.community.hotdeal;

import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.hotdeal.dto.request.DealImageInput;
import com.community.hotdeal.dto.request.DealInput;
import com.community.hotdeal.og.DealPageFetcher;
import com.community.hotdeal.service.DealHosts;
import com.community.hotdeal.service.DealRuleBook;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.permission.entity.Permission;
import com.community.post.dto.request.PostUpdateRequest;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostDetailView;
import com.community.post.dto.response.PostWriteResult;
import com.community.post.service.PostQueryService;
import com.community.post.service.PostWriteService;
import com.community.zconfiguration.exception.CommonException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 핫딜 1차 서버 시험의 바탕 — 공개 사이트 앱 문맥 + 실제 PostgreSQL(커머스 시험 DB, {@link CommerceApiTestBase}). <b>외부 사이트를 부르지 않는다</b>:
 * 상품 링크 가져오기({@link DealPageFetcher})를 가짜로 바꿔 「주소 → 리디렉션 체인 호스트」를 시험이 정한다({@link #chain}).
 *
 * <ul>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 게시판·회원·규칙 도메인은 시험마다 새로 만든다. 핫딜 설정 행은 하나라 바꾼 시험이 {@link #restoreSettings} 로 되돌린다
 *       (사본 15초 — 바꾸면 {@code invalidate}).</li>
 *   <li>링크 살펴보기 결과는 주소별로 24시간 캐시된다 — 시험마다 새 주소({@link #url})를 쓴다.</li>
 * </ul>
 */
@Import(HotdealApiTestBase.FakeFetcherConfig.class)
abstract class HotdealApiTestBase extends CommerceApiTestBase {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String BODY = "핫딜 본문입니다. 가격이 좋아서 올려요.";
    private static final AtomicLong SEQ = new AtomicLong();
    /** 주소 → 체인 호스트(처음 → 최종). 없으면 그 주소의 호스트 하나 */
    static final Map<String, List<String>> CHAINS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class FakeFetcherConfig {

        @Bean
        @Primary
        DealPageFetcher fakeDealPageFetcher() {
            return new DealPageFetcher() {
                @Override
                public Page fetchPage(String url, boolean readHead) {
                    List<String> hosts = CHAINS.getOrDefault(url, List.of(DealHosts.hostOf(url)));
                    return new Page(hosts, url, Outcome.OK, readHead ? new OgTags("가짜 제목", "가짜몰", null) : null, 0);
                }

                @Override
                public Image fetchImage(String url) {
                    return new Image(Outcome.FAILED, null);
                }
            };
        }
    }

    @Autowired
    protected PostWriteService posts;
    @Autowired
    protected PostQueryService postQueries;
    @Autowired
    protected BoardCatalog catalog;
    @Autowired
    protected DealSettingsReader dealSettings;
    @Autowired
    protected DealRuleBook rules;

    protected String board;
    private final List<String> domains = new ArrayList<>();

    @BeforeEach
    void hotdealSetUp() {
        board = dealBoard();
    }

    @AfterEach
    void hotdealTearDown() {
        for (String domain : domains) {
            // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
            jdbc.update("DELETE FROM deal_domains WHERE domain = ?", domain);
            jdbc.update("DELETE FROM deal_site_affiliates WHERE domain = ?", domain);
        }
        rules.invalidate();
        restoreSettings();
    }

    /** 이 실행에서 겹치지 않는 글자 */
    protected static String uniq() {
        return Long.toString(System.nanoTime(), 36) + SEQ.incrementAndGet();
    }

    /** 새 상품 주소(호스트는 {@code host}) */
    protected static String url(String host) {
        return "https://" + host + "/products/" + uniq();
    }

    /** 리디렉션 체인을 정한다 */
    protected static String chain(String url, String... hosts) {
        CHAINS.put(url, List.of(hosts));
        return url;
    }

    /** 새 핫딜 게시판(slug) — 쓰기 최소 자격 REGULAR */
    protected String dealBoard() {
        long id = ids.nextId();
        String slug = "hd" + Long.toString(id, 36);
        boardFixture.board(id, slug, "핫딜 " + slug.substring(slug.length() - 6), 950, "DEAL", "REGULAR");
        catalog.invalidate();
        return slug;
    }

    /** 새 일반 게시판 */
    protected String generalBoard() {
        long id = ids.nextId();
        String slug = "gn" + Long.toString(id, 36);
        boardFixture.board(id, slug, "일반 " + slug.substring(slug.length() - 6), 951);
        catalog.invalidate();
        return slug;
    }

    protected long boardId(String slug) {
        return jdbc.queryForObject("SELECT id FROM boards WHERE slug = ?", Long.class, slug);
    }

    /** 이메일 인증을 마친 일반 등급 회원 */
    protected long member() {
        return member("REGULAR", true);
    }

    protected long member(String grade, boolean verified) {
        long id = newMember();
        if (verified) {
            memberFixture.verifyEmail(id);
        }
        String name = Long.toString(id, 36);
        memberFixture.profile(id, "h" + name.substring(Math.max(0, name.length() - 11)), grade);
        return id;
    }

    /** 그 권한을 가진 활성 운영자 */
    protected long operatorWith(Permission... permissions) {
        long id = newOperator();
        for (Permission permission : permissions) {
            // SQL 로 남김(ON CONFLICT upsert): 권한 표는 관리자 앱이 뜰 때 맞춘다 — 공개 사이트 문맥만 띄운 시험이라 없으면 코드 값으로 넣는다
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at) VALUES (?, 'OPERATOR', ?, ?, ?, now()) ON CONFLICT (code) DO NOTHING",
                    permission.code(), permission.type().id(), permission.unit().name(), permission.featureReady());
        }
        operatorFixture.grantRole(id, "ROLE_ZZ_HD_" + id, "hd-" + (id % 1_000_000_000L), java.util.Arrays.stream(permissions).map(Permission::code).toList());
        return id;
    }

    /** 링크 규칙 한 줄(시험이 끝나면 지운다) */
    protected void domain(String domain, String rule, boolean shortener) {
        domains.add(domain);
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("INSERT INTO deal_domains (id, domain, rule, shortener, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())", ids.nextId(), domain, rule,
                shortener);
        rules.invalidate();
    }

    /** 사이트 제휴 몰 한 줄(시험이 끝나면 지운다) */
    protected void siteStore(String domain, String type, String template, String query) {
        domains.add(domain);
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("INSERT INTO deal_site_affiliates (id, domain, program_name, transform_type, template, query, enabled, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, true, now(), now())", ids.nextId(), domain, "시험 제휴", type, template, query);
        rules.invalidate();
    }

    /** 핫딜 설정 칸 하나를 바꾼다(시험 뒤 기본값으로) */
    protected void setting(String column, Object value) {
        jdbc.update("UPDATE deal_settings SET " + column + " = ? WHERE id = 1", value);
        dealSettings.invalidate();
    }

    protected void restoreSettings() {
        jdbc.update("UPDATE deal_settings SET member_daily_post_limit = 5, same_store_interval_minutes = 10, auto_expire_days = 30, price_max_won = 100000000, "
                + "discount_warn_percent = 90, hot_like_threshold = 10, keep_max_count = 2, end_grace_hours = 12, og_fetch_enabled = true, affiliate_mode = 'MEMBER' "
                + "WHERE id = 1");
        dealSettings.invalidate();
    }

    // ── 딜 칸 ───────────────────────────────────────────────────────────────

    protected static DealInput deal(String url) {
        return new DealInput("시험몰", url, 30_000L, 19_900L, 0L, null, null, false, null, null, "IN_STOCK", false, null);
    }

    protected static DealInput deal(String url, Long normal, Long dealPrice, Long shipping, String basisKind) {
        return new DealInput("시험몰", url, normal, dealPrice, shipping, null, basisKind, false, null, null, "IN_STOCK", false, null);
    }

    protected static DealInput withStock(DealInput d, String stock) {
        return new DealInput(d.storeName(), d.productUrl(), d.normalPrice(), d.dealPrice(), d.shippingFee(), d.couponNote(), d.priceBasisKind(), d.dutyIncluded(),
                d.priceBasis(), d.endsAt(), stock, d.affiliate(), d.image());
    }

    protected static DealInput withAffiliate(DealInput d, boolean affiliate) {
        return new DealInput(d.storeName(), d.productUrl(), d.normalPrice(), d.dealPrice(), d.shippingFee(), d.couponNote(), d.priceBasisKind(), d.dutyIncluded(),
                d.priceBasis(), d.endsAt(), d.stockState(), affiliate, d.image());
    }

    protected static DealInput withEnds(DealInput d, String endsAt) {
        return new DealInput(d.storeName(), d.productUrl(), d.normalPrice(), d.dealPrice(), d.shippingFee(), d.couponNote(), d.priceBasisKind(), d.dutyIncluded(),
                d.priceBasis(), endsAt, d.stockState(), d.affiliate(), d.image());
    }

    protected static DealInput withImage(DealInput d, DealImageInput image) {
        return new DealInput(d.storeName(), d.productUrl(), d.normalPrice(), d.dealPrice(), d.shippingFee(), d.couponNote(), d.priceBasisKind(), d.dutyIncluded(),
                d.priceBasis(), d.endsAt(), d.stockState(), d.affiliate(), image);
    }

    protected static DealInput withPrices(DealInput d, Long normal, Long dealPrice, Long shipping) {
        return new DealInput(d.storeName(), d.productUrl(), normal, dealPrice, shipping, d.couponNote(), d.priceBasisKind(), d.dutyIncluded(), d.priceBasis(),
                d.endsAt(), d.stockState(), d.affiliate(), d.image());
    }

    protected static DealInput withUrl(DealInput d, String url) {
        return new DealInput(d.storeName(), url, d.normalPrice(), d.dealPrice(), d.shippingFee(), d.couponNote(), d.priceBasisKind(), d.dutyIncluded(), d.priceBasis(),
                d.endsAt(), d.stockState(), d.affiliate(), d.image());
    }

    // ── 글 ──────────────────────────────────────────────────────────────────

    protected PostWriteResult write(long member, String slug, DealInput deal) {
        return posts.create(new PostWriteRequest(slug, null, "로봇 청소기 " + uniq(), "TEXT", BODY + " " + uniq(), false, null, null, null, deal), member, "127.0.0.1");
    }

    protected long writeId(long member, DealInput deal) {
        return Long.parseLong(write(member, board, deal).id());
    }

    protected PostDetailView update(long member, long postId, DealInput deal) {
        JsonNode node = deal == null ? null : JSON.valueToTree(deal);
        return posts.update(Long.toString(postId), new PostUpdateRequest(null, "고친 제목 " + uniq(), "TEXT", BODY + " 고침 " + uniq(), false, null, null, node), member,
                "127.0.0.1");
    }

    protected Map<String, Object> dealRow(long postId) {
        return jdbc.queryForMap("SELECT * FROM post_deals WHERE post_id = ?", postId);
    }

    // ── 오류 ─────────────────────────────────────────────────────────────────

    protected static CommonException error(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e;
        } catch (Throwable e) {
            throw new AssertionError("업무 예외가 아니다: " + e, e);
        }
        throw new AssertionError("예외가 나지 않았다");
    }

    /** 400 칸 → 문구 키 */
    protected static Map<String, String> fieldKeys(Executable call) {
        CommonException e = error(call);
        Map<String, String> out = new java.util.LinkedHashMap<>();
        e.getFields().forEach((k, v) -> out.put(k, v.key()));
        return out;
    }
}
