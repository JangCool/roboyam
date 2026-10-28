package com.community.home.entity;

import com.community.home.dto.HomeGadgetSpec;
import com.community.home.service.HomeLayoutJson;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 홈 가젯 한 개({@code home_gadgets}, V105) — 테마에 딸린다. 이번 범위에서 운영자는 넣기·빼기만 하고(내용 설정 화면은 범위 밖), 꾸미기·설정은
 * 만들 때의 값(테마 기본 배치의 시드 또는 종류별 처음 값)을 그대로 둔다. 모르는 칸도 보존하려고 jsonb 글자로 둔다.
 */
@Entity
@Table(name = "home_gadgets")
public class HomeGadgetEntity {

    @Id
    private Long id;

    private String theme;
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

    /** 새 가젯 — {@code spec} 의 ID 는 쓰지 않고 {@code id} 를 쓴다 */
    public static HomeGadgetEntity create(long id, String theme, HomeGadgetSpec spec, Instant now) {
        HomeGadgetEntity row = new HomeGadgetEntity();
        row.id = id;
        row.theme = theme;
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

    public String getType() {
        return type;
    }
}
