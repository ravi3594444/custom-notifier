import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    text = f.read()

text = re.sub(r'import com\.google\.firebase\..*\n', '', text)
text = re.sub(r'\s*try\s*\{\s*if\s*\(FirebaseApp\.getApps\(this\)\.isEmpty\(\)\).*?\} catch \(e: Exception\) \{.*?\}', '', text, flags=re.DOTALL)
text = re.sub(r'\s*try\s*\{\s*val options = FirebaseOptions\.Builder\(\).*?\} catch \(e: Exception\) \{.*?\}', '', text, flags=re.DOTALL)
text = re.sub(r'val auth = com\.google\.firebase\.auth\.FirebaseAuth\.getInstance\(\)', 'val auth = SupabaseClientManager.client.auth', text)
text = re.sub(r'val currentUser = auth\.currentUser', 'val currentUser = auth.currentUserOrNull()', text)
text = re.sub(r'com\.google\.firebase\.auth\.FirebaseAuth\.getInstance\(\)\.signOut\(\)', 'SupabaseClientManager.client.auth.signOut()', text)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(text)

with open("app/src/main/java/com/example/NotificationSetterViewModel.kt", "r") as f:
    text = f.read()

text = re.sub(r'import com\.google\.firebase\..*\n', '', text)
text = re.sub(r'val db = FirebaseFirestore\.getInstance\(\)', 'val db = "dummy"', text)
text = re.sub(r'val storage = com\.google\.firebase\.storage\.FirebaseStorage\.getInstance\(\)', 'val storage = "dummy"', text)

# Just neuter the cloud functions by inserting an early return.
# loadCloudPreference
text = re.sub(r'(fun loadCloudPreference\(context: android\.content\.Context, userEmail: String\) \{)', r'\1\n        return\n', text)
# saveCloudPreference
text = re.sub(r'(private fun saveCloudPreference\(context: android\.content\.Context, userEmail: String, fileUri: Uri, fileName: String, fileSize: String, durationMs: Long, start: Float, end: Float\) \{)', r'\1\n        return\n', text)

with open("app/src/main/java/com/example/NotificationSetterViewModel.kt", "w") as f:
    f.write(text)

