package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.commerce.support.MoneyRequestGuard;
import com.community.permission.entity.MemberPermission;
import com.community.point.service.PointService;
import com.community.pointshop.dto.request.PointShopItemSaveRequest;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.service.PointShopCatalogService;
import com.community.role.entity.MemberRoleIds;
import com.community.testsupport.PointInvariants;
import com.community.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 포인트 상점 회원 API 시험의 바탕(커머스 1-2 — commerce-stage1-contract 「1-2 화면 목업」 API 후보) — 공개 사이트 앱 문맥 전체 + 실제 PostgreSQL({@link CommerceApiTestBase}) + MockMvc
 * (로그인 주체를 요청에 싣는다 — 보안 필터 없이, 그래서 401 은 컨트롤러가 낸다). 시험 데이터는 실제 서비스로 만든다(포인트 지급 {@link PointService#adjust}, 품목 {@link PointShopCatalogService#create}).
 * 시계는 {@code MutableClock}(포인트·포인트 상점 서비스가 쓴다) — 요청 제한·멱등 키는 시스템 시계다.
 *
 * <p>회원 역할: 시험 DB 에는 관리자 서버가 넣는 내장 회원 역할이 없어(없으면 권한을 제한하지 않는다) 처음 한 번 운영과 같은 일반·사업자 회원 역할을 넣는다
 * ({@code BuiltinRoleSeeder} 와 같은 권한 — 일반 회원은 {@code pointshop.use} 를 갖고 사업자 회원은 신고만).
 */
abstract class PointShopApiTestBase extends CommerceApiTestBase {

    private static final List<MemberPermission> GENERAL = List.of(MemberPermission.POST_WRITE, MemberPermission.COMMENT_WRITE, MemberPermission.POST_REACT,
            MemberPermission.REPORT_CREATE, MemberPermission.MESSAGE_SEND, MemberPermission.FILE_ATTACH, MemberPermission.POINTSHOP_USE);
    private static final List<MemberPermission> BUSINESS = List.of(MemberPermission.REPORT_CREATE);

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected PointService points;
    @Autowired
    protected PointShopCatalogService catalog;

    protected MockMvc mvc;
    protected long operator;

    @BeforeEach
    void pointShopSetUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        operator = newOperator();
        seedMemberRoles();
    }

    private void seedMemberRoles() {
        for (MemberPermission p : MemberPermission.values()) {
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready) VALUES (?, 'MEMBER', ?, ?, ?) ON CONFLICT (code) DO NOTHING", p.code(),
                    p.typeId(), p.group().name(), p.featureReady());
        }
        role(MemberRoleIds.GENERAL, GENERAL);
        role(MemberRoleIds.BUSINESS, BUSINESS);
    }

    private void role(String roleId, List<MemberPermission> granted) {
        int created = jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name, scope, grant_mode, builtin) VALUES (?, 'memberKind', 'MEMBER', ?, 'SITE', 'AUTO', true) "
                + "ON CONFLICT (role_id) DO NOTHING", roleId, roleId);
        if (created == 1) {
            for (MemberPermission p : granted) {
                jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ids.nextId(), roleId, p.code());
            }
        }
    }

    // ── 사람 ────────────────────────────────────────────────────────────────

    /** 이메일 인증을 마친 정상 회원 + 프로필(닉네임은 ID 로 겹치지 않게) */
    protected long member() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, nicknameOf(id));
        return id;
    }

    /** 이메일 인증 전 회원 + 프로필 */
    protected long unverifiedMember() {
        long id = newMember();
        jdbc.update("INSERT INTO profiles (user_id, nickname) VALUES (?, ?)", id, nicknameOf(id));
        return id;
    }

    /** 사업자 회원(회원 역할 — 포인트 상점 권한 없음) */
    protected long businessMember() {
        long id = member();
        jdbc.update("INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)", ids.nextId(), id, MemberRoleIds.BUSINESS);
        return id;
    }

    protected static String nicknameOf(long id) {
        String base36 = Long.toString(id, 36);
        return "m" + base36.substring(Math.max(0, base36.length() - 11));
    }

    protected void suspend(long userId) {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", userId);
    }

    /** 포인트 동결(기한 없음) */
    protected void freeze(long userId) {
        jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'POINT_FREEZE', '시험 동결', ?, 'AUTO', "
                + "'POINT_TEST', ?)", ids.nextId(), userId, Timestamp.from(clock.instant().minusSeconds(60)), Timestamp.from(clock.instant()));
    }

    /** 적립(무상) 포인트 지급 — 운영 지급 한 줄 */
    protected void grant(long userId, int amount) {
        tx.executeWithoutResult(s -> points.adjust(userId, amount, "시험 지급", operator));
    }

    protected long balance(long userId) {
        List<Long> rows = jdbc.queryForList("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    protected void assertInvariants(long... members) {
        assertThat(PointInvariants.violations(jdbc, members)).as("포인트 불변식(commerce.md 5-5)").isEmpty();
    }

    // ── 품목 ────────────────────────────────────────────────────────────────

    protected String code(String prefix) {
        return prefix + "-" + Long.toString(ids.nextId(), 36);
    }

    protected long createItem(PointShopItemKind kind, PointShopUsageType usage, int price, String color, String border, Integer duration, Integer stock,
                              Instant saleStartAt, Instant saleEndAt, boolean active) {
        String code = code("t");
        PointShopItemSaveRequest request = new PointShopItemSaveRequest(code, kind.name(), usage.name(), "시험 " + code, null, "시험 품목이에요.", null, color, border, null, price, duration,
                stock, saleStartAt, saleEndAt, active, 0);
        return Long.parseLong(tx.execute(s -> catalog.create(request, operator, "127.0.0.1")).id());
    }

    protected long colorItem(int price, String hex) {
        return createItem(PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERMANENT, price, hex, null, null, null, null, null, true);
    }

    protected long borderItem(int price, String style, Integer stock) {
        return createItem(PointShopItemKind.PROFILE_BORDER, PointShopUsageType.PERMANENT, price, null, style, null, stock, null, null, true);
    }

    protected long periodColorItem(int price, String hex, int days) {
        return createItem(PointShopItemKind.NICKNAME_COLOR, PointShopUsageType.PERIOD, price, hex, null, days, null, null, null, true);
    }

    /** V86 이 넣은 닉네임 변경권 품목 */
    protected long seedTicket() {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'nickname-change'", Long.class);
    }

    protected int seedTicketPrice() {
        return jdbc.queryForObject("SELECT price_point FROM point_shop_items WHERE code = 'nickname-change'", Integer.class);
    }

    /** 시드 아바타 품목 ID */
    protected long avatarItem(String avatarId) {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE avatar_id = ?", Long.class, avatarId);
    }

    protected int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    // ── 요청 ────────────────────────────────────────────────────────────────

    protected static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    protected static String key() {
        return UUID.randomUUID().toString();
    }

    protected ResultActions purchase(long member, String idempotencyKey, long itemId, boolean agree) throws Exception {
        return purchaseBody(member, idempotencyKey, "{\"itemId\":\"" + itemId + "\",\"agreeRefundNotice\":" + agree + "}");
    }

    protected ResultActions purchaseBody(long member, String idempotencyKey, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/point-shop/purchases").contentType(MediaType.APPLICATION_JSON).content(json);
        if (idempotencyKey != null) {
            request = request.header(MoneyRequestGuard.HEADER, idempotencyKey);
        }
        return mvc.perform(as(member, request));
    }

    /** 사서 보유 줄 ID 를 돌려준다(200 이어야 한다) */
    protected long buy(long member, long itemId) throws Exception {
        MvcResult result = purchase(member, key(), itemId, true).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(), "$.data.userItemId"));
    }

    protected static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }
}
