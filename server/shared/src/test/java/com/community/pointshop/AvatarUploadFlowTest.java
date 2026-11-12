package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.avatarupload.dto.response.AdminAvatarUploadPage;
import com.community.avatarupload.dto.response.AdminAvatarUploadView;
import com.community.avatarupload.dto.response.MyAvatarUploadView;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.dto.response.AvatarCatalogView;
import com.community.pointshop.dto.response.AvatarItemView;
import com.community.pointshop.service.AvatarCatalog;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 아바타 올리기(commerce-motion-avatar-contract 규칙 0-1·0-2, 「API 계약」) — 신청 조건(이메일 인증·정지 아님 + 변경권뿐), 승인 = 「내가 올린 아바타」 한 장 + 바로 프로필 아바타,
 * 다른 아바타로 바꿨다 다시 쓰기(변경권 없음), 해제·지우기 = 마지막 정지 아바타로, 심사 상태 전이(이미 처리 409 · 없음 404 · 사유 400), 감사·알림, 목록(변경권 칸·지금 그림·쓰는 중·쉬게 함),
 * 카탈로그 {@code upload-mine} 줄·작성자 표시. 실제 PostgreSQL.
 */
class AvatarUploadFlowTest extends PointShopTestBase {

    private long memberWithTicket() {
        long member = kit.newMember();
        kit.grant(member, 10_000);
        kit.buyItem(member, kit.seedItem("avatar-upload-ticket"));
        return member;
    }

    private static String code(Runnable work) {
        try {
            work.run();
        } catch (CommonException e) {
            return e.getErrorCode();
        }
        return null;
    }

    private AdminAvatarUploadView row(long requestId, String status) {
        AdminAvatarUploadPage page = kit.avatarReviews.list(status, null, "1", "100");
        return page.items().stream().filter(v -> v.id().equals(Long.toString(requestId))).findFirst().orElse(null);
    }

    @Test
    void 신청_조건은_이메일_인증과_정지_아님_그리고_변경권뿐이다() {
        long unverified = kit.newUnverifiedMember();
        assertThat(code(() -> kit.avatarUploads.guard(unverified))).isEqualTo("EMAIL_NOT_VERIFIED");
        long suspended = kit.newMember();
        kit.suspend(suspended);
        assertThat(code(() -> kit.avatarUploads.guard(suspended))).isEqualTo("ACCOUNT_RESTRICTED");

        long fresh = memberWithTicket(); // 가입한 날·등급 기본 — 닉네임 이미지의 관리자 신청 조건은 보지 않는다
        kit.avatarUploads.guard(fresh);
        kit.avatarUploads.requireTicket(fresh);
        long request = kit.submitUpload(fresh, false);
        assertThat(kit.uploadStatus(request)).isEqualTo("PENDING");
        assertThat(kit.operatorNotices).contains(AdminNotificationEvent.AVATAR_UPLOAD_PENDING);
        MyAvatarUploadView status = kit.avatarUploads.status(fresh);
        assertThat(status.ticketCount()).isZero();
        assertThat(status.ticketPrice()).isEqualTo(3000);
        assertThat(status.ticketItemId()).isEqualTo(Long.toString(kit.seedItem("avatar-upload-ticket")));
        assertThat(status.reserved()).isTrue();
        assertThat(status.request().status()).isEqualTo("PENDING");
        assertThat(status.request().url()).startsWith("/media/avatar-upload/");
        assertThat(status.current()).isNull();
        assertThat(status.inUse()).isFalse();
        assertThat(status.limits().maxBytes()).isEqualTo(300 * 1024);
        assertThat(status.limits().minSize()).isEqualTo(96);
        assertThat(status.limits().maxGifFrames()).isEqualTo(30);
    }

    @Test
    void 승인하면_내가_올린_아바타가_되고_바로_프로필_아바타로_쓴다_움직이는_GIF_는_정지_그림이_같이_나간다() {
        long member = memberWithTicket();
        long request = kit.submitUpload(member, true);
        AdminAvatarUploadView approved = kit.approveUpload(Long.toString(request));
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.ticket()).isEqualTo("USED");
        assertThat(approved.current()).isTrue();
        assertThat(approved.inUse()).isTrue();
        assertThat(approved.stillUrl()).endsWith("/still.png");
        assertThat(kit.profileImage(member)).isEqualTo("avatar:upload-mine");

        MyAvatarUploadView status = kit.avatarUploads.status(member);
        assertThat(status.current().animated()).isTrue();
        assertThat(status.current().stillUrl()).endsWith("/still.png");
        assertThat(status.inUse()).isTrue();
        assertThat(status.request()).as("승인되면 대기·반려 신청 칸은 비운다").isNull();

        AuthorLook look = kit.decorations.look(member);
        assertThat(look.avatarUrl()).isEqualTo(status.current().url());
        assertThat(look.avatarStillUrl()).isEqualTo(status.current().stillUrl());

