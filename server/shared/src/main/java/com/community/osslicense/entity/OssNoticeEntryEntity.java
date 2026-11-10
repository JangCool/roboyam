package com.community.osslicense.entity;

import com.community.osslicense.dto.OssNoticeEntryFields;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 오픈소스 라이선스 고지의 직접 추가 항목 — V181__oss_notice.sql 의 {@code oss_notice_entries}(docs/features/oss-licenses-contract.md).
 * 관리자 앱이 만들고 고치며, 공개 서버는 숨기지 않은 것만 이름순으로 내보낸다. 칸 규칙은 {@link com.community.osslicense.service.OssNotices}.
 */
@Entity
@Table(name = "oss_notice_entries")
public class OssNoticeEntryEntity {

    @Id
    private Long id;

    private String name;
    private String version;
    private String license;
    private String copyright;
    private String licenseText;
    private String sourceUrl;
    private String category;
    private boolean hidden;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected OssNoticeEntryEntity() {
    }

    public static OssNoticeEntryEntity create(long id, OssNoticeEntryFields f, long operatorId, Instant now) {
        OssNoticeEntryEntity row = new OssNoticeEntryEntity();
        row.id = id;
        row.apply(f);
        row.createdBy = operatorId;
        row.createdAt = now;
        row.updatedBy = operatorId;
        row.updatedAt = now;
        return row;
    }

    /** 모든 칸을 고친다 */
    public void change(OssNoticeEntryFields f, long operatorId, Instant now) {
        apply(f);
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public void changeHidden(boolean hidden, long operatorId, Instant now) {
        this.hidden = hidden;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    private void apply(OssNoticeEntryFields f) {
        this.name = f.name();
        this.version = f.version();
        this.license = f.license();
        this.copyright = f.copyright();
        this.licenseText = f.licenseText();
        this.sourceUrl = f.sourceUrl();
        this.category = f.category();
        this.hidden = f.hidden();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getLicense() {
        return license;
    }

    public String getCopyright() {
        return copyright;
    }

    public String getLicenseText() {
        return licenseText;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getCategory() {
        return category;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "OssNoticeEntryEntity[id=" + id + ", name=" + name + ", category=" + category + ", hidden=" + hidden + "]";
    }
}
