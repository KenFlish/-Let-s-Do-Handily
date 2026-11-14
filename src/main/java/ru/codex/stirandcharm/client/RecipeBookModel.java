package ru.codex.stirandcharm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.BookModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** Uses Minecraft's real enchanting-table book geometry and UVs without resized cover cubes. */
final class RecipeBookModel {
    static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");
    private static final Quaternionf FROM_EXPORT = new Quaternionf().rotationZ(Mth.PI);
    private final BookModel model;
    private RecipeBookModel() {
        var root = BookModel.createBodyLayer().bakeRoot();
        // RecipeBook draws the interactive turning sheets; do not draw a second pair over them.
        root.getChild("flip_page1").visible = false;
        root.getChild("flip_page2").visible = false;
        model = new BookModel(root);
    }
    static RecipeBookModel bake() { return new RecipeBookModel(); }
    void setupAnim(float angle) { model.setupAnim(0, 0, 0, angle / 1.25F); }
    void render(PoseStack pose, VertexConsumer consumer, int light, int overlay) {
        pose.pushPose();
        pose.mulPose(FROM_EXPORT);
        // Map vanilla's +X page layout into the existing interactive page coordinates.
        pose.scale(-1, 1, 1);
        model.render(pose, consumer, light, overlay, -1);
        pose.popPose();
    }
}
