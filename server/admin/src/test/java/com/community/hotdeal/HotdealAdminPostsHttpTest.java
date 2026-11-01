package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.hotdeal.service.DealModeration;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 핫딜 글 API(3단계) — 목록 {@code view}·{@code blockedActive}·{@code counts}, 상세·이력 모양, 운영진 종료·재개(권한·상태 409·보상·작성자 알림·감사),
 * 게시판 매니저·스텝 담당 판정(shared {@link DealModeration}), 관리자 메뉴 4줄이 admin-web 과 같은지.
 */
class HotdealAdminPostsHttpTest extends HotdealAdminTestBase {

    @Autowired
    private DealModeration moderation;

    private Cookie reader() throws Exception {
        return login(operatorWith(Permission.HOTDEAL_READ));
    }

    @Test
    void 메뉴_네_줄은_서버와_화면이_같고_hotdealReviews_다음이다() throws Exception {
        String menu = Files.readString(Path.of("..", "..", "apps", "admin-web", "app", "utils", "util-admin-menu.ts"));
        for (String key : List.of("hotdeals", "hotdealDomains", "hotdealSuspicions", "hotdealSettings")) {
            AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen(key).orElseThrow(() -> new AssertionError("서버 메뉴에 없다: " + key));
            assertThat(menu).contains("{ key: '" + key + "', to: '" + screen.path() + "', icon: '" + screen.icon() + "', labelKey: '" + screen.labelKey() + "', permission: '"
                    + screen.permission().code() + "' }");
        }
        assertThat(AdminScreenRegistry.screen("hotdealSettings").orElseThrow().permission()).isEqualTo(Permission.HOTDEAL_MANAGE);
        for (Permission p : List.of(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE, Permission.HOTDEAL_DOMAIN_MANAGE)) {
            assertThat(p.featureReady()).as(p.code()).isTrue();
        }
    }

