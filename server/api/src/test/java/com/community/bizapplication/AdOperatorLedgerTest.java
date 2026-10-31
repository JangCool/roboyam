package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.ad.service.AdAdvertiserService;
import com.community.ad.service.AdOperatorService;
import com.community.bizapplication.dto.BizOperator;
import com.community.bizapplication.dto.BizUpload;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.BizSource;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.EventRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizContractStore;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizApplicationProperties;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizPricing;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 광고 일정표의 운영자 처리(ad-contract · 04-ia-review §2, commerce.md 18절) — 운영자 등록 → 심사 대기(자리 안 차지, 승인 때 판정) · 운영자 건은 수동 결제만 · 자기 승인 allow/deny ·
 * 운영자 취소의 심사 대기 범위 · 계약서 미리 첨부 → 확정 CHECK · 긴급 내림 → 되돌리기(연장 — 자리 없으면 거부 · 일할 환불 금액) · 광고주 연락처 암호문·가린 값·평문 열람 기록.
 */
class AdOperatorLedgerTest extends BizApplicationApiTestBase {

    @Autowired
    private AdOperatorService ads;
    @Autowired
    private AdAdvertiserService advertisers;
    @Autowired
    private BizContractStore contracts;

    /** 계정 없는 광고주 하나(이름은 겹치지 않게) */
    private String advertiser() {
        return advertisers.create(operator, new AdAdvertiserService.Create("광고주" + ids.nextId(), "김담당", "010-1234-5678", "ad" + ids.nextId() + "@example.com", null,
                null)).id();
    }

    /** 운영자가 올린 광고 이미지(파일 장부 한 줄) */
    private String operatorImage() {
        long id = ids.nextId();
        jdbc.update("INSERT INTO files (id, purpose, storage_key, content_type, size_bytes, width, height, status, uploaded_by, created_at) "
                + "VALUES (?, 'BIZ_AD_IMAGE', ?, 'image/png', 100, 10, 10, 'PENDING', ?, now())", id, "biz-ad/op/test/" + id + ".png", operator.operatorId());
        return Long.toString(id);
    }

    private long createText(String advertiserId, BizPeriods.Period p, List<String> drafts) {
        return ads.createManual(operator, new AdOperatorService.Manual(advertiserId, "TEXT_LINE", p.start().toString(), p.end().toString(), 3,
                new AdOperatorService.Creative(null, "운영자 한 줄 광고", "https://shop.example.com/op", null), 12_345L, drafts, "전화 계약"));
    }

    private long createBanner(String advertiserId, BizPeriods.Period p) {
        return ads.createManual(operator, new AdOperatorService.Manual(advertiserId, "WIDE_BANNER", p.start().toString(), p.end().toString(), null,
                new AdOperatorService.Creative(operatorImage(), null, "https://shop.example.com/banner", "운영자 배너"), 100_000L, List.of(), null));
    }

    /** 운영자 등록 건을 다른 운영자가 수동 승인(요금표 금액) */
    private void approveByOther(long id, BizPeriods.Period p) {
        BizOperator other = new BizOperator(newOperator(), "127.0.0.1");
        review.approve(other, id, "MANUAL", quoteSupply(id, p), null, clock.instant().plus(Duration.ofDays(7)).toString(), p.start().toString(), p.end().toString());
    }

    /** 운영자 등록 → 다른 운영자 승인 → 계약서 + 입금 → 예약 */
    private long scheduledText(BizPeriods.Period p) {
        long id = createText(advertiser(), p, List.of());
        approveByOther(id, p);
        confirmManual(id);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        return id;
    }

    private List<EventRow> events(long id) {
        return store.events(id);
    }

    // ── 운영자 등록 ─────────────────────────────────────────────────────────

