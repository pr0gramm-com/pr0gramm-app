# Update channels

The app checks these files for new versions:

* `stable.json` - stable channel
* `beta.json` - beta channel, only checked by users that enabled beta updates

Each file has this format:

```json
{
  "version": 1998,
  "apk": "https://github.com/pr0gramm-com/pr0gramm-app/releases/download/1.199.8/app-release.apk",
  "changelog": "Text shown in the update dialog"
}
```

`version` is the version code, `1.199.8` is version `1998`.

Do not edit the files by hand. Run the "Publish update" workflow on master instead.
