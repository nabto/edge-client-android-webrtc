import java.io.ByteArrayOutputStream

fun GitCommand(vararg args: String): String {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
        standardOutput = stdout
        errorOutput = stderr
    }
    return stdout.toString().trim()
}

val gitStatus = GitCommand("status", "--porcelain")
val gitTag = GitCommand("describe", "--exact-match", "--tags")
val gitBranch = GitCommand("rev-parse", "--abbrev-ref", "HEAD")

val isReleaseVersion = gitTag.isNotEmpty() && gitStatus.isEmpty()

rootProject.extra["buildVersionName"] = run {
    if (isReleaseVersion) {
        gitTag.substring(1)
    } else {
        "$gitBranch-SNAPSHOT"
    }
}
