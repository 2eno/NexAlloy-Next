<div align="center">
  <h1>NexAlloyNext</h1>
</div>

**LSPosed module that applies patch bundles from patch sources.**

NexAlloyNext is [NexAlloy](https://github.com/2eno/NexAlloy) rebuilt around plug-ins, like [Morphe](https://morphe.software):
the module no longer contains any patches. Patches come in **patch bundles** (`.nxb` files) that you install from
**patch sources**, so anyone can publish patches for NexAlloy from their own repository.

>[!IMPORTANT]
> - **Root access** and LSPosed (or another framework implementing libxposed API 101) are **required**.
> - Patch bundles run code inside the apps they patch. **Only add patch sources you trust.**

## Usage

1. Install the NexAlloyNext APK from the [latest release](https://github.com/2eno/NexAlloyNext/releases) and enable the module in LSPosed.
   On first start, NexAlloyNext asks LSPosed to add itself to its scope.
2. Open the **Patches** tab. The *NexAlloy Patches* source is preset; tap **Install**.
3. Approve the scope request LSPosed shows for the apps of the installed bundles.
4. Pick an app on the **Home** tab, choose its patches, then force stop the app.

NexAlloyNext checks for updates of itself and of installed patch sources in the background and notifies about them;
both notifications can be turned off in **Settings**.

### Blocking Play Store updates

A new app version can break the patches. Open an app's patches and turn on **Block Play Store updates** to keep the
Play Store from updating it by itself. The Play Store only updates apps it installed, so NexAlloyNext reinstalls the
app from its own APK with the adb shell as its installer (`pm install -r -i`, as the shell user through root). The
app restarts, its data and its place in the module scope stay. Apps whose bundle names a recommended version are
locked by default once root worked for one app (see **Settings**). Turn the switch off to reinstall the app with the
Play Store as its installer again.

Once a patch update recommends a newer version than the installed one, NexAlloyNext gives the app back to the Play
Store, so it updates the app, notifies you, and locks the app again in its next check after the update. The Play
Store installs its latest version, which can be newer than the recommended one. The app card then says untested.

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

The preset *NexAlloy Patches* source is [NexAlloyNext-Patches](https://github.com/2eno/NexAlloyNext-Patches), which
provides the NexAlloy Patches and 2eno Patches bundles. See there for the patched apps.

### Your own bundle

Start from the [bundle template](template) and see [DEVELOPMENT.md](DEVELOPMENT.md#writing-a-patch-bundle).
Publish `.nxb` files as release assets, and users can add your repository as a patch source.

## ⭐ Credits

[NexAlloy](https://github.com/NexAlloy/NexAlloy): the LSPosed module this project is rebuilt from, by ChsBuffer  
[DexKit](https://luckypray.org/DexKit/en/): a high-performance dex runtime parsing library  
[Morphe Patcher](https://github.com/MorpheApp/morphe-patcher): the fingerprint API the patcher API is modeled after  
[Morphe Manager](https://github.com/MorpheApp/morphe-manager): the design of the app cards
