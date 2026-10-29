package com.community.home.entity;

import com.community.home.dto.HomeGadgetSpec;
import com.community.home.service.HomeLayoutJson;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * 홈 가젯 한 개({@code home_gadgets}, V105) — 테마에 딸린다. 가젯 페이지의 가젯은 {@code pageId}(V108)가 있고 페이지 · 테마에 딸린다(없으면 홈 가젯).
 * 게시판 레이아웃의 가젯은 {@code boardLayout}(V110)이 참이다(테마에 딸림, 페이지 없음 — 고정 칸 「게시판 본문」도 종류 {@code BOARD_BODY} 인 행이다).
 * 운영자는 넣기·빼기와 ⚙ 설정 창의 칸({@link #change} — 제목 · 노출 · 꾸미기 · 내용 설정, gadget-page-contract 「가젯 설정」)을 고친다. 꾸미기·설정은 보낸 칸만 덮고
 * 나머지(더보기 · {@code badge} 처럼 화면에서 고치지 않는 칸)는 만들 때의 값(테마 기본 배치의 시드 또는 종류별 처음 값)을 그대로 둔다. 모르는 칸도 보존하려고 jsonb 글자로 둔다.
 */
@Entity
@Table(name = "home_gadgets")
public class HomeGadgetEntity {

    @Id
    private Long id;

    private String theme;
    /** 가젯 페이지 ID — 홈 가젯이면 null */
    private Long pageId;
    /** 게시판 레이아웃의 가젯인가 */
    private boolean boardLayout;
    private String type;
    private String title;
    private boolean desktop;
    private boolean mobile;

    @JdbcTypeCode(SqlTypes.JSON)
    private String style;

    @JdbcTypeCode(SqlTypes.JSON)
    private String config;

    /** 「더보기」 {@code {label, to}} — 없으면 null */
    @JdbcTypeCode(SqlTypes.JSON)
    private String more;

    private Instant createdAt;

    protected HomeGadgetEntity() {
    }

    /** 새 가젯 — {@code spec} 의 ID 는 쓰지 않고 {@code id} 를 쓴다. {@code pageId} 가 null 이면 홈 가젯 */
    public static HomeGadgetEntity create(long id, String theme, Long pageId, HomeGadgetSpec spec, Instant now) {
        return create(id, theme, pageId, false, spec, now);
    }

    /** 새 가젯 — {@code boardLayout} 이면 게시판 레이아웃의 가젯({@code pageId} 는 null 이어야 한다, V110 CHECK) */
    public static HomeGadgetEntity create(long id, String theme, Long pageId, boolean boardLayout, HomeGadgetSpec spec, Instant now) {
        HomeGadgetEntity row = new HomeGadgetEntity();
        row.id = id;
        row.theme = theme;
        row.pageId = pageId;
        row.boardLayout = boardLayout;
        row.type = spec.type();
        row.title = spec.title();
        row.desktop = spec.desktop();
        row.mobile = spec.mobile();
        row.style = HomeLayoutJson.write(spec.style());
        row.config = HomeLayoutJson.write(spec.config());
        row.more = HomeLayoutJson.write(spec.more());
        row.createdAt = now;
        return row;
    }

    /** ⚙ 설정 창의 칸을 바꾼다 — 제목 · 노출 · 꾸미기 · 내용 설정(꾸미기·설정은 부르는 쪽이 저장 값에 보낸 칸을 덮은 전체를 실어 온다) */
    public void change(String title, boolean desktop, boolean mobile, JsonNode style, JsonNode config) {
        this.title = title;
        this.desktop = desktop;
        this.mobile = mobile;
        this.style = HomeLayoutJson.write(style);
        this.config = HomeLayoutJson.write(config);
    }

    public HomeGadgetSpec toSpec() {
        return new HomeGadgetSpec(Long.toString(id), type, title, desktop, mobile, HomeLayoutJson.read(style), HomeLayoutJson.read(config),
                HomeLayoutJson.read(more));
    }

    public Long getId() {
        return id;
    }

    public String getTheme() {
        return theme;
    }

    public Long getPageId() {
        return pageId;
    }

    public boolean isBoardLayout() {
        return boardLayout;
    }

    public String getType() {
        return type;
    }
}
