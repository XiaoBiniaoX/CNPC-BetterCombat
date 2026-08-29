package org.cnpccombat.logic;

import com.google.gson.stream.JsonReader;
import net.bettercombat.api.AttributesContainer;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.WeaponAttributesHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.cnpccombat.CnpcCombat;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BetterCombat "攻击动画组" 注册表。
 *
 * <p>所谓动画组就是 {@code data/<namespace>/weapon_attributes/<name>.json} 定义的一组
 * {@link WeaponAttributes}，例如 {@code bettercombat:sword}（剑攻击动画组）、
 * {@code bettercombat:axe}（斧攻击动画组）。附属 mod 和数据包新增的组会自动被扫到，
 * 因为我们直接读 ResourceManager 而不是查 BetterCombat 的内部表。
 *
 * <p>为什么不复用 BetterCombat 的 {@code WeaponRegistry.containers}：
 * <ul>
 *   <li>它是包私有的（1.9.0 jar 里 flags 只有 ACC_STATIC），要反射；</li>
 *   <li>它<b>从不同步到客户端</b>，客户端 GUI 拿不到；</li>
 *   <li>它只保留 {@code Registries.ITEM.containsId} 通过的条目才注册进 registrations，
 *       抽象组（bettercombat:sword 这种没有同名物品的）拿不到解析结果。</li>
 * </ul>
 * 所以这里自己扫 JSON、自己解析 parent 继承链，服务端算好后用自己的 S2C 包发给客户端 GUI。
 *
 * <p><b>服务端安全：</b>本类不引用任何客户端类。扫描用的是服务端 ResourceManager
 * （{@code MinecraftServer.getResourceManager()}），客户端侧的列表纯靠网络包填充。
 */
public final class AnimationGroupRegistry {
    private static final String DATA_FOLDER = "weapon_attributes";

    /** 组 id -> 原始容器（含 parent 未解析）。服务端扫描结果。 */
    private static final Map<ResourceLocation, AttributesContainer> CONTAINERS = new ConcurrentHashMap<>();

    /** 组 id -> 解析后的属性。服务端用于近战动画组覆盖。 */
    private static final Map<String, WeaponAttributes> RESOLVED = new ConcurrentHashMap<>();

    /** 可供 GUI 选择的组 id 列表（已排序）。双端都有：服务端扫出来，客户端由网络包填。 */
    private static volatile List<String> AVAILABLE = Collections.emptyList();

    private AnimationGroupRegistry() {
    }

    // ---------------------------------------------------------------- 服务端扫描

    /**
     * 服务端：扫描所有数据包里的 weapon_attributes 定义并解析继承链。
     * 由 {@code ServerStartedEvent} 与 {@code AddReloadListenerEvent} 之后调用，
     * 所以 /reload 也能刷新（BetterCombat 本体只在服务器启动时读一次，我们比它更及时）。
     */
    public static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, AttributesContainer> found = new HashMap<>();

        Map<ResourceLocation, Resource> resources =
                resourceManager.listResources(DATA_FOLDER, path -> path.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation file = entry.getKey();
            ResourceLocation groupId = toGroupId(file);
            if (groupId == null) {
                continue;
            }
            try (BufferedReader reader = entry.getValue().openAsReader();
                 JsonReader json = new JsonReader(reader)) {
                AttributesContainer container = WeaponAttributesHelper.decode(json);
                if (container != null) {
                    found.put(groupId, container);
                }
            } catch (Exception e) {
                CnpcCombat.LOGGER.debug("无法解析攻击动画组定义 '{}': {}", file, e.toString());
            }
        }

        CONTAINERS.clear();
        CONTAINERS.putAll(found);

        // 解析继承链，只保留真正带 attacks 的组（没有攻击动作的组选了也没意义）。
        Map<String, WeaponAttributes> resolved = new HashMap<>();
        for (Map.Entry<ResourceLocation, AttributesContainer> entry : found.entrySet()) {
            WeaponAttributes attributes = resolve(entry.getKey(), entry.getValue());
            if (hasUsableAttacks(attributes)) {
                resolved.put(entry.getKey().toString(), attributes);
            }
        }

        RESOLVED.clear();
        RESOLVED.putAll(resolved);

        List<String> ids = new ArrayList<>(resolved.keySet());
        Collections.sort(ids);
        AVAILABLE = Collections.unmodifiableList(ids);

