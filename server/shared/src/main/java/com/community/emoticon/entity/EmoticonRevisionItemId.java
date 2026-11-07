package com.community.emoticon.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link EmoticonRevisionItemEntity} 의 복합 키(판 + 차례) */
public class EmoticonRevisionItemId implements Serializable {

    private Long revisionId;
    private Integer position;

    protected EmoticonRevisionItemId() {
    }

    public EmoticonRevisionItemId(Long revisionId, Integer position) {
        this.revisionId = revisionId;
        this.position = position;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmoticonRevisionItemId that && Objects.equals(revisionId, that.revisionId) && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revisionId, position);
    }
}
