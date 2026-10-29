package com.community.home.entity;

import com.community.home.dto.HomeGridLayout;
import com.community.home.service.HomeLayoutJson;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 가젯 페이지 하나의 테마 하나 배치({@code gadget_page_layouts}, V108) — 행이 없으면 페이지의 처음 배치를 쓴다. 모양은 {@link HomeLayoutEntity} 와 같다
 * (자리 목록 jsonb, 가젯 ID 는 같은 페이지 · 테마의 {@code home_gadgets} 행).
 */
@Entity
@Table(name = "gadget_page_layouts")
@IdClass(GadgetPageLayoutId.class)
public class GadgetPageLayoutEntity {

    @Id
    private Long pageId;

    @Id
    private String theme;

    private short columns;

    @JdbcTypeCode(SqlTypes.JSON)
    private String items;

    private Long updatedBy;
    private Instant updatedAt;

    protected GadgetPageLayoutEntity() {
    }

    public static GadgetPageLayoutEntity create(long pageId, String theme, HomeGridLayout layout, long operatorId, Instant now) {
        GadgetPageLayoutEntity row = new GadgetPageLayoutEntity();
        row.pageId = pageId;
        row.theme = theme;
        row.change(layout, operatorId, now);
        return row;
    }

    public void change(HomeGridLayout layout, long operatorId, Instant now) {
        columns = (short) layout.columns();
        items = HomeLayoutJson.writeItems(layout.items());
        updatedBy = operatorId;
        updatedAt = now;
    }

    public HomeGridLayout toLayout() {
        return new HomeGridLayout(columns, HomeLayoutJson.readItems(items));
    }

    public Long getPageId() {
        return pageId;
    }

    public String getTheme() {
        return theme;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
