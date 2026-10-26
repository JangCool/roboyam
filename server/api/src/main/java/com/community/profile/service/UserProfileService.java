package com.community.profile.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.AuthorMembers;
import com.community.board.service.AuthorViews;
import com.community.board.service.BoardCatalog;
import com.community.board.service.PublicBoardService;
import com.community.board.service.RichText;
import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.poll.repository.master.PollQueries;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.dto.response.PostBoardRefView;
import com.community.post.dto.response.PostFeedPageView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.dto.response.PostPrefixRefView;
import com.community.post.service.PostQueryService;
import com.community.profile.dto.response.AuthorCommentPageView;
import com.community.profile.dto.response.AuthorCommentRowView;
import com.community.profile.dto.response.CommentBoardRefView;
import com.community.profile.dto.response.ProfileStatsView;
import com.community.profile.dto.response.PublicProfileView;
import com.community.profile.exception.ProfileNotFoundException;
import com.community.profile.repository.master.UserActivityQueries;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 회원 프로필 — {@code GET /api/users/{id}/profile}·{@code /posts}·{@code /comments}. 비회원도 읽는다. docs/features/member-activity-contract.md 「공개 프로필」·「결정 (2026-10-24 2차)」.
 *
 * <ul>
 *   <li>없는 회원·탈퇴(삭제)한 회원·형식이 틀린 ID → {@code 404 PROFILE_NOT_FOUND}. 정지된 회원도 보인다. 이전 닉네임·이메일은 싣지 않는다.</li>
 *   <li>볼 수 있는 글·댓글의 정의는 {@link UserActivityQueries} — 지움·숨김·꺼진 게시판 제외. 수치는 읽을 때 바로 센다(캐시 없음).</li>
 *   <li>작성글 줄은 게시판 목록({@code GET /api/boards/{slug}/posts})과 같은 방식으로 만든다(작성자 표시 {@link AuthorViews}, 말머리, 19금 표시) + 그 글의 게시판.</li>
 *   <li>보는 사람의 차단·메모({@link ViewerBlocks}): 머리의 {@code blockedByMe}·{@code memo}, 작성글 줄의 {@code blocked}·작성자 {@code memo}, 작성 댓글 줄의 {@code blocked}
 *       — 게시판 목록·댓글과 같은 판정(단어는 글 제목·댓글 본문), 쪽마다 한 번 모아 읽는다. 줄은 빼지 않는다(화면이 접는다). 본인·비회원은 비어 있다.</li>
 *   <li>쪽: {@code size} 1~{@value #MAX_SIZE}(기본 {@value #DEFAULT_SIZE}), {@code cursor} = 앞 응답의 {@code nextCursor}(마지막 줄 id). 틀리면 {@code 400 VALIDATION fields.size|cursor}
 *       — 회원 확인보다 먼저 본다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class UserProfileService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    /** 댓글 미리보기 글자 수 */
    static final int PREVIEW_MAX = 100;
    private static final String ROLE_USER = "USER";
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{1,19}$");

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final UserActivityQueries activity;
    private final BoardCatalog catalog;
    private final AuthorViews authors;
    private final ViewerBlocks viewerBlocks;
    private final PollQueries polls;
    private final ShopDecorationResolver decorations;

    public UserProfileService(UserRepository users, ProfileRepository profiles, UserActivityQueries activity, BoardCatalog catalog, AuthorViews authors,
                              ViewerBlocks viewerBlocks, PollQueries polls, ShopDecorationResolver decorations) {
        this.decorations = decorations;
        this.polls = polls;
        this.users = users;
        this.profiles = profiles;
        this.activity = activity;
        this.catalog = catalog;
        this.authors = authors;
        this.viewerBlocks = viewerBlocks;
    }

    /** @param viewerId 보는 회원(비회원 null) */
    public PublicProfileView profile(String idText, Long viewerId) {
        Member m = requireMember(idText);
        BoardCatalogSnapshot s = catalog.snapshot();
        UserActivityQueries.Stats stats = activity.stats(m.id(), activeBoardIds(s), Instant.now());
        String gradeKey = m.profile().getGradeKey();
        String label = s.gradeLabel(gradeKey);
        String bio = m.profile().getBio();
        ViewerBlocks.Toward toward = viewerBlocks.toward(viewerId, m.id());
        AuthorLook look = decorations.look(m.id());
        return new PublicProfileView(Long.toString(m.id()), m.profile().getNickname(), gradeKey, label == null ? gradeKey : label, ROLE_USER,
                bio == null || bio.isBlank() ? null : bio, m.user().getCreatedAt(), viewerId != null && viewerId == m.id(),
                new ProfileStatsView(stats.posts(), stats.comments(), stats.likesReceived()), toward.blocked(), toward.memo(), look.decoration(), look.avatarUrl());
    }

    /** @param viewerId 보는 회원(비회원 null) */
    public PostFeedPageView posts(String idText, String sizeText, String cursor, Long viewerId) {
        int size = sizeOf(sizeText);
        Long before = cursorOf(cursor);
        Member m = requireMember(idText);
        BoardCatalogSnapshot s = catalog.snapshot();
        Instant now = Instant.now();
        List<PostRowData> rows = activity.posts(m.id(), activeBoardIds(s), before, size, now);
        boolean more = rows.size() > size;
        List<PostRowData> page = more ? rows.subList(0, size) : rows;
        AuthorMembers members = authors.members(page.stream().map(PostRowData::authorId).toList());
        ViewerBlocks.Lens lens = lensOf(viewerId, m.id(), page.isEmpty());
        Set<Long> withPoll = polls.postIdsWithPoll(page.stream().map(PostRowData::id).toList());
        List<PostFeedRowView> items = page.stream().map(r -> feedRow(r, s.board(r.boardId()).orElseThrow(), s, members, now, lens, withPoll)).toList();
        return new PostFeedPageView(items, more, more ? items.get(items.size() - 1).id() : null);
    }

    /** @param viewerId 보는 회원(비회원 null) */
    public AuthorCommentPageView comments(String idText, String sizeText, String cursor, Long viewerId) {
        int size = sizeOf(sizeText);
        Long before = cursorOf(cursor);
        Member m = requireMember(idText);
        BoardCatalogSnapshot s = catalog.snapshot();
        List<UserActivityQueries.CommentRow> rows = activity.comments(m.id(), activeBoardIds(s), before, size, Instant.now());
        boolean more = rows.size() > size;
        List<UserActivityQueries.CommentRow> page = more ? rows.subList(0, size) : rows;
        ViewerBlocks.Lens lens = lensOf(viewerId, m.id(), page.isEmpty());
        List<AuthorCommentRowView> items = page.stream().map(r -> {
            EffectiveBoard b = s.board(r.boardId()).orElseThrow();
            String text = RichText.plainOf(r.format(), r.content());
            return new AuthorCommentRowView(Long.toString(r.id()), Long.toString(r.postId()), r.postTitle(), new CommentBoardRefView(b.slug(), b.name(), b.nameEn()),
                    preview(text), r.likeCount(), r.reply(), r.createdAt(), lens.blockedOf(m.id(), text));
        }).toList();
        return new AuthorCommentPageView(items, more, more ? items.get(items.size() - 1).id() : null);
    }

    /** 이 쪽을 보는 동안의 차단·메모 — 작성자는 프로필 주인 한 명. 비회원·본인(늘 비어 있다)·빈 쪽은 읽지 않는다 */
    private ViewerBlocks.Lens lensOf(Long viewerId, long ownerId, boolean empty) {
        return empty || viewerId == null || viewerId == ownerId ? ViewerBlocks.Lens.none() : viewerBlocks.lens(viewerId, List.of(ownerId));
    }

    /** 게시판 목록 한 줄({@code PostQueryService} 와 같은 규칙 — 숨긴 글은 여기 오지 않지만 같은 가림을 둔다) + 게시판 */
    private PostFeedRowView feedRow(PostRowData r, EffectiveBoard b, BoardCatalogSnapshot s, AuthorMembers members, Instant now, ViewerBlocks.Lens lens,
                                    Set<Long> withPoll) {
        boolean hidden = r.hiddenAt(now);
        PostAuthorView author = authors.of(r.authorId(), r.guestName(), r.authorIp(), members, s).withMemo(lens.memoOf(r.authorId()));
        PostPrefixRefView prefix = hidden ? null : b.prefix(r.prefixId()).map(p -> new PostPrefixRefView(Long.toString(p.id()), p.name(), p.staffOnly(), p.color()))
                .orElse(null);
        return new PostFeedRowView(Long.toString(r.id()), prefix, hidden ? "" : r.title(), hidden ? 0 : r.commentCount(), !hidden && Boolean.TRUE.equals(r.hasImage()),
                null, hidden ? AuthorViews.blank(author) : author, r.createdAt(), r.viewCount(), r.likeCount(), r.pinnedAt() != null,
                !hidden && Boolean.TRUE.equals(r.adult()), r.publicHiddenReason(now, b.staffHiddenNotice()), hidden ? null : lens.blockedOf(r.authorId(), r.title()),
                !hidden && withPoll.contains(r.id()),
                new PostBoardRefView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.color()));
    }

    /** 공개 프로필의 주인 — 회원·프로필이 있고 탈퇴·삭제되지 않았다 */
    private Member requireMember(String idText) {
        Long id = PostQueryService.parseId(idText);
        if (id == null) {
            throw new ProfileNotFoundException();
        }
        UserEntity user = users.findById(id).filter(u -> !u.isGone()).orElseThrow(ProfileNotFoundException::new);
        ProfileEntity profile = profiles.findById(id).orElseThrow(ProfileNotFoundException::new);
        return new Member(id, user, profile);
    }

    private static Set<Long> activeBoardIds(BoardCatalogSnapshot s) {
        // 소모임 게시판의 글은 프로필 작성글·통계에 넣지 않는다(소모임 2단계 — 공개 소모임 글의 프로필 탭은 6단계)
        return s.boards().stream().filter(b -> b.active() && PublicBoardService.isPublic(b)).map(EffectiveBoard::id).collect(Collectors.toSet());
    }

    /** 앞 100자 — 줄바꿈·연속 공백은 공백 한 칸, 넘으면 {@code …} */
    static String preview(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= PREVIEW_MAX ? flat : flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_MAX)) + "…";
    }

    static int sizeOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SIZE;
        }
        String text = raw.strip();
        int size = DIGITS.matcher(text).matches() && text.length() <= 3 ? Integer.parseInt(text) : -1;
        if (size < 1 || size > MAX_SIZE) {
            throw ValidationException.of("size", new MessageRef("notification.size.range", String.valueOf(MAX_SIZE)));
        }
        return size;
    }

    static Long cursorOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Long id = DIGITS.matcher(raw.strip()).matches() ? PostQueryService.parseId(raw) : null;
        if (id == null) {
            throw ValidationException.of("cursor", new MessageRef("notification.cursor.invalid"));
        }
        return id;
    }

    private record Member(long id, UserEntity user, ProfileEntity profile) {
    }
}
