package com.community.site.entity;

import com.community.site.dto.SiteFooterNotice;
import com.community.site.dto.SiteOperatorValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 운영 주체 한 행 — V158 의 {@code site_operator}(id = 1, 마이그레이션이 늘 만든다). 사업자 정보의 정본이다(커머스 사업자 정보의 겹치는 칸도 여기서 읽는다).
 * 값 검사는 부르는 쪽({@code SiteOperatorService})이 끝냈다. 개인 단계로 바꾸면 사업자 칸을 모두 비운다(DB CHECK 와 같다).
 */
@Entity
@Table(name = "site_operator")
public class SiteOperatorEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    private SiteOperatorStage stage;

    private String operatorName;
    private String contactEmail;
    private String phone;

    @Column(name = "privacy_officer_same")
    private boolean privacyOfficerSame;

    private String privacyOfficerName;
    private String privacyOfficerEmail;
    private String hostingProvider;
    private String mailProvider;
    private String businessName;
    private String representative;
    private String registrationNumber;
    private String address;
    private String mailOrderNumber;
    private String mailOrderAuthority;
    private String corporateNumber;

    /** 푸터 안내 문구(V177) — 줄을 {@code \n} 으로 이은 글, null = 숨김 */
    private String footerNotice;

    @Enumerated(EnumType.STRING)
    private SiteFooterNoticeScope footerNoticeScope;

    private Instant updatedAt;
    private Long updatedBy;

    protected SiteOperatorEntity() {
    }

    /** 값을 통째로 바꾼다(푸터 안내 문구 포함) — 개인 단계면 사업자 칸, 「같음」이면 보호책임자 칸, 법인이 아니면 법인등록번호를 비운다 */
    public void change(SiteOperatorValues v, SiteFooterNotice notice, long operatorId, Instant now) {
        this.footerNotice = notice.stored();
        this.footerNoticeScope = notice.scope();
        this.stage = v.stage();
        this.operatorName = v.operatorName();
        this.contactEmail = v.contactEmail();
        this.phone = v.phone();
        this.privacyOfficerSame = v.privacyOfficerSameAsOperator();
        this.privacyOfficerName = v.privacyOfficerSameAsOperator() ? null : v.privacyOfficerName();
        this.privacyOfficerEmail = v.privacyOfficerSameAsOperator() ? null : v.privacyOfficerEmail();
        this.hostingProvider = v.hostingProvider();
        this.mailProvider = v.mailProvider();
        SiteOperatorValues.Business b = v.stage().business() ? v.business() : null;
        this.businessName = b == null ? null : b.businessName();
        this.representative = b == null ? null : b.representative();
        this.registrationNumber = b == null ? null : b.registrationNumber();
        this.address = b == null ? null : b.address();
        this.mailOrderNumber = b == null ? null : b.mailOrderNumber();
        this.mailOrderAuthority = b == null ? null : b.mailOrderAuthority();
        this.corporateNumber = b == null || v.stage() != SiteOperatorStage.CORPORATION ? null : b.corporateNumber();
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /** 지금 값 — 개인 단계는 {@code business} 가 null */
    public SiteOperatorValues values() {
        SiteOperatorValues.Business business = stage.business()
                ? new SiteOperatorValues.Business(nz(businessName), nz(representative), nz(registrationNumber), nz(address), mailOrderNumber, mailOrderAuthority,
                        corporateNumber)
                : null;
        return new SiteOperatorValues(stage, nz(operatorName), nz(contactEmail), phone, privacyOfficerSame, privacyOfficerName, privacyOfficerEmail, hostingProvider,
                mailProvider, business);
    }

    /** 지금 푸터 안내 문구 — 줄이 없으면 빈 목록(숨김) */
    public SiteFooterNotice footerNotice() {
        return new SiteFooterNotice(SiteFooterNotice.split(footerNotice), footerNoticeScope);
    }

    public SiteOperatorStage getStage() {
        return stage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "SiteOperatorEntity[stage=" + stage + ", updatedAt=" + updatedAt + "]";
    }
}
