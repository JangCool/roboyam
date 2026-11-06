package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.avatarupload.exception.AvatarUploadNotFoundException;
import com.community.pointshop.dto.response.MyItemView;
import com.community.pointshop.dto.response.PointShopItemView;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.entity.UploadTicketUse;
import com.community.pointshop.exception.PointShopRefundNotAllowedException;
import com.community.pointshop.exception.UploadTicketRequiredException;
import com.community.withdrawal.repository.master.WithdrawalEraseStore;
import com.community.zconfiguration.exception.CommonException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/**
 * 올리기 변경권(commerce-motion-avatar-contract 규칙 0 — community 「변경권」 방식): 신청마다 한 장 <b>예약</b>(동시 신청에도 한 장) → 승인 <b>사용</b> · 반려·본인 취소 <b>반환</b> ·
 * 새 신청이 대체하면 같은 장을 <b>넘겨받음</b> · 적용 뒤 해제·지우기는 반환 없음 · 예약된 장은 셀프·예외 환불 불가(풀리면 다시) · 없으면 파일 처리 전 409 + 장 수·값 · 품목·상점 「변경권」 탭.
 * 아바타 올리기 신청(shared {@code AvatarUploadService}·{@code AvatarUploadReviewService})으로 확인한다 — 닉네임 이미지 쪽은 api·admin HTTP 시험. 실제 PostgreSQL.
 */
class UploadTicketTest extends PointShopTestBase {

    private long avatarTicket() {
        return kit.seedItem("avatar-upload-ticket");
    }

    private long nicknameTicket() {
        return kit.seedItem("nickname-image-ticket");
    }

    private Map<String, Object> ticketRow(long userItemId) {
        return kit.userItemRow(userItemId);
    }

    private MyItemView myItem(long member, long userItemId) {
        return kit.userItems.myItems(member).items().stream().filter(v -> v.id().equals(Long.toString(userItemId))).findFirst().orElseThrow();
    }

    @Test
    void 신청하면_한_장이_그_신청에_예약되고_쓸_수_있는_장에서_빠지며_환불할_수_없다() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long ticket = kit.buyItem(member, avatarTicket());
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isEqualTo(1);
        assertThat(myItem(member, ticket).refundable()).as("예약 전에는 소모형 환불 규칙 그대로").isTrue();

        long request = kit.submitUpload(member, false);

