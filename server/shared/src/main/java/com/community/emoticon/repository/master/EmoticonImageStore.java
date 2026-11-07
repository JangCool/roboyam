package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonImageEntity;
import com.community.emoticon.entity.EmoticonImageFormat;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.entity.QEmoticonImageEntity;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 올린 로보티콘 그림({@code emoticon_images}, V142) 장부 — 파일 바이트는 저장소({@code ImageStorage}) 또는 앱 정적 파일(시드)에 있다. QueryDSL 이다({@link EmoticonStoreSupport}) —
 * Native SQL 로 남긴 것은 없다.
 */
@Repository
public class EmoticonImageStore extends EmoticonStoreSupport {

    /**
     * 그림 한 줄.
     *
     * @param storageKey 저장소 키(올린 그림) — 시드 그림이면 null
     * @param staticPath 앱 정적 파일 주소(시드 그림 {@code /emoticons/robotikon-m/01.gif}) — 올린 그림이면 null
     */
    public record ImageRow(long id, EmoticonImageKind kind, EmoticonImageFormat format, int width, int height, int bytes, int frames, boolean loopForever,
                           String contentHash, String storageKey, String staticPath, Long uploadedByUser, Long uploadedByOperator, Instant createdAt) {
    }

    private static final QEmoticonImageEntity M = QEmoticonImageEntity.emoticonImageEntity;

    public EmoticonImageStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    public void insert(ImageRow row) {
        insert(EmoticonImageEntity.create(row.id(), row.kind(), row.format(), row.width(), row.height(), row.bytes(), row.frames(), row.loopForever(), row.contentHash(),
                row.storageKey(), row.staticPath(), row.uploadedByUser(), row.uploadedByOperator(), row.createdAt()));
    }

    public Optional<ImageRow> find(long id) {
        return Optional.ofNullable(rows().where(M.id.eq(id)).fetchFirst()).map(EmoticonImageStore::rowOf);
    }

    public Map<Long, ImageRow> byIds(Collection<Long> ids) {
        Map<Long, ImageRow> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : rows().where(M.id.in(ids)).fetch()) {
            ImageRow row = rowOf(t);
            out.put(row.id(), row);
        }
        return out;
    }

    /** 작가가 {@code since} 뒤에 올린 그림 수(하루 한도 — 색인 {@code ix_emoticon_images_user}) */
    public int uploadedSince(long userId, Instant since) {
        return (int) n(query.select(M.count()).from(M).where(M.uploadedByUser.eq(userId), M.createdAt.goe(since)).fetchFirst());
    }

    private JPAQuery<Tuple> rows() {
        return query.select(M.id, M.kind, M.format, M.width, M.height, M.bytes, M.frames, M.loopForever, M.contentHash, M.storageKey, M.staticPath, M.uploadedByUser,
                M.uploadedByOperator, M.createdAt).from(M);
    }

    private static ImageRow rowOf(Tuple t) {
        return new ImageRow(t.get(M.id), t.get(M.kind), t.get(M.format), t.get(M.width), t.get(M.height), t.get(M.bytes), t.get(M.frames), t.get(M.loopForever),
                t.get(M.contentHash), t.get(M.storageKey), t.get(M.staticPath), t.get(M.uploadedByUser), t.get(M.uploadedByOperator), t.get(M.createdAt));
    }
}
