<div align="center">
  <h1>NexAlloy Next</h1>
</div>

**LSPosed module that applies patch bundles, powered by Morphe, ReVanced, and beyond.**

NexAlloy Next is [NexAlloy](https://github.com/2eno/NexAlloy) rebuilt around plug-ins, like [Morphe](https://morphe.software):
the module no longer contains any patches. Patches come in **patch bundles** (`.nxb` files) that you install from
**patch sources**, so anyone can publish patches for NexAlloy from their own repository.

>[!IMPORTANT]
> - This is **NOT an official Morphe or ReVanced project**, do not ask their developers for help.
> - **Root access** and LSPosed (or another framework implementing libxposed API 101) are **required**.
> - Patch bundles run code inside the apps they patch. **Only add patch sources you trust.**

## Usage

1. Install the NexAlloy APK from the [latest release](https://github.com/2eno/NexAlloy-Next/releases/latest) and enable the module in LSPosed.
   NexAlloy is in its own scope, so it can show at the top of its settings whether the module is active.
2. Open NexAlloy > **Patch sources**. The *NexAlloy Patches* source is preset; tap it and choose **Install**.
3. Approve the scope request LSPosed shows for the patched apps.
4. Pick the patches of each app under **Patch Selection**, then force stop the app.

Updates of installed sources are shown in **Patch sources**; tap a source to update its bundles.

### Patch sources

A patch source is one of:

- **A GitHub repository** (`owner/repo` or `https://github.com/owner/repo`). NexAlloy installs every `.nxb` asset of
  its latest release. Enable *Use pre-releases* to also consider pre-releases; sources without a stable release use their latest pre-release.
- **A bundle JSON URL**, in the format of Morphe's `patches-bundle.json`:
  ```json
  { "version": "1.2.0", "download_url": "https://example.com/my-patches-1.2.0.nxb", "description": "Changelog" }
  ```

You can also import a `.nxb` file from storage.

## Patch bundles

### NexAlloy Patches

The patches of NexAlloy, released with every NexAlloy release.

| App | Patches |
| --- | --- |
| YouTube | Remove ads, SponsorBlock, background playback, sanitize sharing links, navigation buttons, swipe controls, video quality and playback speed options, hide layout components and video action buttons, Shorts resuming, video codecs, auto captions, alternative thumbnails, bypass image region restrictions |
| YouTube Music | Remove music video ads, background playback, hide upgrade button and "Get Music Premium" label, exclusive audio playback |
| Reddit | Hide ads, sanitize sharing links, start as guest |
| Google Photos | Spoof Pixel XL |
| Photomath | Unlock plus |
| Instagram | Ported from [Piko](https://github.com/crimera/piko): hide ads and suggested content, anonymous viewing, disable analytics, layout and link options, and more |
| Threads | Hide ads |
| Strava | Unlock subscription features, disable subscription suggestions |
| AllTrails | Enable Peak membership |

<sub>The Instagram patches are ported from [Piko](https://github.com/crimera/piko), see its [NOTICE](bundles/nexalloy/src/main/java/io/github/nexalloy/piko/NOTICE).</sub>

### 2eno Patches

Spotify, Kleinanzeigen, Untappd and InterPals patches, sharing their extension code with
[2eno Patches](https://github.com/2eno/2eno-patches) for Morphe. Released with every NexAlloy release.

| App | Patches |
| --- | --- |
| Spotify | Mute audio ads, hide ad sections, ad views, context menu upsells, popup ads, playlist ads, video ads and the Premium tab, fix third party launcher widgets, sanitize sharing links, Material You theme, rounded corners |
| Kleinanzeigen | Hide ads, hide Pur, sanitize sharing links |
| Untappd | Hide ads, feed ads and sponsored content |
| InterPals | Hide ads, disable ad placements, download photos, feed age and gender filter |

### Your own bundle

Start from the [bundle template](template) and see [DEVELOPMENT.md](DEVELOPMENT.md#writing-a-patch-bundle).
Publish `.nxb` files as release assets, and users can add your repository as a patch source.

## ⭐ Credits

[NexAlloy](https://github.com/NexAlloy/NexAlloy): the LSPosed module this project is rebuilt from, by ChsBuffer  
[DexKit](https://luckypray.org/DexKit/en/): a high-performance dex runtime parsing library  
[Morphe](https://morphe.software): Transform Your Android Apps  
[ReVanced](https://revanced.app): Continuing the legacy of Vanced at [revanced.app](https://revanced.app)  
[Piko](https://github.com/crimera/piko): Instagram and X patches
