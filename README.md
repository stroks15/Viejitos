# FerOs abues — launcher accesible Android

Repositorio: https://github.com/stroks15/Viejitos

Launcher vertical (HOME) para adultos mayores: letra grande, alto contraste y botones amplios. React Native 0.81.5 + módulos nativos Android (Kotlin).

## Pantallas

1. **Inicio:** reloj y fecha, botón de WhatsApp, últimas notificaciones, accesos grandes a aplicaciones reales (con icono) y enlaces rápidos.
2. **Contactos de emergencia:** 20 espacios persistentes, cuatro por página en cinco páginas verticales; selector nativo de Android, nombre/foto cuando está disponible y acción configurable para llamada telefónica, WhatsApp, SMS o mensaje WhatsApp. La agenda de respaldo permite elegir un contacto si el selector del fabricante no funciona.
3. **WhatsApp:** abrir WhatsApp y ver en vivo las notificaciones de `com.whatsapp` / `com.whatsapp.w4b` (requiere activar el acceso a notificaciones).
4. **Videos:** tres espacios **＋** para canales de YouTube (tres videos recientes por canal) mediante la función `youtube-feed` de Supabase.
5. **Configuración:** conexión de YouTube, enlaces rápidos, servicio de consulta, aplicaciones visibles, **aplicación asignada a cada botón**, tamaño de letra, voz y alto contraste.

## Módulos nativos (`android/app/src/main/java/com/viejitos/`)

- `ContactsModule` / `ContactsPackage`: lectura de agenda y selector nativo Android (`ACTION_PICK`) con devolución asíncrona del contacto seleccionado.
- `LauncherModule` / `LauncherPackage`: apps instaladas (+ iconos Base64), abrir apps y categorías, llamada directa (`ACTION_CALL` con fallback `ACTION_DIAL`), linterna (`CameraManager.setTorchMode`), acceso a notificaciones, márgenes seguros.
- `WhatsAppNotificationService`: `NotificationListenerService` que guarda las últimas 30 notificaciones y las envía a JS (`WhatsAppNotification`).
- Ambos paquetes están registrados en `MainApplication.kt`.

## Permisos y primer uso en el teléfono

1. Aceptar que FerOs abues sea la app de inicio.
2. Contactos y llamadas: aceptar los permisos cuando Android los pida.
3. WhatsApp: pantalla WHATSAPP → *Activar acceso a notificaciones* → activar FerOs abues.
4. Si el interruptor está bloqueado ("Ajuste restringido", típico en APK instaladas a mano): *Información de la app* → ⋮ → *Permitir ajustes restringidos*, y repetir el paso 3.
5. Ajustes → *Aplicaciones de los botones* para elegir la app de RADIO u otras.

La linterna y la apertura de apps no piden permisos en tiempo de ejecución.

## YouTube y Supabase

- Edge Function `youtube-feed` (proyecto `yxjyqpnvgtkdaaukackt`); `YOUTUBE_API_KEY` vive solo en Supabase.
- La app usa la clave pública `anon`. No colocar `service_role` ni claves de YouTube en el cliente.

## Compilación automática (GitHub Actions)

`.github/workflows/build-apk.yml` genera una APK release independiente (bundle JS embebido) con Node 20.19.4, JDK 17, Gradle 8.14.3 y Android SDK 36, y la publica como artefacto `feros-abues-standalone-apk` (30 días).

### Firma estable (recomendado)

`android/app/build.gradle` firma con una clave propia si existen las variables de entorno `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`; si no, usa la clave debug del runner. Con la clave debug, cada compilación queda firmada distinto y Android exige desinstalar la versión anterior (se pierden permisos y ajustes).

Para poder **actualizar encima**, genera una clave y guárdala como secretos de Actions (`ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), y añade al workflow un paso que decodifique el keystore a `$RUNNER_TEMP/release.keystore`, exporte `KEYSTORE_FILE` y pase las tres contraseñas/alias como `env` al paso `assembleRelease`:

```bash
keytool -genkeypair -v -keystore feros.keystore -alias feros -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 feros.keystore
```

No subas `feros.keystore` al repositorio. Esta clave no es de publicación para Play Store.

## Límites y pruebas en dispositivo

- La llamada de WhatsApp abre el chat; por restricciones de WhatsApp, el usuario debe tocar el botón de llamada dentro de la aplicación.
- Los accesos directos y las preferencias se guardan localmente con AsyncStorage; los contactos de emergencia no se envían a un servidor.
- El selector de contactos depende de una app de agenda compatible con `ACTION_PICK`; si no responde, puede usarse la lista de respaldo (requiere permiso de contactos).
- La lectura de notificaciones requiere activar manualmente el acceso especial de Android. Android puede ocultar contenido sensible según la configuración de cada app.
- La APK de CI usa firma debug si no se configuran los secretos de firma; para actualizar sobre una instalación previa se necesita conservar la misma clave privada.

Funciones aún no implementadas: radio FM propia y `ROLE_DIALER` como aplicación de teléfono predeterminada.
