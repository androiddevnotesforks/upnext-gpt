package io.upnextgpt.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.dokar.sheets.rememberBottomSheetState
import io.upnextgpt.base.ImmutableHolder
import io.upnextgpt.base.R
import io.upnextgpt.base.SealedResult
import io.upnextgpt.base.util.IntentUtil
import io.upnextgpt.data.model.Track
import io.upnextgpt.ui.home.control.CoverView
import io.upnextgpt.ui.home.control.PlayControlCard
import io.upnextgpt.ui.home.control.PlayerCard
import io.upnextgpt.ui.home.control.PlayerProgressBar
import io.upnextgpt.ui.home.control.UpNextCard
import io.upnextgpt.ui.home.viewmodel.HomeUiState
import io.upnextgpt.ui.home.viewmodel.HomeViewModel
import io.upnextgpt.ui.home.viewmodel.MusicApp
import io.upnextgpt.ui.shared.dialog.ConnectToPlayersDialog
import io.upnextgpt.ui.shared.remember.rememberLifecycleEvent
import io.upnextgpt.ui.shared.theme.warn
import io.upnextgpt.ui.shared.widget.ShimmerBorderSnackbar
import io.upnextgpt.ui.shared.widget.SnackbarType
import io.upnextgpt.ui.shared.widget.SpringDragBox
import io.upnextgpt.ui.shared.widget.TitleBar
import io.upnextgpt.ui.shared.widget.TypedSnackbarVisuals
import kotlinx.coroutines.launch
import io.upnextgpt.ui.shared.R as SharedR

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (route: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    val lifecycleEvent = rememberLifecycleEvent()

    val isConnectedToPlayer = uiState.isConnectedToPlayers

    var isShowConnectToPlayersDialog by remember(isConnectedToPlayer) {
        mutableStateOf(false)
    }

    LaunchedEffect(viewModel, lifecycleEvent) {
        if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
            viewModel.updatePlayerConnectionStatus()
        }
    }

    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = !isConnectedToPlayer,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            PlayersNotConnectedBar(
                onConnectClick = { isShowConnectToPlayersDialog = true },
            )
        }

        AnimatedVisibility(
            visible = !uiState.isServiceEnabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            ServiceNotEnabledBar(
                onEnableClick = viewModel::enabledService,
            )
        }

        TitleBar(
            title = stringResource(R.string.app_name),
            modifier = Modifier.padding(horizontal = 16.dp),
            endButton = {
                IconButton(onClick = { onNavigate("settings") }) {
                    Icon(
                        painter = painterResource(
                            SharedR.drawable.outline_settings
                        ),
                        contentDescription = "Settings",
                    )
                }
            },
        )

        Player(
            uiState = uiState,
            onPlay = viewModel::play,
            onPause = viewModel::pause,
            onSeek = viewModel::seek,
            onSelectPlayer = viewModel::selectPlayer,
            onPlayPrev = viewModel::playPrev,
            onPlayTrack = viewModel::playTrack,
            onClearError = viewModel::clearError,
            onFetchNextTrackClick = viewModel::fetchNextTrack,
            onLikeTrack = viewModel::likeTrack,
            onCancelLikeTrack = viewModel::cancelLikeTrack,
            onDislikeTrack = viewModel::dislikeTrack,
            onCancelDislikeTrack = viewModel::cancelDislikeTrack,
            onNavigate = onNavigate,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }

    if (isShowConnectToPlayersDialog) {
        ConnectToPlayersDialog(
            onDismissRequest = { isShowConnectToPlayersDialog = false },
            onConnectClick = viewModel::connectToPlayers,
        )
    }
}

