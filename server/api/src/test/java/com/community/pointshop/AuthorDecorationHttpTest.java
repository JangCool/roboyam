package com.community.pointshop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.board.service.BoardCatalog;
import com.community.pointshop.service.AvatarCatalog;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 작성자 장식·아바타(커머스 1-2 — commerce-stage1-contract 「B」 장식 표시·결정 8·14) — 글 목록·글 보기·댓글·공개 프로필의 회원 작성자에 장착한 닉네임 색·프로필 테두리
 * ({@code decoration})와 아바타({@code avatarUrl}), 비회원은 둘 다 null, 장식이 없으면 {@code decoration} null·아바타는 {@code basic-default}, 기간이 끝난 장착은 null.
 * 게시판·글·댓글은 SQL 로 넣는다(쓰기 정책과 무관하게 표시만 본다). 실제 PostgreSQL.
 */
class AuthorDecorationHttpTest extends PointShopApiTestBase {

    @Autowired
    private BoardCatalog boards;
    @Autowired
    private AvatarCatalog avatars;

    private long board;
    private String slug;

    @BeforeEach
    void board() {
        board = ids.nextId();
        slug = "d" + Long.toString(board, 36);
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 900)", board, slug, "장식 " + slug.substring(slug.length() - 6));
        boards.invalidate();
    }

    private long post(Long authorId, String guestName) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO posts (id, board_id, author_id, guest_name, guest_password_hash, title, content) VALUES (?, ?, ?, ?, ?, '장식 시험', '본문')", id, board,
                authorId, guestName, guestName == null ? null : "x");
        return id;
    }

    private long comment(long postId, Long authorId, String guestName) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, guest_name, guest_password_hash, content) VALUES (?, ?, ?, ?, ?, ?, '댓글')", id, postId, board,
                authorId, guestName, guestName == null ? null : "x");
        jdbc.update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = ?", postId);
        return id;
    }

    private static String url(String id) {
        return AvatarCatalog.URL_PREFIX + id + ".svg";
    }

    /** 색·테두리를 사서 장착하고 무료 아바타를 고른 회원 — 고른 아바타 ID */
    private String decorate(long member, String hex, String border) throws Exception {
        grant(member, 5_000);
        long color = buy(member, colorItem(300, hex));
        long ring = buy(member, borderItem(300, border, null));
        mvc.perform(as(member, put("/api/me/equipped/NICKNAME_COLOR").contentType(MediaType.APPLICATION_JSON).content("{\"userItemId\":\"" + color + "\"}")))
                .andExpect(status().isOk());
        mvc.perform(as(member, put("/api/me/equipped/PROFILE_BORDER").contentType(MediaType.APPLICATION_JSON).content("{\"userItemId\":\"" + ring + "\"}")))
                .andExpect(status().isOk());
        String avatar = avatars.all().stream().filter(AvatarCatalog.Avatar::free).filter(a -> !a.id().equals(AvatarCatalog.DEFAULT_ID)).findFirst().orElseThrow().id();
        mvc.perform(as(member, put("/api/me/profile/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"" + avatar + "\"}")))
                .andExpect(status().isOk());
        return avatar;
    }

    @Test
    void 글_보기_목록_댓글_공개_프로필에_장착한_색_테두리와_아바타가_붙는다() throws Exception {
        long writer = member();
        long viewer = member();
        String avatar = decorate(writer, "#BE123C", "ROSE");
        long postId = post(writer, null);
        comment(postId, writer, null);

        mvc.perform(as(viewer, get("/api/posts/" + postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.memberId").value(Long.toString(writer)))
                .andExpect(jsonPath("$.data.author.decoration.nicknameColor").value("#BE123C"))
                .andExpect(jsonPath("$.data.author.decoration.profileBorder").value("ROSE"))
                .andExpect(jsonPath("$.data.author.avatarUrl").value(url(avatar)));
        mvc.perform(as(viewer, get("/api/boards/" + slug + "/posts"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].author.decoration.nicknameColor").value("#BE123C"))
                .andExpect(jsonPath("$.data.items[0].author.avatarUrl").value(url(avatar)));
        mvc.perform(as(viewer, get("/api/posts/" + postId + "/comments"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threads[0].author.decoration.nicknameColor").value("#BE123C"))
                .andExpect(jsonPath("$.data.threads[0].author.decoration.profileBorder").value("ROSE"))
                .andExpect(jsonPath("$.data.threads[0].author.avatarUrl").value(url(avatar)));
        mvc.perform(get("/api/users/" + writer + "/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decoration.nicknameColor").value("#BE123C"))
                .andExpect(jsonPath("$.data.decoration.profileBorder").value("ROSE"))
                .andExpect(jsonPath("$.data.avatarUrl").value(url(avatar)));
    }

    @Test
    void 장식이_없는_회원은_decoration_null_아바타는_기본_비회원은_둘_다_null() throws Exception {
        long plain = member();
        long memberPost = post(plain, null);
        long guestPost = post(null, "손님");
        comment(memberPost, null, "손님");

        mvc.perform(get("/api/posts/" + memberPost)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.decoration").isEmpty())
                .andExpect(jsonPath("$.data.author.avatarUrl").value(url(AvatarCatalog.DEFAULT_ID)));
        mvc.perform(get("/api/posts/" + guestPost)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.kind").value("GUEST"))
                .andExpect(jsonPath("$.data.author.decoration").isEmpty()).andExpect(jsonPath("$.data.author.avatarUrl").isEmpty());
        mvc.perform(get("/api/posts/" + memberPost + "/comments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threads[0].author.kind").value("GUEST"))
                .andExpect(jsonPath("$.data.threads[0].author.avatarUrl").isEmpty());
        mvc.perform(get("/api/users/" + plain + "/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.data.decoration").isEmpty())
                .andExpect(jsonPath("$.data.avatarUrl").value(url(AvatarCatalog.DEFAULT_ID)));
    }

    @Test
    void 기간이_끝난_장착은_작성자_장식에서_빠진다() throws Exception {
        long writer = member();
        grant(writer, 3_000);
        long held = buy(writer, periodColorItem(200, "#4338CA", 1));
        mvc.perform(as(writer, put("/api/me/equipped/NICKNAME_COLOR").contentType(MediaType.APPLICATION_JSON).content("{\"userItemId\":\"" + held + "\"}")))
                .andExpect(status().isOk());
        long postId = post(writer, null);
        comment(postId, writer, null);
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.author.decoration.nicknameColor").value("#4338CA"));

        clock.advance(Duration.ofDays(2));
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.decoration").isEmpty())
                .andExpect(jsonPath("$.data.author.avatarUrl").value(url(AvatarCatalog.DEFAULT_ID)));
        mvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(jsonPath("$.data.threads[0].author.decoration").isEmpty());
        mvc.perform(get("/api/users/" + writer + "/profile")).andExpect(jsonPath("$.data.decoration").isEmpty());
    }
}
