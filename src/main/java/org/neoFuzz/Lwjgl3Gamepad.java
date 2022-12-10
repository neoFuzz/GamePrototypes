package org.neoFuzz;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Lwjgl3Gamepad {
    private long renderWindow;
    private boolean running = true;
    private String text;
    private StringBuilder strBuilder;

    public static void main(String[] args) {
        new Lwjgl3Gamepad().run();
    }

    private void init() {
        if (!glfwInit()) {
            System.out.println("GL failed to initialise");
            System.exit(1);
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        long monitor = glfwGetPrimaryMonitor();
        int framebufferW;
        int framebufferH;
        try (MemoryStack s = stackPush()) {
            FloatBuffer px = s.mallocFloat(1);
            FloatBuffer py = s.mallocFloat(1);

            glfwGetMonitorContentScale(monitor, px, py);

            float contentScaleX = px.get(0);
            float contentScaleY = py.get(0);

            int ww = 640;
            int wh = 480;
            if (Platform.get() == Platform.MACOSX) {
                framebufferW = ww;
                framebufferH = wh;
            } else {
                framebufferW = round(ww * contentScaleX);
                framebufferH = round(wh * contentScaleY);
            }
        }

        renderWindow = glfwCreateWindow(framebufferW, framebufferH, "My GLFW Window", NULL, NULL);

        if (renderWindow == NULL) {
            System.out.println("No Render Window");
            System.exit(1);
        }

        // Center window
        GLFWVidMode vidMode = requireNonNull(glfwGetVideoMode(monitor));
        glfwSetWindowPos(renderWindow,
                (vidMode.width() - framebufferW) / 2,
                (vidMode.height() - framebufferH) / 2
        );

        glfwMakeContextCurrent(renderWindow);
        GL.createCapabilities();

        glfwSwapInterval(1);
    }

    private void loop() {
        float now, last = 0, delta;

        while (running && !glfwWindowShouldClose(renderWindow)) {
            now = (float) glfwGetTime();
            delta = now - last;
            last = now;

            glfwPollEvents();
            glClear(GL_COLOR_BUFFER_BIT);

            handleGamepad();

            glfwSwapBuffers(renderWindow);
        }
    }

    private void handleGamepad() {
        ByteBuffer gamepadButton = glfwGetJoystickButtons(GLFW_JOYSTICK_1);
        FloatBuffer gamepadAxes = glfwGetJoystickAxes(GLFW_JOYSTICK_1);
        String name = glfwGetGamepadName(GLFW_JOYSTICK_1);
        text = name + ": ";
        strBuilder = new StringBuilder();
        //System.out.println("Name: " + name);
        strBuilder.append(name).append(":\n");

        for (int i = 0; i < requireNonNull(gamepadButton).capacity(); ++i) {
            //System.out.print(gamepadButton.get(i));
            strBuilder.append(gamepadButton.get(i));
            if (i == 0 && gamepadButton.get(i) == 1) {
                running = false;
            }
        }
        //System.out.print("\n");
        strBuilder.append("\n");
        for (int i = 0; i < requireNonNull(gamepadAxes).capacity(); ++i) {
            //System.out.println(i + ": " +gamepadAxes.get(i));
            strBuilder.append(i).append(": ").append(gamepadAxes.get(i)).append("\n");
        }
        //System.out.println("\n");
        text = strBuilder.toString();
        System.out.println(text);
    }

    public void run() {
        try {
            init();
            loop();

            glfwDestroyWindow(renderWindow);
            text = "";
            strBuilder = null;
            glfwTerminate();
        } finally {
            glfwTerminate();
        }
    }
}





