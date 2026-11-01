package ds.dsh.DeepSeek

object ThemeColorPolicyTest {
    private const val H = "horizontal"
    private const val V = "vertical"
    private const val DD = "diagonal_down"
    private const val DU = "diagonal_up"

    @JvmStatic
    fun main(args: Array<String>) {
        check(ThemeColorPolicy.parseColor("#123456", 7) == 0xFF123456.toInt())
        check(ThemeColorPolicy.parseColor(" 123456 ", 7) == 0xFF123456.toInt())
        check(ThemeColorPolicy.parseColor("#80123456", 7) == 0x80123456.toInt())
        check(ThemeColorPolicy.parseColor("80123456", 7) == 0x80123456.toInt())
        check(ThemeColorPolicy.parseColor(null, 7) == 7)
        check(ThemeColorPolicy.parseColor("#12345", 7) == 7)
        check(ThemeColorPolicy.parseColor("#GGGGGG", 7) == 7)
        check(ThemeColorPolicy.parseColor("1234567", 7) == 7)
        check(ThemeColorPolicy.parseColor("\u00a0123456\u00a0", 7) == 7)

        check(ThemeColorPolicy.validDirection(H, H, V, DD, DU) == H)
        check(ThemeColorPolicy.validDirection(V, H, V, DD, DU) == V)
        check(ThemeColorPolicy.validDirection(DD, H, V, DD, DU) == DD)
        check(ThemeColorPolicy.validDirection(DU, H, V, DD, DU) == DU)
        check(ThemeColorPolicy.validDirection(null, H, V, DD, DU) == H)
        check(ThemeColorPolicy.validDirection("VERTICAL", H, V, DD, DU) == H)
        check(ThemeColorPolicy.validDirection("", H, V, DD, DU) == H)
        println("Theme color policy tests passed")
    }
}
