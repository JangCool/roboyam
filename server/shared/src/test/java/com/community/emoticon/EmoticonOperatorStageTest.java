package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.emoticon.dto.request.EmoticonSettingsSaveRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.testsupport.SiteOperatorFixture;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * 로보티콘 유료 판매 × 운영 주체 단계(사용자 결정 2026-11-07 — 돈을 받는 기능은 모두 {@code CommerceSalesStore.operatorCanSell()} = 개인사업자 이상 + 통신판매업 신고번호, commerce.md 18절
 * 「규칙은 테스트로」). 설정 저장(켜기 400) · 관리자 조회의 {@code paidSalesAllowed} · 공개 규칙의 {@code paidSalesEnabled}. 구매 409 는 api {@code EmoticonPurchaseHttpTest},
 * 유료 판매 중 개인 전환 409 는 admin {@code SiteOperatorCommerceHttpTest}.
 */
class EmoticonOperatorStageTest extends EmoticonTestBase {

    private Map<String, Object> settingsWas;

    @BeforeEach
    void individual() {
        settingsWas = kit.settingsRow();
        SiteOperatorFixture.clear(kit.jdbc);
        kit.jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = false WHERE id = 1");
    }

    @AfterEach
    void restore() {
        kit.restoreSettings(settingsWas);
        SiteOperatorFixture.clear(kit.jdbc);
    }

    private EmoticonSettingsSaveRequest body(boolean paid, int refundDays) {
        Map<String, Object> s = kit.settingsRow();
        return new EmoticonSettingsSaveRequest((Integer) s.get("reward_per_acquire"), (Integer) s.get("reward_daily_cap"), (Integer) s.get("item_min"),
                (Integer) s.get("item_max"), (Integer) s.get("pending_limit"), (Integer) s.get("fee_rate_percent"), (Integer) s.get("settlement_day"),
                (Integer) s.get("payout_minimum"), refundDays, paid, List.of(1500, 2500, 3500));
    }

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

    private boolean paidInDb() {
        return Boolean.TRUE.equals(kit.jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class));
    }

    @Test
    void 개인_단계에서_유료_판매를_켜는_저장은_400_fields_paidSalesEnabled_값과_감사는_그대로() {
        Map<String, ?> bad = fields(() -> kit.settings.save(kit.operator, null, body(true, 7)));
        assertThat(bad.keySet()).containsExactly("paidSalesEnabled");
        assertThat(((MessageRef) bad.get("paidSalesEnabled")).key()).isEqualTo("emoticon.settings.paidSales.operatorStage");
        assertThat(paidInDb()).isFalse();
        assertThat(kit.audits).isEmpty();

        // 개인사업자여도 통신판매업 신고번호가 없으면 같은 400
        SiteOperatorFixture.fillBusiness(kit.jdbc);
        kit.jdbc.update("UPDATE site_operator SET mail_order_number = NULL WHERE id = 1");
        assertThat(fields(() -> kit.settings.save(kit.operator, null, body(true, 7))).keySet()).containsExactly("paidSalesEnabled");
        assertThat(paidInDb()).isFalse();

        // 꺼진 채 다른 칸 저장은 개인 단계에서도 된다
        SiteOperatorFixture.clear(kit.jdbc);
        EmoticonAdminView.SettingsView saved = kit.settings.save(kit.operator, null, body(false, 6));
        assertThat(saved.refundDays()).isEqualTo(6);
        assertThat(saved.paidSalesEnabled()).isFalse();
    }

    @Test
    void 개인사업자와_신고번호가_있으면_켜진다_조회는_paidSalesAllowed_true_공개_규칙도_켜짐() {
        SiteOperatorFixture.fillBusiness(kit.jdbc);
        EmoticonAdminView.SettingsView saved = kit.settings.save(kit.operator, null, body(true, 7));
        assertThat(saved.paidSalesEnabled()).isTrue();
        assertThat(saved.paidSalesAllowed()).isTrue();
        assertThat(kit.settings.adminView().paidSalesAllowed()).isTrue();
        assertThat(kit.settings.rules().paidSalesEnabled()).isTrue();
        assertThat(paidInDb()).isTrue();
    }

    @Test
    void 개인_단계면_조회는_paidSalesAllowed_false_이미_켜진_스위치는_공개_규칙에서_꺼짐_그대로_저장은_막지_않는다() {
        assertThat(kit.settings.adminView().paidSalesAllowed()).isFalse();
        assertThat(kit.settings.rules().paidSalesEnabled()).isFalse();

        // 이미 켜져 있는 값(옮기기 전 데이터 등) — 공개 규칙은 스위치 && 운영 주체 단계, 같은 값 저장(다른 칸만 바뀜)은 된다
        kit.jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = true WHERE id = 1");
        EmoticonAdminView.SettingsView view = kit.settings.adminView();
        assertThat(view.paidSalesEnabled()).isTrue();
        assertThat(view.paidSalesAllowed()).isFalse();
        assertThat(kit.settings.rules().paidSalesEnabled()).as("스위치가 켜져 있어도 개인 단계면 공개 규칙은 꺼짐").isFalse();
        EmoticonAdminView.SettingsView saved = kit.settings.save(kit.operator, null, body(true, 5));
        assertThat(saved.refundDays()).isEqualTo(5);
        assertThat(saved.paidSalesEnabled()).isTrue();
        // 끄기도 된다
        assertThat(kit.settings.save(kit.operator, null, body(false, 5)).paidSalesEnabled()).isFalse();
    }
}
