/**
 * Search engine registration (Settings > General "Search engines" — `components/site/SiteSearchVerificationSettings.vue`).
 * Same keys and order as `ko/searchVerification.ts`.
 */
export default {
    title: 'Search engines',
    intro: 'Codes that prove site ownership to search engines. Once saved, the verification meta tags are added to the head of the public site HTML. Empty fields add no tag.',
    loadFailed: "Couldn't load search engine settings",
    hint: 'Optional · letters, digits, - and _, up to {max}',
    placeholder: 'content value or the whole copied meta tag',
    openSite: 'Open console',
    newTab: 'new window',
    naverName: 'Naver',
    googleName: 'Google',
    bingName: 'Bing',
    naverLabel: 'Naver Search Advisor',
    naverHelp: 'Search Advisor › Add site › HTML tag: paste the content value. After saving, click verify on Naver.',
    googleLabel: 'Google Search Console',
    googleHelp: 'Search Console › Add property › URL prefix › HTML tag: paste the content value. Leave empty if you verified with the Domain (DNS) method.',
    bingLabel: 'Bing Webmaster Tools',
    bingHelp: 'Bing Webmaster Tools › Add site › HTML meta tag: paste the content value. After saving, click verify on Bing.',
    problemLength: 'Use {max} characters or fewer.',
    problemChars: 'Only letters, digits, - and _ are allowed.',
    problemTag: "This meta tag doesn't belong here. Paste only the content value.",
    problemOtherTag: 'This is a {service} tag. Paste it into the {service} field.',
    previewLabel: 'Tags added to the public site (saved values)',
    previewEmpty: 'No tags are added',
    saved: 'Search engine settings saved',
};
