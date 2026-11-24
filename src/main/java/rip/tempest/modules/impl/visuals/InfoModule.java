package rip.tempest.modules.impl.visuals;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.KeyPressEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.interfaces.click.exhibition.util.Colors;
import rip.tempest.interfaces.click.exhibition.util.RenderingUtil;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.events.impl.render.ShaderEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.modules.impl.player.ScaffoldModule;
import rip.tempest.modules.impl.player.StealerModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.processes.DraggingProcess;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.AnimatedGif;
import rip.tempest.utils.render.ClientLogo;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.render.shaders.StencilUtils;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "HUD", category = ModuleCategory.VISUALS)
public final class InfoModule extends Module {
    public static final ModeProperty<Type> type = new ModeProperty<>("Client Watermark Type", Type.Simple);

    public static final Property<Boolean> themeColors = new Property<>("Theme Colors", true, () -> virtueTheme(type.getValue()) != null);

    public static final ModeProperty<TabGuiMode> tabGuiMode = new ModeProperty<>("Tab GUI Mode", TabGuiMode.Off);

    public enum TabGuiMode {
        Off("Off"),
        Lucid("Virtue Lucid"),
        New("Virtue New"),
        Luna("Luna"),
        Flux("Flux"),
        FluxNew("Flux New"),
        Moon("Moon"),
        MoonNostalgia("Moon Nostalgia"),
        Novoline("Novoline"),
        Opal("Opal"),
        Astolfo("Astolfo"),
        Euphoria("Euphoria"),
        Exhibition("Exhibition"),
        Step("Sigma");

        private final String display;

