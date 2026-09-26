package com.saunhardy.createringtoncurrency.client;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.renderer.Rect2i;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(CreateringtonCurrency.MODID)
public final class JadeCompat implements IWailaPlugin {
    private static final int GAP = 3;

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addBeforeRenderCallback((box, tooltip, graphics, accessor) -> {
            Element.DOMRect strip = VotePopup.overlayRect();
            if (strip == null) return false;
            Rect2i rect = tooltip.rect;
            int minY = (int) Math.ceil(strip.bottom) + GAP;
            boolean overlapsX = rect.getX() < strip.right && rect.getX() + rect.getWidth() > strip.left;
            if (overlapsX && rect.getY() < minY) rect.setY(minY);
            return false;
        });
    }
}
