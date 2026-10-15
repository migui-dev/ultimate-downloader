import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.text.AttributedString
import java.util.zip.ZipFile
import javax.imageio.ImageIO

plugins {
    java
    application
}

group = "com.ultimatedownloader"
version = "1.0.0"

val javafxVersion = "21.0.12"
val jacksonVersion = "2.22.3"

// Sistema para el que se empaqueta. Normalmente es en el que se compila; con
// -Pdestino=win|linux|mac|mac-aarch64|linux-aarch64 se puede pedir otro, que sirve para
// comprobar que las dependencias resuelven, no para generar el instalador de otro sistema
// (jpackage solo sabe crear el del suyo).
val sistemaActual = org.gradle.internal.os.OperatingSystem.current()
val esArm = System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm") }

val destino: String = (project.findProperty("destino") as String?) ?: when {
    sistemaActual.isWindows -> "win"
    sistemaActual.isMacOsX -> if (esArm) "mac-aarch64" else "mac"
    else -> if (esArm) "linux-aarch64" else "linux"
}

// JavaFX se declara con el clasificador del sistema: ese artefacto ya trae las bibliotecas
// nativas, así que no hace falta el plugin de OpenJFX ni configurar module-path a mano.
val javafxPlatform = destino

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // isTransitive = false: cada artefacto "-win" ya incluye clases y nativos, y así
    // ninguna dependencia transitiva puede colar otro JavaFX (sin nativos) en el classpath.
    listOf("javafx-base", "javafx-graphics", "javafx-controls", "javafx-fxml").forEach {
        implementation("org.openjfx:$it:$javafxVersion:$javafxPlatform") { isTransitive = false }
    }

    // Base estructural de los controles. El color entero lo ponen tokens.css y app.css.
    // AtlantaFX pide javafx-controls:23 sin nativos: se excluye y manda el bloque de arriba.
    implementation("io.github.mkpaz:atlantafx-base:2.1.0") {
        exclude(group = "org.openjfx")
    }

    // Barra de título oscura de Windows (DwmSetWindowAttribute). En Linux y macOS no se usa,
    // pero son 2 MB y evitan tener que publicar un jar distinto por sistema.
    implementation("net.java.dev.jna:jna-platform:5.19.1")

    // JSON de `yt-dlp -J` y fichero de ajustes.
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    // Registro en fichero.
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("ch.qos.logback:logback-classic:1.6.5")
}

application {
    // Launcher no extiende Application: así JavaFX arranca igual desde el classpath,
    // tanto en desarrollo como dentro de la imagen generada por jpackage.
    mainClass = "com.ultimatedownloader.Launcher"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-serial,-this-escape")
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    // `gradlew run -PcerrarAlArrancar` abre la ventana y la cierra: comprobación sin interacción.
    if (project.hasProperty("cerrarAlArrancar")) {
        jvmArgs("-Dud.cerrarAlArrancar=true")
    }
}

// ---------------------------------------------------------------- desarrollo

/*
 * Opciones para revisar la interfaz sin tener que ir pulsando a mano:
 *   gradlew run -Purl=https://...          analiza ese enlace al arrancar
 *   gradlew run -Pdescargar                 y además lo manda a la cola
 *   gradlew run -Pabrir=ajustes             abre una pantalla concreta
 *   gradlew run -Parchivos=a.mkv;b.mp4      los carga en el módulo Convertir
 *   gradlew run -Pentorno=C:/tmp/py         usa otra carpeta para el entorno de Python
 *   gradlew run -Pseparar=cancion.flac      lo separa nada más arrancar
 *   gradlew run -Pidioma=en                 cambia de idioma en caliente a los 4 segundos
 *   gradlew run -Pcaptura=ruta.png          guarda una captura y cierra
 *   gradlew run -Psegundos=10               segundos de espera antes de capturar
 *   gradlew run -Pventana=1180x1700         tamaño de la ventana para la captura
 */
