package com.github.alexmodguy.backupbeds.mixins;

import com.github.alexmodguy.backupbeds.BackupBeds;
import com.github.alexmodguy.backupbeds.misc.ServerPlayerAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;
import java.util.Stack;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements ServerPlayerAccessor {

    @Shadow
    public abstract ServerLevel serverLevel();

    @Shadow
    public abstract float getRespawnAngle();

    @Shadow
    private boolean respawnForced;

    @Shadow public abstract ResourceKey<Level> getRespawnDimension();

    @Shadow public abstract void sendSystemMessage(Component component);

    private Stack<GlobalPos> beds = new Stack<>();
    private float lastValidRespawnAngle = 0.0F;
    private GlobalPos lastValidBedFound = null;

    public ServerPlayerMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    private void saveBedsToTag(CompoundTag tag){
        ListTag listTag = new ListTag();
        Iterator<GlobalPos> iterator = beds.iterator();
        while (iterator.hasNext()) {
            GlobalPos pos = iterator.next();
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).resultOrPartial(BackupBeds.LOGGER::error).ifPresent(listTag::add);
        }
        tag.put("BackupBeds", listTag);
    }

    private void readBedsFromTag(CompoundTag tag){
        beds.clear();
        if(tag.contains("BackupBeds")){
            ListTag tag1 = tag.getList("BackupBeds", 10);
            for(int i = 0; i < tag1.size(); ++i) {
                GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag1.getCompound(i)).resultOrPartial(BackupBeds.LOGGER::error).ifPresent(beds::push);
            }
        }
    }

    private void pruneBadBeds() {
        Iterator<GlobalPos> iterator = beds.iterator();
        while (iterator.hasNext()) {
            GlobalPos pos = iterator.next();
            if (!isValidRespawnPoint(pos, true)) {
                BackupBeds.LOGGER.debug("removed bed ({}, {}, {}, {})", pos.dimension(), pos.pos().getX(), pos.pos().getY(), pos.pos().getZ());
                iterator.remove();
            }
        }
    }

    private boolean isValidRespawnPoint(GlobalPos globalPos, boolean includeServerHangs) {
        float f = this.getRespawnAngle();
        ServerLevel dimensionLevel = this.serverLevel().getServer().getLevel(globalPos.dimension());
        if (dimensionLevel == null) {
            //skip, somethings wrong with the server
            BackupBeds.LOGGER.warn("level for dimension {} is null", globalPos.dimension().location());
            return includeServerHangs;
        }

        //fix consuming all the respawn anchor charges
        BlockState state = dimensionLevel.getBlockState(globalPos.pos());
        if (state.getBlock() instanceof RespawnAnchorBlock) {
            if(state.getValue(RespawnAnchorBlock.CHARGE) > 0 && RespawnAnchorBlock.canSetSpawn(dimensionLevel)){
                Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, dimensionLevel, globalPos.pos());
                return optional.isPresent();
            }else{
                return false;
            }
        }

        Optional<Vec3> respawnForwards = Player.findRespawnPositionAndUseSpawnBlock(dimensionLevel, globalPos.pos(), f, false, false);
        if (respawnForwards.isPresent()) {
            lastValidRespawnAngle = f;
            return true;
        }
        Optional<Vec3> respawnBackwards = Player.findRespawnPositionAndUseSpawnBlock(dimensionLevel, globalPos.pos(), f + 180, false, false);
        if (respawnBackwards.isPresent()) {
            lastValidRespawnAngle = f + 180;
            return true;
        }
        return false;
    }

    private void updateLastValidBed() {
        pruneBadBeds();
        GlobalPos found = null;
        while (found == null && beds.size() > 0) {
            GlobalPos pop = beds.pop();
            if (isValidRespawnPoint(pop, false)) {
                found = pop;
            }
        }
        if (found != null) {
            beds.push(found);
            lastValidBedFound = found;
        }
    }

    @Override
    public Stack<GlobalPos> getBackupBeds(){
        return beds;
    }

    private int getBedIndexInStack(ResourceKey<Level> resourceKey, BlockPos blockPos) {
        for(int i = 0; i < beds.size(); i++){
            GlobalPos globalPos = beds.get(i);
            if(globalPos.pos().equals(blockPos) && globalPos.dimension().equals(resourceKey)){
                return i;
            }
        }
        return -1;
    }

    @Inject(method = "Lnet/minecraft/server/level/ServerPlayer;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            cancellable = true,
            at = @At(
                    value = "TAIL"
            ))
    private void backupbeds_readAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        readBedsFromTag(tag);
    }

    @Inject(method = "Lnet/minecraft/server/level/ServerPlayer;addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(
                    value = "TAIL"
            ))
    private void backupbeds_saveAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        saveBedsToTag(tag);
    }

    @Inject(method = "Lnet/minecraft/server/level/ServerPlayer;restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At(
                    value = "TAIL"
            ))
    private void backupbeds_restoreFrom(ServerPlayer serverPlayer, boolean idk, CallbackInfo ci) {
        this.beds.clear();
        this.beds.addAll(((ServerPlayerAccessor)serverPlayer).getBackupBeds());
    }

    @Inject(method = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FZZ)V",
            cancellable = true,
            at = @At(
                    value = "HEAD"
            ))
    private void backupbeds_setRespawnPosition(ResourceKey<Level> resourceKey, BlockPos blockPos, float angle, boolean force, boolean sendMessage, CallbackInfo ci) {
        if (blockPos != null && !force) {
            pruneBadBeds();
            int i = getBedIndexInStack(resourceKey, blockPos);
            if (beds.size() < BackupBeds.CONFIG.backupBedTrackCount.get() && i == -1) {
                beds.push(GlobalPos.of(resourceKey, blockPos));
                lastValidBedFound = beds.peek();
                if(beds.size() >= 1){
                    this.sendSystemMessage(Component.translatable("message.backupbeds.respawn_set", beds.size(), BackupBeds.getNumberSuffix(beds.size())));
                }
            }else if(beds.size() >= BackupBeds.CONFIG.backupBedTrackCount.get()){
                this.sendSystemMessage(Component.translatable("message.backupbeds.respawn_points_exceeded").withStyle(ChatFormatting.RED));
            }else{
                i++;
                this.sendSystemMessage(Component.translatable("message.backupbeds.can_already_respawn_at", i, BackupBeds.getNumberSuffix(i)));
            }
            ci.cancel();
        }
    }

    @Inject(method = "Lnet/minecraft/server/level/ServerPlayer;getRespawnPosition()Lnet/minecraft/core/BlockPos;",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            ))
    private void backupbeds_getRespawnPosition(CallbackInfoReturnable<BlockPos> cir) {
        if (!respawnForced) {
            BlockPos defaultPos = cir.getReturnValue();
            ResourceKey<Level> defaultDim = getRespawnDimension() == null ? Level.OVERWORLD : getRespawnDimension();
            if (defaultPos != null && isValidRespawnPoint(GlobalPos.of(defaultDim, defaultPos), false)) {
                lastValidBedFound = null; // return to vanilla logic
                cir.setReturnValue(defaultPos); // normal respawn is valid... duh doy
            } else {
                if (lastValidBedFound == null || !isValidRespawnPoint(lastValidBedFound, false)) {
                    updateLastValidBed();
                }
                if (lastValidBedFound != null) {
                    cir.setReturnValue(lastValidBedFound.pos());
                }
            }
        }
    }

    @Inject(method = "Lnet/minecraft/server/level/ServerPlayer;getRespawnAngle()F",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            ))
    private void backupbeds_getRespawnAngle(CallbackInfoReturnable<Float> cir) {
        if (!respawnForced && lastValidBedFound != null) {
            cir.setReturnValue(lastValidRespawnAngle);
        }
    }

    @Inject(method = "Lnet/minecraft/server/level/ServerPlayer;getRespawnDimension()Lnet/minecraft/resources/ResourceKey;",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            ))
    private void backupbeds_getRespawnDimension(CallbackInfoReturnable<ResourceKey<Level>> cir) {
        if (!respawnForced) {
            if (lastValidBedFound == null || !isValidRespawnPoint(lastValidBedFound, false)) {
                updateLastValidBed();
            }
            if (lastValidBedFound != null) {
                cir.setReturnValue(lastValidBedFound.dimension());
            }
        }
    }
}
