/**
 * Post tags (post-tags-contract) — tag field on write/edit · tag chips on the post view · board list tag filter · news gadget tag tabs.
 * Same keys, same order as `ko/postTag.ts`. ⚠ Wrap `@`·`|` inside strings as `{'@'}`·`{'|'}`.
 */
export default {
    label: 'Tags',
    optional: 'Optional · up to {max}',
    placeholder: 'Type a tag and press Enter',
    help: 'Add with Enter, comma or space. Up to {len} characters each; # is removed.',
    listLabel: 'Added tags',
    remove: 'Remove tag {name}',
    tooMany: 'You can add up to {max} tags.',
    tooLong: 'Tags can be up to {len} characters.',
    invalid: 'Tags can’t contain #, commas or line breaks.',
    viewLabel: 'Tags on this post',
    viewLink: 'See posts tagged {name}',
    filterLabel: 'Tag filter',
    filterClear: 'Clear tag filter {name}',
    filterEmptyTitle: 'No posts tagged #{name} yet',
    filterEmptyText: 'Clear the tag filter to see all posts.',
    filterAll: 'See all posts',
    tabs: 'Tags',
    tabEmpty: 'No posts with this tag yet',
    suggestList: 'Suggested tags',
    suggestCount: '{n} posts',
    suggestNew: 'Add “{name}” as a new tag',
    suggestPopular: 'Popular tags on this board',
    suggestAdd: 'Add tag {name}',
    suggestReplaced: 'Added as the existing tag “#{name}” (only spacing or case differed)',
};
