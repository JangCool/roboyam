package com.community.trial.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.trial.dto.TrialViews;
import com.community.trial.exception.TrialException;
import com.community.trial.service.TrialService.Actor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고주 화면(사업자 센터 {@code /business/trials} — trial-group-contract 「구현 결정」 8). 광고주 = 회원의 사업자 신원({@code business_profiles})에 이어진 {@code ad_advertisers}
 * (없으면 403 {@code TRIAL_NOT_ADVERTISER}). 자기 모집(초안 빼고)만 보고, 신청자 후보 추천 · (보내는 쪽 광고주일 때) 발송 전 사진·송장 · 반납 받음·반납 사진 · 파손 신고를 한다.
 * 회원 주소·연락처는 동의 범위(광고주가 보내는 모집의 뽑힌 줄 — 신청 때 제공 동의)만 보이고, 볼 때마다 개인정보 제공 감사 기록을 남긴다.
 */
@Service
public class TrialBusinessService {

    private final BusinessProfileRepository profiles;
    private final AdAdvertiserStore advertisers;
    private final TrialService trials;
    private final TrialProvisionLog provisions;

    public TrialBusinessService(BusinessProfileRepository profiles, AdAdvertiserStore advertisers, TrialService trials, TrialProvisionLog provisions) {
        this.profiles = profiles;
        this.advertisers = advertisers;
        this.trials = trials;
        this.provisions = provisions;
    }

    /** 이 회원의 광고주 — 없으면 403 */
    @Transactional(readOnly = true)
    public Actor actor(long userId, String ip) {
        long advertiserId = profiles.findByUserId(userId).flatMap(p -> advertisers.byProfile(p.getId())).map(AdAdvertiserStore.AdvertiserRow::id)
                .orElseThrow(TrialException::notAdvertiser);
        return Actor.advertiser(userId, advertiserId, ip);
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