        AvatarCatalogView catalog = kit.profileAvatars.catalog(member);
        assertThat(catalog.selectedId()).isEqualTo(AvatarCatalog.UPLOAD_ID);
        AvatarItemView mine = catalog.items().stream().filter(a -> a.id().equals(AvatarCatalog.UPLOAD_ID)).findFirst().orElseThrow();
        assertThat(mine.category()).isEqualTo(AvatarCatalog.UPLOAD_CATEGORY);
        assertThat(mine.itemId()).as("상점에 없다").isNull();
        assertThat(mine.owned()).isTrue();
        assertThat(mine.selected()).isTrue();
        assertThat(mine.animated()).isTrue();
        assertThat(mine.refundable()).isFalse();
        int firstPaid = catalog.categories().stream().map(c -> c.free()).toList().indexOf(false);
        assertThat(catalog.categories().get(firstPaid).id()).as("무료 카테고리 바로 뒤").isEqualTo(AvatarCatalog.UPLOAD_CATEGORY);
        assertThat(kit.profileAvatars.catalog(null).items()).as("비로그인 목록에는 없다").noneMatch(a -> a.id().equals(AvatarCatalog.UPLOAD_ID));

        assertThat(kit.audits.stream().map(e -> e.action())).contains(AuditActions.AVATAR_UPLOAD_APPROVE);
        assertThat(kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'AVATAR_UPLOAD_REVIEWED'", member)).isEqualTo(1);
    }

    @Test
    void 새로_승인되면_앞_그림은_바뀌고_내가_올린_아바타는_한_장이다_승인_탭에는_지금_그림이_아닌_것으로_보인다() {
        long member = memberWithTicket();
        long first = kit.submitUpload(member, false);
        kit.approveUpload(Long.toString(first));
        kit.buyItem(member, kit.seedItem("avatar-upload-ticket"));
        kit.clock.advance(Duration.ofSeconds(11));
        long second = kit.submitUpload(member, false);
        assertThat(row(second, "PENDING").ticket()).isEqualTo("RESERVED");
        kit.approveUpload(Long.toString(second));

        assertThat(kit.uploadStatus(first)).isEqualTo("REMOVED");
        assertThat(kit.count("SELECT count(*) FROM avatar_upload_requests WHERE user_id = ? AND status = 'APPROVED'", member)).isEqualTo(1);
        AdminAvatarUploadView old = row(first, "APPROVED");
        assertThat(old.status()).isEqualTo("APPROVED");
        assertThat(old.current()).isFalse();
        assertThat(old.inUse()).isFalse();
        assertThat(old.url()).as("바뀐 그림의 파일은 지운다").isNull();
        assertThat(row(second, "APPROVED").current()).isTrue();
        assertThat(code(() -> kit.revokeUpload(Long.toString(first), "바뀐 그림"))).as("지금 그림이 아니면 해제할 것이 없다")
                .isEqualTo("AVATAR_UPLOAD_NOT_ACTIVE");
    }

    @Test
    void 다른_아바타로_바꿨다가_다시_쓸_때는_변경권이_들지_않고_정지_그림이면_마지막_정지_아바타가_된다() {
        long member = memberWithTicket();
        long request = kit.submitUpload(member, false);
        kit.approveUpload(Long.toString(request));
        assertThat(kit.lastStill(member)).isEqualTo(AvatarCatalog.UPLOAD_ID);
        kit.inTx(() -> kit.profileAvatars.select(member, "hobby-gamer"));
        assertThat(kit.avatarUploads.status(member).inUse()).isFalse();
        assertThat(row(request, "APPROVED").inUse()).isFalse();
        assertThat(row(request, "APPROVED").current()).isTrue();

        int before = kit.count("SELECT count(*) FROM user_items WHERE user_id = ?", member);
        var selected = kit.inTx(() -> kit.profileAvatars.select(member, AvatarCatalog.UPLOAD_ID));
        assertThat(selected.avatarId()).isEqualTo(AvatarCatalog.UPLOAD_ID);
        assertThat(selected.animated()).isFalse();
        assertThat(selected.avatarStillUrl()).isNull();
        assertThat(kit.profileImage(member)).isEqualTo("avatar:upload-mine");
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ?", member)).isEqualTo(before);

        long nobody = kit.newMember();
        assertThat(code(() -> kit.inTx(() -> kit.profileAvatars.select(nobody, AvatarCatalog.UPLOAD_ID)))).as("승인된 그림이 없으면")
                .isEqualTo("AVATAR_NOT_OWNED");
    }

