package me.him188.ani.app.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import me.him188.ani.app.ViewModelAuthSupport
import me.him188.ani.app.data.EpisodeRepository
import me.him188.ani.app.data.SubjectRepository
import me.him188.ani.app.data.setSubjectCollectionTypeOrDelete
import me.him188.ani.app.session.SessionManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.CollectionType
import me.him188.ani.datasources.bangumi.processing.airSeason
import me.him188.ani.datasources.bangumi.processing.isOnAir
import me.him188.ani.datasources.bangumi.processing.nameCNOrName
import me.him188.ani.datasources.bangumi.processing.toCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openapitools.client.models.EpType
import org.openapitools.client.models.EpisodeCollectionType
import org.openapitools.client.models.Subject
import org.openapitools.client.models.SubjectCollectionType
import org.openapitools.client.models.SubjectType
import org.openapitools.client.models.UserEpisodeCollection
import org.openapitools.client.models.UserSubjectCollection

class MyCollectionsViewModel : AbstractViewModel(), KoinComponent, ViewModelAuthSupport {
    private val sessionManager: SessionManager by inject()
    private val subjectRepository: SubjectRepository by inject()
    private val episodeRepository: EpisodeRepository by inject()

    @Stable
    val isLoggedIn = sessionManager.isSessionValid.filterNotNull().localCachedSharedFlow()

    @Stable
    val isLoading = MutableStateFlow(true)

    @Stable
    val collections = sessionManager.username.filterNotNull().flatMapLatest { username ->
        isLoading.value = true
        subjectRepository.getSubjectCollections(username).map { raw ->
            raw.convertToItem()
        }.runningList().onCompletion {
            isLoading.value = false
        }
    }.localCachedStateFlow(null)

    @Stable
    private val _collectionsByType: Map<CollectionType, Flow<List<SubjectCollectionItem>>> =
        CollectionType.entries.associateWith { type ->
            collections.map {
                it.orEmpty().filter { collection -> collection.collectionType == type }
            }
        }

    @Composable
    fun collectionsByType(type: CollectionType): State<List<SubjectCollectionItem>> {
        val state = (_collectionsByType[type] ?: emptyFlow()) // 不应该是 null, 但 defensive
            .collectAsStateWithLifecycle(
                collections.value.orEmpty().filter { collection -> collection.collectionType == type }
            )
        return state
    }


    private suspend fun UserSubjectCollection.convertToItem() = coroutineScope {
        val subject = async {
            subjectRepository.getSubject(subjectId)
        }
        val eps = episodeRepository.getSubjectEpisodeCollection(subjectId, EpType.MainStory).toList()
        val isOnAir = async {
            eps.firstOrNull { it.episode.isOnAir() == true } != null
        }
        val lastWatchedEp = async {
            eps.indexOfLast {
                it.type == EpisodeCollectionType.WATCHED || it.type == EpisodeCollectionType.DISCARDED
            }
        }
        val latestEp = async {
            eps.lastOrNull { it.episode.isOnAir() == false }
                ?: eps.lastOrNull { it.episode.isOnAir() != true }
        }

        createItem(subject.await(), isOnAir.await(), latestEp.await(), lastWatchedEp.await(), eps)
    }

    suspend fun updateSubjectCollection(subjectId: Int, action: SubjectCollectionAction) {
        collections.value = collections.value?.map { item ->
            if (item.subjectId == subjectId) {
                item.copy(collectionType = action.type.toSubjectCollectionType())
            } else {
                item
            }
        }
        subjectRepository.setSubjectCollectionTypeOrDelete(subjectId, action.type.toSubjectCollectionType())
    }

    suspend fun setAllEpisodesWatched(subjectId: Int) {
        collections.value?.find { it.subjectId == subjectId }?.let { collection ->
            collection.episodes = collection.episodes.map { episode ->
                episode.copy(type = EpisodeCollectionType.WATCHED)
            }
        }
        val ids = episodeRepository.getEpisodesBySubjectId(subjectId, EpType.MainStory).map { it.id }.toList()
        episodeRepository.setEpisodeCollection(
            subjectId,
            ids,
            EpisodeCollectionType.WATCHED,
        )
    }

