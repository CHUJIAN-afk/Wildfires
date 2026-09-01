package first.wildfires.client.spell;

import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnFieldEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Invisible renderer for the server-side field entity which owns the constellation lifetime. */
public final class GalaxyHymnFieldRenderer extends EntityRenderer<GalaxyHymnFieldEntity> {

    public GalaxyHymnFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GalaxyHymnFieldEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
