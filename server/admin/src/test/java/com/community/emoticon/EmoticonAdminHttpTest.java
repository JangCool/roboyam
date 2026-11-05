package com.community.emoticon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.dto.request.EmoticonPayoutAccountRequest;
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.emoticon.service.EmoticonStudioService;
import com.community.identity.service.IdentityVerificationService;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 로보티콘 API(emoticon-contract S1) — 실제 필터 체인·세션·권한 인터셉터·PostgreSQL. 관리자 앱 문맥에 로보티콘 서비스가 붙고(shared), 화면이 부르는 주소·권한·응답 모양이 맞다.
 * 심사 목록 → 상세 → 승인(204) → 팩 상세 판매 중 · 권한이 없으면 403 · 설정 조회는 조회 권한, 저장은 설정 권한.
 */
class EmoticonAdminHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private EmoticonStudioService studio;
    @Autowired
    private EmoticonPayoutService payouts;
    @Autowired
    private DataCipher cipher;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private com.community.emoticon.service.EmoticonCreatorWithdrawal creatorWithdrawal;

    /** 작가가 낸 처음 신청(그림은 정적 경로 줄을 장부에 바로 넣는다 — 파일을 쓰지 않는다) — 팩 ID */
    private long submitted(long creator) {
        List<EmoticonStudioSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(new EmoticonStudioSaveRequest.Item(Long.toString(image(creator, "EMOTICON", "GIF", 2)), "표" + i));
        }
        long cover = image(creator, "COVER", "PNG", 1);
        EmoticonStudioSaveRequest body = new EmoticonStudioSaveRequest("관리자 시험", "설명", List.of("시험"), Long.toString(cover), items, "FREE", null, true, true, true, null);
        return Long.parseLong(new TransactionTemplate(transactions).execute(s -> studio.submit(creator, null, body)).id());
    }

    private long image(long creator, String kind, String format, int frames) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO emoticon_images (id, kind, format, width, height, bytes, frames, loop_forever, content_hash, static_path, uploaded_by_user, created_at) "
                + "VALUES (?, ?, ?, 200, 200, 1000, ?, true, lpad(to_hex(?::bigint), 64, '0'), '/emoticons/test.gif', ?, now())", id, kind, format, frames, id, creator);
        return id;
    }

    private long creator() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        return id;
    }

    @Test
    void 심사_목록_상세_승인_팩_상세() throws Exception {
        long operator = operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW);
        Cookie session = login(operator);
        long packId = submitted(creator());
        String reviewId = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        mvc.perform(get("/api/admin/emoticons/reviews").param("status", "PENDING").cookie(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.counts.PENDING").isNumber())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + reviewId + "')].kind").value("FIRST"));
        mvc.perform(get("/api/admin/emoticons/reviews/" + reviewId).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.next.version").value(1))
                .andExpect(jsonPath("$.data.next.items.length()").value(8)).andExpect(jsonPath("$.data.current").doesNotExist())
                .andExpect(jsonPath("$.data.consents[0].kind").value("ORIGINAL")).andExpect(jsonPath("$.data.rules.paidSalesEnabled").value(false));
        mvc.perform(withCsrf(post("/api/admin/emoticons/reviews/" + reviewId + "/approve"), session).content("{\"price\":0}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/emoticons/packs/" + packId).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.items.length()").value(8)).andExpect(jsonPath("$.data.paidReady.payoutRegistered").value(false));
        mvc.perform(withCsrf(post("/api/admin/emoticons/reviews/" + reviewId + "/approve"), session).content("{\"price\":0}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMOTICON_REVIEW_NOT_PENDING"));
        mvc.perform(get("/api/admin/emoticons/packs").param("source", "OFFICIAL").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].source").value("OFFICIAL"));
    }

    @Test
    void 권한이_없으면_403_설정_저장은_설정_권한() throws Exception {
        long reader = operatorWith(Permission.EMOTICON_READ);
        Cookie session = login(reader);
        long packId = submitted(creator());
        String reviewId = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        mvc.perform(withCsrf(post("/api/admin/emoticons/reviews/" + reviewId + "/reject"), session).content("{\"reason\":\"반려해 봅니다\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/emoticons/settings").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.rewardPerAcquire").isNumber())
                .andExpect(jsonPath("$.data.defaults.pendingLimit").value(3));
        String body = "{\"rewardPerAcquire\":10,\"rewardDailyCap\":500,\"itemMin\":8,\"itemMax\":40,\"pendingLimit\":3,\"feeRatePercent\":30,\"settlementDay\":10,"
                + "\"payoutMinimum\":10000,\"refundDays\":7,\"paidSalesEnabled\":false,\"priceTiers\":[1500,2500,3500]}";
        mvc.perform(withCsrf(put("/api/admin/emoticons/settings"), session).content(body)).andExpect(status().isForbidden());
        Cookie settings = login(operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_SETTINGS));
        mvc.perform(withCsrf(put("/api/admin/emoticons/settings"), settings).content(body)).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/admin/emoticons/settings"), settings).content("{\"rewardPerAcquire\":-1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.rewardPerAcquire").exists());
        Cookie none = login(operatorWith(Permission.POINTSHOP_READ));
        mvc.perform(get("/api/admin/emoticons/packs").cookie(none)).andExpect(status().isForbidden());
    }

    /** 후속 결정 6 — 작가 탈퇴로 멈춘 팩: 목록·상세의 {@code suspendKind} · {@code resumable} · 다시 판매·가격 바꾸기 409(두 언어 문구) · 운영자 판매 중단 팩은 다시 판매 */
    @Test
    void 작가_탈퇴로_멈춘_팩은_목록_상세에_구분되고_다시_판매와_가격_바꾸기가_409() throws Exception {
        Cookie reviewer = login(operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW));
        long creator = creator();
        long packId = submitted(creator);
        long otherPack = submitted(creator());
        for (long id : new long[] {packId, otherPack}) {
            String reviewId = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, id).toString();
            mvc.perform(withCsrf(post("/api/admin/emoticons/reviews/" + reviewId + "/approve"), reviewer).content("{\"price\":0}")).andExpect(status().isNoContent());
        }
        new TransactionTemplate(transactions).executeWithoutResult(s -> creatorWithdrawal.withdraw(creator));
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", creator);

        mvc.perform(get("/api/admin/emoticons/packs/" + packId).cookie(reviewer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.suspendKind").value("CREATOR_WITHDRAWN")).andExpect(jsonPath("$.data.resumable").value(false))
                .andExpect(jsonPath("$.data.suspension.by").doesNotExist()).andExpect(jsonPath("$.data.creator.withdrawn").value(true));
        mvc.perform(get("/api/admin/emoticons/packs").param("creator", Long.toString(creator)).cookie(reviewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].suspendKind").value("CREATOR_WITHDRAWN"));
        mvc.perform(withCsrf(post("/api/admin/emoticons/packs/" + packId + "/resume"), reviewer).header("Accept-Language", "ko")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_CREATOR_WITHDRAWN"))
                .andExpect(jsonPath("$.error.message").value("작가가 탈퇴해 판매를 멈춘 팩이라 다시 판매할 수 없어요."));
        mvc.perform(withCsrf(post("/api/admin/emoticons/packs/" + packId + "/price"), reviewer).content("{\"price\":2500,\"reason\":\"유료로 바꿔요\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMOTICON_PACK_CREATOR_WITHDRAWN"));
        // 운영 판단의 판매 중단은 그대로 — OPERATOR · 다시 판매 단추 · 다시 판매
        mvc.perform(withCsrf(post("/api/admin/emoticons/packs/" + otherPack + "/suspend"), reviewer).content("{\"reason\":\"운영 판단으로 멈춤\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspendKind").value("OPERATOR")).andExpect(jsonPath("$.data.resumable").value(true));
        mvc.perform(withCsrf(post("/api/admin/emoticons/packs/" + otherPack + "/resume"), reviewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED")).andExpect(jsonPath("$.data.suspendKind").doesNotExist());
    }

    /**
     * S2 — 판매 중 작가 팩 가격 바꾸기(emoticon.review · 무료 → 유료는 정산 정보 + 지금 판 계약 동의 + 유료 판매 켜짐) · 정산 계좌 원문 보기(emoticon.payout.pii · 사유 · 감사 뒤 값).
     */
    @Test
    void 가격_바꾸기는_심사_권한_정산_계좌_원문_보기는_payout_pii_권한() throws Exception {
        long creator = creator();
        long packId = submitted(creator);
        Cookie reviewer = login(operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_REVIEW));
        String reviewId = jdbc.queryForObject("SELECT id FROM emoticon_reviews WHERE pack_id = ?", Long.class, packId).toString();
        mvc.perform(withCsrf(post("/api/admin/emoticons/reviews/" + reviewId + "/approve"), reviewer).content("{\"price\":0}")).andExpect(status().isNoContent());
        // 작가 — 본인인증 이름 · 정산 정보 · 지금 판 계약 동의
        jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, birth_date, provider, provider_tx_id, verified_at, name_enc) VALUES (?, ?, '1990-01-01', 'FAKE', ?, now(), ?)",
                creator, String.format("%064x", creator), "adm-emo:" + creator, cipher.encrypt(IdentityVerificationService.NAME, "홍길동"));
        new TransactionTemplate(transactions).executeWithoutResult(s -> payouts.save(creator, new EmoticonPayoutAccountRequest("INDIVIDUAL", null, "NH", "3021234567890",
                "홍길동")));
        Integer last = jdbc.queryForObject("SELECT coalesce(max(version), 0) FROM terms_versions WHERE kind = 'ROBOTIKON_CONTRACT'", Integer.class);
        jdbc.update("INSERT INTO terms_versions (id, kind, version, body, effective_date, published_at) VALUES (?, 'ROBOTIKON_CONTRACT', ?, '<p>계약</p>', current_date - 1, now())",
                ids.nextId(), last + 1);
        jdbc.update("INSERT INTO emoticon_contract_consents (id, user_id, version, agreed_at) VALUES (?, ?, ?, now())", ids.nextId(), creator, last + 1);
        Boolean paidWas = jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class);
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = true WHERE id = 1");
        try {
            Cookie reader = login(operatorWith(Permission.EMOTICON_READ));
            String price = "{\"price\":2500,\"reason\":\"유료로 바꿔요\"}";
            mvc.perform(withCsrf(post("/api/admin/emoticons/packs/" + packId + "/price"), reader).content(price)).andExpect(status().isForbidden());
            mvc.perform(withCsrf(post("/api/admin/emoticons/packs/" + packId + "/price"), reviewer).content("{\"price\":2500,\"reason\":\"짧음\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
            mvc.perform(withCsrf(post("/api/admin/emoticons/packs/" + packId + "/price"), reviewer).content(price)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.price").value(2500))
                    .andExpect(jsonPath("$.data.paidReady.contractAgreed").value(true)).andExpect(jsonPath("$.data.paidReady.payoutRegistered").value(true))
                    .andExpect(jsonPath("$.data.priceEvents[0].from").value(0)).andExpect(jsonPath("$.data.priceEvents[0].to").value(2500))
                    .andExpect(jsonPath("$.data.priceEvents[0].reason").value("유료로 바꿔요"));
            // 정산 계좌 원문 보기
            String reveal = "/api/admin/emoticons/creators/" + creator + "/payout-account/reveal";
            mvc.perform(withCsrf(post(reveal), reviewer).content("{\"reason\":\"정산 이체 확인\"}")).andExpect(status().isForbidden());
            Cookie pii = login(operatorWith(Permission.EMOTICON_PAYOUT_PII));
            mvc.perform(withCsrf(post(reveal), pii).content("{\"reason\":\"짧음\"}")).andExpect(status().isBadRequest());
            mvc.perform(withCsrf(post(reveal), pii).content("{\"reason\":\"정산 이체 확인\"}")).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.bank").value("NH"))
                    .andExpect(jsonPath("$.data.number").value("3021234567890")).andExpect(jsonPath("$.data.holder").value("홍길동"))
                    .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
            mvc.perform(withCsrf(post("/api/admin/emoticons/creators/" + newMember() + "/payout-account/reveal"), pii).content("{\"reason\":\"정산 이체 확인\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND"));
            Integer audited = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'EMOTICON_PAYOUT_REVEAL' AND target_id = ?", Integer.class,
                    Long.toString(creator));
            org.assertj.core.api.Assertions.assertThat(audited).isEqualTo(1);
        } finally {
            jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", packId);
            jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ? WHERE id = 1", paidWas);
        }
    }
}
