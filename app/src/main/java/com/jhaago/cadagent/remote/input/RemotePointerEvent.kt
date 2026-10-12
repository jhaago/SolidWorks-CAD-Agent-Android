package com.jhaago.cadagent.remote.input

enum class PointerAction { Click, Down, Move, Up, Scroll }
enum class PointerButton { Primary, Secondary }
sealed interface RemoteInputEvent
data class RemotePointerEvent(
    val action: PointerAction,
    val x: Float,
    val y: Float,
    val button: PointerButton = PointerButton.Primary,
    val scrollY: Float = 0f,
) : RemoteInputEvent {
    val valid: Boolean get() = x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f && scrollY.isFinite()
}
