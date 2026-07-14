package com.lucas.album.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucas.album.data.AppContainer
import com.lucas.album.ui.canvas.CanvasScreen
import com.lucas.album.ui.canvas.CanvasViewModel
import com.lucas.album.ui.pin.PinScreen
import com.lucas.album.ui.pin.PinViewModel
import com.lucas.album.ui.proposal.ProposalScreen

@Composable
fun AlbumApp(container: AppContainer, modifier: Modifier = Modifier) {
    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.factory(container.preferences, container.pinManager)
    )
    val screen by appViewModel.screen.collectAsState()

    AnimatedContent(
        targetState = screen,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(450)) + scaleIn(initialScale = 0.94f, animationSpec = tween(450))) togetherWith
                fadeOut(tween(200))
        },
        label = "screen-switch",
    ) { current ->
        when (current) {
            Screen.Loading -> Unit
            Screen.Proposal -> ProposalScreen(onAnswerYes = appViewModel::onProposalAnswered)
            Screen.Pin -> {
                val pinViewModel: PinViewModel = viewModel(
                    factory = PinViewModel.factory(container.pinManager)
                )
                PinScreen(viewModel = pinViewModel, onVerified = appViewModel::onPinVerified)
            }
            Screen.Canvas -> {
                val canvasViewModel: CanvasViewModel = viewModel(
                    factory = CanvasViewModel.factory(
                        container.photoLayerDao,
                        container.photoFileRepository,
                        container.photoExportRepository,
                    )
                )
                CanvasScreen(viewModel = canvasViewModel)
            }
        }
    }
}
