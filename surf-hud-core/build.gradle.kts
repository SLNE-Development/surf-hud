import dev.slne.surf.api.gradle.util.slneReleases

plugins {
    id("dev.slne.surf.api.gradle.core")
}

dependencies {
    api(projects.surfHudApi)
    // Adventure and Gson come from surf-api / the server; creative must not bring its own copies.
    // creative's types extend Examinable, which Adventure 5 no longer ships; the excluded net.kyori group took it along.
    implementation("net.kyori:examination-api:1.3.0")
    implementation("team.unnamed:creative-api:1.7.3") {
        exclude(group = "net.kyori")
        exclude(group = "com.google.code.gson")
    }
    implementation("team.unnamed:creative-serializer-minecraft:1.7.3") {
        exclude(group = "net.kyori")
        exclude(group = "com.google.code.gson")
    }
}

publishing {
    repositories {
        slneReleases()
    }
}
