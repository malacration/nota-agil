plugins {
	kotlin("jvm") version "2.2.0"
	kotlin("plugin.spring") version "2.2.0"
	id("org.springframework.boot") version "4.0.0-M1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "br.andrew"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webservices")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

//	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    implementation("io.jsonwebtoken:jjwt-api:0.12.4")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.4")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.4")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    //WSDL
    implementation("org.apache.axis:axis:1.7.9")
    implementation("org.apache.axis:axis-jaxrpc:1.4")
    implementation("org.apache.axis:axis-saaj:1.4")
    implementation("axis:axis-wsdl4j:1.5.1")
    implementation("javax.xml:jaxrpc-api:1.1")
    implementation("commons-discovery:commons-discovery:0.5"){
        exclude("commons-logging")
    }

	developmentOnly("org.springframework.boot:spring-boot-devtools")


    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

val wsdlDir = "$projectDir/src/generated/java"

tasks.named("assemble") {
    dependsOn(tasks.named("import-ws"))
}

tasks.named("compileKotlin") {
    dependsOn(tasks.named("import-ws"))
}

tasks.buildDependents {
    dependsOn(tasks.named("import-ws"))
}

tasks.register("import-ws") {
    val packgePrefix = "br.andrew.wsdl"
    fileTree("src/main/resources/wsdl").forEach{ file ->
        doLast {
            javaexec {
                mainClass.set("org.apache.axis.wsdl.WSDL2Java")
//				configurations.implementation.get().isCanBeResolved = true
                classpath = files(
                    configurations.runtimeClasspath.get().files,
//					configurations.implementation.get().files,
                    configurations.annotationProcessor.get().files
                )
                args = listOf("-o","$wsdlDir", "-p","$packgePrefix.${file.name}", file.absolutePath,)
            }
        }
    }
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.apache.axis") {
                useVersion("1.4")
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs(wsdlDir)
        }
    }
}