    @Test
    void 쓰는_중인_그림을_해제하거나_지우면_마지막_정지_아바타로_돌아간다() {
        long member = memberWithTicket();
        kit.inTx(() -> kit.profileAvatars.select(member, "hobby-gamer"));
        long request = kit.submitUpload(member, true); // 움직이는 GIF — 마지막 정지 아바타는 그대로(hobby-gamer)
        kit.approveUpload(Long.toString(request));
        assertThat(kit.lastStill(member)).isEqualTo("hobby-gamer");
        AdminAvatarUploadView revoked = kit.revokeUpload(Long.toString(request), "깜빡임이 너무 빨라요.");
        assertThat(revoked.status()).isEqualTo("REVOKED");
        assertThat(revoked.revokeReason()).isEqualTo("깜빡임이 너무 빨라요.");
        assertThat(revoked.url()).isNull();
        assertThat(kit.profileImage(member)).isEqualTo("avatar:hobby-gamer");
        assertThat(kit.audits.stream().map(e -> e.action())).contains(AuditActions.AVATAR_UPLOAD_REVOKE);

        long other = memberWithTicket();
        long mine = kit.submitUpload(other, false);
        kit.approveUpload(Long.toString(mine));
        kit.inTx(() -> {
            kit.avatarUploads.remove(other);
            return null;
        });
        assertThat(kit.profileImage(other)).as("마지막 정지 아바타가 지운 그림이면 기본").isEqualTo("avatar:basic-default");
        assertThat(kit.decorations.look(other).avatarUrl()).isEqualTo("/avatars/irobit/basic-default.svg");
    }

    @Test
    void 심사는_대기만_승인_반려하고_이미_처리했으면_409_없으면_404_사유는_1에서_200자() {
        long member = memberWithTicket();
        long request = kit.submitUpload(member, false);
        assertThat(code(() -> kit.rejectUpload(Long.toString(request), "  "))).isEqualTo("VALIDATION");
        assertThatThrownBy(() -> kit.rejectUpload(Long.toString(request), "가".repeat(201))).isInstanceOf(ValidationException.class);
        AdminAvatarUploadView rejected = kit.rejectUpload(Long.toString(request), "직접 만든 그림으로 다시 신청해 주세요.");
        assertThat(rejected.ticket()).isEqualTo("RETURNED");
        assertThat(rejected.rejectReason()).isEqualTo("직접 만든 그림으로 다시 신청해 주세요.");
        assertThat(rejected.reviewer().id()).isEqualTo(Long.toString(kit.operator));
        assertThat(code(() -> kit.approveUpload(Long.toString(request)))).isEqualTo("AVATAR_UPLOAD_NOT_PENDING");
        assertThat(code(() -> kit.rejectUpload(Long.toString(request), "다시"))).isEqualTo("AVATAR_UPLOAD_NOT_PENDING");
        assertThat(code(() -> kit.revokeUpload(Long.toString(request), "다시"))).isEqualTo("AVATAR_UPLOAD_NOT_ACTIVE");
        assertThat(code(() -> kit.approveUpload("999999999"))).isEqualTo("AVATAR_UPLOAD_NOT_FOUND");
        assertThat(code(() -> kit.approveUpload("abc"))).isEqualTo("AVATAR_UPLOAD_NOT_FOUND");
        assertThat(kit.audits.stream().map(e -> e.action())).contains(AuditActions.AVATAR_UPLOAD_REJECT);
        assertThat(kit.jdbc.queryForObject("SELECT payload FROM member_notifications WHERE user_id = ? AND type = 'AVATAR_UPLOAD_REVIEWED' ORDER BY id DESC LIMIT 1",
                String.class, member)).contains("REJECTED").contains("직접 만든 그림으로");
        assertThat(kit.avatarUploads.status(member).request().status()).as("반려 신청은 회원 화면에 사유와 함께").isEqualTo("REJECTED");

        assertThat(code(() -> kit.avatarReviews.list("NOPE", null, null, null))).isEqualTo("VALIDATION");
        assertThat(code(() -> kit.avatarReviews.list(null, null, "0", null))).isEqualTo("VALIDATION");
        assertThat(code(() -> kit.avatarReviews.list(null, null, null, "101"))).isEqualTo("VALIDATION");
    }

    @Test
    void 대기_중인_움직이는_GIF_는_움직이는_닉네임_이미지를_쓰는_회원이면_쉬게_한다고_알린다() {
        long member = memberWithTicket();
        long request = kit.submitUpload(member, true);
        assertThat(row(request, "PENDING").pausesNicknameImage()).isFalse();
        long nickname = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO nickname_image_requests (id, user_id, status, image_key, still_key, content_type, bytes, width, height, frames, created_at) "
                + "VALUES (?, ?, 'APPROVED', 'nickname-image/x/image.gif', 'nickname-image/x/still.png', 'image/gif', 1000, 240, 60, 5, now())", nickname, member);
        AdminAvatarUploadPage page = kit.avatarReviews.list("PENDING", null, null, "100");
        assertThat(page.pendingCount()).isGreaterThanOrEqualTo(1);
        assertThat(row(request, "PENDING").pausesNicknameImage()).isTrue();
        assertThat(row(request, "PENDING").member().id()).isEqualTo(Long.toString(member));
        List<AdminAvatarUploadView> byName = kit.avatarReviews.list("PENDING", kit.jdbc.queryForObject("SELECT nickname FROM profiles WHERE user_id = ?", String.class, member),
                null, null).items();
        assertThat(byName).extracting(AdminAvatarUploadView::id).containsExactly(Long.toString(request));
        kit.jdbc.update("DELETE FROM nickname_image_requests WHERE id = ?", nickname);
    }
}