        TabGuiMode(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private static final String TAB_DRAG_KEY = "TabGui";
    private final java.util.Map<TabGuiMode, rip.tempest.modules.impl.visuals.tabgui.HudTabGui> tabGuis = new java.util.EnumMap<>(TabGuiMode.class);

    private rip.tempest.modules.impl.visuals.tabgui.HudTabGui activeTabGui() {
        TabGuiMode mode = tabGuiMode.getValue();
        if (mode == TabGuiMode.Off) return null;
        return tabGuis.computeIfAbsent(mode, m -> switch (m) {
            case Lucid -> virtueAdapter(new VirtueTabGui(true));
            case New -> virtueAdapter(new VirtueTabGui(false));
            case Luna -> {
                LunaTabGui luna = new LunaTabGui();
                yield new rip.tempest.modules.impl.visuals.tabgui.HudTabGui() {
                    public void render() { luna.render(customName); }
                    public float width() { return luna.width(); }
                    public float height() { return luna.height(); }
                    public void keyPress(int key) { luna.keyPress(key); }
                };
            }
            case Flux -> new rip.tempest.modules.impl.visuals.tabgui.FluxTabGui(false);
            case FluxNew -> new rip.tempest.modules.impl.visuals.tabgui.FluxTabGui(true);
            case Moon -> new rip.tempest.modules.impl.visuals.tabgui.MoonTabGui();
            case MoonNostalgia -> new rip.tempest.modules.impl.visuals.tabgui.MoonNostalgiaTabGui();
            case Novoline -> new rip.tempest.modules.impl.visuals.tabgui.NovolineTabGui();
            case Opal -> new rip.tempest.modules.impl.visuals.tabgui.OpalTabGui();
            case Astolfo -> new rip.tempest.modules.impl.visuals.tabgui.AstolfoTabGui();
            case Euphoria -> new rip.tempest.modules.impl.visuals.tabgui.EuphoriaTabGui();
            case Step -> new rip.tempest.modules.impl.visuals.tabgui.StepTabGui();
            default -> new rip.tempest.modules.impl.visuals.tabgui.ExhibitionTabGui();
        });
    }

    private static rip.tempest.modules.impl.visuals.tabgui.HudTabGui virtueAdapter(VirtueTabGui tab) {
        return new rip.tempest.modules.impl.visuals.tabgui.HudTabGui() {
            public void render() { tab.render(); }
            public float width() { return tab.width(); }
            public float height() { return tab.height(); }
            public void keyPress(int key) { tab.keyPress(key); }
        };
    }

    private void drawTabGui() {
        rip.tempest.modules.impl.visuals.tabgui.HudTabGui tab = activeTabGui();
        DraggingProcess.DraggableComponent d = DraggingProcess.components.computeIfAbsent(TAB_DRAG_KEY,
                k -> new DraggingProcess.DraggableComponent(2, 30));
        if (tab == null || mc.gameSettings.showDebugInfo) {
            d.setWidth(0);
            d.setHeight(0);
            return;
        }
        rip.tempest.modules.impl.visuals.tabgui.StepTabGui step = attachedStepTab();
        if (tab instanceof rip.tempest.modules.impl.visuals.tabgui.StepTabGui s && s != step) s.attachedWidth = 0;
        double tx = d.getX(), ty = d.getY();
        if (step != null) {
            DraggingProcess.DraggableComponent w = component();
            tx = w.getX();
            ty = w.getY() + 13;
            d.setWidth(0);
            d.setHeight(0);
        } else {
            d.setWidth(tab.width());
            d.setHeight(tab.height());
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(tx, ty, 0);
        tab.render();
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
    public static final Property<Boolean> info = new Property<>("Watermark Info", true, () -> hasInfoToggle(type.getValue()));

    public static final Property<Boolean> classicProtocol = new Property<>("Protocol", true, () -> type.getValue() == Type.Classic);
    public static final Property<Boolean> classicTime = new Property<>("Time", true, () -> type.getValue() == Type.Classic);
    public static final Property<Boolean> classicFps = new Property<>("FPS", true, () -> type.getValue() == Type.Classic);
    public static final Property<Boolean> classicPing = new Property<>("Ping", true, () -> type.getValue() == Type.Classic);
    public static final Property<Boolean> classicTps = new Property<>("TPS", true, () -> type.getValue() == Type.Classic);
    public static final Property<String> name = new Property<>("Client Name", "Tempest");

    public static final Property<Boolean> customServer = new Property<>("Custom Server", false, () -> showsServer(type.getValue()));
    public static final Property<String> serverAddress = new Property<>("Server Address", "play.example.com",
            () -> showsServer(type.getValue()) && customServer.getValue());

    public static final Property<Boolean> alternativeColors = new Property<>("Alternative Color", true, () -> type.getValue() == Type.AstralisSense);

    public static final SelectProperty gifChoice = new SelectProperty("Gif", () -> type.getValue() == Type.Gif);

    public static final NumberProperty gifSpeed = new NumberProperty("Gif Speed", 100, () -> type.getValue() == Type.Gif, 10, 300, 5);

    public static final Property<String> moonFormat = new Property<>("Watermark String", "%cn%", () -> type.getValue() == Type.MoonCustom);
    public static final NumberProperty gifScale = new NumberProperty("Gif Scale", 1, () -> type.getValue() == Type.Gif, 0.25, 3, 0.05);

    public static String customName = "Tempest";

    public enum Type {
        Simple,
        Astolfo,
        Logo,
        Island,
        Tutorial2017,
        Wurst,
        Nursultan,
        Gradient,
        Scan,

        Ticker,
        Step {
            @Override
            public String toString() {
                return "Sigma";
            }
        },

        ETBOriginal {
            @Override
            public String toString() {
                return "ETB (Original)";
            }
        },

        Luna,

        Capsule,
        Sense,
        Virtue,
        Classic,
        Natsuki,
        Lesbian,

        Text,
        Astralis,
        Adjust,
        Gif,
        AstralisSense {
            @Override
            public String toString() {
                return "Astralis Sense";
            }
        },

        VirtuePlain {
            @Override
            public String toString() {
                return "Virtue Plain";
            }
        },
        NewVirtue {
            @Override
            public String toString() {
                return "New Virtue";
            }
        },
        Crest,
        Trendy,
        Indigo,

        MoonMoon {
            @Override
            public String toString() {
                return "Moon";
            }
        },
        MoonJello {
            @Override
            public String toString() {
                return "Moon Jello";
            }
        },
        MoonAstolfo {
            @Override
            public String toString() {
                return "Moon Astolfo";
            }
        },
        MoonRemix {
            @Override
            public String toString() {
                return "Moon Remix";
            }
        },
        MoonNovoline {
            @Override
            public String toString() {
                return "Moon Novoline";
            }
        },
        MoonXave {
            @Override
            public String toString() {
                return "Moon Xave";
            }
        },
        MoonFlux {
            @Override
            public String toString() {
                return "Moon Flux";
            }
        },
        MoonCustom {
            @Override
            public String toString() {
                return "Moon Custom";
            }
        },
        MoonNostalgia {
            @Override
            public String toString() {
                return "Moon Nostalgia";
            }
        },
        MoonVirtue {
            @Override
            public String toString() {
                return "Moon Virtue";
            }
        }
    }

    private static InfoModule instance;

    private static rip.tempest.modules.impl.client.ClientSettingsModule.Color virtueTheme(Type style) {
        return switch (style) {
            case VirtuePlain -> rip.tempest.modules.impl.client.ClientSettingsModule.Color.VirtuePlain;
            case NewVirtue -> rip.tempest.modules.impl.client.ClientSettingsModule.Color.NewVirtue;
            case Crest -> rip.tempest.modules.impl.client.ClientSettingsModule.Color.Crest;
            case Trendy -> rip.tempest.modules.impl.client.ClientSettingsModule.Color.Trendy;
            case Indigo -> rip.tempest.modules.impl.client.ClientSettingsModule.Color.Indigo;
            default -> null;
        };
    }

    public static rip.tempest.modules.impl.client.ClientSettingsModule.Color themeColor() {
        if (rip.tempest.processes.Themes.active() || instance == null || !instance.isEnabled() || !themeColors.getValue()) return null;
        return virtueTheme(type.getValue());
    }

    private static boolean showsServer(Type style) {
        return switch (style) {
            case Island, Capsule, Sense, Astralis, AstralisSense, Ticker -> true;
            default -> false;
        };
    }

    private static String customServerText() {
        if (!customServer.getValue()) return null;
        String text = serverAddress.getValue();
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private static boolean hasInfoToggle(Type style) {
        return switch (style) {
            case Logo, Island, Tutorial2017, Wurst, Nursultan, Luna,
                 Capsule, Sense, Virtue, Classic, Natsuki, Lesbian,
                 Text, Astralis, Adjust, Gif, AstralisSense,
                 VirtuePlain, NewVirtue, Crest, Trendy, Indigo, Ticker, Step -> false;
            default -> true;
        };
    }

    private final LunaTabGui lunaTabGui = new LunaTabGui();
    private final TickerWatermark ticker = new TickerWatermark();
    private final VirtueTabGui lucidTabGui = new VirtueTabGui(true);
    private final VirtueTabGui newTabGui = new VirtueTabGui(false);

    private VirtueTabGui virtueTabGui() {
        if (virtueTheme(type.getValue()) == null) return null;

        return null;
    }

    private static float virtueTabOffset() {
        return switch (type.getValue()) {
            case Trendy -> 12;
            case Indigo -> 26;
            default -> 0;
        };
    }

    private long lastServerTime;
    private long lastUpdateTime;
    private String currentServer;
    private float islandWidth;
    private float islandHeight;
    private ResourceLocation wurstLogo = new ResourceLocation("tempest/images/wurst.png");
    private static final ResourceLocation ETB_ORIGINAL_LOGO = new ResourceLocation("tempest/images/etb_original.png");
    private float stealerWidth = 200f;
    private float stealerHeight = 28f;
    private final java.util.Map<Integer, ChestItemAnimation> chestItemAnimations = new java.util.HashMap<>();
    private int lastChestSize = 0;
    private boolean wasInChest = false;

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = e -> {
        setSuffix(type.getValue().toString());
        customName = name.getValue();
        drawWatermark();
        drawTabGui();
    };

    @EventLink
    public Listener<KeyPressEvent> keyPressEventListener = e -> {
        if (type.getValue() == Type.Luna) lunaTabGui.keyPress(e.getKey());
        VirtueTabGui virtueTabGui = virtueTabGui();
        if (virtueTabGui != null) virtueTabGui.keyPress(e.getKey());
        if (mc.currentScreen == null) {
            rip.tempest.modules.impl.visuals.tabgui.HudTabGui tab = activeTabGui();
            if (tab != null) tab.keyPress(e.getKey());
        }
    };

    private static final String DRAG_KEY = "Watermark";

    private static CustomFontRenderer stepNameFont() {
        return FontProcess.getStyledFont("sfdisplaybold", 22, java.awt.Font.PLAIN);
    }

    private static CustomFontRenderer stepVersionFont() {
        return FontProcess.getStyledFont("sfdisplaybold", 14, java.awt.Font.PLAIN);
    }

    private static String stepVersion() {
        return "v" + Tempest.VERSION.split(" ")[0];
    }

    private static float stepVersionX(String name) {
        return 1 + stepNameFont().getStringWidth(name) + 0.5f;
    }

    private rip.tempest.modules.impl.visuals.tabgui.StepTabGui attachedStepTab() {
        if (type.getValue() != Type.Step || tabGuiMode.getValue() != TabGuiMode.Step) return null;
        return activeTabGui() instanceof rip.tempest.modules.impl.visuals.tabgui.StepTabGui s ? s : null;
    }

    private float stepWatermarkWidth(String name) {
        float own = Math.max(53, stepVersionX(name) + stepVersionFont().getStringWidth(stepVersion()) + 1);
        rip.tempest.modules.impl.visuals.tabgui.StepTabGui tab = attachedStepTab();
        return tab == null ? own : Math.max(own, tab.naturalWidth());
    }

    private void drawStepWatermark(String name) {
        rip.tempest.modules.impl.visuals.tabgui.StepTabGui tab = attachedStepTab();
        float width = stepWatermarkWidth(name);
        if (tab != null) tab.attachedWidth = width;
        int opacity = tab == null ? 200 : Math.round(tab.opacity());
        int textAlpha = tab == null ? 232 : Math.min(255, opacity + 64);
        int versionAlpha = tab == null ? 255 : Math.min(255, opacity + 64);

        RenderUtils.drawRect(0, 0, width, 13, new Color(0, 0, 0, opacity));
        CustomFontRenderer nameFont = stepNameFont(), versionFont = stepVersionFont();
        nameFont.drawStringWithShadow(name, 1, (13 - nameFont.getHeight()) / 2f, new Color(255, 255, 255, textAlpha).getRGB());
        Color accent = ColorProcess.getColor();
        versionFont.drawStringWithShadow(stepVersion(), stepVersionX(name), 2.5f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), versionAlpha).getRGB());
    }

    private void drawWatermark() {
        ScaledResolution sr = new ScaledResolution(mc);
        DraggingProcess.DraggableComponent c = component();

        GlStateManager.pushMatrix();
        GlStateManager.translate(c.getX(), c.getY(), 0);
        drawWatermarkContent(sr);
        GlStateManager.popMatrix();
    }

    private DraggingProcess.DraggableComponent component() {
        DraggingProcess.DraggableComponent c = DraggingProcess.components.get(DRAG_KEY);
        if (c == null) {
            c = new DraggingProcess.DraggableComponent(0, 0);
            DraggingProcess.components.put(DRAG_KEY, c);
        }

        float[] size = watermarkBounds();
        c.setWidth(size[0]);
        c.setHeight(size[1]);

        if (c.getX() < 0) c.setX(0);
        if (c.getY() < 0) c.setY(0);
        return c;
    }

    private float[] watermarkBounds() {
        float[] moonSize = MoonWatermark.size(type.getValue(), customName, moonFormat.getValue(), String.valueOf(getPing()));
        if (moonSize != null) return moonSize;

        CustomFontRenderer fr = FontProcess.getCurrentFont();
        String text = info.getValue() ? customName + " " + Tempest.VERSION : customName;

        return switch (type.getValue()) {
            case Logo -> new float[]{157 / 2f + 4, 125 / 2f + 4};
            case Ticker -> ticker.size(customName, serverName("Singleplayer"), clock());
            case Step -> new float[]{stepWatermarkWidth(customName), 13 + (attachedStepTab() != null ? attachedStepTab().height() : 0)};
            case ETBOriginal -> new float[]{64, info.getValue() ? 133 : 92};
            case Luna -> new float[]{lunaTabGui.width(), lunaTabGui.height()};
            case Wurst -> new float[]{223, 24};
            case Island -> new float[]{Math.max(60, islandWidth), Math.max(28, islandHeight)};
            case Tutorial2017 -> new float[]{Math.max(fr.getStringWidth(customName), fr.getStringWidth("FPS: " + mc.getDebugFPS())) + 8, 28};
            case Capsule -> new float[]{capsuleWidth(customName) + 4, FontProcess.getFont("sf").getHeight() + 12};
            case Sense -> new float[]{FontProcess.getFont("sf").getStringWidth(senseText(customName)) + 18, 27.5f};
            case Virtue -> new float[]{virtueWidth(customName) + 4, 36};
            case Classic -> new float[]{mc.fontRendererObj.getStringWidth(customName + classicInfo()) + 6, 13};
            case Natsuki -> new float[]{74, 106};
            case Lesbian -> new float[]{PICTURE_WIDTH + 8, PICTURE_HEIGHT + 8};
            case Text -> new float[]{headlineFont().getStringWidth(customName) * headlineScale() + 8,
                    textTop(3) + headlineFont().getHeight() * headlineScale() + 6};
            case Astralis -> new float[]{astralisBarWidth(customName) + 7, 25};
            case Adjust -> new float[]{adjustWidth(customName) + 8, 18};
            case Gif -> new float[]{gifBox() + 7, gifBox() + 7};
            case AstralisSense -> new float[]{astralisSenseWidth(customName) + 9, 20};
            case VirtuePlain -> withVirtueTab(new float[]{virtueFont().getStringWidth(customName) + 4, 12});
            case NewVirtue -> withVirtueTab(new float[]{virtueFont().getStringWidth(newVirtueText(customName)) + 4, 12});
            case Crest -> withVirtueTab(new float[]{virtueFont().getStringWidth(crestText(customName)) + 4, 12});
            case Trendy -> withVirtueTab(new float[]{Math.max(virtueFont().getStringWidth(trendyText(customName)), virtueFont().getStringWidth(trendyCoords())) + 4, 24});
            case Indigo -> withVirtueTab(new float[]{Math.max(indigoNameWidth(customName), virtueFont().getStringWidth("FPS: " + Minecraft.getDebugFPS())) + 4, 38});
            default -> new float[]{fr.getStringWidth(text) + 10, fr.FONT_HEIGHT + 8};
        };
    }

    private void drawWatermarkContent(ScaledResolution sr) {
        CustomFontRenderer fr = FontProcess.getCurrentFont();
        String clientName = customName;
        String text = clientName;

        if (type.getValue() == Type.Astolfo) {
            if (!clientName.isEmpty()) {
                text = clientName.charAt(0) + EnumChatFormatting.WHITE.toString() + clientName.substring(1) + " ";
            }

            fr.drawStringWithShadow(text, 2, 2, ColorProcess.getColor().getRGB());
        } else if (type.getValue() == Type.Simple) {
            text = info.getValue() ? clientName + EnumChatFormatting.WHITE + " " + Tempest.VERSION : clientName;
            fr.drawStringWithShadow(text, 2, 2, ColorProcess.getColor().getRGB());
        } else if (type.getValue() == Type.Gradient) {
            drawGradientWatermark(fr, clientName);
        } else if (type.getValue() == Type.Scan) {
            drawScanWatermark(fr, clientName);
        } else if (type.getValue() == Type.Ticker) {
            ticker.draw(clientName, serverName("Singleplayer"), clock(), getPing(), false);
        } else if (type.getValue() == Type.Step) {
            drawStepWatermark(clientName);
        } else if (type.getValue() == Type.Logo) {
            ClientLogo.draw(2, 2, 157 / 2f, 125 / 2f);
        } else if (type.getValue() == Type.Island) {
            renderDynamicIsland(sr, clientName);
        } else if (type.getValue() == Type.Tutorial2017) {
            RenderUtils.drawRect(2, 2, fr.getStringWidth(clientName) + 4, fr.FONT_HEIGHT + 2, new Color(0, 0, 0, 100));
            fr.drawString(clientName, 4, 4, 0x5555FF);
            RenderUtils.drawRect(2, 15, fr.getStringWidth("FPS: " + mc.getDebugFPS()) + 4, fr.FONT_HEIGHT + 2, new Color(0, 0, 0, 100));
            fr.drawString("FPS: " + mc.getDebugFPS(), 4, 17, -1);
        } else if (type.getValue() == Type.Wurst) {
            RenderUtils.drawRect(0, 10, 223, 21, new Color(255, 255, 255, 100));
            RenderUtils.drawImage(wurstLogo, 0, 10f, 758 / 8.5f, 192 / 8.5f);
            mc.fontRendererObj.drawString("v7.46.1 MC1.8.9 (outdated)", 92, 17, Color.BLACK.getRGB());
        } else if (type.getValue() == Type.Nursultan) {
            String nursultan = clientName + EnumChatFormatting.WHITE + " - " + mc.getDebugFPS() + " FPS - " + getPing() + "ms";
            drawRoundOutline(2, 2, fr.getStringWidth(nursultan) + 4, fr.FONT_HEIGHT + 4, 4, 0.05f,
                    new Color(0, 0, 0, 130), ColorProcess.getColor());
            fr.drawStringWithShadow(nursultan, 4, 4, ColorProcess.getColor().getRGB());
        } else if (type.getValue() == Type.ETBOriginal) {
            drawEtbOriginalWatermark();
        } else if (type.getValue() == Type.Luna) {
            lunaTabGui.render(customName);
        } else if (type.getValue() == Type.Capsule) {
            drawCapsuleWatermark(clientName);
        } else if (type.getValue() == Type.Sense) {
            drawSenseWatermark(clientName);
        } else if (type.getValue() == Type.Virtue) {
            drawVirtueWatermark(clientName);
        } else if (type.getValue() == Type.Classic) {
            drawClassicWatermark(clientName);
        } else if (type.getValue() == Type.Natsuki) {
            drawPicture(NATSUKI_PICTURE, -30, -24, 128, 128, 0);
        } else if (type.getValue() == Type.Lesbian) {
            drawPicture(LESBIAN_PICTURE, 4, 4, PICTURE_WIDTH, PICTURE_HEIGHT, 6);
        } else if (type.getValue() == Type.Text) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(3, textTop(3), 0);
            GlStateManager.scale(headlineScale(), headlineScale(), 1);
            drawWaveString(headlineFont(), clientName, 0, 0, false, ASTRALIS_WAVE_SPEED);
            GlStateManager.popMatrix();
        } else if (type.getValue() == Type.Astralis) {
            drawAstralisWatermark(clientName);
        } else if (type.getValue() == Type.Adjust) {
            drawAdjustWatermark(clientName);
        } else if (type.getValue() == Type.Gif) {
            drawGifWatermark();
        } else if (type.getValue() == Type.AstralisSense) {
            drawAstralisSenseWatermark(clientName);
        } else if (virtueTheme(type.getValue()) != null) {
            drawVirtueTheme(clientName);
        } else {
            MoonWatermark.draw(type.getValue(), clientName, moonFormat.getValue(), String.valueOf(getPing()), false);
        }
    }

    private static net.minecraft.client.gui.FontRenderer virtueFont() {
        return rip.tempest.utils.render.VirtueFont.get();
    }

    private static String virtueClock() {
        String time = new SimpleDateFormat("hh:mm a").format(new Date());
        return time.startsWith("0") ? time.replaceFirst("0", "") : time;
    }

    private static String newVirtueText(String name) {
        return name + "\u00a77 " + virtueClock();
    }

    private static String crestText(String name) {
        String brand = name.isEmpty() ? "" : "\u00a74" + name.charAt(0) + "\u00a77" + name.substring(1);
        return String.format("%s \u00a7r(%s - %s)", brand, virtueClock(), "v" + Tempest.VERSION.split(" ")[0]);
    }

    private static String trendyText(String name) {
        return name + " " + new SimpleDateFormat("{hh:mm a}").format(new Date());
    }

    private static String trendyCoords() {
        if (mc.thePlayer == null) return "0, 0, 0";
        return Math.round(mc.thePlayer.posX) + ", " + Math.round(mc.thePlayer.posY) + ", " + Math.round(mc.thePlayer.posZ);
    }

    private static final float INDIGO_SCALE = 1.7f;

    private static float indigoRestX(String name) {
        if (name.isEmpty()) return 2;
        return Math.round((1 + virtueFont().getStringWidth(name.substring(0, 1))) * INDIGO_SCALE);
    }

    private static float indigoNameWidth(String name) {
        return name.length() < 2 ? indigoRestX(name) : indigoRestX(name) + virtueFont().getStringWidth(name.substring(1));
    }

    private static int virtueColor(int themeColor) {
        return themeColor() != null ? themeColor : ColorProcess.getColor().getRGB();
    }

    private float[] withVirtueTab(float[] size) {
        VirtueTabGui tab = virtueTabGui();
        if (tab == null) return size;
        return new float[]{Math.max(size[0], tab.width()), Math.max(size[1], virtueTabOffset() + tab.height())};
    }

    private void drawVirtueTheme(String name) {
        net.minecraft.client.gui.FontRenderer font = virtueFont();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        switch (type.getValue()) {
            case VirtuePlain -> font.drawStringWithShadow(name, 2, 2, virtueColor(0xFFFFFFFF));
            case NewVirtue -> font.drawStringWithShadow(newVirtueText(name), 2, 2, virtueColor(0xFFCBCED1));
            case Crest -> font.drawStringWithShadow(crestText(name), 2, 2, virtueColor(0xFFCBCED1));
            case Trendy -> {
                int color = virtueColor(0xFF677B89);
                font.drawStringWithShadow(trendyText(name), 2, 2, color);
                font.drawStringWithShadow(trendyCoords(), 2, 14, color);
            }
            case Indigo -> {
                int color = virtueColor(0xFF76CBD6), grey = 0xFF667175;
                if (!name.isEmpty()) {
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(INDIGO_SCALE, INDIGO_SCALE, INDIGO_SCALE);
                    font.drawStringWithShadow(name.substring(0, 1), 1, 1, color);
                    GlStateManager.popMatrix();
                    font.drawStringWithShadow(name.substring(1), indigoRestX(name), 7, color);
                }
                font.drawStringWithShadow(virtueClock(), 2, 17, grey);
                font.drawStringWithShadow("FPS: " + Minecraft.getDebugFPS(), 2, 28, grey);
            }
            default -> {
            }
        }

        VirtueTabGui tab = virtueTabGui();
        if (tab != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, virtueTabOffset(), 0);
            tab.render();
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static final float GIF_BOX = 80f;

    private static float gifBox() {
        return GIF_BOX * gifScale.getValue().floatValue();
    }

    private static final String DUCK = "Dancing Duck";
    private static final String DUCK_RESOURCE = "/assets/minecraft/tempest/gifs/dancing-duck.gif";
    private AnimatedGif gif;
    private int gifFrame;
    private long gifFrameTime;

    public InfoModule() {
        instance = this;
        gifChoice.setOnOpen(InfoModule::scanGifs);
        scanGifs();
    }

    private static void scanGifs() {
        List<String> names = new ArrayList<>();
        try {
            File folder = gifFolder();
            if (!folder.exists()) folder.mkdirs();
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".gif"));
            if (files != null) {
                for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 4));
            }
        } catch (Exception ignored) {
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        names.add(0, DUCK);
        gifChoice.setOptions(names);
    }

