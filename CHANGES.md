# Changes made to Custom Notifier

This file summarises the additional fixes layered on top of commit
`0b16781` ("Normalize volume boost range and fix ringtone/notification
sound customization preferences") which was already on `main`.

---

## What was already fixed by commit `0b16781` (on remote)

1. **Compile error in `MainActivity.kt`** — removed leftover `", e)` and stray `}`.
2. **Firebase → Supabase migration in `NotificationSetterViewModel.kt`** — replaced
   every broken `val db = "dummy"; db.collection(...)` stub with real Supabase
   Storage + Postgrest API calls. Uploads use `upsert = true`, so uploading a
   different file cleanly replaces the old one.
3. **Volume slider 1000% → 100%** — changed `valueRange` from `0.5f..10.0f` to
   `0.0f..1.0f` (0% to 100%, no boost beyond the original level).
4. **Local SharedPreferences caching** for offline-first behaviour.
5. **Safer media-player handling** via a `stopAndResetPlayer()` helper.

## What this commit (`supabase_setup.sql` + `RingtoneUtils.kt` improvements) adds

### 1. `RingtoneUtils.setRingtoneFromUri` — fix "set one but it doesn't go to default audio"

The remote commit only touched the IS_NOTIFICATION / IS_RINGTONE flags and the
folder split. The actual `setActualDefaultRingtoneUri` call was still unreliable
on many devices because:

- It copied bytes directly from the picker's `content://` Uri into MediaStore,
  and on many OEM ROMs that Uri is scope-restricted and becomes unreadable
  after the picker intent finishes — so the saved "default" silently pointed
  at a file the system could no longer open, and you had to go into System
  Sound Settings to fix it by hand.
- It never cleaned up the previous custom entry before inserting a new one, so
  multiple entries piled up and the system sometimes kept playing the older one.
- It called `RingtoneManager.setActualDefaultRingtoneUri` immediately after
  flipping `IS_PENDING` from 1 to 0, with no time for MediaStore to commit the
  change — on some ROMs the system still saw the row as "pending" and rejected
  it.

Now the function:

1. Copies the source bytes into a private cache file first, so the picker's
   scoped Uri is no longer involved.
2. Calls `deleteCustomRingtonesFromSystem` before inserting, so only the new
   sound is present.
3. Inserts the new row, copies bytes from the cache file, flips `IS_PENDING=0`,
   **waits 150 ms** for the commit, runs `MediaScannerConnection.scanFile` on
   the inserted Uri, and only THEN calls `setActualDefaultRingtoneUri`.
4. Verifies the default actually points at the new Uri afterward and shows a
   clear toast — so you don't have to manually open System Sound Settings to
   figure out whether it worked.

The remote's `IS_NOTIFICATION`/`IS_RINGTONE`/`RELATIVE_PATH` split is preserved.

### 2. `supabase_setup.sql` — one-shot DB + bucket setup

A run-once SQL script that creates:

- Table `public.user_preferences` with the snake_case columns the app writes
  (`email`, `selected_file_name`, `selected_file_size`, `trim_range_start`,
  `trim_range_end`, `media_duration_ms`, `fade_in_sec`, `fade_out_sec`,
  `volume_boost`, `last_updated`).
- Row-Level Security policies so each authenticated user can only read / write
  their OWN row.
- Storage bucket `sounds` (public, because the app calls `downloadPublic`).
- Storage RLS policies so each authenticated user can only upload / update /
  delete files under `users/<their_safe_email>/`.

Open it in your Supabase project's SQL Editor and click Run.

---

## Which Supabase API key do you need?

The app uses two Supabase libraries:

| Library                | Used for                                  |
| ---------------------- | ----------------------------------------- |
| `supabase-gotrue`      | Sign in / sign up / sign out (email + password) |
| `supabase-postgrest`   | Read / write the `user_preferences` table |
| `supabase-storage`     | Upload / download / delete audio files in the `sounds` bucket |

All three are wired up through `SupabaseClientManager.client`, which is
initialised from `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_KEY`
(see `SupabaseClientManager.kt`).

The key you need to put in your `.env` file is the **`anon` / `public` key**
(also called the "publishable" key in newer Supabase versions).

**DO NOT use the `service_role` key.** The `service_role` key bypasses
Row-Level Security and would let anyone who decompiles your APK read and
delete every other user's sounds. The `anon` key is safe to ship in the
client app because RLS policies (created by `supabase_setup.sql`) protect
the data — each user can only touch their own row.

### Where to find the `anon` key

1. Go to <https://supabase.com/dashboard> and open your project.
2. Click **Project Settings** (the gear icon, bottom-left).
3. Click **API** in the left sidebar.
4. Under **Project API keys**, copy the value next to **`anon` `public`**.
5. Also copy the **Project URL** at the top of the same page.

### What your `.env` should look like

```
SUPABASE_URL=https://YOUR-PROJECT-REF.supabase.co
SUPABASE_KEY=eyJhbGciOiJI...your-anon-public-key-here...JP_OCi8XwnQ
```

(That long string is the `anon` JWT — it's safe to ship in the APK. The
`service_role` JWT next to it is NOT — never put that one in the app.)

---

## "My Sounds" library (History) — added in a later commit

The app now keeps a personal library of every custom sound the user has
created, so they can switch between ringtones with one tap — no re-trim
needed.

### How it works

- Every time the user taps **Notification** or **Call Ringtone** in the
  Sound Customizer, after the audio is processed the resulting `.m4a` file
  is **copied into a permanent directory** (`filesDir/saved_sounds/`) and
  a `SavedSound` entry is appended to a per-user JSON index file
  (`filesDir/sound_library_<user>.json`).
- A new **"My Sounds"** tab in the navigation drawer lists every saved
  sound with: original filename, post-trim duration, file size, date
  created, last-applied-as badge (Notification / Ringtone / Both), and
  any fade / volume settings used.
- Each row has three actions:
  - **Preview (▶ / ⏸)** — plays the saved file in-app so the user can
    audition it before re-applying.
  - **Notification** — re-applies the sound as the system notification
    sound in one tap. No re-processing, no re-trim.
  - **Ringtone** — re-applies as the call ringtone.
  - **Delete (🗑)** — two-tap delete (tap once shows a "Confirm" button,
    tap again to actually delete). Removes the entry AND deletes the
    underlying file from disk.
- The library is **per-user** — signing out and signing in as a different
  user shows the right library.

### Files added / changed

| File | Purpose |
| --- | --- |
| `SavedSound.kt` (new) | `SavedSound` data class + `SoundLibraryManager` (atomic JSON load / save / add / remove / update) |
| `MySoundsScreen.kt` (new) | Compose UI: list, empty state, preview, apply, delete |
| `NotificationSetterViewModel.kt` | Added `savedSounds` / `previewingSoundId` state flows; `loadSoundLibrary`, `togglePreview`, `stopPreview`, `applySavedSound`, `deleteSavedSound`, `saveProcessedToLibrary`; auto-saves on every successful `processAudioAndSet` |
| `MainActivity.kt` | Added "My Sounds" drawer item + `library` screen branch; pre-loads library on sign-in |
| `CHANGES.md` | This section |

### Storage decisions

- **Plain JSON file instead of Room.** The library is small (typically
  5–50 entries per user) and we already use kotlinx.serialization. Adding
  Room + KSP would add compile time and config complexity for no real
  benefit at this scale.
- **Per-user index file.** `sound_library_<safe_email>.json` so signing
  in as a different user sees the right library. The actual audio bytes
  live in a shared `saved_sounds/` directory keyed by `SavedSound.id`
  in the filename, so different users' files never collide.
- **Atomic writes.** `SoundLibraryManager.saveAll` writes to a `.tmp`
  file first and renames, so a crash mid-write never leaves a truncated
  index.
- **Local-only for now.** The library is NOT synced to Supabase — only
  the *currently-active* sound is synced (via the existing
  `user_preferences` table). Cross-device library sync can be added
  later if needed.

### Bug fixed along the way

The previous Supabase-upload code read `_selectedFileName.value`,
`_selectedFileSize.value`, `_trimRange.value.start`, etc. **after**
`clearSelectedFile()` had already wiped them — so the cloud row was
always saved with empty filename / zero trim range / zero duration.
Fixed by capturing all editor state into local vals *before* clearing.

---

## What you need to do after pulling these changes

1. **Run `supabase_setup.sql`** in your Supabase project's SQL Editor (just
   once). This creates the `user_preferences` table and the `sounds` storage
   bucket with RLS policies.

2. **Put real Supabase credentials in `.env`:**
   ```
   SUPABASE_URL=https://YOUR-PROJECT-REF.supabase.co
   SUPABASE_KEY=<the anon public key, NOT the service_role key>
   ```

3. **Build & run** as usual. The app should now:
   - compile (the previous commit already fixed that),
   - actually set your custom sound as the system default without you having
     to go to System Sound Settings (this commit's `RingtoneUtils` fix),
   - upload a different file to the cloud successfully (replacing the previous
     one — already fixed in the previous commit, plus a latent bug where
     the cloud row got empty values is now fixed),
   - cap the volume slider at 100% instead of 1000% (already fixed in the
     previous commit),
   - **keep a personal "My Sounds" library** so you can re-apply any
     previously-created sound in one tap (this commit's addition).
