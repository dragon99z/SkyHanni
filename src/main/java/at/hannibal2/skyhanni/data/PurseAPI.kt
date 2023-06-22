package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.PurseChangeCause
import at.hannibal2.skyhanni.events.PurseChangeEvent
import at.hannibal2.skyhanni.utils.NumberUtil.formatNumber
import at.hannibal2.skyhanni.utils.StringUtils.matchMatcher
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

class PurseAPI {
    private val pattern = "(Piggy|Purse): §6(?<coins>[\\d,]*).*".toPattern()
    private var currentPurse = 0.0
    private var inventoryCloseTime = 0L

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inventoryCloseTime = System.currentTimeMillis()
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return

        for (line in ScoreboardData.sidebarLinesFormatted) {
            val newPurse = pattern.matchMatcher(line) {
                group("coins").formatNumber().toDouble()
            } ?: continue
            val diff = newPurse - currentPurse
            if (diff == 0.0) continue
            currentPurse = newPurse

            PurseChangeEvent(diff, getCause(diff)).postAndCatch()
        }
    }

    // TODO add more causes in the future (e.g. ah/bz/bank)
    private fun getCause(diff: Double): PurseChangeCause {
        if (diff > 0) {
            if (diff == 1.0) {
                return PurseChangeCause.GAIN_TALISMAN_OF_COINS
            }
            if (Minecraft.getMinecraft().currentScreen == null) {
                val timeDiff = System.currentTimeMillis() - inventoryCloseTime
                if (timeDiff > 2_000) {
                    return PurseChangeCause.GAIN_MOB_KILL
                }

            }
            return PurseChangeCause.GAIN_UNKNOWN
        } else {
            val timeDiff = System.currentTimeMillis() - SlayerAPI.questStartTime
            if (timeDiff < 1500) {
                return PurseChangeCause.LOSE_SLAYER_QUEST_STARTED
            }

            return PurseChangeCause.LOSE_UNKNOWN
        }
    }
}