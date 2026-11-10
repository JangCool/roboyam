package com.community.osslicense.repository.master;

import com.community.osslicense.entity.OssNoticeEntryEntity;
import com.community.osslicense.entity.QOssNoticeEntryEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 직접 추가 항목 목록 — 이름순(대소문자 무시, 같으면 먼저 만든 것). 색인 {@code ix_oss_notice_entries_name (lower(name), id)} */
@Repository
public class OssNoticeEntryRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QOssNoticeEntryEntity ENTRY = QOssNoticeEntryEntity.ossNoticeEntryEntity;

    public OssNoticeEntryRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 모든 항목(관리자) */
    public List<OssNoticeEntryEntity> findAllByName() {
        return ordered(query.selectFrom(ENTRY)).fetch();
    }

    /** 숨기지 않은 항목(공개 화면) */
    public List<OssNoticeEntryEntity> findVisibleByName() {
        return ordered(query.selectFrom(ENTRY).where(ENTRY.hidden.isFalse())).fetch();
    }

    private static JPAQuery<OssNoticeEntryEntity> ordered(JPAQuery<OssNoticeEntryEntity> q) {
        return q.orderBy(ENTRY.name.lower().asc(), ENTRY.id.asc());
    }
}