    private static File gifFolder() {
        return new File(mc.mcDataDir, Tempest.NAME + "/gifs");
    }

    private static CustomFontRenderer headlineFont() {
        CustomFontRenderer current = FontProcess.getCurrentFont();
        return isMinecraftFont(current) ? current : FontProcess.getStyledFont(current.getNameFontTTF(), 24f, Font.PLAIN);
    }

    private static float headlineScale() {
        return isMinecraftFont(FontProcess.getCurrentFont()) ? 1.25f : 1f;
    }

    private static boolean isMinecraftFont(CustomFontRenderer font) {
        return font.getNameFontTTF() == null || font.getNameFontTTF().equalsIgnoreCase("mc");
    }

    private static CustomFontRenderer adjustBold() {
        return FontProcess.getStyledFont("tahoma", 20f, Font.BOLD);
    }

    private static CustomFontRenderer adjustRegular() {
        return FontProcess.getStyledFont("tahoma", 20f, Font.PLAIN);
    }

    private static float textTop(float lineTop) {
        return lineTop + 2;
    }

    public static final double GRADIENT_WAVE_SPEED = 260.0;

    private static final double ASTRALIS_WAVE_SPEED = 200.0;

    private static double wavePhase(double speed) {
        return System.currentTimeMillis() / speed;
    }

