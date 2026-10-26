package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetentionClass;
import com.community.permission.entity.Permission;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 품목 API(커머스 1-2 — commerce-stage1-contract 「B」·「1-2 화면 목업」 품목 편집) — 권한(조회 {@code pointshop.read}, 쓰기 {@code pointshop.manage}), 만들기·고치기의
 * 칸 검사({@code 400 VALIDATION fields.*} — 종류·코드 불변, 아바타 만들기 금지·아바타 한 줄은 이름·영문명·가격·순서·사용 여부만), 감사(일반 운영). 실제 필터 체인·권한 인터셉터·PostgreSQL.
 */
class PointShopItemAdminHttpTest extends PointShopAdminHttpTestBase {

    private ResultActions createItem(Cookie session, String body) throws Exception {
        return mvc.perform(withCsrf(post(BASE + "/items"), session).header("Accept-Language", "ko").content(body));
    }

    private ResultActions updateItem(Cookie session, Object id, String body) throws Exception {
        return mvc.perform(withCsrf(put(BASE + "/items/" + id), session).header("Accept-Language", "ko").content(body));
    }

    /** 닉네임 색 품목 본문 — 칸 전부 */
    private static String colorBody(String code, String name, int price, String color) {
        return "{\"code\":\"" + code + "\",\"kind\":\"NICKNAME_COLOR\",\"usageType\":\"PERMANENT\",\"name\":\"" + name + "\",\"nameEn\":null,"
                + "\"description\":\"닉네임을 장미색으로\",\"descriptionEn\":null,\"color\":\"" + color + "\",\"borderStyle\":null,\"avatarId\":null,\"pricePoint\":" + price
                + ",\"durationDays\":null,\"stock\":null,\"saleStartAt\":null,\"saleEndAt\":null,\"active\":true,\"sortOrder\":3}";
    }