@Composable
private fun Player(
    uiState: HomeUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (position: Long) -> Unit,
    onSelectPlayer: (meta: MusicApp) -> Unit,
    onPlayPrev: () -> Unit,
    onPlayTrack: (track: Track) -> Unit,
    onFetchNextTrackClick: () -> Unit,
    onClearError: () -> Unit,
    onLikeTrack: (track: Track) -> Unit,
    onCancelLikeTrack: (track: Track) -> Unit,
    onDislikeTrack: (track: Track) -> Unit,
    onCancelDislikeTrack: (track: Track) -> Unit,
    onNavigate: (route: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currPlayer = uiState.activePlayer

    val track = uiState.currTrack

    val albumArt = ImmutableHolder(uiState.albumArt)

    val scope = rememberCoroutineScope()

    val snackBarHostState = remember { SnackbarHostState() }

    val scrollState = rememberScrollState()

    val playerSelectorSheetState = rememberBottomSheetState()

    val trackMenuSheetState = rememberBottomSheetState()

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            snackBarHostState.showSnackbar(
                visuals = TypedSnackbarVisuals(
                    type = SnackbarType.Error,
                    message = uiState.error,
                    withDismissAction = true,
                ),
            )
            onClearError()
        }
    }

    DisposableEffect(Unit) {
        onDispose { onClearError() }
    }

    SpringDragBox(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = scrollState)
        ) {
            CoverView(
                key = track?.title + track?.artist,
                bitmap = albumArt,
            )

            Spacer(modifier = Modifier.height(16.dp))

            PlayerProgressBar(
                isPlaying = uiState.isPlaying,
                position = uiState.position,
                duration = uiState.duration,
                onSeek = onSeek,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "Not Playing",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(text = track?.artist ?: "-")
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        scope.launch {
                            if (uiState.currTrack != null) {
                                trackMenuSheetState.expand()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Track menu",
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PlayerCard(
                    playerName = currPlayer?.info?.appName ?: "-",
                    iconRes = currPlayer?.info?.iconRes ?: 0,
                    themeColor = currPlayer?.info?.themeColor
                        ?: MaterialTheme.colorScheme.secondary,
                    onClick = {
                        scope.launch { playerSelectorSheetState.expand() }
                    },
                    onLaunchPlayerClick = {
                        val packageName = currPlayer?.info?.packageName
                            ?: return@PlayerCard
                        when (val ret =
                            IntentUtil.launchApp(context, packageName)) {
                            is SealedResult.Err -> scope.launch {
                                snackBarHostState
                                    .showSnackbar(
                                        message = ret.error,
                                        withDismissAction = true,
                                    )
                            }

                            is SealedResult.Ok -> {}
                        }
                    },
                    modifier = Modifier.weight(1f),
                )

                PlayControlCard(
                    isPlaying = uiState.isPlaying,
                    prevEnabled = true,
                    nextEnabled = uiState.nextTrack != null,
                    onPrevClick = onPlayPrev,
                    onPlayPauseClick = {
                        if (uiState.isPlaying) {
                            onPause()
                        } else {
                            onPlay()
                        }
                    },
                    onNextClick = {
                        if (uiState.nextTrack != null) {
                            onPlayTrack(uiState.nextTrack)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            UpNextCard(
                isRolling = uiState.isLoadingNextTrack,
                nextTrack = uiState.nextTrack,
                playEnabled = uiState.nextTrack != null,
                rollEnabled = !uiState.isLoadingNextTrack,
                onClick = { onNavigate("queue") },
                onPlayClick = { uiState.nextTrack?.let { onPlayTrack(it) } },
                onRollClick = onFetchNextTrackClick,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackBarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ShimmerBorderSnackbar(snackbarData = it)
        }
    }

    PlayerSelectorSheet(
        state = playerSelectorSheetState,
        items = ImmutableHolder(uiState.players),
        onSelect = {
            scope.launch { playerSelectorSheetState.collapse() }
            onSelectPlayer(it)
        },
    )

    if (track != null) {
        TrackMenuSheet(
            track = track,
            albumArt = albumArt,
            state = trackMenuSheetState,
            onLike = { onLikeTrack(track) },
            onCancelLike = { onCancelLikeTrack(track) },
            onDislike = { onDislikeTrack(track) },
            onCancelDislike = { onCancelDislikeTrack(track) },
        )
    }
}

@Composable
private fun ServiceNotEnabledBar(
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Service disabled.")

        TextButton(
            onClick = onEnableClick,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text("Enable")
        }
    }
}

@Composable
private fun PlayersNotConnectedBar(
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.warn)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Players are not connected.")

        TextButton(
            onClick = onConnectClick,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        ) {
            Text("Connect")
        }
    }
}