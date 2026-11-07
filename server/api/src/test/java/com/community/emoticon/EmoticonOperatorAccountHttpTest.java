package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.service.EmoticonPackAdminService;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.identity.dto.request.FakeIdentityInput;
import com.community.identity.service.IdentityVerificationService;
import com.community.permission.entity.MemberPermission;
import com.community.role.entity.MemberRoleIds;
import com.community.testsupport.SiteOperatorFixture;
import com.community.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 운영자 회원 계정은 판매 중 팩 전부를 쓴다(docs/features/emoticon-contract.md 「운영자 회원 계정 연결(2026-11-05)」) — 앱 문맥 전체 + 실제 PostgreSQL + MockMvc.
 * 글 · 소모임 글 · 댓글 쓰기 · 고치기의 토큰 검사, 20개 상한, 판매 중단 · 연결 해제 · 사용 중지, 결제 준비 {@code 409 EMOTICON_PACK_OWNED reason OPERATOR},
 * {@code allPacks} · {@code operatorUsable} · 받기. 연결은 SQL 로 바로 넣는다.
 */
class EmoticonOperatorAccountHttpTest extends CommerceApiTestBase {

    private static final int PRICE = 2500;
    private static final List<MemberPermission> GENERAL = List.of(MemberPermission.POST_WRITE, MemberPermission.COMMENT_WRITE, MemberPermission.POST_REACT,
            MemberPermission.REPORT_CREATE, MemberPermission.MESSAGE_SEND, MemberPermission.FILE_ATTACH, MemberPermission.POINTSHOP_USE);

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private BoardCatalog boards;
    @Autowired
    private IdentityVerificationService identity;
    @Autowired
    private EmoticonStudioService studio;
    @Autowired
    private EmoticonReviewService reviews;
    @Autowired
    private EmoticonPackAdminService packAdmin;

