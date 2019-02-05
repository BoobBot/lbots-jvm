package org.lbots.jvmclient

import okhttp3.*

import org.json.JSONObject

class LBotsClient(botID: Long, private val token: String) {
    private val BASE_URL = "https://lbots.org/api/v1"
    private val base = "$BASE_URL/bots/$botID"

    private val client = OkHttpClient()
    private val ratelimiter = RatelimitHandler

    private fun build(method: String, url: String, post_data: Map<String, Int>?): Request {
        var body: RequestBody? = null

        if (!post_data.isNullOrEmpty()) {
            var data_string = "{"
            post_data.forEach {
                data_string += '"${it.key}": ${it.value},'
            }
            data_string[data_string.length-1] = "}"
            RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data_string)
        }

        return Request.Builder().apply {
            addHeader("Content-Type", "application/json")
            addHeader("Authorization", token)
            url(url)
            method(method, body)
        }.build()
    }

    private fun request(method: String, url: String, post_data: Map<String, Int>? = null): JSONObject {
        val response = client.newCall(build(method, url, post_data)).execute()

        val status = response.code()

        if (200 > status || 300 <= status){
            throw HTTPException("Unexpected response code: $status")
        }

        val content = response.body()!!.string()

        return JSONObject(content)
    }

    fun invalidate(): Boolean {
        val response = request("GET", "$base/invalidate")
        return response["success"] as Boolean
    }

    fun updateStats(guildCount: Int, shardCount: Int = 1, shardID: Int = 0): Boolean {
        val data = mutableMapOf("guild_count" to guildCount)

        if (shardCount > 1){
            data["shard_count"] = shardCount
            data["shard_id"] = shardID
        }

        return ratelimiter.ratelimit("/stats", 20, 5) {
            val response = request("POST", "$base/stats", data.toMap())
            return@ratelimit response["success"]
        } as Boolean
    }

    fun favoriteCount(): Int {
        return ratelimiter.ratelimit("/favorites", 3, 4) {
            val response = request("GET", "$base/favorites")
            return@ratelimit response["favorites"]
        } as Int
    }

    fun userFavorited(userID: Long): Pair<Boolean, String?> {
        val response = request("GET", "$base/favorites/user/$userID")
        var x: String? = null
        if (!response.isNull("time")){
            x = response["time"] as String
        }
        return Pair(response["favorited"] as Boolean, x)
    }
}
