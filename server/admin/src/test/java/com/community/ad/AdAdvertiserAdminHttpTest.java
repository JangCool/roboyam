package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.business.service.BusinessPii;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.menu.dto.AdminMenuNode;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.crypto.DataCipher;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 광고주 관리 API(실제 관리자 앱 문맥 + PostgreSQL, docs/features/ad-advertiser-contract.md) — 개인정보·돈 규칙을 막는다.
 *
 * <ul>
 *   <li>권한 — 조회 {@code ad.read} · 처리 {@code ad.manage} · 평문 {@code ad.contact.reveal}(없으면 403), 모르는 거르기 400 {@code fields.<칸>}, no-store.</li>
 *   <li>연락처 — 목록·상세에는 가린 값만(평문 글자가 응답에 없다), 평문은 reveal 로만 + 열람 기록(사업자 신원 연락처도 신원 키로) + 감사.</li>
 *   <li>사업자 한 줄 합치기(규칙 1) — 신청 건(biz-) + 사업자를 고른 운영자 등록 건 + 붙은 광고주 줄(adv- 별칭)의 건이 한 줄 · 건 수·결제·환불·최근 광고일 합 · 결제 = 신청 건 상세
 *       {@code refundSummary} 와 같은 값 · 광고 담당자를 저장해도 ID 그대로.</li>
 *   <li>보관·삭제(규칙 6) — 삭제는 건 0 일 때만, 보관은 끝나지 않은 건이 없을 때만, 사업자는 둘 다 409 · 보관하면 운영자 등록 찾기에서 빠지고 등록 400.</li>
 *   <li>성과(규칙 11) — {@code biz-} 거르기 = 그 사업자의 모든 건, {@code adv-} 별칭도 같은 결과, {@code topAdvertisers}·건별 줄 광고주 ID = {@code biz-}.</li>
 *   <li>메뉴 — 「광고」 넷째(광고 성과 다음 · 광고 기본 설정 앞).</li>
 * </ul>
 * 시험 줄은 겹치지 않는 새 이름·ID 로 만들고 목록은 찾기({@code q})로 자기 줄만 본다(DB 를 다른 시험과 같이 쓴다).
 */
class AdAdvertiserAdminHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private DataCipher cipher;

    private JsonNode data(ResultActions result) throws Exception {
        return MAPPER.readTree(body(result)).get("data");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String tag() {
        return Long.toString(System.nanoTime(), 36);
    }

    /** 승인 사업자 신원 — 대표자·연락처·이메일은 실제 암호문(용도 business.*) */
    private long businessProfile(String company, String owner, String phone, String email) {
        long profile = ids.nextId();
        // SQL 로 남김: 사업자 신원 줄의 심사 상태·암호문 칸을 심사 기록 없이 바로 세운다 — 팩터리(apply)는 신청(PENDING)만 만든다
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'APPROVED', false, ?, 'x', ?, '4321', ?, ?, ?, '06236', 'x', 'v1', 'v1', now(), now(), now(), now())",
                profile, newMember(), company, String.format("%064x", profile), cipher.encrypt(BusinessPii.OWNER, owner), cipher.encrypt(BusinessPii.PHONE, phone),
                cipher.encrypt(BusinessPii.EMAIL, email));
        return profile;
    }

    /**
     * 신청 건 한 줄 + 상품 줄 + 소재 — 상태에 맞는 결제 칸(온라인 결제 완료 · 수동 입금 확인 {@code biz_deposits})을 채운다. {@code product} null 이면 핫딜 글만(장부 유형 없음).
     */
    private long booking(String status, String method, Instant start, Instant end, Long advertiserId, Long profileId, String product, long operatorId) {
        long id = ids.nextId();
        boolean approved = !status.equals("SUBMITTED");
        boolean confirmed = status.equals("SCHEDULED") || status.equals("TAKEN_DOWN");
        boolean online = "ONLINE".equals(method);
        String kind = product == null ? "HOTDEAL_POST" : product.equals("TEXT_LINE") ? "AD_TEXT_LINE" : "AD_BANNER";
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, business_profile_id, advertiser_id, advertiser_name, company_name, title, "
                        + "start_at, end_at, preferred_method, payment_method, quote_supply, quote_vat, quote_total, approved_supply, approved_vat, approved_total, approved_at, "
                        + "approved_by, payment_deadline_at, deposit_deadline_at, paid_at, payment_order_id, active_contract_count, deposit_confirmed_at, "
                        + "stop_fee_rate, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPERATOR', ?, ?, '광고주', ?, '광고주 시험', ?, ?, ?, ?, 1000, 100, 1100, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, now(), now())",
                id, "AV-" + (id % 1_000_000_000_000L), kind, status, profileId, advertiserId, profileId == null ? null : "합치기상사", Timestamp.from(start),
                Timestamp.from(end), method, approved ? method : null, approved ? 1000 : null, approved ? 100 : null, approved ? 1100 : null,
                approved ? Timestamp.from(start.minus(Duration.ofDays(3))) : null, approved ? operatorId : null,
                approved && online && !confirmed ? Timestamp.from(start.minus(Duration.ofDays(1))) : null,
                approved && !online ? Timestamp.from(start.minus(Duration.ofDays(1))) : null, confirmed && online ? Timestamp.from(start.minus(Duration.ofDays(2))) : null,
                confirmed && online ? ids.nextId() : null, approved && !online ? 1 : 0, confirmed && !online ? Timestamp.from(start.minus(Duration.ofDays(2))) : null,
                operatorId);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO biz_application_items (id, application_id, product, unit_price, units, supply, start_at, end_at, created_at) "
                        + "VALUES (?, ?, ?, 1000, 1, 1000, ?, ?, now())", ids.nextId(), id, product == null ? "HOTDEAL_POST" : product, Timestamp.from(start),
                Timestamp.from(end));
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO biz_creative_versions (id, application_id, version, content, created_by_type, created_by, created_at, approved_at) "
                + "VALUES (?, ?, 1, '{}'::jsonb, 'OPERATOR', ?, now(), ?)", ids.nextId(), id, operatorId, approved ? Timestamp.from(start.minus(Duration.ofDays(3))) : null);
        if (confirmed && !online) {
            jdbc.update("INSERT INTO biz_deposits (application_id, amount, deposited_on, depositor_name, confirmed_by, confirmed_at) VALUES (?, 1100, current_date, '입금자', ?, now())",
                    id, operatorId);
        }
        return id;
    }

    /** 승인된 환불 한 줄 */
    private void refund(long applicationId, long amount, String method) {
        jdbc.update("INSERT INTO biz_refunds (id, application_id, type, status, method, paid_amount, proposed_amount, used_days, total_days, approved_amount, requested_at, "
                + "decided_at) VALUES (?, ?, 'PRORATED', 'APPROVED', ?, 1100, ?, 1, 2, ?, now(), now())", ids.nextId(), applicationId, method, amount, amount);
    }

    private void stat(long id, LocalDate day, long impressions, long clicks) {
        jdbc.update("INSERT INTO ad_daily_stats (application_id, version, day, placement, impressions, clicks) VALUES (?, 1, ?, 'HOME', ?, ?)", id, java.sql.Date.valueOf(day),
                impressions, clicks);
    }

    private JsonNode list(Cookie op, String... params) throws Exception {
        var request = get("/api/admin/ad-advertisers").cookie(op);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return data(mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
    }

    private static List<String> ids(JsonNode page) {
        return page.get("items").valueStream().map(n -> n.get("id").asString()).toList();
    }

    private String lastAuditDetail(String action, String targetId) {
        return auditLogs.where().action(action).targetType("AD_ADVERTISER").target(targetId).latest().orElseThrow().detail();
    }

    @Test
    void 권한_조회_ad_read_처리_ad_manage_평문_ad_contact_reveal_없으면_403_모르는_거르기_400() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.AD_READ));
        Cookie manager = login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE));
        mvc.perform(get("/api/admin/ad-advertisers").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ad-advertisers/adv-1").cookie(none)).andExpect(status().isForbidden());
        list(reader);
        String name = "권한광고주" + tag();
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers"), reader).content("{\"name\":\"" + name + "\"}")).andExpect(status().isForbidden());
        String id = data(mvc.perform(withCsrf(post("/api/admin/ad-advertisers"), manager).content("{\"name\":\"" + name + "\"}")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))).get("id").asString();
        mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + id), reader).content("{\"memo\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + id + "/archive"), reader)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + id + "/unarchive"), reader)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(delete("/api/admin/ad-advertisers/" + id), reader)).andExpect(status().isForbidden());
        // 평문은 ad.contact.reveal 만 — ad.manage 로는 403
        mvc.perform(withCsrf(post("/api/admin/ads/advertisers/" + id + "/reveal"), manager).content("{\"reason\":\"권한 없는 열람\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ad-advertisers").param("kind", "FOO").param("state", "OLD").param("sort", "views").param("dir", "up").param("page", "-1")
                .param("size", "101").cookie(reader)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.kind").exists())
                .andExpect(jsonPath("$.error.fields.state").exists()).andExpect(jsonPath("$.error.fields.sort").exists()).andExpect(jsonPath("$.error.fields.dir").exists())
                .andExpect(jsonPath("$.error.fields.page").exists()).andExpect(jsonPath("$.error.fields.size").exists());
        mvc.perform(get("/api/admin/ad-advertisers/nope-1").cookie(reader)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_NOT_FOUND"));
        mvc.perform(get("/api/admin/ad-advertisers/adv-" + ids.nextId()).cookie(reader)).andExpect(status().isNotFound());
        // 광고주 줄도 신청 건도 AD 서비스 줄도 없는 사업자는 목록에 없고 상세 404
        long bare = businessProfile("빈사업자" + tag(), "최대표", "01011112222", "bare@biz.example");
        mvc.perform(get("/api/admin/ad-advertisers/biz-" + bare).cookie(reader)).andExpect(status().isNotFound());
    }

    @Test
    void 직접_등록_만들기_고치기_평문은_reveal_로만_보관_찾기_제외_등록_400_보관_풀기_삭제() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie op = login(me);
        Cookie revealer = login(operatorWith(Permission.AD_CONTACT_REVEAL));
        String name = "직접광고주" + tag();
        JsonNode created = data(mvc.perform(withCsrf(post("/api/admin/ad-advertisers"), op).content("{\"name\":\"" + name + "\",\"contactName\":\"김담당\","
                + "\"contactPhone\":\"010-2222-3333\",\"email\":\"plain-adv@example.com\",\"memo\":\"전화 계약\"}")).andExpect(status().isOk()));
        String id = created.get("id").asString();
        assertThat(id).startsWith("adv-");
        assertThat(created.get("kind").asString()).isEqualTo("ADVERTISER");
        assertThat(created.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(created.get("contactSource").asString()).isEqualTo("ADVERTISER");
        assertThat(created.get("masked").get("contactPhone").asString()).isEqualTo("010-****-3333");
        assertThat(created.get("masked").get("contactName").asString()).isEqualTo("김*당");
        assertThat(created.get("contactMasked").asString()).isEqualTo("010-****-3333");
        assertThat(created.get("memo").asString()).isEqualTo("전화 계약");
        assertThat(created.get("history").get(0).get("action").asString()).isEqualTo("CREATE");
        assertThat(created.get("deletable").asBoolean()).isTrue();
        assertThat(created.get("archivable").asBoolean()).isTrue();
        assertThat(created.get("business").isNull()).isTrue();
        assertThat(created.toString()).doesNotContain("2222").doesNotContain("plain-adv@").doesNotContain("김담당");
        // 같은 이름 400
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers"), op).content("{\"name\":\" " + name.toLowerCase() + " \"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.name").exists());

        // 목록 — 가린 값만
        String listed = body(mvc.perform(get("/api/admin/ad-advertisers").param("q", name).cookie(op)).andExpect(status().isOk()));
        assertThat(listed).contains(id).contains("010-****-3333").doesNotContain("2222").doesNotContain("plain-adv@").doesNotContain("김담당");
        JsonNode page = list(op, "q", name);
        assertThat(ids(page)).containsExactly(id);
        assertThat(page.get("counts").get("ADVERTISER").asLong()).isEqualTo(1);
        assertThat(page.get("counts").get("ALL").asLong()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("lastAdAt").isNull()).isTrue();
        assertThat(page.get("items").get(0).get("paidTotal").asLong()).isZero();

        // 고치기 — 빠진 칸 그대로 · 빈 글자 지우기 · 같은 값이면 기록 없음 · 칸 이름만 남는다
        JsonNode updated = data(mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + id), op).content("{\"contactPhone\":\"010-9999-8888\",\"memo\":\"\"}"))
                .andExpect(status().isOk()));
        assertThat(updated.get("masked").get("contactPhone").asString()).isEqualTo("010-****-8888");
        assertThat(updated.get("masked").get("email").asString()).isEqualTo("pl***@example.com");
        assertThat(updated.get("memo").isNull()).isTrue();
        assertThat(updated.get("history").size()).isEqualTo(2);
        assertThat(updated.get("history").get(0).get("action").asString()).isEqualTo("UPDATE");
        assertThat(updated.get("history").get(0).get("fields").valueStream().map(JsonNode::asString).toList()).containsExactly("contactPhone", "memo");
        assertThat(updated.toString()).doesNotContain("9999");
        String audit = lastAuditDetail("AD_ADVERTISER_UPDATE", id.substring(4));
        assertThat(audit).contains("contactPhone").doesNotContain("9999").doesNotContain("8888");
        JsonNode same = data(mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + id), op).content("{\"contactPhone\":\"01099998888\",\"memo\":\"  \"}"))
                .andExpect(status().isOk()));
        assertThat(same.get("history").size()).isEqualTo(2);
        mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + id), op).content("{\"name\":\"\",\"email\":\"nope\",\"contactPhone\":\"12\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.name").exists()).andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.contactPhone").exists());

        // 평문 — reveal 로만, 열람 기록 + 감사(개인정보)
        JsonNode plain = data(mvc.perform(withCsrf(post("/api/admin/ads/advertisers/" + id + "/reveal"), revealer).content("{\"reason\":\"계약 확인 전화\"}"))
                .andExpect(status().isOk()));
        assertThat(plain.get("contactPhone").asString()).isEqualTo("010-9999-8888");
        assertThat(plain.get("email").asString()).isEqualTo("plain-adv@example.com");
        assertThat(plain.get("hideAt").asString()).isNotBlank();
        JsonNode afterReveal = data(mvc.perform(get("/api/admin/ad-advertisers/" + id).cookie(op)).andExpect(status().isOk()));
        assertThat(afterReveal.get("views").size()).isEqualTo(1);
        assertThat(afterReveal.get("views").get(0).get("reason").asString()).isEqualTo("계약 확인 전화");
        assertThat(afterReveal.toString()).doesNotContain("9999");
        assertThat(lastAuditDetail("AD_CONTACT_REVEAL", id.substring(4))).contains("계약 확인 전화").doesNotContain("9999");

        // 보관 — 상태·처리 기록, 기본 목록(사용 중)에서 빠지고 보관 거르기에 나온다, 운영자 등록 찾기에서 빠지고 등록하면 400 fields.advertiserId
        JsonNode archived = data(mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + id + "/archive"), op)).andExpect(status().isOk()));
        assertThat(archived.get("state").asString()).isEqualTo("ARCHIVED");
        assertThat(archived.get("history").get(0).get("action").asString()).isEqualTo("ARCHIVE");
        assertThat(ids(list(op, "q", name))).isEmpty();
        assertThat(ids(list(op, "q", name, "state", "ARCHIVED"))).containsExactly(id);
        assertThat(ids(list(op, "q", name, "state", "ALL"))).containsExactly(id);
        JsonNode search = data(mvc.perform(get("/api/admin/ads/advertisers").param("q", name).cookie(op)).andExpect(status().isOk()));
        assertThat(search.valueStream().map(n -> n.get("id").asString()).toList()).doesNotContain(id);
        LocalDate day = LocalDate.of(2049, 3, 1);
        String manual = "{\"advertiserId\":\"" + id + "\",\"type\":\"TEXT_LINE\",\"period\":{\"startAt\":\"" + day.atStartOfDay(KST).toInstant() + "\",\"endAt\":\""
                + day.plusDays(2).atStartOfDay(KST).toInstant() + "\"},\"weight\":5,\"creative\":{\"text\":\"보관 광고주\",\"linkUrl\":\"https://shop.example.com/x\"},"
                + "\"supply\":9000,\"contractFileIds\":[]}";
        mvc.perform(withCsrf(post("/api/admin/ads/manual"), op).content(manual)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.advertiserId").exists());

        // 보관 풀기 — 찾기에 다시 나온다
        JsonNode restored = data(mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + id + "/unarchive"), op)).andExpect(status().isOk()));
        assertThat(restored.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(restored.get("history").get(0).get("action").asString()).isEqualTo("UNARCHIVE");
        JsonNode again = data(mvc.perform(get("/api/admin/ads/advertisers").param("q", name).cookie(op)).andExpect(status().isOk()));
        assertThat(again.valueStream().map(n -> n.get("id").asString()).toList()).contains(id);

        // 삭제 — 건 0 이면 204, 광고주 줄·열람 기록·처리 기록이 없어지고 감사는 남는다
        mvc.perform(withCsrf(delete("/api/admin/ad-advertisers/" + id), op)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/ad-advertisers/" + id).cookie(op)).andExpect(status().isNotFound());
        long raw = Long.parseLong(id.substring(4));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_views WHERE advertiser_id = ?", Integer.class, raw)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_events WHERE advertiser_id = ?", Integer.class, raw)).isZero();
        assertThat(lastAuditDetail("AD_ADVERTISER_DELETE", id.substring(4))).contains(name);
    }

    @Test
    void 건이_있으면_삭제_409_끝나지_않은_건이_있으면_보관_409() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie op = login(me);
        String name = "막힘광고주" + tag();
        String id = data(mvc.perform(withCsrf(post("/api/admin/ad-advertisers"), op).content("{\"name\":\"" + name + "\"}")).andExpect(status().isOk())).get("id").asString();
        long raw = Long.parseLong(id.substring(4));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        long waiting = booking("SUBMITTED", "MANUAL", now.plus(Duration.ofDays(30)), now.plus(Duration.ofDays(32)), raw, null, "TEXT_LINE", me);
        JsonNode detail = data(mvc.perform(get("/api/admin/ad-advertisers/" + id).cookie(op)).andExpect(status().isOk()));
        assertThat(detail.get("deletable").asBoolean()).isFalse();
        assertThat(detail.get("archivable").asBoolean()).isFalse();
        assertThat(detail.get("counts").get("waiting").asInt()).isEqualTo(1);
        mvc.perform(withCsrf(delete("/api/admin/ad-advertisers/" + id), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_HAS_BOOKINGS"));
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + id + "/archive"), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_HAS_OPEN_BOOKINGS"));
        // 진행 중 건도 막는다
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE biz_applications SET status = 'CANCELED', closed_at = now() WHERE id = ?", waiting);
        booking("SCHEDULED", "MANUAL", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)), raw, null, "TEXT_LINE", me);
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + id + "/archive"), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_HAS_OPEN_BOOKINGS"));
        // 끝난 건만 남으면 보관된다 — 삭제는 여전히 409
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE biz_applications SET start_at = ?, end_at = ? WHERE advertiser_id = ? AND status = 'SCHEDULED'", Timestamp.from(now.minus(Duration.ofDays(5))),
                Timestamp.from(now.minus(Duration.ofDays(2))), raw);
        JsonNode archived = data(mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + id + "/archive"), op)).andExpect(status().isOk()));
        assertThat(archived.get("state").asString()).isEqualTo("ARCHIVED");
        assertThat(archived.get("counts").get("total").asInt()).isEqualTo(2);
        assertThat(archived.get("lastAdAt").asString()).isNotBlank();
        mvc.perform(withCsrf(delete("/api/admin/ad-advertisers/" + id), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_HAS_BOOKINGS"));
    }

    @Test
    void 사업자_한_줄_합치기_건_수_돈_최근_광고일_신원_연락처_열람_담당자_저장_뒤_ID_유지_보관_삭제_409_성과_거르기() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie op = login(me);
        Cookie revealer = login(operatorWith(Permission.AD_CONTACT_REVEAL));
        String company = "합치기상사" + tag();
        long profile = businessProfile(company, "박대표", "01077778888", "boss@biz.example");
        String biz = "biz-" + profile;
        // 사업자에 붙은 광고주 줄(광고 담당자 칸 없음) — 옛 ID adv-L 은 별칭
        String alias = data(mvc.perform(withCsrf(post("/api/admin/ads/advertisers"), op).content("{\"name\":\"연결" + tag() + "\",\"businessId\":\"" + profile + "\"}"))
                .andExpect(status().isOk())).get("id").asString();
        long linked = Long.parseLong(alias.substring(4));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        // 사업자 신원 건(진행 중 · 수동 1100 · 환불 300) · 붙은 광고주 줄만 가리키는 건(끝남 · 온라인 1100) · 핫딜 글만(대기 — 장부 유형 없음) · 예약(시작 전 · 수동 1100)
        long running = booking("SCHEDULED", "MANUAL", now.minus(Duration.ofDays(3)), now.plus(Duration.ofDays(3)), null, profile, "TEXT_LINE", me);
        refund(running, 300, "MANUAL");
        long ended = booking("SCHEDULED", "ONLINE", now.minus(Duration.ofDays(20)), now.minus(Duration.ofDays(10)), linked, null, "WIDE_BANNER", me);
        long dealOnly = booking("SUBMITTED", "MANUAL", now.plus(Duration.ofDays(5)), now.plus(Duration.ofDays(7)), null, profile, null, me);
        long future = booking("SCHEDULED", "MANUAL", now.plus(Duration.ofDays(10)), now.plus(Duration.ofDays(12)), null, profile, "TEXT_LINE", me);

        // 목록 — 사업자 하나 = 한 줄(biz-), 붙은 광고주 줄(adv-)은 따로 나오지 않는다
        JsonNode page = list(op, "q", company);
        assertThat(ids(page)).containsExactly(biz);
        assertThat(page.get("counts").get("BUSINESS").asLong()).isEqualTo(1);
        assertThat(page.get("counts").get("ADVERTISER").asLong()).isZero();
        JsonNode row = page.get("items").get(0);
        assertThat(row.get("kind").asString()).isEqualTo("BUSINESS");
        assertThat(row.get("name").asString()).isEqualTo(company);
        assertThat(row.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(row.get("business").get("id").asString()).isEqualTo(Long.toString(profile));
        assertThat(row.get("business").get("maskedRegNo").asString()).isEqualTo("***-**-*4321");
        assertThat(row.get("business").get("status").asString()).isEqualTo("APPROVED");
        assertThat(row.get("business").get("adService").asString()).isEqualTo("OFF");
        assertThat(row.get("counts").get("running").asInt()).isEqualTo(1);
        assertThat(row.get("counts").get("scheduled").asInt()).isEqualTo(1);
        assertThat(row.get("counts").get("waiting").asInt()).isEqualTo(1);
        assertThat(row.get("counts").get("total").asInt()).isEqualTo(4);
        assertThat(row.get("paidTotal").asLong()).isEqualTo(3300); // 환불을 빼지 않는다
        assertThat(Instant.parse(row.get("lastAdAt").asString())).isEqualTo(now.minus(Duration.ofDays(3))); // 시작 전 예약은 빼고 이미 시작한 건 중 가장 늦은 시작
        assertThat(row.get("contactMasked").asString()).isEqualTo("010-****-8888"); // 광고 담당자 칸이 없으면 신원 연락처의 가린 값
        assertThat(ids(list(op, "q", "4321", "kind", "BUSINESS", "size", "100"))).contains(biz); // 등록번호 끝자리 찾기
        assertThat(ids(list(op, "q", company, "state", "ARCHIVED"))).isEmpty(); // 사업자는 늘 사용 중 쪽

        // 상세 — 별칭 adv- 로 열어도 biz- · 돈 · 광고 건(장부 유형 있는 건만, 광고주 ID biz-) · 신원 연락처 가린 값(평문 없음)
        JsonNode detail = data(mvc.perform(get("/api/admin/ad-advertisers/" + alias).cookie(op)).andExpect(status().isOk()));
        assertThat(detail.get("id").asString()).isEqualTo(biz);
        assertThat(detail.get("money").get("paid").asLong()).isEqualTo(3300);
        assertThat(detail.get("money").get("online").asLong()).isEqualTo(1100);
        assertThat(detail.get("money").get("manual").asLong()).isEqualTo(2200);
        assertThat(detail.get("money").get("refunded").asLong()).isEqualTo(300);
        assertThat(detail.get("contactSource").asString()).isEqualTo("BUSINESS");
        assertThat(detail.get("masked").get("contactName").asString()).isEqualTo("박*표");
        assertThat(detail.get("masked").get("contactPhone").asString()).isEqualTo("010-****-8888");
        assertThat(detail.get("masked").get("email").asString()).isEqualTo("bo***@biz.example");
        assertThat(detail.toString()).doesNotContain("7777").doesNotContain("박대표").doesNotContain("boss@");
        assertThat(detail.get("deletable").asBoolean()).isFalse();
        assertThat(detail.get("archivable").asBoolean()).isFalse();
        List<String> bookingIds = detail.get("bookings").valueStream().map(n -> n.get("id").asString()).toList();
        assertThat(bookingIds).containsExactly(Long.toString(future), Long.toString(running), Long.toString(ended)).doesNotContain(Long.toString(dealOnly));
        assertThat(detail.get("bookings").valueStream().map(n -> n.get("advertiser").get("id").asString()).distinct().toList()).containsExactly(biz);
        JsonNode runningRow = detail.get("bookings").get(1);
        assertThat(runningRow.get("paid").asLong()).isEqualTo(1100);
        assertThat(runningRow.get("refunded").asLong()).isEqualTo(300);
        assertThat(runningRow.get("status").asString()).isEqualTo("RUNNING");
        // 결제·환불 = 신청 건 상세 refundSummary 와 같은 원천
        for (long app : List.of(running, ended)) {
            JsonNode ad = data(mvc.perform(get("/api/admin/ads/" + app).cookie(op)).andExpect(status().isOk()));
            JsonNode b = detail.get("bookings").valueStream().filter(n -> n.get("id").asString().equals(Long.toString(app))).findFirst().orElseThrow();
            assertThat(b.get("paid").asLong()).as("paid " + app).isEqualTo(ad.get("refundSummary").get("paid").asLong());
            assertThat(b.get("refunded").asLong()).as("refunded " + app).isEqualTo(ad.get("refundSummary").get("refunded").asLong());
            assertThat(ad.get("advertiser").get("id").asString()).isEqualTo(biz); // 일정표 줄의 광고주 ID 도 같은 키
        }

        // 평문 — 광고 담당자 칸이 없으면 신원 대표자·연락처·이메일, 열람 기록은 신원 키로
        JsonNode plain = data(mvc.perform(withCsrf(post("/api/admin/ads/advertisers/" + biz + "/reveal"), revealer).content("{\"reason\":\"세금계산서 확인\"}"))
                .andExpect(status().isOk()));
        assertThat(plain.get("contactName").asString()).isEqualTo("박대표");
        assertThat(plain.get("contactPhone").asString()).isEqualTo("010-7777-8888");
        assertThat(plain.get("email").asString()).isEqualTo("boss@biz.example");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_views WHERE business_profile_id = ? AND source = 'BUSINESS'", Integer.class, profile)).isOne();
        assertThat(lastAuditDetail("AD_CONTACT_REVEAL", biz)).contains("세금계산서 확인").contains("BUSINESS").doesNotContain("7777");

        // 고치기 — 이름은 400(상호), 광고 담당자 저장 뒤에도 ID 그대로 · 원천은 광고 담당자 칸
        mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + biz), op).content("{\"name\":\"새이름\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.name").exists());
        JsonNode edited = data(mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + biz), op).content("{\"contactName\":\"이담당\",\"contactPhone\":\"010-1111-2222\"}"))
                .andExpect(status().isOk()));
        assertThat(edited.get("id").asString()).isEqualTo(biz);
        assertThat(edited.get("contactSource").asString()).isEqualTo("ADVERTISER");
        assertThat(edited.get("masked").get("contactPhone").asString()).isEqualTo("010-****-2222");
        assertThat(edited.get("masked").get("email").isNull()).isTrue();
        assertThat(edited.get("history").get(0).get("fields").valueStream().map(JsonNode::asString).toList()).containsExactly("contactName", "contactPhone");
        assertThat(edited.get("views").size()).isEqualTo(1);
        JsonNode revealed2 = data(mvc.perform(withCsrf(post("/api/admin/ads/advertisers/" + alias + "/reveal"), revealer).content("{\"reason\":\"담당자 연락\"}"))
                .andExpect(status().isOk()));
        assertThat(revealed2.get("contactName").asString()).isEqualTo("이담당");
        assertThat(revealed2.get("contactPhone").asString()).isEqualTo("010-1111-2222");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_views WHERE business_profile_id = ? AND source = 'ADVERTISER'", Integer.class, profile)).isOne();
        assertThat(ids(list(op, "q", company))).containsExactly(biz);

        // 사업자는 보관·삭제 없음
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + biz + "/archive"), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_BUSINESS"));
        mvc.perform(withCsrf(post("/api/admin/ad-advertisers/" + alias + "/archive"), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_BUSINESS"));
        mvc.perform(withCsrf(delete("/api/admin/ad-advertisers/" + alias), op)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AD_ADVERTISER_BUSINESS"));

        // 성과 — biz- 거르기 = 그 사업자의 모든 건(붙은 광고주 줄의 건 포함), adv- 별칭도 같은 결과, 광고주 순위·건별 줄도 biz-
        LocalDate today = LocalDate.ofInstant(now, KST);
        stat(running, today.minusDays(1), 100, 4);
        stat(ended, LocalDate.ofInstant(now.minus(Duration.ofDays(15)), KST), 50, 1);
        String from = today.minusDays(25).toString();
        String to = today.toString();
        for (String key : List.of(biz, alias)) {
            JsonNode stats = data(mvc.perform(get("/api/admin/ads/stats").param("from", from).param("to", to).param("advertiserId", key).cookie(op))
                    .andExpect(status().isOk()));
            assertThat(stats.get("totals").get("impressions").asLong()).as(key).isEqualTo(150);
            assertThat(stats.get("totals").get("bookings").asLong()).as(key).isEqualTo(2);
            assertThat(stats.get("advertiser").get("id").asString()).as(key).isEqualTo(biz);
            assertThat(stats.get("advertiser").get("name").asString()).as(key).isEqualTo(company);
            assertThat(stats.get("topAdvertisers").size()).as(key).isEqualTo(1);
            assertThat(stats.get("topAdvertisers").get(0).get("advertiser").get("id").asString()).as(key).isEqualTo(biz);
            assertThat(stats.get("topAdvertisers").get(0).get("impressions").asLong()).as(key).isEqualTo(150);
            assertThat(stats.get("topAdvertisers").get(0).get("bookings").asLong()).as(key).isEqualTo(2);
            JsonNode bookings = data(mvc.perform(get("/api/admin/ads/stats/bookings").param("from", from).param("to", to).param("advertiserId", key).cookie(op))
                    .andExpect(status().isOk()));
            assertThat(ids(bookings)).as(key).containsExactly(Long.toString(running), Long.toString(ended));
            assertThat(bookings.get("items").valueStream().map(n -> n.get("advertiser").get("id").asString()).distinct().toList()).as(key).containsExactly(biz);
        }
    }

    @Test
    void AD_서비스_줄만_있는_사업자도_한_줄이고_광고_담당자를_처음_저장하면_붙은_광고주_줄이_생겨도_ID_그대로() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie op = login(me);
        String company = "서비스상사" + tag();
        long profile = businessProfile(company, "정대표", "0212345678", "svc@biz.example");
        businessFixture.service(ids.nextId(), profile, "AD", "APPROVED");
        String biz = "biz-" + profile;
        JsonNode page = list(op, "q", company, "kind", "BUSINESS");
        assertThat(ids(page)).containsExactly(biz);
        assertThat(page.get("items").get(0).get("business").get("adService").asString()).isEqualTo("APPROVED");
        assertThat(page.get("items").get(0).get("counts").get("total").asInt()).isZero();
        JsonNode before = data(mvc.perform(get("/api/admin/ad-advertisers/" + biz).cookie(op)).andExpect(status().isOk()));
        assertThat(before.get("history").size()).isZero();
        assertThat(before.get("contactSource").asString()).isEqualTo("BUSINESS");
        assertThat(before.get("masked").get("contactPhone").asString()).isEqualTo("021-****-5678");
        // 빈 글자만 보내면 바뀐 것이 없다 — 광고주 줄을 만들지 않는다
        data(mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + biz), op).content("{\"contactName\":\"\",\"memo\":\"\"}")).andExpect(status().isOk()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertisers WHERE business_profile_id = ?", Integer.class, profile)).isZero();
        JsonNode after = data(mvc.perform(withCsrf(patch("/api/admin/ad-advertisers/" + biz), op).content("{\"contactName\":\"이담당\",\"memo\":\"광고 담당 따로\"}"))
                .andExpect(status().isOk()));
        assertThat(after.get("id").asString()).isEqualTo(biz);
        assertThat(after.get("memo").asString()).isEqualTo("광고 담당 따로");
        assertThat(after.get("contactSource").asString()).isEqualTo("ADVERTISER");
        assertThat(after.get("masked").get("contactName").asString()).isEqualTo("이*당");
        assertThat(after.get("history").get(0).get("action").asString()).isEqualTo("UPDATE");
        assertThat(after.get("history").get(0).get("fields").valueStream().map(JsonNode::asString).toList()).containsExactly("contactName", "memo");
        Long created = jdbc.queryForObject("SELECT id FROM ad_advertisers WHERE business_profile_id = ?", Long.class, profile);
        assertThat(jdbc.queryForObject("SELECT name FROM ad_advertisers WHERE id = ?", String.class, created)).isEqualTo(company);
        // 새 광고주 줄의 adv- 도 같은 사업자로 — 목록은 여전히 한 줄
        assertThat(data(mvc.perform(get("/api/admin/ad-advertisers/adv-" + created).cookie(op)).andExpect(status().isOk())).get("id").asString()).isEqualTo(biz);
        assertThat(ids(list(op, "q", company))).containsExactly(biz);
        assertThat(lastAuditDetail("AD_ADVERTISER_UPDATE", biz)).contains("contactName").doesNotContain("이담당");
    }

    @Test
    void 메뉴_광고_묶음_넷째_광고_성과_다음_광고_기본_설정_앞_ad_read() {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("adAdvertisers").orElseThrow();
        assertThat(screen.path()).isEqualTo("/ad-advertisers");
        assertThat(screen.labelKey()).isEqualTo("menu.adAdvertisers");
        assertThat(screen.icon()).isEqualTo("pi pi-id-card");
        assertThat(screen.permission()).isEqualTo(Permission.AD_READ);
        AdminMenuNode ad = AdminScreenRegistry.defaults().get(AdminScreenRegistry.NORMAL).stream().filter(n -> "menu.ad".equals(n.labelKey())).findFirst().orElseThrow();
        assertThat(ad.children().stream().map(AdminMenuNode::targetKey).toList()).containsExactly("adReviews", "ads", "adPerformance", "adAdvertisers", "adPlacements");
    }
}
