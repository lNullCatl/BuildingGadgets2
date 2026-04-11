package com.direwolf20.buildinggadgets2.setup;

import com.direwolf20.buildinggadgets2.BuildingGadgets2;
import com.direwolf20.buildinggadgets2.client.KeyBindings;
// TODO(rendering-port): re-enable once the rendering pipeline is rewritten for 26.1.
//import com.direwolf20.buildinggadgets2.client.blockentityrenders.RenderBlockBER;
import com.direwolf20.buildinggadgets2.client.events.EventKeyInput;
// TODO(rendering-port): re-enable once RenderLevelLast is rewritten for 26.1.
//import com.direwolf20.buildinggadgets2.client.events.RenderLevelLast;
// TODO(rendering-port): re-import TemplateManagerGUI once its 3D preview pane is rewritten for 26.1.
//import com.direwolf20.buildinggadgets2.client.screen.TemplateManagerGUI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = BuildingGadgets2.MODID, value = Dist.CLIENT)
public class ClientSetup {
    public static void init(final FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(KeyBindings::onClientInput);

        //Register our Render Events Class
        // TODO(rendering-port): re-register RenderLevelLast once rewritten for 26.1.
        //NeoForge.EVENT_BUS.register(RenderLevelLast.class);
        NeoForge.EVENT_BUS.register(EventKeyInput.class);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // TODO(rendering-port): re-register TemplateManagerGUI once its 3D preview pane is rewritten for 26.1.
        //event.register(Registration.TemplateManager_Container.get(), TemplateManagerGUI::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        //Register Block Entity Renders
        // TODO(rendering-port): re-register RenderBlockBER once rewritten for 26.1.
        //event.registerBlockEntityRenderer(Registration.RenderBlock_BE.get(), RenderBlockBER::new);
    }

    @SubscribeEvent
    public static void registerTooltipFactory(RegisterClientTooltipComponentFactoriesEvent event) {
        //LOGGER.debug("Registering custom tooltip component factories for {}", Reference.MODID);
        //event.register(EventTooltip.CopyPasteTooltipComponent.Data.class, EventTooltip.CopyPasteTooltipComponent::new);
    }
}