        // 加载结果不打日志（用户要求只保留 ERROR 级）。
    }

    /**
     * {@code bettercombat:weapon_attributes/sword.json} -> {@code bettercombat:sword}。
     * 与 BetterCombat 的 loadContainers 保持一致，但用 startsWith 而非全串 replace，
     * 避免命名空间里恰好含有 "weapon_attributes/" 时被误删。
     */
    @Nullable
    private static ResourceLocation toGroupId(ResourceLocation file) {
        String path = file.getPath();
        if (!path.startsWith(DATA_FOLDER + "/")) {
            return null;
        }
        path = path.substring(DATA_FOLDER.length() + 1);
        int dot = path.lastIndexOf('.');
        if (dot > 0) {
            path = path.substring(0, dot);
        }
        if (path.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(file.getNamespace() + ":" + path);
    }

    /**
     * 沿 parent 链自底向上收集，再用 BetterCombat 自己的 override 语义从根向下合并。
     * 复刻 {@code WeaponRegistry.resolveAttributes}，但读我们自己的 CONTAINERS，
     * 这样即使 BetterCombat 还没加载完也能算出结果。
     */
    @Nullable
    private static WeaponAttributes resolve(ResourceLocation groupId, AttributesContainer container) {
        try {
            List<WeaponAttributes> chain = new ArrayList<>();
            AttributesContainer current = container;
            // 防御 parent 成环（数据包写错就会死循环）。
            Set<String> visited = new java.util.HashSet<>();
            visited.add(groupId.toString());

            while (current != null) {
                chain.add(0, current.attributes());
                String parent = current.parent();
                if (parent == null || parent.isBlank() || !visited.add(parent)) {
                    break;
                }
                ResourceLocation parentId = ResourceLocation.tryParse(parent);
                current = parentId == null ? null : CONTAINERS.get(parentId);
            }

            WeaponAttributes result = new WeaponAttributes(0, null, null, false, null, null);
            for (WeaponAttributes step : chain) {
                if (step != null) {
                    result = WeaponAttributesHelper.override(result, step);
                }
            }
            // validate 要求每个 attack 都有 hitbox 和 animation。
            // 弓/弩类动画组（bow_two_handed_* 等）通常缺 hitbox 会在这里被拒 ——
            // 这正是我们要的：它们不是近战动画组，不该出现在选择列表里。
            // 因此这里静默跳过而不打日志，否则正常整合包会刷一堆无害警告。
            WeaponAttributesHelper.validate(result);
            return result;
        } catch (Exception e) {
            CnpcCombat.LOGGER.debug("跳过攻击动画组 '{}': {}", groupId, e.getMessage());
            return null;
        }
    }

    private static boolean hasUsableAttacks(@Nullable WeaponAttributes attributes) {
        if (attributes == null || attributes.attacks() == null || attributes.attacks().length == 0) {
            return false;
        }
        for (WeaponAttributes.Attack attack : attributes.attacks()) {
            if (attack != null && attack.animation() != null && !attack.animation().isBlank()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 查询

    /** 服务端：取解析好的动画组属性。客户端调用会返回 null（客户端只需要 id 列表）。 */
    @Nullable
    public static WeaponAttributes get(@Nullable String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return null;
        }
        return RESOLVED.get(groupId);
    }

    /** 该组 id 是否有效。 */
    public static boolean isValid(@Nullable String groupId) {
        return get(groupId) != null;
    }

    /** GUI 可选列表。 */
    public static List<String> available() {
        return AVAILABLE;
    }

    // ---------------------------------------------------------------- 客户端同步

    /** 服务端：要发给客户端的组 id 列表。 */
    public static List<String> exportIds() {
        return new ArrayList<>(AVAILABLE);
    }

    /**
     * 客户端：接收服务端发来的组 id 列表。
     * 只填 AVAILABLE（GUI 用），不填 RESOLVED（客户端不需要解析结果，
     * 动画播放走的是服务端下发的 animation id）。
     */
    public static void acceptFromServer(List<String> ids) {
        List<String> copy = new ArrayList<>(ids);
        Collections.sort(copy);
        AVAILABLE = Collections.unmodifiableList(copy);
    }

    /** 单机/局域网主机：客户端与服务端在同一 JVM，列表已经是现成的，不必清空。 */
    public static boolean hasData() {
        return !AVAILABLE.isEmpty();
    }

    /** 便于调试：组 id -> 类别名（category），用于 GUI 里显示更友好的名字。 */
    public static Map<String, String> categories() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : AVAILABLE) {
            WeaponAttributes attributes = RESOLVED.get(id);
            if (attributes != null && attributes.category() != null && !attributes.category().isBlank()) {
                out.put(id, attributes.category());
            }
        }
        return out;
    }
}
