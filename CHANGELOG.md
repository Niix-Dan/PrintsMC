## 1.1.0
* Changed project name to ` PrintsMC `
* Refactored actions Modal
* Added option to rename images
* Changed image hosting provider to a proprietary one -> [printsmc.com](https://printsmc.com)
* Added configurable time for image upload (24h / 7d / 30d)
* Added button to copy the image directly to your clipboard

---

## v1.0.2
* Added a brand new mod icon.
* Migrated the image upload host to Uguu.se (replacing catbox.moe).
* You can now copy the image URL simply by clicking the link.
* Replaced the Scale button with a Slider.
* Added mod short description to the mod properties.
* Implemented asynchronous Lazy Loading for screenshots, drastically improving performance and reducing RAM/VRAM usage when scrolling.

---

## v1.0.1
* Added Mod Publish Plugin for automated CurseForge and GitHub releases.
* Implemented native `CHANGELOG.md` reading through Gradle.
* Fixed build artifact resolution (`remapJar` for Fabric, `jar` for NeoForge/Forge).
* Updated GitHub Actions workflow for automated tag releases and manual publishing triggers.
* Changed image upload button position.