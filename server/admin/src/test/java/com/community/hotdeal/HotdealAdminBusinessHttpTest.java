package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 핫딜 글의 출처(biz-hotdeal-contract G — V123): {@code source=MEMBER|BUSINESS} 거르기, 줄·상세의 {@code source}·{@code business{name, applicationId}}.
 */
class HotdealAdminBusinessHttpTest extends HotdealAdminTestBase {

    @Test
    void 출처로_거르고_사업자_글_줄은_상호와_신청_건을_싣는다() throws Exception {
        long board = dealBoard();
        long author = newMember();
        long member = deal(board, author, "m" + uniq() + ".shop.example");
        long business = deal(board, author, "b" + uniq() + ".shop.example");
        long applicationId = ids.nextId();
        jdbc.update("UPDATE post_deals SET source = 'BUSINESS', biz_application_id = ?, business_name = '출처상사' WHERE post_id = ?", applicationId, business);
        Cookie reader = login(operatorWith(Permission.HOTDEAL_READ));

        JsonNode all = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).cookie(reader)).andExpect(status().isOk())
                .andReturn());
        assertThat(all.path("total").asLong()).isEqualTo(2);
        JsonNode biz = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).param("source", "BUSINESS").cookie(reader))
                .andExpect(status().isOk()).andReturn());
        assertThat(biz.path("total").asLong()).isEqualTo(1);
        JsonNode row = biz.path("items").get(0);
        assertThat(row.path("postId").asString()).isEqualTo(Long.toString(business));
        assertThat(row.path("source").asString()).isEqualTo("BUSINESS");
        assertThat(row.path("business").path("name").asString()).isEqualTo("출처상사");
        assertThat(row.path("business").path("applicationId").asString()).isEqualTo(Long.toString(applicationId));
        assertThat(row.path("deal").path("business").path("name").asString()).isEqualTo("출처상사");
        assertThat(biz.path("counts").path("ALL").asLong()).isEqualTo(1);

        JsonNode mem = data(mvc.perform(get("/api/admin/hotdeals").param("view", "ALL").param("board", Long.toString(board)).param("source", "member").cookie(reader))
                .andExpect(status().isOk()).andReturn());
        List<String> rows = new ArrayList<>();
        mem.path("items").forEach(i -> rows.add(i.path("postId").asString() + ":" + i.path("source").asString() + ":" + (i.path("business").isNull() || i.path("business").isMissingNode())));
        assertThat(rows).containsExactly(member + ":MEMBER:true");

        JsonNode detail = data(mvc.perform(get("/api/admin/hotdeals/" + business).cookie(reader)).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("row").path("business").path("applicationId").asString()).isEqualTo(Long.toString(applicationId));
    }
}
