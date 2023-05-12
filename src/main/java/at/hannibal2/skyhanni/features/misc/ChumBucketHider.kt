package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.events.CheckRenderEntityEvent
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.utils.ItemUtils.name
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.onToggle
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityArmorStand
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class ChumBucketHider {
    private val config get() = SkyHanniMod.feature.fishing.chumBucketHider
    private val titleEntity = mutableListOf<Entity>()
    private val hiddenEntities = mutableListOf<Entity>()

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        reset()
    }

    @SubscribeEvent
    fun onCheckRender(event: CheckRenderEntityEvent<*>) {
        if (!LorenzUtils.inSkyBlock) return
        if (!config.enabled.get()) return

        val entity = event.entity
        if (entity !is EntityArmorStand) return

        if (entity in hiddenEntities) {
            event.isCanceled = true
            return
        }

        val name = entity.name

        // First text line
        if (name.endsWith("'s Chum Bucket")) {
            if (name.contains(LorenzUtils.getPlayerName()) && !config.hideOwn.get()) return
            titleEntity.add(entity)
            hiddenEntities.add(entity)
            event.isCanceled = true
            return
        }

        // Second text line
        if (name.contains("/10 §aChums")) {
            val entityLocation = entity.getLorenzVec()
            for (title in titleEntity) {
                if (entityLocation.equalsIgnoreY(title.getLorenzVec())) {
                    println("found lower chum entity")
                    hiddenEntities.add(entity)
                    event.isCanceled = true
                    return
                }
            }
        }

        // Chum Bucket
        if (config.hideBucket.get()) {
            if (entity.inventory.any { it != null && it.name == "§fEmpty Chum Bucket" }) {
                val entityLocation = entity.getLorenzVec()
                for (title in titleEntity) {
                    if (entityLocation.equalsIgnoreY(title.getLorenzVec())) {
                        hiddenEntities.add(entity)
                        event.isCanceled = true
                        return
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        onToggle(config.enabled, config.hideBucket, config.hideOwn) { HideArmor.updateArmor() }
    }

    private fun reset() {
        titleEntity.clear()
        hiddenEntities.clear()
    }
}