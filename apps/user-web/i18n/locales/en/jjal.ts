/**
 * My stickers (jjal) — picker tab, sticker menu, first-time notice, upload dialog, "My stickers" section of My Robotikon.
 * Same keys and order as `ko/jjal.ts`. Server error messages come from the server.
 */
export default {
    // Picker tab
    tab: 'GIFs',
    tabTitle: 'My GIFs',
    upload: 'Upload',
    count: '{count} / {limit}',
    pickerNote: 'Only you can use this collection. It is not listed in the shop.',
    pickerEmpty: 'No GIFs yet. Upload one or save GIFs from posts and comments.',
    pickAria: 'Insert GIF',
    loading: 'Loading your GIFs',
    loadFailed: "Couldn't load your GIFs.",
    failed: 'Please try again in a moment.',
    tooManyComment: 'A comment can have up to {max} GIF.',
    tooManyPost: 'A post can have up to {max} GIFs.',
    inBody: '{count} GIF(s) added',
    removeFromBody: 'Remove GIF',

    // GIFs in posts and comments
    blockedLabel: 'GIF removed',
    summaryWord: '(GIF)',

    // Sticker menu
    menuTitle: 'GIF',
    menuCollect: 'Save to my GIFs',
    menuView: 'View larger',
    menuReport: 'Report GIF',
    viewTitle: 'GIF',
    collected: 'Saved to my GIFs {count} / {limit}',
    alreadyIn: 'Already in your GIFs',
    full: 'Your {limit} GIF slots are full',
    organize: 'Tidy up',
    loginTitle: 'Log in to save GIFs',
    loginText: 'Save GIFs you like and use them in posts and comments.',
    reportTitle: 'Report GIF',
    reported: 'Reported. Our team will review it.',

    // First-time notice
    introTitle: 'About my GIFs',
    introPrivate: "It's a private collection. Nobody can see who saved what.",
    introShare: 'Anyone can save GIFs posted in comments — including yours.',
    introReport: 'A GIF removed after a report is removed everywhere.',
    introKeep: 'Saved GIFs stay even if the uploader deletes theirs.',
    introCheck: 'Got it',
    introLater: 'Later',
    introContinue: 'Continue',

    // Upload dialog
    uploadTitle: 'Upload a GIF',
    uploadDrop: 'Drop a file here or',
    uploadChoose: 'choose a file',
    uploadFormats: 'GIF · PNG · JPG · WebP',
    ruleSize: 'Up to 2MB · resized to 300px on the long side',
    ruleAnimated: 'Animated: up to 10 seconds · 100 frames',
    ruleLimit: 'You can keep up to {limit}',
    confirmClean: 'It is not obscene, illegally filmed or hateful',
    confirmRights: 'It may be removed after a rights complaint',
    preparing: 'Resizing…',
    preparedInfo: '{width}×{height} · {size}',
    preparedAnimated: '{frames} frames · {seconds}s',
    previewAlt: 'Preview of the GIF to upload',
    changeFile: 'Another file',
    uploadSubmit: 'Upload',
    uploaded: 'Uploaded to my GIFs {count} / {limit}',
    uploadFull: 'Your {limit} GIF slots are full. Remove some first.',

    // Resize problems
    problem: {
        unsupported: 'Only GIF, PNG, JPG and WebP images can be uploaded.',
        webpAnimated: "This browser can't convert animated WebP. Please upload a GIF.",
        broken: "Couldn't read the image. Try another file.",
        tooLong: 'This GIF is {seconds}s long. Keep it under {max}s.',
        tooManyFrames: 'This GIF has {frames} frames. Keep it under {max}.',
        resultTooBig: 'Still over {max} after resizing. Try a shorter GIF.',
        sourceTooBig: 'The file is too large. Use one under {max}.',
    },

    // My Robotikon section
    myTitle: 'My GIFs',
    myEmpty: 'No GIFs yet',
    myEmptyText: 'Upload a GIF, or tap a GIF in a post or comment and choose "Save to my GIFs".',
    mySelect: 'Select',
    mySelectDone: 'Done',
    mySelected: '{count} selected',
    myRemove: 'Remove',
    myItemAria: 'GIF {n}',
    mySourceUpload: 'Uploaded',
    mySourceCollect: 'Saved',
    removeConfirmTitle: 'Remove {count} GIF(s) from your collection?',
    removeConfirmText: 'They are only removed from your collection. GIFs already used in posts and comments stay.',
    removed: 'Removed {count} from your GIFs.',
    blockedNote: '{count} hidden — removed after a rights complaint. They also show as "GIF removed" in your comments.',
    blockedCell: 'Removed',
    myReorder: 'Reorder',
    myReorderHint: 'Drag a tile or use the earlier/later buttons. The picker shows GIFs in this order too.',
    moveEarlier: 'Move GIF {n} earlier',
    moveLater: 'Move GIF {n} later',
    movedTo: 'Moved to position {n}',
    orderSaved: 'Saved the order of your GIFs.',
};