tasks.named<JavaExec>("run") {
    mapOf(
        "captura" to "ud.capturaEn",
        "segundos" to "ud.capturaTrasSegundos",
        "url" to "ud.urlInicial",
        "ventana" to "ud.ventana",
        "abrir" to "ud.abrir",
        "archivos" to "ud.archivos",
        "entorno" to "ud.entorno",
        "separar" to "ud.separar",
        "idioma" to "ud.cambiarIdioma",
    ).forEach { (propiedad, sistema) ->
        if (project.hasProperty(propiedad)) {
            jvmArgs("-D$sistema=${project.property(propiedad)}")
        }
    }
    if (project.hasProperty("descargar")) {
        jvmArgs("-Dud.descargarAlArrancar=true")
    }
    if (project.hasProperty("instalar")) {
        jvmArgs("-Dud.instalarEntorno=true")
    }
}

// ---------------------------------------------------------------- empaquetado

/*
 * jpackage crea una aplicación autocontenida: lleva dentro su propio runtime de Java, así que
 * funciona en un equipo que no tenga Java instalado.
 *
 *   gradlew imagenPortable   carpeta ejecutable en build/dist
 *   gradlew zipPortable      esa carpeta comprimida, lista para repartir
 *   gradlew instalador       instalador del sistema en el que se ejecuta:
 *                            .exe en Windows (necesita WiX), .deb en Linux, .dmg en macOS
 *
 * jpackage solo genera lo de su propio sistema: los tres instaladores salen de la misma
 * configuración, cada uno en su máquina (o en su runner de GitHub Actions).
 */
val nombreAplicacion = "Ultimate Downloader"

// En macOS la imagen que genera jpackage es un paquete «.app»; en el resto, una carpeta normal.
val nombreDeLaImagen = if (sistemaActual.isMacOsX) "$nombreAplicacion.app" else nombreAplicacion
val carpetaDist = layout.buildDirectory.dir("dist")
val carpetaEntradaJpackage = layout.buildDirectory.dir("jpackage-entrada")

// Módulos del JDK que hacen falta. Se indican a mano porque los jar de JavaFX van en el
// classpath y jdeps no puede deducirlos desde ahí: java.desktop para guardar PNG,
// java.net.http para descargar los binarios y java.xml para la configuración de Logback.
val modulosNecesarios = listOf(
    "java.base", "java.desktop", "java.net.http", "java.logging",
    "java.xml", "java.naming", "java.management", "java.prefs",
    "java.scripting", "jdk.unsupported", "jdk.crypto.ec",
    // Sin jdk.localedata el runtime solo trae la configuración regional raíz y los números
    // salen con punto decimal: «276.2 GB» en vez de «276,2 GB».
    "jdk.localedata",
).joinToString(",")

/*
 * yt-dlp y ffmpeg, dentro del paquete.
 *
 * Se bajan al empaquetar y viajan en la aplicación, para que al instalarla ya esté todo y no
 * haya que esperar a ninguna descarga la primera vez. Son los mismos archivos que bajaría la
 * aplicación por su cuenta y de las mismas fuentes: los binarios oficiales de yt-dlp y, de
 * ffmpeg, las compilaciones estáticas que publica el propio proyecto yt-dlp para Windows y
 * Linux y las de ffmpeg-static para macOS, donde aquellas no llegan.
 *
 * Engordan el instalador en algo más de 100 MB, que es el precio de que funcione nada más
 * instalarlo. Si alguna vez se prefiere lo contrario, basta con quitar el «from» de
 * prepararJpackage: la aplicación vuelve a descargarlos sola al arrancar.
 *
 * Como van dentro de la carpeta de instalación, «Actualizar yt-dlp» puede no tener permiso
 * para reemplazarlos; en ese caso la aplicación se baja la versión nueva a la carpeta de
 * datos del usuario, que tiene prioridad sobre esta.
 */
val carpetaBinariosIncluidos = layout.buildDirectory.dir("binarios")

val urlYtDlp = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + when {
    sistemaActual.isWindows -> "yt-dlp.exe"
    sistemaActual.isMacOsX -> "yt-dlp_macos"
    else -> "yt-dlp_linux"
}

