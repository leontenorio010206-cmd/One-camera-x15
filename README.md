# LumenForge v0.1.0

Cámara computacional Android para experimentar con el OnePlus 15.

## Incluye
- Captura con CameraX, cámaras frontal y trasera.
- Importación desde galería sin permisos generales de almacenamiento.
- Ajustes locales de **reducción de ruido**, **nitidez**, **saturación** y **calidez**.
- Comparación del original y la versión editada.
- Copia de fotografía original en DCIM/LumenForge y exportación JPG a Pictures/LumenForge.
- Fotos originales preservadas, sin subida a servidores.

**Limitaciones:** el procesamiento trabaja sobre imágenes reducidas a un máximo de 3072 píxeles de lado mayor; no es RAW/DNG, IA ni el HDR propietario del fabricante. El acceso a telefoto de 3.5× y resolución de 50 MP depende de lo que el sistema exponga a CameraX.

## Compilación

1. Ve a [GitHub Actions](../../actions/workflows/android-debug.yml).
2. Selecciona *APK de prueba Android* → *Run workflow* (o revisa la ejecución iniciada por el push).
3. Al terminar correctamente, descarga el artefacto **LumenForge-v0.1.0-debug**.
4. Extrae el ZIP del artefacto e instala `app-debug.apk` en Android.

El APK debug está firmado con clave de desarrollo: úsalo solo para pruebas. También puedes abrir el repositorio en Android Studio con Android SDK API 36 y JDK 17. Las dependencias se descargan desde repositorios oficiales de Gradle y Google.

## Prueba algorítmica local

```bash
kotlinc app/src/main/java/mx/lumenforge/photo/PhotoAlgorithms.kt tools/AlgorithmSmokeTest.kt -include-runtime -d test.jar
java -jar test.jar
```

## Roadmap
Próximos objetivos: manejo de resolución completa, Camera2 manual (ISO/obturación/enfoque donde esté disponible), histograma, revelado RAW y aceleración GPU. No se ha confirmado funcionamiento físico en el OnePlus 15.
