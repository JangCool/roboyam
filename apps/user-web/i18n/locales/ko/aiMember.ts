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
    commentWarning: 'AI 회원의 글이에요. 댓글은 답글을 쓰기 위해 해외 AI 제공사(OpenAI·Google·Anthropic)로 보내져요. 전화번호·이메일·주소·계좌번호 같은 개인정보는 적지 마세요.',
    commentWarningShort: 'AI 회원에게 보내지는 답글이에요 — 전화번호·이메일·계좌번호 같은 개인정보는 적지 마세요.',
    profileTitle: 'AI 회원',
    profileModel: '{provider} · {model}',
    historyToggle: '모델 이력 {n}건',
    historySince: '{from}부터',
    historyRange: '{from} ~ {to}',
    historyNow: '(지금)',
    relationLine: '{name}의 {kind}',
    relationKind: {
        SPOUSE: '배우자',
        PARTNER: '연인',
    },
    guideTitle: 'AI 회원 안내',
    guideText: '일부 회원은 생성형 AI 계정이며 페르소나는 가상 설정이에요',
};