// En macOS no hay compilación de ffmpeg del proyecto yt-dlp: se usan las de ffmpeg-static,
// que publica un binario por arquitectura en vez de un paquete.
val baseFfmpegStatic = "https://github.com/eugeneware/ffmpeg-static/releases/latest/download/"
val sufijoMac = if (esArm) "darwin-arm64" else "darwin-x64"
val paqueteFfmpeg = when {
    sistemaActual.isWindows ->
        "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"
    sistemaActual.isMacOsX -> null
    else ->
        "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"
}

fun bajar(url: String, destino: File) {
    destino.parentFile.mkdirs()
    logger.lifecycle("Descargando $url")
    uri(url).toURL().openStream().use { entrada ->
        destino.outputStream().use { salida -> entrada.copyTo(salida) }
    }
}

val binariosIncluidos = tasks.register("binariosIncluidos") {
    group = "distribution"
    description = "Descarga yt-dlp y ffmpeg para meterlos dentro del paquete."

    val salida = carpetaBinariosIncluidos
    val esWindows = sistemaActual.isWindows
    val esMac = sistemaActual.isMacOsX
    val ejecutables = listOf("ffmpeg", "ffprobe").map { if (esWindows) "$it.exe" else it }
    val nombreYtDlp = if (esWindows) "yt-dlp.exe" else "yt-dlp"

    // Sin inputs, Gradle daría la tarea por hecha con que exista la carpeta. Con las URL como
    // entrada se rehace cuando cambian; para forzar una versión nueva de los binarios, clean.
    inputs.property("urlYtDlp", urlYtDlp)
    inputs.property("paqueteFfmpeg", paqueteFfmpeg ?: baseFfmpegStatic + sufijoMac)
    outputs.dir(salida)

    doLast {
        val destino = salida.get().asFile
        destino.deleteRecursively()
        destino.mkdirs()

        bajar(urlYtDlp, File(destino, nombreYtDlp))

        if (esMac) {
            ejecutables.forEach { bajar("$baseFfmpegStatic$it-$sufijoMac", File(destino, it)) }
        } else {
            val paquete = File(destino, if (esWindows) "ffmpeg.zip" else "ffmpeg.tar.xz")
            bajar(paqueteFfmpeg!!, paquete)
            if (esWindows) {
                // El zip trae la carpeta de la compilación y dentro bin/: se sacan los dos
                // ejecutables que hacen falta y se tira el resto, que incluye ffplay.
                ZipFile(paquete).use { zip ->
                    zip.entries().asSequence()
                        .filter { entrada -> ejecutables.any { entrada.name.endsWith("/bin/$it") } }
                        .forEach { entrada ->
                            val archivo = File(destino, entrada.name.substringAfterLast('/'))
                            zip.getInputStream(entrada).use { dentro ->
                                archivo.outputStream().use { fuera -> dentro.copyTo(fuera) }
                            }
                        }
                }
            } else {
                val dentroDelTar = providers.exec {
                    commandLine("tar", "-tf", paquete.absolutePath)
                }.standardOutput.asText.get().lines()
                    .filter { linea -> ejecutables.any { linea.endsWith("/bin/$it") } }
                providers.exec {
                    commandLine(
                        listOf("tar", "-xf", paquete.absolutePath, "-C", destino.absolutePath,
                            "--strip-components=2") + dentroDelTar,
                    )
                }.result.get().assertNormalExitValue()
            }
            paquete.delete()
        }

        (ejecutables + nombreYtDlp).forEach { File(destino, it).setExecutable(true, false) }
        destino.listFiles()?.forEach { logger.lifecycle("  ${it.name} (${it.length() / 1_048_576} MB)") }
    }
}

// Copia el jar y todas sus dependencias a una sola carpeta.
val prepararJpackage = tasks.register<Sync>("prepararJpackage") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    // Los binarios van en «herramientas», dentro de la carpeta de la aplicación. jpackage no
    // sabe nada de ellos: simplemente los copia como un archivo más del programa.
    from(binariosIncluidos) { into("herramientas") }
    into(carpetaEntradaJpackage)
}

