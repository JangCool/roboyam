package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.avatarupload.service.AvatarUploadService;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.permission.entity.Permission;
import com.community.pointshop.service.ProfileAvatarService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 아바타 올리기 심사 API(관리자 — commerce-motion-avatar-contract 「API 계약」 admin) — 권한(닉네임 이미지와 같은 {@code nickname-image.read}·{@code .manage}), 목록 모양(쪽 1부터·
 * {@code pendingCount}·변경권 칸), 승인(변경권 사용 · 내가 올린 아바타 + 프로필 아바타 · 감사 일반 운영 · 회원 알림), 반려(사유 · 반환), 해제(지금 그림만 · 쓰는 중이면 마지막 정지 아바타),
 * 이미 처리 409 · 없음 404 · 사유 400. 실제 필터 체인·세션·PostgreSQL. 대기 신청은 shared {@link AvatarUploadService} 로 만든다(파일 키는 시험용 — 저장소에 쓰지 않는다).
 */
class AvatarUploadAdminHttpTest extends PointShopAdminHttpTestBase {

    private static final String UPLOADS = "/api/admin/avatar-uploads";

    @Autowired
    private AvatarUploadService uploads;
    @Autowired
    private ProfileAvatarService profileAvatars;

    private ResultActions act(Cookie session, Object id, String action, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return mvc.perform(withCsrf(post(UPLOADS + "/" + id + "/" + action), session).header("Accept-Language", "ko").content(body));
    }

    private long ticketItem() {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'avatar-upload-ticket'", Long.class);
    }

    /** 아바타 변경권 한 장을 사서 대기 신청을 낸 회원 — {회원, 신청} */
    private long[] pending(boolean animated) {
        long member = buyer(5_000);
        buy(member, ticketItem());
        long request = ids.nextId();
        String folder = "avatar-upload/t-" + request;
        tx.execute(s -> uploads.submit(member, new AvatarUploadService.NewUpload(request, folder + "/image." + (animated ? "gif" : "png"),
                animated ? folder + "/still.png" : null, animated ? "image/gif" : "image/png", 2000, 200, 200, animated ? 8 : 1)));
        return new long[] {member, request};
    }