    private MockMvc mvc;
    private Boolean paidWas;
    private String slug;
    private long board;
    private long operator;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        operator = newOperator();
        paidWas = jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class);
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = true WHERE id = 1");
        SiteOperatorFixture.fillBusiness(jdbc); // 유료 판매·구매는 운영 주체가 개인사업자 이상 + 통신판매업 신고번호(사용자 결정 2026-11-07)
        for (MemberPermission p : MemberPermission.values()) {
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready) VALUES (?, 'MEMBER', ?, ?, ?) ON CONFLICT (code) DO NOTHING", p.code(),
                    p.typeId(), p.group().name(), p.featureReady());
        }
        if (jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name, scope, grant_mode, builtin) VALUES (?, 'memberKind', 'MEMBER', ?, 'SITE', 'AUTO', true) "
                + "ON CONFLICT (role_id) DO NOTHING", MemberRoleIds.GENERAL, MemberRoleIds.GENERAL) == 1) {
            for (MemberPermission p : GENERAL) {
                jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ids.nextId(), MemberRoleIds.GENERAL, p.code());
            }
        }
        board = ids.nextId();
        slug = "eoa" + Long.toString(board, 36);
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 953)", board, slug, "운영자 팩 " + slug.substring(slug.length() - 5));
        boards.invalidate();
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE price > 0");
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ? WHERE id = 1", paidWas);
        SiteOperatorFixture.clear(jdbc);
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private long member() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        String base36 = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname, grade_key) VALUES (?, ?, 'REGULAR')", id, "a" + base36.substring(Math.max(0, base36.length() - 11)));
        return id;
    }

    /** 이어진 운영자 회원 계정(본인인증한 성인 — 결제 준비 자격 검사를 지나 운영자 판정까지 간다) */
    private long operatorAccount() {
        long id = member();
        identity.confirm(id, identity.start(id).verificationId(), new FakeIdentityInput("1990-05-14", newPerson()));
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", newOperator(), id);
        return id;
    }

    private long operatorOf(long userId) {
        return jdbc.queryForObject("SELECT operator_id FROM operator_member_accounts WHERE user_id = ?", Long.class, userId);
    }

    private void unlink(long userId) {
        jdbc.update("DELETE FROM operator_member_accounts WHERE user_id = ?", userId);
    }

    private static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B as(long memberId, B request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    private long image(long creator, String kind, String format, int frames) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, uploaded_by_user, created_at) "
                + "VALUES (?, ?, ?, 200, 200, 1000, ?, true, lpad(to_hex(?::bigint), 64, '0'), '/emoticons/test.gif', ?, now())", id, kind, format, frames, id, creator);
        return id;
    }

    /** 작가 팩을 승인(무료)한 뒤 운영자가 유료(2,500원)로 바꾼 판매 중 팩 */
    private long paidPack() {
        long creator = member();
        List<EmoticonStudioSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(new EmoticonStudioSaveRequest.Item(Long.toString(image(creator, "EMOTICON", "GIF", 2)), "표" + i));
        }
        EmoticonStudioSaveRequest body = new EmoticonStudioSaveRequest("운영자 시험 팩", "설명", List.of("시험"), Long.toString(image(creator, "COVER", "PNG", 1)), items, "FREE",
                null, true, true, true, null);
        long packId = Long.parseLong(tx.execute(s -> studio.submit(creator, null, body)).id());
        String review = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        tx.executeWithoutResult(s -> reviews.approve(operator, "127.0.0.1", review, new EmoticonReviewDecisionRequest(0, null)));
        jdbc.update("UPDATE emoticon_packs SET price = ? WHERE id = ?", PRICE, packId);
        return packId;
    }

    private void suspend(long pack) {
        tx.executeWithoutResult(s -> packAdmin.suspend(operator, null, Long.toString(pack), "확인하는 동안 멈춰요"));
    }

    private List<Long> emoticons(long pack) {
        return jdbc.queryForList("SELECT i.emoticon_id FROM emoticon_packs p JOIN emoticon_revision_items i ON i.revision_id = p.live_revision_id WHERE p.id = ? ORDER BY i.position",
                Long.class, pack);
    }

    private static String token(long id) {
        return ":emo-" + id + ":";
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private ResultActions writePost(long member, String content) throws Exception {
        return mvc.perform(as(member, post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"boardSlug\":\"" + slug + "\",\"title\":\"운영자 팩 글\",\"contentFormat\":\"TEXT\",\"content\":" + json(content) + "}")));
    }

    private ResultActions editPost(long member, long postId, String content) throws Exception {
        return mvc.perform(as(member, put("/api/posts/" + postId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"고친 운영자 팩 글\",\"contentFormat\":\"TEXT\",\"content\":" + json(content) + "}")));
    }

    private ResultActions writeComment(long member, long postId, String content) throws Exception {
        return mvc.perform(as(member, post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentFormat\":\"TEXT\",\"content\":" + json(content) + "}")));
    }

    private ResultActions editComment(long member, long commentId, String content) throws Exception {
        return mvc.perform(as(member, put("/api/comments/" + commentId).contentType(MediaType.APPLICATION_JSON).content("{\"contentFormat\":\"TEXT\",\"content\":" + json(content) + "}")));
    }

    private long postOf(long member) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO posts (id, board_id, author_id, title, content) VALUES (?, ?, ?, '운영자 팩 시험', '본문이에요')", id, board, member);
        return id;
    }

    private long commentOf(long postId, long member) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, content, created_at) VALUES (?, ?, ?, ?, '댓글이에요', now() - interval '1 hour')", id, postId, board,
                member);
        jdbc.update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = ?", postId);
        return id;
    }

    private long group(long leader) {
        long groupId = ids.nextId();
        jdbc.update("INSERT INTO groups (id, name, name_key, description, board_id, join_type, capacity, status, leader_id, creator_id, member_count, requested_at, opened_at) "
                + "VALUES (?, ?, ?, '시험', ?, 'OPEN', 50, 'ACTIVE', ?, ?, 1, now(), now())", groupId, "o" + Long.toString(groupId, 36), "o" + Long.toString(groupId, 36), board,
                leader, leader);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role, status, joined_at) VALUES (?, ?, 'LEADER', 'ACTIVE', now())", groupId, leader);
        return groupId;
    }

    private ResultActions writeGroupPost(long member, long groupId, String content) throws Exception {
        return mvc.perform(as(member, post("/api/groups/" + groupId + "/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"소모임 글\",\"contentFormat\":\"TEXT\",\"content\":" + json(content) + "}")));
    }

    /** 다음 쓰기가 작성 간격(429 — 설정 상한 3600초)에 걸리지 않게 시계를 민다 */
    private void later() {
        clock.set(clock.instant().plusSeconds(3601));
    }

    // ── 토큰 검사(글 · 댓글 · 소모임 글, 쓰기 · 고치기) ──────────────────────

    @Test
    void 운영자_회원_계정은_가지지_않은_판매_중_유료_팩을_글_댓글_소모임_글에_쓰고_사용함_판매가_생기지_않는다() throws Exception {
        long pack = paidPack();
        List<Long> emo = emoticons(pack);
        long user = operatorAccount();
        String created = writePost(user, "운영자 " + token(emo.get(0))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long postId = Long.parseLong(JsonPath.read(created, "$.data.id"));
        later();
        editPost(user, postId, "고침 " + token(emo.get(1)) + token(emo.get(2))).andExpect(status().isOk());
        long otherPost = postOf(member());
        writeComment(user, otherPost, "댓글 " + token(emo.get(3))).andExpect(status().isCreated());
        long commentId = commentOf(otherPost, user);
        editComment(user, commentId, "고친 댓글 " + token(emo.get(4))).andExpect(status().isOk());
        // 소모임 글 — 작성 간격에 걸리지 않게 다른 운영자 회원 계정으로
        long leader = operatorAccount();
        writeGroupPost(leader, group(leader), "모임 " + token(emo.get(5))).andExpect(status().is2xxSuccessful());
        // 가진 것으로 치지 않는다 — 받기 줄 · 구매(사용함) · 받은 수 · 판매 없음
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE user_id IN (?, ?)", Integer.class, user, leader)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_purchases WHERE user_id IN (?, ?) OR pack_id = ?", Integer.class, user, leader, pack)).isZero();
        assertThat(jdbc.queryForObject("SELECT acquire_count FROM emoticon_packs WHERE id = ?", Integer.class, pack)).isZero();
        // 일반 회원은 같은 본문이 409
        writePost(member(), "회원 " + token(emo.get(0))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
    }

    @Test
    void 이십개_상한은_운영자_회원_계정도_400() throws Exception {
        long pack = paidPack();
        long user = operatorAccount();
        String one = token(emoticons(pack).get(0));
        writePost(user, "많이 " + one.repeat(21)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.content").value("로보티콘은 한 번에 20개까지 쓸 수 있어요."));
        writeComment(user, postOf(member()), one.repeat(21)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.content").exists());
        writeGroupPost(user, group(user), "x " + one.repeat(21)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.content").exists());
        writePost(user, "딱 " + one.repeat(20)).andExpect(status().isCreated());
    }

    @Test
    void 판매_중단_팩은_가지지_않았으면_409() throws Exception {
        long pack = paidPack();
        long user = operatorAccount();
        suspend(pack);
        writePost(user, "멈춤 " + token(emoticons(pack).get(0))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
        writeComment(user, postOf(member()), "멈춤 " + token(emoticons(pack).get(0))).andExpect(status().isConflict());
        writeGroupPost(user, group(user), "멈춤 " + token(emoticons(pack).get(0))).andExpect(status().isConflict());
    }

    @Test
    void 연결_해제_운영자_사용_중지_뒤에는_409이고_고치기도_막힌다() throws Exception {
        long pack = paidPack();
        String body = "운영자 " + token(emoticons(pack).get(0));
        long unlinked = operatorAccount();
        String created = writePost(unlinked, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long postId = Long.parseLong(JsonPath.read(created, "$.data.id"));
        unlink(unlinked);
        later();
        writePost(unlinked, body).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
        editPost(unlinked, postId, body).andExpect(status().isConflict());
        long disabled = operatorAccount();
        jdbc.update("UPDATE operators SET enabled = false WHERE id = ?", operatorOf(disabled));
        writePost(disabled, body).andExpect(status().isConflict());
        writeComment(disabled, postOf(member()), body).andExpect(status().isConflict());
        writeGroupPost(disabled, group(disabled), body).andExpect(status().isConflict());
    }

    @Test
    void 게시판_매니저_스텝은_바뀌지_않는다() throws Exception {
        long pack = paidPack();
        String body = "운영진 " + token(emoticons(pack).get(0));
        long manager = member();
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER')", board, manager);
        long staff = member();
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'STAFF')", board, staff);
        for (long who : new long[] {manager, staff}) {
            writePost(who, body).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_NOT_OWNED"));
            mvc.perform(as(who, get("/api/me/emoticons"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.allPacks").value(false));
            mvc.perform(as(who, get("/api/emoticons/packs/" + pack))).andExpect(jsonPath("$.data.operatorUsable").value(false));
        }
    }

    // ── 결제 준비 · 받기 · 내 로보티콘 · 팩 상세 ────────────────────────────

    @Test
    void 결제_준비는_409_EMOTICON_PACK_OWNED_reason_OPERATOR_이고_구매를_만들지_않는다() throws Exception {
        long pack = paidPack();
        long user = operatorAccount();
        mvc.perform(as(user, post("/api/emoticons/packs/" + pack + "/purchase")).contentType(MediaType.APPLICATION_JSON).content("{\"agreeRefundNotice\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_OWNED"))
                .andExpect(jsonPath("$.error.reason").value("OPERATOR"))
                .andExpect(jsonPath("$.error.message").value("운영자 계정은 판매 중인 모든 팩을 사지 않아도 쓸 수 있어요."));
        mvc.perform(as(user, post("/api/emoticons/packs/" + pack + "/purchase")).contentType(MediaType.APPLICATION_JSON).content("{\"agreeRefundNotice\":true}")
                        .header("Accept-Language", "en"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.reason").value("OPERATOR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_purchases WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_orders WHERE user_id = ?", Integer.class, user)).isZero();
        // 힘이 멈추면 보통 회원처럼 결제 준비가 된다
        unlink(user);
        mvc.perform(as(user, post("/api/emoticons/packs/" + pack + "/purchase")).contentType(MediaType.APPLICATION_JSON).content("{\"agreeRefundNotice\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void allPacks_operatorUsable_받기() throws Exception {
        long pack = paidPack();
        long user = operatorAccount();
        mvc.perform(as(user, get("/api/me/emoticons"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.allPacks").value(true))
                .andExpect(jsonPath("$.data.packs.length()").value(0));
        mvc.perform(as(member(), get("/api/me/emoticons"))).andExpect(jsonPath("$.data.allPacks").value(false));
        mvc.perform(as(user, get("/api/emoticons/packs/" + pack))).andExpect(status().isOk()).andExpect(jsonPath("$.data.operatorUsable").value(true))
                .andExpect(jsonPath("$.data.acquired").value(false));
        mvc.perform(get("/api/emoticons/packs/" + pack)).andExpect(jsonPath("$.data.operatorUsable").value(false));
        // 무료 팩 받기 — 200 이지만 줄 · 받은 수가 생기지 않는다
        jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", pack);
        mvc.perform(as(user, post("/api/emoticons/packs/" + pack + "/acquire"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.acquired").value(false))
                .andExpect(jsonPath("$.data.acquireCount").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM emoticon_acquisitions WHERE user_id = ?", Integer.class, user)).isZero();
        // 운영자 사용 중지 — 바로 일반 규칙
        jdbc.update("UPDATE operators SET enabled = false WHERE id = ?", operatorOf(user));
        mvc.perform(as(user, get("/api/me/emoticons"))).andExpect(jsonPath("$.data.allPacks").value(false));
        mvc.perform(as(user, get("/api/emoticons/packs/" + pack))).andExpect(jsonPath("$.data.operatorUsable").value(false));
    }
}