// Aparta la imagen anterior renombrándola, y solo después la borra. Con la aplicación
// abierta Windows bloquea sus .exe: el renombrado falla entero y la imagen que había sigue
// sirviendo. Borrando directamente se iban los archivos no bloqueados —el .cfg con el
// classpath, entre ellos— y quedaba una carpeta a medias que ya no arrancaba.
fun apartarImagenAnterior() {
    val imagen = carpetaDist.get().dir(nombreDeLaImagen).asFile
    if (!imagen.exists()) {
        return
    }
    val apartada = File(imagen.parentFile, "${nombreDeLaImagen}.anterior-${System.nanoTime()}")
    if (!imagen.renameTo(apartada)) {
        throw GradleException(
            "No se ha podido reemplazar la imagen anterior: hay archivos en uso.\n" +
                "Cierra «$nombreAplicacion» si lo tienes abierto y vuelve a intentarlo.\n" +
                "La imagen que ya había sigue intacta en ${imagen.absolutePath}",
        )
    }
    delete(apartada)
}

// Ruta de jpackage dentro del JDK del toolchain.
fun rutaJpackage(): String {
    val servicio = extensions.getByType<JavaToolchainService>()
    val lanzador = servicio.launcherFor(java.toolchain).get()
    val nombre = if (sistemaActual.isWindows) "bin/jpackage.exe" else "bin/jpackage"
    return lanzador.metadata.installationPath.file(nombre).asFile.absolutePath
}

// El icono tiene un formato por sistema; si falta, jpackage pone el suyo por defecto.
fun iconoDelSistema(): File? {
    val img = layout.projectDirectory.dir("src/main/resources/com/ultimatedownloader/img")
    val archivo = when {
        sistemaActual.isWindows -> img.file("icono.ico")
        sistemaActual.isMacOsX -> img.file("icono.icns")
        else -> img.file("icono-256.png")
    }.asFile
    return if (archivo.isFile) archivo else null
}

/*
 * Opciones de jlink. El runtime se lleva más de la mitad del tamaño del paquete, así que se
 * quita todo lo que no se usa: símbolos de depuración, cabeceras, páginas de manual y los
 * ejecutables del JDK (java, keytool…), que el lanzador de jpackage no necesita.
 */
val opcionesJlink = listOf(
    "--strip-debug",
    "--no-header-files",
    "--no-man-pages",
    "--strip-native-commands",
    "--compress=zip-6",
).joinToString(" ")

val imagenPortable = tasks.register<Exec>("imagenPortable") {
    group = "distribution"
    description = "Crea en build/dist una carpeta ejecutable con el runtime de Java incluido."
    dependsOn(prepararJpackage)
    // Sin declarar las entradas, Gradle da la tarea por hecha solo con que exista la carpeta
    // de salida, y se queda una imagen vieja que parece recién hecha.
    inputs.dir(carpetaEntradaJpackage)
    outputs.dir(carpetaDist.map { it.dir(nombreDeLaImagen) })

    doFirst {
        apartarImagenAnterior()
        carpetaDist.get().asFile.mkdirs()
        commandLine(
            rutaJpackage(),
            "--type", "app-image",
            "--name", nombreAplicacion,
            "--app-version", project.version.toString(),
            "--vendor", "Ultimate Downloader",
            "--description", "Descarga, convierte y separa voz",
            "--copyright", "Software libre, sin garantía",
            "--dest", carpetaDist.get().asFile.absolutePath,
            "--input", carpetaEntradaJpackage.get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", "com.ultimatedownloader.Launcher",
            "--add-modules", modulosNecesarios,
            "--jlink-options", opcionesJlink,
            "--java-options", "-Dfile.encoding=UTF-8",
            "--java-options", "-Xmx1g",
            // jpackage cambia $APPDIR por la carpeta de la aplicación al arrancar, que no
            // está en el mismo sitio en los tres sistemas. Es como encuentra yt-dlp y ffmpeg
            // sin tener que deducir la ruta desde el ejecutable.
            "--java-options", "-Dud.herramientas=\$APPDIR/herramientas",
        )
        iconoDelSistema()?.let { args("--icon", it.absolutePath) }
    }
}

