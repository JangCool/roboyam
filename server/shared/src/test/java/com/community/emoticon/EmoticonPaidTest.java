package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditEvent;
import com.community.emoticon.dto.request.EmoticonPayoutAccountRequest;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonSettingsSaveRequest;
import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.identity.exception.IdentityRequiredException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataAccessException;

/**
 * 로보티콘 S2(emoticon-contract 「가격과 결제」 · 가정 1·5 · 목업 3차 · 「유료 ↔ 무료 전환」 · 「화면 확정」) — 실제 PostgreSQL.
 * 상품화 계약(지금 판 · 동의 추가만 · 판이 바뀌면 다시 동의) · 작가 정산 정보(본인인증 · 예금주 대조 · 암호화 · 가림 · 원문 보기 사유·감사) · 유료 희망 신청 조건 · 승인 가격(단계 · 유료 조건) ·
 * 가격 바꾸기(유료↔무료 · 사유 · 기록 · 알림 · 가진 사람·보상) · 설정의 유료 칸(범위 · 쓰는 단계 빼기 막기 · 감사) · DB 마지막 그물(CHECK · 트리거).
 */
class EmoticonPaidTest extends EmoticonTestBase {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static Map<String, ?> fields(Executable call) {
        try {
            call.execute();
        } catch (ValidationException e) {
            return e.getFields();
        } catch (Throwable e) {
            throw new AssertionError("400 VALIDATION 이 아니다: " + e, e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    private static String key(Map<String, ?> fields, String field) {
        return ((com.community.zconfiguration.message.MessageRef) fields.get(field)).key();
    }

    private static String code(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getErrorCode();
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("오류가 나지 않았다");
    }

    /** 정산 정보·지금 판 동의를 갖춘 작가의 유료 팩을 2,500원으로 승인한다 — 팩 ID */
    private long paidPack(long creator, int contract) {
        long id = Long.parseLong(kit.submitPaid(creator, "유료 팩", contract).id());
        kit.approve(id, 2500);
        return id;
    }

    private Object acquire(long member, long pack) {
        return kit.inTx(() -> kit.acquire.acquire(member, Long.toString(pack)));
    }

    private EmoticonAdminView.PackDetail changePrice(long packId, Integer price, String reason) {
        return kit.inTx(() -> kit.packs.changePrice(kit.operator, "10.0.0.9", Long.toString(packId), new EmoticonReviewDecisionRequest(price, reason)));
    }

    private List<Map<String, Object>> notices(long userId) {
        return kit.jdbc.queryForList("SELECT payload->>'kind' AS kind, payload->>'note' AS note, payload->>'from' AS \"from\", payload->>'to' AS \"to\" "
                + "FROM member_notifications WHERE user_id = ? AND type = 'EMOTICON_PACK' ORDER BY id", userId);
    }

    // ── 상품화 계약 ─────────────────────────────────────────────────────────

    @Test
    void 계약_화면은_지금_판_제목_요약_평문_전문_내_동의_시행_전_판은_지금_판이_아니다() {
        int v = kit.publishContract();
        kit.publishContract(LocalDate.now(SEOUL).plusDays(3)); // 시행 예정 — 지금 판이 아니다
        long creator = kit.member();
        EmoticonStudioView.Contract c = kit.contracts.creatorView(creator);
        assertThat(c.version()).isEqualTo(v);
        assertThat(c.effectiveDate()).isEqualTo(LocalDate.now(SEOUL).toString());
        assertThat(c.title()).isEqualTo("로보티콘 상품화 계약 v" + v);
        assertThat(c.summary()).containsExactly("사이트가 판매자예요.", "정산 기준의 70%를 받아요 & 매달 정산해요.");
        assertThat(c.body()).doesNotContain("<", ">", "&amp;").contains("제1조(목적) 이 계약은 판매 조건을 정한다.").contains("제2조\n둘째 줄")
                .doesNotContain("로보티콘 상품화 계약 v" + v);
        assertThat(c.agreements()).isEmpty();
    }

    @Test
    void 유료_희망은_정산_정보_다음_지금_판_동의_동의는_한_번만_남고_판이_바뀌면_다시_동의() {
        kit.withPaid(() -> {
            int v1 = kit.publishContract();
            long creator = kit.member();
            // 정산 정보가 먼저(계약 판을 맞게 보내도)
            assertThat(code(() -> kit.submitPaid(creator, "유료 하나", v1))).isEqualTo(EmoticonConflictException.PAYOUT_REQUIRED);
            kit.registerPayout(creator);
            // 판을 보내지 않거나 다른 판이면 409
            assertThat(code(() -> kit.submitPaid(creator, "유료 하나", 0))).isEqualTo(EmoticonConflictException.CONTRACT_REQUIRED);
            assertThat(code(() -> kit.inTx(() -> kit.studio.submit(creator, null, EmoticonTestKit.paidBody("유료 하나", kit.uploadCover(creator),
                    kit.uploads(creator, 8), null))))).isEqualTo(EmoticonConflictException.CONTRACT_REQUIRED);
            assertThat(kit.count("SELECT count(*) FROM emoticon_contract_consents WHERE user_id = ?", creator)).isZero();
            EmoticonStudioView.Pack first = kit.submitPaid(creator, "유료 하나", v1);
            assertThat(first.status()).isEqualTo("PENDING");
            assertThat(first.saleWish()).isEqualTo("PAID");
            assertThat(first.price()).as("가격은 운영자가 승인 때 정한다").isZero();
            assertThat(kit.contracts.creatorView(creator).agreements()).extracting(EmoticonStudioView.Agreement::version).containsExactly(v1);
            // 이미 지금 판에 동의했으면 판을 보내지 않아도 되고 다시 남기지 않는다(추가만 — 같은 판 한 줄)
            kit.inTx(() -> kit.studio.submit(creator, null, EmoticonTestKit.paidBody("유료 둘", kit.uploadCover(creator), kit.uploads(creator, 8), null)));
            assertThat(kit.count("SELECT count(*) FROM emoticon_contract_consents WHERE user_id = ?", creator)).isEqualTo(1);
            // 새 판이 나오면 다시 동의 — 옛 판은 409, 새 판이면 한 줄 더
            int v2 = kit.publishContract();
            assertThat(code(() -> kit.submitPaid(creator, "유료 셋", v1))).isEqualTo(EmoticonConflictException.CONTRACT_REQUIRED);
            kit.submitPaid(creator, "유료 셋", v2);
            assertThat(kit.contracts.creatorView(creator).agreements()).extracting(EmoticonStudioView.Agreement::version).containsExactly(v1, v2);
            assertThat(kit.jdbc.queryForObject("SELECT pack_id FROM emoticon_contract_consents WHERE user_id = ? AND version = ?", Long.class, creator, v1))
                    .isEqualTo(Long.parseLong(first.id()));
            // 무료 신청은 계약·정산 정보를 보지 않는다
            assertThat(kit.submitNew(kit.member(), "무료 팩").status()).isEqualTo("PENDING");
        });
    }

    @Test
    void 동의_기록과_가격_기록은_고치거나_지우지_못한다() {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = paidPack(creator, v);
            changePrice(pack, 0, "무료로 바꿔요");
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_contract_consents SET version = version + 100 WHERE user_id = ?", creator))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM emoticon_contract_consents WHERE user_id = ?", creator)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_price_events SET to_price = 3500 WHERE pack_id = ?", pack)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM emoticon_price_events WHERE pack_id = ?", pack)).isInstanceOf(DataAccessException.class);
        });
    }

    @Test
    void 이미_유료로_나온_팩의_새_판은_지금_판_동의가_있어야_무료_팩의_새_판은_보지_않는다() {
        kit.withPaid(() -> {
            int v1 = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long paid = paidPack(creator, v1);
            long free = kit.approvedPack(creator);
            int v2 = kit.publishContract();
            EmoticonStudioSaveRequest revision = EmoticonTestKit.body("새 판", kit.uploadCover(creator), kit.uploads(creator, 8));
            assertThat(code(() -> kit.inTx(() -> kit.studio.submit(creator, Long.toString(paid), revision)))).isEqualTo(EmoticonConflictException.CONTRACT_REQUIRED);
            EmoticonStudioSaveRequest agreed = new EmoticonStudioSaveRequest(revision.name(), revision.description(), revision.tags(), revision.coverImageId(),
                    revision.emoticons(), "FREE", null, true, true, true, v2);
            assertThat(kit.inTx(() -> kit.studio.submit(creator, Long.toString(paid), agreed)).revision().status()).isEqualTo("PENDING");
            // 무료로 나온 팩의 새 판 — 다른 작가(정산 정보 없음)여도 된다
            long other = kit.member();
            long otherFree = kit.approvedPack(other);
            assertThat(kit.inTx(() -> kit.studio.submit(other, Long.toString(otherFree), EmoticonTestKit.body("새 판", kit.uploadCover(other), kit.uploads(other, 8))))
                    .revision().status()).isEqualTo("PENDING");
            assertThat(free).isPositive();
        });
    }

    // ── 정산 정보 ───────────────────────────────────────────────────────────

    @Test
    void 정산_정보는_본인인증_403_인증_이름이_없으면_409_칸은_모아_400_예금주는_인증_이름과_같아야() {
        long member = kit.member();
        EmoticonPayoutAccountRequest ok = new EmoticonPayoutAccountRequest("INDIVIDUAL", null, "KB", "1234567890", "홍길동");
        assertThatThrownBy(() -> kit.inTx(() -> kit.payouts.save(member, ok))).isInstanceOf(IdentityRequiredException.class);
        assertThat(kit.payouts.view(member)).isEqualTo(new EmoticonStudioView.PayoutAccount(false, null, null, null, null, null, null, null));
        kit.verify(member, null);
        assertThat(code(() -> kit.inTx(() -> kit.payouts.save(member, ok)))).isEqualTo(EmoticonConflictException.IDENTITY_NAME_MISSING);

        long creator = kit.member();
        kit.verify(creator, "홍길동");
        assertThat(kit.payouts.view(creator).identityNameMasked()).isEqualTo("홍*동");
        Map<String, ?> bad = fields(() -> kit.inTx(() -> kit.payouts.save(creator, new EmoticonPayoutAccountRequest("CORP", null, "NOPE", "12-34", "김철수"))));
        assertThat(bad.keySet()).containsExactly("type", "bankCode", "accountNumber", "holderName");
        assertThat(fields(() -> kit.inTx(() -> kit.payouts.save(creator, new EmoticonPayoutAccountRequest("BUSINESS", "123-45-67890", "KB", "1234567890", "홍길동")))))
                .containsOnlyKeys("businessNumber"); // 국세청 검증 숫자가 틀렸다
        assertThat(fields(() -> kit.inTx(() -> kit.payouts.save(creator, new EmoticonPayoutAccountRequest("INDIVIDUAL", null, "KB", "123456789012345", "홍길동")))))
                .containsOnlyKeys("accountNumber"); // 15자리
        assertThat(kit.count("SELECT count(*) FROM emoticon_payout_accounts WHERE user_id = ?", creator)).isZero();
        // 정지 회원은 작가 자격이 없다
        long suspended = kit.member();
        kit.verify(suspended, "홍길동");
        kit.suspend(suspended);
        assertThat(code(() -> kit.inTx(() -> kit.payouts.save(suspended, ok)))).isEqualTo("ACCOUNT_RESTRICTED");
    }

    @Test
    void 정산_정보는_암호문으로_저장하고_가린_값만_보이며_다시_저장해도_처음_등록_시각은_남는다() {
        long creator = kit.member();
        kit.verify(creator, "홍길동");
        // 띄어쓰기는 무시하고 대조한다 · 하이픈은 뗀다 · 사업자는 사업자 번호(검증 숫자가 맞는 번호)
        EmoticonStudioView.PayoutAccount saved = kit.inTx(() -> kit.payouts.save(creator, new EmoticonPayoutAccountRequest("BUSINESS", "220-81-62517", "SHINHAN",
                "110-123-456789", " 홍 길동 ")));
        assertThat(saved).isEqualTo(new EmoticonStudioView.PayoutAccount(true, "BUSINESS", "220-81-*****", "SHINHAN", "****6789", "홍*동", "홍*동", saved.updatedAt()));
        Map<String, Object> row = kit.jdbc.queryForMap("SELECT * FROM emoticon_payout_accounts WHERE user_id = ?", creator);
        assertThat(row.get("account_number_enc").toString()).startsWith("d1:").doesNotContain("110123456789");
        assertThat(row.get("holder_name_enc").toString()).startsWith("d1:").doesNotContain("홍길동");
        assertThat(row.get("business_number_enc").toString()).startsWith("d1:").doesNotContain("2208162517");
        assertThat(kit.cipher.decrypt(EmoticonPayoutService.ACCOUNT, row.get("account_number_enc").toString())).isEqualTo("110123456789");
        Object created = row.get("created_at");
        kit.clock.advance(Duration.ofMinutes(5));
        EmoticonStudioView.PayoutAccount again = kit.inTx(() -> kit.payouts.save(creator, new EmoticonPayoutAccountRequest("INDIVIDUAL", null, "KB", "9876543210", "홍길동")));
        assertThat(again.type()).isEqualTo("INDIVIDUAL");
        assertThat(again.businessNumberMasked()).as("개인으로 바꾸면 사업자 번호는 지운다").isNull();
        assertThat(again.accountMasked()).isEqualTo("****3210");
        Map<String, Object> after = kit.jdbc.queryForMap("SELECT created_at, business_number_enc FROM emoticon_payout_accounts WHERE user_id = ?", creator);
        assertThat(after.get("created_at")).isEqualTo(created);
        assertThat(after.get("business_number_enc")).isNull();
        assertThat(kit.payouts.view(creator).registered()).isTrue();
    }

    @Test
    void 원문_보기는_사유_5_200자_등록_전이면_404_감사를_남긴_뒤에만_값_10분() {
        long creator = kit.member();
        kit.registerPayout(creator);
        assertThat(fields(() -> kit.payouts.reveal(kit.operator, "1.1.1.1", Long.toString(creator), "짧음"))).containsKey("reason");
        assertThat(code(() -> kit.payouts.reveal(kit.operator, "1.1.1.1", Long.toString(kit.member()), "정산 이체 확인"))).isEqualTo("EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND");
        assertThat(code(() -> kit.payouts.reveal(kit.operator, "1.1.1.1", "x", "정산 이체 확인"))).isEqualTo("EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND");
        assertThat(kit.audits).isEmpty();
        EmoticonAdminView.PayoutAccountReveal shown = kit.inTx(() -> kit.payouts.reveal(kit.operator, "1.1.1.1", Long.toString(creator), "정산 이체 확인"));
        assertThat(shown.bank()).isEqualTo("KB");
        assertThat(shown.number()).isEqualTo("12345678901234");
        assertThat(shown.holder()).isEqualTo("홍길동");
        assertThat(shown.type()).isEqualTo("INDIVIDUAL");
        assertThat(shown.businessNumber()).isNull();
        assertThat(shown.expiresAt()).isEqualTo(kit.clock.instant().plus(Duration.ofMinutes(10)));
        AuditEvent audit = kit.audits.get(0);
        assertThat(audit.action()).isEqualTo("EMOTICON_PAYOUT_REVEAL");
        assertThat(audit.detail()).containsEntry("reason", "정산 이체 확인");
        assertThat(audit.detail().toString()).doesNotContain("12345678901234", "홍길동");
    }

    // ── 승인 가격 ───────────────────────────────────────────────────────────

    @Test
    void 유료_희망을_가격_단계로_승인하면_유료_팩_작가_알림에_가격_받기는_막힌다() {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = Long.parseLong(kit.submitPaid(creator, "유료 승인", v).id());
            long review = kit.pendingReview(pack);
            kit.inTx(() -> kit.reviews.approve(kit.operator, "10.0.0.1", Long.toString(review), new EmoticonReviewDecisionRequest(2500, null)));
            assertThat(kit.price(pack)).isEqualTo(2500);
            assertThat(kit.jdbc.queryForObject("SELECT approved_price FROM emoticon_reviews WHERE id = ?", Integer.class, review)).isEqualTo(2500);
            assertThat(kit.shop.pack(null, Long.toString(pack)).price()).isEqualTo(2500);
            assertThat(kit.studio.pack(creator, Long.toString(pack)).price()).isEqualTo(2500);
            assertThat(kit.lastNotice(creator)).containsEntry("kind", "APPROVED_PAID").containsEntry("note", "2,500");
            assertThat(kit.audits.get(kit.audits.size() - 1).detail()).containsEntry("price", 2500);
            assertThat(kit.count("SELECT count(*) FROM emoticon_price_events WHERE pack_id = ?", pack)).as("처음 승인 가격은 시작 값 — 기록하지 않는다").isZero();
            // 유료 팩은 무료로 받지 못한다(구매 — S3)
            assertThat(code(() -> acquire(kit.member(), pack))).isEqualTo(EmoticonConflictException.PACK_PAID);
            // 심사 서랍 — 지금 판 동의 · 정산 정보
            EmoticonAdminView.ReviewDetail detail = kit.reviews.detail(Long.toString(review));
            assertThat(detail.contractConsent().version()).isEqualTo(v);
            assertThat(detail.contractConsent().current()).isTrue();
            assertThat(detail.payoutRegistered()).isTrue();
            assertThat(detail.rules().paidSalesEnabled()).isTrue();
            assertThat(detail.rules().priceTiers()).containsExactly(1500, 2500, 3500);
        });
    }

    @Test
    void 승인_가격_검사_단계_밖_유료_판매_꺼짐_무료_신청_정산_정보_없음_계약_판이_바뀜은_400_무료_승인은_된다() {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = Long.parseLong(kit.submitPaid(creator, "검사", v).id());
            String review = Long.toString(kit.pendingReview(pack));
            assertThat(fields(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, review, new EmoticonReviewDecisionRequest(2000, null))))).containsKey("price");
            kit.setPaid(false, List.of(1500, 2500, 3500));
            assertThat(key(fields(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, review, new EmoticonReviewDecisionRequest(2500, null)))), "price"))
                    .isEqualTo("emoticon.review.price.paidDisabled");
            kit.setPaid(true, List.of(1500, 2500, 3500));
            kit.publishContract(); // 작가가 동의한 판이 지난 판이 됐다
            assertThat(key(fields(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, review, new EmoticonReviewDecisionRequest(2500, null)))), "price")).isEqualTo("emoticon.review.price.noContract");
            // 무료 신청은 유료로 승인하지 못한다 · 정산 정보가 없는 작가
            long freeCreator = kit.member();
            long freePack = Long.parseLong(kit.submitNew(freeCreator, "무료 신청").id());
            assertThat(key(fields(() -> kit.inTx(() -> kit.reviews.approve(kit.operator, null, Long.toString(kit.pendingReview(freePack)),
                    new EmoticonReviewDecisionRequest(1500, null)))), "price")).isEqualTo("emoticon.review.price.freeWish");
            assertThat(kit.status(pack)).isEqualTo("PENDING");
            // 유료 희망이어도 무료 승인은 된다
            kit.approve(pack, 0);
            assertThat(kit.status(pack)).isEqualTo("APPROVED");
            assertThat(kit.price(pack)).isZero();
        });
    }

    @Test
    void 새_판_승인으로_가격이_바뀌면_가격_기록_심사_건_사유_없음_작가에게_두_알림() {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = paidPack(creator, v);
            kit.inTx(() -> kit.studio.submit(creator, Long.toString(pack), EmoticonTestKit.body("새 판", kit.uploadCover(creator), kit.uploads(creator, 8))));
            long review = kit.pendingReview(pack);
            kit.approve(pack, 3500);
            assertThat(kit.price(pack)).isEqualTo(3500);
            Map<String, Object> event = kit.jdbc.queryForMap("SELECT from_price, to_price, reason, review_id FROM emoticon_price_events WHERE pack_id = ?", pack);
            assertThat(event).containsEntry("from_price", 2500).containsEntry("to_price", 3500).containsEntry("review_id", review);
            assertThat(event.get("reason")).isNull();
            List<Map<String, Object>> list = notices(creator);
            assertThat(list.subList(list.size() - 2, list.size())).extracting(m -> m.get("kind")).containsExactly("UPDATE_APPROVED", "UPDATE_PRICE_CHANGED");
            assertThat(list.get(list.size() - 1)).containsEntry("from", "2500").containsEntry("to", "3500");
            assertThat(kit.studio.pack(creator, Long.toString(pack)).lastPriceChange()).isEqualTo(new EmoticonStudioView.PriceChange(2500, 3500, null,
                    kit.studio.pack(creator, Long.toString(pack)).lastPriceChange().changedAt()));
        });
    }

    // ── 가격 바꾸기 ─────────────────────────────────────────────────────────

    @Test
    void 유료에서_무료로_사유_필수_기록_감사_알림_가진_사람은_그대로_그_뒤_받기는_보상() {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = paidPack(creator, v);
            assertThat(fields(() -> changePrice(pack, 0, "짧음"))).containsKey("reason");
            assertThat(fields(() -> changePrice(pack, 2500, "같은 가격으로"))).containsKey("price");
            assertThat(fields(() -> changePrice(pack, null, "가격이 없어요"))).containsKey("price");
            EmoticonAdminView.PackDetail detail = changePrice(pack, 0, "  무료 이벤트로 바꿔요  ");
            assertThat(detail.price()).isZero();
            assertThat(detail.priceEvents()).hasSize(1);
            EmoticonAdminView.PriceEvent event = detail.priceEvents().get(0);
            assertThat(event.from()).isEqualTo(2500);
            assertThat(event.to()).isZero();
            assertThat(event.reason()).isEqualTo("무료 이벤트로 바꿔요");
            assertThat(event.by()).isNotBlank();
            AuditEvent audit = kit.audits.get(kit.audits.size() - 1);
            assertThat(audit.action()).isEqualTo("EMOTICON_PACK_PRICE");
            assertThat(audit.detail()).containsEntry("from", 2500).containsEntry("to", 0).containsEntry("reason", "무료 이벤트로 바꿔요");
            assertThat(notices(creator).get(notices(creator).size() - 1)).containsEntry("kind", "PRICE_CHANGED").containsEntry("note", "무료 이벤트로 바꿔요")
                    .containsEntry("from", "2500").containsEntry("to", "0");
            EmoticonStudioView.PriceChange last = kit.studio.overview(creator).packs().stream().filter(p -> p.id().equals(Long.toString(pack))).findFirst().orElseThrow()
                    .lastPriceChange();
            assertThat(last.from()).isEqualTo(2500);
            assertThat(last.to()).isZero();
            assertThat(last.reason()).isEqualTo("무료 이벤트로 바꿔요");
            // 무료가 된 뒤의 받기는 작가 받기 보상이 생긴다
            long rewardsBefore = kit.rewards(creator);
            acquire(kit.member(), pack);
            assertThat(kit.rewards(creator)).isGreaterThan(rewardsBefore);
        });
    }

    @Test
    void 무료에서_유료는_유료_승인과_같은_조건_이미_받은_사람은_계속_쓰고_새_받기와_보상은_멈춘다() {
        kit.withPaid(() -> {
            kit.publishContract();
            long creator = kit.member();
            long pack = kit.approvedPack(creator);
            long owner = kit.member();
            long remover = kit.member();
            acquire(owner, pack);
            acquire(remover, pack);
            kit.acquire.remove(remover, Long.toString(pack));
            assertThat(key(fields(() -> changePrice(pack, 2500, "유료로 바꿔요")), "price")).isEqualTo("emoticon.review.price.noPayout");
            kit.verify(creator, "홍길동");
            kit.inTx(() -> kit.payouts.save(creator, new EmoticonPayoutAccountRequest("INDIVIDUAL", null, "KB", "1234567890", "홍길동")));
            assertThat(key(fields(() -> changePrice(pack, 2500, "유료로 바꿔요")), "price")).isEqualTo("emoticon.review.price.noContract");
            assertThat(kit.packs.detail(Long.toString(pack)).paidReady()).isEqualTo(new EmoticonAdminView.PaidReady(false, true));
            // 동의(유료 희망 신청 — 다른 팩)로 지금 판을 갖춘다
            kit.submitPaid(creator, "동의용", kit.contracts.current().orElseThrow().version());
            assertThat(kit.packs.detail(Long.toString(pack)).paidReady()).isEqualTo(new EmoticonAdminView.PaidReady(true, true));
            assertThat(fields(() -> changePrice(pack, 2000, "단계 밖이에요"))).containsKey("price");
            assertThat(changePrice(pack, 2500, "유료로 바꿔요").price()).isEqualTo(2500);
            long rewards = kit.rewards(creator);
            int count = kit.acquireCount(pack);
            // 이미 받은 사람은 계속 쓴다(내 로보티콘 · 토큰 검사) — 뺐던 사람도 다시 넣을 수 있다(다시 사지 않는다)
            assertThat(kit.acquire.mine(owner).packs()).extracting(EmoticonPublicViewIds::id).contains(Long.toString(pack));
            long emoticon = kit.liveEmoticons(pack).get(0);
            kit.guard.requireOwned(owner, ":emo-" + emoticon + ":");
            acquire(remover, pack);
            assertThat(kit.acquire.mine(remover).packs()).extracting(EmoticonPublicViewIds::id).contains(Long.toString(pack));
            // 새 회원은 받지 못하고 보상·받은 수는 그대로
            long newcomer = kit.member();
            assertThat(code(() -> acquire(newcomer, pack))).isEqualTo(EmoticonConflictException.PACK_PAID);
            assertThat(code(() -> kit.guard.requireOwned(newcomer, ":emo-" + emoticon + ":"))).isEqualTo("EMOTICON_NOT_OWNED");
            assertThat(kit.rewards(creator)).isEqualTo(rewards);
            assertThat(kit.acquireCount(pack)).isEqualTo(count);
        });
    }

    @Test
    void 유료_단계_바꾸기는_계약을_다시_보지_않고_유료_판매가_꺼지면_무료로만_판매_중_작가_팩만() {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = paidPack(creator, v);
            kit.publishContract(); // 동의한 판이 지난 판 — 이미 유료인 팩의 단계 바꾸기는 상관없다(목업)
            assertThat(changePrice(pack, 3500, "단계를 올려요").price()).isEqualTo(3500);
            kit.setPaid(false, List.of(1500, 2500, 3500));
            assertThat(key(fields(() -> changePrice(pack, 1500, "단계를 내려요")), "price")).isEqualTo("emoticon.review.price.paidDisabled");
            assertThat(changePrice(pack, 0, "무료로 바꿔요").price()).isZero();
            kit.setPaid(true, List.of(1500, 2500, 3500));
            // 판매 중단 팩 · 운영 팩 · 심사 대기 팩은 409
            kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(pack), "권리 확인 중이에요"));
            assertThat(code(() -> changePrice(pack, 1500, "판매 중단 팩이에요"))).isEqualTo(EmoticonConflictException.PACK_NOT_ON_SALE);
            long official = kit.jdbc.queryForObject("SELECT id FROM emoticon_packs WHERE source = 'OFFICIAL' ORDER BY id LIMIT 1", Long.class);
            assertThat(code(() -> changePrice(official, 1500, "운영 팩이에요"))).isEqualTo(EmoticonConflictException.PACK_NOT_ON_SALE);
            long pending = Long.parseLong(kit.submitNew(creator, "대기").id());
            assertThat(code(() -> changePrice(pending, 1500, "대기 팩이에요"))).isEqualTo(EmoticonConflictException.PACK_NOT_ON_SALE);
            assertThat(code(() -> changePrice(Long.MAX_VALUE, 1500, "없는 팩이에요"))).isEqualTo("EMOTICON_PACK_NOT_FOUND");
            assertThat(kit.packs.detail(Long.toString(pack)).priceEvents()).extracting(EmoticonAdminView.PriceEvent::to).containsExactly(0, 3500);
        });
    }

    // ── 설정의 유료 칸 ──────────────────────────────────────────────────────

    @Test
    void 설정의_유료_칸은_범위를_모아_400_단계는_정렬해_저장_공개_규칙_반영_감사() {
        kit.withSeller(() -> paidFieldsSaved()); // 유료 판매 켜기는 운영 주체가 판매할 수 있는 단계여야 한다(사용자 결정 2026-11-07 — 그 규칙은 EmoticonOperatorStageTest)
    }

    private void paidFieldsSaved() {
        Map<String, Object> was = kit.settingsRow();
        try {
            Map<String, ?> bad = fields(() -> kit.settings.save(kit.operator, null, new EmoticonSettingsSaveRequest(10, 500, 8, 40, 3, 91, 29, 1_000_001, 31, null,
                    List.of())));
            assertThat(bad.keySet()).containsExactly("feeRatePercent", "settlementDay", "payoutMinimum", "refundDays", "paidSalesEnabled", "priceTiers");
            assertThat(key(fields(() -> save(List.of(1500, 2550))), "priceTiers")).isEqualTo("emoticon.settings.priceTiers.range");
            assertThat(key(fields(() -> save(List.of(1500, 1500))), "priceTiers")).isEqualTo("emoticon.settings.priceTiers.duplicate");
            assertThat(key(fields(() -> save(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).stream().map(i -> i * 100).toList())), "priceTiers")).isEqualTo("emoticon.settings.priceTiers.count");
            assertThat(key(fields(() -> save(List.of(50))), "priceTiers")).isEqualTo("emoticon.settings.priceTiers.range");
            EmoticonAdminView.SettingsView saved = kit.settings.save(kit.operator, "10.0.0.5", new EmoticonSettingsSaveRequest((Integer) was.get("reward_per_acquire"),
                    (Integer) was.get("reward_daily_cap"), (Integer) was.get("item_min"), (Integer) was.get("item_max"), (Integer) was.get("pending_limit"), 25, 15, 20_000,
                    5, true, List.of(4000, 1000, 2000)));
            assertThat(saved.priceTiers()).containsExactly(1000, 2000, 4000);
            assertThat(saved.feeRatePercent()).isEqualTo(25);
            assertThat(saved.settlementDay()).isEqualTo(15);
            assertThat(saved.payoutMinimum()).isEqualTo(20_000);
            assertThat(saved.refundDays()).isEqualTo(5);
            assertThat(saved.paidSalesEnabled()).isTrue();
            assertThat(saved.defaults().paidSalesEnabled()).isFalse();
            assertThat(saved.defaults().priceTiers()).containsExactly(1500, 2500, 3500);
            assertThat(kit.settings.rules().priceTiers()).containsExactly(1000, 2000, 4000);
            assertThat(kit.settings.rules().feeRate()).isEqualTo(0.25);
            assertThat(kit.settings.rules().paidSalesEnabled()).isTrue();
            assertThat(kit.settings.rules().settlementMin()).isEqualTo(20_000);
            Map<?, ?> after = (Map<?, ?>) kit.audits.get(kit.audits.size() - 1).detail().get("after");
            assertThat(after.keySet()).map(String::valueOf).containsExactlyInAnyOrder("paidSalesEnabled", "feeRatePercent", "settlementDay", "payoutMinimum", "refundDays",
                    "priceTiers");
            assertThat(after.get("priceTiers")).isEqualTo(List.of(1000, 2000, 4000));
        } finally {
            kit.restoreSettings(was);
        }
    }

    @Test
    void 상점에_나온_팩이_쓰는_단계는_뺄_수_없다_판매_중단도_센다_설정_화면에_쓰는_단계와_계약_요약() {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = paidPack(creator, v);
            kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(pack), "권리 확인 중이에요"));
            Map<String, Object> was = kit.settingsRow();
            try {
                assertThat(key(fields(() -> save(List.of(1500, 3500))), "priceTiers")).isEqualTo("emoticon.settings.priceTiers.inUse");
                EmoticonAdminView.SettingsView view = kit.settings.adminView();
                assertThat(view.priceTiersInUse()).contains(2500);
                assertThat(view.contract().version()).isEqualTo(v);
                assertThat(view.contract().agreedCount()).isEqualTo(kit.count("SELECT count(*) FROM emoticon_contract_consents WHERE version = ?", v));
                assertThat(view.contract().agreedCount()).isPositive();
                // DB 트리거도 막는다
                assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settings SET price_tiers = '{1500,3500}' WHERE id = 1")).isInstanceOf(DataAccessException.class);
            } finally {
                kit.jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", pack);
                kit.restoreSettings(was);
            }
        });
    }

    @Test
    void DB_마지막_그물_운영_팩은_무료_가격은_열린_단계만_단계는_정렬된_100원_단위() {
        long official = kit.jdbc.queryForObject("SELECT id FROM emoticon_packs WHERE source = 'OFFICIAL' ORDER BY id LIMIT 1", Long.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET price = 1500 WHERE id = ?", official)).isInstanceOf(DataAccessException.class);
        long creatorPack = kit.approvedPack(kit.member());
        kit.withPaid(false, List.of(1500, 2500, 3500), () -> assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET price = 1500 WHERE id = ?", creatorPack))
                .as("유료 판매가 꺼져 있다").isInstanceOf(DataAccessException.class));
        kit.withPaid(() -> {
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_packs SET price = 2000 WHERE id = ?", creatorPack)).as("단계 밖").isInstanceOf(DataAccessException.class);
            assertThat(kit.jdbc.update("UPDATE emoticon_packs SET price = 1500 WHERE id = ?", creatorPack)).isEqualTo(1);
            kit.jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", creatorPack);
        });
        for (String tiers : List.of("{2500,1500}", "{}", "{1550}", "{1500,1500}", "{50}", "{200000}")) {
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settings SET price_tiers = ?::integer[] WHERE id = 1", tiers)).as(tiers)
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settings SET fee_rate_percent = 91 WHERE id = 1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE emoticon_settings SET refund_days = 0 WHERE id = 1")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void 단계_빼기와_그_단계로_가격_바꾸기가_동시에_와도_쓰는_단계가_빠지지_않는다() throws Exception {
        kit.withPaid(() -> {
            int v = kit.publishContract();
            long creator = kit.member();
            kit.registerPayout(creator);
            long pack = paidPack(creator, v);
            Map<String, Object> was = kit.settingsRow();
            try {
                List<Callable<Object>> jobs = new ArrayList<>();
                jobs.add(() -> changePrice(pack, 3500, "단계를 올려요"));
                jobs.add(() -> kit.inTx(() -> save(List.of(1500, 2500))));
                List<Object> results = EmoticonTestKit.runTogether(jobs);
                List<Integer> tiers = kit.settings.current().priceTiers();
                int price = kit.price(pack);
                assertThat(tiers).as("결과 " + results).contains(price);
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                kit.jdbc.update("UPDATE emoticon_packs SET price = 0 WHERE id = ?", pack);
                kit.restoreSettings(was);
            }
        });
    }

    /** 지금 다른 칸은 그대로 두고 유료 켜짐 + 가격 단계만 바꿔 저장한다 */
    private EmoticonAdminView.SettingsView save(List<Integer> tiers) {
        Map<String, Object> s = kit.settingsRow();
        return kit.settings.save(kit.operator, null, new EmoticonSettingsSaveRequest((Integer) s.get("reward_per_acquire"), (Integer) s.get("reward_daily_cap"),
                (Integer) s.get("item_min"), (Integer) s.get("item_max"), (Integer) s.get("pending_limit"), (Integer) s.get("fee_rate_percent"),
                (Integer) s.get("settlement_day"), (Integer) s.get("payout_minimum"), (Integer) s.get("refund_days"), true, tiers));
    }

    /** 내 로보티콘 팩 ID 꺼내기 */
    private static final class EmoticonPublicViewIds {
        static String id(com.community.emoticon.dto.response.EmoticonPublicView.MyPack p) {
            return p.id();
        }
    }
}
