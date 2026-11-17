# FerOs abues — launcher accesible Android

Repositorio: https://github.com/stroks15/Viejitos

Launcher vertical (HOME) para adultos mayores: letra grande, alto contraste y botones amplios. React Native 0.81.5 + módulos nativos Android (Kotlin).

## Pantallas

1. **Inicio:** reloj y fecha, botón de WhatsApp, últimas notificaciones, accesos grandes a aplicaciones reales (con icono) y enlaces rápidos.
2. **Contactos:** agenda real del teléfono (nombre, foto y número) con búsqueda y llamada directa con un toque (si no hay permiso de llamada, abre el marcador).
3. **WhatsApp:** abrir WhatsApp y ver en vivo las notificaciones de `com.whatsapp` / `com.whatsapp.w4b` (requiere activar el acceso a notificaciones).
4. **Videos:** tres espacios **＋** para canales de YouTube (tres videos recientes por canal) mediante la función `youtube-feed` de Supabase.
5. **Configuración:** conexión de YouTube, enlaces rápidos, servicio de consulta, aplicaciones visibles, **aplicación asignada a cada botón**, tamaño de letra, voz y alto contraste.

## Módulos nativos (`android/app/src/main/java/com/viejitos/`)

- `ContactsModule` / `ContactsPackage`: lectura de agenda.
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

## Pendiente

Radio FM propia, `ROLE_DIALER` como app de teléfono y la agenda de 24 posiciones fijas no están implementadas.