    @Test
    void 권한_조회만_있으면_쓰기는_403_권한이_없으면_조회도_403() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINTSHOP_READ));
        Cookie nobody = login(operatorWith(Permission.POINT_READ));
        long item = colorItem(500, null);

        mvc.perform(get(BASE + "/items").cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(BASE + "/items/" + item).cookie(reader)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/avatars/summary").cookie(reader)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/purchases").cookie(reader)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/settings").cookie(reader)).andExpect(status().isOk());

        createItem(reader, colorBody(code("r"), "막힘", 100, "#112233")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        updateItem(reader, item, colorBody("x", "막힘", 100, "#112233")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(put(BASE + "/avatars/price"), reader).content("{\"scope\":\"ALL\",\"price\":1000}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(BASE + "/avatars/sale"), reader).content("{\"scope\":\"ALL\",\"active\":true}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(put(BASE + "/settings"), reader).content("{\"refundDays\":7}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(BASE + "/purchases/1/refund"), reader).content("{\"reason\":\"시험 환불 사유\"}")).andExpect(status().isForbidden());

        for (String path : List.of("/items", "/items/" + item, "/avatars/summary", "/purchases", "/settings")) {
            mvc.perform(get(BASE + path).cookie(nobody)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        }
        // 로그인하지 않으면 401
        mvc.perform(get(BASE + "/items")).andExpect(status().isUnauthorized());
        // 관리 권한만으로는 예외 환불이 안 된다(pointshop.refund 따로)
        Cookie manager = login(operatorWith(Permission.POINTSHOP_MANAGE));
        mvc.perform(withCsrf(post(BASE + "/purchases/1/refund"), manager).content("{\"reason\":\"시험 환불 사유\"}")).andExpect(status().isForbidden());
    }

    @Test
    void 품목을_만들면_201_과_칸_전부_만든_운영자_이름이고_감사_일반_운영이_남는다() throws Exception {
        long operator = operatorWith(Permission.POINTSHOP_MANAGE, Permission.POINTSHOP_READ);
        Cookie session = login(operator);
        String code = code("rose");

        String body = createItem(session, colorBody(code, "장미 닉네임", 1200, "#E11D48")).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.code").value(code)).andExpect(jsonPath("$.data.kind").value("NICKNAME_COLOR"))
                .andExpect(jsonPath("$.data.usageType").value("PERMANENT")).andExpect(jsonPath("$.data.name").value("장미 닉네임"))
                .andExpect(jsonPath("$.data.color").value("#E11D48")).andExpect(jsonPath("$.data.borderStyle").value(nullValue()))
                .andExpect(jsonPath("$.data.avatarId").value(nullValue())).andExpect(jsonPath("$.data.category").value(nullValue()))
                .andExpect(jsonPath("$.data.pricePoint").value(1200)).andExpect(jsonPath("$.data.stock").value(nullValue()))
                .andExpect(jsonPath("$.data.active").value(true)).andExpect(jsonPath("$.data.sortOrder").value(3))
                .andExpect(jsonPath("$.data.soldCount").value(0)).andExpect(jsonPath("$.data.createdBy").value(operatorName(operator)))
                .andExpect(jsonPath("$.data.updatedBy").value(operatorName(operator)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.id");

        Map<String, Object> audit = lastAudit(AuditActions.POINT_SHOP_ITEM_CREATE, id);
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(operator);
        assertThat(audit.get("target_type")).isEqualTo("POINT_SHOP_ITEM");
        assertThat(audit.get("ip")).isNotNull();
        assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.GENERAL.name());
        assertThat(audit.get("detail").toString()).contains("\"code\":\"" + code + "\"", "\"pricePoint\":1200");

        // 조회 — 한 개·목록(excludeKind=PROFILE_AVATAR 면 아바타가 없다)
        mvc.perform(get(BASE + "/items/" + id).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value(code));
        String list = mvc.perform(get(BASE + "/items").param("excludeKind", "PROFILE_AVATAR").cookie(session)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        List<String> kinds = JsonPath.read(list, "$.data.items[*].kind");
        List<String> codes = JsonPath.read(list, "$.data.items[*].code");
        assertThat(kinds).doesNotContain("PROFILE_AVATAR");
        assertThat(codes).contains(code, "nickname-change");
        String colors = mvc.perform(get(BASE + "/items").param("kind", "NICKNAME_COLOR").cookie(session)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(colors, "$.data.items[*].kind")).containsOnly("NICKNAME_COLOR");
    }

    @Test
    void 만들기_칸_검사는_400_fields_로_모아_나가고_아무것도_만들어지지_않는다() throws Exception {
        Cookie session = login(operatorWith(Permission.POINTSHOP_MANAGE));
        int before = count("SELECT count(*) FROM point_shop_items");

        // 칸이 모두 틀림 — 모아서 한 번에
        createItem(session, "{\"code\":\"A!\",\"kind\":\"NICKNAME_COLOR\",\"usageType\":\"CONSUMABLE\",\"name\":\"\",\"description\":\"\",\"color\":\"red\","
                + "\"borderStyle\":\"GOLD\",\"pricePoint\":0,\"durationDays\":5,\"stock\":-1,\"saleStartAt\":\"2026-11-02T00:00:00Z\",\"saleEndAt\":\"2026-11-01T00:00:00Z\","
                + "\"active\":null,\"sortOrder\":-1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.code").value("코드는 영소문자·숫자·하이픈 3~40자로 적어 주세요."))
                .andExpect(jsonPath("$.error.fields.usageType").exists()).andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.description").exists()).andExpect(jsonPath("$.error.fields.color").exists())
                .andExpect(jsonPath("$.error.fields.borderStyle").exists()).andExpect(jsonPath("$.error.fields.pricePoint").value("가격은 1~100,000P로 적어 주세요."))
                .andExpect(jsonPath("$.error.fields.durationDays").exists()).andExpect(jsonPath("$.error.fields.stock").exists())
                .andExpect(jsonPath("$.error.fields.saleEndAt").exists()).andExpect(jsonPath("$.error.fields.active").exists())
                .andExpect(jsonPath("$.error.fields.sortOrder").exists());
        // 아바타는 새로 만들 수 없다
        createItem(session, colorBody(code("av"), "아바타", 100, "#112233").replace("NICKNAME_COLOR", "PROFILE_AVATAR"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.kind").value("아바타 품목은 새로 만들 수 없어요."));
        // 코드 중복 · 아바타 예약 접두어
        createItem(session, colorBody("nickname-change", "중복", 100, "#112233")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.code").value("이미 쓰는 코드예요."));
        createItem(session, colorBody("avatar-x1", "예약", 100, "#112233")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.code").exists());
        // 본문이 없으면 칸이 모두 비어 있는 것과 같다
        mvc.perform(withCsrf(post(BASE + "/items"), session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.code").exists())
                .andExpect(jsonPath("$.error.fields.kind").value("종류를 골라 주세요."));
        // 모르는 종류·사용 방식은 형식 오류(MALFORMED_REQUEST)가 아니라 다른 칸 오류와 함께 fields 로
        createItem(session, colorBody(code("uk"), "모름", 0, "#112233").replace("\"NICKNAME_COLOR\"", "\"HAT\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.kind").value("모르는 종류예요. 목록에서 골라 주세요."))
                .andExpect(jsonPath("$.error.fields.pricePoint").exists());
        createItem(session, colorBody(code("uu"), "모름", 0, "#112233").replace("\"PERMANENT\"", "\"FOREVER\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.usageType").value("모르는 사용 방식이에요. 목록에서 골라 주세요."))
                .andExpect(jsonPath("$.error.fields.pricePoint").exists());
        // 가격 상한 100,000
        createItem(session, colorBody(code("p"), "비쌈", 100_001, "#112233")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.pricePoint").exists());

        assertThat(count("SELECT count(*) FROM point_shop_items")).isEqualTo(before);
    }

    @Test
    void 고치기는_종류_코드를_바꾸면_400_이고_바뀐_칸만_감사에_남고_없는_품목은_404() throws Exception {
        long operator = operatorWith(Permission.POINTSHOP_MANAGE);
        Cookie session = login(operator);
        long item = colorItem(500, null);
        String code = jdbc.queryForObject("SELECT code FROM point_shop_items WHERE id = ?", String.class, item);

        updateItem(session, item, colorBody(code + "x", "바꿈", 500, "#E11D48")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.code").value("코드는 만든 뒤 바꿀 수 없어요."));
        updateItem(session, item, colorBody(code, "바꿈", 500, "#E11D48").replace("NICKNAME_COLOR", "PROFILE_BORDER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.kind").value("종류는 만든 뒤 바꿀 수 없어요."));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = ? AND target_id = ?", AuditActions.POINT_SHOP_ITEM_UPDATE, Long.toString(item))).isZero();

        updateItem(session, item, colorBody(code, "새 이름", 700, "#0EA5E9")).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("새 이름"))
                .andExpect(jsonPath("$.data.pricePoint").value(700)).andExpect(jsonPath("$.data.color").value("#0EA5E9"))
                .andExpect(jsonPath("$.data.updatedBy").value(operatorName(operator))).andExpect(header().string("Cache-Control", "no-store"));
        Map<String, Object> audit = lastAudit(AuditActions.POINT_SHOP_ITEM_UPDATE, Long.toString(item));
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(operator);
        assertThat(audit.get("retention_class")).isEqualTo(AuditRetentionClass.GENERAL.name());
        assertThat(audit.get("detail").toString()).contains("\"before\"", "\"after\"", "\"pricePoint\":700", "\"pricePoint\":500").doesNotContain("\"usageType\"", "\"active\"");

        // 같은 값으로 다시 저장하면 감사가 늘지 않는다
        updateItem(session, item, colorBody(code, "새 이름", 700, "#0EA5E9")).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = ? AND target_id = ?", AuditActions.POINT_SHOP_ITEM_UPDATE, Long.toString(item))).isEqualTo(1);

        updateItem(session, 999_999_999L, colorBody(code, "없음", 700, "#0EA5E9")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
        updateItem(session, "abc", colorBody(code, "없음", 700, "#0EA5E9")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
        mvc.perform(get(BASE + "/items/999999999").cookie(login(operatorWith(Permission.POINTSHOP_READ)))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POINT_SHOP_ITEM_NOT_FOUND"));
    }

    @Test
    void 아바타_한_줄은_이름_영문명_가격_순서_사용_여부만_고치고_나머지_칸을_바꾸면_400() throws Exception {
        Cookie session = login(operatorWith(Permission.POINTSHOP_MANAGE, Permission.POINTSHOP_READ));
        String row = mvc.perform(get(BASE + "/items").param("kind", "PROFILE_AVATAR").cookie(session)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<Object>>read(row, "$.data.items")).hasSize(306);
        String id = JsonPath.read(row, "$.data.items[0].id");
        String avatarId = JsonPath.read(row, "$.data.items[0].avatarId");
        String code = JsonPath.read(row, "$.data.items[0].code");
        String description = JsonPath.read(row, "$.data.items[0].description");
        String descriptionEn = JsonPath.read(row, "$.data.items[0].descriptionEn");
        String category = JsonPath.read(row, "$.data.items[0].category");
        assertThat(code).isEqualTo("avatar-" + avatarId);
        assertThat(category).isNotBlank();

        java.util.function.Function<String, String> body = patch -> ("{\"code\":\"" + code + "\",\"kind\":\"PROFILE_AVATAR\",\"usageType\":\"PERMANENT\",\"name\":\"새 아바타 이름\","
                + "\"nameEn\":\"New\",\"description\":\"" + description + "\",\"descriptionEn\":" + (descriptionEn == null ? "null" : "\"" + descriptionEn + "\"")
                + ",\"color\":null,\"borderStyle\":null,\"avatarId\":\"" + avatarId + "\","
                + "\"pricePoint\":1500,\"durationDays\":null,\"stock\":null,\"saleStartAt\":null,\"saleEndAt\":null,\"active\":false,\"sortOrder\":5" + patch + "}");

        updateItem(session, id, body.apply("").replace("\"avatarId\":\"" + avatarId + "\"", "\"avatarId\":\"other-avatar\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.avatarId").value("아바타는 바꿀 수 없어요."));
        updateItem(session, id, body.apply("").replace("\"stock\":null", "\"stock\":5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.stock").exists());
        updateItem(session, id, body.apply("").replace("\"saleEndAt\":null", "\"saleEndAt\":\"2030-01-01T00:00:00Z\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.saleEndAt").exists());
        updateItem(session, id, body.apply("").replace("\"description\":\"" + description + "\"", "\"description\":\"설명 바꿈\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.description").value("아바타 품목은 이름·영문 이름·가격·순서·사용 여부만 바꿀 수 있어요."));

        updateItem(session, id, body.apply("")).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("새 아바타 이름"))
                .andExpect(jsonPath("$.data.pricePoint").value(1500)).andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.avatarId").value(avatarId)).andExpect(jsonPath("$.data.category").value(category));
        // 이름은 시드 값으로 되돌린다(공용 행 — 가격·판매는 바탕 클래스가 되돌린다)
        jdbc.update("UPDATE point_shop_items SET name = ?, name_en = NULL, sort_order = ? WHERE id = ?", JsonPath.read(row, "$.data.items[0].name"),
                JsonPath.<Integer>read(row, "$.data.items[0].sortOrder"), Long.parseLong(id));
    }

    @Test
    void 목록의_모르는_종류는_400_fields_kind_excludeKind() throws Exception {
        Cookie reader = login(operatorWith(Permission.POINTSHOP_READ));
        mvc.perform(get(BASE + "/items").param("kind", "HAT").param("excludeKind", "CAPE").cookie(reader).header("Accept-Language", "ko"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.kind").value("모르는 품목 종류로 거를 수 없어요.")).andExpect(jsonPath("$.error.fields.excludeKind").exists());
    }
}
