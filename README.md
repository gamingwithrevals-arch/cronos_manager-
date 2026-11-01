Chronos Manager

One app. All your files. No compromises.

Chronos Manager is a powerful, free and feature-packed Android file manager designed to handle everything you do with your files. Browse, organize, copy, move, rename, delete, compress, extract and manage your files, all from one clean interface.

But that's not all.

Chronos Manager brings HatchZip's revolutionary low-storage extraction technology to Android, allowing you to extract massive game archives without needing enough storage for both the original ZIP and the extracted files simultaneously.

Whether you're managing everyday files, organizing folders, working with archives or handling massive PC game downloads, Chronos Manager has you covered.

Everything you need to manage your files

Chronos Manager isn't just an archive extractor. It's your everyday file management solution.

File management

- Browse internal storage and accessible external storage.
- Copy, cut, move and paste files and folders.
- Rename files and folders.
- Delete files and folders.
- Create new folders and directories.
- Create and edit text and code files.
- Select multiple files for batch operations.
- Split-view file browsing for easier file management.
- View detailed file and folder information.
- Check storage usage with a built-in storage space indicator.
- Sort and organize files with ease.

Advanced archive management

Manage your archives without needing another application.

- Extract ZIP, 7z, TAR, TAR.GZ and RAR archives.
- Create ZIP archives from files and folders.
- Browse archive contents without extracting them.
- Batch extraction for multiple archives.
- Pause and resume extraction.
- Restore partially extracted archives to their original state.

Low-storage extraction technology

Extract massive archives without needing double the storage.

Traditional extraction requires enough free space to hold both the original archive and the extracted files. This becomes a serious problem when dealing with large PC games.

Chronos Manager uses the low-storage extraction engine inherited from HatchZip to reduce this requirement.

How does it work?

1. Storage analysis: Chronos Manager analyses the archive and calculates the required free space before extraction begins.
2. Reverse extraction: The archive is processed from the end, extracting sections one at a time.
3. In-place archive trimming: Successfully extracted sections are removed from the original archive, freeing up storage for subsequent sections.
4. Continuous extraction: This process repeats until all files have been extracted.

Instead of keeping the entire archive and extracted game simultaneously, the archive gradually shrinks as the extracted data grows.

Real-world storage examples:

Archive size| Free space required
33 GB| 6 GB
61 GB| 21 GB
109 GB| 73 GB

Requirements depend on archive structure and compression. Chronos Manager analyses each archive and displays its estimated storage requirement before extraction.

Built for everyday use

Chronos Manager brings essential file management and advanced archive functionality together in one application.

No need to jump between multiple apps just to manage your files.

Installation

1. Visit the "Releases" (../../releases) page.
2. Download the latest "CronosManager-<version>.apk".
3. Open the APK and allow installation when Android asks.
4. Install and launch Chronos Manager.

Each release also includes a ".sha256" file to verify the APK's integrity.

Important notes

- Always check the required free storage displayed before starting an extraction.
- Keep backups of important files. Low-storage extraction modifies the original archive as it progresses.
- Android 11 and newer restrict access to certain application directories, including "Android/data" and "Android/obb". Additional permissions or tools such as Shizuku or root may be needed.
- Requires Android 8.0 (API 26) or newer.

Building from source

Chronos Manager is built using GitHub Actions.

The Android application uses Kotlin and Gradle, while the native extraction engine is written in Rust and compiled for Android using the Android NDK.

Credits

- HatchZip: Original low-storage extraction technology.
- Chronos Zip: The project this application is based on.
- AizenEMU: Project support and development. "YouTube" (https://youtube.com/@aizenemu)

If you find Chronos Manager useful, consider supporting the project and the channel!

License

- "LICENSE-APACHE" and "LICENSE-MIT" cover the project's own code.
- "LICENSE-UNRAR" covers the UnRAR library used for RAR support. Its licensing terms restrict the use of its code to building a RAR-compatible archiver.
