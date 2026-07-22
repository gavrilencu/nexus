package com.example.toolkit.data.graphql

import com.example.toolkit.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class GqlField(val name: String, val type: String, val args: List<String>)
data class GqlType(val name: String, val kind: String, val fields: List<GqlField>)

data class GraphqlResult(
    val endpoint: String,
    val reachable: Boolean = false,
    val introspectionEnabled: Boolean = false,
    val queryType: String? = null,
    val mutationType: String? = null,
    val subscriptionType: String? = null,
    val queries: List<GqlField> = emptyList(),
    val mutations: List<GqlField> = emptyList(),
    val types: List<GqlType> = emptyList(),
    val note: String? = null,
    val error: String? = null
)

/**
 * GraphQL endpoint inspector. Confirms a GraphQL endpoint responds, runs the
 * standard introspection query and, when enabled, maps the schema: root
 * query/mutation/subscription types plus every custom object type and its
 * fields. Flags introspection being publicly enabled as a finding.
 */
class GraphqlEngine {

    private val client = HttpClients.default
    private val json = "application/json".toMediaType()

    private val candidates = listOf(
        "/graphql", "/graphql/", "/api/graphql", "/v1/graphql", "/query", "/gql",
        "/graphql/console", "/api/gql", "/graphql-api"
    )

    private val introspectionQuery = """
        {"query":"query IntrospectionQuery { __schema { queryType { name } mutationType { name } subscriptionType { name } types { name kind fields(includeDeprecated:true){ name args{ name type{ name kind ofType{ name } } } type{ name kind ofType{ name kind ofType{ name } } } } } } }"}
    """.trimIndent()

    suspend fun inspect(rawUrl: String): GraphqlResult = withContext(Dispatchers.IO) {
        val endpoint = resolveEndpoint(rawUrl) ?: return@withContext GraphqlResult(rawUrl,
            error = "No GraphQL endpoint responded. Try the exact endpoint URL.")

        val resp = post(endpoint, introspectionQuery)
            ?: return@withContext GraphqlResult(endpoint, reachable = true,
                note = "Endpoint reachable but introspection request failed or was blocked.")

        val root = runCatching { JSONObject(resp) }.getOrNull()
            ?: return@withContext GraphqlResult(endpoint, reachable = true,
                note = "Non-JSON response; endpoint may not be GraphQL.")

        val schema = root.optJSONObject("data")?.optJSONObject("__schema")
            ?: return@withContext GraphqlResult(endpoint, reachable = true, introspectionEnabled = false,
                note = "Introspection disabled (no __schema in response). This is the hardened default.")

        parseSchema(endpoint, schema)
    }

    private fun parseSchema(endpoint: String, schema: JSONObject): GraphqlResult {
        val queryTypeName = schema.optJSONObject("queryType")?.optString("name")
        val mutationTypeName = schema.optJSONObject("mutationType")?.optString("name")
        val subTypeName = schema.optJSONObject("subscriptionType")?.optString("name")

        val types = ArrayList<GqlType>()
        var queries = emptyList<GqlField>()
        var mutations = emptyList<GqlField>()

        val typesArr = schema.optJSONArray("types") ?: return GraphqlResult(endpoint, true, true,
            queryTypeName, mutationTypeName, subTypeName)

        for (i in 0 until typesArr.length()) {
            val t = typesArr.optJSONObject(i) ?: continue
            val name = t.optString("name")
            if (name.startsWith("__")) continue
            val kind = t.optString("kind")
            val fields = parseFields(t.optJSONArray("fields"))
            if (name == queryTypeName) queries = fields
            if (name == mutationTypeName) mutations = fields
            if (fields.isNotEmpty() || kind == "OBJECT" || kind == "INPUT_OBJECT" || kind == "ENUM") {
                types.add(GqlType(name, kind, fields))
            }
        }

        return GraphqlResult(
            endpoint = endpoint, reachable = true, introspectionEnabled = true,
            queryType = queryTypeName, mutationType = mutationTypeName, subscriptionType = subTypeName,
            queries = queries, mutations = mutations, types = types,
            note = "Introspection is ENABLED — schema fully disclosed (should be disabled in production)."
        )
    }

    private fun parseFields(arr: org.json.JSONArray?): List<GqlField> {
        if (arr == null) return emptyList()
        val out = ArrayList<GqlField>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val name = f.optString("name")
            val type = typeName(f.optJSONObject("type"))
            val args = ArrayList<String>()
            f.optJSONArray("args")?.let { a ->
                for (j in 0 until a.length()) {
                    val arg = a.optJSONObject(j) ?: continue
                    args.add("${arg.optString("name")}: ${typeName(arg.optJSONObject("type"))}")
                }
            }
            out.add(GqlField(name, type, args))
        }
        return out
    }

    private fun typeName(t: JSONObject?): String {
        if (t == null) return "?"
        val n = t.optString("name")
        if (n.isNotBlank() && n != "null") return n
        val of = t.optJSONObject("ofType")
        return typeName(of)
    }

    private fun resolveEndpoint(rawUrl: String): String? {
        val base = normalize(rawUrl)
        // If URL already looks like a graphql endpoint, test it directly first.
        val direct = post(base, """{"query":"{__typename}"}""")
        if (direct != null && (direct.contains("__typename") || direct.contains("errors") || direct.contains("data"))) return base

        val origin = base.substringBefore("/", base).let {
            val u = java.net.URI(base); "${u.scheme}://${u.authority}"
        }
        for (c in candidates) {
            val url = origin + c
            val r = post(url, """{"query":"{__typename}"}""")
            if (r != null && (r.contains("__typename") || r.contains("\"data\"") || r.contains("\"errors\""))) return url
        }
        return null
    }

    private fun post(url: String, body: String): String? = try {
        val req = Request.Builder().url(url)
            .header("User-Agent", "NEXUS-Toolkit/1.0")
            .header("Accept", "application/json")
            .post(body.toRequestBody(json)).build()
        client.newCall(req).execute().use { it.body?.string()?.take(2_000_000) }
    } catch (_: Exception) { null }

    private fun normalize(raw: String): String {
        var s = raw.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }
}
