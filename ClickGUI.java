package zarex.client.gui.clickui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.lwjgl.glfw.GLFW;

import zarex.client.zarexclient;
import zarex.client.core.Managers;
import zarex.client.core.manager.client.ModuleManager;
import zarex.client.core.manager.client.ThemeManager;
import zarex.client.features.hud.HudElement;
import zarex.client.features.modules.Module;
import zarex.client.features.modules.client.ClickGui;
import zarex.client.features.modules.client.HudEditor;
import zarex.client.gui.InterfaceStyle;
import zarex.client.gui.clickui.impl.ColorPickerElement;
import zarex.client.gui.clickui.impl.ModeElement;
import zarex.client.gui.clickui.impl.SearchBar;
import zarex.client.gui.clickui.impl.SliderElement;
import zarex.client.gui.font.FontRenderers;
import zarex.client.gui.hud.HudEditorGui;
import zarex.client.setting.Setting;
import zarex.client.setting.impl.ColorSetting;
import zarex.client.utility.render.Render2DEngine;
import zarex.client.utility.render.animation.AnimationUtility;
import zarex.client.utility.render.animation.EaseOutBack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static zarex.client.features.modules.Module.mc;

public class ClickGUI extends Screen {

    private static ClickGUI INSTANCE = new ClickGUI();

    private final Map<Module.Category, Category> categoryPanels = new LinkedHashMap<>();
    private final List<Module.Category> categories = new ArrayList<>();

    private Module.Category selectedCategory = Module.Category.COMBAT;
    private final Module.Category searchCategory = Module.Category.MISC;

    private Category searchPanel;
    private int searchPanelModuleCount = -1;

    private int guiX;
    private int guiY;
    private int guiWidth;
    private int guiHeight;

    private int categoryScroll;

    private static String moduleSearch = "";
    private boolean searchFocused;

    private boolean themesSelected;
    private int themeScroll;
    private int themeEditorScroll;

    private ThemeManager.ThemePreset editingTheme;

    private final List<AbstractElement> themeEditorElements = new ArrayList<>();
    private final List<Setting<ColorSetting>> themeEditorColors = new ArrayList<>();

    private Setting<ClickGui.colorModeEn> themeEditorColorMode;
    private Setting<Integer> themeEditorColorSpeed;
    private Setting<HudEditor.HudStyle> themeEditorHudStyle;
    private Setting<Float> themeEditorBlurOpacity;
    private Setting<Float> themeEditorBlurStrength;

    private boolean positioned;

    private boolean draggingWindow;
    private double dragOffsetX;
    private double dragOffsetY;

    private final Map<Module.Category, Float> tabHoverAnimations = new LinkedHashMap<>();

    private Module.Category transitionCategory = Module.Category.COMBAT;

    private float contentTransition = 1f;
    private float contentSlide;

    private float tabIndicatorY = Float.NaN;
    private float tabIndicatorOpacity;

    private float backdropOpacity;

    private float searchFocusProgress;

    private float themesHoverProgress;
    private float hudEditorHoverProgress;

    private float settingsPaneProgress;
    private float settingsPaneX;
    private float settingsPaneDrawX;
    private float settingsPaneY;
    private float settingsPaneWidth;
    private float settingsPaneHeight;

    private Module settingsModule;

    private float headerHoverProgress;
    private float closeHoverProgress;

    public static boolean anyHovered;
    public static boolean close;
    public static boolean imageDirection;

    public static String currentDescription = "";

    public final EaseOutBack imageAnimation = new EaseOutBack(6);

    public ClickGUI() {
        super(Text.of("Zarex Window GUI"));
        INSTANCE = this;
    }

    public static ClickGUI getInstance() {
        return INSTANCE;
    }

    public static void toggleSettingsFor(Module module) {
        ClickGUI gui = getInstance();

        if (gui.settingsModule == module) {
            gui.settingsModule = null;
            Managers.SOUND.playSwipeOut();
            return;
        }

        gui.settingsModule = module;
        gui.settingsPaneProgress = 0f;

        ModuleButton button = gui.findSettingsButton(module);

        if (button != null)
            button.resetSettingsScroll();

        Managers.SOUND.playSwipeIn();
    }

    public static ClickGUI getClickGui() {
        ClickGUI gui = getInstance();

        gui.ensureCategories();

        close = false;
        imageDirection = true;

        gui.imageAnimation.reset();

        gui.backdropOpacity = 0f;
        gui.tabIndicatorY = Float.NaN;
        gui.tabIndicatorOpacity = 0f;

        gui.transitionCategory = gui.selectedCategory;
        gui.contentTransition = 1f;
        gui.contentSlide = 0f;

        gui.searchFocused = false;
        gui.searchFocusProgress = 0f;

        gui.settingsPaneProgress = 0f;

        moduleSearch = "";

        SearchBar.listening = false;

        return gui;
    }

    public static String getModuleSearch() {
        return moduleSearch;
    }

    public static boolean matchesModule(Module module) {
        String query = moduleSearch.trim();

        return query.isEmpty()
                || module.getName()
                .toLowerCase(Locale.ROOT)
                .contains(query.toLowerCase(Locale.ROOT));
    }

    private void ensureCategories() {
        categories.clear();
        categories.addAll(Managers.MODULE.getCategories());

        List<String> categoryOrder = List.of(
                "Combat",
                "Movement",
                "Player",
                "Render",
                "Misc",
                "Client",
                "HUD"
        );

        categories.sort(
                Comparator.comparingInt(category -> {
                    int index = categoryOrder.indexOf(category.getName());
                    return index < 0 ? categoryOrder.size() : index;
                })
        );

        for (Module.Category category : categories) {
            if (!categoryPanels.containsKey(category)) {
                Category panel = new Category(
                        category,
                        Managers.MODULE.getModulesByCategory(category),
                        0,
                        0,
                        0,
                        22,
                        true
                );

                panel.setOpen(true);
                panel.init();

                categoryPanels.put(category, panel);
            }
        }

        if (
                searchPanel == null
                        || searchPanelModuleCount != Managers.MODULE.modules.size()
        ) {
            searchPanel = new Category(
                    searchCategory,
                    Managers.MODULE.modules,
                    0,
                    0,
                    0,
                    22,
                    true,
                    true
            );

            searchPanel.setOpen(true);
            searchPanel.init();

            searchPanelModuleCount = Managers.MODULE.modules.size();
        }

        categoryPanels.keySet()
                .removeIf(category -> !categories.contains(category));

        tabHoverAnimations.keySet()
                .removeIf(category -> !categories.contains(category));

        if (
                !categories.isEmpty()
                        && !categories.contains(selectedCategory)
        ) {
            selectedCategory = categories.getFirst();
        }

        if (
                transitionCategory == null
                        || !categories.contains(transitionCategory)
        ) {
            transitionCategory = selectedCategory;
            contentTransition = 1f;
        }
    }

