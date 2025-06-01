plugins {
    id("java")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.avro:avro:1.12.0")
    implementation("org.apache.avro:avro-tools:1.12.0")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.apache.avro.tool.Main")
}
