package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.permission.entity.Permission;
import com.community.pointshop.entity.UploadTicketUse;
import com.community.pointshop.service.UploadTicketService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 닉네임 이미지 심사와 변경권·하나만 규칙(commerce-motion-avatar-contract 규칙 0·3) — 승인 = 예약된 닉네임 이미지 변경권 사용, 반려 = 반환, 해제 = 반환 없음, 이 규칙 전 신청(예약 없음)도 그대로 심사,
 * 움직이는 그림을 승인했는데 회원이 움직이는 아바타를 쓰면 아바타는 마지막 정지 아바타로. 실제 필터 체인·세션·PostgreSQL. 신청 행은 SQL 로 넣는다(파일 키는 시험용).
 */
class NicknameImageTicketAdminHttpTest extends PointShopAdminHttpTestBase {

    private static final String IMAGES = "/api/admin/nickname-images";

    @Autowired
    private UploadTicketService tickets;

    private ResultActions act(Cookie session, long id, String action, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return mvc.perform(withCsrf(post(IMAGES + "/" + id + "/" + action), session).header("Accept-Language", "ko").content(body));
    }

    private long ticketItem() {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'nickname-image-ticket'", Long.class);
    }

    private long pendingRequest(long member, boolean animated) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO nickname_image_requests (id, user_id, status, image_key, still_key, content_type, bytes, width, height, frames, created_at) "
                + "VALUES (?, ?, 'PENDING', ?, ?, ?, 1000, 240, 60, ?, now())", id, member, "nickname-image/t-" + id + "/image." + (animated ? "gif" : "png"),
                animated ? "nickname-image/t-" + id + "/still.png" : null, animated ? "image/gif" : "image/png", animated ? 6 : 1);
        return id;
    }

    /** 변경권 한 장을 사서 그 신청에 예약한 대기 신청 */
    private long reservedRequest(long member, boolean animated) {
        buy(member, ticketItem());
        long id = pendingRequest(member, animated);
        tx.execute(s -> tickets.reserve(member, UploadTicketUse.NICKNAME_IMAGE, id));
        return id;
    }

    private String ticketStatus(long member, long requestId) {
        return jdbc.queryForObject("SELECT status || ':' || (reserved_at IS NOT NULL) FROM user_items WHERE user_id = ? AND ref_id = ?", String.class, member, requestId);
    }

    @Test
    void 승인은_예약된_변경권을_쓰고_반려는_돌려주며_해제는_돌려주지_않는다() throws Exception {
        Cookie session = login(operatorWith(Permission.NICKNAME_IMAGE_READ, Permission.NICKNAME_IMAGE_MANAGE));
        long member = buyer(10_000);
        long approved = reservedRequest(member, false);
        assertThat(ticketStatus(member, approved)).isEqualTo("OWNED:true");
        act(session, approved, "approve", null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(ticketStatus(member, approved)).isEqualTo("USED:false");
        assertThat(lastAudit(AuditActions.NICKNAME_IMAGE_APPROVE, Long.toString(approved)).get("detail").toString()).contains("ticketUsed");

        act(session, approved, "revoke", "신고가 많아 내렸어요.").andExpect(status().isOk());
        assertThat(ticketStatus(member, approved)).as("해제는 반환 없음").isEqualTo("USED:false");

        long rejected = reservedRequest(member, false);
        act(session, rejected, "reject", "글자가 너무 작아요.").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(count("SELECT count(*) FROM user_items WHERE user_id = ? AND ref_id = ?", member, rejected)).as("반환 — 신청 칸을 지운다").isZero();
        assertThat(tickets.count(member, UploadTicketUse.NICKNAME_IMAGE)).isEqualTo(1);

        long legacy = pendingRequest(member, false);
        act(session, legacy, "approve", null).andExpect(status().isOk());
        assertThat(tickets.count(member, UploadTicketUse.NICKNAME_IMAGE)).as("남은 장은 쓰지 않는다").isEqualTo(1);
    }

    @Test
    void 움직이는_그림을_승인했는데_움직이는_아바타를_쓰는_중이면_아바타가_마지막_정지_아바타로_돌아간다() throws Exception {
        Cookie session = login(operatorWith(Permission.NICKNAME_IMAGE_READ, Permission.NICKNAME_IMAGE_MANAGE));
        long member = buyer(10_000);
        buy(member, avatarItem("motion-robot-bob"));
        // SQL 로 남김: 아바타·이미지 칸을 바로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE profiles SET profile_image = 'avatar:motion-robot-bob', last_still_avatar = 'hobby-gamer' WHERE user_id = ?", member);
        long still = reservedRequest(member, false);
        act(session, still, "approve", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).as("정지 그림은 함께 쓴다")
                .isEqualTo("avatar:motion-robot-bob");

        long moving = reservedRequest(member, true);
        act(session, moving, "approve", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).isEqualTo("avatar:hobby-gamer");
        assertThat(lastAudit(AuditActions.NICKNAME_IMAGE_APPROVE, Long.toString(moving)).get("detail").toString()).contains("avatarYielded");
    }
}