    private static void drawWaveString(CustomFontRenderer font, String text, float x, float y, boolean shadow, double speed) {
        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();
        double phase = wavePhase(speed);
        float total = Math.max(1, font.getStringWidth(text));
        for (int i = 0; i < text.length(); i++) {
            float offset = font.getStringWidth(text.substring(0, i));
            int color = Glyphs.waveColor(first, second, offset / total, phase).getRGB();
            String letter = String.valueOf(text.charAt(i));
            if (shadow) font.drawStringWithShadow(letter, x + offset, y, color);
            else font.drawString(letter, x + offset, y, color);
        }
    }

    private String astralisServer() {
        String custom = customServerText();
        if (custom != null) return custom;
        if (mc.isSingleplayer() || mc.getCurrentServerData() == null) return "none";
        String ip = mc.getCurrentServerData().serverIP;
        String domain = "liquidproxy.net";
        return ip.endsWith(domain) ? "***" + ip.substring(ip.lastIndexOf(domain)) : ip;
    }

    private String astralisInfo(String clientName) {
        String player = NickHiderModule.displayName();
        return clientName + " " + EnumChatFormatting.WHITE + Tempest.VERSION + " - " + astralisServer()
                + " - FPS " + Minecraft.getDebugFPS() + " - " + player;
    }

    private float astralisBarWidth(String clientName) {
        return FontProcess.getFont("sfui").getStringWidth(astralisInfo(clientName)) + 6;
    }

    private void drawAstralisWatermark(String clientName) {
        CustomFontRenderer font = FontProcess.getFont("sfui");
        String info = astralisInfo(clientName);
        float x = 5, y = 5, width = font.getStringWidth(info) + 6, height = 18;

        RenderUtils.drawRoundedRect(x, y, width, height, 3, new Color(0, 0, 0, 100));
        Glyphs.waveOutline(x, y, width, height, 3, 1.5f, ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(), wavePhase(ASTRALIS_WAVE_SPEED));
        font.drawString(info, x + 3.5f, textTop(y + 3), ColorProcess.colors.getFirst().getRGB());
    }

    private float adjustWidth(String clientName) {
        if (clientName.isEmpty()) return 0;
        return adjustBold().getStringWidth(clientName.substring(0, 1)) + adjustRegular().getStringWidth(clientName.substring(1));
    }

    private void drawAdjustWatermark(String clientName) {
        if (clientName.isEmpty()) return;
        String first = clientName.substring(0, 1);
        float x = 5, y = 5;
        drawWaveString(adjustBold(), first, x, textTop(y), true, ASTRALIS_WAVE_SPEED);
        adjustRegular().drawStringWithShadow(clientName.substring(1), x + adjustBold().getStringWidth(first), textTop(y), 0xFFFFFFFF);
    }

    private void drawGifWatermark() {
        String name = gifChoice.getValue();
        if (name == null || !gifChoice.getOptions().contains(name)) name = DUCK;
        float x = 5, y = 5;
        if (gif == null || !gif.source().equals(name)) {
            if (gif != null) gif.delete();
            gif = loadGif(name);
            gifFrame = 0;
            gifFrameTime = 0;
        }

        if (gif.failed()) {
            FontProcess.getFont("sfui").drawStringWithShadow("Can't read the GIF: " + name, x, y, 0xFFFF6B6B);
            return;
        }
        if (!gif.ready() || gif.frameCount() == 0) return;

        long now = System.currentTimeMillis();
        double speed = Math.max(10, gifSpeed.getValue()) / 100.0;
        long frameLength = Math.max(1, Math.round(gif.delay(gifFrame) / speed));
        if (gifFrameTime == 0 || now - gifFrameTime > 5000) gifFrameTime = now;
        while (now - gifFrameTime >= frameLength) {
            gifFrame = (gifFrame + 1) % gif.frameCount();
            gifFrameTime += frameLength;
            frameLength = Math.max(1, Math.round(gif.delay(gifFrame) / speed));
        }

        float box = gifBox();
        float scale = box / Math.max(gif.width(), gif.height());
        float w = gif.width() * scale, h = gif.height() * scale;
        gif.draw(gifFrame, x + (box - w) / 2f, y + (box - h) / 2f, w, h);
    }

