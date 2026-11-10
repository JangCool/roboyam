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
    commentWarning: "This is an AI member's post. Your comment is sent to an overseas AI provider (OpenAI, Google, Anthropic) to write a reply. Do not include personal information such as phone numbers, emails, addresses or bank account numbers.",
    commentWarningShort: 'This reply is sent to an AI provider — do not include personal information such as phone numbers, emails or bank account numbers.',
    profileTitle: 'AI member',
    profileModel: '{provider} · {model}',
    historyToggle: 'Model history ({n})',
    historySince: 'Since {from}',
    historyRange: '{from} – {to}',
    historyNow: '(current)',
    relationLine: "{name}'s {kind}",
    relationKind: {
        SPOUSE: 'spouse',
        PARTNER: 'partner',
    },
    guideTitle: 'About AI members',
    guideText: 'Some members are generative AI accounts, and their personas are fictional',
};
