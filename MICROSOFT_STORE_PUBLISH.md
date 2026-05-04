# FocusFlow — Microsoft Store Publishing Guide

## Overview

Publishing FocusFlow to the Microsoft Store requires packaging it as MSIX,
signing it, enrolling in Partner Center, and submitting for certification.
This guide covers every step from build to store listing.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Windows 10/11 SDK | 10.0.19041+ | MSIX packaging tools |
| Visual Studio 2022 | Any edition | `makeappx`, `signtool` |
| JDK 17 (Zulu/Temurin) | 17.x | Build host |
| Gradle | 8.x | Build system |
| GitHub Actions runner | `windows-latest` | CI packaging |

---

## Step 1 — Enroll in Microsoft Partner Center

1. Go to [partner.microsoft.com](https://partner.microsoft.com) and sign in with a Microsoft account.
2. Navigate to **Windows & Xbox** → **Overview** → **Open** (for Windows apps).
3. Pay the one-time developer fee ($19 USD individual, $99 USD company).
4. Fill in your **Publisher Display Name** (this appears on the Store listing).
5. Note your **Publisher Identity** — it looks like:
   ```
   CN=YourName, O=YourCompany, L=City, S=State, C=US
   ```
   You will need this for the MSIX manifest.

---

## Step 2 — Obtain a Code Signing Certificate

MSIX packages **must** be signed. You have two options:

### Option A — Trusted Certificate (required for Store) 
The Microsoft Store signs your package on upload automatically. You can use a **self-signed** cert for testing, but Partner Center re-signs on publish.

### Option B — Self-Signed (for sideloading / testing only)
```powershell
# Create self-signed cert
$cert = New-SelfSignedCertificate `
  -Type CodeSigningCert `
  -Subject "CN=FocusFlow Dev" `
  -CertStoreLocation "Cert:\CurrentUser\My"

# Export as PFX
Export-PfxCertificate `
  -cert $cert `
  -FilePath focusflow-dev.pfx `
  -Password (ConvertTo-SecureString -String "YourPassword" -Force -AsPlainText)
```

Store submissions do NOT need you to sign — Partner Center handles signing after upload.

---

## Step 3 — Build the MSIX Package

### 3a. Update `build.gradle.kts` for MSIX

Add `TargetFormat.Msix` to the nativeDistributions block:

```kotlin
nativeDistributions {
    targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Msix)

    packageName    = "FocusFlow"
    packageVersion = "1.0.0"
    description    = "Focus & productivity app with real app blocking"
    vendor         = "YourCompany"

    windows {
        menuGroup      = "FocusFlow"
        shortcut       = true
        dirChooser     = true
        perUserInstall = true
        upgradeUuid    = "B4C3F3A2-8E41-4D9A-B7C6-D1E0F2A34B56"

        // Required for Store submission
        iconFile.set(project.file("src/main/resources/icon.ico"))
    }
}
```

### 3b. Run the Build

```bash
# On Windows (GitHub Actions or local)
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17..."
./gradlew packageMsix
```

Output: `build/compose/binaries/main/msix/FocusFlow-1.0.0.msix`

### 3c. GitHub Actions Workflow (automated)

The existing `.github/workflows/build-windows.yml` already targets Windows.
Add this step to produce the MSIX artifact:

```yaml
- name: Build MSIX
  run: ./gradlew packageMsix

- name: Upload MSIX
  uses: actions/upload-artifact@v3
  with:
    name: focusflow-msix
    path: build/compose/binaries/main/msix/*.msix
```

---

## Step 4 — Create the Store Listing

1. In Partner Center → **Apps and games** → **New product** → **MSIX or PWA app**.
2. Reserve your app name: **FocusFlow**.
3. Fill in:
   - **Description** (up to 10,000 chars) — see suggested text below
   - **Features** bullet list
   - **Screenshots** (min 1, recommended 4–8, 1366×768 or 1920×1080)
   - **Store logo** (300×300 px)
   - **Age rating**: PEGI 3 / ESRB Everyone (no objectionable content)
   - **Category**: Productivity → Utilities & Tools
   - **Pricing**: Free (or set price)

### Suggested Store Description

```
FocusFlow — Deep Work, Without Distractions.

FocusFlow is a Windows productivity app that helps you eliminate digital 
distractions and build laser-sharp focus habits.

FEATURES
• Real-time app blocking — automatically kills distracting processes 
  while you focus
• Pomodoro timer with automatic short & long breaks
• Habit tracker with 7-day streak grid
• Daily journaling with mood tracking
• Detailed session reports and focus statistics
• Nuclear Mode — maximum enforcement (blocks Task Manager itself)
• Scheduled blocking — automatic focus windows on a weekly timetable
• Daily allowances — limit time in specific apps per day
• System tray support — runs quietly in background
• All data stored locally. No accounts. No cloud. No tracking.

FocusFlow is designed for students, developers, writers, and anyone who 
wants to reclaim their attention in a world of constant notifications.
```

---

## Step 5 — Capabilities & Permissions Declaration

Microsoft Store certification checks that your manifest matches what your
app actually does. Add the following to the MSIX manifest capabilities:

```xml
<Capabilities>
  <!-- Needed to monitor and terminate processes -->
  <Capability Name="runFullTrust" />
  <!-- Needed for system tray integration -->
  <Capability Name="allowElevation" />
</Capabilities>
```

Because FocusFlow uses **process termination** (killing apps) and optionally
**network rules** (netsh), it must declare `runFullTrust`. Partner Center will
route it through a manual policy review — this is expected. Include a clear
justification in the **Notes to Certification** field:

> "This app monitors and terminates user-selected processes as a focus-aid 
> productivity tool. The user explicitly configures which processes are blocked. 
> Network rules use Windows netsh and require elevated privilege. No user data 
> leaves the device."

---

## Step 6 — Submit for Certification

1. Upload the `.msix` file under **Packages**.
2. Complete all listing sections (screenshots, description, age rating).
3. Set **Availability**: All markets, or restrict to specific regions.
4. Click **Submit to Store**.

Certification typically takes **1–3 business days**. Common rejection reasons:
- Missing capability declarations
- App crashes on clean Windows install (test on a fresh VM first)
- Icon does not meet Store guidelines (must be PNG with alpha, 300×300)

---

## Step 7 — Post-Publish

- **Version updates**: Bump `packageVersion` in `build.gradle.kts`, rebuild MSIX, upload to Partner Center.
- **Store rating**: Respond to reviews promptly to maintain rating.
- **Telemetry**: Partner Center provides download/install stats without any SDK needed.

---

## Resetting to v1.0.0

If you need to roll the version number back to `1.0.0` (e.g. after test builds):

### In `build.gradle.kts`
```kotlin
version = "1.0.0"

nativeDistributions {
    packageVersion = "1.0.0"
    ...
}
```

### Git tag the release
```bash
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

### Rebuild clean
```bash
./gradlew clean packageMsix
```

The MSIX filename and internal version will both read `1.0.0`.

> **Note**: The Microsoft Store enforces that each submission's version number
> must be **greater than or equal to** the previous. If you published `1.0.1`
> and want to re-submit as `1.0.0`, you must unpublish the higher version first
> or use `1.0.0.0` (four-part version). Partner Center accepts four-part 
> versions: `major.minor.build.revision`.

---

## Useful Links

- [Partner Center](https://partner.microsoft.com/en-us/dashboard)
- [MSIX packaging guide (Microsoft Docs)](https://docs.microsoft.com/en-us/windows/msix/)
- [Store policy](https://docs.microsoft.com/en-us/windows/uwp/publish/store-policies)
- [Certification requirements](https://docs.microsoft.com/en-us/windows/uwp/publish/the-app-certification-process)
- [Compose Desktop packaging](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md)
