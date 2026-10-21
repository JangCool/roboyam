package com.community.site.service;

import com.community.site.dto.SiteInput;
import com.community.site.dto.SiteValues;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 이름·공개 주소·슬로건의 입력 검사와 저장. 설치 마법사가 쓴다(설치 뒤 설정 화면의 읽기·수정은 그 기능을 만들 때 더한다).
 */
@Service
public class SiteSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SiteSettingsService.class);

    private final SiteSettingRepository settings;

    public SiteSettingsService(SiteSettingRepository settings) {
        this.settings = settings;
    }

    /**
     * 입력을 검사하고 저장할 모양으로 다듬는다. 위반은 {@code errors} 에 {@code <fieldPrefix>name} · {@code <fieldPrefix>publicUrl} · {@code <fieldPrefix>tagline} 로 모은다
     * (설치 마법사는 {@code site.} 을 붙인다). 입력이 통째로 없어도 이름·공개 주소는 오류다 — 슬로건은 선택이라 없어도 오류가 아니다.
     */
    public SiteValues normalize(Map<String, MessageRef> errors, String fieldPrefix, SiteInput input) {
        String name = SitePolicy.normalizeName(input == null ? null : input.name());
        String publicUrl = input == null || input.publicUrl() == null ? null : input.publicUrl().strip();
        String tagline = SitePolicy.normalizeTagline(input == null ? null : input.tagline());
        SitePolicy.nameViolation(name).ifPresent(message -> errors.put(fieldPrefix + "name", message));
        SitePolicy.publicUrlViolation(publicUrl).ifPresent(message -> errors.put(fieldPrefix + "publicUrl", message));
        SitePolicy.taglineViolation(tagline).ifPresent(message -> errors.put(fieldPrefix + "tagline", message));
        return new SiteValues(name, errors.containsKey(fieldPrefix + "publicUrl") ? publicUrl : SitePolicy.normalizePublicUrl(publicUrl), tagline);
    }

    /** 저장된 사이트 설정(이름·공개 주소·슬로건). 설치가 끝나면 항상 있다 — 행이 없으면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<SiteValues> current() {
        return settings.findById(SiteSettingEntity.ROW_ID)
                .map(row -> new SiteValues(row.getSiteName(), row.getPublicUrl(), row.getTagline()));
    }

    /**
     * 검사를 마친 값을 처음 저장한다. <b>트랜잭션은 부르는 쪽이 연다</b> — 설치 마법사는 이것을 설치 트랜잭션 안에서 불러 실패하면 함께 되돌린다.
     */
    @Transactional
    public void create(SiteValues values, long adminId, Instant now) {
        settings.persist(SiteSettingEntity.create(values.name(), values.publicUrl(), values.tagline(), adminId, now));
        log.info("사이트 설정 저장(슬로건 {}) userId={}", values.tagline() == null ? "없음" : "있음", adminId);
    }
}
