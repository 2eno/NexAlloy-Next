<div align="center">
  <h1>NexAlloyNext</h1>
</div>

**LSPosed module that applies patch bundles from patch sources.**

NexAlloy Next is [NexAlloy](https://github.com/2eno/NexAlloy) rebuilt around plug-ins, like [Morphe](https://morphe.software):
the module no longer contains any patches. Patches come in **patch bundles** (`.nxb` files) that you install from
**patch sources**, so anyone can publish patches for NexAlloy from their own repository.

>[!IMPORTANT]
> - **Root access** and LSPosed (or another framework implementing libxposed API 101) are **required**.
> - Patch bundles run code inside the apps they patch. **Only add patch sources you trust.**

## Usage

1. Install the NexAlloyNext APK from the [latest release](https://github.com/2eno/NexAlloy-Next/releases) and enable the module in LSPosed.
   On first start, NexAlloyNext asks LSPosed to add itself to its scope.
2. Open the **Patches** tab. The *NexAlloy Patches* source is preset; tap **Install**.
3. Approve the scope request LSPosed shows for the apps of the installed bundles.
4. Pick an app on the **Home** tab, choose its patches, then force stop the app.

NexAlloyNext checks for updates of itself and of installed patch sources in the background and notifies about them;
both notifications can be turned off in **Settings**.

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

The preset *NexAlloy Patches* source is [NexAlloy-Next.2eno](https://github.com/2eno/NexAlloy-Next.2eno), which
provides the NexAlloy Patches and 2eno Patches bundles. See there for the patched apps.

### Your own bundle

Start from the [bundle template](template) and see [DEVELOPMENT.md](DEVELOPMENT.md#writing-a-patch-bundle).
Publish `.nxb` files as release assets, and users can add your repository as a patch source.

## ⭐ Credits

[NexAlloy](https://github.com/NexAlloy/NexAlloy): the LSPosed module this project is rebuilt from, by ChsBuffer  
[DexKit](https://luckypray.org/DexKit/en/): a high-performance dex runtime parsing library  
[Morphe Patcher](https://github.com/MorpheApp/morphe-patcher): the fingerprint API the patcher API is modeled after
