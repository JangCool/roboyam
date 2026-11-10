package com.community.osslicense.service;

import com.community.osslicense.dto.response.OssNoticePublicEntryView;
import com.community.osslicense.dto.response.OssNoticePublicResponse;
import com.community.osslicense.entity.OssNoticeSettingEntity;
import com.community.osslicense.repository.master.OssNoticeEntryRepositoryQDSL;
import com.community.osslicense.repository.master.OssNoticeSettingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 공개 오픈소스 라이선스 고지를 DB 에서 읽는다(사본 {@link PublicOssNoticeService} 가 부른다) — 안내 문구 + 숨기지 않은 직접 추가 항목(이름순)을 한 스냅숏으로 */
@Component
public class OssNoticePublicReader {

    private final OssNoticeSettingRepository settings;
    private final OssNoticeEntryRepositoryQDSL entries;

    public OssNoticePublicReader(OssNoticeSettingRepository settings, OssNoticeEntryRepositoryQDSL entries) {
        this.settings = settings;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public OssNoticePublicResponse read() {
        String intro = settings.findById(OssNoticeSettingEntity.ROW_ID).map(OssNoticeSettingEntity::getIntro).orElse(null);
        return new OssNoticePublicResponse(intro, entries.findVisibleByName().stream()
                .map(e -> new OssNoticePublicEntryView(Long.toString(e.getId()), e.getName(), e.getLicense(), e.getCopyright(), e.getLicenseText(),
                        e.getSourceUrl(), e.getCategory()))
                .toList());
    }
}
