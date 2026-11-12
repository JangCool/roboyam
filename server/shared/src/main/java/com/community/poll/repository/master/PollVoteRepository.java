package com.community.poll.repository.master;

import com.community.poll.entity.PollVoteEntity;
import com.community.poll.entity.PollVoteId;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 투표한 표({@code poll_votes}) — 넣기는 {@code persist}. 질의는 {@link PollQueries} */
public interface PollVoteRepository extends IrobitRepository<PollVoteEntity, PollVoteId> {
}
