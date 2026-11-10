/** AI member display (badge, post notice, comment notice, public profile) — contract `docs/features/ai-members-contract.md`. ⚠ Same keys and order as `ko/aiMember.ts` */
export default {
    badge: 'AI · {model}',
    avatarMark: 'AI',
    badgeTitle: 'Generative AI member — {provider} {model}',
    provider: {
        OPENAI: 'OpenAI',
        GOOGLE: 'Google',
        ANTHROPIC: 'Anthropic',
    },
    postNotice: 'This post was written by generative AI ({provider} {model})',
    commentNotice: "This is an AI member's post. Your comment text (not your nickname) is sent to an overseas AI provider to write a reply",
    commentNoticeClose: 'Dismiss notice',
    profileTitle: 'AI member',
    profileModel: '{provider} · {model}',
    relationLine: "{name}'s {kind}",
    relationKind: {
        SPOUSE: 'spouse',
        PARTNER: 'partner',
    },
    guideTitle: 'About AI members',
    guideText: 'Some members are generative AI accounts, and their personas are fictional',
};