// Acceso directo en la raíz del proyecto. Lo rehace imagenPortable cada vez: apunta a
// build/dist, que se borra con clean, y un .lnk no sabe si lo que hay al otro lado sigue ahí.
val accesoDirecto = tasks.register("accesoDirecto") {
    group = "distribution"
    description = "Crea en la raíz del proyecto un acceso directo a la versión portable."
    onlyIf("solo tiene sentido en Windows") { sistemaActual.isWindows }

    doLast {
        val destino = layout.projectDirectory.file("$nombreAplicacion.lnk").asFile
        val ejecutable = carpetaDist.get().dir(nombreAplicacion).file("$nombreAplicacion.exe")
            .asFile
        if (!ejecutable.isFile) {
            throw GradleException(
                "No hay ninguna versión portable a la que apuntar.\n" +
                    "Ejecuta antes: gradlew imagenPortable",
            )
        }
        // WScript.Shell es la única forma de escribir un .lnk sin dependencias de terceros.
        val guion = """
            ${'$'}atajo = (New-Object -ComObject WScript.Shell).CreateShortcut('${destino.absolutePath}')
            ${'$'}atajo.TargetPath = '${ejecutable.absolutePath}'
            ${'$'}atajo.WorkingDirectory = '${ejecutable.parentFile.absolutePath}'
            ${'$'}atajo.IconLocation = '${ejecutable.absolutePath},0'
            ${'$'}atajo.Description = 'Descarga, convierte y separa voz'
            ${'$'}atajo.Save()
        """.trimIndent()
        providers.exec {
            commandLine("powershell", "-NoProfile", "-NonInteractive", "-Command", guion)
        }.result.get().assertNormalExitValue()
        logger.lifecycle("Acceso directo actualizado: ${destino.absolutePath}")
    }
}

imagenPortable.configure { finalizedBy(accesoDirecto) }

val zipPortable = tasks.register<Zip>("zipPortable") {
    group = "distribution"
    description = "Comprime la versión portable en un ZIP."
    dependsOn(imagenPortable)
    from(carpetaDist.map { it.dir(nombreDeLaImagen) })
    into(nombreAplicacion)
    archiveFileName.set("UltimateDownloader-${project.version}-portable.zip")
    destinationDirectory.set(carpetaDist)
}

// ------------------------------------------ personalización del instalador de Windows

/*
 * jpackage deja personalizar el instalador sustituyendo las piezas con las que lo construye:
 * --resource-dir es una carpeta donde busca archivos con nombres concretos y usa los suyos
 * solo para los que no encuentre. Lo que se le pasa desde aquí:
 *
 *   main.wxs       plantilla WiX con el idioma del paquete y las imágenes del asistente
 *   ShortcutPromptDlg.wxs  la pantalla de accesos directos, sin la casilla del menú Inicio:
 *                  esa entrada se crea siempre, que es de donde la saca el buscador
 *   es-es.wxl      los textos en español (y MsiInstallerStrings_en.wxl, el respaldo)
 *   banner.bmp     cabecera de 493x58, sale en todas las pantallas menos la primera y la última
 *   lateral.bmp    imagen de 493x312 de la primera pantalla y la última
 *   licencia.rtf   la licencia que hay que aceptar, en el formato que pide WiX
 *
 * Las imágenes y el RTF se generan en vez de guardarse hechos: así el instalador usa siempre
 * el icono, la tipografía y la licencia que hay en el repositorio, y no se quedan atrás
 * cuando alguno cambie. Los dos .wxs y .wxl, que son los que de verdad hay que mantener a
 * mano, están en src/main/installer/windows.
 */
