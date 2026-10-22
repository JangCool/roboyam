package com.community.board.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.post.dto.response.PostAuthorView;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 글·댓글 작성자 표시({@link PostAuthorView}) — 회원은 닉네임·등급, 비회원은 입력한 이름 + 작성 IP 앞 두 자리.
 * 회원 정보는 목록마다 한 번에 읽는다({@link #members}). 작성 IP 원문은 응답에 싣지 않는다.
 */
@Component
public class AuthorViews {

    static final String MEMBER = "MEMBER";
    static final String GUEST = "GUEST";
    /** 회원 작성자의 공개 역할 — 게시판 매니저·스텝 표시는 공개 사이트에 내지 않는다(docs/domain/operator-permissions.md) */
    static final String ROLE_USER = "USER";

    private final ProfileRepository profiles;

    public AuthorViews(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** 회원 ID → 프로필(닉네임·등급) */
    public Map<Long, ProfileEntity> members(Collection<Long> ids) {
        Map<Long, ProfileEntity> out = new HashMap<>();
        List<Long> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!wanted.isEmpty()) {
            profiles.findAllById(wanted).forEach(p -> out.put(p.getUserId(), p));
        }
        return out;
    }

    /**
     * @param authorId 회원 글이면 회원 ID, 비회원 글이면 null
     * @param members  {@link #members} 로 읽어 둔 프로필
     */
    public PostAuthorView of(Long authorId, String guestName, String authorIp, Map<Long, ProfileEntity> members, BoardCatalogSnapshot catalog) {
        if (authorId == null) {
            return new PostAuthorView(GUEST, null, guestName == null ? "" : guestName, null, null, null, maskIp(authorIp));
        }
        ProfileEntity profile = members.get(authorId);
        String gradeKey = profile == null ? null : profile.getGradeKey();
        String label = gradeKey == null ? null : catalog.gradeLabel(gradeKey);
        return new PostAuthorView(MEMBER, Long.toString(authorId), profile == null ? "" : profile.getNickname(), gradeKey, label == null ? gradeKey : label,
                ROLE_USER, null);
    }

    /** 숨긴 글·지운 댓글의 작성자 — 이름·IP 를 비운다(종류·회원 ID 는 그대로) */
    public static PostAuthorView blank(PostAuthorView author) {
        return new PostAuthorView(author.kind(), author.memberId(), "", author.grade(), author.gradeLabel(), author.role(), null);
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
