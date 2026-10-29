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
 * 테마 하나의 게시판 레이아웃({@code board_layouts}, V110) — 테마마다 한 행, 모든 게시판에 같이 쓴다. 행이 없으면 기본 배치({@code BoardLayoutDefaults} — 본문만, 두 화면 켬).
 * 배치 모양은 {@link HomeLayoutEntity} 와 같고(가젯 ID 는 같은 테마의 게시판 레이아웃 {@code home_gadgets} 행) 적용 화면(게시판 목록 · 글 보기) 스위치가 더 있다.
 */
@Entity
@Table(name = "board_layouts")
public class BoardLayoutEntity {

    @Id
    private String theme;

    /** 게시판 목록 {@code /boards/{slug}} 에 쓰는가 */
    private boolean listEnabled;
    /** 글 보기 {@code /posts/{id}} 에 쓰는가 */
    private boolean postEnabled;

    private short columns;

    /** 자리 목록 JSON — {@code [{gadgetId, x, y, w, h}]} */
    @JdbcTypeCode(SqlTypes.JSON)
    private String items;

    private Long updatedBy;
    private Instant updatedAt;

    protected BoardLayoutEntity() {
    }

    public static BoardLayoutEntity create(String theme, boolean listEnabled, boolean postEnabled, HomeGridLayout layout, long operatorId, Instant now) {
        BoardLayoutEntity row = new BoardLayoutEntity();
        row.theme = theme;
        row.change(listEnabled, postEnabled, layout, operatorId, now);
        return row;
    }

    public void change(boolean listEnabled, boolean postEnabled, HomeGridLayout layout, long operatorId, Instant now) {
        this.listEnabled = listEnabled;
        this.postEnabled = postEnabled;
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

    public boolean isListEnabled() {
        return listEnabled;
    }

    public boolean isPostEnabled() {
        return postEnabled;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
