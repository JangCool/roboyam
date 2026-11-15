package com.community.post.repository.master;

import com.community.post.entity.PostTagDirtyDayEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 태그가 바뀐 날 표시(V216 {@code post_tag_dirty_days}) — 앱은 넣기만 한다({@code PostTagStore#replace}), 지우기는 DB 배치 R__batch_44 */
public interface PostTagDirtyDayRepository extends IrobitRepository<PostTagDirtyDayEntity, Long> {
}