/*
 * Dependencias del .deb, escritas a mano en vez de dejar que las deduzca jpackage.
 *
 * jpackage mira en qué paquete vive cada biblioteca que usa la aplicación, pero lo mira en la
 * máquina donde se compila, y los nombres cambian entre versiones de Ubuntu: en 24.04 varias
 * pasaron a llamarse «...t64» al cambiar time_t a 64 bits. Un paquete construido allí pide
 * «libasound2t64» y no se puede instalar en 22.04 ni en Debian 12; construido en 22.04, al
 * revés, depende de que la otra declare que lo provee. Poniéndolas aquí con alternativas
 * («a | b», que dpkg resuelve con la primera que exista), el mismo .deb vale en todas y da
 * igual dónde se haya construido.
 *
 * Solo hacen falta las que la aplicación enlaza directamente: las demás vienen arrastradas.
 * Esto no cambia con qué glibc funciona —de eso se encargan el runtime de Java y las
 * bibliotecas de JavaFX, compiladas contra una antigua—, solo qué le pide al gestor.
 *
 * --linux-package-deps por sí solo no basta: jpackage lo añade detrás de su lista en vez de
 * sustituirla («Depends: PACKAGE_DEFAULT_DEPENDENCIES PACKAGE_CUSTOM_DEPENDENCIES» en su
 * plantilla), y la suya seguía trayendo el nombre con t64. Por eso se le pasa también
 * src/main/installer/linux, con una copia de esa plantilla sin la parte automática.
 *
 * libc6 no está en esta lista porque va escrito en esa plantilla: jpackage antepone «, » a
 * lo que se le pase aquí siempre que su lista no esté vacía —y sigue calculándola aunque no
 * se use—, así que hace falta algo delante o el Depends empieza por coma y dpkg-deb se
 * niega a construir el paquete.
 *
 * No vale para el .rpm: ahí «|» no significa nada, así que todo esto es solo del .deb.
 */
val dependenciasDeb = listOf(
    "libasound2t64 | libasound2",
    "libx11-6",
    "libxext6",
    "libxi6",
    "libxrender1",
    "libxtst6",
    "xdg-utils",
).joinToString(", ")

val carpetaRecursosInstalador = layout.buildDirectory.dir("jpackage-recursos")
val fuenteInstalador = layout.projectDirectory
    .file("src/main/resources/com/ultimatedownloader/fonts/Inter-SemiBold.ttf")

// Negro y blanco, los mismos de tokens.css. El asistente dibuja su texto en negro encima de
// estas imágenes, así que la parte donde escribe tiene que quedarse clara.
val negro = Color(0x000000)
val blanco = Color(0xFFFFFF)
val grisBorde = Color(0xE5E5E5)

// Dibuja una línea centrada en [centroX] con la separación entre letras de los rótulos.
fun rotulo(g: java.awt.Graphics2D, texto: String, fuente: Font, centroX: Int, base: Int) {
    val atribuido = AttributedString(texto)
    atribuido.addAttribute(TextAttribute.FONT, fuente)
    atribuido.addAttribute(TextAttribute.TRACKING, 0.12)
    val ancho = g.fontMetrics.stringWidth(texto) + (texto.length * fuente.size2D * 0.12f).toInt()
    g.drawString(atribuido.iterator, (centroX - ancho / 2).toFloat(), base.toFloat())
}

fun lienzo(ancho: Int, alto: Int, pintar: (java.awt.Graphics2D) -> Unit): BufferedImage {
    // Sin canal alfa: ImageIO escribe entonces un BMP de 24 bits, que es lo que entiende WiX.
    val imagen = BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB)
    val g = imagen.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.color = blanco
    g.fillRect(0, 0, ancho, alto)
    pintar(g)
    g.dispose()
    return imagen
}

// Pasa un texto a RTF, que es el único formato que el diálogo de licencia sabe enseñar con
// formato. Los párrafos del archivo van separados por líneas en blanco: cada bloque se junta
// en un párrafo para que el asistente lo reparta según el ancho de su ventana.
fun textoARtf(texto: String): String {
    val parrafos = texto.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { bloque ->
        bloque.trim().lines().joinToString(" ") { it.trim() }
            .replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")
            // RTF es ASCII: las tildes van como \uNNNN con un sustituto detrás para los
            // lectores antiguos, que es lo que descarta WiX al enseñarlo.
            .map { if (it.code > 127) "\\u${it.code}?" else it.toString() }.joinToString("")
    }
    return parrafos.joinToString("\\par\\par\n", "{\\rtf1\\ansi\\deff0" +
        "{\\fonttbl{\\f0\\fswiss\\fcharset0 Segoe UI;}}\n\\fs18\n", "\n}\n")
}

