package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 광고 일정표 API(실제 관리자 앱 문맥 + PostgreSQL, ad-contract) — 권한(조회 ad.read · 등록·광고주 ad.manage · 긴급 내림 ad.takedown · 되돌리기 ad.restore · 연락처 평문
 * ad.contact.reveal), 등록 → 광고 심사 큐(「운영자 등록」 source)·상세의 등록자·자기 승인 설정, 계약서 미리 올리기 multipart, 달력(날짜별 남은 칸 + 그 달 장부 건)·목록·상세 모양.
 */
class AdAdminHttpTest extends Commerce04AdminTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    /** 다른 시험과 겹치지 않는 먼 미래(한도 판정이 기간을 공유한다) */
    private static final AtomicInteger SLOT = new AtomicInteger();

    private JsonNode data(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return MAPPER.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    /** 한국 0시 시작 {@code days} 일 — 2045년 이후 40일씩 */
    private static Instant[] period(int days) {
        LocalDate start = LocalDate.of(2045, 1, 5).plusDays(40L * SLOT.incrementAndGet());
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return new Instant[] {start.atStartOfDay(kst).toInstant(), start.plusDays(days).atStartOfDay(kst).toInstant()};
    }

    private String createAdvertiser(Cookie op) throws Exception {
        JsonNode adv = data(mvc.perform(withCsrf(post("/api/admin/ads/advertisers"), op).content("{\"name\":\"광고주" + ids.nextId()
                + "\",\"contactName\":\"김담당\",\"contactPhone\":\"010-2222-3333\",\"email\":\"adv@example.com\"}")).andExpect(status().isOk()));
        assertThat(adv.get("contactMasked").asString()).isEqualTo("010-****-3333");
        assertThat(adv.toString()).doesNotContain("2222").doesNotContain("adv@example.com");
        return adv.get("id").asString();
    }

    private String manualBody(String advertiserId, Instant[] p, String contractId) {
        return "{\"advertiserId\":\"" + advertiserId + "\",\"type\":\"TEXT_LINE\",\"period\":{\"startAt\":\"" + p[0] + "\",\"endAt\":\"" + p[1] + "\"},\"weight\":7,"
                + "\"creative\":{\"text\":\"운영자 한 줄\",\"linkUrl\":\"https://shop.example.com/x\"},\"supply\":9000,\"contractFileIds\":["
                + (contractId == null ? "" : "\"" + contractId + "\"") + "],\"memo\":\"전화 계약\"}";
    }

    @Test
    void 권한_조회_ad_read_등록_ad_manage_내림_ad_takedown_되돌리기_ad_restore_평문_ad_contact_reveal() throws Exception {
        Cookie none = login(operatorWith());
        Cookie reader = login(operatorWith(Permission.AD_READ));
        Cookie manager = login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE));
        mvc.perform(get("/api/admin/ads/calendar").param("type", "WIDE_BANNER").param("month", "2045-02").cookie(none)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ads/calendar").param("type", "WIDE_BANNER").param("month", "2045-02").cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/ads").param("type", "HOTDEAL_TOP").cookie(reader)).andExpect(status().isOk()); // 핫딜 노출 자리 탭도 ad.read
        mvc.perform(get("/api/admin/ads/advertisers").cookie(reader)).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/ads/manual"), reader).content("{}")).andExpect(status().isForbidden());
        // 등록(관리자) → 장부 건
        String adv = createAdvertiser(manager);
        Instant[] p = period(2);
        JsonNode created = data(mvc.perform(withCsrf(post("/api/admin/ads/manual"), manager).content(manualBody(adv, p, null))).andExpect(status().isOk()));
        String id = created.get("id").asString();
        // 처리 권한 셋은 각자 — ad.manage 만으로는 못 한다
        mvc.perform(withCsrf(post("/api/admin/ads/" + id + "/takedown"), manager).content("{\"reason\":\"권한 없음\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/ads/" + id + "/restore"), manager).content("{\"compensation\":\"EXTEND\",\"reason\":\"권한 없음\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/ads/advertisers/" + adv + "/reveal"), manager).content("{\"reason\":\"권한 없는 열람\"}")).andExpect(status().isForbidden());
        // 권한이 있으면 규칙까지 간다 — 심사 대기는 내릴 수 없다(409), 내림이 아니면 되돌릴 수 없다(409)
        Cookie takedown = login(operatorWith(Permission.AD_TAKEDOWN));
        Cookie restore = login(operatorWith(Permission.AD_RESTORE));
        mvc.perform(withCsrf(post("/api/admin/ads/" + id + "/takedown"), takedown).content("{\"reason\":\"심사 대기\",\"notifyMail\":false}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_STATE_CHANGED"));
        mvc.perform(withCsrf(post("/api/admin/ads/" + id + "/restore"), restore).content("{\"compensation\":\"EXTEND\",\"reason\":\"내림 아님\"}"))
                .andExpect(status().isConflict());
        Cookie revealer = login(operatorWith(Permission.AD_CONTACT_REVEAL));
        JsonNode revealed = data(mvc.perform(withCsrf(post("/api/admin/ads/advertisers/" + adv + "/reveal"), revealer).content("{\"reason\":\"계약 확인 전화\"}"))
                .andExpect(status().isOk()));
        assertThat(revealed.get("contactPhone").asString()).isEqualTo("010-2222-3333");
        assertThat(revealed.get("email").asString()).isEqualTo("adv@example.com");
        mvc.perform(withCsrf(post("/api/admin/ads/advertisers/" + adv + "/reveal"), revealer).content("{\"reason\":\"짧음\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
    }

    @Test
    void 등록하면_광고_심사_큐에_운영자_등록으로_들어가고_상세에_등록자와_자기_승인_설정() throws Exception {
        long me = operatorWith(Permission.AD_READ, Permission.AD_MANAGE);
        Cookie op = login(me);
        String adv = createAdvertiser(op);
        MockMultipartFile pdf = new MockMultipartFile("file", "계약서.pdf", "application/pdf", "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII));
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload = multipart("/api/admin/ads/contract-files").file(pdf);
        upload.cookie(new Cookie("XSRF-TOKEN", XSRF), op).header("X-XSRF-TOKEN", XSRF);
        JsonNode contract = data(mvc.perform(upload).andExpect(status().isOk()));
        assertThat(contract.get("fileName").asString()).isEqualTo("계약서.pdf");
        Instant[] p = period(3);
        JsonNode created = data(mvc.perform(withCsrf(post("/api/admin/ads/manual"), op).content(manualBody(adv, p, contract.get("id").asString())))
                .andExpect(status().isOk()));
        String id = created.get("id").asString();
        assertThat(created.get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(created.get("source").asString()).isEqualTo("OPERATOR");
        assertThat(created.get("type").asString()).isEqualTo("TEXT_LINE");
        assertThat(created.get("domain").asString()).isEqualTo("AD");
        assertThat(created.get("weight").asInt()).isEqualTo(7);
        assertThat(created.get("proposedSupply").asLong()).isEqualTo(9000);
        assertThat(created.get("amount").get("supply").asLong()).isEqualTo(9000); // 승인 전 금액 = 제안
        assertThat(created.get("contracts").size()).isEqualTo(1);
        assertThat(created.get("registrant").get("me").asBoolean()).isTrue();
        assertThat(created.get("history").get(0).get("action").asString()).isEqualTo("SUBMITTED");
        assertThat(created.get("advertiser").get("id").asString()).isEqualTo(adv);
        // 광고 심사 큐 — 줄에 source, 상세에 등록자(나)·자기 승인 설정·제안 금액·비중·메모
        JsonNode queue = data(mvc.perform(get("/api/admin/biz-applications").param("domain", "AD").param("queue", "REVIEW").cookie(op)).andExpect(status().isOk()));
        JsonNode row = queue.get("items").valueStream().filter(n -> n.get("id").asString().equals(id)).findFirst().orElseThrow();
        assertThat(row.get("source").asString()).isEqualTo("OPERATOR");
        JsonNode review = data(mvc.perform(get("/api/admin/biz-applications/" + id).cookie(op)).andExpect(status().isOk()));
        assertThat(review.get("registrant").get("id").asString()).isEqualTo(Long.toString(me));
        assertThat(review.get("registrant").get("me").asBoolean()).isTrue();
        assertThat(review.get("selfApproval").asString()).isEqualTo("ALLOW");
        assertThat(review.get("proposedSupply").asLong()).isEqualTo(9000);
        assertThat(review.get("weight").asInt()).isEqualTo(7);
        assertThat(review.get("memo").asString()).isEqualTo("전화 계약");
        // 다른 운영자가 보면 me = false
        JsonNode otherView = data(mvc.perform(get("/api/admin/biz-applications/" + id).cookie(login(operatorWith(Permission.AD_READ)))).andExpect(status().isOk()));
        assertThat(otherView.get("registrant").get("me").asBoolean()).isFalse();
        // 자기 승인 — 메모 없으면 400 fields.memo, 온라인은 400 fields.method, 메모 있으면 승인
        long supply = review.get("quote").get("supply").asLong();
        String deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        String approve = "{\"method\":\"%s\",\"supply\":" + supply + ",\"deadlineAt\":\"" + deadline + "\",\"period\":{\"startAt\":\"" + p[0] + "\",\"endAt\":\"" + p[1]
                + "\"}%s}";
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/approve"), op).content(String.format(approve, "MANUAL", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.memo").exists());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/approve"), op).content(String.format(approve, "ONLINE", ",\"memo\":\"자기 승인\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.method").exists());
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/approve"), op).content(String.format(approve, "MANUAL", ",\"memo\":\"1인 운영 자기 승인\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"));
        // 일정표 장부 — 목록·달력(그 달에 걸친 건)·상세
        JsonNode list = data(mvc.perform(get("/api/admin/ads").param("type", "TEXT_LINE").param("status", "AWAITING_DEPOSIT").param("size", "100").cookie(op))
                .andExpect(status().isOk()));
        assertThat(list.get("items").valueStream().map(n -> n.get("id").asString())).contains(id);
        String month = LocalDate.ofInstant(p[0], ZoneId.of("Asia/Seoul")).toString().substring(0, 7);
        JsonNode calendar = data(mvc.perform(get("/api/admin/ads/calendar").param("type", "TEXT_LINE").param("month", month).cookie(op)).andExpect(status().isOk()));
        assertThat(calendar.get("summary").get("limit").asInt()).isEqualTo(5);
        assertThat(calendar.get("days").size()).isBetween(28, 31);
        assertThat(calendar.get("bookings").valueStream().map(n -> n.get("id").asString())).contains(id);
        String firstDay = LocalDate.ofInstant(p[0], ZoneId.of("Asia/Seoul")).toString();
        JsonNode day = calendar.get("days").valueStream().filter(d -> d.get("date").asString().equals(firstDay)).findFirst().orElseThrow();
        assertThat(day.get("remaining").asInt()).isLessThan(5); // 승인된 건이 자리를 잡았다
        mvc.perform(get("/api/admin/ads/" + id).cookie(op)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.data.creatives[0].version").value(1));
        JsonNode avail = data(mvc.perform(get("/api/admin/ads/availability").param("type", "TEXT_LINE").param("startAt", p[0].toString())
                .param("endAt", p[1].toString()).cookie(op)).andExpect(status().isOk()));
        assertThat(avail.get("limit").asInt()).isEqualTo(5);
        assertThat(avail.get("quote").get("units").asInt()).isEqualTo(3);
        // 잘못된 유형·달 400
        mvc.perform(get("/api/admin/ads/calendar").param("type", "HOTDEAL_POST").param("month", "2045-13").cookie(op)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.type").exists()).andExpect(jsonPath("$.error.fields.month").exists());
        // 운영자 등록 건의 심사 대기 취소는 신청 건 API — 이미 승인했으니 여기서는 입금 대기 취소
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + id + "/cancel"), op).content("{\"reason\":\"광고주 철회\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }
}
