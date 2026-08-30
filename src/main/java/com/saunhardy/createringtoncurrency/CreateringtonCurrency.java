package com.saunhardy.createringtoncurrency;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import com.saunhardy.createringtoncurrency.block.DecorativeATMBlock;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlock;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlockEntity;
import com.saunhardy.createringtoncurrency.client.ClientOnlyHooks;
import com.saunhardy.createringtoncurrency.datagen.DataGenerators;
import com.saunhardy.createringtoncurrency.enchantment.ModEnchantmentEffects;
import com.saunhardy.createringtoncurrency.item.BankCardItem;
import com.saunhardy.createringtoncurrency.menu.ATMMenu;
import com.saunhardy.createringtoncurrency.menu.DepositorMenu;
import com.saunhardy.createringtoncurrency.mobdrops.MobDrops;
import com.saunhardy.createringtoncurrency.network.ATMNetworking;
import com.saunhardy.createringtoncurrency.network.DepositorNetworking;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.List;

@Mod(CreateringtonCurrency.MODID)
public class CreateringtonCurrency {
    public static final String MODID = "createringtoncurrency";
    public static final int[] DENOMINATIONS = {1000, 500, 100, 50, 20, 10, 5, 1};
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS   = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredItem<Item> BILL_1    = ITEMS.register("bill_1",    () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> BILL_5    = ITEMS.register("bill_5",    () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> BILL_10   = ITEMS.register("bill_10",   () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> BILL_20   = ITEMS.register("bill_20",   () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> BILL_50   = ITEMS.register("bill_50",   () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> BILL_100  = ITEMS.register("bill_100",  () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> BILL_500  = ITEMS.register("bill_500",  () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> BILL_1000 = ITEMS.register("bill_1000", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<BankCardItem> BANK_CARD = ITEMS.register("bank_card", () -> new BankCardItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.register("circuit_board", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> KEYPAD = ITEMS.register("keypad", () -> new Item(new Item.Properties().stacksTo(64)));

    private static final BlockBehaviour.Properties DECORATIVE_ATM_PROPS = BlockBehaviour.Properties.of()
            .strength(2.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .lightLevel(state -> state.getValue(DecorativeATMBlock.HALF) == DoubleBlockHalf.UPPER ? 8 : 0);

    public static final DeferredBlock<DecorativeATMBlock> ATM_BLUE_BLOCK = BLOCKS.register("atm_blue", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_BLUE_ITEM =
            ITEMS.register("atm_blue", () -> new BlockItem(ATM_BLUE_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<DecorativeATMBlock> ATM_GREEN_BLOCK = BLOCKS.register("atm_green", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_GREEN_ITEM =
            ITEMS.register("atm_green", () -> new BlockItem(ATM_GREEN_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<DecorativeATMBlock> ATM_PURPLE_BLOCK = BLOCKS.register("atm_purple", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_PURPLE_ITEM =
            ITEMS.register("atm_purple", () -> new BlockItem(ATM_PURPLE_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<DecorativeATMBlock> ATM_BLACK_BLOCK = BLOCKS.register("atm_black", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_BLACK_ITEM =
            ITEMS.register("atm_black", () -> new BlockItem(ATM_BLACK_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<DecorativeATMBlock> ATM_BRASS_BLOCK = BLOCKS.register("atm_brass", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_BRASS_ITEM =
            ITEMS.register("atm_brass", () -> new BlockItem(ATM_BRASS_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<DecorativeATMBlock> ATM_ANDESITE_BLOCK = BLOCKS.register("atm_andesite", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_ANDESITE_ITEM =
            ITEMS.register("atm_andesite", () -> new BlockItem(ATM_ANDESITE_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<DecorativeATMBlock> ATM_RED_BLOCK = BLOCKS.register("atm_red", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_RED_ITEM =
            ITEMS.register("atm_red", () -> new BlockItem(ATM_RED_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<DecorativeATMBlock> ATM_PINK_BLOCK = BLOCKS.register("atm_pink", () ->
            new DecorativeATMBlock(DECORATIVE_ATM_PROPS));
    public static final DeferredItem<BlockItem> ATM_PINK_ITEM =
            ITEMS.register("atm_pink", () -> new BlockItem(ATM_PINK_BLOCK.get(), new Item.Properties()));

    /** Every decorative ATM, in creative-tab and block-tag order. */
    public static final List<DeferredBlock<DecorativeATMBlock>> DECORATIVE_ATMS = List.of(
            ATM_BLUE_BLOCK, ATM_GREEN_BLOCK, ATM_PURPLE_BLOCK, ATM_BLACK_BLOCK,
            ATM_BRASS_BLOCK, ATM_ANDESITE_BLOCK, ATM_RED_BLOCK, ATM_PINK_BLOCK);

    public static final DeferredBlock<DepositorTerminalBlock> DEPOSITOR_TERMINAL_BLOCK = BLOCKS.register("depositor_terminal", () ->
            new DepositorTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
    public static final DeferredItem<BlockItem> DEPOSITOR_TERMINAL_ITEM =
            ITEMS.register("depositor_terminal", () -> new BlockItem(DEPOSITOR_TERMINAL_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepositorTerminalBlockEntity>> DEPOSITOR_TERMINAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("depositor_terminal", () ->
                    BlockEntityType.Builder.of(DepositorTerminalBlockEntity::new, DEPOSITOR_TERMINAL_BLOCK.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<ATMMenu>> ATM_MENU =
            MENUS.register("atm", () -> new MenuType<>(ATMMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<DepositorMenu>> DEPOSITOR_MENU =
            MENUS.register("depositor_terminal", () -> IMenuTypeExtension.create(DepositorMenu::fromBuf));

    public static final DeferredItem<Item> MOD_ICON =
            ITEMS.register("mod_icon", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("mod_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createringtoncurrency"))
                    .icon(() -> MOD_ICON.get().getDefaultInstance())
                    .displayItems((params, out) -> {
                        DECORATIVE_ATMS.forEach(atm -> out.accept(atm.get()));
                        out.accept(DEPOSITOR_TERMINAL_BLOCK.get());
                        out.accept(BILL_1.get());
                        out.accept(BILL_5.get());
                        out.accept(BILL_10.get());
                        out.accept(BILL_20.get());
                        out.accept(BILL_50.get());
                        out.accept(BILL_100.get());
                        out.accept(BILL_500.get());
                        out.accept(BILL_1000.get());
                        out.accept(BANK_CARD.get());
                        out.accept(CIRCUIT_BOARD.get());
                        out.accept(KEYPAD.get());
                    })
                    .build()
            );

    public CreateringtonCurrency(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        ModEnchantmentEffects.register(modEventBus);

        modEventBus.addListener(ATMNetworking::register);
        modEventBus.addListener(DepositorNetworking::register);
        modEventBus.addListener(CreateringtonCurrency::registerCapabilities);
        modEventBus.addListener(DataGenerators::gatherData);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Skip CRNet-backed API init in integrated singleplayer — the backend is only used in multiplayer.
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> {
            if (e.getServer().isDedicatedServer()) {
                CurrencyApi.init(e.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener(DepositorNetworking::onPlayerLogout);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientOnlyHooks::registerScreens);
            modEventBus.addListener(ClientOnlyHooks::registerKeyMappings);
            modEventBus.addListener(ClientOnlyHooks::registerBlockColors);
            modEventBus.addListener(ClientOnlyHooks::registerItemColors);
            NeoForge.EVENT_BUS.register(ClientOnlyHooks.class);

            if (ModList.get().isLoaded("create")) {
                NeoForge.EVENT_BUS.register(com.saunhardy.createringtoncurrency.client.DepositorShopOverlay.class);
            }
        }

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            NeoForge.EVENT_BUS.register(MobDrops.class);
            NeoForge.EVENT_BUS.register(MoneyCommands.class);
            NeoForge.EVENT_BUS.register(VoteCommand.class);
        }
        
        // Register Create integration if Create is loaded
        if (ModList.get().isLoaded("create")) {
            NeoForge.EVENT_BUS.register(com.saunhardy.createringtoncurrency.events.StockTickerIntegration.class);
            LOGGER.info("Create mod detected - Stock Ticker integration enabled");
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEPOSITOR_TERMINAL_BLOCK_ENTITY.get(),
                (be, side) -> be.getExtractOnlyStorage());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("CreateringtonCurrency: common setup");
    }
}
