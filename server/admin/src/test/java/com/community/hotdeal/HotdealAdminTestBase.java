package com.community.hotdeal;

import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.hotdeal.service.DealRuleBook;
import com.community.hotdeal.service.DealSettingsReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원 핫딜 3단계(관리자 API) 시험의 바탕 — 관리자 앱 문맥 전체 + 실제 PostgreSQL({@link Commerce04AdminTestBase}). 글·딜은 SQL 로 바로 넣는다(공개 글쓰기는 api 시험의 몫).
 *
 * <ul>
 *   <li>DB 를 다른 시험과 같이 쓴다 — 게시판·회원·도메인은 시험마다 새로 만들고, 만든 규칙·몰은 끝나면 지운다.</li>
 *   <li>핫딜 설정 행은 하나라 시험이 끝나면 기본값으로 되돌린다(사본 15초 — 바꾸면 {@code invalidate}).</li>
 * </ul>
 */
abstract class HotdealAdminTestBase extends Commerce04AdminTestBase {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    protected BoardCatalog catalog;
    @Autowired
    protected DealSettingsReader dealSettings;
    @Autowired
    protected DealRuleBook rules;

    /** 시험이 만든 도메인(끝나면 규칙·몰 표에서 지운다) */
    protected final List<String> domains = new ArrayList<>();

    @AfterEach
    void hotdealAdminTearDown() {
        for (String domain : domains) {
            jdbc.update("DELETE FROM deal_domains WHERE domain = ?", domain);
            jdbc.update("DELETE FROM deal_site_affiliates WHERE domain = ?", domain);
        }
        rules.invalidate();
        jdbc.update("UPDATE deal_settings SET end_request_threshold = 3, end_grace_hours = 12, end_request_daily_limit = 20, end_request_per_author_limit = 3, "
                + "end_request_per_author_days = 7, keep_max_count = 2, member_daily_post_limit = 5, same_store_interval_minutes = 10, auto_expire_days = 30, "
                + "price_max_won = 100000000, discount_warn_percent = 90, hot_like_threshold = 10, suspicion_min_posts = 3, suspicion_window_days = 7, "
                + "store_suspicion_members = 3, store_suspicion_window_days = 30, store_suspicion_other_ratio_percent = 20, og_fetch_enabled = true, "
                + "affiliate_mode = 'MEMBER' WHERE id = 1");
        dealSettings.invalidate();
    }

    /** 이 실행에서 겹치지 않는 글자(소문자·숫자) */
    protected static String uniq() {
        return Long.toString(System.nanoTime(), 36) + SEQ.incrementAndGet();
    }

    /** 이 시험만 쓰는 도메인(끝나면 지운다) */
    protected String domain(String prefix) {
        String d = prefix + uniq() + ".zz-test.example";
        domains.add(d);
        return d;
    }

    /** 새 핫딜 게시판 */
    protected long dealBoard() {
        long id = ids.nextId();
        String slug = "hd" + Long.toString(id, 36);
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order, kind, write_min) VALUES (?, ?, ?, 950, 'DEAL', 'REGULAR')", id, slug, "핫딜 " + slug.substring(slug.length() - 6));
        catalog.invalidate();
        return id;
    }

    /** 새 일반 게시판 */
    protected long generalBoard() {
        long id = ids.nextId();
        String slug = "gn" + Long.toString(id, 36);
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 951)", id, slug, "일반 " + slug.substring(slug.length() - 6));
        catalog.invalidate();
        return id;
    }

    /** 회원 글 한 줄 */
    protected long boardPost(long boardId, long authorId, String title) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO posts (id, board_id, author_id, title, content) VALUES (?, ?, ?, ?, '본문')", id, boardId, authorId, title);
        return id;
    }

    /** 핫딜 글 + 진행 중 딜(판매처 {@code host}, 같은 딜 키는 글마다 다르다) */
    protected long deal(long boardId, long authorId, String host) {
        long postId = boardPost(boardId, authorId, "로봇 청소기 " + uniq());
        jdbc.update("INSERT INTO post_deals (post_id, board_id, author_id, store_name, product_url, product_host, product_url_key, normal_price, deal_price, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 30000, 19900, now(), now())",
                postId, boardId, authorId, "시험몰", "https://" + host + "/p/" + uniq(), host, String.format("%064x", postId));
        return postId;
    }

    protected void flag(long postId) {
        jdbc.update("UPDATE post_deals SET flagged_at = now() - interval '1 hour' WHERE post_id = ?", postId);
    }

    protected void endByAuthor(long postId) {
        jdbc.update("UPDATE post_deals SET status = 'ENDED', ended_by = 'AUTHOR', ended_by_id = author_id, ended_at = now() WHERE post_id = ?", postId);
    }

    protected void expire(long postId) {
        jdbc.update("UPDATE post_deals SET ends_at = now() - interval '1 hour' WHERE post_id = ?", postId);
    }

    /** 의심 구간 종료 요청(보상 대상) — 요청한 회원 */
    protected long flaggedRequest(long postId) {
        long member = newMember();
        jdbc.update("INSERT INTO deal_end_requests (id, post_id, member_id, author_id, reason, flagged_period, created_at) "
                + "SELECT ?, post_id, ?, author_id, 'SOLD_OUT', true, now() FROM post_deals WHERE post_id = ?", ids.nextId(), member, postId);
        return member;
    }

    protected Map<String, Object> dealRow(long postId) {
        return jdbc.queryForMap("SELECT * FROM post_deals WHERE post_id = ?", postId);
    }

    protected int notices(long user, String type, long postId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = ? AND post_id = ?", Integer.class, user, type, postId);
        return n == null ? 0 : n;
    }

    protected int rewards(long user) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'DEAL_END_REQUEST' AND ref_type = 'DEAL_END_REQUEST'",
                Integer.class, user);
        return n == null ? 0 : n;
    }

    /** 이 운영자가 남긴 가장 최근 감사 한 줄(없으면 null) */
    protected Map<String, Object> lastAudit(long operatorId, String action) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM audit_logs WHERE actor_id = ? AND action = ? ORDER BY occurred_at DESC, id DESC LIMIT 1",
                operatorId, action);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected int audits(long operatorId, String action) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id = ? AND action = ?", Integer.class, operatorId, action);
        return n == null ? 0 : n;
    }

    protected static JsonNode data(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).path("data");
    }

    protected static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
