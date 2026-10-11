package com.saunhardy.createringtoncurrency.client.ponder;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.saunhardy.createringtoncurrency.block.DecorativeATMBlock;
import net.createmod.ponder.api.registration.IndexExclusionHelper;
import net.createmod.ponder.api.registration.MultiTagBuilder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.NotNull;

public final class CurrencyPonderPlugin implements PonderPlugin {
    public static final ResourceLocation ECONOMY =
            ResourceLocation.fromNamespaceAndPath(CreateringtonCurrency.MODID, "economy");

    private CurrencyPonderPlugin() {}

    public static void register() {
        PonderIndex.addPlugin(new CurrencyPonderPlugin());
    }

    @Override
    public @NotNull String getModId() {
        return CreateringtonCurrency.MODID;
    }

    @Override
    public void registerScenes(@NotNull PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CreateringtonCurrency.DEPOSITOR_TERMINAL_BLOCK.getId())
                .addStoryBoard("depositor_terminal/payment", DepositorScenes::payment)
                .addStoryBoard("depositor_terminal/storage", DepositorScenes::storage)
                .addStoryBoard("depositor_terminal/redstone", DepositorScenes::redstone);

        for (DeferredBlock<DecorativeATMBlock> atm : CreateringtonCurrency.DECORATIVE_ATMS) {
            helper.addStoryBoard(atm.getId(), "atm", (scene, util) -> ATMScenes.banking(scene, util, atm.get()));
        }
    }

    @Override
    public void registerTags(@NotNull PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(ECONOMY)
                .addToIndex()
                .item(CreateringtonCurrency.MOD_ICON.get(), true, false)
                .title("Createrington Currency")
                .description("Blocks that turn bills into payments and bank balance")
                .register();

        MultiTagBuilder.Tag<ResourceLocation> economy = helper.addToTag(ECONOMY);
        CreateringtonCurrency.DECORATIVE_ATMS.forEach(atm -> economy.add(atm.getId()));
        economy.add(CreateringtonCurrency.DEPOSITOR_TERMINAL_BLOCK.getId());
    }

    @Override
    public void indexExclusions(@NotNull IndexExclusionHelper helper) {
        helper.excludeBlockVariants(DecorativeATMBlock.class, CreateringtonCurrency.ATM_BLUE_BLOCK.get());
    }
}
