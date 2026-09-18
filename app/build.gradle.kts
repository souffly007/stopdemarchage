import java.util.Base64
import java.util.Properties

plugins { id("com.android.application") }
val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
fun javaLiteral(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
val publicKey = localConfig.getProperty("supabase_publishable_key", "").trim()
require(!publicKey.startsWith("sb_secret_")) { "Ne jamais intégrer une clé Supabase secrète." }
if (publicKey.startsWith("eyJ")) {
    val parts = publicKey.split('.')
    val claims = if (parts.size == 3) String(Base64.getUrlDecoder().decode(parts[1])) else ""
    require(Regex("\"role\"\\s*:\\s*\"anon\"").containsMatchIn(claims)) { "Seul un JWT anon est accepté." }
}
android {
    buildFeatures { buildConfig = true }
    namespace = "fr.bonobo.stopdemarchage"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.bonobo.stopdemarchage"
        buildConfigField("String", "SUPABASE_URL", javaLiteral(localConfig.getProperty("supabase_url", "").trim().trimEnd('/')))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", javaLiteral(publicKey))
        minSdk = 29
        targetSdk = 36
        // Correctif de démarrage : code croissant pour installation par-dessus la V1.0.
        versionCode = 9
        versionName = "1.0-beta7"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}
// Aucun SDK publicitaire, aucune bibliothèque externe, aucun code natif.
