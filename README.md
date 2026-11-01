# Viejitos — launcher accesible Android

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

Este repositorio todavía no contiene `android/`, Gradle Wrapper ni `AndroidManifest.xml`; por eso no es aún un proyecto Android compilable ni se pudo producir un APK aquí. También falta implementar y probar en dispositivo las integraciones nativas de launcher HOME, contactos reales, notificaciones mediante Notification Listener, llamadas, linterna, radio FM y apertura de aplicaciones instaladas. Los accesos de esas funciones que aún no tienen módulo nativo se muestran como pendientes y no deben anunciarse como funcionales.

## Seguridad y separación

La función `youtube-feed` es independiente de `gestionar-usuarios`; el despliegue de YouTube no modifica tablas ni reemplaza la función anterior. No colocar `service_role` ni la clave de YouTube en el cliente Android.
