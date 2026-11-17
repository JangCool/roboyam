package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 체험단 설정 — {@code trial_settings}(V241, 행 하나 id = 1). 칸 뜻은 마이그레이션 주석 */
@Entity
@Table(name = "trial_settings")
public class TrialSettingEntity {

    public static final int ID = 1;

    @Id
    private Integer id;
    private long givePriceLimit;
    private String disclosureText;
    private String sponsorDisclosureText;
    private int returnRemindFirstDays;
    private int returnRemindSecondDays;
    private int unreturnedDays;
    private int reviewDays;
    private int lateBanDays;
    private int lateRevokeStrikes;
    private int proofBonusPoints;
    private int proofPhotoDays;
    private int stockKeepDays;
    private Instant updatedAt;
    private Long updatedBy;

    protected TrialSettingEntity() {
    }

    /** 설정 값 묶음(읽기·저장) */
    public record Values(long givePriceLimit, String disclosureText, String sponsorDisclosureText, int returnRemindFirstDays, int returnRemindSecondDays,
                         int unreturnedDays, int reviewDays, int lateBanDays, int lateRevokeStrikes, int proofBonusPoints, int proofPhotoDays, int stockKeepDays) {
    }

    public Values values() {
        return new Values(givePriceLimit, disclosureText, sponsorDisclosureText, returnRemindFirstDays, returnRemindSecondDays, unreturnedDays, reviewDays, lateBanDays,
                lateRevokeStrikes, proofBonusPoints, proofPhotoDays, stockKeepDays);
    }

    public void change(Values v, long operatorId, Instant now) {
        this.givePriceLimit = v.givePriceLimit();
        this.disclosureText = v.disclosureText();
        this.sponsorDisclosureText = v.sponsorDisclosureText();
        this.returnRemindFirstDays = v.returnRemindFirstDays();
        this.returnRemindSecondDays = v.returnRemindSecondDays();
        this.unreturnedDays = v.unreturnedDays();
        this.reviewDays = v.reviewDays();
        this.lateBanDays = v.lateBanDays();
        this.lateRevokeStrikes = v.lateRevokeStrikes();
        this.proofBonusPoints = v.proofBonusPoints();
        this.proofPhotoDays = v.proofPhotoDays();
        this.stockKeepDays = v.stockKeepDays();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Integer getId() {
        return id;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
