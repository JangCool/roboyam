package com.community.trial.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.entity.BusinessStatus;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessServiceRepository;
import com.community.trial.dto.TrialViews;
import com.community.trial.exception.TrialException;
import com.community.trial.service.TrialService.Actor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고주 화면(사업자 센터 {@code /business/trials} — trial-group-contract 「구현 결정」 8). 광고주 = 회원의 사업자 신원({@code business_profiles})에 이어진 {@code ad_advertisers}
 * (없으면 403 {@code TRIAL_NOT_ADVERTISER}). 자기 모집만 보고(초안은 결제 대상 선결제 초안만 「공개 전」으로 — {@link TrialPayments#advertiserSeesDraft}, 처리는 못 한다), 신청자 후보 추천 · (보내는 쪽 광고주일 때) 발송 전 사진·송장 · 반납 받음·반납 사진 · 파손 신고를 한다.
 * 회원 주소·연락처는 동의 범위(광고주가 보내는 모집의 뽑힌 줄 — 신청 때 제공 동의)만 보이고, 볼 때마다 개인정보 제공 감사 기록을 남긴다.
 *
 * <p><b>자격</b>(점검 D5 — 광고 이미지 올리기와 같은 기준): 사업자 신원 승인(재심사 중이면 진행 중 건이 그대로 도므로 허용) · 광고 서비스 승인 · 광고주 보관 아님. 하나라도 아니면
 * 403 {@code TRIAL_ADVERTISER_BLOCKED} — 사업자 정지·탈퇴·광고 서비스 정지면 진행 중 모집은 운영자가 대신 처리한다(점검 D7).
 */
@Service
public class TrialBusinessService {

    private final BusinessProfileRepository profiles;
    private final BusinessServiceRepository services;
    private final AdAdvertiserStore advertisers;
    private final TrialService trials;
    private final TrialProvisionLog provisions;

    public TrialBusinessService(BusinessProfileRepository profiles, BusinessServiceRepository services, AdAdvertiserStore advertisers, TrialService trials,
                                TrialProvisionLog provisions) {
        this.profiles = profiles;
        this.services = services;
        this.advertisers = advertisers;
        this.trials = trials;
        this.provisions = provisions;
    }

    /** 이 회원의 광고주 — 없으면 403 {@code TRIAL_NOT_ADVERTISER}, 자격이 없으면 403 {@code TRIAL_ADVERTISER_BLOCKED} */
    @Transactional(readOnly = true)
    public Actor actor(long userId, String ip) {
        BusinessProfileEntity profile = profiles.findByUserId(userId).orElseThrow(TrialException::notAdvertiser);
        AdAdvertiserStore.AdvertiserRow advertiser = advertisers.byProfile(profile.getId()).orElseThrow(TrialException::notAdvertiser);
        boolean identity = profile.getStatus() == BusinessStatus.APPROVED || (profile.getStatus() == BusinessStatus.PENDING && profile.isReReview());
        boolean adService = services.findByProfileIdAndService(profile.getId(), BusinessServiceKind.AD).filter(s -> s.getStatus() == BusinessServiceStatus.APPROVED)
                .isPresent();
        if (!identity || !adService || advertiser.archivedAt() != null) {
            throw TrialException.advertiserBlocked();
        }
        return Actor.advertiser(userId, advertiser.id(), ip);
    }

    public List<TrialViews.TrialRow> list(long userId) {
        return trials.listOf(actor(userId, null).advertiserId());
    }

    @Transactional
    public TrialViews.BusinessTrialDetail detail(long userId, String ip, String trialIdText) {
        Actor actor = actor(userId, ip);
        TrialViews.TrialDetail detail = trials.detail(trialIdText, actor);
        provisions.viewed(actor, detail);
        return new TrialViews.BusinessTrialDetail(detail, trials.applicants(trialIdText, actor));
    }

    public void recommend(long userId, String applicationIdText, Boolean on) {
        trials.recommend(actor(userId, null), applicationIdText, on);
    }

    public TrialViews.Uploaded uploadPhoto(long userId, String trialIdText, String kind, byte[] data) {
        return trials.uploadPhoto(actor(userId, null), trialIdText, kind, data);
    }

    /** 사진 보기(점검 D3) — 자기 모집의 붙은 사진 · 자기가 올린 붙기 전 사진 */
    public TrialViews.PhotoBytes photo(long userId, String photoIdText) {
        return trials.photo(actor(userId, null), photoIdText);
    }

    public void ship(long userId, String ip, String participantIdText, TrialViews.ShipmentRequest body) {
        trials.ship(actor(userId, ip), participantIdText, body);
    }

    public void confirmReturn(long userId, String ip, String participantIdText, TrialViews.PhotosRequest body) {
        trials.confirmReturn(actor(userId, ip), participantIdText, body);
    }

    public TrialViews.DamageReport reportDamage(long userId, String ip, String participantIdText, TrialViews.DamageRequest body) {
        return trials.reportDamage(actor(userId, ip), participantIdText, body);
    }
}
