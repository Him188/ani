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

import org.openapitools.client.models.UserEpisodeCollection

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param total 
 * @param limit 
 * @param offset 
 * @param `data` 
 */


data class GetUserSubjectEpisodeCollection200Response (

    @Json(name = "total")
    val total: kotlin.Int,

    @Json(name = "limit")
    val limit: kotlin.Int,

    @Json(name = "offset")
    val offset: kotlin.Int,

    @Json(name = "data")
    val `data`: kotlin.collections.List<UserEpisodeCollection>? = null

)