    @Test
    void 목록은_상태별로_거르고_counts_는_상태_조건만_뺀_수다() throws Exception {
        long board = dealBoard();
        long author = newMember();
        String blockedHost = domain("blk");
        jdbc.update("INSERT INTO deal_domains (id, domain, rule, created_at, updated_at) VALUES (?, ?, 'BLOCK', now(), now())", ids.nextId(), blockedHost);
        rules.invalidate();
        long active = deal(board, author, "a" + uniq() + ".shop.example");
        long blocked = deal(board, author, "x." + blockedHost); // 하위 도메인도 차단에 걸린다(접미사)
        long flagged = deal(board, author, "f" + uniq() + ".shop.example");
        flag(flagged);
        long ended = deal(board, author, "e" + uniq() + ".shop.example");
        endByAuthor(ended);
        long expired = deal(board, author, "x" + uniq() + ".shop.example");
        expire(expired);
        long endedBlocked = deal(board, author, blockedHost);
        endByAuthor(endedBlocked); // 끝난 글은 「차단 도메인 진행 중」이 아니다
        Cookie reader = reader();

        JsonNode all = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).cookie(reader))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20)).andReturn());
        assertThat(all.path("total").asLong()).isEqualTo(6);
        assertThat(all.path("counts").path("ACTIVE").asLong()).isEqualTo(2);
        assertThat(all.path("counts").path("FLAGGED").asLong()).isEqualTo(1);
        assertThat(all.path("counts").path("ENDED").asLong()).isEqualTo(2);
        assertThat(all.path("counts").path("EXPIRED").asLong()).isEqualTo(1);
        assertThat(all.path("counts").path("ALL").asLong()).isEqualTo(6);
        assertThat(all.path("blockedActiveCount").asLong()).isEqualTo(1);
        assertThat(all.path("items").get(0).path("postId").asString()).isEqualTo(Long.toString(endedBlocked)); // 최신 등록순
        List<String> views = new ArrayList<>();
        all.path("items").forEach(i -> views.add(i.path("postId").asString() + ":" + i.path("deal").path("view").asString()));
        assertThat(views).contains(active + ":ACTIVE", flagged + ":FLAGGED", ended + ":ENDED", expired + ":EXPIRED");
        JsonNode blockedRow = row(all, blocked);
        assertThat(blockedRow.path("blockedHost").asString()).isEqualTo("x." + blockedHost);
        assertThat(row(all, endedBlocked).path("blockedHost").isNull()).isTrue();
        assertThat(blockedRow.path("board").path("id").asString()).isEqualTo(Long.toString(board));
        assertThat(blockedRow.path("author").path("id").asString()).isEqualTo(Long.toString(author));
        assertThat(blockedRow.path("deal").path("buyUrl").asString()).isEqualTo("/api/deals/" + blocked + "/go");
        assertThat(blockedRow.path("deal").path("endRequests").path("mine").isNull()).isTrue();

        JsonNode onlyFlagged = data(mvc.perform(get("/api/admin/hotdeals").param("view", "FLAGGED").param("board", Long.toString(board)).cookie(reader)).andReturn());
        assertThat(onlyFlagged.path("total").asLong()).isEqualTo(1);
        assertThat(onlyFlagged.path("items").get(0).path("postId").asString()).isEqualTo(Long.toString(flagged));
        assertThat(onlyFlagged.path("counts").path("ALL").asLong()).isEqualTo(6); // 상태 조건만 뺀 수

        JsonNode blockedOnly = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).param("blockedActive", "true")
                .cookie(reader)).andReturn());
        assertThat(blockedOnly.path("total").asLong()).isEqualTo(1);
        assertThat(blockedOnly.path("items").get(0).path("postId").asString()).isEqualTo(Long.toString(blocked));
        assertThat(blockedOnly.path("counts").path("ALL").asLong()).isEqualTo(1); // 차단 거르기는 counts 에도
        assertThat(blockedOnly.path("counts").path("ACTIVE").asLong()).isEqualTo(1);
        assertThat(blockedOnly.path("blockedActiveCount").asLong()).isEqualTo(1);

        // 다른 게시판 ID · 일반 게시판 ID 는 아무 글도 안 맞는다
        mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(generalBoard())).cookie(reader))
                .andExpect(jsonPath("$.data.total").value(0));
        // 날짜 형식 오류 · 뒤집힌 기간
        mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("createdFrom", "어제").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.createdFrom").exists());
        mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("createdFrom", "2026-11-02T00:00:00Z").param("createdTo", "2026-11-01T00:00:00Z")
                .cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.createdTo").exists());
        // 권한 없음
        mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").cookie(login(operatorWith()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void 검색은_제목_쇼핑몰_작성자를_보고_게시판_고르기는_핫딜_게시판만() throws Exception {
        long board = dealBoard();
        long author = newMember();
        long a = deal(board, author, "q" + uniq() + ".shop.example");
        String mall = "몰" + uniq();
        jdbc.update("UPDATE post_deals SET store_name = ? WHERE post_id = ?", mall, a);
        deal(board, newMember(), "q" + uniq() + ".shop.example");
        Cookie reader = reader();
        JsonNode found = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).param("q", mall).cookie(reader))
                .andReturn());
        assertThat(found.path("total").asLong()).isEqualTo(1);
        assertThat(found.path("items").get(0).path("deal").path("storeName").asString()).isEqualTo(mall);
        String nickname = jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, author);
        assertThat(data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).param("q", nickname).cookie(reader))
                .andReturn()).path("total").asLong()).isEqualTo(1);

        long general = generalBoard();
        JsonNode boards = data(mvc.perform(get("/api/admin/hotdeals/boards").cookie(reader)).andExpect(status().isOk()).andReturn()).path("boards");
        List<String> boardIds = new ArrayList<>();
        boards.forEach(b -> boardIds.add(b.path("id").asString()));
        assertThat(boardIds).contains(Long.toString(board)).doesNotContain(Long.toString(general));
        assertThat(boards.get(0).has("prefixes")).isTrue();
        assertThat(boards.get(0).has("active")).isTrue();
    }

    @Test
    void 상세와_수정_이력_모양() throws Exception {
        long board = dealBoard();
        long author = newMember();
        String host = "d" + uniq() + ".shop.example";
        long first = deal(board, author, host);
        long same = deal(board, newMember(), host);
        jdbc.update("UPDATE post_deals SET product_url_key = (SELECT product_url_key FROM post_deals WHERE post_id = ?) WHERE post_id = ?", first, same);
        jdbc.update("UPDATE post_deals SET kept_at = now(), keep_count = 1, click_count = 7 WHERE post_id = ?", first);
        jdbc.update("INSERT INTO post_deal_revisions (id, post_id, actor_kind, actor_id, changes, host_changed, price_edit, created_at) VALUES (?, ?, 'AUTHOR', ?, "
                + "'[{\"field\":\"dealPrice\",\"before\":\"21000\",\"after\":\"19900\"},{\"field\":\"affiliate\",\"before\":\"false\",\"after\":\"true\"},"
                + "{\"field\":\"couponNote\",\"before\":null,\"after\":\"쿠폰\"}]'::jsonb, true, true, now())", ids.nextId(), first, author);
        Cookie reader = reader();

        JsonNode detail = data(mvc.perform(get("/api/admin/hotdeals/" + first).cookie(reader)).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("row").path("postId").asString()).isEqualTo(Long.toString(first));
        assertThat(detail.path("row").path("clickCount").asLong()).isEqualTo(7);
        assertThat(detail.path("row").path("deal").path("sameDealCount").asInt()).isEqualTo(1);
        assertThat(detail.path("sameDeals")).hasSize(1);
        assertThat(detail.path("sameDeals").get(0).path("postId").asString()).isEqualTo(Long.toString(same));
        assertThat(detail.path("sameDeals").get(0).path("view").asString()).isEqualTo("ACTIVE");
        assertThat(detail.path("keptAt").isNull()).isFalse();
        assertThat(detail.path("affiliateMode").asString()).isEqualTo("MEMBER");
        assertThat(detail.has("publicUrl")).isTrue();
        assertThat(detail.path("endedByName").isNull()).isTrue();

        JsonNode revisions = data(mvc.perform(get("/api/admin/hotdeals/" + first + "/revisions").cookie(reader)).andExpect(status().isOk()).andReturn());
        assertThat(revisions.path("retentionMonths").asInt()).isEqualTo(6);
        JsonNode r = revisions.path("items").get(0);
        assertThat(r.path("actor").path("kind").asString()).isEqualTo("AUTHOR");
        assertThat(r.path("actor").path("name").asString()).isEqualTo(jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, author));
        assertThat(r.path("hostChanged").asBoolean()).isTrue();
        assertThat(r.path("changes").get(0).path("field").asString()).isEqualTo("dealPrice");
        assertThat(r.path("changes").get(0).path("before").isNumber()).isTrue();
        assertThat(r.path("changes").get(0).path("after").asLong()).isEqualTo(19900);
        assertThat(r.path("changes").get(1).path("after").isBoolean()).isTrue();
        assertThat(r.path("changes").get(2).path("before").isNull()).isTrue();

        mvc.perform(get("/api/admin/hotdeals/" + boardPost(generalBoard(), author, "일반 글")).cookie(reader)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
        mvc.perform(get("/api/admin/hotdeals/abc/revisions").cookie(reader)).andExpect(status().isNotFound());
    }

    @Test
    void 운영진_종료는_보상과_작성자_알림이_실제로_나가고_다시_종료하면_409() throws Exception {
        long board = dealBoard();
        long author = newMember();
        long postId = deal(board, author, "m" + uniq() + ".shop.example");
        flag(postId);
        long requester = flaggedRequest(postId);
        long manager = operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE);
        Cookie cookie = login(manager);

        // 읽기 권한만으로는 못 한다
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/end"), reader()).content("{}")).andExpect(status().isForbidden());
        // 사유 200자 넘음
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/end"), cookie).content("{\"reason\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());

        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/end"), cookie).content("{\"reason\":\"가격이 바뀌었어요\"}")).andExpect(status().isNoContent());
        assertThat(dealRow(postId).get("status")).isEqualTo("ENDED");
        assertThat(dealRow(postId).get("ended_by")).isEqualTo("MANAGER");
        assertThat(dealRow(postId).get("ended_by_id")).isEqualTo(manager);
        assertThat(dealRow(postId).get("end_note")).isEqualTo("가격이 바뀌었어요");
        assertThat(rewards(requester)).isEqualTo(1);
        assertThat(notices(author, "DEAL_MANAGER_ENDED", postId)).isEqualTo(1);
        assertThat(lastAudit(manager, AuditActions.HOTDEAL_END)).isNotNull();
        assertThat((String) lastAudit(manager, AuditActions.HOTDEAL_END).get("detail")).contains("FLAGGED").contains("가격이 바뀌었어요");

        JsonNode detail = data(mvc.perform(get("/api/admin/hotdeals/" + postId).cookie(cookie)).andReturn());
        assertThat(detail.path("endedByName").asString()).isEqualTo(operatorName(manager));
        assertThat(detail.path("endReason").asString()).isEqualTo("가격이 바뀌었어요");
        assertThat(detail.path("row").path("deal").path("endedBy").asString()).isEqualTo("MANAGER");

        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/end"), cookie).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_STATE_CHANGED"));
        assertThat(rewards(requester)).isEqualTo(1); // 두 번 주지 않는다
        // 일반 글 · 없는 글
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + boardPost(generalBoard(), author, "일반") + "/end"), cookie).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void 기간_지난_딜도_종료하고_재개는_종료만_의심_요청을_지우고_기간이_지났으면_알린다() throws Exception {
        long board = dealBoard();
        long author = newMember();
        long postId = deal(board, author, "r" + uniq() + ".shop.example");
        expire(postId);
        long manager = operatorWith(Permission.HOTDEAL_MANAGE);
        Cookie cookie = login(manager);

        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/reopen"), cookie).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_STATE_CHANGED")); // 종료가 아니면 재개 없음
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/end"), cookie)).andExpect(status().isNoContent()); // 기간 지남도 종료(본문 없이도)
        jdbc.update("UPDATE post_deals SET flagged_at = now(), keep_count = 2 WHERE post_id = ?", postId);
        flaggedRequest(postId);

        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/reopen"), cookie).content("{\"reason\":\"다시 열어요\"}")).andExpect(status().isNoContent());
        assertThat(dealRow(postId).get("status")).isEqualTo("ACTIVE");
        assertThat(dealRow(postId).get("flagged_at")).isNull();
        assertThat(dealRow(postId).get("keep_count")).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, postId)).isZero();
        assertThat(notices(author, "DEAL_REOPENED_FIX_ENDS", postId)).isEqualTo(1);
        assertThat((String) lastAudit(manager, AuditActions.HOTDEAL_REOPEN).get("detail")).contains("다시 열어요").contains("MANAGER");
        mvc.perform(withCsrf(post("/api/admin/hotdeals/" + postId + "/reopen"), cookie).content("{}")).andExpect(status().isConflict());
    }

    @Test
    void 게시판_매니저_스텝은_그_게시판만_다른_게시판_매니저는_안_된다() {
        long board = dealBoard();
        long other = dealBoard();
        long author = newMember();
        long postId = deal(board, author, "s" + uniq() + ".shop.example");
        long staff = newMember();
        long manager = newMember();
        long otherManager = newMember();
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'STAFF')", board, staff);
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER')", board, manager);
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER')", other, otherManager);

        assertThat(moderation.end(postId, DealModeration.Actor.boardMember(otherManager), null)).isEqualTo(DealModeration.Result.FORBIDDEN);
        assertThat(moderation.end(postId, DealModeration.Actor.boardMember(newMember()), null)).isEqualTo(DealModeration.Result.FORBIDDEN);
        assertThat(moderation.end(postId, DealModeration.Actor.operator(operatorWith(), false), null)).isEqualTo(DealModeration.Result.FORBIDDEN);
        assertThat(dealRow(postId).get("status")).isEqualTo("ACTIVE");

        assertThat(moderation.end(postId, DealModeration.Actor.boardMember(staff), "스텝 종료")).isEqualTo(DealModeration.Result.CHANGED);
        assertThat(dealRow(postId).get("ended_by")).isEqualTo("MANAGER");
        assertThat(dealRow(postId).get("ended_by_id")).isEqualTo(staff);
        assertThat(notices(author, "DEAL_MANAGER_ENDED", postId)).isEqualTo(1);
        assertThat(moderation.end(postId, DealModeration.Actor.boardMember(manager), null)).isEqualTo(DealModeration.Result.STATE_CHANGED);
        assertThat(moderation.reopen(postId, DealModeration.Actor.boardMember(otherManager))).isEqualTo(DealModeration.Result.FORBIDDEN);
        assertThat(moderation.reopen(postId, DealModeration.Actor.boardMember(manager))).isEqualTo(DealModeration.Result.CHANGED);
        assertThat(moderation.end(boardPost(generalBoard(), author, "일반"), DealModeration.Actor.boardMember(manager), null)).isEqualTo(DealModeration.Result.NOT_FOUND);
    }

    private static JsonNode row(JsonNode page, long postId) {
        for (JsonNode i : page.path("items")) {
            if (i.path("postId").asString().equals(Long.toString(postId))) {
                return i;
            }
        }
        throw new AssertionError("줄이 없다: " + postId);
    }
}