    private static AnimatedGif loadGif(String name) {
        if (name.equals(DUCK)) {
            return AnimatedGif.load(name, () -> InfoModule.class.getResourceAsStream(DUCK_RESOURCE));
        }
        File file = new File(gifFolder(), name + ".gif");
        return AnimatedGif.load(name, () -> new FileInputStream(file));
    }

    private String astralisSenseRest() {
        return " - (" + EnumChatFormatting.GRAY + Tempest.VERSION + EnumChatFormatting.WHITE + ") - " + astralisServer();
    }

    private float astralisSenseWidth(String clientName) {
        CustomFontRenderer font = FontProcess.getFont("sfui");
        return font.getStringWidth(clientName) + font.getStringWidth("Sense") + font.getStringWidth(astralisSenseRest()) + 4.5f;
    }

    private void drawAstralisSenseWatermark(String clientName) {
        CustomFontRenderer font = FontProcess.getFont("sfui");
        String rest = astralisSenseRest();
        float width = astralisSenseWidth(clientName);

        Color frame = alternativeColors.getValue() ? new Color(59, 57, 57) : new Color(59, 57, 57).darker();
        Color body = alternativeColors.getValue() ? new Color(33, 29, 29) : new Color(23, 23, 23);

        Glyphs.bar(4, 3, width + 3, 15, frame);
        Glyphs.bar(5, 4, width + 1, 13, body);
        Glyphs.bar(5.5f, 4.5f, width, 12, frame);
        Glyphs.bar(6.5f, 5.5f, width - 2, 10, body);
        Glyphs.waveGradient(6.5f, 15, width - 2, 0.5f, ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(), wavePhase(ASTRALIS_WAVE_SPEED));

        float textY = textTop(4.5f);
        float nameWidth = font.getStringWidth(clientName), senseWidth = font.getStringWidth("Sense");
        font.drawString(clientName, 8, textY, 0xFFFFFFFF);
        drawWaveString(font, "Sense", 8 + nameWidth, textY, false, GRADIENT_WAVE_SPEED);
        font.drawString(rest, 8 + nameWidth + senseWidth, textY, 0xFFFFFFFF);
    }

    private void drawEtbOriginalWatermark() {
        int colorInt = 0xffff4d4c;
        String hudName = "\24772.0\247r [" + facing() + "]";

        mc.getTextureManager().bindTexture(ETB_ORIGINAL_LOGO);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

        GlStateManager.pushMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawModalRectWithCustomSizedTexture(1, 2, 0, 0, 24, 24, 24, 24);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.popMatrix();

        mc.fontRendererObj.drawStringWithShadow(hudName, 23, 17, colorInt);

        int yOffset = 16;
        RenderingUtil.rectangleBordered(2, yOffset + 12, 62, yOffset + 74, 1, Colors.getColor(0, 150), Colors.getColor(0, 200));
        RenderingUtil.rectangle(3, yOffset + 13, 61, yOffset + 25, colorInt);

        mc.fontRendererObj.drawStringWithShadow("Combat", 8, yOffset + 15, -1);
        mc.fontRendererObj.drawString("\2477Render", 6, yOffset + 27, -1);
        mc.fontRendererObj.drawString("\2477Movement", 6, yOffset + 39, -1);
        mc.fontRendererObj.drawString("\2477Player", 6, yOffset + 51, -1);
        mc.fontRendererObj.drawString("\2477World", 6, yOffset + 63, -1);

        if (info.getValue()) {
            mc.fontRendererObj.drawStringWithShadow("\2477Ver: \247f" + serverProtocol(), 3, yOffset + 77, -1);
            mc.fontRendererObj.drawStringWithShadow("\2477FPS: \247f" + Minecraft.getDebugFPS(), 2, yOffset + 87, -1);
            mc.fontRendererObj.drawStringWithShadow("\2477Ping: \247f" + getPing(), 2, yOffset + 97, -1);
            mc.fontRendererObj.drawStringWithShadow("\2477TPS: \247f" + String.format("%.1f", ticksPerSecond), 2, yOffset + 107, -1);
        }
    }

    private String facing() {
        if (mc.thePlayer == null) return "N";
        float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw + (45F / 2F)) % 360;

