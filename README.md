# FerOs abues — launcher accesible Android

Repositorio: https://github.com/stroks15/Viejitos

## Estructura de las cinco pantallas

1. **Inicio:** reloj y fecha, accesos grandes, enlaces rápidos configurables y acceso al servicio de consulta.
2. **Contactos:** espacio previsto para los contactos del dispositivo. La lectura real de agenda y las 24 posiciones todavía requieren integración Android.
3. **WhatsApp:** pantalla preparada para mostrar notificaciones autorizadas. No lee conversaciones privadas de WhatsApp ni evita el permiso de Android.
4. **Videos:** exactamente tres espacios independientes **＋**. Cada uno solicita un enlace de canal y muestra hasta tres videos públicos recientes, ordenados del más nuevo al anterior. Editar y eliminar canales está disponible; no se precarga un canal de ejemplo. Al tocar un video se abre su URL de YouTube.
5. **Configuración:** conexión segura de YouTube (URL de Edge Function y clave pública anon), cuatro enlaces rápidos editables, nombre/teléfono/enlace del servicio de consulta, selector de aplicaciones visibles, tamaño de letra, lectura por voz y alto contraste. Las preferencias se guardan localmente.

## YouTube y Supabase

- Edge Function: `youtube-feed`.
- Proyecto Supabase existente: `yxjyqpnvgtkdaaukackt`.
- URL predeterminada: `https://yxjyqpnvgtkdaaukackt.supabase.co/functions/v1/youtube-feed`.
- `YOUTUBE_API_KEY` debe permanecer como secreto en Supabase; no se incorpora al APK.
- La app usa la clave pública `anon` del proyecto para invocar la función con JWT verificado.
- La función resuelve canales `/@handle` y `/channel/UC...`, obtiene la lista de subidas y devuelve como máximo tres videos públicos.

## Verificaciones ejecutadas

- `npm run typecheck`: correcto.
- `npx react-native bundle --platform android --dev false --entry-file index.js ...`: correcto; Metro generó el bundle JS.
- Petición real a `youtube-feed` con un canal público de prueba: HTTP 200 y tres videos devueltos. Esto confirma que la función está activa y que el secreto de YouTube está disponible para esa ejecución.

## Trabajo nativo pendiente antes de distribuir un APK

El repositorio ya contiene el proyecto Android nativo (`android/`), `AndroidManifest.xml` y Gradle Wrapper alineado con la plantilla oficial de React Native 0.81.5. La compilación de CI verifica que ese esqueleto genere una APK; no equivale a completar todas las integraciones nativas. Siguen pendientes la lectura de contactos reales y sus 24 posiciones, notificaciones mediante Notification Listener, integración de llamadas como app de teléfono/ROLE_DIALER, linterna, radio FM y apertura/administración de aplicaciones instaladas. No se debe anunciar como funcional una integración que aún no tenga módulo nativo y prueba en dispositivo.

## Seguridad y separación

La función `youtube-feed` es independiente de `gestionar-usuarios`; el despliegue de YouTube no modifica tablas ni reemplaza la función anterior. No colocar `service_role` ni la clave de YouTube en el cliente Android.


## Compilación automática de APK

El workflow `.github/workflows/build-apk.yml` compila una APK Android independiente (release) en GitHub Actions cuando se actualiza `main`, cuando se abre/actualiza un pull request hacia `main`, o manualmente desde Actions. La salida se publica como artefacto `viejitos-standalone-apk` durante 30 días. La compilación usa React Native 0.81.5, Node 20.19.4, JDK 17, Gradle Wrapper 8.14.3 y Android SDK 36. El workflow usa los command-line tools ya instalados en el runner y llama directamente a `sdkmanager`; no usa `android-actions/setup-android@v3`, cuya instalación intentaba instalar el paquete retirado `tools` y detenía el job antes de compilar. El nombre visible de la aplicación es `FerOs abues`; el identificador técnico `com.viejitos` se conserva para permitir actualizaciones sin cambiar de paquete. La interfaz y la actividad Android están bloqueadas en orientación vertical (portrait), y al abrir la app se solicita a Android establecer FerOs abues como aplicación HOME predeterminada.

La compilación se ejecuta desde el wrapper versionado (`android/gradlew`), para que local y CI utilicen la misma distribución de Gradle. Esta automatización genera una APK release con el bundle JavaScript integrado en `assets/index.android.bundle`, y verifica que el bundle exista y que la APK tenga una firma válida. Para facilitar la instalación de pruebas se firma con la clave debug efímera del runner. No es una APK de publicación firmada para Play Store. Para distribución pública se debe configurar una clave de firma privada mediante GitHub Secrets y un flujo release separado.

La APK standalone no necesita Metro, un servidor de desarrollo ni ADB para iniciar. La compilación de la APK no significa que estén terminadas las integraciones nativas de contactos, notificaciones, llamadas, linterna o radio FM; esas limitaciones siguen descritas arriba.


### Diferencia entre APK de desarrollo y APK independiente

`build:android:debug` genera una variante de desarrollo que, por configuración predeterminada de React Native, espera Metro y puede mostrar el mensaje `Unable to load script` cuando se instala sin el servidor. Para entregar e instalar la app sin PC ni ADB se debe generar `build:android:standalone` (`assembleRelease`), que integra el bundle JS en el APK. El workflow de CI construye y comprueba esta segunda variante. Si se sustituye una APK anterior firmada con otra clave temporal, Android puede requerir desinstalarla antes de instalar la nueva.
