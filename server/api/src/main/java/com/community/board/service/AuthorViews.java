package com.community.board.service;

import com.community.aimember.service.AiMemberDirectory;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.business.service.BusinessEligibility;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.post.dto.response.PostAuthorView;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 글·댓글 작성자 표시({@link PostAuthorView}) — 회원은 닉네임·등급, 비회원은 입력한 이름 + 작성 IP 앞 두 자리.
 * 회원 정보·포인트 상점 장식·아바타는 목록마다 한 번에 읽는다({@link #members}). 작성 IP 원문은 응답에 싣지 않는다.
 */
@Component
public class AuthorViews {

    static final String MEMBER = "MEMBER";
    static final String GUEST = "GUEST";
    /** 회원 작성자의 공개 역할 — 게시판 매니저·스텝 표시는 공개 사이트에 내지 않는다(docs/domain/operator-permissions.md) */
    static final String ROLE_USER = "USER";

    private final ProfileRepository profiles;
    private final ShopDecorationResolver decorations;
    private final BusinessEligibility businesses;
    private final OperatorMemberAccountService operatorAccounts;
    private final UserRepository users;
    private final AiMemberDirectory aiMembers;

    public AuthorViews(ProfileRepository profiles, ShopDecorationResolver decorations, BusinessEligibility businesses, OperatorMemberAccountService operatorAccounts,
                       UserRepository users, AiMemberDirectory aiMembers) {
        this.aiMembers = aiMembers;
        this.users = users;
        this.operatorAccounts = operatorAccounts;
        this.profiles = profiles;
        this.decorations = decorations;
        this.businesses = businesses;
    }

    /**
     * 회원 ID → 프로필(닉네임·등급) + 포인트 상점 표시(장식·아바타) + 사업자 뱃지. 목록 하나에 프로필 한 번, 표시 한 번(장착 표 + 프로필 이미지), 사업자 신원 한 번 — 줄마다 읽지 않는다.
     */
    public AuthorMembers members(Collection<Long> ids) {
        List<Long> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return AuthorMembers.EMPTY;
        }
        Map<Long, ProfileEntity> out = new HashMap<>();
        profiles.findAllById(wanted).forEach(p -> out.put(p.getUserId(), p));
        Map<Long, AuthorLook> looks = decorations.resolve(wanted);
        Set<Long> approved = businesses.approvedUserIds(wanted);
        Set<Long> operators = operatorAccounts.activeOperatorUserIds(wanted); // 운영자 뱃지(정책 10) — 힘이 살아 있는 연결만, 한 질의
        Map<Long, AiMemberDirectory.AiAuthor> ai = aiMembers.authorsOf(wanted); // AI 배지(ai-members-contract 정책 3) — 한 질의
        return new AuthorMembers(out, looks, approved, operators, ai, Map.of(), Map.of());
    }

    /**
     * 글들을 만든 생성 줄의 모델을 덧붙인다(ai-members-contract 정책 3 — 실제로 부른 모델) — AI 작성자가 없으면 읽지 않고, 있으면 한 질의(IN).
     */
    public AuthorMembers forPosts(AuthorMembers members, Collection<Long> postIds) {
        if (members.ai() == null || members.ai().isEmpty() || postIds == null || postIds.isEmpty()) {
            return members;
        }
        return members.withContent(aiMembers.postModels(postIds), members.commentAi());
    }

    /** 댓글들을 만든 생성 줄의 모델을 덧붙인다(한 질의) */
    public AuthorMembers forComments(AuthorMembers members, Collection<Long> commentIds) {
        if (members.ai() == null || members.ai().isEmpty() || commentIds == null || commentIds.isEmpty()) {
            return members;
        }
        return members.withContent(members.postAi(), aiMembers.commentModels(commentIds));
    }

    /** 글 작성자 — AI 배지 모델은 그 글을 만든 모델({@link #forPosts} 로 읽어 둔 것, 없으면 계정의 지금 모델) */
    public PostAuthorView ofPost(long postId, Long authorId, String guestName, String authorIp, AuthorMembers members, BoardCatalogSnapshot catalog) {
        PostAuthorView v = of(authorId, guestName, authorIp, members, catalog);
        return authorId == null ? v : v.withAi(aiView(members.aiOfPost(authorId, postId)));
    }

    /** 댓글 작성자 — AI 배지 모델은 그 댓글을 만든 모델({@link #forComments}) */
    public PostAuthorView ofComment(long commentId, Long authorId, String guestName, String authorIp, AuthorMembers members, BoardCatalogSnapshot catalog) {
        PostAuthorView v = of(authorId, guestName, authorIp, members, catalog);
        return authorId == null ? v : v.withAi(aiView(members.aiOfComment(authorId, commentId)));
    }

    /**
     * @param authorId 회원 글이면 회원 ID, 비회원 글이면 null
     * @param members  {@link #members} 로 읽어 둔 프로필·표시
     */
    public PostAuthorView of(Long authorId, String guestName, String authorIp, AuthorMembers members, BoardCatalogSnapshot catalog) {
        if (authorId == null) {
            return new PostAuthorView(GUEST, null, guestName == null ? "" : guestName, null, null, null, maskIp(authorIp), null, null, null, null, false, false, null, null, null);
        }
        ProfileEntity profile = members.get(authorId);
        String gradeKey = profile == null ? null : profile.getGradeKey();
        String label = gradeKey == null ? null : catalog.gradeLabel(gradeKey);
        AuthorLook look = members.look(authorId);
        return new PostAuthorView(MEMBER, Long.toString(authorId), profile == null ? "" : profile.getNickname(), gradeKey, label == null ? gradeKey : label,
                ROLE_USER, null, null, look == null ? null : look.decoration(), look == null ? null : look.avatarUrl(), look == null ? null : look.avatarStillUrl(), members.business(authorId),
                members.operator(authorId), null, null, aiView(members.ai(authorId)));
    }

    /**
     * 글 상세의 작성자 프로필(profile-privacy-contract) — 회원 글이고 작성자가 탈퇴(진행 중 포함)·삭제되지 않았을 때만 값: 공개 범위({@code PUBLIC}·{@code PRIVATE})와
     * 공개이고 소개가 비어 있지 않을 때의 자기소개. 비공개면 소개는 늘 null(글 보기 카드는 「비공개 프로필」, 공개인데 소개가 없으면 「자기소개가 없습니다」 — 사용자 2026-11-08).
     * 프로필은 {@link #members} 가 이미 읽어 둔 것을 쓰고 회원 상태만 기본 키로 한 번 읽는다 — 목록·댓글은 부르지 않는다(칸 null).
     */
    public PostAuthorView.AuthorProfileView detailProfile(Long authorId, AuthorMembers members) {
        ProfileEntity profile = authorId == null ? null : members.get(authorId);
        if (profile == null) {
            return null;
        }
        boolean present = users.findById(authorId).filter(u -> !u.isGone() && u.getStatus() != UserStatus.WITHDRAWING).isPresent();
        if (!present) {
            return null;
        }
        if (profile.isPrivate()) {
            return new PostAuthorView.AuthorProfileView("PRIVATE", null);
        }
        String bio = profile.getBio() == null || profile.getBio().isBlank() ? null : profile.getBio();
        return new PostAuthorView.AuthorProfileView("PUBLIC", bio);
    }

    /** 숨긴 글·지운 댓글의 작성자 —이름·IP·장식·아바타·사업자 뱃지·운영자 뱃지를 비운다(종류·회원 ID 는 그대로) */
    public static PostAuthorView blank(PostAuthorView author) {
        return new PostAuthorView(author.kind(), author.memberId(), "", author.grade(), author.gradeLabel(), author.role(), null, null, null, null, null, false, false, null, null, null);
    }

    private static PostAuthorView.AuthorAiView aiView(AiMemberDirectory.AiAuthor ai) {
        return ai == null ? null : new PostAuthorView.AuthorAiView(ai.provider().name(), ai.model());
    }

    /** IP 앞 두 자리 — IPv4 {@code 123.45.*.*}, IPv6 앞 두 묶음 {@code 2001:db8:*:*}. IP 가 없으면(보유기간이 지나 지움) null, 읽지 못하면 {@code *} */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.ofLiteral(ip.strip());
            byte[] b = address.getAddress();
            if (address instanceof Inet4Address) {
                return (b[0] & 0xff) + "." + (b[1] & 0xff) + ".*.*";
            }
            int g1 = ((b[0] & 0xff) << 8) | (b[1] & 0xff);
            int g2 = ((b[2] & 0xff) << 8) | (b[3] & 0xff);
            return Integer.toHexString(g1) + ":" + Integer.toHexString(g2) + ":*:*";
        } catch (IllegalArgumentException e) {
            return "*";
        }
    }
}