val recursosInstalador = tasks.register("recursosInstalador") {
    group = "distribution"
    description = "Genera en build/jpackage-recursos las imágenes y textos del instalador."
    onlyIf("solo el instalador de Windows usa WiX") { sistemaActual.isWindows }

    val origen = layout.projectDirectory.dir("src/main/installer/windows")
    val licencia = layout.projectDirectory.file("LICENSE")
    val icono = layout.projectDirectory
        .file("src/main/resources/com/ultimatedownloader/img/icono-256.png")
    val fuente = fuenteInstalador
    val salida = carpetaRecursosInstalador

    inputs.dir(origen)
    inputs.file(licencia)
    inputs.file(icono)
    inputs.file(fuente)
    outputs.dir(salida)

    doLast {
        val destino = salida.get().asFile
        destino.mkdirs()
        val marca = ImageIO.read(icono.asFile)
        val inter = Font.createFont(Font.TRUETYPE_FONT, fuente.asFile)

        // Cabecera. El asistente escribe su título a la izquierda, hasta unos 400 px, así que
        // el icono va al otro extremo.
        val banner = File(destino, "banner.bmp")
        ImageIO.write(
            lienzo(493, 58) { g -> g.drawImage(marca, 493 - 16 - 36, 11, 36, 36, null) },
            "bmp",
            banner,
        )

        // Primera pantalla y última. El texto empieza sobre los 180 px: la banda negra se
        // queda en 164 para no pasar por debajo.
        val lateral = File(destino, "lateral.bmp")
        ImageIO.write(
            lienzo(493, 312) { g ->
                g.color = negro
                g.fillRect(0, 0, 164, 312)
                g.color = grisBorde
                g.fillRect(164, 0, 1, 312)
                g.drawImage(marca, 38, 78, 88, 88, null)
                g.color = blanco
                g.font = inter.deriveFont(12f)
                rotulo(g, "ULTIMATE", g.font, 82, 206)
                rotulo(g, "DOWNLOADER", g.font, 82, 224)
            },
            "bmp",
            lateral,
        )

        File(destino, "licencia.rtf").writeText(textoARtf(licencia.asFile.readText()))

        // Las plantillas se copian con las marcas sustituidas: las rutas de las imágenes se
        // resuelven al enlazar, cuando WiX ya no sabe de dónde salió el archivo, así que
        // tienen que ir absolutas. Los textos van tal cual.
        origen.asFile.listFiles().orEmpty().forEach { archivo ->
            when (archivo.extension) {
                "wxs" -> File(destino, archivo.name).writeText(
                    archivo.readText()
                        .replace("@BANNER@", banner.absolutePath.replace("\\", "/"))
                        .replace("@LATERAL@", lateral.absolutePath.replace("\\", "/")),
                )
                "wxl" -> archivo.copyTo(File(destino, archivo.name), overwrite = true)
            }
        }
    }
}

/*
 * Instalador del sistema en el que se ejecuta la tarea.
 *
 *   Windows  .exe   necesita WiX Toolset v3 en el PATH
 *   Linux    .deb   necesita dpkg-deb y fakeroot (con -Ptipo=rpm, rpmbuild)
 *   macOS    .dmg   no necesita nada más
 */
