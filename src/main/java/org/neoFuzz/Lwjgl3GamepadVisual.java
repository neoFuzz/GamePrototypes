package org.neoFuzz;

/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.system.Callback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static lwjglDemos.GLFWUtil.glfwInvoke;
import static lwjglDemos.IOUtil.ioResourceToByteBuffer;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Visual Gamepad Demo
 *
 * <p>Based on the TrueType Oversample LWJGL Demo
 * </p>
 */
public final class Lwjgl3GamepadVisual {
    private static final int BITMAP_W = 512;
    private static final int BITMAP_H = 512;
    private static final float[] scale = {
            24.0f,
            14.0f
    };

    private static final int[] sf = {
            0, 1, 2,
            0, 1, 2
    };

    private final STBTTAlignedQuad q = STBTTAlignedQuad.malloc();
    private final FloatBuffer xb = memAllocFloat(1);
    private final FloatBuffer yb = memAllocFloat(1);
    private long window;
    private Callback debugProc;
    private int ww = 1024;
    private int wh = 768;
    private int fbw = ww;
    private int fbh = wh;
    private int font_tex;
    private STBTTPackedchar.Buffer charData;
    private int font = 3;
    private boolean black_on_white;
    private boolean integer_align;
    private boolean translating;
    private boolean rotating;
    private boolean supportsSRGB;
    private boolean srgb;
    private String text = "";
    private float rotate_t, translate_t;
    private boolean show_tex;

    private Lwjgl3GamepadVisual() {
    }

    public static void main(String[] args) {
        new Lwjgl3GamepadVisual().run("Game Pad Demo");
    }

    private static void drawBoxTC(float x0, float y0, float x1, float y1, float s0, float t0, float s1, float t1) {
        glTexCoord2f(s0, t0);
        glVertex2f(x0, y0);
        glTexCoord2f(s1, t0);
        glVertex2f(x1, y0);
        glTexCoord2f(s1, t1);
        glVertex2f(x1, y1);
        glTexCoord2f(s0, t1);
        glVertex2f(x0, y1);
    }

