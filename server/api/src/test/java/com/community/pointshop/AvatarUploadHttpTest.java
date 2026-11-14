package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.avatarupload.service.AvatarUploadReviewService;
import com.community.zconfiguration.StaticAssetUrls;
import com.community.media.service.ImageStorage;
import com.community.pointshop.service.AvatarCatalog;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 아바타 올리기 API(commerce-motion-avatar-contract 「API 계약」 api · 규칙 0·0-1·0-2) — 자격(이메일 인증·정지) → 변경권(파일 처리 전 409 + 장 수·값) → 파일 규격(PNG·JPG·WebP·GIF,
 * 정사각형 ±2%, 96px 이상, 300KB 이하, GIF 30장면, 움직이는 WebP 거절, 256px 이하로 맞춤, 움직이는 GIF 는 원본 + 정지 PNG) → 대기·취소(반환) → 승인(운영자 서비스) 뒤
 * 내 프로필·카탈로그 {@code upload-mine}·고르기·공개 프로필·지우기. 저장소는 앱 설정의 로컬 폴더 — 시험이 만든 파일은 끝에 지운다. 실제 PostgreSQL.
 */
class AvatarUploadHttpTest extends PointShopApiTestBase {

    @Autowired
    private ImageStorage storage;
    @Autowired
    private AvatarUploadReviewService reviews;

    private final List<Long> members = new java.util.ArrayList<>();