    @Override
    protected void init() {
        ensureCategories();

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        /*
         * New proportions.
         *
         * The GUI is deliberately larger than the old one.
         * This gives modules and settings enough breathing room.
         */
        guiWidth = Math.min(
                1040,
                Math.max(
                        620,
                        screenWidth - 46
                )
        );

        guiHeight = Math.min(
                620,
                Math.max(
                        360,
                        screenHeight - 46
                )
        );

        if (!positioned) {
            guiX = (screenWidth - guiWidth) / 2;
            guiY = (screenHeight - guiHeight) / 2;

            positioned = true;
        }

        guiX = Math.clamp(
                guiX,
                0,
                Math.max(0, screenWidth - guiWidth)
        );

        guiY = Math.clamp(
                guiY,
                0,
                Math.max(0, screenHeight - guiHeight)
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        Category panel = getActivePanel();

        if (panel != null)
            panel.tick();

        imageAnimation.update(imageDirection);
    }

    @Override
    public void render(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        if (ModuleManager.clickGui.blur.getValue())
            applyBlur(delta);

        anyHovered = false;

        backdropOpacity = AnimationUtility.fast(
                backdropOpacity,
                138f,
                14f
        );

        settingsPaneProgress = AnimationUtility.fast(
                settingsPaneProgress,
                settingsModule == null ? 0f : 1f,
                15f
        );

        headerHoverProgress = AnimationUtility.fast(
                headerHoverProgress,
                Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        guiX,
                        guiY,
                        guiWidth,
                        62
                ) ? 1f : 0f,
                18f
        );

        if (!Objects.equals(
                transitionCategory,
                selectedCategory
        )) {
            transitionCategory = selectedCategory;
            contentTransition = 0f;
        }

        contentTransition = AnimationUtility.fast(
                contentTransition,
                1f,
                14f
        );

        contentSlide =
                (1f - contentTransition) * 14f;

        searchFocusProgress = AnimationUtility.fast(
                searchFocusProgress,
                searchFocused ? 1f : 0f,
                18f
        );

        int screenWidth =
                mc.getWindow().getScaledWidth();

        int screenHeight =
                mc.getWindow().getScaledHeight();

        if (draggingWindow) {
            guiX = Math.clamp(
                    (int) (mouseX - dragOffsetX),
                    0,
                    Math.max(
                            0,
                            screenWidth - guiWidth
                    )
            );

            guiY = Math.clamp(
                    (int) (mouseY - dragOffsetY),
                    0,
                    Math.max(
                            0,
                            screenHeight - guiHeight
                    )
            );
        }

        /*
         * ---------------------------------------------------------
         * BACKGROUND IMAGE
         * ---------------------------------------------------------
         */

        ClickGui.Image image =
                ModuleManager.clickGui.image.getValue();

        if (image != ClickGui.Image.None) {
            RenderSystem.setShaderTexture(
                    0,
                    image.file
            );

            Render2DEngine.renderTexture(
                    context.getMatrices(),
                    screenWidth
                            - image.fileWidth
                            * imageAnimation.getAnimationd(),
                    screenHeight
                            - image.fileHeight,
                    image.fileWidth,
                    image.fileHeight,
                    0,
                    0,
                    image.fileWidth,
                    image.fileHeight,
                    image.fileWidth,
                    image.fileHeight
            );
        }

        /*
         * ---------------------------------------------------------
         * BACKDROP
         * ---------------------------------------------------------
         */

        Render2DEngine.drawRect(
                context.getMatrices(),
                0,
                0,
                screenWidth,
                screenHeight,
                new Color(
                        4,
                        3,
                        9,
                        Math.clamp(
                                Math.round(backdropOpacity),
                                0,
                                165
                        )
                )
        );

        Color accent =
                Managers.THEMES.getAccentColor();

        /*
         * Ambient accent glow.
         */
        Render2DEngine.drawRound(
                context.getMatrices(),
                guiX - guiWidth * .20f,
                guiY - guiHeight * .35f,
                guiWidth * .65f,
                guiHeight * .70f,
                guiHeight * .32f,
                new Color(
                        accent.getRed(),
                        accent.getGreen(),
                        accent.getBlue(),
                        17
                )
        );

        Render2DEngine.drawRound(
                context.getMatrices(),
                guiX + guiWidth * .74f,
                guiY + guiHeight * .60f,
                guiWidth * .42f,
                guiHeight * .42f,
                guiHeight * .22f,
                new Color(
                        90,
                        45,
                        190,
                        14
                )
        );

        /*
         * ---------------------------------------------------------
         * MAIN WINDOW
         * ---------------------------------------------------------
         */

        InterfaceStyle.drawPanel(
                context.getMatrices(),
                guiX,
                guiY,
                guiWidth,
                guiHeight,
                20f,
                true
        );

        /*
         * Window inner surface.
         */
        Render2DEngine.drawRound(
                context.getMatrices(),
                guiX + 1,
                guiY + 1,
                guiWidth - 2,
                guiHeight - 2,
                19f,
                new Color(
                        11,
                        9,
                        18,
                        246
                )
        );

        /*
         * ---------------------------------------------------------
         * HEADER
         * ---------------------------------------------------------
         */

        renderHeader(
                context,
                mouseX,
                mouseY,
                accent
        );

        /*
         * ---------------------------------------------------------
         * SIDEBAR
         * ---------------------------------------------------------
         */

        int sidebarWidth =
                getSidebarWidth();

        Render2DEngine.drawRound(
                context.getMatrices(),
                guiX + 8,
                guiY + 70,
                sidebarWidth,
                guiHeight - 78,
                14f,
                new Color(
                        8,
                        7,
                        14,
                        238
                )
        );

        Render2DEngine.drawRect(
                context.getMatrices(),
                guiX + sidebarWidth + 16,
                guiY + 70,
                1,
                guiHeight - 84,
                new Color(
                        255,
                        255,
                        255,
                        13
                )
        );

        renderSidebar(
                context,
                mouseX,
                mouseY,
                accent,
                sidebarWidth
        );

        /*
         * ---------------------------------------------------------
         * CONTENT
         * ---------------------------------------------------------
         */

        int contentX =
                guiX + sidebarWidth + 32;

        int contentWidth =
                guiWidth - sidebarWidth - 48;

        context.getMatrices().push();

        context.getMatrices().translate(
                0f,
                contentSlide,
                0f
        );

        renderContent(
                context,
                mouseX,
                mouseY,
                delta,
                accent,
                contentX,
                contentWidth
        );

        context.getMatrices().pop();

        /*
         * ---------------------------------------------------------
         * DESCRIPTION TOOLTIP
         * ---------------------------------------------------------
         */

        if (
                !Objects.equals(
                        currentDescription,
                        ""
                )
                        && ModuleManager.clickGui.descriptions.getValue()
        ) {
            float tooltipWidth =
                    FontRenderers.sf_medium.getStringWidth(
                            currentDescription
                    ) + 18;

            Render2DEngine.drawRound(
                    context.getMatrices(),
                    mouseX + 8,
                    mouseY + 7,
                    tooltipWidth,
                    17,
                    5,
                    new Color(
                            12,
                            10,
                            20,
                            245
                    )
            );

            Render2DEngine.drawRound(
                    context.getMatrices(),
                    mouseX + 8,
                    mouseY + 7,
                    2,
                    17,
                    1,
                    accent
            );

            FontRenderers.sf_medium.drawString(
                    context.getMatrices(),
                    currentDescription,
                    mouseX + 14,
                    mouseY + 11,
                    accent.getRGB()
            );

            currentDescription = "";
        }

        /*
         * ---------------------------------------------------------
         * FOOTER
         * ---------------------------------------------------------
         */

        renderFooter(
                context,
                accent
        );

        if (
                !HudElement.anyHovered
                        && !anyHovered
                        && GLFW.glfwGetPlatform()
                        != GLFW.GLFW_PLATFORM_WAYLAND
        ) {
            GLFW.glfwSetCursor(
                    mc.getWindow().getHandle(),
                    GLFW.glfwCreateStandardCursor(
                            GLFW.GLFW_ARROW_CURSOR
                    )
            );
        }
    }

    private void renderHeader(
            DrawContext context,
            int mouseX,
            int mouseY,
            Color accent
    ) {
        /*
         * Logo.
         */
        InterfaceStyle.drawPill(
                context.getMatrices(),
                guiX + 16,
                guiY + 15,
                36,
                36,
                true
        );

        InterfaceStyle.drawMiniLogo(
                context,
                guiX + 22,
                guiY + 21,
                24
        );

        FontRenderers.sf_bold.drawString(
                context.getMatrices(),
                "ZAREX",
                guiX + 63,
                guiY + 15,
                InterfaceStyle.text().getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "CONTROL CENTER",
                guiX + 63,
                guiY + 32,
                InterfaceStyle.muted().getRGB()
        );

        /*
         * Current page badge.
         */
        String currentPage;

        if (themesSelected) {
            currentPage =
                    editingTheme != null
                            ? "THEME EDITOR"
                            : "THEMES";
        } else if (!moduleSearch.isBlank()) {
            currentPage = "SEARCH";
        } else {
            currentPage =
                    selectedCategory == null
                            ? "MODULES"
                            : categoryLabel(
                            selectedCategory
                    ).toUpperCase(
                            Locale.ROOT
                    );
        }

        float badgeWidth =
                FontRenderers.sf_medium_mini
                        .getStringWidth(currentPage)
                        + 22;

        float badgeX =
                guiX + guiWidth - 230;

        InterfaceStyle.drawPill(
                context.getMatrices(),
                badgeX,
                guiY + 21,
                badgeWidth,
                24,
                false
        );

        FontRenderers.sf_medium_mini.drawCenteredString(
                context.getMatrices(),
                currentPage,
                badgeX + badgeWidth / 2f,
                guiY + 29,
                new Color(
                        171,
                        162,
                        190
                ).getRGB()
        );

        /*
         * Search.
         */
        int searchWidth =
                getSearchWidth();

        int searchX =
                getSearchX();

        int searchY =
                guiY + 17;

        boolean searchHovered =
                Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        searchX,
                        searchY,
                        searchWidth,
                        32
                );

        int focusAlpha =
                Math.clamp(
                        (int)
                                (
                                        searchFocusProgress
                                                * 150f
                                                + (
                                                searchHovered
                                                        ? 35f
                                                        : 0f
                                        )
                                ),
                        0,
                        210
                );

        Color searchBackground =
                new Color(
                        18
                                + (int)
                                (
                                        searchFocusProgress * 5
                                ),
                        15
                                + (int)
                                (
                                        searchFocusProgress * 4
                                ),
                        27
                                + (int)
                                (
                                        searchFocusProgress * 8
                                ),
                        245
                );

        InterfaceStyle.drawOutlinedRound(
                context.getMatrices(),
                searchX,
                searchY,
                searchWidth,
                32,
                searchBackground,
                focusAlpha > 0
                        ? new Color(
                        accent.getRed(),
                        accent.getGreen(),
                        accent.getBlue(),
                        focusAlpha
                )
                        : new Color(
                        255,
                        255,
                        255,
                        28
                ),
                10f
        );

        /*
         * Search icon.
         */
        Render2DEngine.drawRound(
                context.getMatrices(),
                searchX + 10,
                searchY + 10,
                11,
                11,
                5.5f,
                new Color(
                        accent.getRed(),
                        accent.getGreen(),
                        accent.getBlue(),
                        searchFocused ? 135 : 65
                )
        );

        FontRenderers.sf_bold_mini.drawString(
                context.getMatrices(),
                "/",
                searchX + 13,
                searchY + 11,
                searchFocused
                        ? accent.getRGB()
                        : new Color(
                        130,
                        124,
                        143
                ).getRGB()
        );

