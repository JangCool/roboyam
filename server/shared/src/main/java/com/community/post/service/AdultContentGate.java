package com.community.post.service;

import com.community.identity.service.IdentityVerificationService;
import com.community.post.entity.PostEntity;
import com.community.post.exception.PostAdultOnlyException;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 19금 글 판정 한 곳 — docs/features/adult-content-contract.md. <b>성인</b> = 로그인 + 본인인증 완료 + 만 19세 이상({@link IdentityVerificationService#isAdult}).
 *
 * <ul>
 *   <li>목록(게시판·최신글·검색·프로필·가젯)은 성인이 아니면 19금 글을 <b>질의에서 뺀다</b>({@code PostRepositoryQDSL#adultVisible}) — 쪽 수·건수도 뺀 기준이다.</li>
 *   <li>글 하나를 여는 API(상세·댓글·투표·추천·신고·고치기)는 {@code GroupPostGate#resolve} 가 {@link #requireViewable} 로 막는다(403 {@code POST_ADULT_ONLY}).</li>
 *   <li>19금 글을 쓰는(19금으로 고르거나 성인 전용 말머리를 고르는) 사람도 성인이어야 한다({@code PostWriteService}).</li>
 * </ul>
 */
@Component
public class AdultContentGate {

    private final IdentityVerificationService identity;

    public AdultContentGate(IdentityVerificationService identity) {
        this.identity = identity;
    }

    /** 이 사람이 19금 글을 볼 수 있는가(비회원 false) */
    public boolean adult(Long viewerId) {
        return identity.isAdult(viewerId);
    }

    /** 19금 글이면 성인만 — 아니면 403 {@code POST_ADULT_ONLY}(사유 {@code LOGIN}·{@code IDENTITY}·{@code UNDERAGE}) */
    public void requireViewable(PostEntity post, Long viewerId) {
        if (!post.isAdult() || adult(viewerId)) {
            return;
        }
        if (viewerId == null) {
            throw new PostAdultOnlyException(PostAdultOnlyException.LOGIN, null);
        }
        LocalDate adultFrom = identity.adultFrom(viewerId).orElse(null);
        throw new PostAdultOnlyException(adultFrom == null ? PostAdultOnlyException.IDENTITY : PostAdultOnlyException.UNDERAGE, adultFrom);
    }
}
