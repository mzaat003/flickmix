# Getting FlickMix onto your Fire Stick

There are three ways to turn this source into an installable
`FlickMix_FireTV.apk`. Pick the one that matches what you have.

None of them can happen inside a chat window. Compiling an Android app means
running the Android SDK's build tools against this project — a real toolchain
on a real machine. That is why you got source and not an APK the first time,
and it is why you are getting source and a build pipeline this time. **Option
A below is the one that needs the least from you.**

---

## Option A — Build it in the cloud (no computer needed)

This uses the workflow already included at `.github/workflows/build-apk.yml`.
GitHub compiles it on their machines for free.

1. Make a free account at **github.com** if you do not have one.
2. Create a **new repository**. Name it `flickmix`. Private is fine.
3. Upload this whole project folder to it.
   - On the repo page: **Add file → Upload files**, then drag in everything.
   - Keep the folder structure exactly as it is. `app/`, `gradle/`,
     `.github/`, `build.gradle`, `settings.gradle`, `gradle.properties`
     all need to sit at the top level of the repo.
4. Go to the **Actions** tab. You will see **Build FlickMix APK**.
   It usually starts on its own after the upload. If not, click it and press
   **Run workflow**.
5. Wait about 3–5 minutes for the green check.
6. Click into the finished run and scroll to **Artifacts**. Download
   **FlickMix_FireTV**. It is a zip containing `FlickMix_FireTV.apk`.

You can do all six steps from the browser on your Samsung phone.

---

## Option B — Build it on a computer

1. Install **Android Studio** (free, Windows/Mac/Linux).
2. **File → Open**, select this `FlickMix` folder.
3. Android Studio will offer to download the Gradle wrapper and any missing
   SDK components. Let it. First sync takes a few minutes.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The APK lands at:
   `app/build/outputs/apk/debug/app-debug.apk`
6. Rename it to `FlickMix_FireTV.apk`.

Command line equivalent, if you prefer:

```bash
cd FlickMix
gradle wrapper          # generates ./gradlew the first time
./gradlew assembleDebug
```

---

## Option C — Someone else builds it

Hand the project folder to anyone who has Android Studio. The instructions in
Option B are all they need.

---

# Installing on the Fire Stick

The Fire Stick will not accept a zip of source code. It only accepts the APK.

## 1. Allow sideloading on the Fire Stick

**Settings → My Fire TV → Developer Options**

- Turn on **Apps from Unknown Sources**
- Turn on **ADB Debugging** (only needed for the ADB method below)

If Developer Options is not visible: go to **Settings → My Fire TV → About**
and click **Fire TV Stick** seven times. It will appear.

## 2. Get the APK onto the stick

### Method 1 — Downloader app (simplest)

1. On the Fire Stick, search the Appstore for **Downloader** (by AFTVnews) and
   install it.
2. Put `FlickMix_FireTV.apk` somewhere with a direct link — Google Drive with
   link sharing, Dropbox, or the GitHub release page from Option A.
3. Open Downloader, type the URL, press **Go**.
4. When the download finishes, Downloader offers to install. Accept.

### Method 2 — ADB from the phone

Your Samsung phone is only a courier here; FlickMix does not run on it.

1. Find the Fire Stick's IP: **Settings → My Fire TV → About → Network**.
2. Install an ADB app on the phone, or use a computer with ADB installed.
3. Connect and install:

```bash
adb connect 192.168.1.xx:5555
adb install -r FlickMix_FireTV.apk
```

4. Accept the "Allow USB debugging?" prompt that appears on the TV.

## 3. Launch it

FlickMix appears under **Your Apps & Channels**. Scroll to the end of the row
and select **See All** if you do not spot it immediately — sideloaded apps go
to the bottom.

---

# First run

FlickMix starts empty on purpose. It ships with no sources configured.

1. Press **Menu** on the remote, or select **ADD SOURCE**.
2. Pick a slot type:
   - **Direct** — you paste in individual video URLs one at a time
   - **Playlist** — you give it one M3U/M3U8 URL and it fills the catalog
   - **Web** — it opens that site in the built-in TV browser
3. For Direct slots, use **+ ADD TITLE** to add entries: name, media URL,
   optional poster image URL, year, and category.

Category determines which row the title shows up in — Movies, TV Shows or
Anime.

---

# Troubleshooting

**"App not installed"** — an older FlickMix is already there with a different
signature. Uninstall it first, then reinstall.

**Black screen, audio only** — the Fire Stick cannot hardware-decode that
video codec. Fire Stick 3rd gen handles H.264 and H.265 well; VP9 and AV1 are
unreliable on that hardware.

**"This Fire Stick cannot decode that format"** — same cause. Try a different
rendition of the file.

**"This stream is DRM-protected"** — that is expected and correct. Licensed
content plays in its own licensed app. FlickMix does not and will not open
protected streams.

**Playback stutters** — check the QUALITY menu and pin a lower rendition. Auto
adapts to bandwidth, but a weak 2.4 GHz signal to a 3rd-gen stick will still
struggle at 1080p.

**Build fails on GitHub Actions** — open the failed run and read the red step.
Nine times out of ten it is a file that did not upload, or the folder
structure got nested one level too deep (`flickmix/FlickMix/app/...` instead
of `flickmix/app/...`).
