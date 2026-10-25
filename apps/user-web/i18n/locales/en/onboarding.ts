/**
 * Onboarding (`/onboarding`, the first screen after signing up): a welcome and three community rules (draft wording — to be reviewed).
 * Only the examples are adapted to Roboyam's topics (robots and AI).
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
    sourceTag: 'Paper',
    sourceExample: 'Summary: new AI model lifts robot-arm grasp success by 20 points',
    sourceNote: 'Source: paper title and venue (example) · link',

    // Rule 2 — no abuse
    respectShort: 'No personal attacks',
    respectTitle: 'No abuse or personal attacks',
    respectBody: 'Do not attack or insult people. Report posts that look like misinformation — the team reviews them.',
    respectExample: 'Instead of “everyone at that robot company is a fraud”, write “this demo looks teleoperated (see 1:20 in the video)”. Report from the ⋯ menu on a post or comment.',

    // Rule 3 — investment notice
    investShort: 'Follow the investment notice',
    investTitle: 'Industry and stock posts carry an investment notice',
    investBody: 'Buy/sell recommendations, price targets and guaranteed returns are not allowed. The team discloses any related holdings.',
    investNotice: 'Investment notice (example) · This post shares robotics and AI industry information and is not advice to buy or sell any stock. Your decisions and their results are your own.',
};
