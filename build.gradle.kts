import org.gradle.api.tasks.Copy

plugins {
    base
    id("de.undercouch.download") version "4.1.1"
}

group = "ir.amv.laas.samples"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}
subprojects {
    pluginManager.withPlugin("org.fbme.gradle.mps") {
        if (the<org.fbme.gradle.MpsExtension>().hasBuildSolution) {
            buildBootstrap.get().inputs.dir("$projectDir/buildsolution/models")
            dependencies {
                "antBinaries"("org.apache.ant:ant-junit:1.10.1")
            }
            tasks.named("mpsPrepare") {
                dependsOn(buildBootstrap)
            }
        }
        build.dependsOn(tasks.named("build"))
    }
}
fun Task.antexec(path: String, vararg tasks: String) {
    doLast {
        javaexec {
            main = "org.apache.tools.ant.launch.Launcher"
            classpath = ant_lib
            args("-Dbasedir=$projectDir", "-buildfile", file(path))
            args(*tasks)
        }
    }
}

val mps by configurations.creating
val ant_lib by configurations.creating

dependencies {
    mps(mpsDistribution())
    ant_lib("org.apache.ant:ant-junit:1.10.1")
}

val buildBootstrap by tasks.registering {
    dependsOn(mps)
    inputs.dir("buildscripts/models")
    inputs.file("build-bootstrap.xml")
    outputs.dir("buildscripts/source_gen")
    antexec("build-bootstrap.xml", "generate")
}

val build by tasks.getting {
    dependsOn(buildBootstrap)
}

val buildRcp by tasks.registering {
    dependsOn(build)
    antexec("build/build-rcp.xml")
}

val copyStartupScripts by tasks.registering(Copy::class) {
    dependsOn(buildBootstrap)
    from("buildscripts/source_gen/org/fbme/ide/build")
    include("*.bat")
    include("*.sh")
    include("*.vmoptions")
    include("Info.plist.xml")
    into("build/startup")
}

val buildRcpDistrib by tasks.registering {
    dependsOn(buildRcp, copyStartupScripts)
    antexec("build/build-rcpdistrib.xml")
}

val clean by tasks.getting {
    antexec("build-bootstrap.xml", "clean", "cleanSources")
}

val jbrSdkVersion = "11_0_8"
val jbrBuild = "b1129.2"

fun registerJbrTaskFor(os: String): TaskProvider<Copy> {
    val downloadTask = tasks.register<de.undercouch.gradle.tasks.download.Download>("download${os}Jbr") {
        src("https://jetbrains.bintray.com/intellij-jbr/jbrsdk-$jbrSdkVersion-$os-x64-${jbrBuild}.tar.gz")
        dest(file("lib/jbr.$os.tar.gz"))
        overwrite(false)
    }
    return tasks.register<Copy>("copy${os}Jbr") {
        dependsOn(downloadTask)
        from(tarTree("lib/jbr.$os.tar.gz"))
        into(file("lib/jbr-$os"))
    }
}

val osxCopyJbr = registerJbrTaskFor("osx")
val linuxCopyJbr = registerJbrTaskFor("linux")
val winCopyJbr = registerJbrTaskFor("windows")

val copyJbr = tasks.register("copyJbr") {
    dependsOn(osxCopyJbr, linuxCopyJbr, winCopyJbr)
}

val buildRcpDistribJbr by tasks.registering {
    dependsOn(buildRcpDistrib, copyJbr)
    antexec("build/build-rcpdistrib-jbr.xml")
}
