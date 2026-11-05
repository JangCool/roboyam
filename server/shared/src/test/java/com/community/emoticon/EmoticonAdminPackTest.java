package com.community.emoticon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditEvent;
import com.community.emoticon.dto.request.EmoticonOfficialPackSaveRequest;
import com.community.emoticon.dto.request.EmoticonSettingsSaveRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.repository.master.EmoticonImageStore.ImageRow;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * 관리자 팩 관리 · 운영 팩 · 설정(emoticon-contract S1, admin-web 목업 {@code officialFields}·설정 범위) — 실제 PostgreSQL.
 * 판매 중단·다시 판매(상태가 안 맞으면 409) · 운영 팩 만들기(심사 없이 판매 중, 대표 필수·개수·이름표 겹침 없음·올린 것만) · 고치기(새 판, 로보티콘 ID 그대로, 작가 팩은 409) · 설정 범위·감사.
 */
class EmoticonAdminPackTest extends EmoticonTestBase {

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

    private List<ImageRow> operatorUploads(int n) {
        List<ImageRow> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(kit.images.uploadByOperator(kit.operator, TestImages.animated("op" + kit.ids.nextId()), EmoticonImageKind.EMOTICON));
        }
        return out;
    }

    private static EmoticonOfficialPackSaveRequest official(String name, List<String> ids, String cover) {
        List<EmoticonOfficialPackSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            items.add(new EmoticonOfficialPackSaveRequest.Item(ids.get(i), "이름" + (i + 1)));
        }
        return new EmoticonOfficialPackSaveRequest(name, "운영 팩이에요.", List.of("운영"), items, cover);
    }

    private static List<String> idsOf(List<ImageRow> images) {
        return images.stream().map(i -> Long.toString(i.id())).toList();
    }

    @Test
    void 판매_중단은_판매_중인_팩만_다시_판매는_판매_중단만_사유는_5_200자_감사() {
        long creator = kit.member();
        long pack = kit.approvedPack(creator);
        assertThat(fields(() -> kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(pack), "짧음")))).containsKey("reason");
        assertThat(code(() -> kit.inTx(() -> kit.packs.resume(kit.operator, null, Long.toString(pack))))).isEqualTo(EmoticonConflictException.PACK_NOT_SUSPENDED);
        EmoticonAdminView.PackDetail suspended = kit.inTx(() -> kit.packs.suspend(kit.operator, "10.0.0.2", Long.toString(pack), "권리 확인 중이에요"));
        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(suspended.suspension().reason()).isEqualTo("권리 확인 중이에요");
        assertThat(suspended.suspension().by()).isNotNull();
        assertThat(code(() -> kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(pack), "또 멈추기 시도")))).isEqualTo(EmoticonConflictException.PACK_NOT_ON_SALE);
        EmoticonAdminView.PackDetail resumed = kit.inTx(() -> kit.packs.resume(kit.operator, null, Long.toString(pack)));
        assertThat(resumed.status()).isEqualTo("APPROVED");
        assertThat(resumed.suspension()).isNull();
        assertThat(kit.audits).extracting(AuditEvent::action).containsSubsequence("EMOTICON_PACK_SUSPEND", "EMOTICON_PACK_RESUME");
        assertThat(kit.lastNotice(creator)).containsEntry("type", "EMOTICON_PACK").containsEntry("kind", "RESUMED");
        // 심사 중 팩은 판매 중이 아니다
        long pending = Long.parseLong(kit.submitNew(creator, "심사 중 중단").id());
        assertThat(code(() -> kit.inTx(() -> kit.packs.suspend(kit.operator, null, Long.toString(pending), "멈춰 보기 시도")))).isEqualTo(EmoticonConflictException.PACK_NOT_ON_SALE);
    }

    @Test
    void 팩_목록은_임시_제외_운영_팩이_위_거르기_작가_상세() {
        long creator = kit.member();
        long approved = kit.approvedPack(creator);
        String draft = kit.inTx(() -> kit.studio.save(creator, null, EmoticonTestKit.body("임시 목록", null, List.of()))).id();
        List<EmoticonAdminView.PackRow> rows = kit.packs.list(null, null, null, Long.toString(creator)).items();
        assertThat(rows).extracting(EmoticonAdminView.PackRow::id).containsExactly(Long.toString(approved)).doesNotContain(draft);
        assertThat(rows.get(0).rewardTotal()).isZero();
        assertThat(rows.get(0).salesCount()).isNull();
        List<EmoticonAdminView.PackRow> all = kit.packs.list(null, null, null, null).items();
        assertThat(all.get(0).source()).isEqualTo("OFFICIAL");
        assertThat(all.stream().filter(r -> r.source().equals("OFFICIAL")).allMatch(r -> r.rewardTotal() == null)).isTrue();
        assertThat(kit.packs.list("OFFICIAL", "APPROVED", "로보얌콘핑크", null).items()).extracting(EmoticonAdminView.PackRow::name).contains("로보얌콘핑크");
        EmoticonAdminView.PackDetail detail = kit.packs.detail(Long.toString(approved));
        assertThat(detail.items()).hasSize(8);
        assertThat(detail.items().get(0).contentHash()).matches("^[0-9a-f]{64}$");
        assertThat(detail.items().get(0).frames()).isEqualTo(2);
        assertThat(detail.items().get(0).loopForever()).isTrue();
        assertThat(detail.paidReady()).isEqualTo(new EmoticonAdminView.PaidReady(false, false));
        assertThat(detail.priceEvents()).isEmpty();
        assertThat(code(() -> kit.packs.detail(draft))).isEqualTo("EMOTICON_PACK_NOT_FOUND");
        // 작가 요약 — 낸 팩이 없으면 null
        assertThat(kit.packs.creator(Long.toString(creator)).packCount()).isEqualTo(1);
        assertThat(kit.packs.creator(Long.toString(kit.member()))).isNull();
    }

    @Test
    void 운영_팩_만들기는_심사_없이_판매_중_대표_필수_개수_이름표_겹침_없음_올린_것만() {
        List<ImageRow> uploads = operatorUploads(8);
        ImageRow cover = kit.images.uploadByOperator(kit.operator, TestImages.cover(0xfe0000), EmoticonImageKind.COVER);
        assertThat(fields(() -> kit.inTx(() -> kit.packs.createOfficial(kit.operator, null, official("운영 시험", idsOf(uploads), null))))).containsKey("cover");
        assertThat(fields(() -> kit.inTx(() -> kit.packs.createOfficial(kit.operator, null, official("운영 시험", idsOf(uploads.subList(0, 7)), Long.toString(cover.id()))))))
                .containsKey("items");
        List<EmoticonOfficialPackSaveRequest.Item> dup = new ArrayList<>();
        for (ImageRow u : uploads) {
            dup.add(new EmoticonOfficialPackSaveRequest.Item(Long.toString(u.id()), "같음"));
        }
        assertThat(fields(() -> kit.inTx(() -> kit.packs.createOfficial(kit.operator, null, new EmoticonOfficialPackSaveRequest("운영 시험", "", List.of(), dup,
                Long.toString(cover.id())))))).containsKey("items[1].alt").doesNotContainKey("items[0].alt");
        // 회원이 올린 그림은 운영 팩에 넣지 못한다
        long member = kit.member();
        List<String> withMemberImage = new ArrayList<>(idsOf(uploads.subList(0, 7)));
        withMemberImage.add(Long.toString(kit.upload(member).id()));
        assertThat(fields(() -> kit.inTx(() -> kit.packs.createOfficial(kit.operator, null, official("운영 시험", withMemberImage, Long.toString(cover.id())))))).containsKey("items");

        EmoticonAdminView.PackDetail created = kit.inTx(() -> kit.packs.createOfficial(kit.operator, "10.0.0.3", official("운영 시험", idsOf(uploads), Long.toString(cover.id()))));
        assertThat(created.status()).isEqualTo("APPROVED");
        assertThat(created.source()).isEqualTo("OFFICIAL");
        assertThat(created.items()).hasSize(8);
        assertThat(created.approvedAt()).isNotNull();
        assertThat(kit.shop.catalog().catalog().items()).filteredOn(i -> i.packId().equals(created.id())).hasSize(8);
        assertThat(kit.audits).extracting(AuditEvent::action).contains("EMOTICON_PACK_CREATE");
    }

    @Test
    void 운영_팩_고치기는_새_판이_바로_상점_판_로보티콘_ID_그대로_작가_팩은_409() {
        List<ImageRow> uploads = operatorUploads(8);
        ImageRow cover = kit.images.uploadByOperator(kit.operator, TestImages.cover(0xfe0101), EmoticonImageKind.COVER);
        EmoticonAdminView.PackDetail created = kit.inTx(() -> kit.packs.createOfficial(kit.operator, null, official("고치기 시험", idsOf(uploads), Long.toString(cover.id()))));
        List<String> before = created.items().stream().map(EmoticonAdminView.ItemView::id).toList();
        // 지금 로보티콘 7개(차례 뒤집기) + 새로 올린 하나, 대표는 지금 것 그대로
        List<String> next = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            next.add(before.get(i));
        }
        ImageRow fresh = operatorUploads(1).get(0);
        next.add(Long.toString(fresh.id()));
        EmoticonAdminView.PackDetail updated = kit.inTx(() -> kit.packs.updateOfficial(kit.operator, null, created.id(), official("고치기 시험 둘", next,
                created.cover().id())));
        List<String> after = updated.items().stream().map(EmoticonAdminView.ItemView::id).toList();
        assertThat(after.subList(0, 7)).isEqualTo(next.subList(0, 7));
        assertThat(before).doesNotContain(after.get(7));
        assertThat(updated.name()).isEqualTo("고치기 시험 둘");
        assertThat(kit.jdbc.queryForObject("SELECT max(version) FROM emoticon_pack_revisions WHERE pack_id = ?", Integer.class, Long.parseLong(created.id()))).isEqualTo(2);
        // 판매 중단인 운영 팩을 고쳐도 판매 중단 그대로
        kit.inTx(() -> kit.packs.suspend(kit.operator, null, created.id(), "잠시 멈춰요 확인"));
        EmoticonAdminView.PackDetail stillSuspended = kit.inTx(() -> kit.packs.updateOfficial(kit.operator, null, created.id(), official("고치기 시험 셋", after,
                created.cover().id())));
        assertThat(stillSuspended.status()).isEqualTo("SUSPENDED");
        assertThat(kit.audits).extracting(AuditEvent::action).contains("EMOTICON_PACK_UPDATE");
        // 작가 팩은 여기서 고치지 못한다
        long creatorPack = kit.approvedPack(kit.member());
        assertThat(code(() -> kit.inTx(() -> kit.packs.updateOfficial(kit.operator, null, Long.toString(creatorPack), official("작가 팩", after, created.cover().id())))))
                .isEqualTo(EmoticonConflictException.PACK_NOT_OFFICIAL);
    }

    @Test
    void 설정은_범위를_모아_400_저장하면_공개_규칙에_바로_반영_바뀐_칸만_감사() {
        Map<String, Object> was = kit.settingsRow();
        try {
            Map<String, ?> bad = fields(() -> kit.settings.save(kit.operator, null, new EmoticonSettingsSaveRequest(1001, 5, 0, 61, 11, 30, 10, 10000, 7, true, List.of(1500))));
            assertThat(bad.keySet()).containsExactly("rewardPerAcquire", "rewardDailyCap", "itemMin", "itemMax", "pendingLimit");
            assertThat(fields(() -> kit.settings.save(kit.operator, null, new EmoticonSettingsSaveRequest(20, 10, 8, 40, 3, 30, 10, 10000, 7, false, List.of(1500)))))
                    .containsOnlyKeys("rewardDailyCap");
            assertThat(fields(() -> kit.settings.save(kit.operator, null, new EmoticonSettingsSaveRequest(10, 500, 30, 20, 3, 30, 10, 10000, 7, false, List.of(1500)))))
                    .containsOnlyKeys("itemMax");
            EmoticonAdminView.SettingsView saved = kit.settings.save(kit.operator, "10.0.0.4", new EmoticonSettingsSaveRequest(15, 600, 8, 40, 3,
                    (Integer) was.get("fee_rate_percent"), (Integer) was.get("settlement_day"), (Integer) was.get("payout_minimum"), (Integer) was.get("refund_days"),
                    (Boolean) was.get("paid_sales_enabled"), kit.settings.current().priceTiers()));
            assertThat(saved.rewardPerAcquire()).isEqualTo(15);
            assertThat(saved.rewardDailyCap()).isEqualTo(600);
            assertThat(saved.updatedBy()).isNotNull();
            assertThat(kit.settings.rules().rewardPerAcquire()).isEqualTo(15);
            AuditEvent audit = kit.audits.get(kit.audits.size() - 1);
            assertThat(audit.action()).isEqualTo("EMOTICON_SETTINGS_UPDATE");
            List<String> changed = ((Map<?, ?>) audit.detail().get("after")).keySet().stream().map(String::valueOf).toList();
            assertThat(changed).containsExactlyInAnyOrder("rewardPerAcquire", "rewardDailyCap");
            // 바뀐 칸이 없으면 쓰지 않는다
            int audits = kit.audits.size();
            kit.settings.save(kit.operator, null, new EmoticonSettingsSaveRequest(15, 600, 8, 40, 3, (Integer) was.get("fee_rate_percent"),
                    (Integer) was.get("settlement_day"), (Integer) was.get("payout_minimum"), (Integer) was.get("refund_days"), (Boolean) was.get("paid_sales_enabled"),
                    kit.settings.current().priceTiers()));
            assertThat(kit.audits).hasSize(audits);
        } finally {
            kit.restoreSettings(was);
        }
    }

    @Test
    void 설정의_팩당_개수가_심사_요청에_바로_반영된다() {
        long creator = kit.member();
        Map<String, Object> was = kit.jdbc.queryForMap("SELECT item_min FROM emoticon_settings WHERE id = 1");
        kit.jdbc.update("UPDATE emoticon_settings SET item_min = 10 WHERE id = 1");
        try {
            assertThatThrownBy(() -> kit.submitNew(creator, "개수 시험")).isInstanceOfSatisfying(ValidationException.class,
                    e -> assertThat(e.getFields()).containsKey("emoticons"));
        } finally {
            kit.jdbc.update("UPDATE emoticon_settings SET item_min = ? WHERE id = 1", was.get("item_min"));
        }
        assertThat(kit.submitNew(creator, "개수 시험").status()).isEqualTo("PENDING");
    }
}
