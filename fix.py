import re

# Fix MainActivity.kt
with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    ma = f.read()

ma = re.sub(r'import com\.google\.firebase\..*\n', '', ma)
ma = re.sub(r'FirebaseApp\.initializeApp\(this\).*?\n', '', ma)
ma = re.sub(r'val options = FirebaseOptions\.Builder\(\).*?\.build\(\)\n', '', ma, flags=re.DOTALL)
ma = re.sub(r'\s*FirebaseApp\.initializeApp\(this, options\)\n', '', ma)
ma = re.sub(r'\s*val auth = .*?\n', '', ma)
ma = re.sub(r'\s*val currentUser = auth\.currentUser\n', '', ma)
ma = re.sub(r'\s*com\.google\.firebase\.auth\.FirebaseAuth\.getInstance\(\)\.signOut\(\)\n', '', ma)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(ma)

# Fix NotificationSetterViewModel.kt
with open('app/src/main/java/com/example/NotificationSetterViewModel.kt', 'r') as f:
    vm = f.read()

vm = re.sub(r'import com\.google\.firebase\..*\n', '', vm)
# We will just replace loadCloudPreference and saveCloudPreference entirely
load_pattern = re.compile(r'fun loadCloudPreference\(context: android\.content\.Context, userEmail: String\) \{.*?_isProcessing\.value = false\n\s*\}\n\s*\}', re.DOTALL)
save_pattern = re.compile(r'private fun saveCloudPreference\(context: android\.content\.Context, userEmail: String, fileUri: Uri, fileName: String, fileSize: String, durationMs: Long, start: Float, end: Float\) \{.*?_isProcessing\.value = false\n\s*\}\n\s*\}', re.DOTALL)

def repl_load(m):
    return '''fun loadCloudPreference(context: android.content.Context, userEmail: String) {
        if (userEmail.isEmpty() || userEmail.startsWith("guest_")) return
        _isProcessing.value = false
    }'''

def repl_save(m):
    return '''private fun saveCloudPreference(context: android.content.Context, userEmail: String, fileUri: Uri, fileName: String, fileSize: String, durationMs: Long, start: Float, end: Float) {
        if (userEmail.isEmpty() || userEmail.startsWith("guest_")) return
        _isProcessing.value = false
    }'''

vm = load_pattern.sub(repl_load, vm)
vm = save_pattern.sub(repl_save, vm)

# Also remove any dangling db. or storage.
vm = re.sub(r'\s*val db = FirebaseFirestore\.getInstance\(\)\n', '', vm)
vm = re.sub(r'\s*val storage = com\.google\.firebase\.storage\.FirebaseStorage\.getInstance\(\)\n', '', vm)

# If there are any db.collection(...) calls left, remove them
vm = re.sub(r'\s*db\.collection.*?\n', '', vm)
vm = re.sub(r'\s*\.addOnSuccessListener.*?\n', '', vm)
vm = re.sub(r'\s*\.addOnFailureListener.*?\n', '', vm)

with open('app/src/main/java/com/example/NotificationSetterViewModel.kt', 'w') as f:
    f.write(vm)

