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
 * 운영자는 넣기·빼기와 내용 설정({@link #changeConfig} — 공구 · 글 목록 · 갤러리 · 게시판 카드의 ⚙ 칸)만 고치고, 나머지 꾸미기·설정은
 * 만들 때의 값(테마 기본 배치의 시드 또는 종류별 처음 값)을 그대로 둔다. 모르는 칸도 보존하려고 jsonb 글자로 둔다.
 */
@Entity
@Table(name = "home_gadgets")
public class HomeGadgetEntity {

    @Id
    private Long id;

    private String theme;
    /** 가젯 페이지 ID — 홈 가젯이면 null */
    private Long pageId;
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
        HomeGadgetEntity row = new HomeGadgetEntity();
        row.id = id;
        row.theme = theme;
        row.pageId = pageId;
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

    /** 내용 설정을 바꾼다(관리자 ⚙ 설정 창의 칸 — 다른 칸은 부르는 쪽이 그대로 실어 온다) */
    public void changeConfig(JsonNode next) {
        this.config = HomeLayoutJson.write(next);
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

    public String getType() {
        return type;
    }
}
