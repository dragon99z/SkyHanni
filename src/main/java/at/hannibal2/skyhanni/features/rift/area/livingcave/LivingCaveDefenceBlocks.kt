package at.hannibal2.skyhanni.features.rift.area.livingcave

import at.hannibal2.skyhanni.events.PacketEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.ServerBlockChangeEvent
import at.hannibal2.skyhanni.events.withAlpha
import at.hannibal2.skyhanni.features.rift.everywhere.RiftAPI
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.test.GriffinUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.EntityUtils.getEntitiesNearby
import at.hannibal2.skyhanni.utils.EntityUtils.isAtFullHealth
import at.hannibal2.skyhanni.utils.LocationUtils.distanceTo
import at.hannibal2.skyhanni.utils.LorenzUtils.editCopy
import at.hannibal2.skyhanni.utils.LorenzUtils.toChromaColor
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.draw3DLine
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.network.play.server.S22PacketMultiBlockChange
import net.minecraft.network.play.server.S23PacketBlockChange
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class LivingCaveDefenceBlocks {
    private val config get() = RiftAPI.config.area.livingCaveConfig.defenceBlockConfig
    private var movingBlocks = mapOf<DefenceBlock, Long>()
    private var staticBlocks = emptyList<DefenceBlock>()
//    private var helpLocation = emptyList<LorenzVec>()

    class DefenceBlock(val entity: EntityOtherPlayerMP, val location: LorenzVec, var hidden: Boolean = false)

    @SubscribeEvent
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        val location = event.location.add(-0.5, 0.0, -0.5)

//        if (event.type == EnumParticleTypes.CRIT_MAGIC) {
//            helpLocation = helpLocation.editCopy { add(location) }
//        }

        // TODO remove
        Minecraft.getMinecraft().thePlayer?.let {
            if (it.isSneaking) {
                staticBlocks = emptyList()
//                helpLocation = emptyList()
            }
        }
        if (!isEnabled()) return

        movingBlocks = movingBlocks.editCopy {
            values.removeIf { System.currentTimeMillis() > it + 2000 }
            keys.removeIf { staticBlocks.any { others -> others.location.distance(it.location) < 1.5 } }
        }


        // Ignore particles around blocks
        if (staticBlocks.any { it.location.distance(location) < 3 }) {
            event.isCanceled = true
            return
        }

        if (event.type == EnumParticleTypes.CRIT_MAGIC) {
            var entity: EntityOtherPlayerMP? = null

            // read old entity data
            getNearestMovingDefenceBlock(location)?.let {
                if (it.location.distance(location) < 0.5) {
                    movingBlocks = movingBlocks.editCopy {
                        it.hidden = true
                    }
                    entity = it.entity
                }
            }

            if (entity == null) {
                // read new entity data
                val compareLocation = event.location.add(-0.5, -1.5, -0.5)
                entity = Minecraft.getMinecraft().theWorld.getEntitiesNearby<EntityOtherPlayerMP>(compareLocation, 2.0)
                    .filter { it.name == "Autonull " || it.name == "Autoboots " }
                    .filter { !it.isAtFullHealth() }
                    .minByOrNull { it.distanceTo(compareLocation) }
            }

            val defenceBlock = entity?.let { DefenceBlock(it, location) } ?: return

            movingBlocks = movingBlocks.editCopy { this[defenceBlock] = System.currentTimeMillis() + 250 }
            event.isCanceled = true
        }
    }

    // TODO move to somewhere else
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    fun onChatPacket(event: PacketEvent.ReceiveEvent) {
        val packet = event.packet

        if (packet is S23PacketBlockChange) {
            ServerBlockChangeEvent(packet.blockPosition, packet.blockState).postAndCatch()
        } else if (packet is S22PacketMultiBlockChange) {
            for (block in packet.changedBlocks) {
                ServerBlockChangeEvent(block.pos, block.blockState).postAndCatch()
            }
        }
    }

    @SubscribeEvent
    fun onBlockChange(event: ServerBlockChangeEvent) {
        if (!isEnabled()) return
        val location = event.location
        val old = event.old
        val new = event.new

        // spawn block
        if (old == "air" && (new == "stained_glass" || new == "diamond_block")) {
            val entity = getNearestMovingDefenceBlock(location)?.entity ?: return
            staticBlocks = staticBlocks.editCopy {
                add(DefenceBlock(entity, location))
                RenderLivingEntityHelper.setEntityColor(
                    entity,
                    color.withAlpha(50)
                ) { isEnabled() && staticBlocks.any { it.entity == entity } }
            }
        }

        // despawn block
        val nearestBlock = getNearestStaticDefenceBlock(location)
        if (new == "air" && location == nearestBlock?.location) {
            staticBlocks = staticBlocks.editCopy { remove(nearestBlock) }
        }
    }

    private fun getNearestMovingDefenceBlock(location: LorenzVec) =
        movingBlocks.keys.filter { it.location.distance(location) < 15 }.minByOrNull { it.location.distance(location) }

    private fun getNearestStaticDefenceBlock(location: LorenzVec) =
        staticBlocks.filter { it.location.distance(location) < 15 }.minByOrNull { it.location.distance(location) }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
//        for (location in helpLocation) {
//            event.drawWaypointFilled(location, LorenzColor.GREEN.toColor())
//            event.drawDynamicText(location, "§aTest", 1.5)
//
//        }
        if (!isEnabled()) return


        for ((block, time) in movingBlocks) {
            if (block.hidden) continue
            if (time > System.currentTimeMillis()) {
                val location = block.location
                event.drawWaypointFilled(location, color)
                event.draw3DLine(
                    block.entity.getLorenzVec().add(0.0, 0.5, 0.0),
                    location.add(0.5, 0.5, 0.5),
                    color,
                    1,
                    false
                )
            }
        }
        for (block in staticBlocks) {
            val location = block.location
            event.drawWaypointFilled(location, color)
            event.drawDynamicText(location, "§bBreak!", 1.5)

            event.draw3DLine(
                block.entity.getLorenzVec().add(0.0, 0.5, 0.0),
                location.add(0.5, 0.5, 0.5),
                color,
                3,
                false
            )
        }
    }

    val color get() = config.color.get().toChromaColor()

    fun isEnabled() = RiftAPI.inRift() && config.enabled && RiftAPI.inLivingCave()
}