    private void load_fonts() {
        font_tex = glGenTextures();
        charData = STBTTPackedchar.malloc(6 * 128);

        try (STBTTPackContext pc = STBTTPackContext.malloc()) {
            ByteBuffer ttf = ioResourceToByteBuffer("c:/windows/fonts/OCRAEXT.ttf", 512 * 1024);

            ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_W * BITMAP_H);

            stbtt_PackBegin(pc, bitmap, BITMAP_W, BITMAP_H, 0, 1, NULL);
            for (int i = 0; i < 2; i++) {
                int p = (i * 3) * 128 + 32;
                charData.limit(p + 95);
                charData.position(p);
                stbtt_PackSetOversampling(pc, 1, 1);
                stbtt_PackFontRange(pc, ttf, 0, scale[i], 32, charData);

                p = (i * 3 + 1) * 128 + 32;
                charData.limit(p + 95);
                charData.position(p);
                stbtt_PackSetOversampling(pc, 2, 2);
                stbtt_PackFontRange(pc, ttf, 0, scale[i], 32, charData);

                p = (i * 3 + 2) * 128 + 32;
                charData.limit(p + 95);
                charData.position(p);
                stbtt_PackSetOversampling(pc, 3, 1);
                stbtt_PackFontRange(pc, ttf, 0, scale[i], 32, charData);
            }
            charData.clear();
            stbtt_PackEnd(pc);

            glBindTexture(GL_TEXTURE_2D, font_tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_W, BITMAP_H, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void draw_init() {
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_DEPTH_TEST);

        glViewport(0, 0, fbw, fbh);
        if (black_on_white) {
            glClearColor(1.0f, 1.0f, 1.0f, 0.0f);
        } else {
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        }
        glClear(GL_COLOR_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0.0, ww, wh, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void print(float x, float y, int font, String text) {
        xb.put(0, x);
        yb.put(0, y);

        charData.position(font * 128);

        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, font_tex);

        glBegin(GL_QUADS);
        for (int i = 0; i < text.length(); i++) {
            stbtt_GetPackedQuad(charData, BITMAP_W, BITMAP_H, text.charAt(i), xb, yb, q, font == 0 && integer_align);
            drawBoxTC(
                    q.x0(), q.y0(), q.x1(), q.y1(),
                    q.s0(), q.t0(), q.s1(), q.t1()
            );
        }
        glEnd();
    }

    private void draw_world() {
        int sfont = sf[font];

        float x = 20;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (black_on_white) {
            glColor3f(0.0f, 0.0f, 0.0f);
        } else {
            glColor3f(1.0f, 1.0f, 1.0f);
        }

        List<String> al = new ArrayList<>(Arrays.asList(text.split("\n")));
        int line = 20;
        for (String s : al) {
            print(80, line, sfont, s);
            line += 25;
        }

        /*print(100, 60, sfont, "S: toggle font size");
        print(100, 85, sfont, "O: toggle oversampling");
        print(100, 110, sfont, "T: toggle translation");
        print(100, 135, sfont, "R: toggle rotation");
        print(100, 160, sfont, "P: toggle pixel-snap (only non-oversampled)");
        if (supportsSRGB) {
            print(100, 185, sfont, "G: toggle srgb gamma-correction");
        }
        if (black_on_white) {
            print(100, 210, sfont, "B: toggle to white-on-black");
        } else {
            print(100, 210, sfont, "B: toggle to black-on-white");
        }*/
        print(100, 235, sfont, "V: view font texture");

        print(80, 300, sfont, "Current font:");

        if (!show_tex) {
            if (font < 3) {
                print(100, 350, sfont, "Font height: 24 pixels");
            } else {
                print(100, 350, sfont, "Font height: 14 pixels");
            }
        }

        if (font % 3 == 1) {
            print(100, 325, sfont, "2x2 oversampled text at 1:1");
        } else if (font % 3 == 2) {
            print(100, 325, sfont, "3x1 oversampled text at 1:1");
        } else if (integer_align) {
            print(100, 325, sfont, "1:1 text, one texel = one pixel, snapped to integer coordinates");
        } else {
            print(100, 325, sfont, "1:1 text, one texel = one pixel");
        }

        if (show_tex) {
            glBegin(GL_QUADS);
            drawBoxTC(200, 400, 200 + BITMAP_W, 300 + BITMAP_H, 0, 0, 1, 1);
            glEnd();
        } else {
            glMatrixMode(GL_MODELVIEW);
            glTranslatef(200, 350, 0);

            if (translating) {
                x += translate_t * 8 % 30;
            }

            if (rotating) {
                glTranslatef(100, 150, 0);
                glRotatef(rotate_t * 2, 0, 0, 1);
                glTranslatef(-100, -150, 0);
            }
            print(x, 100, font, "This is a test");
            print(x, 130, font, "Now is the time for all good men to come to the aid of their country.");
            print(x, 160, font, "The quick brown fox jumps over the lazy dog.");
            print(x, 190, font, "0123456789");
        }
    }

    private void draw() {
        draw_init();
        draw_world();
        glfwSwapBuffers(window);
    }

    private void loopmode(float dt) {
        if (dt > 0.25f) {
            dt = 0.25f;
        }
        if (dt < 0.01f) {
            dt = 0.01f;
        }

        rotate_t += dt;
        translate_t += dt;

        draw();
    }

    private void windowSizeChanged(long window, int width, int height) {
        this.ww = width;
        this.wh = height;
    }

    private void framebufferSizeChanged(long window, int width, int height) {
        this.fbw = width;
        this.fbh = height;
    }

    private void createWindow(String title) {
        GLFWErrorCallback.createPrint().set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        this.window = glfwCreateWindow(ww, wh, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetWindowSizeCallback(window, this::windowSizeChanged);
        glfwSetFramebufferSizeCallback(window, this::framebufferSizeChanged);

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) {
                return;
            }

            switch (key) {
                case GLFW_KEY_ESCAPE:
                    glfwSetWindowShouldClose(window, true);
                    break;
                case GLFW_KEY_O:
                    font = (font + 1) % 3 + (font / 3) * 3;
                    break;
                case GLFW_KEY_S:
                    font = (font + 3) % 6;
                    break;
                case GLFW_KEY_T:
                    translating = !translating;
                    translate_t = 0.0f;
                    break;
                case GLFW_KEY_R:
                    rotating = !rotating;
                    rotate_t = 0.0f;
                    break;
                case GLFW_KEY_P:
                    integer_align = !integer_align;
                    break;
                case GLFW_KEY_G:
                    if (!supportsSRGB) {
                        break;
                    }

                    srgb = !srgb;
                    if (srgb) {
                        glEnable(GL30.GL_FRAMEBUFFER_SRGB);
                    } else {
                        glDisable(GL30.GL_FRAMEBUFFER_SRGB);
                    }
                    break;
                case GLFW_KEY_V:
                    show_tex = !show_tex;
                    break;
                case GLFW_KEY_B:
                    black_on_white = !black_on_white;
                    break;
            }
        });

        // Center window
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());

