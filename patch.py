import sys

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    lines = f.readlines()

out = []
skip = False
for line in lines:
    if line.startswith('suspend fun trimAudio'):
        skip = True
    if line.startswith('fun setRingtoneFromUri'):
        skip = False
    
    if not skip:
        out.append(line)

new_functions = """
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig

suspend fun trimAudio(context: Context, inputUri: Uri, outputFile: File, startTimeMs: Long, endTimeMs: Long): Boolean = withContext(Dispatchers.IO) {
    try {
        val inputPath = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        val outputPath = outputFile.absolutePath
        
        val startSec = startTimeMs / 1000.0
        val toSec = endTimeMs / 1000.0
        
        // Use -ss and -to for exact trimming, avoid video with -vn, and set codec to aac
        val command = "-y -i \\"$inputPath\\" -ss $startSec -to $toSec -vn -c:a aac -b:a 128k \\"$outputPath\\""
        
        val session = FFmpegKit.execute(command)
        return@withContext session.returnCode.isValueSuccess
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    }
}

suspend fun extractAudioFromVideo(context: Context, videoUri: Uri, outputFile: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val inputPath = FFmpegKitConfig.getSafParameterForRead(context, videoUri)
        val outputPath = outputFile.absolutePath
        
        val command = "-y -i \\"$inputPath\\" -vn -c:a aac -b:a 128k \\"$outputPath\\""
        
        val session = FFmpegKit.execute(command)
        return@withContext session.returnCode.isValueSuccess
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    }
}

"""

out.insert(out.index('fun setRingtoneFromUri(context: Context, sourceUri: Uri, isExtracted: Boolean = false) {\n'), new_functions)

# Wait, the imports should go at the top of the file!
# Let's extract the imports and put them at the top.

# find the last import
last_import_idx = 0
for i, line in enumerate(out):
    if line.startswith('import '):
        last_import_idx = i

out.insert(last_import_idx + 1, "import com.arthenica.ffmpegkit.FFmpegKit\nimport com.arthenica.ffmpegkit.FFmpegKitConfig\n")

# remove them from new_functions
new_functions_clean = new_functions.replace("import com.arthenica.ffmpegkit.FFmpegKit\nimport com.arthenica.ffmpegkit.FFmpegKitConfig\n\n", "")
out[out.index(new_functions)] = new_functions_clean

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.writelines(out)

