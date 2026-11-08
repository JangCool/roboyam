package com.community.popup.entity;

import com.community.popup.dto.SitePopupFields;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 팝업 — V162__site_popup.sql 의 {@code site_popups}(docs/features/site-popup-contract.md). 관리자 앱이 만들고 고치며, 공개 서버는 {@code GET /api/site/popups} 를 그릴 때 읽는다.
 * 규칙(검사·정리·상태·version)은 {@link com.community.popup.service.SitePopups}. 「노출 중」 = {@link #isShowingAt}(노출 켬 + 기간 안).
 */
@Entity
@Table(name = "site_popups")
public class SitePopupEntity {

    @Id
    private Long id;

    private String name;
    private String kind;
    private Long imageFileId;
    private String imageAlt;
    private String link;
    private String heading;
    private String body;
    private String buttonLabel;
    private String target;
    private boolean showPc;
    private boolean showMobile;
    private String hideOption;
    /** 띄울 창 1~3(V164) — 같은 번호끼리 한 창에서 슬라이드 */
    private int windowNo = 1;
    private boolean visible;
    private Instant showFrom;
    private Instant showUntil;
    private int sortOrder;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected SitePopupEntity() {
    }

    /** 새 팝업 — 그림 파일은 {@code imageFileId}(지정을 마친 파일, 없으면 null) */
    public static SitePopupEntity create(long id, SitePopupFields f, Long imageFileId, int sortOrder, long operatorId, Instant now) {
        SitePopupEntity row = new SitePopupEntity();
        row.id = id;
        row.apply(f, imageFileId);
        row.sortOrder = sortOrder;
        row.createdBy = operatorId;
        row.createdAt = now;
        row.updatedBy = operatorId;
        row.updatedAt = now;
        return row;
    }

    /** 모든 칸을 고친다(차례는 그대로) */
    public void change(SitePopupFields f, Long imageFileId, long operatorId, Instant now) {
        apply(f, imageFileId);
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public void changeVisible(boolean visible, long operatorId, Instant now) {
        this.visible = visible;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    private void apply(SitePopupFields f, Long imageFileId) {
        this.name = f.name();
        this.kind = f.kind();
        this.imageFileId = imageFileId;
        this.imageAlt = f.imageAlt();
        this.link = f.link();
        this.heading = f.heading();
        this.body = f.body();
        this.buttonLabel = f.buttonLabel();
        this.target = f.target();
        this.showPc = f.showPc();
        this.showMobile = f.showMobile();
        this.hideOption = f.hideOption();
        this.windowNo = f.windowNo();
        this.visible = f.visible();
        this.showFrom = f.showFrom();
        this.showUntil = f.showUntil();
    }

    /** 노출을 켰고 지금이 노출 기간 안인가(시작 포함·끝 미포함, 비어 있으면 상시) */
    public boolean isShowingAt(Instant now) {
        return visible && (showFrom == null || !showFrom.isAfter(now)) && (showUntil == null || showUntil.isAfter(now));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** {@code IMAGE} · {@code TEXT} */
    public String getKind() {
        return kind;
    }

    public Long getImageFileId() {
        return imageFileId;
    }

    public String getImageAlt() {
        return imageAlt;
    }

    public String getLink() {
        return link;
    }

    public String getHeading() {
        return heading;
    }

    public String getBody() {
        return body;
    }

    public String getButtonLabel() {
        return buttonLabel;
    }

    /** {@code HOME} · {@code ALL} */
    public String getTarget() {
        return target;
    }

    public boolean isShowPc() {
        return showPc;
    }

    public boolean isShowMobile() {
        return showMobile;
    }

    /** {@code TODAY} · {@code WEEK} · {@code NEVER} · {@code NONE} */
    public String getHideOption() {
        return hideOption;
    }

    /** 띄울 창 1~3 */
    public int getWindowNo() {
        return windowNo;
    }

    public boolean isVisible() {
        return visible;
    }

    public Instant getShowFrom() {
        return showFrom;
    }

    public Instant getShowUntil() {
        return showUntil;
    }

    public int getSortOrder() {
        return sortOrder;
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
}
