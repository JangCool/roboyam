package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.media.service.ImageStorage;
import com.community.pointshop.service.AvatarCatalog;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 닉네임 이미지 변경권·하나만 규칙(commerce-motion-avatar-contract 규칙 0·2·3, 「API 계약」 닉네임 이미지) — 지금 신청 조건 뒤 · 파일 처리 전에 변경권(없으면 409 + 장 수·값), 신청 = 예약
 * ({@code request.ticketReserved}), 본인 취소 = 반환, 대체 = 같은 장, 이 규칙 전 대기 신청은 예약이 없어 돌려줄 것이 없음, {@code GET} 의 {@code ticketCount}·{@code ticketPrice}·{@code paused},
 * {@code POST …/resume}(움직이는 아바타 → 마지막 정지 아바타). 실제 PostgreSQL. 승인·반려는 관리자 시험({@code NicknameImageTicketAdminHttpTest}).
 */
class NicknameImageTicketHttpTest extends PointShopApiTestBase {

    @Autowired
    private ImageStorage storage;

    private final List<Long> members = new ArrayList<>();

    @AfterEach
    void cleanFiles() {
        for (long member : members) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT image_key, still_key FROM nickname_image_requests WHERE user_id = ?", member)) {
                for (Object key : row.values()) {
                    if (key != null && ((String) key).startsWith("nickname-image/") && !((String) key).startsWith("nickname-image/t-")) {
                        storage.delete((String) key);
                    }
                }
            }
        }
    }

    /** 기본 신청 조건(등급 기준 · 베테랑 · 이메일 인증)을 채운 회원 */
    private long veteran() {
        long member = member();
        jdbc.update("UPDATE profiles SET grade_key = 'VETERAN' WHERE user_id = ?", member);
        members.add(member);
        return member;
    }

    private long ticketItem() {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'nickname-image-ticket'", Long.class);
    }

    private long withTicket() throws Exception {
        long member = veteran();
        grant(member, 10_000);
        buy(member, ticketItem());
        return member;
    }

    private ResultActions apply(long member, byte[] data) throws Exception {
        com.community.auth.dto.AuthUser user = new com.community.auth.dto.AuthUser(member, com.community.user.entity.Role.USER);
        return mvc.perform(multipart("/api/me/nickname-image").file(new MockMultipartFile("file", "n.png", "image/png", data))
                .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko"));
    }

    private String state(long member) throws Exception {
        return body(mvc.perform(as(member, get("/api/me/nickname-image"))).andExpect(status().isOk()));
    }

    private void backdate(long member) {
        jdbc.update("UPDATE nickname_image_requests SET created_at = created_at - interval '1 minute' WHERE user_id = ?", member);
    }

    /** 이 규칙 전에 올라온 것처럼(예약 없이) 대기·승인 신청을 넣는다 — 파일 키는 가짜 */
    private long insert(long member, String status, boolean animated) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO nickname_image_requests (id, user_id, status, image_key, still_key, content_type, bytes, width, height, frames, reviewed_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1000, 240, 60, ?, CASE WHEN ? = 'APPROVED' THEN now() END, now() - interval '1 hour')", id, member, status,
                "nickname-image/t-" + id + "/image." + (animated ? "gif" : "png"), animated ? "nickname-image/t-" + id + "/still.png" : null, animated ? "image/gif" : "image/png",
                animated ? 6 : 1, status);
        return id;
    }

    @Test
    void 변경권이_없으면_조건_검사_뒤_파일_처리_전에_409_와_장_수_값() throws Exception {
        long unverified = unverifiedMember();
        jdbc.update("UPDATE profiles SET grade_key = 'VETERAN' WHERE user_id = ?", unverified);
        apply(unverified, new byte[] {1}).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
        long plain = member();
        apply(plain, new byte[] {1}).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_IMAGE_REQUIREMENTS_NOT_MET"));

        long member = veteran();
        apply(member, "not an image".getBytes()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NICKNAME_IMAGE_TICKET_REQUIRED"))
                .andExpect(jsonPath("$.error.ticketCount").value(0)).andExpect(jsonPath("$.error.ticketPrice").value(2000));
        String body = state(member);
        assertThat((Integer) JsonPath.read(body, "$.data.ticketCount")).isZero();
        assertThat((Integer) JsonPath.read(body, "$.data.ticketPrice")).isEqualTo(2000);
        assertThat((String) JsonPath.read(body, "$.data.ticketItemId")).isEqualTo(Long.toString(ticketItem()));
        assertThat((Boolean) JsonPath.read(body, "$.data.paused")).isFalse();
        assertThat(count("SELECT count(*) FROM nickname_image_requests WHERE user_id = ?", member)).isZero();
    }

    @Test
    void 신청하면_한_장을_예약하고_본인_취소면_돌려준다_대체는_같은_장을_넘겨받는다() throws Exception {
        long member = withTicket();
        String created = body(apply(member, TestImages.png(240, 60)).andExpect(status().isCreated()));
        assertThat((Integer) JsonPath.read(created, "$.data.ticketCount")).isZero();
        assertThat((Boolean) JsonPath.read(created, "$.data.request.ticketReserved")).isTrue();
        long first = Long.parseLong(JsonPath.read(created, "$.data.request.id"));
        assertThat(jdbc.queryForObject("SELECT ref_id FROM user_items WHERE user_id = ? AND ref_type = 'NICKNAME_IMAGE'", Long.class, member)).isEqualTo(first);

        backdate(member);
        String replaced = body(apply(member, TestImages.png(480, 120)).andExpect(status().isCreated()));
        long second = Long.parseLong(JsonPath.read(replaced, "$.data.request.id"));
        assertThat((Boolean) JsonPath.read(replaced, "$.data.request.ticketReserved")).as("대체는 같은 장을 넘겨받는다 — 한 장뿐인데 된다").isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM nickname_image_requests WHERE id = ?", String.class, first)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT ref_id FROM user_items WHERE user_id = ? AND ref_type = 'NICKNAME_IMAGE'", Long.class, member)).isEqualTo(second);

        mvc.perform(as(member, delete("/api/me/nickname-image/request"))).andExpect(status().isNoContent());
        String after = state(member);
        assertThat((Integer) JsonPath.read(after, "$.data.ticketCount")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_items WHERE user_id = ? AND reserved_at IS NOT NULL", member)).isZero();
    }

    @Test
    void 이_규칙_전에_올라온_대기_신청은_예약이_없어_취소해도_돌려줄_것이_없고_대체하면_새로_한_장을_예약한다() throws Exception {
        long member = withTicket();
        long legacy = insert(member, "PENDING", false);
        assertThat((Boolean) JsonPath.read(state(member), "$.data.request.ticketReserved")).isFalse();
        mvc.perform(as(member, delete("/api/me/nickname-image/request"))).andExpect(status().isNoContent());
        assertThat((Integer) JsonPath.read(state(member), "$.data.ticketCount")).as("돌려받을 것도 쓴 것도 없다").isEqualTo(1);

        long again = insert(member, "PENDING", false);
        String created = body(apply(member, TestImages.png(240, 60)).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT status FROM nickname_image_requests WHERE id = ?", String.class, again)).isEqualTo("CANCELED");
        assertThat((Integer) JsonPath.read(created, "$.data.ticketCount")).as("예약이 없던 신청의 대체는 한 장을 새로 예약").isZero();
        assertThat((Boolean) JsonPath.read(created, "$.data.request.ticketReserved")).isTrue();
        assertThat(legacy).isNotZero();
    }

    @Test
    void 움직이는_닉네임_이미지와_움직이는_아바타는_하나만_쉬는_중이면_paused_다시_쓰면_아바타가_마지막_정지_아바타로() throws Exception {
        long member = veteran();
        grant(member, 10_000);
        mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"hobby-gamer\"}"))).andExpect(status().isOk());
        buy(member, avatarItem("motion-robot-wave"));
        mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"motion-robot-wave\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarStillUrl").value("/avatars/motion/motion-robot-wave-still.svg"));

        mvc.perform(as(member, post("/api/me/nickname-image/resume"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_IMAGE_NOT_FOUND"));
        long still = insert(member, "APPROVED", false);
        assertThat((Boolean) JsonPath.read(state(member), "$.data.paused")).as("정지 닉네임 이미지 + 움직이는 아바타는 함께 쓴다").isFalse();
        mvc.perform(as(member, post("/api/me/nickname-image/resume"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("/avatars/motion/motion-robot-wave.svg"));
        jdbc.update("UPDATE nickname_image_requests SET status = 'REMOVED', ended_at = now() WHERE id = ?", still);

        insert(member, "APPROVED", true);
        assertThat((Boolean) JsonPath.read(state(member), "$.data.paused")).as("움직이는 그림 + 움직이는 아바타 → 쉰다(저장하지 않는다)").isTrue();
        mvc.perform(as(member, post("/api/me/nickname-image/resume"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(AvatarCatalog.URL_PREFIX + "hobby-gamer.svg")).andExpect(jsonPath("$.data.avatarStillUrl").isEmpty());
        assertThat((Boolean) JsonPath.read(state(member), "$.data.paused")).isFalse();

        long suspended = veteran();
        insert(suspended, "APPROVED", true);
        suspend(suspended);
        mvc.perform(as(suspended, post("/api/me/nickname-image/resume"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
    }
}
