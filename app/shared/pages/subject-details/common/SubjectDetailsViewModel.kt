package me.him188.ani.app.ui.subject.details

import androidx.compose.runtime.Stable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runInterruptible
import me.him188.ani.app.data.repositories.EpisodeRepository
import me.him188.ani.app.data.repositories.SubjectRepository
import me.him188.ani.app.data.repositories.setSubjectCollectionTypeOrDelete
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.session.SessionManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.subject.collection.progress.EpisodeProgressState
import me.him188.ani.datasources.api.paging.PageBasedPagedSource
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.client.BangumiEpType
import me.him188.ani.datasources.bangumi.client.BangumiEpisode
import me.him188.ani.datasources.bangumi.models.subjects.BangumiSubjectImageSize
import me.him188.ani.datasources.bangumi.processing.nameCNOrName
import me.him188.ani.datasources.bangumi.processing.sortByRelation
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.Count
import org.openapitools.client.models.EpisodeCollectionType
import org.openapitools.client.models.Item
import org.openapitools.client.models.RelatedCharacter
import org.openapitools.client.models.RelatedPerson
import org.openapitools.client.models.Subject
import org.openapitools.client.models.Tag

@Stable
class SubjectDetailsViewModel(
    val subjectId: Int,
) : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()
    private val bangumiClient: BangumiClient by inject()
    private val subjectRepository: SubjectRepository by inject()
    private val episodeRepository: EpisodeRepository by inject()
    private val browserNavigator: BrowserNavigator by inject()
//    private val subjectProvider: SubjectProvider by inject()

    private val subject: SharedFlow<Subject?> = flowOf(this.subjectId).mapLatest {
        subjectRepository.getSubject(it)
    }.shareInBackground()

    private val subjectNotNull = subject.mapNotNull { it }

    val chineseName: SharedFlow<String> = subjectNotNull.map { it.nameCNOrName() }.shareInBackground()
    val officialName: SharedFlow<String> = subjectNotNull.map { it.name }.shareInBackground()

    val coverImage: SharedFlow<String> = subjectNotNull.map {
        bangumiClient.subjects.getSubjectImageUrl(
            it.id,
            BangumiSubjectImageSize.MEDIUM
        )
    }.shareInBackground()

    val totalEpisodes: SharedFlow<Int> =
        subjectNotNull.map { it.totalEpisodes }.shareInBackground()

    val tags: SharedFlow<List<Tag>> =
        subjectNotNull.map { it.tags }
            .map { tags -> tags.sortedByDescending { it.count } }
            .shareInBackground()

    val ratingScore: SharedFlow<String> = subjectNotNull.map { it.rating.score }
        .mapLatest { String.format(".2f", it) }.shareInBackground()
    val ratingCounts: SharedFlow<Count> =
        subjectNotNull.map { it.rating.count }.shareInBackground()

    private val infoboxList: SharedFlow<List<Item>> =
        subjectNotNull.map { it.infobox.orEmpty() }.shareInBackground()

    val summary: SharedFlow<String> =
        subjectNotNull.map { it.summary }.shareInBackground()

    val characters: SharedFlow<List<RelatedCharacter>> = subjectNotNull.map { subject ->
        runInterruptible(Dispatchers.IO) { bangumiClient.api.getRelatedCharactersBySubjectId(subject.id) }
            .distinctBy { it.id }
    }.shareInBackground()

    val relatedPersons: SharedFlow<List<RelatedPerson>> = subjectNotNull.map { subject ->
        runInterruptible(Dispatchers.IO) { bangumiClient.api.getRelatedPersonsBySubjectId(subject.id) }
            .sortByRelation()
            .distinctBy { it.id }
    }.shareInBackground()
//
//    val staff: SharedFlow<Staff> = combine(infoboxList, relatedPersons) { infoboxList, relatedPersons ->
//        infoboxList to relatedPersons
//    }.map { (infoboxList, relatedPersons) ->
//        infoboxList.map { it.key to  }
//        val company = relatedPersons.filter { it.type == "公司" }
//        val selectedRelatedPersons = relatedPersons.filter { it.type != "公司" }
//        Staff(company, selectedRelatedPersons)
//    }.shareInBackground()

    val episodesMain: SharedFlow<List<BangumiEpisode>> = episodesFlow(BangumiEpType.MAIN)
    val episodesPV: SharedFlow<List<BangumiEpisode>> = episodesFlow(BangumiEpType.PV)
    val episodesSP: SharedFlow<List<BangumiEpisode>> = episodesFlow(BangumiEpType.SP)
    val episodesOther: SharedFlow<List<BangumiEpisode>> = episodesFlow(BangumiEpType.OTHER)


    /**
     * 全站用户的收藏情况
     */
    val collection = subjectNotNull.map { it.collection }.shareInBackground()

    /***
     * 登录用户的收藏情况
     *
     * 未登录或网络错误时为 `null`.
     */
    private val selfCollectionType = combine(
        subjectNotNull,
        sessionManager.username.filterNotNull()
    ) { subject, username ->
        runCatching {
            runInterruptible(Dispatchers.IO) { bangumiClient.api.getUserCollection(username, subject.id) }.type
                .toCollectionType()
        }.onFailure {
            if (it is ClientException && it.statusCode == 404) {
                // 用户没有收藏这个
                return@combine UnifiedCollectionType.NOT_COLLECTED
            }
        }.getOrNull()
    }.localCachedSharedFlow()

    /**
     * 登录用户是否收藏了该条目.
     *
     * 未登录或网络错误时为 `null`.
     */
    val selfCollected = selfCollectionType.map { it != UnifiedCollectionType.NOT_COLLECTED }.shareInBackground()

    /**
     * 根据登录用户的收藏类型的相应动作, 例如未追番时为 "追番", 已追番时为 "已在看" / "已看完" 等.
     *
     * 未登录或网络错误时为 `null`.
     */
    val selfCollectionAction = selfCollectionType.shareInBackground()

    val episodeProgressState by lazy { EpisodeProgressState(subjectId, this) }

    /**
     * null means delete
     */
    suspend fun setSelfCollectionType(subjectCollectionType: UnifiedCollectionType) {
        selfCollectionType.emit(subjectCollectionType)
        subjectRepository.setSubjectCollectionTypeOrDelete(
            subjectId,
            subjectCollectionType.toSubjectCollectionType()
        )
    }

    suspend fun setAllEpisodesWatched() {

        episodeRepository.setEpisodeCollection(
            subjectId,
            episodesMain.first().map { it.id.toInt() },
            EpisodeCollectionType.WATCHED,
        )
    }

    private fun episodesFlow(type: BangumiEpType) = flowOf(this.subjectId).mapLatest { subjectId ->
        PageBasedPagedSource { page ->
            bangumiClient.episodes.getEpisodes(
                subjectId.toLong(),
                type,
                offset = page * 100,
                limit = 100
            )
        }.results.toList()
    }.shareInBackground()

    fun browseSubjectBangumi(context: ContextMP) {
        browserNavigator.openBrowser(context, "https://bgm.tv/subject/${subjectId}")
    }
}

//private val ignoredLevels = listOf(
//    "原画",
//)
