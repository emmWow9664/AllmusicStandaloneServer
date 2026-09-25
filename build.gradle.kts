import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.example"
version = "1.3"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/groups/public/")
}

val adventureVersion = "4.21.0"

dependencies {
    // AllMusic 核心依赖
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("io.netty:netty-buffer:4.1.109.Final")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.6.1")
    implementation("org.apache.httpcomponents.core5:httpcore5:5.4.2")
    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4.2")

    // Adventure 文本组件（核心代码使用 MiniMessage）
    implementation("net.kyori:adventure-api:$adventureVersion")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    implementation("net.kyori:adventure-text-serializer-gson:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")

    // 现代化 GUI（明暗主题），本地 jar 依赖
    implementation(files("libs/flatlaf-3.5.4.jar"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ShadowJar> {
    archiveFileName.set("AllmusicStandaloneServer-${project.version}.jar")
    destinationDirectory.set(file("build/libs"))
    // 与原版 AllMusic 一致：将 httpclient 依赖重定位，保证官方音乐 API jar（如 netapi）可正常加载
    relocate("org.apache.hc", "com.coloryr.allmusic.libs.org.apache.hc")
    manifest {
        attributes["Main-Class"] = "com.example.standalone.Main"
        attributes["Implementation-Title"] = "Allmusic Standalone Server"
        attributes["Implementation-Version"] = project.version
    }
    // Gradle 7.6+ 会清理 build/ 下"陈旧的任务输出"，导致历史版本 jar 被删除。
    // 因此额外归档一份到项目根目录的 releases/（不在 build/ 内，不会被清理），保证新旧版本都保留。
    doLast {
        val built = archiveFile.get().asFile
        val keepDir = layout.projectDirectory.dir("releases").asFile
        keepDir.mkdirs()
        val kept = File(keepDir, built.name)
        if (!kept.exists() || kept.length() != built.length()) {
            built.copyTo(kept, overwrite = true)
        }
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
}
