package com.community.home.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link GadgetPageLayoutEntity} 의 복합 키(페이지 + 테마) */
public class GadgetPageLayoutId implements Serializable {

    private Long pageId;
    private String theme;

    protected GadgetPageLayoutId() {
    }

    public GadgetPageLayoutId(Long pageId, String theme) {
        this.pageId = pageId;
        this.theme = theme;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GadgetPageLayoutId that && Objects.equals(pageId, that.pageId) && Objects.equals(theme, that.theme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageId, theme);
    }
}
