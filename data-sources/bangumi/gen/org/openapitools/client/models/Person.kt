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

import org.openapitools.client.models.PersonCareer
import org.openapitools.client.models.PersonImages
import org.openapitools.client.models.PersonType

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param id 
 * @param name 
 * @param type 
 * @param career 
 * @param shortSummary 
 * @param locked 
 * @param images 
 */


data class Person (

    @Json(name = "id")
    val id: kotlin.Int,

    @Json(name = "name")
    val name: kotlin.String,

    @Json(name = "type")
    val type: PersonType,

    @Json(name = "career")
    val career: kotlin.collections.List<PersonCareer>,

    @Json(name = "short_summary")
    val shortSummary: kotlin.String,

    @Json(name = "locked")
    val locked: kotlin.Boolean,

    @Json(name = "images")
    val images: PersonImages? = null

)
