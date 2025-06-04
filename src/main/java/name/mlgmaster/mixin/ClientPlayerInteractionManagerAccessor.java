package name.mlgmaster.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {

    @Invoker("interactItem")
    ActionResult invokeInteractItem(PlayerEntity player, Hand hand);

    @Invoker("interactBlock")
    ActionResult invokeInteractBlock(ClientPlayerEntity player, Hand hand,
            BlockHitResult hitResult);

    @Invoker("interactBlockInternal")
    ActionResult invokeInteractBlockInternal(ClientPlayerEntity player, Hand hand,
            BlockHitResult hitResult);
}
