package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.bizapplication.dto.BizContractFile;
import com.community.bizapplication.dto.BizUpload;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizContractStore;
import com.community.bizapplication.repository.master.BizContractStore.ContractRow;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 수동 결제(★-2 · F3) — 계약서 사본(형식·크기·장수·대체됨·원본 보존·암호문·열람 기록) · 입금 확인(입금액·입금일·입금자명·불일치 사유) · 예약 확정(둘 다 있어야 — 서비스 409 + DB CHECK) ·
 * 입금 기한 연장.
 */
class BizApplicationManualPaymentTest extends BizApplicationApiTestBase {

    @Autowired
    private BizContractStore contracts;

    private long awaitingDeposit() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "MANUAL");
        approve(id, "MANUAL", freshPeriod(3));
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        return id;
    }

    @Test
    void 계약서_사본_없이_또는_입금_확인_없이_확정하면_409_BIZ_DEPOSIT_INCOMPLETE() {
        long id = awaitingDeposit();
        assertThat(codeOf(() -> review.confirm(operator, id))).isEqualTo("BIZ_DEPOSIT_INCOMPLETE");
        review.saveDeposit(operator, id, app(id).approved().total(), today().toString(), "시험상사", null);
        assertThat(codeOf(() -> review.confirm(operator, id))).isEqualTo("BIZ_DEPOSIT_INCOMPLETE"); // 입금만 있음

        long other = awaitingDeposit();
        review.uploadContracts(operator, other, List.of(new BizUpload("계약서.pdf", pdf())), null);
        assertThat(codeOf(() -> review.confirm(operator, other))).isEqualTo("BIZ_DEPOSIT_INCOMPLETE"); // 계약서만 있음

        review.uploadContracts(operator, id, List.of(new BizUpload("계약서.pdf", pdf())), null);
        review.confirm(operator, id);
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(a.activeContractCount()).isEqualTo(1);
        assertThat(a.depositConfirmedAt()).isNotNull();
        assertThat(lastAudit("BIZ_APPLICATION", Long.toString(id), "BIZ_APP_CONFIRM")).isNotNull();
    }

    @Test
    void DB_도_수동_예약을_계약서와_입금_확인_없이는_받지_않는다_CHECK() {
        long id = awaitingDeposit();
        assertThatThrownBy(() -> jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_ba_manual_confirmed");
        jdbc.update("UPDATE biz_applications SET deposit_confirmed_at = now() WHERE id = ?", id);
        assertThatThrownBy(() -> jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_ba_manual_confirmed");
        jdbc.update("UPDATE biz_applications SET deposit_confirmed_at = NULL, active_contract_count = 1 WHERE id = ?", id);
        assertThatThrownBy(() -> jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_ba_manual_confirmed");
        // 온라인 예약도 결제 기록 없이는 안 된다
        long online = submitTextLine(approvedBusiness(), 0, 3, "ONLINE");
        approve(online, "ONLINE", freshPeriod(3));
        assertThatThrownBy(() -> jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED' WHERE id = ?", online))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_ba_online_paid");
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
    }

    @Test
    void 계약서는_PDF_JPG_PNG_만_내용_첫_바이트로_판정하고_10MB_까지_한_번에_1에서_10장() {
        long id = awaitingDeposit();
        assertThat(fieldsOf(() -> review.uploadContracts(operator, id, List.of(new BizUpload("계약서.pdf", "그냥 글".getBytes(StandardCharsets.UTF_8))), null)))
                .containsExactly("file");
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(pdf(), 0, big, 0, pdf().length);
        assertThat(fieldsOf(() -> review.uploadContracts(operator, id, List.of(new BizUpload("큰.pdf", big)), null))).containsExactly("file");
        assertThat(fieldsOf(() -> review.uploadContracts(operator, id, List.of(), null))).containsExactly("file");
        List<BizUpload> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> new BizUpload("c" + i + ".pdf", pdf())).toList();
        assertThat(fieldsOf(() -> review.uploadContracts(operator, id, eleven, null))).containsExactly("file");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        review.uploadContracts(operator, id, List.of(new BizUpload("a.png", png), new BizUpload("b.jpg", jpg), new BizUpload("../../c.pdf", pdf())), null);
        List<ContractRow> rows = contracts.list(id);
        assertThat(rows).extracting(ContractRow::contentType).containsExactly("image/png", "image/jpeg", "application/pdf");
        assertThat(rows).extracting(ContractRow::fileName).contains("c.pdf");
        assertThat(app(id).activeContractCount()).isEqualTo(3);
        // 저장은 암호문 — 평문이 그대로 있지 않다
        byte[] stored = contracts.content(id, rows.get(2).id()).orElseThrow();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
    }

    @Test
    void 계약서를_바꾸면_새_파일을_더하고_옛_파일은_대체됨으로_남는다_대체된_파일만으로는_확정할_수_없다() {
        long id = awaitingDeposit();
        review.uploadContracts(operator, id, List.of(new BizUpload("v1.pdf", pdf())), null);
        long first = contracts.list(id).get(0).id();
        assertThat(fieldsOf(() -> review.uploadContracts(operator, id, List.of(new BizUpload("a.pdf", pdf()), new BizUpload("b.pdf", pdf())), Long.toString(first))))
                .containsExactly("file"); // 바꾸기는 정확히 1장
        review.uploadContracts(operator, id, List.of(new BizUpload("v2.pdf", pdf())), Long.toString(first));
        List<ContractRow> rows = contracts.list(id);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).replacedAt()).isNotNull();
        assertThat(rows.get(0).replacedByFileId()).isEqualTo(rows.get(1).id());
        assertThat(rows.get(1).active()).isTrue();
        assertThat(app(id).activeContractCount()).isEqualTo(1);
        // 이미 대체된 파일을 다시 바꾸면 409
        assertThat(codeOf(() -> review.uploadContracts(operator, id, List.of(new BizUpload("v3.pdf", pdf())), Long.toString(first))))
                .isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(contracts.list(id)).hasSize(2);
        // 남은 활성 파일을 대체해 버리면? — 바꾸기는 늘 새 파일을 더하므로 활성 수는 1 그대로다. 대체된 것만 남는 상황은 직접 만들어 확인
        jdbc.update("UPDATE biz_contract_files SET replaced_at = now(), replaced_by_file_id = id WHERE application_id = ?", id);
        review.saveDeposit(operator, id, app(id).approved().total(), today().toString(), "시험상사", null);
        assertThat(codeOf(() -> review.confirm(operator, id))).isEqualTo("BIZ_DEPOSIT_INCOMPLETE");
    }

    @Test
    void 계약서_열람은_data_URL_로_원본을_주고_처리_기록과_개인정보_열람_감사를_남긴다() {
        long id = awaitingDeposit();
        review.uploadContracts(operator, id, List.of(new BizUpload("계약서.pdf", pdf())), null);
        long fileId = contracts.list(id).get(0).id();
        BizContractFile file = review.viewContract(operator, id, fileId);
        assertThat(file.mimeType()).isEqualTo("application/pdf");
        assertThat(file.fileName()).isEqualTo("계약서.pdf");
        assertThat(file.url()).startsWith("data:application/pdf;base64,");
        assertThat(Base64.getDecoder().decode(file.url().substring(file.url().indexOf(',') + 1))).isEqualTo(pdf());
        assertThat(store.countEvents(id, com.community.bizapplication.entity.BizEventAction.CONTRACT_VIEWED)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_CONTRACT_VIEW'", String.class,
                Long.toString(id))).isEqualTo("PERSONAL_DATA");
        assertThat(codeOf(() -> review.viewContract(operator, awaitingDeposit(), fileId))).isEqualTo("BIZ_CONTRACT_NOT_FOUND"); // 다른 건의 파일
    }

    @Test
    void 입금액이_확정_금액과_다르면_사유가_있어야_하고_입금일은_오늘까지_입금자명_필수() {
        long id = awaitingDeposit();
        long total = app(id).approved().total();
        String today = today().toString();
        assertThat(fieldsOf(() -> review.saveDeposit(operator, id, total - 1000, today, "시험상사", null))).containsExactly("mismatchReason");
        assertThat(fieldsOf(() -> review.saveDeposit(operator, id, 0L, today().plusDays(1).toString(), " ", null)))
                .containsExactly("amount", "depositedOn", "depositorName");
        review.saveDeposit(operator, id, total - 1000, today, "시험상사", "송금 수수료 차감");
        assertThat(store.deposit(id).orElseThrow().mismatchReason()).isEqualTo("송금 수수료 차감");
        // 고치면 덮는다(같은 금액이면 사유는 지운다)
        review.saveDeposit(operator, id, total, today, "시험상사", "필요 없는 사유");
        assertThat(store.deposit(id).orElseThrow().amount()).isEqualTo(total);
        assertThat(store.deposit(id).orElseThrow().mismatchReason()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id = ? AND action = 'BIZ_DEPOSIT_CONFIRM' AND retention_class = 'MONEY'",
                Integer.class, Long.toString(id))).isEqualTo(2);
    }

    @Test
    void 입금_기한은_지금_뒤로만_바꿀_수_있고_지나도_자동_취소하지_않는다() {
        long id = awaitingDeposit();
        assertThat(fieldsOf(() -> review.extendDeposit(operator, id, clock.instant().minusSeconds(60).toString()))).containsExactly("deadlineAt");
        clock.set(app(id).depositDeadlineAt().plus(Duration.ofDays(1)));
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT); // 지나도 그대로(입금 기한 지남 큐)
        String later = clock.instant().plus(Duration.ofDays(3)).toString();
        review.extendDeposit(operator, id, later);
        assertThat(app(id).depositDeadlineAt()).isEqualTo(java.time.Instant.parse(later));
        assertThat(lastAudit("BIZ_APPLICATION", Long.toString(id), "BIZ_APP_DEPOSIT_DEADLINE")).isNotNull();
    }

    @Test
    void 입금_대기가_아니면_계약서_입금_확정_기한_모두_409() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "ONLINE");
        assertThat(codeOf(() -> review.uploadContracts(operator, id, List.of(new BizUpload("a.pdf", pdf())), null))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> review.saveDeposit(operator, id, 1000L, today().toString(), "x", "y"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> review.confirm(operator, id))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> review.extendDeposit(operator, id, clock.instant().plus(Duration.ofDays(1)).toString()))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }
}
