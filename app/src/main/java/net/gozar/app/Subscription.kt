package net.gozar.app

import org.json.JSONObject
import java.util.UUID

data class Subscription(
    val name: String,
    val url: String,
    val used: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val lastUpdated: Long = 0,
    /**
     * The panel username this subscription was delivered for, when it came
     * from the shop or the bot.
     *
     * Without it a delivered service and a hand-pasted link are the same
     * object once they are in the list, so there was no way to offer renewal
     * on the one that can actually be renewed. Blank for everything else, and
     * never shown - it only ever goes back to the API that issued it.
     */
    val serviceUsername: String = "",
    val id: String = UUID.randomUUID().toString()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("url", url)
        .put("used", used).put("total", total).put("expire", expire)
        .put("lastUpdated", lastUpdated)
        .put("serviceUsername", serviceUsername)

    companion object {
        fun fromJson(o: JSONObject) = Subscription(
            name = o.optString("name"),
            url = o.optString("url"),
            used = o.optLong("used", 0),
            total = o.optLong("total", 0),
            expire = o.optLong("expire", 0),
            lastUpdated = o.optLong("lastUpdated", 0),
            serviceUsername = o.optString("serviceUsername", ""),
            id = o.optString("id", UUID.randomUUID().toString())
        )
    }
}