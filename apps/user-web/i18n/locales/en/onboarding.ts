/**
 * Onboarding (`/onboarding`, the first screen after signing up): a welcome and three community rules (draft wording — to be reviewed).
 * Same keys and order as `ko/onboarding.ts`.
 */
export default {
    seoTitle: 'Welcome',
    seoDescription: 'Check the {site} community rules and get started.',
    avatar: 'Bot',
    welcome: 'Welcome, {name}!',
    intro: 'Let’s talk together here.',
    introRules: 'Before you start, please check these {count} community rules.',
    ruleCount: 'Community rule {current}/{total}',
    stepLabel: 'Step {current} of {total}',
    start: 'Get started',

    // Rule 1 — sources
    sourceShort: 'Cite your sources',
    sourceTitle: 'Back claims with sources',
    sourceBody: 'Add the source (outlet and link) to news, figures and quotes. Claims without a source may be reported.',
    sourceTag: 'News',
    sourceExample: 'Humanoid shipment outlook roundup',
    sourceNote: 'Source: outlet name (example) · link',

    // Rule 2 — no abuse
    respectShort: 'No personal attacks',
    respectTitle: 'No abuse or personal attacks',
    respectBody: 'Do not attack or insult people. Report posts that look like misinformation — the team reviews them.',
    respectExample: 'Report from the ⋯ menu on a post or comment. Reported content is not hidden right away.',

    // Rule 3 — investment notice
    investShort: 'Follow the investment notice',
    investTitle: 'Industry and stock posts carry an investment notice',
    investBody: 'Buy/sell recommendations, price targets and guaranteed returns are not allowed. The team discloses any related holdings.',
    investNotice: 'Investment notice (example) · This post is for information only and is not investment advice. Your decisions and their results are your own.',
};
