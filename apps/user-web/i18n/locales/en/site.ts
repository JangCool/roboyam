/**
 * Labels for the site name, tagline and public URL inputs (install wizard step 3, admin settings). Same keys and order as `ko/site.ts`.
 */
export default {
    // Field names and hints
    nameLabel: 'Site name',
    nameHint: '1–{max} characters',
    taglineLabel: 'Tagline (optional)',
    taglineHint: '0–{max} characters',
    publicUrlLabel: 'Public URL',

    // Help
    taglineHelp: 'Shown under the logo and after the page title in search results. Leave it empty to hide it (you can change it later in admin settings).',
    publicUrlHelp: 'The address people use to open this site in a browser. e.g. https://example.com',
    publicUrlWhatTitle: 'What is the public URL?',
    publicUrlWhat:
        'The address people use to reach this site in a browser. It is the base for links in sign-up verification and password reset emails, social login callbacks, and search engine metadata. Write it like https://example.com, without a trailing /.',
    publicUrlPrefilled: 'Filled in with the address you are using now. Change it if users will reach the site at a different address.',
    publicUrlLocal: 'An address like localhost only works on this computer. Email links and social login need a real address that other people can open.',
    // Review screen when the tagline is left empty — if the chosen default board setup has a tagline the server fills it in at install time, so we do not say "none"
    taglineNone: 'Not entered',

    // Checks done on the screen
    nameRequired: 'Enter a site name.',
    nameLength: 'The site name must be {max} characters or fewer.',
    taglineChars: 'The tagline cannot contain angle brackets (“<” or “>”).',
    taglineLength: 'The tagline must be {max} characters or fewer.',
    publicUrlRequired: 'Enter a public URL.',
    publicUrlScheme: 'The address must start with http:// or https://.',
    publicUrlPath: 'Remove the trailing / and any path or query, e.g. https://example.com.',
    publicUrlFormat: 'Check the address format, e.g. https://example.com',
};
