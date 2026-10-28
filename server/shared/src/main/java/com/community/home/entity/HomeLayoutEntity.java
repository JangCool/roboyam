package com.community.home.entity;

import com.community.home.dto.HomeGridLayout;
import com.community.home.service.HomeLayoutJson;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 테마 하나의 홈 배치({@code home_layouts}, V105) — 테마마다 한 행. 행이 없으면 그 테마의 기본 배치({@code HomeLayoutDefaults})를 쓴다.
 * 자리 목록은 jsonb 이고 가젯 ID 는 같은 테마의 {@code home_gadgets} 행을 가리킨다(검사·정리는 저장하는 쪽이 끝낸다).
 */
@Entity
@Table(name = "home_layouts")
public class HomeLayoutEntity {

    @Id
    private String theme;

    private short columns;

    /** 자리 목록 JSON — {@code [{gadgetId, x, y, w, h}]} */
    @JdbcTypeCode(SqlTypes.JSON)
    private String items;

    private Long updatedBy;
    private Instant updatedAt;

    protected HomeLayoutEntity() {
    }

    public static HomeLayoutEntity create(String theme, HomeGridLayout layout, long operatorId, Instant now) {
        HomeLayoutEntity row = new HomeLayoutEntity();
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
