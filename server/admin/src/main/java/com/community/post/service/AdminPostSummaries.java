package com.community.post.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.post.dto.AdminPostRow;
import com.community.post.dto.response.AdminPostRefView;
import com.community.post.repository.master.AdminPostRepositoryQDSL;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 관리자 화면의 글 한 줄({@link AdminPostRefView}) 만들기 — 게시판·말머리 이름은 게시판 사본({@code BoardCatalog}), 작성자 닉네임은 한 번에 읽는다(N+1 없음).
 * 트랜잭션은 부르는 서비스가 연다.
 */
@Component
public class AdminPostSummaries {

    static final String MEMBER = "MEMBER";
    static final String GUEST = "GUEST";

    private final AdminPostRepositoryQDSL posts;
    private final ProfileRepository profiles;

    public AdminPostSummaries(AdminPostRepositoryQDSL posts, ProfileRepository profiles) {
        this.posts = posts;
        this.profiles = profiles;
    }

    /** 글 ID → 줄(지운·숨긴 글 포함, 없는 글은 빠진다) */
    public Map<Long, AdminPostRow> rows(Collection<Long> ids) {
        Map<Long, AdminPostRow> out = new HashMap<>();
        posts.byIds(ids).forEach(r -> out.put(r.id(), r));
        return out;
    }

    /** 작성 회원의 프로필 */
    public Map<Long, ProfileEntity> authors(Collection<AdminPostRow> rows) {
        List<Long> ids = rows.stream().map(AdminPostRow::authorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProfileEntity> out = new HashMap<>();
        if (!ids.isEmpty()) {
            profiles.findAllById(ids).forEach(p -> out.put(p.getUserId(), p));
        }
        return out;
    }

    /** 줄 하나 */
    public AdminPostRefView view(AdminPostRow r, BoardCatalogSnapshot s, Map<Long, ProfileEntity> members, Instant now) {
        EffectiveBoard b = r.boardId() == null ? null : s.board(r.boardId()).orElse(null);
        String prefixName = b == null ? null : b.prefix(r.prefixId()).map(BoardPrefixInfo::name).orElse(null);
        ProfileEntity profile = r.authorId() == null ? null : members.get(r.authorId());
        String authorName = r.authorId() == null ? (r.guestName() == null ? "" : r.guestName()) : (profile == null ? "" : profile.getNickname());
        return new AdminPostRefView(Long.toString(r.id()), r.title(), r.boardId() == null ? null : Long.toString(r.boardId()), b == null ? null : b.name(),
                b == null ? null : b.nameEn(), prefixName, r.authorId() == null ? GUEST : MEMBER, r.authorId() == null ? null : Long.toString(r.authorId()),
                authorName, n(r.likeCount()), n(r.commentCount()), n(r.viewCount()), r.createdAt(), r.stateAt(now, b != null && b.active()));
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }
}
