package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.DefaultModelEqualityDelegate
import coil3.compose.EqualityDelegate
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import me.him188.ani.app.platform.currentPlatform
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.app.platform.isDesktop
import me.him188.ani.app.ui.external.placeholder.placeholder
import okhttp3.OkHttpClient

val LocalImageLoader = androidx.compose.runtime.staticCompositionLocalOf<ImageLoader> {
    error("No ImageLoader provided")
}

@Stable
inline val defaultFilterQuality get() = if (currentPlatform.isDesktop()) FilterQuality.High else FilterQuality.Low

/**
 * Placeholder is [Modifier.placeholder]
 */
@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
    modelEqualityDelegate: EqualityDelegate = DefaultModelEqualityDelegate,
) {
    var isLoading by remember { mutableStateOf(true) }
    return coil3.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = Modifier.placeholder(isLoading).then(modifier),
        placeholder = null,
        error = error,
        fallback = fallback,
        onLoading = {
            isLoading = true
            onLoading?.invoke(it)
        },
        onSuccess = {
            isLoading = false
            onSuccess?.invoke(it)
        },
        onError = {
            isLoading = false
            onError?.invoke(it)
        },
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
        modelEqualityDelegate = modelEqualityDelegate,
    )
}


@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
    modelEqualityDelegate: EqualityDelegate = DefaultModelEqualityDelegate,
) {
    return coil3.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        fallback = fallback,
        onLoading = onLoading,
        onSuccess = onSuccess,
        onError = onError,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
        modelEqualityDelegate = modelEqualityDelegate,
    )
}

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalImageLoader.current,
    transform: (AsyncImagePainter.State) -> AsyncImagePainter.State = AsyncImagePainter.DefaultTransform,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = defaultFilterQuality,
    clipToBounds: Boolean = true,
    modelEqualityDelegate: EqualityDelegate = DefaultModelEqualityDelegate,
) {
    return coil3.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = modifier,
        transform = transform,
        onState = onState,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
        modelEqualityDelegate = modelEqualityDelegate,
    )
}

fun getDefaultImageLoader(
    context: PlatformContext,
    config: ImageLoader.Builder.() -> Unit = {}
): ImageLoader {
    return ImageLoader.Builder(context).apply {
        crossfade(true)

//        diskCache(DiskCache.Builder().apply {
//            maxSizeBytes(100 * 1024 * 1024)
//        }.build())

        diskCachePolicy(CachePolicy.ENABLED)
        memoryCachePolicy(CachePolicy.ENABLED)
        memoryCache {
            MemoryCache.Builder().apply {
                maxSizeBytes(10 * 1024 * 1024)
            }.build()
        }
        networkCachePolicy(CachePolicy.ENABLED)

        components {
            add(SvgDecoder.Factory())

            val client = OkHttpClient.Builder().apply {
                addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder().addHeader(
                            "User-Agent",
                            getAniUserAgent()
                        ).build()
                    )
                }
            }.build()

            add(OkHttpNetworkFetcherFactory(client))
        }

        config()
    }.build()
}