        glfwSetWindowPos(window,
                (vidMode.width() - ww) / 2,
                (vidMode.height() - wh) / 2
        );

        // Create context
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        debugProc = GLUtil.setupDebugMessageCallback();

        glfwSwapInterval(1);
        glfwShowWindow(window);

        glfwInvoke(window, this::windowSizeChanged, this::framebufferSizeChanged);

        // Detect sRGB support
        GLCapabilities caps = GL.getCapabilities();
        supportsSRGB = caps.OpenGL30 || caps.GL_ARB_framebuffer_sRGB || caps.GL_EXT_framebuffer_sRGB;
    }

    private void run(String title) {
        try {
            createWindow(title);
            load_fonts();

            long time = System.nanoTime();
            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents();


                long t = System.nanoTime();
                float dt = (float) ((t - time) / 1000000000.0);
                time = t;

                loopmode(dt);
                handleGamepad();
            }
        } finally {
            try {
                destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void destroy() {
        GL.setCapabilities(null);
        charData.free();

        if (debugProc != null) {
            debugProc.free();
        }

        glfwFreeCallbacks(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();

        memFree(yb);
        memFree(xb);
        q.free();
    }

    private void handleGamepad() {
        ByteBuffer gamepadButton = glfwGetJoystickButtons(GLFW_JOYSTICK_1);
        FloatBuffer gamepadAxes = glfwGetJoystickAxes(GLFW_JOYSTICK_1);
        String name = glfwGetGamepadName(GLFW_JOYSTICK_1);
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(name).append(":\n");

        for (int i = 0; i < Objects.requireNonNull(gamepadButton).capacity(); ++i) {
            if (gamepadButton.get(i) == 1) {
                switch (i) {
                    case GLFW_GAMEPAD_BUTTON_A:
                        strBuilder.append("A");
                        break;
                    case GLFW_GAMEPAD_BUTTON_B:
                        strBuilder.append("B");
                        break;
                    case GLFW_GAMEPAD_BUTTON_X:
                        strBuilder.append("X");
                        break;
                    case GLFW_GAMEPAD_BUTTON_Y:
                        strBuilder.append("Y");
                        break;
                    case GLFW_GAMEPAD_BUTTON_LEFT_BUMPER:
                        strBuilder.append("LB");
                        break;
                    case GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER:
                        strBuilder.append("RB");
                        break;
                    case GLFW_GAMEPAD_BUTTON_BACK:
                        strBuilder.append("Back");
                        break;
                    case GLFW_GAMEPAD_BUTTON_START:
                        strBuilder.append("Start");
                        break;
                    case 8:
                        strBuilder.append("L-Stick");
                        break;
                    case 9:
                        strBuilder.append("R-Stick");
                        break;
                    case 10:
                        strBuilder.append("D Up");
                        break;
                    case 11:
                        strBuilder.append("D Right");
                        break;
                    case 12:
                        strBuilder.append("D Down");
                        break;
                    case 13:
                        strBuilder.append("D Left");
                        break;
                    default:
                        strBuilder.append(i).append(":").append(gamepadButton.get(i));
                }
                strBuilder.append(" ");
            }
            if (gamepadButton.get(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) == GLFW_TRUE &&
                    gamepadButton.get(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW_TRUE) {
                // exit by holding down LB + RB buttons
                glfwSetWindowShouldClose(window, true);
            }
        }
        strBuilder.append("\n");
        for (int i = 0; i < gamepadAxes.capacity(); ++i) {
            strBuilder.append(i).append(": ");
            if (gamepadAxes.get(i) > 0) {
                strBuilder.append("+").append(gamepadAxes.get(i)).append("\n");
            } else {
                strBuilder.append(gamepadAxes.get(i)).append("\n");
            }
        }
        text = strBuilder.toString();

    }
}
