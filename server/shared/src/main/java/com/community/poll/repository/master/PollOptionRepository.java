package com.community.poll.repository.master;

import com.community.poll.entity.PollOptionEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 투표 선택지({@code poll_options}) — 넣기는 {@code persist}. 질의는 {@link PollQueries} */
public interface PollOptionRepository extends IrobitRepository<PollOptionEntity, Long> {
}
