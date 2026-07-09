import re
with open("app/src/main/java/com/example/NotificationSetterViewModel.kt", "r") as f:
    text = f.read()

# Completely empty the bodies of loadCloudPreference and saveCloudPreference
load_pattern = re.compile(r'fun loadCloudPreference\(context: android\.content\.Context, userEmail: String\) \{.*?\n    \}', re.DOTALL)
save_pattern = re.compile(r'private fun saveCloudPreference\(context: android\.content\.Context, userEmail: String, fileUri: Uri, fileName: String, fileSize: String, durationMs: Long, start: Float, end: Float\) \{.*?\n    \}', re.DOTALL)

text = load_pattern.sub('fun loadCloudPreference(context: android.content.Context, userEmail: String) { }', text)
text = save_pattern.sub('private fun saveCloudPreference(context: android.content.Context, userEmail: String, fileUri: Uri, fileName: String, fileSize: String, durationMs: Long, start: Float, end: Float) { }', text)

with open("app/src/main/java/com/example/NotificationSetterViewModel.kt", "w") as f:
    f.write(text)
