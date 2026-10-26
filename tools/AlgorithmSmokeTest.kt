import mx.lumenforge.photo.PhotoAlgorithms
import mx.lumenforge.photo.ProcessingParams

fun main() {
    val p = intArrayOf(
        0xFF102030.toInt(), 0xFF202F40.toInt(), 0xFF304050.toInt(),
        0xFF152535.toInt(), 0xFF223344.toInt(), 0xFF425364.toInt(),
        0xFF152535.toInt(), 0xFF556677.toInt(), 0xFFDFD0C0.toInt()
    )
    val original = p.copyOf()
    check(PhotoAlgorithms.process(p, 3, 3, ProcessingParams(0,0,50,50))
        .contentEquals(original))
    check(!PhotoAlgorithms.process(p, 3, 3, ProcessingParams(80,75,90,20))
        .contentEquals(original))
    check(p.contentEquals(original))
    println("OK: el procesamiento funciona y el original permanece intacto.")
}
