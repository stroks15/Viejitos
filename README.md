# Viejitos — Launcher accesible Android

Repositorio: https://github.com/stroks15/Viejitos

## Pantallas
1. Inicio: reloj, conexiones, WhatsApp, notificaciones y accesos grandes.
2. Contactos.
3. WhatsApp.
4. Videos: exactamente tres espacios **＋**. Al pulsar uno, la persona pega el enlace de un canal. Ese espacio muestra sus **tres videos más recientes**, el nuevo primero. Los canales se pueden editar o eliminar. No se precarga el canal usado como ejemplo.
5. Configuración: accesibilidad y conexión al servicio seguro de YouTube.

## Activar los videos
- Crear/desplegar la Edge Function `supabase/functions/youtube-feed`.
- En Google Cloud habilitar YouTube Data API v3 y crear una API key restringida a esa API.
- Guardar la clave como secreto Supabase `YOUTUBE_API_KEY`; nunca incluirla en la app.
- En pantalla 5 configurar la URL `https://<PROJECT_REF>.supabase.co/functions/v1/youtube-feed` y la clave pública anon/publishable de Supabase. No usar `service_role` en el móvil.
- La app solicita `limit: 3`; la función limita la respuesta a tres publicaciones públicas y las ordena por fecha descendente.
- Al tocar una miniatura se abre el enlace del video mediante Android; si YouTube está instalado, Android normalmente lo entrega a esa aplicación.

## Estado
Código fuente inicial en React Native. Aún no es un APK compilado. Los módulos nativos de launcher HOME, notificaciones reales, llamadas, linterna, radio FM, conectividad y selector de apps requieren implementación y pruebas en dispositivo.

## Seguridad
La clave API de YouTube se guarda únicamente en Supabase. La clave anon es pública, pero no sustituye autenticación ni políticas RLS. No guardar contenido privado de notificaciones en Supabase por defecto.
