/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package org.openapitools.client.models


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param wish 
 * @param collect 
 * @param doing 
 * @param onHold 
 * @param dropped 
 */


data class Collection (

    @Json(name = "wish")
    val wish: kotlin.Int,

    @Json(name = "collect")
    val collect: kotlin.Int,

    @Json(name = "doing")
    val doing: kotlin.Int,

    @Json(name = "on_hold")
    val onHold: kotlin.Int,

    @Json(name = "dropped")
    val dropped: kotlin.Int

)

