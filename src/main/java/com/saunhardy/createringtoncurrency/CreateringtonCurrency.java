package com.saunhardy.createringtoncurrency;

import com.saunhardy.createringtoncurrency.mobdrops.MobDrops;
import com.saunhardy.createringtoncurrency.enchantment.ModEnchantments;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(CreateringtonCurrency.MODID)
public class CreateringtonCurrency {
    public static final String MODID = "createringtoncurrency";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> BILL_1    = ITEMS.register("bill_1",    () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> BILL_5    = ITEMS.register("bill_5",    () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> BILL_10   = ITEMS.register("bill_10",   () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> BILL_20   = ITEMS.register("bill_20",   () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> BILL_50   = ITEMS.register("bill_50",   () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> BILL_100  = ITEMS.register("bill_100",  () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> BILL_500  = ITEMS.register("bill_500",  () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> BILL_1000 = ITEMS.register("bill_1000", () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> MOD_ICON = ITEMS.register("mod_icon",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .alwaysEat().nutrition(1).saturationMod(2f).build())));

    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB =
            CREATIVE_MODE_TABS.register("mod_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createringtoncurrency.mod_tab"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> MOD_ICON.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(MOD_ICON.get());
                        output.accept(BILL_1.get());
                        output.accept(BILL_5.get());
                        output.accept(BILL_10.get());
                        output.accept(BILL_20.get());
                        output.accept(BILL_50.get());
                        output.accept(BILL_100.get());
                        output.accept(BILL_500.get());
                        output.accept(BILL_1000.get());
                    })
                    .build());

    public CreateringtonCurrency() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        ModEnchantments.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            MinecraftForge.EVENT_BUS.register(MobDrops.class);
            MinecraftForge.EVENT_BUS.register(MoneyCommands.class);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }
}
