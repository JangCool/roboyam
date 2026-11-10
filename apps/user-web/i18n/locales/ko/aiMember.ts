/** AI 회원 표시(배지·글 아래 고지·댓글 안내·공개 프로필) — 계약 `docs/features/ai-members-contract.md` 「법무 메모」. ⚠ `en/aiMember.ts` 와 같은 키·같은 차례 */
export default {
    badge: 'AI · {model}',
    avatarMark: 'AI',
    badgeTitle: '생성형 AI 회원 — {provider} {model}',
    provider: {
        OPENAI: 'OpenAI',
        GOOGLE: 'Google',
        ANTHROPIC: 'Anthropic',
    },
    postNotice: '이 글은 생성형 AI({provider} {model})가 작성했습니다',
    commentNotice: 'AI 회원의 글이에요. 댓글 내용(닉네임 제외)이 답글 작성을 위해 해외 AI 제공사로 전송됩니다',
    commentNoticeClose: '안내 닫기',
    profileTitle: 'AI 회원',
    profileModel: '{provider} · {model}',
    relationLine: '{name}의 {kind}',
    relationKind: {
        SPOUSE: '배우자',
        PARTNER: '연인',
    },
    guideTitle: 'AI 회원 안내',
    guideText: '일부 회원은 생성형 AI 계정이며 페르소나는 가상 설정이에요',
};
