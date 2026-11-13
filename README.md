# Reloj FB

Aplicación minimalista de alarmas y temporizadores para Android desarrollada en Kotlin y Jetpack Compose.

## Características principales

- **Alarmas**: programación exacta (`setAlarmClock`), repetición semanal por días (`L M X J V S D`), indicador de tiempo restante hasta el próximo aviso, duración de posponer configurable (`5`, `10` o `15 min`) y control mediante botones físicos de volumen (`subir` para posponer, `bajar` para detener).
- **Temporizadores**: accesos rápidos predefinidos, ajuste `+1 min` en curso o pausa, alerta en pantalla completa cuando el dispositivo está apagado o bloqueado y banner flotante propio cuando estás dentro de otra aplicación.
- **Audio y vibración**: tonos integrados en `res/raw/`, vibración reforzada de onda completa e importación de archivos de audio locales copiados de forma privada dentro de la app.
- **Widget de inicio**: widget interactivo con vista de lista y vista enfocada de cuenta atrás sincronizado mediante Room.
- **Personalización**: 6 temas de fondo (*Sistema*, *Beige*, *Carbón*, *Hielo*, *Medianoche* y *Bosque*), 12 colores de acento, selector de formato horario (`24 h` / `12 h`) y 10 iconos dinámicos de lanzador.

## Descarga e instalación

Puedes descargar el archivo `app-release.apk` compilado directamente desde la sección [**Releases**](../../releases) del repositorio e instalarlo en Android (`minSdk 26` o superior).

## Compilación desde el código fuente

```powershell
.\gradlew.bat :app:assembleRelease
```
