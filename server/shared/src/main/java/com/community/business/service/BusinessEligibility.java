package com.community.business.service;

import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.exception.BusinessException;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessProfileRepositoryQDSL;
import com.community.business.repository.master.BusinessServiceRepository;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업자 이용 자격 판정 <b>한 곳</b>(business-contract 「모델」) — 신원 {@code APPROVED} 이고 그 서비스 줄 {@code APPROVED}. 광고·핫딜 신청 건은 이것만 부른다.
 * 재심사 중(F7)·정지·반려·탈퇴면 자격이 없다 — 이미 예약·진행 중인 건을 어떻게 할지는 신청 건 쪽 규칙이다(재심사 중엔 그대로 진행).
 */
@Service
@Transactional(readOnly = true)
public class BusinessEligibility {

    /** 자격이 있는 사업자 — 신청 건이 거래 근거로 남길 값(상호·끝 4자리) */
    public record ApprovedBusiness(long profileId, long userId, String companyName, String bizNoLast4) {
    }

    private final BusinessProfileRepository profiles;
    private final BusinessServiceRepository services;
    private final BusinessProfileRepositoryQDSL profileQueries;

    public BusinessEligibility(BusinessProfileRepository profiles, BusinessServiceRepository services, BusinessProfileRepositoryQDSL profileQueries) {
        this.profiles = profiles;
        this.services = services;
        this.profileQueries = profileQueries;
    }

    /**
     * 사업자 뱃지가 붙는 회원 ID(글·댓글 작성자·쪽지 상대·소모임 사람·공개 프로필의 {@code business}) — 사업자 신원 {@code APPROVED} 만. 재심사 중({@code PENDING})·반려·정지·탈퇴는
     * 빠지고 서비스별(광고·핫딜) 승인은 보지 않는다. 본인이 숨길 수 없다. 목록 하나에 <b>한 문장</b>(줄마다 읽지 않는다), null 은 건너뛴다.
     */
    public Set<Long> approvedUserIds(Collection<Long> userIds) {
        return profileQueries.approvedUserIds(userIds.stream().filter(Objects::nonNull).distinct().toList());
    }

    /** 그 서비스를 쓸 수 있는 승인 사업자 — 아니면 비어 있다 */
    public Optional<ApprovedBusiness> approvedProfileOf(long userId, BusinessServiceKind service) {
        return profiles.findByUserId(userId).filter(p -> p.getStatus() == BusinessStatus.APPROVED)
                .filter(p -> services.findByProfileIdAndService(p.getId(), service).filter(s -> s.getStatus() == BusinessServiceStatus.APPROVED).isPresent())
                .map(BusinessEligibility::approved);
    }

    /**
     * 자격이 없으면 409 — 재심사 중이면 {@code BUSINESS_RE_REVIEW}(새 신청만 막힌다는 안내), 그 밖 {@code BUSINESS_NOT_APPROVED}.
     */
    public ApprovedBusiness requireApproved(long userId, BusinessServiceKind service) {
        Optional<ApprovedBusiness> found = approvedProfileOf(userId, service);
        if (found.isPresent()) {
            return found.get();
        }
        boolean reReview = profiles.findByUserId(userId).map(BusinessProfileEntity::isReReview).orElse(false);
        throw reReview ? BusinessException.reReview() : BusinessException.notApproved();
    }

    /**
     * 회원 화면에 보일 사업자 상태(로그인 정보 {@code businessStatus}·{@code GET /api/business/me}) — 신청 전·탈퇴 {@code NONE}, 재심사 중은 {@code APPROVED}
     * (이미 예약·진행 중인 건이 그대로 돌고 화면은 「승인 + 재심사 중」 안내를 보인다 — F7), 그 밖은 신원 상태 그대로.
     */
    public static String memberStatus(BusinessProfileEntity p) {
        if (p == null || p.getStatus() == BusinessStatus.WITHDRAWN) {
            return "NONE";
        }
        return p.isReReview() ? BusinessStatus.APPROVED.name() : p.getStatus().name();
    }

    public String memberStatusOf(long userId) {
        return memberStatus(profiles.findByUserId(userId).orElse(null));
    }

    private static ApprovedBusiness approved(BusinessProfileEntity p) {
        return new ApprovedBusiness(p.getId(), p.getUserId(), p.getCompanyName(), p.getBizNoLast4());
    }
}
