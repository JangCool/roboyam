package com.community.title.entity;

import com.community.title.dto.TitleSpec;
import com.community.title.service.TitleConditions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 칭호 정의 한 줄 — {@code titles}(V240, member-title-contract 「구현 결정」 2). 기본 칭호({@code builtin})는 키·받는 방법을 바꿀 수 없다. 꺼진 칭호는 어디에도 보이지 않고 자동 지급·신청도 멈춘다
 * (가진 줄은 남는다). 조건은 JSON 배열 글자로 들고 {@link TitleConditions} 가 읽고 쓴다.
 */
@Entity
@Table(name = "titles")
public class TitleEntity {

    @Id
    private Long id;
    private String titleKey;
    private String name;
    private String nameEn;
    private String icon;
    private String chip;
    @Enumerated(EnumType.STRING)
    private TitleMethod method;
    @Enumerated(EnumType.STRING)
    private TitleReviewer reviewer;
    private Long boardId;
    private String boardSlug;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String conditions;
    @Enumerated(EnumType.STRING)
    private TitleMetric tierMetric;
    private Integer tierBronze;
    private Integer tierSilver;
    private Integer tierGold;
    private boolean benefitTrialPriority;
    private boolean benefitMagazine;
    private boolean benefitAvatarFrame;
    private boolean dormantHide;
    private boolean builtin;
    private boolean enabled;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    private Long updatedBy;

    protected TitleEntity() {
    }

    /** 운영자가 만든 칭호(기본 칭호가 아니다) — 키는 서버가 정한다 */
    public static TitleEntity create(long id, String titleKey, TitleSpec spec, long operatorId, Instant now) {
        TitleEntity e = new TitleEntity();
        e.id = id;
        e.titleKey = titleKey;
        e.builtin = false;
        e.createdAt = now;
        e.apply(spec, operatorId, now);
        e.method = spec.method();
        return e;
    }

    /** 고치기 — 기본 칭호는 받는 방법을 그대로 둔다(부르는 쪽이 먼저 막는다) */
    public void change(TitleSpec spec, long operatorId, Instant now) {
        apply(spec, operatorId, now);
        if (!builtin) {
            this.method = spec.method();
        }
    }

    private void apply(TitleSpec spec, long operatorId, Instant now) {
        this.name = spec.name();
        this.nameEn = spec.nameEn();
        this.icon = spec.icon();
        this.chip = spec.chip();
        this.reviewer = spec.reviewer();
        this.boardId = spec.boardId();
        this.boardSlug = spec.boardId() == null ? spec.boardSlug() : null; // 게시판을 고르면 ID 만 남는다(기본 칭호의 처음 slug 는 게시판이 생기면 ID 로 굳는다)
        this.conditions = TitleConditions.write(spec.conditions());
        this.tierMetric = spec.tierMetric();
        this.tierBronze = spec.tierBronze();
        this.tierSilver = spec.tierSilver();
        this.tierGold = spec.tierGold();
        this.benefitTrialPriority = spec.benefitTrialPriority();
        this.benefitMagazine = spec.benefitMagazine();
        this.benefitAvatarFrame = spec.benefitAvatarFrame();
        this.dormantHide = spec.dormantHide();
        this.enabled = spec.enabled();
        this.sortOrder = spec.sortOrder();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 단계 칭호인가 */
    public boolean isTiered() {
        return tierMetric != null;
    }

    /** 그 수가 닿은 가장 높은 단계(동 문턱 미만이면 null) */
    public TitleTier tierFor(long count) {
        if (!isTiered()) {
            return null;
        }
        if (count >= tierGold) {
            return TitleTier.GOLD;
        }
        if (count >= tierSilver) {
            return TitleTier.SILVER;
        }
        return count >= tierBronze ? TitleTier.BRONZE : null;
    }

    /** 단계의 문턱 */
    public Integer thresholdOf(TitleTier tier) {
        if (!isTiered() || tier == null) {
            return null;
        }
        return switch (tier) {
            case BRONZE -> tierBronze;
            case SILVER -> tierSilver;
            case GOLD -> tierGold;
        };
    }

    public Long getId() {
        return id;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getIcon() {
        return icon;
    }

    public String getChip() {
        return chip;
    }

    public TitleMethod getMethod() {
        return method;
    }

    public TitleReviewer getReviewer() {
        return reviewer;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getBoardSlug() {
        return boardSlug;
    }

    public String getConditions() {
        return conditions;
    }

    public TitleMetric getTierMetric() {
        return tierMetric;
    }

    public Integer getTierBronze() {
        return tierBronze;
    }

    public Integer getTierSilver() {
        return tierSilver;
    }

    public Integer getTierGold() {
        return tierGold;
    }

    public boolean isBenefitTrialPriority() {
        return benefitTrialPriority;
    }

    public boolean isBenefitMagazine() {
        return benefitMagazine;
    }

    public boolean isBenefitAvatarFrame() {
        return benefitAvatarFrame;
    }

    public boolean isDormantHide() {
        return dormantHide;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
