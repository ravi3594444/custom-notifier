-- =====================================================================
-- Custom Notifier - Supabase Setup Script
-- =====================================================================
-- Run this in your Supabase project's SQL Editor to create the table
-- and storage bucket the app needs for cloud sound sync.
--
-- This schema matches the UserSoundPreference data class in
-- NotificationSetterViewModel.kt (snake_case column names, table
-- "user_preferences", bucket "sounds").
-- =====================================================================

-- 1. Create the table that holds each user's last-saved sound metadata.
create table if not exists public.user_preferences (
    email              text primary key,
    selected_file_name text,
    selected_file_size text,
    trim_range_start   real,
    trim_range_end     real,
    media_duration_ms  bigint,
    fade_in_sec        real,
    fade_out_sec       real,
    volume_boost       real,
    last_updated       bigint
);

-- Allow authenticated users to read / write only their OWN row.
alter table public.user_preferences enable row level security;

drop policy if exists "Users can read own sound preference" on public.user_preferences;
create policy "Users can read own sound preference"
  on public.user_preferences for select
  to authenticated
  using (email = auth.jwt() ->> 'email');

drop policy if exists "Users can upsert own sound preference" on public.user_preferences;
create policy "Users can upsert own sound preference"
  on public.user_preferences for insert
  to authenticated
  with check (email = auth.jwt() ->> 'email');

drop policy if exists "Users can update own sound preference" on public.user_preferences;
create policy "Users can update own sound preference"
  on public.user_preferences for update
  to authenticated
  using (email = auth.jwt() ->> 'email')
  with check (email = auth.jwt() ->> 'email');

drop policy if exists "Users can delete own sound preference" on public.user_preferences;
create policy "Users can delete own sound preference"
  on public.user_preferences for delete
  to authenticated
  using (email = auth.jwt() ->> 'email');

-- 2. Create the storage bucket that holds the actual audio bytes.
--    The app calls `storage["sounds"]` and the download uses
--    `downloadPublic(...)`, so the bucket must be PUBLIC.
insert into storage.buckets (id, name, public)
values ('sounds', 'sounds', true)
on conflict (id) do nothing;

-- Storage RLS: a user may only touch files under users/<their_safe_email>/
drop policy if exists "Users can upload own sound file" on storage.objects;
create policy "Users can upload own sound file"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'sounds'
    and (storage.foldername(name))[1] = ('users/' || replace(replace(auth.jwt() ->> 'email', '@', '_'), '.', '_'))
  );

drop policy if exists "Anyone can read sound files" on storage.objects;
create policy "Anyone can read sound files"
  on storage.objects for select
  to anon, authenticated
  using (bucket_id = 'sounds');

drop policy if exists "Users can update own sound file" on storage.objects;
create policy "Users can update own sound file"
  on storage.objects for update
  to authenticated
  using (
    bucket_id = 'sounds'
    and (storage.foldername(name))[1] = ('users/' || replace(replace(auth.jwt() ->> 'email', '@', '_'), '.', '_'))
  );

drop policy if exists "Users can delete own sound file" on storage.objects;
create policy "Users can delete own sound file"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'sounds'
    and (storage.foldername(name))[1] = ('users/' || replace(replace(auth.jwt() ->> 'email', '@', '_'), '.', '_'))
  );
