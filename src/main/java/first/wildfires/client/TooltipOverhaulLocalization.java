package first.wildfires.client;

import net.minecraft.client.resources.language.I18n;

import java.util.Locale;
import java.util.Map;

/** Client-only text replacements for Tooltip Overhaul's hard-coded config UI. */
public final class TooltipOverhaulLocalization {
    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("Animation style for the icon appearance.", "图标出现时使用的动画样式。"),
            Map.entry("Apply a custom frame overlay texture to every item. This option is overrided if the hovered itemstack has a custom frame. Write down the texture location (such as tooltipoverhaul:textures/overlay/silver_frame.png) or leave it empty. No frame overlays are applied globally by default.", "为所有物品应用自定义边框叠加纹理；有专属边框的物品会覆盖此设置。填写纹理位置（例如 tooltipoverhaul:textures/overlay/silver_frame.png），留空则默认不全局应用边框。"),
            Map.entry("Automatically reposition the preview panel (second panel) to the right side when there is insufficient space on the left. If set to false, the panel will always remain on the left.", "左侧空间不足时，自动将预览面板（第二面板）移至右侧；关闭后始终显示在左侧。"),
            Map.entry("Bedrock-like centering. When the tooltip fits neither to the right nor to the left of the cursor, centers it horizontally on the screen and places it above the cursor (or below if there is no room above), so it doesn't cover the hovered item.", "基岩版式居中：提示框无法放在鼠标左右两侧时，水平居中并放在鼠标上方（上方无空间时放在下方），避免遮住悬停物品。"),
            Map.entry("Color palette for CHAOS rarity (bright to dark). Must specify exactly 3 ARGB colors.", "混沌稀有度的配色（从亮到暗），必须恰好填写 3 个 ARGB 颜色。"),
            Map.entry("Color palette for COMMON rarity (bright to dark). Must specify exactly 3 ARGB colors.", "普通稀有度的配色（从亮到暗），必须恰好填写 3 个 ARGB 颜色。"),
            Map.entry("Color palette for EPIC rarity (bright to dark). Must specify exactly 3 ARGB colors.", "史诗稀有度的配色（从亮到暗），必须恰好填写 3 个 ARGB 颜色。"),
            Map.entry("Color palette for LEGENDARY rarity (bright to dark). Must specify exactly 3 ARGB colors.", "传说稀有度的配色（从亮到暗），必须恰好填写 3 个 ARGB 颜色。"),
            Map.entry("Color palette for RARE rarity (bright to dark). Must specify exactly 3 ARGB colors.", "稀有稀有度的配色（从亮到暗），必须恰好填写 3 个 ARGB 颜色。"),
            Map.entry("Color palette for tooltips without a stack. Must specify exactly 3 ARGB colors.", "无物品提示框的配色，必须恰好填写 3 个 ARGB 颜色。"),
            Map.entry("Color palette for UNCOMMON rarity (bright to dark). Must specify exactly 3 ARGB colors.", "优秀稀有度的配色（从亮到暗），必须恰好填写 3 个 ARGB 颜色。"),
            Map.entry("Corner style for the inner frame of the tooltip. Options: default (square), rounded (corner pixel removed), bevel (45 degree diagonal cut), inner (extra pixel in the inner corner), cut (2px chamfer), thick (solid triangular corner, top-right only), bracket (inner corner bracket), block (2x2 solid block corner), notch (rectangular inner offset), weld (reinforced inner corner), gem (hollow triangle). The notch style pairs with the matching notch background corner style.", "提示框内框边角样式。可选：default（方角）、rounded（去除角像素）、bevel（45 度切角）、inner（内角额外像素）、cut（2 像素倒角）、thick（实心三角角）、bracket（内角括号）、block（2x2 实心角）、notch（矩形内凹）、weld（强化角）、gem（空心三角）。notch 应与对应的背景边角样式搭配。"),
            Map.entry("Corner style for the outer background border of the tooltip. Options: default (vanilla notch), square (sharp 90 degree), rounded, notch (rectangular inner offset, matches the inner frame notch style).", "提示框外部背景边框的边角样式。可选：default（原版缺口）、square（90 度直角）、rounded、notch（矩形内凹，与内框 notch 样式匹配）。"),
            Map.entry("Default color palette for items whose rarity is not a vanilla one (COMMON/UNCOMMON/RARE/EPIC), such as custom rarities added by other mods. Must specify exactly 3 ARGB colors. Defaults to the same colors as the legendary palette.", "非原版稀有度（COMMON/UNCOMMON/RARE/EPIC）的默认配色，例如其他模组添加的稀有度；必须恰好填写 3 个 ARGB 颜色，默认与传说配色相同。"),
            Map.entry("Default inner overlay style for tooltips. Options: glint, static (monochrome), gradient.", "提示框默认内部叠加样式。可选：glint、static（单色）、gradient。"),
            Map.entry("Default tooltip background color in ARGB format (#AARRGGBB). Example: #F0010110.", "默认提示框背景颜色，格式为 ARGB（#AARRGGBB），例如 #F0010110。"),
            Map.entry("Disable the item icon.", "隐藏物品图标。"),
            Map.entry("Disable the divider line", "禁用分隔线。"),
            Map.entry("Disable tooltip scrolling", "禁用提示框滚动。"),
            Map.entry("Divider line color. Options: 'match_inner_frame_color', 'match_item_name_color' or a hex ARGB color (e.g., 0xA0EFEFEF).", "分隔线颜色。可选：match_inner_frame_color、match_item_name_color，或十六进制 ARGB 颜色（例如 0xA0EFEFEF）。"),
            Map.entry("Extra padding above the divider line", "分隔线上方的额外间距。"),
            Map.entry("Extra padding below the divider line", "分隔线下方的额外间距。"),
            Map.entry("Duration (in seconds) of the tooltip appear/disappear animation. Lower values are snappier.", "提示框出现/消失动画时长（秒）；数值越小越迅速。"),
            Map.entry("Enable the tooltip shadow.", "启用提示框阴影。"),
            Map.entry("Effects. Options: bubbles, cinder, crystals, echo, fireflies, galaxy, magic_orbs, speed_lines, nebula, spiral, white_dust, metal_shining, rim_light, ripples, sonar, stars. You can chain effects together, for example: 'white_dust, nebula'", "特效。可选：bubbles、cinder、crystals、echo、fireflies、galaxy、magic_orbs、speed_lines、nebula、spiral、white_dust、metal_shining、rim_light、ripples、sonar、stars。可组合多个特效，例如：white_dust, nebula。"),
            Map.entry("Horizontal offset (in pixels) for the second panel when a 3D preview is shown. Negative = left, positive = right.", "显示 3D 预览时第二面板的水平像素偏移；负数向左，正数向右。"),
            Map.entry("Horizontal offset (in pixels) for the tooltip. Negative = left, positive = right.", "提示框的水平像素偏移；负数向左，正数向右。"),
            Map.entry("Horizontal padding for the main panel.", "主面板的水平内边距。"),
            Map.entry("Horizontal padding for the main panel when there isn't a stack present.", "无物品时主面板的水平内边距。"),
            Map.entry("If PREVIEW_PANEL_MODEL is set to player_skin, renders the current player skin (true) or a placeholder skin (false).", "PREVIEW_PANEL_MODEL 为 player_skin 时，true 使用当前玩家皮肤，false 使用占位皮肤。"),
            Map.entry("Icon background type. Options: focus, void, slot, slot_border and glow", "图标背景类型。可选：focus、void、slot、slot_border、glow。"),
            Map.entry("Override vanilla tooltips even when no ItemStack is present (e.g., JEI category buttons or unsupported stacks). To disable custom tooltips for specific items, edit your custom_frames.json and set disableTooltip=true for those items (or tags).", "即使没有 ItemStack（如 JEI 分类按钮或不受支持的物品）也覆盖原版提示框。要禁用特定物品的自定义提示框，请在 custom_frames.json 中为其物品或标签设置 disableTooltip=true。"),
            Map.entry("Preview model to render in the second panel. Options: armor_stand, player_skin.", "第二面板使用的预览模型。可选：armor_stand、player_skin。"),
            Map.entry("Rating alignment. Options: left, middle, right.", "评级对齐方式。可选：left、middle、right。"),
            Map.entry("Render a 3D preview of armor pieces on the left side of the tooltip.", "在提示框左侧显示盔甲部件的 3D 预览。"),
            Map.entry("Render a 3D preview of tiered items (swords, axes, etc.) on the left side of the tooltip.", "在提示框左侧显示工具与武器的 3D 预览。"),
            Map.entry("Rotation speed multiplier for the tiered item preview in the second panel.", "第二面板中工具与武器预览的旋转速度倍率。"),
            Map.entry("Rotation speed of the icon.", "图标旋转速度。"),
            Map.entry("Show rating text.", "显示评级文本。"),
            Map.entry("Size (in pixels) of the second panel in the X axis when a 3D preview is shown.", "显示 3D 预览时第二面板的宽度（像素）。"),
            Map.entry("Size (in pixels) of the second panel in the Y axis when a 3D preview is shown.", "显示 3D 预览时第二面板的高度（像素）。"),
            Map.entry("Title alignment. Options: left, middle, right.", "标题对齐方式。可选：left、middle、right。"),
            Map.entry("Vertical offset (in pixels) for the second panel when a 3D preview is shown. Negative = up, positive = down.", "显示 3D 预览时第二面板的垂直像素偏移；负数向上，正数向下。"),
            Map.entry("Vertical offset (in pixels) for the tooltip. Negative = up, positive = down.", "提示框的垂直像素偏移；负数向上，正数向下。"),
            Map.entry("Vertical padding for the main panel.", "主面板的垂直内边距。"),
            Map.entry("Vertical padding for the main panel when there isn't a stack present.", "无物品时主面板的垂直内边距。"),
            Map.entry("Vignette entries for gradient overlays around the tooltip background. Each vignette must be written in parentheses in this exact order: (type, position, color, radius, extraPositionX, extraPositionY). Example: (circular, top_left, #FF567823, 0.4, 0, 0). You can define multiple vignettes by separating them with commas: (...), (...), (...). type = vignette shape (options: circular, hole). position = anchor on the tooltip (e.g. top_left, top_right, middle, right, bottom_left). color = ARGB hex in #AARRGGBB. radius = relative size factor (e.g. radius 0.4). extraPositionX / extraPositionY = additional pixel offset from the chosen position.", "提示框背景周围的渐变晕影条目。每个晕影必须按以下顺序用括号填写：(type, position, color, radius, extraPositionX, extraPositionY)。示例：(circular, top_left, #FF567823, 0.4, 0, 0)。可用逗号分隔多个条目。type 为形状（circular、hole），position 为锚点（如 top_left、top_right、middle、right、bottom_left），color 为 #AARRGGBB 的 ARGB 颜色，radius 为相对大小，extraPositionX/Y 为额外像素偏移。"),
            Map.entry("Animation played when a tooltip appears (on hover) and disappears. Options: none, fade, pop, rise, unfold, zoom, slide, swing, emerge, squash, card, shake.", "提示框出现（悬停）与消失时播放的动画。可选：none、fade、pop、rise、unfold、zoom、slide、swing、emerge、squash、card、shake。"),
            Map.entry("Appends the display name of the mod that adds the hovered item as the last tooltip line (blue italic), always visible in both survival and creative (previously visible only in creative mode).", "在提示框最后一行追加添加该物品的模组名称（蓝色斜体），生存与创造模式均显示。"),
            Map.entry("Always show the equipment comparison tooltip when hovering a comparable item, without needing to hold the compare key (Left Shift by default).", "悬停可对比物品时始终显示装备对比提示，不需要按住对比键（默认左 Shift）。")
    );
    private TooltipOverhaulLocalization() {
    }

    public static String localize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String description = DESCRIPTIONS.get(text);
        if (description != null) {
            return description;
        }

        String id = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        String categoryKey = "wildfires.tooltipoverhaul.category." + id;
        if (I18n.exists(categoryKey)) {
            return I18n.get(categoryKey);
        }

        String optionKey = "wildfires.tooltipoverhaul.option." + id;
        return I18n.exists(optionKey) ? I18n.get(optionKey) : text;
    }
}