    @Test
    void 대기_신청에_예약된_변경권은_구매_내역에서_셀프_환불_불가_RESERVED_이고_예외_환불도_409() throws Exception {
        long[] p = pending(false);
        Cookie session = login(operatorWith(Permission.POINTSHOP_READ, Permission.POINTSHOP_REFUND));
        String body = mvc.perform(get(BASE + "/purchases").param("q", Long.toString(p[0])).cookie(session)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.data.items[?(@.kind == 'AVATAR_UPLOAD_TICKET')].selfRefund.blockedBy")).containsExactly("RESERVED");
        String purchaseId = JsonPath.<List<String>>read(body, "$.data.items[?(@.kind == 'AVATAR_UPLOAD_TICKET')].id").get(0);
        mvc.perform(withCsrf(post(BASE + "/purchases/" + purchaseId + "/refund"), session).header("Accept-Language", "ko").content("{\"reason\":\"예약된 장 환불 시험\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_SHOP_REFUND_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.blockedBy").value("RESERVED"));
    }

    @Test
    void 권한은_닉네임_이미지_심사와_같다_조회_read_처리_manage() throws Exception {
        long[] p = pending(false);
        Cookie nobody = login(operatorWith(Permission.MEMBER_READ));
        mvc.perform(get(UPLOADS).cookie(nobody)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        Cookie reader = login(operatorWith(Permission.NICKNAME_IMAGE_READ));
        mvc.perform(get(UPLOADS).cookie(reader)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        act(reader, p[1], "approve", null).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        act(reader, p[1], "reject", "권한 없이 반려").andExpect(status().isForbidden());
        act(reader, p[1], "revoke", "권한 없이 해제").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM avatar_upload_requests WHERE id = ?", String.class, p[1])).isEqualTo("PENDING");
    }

    @Test
    void 승인하면_변경권을_쓰고_내가_올린_아바타가_되어_바로_프로필_아바타_감사_일반_운영_회원_알림() throws Exception {
        long[] p = pending(true);
        Cookie session = login(operatorWith(Permission.NICKNAME_IMAGE_READ, Permission.NICKNAME_IMAGE_MANAGE));
        String list = mvc.perform(get(UPLOADS).param("status", "PENDING").param("size", "100").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(list, "$.data.page")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(list, "$.data.pendingCount")).isGreaterThanOrEqualTo(1);
        String id = Long.toString(p[1]);
        assertThat(JsonPath.<List<String>>read(list, "$.data.items[?(@.id == '" + id + "')].ticket")).containsExactly("RESERVED");
        assertThat(JsonPath.<List<Integer>>read(list, "$.data.items[?(@.id == '" + id + "')].frames")).containsExactly(8);
        assertThat(JsonPath.<List<String>>read(list, "$.data.items[?(@.id == '" + id + "')].member.id")).containsExactly(Long.toString(p[0]));

        act(session, p[1], "approve", null).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("APPROVED")).andExpect(jsonPath("$.data.ticket").value("USED"))
                .andExpect(jsonPath("$.data.current").value(true)).andExpect(jsonPath("$.data.inUse").value(true))
                .andExpect(jsonPath("$.data.stillUrl").value(org.hamcrest.Matchers.endsWith("/still.png")));
        assertThat(jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, p[0])).isEqualTo("avatar:upload-mine");
        assertThat(profileAvatars.look(p[0]).animated()).isTrue();
        assertThat(count("SELECT count(*) FROM user_items WHERE user_id = ? AND ref_id = ? AND status = 'USED'", p[0], p[1])).isEqualTo(1);
        Map<String, Object> audit = lastAudit(AuditActions.AVATAR_UPLOAD_APPROVE, id);
        assertThat(audit).containsEntry("target_type", "AVATAR_UPLOAD").containsEntry("retention_class", "GENERAL");
        assertThat(count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'AVATAR_UPLOAD_REVIEWED'", p[0])).isEqualTo(1);

        act(session, p[1], "approve", null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_NOT_PENDING"));
        act(session, p[1], "reject", "이미 승인").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_NOT_PENDING"));
        assertThat(AdminNotificationEvent.AVATAR_UPLOAD_PENDING.requiredPermission()).isEqualTo(Permission.NICKNAME_IMAGE_MANAGE);
    }

    @Test
    void 반려는_사유와_함께_변경권을_돌려주고_해제는_지금_그림만_쓰는_중이면_마지막_정지_아바타로() throws Exception {
        Cookie session = login(operatorWith(Permission.NICKNAME_IMAGE_READ, Permission.NICKNAME_IMAGE_MANAGE));
        long[] r = pending(false);
        act(session, r[1], "reject", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").exists());
        act(session, r[1], "reject", "가".repeat(201)).andExpect(status().isBadRequest());
        act(session, r[1], "reject", "직접 만든 그림으로 다시 신청해 주세요.").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.ticket").value("RETURNED")).andExpect(jsonPath("$.data.rejectReason").value("직접 만든 그림으로 다시 신청해 주세요."));
        assertThat(count("SELECT count(*) FROM user_items WHERE user_id = ? AND status = 'OWNED' AND reserved_at IS NULL AND ref_id IS NULL", r[0])).isEqualTo(1);
        assertThat(lastAudit(AuditActions.AVATAR_UPLOAD_REJECT, Long.toString(r[1]))).containsEntry("retention_class", "GENERAL");
        act(session, r[1], "revoke", "반려된 것").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_NOT_ACTIVE"));

        long[] a = pending(true);
        jdbc.update("UPDATE profiles SET last_still_avatar = 'hobby-gamer' WHERE user_id = ?", a[0]);
        act(session, a[1], "approve", null).andExpect(status().isOk());
        act(session, a[1], "revoke", "깜빡임이 너무 빨라요.").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.ticket").value("USED")).andExpect(jsonPath("$.data.current").value(false))
                .andExpect(jsonPath("$.data.revokeReason").value("깜빡임이 너무 빨라요."));
        assertThat(jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, a[0])).isEqualTo("avatar:hobby-gamer");
        assertThat(lastAudit(AuditActions.AVATAR_UPLOAD_REVOKE, Long.toString(a[1]))).containsEntry("retention_class", "GENERAL");
        String revokedTab = mvc.perform(get(UPLOADS).param("status", "REVOKED").param("size", "100").cookie(session)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(revokedTab, "$.data.items[*].id")).contains(Long.toString(a[1]));

        act(session, 999_999_999L, "approve", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_NOT_FOUND"));
        act(session, "abc", "approve", null).andExpect(status().isNotFound());
        mvc.perform(get(UPLOADS).param("status", "NOPE").cookie(session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.status").exists());
        mvc.perform(get(UPLOADS).param("page", "0").cookie(session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.page").exists());
    }
}
