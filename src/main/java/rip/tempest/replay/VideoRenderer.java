package rip.tempest.replay;

import rip.tempest.Tempest;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.interfaces.menu.MenuTheme;
import rip.tempest.modules.impl.client.ReplayModule;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Renders a camera path to video. The replay is stepped by hand, frame by frame: for each frame
 * the path gives a replay time and a camera; the world gets exactly the ticks (and the packets
 * between them) that bring it to that time, then the frame is drawn off-screen at the chosen
 * size with no HUD, read back, and handed to ffmpeg (an MP4) or written out as a PNG. So a video
 * is smooth however long each frame takes to draw, and slow motion and frozen time come out
 * right. Chunks are given a moment to finish building before each frame is kept.
 *
 * <p>The window shows the frame just drawn and the progress now and then; Escape stops.
 *
 * <p>Based on ReplayMod's VideoRenderer and its ffmpeg pipe (GPL-3.0-or-later), much reduced.
 */
final class VideoRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final long CHUNK_WAIT_NANOS = 400_000_000L, WARM_UP_NANOS = 6_000_000_000L;

    private final ReplayViewer viewer;
    private final CameraPath path;
    private final ReplayModule.Format format;
    private final int fps, crf;
    private int width, height;
    private volatile boolean cancelled;
    private long lastShown;

    VideoRenderer(ReplayViewer viewer, CameraPath path) {
        this.viewer = viewer;
        this.path = path;
        this.format = ReplayModule.renderFormat.getValue();
        this.fps = ReplayModule.renderFps.getValue().fps;
        this.crf = ReplayModule.renderQuality.getValue().crf;
    }

    static File folder() {
        return new File(Tempest.NAME, "renders");
    }

    static File findFfmpeg() {
        boolean windows = Util.getOSType() == Util.EnumOS.WINDOWS;
        String exe = windows ? "ffmpeg.exe" : "ffmpeg";
        List<File> candidates = new ArrayList<>();
        File own = new File(Tempest.NAME, "ffmpeg");
        candidates.add(new File(own, exe));
        candidates.add(new File(own, "bin/" + exe));
        candidates.add(new File(Tempest.NAME, exe));
        File[] builds = own.listFiles(File::isDirectory);
        if (builds != null) for (File build : builds) candidates.add(new File(build, "bin/" + exe));
        String local = System.getenv("LOCALAPPDATA");
        if (windows && local != null) candidates.add(new File(local, "Microsoft/WinGet/Links/" + exe));
        String path = System.getenv("PATH");
        if (path != null) for (String dir : path.split(File.pathSeparator)) if (!dir.isEmpty()) candidates.add(new File(dir, exe));
        for (File file : candidates) if (file.isFile()) return file;
        return null;
    }

    File run() throws IOException {
        int windowWidth = mc.displayWidth, windowHeight = mc.displayHeight;
        ReplayModule.Resolution size = ReplayModule.renderSize.getValue();
        width = even(size == ReplayModule.Resolution.WINDOW ? windowWidth : size.width);
        height = even(size == ReplayModule.Resolution.WINDOW ? windowHeight : size.height);
        int frames = Math.max(1, (int) Math.round(path.duration() * fps));

        File folder = folder();
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("can't make " + folder);
        String name = viewer.title + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        Sink sink;
        File output;
        if (format == ReplayModule.Format.MP4) {
            File ffmpeg = findFfmpeg();
            if (ffmpeg == null) throw new IOException("FFmpeg not found");
            output = new File(folder, name + ".mp4");
            sink = new FfmpegSink(ffmpeg, output, new File(folder, name + ".log"), width, height, fps, crf);
        } else {
            output = new File(folder, name);
            sink = new PngSink(output, width, height);
        }

        boolean hideGui = mc.gameSettings.hideGUI, pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        float volume = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
        boolean finished = false;
        try {
            mc.gameSettings.hideGUI = true;
            mc.gameSettings.pauseOnLostFocus = false;
            mc.gameSettings.setSoundLevel(SoundCategory.MASTER, 0f);
            viewer.stopSpectating();

            CameraPath.Sample start = path.sample(0);
            viewer.seek((int) start.time());
            if (ReplayViewer.current() != viewer || mc.theWorld == null) return null;
            mc.displayGuiScreen(new Screen());
            mc.setRenderSize(width, height);

            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 3);
            double base = start.time();
            int ticks = 0;
            long began = System.nanoTime();
            warmUp(start, windowWidth, windowHeight, frames);

            for (int frame = 0; frame < frames && !cancelled; frame++) {
                CameraPath.Sample sample = path.sample(frame / (double) fps);

                double target = (sample.time() - base) / 50.0;
                while (ticks + 1 <= target + 1e-6 && !cancelled) {
                    ticks++;
                    viewer.sendUntil((int) Math.floor(base + ticks * 50.0));
                    mc.runTick();
                }
                viewer.sendUntil((int) Math.floor(sample.time()));
                viewer.renderedAt(sample.time());
                float partial = (float) Math.max(0, Math.min(1, target - ticks));

                draw(sample, partial, CHUNK_WAIT_NANOS);
                byte[] buffer = sink.buffer();
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
                pixels.clear();
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);
                pixels.get(buffer);
                mc.getFramebuffer().unbindFramebuffer();
                GlStateManager.popMatrix();
                sink.write(buffer);

                long now = System.nanoTime();
                if (now - lastShown > 100_000_000L || frame == frames - 1) {
                    lastShown = now;
                    double perFrame = (now - began) / 1e9 / (frame + 1);
                    String detail = "Frame " + (frame + 1) + " of " + frames + "  ·  " + ReplayViewer.clock((int) ((frames - frame - 1) * perFrame * 1000)) + " left";
                    show(windowWidth, windowHeight, "Rendering", detail, (frame + 1) / (float) frames, true);
                }
            }

            if (cancelled) return null;
            show(windowWidth, windowHeight, format == ReplayModule.Format.MP4 ? "Finishing the video" : "Writing the last frames", "", 1f, false);
            sink.finish();
            finished = true;
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (!finished) sink.cancel();
            mc.setRenderSize(windowWidth, windowHeight);
            mc.gameSettings.hideGUI = hideGui;
            mc.gameSettings.pauseOnLostFocus = pauseOnLostFocus;
            mc.gameSettings.setSoundLevel(SoundCategory.MASTER, volume);
            if (mc.currentScreen instanceof Screen) mc.displayGuiScreen(null);
        }
    }

    private void warmUp(CameraPath.Sample start, int windowWidth, int windowHeight, int frames) {
        long began = System.nanoTime();
        while (!cancelled && System.nanoTime() - began < WARM_UP_NANOS) {
            draw(start, 0f, 0L);
            mc.getFramebuffer().unbindFramebuffer();
            GlStateManager.popMatrix();
            if (mc.renderGlobal.hasNoChunkUpdates()) break;
            mc.renderGlobal.updateChunks(System.nanoTime());
            show(windowWidth, windowHeight, "Loading the world", "Rendering " + frames + " frames at " + width + "x" + height, 0f, true);
            sleep();
        }
    }

    private void draw(CameraPath.Sample sample, float partial, long chunkWait) {
        CameraEntity camera = ReplayViewer.camera();
        if (camera != null) {
            camera.moveTo(sample.x(), sample.y(), sample.z());
            camera.turnTo(sample.yaw(), sample.pitch());
            if (mc.getRenderViewEntity() != camera) mc.setRenderViewEntity(camera);
        }
        mc.timer.renderPartialTicks = partial;
        drawOnce(partial);
        if (chunkWait <= 0 || mc.renderGlobal.hasNoChunkUpdates()) return;

        long began = System.nanoTime();
        boolean changed = false;
        while (!mc.renderGlobal.hasNoChunkUpdates() && System.nanoTime() - began < chunkWait) {
            mc.renderGlobal.updateChunks(System.nanoTime());
            changed = true;
            sleep();
        }
        if (changed) {
            mc.getFramebuffer().unbindFramebuffer();
            GlStateManager.popMatrix();
            drawOnce(partial);
        }
    }

    private void drawOnce(float partial) {
        GuiScreen screen = mc.currentScreen;
        mc.currentScreen = null;
        try {
            Framebuffer framebuffer = mc.getFramebuffer();
            GlStateManager.pushMatrix();
            GlStateManager.clear(16640);
            framebuffer.bindFramebuffer(true);
            GlStateManager.enableTexture2D();
            mc.entityRenderer.updateCameraAndRender(partial, System.nanoTime());
            framebuffer.bindFramebuffer(true);
        } finally {
            mc.currentScreen = screen;
        }
    }

    private void show(int windowWidth, int windowHeight, String title, String detail, float progress, boolean stoppable) {
        int renderWidth = mc.displayWidth, renderHeight = mc.displayHeight;
        try {
            mc.displayWidth = windowWidth;
            mc.displayHeight = windowHeight;
            GlStateManager.pushMatrix();
            mc.getFramebuffer().framebufferRender(windowWidth, windowHeight);
            GlStateManager.popMatrix();
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableLighting();
            ScaledResolution resolution = new ScaledResolution(mc);
            MenuTheme.beginFrame();
            panel(resolution.getScaledWidth(), resolution.getScaledHeight(), title, detail, progress, stoppable);
            Display.update();
        } finally {
            mc.displayWidth = renderWidth;
            mc.displayHeight = renderHeight;
        }
        if (mc.currentScreen != null) {
            try {
                mc.currentScreen.handleInput();
            } catch (IOException ignored) {
            }
        }
        if (Display.isCloseRequested()) cancelled = true;
    }

    private static void panel(int width, int height, String title, String detail, float progress, boolean stoppable) {
        CustomFontRenderer bold = MenuTheme.bold(), small = MenuTheme.small();
        float w = 260f, h = 46f, x = (width - w) / 2f, y = height - h - 12f;
        RenderUtils.drawRoundedRect(x, y, w, h, 8f, new Color(16, 16, 23, 230));
        RenderUtils.drawRoundOutline(x, y, w, h, 8f, 1f, MenuTheme.CLEAR, MenuTheme.white(0.08f));
        bold.drawString(title, x + 10f, y + 9f, MenuTheme.text(MenuTheme.TEXT, 1f));
        if (stoppable) {
            String hint = "Esc to stop";
            small.drawString(hint, x + w - 10f - small.getStringWidth(hint), y + 10f, MenuTheme.text(MenuTheme.TEXT_MUTED, 1f));
        }
        if (!detail.isEmpty()) small.drawString(detail, x + 10f, y + 22f, MenuTheme.text(MenuTheme.TEXT_SECONDARY, 1f));
        float barY = y + h - 9f, left = x + 10f, right = x + w - 10f;
        Glyphs.stroke(left, barY, right, barY, 3f, MenuTheme.white(0.1f));
        float end = left + (right - left) * MenuTheme.clamp(progress);
        if (end > left + 0.5f) Glyphs.stroke(left, barY, end, barY, 3f, MenuTheme.accent());
    }

    private static int even(int value) {
        return Math.max(2, value & ~1);
    }

    private static void sleep() {
        try {
            Thread.sleep(2L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Screen extends GuiScreen {
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == Keyboard.KEY_ESCAPE) cancelled = true;
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    private interface Sink {
        byte[] buffer() throws InterruptedException;

        void write(byte[] frame) throws IOException, InterruptedException;

        void finish() throws IOException, InterruptedException;

        void cancel();
    }

    private static final class FfmpegSink implements Sink {
        private static final byte[] END = new byte[0];

        private final Process process;
        private final OutputStream in;
        private final File output;
        private final int frameSize;
        private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(3), spare = new ArrayBlockingQueue<>(3);
        private final Thread writer;
        private volatile IOException failure;

        FfmpegSink(File ffmpeg, File output, File log, int width, int height, int fps, int crf) throws IOException {
            this.output = output;
            this.frameSize = width * height * 3;
            List<String> command = List.of(ffmpeg.getAbsolutePath(), "-hide_banner", "-loglevel", "warning", "-y",
                    "-f", "rawvideo", "-pix_fmt", "rgb24", "-s:v", width + "x" + height, "-framerate", String.valueOf(fps), "-i", "-",
                    "-vf", "vflip", "-c:v", "libx264", "-preset", "medium", "-crf", String.valueOf(crf),
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", output.getAbsolutePath());
            process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(log).start();
            in = process.getOutputStream();
            writer = new Thread(() -> {
                try {
                    while (true) {
                        byte[] frame = queue.take();
                        if (frame == END) break;
                        in.write(frame);
                        spare.offer(frame);
                    }
                    in.close();
                } catch (IOException e) {
                    failure = e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Replay video writer");
            writer.setDaemon(true);
            writer.start();
        }

        @Override
        public byte[] buffer() {
            byte[] frame = spare.poll();
            return frame != null ? frame : new byte[frameSize];
        }

        @Override
        public void write(byte[] frame) throws IOException, InterruptedException {
            if (failure != null) throw new IOException("FFmpeg stopped taking frames (see the .log next to the video)", failure);
            queue.put(frame);
        }

        @Override
        public void finish() throws IOException, InterruptedException {
            queue.put(END);
            writer.join();
            if (failure != null) throw new IOException("FFmpeg stopped taking frames (see the .log next to the video)", failure);
            if (!process.waitFor(10, TimeUnit.MINUTES)) throw new IOException("FFmpeg didn't finish");
            if (process.exitValue() != 0) throw new IOException("FFmpeg failed (see the .log next to the video)");
        }

        @Override
        public void cancel() {
            writer.interrupt();
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            output.delete();
        }
    }

    private static final class PngSink implements Sink {
        private final File folder;
        private final int width, height;
        private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "Replay frame writer");
            thread.setDaemon(true);
            return thread;
        });
        private final Semaphore slots = new Semaphore(6);
        private final BlockingQueue<byte[]> spare = new ArrayBlockingQueue<>(6);
        private int index;
        private volatile IOException failure;

        PngSink(File folder, int width, int height) throws IOException {
            this.folder = folder;
            this.width = width;
            this.height = height;
            if (!folder.mkdirs()) throw new IOException("can't make " + folder);
        }

        @Override
        public byte[] buffer() throws InterruptedException {
            slots.acquire();
            byte[] frame = spare.poll();
            return frame != null ? frame : new byte[width * height * 3];
        }

        @Override
        public void write(byte[] frame) throws IOException {
            if (failure != null) throw failure;
            File file = new File(folder, String.format("frame_%06d.png", ++index));
            pool.execute(() -> {
                try {
                    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                    byte[] out = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                    int row = width * 3;

                    for (int y = 0; y < height; y++) {
                        int from = (height - 1 - y) * row, to = y * row;
                        for (int i = 0; i < row; i += 3) {
                            out[to + i] = frame[from + i + 2];
                            out[to + i + 1] = frame[from + i + 1];
                            out[to + i + 2] = frame[from + i];
                        }
                    }
                    ImageIO.write(image, "png", file);
                } catch (IOException e) {
                    failure = e;
                } finally {
                    spare.offer(frame);
                    slots.release();
                }
            });
        }

        @Override
        public void finish() throws IOException, InterruptedException {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.MINUTES);
            if (failure != null) throw failure;
        }

        @Override
        public void cancel() {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            File[] files = folder.listFiles();
            if (files != null) for (File file : files) file.delete();
            folder.delete();
        }
    }
}