    @AfterEach
    void cleanFiles() {
        for (long member : members) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT image_key, still_key FROM avatar_upload_requests WHERE user_id = ?", member)) {
                for (Object key : row.values()) {
                    if (key != null) {
                        storage.delete((String) key);
                    }
                }
            }
        }
    }

    private long uploader() {
        long member = member();
        members.add(member);
        return member;
    }

    private long withTicket() throws Exception {
        long member = uploader();
        grant(member, 10_000);
        buy(member, ticketItem());
        return member;
    }

    private long ticketItem() {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'avatar-upload-ticket'", Long.class);
    }

    private ResultActions upload(long member, byte[] data, String name) throws Exception {
        com.community.auth.dto.AuthUser user = new com.community.auth.dto.AuthUser(member, com.community.user.entity.Role.USER);
        return mvc.perform(multipart("/api/me/avatar-upload").file(new MockMultipartFile("file", name, "application/octet-stream", data))
                .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko"));
    }

    /** 다음 올리기가 속도 제한(10초)에 걸리지 않게 앞 신청 시각을 당긴다 */
    private void backdate(long member) {
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE avatar_upload_requests SET created_at = created_at - interval '1 minute' WHERE user_id = ?", member);
    }

    private String uploadState(long member) throws Exception {
        return body(mvc.perform(as(member, get("/api/me/avatar-upload"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
    }

    @Test
    void 이메일_인증_정지_다음_변경권이고_변경권이_없으면_파일을_보기_전에_409() throws Exception {
        long unverified = unverifiedMember();
        members.add(unverified);
        upload(unverified, new byte[] {1, 2, 3}, "x.png").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
        long suspended = uploader();
        suspend(suspended);
        upload(suspended, TestImages.png(128, 128), "a.png").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));

        long member = uploader();
        upload(member, new byte[] {1, 2, 3}, "garbage.bin").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_TICKET_REQUIRED"))
                .andExpect(jsonPath("$.error.ticketCount").value(0)).andExpect(jsonPath("$.error.ticketPrice").value(3000));
        String body = uploadState(member);
        assertThat((Integer) JsonPath.read(body, "$.data.ticketCount")).isZero();
        assertThat((Integer) JsonPath.read(body, "$.data.ticketPrice")).isEqualTo(3000);
        assertThat((String) JsonPath.read(body, "$.data.ticketItemId")).isEqualTo(Long.toString(ticketItem()));
        assertThat((Object) JsonPath.read(body, "$.data.request")).isNull();
        assertThat((Integer) JsonPath.read(body, "$.data.limits.maxBytes")).isEqualTo(300 * 1024);
        assertThat((Integer) JsonPath.read(body, "$.data.limits.minSize")).isEqualTo(96);
        assertThat((Integer) JsonPath.read(body, "$.data.limits.maxSize")).isEqualTo(256);
        mvc.perform(get("/api/me/avatar-upload")).andExpect(status().isUnauthorized());
    }

    @Test
    void 파일_규격을_어기면_400_fields_file_이고_변경권은_예약되지_않는다() throws Exception {
        long member = withTicket();
        upload(member, new byte[0], "empty.png").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        upload(member, "not an image".getBytes(), "x.png").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        upload(member, TestImages.png(200, 150), "wide.png").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        upload(member, TestImages.png(80, 80), "small.png").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        byte[] large = TestImages.noisePng(400, 400);
        assertThat(large.length).isGreaterThan(300 * 1024);
        upload(member, large, "large.png").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        upload(member, TestImages.gif(31, 100, 100), "long.gif").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        upload(member, TestImages.webpAnimated(128, 128), "moving.webp").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.file").exists());
        upload(member, TestImages.png(205, 200), "almost.png").andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM avatar_upload_requests WHERE user_id = ?", member)).isZero();
        assertThat((Integer) JsonPath.read(uploadState(member), "$.data.ticketCount")).as("예약되지 않았다").isEqualTo(1);
    }

    @Test
    void 정지_그림은_256px_이하로_다시_만들고_예약한_뒤_취소하면_돌려준다() throws Exception {
        long member = withTicket();
        String created = body(upload(member, TestImages.png(400, 404), "me.png").andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store")));
        assertThat((String) JsonPath.read(created, "$.data.request.status")).isEqualTo("PENDING");
        assertThat((Boolean) JsonPath.read(created, "$.data.request.animated")).isFalse();
        assertThat((Object) JsonPath.read(created, "$.data.request.stillUrl")).isNull();
        assertThat((String) JsonPath.read(created, "$.data.request.url")).startsWith("/media/avatar-upload/").endsWith("/image.png");
        assertThat((Integer) JsonPath.read(created, "$.data.ticketCount")).isZero();
        assertThat((Boolean) JsonPath.read(created, "$.data.reserved")).isTrue();
        Map<String, Object> row = jdbc.queryForMap("SELECT width, height, frames, content_type FROM avatar_upload_requests WHERE user_id = ?", member);
        assertThat(row).containsEntry("width", 253).containsEntry("height", 256).containsEntry("frames", 1).containsEntry("content_type", "image/png");
        String items = body(mvc.perform(as(member, get("/api/me/items"))).andExpect(status().isOk()));
        assertThat(JsonPath.<List<Boolean>>read(items, "$.data.items[?(@.kind == 'AVATAR_UPLOAD_TICKET')].reserved")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(items, "$.data.items[?(@.kind == 'AVATAR_UPLOAD_TICKET')].refundable")).containsExactly(false);

        mvc.perform(as(member, delete("/api/me/avatar-upload/request"))).andExpect(status().isNoContent());
        String after = uploadState(member);
        assertThat((Integer) JsonPath.read(after, "$.data.ticketCount")).isEqualTo(1);
        assertThat((Boolean) JsonPath.read(after, "$.data.reserved")).isFalse();
        mvc.perform(as(member, delete("/api/me/avatar-upload/request"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_REQUEST_NOT_FOUND"));
        mvc.perform(as(member, delete("/api/me/avatar-upload"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("AVATAR_UPLOAD_NOT_FOUND"));

        backdate(member);
        upload(member, TestImages.jpeg(120, 120), "me.jpg").andExpect(status().isCreated()).andExpect(jsonPath("$.data.request.url").value(org.hamcrest.Matchers.endsWith(".jpg")));
        backdate(member);
        upload(member, TestImages.webpStill(160, 160), "me.webp").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ticketCount").value(0)).andExpect(jsonPath("$.data.reserved").value(true));
        upload(member, TestImages.png(128, 128), "again.png").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void 움직이는_GIF_는_원본과_첫_장면_정지_그림을_남기고_승인되면_바로_프로필_아바타가_된다() throws Exception {
        long member = withTicket();
        mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"hobby-gamer\"}"))).andExpect(status().isOk());
        String created = body(upload(member, TestImages.gif(4, 300, 300), "dance.gif").andExpect(status().isCreated()));
        assertThat((Boolean) JsonPath.read(created, "$.data.request.animated")).isTrue();
        assertThat((String) JsonPath.read(created, "$.data.request.url")).endsWith("/image.gif");
        assertThat((String) JsonPath.read(created, "$.data.request.stillUrl")).endsWith("/still.png");
        assertThat(jdbc.queryForObject("SELECT frames FROM avatar_upload_requests WHERE user_id = ?", Integer.class, member)).isEqualTo(4);
        long request = Long.parseLong(JsonPath.read(created, "$.data.request.id"));

        tx.execute(s -> reviews.approve(operator, "127.0.0.1", Long.toString(request)));
        String notices = body(mvc.perform(as(member, get("/api/notifications"))).andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(notices, "$.data.items[?(@.type == 'AVATAR_UPLOAD_REVIEWED')].link")).containsExactly("/settings/avatar-upload");
        assertThat(JsonPath.<List<String>>read(notices, "$.data.items[?(@.type == 'AVATAR_UPLOAD_REVIEWED')].title")).containsExactly("올린 아바타가 승인됐어요");
        String mine = uploadState(member);
        assertThat((Boolean) JsonPath.read(mine, "$.data.inUse")).isTrue();
        assertThat((Boolean) JsonPath.read(mine, "$.data.current.animated")).isTrue();
        String url = JsonPath.read(mine, "$.data.current.url");
        String still = JsonPath.read(mine, "$.data.current.stillUrl");
        mvc.perform(as(member, get("/api/me/profile"))).andExpect(jsonPath("$.data.avatarUrl").value(url)).andExpect(jsonPath("$.data.avatarStillUrl").value(still));
        mvc.perform(get("/api/users/" + member + "/profile")).andExpect(jsonPath("$.data.avatarUrl").value(url)).andExpect(jsonPath("$.data.avatarStillUrl").value(still));
        String catalog = body(mvc.perform(as(member, get("/api/avatars"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedId").value(AvatarCatalog.UPLOAD_ID)));
        assertThat(JsonPath.<List<Object>>read(catalog, "$.data.items[?(@.id == 'upload-mine')].itemId")).containsExactly((Object) null);
        assertThat(JsonPath.<List<String>>read(catalog, "$.data.items[?(@.id == 'upload-mine')].category")).containsExactly("upload");
        assertThat(JsonPath.<List<String>>read(catalog, "$.data.items[?(@.id == 'upload-mine')].stillUrl")).containsExactly(still);

        // 다른 아바타로 바꿨다가 다시 — 변경권 없이
        mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"hobby-gamer\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarStillUrl").isEmpty());
        mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"upload-mine\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(url)).andExpect(jsonPath("$.data.avatarStillUrl").value(still));

        // 지우기 — 반환 없음, 쓰는 중이었으면 마지막 정지 아바타로
        mvc.perform(as(member, delete("/api/me/avatar-upload"))).andExpect(status().isNoContent());
        mvc.perform(as(member, get("/api/me/profile"))).andExpect(jsonPath("$.data.avatarUrl").value(StaticAssetUrls.of(AvatarCatalog.URL_PREFIX + "hobby-gamer.svg")))
                .andExpect(jsonPath("$.data.avatarStillUrl").isEmpty());
        String gone = uploadState(member);
        assertThat((Object) JsonPath.read(gone, "$.data.current")).isNull();
        assertThat((Integer) JsonPath.read(gone, "$.data.ticketCount")).isZero();
        mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"upload-mine\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("AVATAR_NOT_OWNED"));
    }
}
