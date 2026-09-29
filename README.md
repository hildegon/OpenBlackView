# OpenBlackView

App Android ligera para cámaras BlackVue con Wi‑Fi local. En el teléfono se llama **BlackVue Eventos**. Se conecta al Wi‑Fi de la cámara, baja los clips de evento y, si lo activas, los de parking, y puede mostrar el vídeo en vivo (frente y trasera). Sirve para varias familias que comparten la misma API HTTP local; la DR590XP es el modelo en el que se ha probado. No usa la app oficial ni BlackVue Cloud. No hay cuenta ni analíticas.

Los vídeos se quedan en el teléfono. Copiarlos a un disco de red queda fuera: sirve cualquier app que sincronice una carpeta.

## English

Lightweight Android app for BlackVue dashcams that expose a local Wi‑Fi HTTP API. It pulls event clips (and optional parking clips) and shows front/rear live view. Tried on a DR590XP; other families that use the same endpoints should work (see below). Join the camera Wi‑Fi, set the camera IP in settings, then sync or open live view. Files land in `blackvue/eventos` and `blackvue/parking`. Termux is not required. NAS upload is out of scope. MIT licensed.

## Cómo funciona

1. Conecta el teléfono al **Wi‑Fi de la cámara** y desactiva los datos móviles.
2. Abre ajustes y comprueba la **IP de la cámara**. El valor inicial es `10.99.77.1`, la dirección habitual del Wi‑Fi de la cámara en el firmware reciente. Algunos modelos antiguos usan `192.168.8.1`. No es una IP de la red de casa.
3. Pulsa **Sincronizar cámara** o **En vivo**.

Se bajan los tipos de [blackvuesync](https://github.com/acolomba/blackvuesync) `E,M,I,O,A,T,B`: evento, manual, impacto, exceso de velocidad, aceleración, curva y frenada. El parking (`P`) va aparte y viene activado. Los archivos que ya están se omiten. Cada día va en su carpeta.

```text
Almacenamiento interno/blackvue/eventos/AAAA-MM-DD/*.mp4
Almacenamiento interno/blackvue/parking/AAAA-MM-DD/*.mp4
```

Ejemplo: `blackvue/eventos/2026-09-29/2026-09-29_18-45-03_evento_frente.mp4`

El nombre de la cámara (`20260929_184503_EF.mp4`) se guarda con fecha, hora, tipo y cámara en español, sin tildes. Una `L` o `S` final se conserva para que no se pisen. Si el archivo ya estaba con el nombre original, no se vuelve a bajar.

La sincronización baja varios archivos a la vez (de 1 a 4, por defecto 3). Si la cámara no aguanta, sigue de uno en uno. Cada archivo se escribe como `.partial` y solo pasa a `.mp4` al terminar.

Si la cámara no responde: «No se alcanza la cámara. Conéctate a su Wi‑Fi e inténtalo.»

El firmware antiguo lista en `http://IP/blackvue_vod.cgi` y sirve desde `/Record/`. Si responde en `/accessible` y `/vodList`, la app usa esa API. **En vivo** usa `http://IP/blackvue_live.cgi` y la trasera añade `?direction=R`. Al salir de esa pantalla se cierra la conexión.

## Cámaras compatibles

Probado en una **DR590XP**. Debería funcionar en modelos BlackVue con Wi‑Fi local y la API HTTP clásica: vídeo en vivo en `blackvue_live.cgi` (trasera: `?direction=R`) y listado de grabaciones en `blackvue_vod.cgi` o, con firmware reciente (a partir de V1.009), en `/vodList` tras `GET /accessible`.

Familias que históricamente usan esos endpoints (app oficial, [blackvuesync](https://github.com/acolomba/blackvuesync) y otras herramientas de la comunidad). No es una lista oficial ni exhaustiva:

- DR590X, DR590X-2CH y DR590XP
- DR750X, DR900X, DR770X y DR970X, incluidas variantes Plus y LTE, con el teléfono en el Wi‑Fi de la propia cámara
- Series anteriores con el mismo listado: DR650S, DR750S y DR900S

La misma nota está en **Ajustes** de la app. La IP inicial `10.99.77.1` es la habitual en el firmware reciente. Algunos modelos antiguos usan `192.168.8.1`.

## Termux

No hace falta Termux, ni Python, ni scripts. Todo va dentro del APK. La app no tiene un botón para instalar dependencias externas porque no hay ninguna.

## Permisos

- Internet, para hablar con la cámara por HTTP en su Wi‑Fi.
- En Android 11 o posterior, acceso a todos los archivos para escribir en `Almacenamiento interno/blackvue`.
- En Android 8 y 9, el permiso de almacenamiento.
- En Android 10, el selector del sistema para elegir la carpeta `blackvue`.
- Notificación mientras dura una copia, para que no se corte al apagar la pantalla.

## Compilar e instalar

Requisitos en el ordenador: JDK 17, Android SDK 35, `minSdk` 26.

```sh
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Instálalo con `adb install -r` o cópialo al teléfono y ábrelo. Si Android bloquea orígenes desconocidos, permite la instalación desde el gestor de archivos.

Pruebas de la lógica de nombres, sin teléfono:

```sh
./gradlew testDebugUnitTest
```

## Limitaciones

- No baja grabaciones normales (`N`).
- **Borrar de la cámara** solo se ofrece para los archivos de esa sincronización que se descargaron enteros y coinciden en tamaño. Pide confirmación. En muchos modelos (por ejemplo la DR590XP) el borrado por Wi‑Fi no existe: la app lo intenta y, si el archivo sigue en el listado, hay que quitarlo con la tarjeta. En parte del firmware reciente de DR770X y DR970X el borrado sí puede responder.
- No sube archivos a un NAS ni abre BlackVue Cloud.

## Licencia

MIT. Ver [LICENSE](LICENSE).
