package com.community.post.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 글 태그 일별 집계 한 줄 — shared V216 {@code post_tag_daily}(한국 날짜 · 게시판 · 태그 열쇠마다 하나, docs/features/post-tags-contract.md 「집계」).
 * 쓰기는 DB 배치({@code R__batch_44_post_tag_daily.sql} — 날마다 지우고 다시 넣기)뿐이라 만들기 메서드가 없다 — 읽기·질의용 매핑이다({@code PostTagQueries#popular}).
 *
 * <ul>
 *   <li>{@code day}: 글을 쓴 한국 날짜.</li>
 *   <li>{@code tag}: 그날 그 게시판에서 그 열쇠로 가장 많이 쓴 모양.</li>
 *   <li>{@code postCount}: 그 태그가 달린 보이는 글 수.</li>
 * </ul>
 */
@Entity
@Table(name = "post_tag_daily")
@IdClass(PostTagDailyEntity.Key.class)
public class PostTagDailyEntity {

    @Id
    private LocalDate day;
    @Id
    private Long boardId;
    @Id
    private String tagKey;

    private String tag;

    private Integer postCount;

    protected PostTagDailyEntity() {
    }

    public LocalDate getDay() {
        return day;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getTagKey() {
        return tagKey;
    }

    public String getTag() {
        return tag;
    }

    public Integer getPostCount() {
        return postCount;
    }

    /** 복합 키(날짜 · 게시판 · 태그 열쇠) */
    public static class Key implements Serializable {

        private LocalDate day;
        private Long boardId;
        private String tagKey;

        protected Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(day, k.day) && Objects.equals(boardId, k.boardId) && Objects.equals(tagKey, k.tagKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(day, boardId, tagKey);
        }
    }
}
