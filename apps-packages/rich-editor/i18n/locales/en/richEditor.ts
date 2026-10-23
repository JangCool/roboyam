/**
 * Messages for the rich text editor (`RichEditor`) and the post-body enhancer (`UtilRichBody`). Same keys, same order as `ko/richEditor.ts`.
 * **When the server sends a `message`, that is shown first.**
 */
export default {
    // Toolbar
    toolbar: 'Formatting',
    body: 'Body',
    heading: 'Heading',
    paragraph: 'Normal text',
    heading2: 'Heading 2',
    heading3: 'Heading 3',
    textColor: 'Text color',
    backgroundColor: 'Background color',
    colorDefault: 'Default color',
    backgroundNone: 'No background',
    color: {
        red: 'Red',
        orange: 'Orange',
        yellow: 'Yellow',
        green: 'Green',
        blue: 'Blue',
        purple: 'Purple',
        gray: 'Gray',
        lime: 'Lime',
        sky: 'Sky',
        pink: 'Pink',
    },
    bold: 'Bold',
    italic: 'Italic',
    underline: 'Underline',
    strike: 'Strikethrough',
    bulletList: 'Bulleted list',
    orderedList: 'Numbered list',
    blockquote: 'Quote',
    code: 'Code',
    codeInline: 'Inline code',
    codeBlock: 'Code block',
    link: 'Link',
    horizontalRule: 'Divider',
    image: 'Insert image (JPG, PNG, GIF, WebP)',
    youtube: 'Insert YouTube video',

    // Images and videos in the body
    imageAlt: 'Image in post',
    imageRemove: 'Remove image',
    imageBroken: "Couldn't load the image",
    imageBrokenHint: 'The network may be unstable or the image may have been removed.',
    youtubeLabel: 'YouTube video',
    youtubeRemove: 'Remove YouTube video',
    youtubePlay: 'Play YouTube video',
    youtubeFrame: 'YouTube video',
    imageNotAllowed: "Images can't be uploaded here",
    mediaCount: '{images} images · {videos} videos',

    // Insert link
    linkTitle: 'Insert link',
    linkDescription: 'Enter an http:// or https:// address. If you selected text, the link goes on that text.',
    linkUrl: 'Link address',
    linkRemove: 'Remove link',
    linkInvalid: 'Enter a valid address starting with http:// or https://.',

    // Insert YouTube
    youtubeTitle: 'Insert YouTube video',
    youtubeUrl: 'YouTube address',
    youtubeHelp: "Only YouTube links can be added. Video files can't be uploaded.",
    youtubePreview: 'Video thumbnail preview',
    youtubeInsert: 'Insert',
    youtubeRequired: 'Enter a YouTube video address.',
    youtubeInvalid: "That isn't a YouTube video address. Use a youtu.be/… or youtube.com/watch?v=… link.",

    // Image drop zone
    dropZoneLabel: 'Upload images. Drag files here or click to choose.',
    dropZoneDrop: 'Drop to upload',
    dropZoneText: 'Drag images here or click to upload',
    dropZoneFormats: 'JPG, PNG, GIF, WebP',
    dropZoneCollapse: 'Collapse upload area',
    dropZoneExpand: 'Expand upload area',

    // Upload list
    uploadListLabel: 'Image uploads',
    uploadFailedTitle: 'Some images failed to upload',
    uploadFailedText: 'Remove or retry the failed files before saving.',
    uploadingCount: 'Uploading images · {n}',
    uploadingStay: "Don't leave while uploading — leaving cancels the uploads.",
    uploadingItem: 'Uploading {name}',
    retry: 'Retry',
    retryItem: 'Retry {name}',
    removeUpload: 'Remove',
    removeItem: 'Remove {name}',
    cancelUpload: 'Cancel',
    cancelItem: 'Cancel uploading {name}',
    uploadQueued: 'Waiting · {size}',
    uploadProcessing: 'Processing on the server · {size}',
    uploadProgress: '{percent}% · {size}',

    // Upload failures (only when the server sends no message)
    uploadUnsupported: 'Unsupported format. Only JPG, PNG, GIF and WebP can be uploaded.',
    uploadEmpty: 'The file is empty. Choose another image.',
    uploadTooLarge: 'The file is too large. Choose a smaller image.',
    uploadNetwork: "Couldn't upload because of a network problem. Try again shortly.",
    uploadFailedAnnounce: "Couldn't upload the image. {name}. {message}",
    uploadedAnnounce: 'Image inserted. {name}',
};
