# Chronos Manager

**Unzip huge games without needing double storage.** Chronos Manager is a free Android file manager with a low-storage archive extractor. It extracts large ZIP files without needing enough space for both the original archive and the extracted game at the same time.

Chronos Manager brings HatchZip functionality to Android. It is a fork of Chronos Zip, which is itself a fork of HatchZip, expanded into a full file manager.

## The problem

Downloading a large game ZIP and extracting it normally can require almost twice its size in free storage. A 109 GB archive that extracts to 178 GB needs 287 GB free before you start.

## How the extraction works

Chronos Manager extracts the archive in place, so it doesn't need a second full copy of the data:

1. **It checks your storage first.** The app calculates the free space each archive needs and tells you before anything changes. If there isn't enough room, it stops before starting.
2. **It extracts from the end of the archive.** Each section is written out in turn, beginning with the last one.
3. **It removes each finished section from the archive.** Once a section is extracted and saved to disk, it is cut from the original ZIP. That frees the space for the next section.
4. **It repeats until the game is complete.** The archive shrinks as the extracted files grow, so the total space used stays close to the size of the extracted content.

Because the archive is trimmed as it goes, a 109 GB ZIP that extracts to 178 GB needs about 73 GB of free space, instead of 287 GB.

## How much free space you need

It depends on how the archive was built. Chronos Manager analyses each archive and shows the exact free space it needs before extraction starts.

| ZIP size | Free space required |
|---|---|
| 33 GB | 6 GB |
| 61 GB | 21 GB |
| 109 GB | 73 GB |

Why the number changes:
- **Smaller sections** that extract quickly generally need less temporary storage.
- **Larger sections** that are harder to remove need more.
- **Archives with many small files** can sometimes need less than 10 GB.

## Features

- **Low-storage extraction** for large ZIP, 7z, TAR, TAR.GZ, and RAR archives
- **Archive viewer:** browse what's inside an archive without extracting it
- **Batch extract:** select several archives and extract them one after another
- **Create ZIP:** compress selected files and folders into a new archive
- **Pause and resume:** stop an extraction at any point and continue later
- **Restore:** turn a partly extracted archive back into a normal archive
- **File manager:** browse, copy, move, rename, and delete files, with split view
- **Storage space bar** and file info for every item
- **Create files:** make new text and code files from the app

## Installation

1. Download the latest `CronosManager-<version>.apk` from the [Releases](../../releases) page.
2. Open the file on your phone and allow installs from your browser or file manager when Android asks.
3. Install and open **Chronos Manager**.

Each release includes a `.sha256` file. To verify the download, compare its hash with the APK's SHA-256.

## Important notes

- **Always check the required free space** shown by Chronos Manager before you start an extraction.
- **Keep a backup** of anything important. Extraction changes the archive file as it goes.
- **Android restricts some app folders.** On Android 11 and later, other apps' private folders (`Android/data` and `Android/obb`) can't be read without a tool such as Shizuku or root.
- **Android 8.0 (API 26) or newer** is required.

## Building from source

The app builds in GitHub Actions. The Rust engine is compiled with the Android NDK, and the Kotlin app is built with Gradle.

## Credits

- **HatchZip**: the original low-storage extraction engine.
- **Chronos Zip**: the fork this project builds on.
- **AizenEMU**: [youtube.com/@aizenemu](https://youtube.com/@aizenemu). If you find the app useful, please support the channel.

## License

- `LICENSE-APACHE` and `LICENSE-MIT` cover the project's own code.
- `LICENSE-UNRAR` covers the UnRAR library used for RAR support. It restricts using UnRAR's code to build a RAR-compatible archiver.