    @Test
    void 운영자_등록은_심사_대기_SUBMITTED_수동_결제_소재_버전_1_자리_줄_처리_기록_감사() {
        BizPeriods.Period p = freshPeriod(3);
        String adv = advertiser();
        long id = createBanner(adv, p);
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.source()).isEqualTo(BizSource.OPERATOR);
        assertThat(a.preferredMethod()).isEqualTo(BizPaymentMethod.MANUAL);
        assertThat(a.userId()).isNull(); // 사업자 화면·사업자 취소에 나타나지 않는다
        assertThat(a.createdBy()).isEqualTo(operator.operatorId());
        assertThat(a.advertiserId()).isEqualTo(Long.parseLong(adv.substring(4)));
        assertThat(a.weight()).isEqualTo(5); // 비중 기본
        assertThat(a.proposedSupply()).isEqualTo(100_000L);
        // 금액 줄은 요금표 계산(넓은 배너 50,000 × 3일), 제안 금액은 따로
        List<ItemRow> items = store.items(id);
        assertThat(items).extracting(ItemRow::product).containsExactly(BizProduct.WIDE_BANNER);
        assertThat(a.quote().supply()).isEqualTo(150_000L).isEqualTo(items.get(0).supply());
        var creative = store.latestCreative(id).orElseThrow();
        assertThat(creative.version()).isEqualTo(1);
        assertThat(creative.createdByType()).isEqualTo("OPERATOR");
        assertThat(creative.content()).containsKeys("imageFileId", "linkUrl", "altText");
        assertThat(jdbc.queryForObject("SELECT status FROM files WHERE id = ?", String.class, Long.parseLong((String) creative.content().get("imageFileId"))))
                .isEqualTo("IN_USE");
        EventRow submitted = events(id).get(0);
        assertThat(submitted.action()).isEqualTo("SUBMITTED");
        assertThat(submitted.actorType()).isEqualTo(BizActorType.OPERATOR);
        assertThat(submitted.actorId()).isEqualTo(operator.operatorId());
        Map<String, Object> audit = lastAudit("BIZ_APPLICATION", Long.toString(id), "BIZ_APP_OPERATOR_CREATE");
        assertThat(audit).isNotNull();
        assertThat(audit.get("detail").toString()).contains("\"source\":\"OPERATOR\"").contains("\"proposedSupply\":100000");
    }

    @Test
    void 운영자_등록_입력_검사_광고_종류만_시작은_지금_뒤_비중_1에서_10_배너는_운영자_이미지() {
        BizPeriods.Period p = freshPeriod(3);
        String adv = advertiser();
        assertThat(fieldsOf(() -> ads.createManual(operator, new AdOperatorService.Manual(adv, "HOTDEAL_TOP", p.start().toString(), p.end().toString(), 5,
                new AdOperatorService.Creative(null, "x", "https://a.example.com", null), 1L, List.of(), null)))).contains("type");
        Instant past = BizPeriods.startOf(today());
        assertThat(fieldsOf(() -> ads.createManual(operator, new AdOperatorService.Manual(adv, "TEXT_LINE", past.toString(), past.plus(Duration.ofDays(1)).toString(), 5,
                new AdOperatorService.Creative(null, "x", "https://a.example.com", null), 1L, List.of(), null)))).containsExactly("period");
        assertThat(fieldsOf(() -> ads.createManual(operator, new AdOperatorService.Manual(adv, "TEXT_LINE", p.start().toString(), p.end().toString(), 11,
                new AdOperatorService.Creative(null, "x", "https://a.example.com", null), 0L, List.of(), null)))).containsExactlyInAnyOrder("weight", "supply");
        // 회원이 올린 이미지는 운영자 등록에 못 쓴다
        long memberImage = adImage(approvedBusiness());
        assertThat(fieldsOf(() -> ads.createManual(operator, new AdOperatorService.Manual(adv, "SQUARE_BANNER", p.start().toString(), p.end().toString(), 5,
                new AdOperatorService.Creative(Long.toString(memberImage), null, "https://a.example.com", "대체 글"), 1L, List.of(), null))))
                .containsExactly("creative.imageFileId");
        assertThat(fieldsOf(() -> ads.createManual(operator, new AdOperatorService.Manual("adv-1", "TEXT_LINE", p.start().toString(), p.end().toString(), 5,
                new AdOperatorService.Creative(null, "x", "https://a.example.com", null), 1L, List.of(), null)))).containsExactly("advertiserId");
    }

    @Test
    void 사업자를_고른_등록은_사업자_신원과_상호_스냅샷을_남기고_회원은_비운다() {
        long user = approvedBusiness();
        long profile = profileOf(user);
        BizPeriods.Period p = freshPeriod(2);
        long id = createText("biz-" + profile, p, List.of());
        AppRow a = app(id);
        assertThat(a.businessProfileId()).isEqualTo(profile);
        assertThat(a.companyName()).isEqualTo("시험상사");
        assertThat(a.bizNoLast4()).isEqualTo("6789");
        assertThat(a.userId()).isNull();
        assertThat(a.advertiserId()).isNull();
    }

    @Test
    void 심사_대기는_자리를_차지하지_않고_승인할_때_잠금_아래_판정한다() {
        BizPeriods.Period p = freshPeriod(3); // 넓은 배너 한도 2
        String adv = advertiser();
        long first = createBanner(adv, p);
        long second = createBanner(adv, p);
        long third = createBanner(adv, p); // 심사 대기 둘이 있어도 등록된다
        assertThat(List.of(app(first), app(second), app(third))).extracting(AppRow::status).containsOnly(BizApplicationStatus.SUBMITTED);
        approveByOther(first, p);
        approveByOther(second, p);
        assertThat(app(first).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        assertThat(codeOf(() -> approveByOther(third, p))).isEqualTo("BIZ_SLOT_FULL");
        assertThat(app(third).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        // 승인된 둘이 자리를 잡은 뒤에는 등록도 안내 판정에서 막힌다
        assertThat(codeOf(() -> createBanner(adv, p))).isEqualTo("BIZ_SLOT_FULL");
    }

    @Test
    void 운영자_등록_건은_온라인으로_승인할_수_없다_400_fields_method() {
        BizPeriods.Period p = freshPeriod(2);
        long id = createText(advertiser(), p, List.of());
        BizOperator other = new BizOperator(newOperator(), "127.0.0.1");
        assertThat(fieldsOf(() -> review.approve(other, id, "ONLINE", quoteSupply(id, p), null, clock.instant().plus(Duration.ofHours(1)).toString(),
                p.start().toString(), p.end().toString()))).containsExactly("method");
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
    }

    // ── 자기 승인 ───────────────────────────────────────────────────────────

    @Test
    void 자기_승인_allow_는_메모가_있어야_하고_처리_기록과_감사에_selfApproved() {
        BizPeriods.Period p = freshPeriod(2);
        long id = createText(advertiser(), p, List.of());
        String deadline = clock.instant().plus(Duration.ofDays(7)).toString();
        assertThat(fieldsOf(() -> review.approve(operator, id, "MANUAL", quoteSupply(id, p), null, deadline, p.start().toString(), p.end().toString(), " ")))
                .containsExactly("memo");
        review.approve(operator, id, "MANUAL", quoteSupply(id, p), null, deadline, p.start().toString(), p.end().toString(), "1인 운영 — 전화 계약 확인");
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        EventRow approved = events(id).stream().filter(e -> e.action().equals("APPROVED_MANUAL")).findFirst().orElseThrow();
        assertThat(approved.note()).startsWith("SELF_APPROVED · 1인 운영");
        assertThat(lastAudit("BIZ_APPLICATION", Long.toString(id), "BIZ_APP_APPROVE").get("detail").toString()).contains("\"selfApproved\":true");

        // 다른 운영자가 승인하면 자기 승인이 아니다(메모 없이)
        long other = createText(advertiser(), freshPeriod(2), List.of());
        BizPeriods.Period op = BizPeriods.ofDates(BizPeriods.startDate(app(other).startAt()), BizPeriods.endDate(app(other).endAt()));
        approveByOther(other, op);
        assertThat(lastAudit("BIZ_APPLICATION", Long.toString(other), "BIZ_APP_APPROVE").get("detail").toString()).doesNotContain("selfApproved");
    }

    @Test
    void 자기_승인_deny_는_409_BIZ_SELF_APPROVAL_DENIED_다른_운영자는_승인한다() {
        Object before = ReflectionTestUtils.getField(review, "props");
        ReflectionTestUtils.setField(review, "props", new BizApplicationProperties(72, 7, new BizApplicationProperties.DepositAccount("", "", ""),
                BizApplicationProperties.SelfApproval.DENY));
        try {
            BizPeriods.Period p = freshPeriod(2);
            long id = createText(advertiser(), p, List.of());
            assertThat(codeOf(() -> review.approve(operator, id, "MANUAL", quoteSupply(id, p), null, clock.instant().plus(Duration.ofDays(7)).toString(),
                    p.start().toString(), p.end().toString(), "메모가 있어도"))).isEqualTo("BIZ_SELF_APPROVAL_DENIED");
            assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
            approveByOther(id, p);
            assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        } finally {
            ReflectionTestUtils.setField(review, "props", before);
        }
    }

    @Test
    void 설정_기본값은_자기_승인_허용() {
        assertThat(BizApplicationProperties.DEFAULTS.selfApproval()).isEqualTo(BizApplicationProperties.SelfApproval.ALLOW);
        assertThat(new BizApplicationProperties(72, 7, null, null).selfApproval()).isEqualTo(BizApplicationProperties.SelfApproval.ALLOW);
    }

    // ── 운영자 취소 ─────────────────────────────────────────────────────────

    @Test
    void 운영자_취소의_심사_대기는_운영자_등록_건만_사업자_신청은_반려로() {
        long id = createText(advertiser(), freshPeriod(2), List.of());
        review.cancel(operator, id, "등록 실수");
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(a.closedAt()).isNotNull();
        assertThat(events(id)).extracting(EventRow::action).contains("CANCELED");
        long business = submitTextLine(approvedBusiness(), 0, 2, "MANUAL");
        assertThat(codeOf(() -> review.cancel(operator, business, "사업자 건은 반려로"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(app(business).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
    }

    // ── 계약서 미리 첨부 ────────────────────────────────────────────────────

    @Test
    void 미리_올린_계약서는_등록_건에_붙고_승인_뒤_입금_확인이_있어야_확정된다() {
        var draft = review.uploadDraftContract(operator, new BizUpload("오프라인계약.pdf", pdf()));
        assertThat(contracts.findDraft(draft.id())).isPresent();
        BizPeriods.Period p = freshPeriod(2);
        long id = createText(advertiser(), p, List.of(Long.toString(draft.id())));
        assertThat(app(id).activeContractCount()).isEqualTo(1);
        assertThat(contracts.list(id)).extracting(BizContractStore.ContractRow::fileName).containsExactly("오프라인계약.pdf");
        assertThat(events(id)).extracting(EventRow::action).contains("CONTRACT_RECEIVED");
        // 이미 붙은 사본은 다른 등록에 못 쓴다
        assertThat(fieldsOf(() -> createText(advertiser(), freshPeriod(2), List.of(Long.toString(draft.id()))))).containsExactly("contractFileIds");
        // 운영자 등록 건은 심사 대기부터 계약서를 올릴 수 있다(사업자 신청의 심사 대기는 409)
        review.uploadContracts(operator, id, List.of(new BizUpload("추가.pdf", pdf())), null);
        assertThat(app(id).activeContractCount()).isEqualTo(2);
        long business = submitTextLine(approvedBusiness(), 0, 2, "MANUAL");
        assertThat(codeOf(() -> review.uploadContracts(operator, business, List.of(new BizUpload("x.pdf", pdf())), null)))
                .isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        // 입금 확인은 입금 대기에서만
        assertThat(codeOf(() -> review.saveDeposit(operator, id, 1L, today().toString(), "광고주", null))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        review.approve(operator, id, "MANUAL", quoteSupply(id, p), null, clock.instant().plus(Duration.ofDays(7)).toString(), p.start().toString(), p.end().toString(),
                "자기 승인 메모");
        assertThat(codeOf(() -> review.confirm(operator, id))).isEqualTo("BIZ_DEPOSIT_INCOMPLETE"); // 계약서만 있음
        assertThatThrownBy(() -> jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_ba_manual_confirmed");
        review.saveDeposit(operator, id, app(id).approved().total(), today().toString(), "광고주", null);
        review.confirm(operator, id);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }

    // ── 긴급 내림·되돌리기 ──────────────────────────────────────────────────

    @Test
    void 긴급_내림은_예약만_사유_필수_내린_운영자와_감사_메일_결과() {
        BizPeriods.Period p = freshPeriod(3);
        long submitted = createText(advertiser(), freshPeriod(2), List.of());
        assertThat(codeOf(() -> ads.takedown(operator, submitted, "심사 대기는 못 내린다", false))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        long id = scheduledText(p);
        assertThat(fieldsOf(() -> ads.takedown(operator, id, " ", false))).containsExactly("reason");
        clock.set(p.start().plus(Duration.ofHours(5)));
        ads.takedown(operator, id, "허위 표기 신고", true);
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(a.takedownCause()).isEqualTo(BizTakedownCause.EMERGENCY);
        assertThat(a.takedownText()).isEqualTo("허위 표기 신고");
        assertThat(a.takenDownBy()).isEqualTo(operator.operatorId());
        assertThat(a.takedownMail()).isIn("SENT", "FAILED"); // 시험 환경은 메일 서버가 없어 FAILED
        assertThat(lastAudit("BIZ_APPLICATION", Long.toString(id), "AD_TAKEDOWN").get("detail").toString()).contains("\"phase\":\"RUNNING\"");
        // 끝난 광고는 내릴 수 없다
        long ended = scheduledText(freshPeriod(1));
        clock.set(app(ended).endAt().plusSeconds(1));
        assertThat(codeOf(() -> ads.takedown(operator, ended, "끝남", false))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 되돌리기_연장은_중단_시간만큼_끝을_늘린다() {
        BizPeriods.Period p = freshPeriod(3);
        long id = scheduledText(p);
        clock.set(p.start().plus(Duration.ofHours(5)));
        ads.takedown(operator, id, "점검", false);
        clock.set(p.start().plus(Duration.ofHours(7)).plusSeconds(30));
        assertThat(fieldsOf(() -> ads.restore(operator, id, "NOPE", "x"))).containsExactly("compensation");
        ads.restore(operator, id, "EXTEND", "점검 끝");
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(a.endAt()).isEqualTo(p.end().plus(Duration.ofHours(2)));
        assertThat(a.takenDownAt()).isNull();
        assertThat(a.takenDownBy()).isNull();
        assertThat(store.items(id)).extracting(ItemRow::endAt).containsOnly(p.end().plus(Duration.ofHours(2)));
        assertThat(events(id)).filteredOn(e -> e.action().equals("RESTORED")).extracting(EventRow::note).containsExactly("EXTENDED:120 · 점검 끝");
        assertThat(lastAudit("BIZ_APPLICATION", Long.toString(id), "AD_RESTORE").get("detail").toString()).contains("\"compensation\":\"EXTEND\"")
                .contains("\"lostMinutes\":120");
    }

    @Test
    void 되돌리기_연장은_늘린_기간에_자리가_없으면_409_BIZ_SLOT_FULL_그대로_내림() {
        BizPeriods.Period p = freshPeriod(3); // 넓은 배너 한도 2
        String adv = advertiser();
        long target = createBanner(adv, p);
        approveByOther(target, p);
        confirmManual(target);
        // 끝 바로 뒤를 두 건이 채운다
        BizPeriods.Period after = new BizPeriods.Period(p.end(), p.end().plus(Duration.ofDays(2)));
        for (int i = 0; i < 2; i++) {
            long next = createBanner(adv, after);
            approveByOther(next, after);
        }
        clock.set(p.start().plus(Duration.ofHours(1)));
        ads.takedown(operator, target, "점검", false);
        clock.set(p.start().plus(Duration.ofHours(4)));
        assertThat(codeOf(() -> ads.restore(operator, target, "EXTEND", "연장"))).isEqualTo("BIZ_SLOT_FULL");
        AppRow a = app(target);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.TAKEN_DOWN);
        assertThat(a.endAt()).isEqualTo(p.end());
        // 일할 환불로는 되돌릴 수 있다
        ads.restore(operator, target, "PRORATE_REFUND", "연장 불가 — 환불");
        assertThat(app(target).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }

    @Test
    void 되돌리기_일할_환불은_중단_일수_비율로_바로_승인하고_수동은_송금_대기() {
        BizPeriods.Period p = freshPeriod(3); // 총 3일
        long id = scheduledText(p);
        long paid = app(id).approved().total();
        clock.set(p.start().plus(Duration.ofHours(2)));
        ads.takedown(operator, id, "점검", false);
        clock.set(p.start().plus(Duration.ofHours(28))); // 중단 26시간 → 2일
        ads.restore(operator, id, "PRORATE_REFUND", "운영 귀책");
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(a.endAt()).isEqualTo(p.end()); // 끝은 그대로
        RefundRow r = refunds.latest(id).orElseThrow();
        assertThat(r.type()).isEqualTo(BizRefundType.DOWNTIME);
        assertThat(r.status()).isEqualTo(BizRefundStatus.APPROVED);
        assertThat(r.method()).isEqualTo(BizPaymentMethod.MANUAL);
        assertThat(r.approvedAmount()).isEqualTo(Math.floorDiv(paid * 2, 3)).isEqualTo(r.proposedAmount());
        assertThat(r.totalDays()).isEqualTo(3);
        assertThat(r.usedDays()).isEqualTo(1);
        assertThat(r.transferStatus()).isEqualTo("PENDING");
        assertThat(lastAudit("BIZ_APPLICATION", Long.toString(id), "AD_RESTORE").get("detail").toString())
                .contains("\"refundAmount\":" + Math.floorDiv(paid * 2, 3));
        assertThat(events(id)).extracting(EventRow::action).contains("REFUND_APPROVED", "RESTORED");
    }

    @Test
    void 일할_환불_금액_계산_중단_일수_올림_최소_1_총_일수까지() {
        Instant start = BizPeriods.startOf(java.time.LocalDate.of(2031, 3, 1));
        Instant end = start.plus(Duration.ofDays(10));
        assertThat(BizPricing.downtime(100_000, start, end, Duration.ZERO).proposed()).isZero();
        assertThat(BizPricing.downtime(100_000, start, end, Duration.ofMinutes(1)).proposed()).isEqualTo(10_000);
        assertThat(BizPricing.downtime(100_000, start, end, Duration.ofHours(24)).proposed()).isEqualTo(10_000);
        assertThat(BizPricing.downtime(100_000, start, end, Duration.ofHours(25)).proposed()).isEqualTo(20_000);
        assertThat(BizPricing.downtime(99_999, start, end, Duration.ofDays(3)).proposed()).isEqualTo(29_999); // 버림
        BizPricing.Prorate all = BizPricing.downtime(100_000, start, end, Duration.ofDays(40));
        assertThat(all.proposed()).isEqualTo(100_000);
        assertThat(all.usedDays()).isZero();
    }

    @Test
    void 되돌리기는_긴급_내림만_끝나기_전에만() {
        BizPeriods.Period p = freshPeriod(2);
        long id = scheduledText(p);
        assertThat(codeOf(() -> ads.restore(operator, id, "EXTEND", "내림 아님"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        clock.set(p.start().plus(Duration.ofHours(1)));
        ads.takedown(operator, id, "점검", false);
        clock.set(p.end().plusSeconds(1));
        assertThat(codeOf(() -> ads.restore(operator, id, "EXTEND", "끝남"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        // 사업자 정지로 내린 건은 사업자 해제가 되돌린다
        BizPeriods.Period q = freshPeriod(2);
        clock.set(Instant.now());
        long other = scheduledText(q);
        jdbc.update("UPDATE biz_applications SET status = 'TAKEN_DOWN', taken_down_at = now(), takedown_cause = 'SUSPENDED' WHERE id = ?", other);
        assertThat(codeOf(() -> ads.restore(operator, other, "EXTEND", "정지 내림"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 되돌리기_보상_환불은_사업자_환불_요청을_막지_않고_뒤_제안액에서_뺀다() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "MANUAL");
        BizPeriods.Period p = freshPeriod(4);
        approve(id, "MANUAL", p);
        confirmManual(id);
        long paid = app(id).approved().total();
        clock.set(p.start().plus(Duration.ofHours(1)));
        ads.takedown(operator, id, "점검", false);
        clock.set(p.start().plus(Duration.ofHours(2)));
        ads.restore(operator, id, "PRORATE_REFUND", "운영 귀책"); // 1일치 = paid / 4
        long downtime = Math.floorDiv(paid, 4);
        assertThat(refunds.approvedTotal(id)).isEqualTo(downtime);
        clock.set(p.start().plus(Duration.ofDays(1)).plus(Duration.ofHours(1))); // 둘째 날 — 남은 2일
        members.requestRefund(user, id, "중도 해지"); // 보상 환불 줄이 있어도 요청할 수 있다
        RefundRow open = refunds.lockOpen(id).orElseThrow();
        assertThat(open.type()).isEqualTo(BizRefundType.PRORATED);
        assertThat(open.proposedAmount()).isEqualTo(Math.min(Math.floorDiv(paid * 2, 4), paid - downtime));
        assertThat(open.paidAmount()).isEqualTo(paid);
    }

    // ── 광고주 ──────────────────────────────────────────────────────────────

    @Test
    void 광고주_연락처는_암호문과_가린_값으로_두고_평문은_사유와_기록을_남겨_연다() {
        String name = "한빛손해보험" + ids.nextId();
        AdAdvertiserService.Option created = advertisers.create(operator, new AdAdvertiserService.Create(name, "홍길동", "02-123-4567", "hanbit@example.com", "보험", null));
        assertThat(created.kind()).isEqualTo("ADVERTISER");
        assertThat(created.contactMasked()).isEqualTo("021-****-4567");
        long id = Long.parseLong(created.id().substring(4));
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM ad_advertisers WHERE id = ?", id);
        assertThat(row.get("contact_phone_enc").toString()).doesNotContain("1234567").doesNotContain("4567");
        assertThat(row.get("email_enc").toString()).doesNotContain("hanbit");
        assertThat(row.get("contact_name_enc").toString()).doesNotContain("홍길동");
        assertThat(row.get("contact_name_masked")).isEqualTo("홍*동");
        assertThat(row.get("email_masked")).isEqualTo("ha***@example.com");
        assertThat(advertisers.search(name)).extracting(AdAdvertiserService.Option::id).contains(created.id());
        // 같은 이름 400, 연락처·이메일 형식 400
        assertThat(fieldsOf(() -> advertisers.create(operator, new AdAdvertiserService.Create(" " + name.toLowerCase() + " ", null, null, null, null, null))))
                .containsExactly("name");
        assertThat(fieldsOf(() -> advertisers.create(operator, new AdAdvertiserService.Create("새광고주" + ids.nextId(), null, "12", "nope", null, null))))
                .containsExactlyInAnyOrder("contactPhone", "email");
        // 평문 열람 — 사유 5자 이상, 기록 + 감사
        assertThat(fieldsOf(() -> advertisers.reveal(operator, created.id(), "짧음"))).containsExactly("reason");
        AdAdvertiserService.Revealed revealed = advertisers.reveal(operator, created.id(), "계약 확인 전화");
        assertThat(revealed.contactName()).isEqualTo("홍길동");
        assertThat(revealed.contactPhone()).isEqualTo("02-123-4567");
        assertThat(revealed.email()).isEqualTo("hanbit@example.com");
        assertThat(revealed.hideAt()).isAfter(clock.instant());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ad_advertiser_views WHERE advertiser_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(lastAudit("AD_ADVERTISER", Long.toString(id), "AD_CONTACT_REVEAL").get("detail").toString()).contains("계약 확인 전화")
                .doesNotContain("hanbit@example.com");
        assertThat(lastAudit("AD_ADVERTISER", Long.toString(id), "AD_ADVERTISER_CREATE").get("detail").toString()).doesNotContain("홍길동").doesNotContain("4567");
        assertThat(codeOf(() -> advertisers.reveal(operator, "adv-1", "없는 광고주 열람"))).isEqualTo("AD_ADVERTISER_NOT_FOUND");
    }

    @Test
    void 광고주는_사업자와_1대1로_연결되고_연결된_사업자는_검색에서_광고주로만_나온다() {
        long user = approvedBusiness();
        long profile = profileOf(user);
        AdAdvertiserService.Option linked = advertisers.create(operator, new AdAdvertiserService.Create("연결광고주" + ids.nextId(), null, null, null, null,
                Long.toString(profile)));
        assertThat(linked.kind()).isEqualTo("BUSINESS");
        assertThat(linked.businessId()).isEqualTo(Long.toString(profile));
        assertThat(linked.maskedRegNo()).isEqualTo("***-**-*6789");
        assertThat(fieldsOf(() -> advertisers.create(operator, new AdAdvertiserService.Create("또연결" + ids.nextId(), null, null, null, null, Long.toString(profile)))))
                .containsExactly("businessId");
        assertThat(advertisers.search("시험상사")).extracting(AdAdvertiserService.Option::id).doesNotContain("biz-" + profile).contains(linked.id());
        // 사업자 쪽 ID 로 등록하면 연결된 광고주도 함께 남는다
        long id = createText("biz-" + profile, freshPeriod(2), List.of());
        assertThat(app(id).advertiserId()).isEqualTo(Long.parseLong(linked.id().substring(4)));
        assertThat(new String(pdf(), StandardCharsets.US_ASCII)).startsWith("%PDF");
    }
}
