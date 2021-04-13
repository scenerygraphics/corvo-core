package graphics.scenery.xtradimensionvr

import uno.glfw.GlfwWindow
import graphics.scenery.Hub
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.SceneryWindow

class Imgui {

    val start = System.currentTimeMillis()
    val hub by lazy { Hub() }
    val window by lazy { GlfwWindow.from((hub.get<Renderer>()!!.window as SceneryWindow.GLFWWindow).window) }

    //    val window: GlfwWindow
    //    val ctx: Context
    fun trai() {

        if(System.currentTimeMillis() - start > 5_000)
            println(window.handle.value)
    }
}