val instalador = tasks.register<Exec>("instalador") {
    group = "distribution"
    description = "Crea el instalador nativo del sistema en el que se ejecuta."
    dependsOn(imagenPortable)
    if (sistemaActual.isWindows) {
        dependsOn(recursosInstalador)
    }

    doFirst {
        // WiX se puede usar sin instalarlo: basta con descomprimir wix314-binaries.zip y
        // pasar su carpeta con -Pwix=..., que es lo que hace falta para un .exe en Windows.
        (project.findProperty("wix") as String?)?.let {
            environment("PATH", it + File.pathSeparator + System.getenv("PATH"))
        }
        val tipo = (project.findProperty("tipo") as String?) ?: when {
            sistemaActual.isWindows -> "exe"
            sistemaActual.isMacOsX -> "dmg"
            else -> "deb"
        }
        commandLine(
            rutaJpackage(),
            "--type", tipo,
            "--name", nombreAplicacion,
            "--app-version", project.version.toString(),
            "--vendor", "Ultimate Downloader",
            "--description", "Descarga, convierte y separa voz",
            "--app-image", carpetaDist.get().dir(nombreDeLaImagen).asFile.absolutePath,
            "--dest", carpetaDist.get().asFile.absolutePath,
        )
        iconoDelSistema()?.let { args("--icon", it.absolutePath) }
        when {
            sistemaActual.isWindows -> args(
                // Instalación por usuario: no pide permisos de administrador y permite que
                // yt-dlp se actualice solo.
                "--win-per-user-install",
                "--win-dir-chooser",
                "--win-menu",
                "--win-menu-group", nombreAplicacion,
                "--win-shortcut",
                "--win-shortcut-prompt",
                // Imágenes, textos en español y plantilla WiX propia (ver recursosInstalador).
                "--resource-dir", carpetaRecursosInstalador.get().asFile.absolutePath,
                "--license-file",
                carpetaRecursosInstalador.get().file("licencia.rtf").asFile.absolutePath,
                // Identificador de la aplicación para Windows: es lo que mira el instalador
                // para saber que una versión sustituye a la anterior en vez de ponerse al
                // lado. jpackage lo venía calculando con un MD5 de «UpgradeCode/vendedor/
                // nombre», así que este es justo el valor que ya tienen las instalaciones
                // hechas hasta ahora; escrito aquí, cambiar el nombre o el vendedor deja de
                // romper las actualizaciones. No debe cambiar nunca.
                "--win-upgrade-uuid", "1FCBB50D-B846-39B1-9024-2B813A4AB97E",
                // Enlaces de la ficha de «Aplicaciones instaladas».
                "--about-url", "https://github.com/migui-dev/ultimate-downloader",
                "--win-help-url",
                "https://github.com/migui-dev/ultimate-downloader#instalación",
                "--win-update-url",
                "https://github.com/migui-dev/ultimate-downloader/releases/latest",
            )
            sistemaActual.isMacOsX -> args(
                "--mac-package-name", nombreAplicacion,
                "--mac-package-identifier", "com.ultimatedownloader.app",
            )
            else -> {
                args(
                    "--linux-package-name", "ultimate-downloader",
                    "--linux-app-category", "AudioVideo",
                    "--linux-menu-group", "AudioVideo;Audio;Video",
                    "--linux-shortcut",
                )
                if (tipo == "deb") {
                    args(
                        "--resource-dir",
                        layout.projectDirectory.dir("src/main/installer/linux")
                            .asFile.absolutePath,
                        "--linux-package-deps", dependenciasDeb,
                    )
                }
            }
        }
    }
}

/*
 * Nombre del archivo que se publica. jpackage lo saca de --name, así que salía con el espacio
 * de «Ultimate Downloader» dentro; GitHub lo sustituye por un punto al colgarlo de la release
 * y quedaba «Ultimate.Downloader-1.0.0.exe». Se renombra para que los tres sistemas descarguen
 * un archivo con el mismo nombre y bien escrito.
 *
 * Lo que ve el usuario una vez instalado no cambia: el programa se sigue llamando «Ultimate
 * Downloader» en el menú, en la ventana y en la lista de aplicaciones. El paquete de Debian
 * mantiene además su nombre interno en minúsculas, «ultimate-downloader», porque es lo que
 * exige dpkg y es el que hay que usar para desinstalarlo.
 */
instalador.configure {
    doLast {
        val carpeta = carpetaDist.get().asFile
        val extensiones = listOf("exe", "msi", "deb", "rpm", "dmg", "pkg")
        carpeta.listFiles { archivo -> archivo.extension in extensiones }.orEmpty()
            .filter { it.name.contains(" ") || it.name.startsWith("ultimate-downloader") }
            .forEach { archivo ->
                val nuevo = File(carpeta, "UltimateDownloader-${project.version}.${archivo.extension}")
                if (nuevo.exists()) {
                    nuevo.delete()
                }
                if (archivo.renameTo(nuevo)) {
                    logger.lifecycle("Instalador: ${nuevo.name}")
                }
            }
    }
}
