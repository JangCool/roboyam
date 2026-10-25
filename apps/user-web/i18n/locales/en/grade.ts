/**
 * Grade guide (`/me/grade`), grade progress and the grade line on my profile. Grade names are set by operators and come from the server.
 * Same keys and order as `ko/grade.ts`.
 */
export default {
    title: 'Grades',
    seoDescription: 'See how {site} grades work and your progress to the next one.',
    mineLabel: 'My grade',
    rulesHeading: 'Grade requirements',
    myRuleTag: 'My grade',

    // Requirement text
    ruleInstant: 'On sign-up',
    ruleDays: '{n} days after sign-up',
    ruleActivity: '{n} posts + comments',
    nextHeading: 'To the next grade, {label}',
    reqDays: '{n} days after sign-up',
    reqActivity: '{n} posts + comments',
    reqOther: 'Requirement {n}',
    progressDays: 'Day {n}',
    progressDaysLeft: 'Day {n} ({left} days left)',
    progressCount: '{n}',
    progressCountLeft: '{n} ({left} to go)',
    highestTitle: "You're at the highest grade",
    highestText: "You've met every requirement.",

    // How it works
    howHeading: 'How it works',
    how1: "Deleted or hidden posts and comments don't count toward activity.",
    how2: "Likes you receive don't count toward promotion.",
    how3: 'Promotion is on hold while your account is suspended.',
    how4: "You move up automatically when you meet the requirements. You won't move down automatically if your activity drops.",

    // States
    authLoading: 'Checking your sign-in',
    progressLoading: 'Loading your progress',
    rulesLoading: 'Loading grade requirements',
    rulesErrorTitle: "Couldn't load grade requirements",
    progressErrorTitle: "Couldn't load your progress",
    progressErrorText: 'You can still see the requirements. Please try again shortly.',
    guestNote: 'Log in to see your progress.',
    pausedSanctionTitle: 'Promotion is on hold while you are suspended.',
    pausedSanctionText: "It'll be recalculated once the suspension ends.",
    pausedAdminTitle: 'Automatic promotion is paused.',
    pausedAdminText: "The team adjusted your grade directly, so meeting the requirements won't promote you automatically for now.",
    sanctionLink: 'View sanction notice',
};