        assertThat(ticketRow(ticket)).containsEntry("status", "OWNED").containsEntry("ref_type", "AVATAR_UPLOAD").containsEntry("ref_id", request);
        assertThat(ticketRow(ticket).get("reserved_at")).isNotNull();
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isZero();
        MyItemView view = myItem(member, ticket);
        assertThat(view.reserved()).isTrue();
        assertThat(view.refundable()).isFalse();
        assertThat(view.kind()).isEqualTo(PointShopItemKind.AVATAR_UPLOAD_TICKET);
        PointShopItemView item = kit.catalog.memberItem(member, avatarTicket());
        assertThat(item.mine().count()).as("상점의 장 수에서도 빠진다").isZero();
        assertThatThrownBy(() -> kit.refundSelf(member, ticket)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("blockedBy", "RESERVED"));
        assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.refundByOperator(kit.purchaseOf(ticket), "예약된 장 예외 환불 시험", kit.operator, null)))
                .as("예외 환불도 막는다 — 신청을 반려하면 풀린다").isInstanceOf(PointShopRefundNotAllowedException.class);
        assertThat(kit.uploadTickets.reserved(UploadTicketUse.AVATAR_UPLOAD, request)).isTrue();
    }

    @Test
    void 장이_없으면_409_에_장수와_값을_싣고_신청은_생기지_않는다() {
        long member = kit.newMember();
        assertThatThrownBy(() -> kit.inTx(() -> {
            kit.avatarUploads.requireTicket(member);
            return null;
        })).isInstanceOf(UploadTicketRequiredException.class).satisfies(e -> {
            CommonException ce = (CommonException) e;
            assertThat(ce.getErrorCode()).isEqualTo("AVATAR_UPLOAD_TICKET_REQUIRED");
            assertThat(ce.getExtras()).containsEntry("ticketCount", 0).containsEntry("ticketPrice", 3000);
        });
        assertThatThrownBy(() -> kit.submitUpload(member, false)).as("파일을 들고 와도 트랜잭션에서 다시 막는다").isInstanceOf(UploadTicketRequiredException.class);
        assertThat(kit.count("SELECT count(*) FROM avatar_upload_requests WHERE user_id = ?", member)).isZero();
        assertThat(kit.uploadTickets.required(member, UploadTicketUse.NICKNAME_IMAGE).getErrorCode()).isEqualTo("NICKNAME_IMAGE_TICKET_REQUIRED");
        assertThat(kit.uploadTickets.price(UploadTicketUse.NICKNAME_IMAGE)).isEqualTo(2000);
    }

    @Test
    void 닉네임_이미지_변경권과_아바타_변경권은_서로_쓰이지_않는다() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        kit.buyItem(member, nicknameTicket());
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.NICKNAME_IMAGE)).isEqualTo(1);
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isZero();
        assertThatThrownBy(() -> kit.submitUpload(member, false)).isInstanceOf(UploadTicketRequiredException.class);
        assertThat(kit.tickets.count(member)).as("닉네임 변경권 수에도 섞이지 않는다").isZero();
    }

    @Test
    void 장이_하나면_동시에_두_신청에_예약해도_하나만_된다() throws Exception {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long ticket = kit.buyItem(member, avatarTicket());
        long first = kit.ids.nextId();
        long second = kit.ids.nextId();
        List<Callable<String>> jobs = new ArrayList<>();
        for (long request : List.of(first, second)) {
            jobs.add(() -> {
                try {
                    return "RESERVED " + kit.inTx(() -> kit.uploadTickets.reserve(member, UploadTicketUse.AVATAR_UPLOAD, request));
                } catch (UploadTicketRequiredException e) {
                    return e.getErrorCode();
                }
            });
        }
        assertThat(PointShopTestKit.runTogether(jobs)).containsExactlyInAnyOrder("RESERVED " + ticket, "AVATAR_UPLOAD_TICKET_REQUIRED");
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ? AND reserved_at IS NOT NULL", member)).isEqualTo(1);
    }

    @Test
    void 동시_신청_둘은_대기_하나와_예약_한_장으로_끝난다_대체는_같은_장을_넘겨받는다() throws Exception {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long a = kit.buyItem(member, avatarTicket());
        long b = kit.buyItem(member, avatarTicket());
        List<Callable<Long>> jobs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            jobs.add(() -> kit.submitUpload(member, false));
        }
        List<Long> requests = PointShopTestKit.runTogether(jobs);
        assertThat(kit.count("SELECT count(*) FROM avatar_upload_requests WHERE user_id = ? AND status = 'PENDING'", member)).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM avatar_upload_requests WHERE user_id = ? AND status = 'CANCELED'", member)).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ? AND reserved_at IS NOT NULL", member)).as("예약된 장은 한 장").isEqualTo(1);
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isEqualTo(1);
        long pending = kit.jdbc.queryForObject("SELECT id FROM avatar_upload_requests WHERE user_id = ? AND status = 'PENDING'", Long.class, member);
        assertThat(requests).contains(pending);
        assertThat(kit.uploadTickets.reserved(UploadTicketUse.AVATAR_UPLOAD, pending)).as("예약은 남은 대기 신청에").isTrue();
        assertThat(java.util.Arrays.asList(ticketRow(a).get("ref_id"), ticketRow(b).get("ref_id"))).contains(pending);
    }

    @Test
    void 새_신청이_대기_신청을_대체하면_예약된_장을_그대로_넘겨받아_한_장_더_들지_않는다() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long ticket = kit.buyItem(member, avatarTicket());
        long first = kit.submitUpload(member, false);
        kit.clock.advance(Duration.ofSeconds(11));
        long second = kit.submitUpload(member, true);

        assertThat(kit.uploadStatus(first)).isEqualTo("CANCELED");
        assertThat(kit.uploadStatus(second)).isEqualTo("PENDING");
        assertThat(ticketRow(ticket)).containsEntry("ref_id", second).containsEntry("status", "OWNED");
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isZero();
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ?", member)).as("장을 더 사지 않았다").isEqualTo(1);
        assertThat(kit.jdbc.queryForObject("SELECT image_key FROM avatar_upload_requests WHERE id = ?", String.class, first)).as("대체된 신청의 파일은 지운다").isNull();
    }

    @Test
    void 본인_취소와_반려는_같은_장을_돌려주고_다시_환불할_수_있다() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long ticket = kit.buyItem(member, avatarTicket());

        long first = kit.submitUpload(member, false);
        kit.inTx(() -> {
            kit.avatarUploads.cancel(member);
            return null;
        });
        assertThat(kit.uploadStatus(first)).isEqualTo("CANCELED");
        assertThat(ticketRow(ticket).get("reserved_at")).isNull();
        assertThat(ticketRow(ticket).get("ref_id")).isNull();
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isEqualTo(1);

        kit.clock.advance(Duration.ofSeconds(11));
        long second = kit.submitUpload(member, false);
        assertThat(ticketRow(ticket)).containsEntry("ref_id", second);
        kit.rejectUpload(Long.toString(second), "다른 사람이 만든 그림이에요.");
        assertThat(kit.uploadStatus(second)).isEqualTo("REJECTED");
        assertThat(ticketRow(ticket).get("reserved_at")).isNull();
        assertThat(ticketRow(ticket)).containsEntry("status", "OWNED");
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isEqualTo(1);
        assertThat(myItem(member, ticket).refundable()).as("돌아온 장은 소모형 환불 규칙 그대로 — 기한 안 셀프 환불").isTrue();
        assertThat(kit.refundSelf(member, ticket)).isEqualTo(3000);

        assertThatThrownBy(() -> kit.inTx(() -> {
            kit.avatarUploads.cancel(member);
            return null;
        })).as("대기 신청이 없으면 404").isInstanceOf(CommonException.class).extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo("AVATAR_UPLOAD_REQUEST_NOT_FOUND");
    }

    @Test
    void 승인되면_예약된_장을_쓴_것이고_해제_지우기는_돌려주지_않는다() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long ticket = kit.buyItem(member, avatarTicket());
        long first = kit.submitUpload(member, false);
        kit.approveUpload(Long.toString(first));
        assertThat(ticketRow(ticket)).containsEntry("status", "USED").containsEntry("ref_id", first);
        assertThat(ticketRow(ticket).get("reserved_at")).isNull();
        assertThat(ticketRow(ticket).get("used_at")).isNotNull();
        assertThat(myItem(member, ticket).refundable()).isFalse();
        assertThatThrownBy(() -> kit.refundSelf(member, ticket)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("blockedBy", "USED"));
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET ref_id = 1 WHERE id = ?", ticket)).as("쓴 장의 신청은 바꾸지 않는다(트리거)")
                .hasMessageContaining("ref of a used ticket is kept");

        kit.revokeUpload(Long.toString(first), "신고가 많아 내렸어요.");
        assertThat(ticketRow(ticket)).as("해제는 반환 없음").containsEntry("status", "USED");

        long second = kit.buyItem(member, avatarTicket());
        kit.clock.advance(Duration.ofSeconds(11));
        long request = kit.submitUpload(member, false);
        kit.approveUpload(Long.toString(request));
        kit.inTx(() -> {
            kit.avatarUploads.remove(member);
            return null;
        });
        assertThat(kit.uploadStatus(request)).isEqualTo("REMOVED");
        assertThat(ticketRow(second)).as("지우기도 반환 없음").containsEntry("status", "USED");
        assertThat(kit.uploadTickets.count(member, UploadTicketUse.AVATAR_UPLOAD)).isZero();
        assertThatThrownBy(() -> kit.inTx(() -> {
            kit.avatarUploads.remove(member);
            return null;
        })).isInstanceOf(AvatarUploadNotFoundException.class);
    }

    @Test
    void 예약_칸_CHECK_신청_하나에_장_하나_예약은_가진_장만() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long a = kit.buyItem(member, avatarTicket());
        long b = kit.buyItem(member, avatarTicket());
        long color = kit.buyItem(member, kit.color(100, "#123456"));
        kit.jdbc.update("UPDATE user_items SET reserved_at = now(), ref_type = 'AVATAR_UPLOAD', ref_id = 77 WHERE id = ?", a);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET reserved_at = now(), ref_type = 'AVATAR_UPLOAD', ref_id = 77 WHERE id = ?", b))
                .as("신청 하나에 장 하나").hasMessageContaining("ux_user_items_ref");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET ref_type = 'AVATAR_UPLOAD', ref_id = 78 WHERE id = ?", b)).as("이어진 가진 장은 예약 중")
                .hasMessageContaining("ck_user_items_reserved");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET reserved_at = now(), ref_type = 'AVATAR_UPLOAD', ref_id = 79 WHERE id = ?", color))
                .as("소모형만").hasMessageContaining("ck_user_items_ref");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE user_items SET status = 'REVOKED', revoked_at = now() WHERE id = ?", a)).as("예약된 채로 회수 불가")
                .hasMessageContaining("ck_user_items_reserved");
        kit.jdbc.update("UPDATE user_items SET reserved_at = NULL, ref_type = NULL, ref_id = NULL WHERE id = ?", a);
    }

    @Test
    void 탈퇴_정리는_예약된_장도_회수한다() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        long ticket = kit.buyItem(member, avatarTicket());
        kit.submitUpload(member, false);
        int revoked = kit.inTx(() -> new WithdrawalEraseStore(kit.dataSource).revokeItems(member, kit.clock.instant()));
        assertThat(revoked).isEqualTo(1);
        assertThat(ticketRow(ticket)).containsEntry("status", "REVOKED");
        assertThat(ticketRow(ticket).get("reserved_at")).isNull();
    }

    @Test
    void 상점_변경권_탭은_세_변경권을_함께_주고_관리자는_두_변경권을_소모형으로만_만든다() {
        long member = kit.newMember();
        List<PointShopItemKind> kinds = kit.catalog.memberList(member, PointShopItemKind.NICKNAME_CHANGE_TICKET).items().stream().map(PointShopItemView::kind).distinct()
                .toList();
        assertThat(kinds).contains(PointShopItemKind.NICKNAME_CHANGE_TICKET, PointShopItemKind.NICKNAME_IMAGE_TICKET, PointShopItemKind.AVATAR_UPLOAD_TICKET)
                .allMatch(PointShopItemKind::ticket);
        assertThat(kit.catalog.memberList(member, PointShopItemKind.AVATAR_UPLOAD_TICKET).items()).allMatch(v -> v.kind() == PointShopItemKind.AVATAR_UPLOAD_TICKET);

        var created = kit.create(PointShopTestKit.request(kit.code("it"), PointShopItemKind.NICKNAME_IMAGE_TICKET, PointShopUsageType.CONSUMABLE, 1500, null, null, null, null,
                null, null, true));
        assertThat(created.kind()).isEqualTo(PointShopItemKind.NICKNAME_IMAGE_TICKET);
        kit.create(PointShopTestKit.request(kit.code("at"), PointShopItemKind.AVATAR_UPLOAD_TICKET, PointShopUsageType.CONSUMABLE, 2500, null, null, null, null, null, null,
                false));
        assertThatThrownBy(() -> kit.create(PointShopTestKit.request(kit.code("ax"), PointShopItemKind.AVATAR_UPLOAD_TICKET, PointShopUsageType.PERMANENT, 2500, null, null,
                null, null, null, null, true))).isInstanceOf(CommonException.class)
                .satisfies(e -> assertThat(((CommonException) e).getFields()).containsOnlyKeys("usageType"));
        assertThat(kit.uploadTickets.price(UploadTicketUse.NICKNAME_IMAGE)).as("파는 변경권 품목 중 순서 → ID 첫째").isEqualTo(1500);
        kit.jdbc.update("UPDATE point_shop_items SET active = false WHERE id = ?", Long.parseLong(created.id()));
        assertThat(kit.uploadTickets.price(UploadTicketUse.NICKNAME_IMAGE)).as("그 품목을 멈추면 다음 차례(시드 2,000P)").isEqualTo(2000);
    }
}
