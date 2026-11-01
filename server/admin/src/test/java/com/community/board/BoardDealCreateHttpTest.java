package com.community.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 게시판 만들기에서 핫딜 종류({@code kind: "DEAL"}) — hotdeal-contract 「서버 구현 결정 (2026-11-01)」.
 * 만든 핫딜 게시판은 19금 끔 · 글쓰기 최소 자격 첫 등급 바로 위 · 비회원 글쓰기 없음 · 가젯 노출 금지 인기글이고, 상세를 그대로 다시 저장해도 검사에 걸리지 않는다.
 * 직접홍보(PROMO)는 여전히 거절, 소모임 분류는 소모임 게시판만. 핫딜 게시판의 19금 켜기·첫 등급 글쓰기 저장은 400.
 * 핫딜 게시판에도 매니저·스텝을 지정할 수 있다(핫딜 담당 = 그 게시판 매니저·스텝).
 * 핫딜 게시판 말머리는 공통을 끄고 고유 말머리 로봇·AI·메이킹·기타로 시작하고, 일반 게시판은 공통 넷 그대로다(사용자 지시 2026-11-02).
 */
class BoardDealCreateHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private BoardCatalog catalog;

    private Cookie session;
    private final List<String> created = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        session = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE, Permission.BOARD_SETTINGS, Permission.BOARD_DELETE,
                Permission.GROUP_SETTINGS, Permission.BOARD_STAFF_MANAGE));
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (String id : created) {
            mvc.perform(withCsrf(delete("/api/admin/boards/" + id), session)).andExpect(status().isNoContent());
        }
        catalog.invalidate();
    }

    private ResultActions create(String kind, String layout, String extra) throws Exception {
        String suffix = Long.toString(ids.nextId(), 36);
        String body = "{\"name\":\"핫딜 " + suffix.substring(suffix.length() - 6) + "\",\"slug\":\"hd" + suffix + "\",\"description\":\"핫딜 만들기 시험\","
                + "\"layout\":\"" + layout + "\",\"kind\":\"" + kind + "\"" + extra + "}";
        return mvc.perform(withCsrf(post("/api/admin/boards"), session).content(body));
    }

    private String createDeal(String layout) throws Exception {
        String response = create("DEAL", layout, "").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.data.id");
        created.add(id);
        return id;
    }

    private String detail(String id) throws Exception {
        return mvc.perform(get("/api/admin/boards/" + id).cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** 상세 응답을 화면이 보내는 저장 요청 모양으로 그대로 옮긴다(공통 말머리는 켜고 끄기만, 고유 말머리는 칸 전부) */
    @SuppressWarnings("unchecked")
    private Map<String, Object> saveBodyFrom(String detailJson) {
        Map<String, Object> data = JsonPath.read(detailJson, "$.data");
        Map<String, Object> body = new LinkedHashMap<>();
        for (String field : List.of("name", "nameEn", "description", "layout", "viewToggle", "color", "allowAdult", "investmentNotice", "writeLock",
                "introText", "writeGuideText", "policy", "gadgetExclusions", "adsHidden")) {
            body.put(field, data.get(field));
        }
        List<Map<String, Object>> common = new ArrayList<>();
        List<Map<String, Object>> own = new ArrayList<>();
        for (Map<String, Object> prefix : (List<Map<String, Object>>) data.get("prefixes")) {
            if (prefix.get("common") != null) {
                common.add(Map.of("id", prefix.get("id"), "active", prefix.get("active")));
            } else {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", prefix.get("id"));
                item.put("name", prefix.get("name"));
                item.put("staffOnly", prefix.get("staffOnly"));
                item.put("color", prefix.get("color"));
                item.put("active", prefix.get("active"));
                own.add(item);
            }
        }
        body.put("commonPrefixes", common);
        body.put("prefixes", own);
        return body;
    }

    private ResultActions save(String id, Map<String, Object> body) throws Exception {
        return mvc.perform(withCsrf(put("/api/admin/boards/" + id), session).content(JSON.writeValueAsString(body)));
    }

    private String gradeAt(int offset) {
        return jdbc.queryForObject("SELECT grade_key FROM grades ORDER BY level OFFSET ? LIMIT 1", String.class, offset);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 핫딜_게시판을_만들면_19금_끔_쓰기_첫_등급_바로_위_비회원_글쓰기_없음_인기글_가젯_제외() throws Exception {
        String second = gradeAt(1);
        String response = create("DEAL", "LIST", "").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("DEAL"))
                .andExpect(jsonPath("$.data.layout").value("LIST"))
                .andExpect(jsonPath("$.data.writeMin").value(second))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.data.id");
        created.add(id);

        long boardId = Long.parseLong(id);
        Map<String, Object> row = jdbc.queryForMap("SELECT kind, layout, allow_adult, write_min, comment_min, array_to_string(gadget_exclusions, ',') AS gadgets "
                + "FROM boards WHERE id = ?", boardId);
        assertThat(row.get("kind")).isEqualTo("DEAL");
        assertThat(row.get("allow_adult")).isEqualTo(false);
        assertThat(row.get("write_min")).isEqualTo(second).isNotEqualTo(gradeAt(0)).isNotEqualTo("GUEST");
        assertThat(row.get("comment_min")).isNotEqualTo("GUEST");
        assertThat(row.get("gadgets")).isEqualTo("POPULAR");

        String detail = detail(id);
        assertThat((Boolean) JsonPath.read(detail, "$.data.allowAdult")).isFalse();
        assertThat((String) JsonPath.read(detail, "$.data.policy.writeMin")).isEqualTo(second);
        assertThat((List<String>) JsonPath.read(detail, "$.data.gadgetExclusions")).containsExactly("POPULAR");

        // 감사 after 에 종류가 남는다
        String audit = jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE action = 'BOARD_CREATE' AND target_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 1",
                String.class, id);
        assertThat(audit).contains("DEAL");

        // 카탈로그(공개 쪽 유효값)도 핫딜·19금 끔·비회원 글쓰기 없음
        catalog.invalidate();
        var effective = catalog.snapshot().byId().get(boardId);
        assertThat(effective.kind()).isEqualTo("DEAL");
        assertThat(effective.allowAdult()).isFalse();
        assertThat(effective.writeMin()).isEqualTo(second);
    }

    /** 사용 중인 말머리 이름(표시 순서) — 공통이면 {@code common} 이 true 인 것, 아니면 고유 */
    private List<String> activePrefixes(String id, boolean common) {
        return jdbc.queryForList("SELECT name FROM board_prefixes WHERE board_id = ? AND active AND (common_key IS NOT NULL) = ? ORDER BY sort_order",
                String.class, Long.parseLong(id), common);
    }

    @Test
    void 핫딜_게시판은_공통_말머리를_끄고_고유_말머리_로봇_AI_메이킹_기타로_시작한다() throws Exception {
        String id = createDeal("LIST");
        assertThat(activePrefixes(id, true)).isEmpty();
        assertThat(activePrefixes(id, false)).containsExactly("로봇", "AI", "메이킹", "기타");
        // 공통 말머리 줄은 꺼진 채 있다(나중에 켤 수 있게), 고유 말머리는 모두 운영진 전용 아님 · 색 없음
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_prefixes WHERE board_id = ? AND common_key IS NOT NULL AND NOT active", Integer.class,
                Long.parseLong(id))).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_prefixes WHERE board_id = ? AND common_key IS NULL AND (staff_only OR color IS NOT NULL)",
                Integer.class, Long.parseLong(id))).isZero();
        // 상세 응답에도 같은 차례로 보인다
        List<String> shown = JsonPath.read(detail(id), "$.data.prefixes[?(@.active == true)].name");
        assertThat(shown).containsExactly("로봇", "AI", "메이킹", "기타");
    }

    @Test
    void 일반_게시판은_그대로_공통_말머리_넷만_켜고_고유_말머리는_없다() throws Exception {
        String response = create("GENERAL", "LIST", "").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.data.id");
        created.add(id);
        assertThat(activePrefixes(id, true)).containsExactly("질문", "후기", "잡담", "공지");
        assertThat(activePrefixes(id, false)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_prefixes WHERE board_id = ? AND common_key IS NULL", Integer.class, Long.parseLong(id))).isZero();
    }

    @Test
    void 레이아웃은_요청값_그대로() throws Exception {
        String card = createDeal("CARD");
        assertThat((String) JsonPath.read(detail(card), "$.data.layout")).isEqualTo("CARD");
        String gallery = createDeal("GALLERY");
        assertThat((String) JsonPath.read(detail(gallery), "$.data.layout")).isEqualTo("GALLERY");
    }

    @Test
    void 만든_직후_상세를_그대로_저장해도_검사에_걸리지_않는다() throws Exception {
        for (String layout : List.of("LIST", "CARD")) {
            String id = createDeal(layout);
            save(id, saveBodyFrom(detail(id))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.kind").value("DEAL"))
                    .andExpect(jsonPath("$.data.allowAdult").value(false))
                    .andExpect(jsonPath("$.data.policy.writeMin").value(gradeAt(1)));
        }
    }

    @Test
    void 핫딜_게시판에_소모임_분류를_보내면_400_groupCategoryId() throws Exception {
        create("DEAL", "LIST", ",\"groupCategoryId\":\"" + ids.nextId() + "\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.groupCategoryId").exists())
                .andExpect(jsonPath("$.error.fields.kind").doesNotExist());
    }

    @Test
    void 직접홍보는_여전히_만들_수_없다() throws Exception {
        create("PROMO", "LIST", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.kind").exists());
        create("NOPE", "LIST", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.kind").exists());
    }

    @Test
    void 핫딜_게시판에_19금을_켜면_400_allowAdult() throws Exception {
        String id = createDeal("LIST");
        Map<String, Object> body = saveBodyFrom(detail(id));
        body.put("allowAdult", true);
        save(id, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.allowAdult").exists());
        assertThat(jdbc.queryForObject("SELECT allow_adult FROM boards WHERE id = ?", Boolean.class, Long.parseLong(id))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 핫딜_게시판_글쓰기를_첫_등급으로_내리면_400_writeMin() throws Exception {
        String id = createDeal("LIST");
        Map<String, Object> body = saveBodyFrom(detail(id));
        Map<String, Object> policy = new LinkedHashMap<>((Map<String, Object>) body.get("policy"));
        policy.put("writeMin", gradeAt(0));
        body.put("policy", policy);
        save(id, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.['policy.writeMin']").exists());
        assertThat(jdbc.queryForObject("SELECT write_min FROM boards WHERE id = ?", String.class, Long.parseLong(id))).isEqualTo(gradeAt(1));
    }

    @Test
    void 핫딜_게시판에도_매니저를_지정할_수_있다() throws Exception {
        String id = createDeal("LIST");
        long member = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", member);
        jdbc.update("UPDATE profiles SET grade_key = ? WHERE user_id = ?", gradeAt(1), member);
        mvc.perform(withCsrf(post("/api/admin/boards/" + id + "/staff"), session).content("{\"memberId\":\"" + member + "\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].role").value("MANAGER"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_staff WHERE board_id = ? AND user_id = ?", Integer.class, Long.parseLong(id), member)).isEqualTo(1);
        mvc.perform(withCsrf(delete("/api/admin/boards/" + id + "/staff/" + member), session)).andExpect(status().isOk());
    }
}