        String searchText =
                moduleSearch.isEmpty()
                        ? "Search modules..."
                        : moduleSearch;

        if (
                searchFocused
                        && (
                        System.currentTimeMillis()
                                / 450L
                                ) % 2L == 0L
        ) {
            searchText += "_";
        }

        int available =
                searchWidth
                        - (
                        moduleSearch.isEmpty()
                                ? 35
                                : 50
                );

        while (
                searchText.length() > 1
                        && FontRenderers.sf_medium_mini
                        .getStringWidth(searchText)
                        > available
        ) {
            searchText =
                    searchText.substring(
                            0,
                            searchText.length() - 1
                    );
        }

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                searchText,
                searchX + 29,
                searchY + 12,
                moduleSearch.isEmpty()
                        ? new Color(
                        115,
                        110,
                        128
                ).getRGB()
                        : Color.WHITE.getRGB()
        );

        if (!moduleSearch.isEmpty()) {
            FontRenderers.sf_bold_mini.drawString(
                    context.getMatrices(),
                    "×",
                    searchX + searchWidth - 17,
                    searchY + 11,
                    new Color(
                            150,
                            145,
                            162
                    ).getRGB()
            );
        }

        /*
         * Header separator.
         */
        Render2DEngine.drawRect(
                context.getMatrices(),
                guiX + 18,
                guiY + 61,
                guiWidth - 36,
                1,
                new Color(
                        255,
                        255,
                        255,
                        18
                )
        );
    }

    private void renderSidebar(
            DrawContext context,
            int mouseX,
            int mouseY,
            Color accent,
            int sidebarWidth
    ) {
        int tabX = guiX + 18;
        int tabWidth = sidebarWidth - 20;

        FontRenderers.sf_bold_mini.drawString(
                context.getMatrices(),
                "NAVIGATION",
                tabX + 3,
                guiY + 82,
                new Color(
                        119,
                        111,
                        140
                ).getRGB()
        );

        int visibleTabs =
                getVisibleTabs();

        int sidebarItems =
                getSidebarItemCount();

        categoryScroll =
                Math.clamp(
                        categoryScroll,
                        0,
                        Math.max(
                                0,
                                sidebarItems
                                        - visibleTabs
                        )
                );

        int tabTop =
                getTabTop();

        float indicatorTargetY =
                Float.NaN;

        for (
                int i = categoryScroll;
                i < Math.min(
                        categories.size(),
                        categoryScroll + visibleTabs
                );
                i++
        ) {
            Module.Category category =
                    categories.get(i);

            int tabY =
                    tabTop
                            + (
                            i - categoryScroll
                    ) * 31;

            boolean hovered =
                    Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            tabX,
                            tabY,
                            tabWidth,
                            27
                    );

            boolean selected =
                    !themesSelected
                            && category.equals(
                            selectedCategory
                    );

            float target =
                    selected || hovered
                            ? 1f
                            : 0f;

            float hover =
                    AnimationUtility.fast(
                            tabHoverAnimations
                                    .getOrDefault(
                                            category,
                                            0f
                                    ),
                            target,
                            18f
                    );

            tabHoverAnimations.put(
                    category,
                    hover
            );

            drawSidebarItem(
                    context,
                    tabX,
                    tabY,
                    tabWidth,
                    categoryGlyph(category),
                    categoryLabel(category),
                    selected,
                    hovered,
                    hover
            );

            if (selected)
                indicatorTargetY =
                        tabY + 6;
        }

        /*
         * HUD Studio.
         */
        int hudIndex =
                categories.size();

        if (
                hudIndex >= categoryScroll
                        && hudIndex
                        < categoryScroll
                        + visibleTabs
        ) {
            int tabY =
                    tabTop
                            + (
                            hudIndex
                                    - categoryScroll
                    ) * 31;

            boolean hovered =
                    Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            tabX,
                            tabY,
                            tabWidth,
                            27
                    );

            hudEditorHoverProgress =
                    AnimationUtility.fast(
                            hudEditorHoverProgress,
                            hovered ? 1f : 0f,
                            18f
                    );

            drawSidebarItem(
                    context,
                    tabX,
                    tabY,
                    tabWidth,
                    "H",
                    "HUD Studio",
                    false,
                    hovered,
                    hudEditorHoverProgress
            );
        }

        /*
         * Themes.
         */
        int themesIndex =
                categories.size() + 1;

        if (
                themesIndex >= categoryScroll
                        && themesIndex
                        < categoryScroll
                        + visibleTabs
        ) {
            int tabY =
                    tabTop
                            + (
                            themesIndex
                                    - categoryScroll
                    ) * 31;

            boolean hovered =
                    Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            tabX,
                            tabY,
                            tabWidth,
                            27
                    );

            themesHoverProgress =
                    AnimationUtility.fast(
                            themesHoverProgress,
                            themesSelected || hovered
                                    ? 1f
                                    : 0f,
                            18f
                    );

            drawSidebarItem(
                    context,
                    tabX,
                    tabY,
                    tabWidth,
                    "T",
                    "Themes",
                    themesSelected,
                    hovered,
                    themesHoverProgress
            );

            if (themesSelected)
                indicatorTargetY =
                        tabY + 6;
        }

        /*
         * Animated indicator.
         */
        if (!Float.isNaN(indicatorTargetY)) {
            tabIndicatorY =
                    Float.isNaN(tabIndicatorY)
                            ? indicatorTargetY
                            : AnimationUtility.fast(
                            tabIndicatorY,
                            indicatorTargetY,
                            20f
                    );
        }

        tabIndicatorOpacity =
                AnimationUtility.fast(
                        tabIndicatorOpacity,
                        Float.isNaN(indicatorTargetY)
                                ? 0f
                                : 1f,
                        20f
                );

        if (
                !Float.isNaN(tabIndicatorY)
                        && tabIndicatorOpacity > .01f
        ) {
            Render2DEngine.drawRound(
                    context.getMatrices(),
                    tabX - 4,
                    tabIndicatorY,
                    3,
                    15,
                    1.5f,
                    new Color(
                            accent.getRed(),
                            accent.getGreen(),
                            accent.getBlue(),
                            Math.clamp(
                                    (int)
                                            (
                                                    255f
                                                            * tabIndicatorOpacity
                                            ),
                                    0,
                                    255
                            )
                    )
            );
        }

        /*
         * Version badge.
         */
        String version =
                "v" + zarexclient.VERSION;

        float versionWidth =
                FontRenderers.sf_medium_mini
                        .getStringWidth(version)
                        + 20;

        InterfaceStyle.drawPill(
                context.getMatrices(),
                tabX,
                guiY + guiHeight - 39,
                versionWidth,
                22,
                false
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                version,
                tabX + 10,
                guiY + guiHeight - 32,
                new Color(
                        145,
                        136,
                        164
                ).getRGB()
        );
    }

    private void drawSidebarItem(
            DrawContext context,
            float x,
            float y,
            float width,
            String glyph,
            String label,
            boolean selected,
            boolean hovered,
            float progress
    ) {
        Color accent =
                Managers.THEMES.getAccentColor();

        int backgroundAlpha =
                Math.clamp(
                        Math.round(
                                10
                                        + progress * 30
                        ),
                        0,
                        255
                );

        if (progress > .01f) {
            Render2DEngine.drawRound(
                    context.getMatrices(),
                    x,
                    y,
                    width,
                    27,
                    8,
                    new Color(
                            accent.getRed(),
                            accent.getGreen(),
                            accent.getBlue(),
                            backgroundAlpha
                    )
            );
        }

        if (selected) {
            Render2DEngine.drawRound(
                    context.getMatrices(),
                    x + 1,
                    y + 1,
                    25,
                    25,
                    7,
                    new Color(
                            accent.getRed(),
                            accent.getGreen(),
                            accent.getBlue(),
                            38
                    )
            );
        }

        FontRenderers.sf_bold_mini.drawCenteredString(
                context.getMatrices(),
                glyph,
                x + 13.5f,
                y + 9,
                selected || hovered
                        ? InterfaceStyle.text().getRGB()
                        : new Color(
                        132,
                        124,
                        154
                ).getRGB()
        );

        FontRenderers.sf_medium.drawString(
                context.getMatrices(),
                label,
                x + 29,
                y + 9,
                selected || hovered
                        ? InterfaceStyle.text().getRGB()
                        : new Color(
                        185,
                        178,
                        199
                ).getRGB()
        );
    }

    private void renderContent(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            Color accent,
            int contentX,
            int contentWidth
    ) {
        int contentTop =
                guiY + 78;

        String pageTitle;

        if (themesSelected) {
            pageTitle =
                    editingTheme != null
                            ? editingTheme.name
                            : "Themes";
        } else if (!moduleSearch.isBlank()) {
            pageTitle = "Search results";
        } else {
            pageTitle =
                    selectedCategory == null
                            ? "Modules"
                            : categoryLabel(
                            selectedCategory
                    );
        }

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                themesSelected
                        ? "APPEARANCE"
                        : "MODULE LIBRARY",
                contentX,
                contentTop,
                new Color(
                        130,
                        121,
                        153
                ).getRGB()
        );

        FontRenderers.sf_bold.drawString(
                context.getMatrices(),
                pageTitle,
                contentX,
                contentTop + 14,
                InterfaceStyle.text().getRGB()
        );

        String subtitle;

        if (themesSelected) {
            subtitle =
                    editingTheme != null
                            ? "Customize this preset"
                            : Managers.THEMES.getThemes().size()
                            + " saved presets";
        } else if (!moduleSearch.isBlank()) {
            subtitle =
                    countMatchingModules()
                            + " matching modules";
        } else {
            int count =
                    selectedCategory == null
                            ? 0
                            : Managers.MODULE
                            .getModulesByCategory(
                                    selectedCategory
                            )
                            .size();

            subtitle =
                    count
                            + " modules available";
        }

        while (
                subtitle.length() > 1
                        && FontRenderers.sf_medium
                        .getStringWidth(subtitle)
                        > contentWidth - 130
        ) {
            subtitle =
                    subtitle.substring(
                            0,
                            subtitle.length() - 1
                    );
        }

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                subtitle,
                contentX,
                contentTop + 31,
                new Color(
                        148,
                        139,
                        166
                ).getRGB()
        );

        /*
         * Item counter.
         */
        String counter;

        if (themesSelected) {
            counter =
                    editingTheme != null
                            ? "EDIT"
                            : Managers.THEMES.getThemes().size()
                            + " PRESETS";
        } else if (!moduleSearch.isBlank()) {
            counter =
                    countMatchingModules()
                            + " FOUND";
        } else {
            counter =
                    selectedCategory == null
                            ? "0 ITEMS"
                            : Managers.MODULE
                            .getModulesByCategory(
                                    selectedCategory
                            )
                            .size()
                            + " ITEMS";
        }

        float counterWidth =
                FontRenderers.sf_medium_mini
                        .getStringWidth(counter)
                        + 20;

        InterfaceStyle.drawPill(
                context.getMatrices(),
                contentX
                        + contentWidth
                        - counterWidth,
                contentTop - 2,
                counterWidth,
                22,
                false
        );

        FontRenderers.sf_medium_mini.drawCenteredString(
                context.getMatrices(),
                counter,
                contentX
                        + contentWidth
                        - counterWidth / 2f,
                contentTop + 5,
                new Color(
                        186,
                        178,
                        202
                ).getRGB()
        );

        Render2DEngine.drawRect(
                context.getMatrices(),
                contentX,
                contentTop + 48,
                contentWidth,
                1,
                new Color(
                        255,
                        255,
                        255,
                        16
                )
        );

        float panelY =
                contentTop + 61;

        float panelHeight =
                Math.max(
                        100,
                        guiHeight
                                - 149
                );

        if (themesSelected) {
            if (editingTheme == null) {
                renderThemesTab(
                        context,
                        mouseX,
                        mouseY,
                        contentX,
                        panelY,
                        contentWidth,
                        panelHeight
                );
            } else {
                renderThemeEditor(
                        context,
                        mouseX,
                        panelMouseY(mouseY),
                        contentX,
                        panelY,
                        contentWidth,
                        panelHeight,
                        delta
                );
            }

            return;
        }

        Category activePanel =
                getActivePanel();

        if (activePanel == null)
            return;

        ModuleButton settingsButton =
                findSettingsButton(
                        settingsModule
                );

        boolean showSettingsPane =
                settingsModule != null
                        && settingsButton != null;

        float paneWidth =
                showSettingsPane
                        ? Math.min(
                        300f,
                        contentWidth * .42f
                )
                        : 0f;

        boolean splitSettings =
                showSettingsPane
                        && contentWidth >= 520f;

        float listWidth =
                splitSettings
                        ? Math.max(
                        210f,
                        contentWidth
                                - paneWidth
                                - 14f
                )
                        : contentWidth;

        activePanel.setWindowLayout(
                contentX,
                panelY,
                listWidth,
                12,
                panelHeight
        );

        activePanel.render(
                context,
                mouseX,
                panelMouseY(mouseY),
                delta
        );

        /*
         * Empty search state.
         */
        if (
                !moduleSearch.isBlank()
                        && countMatchingModules() == 0
        ) {
            renderEmptySearch(
                    context,
                    contentX,
                    panelY,
                    listWidth
            );
        }

        /*
         * Settings panel.
         */
        if (showSettingsPane) {
            settingsPaneWidth =
                    splitSettings
                            ? paneWidth
                            : contentWidth;

            settingsPaneHeight =
                    panelHeight - 12f;

            settingsPaneX =
                    splitSettings
                            ? contentX
                            + listWidth
                            + 14f
                            : contentX
                            + contentWidth
                            - settingsPaneWidth;

            settingsPaneY =
                    panelY + 6f;

            float slide =
                    (1f - settingsPaneProgress)
                            * 16f;

            settingsPaneDrawX =
                    settingsPaneX + slide;

            settingsButton.renderSettingsPane(
                    context,
                    settingsPaneDrawX,
                    settingsPaneY,
                    settingsPaneWidth,
                    settingsPaneHeight,
                    mouseX,
                    panelMouseY(mouseY),
                    delta
            );
        }
    }

    private void renderEmptySearch(
            DrawContext context,
            int x,
            float y,
            float width
    ) {
        float boxX = x + 10;
        float boxY = y + 58;

        float boxWidth =
                Math.max(
                        100,
                        width - 20
                );

        Render2DEngine.drawRound(
                context.getMatrices(),
                boxX,
                boxY,
                boxWidth,
                72,
                12,
                new Color(
                        15,
                        12,
                        23,
                        238
                )
        );

        Color accent =
                Managers.THEMES.getAccentColor();

        Render2DEngine.drawRound(
                context.getMatrices(),
                boxX + 18,
                boxY + 18,
                34,
                34,
                10,
                new Color(
                        accent.getRed(),
                        accent.getGreen(),
                        accent.getBlue(),
                        30
                )
        );

        FontRenderers.sf_bold.drawCenteredString(
                context.getMatrices(),
                "?",
                boxX + 35,
                boxY + 27,
                accent.getRGB()
        );

        FontRenderers.sf_medium.drawString(
                context.getMatrices(),
                "No matching modules",
                boxX + 66,
                boxY + 17,
                new Color(
                        220,
                        214,
                        230
                ).getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "Try another name or category",
                boxX + 66,
                boxY + 36,
                new Color(
                        130,
                        123,
                        145
                ).getRGB()
        );
    }

    private void renderFooter(
            DrawContext context,
            Color accent
    ) {
        int y =
                guiY + guiHeight - 25;

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "LMB",
                guiX + 18,
                y,
                accent.getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "TOGGLE",
                guiX + 40,
                y,
                new Color(
                        121,
                        114,
                        137
                ).getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "RMB",
                guiX + 82,
                y,
                accent.getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "SETTINGS",
                guiX + 104,
                y,
                new Color(
                        121,
                        114,
                        137
                ).getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "MMB",
                guiX + 163,
                y,
                accent.getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "BIND",
                guiX + 185,
                y,
                new Color(
                        121,
                        114,
                        137
                ).getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "ESC",
                guiX + 221,
                y,
                accent.getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "CLOSE",
                guiX + 244,
                y,
                new Color(
                        121,
                        114,
                        137
                ).getRGB()
        );

        if (ModuleManager.clickGui.tips.getValue()) {
            FontRenderers.sf_medium_mini.drawString(
                    context.getMatrices(),
                    "DRAG HEADER TO MOVE",
                    guiX + guiWidth - 145,
                    y,
                    new Color(
                            105,
                            98,
                            122
                    ).getRGB()
            );
        }
    }

    private int panelMouseY(
            double mouseY
    ) {
        return (int)
                Math.round(
                        mouseY
                                - contentSlide
                );
    }

    private int getSidebarWidth() {
        return Math.min(
                205,
                Math.max(
                        165,
                        (int)
                                (
                                        guiWidth * .215f
                                )
                )
        );
    }

    private int getVisibleTabs() {
        return Math.max(
                1,
                (guiHeight - 145) / 31
        );
    }

    private int getTabTop() {
        return guiY + 101;
    }

    private int getSearchWidth() {
        return Math.clamp(
                guiWidth - 540,
                170,
                260
        );
    }

    private int getSearchX() {
        return guiX
                + guiWidth
                - 18
                - getSearchWidth();
    }

    private long countMatchingModules(
            Module.Category category
    ) {
        return Managers.MODULE
                .getModulesByCategory(category)
                .stream()
                .filter(ClickGUI::matchesModule)
                .count();
    }

    private long countMatchingModules() {
        return Managers.MODULE.modules
                .stream()
                .filter(ClickGUI::matchesModule)
                .count();
    }

    static String categoryLabel(
            Module.Category category
    ) {
        if (category == Module.Category.HUD)
            return "Hud";

        if (category == Module.Category.RENDER)
            return "Visuals";

        return category.getName();
    }

    private static String categoryGlyph(
            Module.Category category
    ) {
        return switch (category.getName()) {
            case "Combat" -> "×";
            case "Movement" -> "↗";
            case "Player" -> "P";
            case "Render" -> "◈";
            case "Misc" -> "•";
            case "Client" -> "⚙";
            case "HUD" -> "H";
            default ->
                    category.getName()
                            .substring(
                                    0,
                                    1
                            )
                            .toUpperCase(
                                    Locale.ROOT
                            );
        };
    }

    private int getSidebarItemCount() {
        return categories.size() + 2;
    }

    private Category getActivePanel() {
        return themesSelected
                ? null
                : !moduleSearch.isBlank()
                ? searchPanel
                : categoryPanels.get(
                selectedCategory
        );
    }

    private Module getHudEditorModule() {
        return Managers.MODULE.modules
                .stream()
                .filter(module ->
                        module.getName()
                                .equalsIgnoreCase(
                                        "HudEditor"
                                )
                )
                .findFirst()
                .orElse(null);
    }

    private ModuleButton findSettingsButton(
            Module module
    ) {
        if (module == null)
            return null;

        if (searchPanel != null) {
            ModuleButton button =
                    searchPanel.getModuleButton(
                            module
                    );

            if (button != null)
                return button;
        }

        Category panel =
                categoryPanels.get(
                        module.getCategory()
                );

        return panel == null
                ? null
                : panel.getModuleButton(
                module
        );
    }

    /*
     * ============================================================
     * THEMES
     * ============================================================
     */

    private void renderThemesTab(
            DrawContext context,
            int mouseX,
            int mouseY,
            float x,
            float y,
            float width,
            float height
    ) {
        List<ThemeManager.ThemePreset> themes =
                Managers.THEMES.getThemes();

        InterfaceStyle.drawCard(
                context.getMatrices(),
                x,
                y,
                width,
                height,
                14f,
                false,
                true
        );

        FontRenderers.sf_bold_mini.drawString(
                context.getMatrices(),
                "YOUR THEMES",
                x + 16,
                y + 15,
                new Color(
                        215,
                        210,
                        224
                ).getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "Create and manage your Zarex appearance presets",
                x + 16,
                y + 31,
                new Color(
                        123,
                        116,
                        138
                ).getRGB()
        );

        /*
         * New theme button.
         */
        int plusX =
                (int)
                        (
                                x
                                        + width
                                        - 51
                        );

        int plusY =
                (int)
                        (
                                y + 13
                        );

        boolean plusHovered =
                Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        plusX,
                        plusY,
                        35,
                        29
                );

        Render2DEngine.drawRound(
                context.getMatrices(),
                plusX,
                plusY,
                35,
                29,
                9f,
                plusHovered
                        ? new Color(
                        245,
                        242,
                        250
                )
                        : new Color(
                        30,
                        27,
                        38
                )
        );

        FontRenderers.sf_bold.drawCenteredString(
                context.getMatrices(),
                "+",
                plusX + 17.5f,
                plusY + 7,
                plusHovered
                        ? new Color(
                        10,
                        8,
                        15
                ).getRGB()
                        : Color.WHITE.getRGB()
        );

        /*
         * Current colors.
         */
        Color[] swatches = {
                HudEditor.hcolor1
                        .getValue()
                        .getColorObject(),

                HudEditor.acolor
                        .getValue()
                        .getColorObject(),

                HudEditor.plateColor
                        .getValue()
                        .getColorObject(),

                HudEditor.textColor
                        .getValue()
                        .getColorObject(),

                HudEditor.textColor2
                        .getValue()
                        .getColorObject(),

                HudEditor.blurColor
                        .getValue()
                        .getColorObject()
        };

        String[] names = {
                "ACC",
                "ALT",
                "PANEL",
                "TEXT",
                "MUTED",
                "BLUR"
        };

        float swatchCellWidth =
                Math.min(
                        48f,
                        (width - 32f)
                                / swatches.length
                );

        float swatchWidth =
                Math.max(
                        10f,
                        swatchCellWidth - 8f
                );

        float swatchX =
                x + 16;

        float swatchY =
                y + 53;

        for (
                int i = 0;
                i < swatches.length;
                i++
        ) {
            Render2DEngine.drawRound(
                    context.getMatrices(),
                    swatchX,
                    swatchY,
                    swatchWidth,
                    10,
                    4,
                    swatches[i]
            );

            FontRenderers.sf_medium_mini.drawString(
                    context.getMatrices(),
                    names[i],
                    swatchX,
                    swatchY + 14,
                    new Color(
                            104,
                            98,
                            117
                    ).getRGB()
            );

            swatchX += swatchCellWidth;
        }

        float cardsTop =
                y + 86;

        float cardsBottom =
                y + height - 13;

        int columns =
                width >= 600
                        ? 2
                        : 1;

        float gap = 12f;

        float cardWidth =
                (
                        width
                                - 28f
                                - gap
                                * (
                                columns - 1
                        )
                )
                        / columns;

        int rows =
                (
                        themes.size()
                                + columns
                                - 1
                )
                        / columns;

        float contentHeight =
                rows == 0
                        ? 0
                        : rows * 82f
                        + Math.max(
                        0,
                        rows - 1
                ) * gap;

        float viewportHeight =
                Math.max(
                        0f,
                        cardsBottom
                                - cardsTop
                );

        themeScroll =
                Math.clamp(
                        themeScroll,
                        0,
                        Math.max(
                                0,
                                (int)
                                        Math.ceil(
                                                contentHeight
                                                        - viewportHeight
                                        )
                        )
                );

        Render2DEngine.addWindow(
                context.getMatrices(),
                x + 7,
                cardsTop,
                x + width - 7,
                cardsBottom,
                1f
        );

        for (
                int i = 0;
                i < themes.size();
                i++
        ) {
            ThemeManager.ThemePreset theme =
                    themes.get(i);

            int row =
                    i / columns;

            int column =
                    i % columns;

            float cardX =
                    x + 14
                            + column
                            * (
                            cardWidth
                                    + gap
                    );

            float cardY =
                    cardsTop
                            + row
                            * (
                            82f
                                    + gap
                    )
                            - themeScroll;

            boolean active =
                    theme.name.equalsIgnoreCase(
                            Objects.toString(
                                    Managers.THEMES
                                            .getActiveThemeName(),
                                    ""
                            )
                    );

            boolean hovered =
                    Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            cardX,
                            cardY,
                            cardWidth,
                            82
                    );

            InterfaceStyle.drawCard(
                    context.getMatrices(),
                    cardX,
                    cardY,
                    cardWidth,
                    82,
                    12f,
                    hovered,
                    active
            );

            FontRenderers.sf_medium.drawString(
                    context.getMatrices(),
                    theme.name,
                    cardX + 13,
                    cardY + 13,
                    Color.WHITE.getRGB()
            );

            FontRenderers.sf_medium_mini.drawString(
                    context.getMatrices(),
                    active
                            ? "ACTIVE"
                            : theme.colorMode,
                    cardX + 13,
                    cardY + 29,
                    active
                            ? Managers.THEMES
                            .getAccentColor()
                            .getRGB()
                            : new Color(
                            125,
                            119,
                            137
                    ).getRGB()
            );

            Color[] presetColors = {
                    new Color(
                            theme.primaryColor,
                            true
                    ),
                    new Color(
                            theme.secondaryColor,
                            true
                    ),
                    new Color(
                            theme.panelColor,
                            true
                    ),
                    new Color(
                            theme.textColor,
                            true
                    ),
                    new Color(
                            theme.secondaryTextColor,
                            true
                    ),
                    new Color(
                            theme.blurColor,
                            true
                    )
            };

            float chipX =
                    cardX + 13;

            for (Color color : presetColors) {
                Render2DEngine.drawRound(
                        context.getMatrices(),
                        chipX,
                        cardY + 52,
                        22,
                        12,
                        4,
                        color
                );

                chipX += 26;
            }

            int deleteX =
                    (int)
                            (
                                    cardX
                                            + cardWidth
                                            - 28
                            );

            boolean deleteHovered =
                    Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            deleteX,
                            (int) cardY + 9,
                            18,
                            18
                    );

            Render2DEngine.drawRound(
                    context.getMatrices(),
                    deleteX,
                    cardY + 9,
                    18,
                    18,
                    6,
                    deleteHovered
                            ? new Color(
                            50,
                            45,
                            58
                    )
                            : new Color(
                            25,
                            22,
                            32
                    )
            );

            FontRenderers.sf_medium_mini.drawCenteredString(
                    context.getMatrices(),
                    "×",
                    deleteX + 9,
                    cardY + 14,
                    deleteHovered
                            ? Color.WHITE.getRGB()
                            : new Color(
                            130,
                            123,
                            142
                    ).getRGB()
            );
        }

        Render2DEngine.popWindow();

        if (themes.isEmpty()) {
            FontRenderers.sf_medium.drawCenteredString(
                    context.getMatrices(),
                    "No saved themes",
                    x + width / 2f,
                    cardsTop + 38,
                    new Color(
                            210,
                            205,
                            220
                    ).getRGB()
            );

            FontRenderers.sf_medium_mini.drawCenteredString(
                    context.getMatrices(),
                    "Press + to save your current palette",
                    x + width / 2f,
                    cardsTop + 55,
                    new Color(
                            120,
                            113,
                            134
                    ).getRGB()
            );
        }
    }

    private void beginThemeEdit(
            ThemeManager.ThemePreset theme
    ) {
        editingTheme = theme;

        themeEditorScroll = 0;

        themeEditorElements.clear();
        themeEditorColors.clear();

        themeEditorColorMode =
                new Setting<>(
                        "Color mode",
                        enumValue(
                                ClickGui.colorModeEn.class,
                                theme.colorMode,
                                ClickGui.colorModeEn.Static
                        )
                );

        themeEditorColorSpeed =
                new Setting<>(
                        "Color speed",
                        Math.clamp(
                                theme.colorSpeed,
                                2,
                                54
                        ),
                        2,
                        54
                );

        themeEditorHudStyle =
                new Setting<>(
                        "Blur style",
                        enumValue(
                                HudEditor.HudStyle.class,
                                theme.hudStyle,
                                HudEditor.HudStyle.Blurry
                        )
                );

        themeEditorBlurOpacity =
                new Setting<>(
                        "Blur opacity",
                        Math.clamp(
                                theme.blurOpacity,
                                0f,
                                1f
                        ),
                        0f,
                        1f
                );

        themeEditorBlurStrength =
                new Setting<>(
                        "Blur strength",
                        Math.clamp(
                                theme.blurStrength,
                                5f,
                                50f
                        ),
                        5f,
                        50f
                );

        addThemeEditorElement(
                new ModeElement(
                        themeEditorColorMode
                )
        );

        addThemeEditorElement(
                new SliderElement(
                        themeEditorColorSpeed
                )
        );

        addThemeColor(
                "Accent",
                theme.primaryColor,
                theme.primaryRainbow
        );

        addThemeColor(
                "Secondary",
                theme.secondaryColor,
                theme.secondaryRainbow
        );

        addThemeColor(
                "Panel",
                theme.panelColor,
                theme.panelRainbow
        );

        addThemeColor(
                "Text",
                theme.textColor,
                theme.textRainbow
        );

        addThemeColor(
                "Muted text",
                theme.secondaryTextColor,
                theme.secondaryTextRainbow
        );

        addThemeColor(
                "Blur color",
                theme.blurColor,
                theme.blurRainbow
        );

        addThemeEditorElement(
                new ModeElement(
                        themeEditorHudStyle
                )
        );

        addThemeEditorElement(
                new SliderElement(
                        themeEditorBlurOpacity
                )
        );

        addThemeEditorElement(
                new SliderElement(
                        themeEditorBlurStrength
                )
        );
    }

    private <T extends Enum<T>> T enumValue(
            Class<T> enumClass,
            String name,
            T fallback
    ) {
        try {
            return Enum.valueOf(
                    enumClass,
                    name
            );
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addThemeColor(
            String name,
            int color,
            boolean rainbow
    ) {
        Setting<ColorSetting> setting =
                new Setting<>(
                        name,
                        new ColorSetting(color)
                );

        setting.getValue()
                .setRainbow(rainbow);

        themeEditorColors.add(setting);

        addThemeEditorElement(
                new ColorPickerElement(setting)
        );
    }

    private void addThemeEditorElement(
            AbstractElement element
    ) {
        element.setMonochrome(true);
        themeEditorElements.add(element);
    }

    private boolean saveThemeEditor() {
        if (editingTheme == null)
            return false;

        editingTheme.colorMode =
                themeEditorColorMode
                        .getValue()
                        .name();

        editingTheme.colorSpeed =
                themeEditorColorSpeed
                        .getValue();

        editingTheme.hudStyle =
                themeEditorHudStyle
                        .getValue()
                        .name();

        editingTheme.blurOpacity =
                themeEditorBlurOpacity
                        .getValue();

        editingTheme.blurStrength =
                themeEditorBlurStrength
                        .getValue();

        editingTheme.primaryColor =
                themeEditorColors
                        .get(0)
                        .getValue()
                        .getStoredColor();

        editingTheme.primaryRainbow =
                themeEditorColors
                        .get(0)
                        .getValue()
                        .isRainbow();

        editingTheme.secondaryColor =
                themeEditorColors
                        .get(1)
                        .getValue()
                        .getStoredColor();

        editingTheme.secondaryRainbow =
                themeEditorColors
                        .get(1)
                        .getValue()
                        .isRainbow();

        editingTheme.panelColor =
                themeEditorColors
                        .get(2)
                        .getValue()
                        .getStoredColor();

        editingTheme.panelRainbow =
                themeEditorColors
                        .get(2)
                        .getValue()
                        .isRainbow();

        editingTheme.textColor =
                themeEditorColors
                        .get(3)
                        .getValue()
                        .getStoredColor();

        editingTheme.textRainbow =
                themeEditorColors
                        .get(3)
                        .getValue()
                        .isRainbow();

        editingTheme.secondaryTextColor =
                themeEditorColors
                        .get(4)
                        .getValue()
                        .getStoredColor();

        editingTheme.secondaryTextRainbow =
                themeEditorColors
                        .get(4)
                        .getValue()
                        .isRainbow();

        editingTheme.blurColor =
                themeEditorColors
                        .get(5)
                        .getValue()
                        .getStoredColor();

        editingTheme.blurRainbow =
                themeEditorColors
                        .get(5)
                        .getValue()
                        .isRainbow();

        boolean saved =
                Managers.THEMES.save(
                        editingTheme
                );

        if (
                saved
                        && editingTheme.name
                        .equalsIgnoreCase(
                                Objects.toString(
                                        Managers.THEMES
                                                .getActiveThemeName(),
                                        ""
                                )
                        )
        ) {
            Managers.THEMES.apply(
                    editingTheme
            );
        }

        return saved;
    }

    private void renderThemeEditor(
            DrawContext context,
            int mouseX,
            int mouseY,
            float x,
            float y,
            float width,
            float height,
            float delta
    ) {
        Color accent =
                Managers.THEMES.getAccentColor();

        InterfaceStyle.drawCard(
                context.getMatrices(),
                x,
                y,
                width,
                height,
                14f,
                false,
                true
        );

        FontRenderers.sf_bold_mini.drawString(
                context.getMatrices(),
                "THEME EDITOR",
                x + 16,
                y + 15,
                new Color(
                        220,
                        215,
                        228
                ).getRGB()
        );

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "Colors, animation and blur",
                x + 16,
                y + 31,
                new Color(
                        123,
                        116,
                        138
                ).getRGB()
        );

        int backX =
                (int)
                        (
                                x
                                        + width
                                        - 112
                        );

        int saveX =
                (int)
                        (
                                x
                                        + width
                                        - 59
                        );

        boolean backHovered =
                Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        backX,
                        (int) y + 12,
                        47,
                        28
                );

        boolean saveHovered =
                Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        saveX,
                        (int) y + 12,
                        47,
                        28
                );

        Render2DEngine.drawRound(
                context.getMatrices(),
                backX,
                y + 12,
                47,
                28,
                8,
                backHovered
                        ? new Color(
                        50,
                        45,
                        60
                )
                        : new Color(
                        28,
                        24,
                        34
                )
        );

        FontRenderers.sf_medium_mini.drawCenteredString(
                context.getMatrices(),
                "BACK",
                backX + 23.5f,
                y + 21,
                backHovered
                        ? Color.WHITE.getRGB()
                        : new Color(
                        172,
                        164,
                        186
                ).getRGB()
        );

        Render2DEngine.drawRound(
                context.getMatrices(),
                saveX,
                y + 12,
                47,
                28,
                8,
                saveHovered
                        ? Color.WHITE
                        : new Color(
                        220,
                        216,
                        225
                )
        );

        FontRenderers.sf_medium_mini.drawCenteredString(
                context.getMatrices(),
                "SAVE",
                saveX + 23.5f,
                y + 21,
                new Color(
                        5,
                        4,
                        8
                ).getRGB()
        );

        float listX =
                x + 13;

        float listY =
                y + 50;

        float listWidth =
                width - 26;

        float viewportHeight =
                Math.max(
                        0f,
                        height - 63f
                );

        float contentHeight =
                layoutThemeEditor(
                        listX,
                        listY,
                        listWidth,
                        0f
                );

        themeEditorScroll =
                Math.clamp(
                        themeEditorScroll,
                        0,
                        Math.max(
                                0,
                                (int)
                                        Math.ceil(
                                                contentHeight
                                                        - viewportHeight
                                        )
                        )
                );

        layoutThemeEditor(
                listX,
                listY,
                listWidth,
                -themeEditorScroll
        );

        Render2DEngine.addWindow(
                context.getMatrices(),
                listX,
                listY,
                listX + listWidth,
                listY + viewportHeight,
                1f
        );

        for (
                AbstractElement element
                : themeEditorElements
        ) {
            if (!element.isVisible())
                continue;

            Render2DEngine.drawRound(
                    context.getMatrices(),
                    element.getX() - 3,
                    element.getY() - 2,
                    element.getWidth() + 6,
                    element.getHeight() + 4,
                    7f,
                    new Color(
                            13,
                            11,
                            19,
                            235
                    )
            );

            element.render(
                    context,
                    mouseX,
                    mouseY,
                    delta
            );
        }

        Render2DEngine.popWindow();

        FontRenderers.sf_medium_mini.drawString(
                context.getMatrices(),
                "Saved in configs/themes",
                x + 16,
                y + height - 17,
                new Color(
                        accent.getRed(),
                        accent.getGreen(),
                        accent.getBlue()
                ).getRGB()
        );
    }

    private float layoutThemeEditor(
            float x,
            float y,
            float width,
            float scrollOffset
    ) {
        float offset = 0f;

        for (
                AbstractElement element
                : themeEditorElements
        ) {
            if (!element.isVisible())
                continue;

            element.setOffsetY(0f);

            element.setX(x);

            element.setY(
                    y
                            + offset
                            + scrollOffset
            );

            element.setWidth(width);

            element.setHeight(13f);

            if (element instanceof ColorPickerElement picker) {
                element.setHeight(
                        picker.getHeight()
                );
            } else if (element instanceof SliderElement) {
                element.setHeight(18f);
            }

            if (element instanceof ModeElement mode) {
                mode.setWHeight(13);

                element.setHeight(
                        mode.isOpen()
                                ? 13
                                + mode.getSetting()
                                .getModes()
                                .length
                                * 12
                                : 13
                );
            }

            offset +=
                    element.getHeight()
                            + 5f;
        }

        return offset;
    }

    private boolean handleThemeEditorClick(
            double mouseX,
            double mouseY,
            float x,
            float y,
            float width,
            float height,
            int button
    ) {
        int backX =
                (int)
                        (
                                x
                                        + width
                                        - 112
                        );

        int saveX =
                (int)
                        (
                                x
                                        + width
                                        - 59
                        );

        if (
                button == 0
                        && Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        backX,
                        (int) y + 12,
                        47,
                        28
                )
        ) {
            closeThemeEditor(true);
            return true;
        }

        if (
                button == 0
                        && Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        saveX,
                        (int) y + 12,
                        47,
                        28
                )
        ) {
            saveThemeEditor();
            return true;
        }

        float top =
                y + 50;

        float bottom =
                y + height - 12;

        if (
                mouseY < top
                        || mouseY > bottom
        ) {
            return Render2DEngine.isHovered(
                    mouseX,
                    mouseY,
                    x,
                    y,
                    width,
                    height
            );
        }

        for (
                AbstractElement element
                : themeEditorElements
        ) {
            if (
                    element.isVisible()
                            && Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            element.getX(),
                            element.getY(),
                            element.getWidth(),
                            element.getHeight()
                    )
            ) {
                element.mouseClicked(
                        (int) mouseX,
                        (int) mouseY,
                        button
                );

                return true;
            }
        }

        return Render2DEngine.isHovered(
                mouseX,
                mouseY,
                x,
                y,
                width,
                height
        );
    }

    private boolean closeThemeEditor(
            boolean save
    ) {
        if (editingTheme == null)
            return true;

        if (
                save
                        && !saveThemeEditor()
        ) {
            return false;
        }

        themeEditorElements
                .forEach(
                        AbstractElement::onClose
                );

        editingTheme = null;

        themeEditorElements.clear();
        themeEditorColors.clear();

        return true;
    }

    private boolean handleThemeTabClick(
            double mouseX,
            double mouseY,
            float x,
            float y,
            float width,
            float height,
            int button
    ) {
        int plusX =
                (int)
                        (
                                x
                                        + width
                                        - 51
                        );

        int plusY =
                (int)
                        (
                                y + 13
                        );

        if (
                button == 0
                        && Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        plusX,
                        plusY,
                        35,
                        29
                )
        ) {
            Managers.THEMES.createFromCurrent();
            return true;
        }

        List<ThemeManager.ThemePreset> themes =
                Managers.THEMES.getThemes();

        int columns =
                width >= 600
                        ? 2
                        : 1;

        float gap = 12f;

        float cardWidth =
                (
                        width
                                - 28f
                                - gap
                                * (
                                columns - 1
                        )
                )
                        / columns;

        float cardsTop =
                y + 86;

        float cardsBottom =
                y + height - 13;

        for (
                int i = 0;
                i < themes.size();
                i++
        ) {
            ThemeManager.ThemePreset theme =
                    themes.get(i);

            int row =
                    i / columns;

            int column =
                    i % columns;

            float cardX =
                    x + 14
                            + column
                            * (
                            cardWidth
                                    + gap
                    );

            float cardY =
                    cardsTop
                            + row
                            * (
                            82f
                                    + gap
                    )
                            - themeScroll;

            if (
                    mouseY < cardsTop
                            || mouseY > cardsBottom
                            || !Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            cardX,
                            cardY,
                            cardWidth,
                            82f
                    )
            ) {
                continue;
            }

            if (button == 1) {
                beginThemeEdit(theme);
                return true;
            }

            if (button != 0)
                return true;

            int deleteX =
                    (int)
                            (
                                    cardX
                                            + cardWidth
                                            - 28
                            );

            if (
                    Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            deleteX,
                            (int) cardY + 9,
                            18,
                            18
                    )
            ) {
                Managers.THEMES.delete(
                        theme.name
                );
            } else {
                Managers.THEMES.apply(
                        theme
                );
            }

            return true;
        }

        return Render2DEngine.isHovered(
                mouseX,
                mouseY,
                x,
                y,
                width,
                height
        );
    }

    private void resetModuleScroll() {
        categoryPanels
                .values()
                .forEach(
                        panel ->
                                panel.moduleOffset = 0f
                );

        if (searchPanel != null)
            searchPanel.moduleOffset = 0f;
    }

    /*
     * ============================================================
     * MOUSE
     * ============================================================
     */

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        /*
         * Search.
         */
        int searchWidth =
                getSearchWidth();

        int searchX =
                getSearchX();

        int searchY =
                guiY + 17;

        if (
                button == 0
                        && Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        searchX,
                        searchY,
                        searchWidth,
                        32
                )
        ) {
            if (
                    !moduleSearch.isEmpty()
                            && mouseX
                            >= searchX
                            + searchWidth
                            - 25
            ) {
                moduleSearch = "";
                resetModuleScroll();
            }

            if (!closeThemeEditor(true))
                return true;

            themesSelected = false;
            searchFocused = true;

            SearchBar.listening = false;

            return true;
        }

        searchFocused = false;

        /*
         * Drag header.
         */
        if (
                button == 0
                        && Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        guiX,
                        guiY,
                        guiWidth,
                        62
                )
        ) {
            draggingWindow = true;

            dragOffsetX =
                    mouseX - guiX;

            dragOffsetY =
                    mouseY - guiY;

            return true;
        }

        /*
         * Sidebar.
         */
        int sidebarWidth =
                getSidebarWidth();

        int visibleTabs =
                getVisibleTabs();

        int tabTop =
                getTabTop();

        int tabX =
                guiX + 18;

        int tabWidth =
                sidebarWidth - 20;

        for (
                int i = categoryScroll;
                i < Math.min(
                        getSidebarItemCount(),
                        categoryScroll
                                + visibleTabs
                );
                i++
        ) {
            int tabY =
                    tabTop
                            + (
                            i - categoryScroll
                    ) * 31;

            if (
                    !Render2DEngine.isHovered(
                            mouseX,
                            mouseY,
                            tabX,
                            tabY,
                            tabWidth,
                            27
                    )
            ) {
                continue;
            }

            /*
             * HUD Studio.
             */
            if (i == categories.size()) {
                if (!closeThemeEditor(true))
                    return true;

                themesSelected = false;
                settingsModule = null;
                moduleSearch = "";

                resetModuleScroll();

                SearchBar.listening = false;

                Module hudEditor =
                        getHudEditorModule();

                if (hudEditor != null) {
                    if (
                            mc.player == null
                                    || mc.world == null
                    ) {
                        mc.setScreen(
                                HudEditorGui
                                        .getHudGui()
                        );
                    } else {
                        hudEditor.enable();
                    }
                }

                return true;
            }

            /*
             * Themes.
             */
            if (
                    i
                            == categories.size()
                            + 1
            ) {
                if (!closeThemeEditor(true))
                    return true;

                themesSelected = true;
                settingsModule = null;
                moduleSearch = "";

                resetModuleScroll();

                SearchBar.listening = false;

                return true;
            }

            /*
             * Category.
             */
            if (!closeThemeEditor(true))
                return true;

            selectedCategory =
                    categories.get(i);

            themesSelected = false;
            settingsModule = null;

            SearchBar.listening = false;

            return true;
        }

        /*
         * Themes interaction.
         */
        if (themesSelected) {
            int contentX =
                    guiX
                            + sidebarWidth
                            + 32;

            int contentWidth =
                    guiWidth
                            - sidebarWidth
                            - 48;

            if (editingTheme != null) {
                return handleThemeEditorClick(
                        mouseX,
                        panelMouseY(mouseY),
                        contentX,
                        guiY + 139,
                        contentWidth,
                        Math.max(
                                100,
                                guiHeight - 149
                        ),
                        button
                )
                        || super.mouseClicked(
                        mouseX,
                        mouseY,
                        button
                );
            }

            return handleThemeTabClick(
                    mouseX,
                    panelMouseY(mouseY),
                    contentX,
                    guiY + 139,
                    contentWidth,
                    Math.max(
                            100,
                            guiHeight - 149
                    ),
                    button
            )
                    || super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        /*
         * Settings panel.
         */
        int inputMouseY =
                panelMouseY(mouseY);

        ModuleButton settingsButton =
                findSettingsButton(
                        settingsModule
                );

        if (
                settingsButton != null
                        && Render2DEngine.isHovered(
                        mouseX,
                        inputMouseY,
                        settingsPaneDrawX,
                        settingsPaneY,
                        settingsPaneWidth,
                        settingsPaneHeight
                )
        ) {
            if (
                    button == 0
                            && settingsButton
                            .isSettingsCloseHovered(
                                    (int) mouseX,
                                    inputMouseY,
                                    settingsPaneDrawX,
                                    settingsPaneY,
                                    settingsPaneWidth
                            )
            ) {
                settingsModule = null;
                return true;
            }

            float settingsTop =
                    settingsPaneY + 45f;

            float settingsBottom =
                    settingsPaneY
                            + settingsPaneHeight
                            - 70f;

            if (
                    inputMouseY >= settingsTop
                            && inputMouseY <= settingsBottom
            ) {
                settingsButton.mouseClickedSettings(
                        (int) mouseX,
                        inputMouseY,
                        button
                );
            }

            return true;
        }

        /*
         * Module panel.
         */
        Category activePanel =
                getActivePanel();

        if (activePanel != null) {
            activePanel.mouseClicked(
                    (int) mouseX,
                    panelMouseY(mouseY),
                    button
            );
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        draggingWindow = false;

        if (editingTheme != null) {
            themeEditorElements.forEach(
                    element ->
                            element.mouseReleased(
                                    (int) mouseX,
                                    panelMouseY(mouseY),
                                    button
                            )
            );
        }

        ModuleButton settingsButton =
                findSettingsButton(
                        settingsModule
                );

        if (settingsButton != null) {
            settingsButton.mouseReleasedSettings(
                    (int) mouseX,
                    panelMouseY(mouseY),
                    button
            );
        }

        Category activePanel =
                getActivePanel();

        if (activePanel != null) {
            activePanel.mouseReleased(
                    (int) mouseX,
                    panelMouseY(mouseY),
                    button
            );
        }

        return super.mouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        /*
         * Sidebar scroll.
         */
        int sidebarWidth =
                getSidebarWidth();

        int visibleTabs =
                getVisibleTabs();

        if (
                Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        guiX,
                        guiY + 70,
                        sidebarWidth + 8,
                        guiHeight - 78
                )
                        && getSidebarItemCount()
                        > visibleTabs
        ) {
            categoryScroll =
                    Math.clamp(
                            categoryScroll
                                    - (int)
                                    Math.signum(
                                            verticalAmount
                                    ),
                            0,
                            Math.max(
                                    0,
                                    getSidebarItemCount()
                                            - visibleTabs
                            )
                    );

            return true;
        }

        /*
         * Themes.
         */
        if (
                themesSelected
                        && Render2DEngine.isHovered(
                        mouseX,
                        mouseY,
                        guiX
                                + sidebarWidth
                                + 32,
                        guiY + 139,
                        guiWidth
                                - sidebarWidth
                                - 48,
                        Math.max(
                                100,
                                guiHeight - 149
                        )
                )
        ) {
            if (editingTheme != null) {
                float viewportHeight =
                        Math.max(
                                0f,
                                Math.max(
                                        100,
                                        guiHeight - 149
                                ) - 63f
                        );

                float contentHeight =
                        layoutThemeEditor(
                                0,
                                0,
                                guiWidth
                                        - sidebarWidth
                                        - 74,
                                0f
                        );

                themeEditorScroll =
                        Math.clamp(
                                themeEditorScroll
                                        - (int)
                                        Math.round(
                                                verticalAmount
                                                        * 28f
                                        ),
                                0,
                                Math.max(
                                        0,
                                        (int)
                                                Math.ceil(
                                                        contentHeight
                                                                - viewportHeight
                                                )
                                )
                        );

                return true;
            }

            int contentWidth =
                    guiWidth
                            - sidebarWidth
                            - 48;

            float cardsViewport =
                    Math.max(
                            0,
                            Math.max(
                                    100,
                                    guiHeight - 149
                            ) - 99
                    );

            List<ThemeManager.ThemePreset> themes =
                    Managers.THEMES.getThemes();

            int columns =
                    contentWidth >= 600
                            ? 2
                            : 1;

            int rows =
                    (
                            themes.size()
                                    + columns
                                    - 1
                    )
                            / columns;

            float contentHeight =
                    rows == 0
                            ? 0
                            : rows * 82f
                            + Math.max(
                            0,
                            rows - 1
                    ) * 12f;

            themeScroll =
                    Math.clamp(
                            themeScroll
                                    - (int)
                                    Math.round(
                                            verticalAmount
                                                    * 28f
                                    ),
                            0,
                            Math.max(
                                    0,
                                    (int)
                                            Math.ceil(
                                                    contentHeight
                                                            - cardsViewport
                                            )
                            )
                    );

            return true;
        }

        /*
         * Settings.
         */
        if (
                settingsModule != null
                        && Render2DEngine.isHovered(
                        mouseX,
                        panelMouseY(mouseY),
                        settingsPaneDrawX,
                        settingsPaneY,
                        settingsPaneWidth,
                        settingsPaneHeight
                )
        ) {
            ModuleButton settingsButton =
                    findSettingsButton(
                            settingsModule
                    );

            if (settingsButton != null) {
                settingsButton.scrollSettings(
                        verticalAmount,
                        settingsPaneHeight,
                        true
                );
            }

            return true;
        }

        /*
         * Module list.
         */
        Category activePanel =
                getActivePanel();

        if (activePanel != null) {
            activePanel.setModuleOffset(
                    (float)
                            (
                                    verticalAmount
                                            * 5D
                            ),
                    (float) mouseX,
                    panelMouseY(mouseY)
            );
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                horizontalAmount,
                verticalAmount
        );
    }

    /*
     * ============================================================
     * KEYBOARD
     * ============================================================
     */

    @Override
    public boolean charTyped(
            char chr,
            int modifiers
    ) {
        if (searchFocused) {
            if (
                    StringHelper.isValidChar(chr)
                            && moduleSearch.length()
                            < 40
            ) {
                moduleSearch += chr;
                resetModuleScroll();
            }

            return true;
        }

        Category activePanel =
                getActivePanel();

        if (activePanel != null) {
            activePanel.charTyped(
                    chr,
                    modifiers
            );
        }

        if (editingTheme != null) {
            themeEditorElements.forEach(
                    element ->
                            element.charTyped(
                                    chr,
                                    modifiers
                            )
            );
        }

        ModuleButton settingsButton =
                findSettingsButton(
                        settingsModule
                );

        if (settingsButton != null) {
            settingsButton.charTypedSettings(
                    chr,
                    modifiers
            );
        }

        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (searchFocused) {
            if (
                    keyCode == GLFW.GLFW_KEY_ESCAPE
                            || keyCode == GLFW.GLFW_KEY_ENTER
            ) {
                searchFocused = false;
                return true;
            }

            if (
                    keyCode
                            == GLFW.GLFW_KEY_BACKSPACE
                            && !moduleSearch.isEmpty()
            ) {
                moduleSearch =
                        moduleSearch.substring(
                                0,
                                moduleSearch.length() - 1
                        );

                resetModuleScroll();

                return true;
            }

            if (
                    keyCode
                            == GLFW.GLFW_KEY_DELETE
            ) {
                moduleSearch = "";
                resetModuleScroll();

                return true;
            }
        } else if (
                keyCode
                        == GLFW.GLFW_KEY_SLASH
        ) {
            if (!closeThemeEditor(true))
                return true;

            themesSelected = false;
            searchFocused = true;

            SearchBar.listening = false;

            return true;
        }

        /*
         * Theme editor escape.
         */
        if (
                keyCode
                        == GLFW.GLFW_KEY_ESCAPE
                        && editingTheme != null
        ) {
            if (!closeThemeEditor(true))
                return true;

            return true;
        }

        /*
         * Settings escape.
         */
        if (
                keyCode
                        == GLFW.GLFW_KEY_ESCAPE
                        && settingsModule != null
        ) {
            settingsModule = null;
            return true;
        }

        Category activePanel =
                getActivePanel();

        if (activePanel != null) {
            activePanel.keyTyped(
                    keyCode
            );
        }

        if (editingTheme != null) {
            themeEditorElements.forEach(
                    element ->
                            element.keyTyped(
                                    keyCode
                            )
            );
        }

        ModuleButton settingsButton =
                findSettingsButton(
                        settingsModule
                );

        if (settingsButton != null) {
            settingsButton.keyTypedSettings(
                    keyCode
            );
        }

        if (
                keyCode
                        == GLFW.GLFW_KEY_ESCAPE
        ) {
            close = false;
            imageDirection = false;
            imageAnimation.reset();

            return super.keyPressed(
                    keyCode,
                    scanCode,
                    modifiers
            );
        }

        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers
        );
    }

    @Override
    public void removed() {
        close = false;

        draggingWindow = false;
        searchFocused = false;

        SearchBar.listening = false;

        categoryPanels
                .values()
                .forEach(
                        Category::onClose
                );

        if (searchPanel != null)
            searchPanel.onClose();

        closeThemeEditor(true);

        ModuleButton settingsButton =
                findSettingsButton(
                        settingsModule
                );

        if (settingsButton != null) {
            settingsButton
                    .getElements()
                    .forEach(
                            AbstractElement::onClose
                    );
        }

        super.removed();
    }
}