    suspend fun setEpisodeWatched(subjectId: Int, episodeId: Int, watched: Boolean) {
        val newType = if (watched) EpisodeCollectionType.WATCHED else EpisodeCollectionType.WATCHLIST
        collections.value?.find { it.subjectId == subjectId }?.let { collection ->
            collection.episodes = collection.episodes.map { episode ->
                if (episode.episode.id == episodeId) {
                    episode.copy(type = newType)
                } else {
                    episode
                }
            }
        }

        episodeRepository.setEpisodeCollection(
            subjectId,
            listOf(episodeId),
            newType,
        )
    }
}

@Stable
class SubjectCollectionItem(
    val subjectId: Int,
    val displayName: String,
    val image: String,
    val rate: Int?,

    val date: String?,
    val totalEps: Int,
    val isOnAir: Boolean,
    /**
     * 最新更新到
     */
    val latestEp: UserEpisodeCollection?,
    val lastWatchedEpIndex: Int?,

    episodes: List<UserEpisodeCollection>,
    collectionType: SubjectCollectionType?,
) {
    val collectionType: CollectionType = collectionType.toCollectionType()
    var episodes by mutableStateOf(episodes)

    val latestEpIndex: Int? = episodes.indexOfFirst { it.episode.id == latestEp?.episode?.id }
        .takeIf { it != -1 }
        ?: episodes.lastIndex.takeIf { it != -1 }

    val onAirDescription = if (isOnAir) {
        if (latestEp == null) {
            "连载中"
        } else {
            "连载至第 ${latestEp.episode.sort} 话"
        }
    } else {
        "已完结"
    }

    val serialProgress = "全 $totalEps 话"

    fun copy(
        subjectId: Int = this.subjectId,
        displayName: String = this.displayName,
        image: String = this.image,
        rate: Int? = this.rate,
        date: String? = this.date,
        totalEps: Int = this.totalEps,
        isOnAir: Boolean = this.isOnAir,
        latestEp: UserEpisodeCollection? = this.latestEp,
        lastWatchedEpIndex: Int? = this.lastWatchedEpIndex,
        episodes: List<UserEpisodeCollection> = this.episodes,
        collectionType: SubjectCollectionType? = this.collectionType.toSubjectCollectionType(),
    ) = SubjectCollectionItem(
        subjectId = subjectId,
        displayName = displayName,
        image = image,
        rate = rate,
        date = date,
        totalEps = totalEps,
        isOnAir = isOnAir,
        latestEp = latestEp,
        lastWatchedEpIndex = lastWatchedEpIndex,
        episodes = episodes,
        collectionType = collectionType,
    )
}

private fun UserSubjectCollection.createItem(
    subject: Subject?,
    isOnAir: Boolean,
    latestEp: UserEpisodeCollection?,
    lastWatchedEpIndex: Int?,
    episodes: List<UserEpisodeCollection>,
): SubjectCollectionItem {
    if (subject == null || subject.type != SubjectType.Anime) {
        return SubjectCollectionItem(
            subjectId = subjectId,
            displayName = this.subject?.nameCNOrName() ?: "",
            image = "",
            rate = this.rate,
            date = this.subject?.airSeason,
            totalEps = episodes.size,
            isOnAir = isOnAir,
            latestEp = latestEp,
            lastWatchedEpIndex = null,
            episodes = episodes,
            collectionType = type,
        )
    }

    return SubjectCollectionItem(
        subjectId = subjectId,
        displayName = this.subject?.nameCNOrName() ?: "",
        image = this.subject?.images?.common ?: "",
        rate = this.rate,
        date = subject.airSeason ?: "",
        totalEps = episodes.size,
        isOnAir = isOnAir,
        latestEp = latestEp,
        lastWatchedEpIndex = lastWatchedEpIndex,
        episodes = episodes,
        collectionType = type,
    )
}