        String facing = "N";
        if (yaw >= 0 && yaw < 45)
            facing = "S";
        if (yaw > 45 && yaw < 90)
            facing = "SW";
        if (yaw >= 90 && yaw < 135)
            facing = "W";
        if (yaw > 135 && yaw < 180)
            facing = "NW";
        if (yaw >= -180 && yaw < -135)
            facing = "N";
        if (yaw > -135 && yaw < -90)
            facing = "NE";
        if (yaw >= -90 && yaw < -45)
            facing = "E";
        if (yaw > -45 && yaw < 0)
            facing = "SE";
        return facing;
    }

    private String serverProtocol() {
        try {
            ViaLoadingBase via = ViaLoadingBase.getInstance();
            if (via != null && via.getTargetVersion() != null) {
                return via.getTargetVersion().getName();
            }
        } catch (Exception ignored) {
        }
        return "1.8.x";
    }

    private long lastTimeUpdate = -1;
    private double ticksPerSecond = 20;

    @EventLink
    public Listener<PacketReceiveEvent> packetReceiveEventListener = e -> {
        if (!(e.getPacket() instanceof S03PacketTimeUpdate)) return;
        long now = System.currentTimeMillis();
        if (lastTimeUpdate > 0 && now > lastTimeUpdate) {
            ticksPerSecond = Math.max(0, Math.min(20, 20 / ((now - lastTimeUpdate) / 1000.0)));
        }
        lastTimeUpdate = now;
    };

    private void drawGradientWatermark(CustomFontRenderer fr, String clientName) {
        String text = info.getValue() ? clientName + " " + Tempest.VERSION : clientName;
        float x = 4, y = 4;

        Color c1 = ColorProcess.getColors().getFirst();
        Color c2 = ColorProcess.getColors().getSecond();
        float total = Math.max(1, fr.getStringWidth(text));

        double time = wavePhase(GRADIENT_WAVE_SPEED);

        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float pos = (cx - x) / total;
            float t = (float) (0.5 + 0.5 * Math.sin(pos * Math.PI * 2.0 - time));
            fr.drawStringWithShadow(ch, cx, y, RenderUtils.interpolateColorC(c1, c2, t).getRGB());
            cx += fr.getStringWidth(ch);
        }
    }

    private void drawScanWatermark(CustomFontRenderer fr, String clientName) {
        String text = info.getValue() ? clientName + " " + Tempest.VERSION : clientName;
        float x = 4, y = 4;
        float total = Math.max(1, fr.getStringWidth(text));

        Color accent = ColorProcess.getColor();
        Color dim = new Color(150, 150, 160);
        float sweep = (System.currentTimeMillis() % 2200L) / 2200f;

        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float pos = (cx - x) / total;
            float highlight = Math.max(0f, 1f - Math.abs(pos - sweep) * 6f);
            Color base = RenderUtils.interpolateColorC(dim, accent, highlight);
            Color out = RenderUtils.interpolateColorC(base, Color.WHITE, highlight * 0.6f);
            fr.drawStringWithShadow(ch, cx, y, out.getRGB());
            cx += fr.getStringWidth(ch);
        }
    }

    private static final Color CAPSULE_BACKGROUND = new Color(0, 0, 0, 130);
    private static final float CAPSULE_GAP = 4f;
    private static final float CAPSULE_ICON = 8f;
    private static final Color SENSE_FRAME = new Color(59, 57, 57);
    private static final Color SENSE_BODY = new Color(23, 23, 23);
    private static final ResourceLocation NATSUKI_PICTURE = new ResourceLocation("tempest/images/natsuki.png");
    private static final ResourceLocation LESBIAN_PICTURE = new ResourceLocation("tempest/images/lesbians.png");

    private static final float PICTURE_HEIGHT = 128f;
    private static final float PICTURE_WIDTH = PICTURE_HEIGHT * 362f / 512f;

    private String serverName(String alone) {
        String custom = customServerText();
        if (custom != null) return custom;
        if (mc.isSingleplayer()) return alone;
        return mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "unknown";
    }

    private String clock() {
        return new SimpleDateFormat("h:mm a").format(new Date());
    }

    private void drawRoundOutline(float x, float y, float width, float height, float radius, float thickness, Color fill, Color outline) {
        DraggingProcess.DraggableComponent c = component();
        float offsetX = (float) c.getX(), offsetY = (float) c.getY();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-offsetX, -offsetY, 0);
        RenderUtils.drawRoundOutline(x + offsetX, y + offsetY, width, height, radius, thickness, fill, outline);
        GlStateManager.popMatrix();
    }

    private float capsuleWidth(String clientName) {
        CustomFontRenderer font = FontProcess.getFont("sf");
        return 8f + font.getStringWidth(clientName)
                + CAPSULE_GAP + CAPSULE_ICON + CAPSULE_GAP + font.getStringWidth(serverName("Singleplayer"))
                + CAPSULE_GAP + CAPSULE_ICON + CAPSULE_GAP + font.getStringWidth(Minecraft.getDebugFPS() + " FPS")
                + CAPSULE_GAP + CAPSULE_ICON + CAPSULE_GAP + font.getStringWidth(clock());
    }

    private void drawCapsuleWatermark(String clientName) {
        CustomFontRenderer font = FontProcess.getFont("sf");
        Color accent = ColorProcess.getColor();
        int main = accent.getRGB(), white = 0xFFFFFFFF;

        String initial = clientName.isEmpty() ? "" : clientName.substring(0, 1);
        String rest = clientName.isEmpty() ? "" : clientName.substring(1);
        String[] readouts = {serverName("Singleplayer"), Minecraft.getDebugFPS() + " FPS", clock()};

        drawRoundOutline(2, 2, capsuleWidth(clientName), font.getHeight() + 8f, 6f, -0.4f, CAPSULE_BACKGROUND, accent);

        float x = 6, y = 6;
        font.drawStringWithShadow(initial, x, y, main);
        x += font.getStringWidth(initial);
        font.drawStringWithShadow(rest, x, y, white);
        x += font.getStringWidth(rest) + CAPSULE_GAP;

        float iconY = y + font.getHeight() / 2f;
        for (int i = 0; i < readouts.length; i++) {
            float iconX = x + CAPSULE_ICON / 2f;
            if (i == 0) drawGlobeIcon(iconX, iconY, accent);
            else if (i == 1) drawMeterIcon(iconX, iconY, accent);
            else drawClockIcon(iconX, iconY, accent);
            x += CAPSULE_ICON + CAPSULE_GAP;

            font.drawStringWithShadow(readouts[i], x, y, white);
            x += font.getStringWidth(readouts[i]) + CAPSULE_GAP;
        }
    }

    private static void drawGlobeIcon(float centerX, float centerY, Color color) {
        float radius = 3.2f;
        Glyphs.ring(centerX, centerY, radius, 0.8f, 0f, 360f, color);
        Glyphs.stroke(centerX - radius, centerY, centerX + radius, centerY, 0.7f, color);

        float[] points = new float[26];
        for (int i = 0; i <= 12; i++) {
            double angle = Math.toRadians(i * 30);
            points[i * 2] = centerX + (float) Math.sin(angle) * radius * 0.45f;
            points[i * 2 + 1] = centerY - (float) Math.cos(angle) * radius;
        }
        Glyphs.path(0.7f, 1f, color, points);
    }

    private static void drawMeterIcon(float centerX, float centerY, Color color) {
        for (int bar = 0; bar < 3; bar++) {
            float barHeight = 2.4f + bar * 2f;
            Glyphs.bar(centerX - 3.4f + bar * 2.6f, centerY + 3.2f - barHeight, 1.6f, barHeight, color);
        }
    }

    private static void drawClockIcon(float centerX, float centerY, Color color) {
        float radius = 3.2f;
        Glyphs.ring(centerX, centerY, radius, 0.8f, 0f, 360f, color);
        Glyphs.stroke(centerX, centerY, centerX, centerY - radius * 0.6f, 0.8f, color);
        Glyphs.stroke(centerX, centerY, centerX + radius * 0.5f, centerY + radius * 0.25f, 0.8f, color);
    }

    private String senseText(String clientName) {
        String player = NickHiderModule.displayName();
        return clientName.toLowerCase() + "sense - " + player + " - " + serverName("singleplayer") + " - " + getPing() + "ms";
    }

    private void drawSenseWatermark(String clientName) {
        CustomFontRenderer font = FontProcess.getFont("sf");
        String brand = clientName.toLowerCase();
        String text = senseText(clientName);
        Color darker = SENSE_FRAME.darker();

        float x = 4.5f, y = 4.5f;
        float textWidth = font.getStringWidth(text) + 2;

        Glyphs.bar(x, y, textWidth + 7, 18.5f, SENSE_FRAME);
        Glyphs.bar(x + 2.5f, y + 2.5f, textWidth + 2, 13, SENSE_BODY);
        Glyphs.bar(x + 1, y + 1, textWidth + 5, 0.5f, darker);
        Glyphs.bar(x + 1, y + 17, textWidth + 5, 0.5f, darker);
        Glyphs.bar(x + 1, y + 1.5f, 0.5f, 16, darker);
        Glyphs.bar(x + 5.5f + textWidth, y + 1.5f, 0.5f, 16, darker);
        Glyphs.waveGradient(x + 2.5f, y + 14.5f, textWidth + 2, 1f, ColorProcess.colors.getFirst(), ColorProcess.colors.getSecond(), wavePhase(ASTRALIS_WAVE_SPEED));
        Glyphs.bar(x + 2.5f, y + 16, textWidth + 2, 0.5f, darker);

        drawWaveString(font, brand, x + 4.5f, y + 4.3f, true, GRADIENT_WAVE_SPEED);
        font.drawStringWithShadow(text.substring(brand.length()), x + 4.5f + font.getStringWidth(brand), y + 4.3f, 0xFFFFFFFF);
    }

    private float virtueWidth(String clientName) {
        net.minecraft.client.gui.FontRenderer font = mc.fontRendererObj;
        int widest = Math.max(font.getStringWidth(clientName), Math.max(font.getStringWidth(clock()), font.getStringWidth("Fps: " + Minecraft.getDebugFPS())));

        return Math.max(58, widest + 8);
    }

    private void drawVirtueWatermark(String clientName) {
        net.minecraft.client.gui.FontRenderer font = mc.fontRendererObj;
        float width = virtueWidth(clientName), height = 32;
        float x = 2, y = 2, center = x + width / 2f;

        Glyphs.bar(x, y, width, height, new Color(0x69, 0x69, 0x69, 0xA0));
        Glyphs.bar(x, y, width, 0.5f, Color.BLACK);
        Glyphs.bar(x, y + height - 0.5f, width, 0.5f, Color.BLACK);
        Glyphs.bar(x, y, 0.5f, height, Color.BLACK);
        Glyphs.bar(x + width - 0.5f, y, 0.5f, height, Color.BLACK);

        String[] lines = {clientName, clock(), "Fps: " + Minecraft.getDebugFPS()};
        for (int i = 0; i < lines.length; i++) {
            font.drawString(lines[i], center - font.getStringWidth(lines[i]) / 2f, 4f + i * 11f, 0xFFBFBFBF, true);
        }
    }

    private String classicInfo() {
        StringBuilder readouts = new StringBuilder();
        if (classicProtocol.getValue()) readouts.append(" §7[§r").append(serverProtocol()).append("§7]§f");
        if (classicTime.getValue()) readouts.append(" §7[§r").append(clock()).append("§7]§f");
        if (classicFps.getValue()) readouts.append(" §7[§r").append(Minecraft.getDebugFPS()).append(" FPS§7]§f");
        if (classicPing.getValue()) readouts.append(" §7[§r").append(getPing()).append("ms§7]§f");
        if (classicTps.getValue()) readouts.append(" §7[§r").append(String.format("%.1f", ticksPerSecond)).append(" TPS§7]§f");
        return readouts.toString();
    }

    private void drawClassicWatermark(String clientName) {
        net.minecraft.client.gui.FontRenderer font = mc.fontRendererObj;
        String initial = clientName.isEmpty() ? "" : "§l" + clientName.charAt(0);
        String rest = (clientName.isEmpty() ? "" : clientName.substring(1)) + classicInfo();

        font.drawStringWithShadow(initial, 2, 2, ColorProcess.getColor().getRGB());
        font.drawStringWithShadow(rest, 2 + font.getStringWidth(initial), 2, 0xFFFFFFFF);
    }

    private void drawPicture(ResourceLocation picture, float x, float y, float width, float height, float radius) {
        boolean rounded = radius > 0;
        boolean stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        if (rounded) {
            StencilUtils.checkSetupFBO(mc.getFramebuffer());
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(false, false, false, false);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            RenderUtils.drawRoundedRect(x, y, width, height, radius, Color.WHITE);
            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        }

        mc.getTextureManager().bindTexture(picture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0f, 0f, width, height, width, height);

        if (rounded && !stencilWasEnabled) GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private int getPing() {
        if (mc.thePlayer != null && mc.getNetHandler() != null) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());

            if (playerInfo != null) {
                return playerInfo.getResponseTime();
            }
        }

        return 0;
    }

    private void renderDynamicIsland(ScaledResolution sr, String clientName) {
        CustomFontRenderer fr = FontProcess.getCurrentFont();
        if (System.currentTimeMillis() - lastServerTime > 5000) {
            updateServerInfo();
            lastServerTime = System.currentTimeMillis();
        }

        int centerX = sr.getScaledWidth() / 2;
        int yPos = 8;

        StealerModule stealer = Tempest.INSTANCE.getModuleManager().getModule(StealerModule.class);
        boolean stealerActive = stealer != null && stealer.isEnabled() && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest;

        ScaffoldModule scaffold = Tempest.INSTANCE.getModuleManager().getModule(ScaffoldModule.class);
        boolean scaffoldEnabled = scaffold != null && scaffold.isEnabled();

        if (stealerActive) {
            renderStealerIsland(sr, centerX, yPos);
        } else if (scaffoldEnabled) {
            renderScaffoldIsland(sr, centerX, yPos);
        } else {
            renderNormalIsland(sr, centerX, yPos, clientName);
        }
    }
    private void renderNormalIsland(ScaledResolution sr, int centerX, int yPos, String clientName) {
        CustomFontRenderer fr = FontProcess.getCurrentFont();

        String serverText = customServerText() != null ? customServerText() : currentServer != null ? currentServer : "Loading...";
        String versionText = "v" + Tempest.VERSION;
        String fpsText = mc.getDebugFPS() + " fps";

        int contentPadding = 12;
        int logoSize = 30;
        int textSpacing = 8;

        int dotWidth = fr.getStringWidth("|");
        int textWidth = fr.getStringWidth(serverText) +
                textSpacing + dotWidth +
                textSpacing + fr.getStringWidth(versionText) +
                textSpacing + dotWidth +
                textSpacing + fr.getStringWidth(fpsText);

        int totalWidth = logoSize + contentPadding * 2 + textWidth + 8;
        int height = 28;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;

        float smoothTime = 0.2f;
        float alpha = 1.0f - (float) Math.exp(-deltaTime / smoothTime);

        islandHeight = RenderUtils.lerp(islandHeight, height, alpha);
        islandWidth = RenderUtils.lerp(islandWidth, totalWidth, alpha);

        float startX = centerX - islandWidth / 2;

        RenderUtils.drawRoundedRect(startX, yPos, islandWidth, islandHeight, 14f, new Color(20, 20, 25, 200));
        RenderUtils.drawRoundOutline(startX, yPos, islandWidth, islandHeight, 14f, 0.5f,
                new Color(0, 0, 0, 0), new Color(255, 255, 255, 30));

        float logoX = startX + contentPadding;
        float logoWidth = logoSize;
        float logoHeight = logoSize / 1.256f;
        float logoY = yPos + (islandHeight - logoHeight) / 2;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        ClientLogo.draw(logoX, logoY, logoWidth, logoHeight);
        GlStateManager.disableBlend();

        float textX = logoX + logoSize + 10;
        float textY = yPos + (islandHeight - fr.FONT_HEIGHT) / 1.8f;

        fr.drawString(serverText, (int) textX, (int) textY, ColorProcess.getColor().getRGB());
        textX += fr.getStringWidth(serverText) + textSpacing;

        fr.drawString("|", (int) textX, (int) textY, new Color(100, 100, 110).getRGB());
        textX += dotWidth + textSpacing;

        fr.drawString(versionText, (int) textX, (int) textY, new Color(150, 150, 160).getRGB());
        textX += fr.getStringWidth(versionText) + textSpacing;

        fr.drawString("|", (int) textX, (int) textY, new Color(100, 100, 110).getRGB());
        textX += dotWidth + textSpacing;

        fr.drawString(fpsText, (int) textX, (int) textY, new Color(180, 180, 190).getRGB());
    }

    private float scaffoldWidth = 200f;
    private float scaffoldHeight = 28f;
    private int lastBlockCount = 0;
    private float blockCountDisplay = 0;
    private long lastBPSUpdate = 0;
    private float currentBPS = 0;
    private float displayBPS = 0;

    private void renderScaffoldIsland(ScaledResolution sr, int centerX, int yPos) {
        CustomFontRenderer fr = FontProcess.getCurrentFont();

        int currentBlockCount = 0;
        int totalBlocks = 0;

        for (int i = 0; i < mc.thePlayer.inventory.mainInventory.length; i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                totalBlocks += stack.stackSize;
            }
        }

        if (mc.thePlayer.inventory.getCurrentItem() != null &&
                mc.thePlayer.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBlock) {
            currentBlockCount = totalBlocks;
        } else {
            currentBlockCount = totalBlocks;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBPSUpdate > 50) {
            double dx = mc.thePlayer.posX - mc.thePlayer.lastTickPosX;
            double dz = mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ;
            double distance = Math.sqrt(dx * dx + dz * dz) * 20;
            currentBPS = (float) distance;
            lastBPSUpdate = currentTime;
        }

        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        float smoothTime = 0.15f;
        float alpha = 1.0f - (float) Math.exp(-deltaTime / smoothTime);

        blockCountDisplay = RenderUtils.lerp(blockCountDisplay, currentBlockCount, alpha * 2);

        if (lastBlockCount == 0 || currentBlockCount > lastBlockCount) {
            lastBlockCount = currentBlockCount;
        }

        displayBPS = RenderUtils.lerp(displayBPS, currentBPS, alpha);

        int contentPadding = 12;
        int logoSize = 30;
        int textSpacing = 8;
        int progressBarWidth = 80;
        int progressBarHeight = 4;

        String blocksText = (int) blockCountDisplay + "/" + lastBlockCount;
        String bpsText = String.format("%.1f BPS", displayBPS);

        int dotWidth = fr.getStringWidth("|");
        int textWidth = logoSize + 10 +
                fr.getStringWidth(blocksText) + textSpacing +
                progressBarWidth + textSpacing +
                dotWidth + textSpacing +
                fr.getStringWidth(bpsText);

        int targetWidth = contentPadding * 2 + textWidth + 8;
        int targetHeight = 36;

        scaffoldHeight = RenderUtils.lerp(scaffoldHeight, targetHeight, alpha);
        scaffoldWidth = RenderUtils.lerp(scaffoldWidth, targetWidth, alpha);

        float startX = centerX - scaffoldWidth / 2;

        RenderUtils.drawRoundedRect(startX, yPos, scaffoldWidth, scaffoldHeight, 14f,
                new Color(20, 20, 25, 200));
        RenderUtils.drawRoundOutline(startX, yPos, scaffoldWidth, scaffoldHeight, 14f, 0.5f,
                new Color(0, 0, 0, 0), new Color(255, 255, 255, 30));

        float logoX = startX + contentPadding;
        float logoWidth = logoSize;
        float logoHeight = logoSize / 1.256f;
        float logoY = yPos + (scaffoldHeight - logoHeight) / 2;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        ClientLogo.draw(logoX, logoY, logoWidth, logoHeight);
        GlStateManager.disableBlend();

        float textX = logoX + logoSize + 10;
        float textY = yPos + (scaffoldHeight - fr.FONT_HEIGHT) / 1.8f;

        fr.drawString(blocksText, (int) textX, (int) textY, ColorProcess.getColor().getRGB());
        textX += fr.getStringWidth(blocksText) + textSpacing;

        float progressY = textY + fr.FONT_HEIGHT / 2 - progressBarHeight / 2;
        float progress = lastBlockCount > 0 ? (float) currentBlockCount / lastBlockCount : 0;
        progress = Math.max(0, Math.min(1, progress));

        RenderUtils.drawRoundedRect(textX, progressY, progressBarWidth, progressBarHeight, 2f,
                new Color(40, 40, 45, 180));

        if (progress > 0) {
            float fillWidth = progressBarWidth * progress;
            Color progressColor = progress < 0.2f ?
                    new Color(255, 100, 100) :
                    progress < 0.5f ?
                            new Color(255, 255, 100) :
                            ColorProcess.getColor();

            RenderUtils.drawRoundedRect(textX, progressY, fillWidth, progressBarHeight, 2f, progressColor);
        }

        textX += progressBarWidth + textSpacing;

        fr.drawString("|", (int) textX, (int) textY, new Color(100, 100, 110).getRGB());
        textX += dotWidth + textSpacing;

        Color bpsColor = displayBPS > 8 ?
                new Color(100, 255, 100) :
                displayBPS > 4 ?
                        new Color(255, 255, 100) :
                        new Color(180, 180, 190);
        fr.drawString(bpsText, (int) textX, (int) textY, bpsColor.getRGB());
    }

    private void renderStealerIsland(ScaledResolution sr, int centerX, int yPos) {
        CustomFontRenderer fr = FontProcess.getCurrentFont();

        if (!(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiChest)) {
            wasInChest = false;
            chestItemAnimations.clear();
            return;
        }

        net.minecraft.client.gui.inventory.GuiChest guiChest = (net.minecraft.client.gui.inventory.GuiChest) mc.currentScreen;
        net.minecraft.inventory.ContainerChest chest = (net.minecraft.inventory.ContainerChest) mc.thePlayer.openContainer;
        net.minecraft.inventory.IInventory inventory = chest.getLowerChestInventory();

        int chestSize = inventory.getSizeInventory();
        boolean isDoubleChest = chestSize > 27;
        int rows = isDoubleChest ? 6 : 3;
        int columns = 9;

        if (!wasInChest || lastChestSize != chestSize) {
            chestItemAnimations.clear();
            lastChestSize = chestSize;
        }
        wasInChest = true;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        float smoothTime = 0.15f;
        float alpha = 1.0f - (float) Math.exp(-deltaTime / smoothTime);

        for (int i = 0; i < chestSize; i++) {
            net.minecraft.item.ItemStack stack = inventory.getStackInSlot(i);

            if (!chestItemAnimations.containsKey(i)) {
                if (stack != null) {
                    chestItemAnimations.put(i, new ChestItemAnimation(stack));
                }
            } else {
                ChestItemAnimation anim = chestItemAnimations.get(i);
                if (stack == null && anim.lastStack != null) {
                    anim.removing = true;
                } else if (stack != null) {
                    anim.lastStack = stack;
                    anim.removing = false;
                    anim.opacity = 1.0f;
                    anim.scale = 1.0f;
                }
            }
        }

        java.util.Iterator<java.util.Map.Entry<Integer, ChestItemAnimation>> iterator =
                chestItemAnimations.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<Integer, ChestItemAnimation> entry = iterator.next();
            ChestItemAnimation anim = entry.getValue();

            if (anim.removing) {
                anim.opacity = Math.max(0, anim.opacity - deltaTime * 4);
                anim.scale = Math.min(1.5f, anim.scale + deltaTime * 3);

                if (anim.opacity <= 0) {
                    iterator.remove();
                }
            }
        }

        int contentPadding = 12;
        int logoSize = 30;
        int textSpacing = 8;
        int itemSize = 16;
        int itemSpacing = 2;

        String chestName = inventory.getDisplayName().getFormattedText();
        if (chestName.length() > 20) {
            chestName = chestName.substring(0, 20) + "...";
        }

        int chestGridWidth = (columns * itemSize) + ((columns - 1) * itemSpacing);
        int chestGridHeight = (rows * itemSize) + ((rows - 1) * itemSpacing);

        int textWidth = Math.max(fr.getStringWidth(chestName), chestGridWidth);
        int targetWidth = contentPadding * 2 + logoSize + 10 + textWidth + 8;
        int targetHeight = contentPadding * 2 + Math.max(logoSize, fr.FONT_HEIGHT + 8 + chestGridHeight);

        stealerHeight = RenderUtils.lerp(stealerHeight, targetHeight, alpha);
        stealerWidth = RenderUtils.lerp(stealerWidth, targetWidth, alpha);

        float startX = centerX - stealerWidth / 2;

        RenderUtils.drawRoundedRect(startX, yPos, stealerWidth, stealerHeight, 14f,
                new Color(20, 20, 25, 200));
        RenderUtils.drawRoundOutline(startX, yPos, stealerWidth, stealerHeight, 14f, 0.5f,
                new Color(0, 0, 0, 0), new Color(255, 255, 255, 30));

        float logoX = startX + contentPadding;
        float logoWidth = logoSize;
        float logoHeight = logoSize / 1.256f;
        float logoY = yPos + contentPadding;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        ClientLogo.draw(logoX, logoY, logoWidth, logoHeight);
        GlStateManager.disableBlend();

        float textX = logoX + logoSize + 10;
        float textY = yPos + contentPadding;

        fr.drawString(chestName, (int) textX, (int) textY, ColorProcess.getColor().getRGB());

        float gridX = textX;
        float gridY = textY + fr.FONT_HEIGHT + 6;

        if (chestGridWidth < fr.getStringWidth(chestName)) {
            gridX += (fr.getStringWidth(chestName) - chestGridWidth) / 2;
        }

        renderChestGrid(inventory, gridX, gridY, rows, columns, itemSize, itemSpacing);
    }

    private void renderChestGrid(net.minecraft.inventory.IInventory inventory, float startX, float startY,
                                 int rows, int columns, int itemSize, int itemSpacing) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableRescaleNormal();
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int slotIndex = row * columns + col;
                if (slotIndex >= inventory.getSizeInventory()) break;

                float slotX = startX + (col * (itemSize + itemSpacing));
                float slotY = startY + (row * (itemSize + itemSpacing));

                RenderUtils.drawRoundedRect(slotX, slotY, itemSize, itemSize, 2f,
                        new Color(30, 30, 35, 150));

                net.minecraft.item.ItemStack stack = inventory.getStackInSlot(slotIndex);
                ChestItemAnimation anim = chestItemAnimations.get(slotIndex);

                if (anim != null && anim.lastStack != null) {
                    GlStateManager.pushMatrix();

                    float centerX = slotX + itemSize / 2f;
                    float centerY = slotY + itemSize / 2f;

                    GlStateManager.translate(centerX, centerY, 0);
                    GlStateManager.scale(anim.scale, anim.scale, 1);
                    GlStateManager.translate(-centerX, -centerY, 0);

                    GlStateManager.color(1.0f, 1.0f, 1.0f, anim.opacity);

                    try {
                        mc.getRenderItem().renderItemAndEffectIntoGUI(anim.lastStack,
                                (int) slotX, (int) slotY);

                        if (anim.lastStack.stackSize > 1) {
                            String stackSize = String.valueOf(anim.lastStack.stackSize);
                            GlStateManager.disableLighting();
                            GlStateManager.disableDepth();
                            GlStateManager.disableBlend();

                            mc.fontRendererObj.drawStringWithShadow(stackSize,
                                    slotX + itemSize - mc.fontRendererObj.getStringWidth(stackSize) - 1,
                                    slotY + itemSize - 7,
                                    (int) (255 * anim.opacity) << 24 | 0xFFFFFF);

                            GlStateManager.enableLighting();
                            GlStateManager.enableDepth();
                            GlStateManager.enableBlend();
                        }
                    } catch (Exception e) {
                    }

                    GlStateManager.popMatrix();
                    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        }

        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    private void updateServerInfo() {
        currentServer = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP.toLowerCase().contains("liquidproxy") ? "LiquidProxy" : mc.getCurrentServerData().serverIP : "Singleplayer";
    }

    private static class ChestItemAnimation {
        float opacity = 1.0f;
        float scale = 1.0f;
        boolean removing = false;
        net.minecraft.item.ItemStack lastStack;

        ChestItemAnimation(net.minecraft.item.ItemStack stack) {
            this.lastStack = stack;
        }
    }
}
