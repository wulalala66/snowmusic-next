package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.utils.LayerPaint
import com.mocharealm.accompanist.lyrics.ui.utils.easing.Bounce
import com.mocharealm.accompanist.lyrics.ui.utils.easing.DipAndRise
import com.mocharealm.accompanist.lyrics.ui.utils.easing.Swell
import com.mocharealm.accompanist.lyrics.ui.utils.isPunctuation
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import kotlin.math.roundToInt

/**
 * Creates a horizontal gradient brush that represents the karaoke progress.
 * The gradient moves from inactive color to active color based on the current time.
 *
 * @param lineLayout The layout information for syllables in the line.
 * @param currentTimeMs The current playback time in milliseconds.
 * @param isRtl Whether the layout direction is Right-to-Left.
 */
private fun createLineGradientBrush(
    lineLayout: List<SyllableLayout>,
    currentTimeMs: Int,
    isRtl: Boolean
): Brush {
    val activeColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.2f)
    val minFadeWidth = 100f

    if (lineLayout.isEmpty()) {
        return Brush.horizontalGradient(colors = listOf(inactiveColor, inactiveColor))
    }

    val totalMinX = lineLayout.minOf { it.position.x }
    val totalMaxX = lineLayout.maxOf { it.position.x + it.width }
    val totalWidth = totalMaxX - totalMinX

    if (totalWidth <= 0f) {
        val isFinished = currentTimeMs >= lineLayout.last().syllable.end
        val color = if (isFinished) activeColor else inactiveColor
        return Brush.horizontalGradient(listOf(color, color))
    }

    val firstSyllableStart = lineLayout.first().syllable.start
    val lastSyllableEnd = lineLayout.last().syllable.end

    val lineProgress = run {
        if (currentTimeMs <= firstSyllableStart) return Brush.horizontalGradient(
            listOf(inactiveColor, inactiveColor)
        )
        if (currentTimeMs >= lastSyllableEnd) return Brush.horizontalGradient(
            listOf(activeColor, activeColor)
        )

        val activeSyllableLayout = lineLayout.find {
            currentTimeMs in it.syllable.start until it.syllable.end
        }

        val currentPixelPosition = when {
            activeSyllableLayout != null -> {
                val syllableProgress = activeSyllableLayout.syllable.progress(currentTimeMs)
                if (isRtl) {
                    activeSyllableLayout.position.x + activeSyllableLayout.width * (1f - syllableProgress)
                } else {
                    activeSyllableLayout.position.x + activeSyllableLayout.width * syllableProgress
                }
            }

            else -> {
                val lastFinished = lineLayout.lastOrNull { currentTimeMs >= it.syllable.end }
                if (isRtl) {
                    lastFinished?.position?.x ?: totalMaxX
                } else {
                    lastFinished?.let { it.position.x + it.width } ?: totalMinX
                }
            }
        }
        ((currentPixelPosition - totalMinX) / totalWidth).coerceIn(0f, 1f)
    }

    val fadeRange = (minFadeWidth / totalWidth).coerceAtMost(1f)
    val fadeCenterStart = -fadeRange / 2f
    val fadeCenterEnd = 1f + fadeRange / 2f
    val fadeCenter = fadeCenterStart + (fadeCenterEnd - fadeCenterStart) * lineProgress
    val fadeStart = fadeCenter - fadeRange / 2f
    val fadeEnd = fadeCenter + fadeRange / 2f

    val colorStops = if (isRtl) {
        arrayOf(
            0.0f to inactiveColor,
            fadeStart.coerceIn(0f, 1f) to inactiveColor,
            fadeEnd.coerceIn(0f, 1f) to activeColor,
            1.0f to activeColor
        )
    } else {
        arrayOf(
            0.0f to activeColor,
            fadeStart.coerceIn(0f, 1f) to activeColor,
            fadeEnd.coerceIn(0f, 1f) to inactiveColor,
            1.0f to inactiveColor
        )
    }

    return Brush.horizontalGradient(
        colorStops = colorStops,
        startX = totalMinX,
        endX = totalMaxX
    )
}

/**
 * Draws a multi-row lyrics line into the canvas.
 * Handles row wrapping, padding, and applying the karaoke progress gradient.
 *
 * @param lineLayouts The pre-calculated layout of syllables, organized by rows.
 * @param currentTimeMs The current playback time in milliseconds.
 * @param color The base text color.
 * @param blendMode The blend mode to use for drawing.
 * @param isRtl Whether the layout direction is Right-to-Left.
 * @param showDebugRectangles Whether to draw debug outlines around glyphs.
 */
fun DrawScope.drawLyricsLine(
    lineLayouts: List<List<SyllableLayout>>,
    currentTimeMs: Int,
    color: Color,
    blendMode: BlendMode,
    isRtl: Boolean,
    showDebugRectangles: Boolean = false
) {
    lineLayouts.forEach { rowLayouts ->
        if (rowLayouts.isEmpty()) return@forEach

        val lastSyllableEnd = rowLayouts.last().syllable.end

        if (currentTimeMs >= lastSyllableEnd) {
            drawRowText(rowLayouts, color, blendMode, showDebugRectangles, currentTimeMs)
            return@forEach
        }

        val minX = rowLayouts.minOf { it.position.x }
        val maxX = rowLayouts.maxOf { it.position.x + it.width }
        val minY = rowLayouts.minOf { it.position.y }
        val totalHeight = rowLayouts.maxOf { it.textLayoutResult.size.height }.toFloat()

        val maxPhoneticHeight =
            rowLayouts.mapNotNull { it.phoneticLayoutResult?.size?.height }.maxOrNull()?.toFloat()
                ?: 0f
        val verticalPadding = (totalHeight * 0.1).dp.toPx()
        val horizontalPadding = ((maxX - minX) * 0.2).dp.toPx()

        drawIntoCanvas { canvas ->
            val layerBounds = Rect(
                left = minX - horizontalPadding,
                top = minY - verticalPadding - maxPhoneticHeight - 8.dp.toPx(),
                right = maxX + horizontalPadding,
                bottom = minY + totalHeight + verticalPadding + 8.dp.toPx()
            )
            canvas.saveLayer(layerBounds, LayerPaint)

            drawRowText(rowLayouts, color, blendMode, showDebugRectangles, currentTimeMs)

            val progressBrush = createLineGradientBrush(rowLayouts, currentTimeMs, isRtl)
            drawRect(
                brush = progressBrush,
                topLeft = layerBounds.topLeft,
                size = layerBounds.size,
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }
}

/**
 * Draws text for a single row, handling word and character animations.
 *
 * @param rowLayouts The layouts for syllables in this row.
 * @param drawColor The color to draw the text with.
 * @param blendMode The blend mode to use.
 * @param showDebugRectangles Whether to show debug bounds.
 * @param currentTimeMs Current playback time.
 */
private fun DrawScope.drawRowText(
    rowLayouts: List<SyllableLayout>,
    drawColor: Color,
    blendMode: BlendMode,
    showDebugRectangles: Boolean,
    currentTimeMs: Int,
) {
    rowLayouts.forEachIndexed { index, syllableLayout ->
        val wordAnimInfo = syllableLayout.wordAnimInfo

        if (wordAnimInfo != null) {
            val fastCharAnimationThresholdMs = 200f
            val awesomeDuration = wordAnimInfo.wordDuration * 0.8f

            val charLayouts = syllableLayout.charLayouts ?: emptyList()
            val charBounds = syllableLayout.charOriginalBounds ?: emptyList()

            val numCharsInWord = wordAnimInfo.wordContent.length
            val earliestStartTime = wordAnimInfo.wordStartTime
            val latestStartTime = wordAnimInfo.wordEndTime - awesomeDuration

            syllableLayout.syllable.content.forEachIndexed { charIndex, _ ->
                val singleCharLayoutResult =
                    charLayouts.getOrNull(charIndex) ?: return@forEachIndexed
                val charBox = charBounds.getOrNull(charIndex) ?: return@forEachIndexed

                val absoluteCharIndex = syllableLayout.charOffsetInWord + charIndex
                val charRatio =
                    if (numCharsInWord > 1) absoluteCharIndex.toFloat() / (numCharsInWord - 1) else 0.5f
                val awesomeStartTime =
                    (earliestStartTime + (latestStartTime - earliestStartTime) * charRatio).toLong()
                val awesomeProgress =
                    ((currentTimeMs - awesomeStartTime).toFloat() / awesomeDuration).coerceIn(
                        0f, 1f
                    )

                val floatOffset = 4f * DipAndRise(
                    dip = ((0.5 * (wordAnimInfo.wordDuration - fastCharAnimationThresholdMs * numCharsInWord) / 1000)).coerceIn(
                        0.0, 0.5
                    )
                ).transform(1.0f - awesomeProgress)
                val scale = 1f + Swell(
                    (0.1 * (wordAnimInfo.wordDuration - fastCharAnimationThresholdMs * numCharsInWord) / 1000).coerceIn(
                        0.0, 0.1
                    )
                ).transform(awesomeProgress)

                val centeredOffsetX = (charBox.width - singleCharLayoutResult.size.width) / 2f
                val xPos = syllableLayout.position.x + charBox.left + centeredOffsetX
                val yPos = syllableLayout.position.y + charBox.top + floatOffset

                val blurRadius = 10f * Bounce.transform(awesomeProgress)
                val shadow = Shadow(
                    color = drawColor.copy(0.4f), offset = Offset(0f, 0f), blurRadius = blurRadius
                )
                withTransform({ scale(scale = scale, pivot = syllableLayout.wordPivot) }) {
                    drawText(
                        textLayoutResult = singleCharLayoutResult,
                        color = drawColor,
                        topLeft = Offset(xPos, yPos),
                        shadow = shadow,
                    )

                    if (showDebugRectangles) {
                        drawRect(
                            color = Color.Red, topLeft = Offset(xPos, yPos), size = Size(
                                singleCharLayoutResult.size.width.toFloat(),
                                singleCharLayoutResult.size.height.toFloat()
                            ), style = Stroke(1f)
                        )
                    }
                }
            }

            syllableLayout.phoneticLayoutResult?.let { phoneticLayout ->
                val syllableMidIndex =
                    syllableLayout.charOffsetInWord + (syllableLayout.syllable.content.length - 1) / 2f
                val syllableRatio =
                    if (numCharsInWord > 1) syllableMidIndex / (numCharsInWord - 1) else 0.5f
                val awesomeStartTime =
                    (earliestStartTime + (latestStartTime - earliestStartTime) * syllableRatio).toLong()
                val awesomeProgress =
                    ((currentTimeMs - awesomeStartTime).toFloat() / awesomeDuration).coerceIn(
                        0f, 1f
                    )

                val floatOffset = 4f * DipAndRise(
                    dip = ((0.5 * (wordAnimInfo.wordDuration - fastCharAnimationThresholdMs * numCharsInWord) / 1000)).coerceIn(
                        0.0, 0.5
                    )
                ).transform(1.0f - awesomeProgress)
                val scale = 1f + Swell(
                    (0.1 * (wordAnimInfo.wordDuration - fastCharAnimationThresholdMs * numCharsInWord) / 1000).coerceIn(
                        0.0, 0.1
                    )
                ).transform(awesomeProgress)

                val phoneticX = syllableLayout.position.x
                val phoneticY =
                    syllableLayout.position.y - phoneticLayout.size.height + 4.dp.toPx() + floatOffset

                withTransform({ scale(scale = scale, pivot = syllableLayout.wordPivot) }) {
                    drawText(
                        textLayoutResult = phoneticLayout,
                        color = drawColor.copy(alpha = 0.4f),
                        topLeft = Offset(phoneticX, phoneticY)
                    )
                }
            }
        } else {
            val driverLayout = if (syllableLayout.syllable.content.trim().isPunctuation()) {
                var searchIndex = index - 1
                while (searchIndex >= 0) {
                    val candidate = rowLayouts[searchIndex]
                    if (!candidate.syllable.content.trim().isPunctuation()) {
                        break
                    }
                    searchIndex--
                }
                if (searchIndex < 0) syllableLayout else rowLayouts[searchIndex]
            } else {
                syllableLayout
            }

            val animationFixedDuration = 700f
            val timeSinceStart = currentTimeMs - driverLayout.syllable.start
            val animationProgress = (timeSinceStart / animationFixedDuration).coerceIn(0f, 1f)

            val maxOffsetY = 4f
            val floatCurveValue =
                CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f).transform(1f - animationProgress)
            val floatOffset = maxOffsetY * floatCurveValue

            val finalPosition = syllableLayout.position.copy(
                y = syllableLayout.position.y + floatOffset
            )

            drawText(
                textLayoutResult = syllableLayout.textLayoutResult,
                color = drawColor,
                topLeft = finalPosition,
            )

            syllableLayout.phoneticLayoutResult?.let { phoneticLayout ->
                val phoneticX = syllableLayout.position.x
                val phoneticY = finalPosition.y - phoneticLayout.size.height + 4.dp.toPx()
                drawText(
                    textLayoutResult = phoneticLayout,
                    color = drawColor.copy(alpha = 0.4f),
                    topLeft = Offset(phoneticX, phoneticY),
                )
            }

            if (showDebugRectangles) {
                drawRect(
                    color = Color.Green, topLeft = finalPosition, size = Size(
                        syllableLayout.textLayoutResult.size.width.toFloat(),
                        syllableLayout.textLayoutResult.size.height.toFloat()
                    ), style = Stroke(2f)
                )
            }
        }
    }
}

/**
 * Renders a single karaoke line, capable of handling multi-row wrapping.
 *
 * This composable pre-calculates the text layout using [NativeTextEngine] and then
 * renders the frames using an efficient Canvas drawing strategy. It handles:
 * - Text measurement and line breaking
 * - Syllable and character-level animations (bounce, rise, swell)
 * - Karaoke fill gradient application
 *
 * @param line The karaoke line data.
 * @param currentTimeProvider Provider for the current playback time.
 * @param modifier Modifier for the layout.
 * @param normalLineTextStyle Style for normal lines.
 * @param accompanimentLineTextStyle Style for accompaniment lines.
 * @param phoneticTextStyle Style for phonetics.
 * @param activeColor Color for the active (sung) portion of text.
 * @param blendMode Blend mode for drawing.
 * @param showDebugRectangles Debug flag for layout bounds.
 * @param precalculatedLayouts Optional pre-calculated layouts (optimization).
 * @param isDuoView Whether this line is part of a duet view.
 * @param textMeasurer Text measurer for layout (default provided).
 */
@Composable
fun KaraokeLineText(
    line: KaraokeLine,
    currentTimeProvider: () -> Int,
    modifier: Modifier = Modifier,
    normalLineTextStyle: TextStyle = LocalTextStyle.current,
    accompanimentLineTextStyle: TextStyle = LocalTextStyle.current,
    phoneticTextStyle: TextStyle = LocalTextStyle.current,
    activeColor: Color = Color.White,
    blendMode: BlendMode = BlendMode.SrcOver,
    showDebugRectangles: Boolean = false,
    precalculatedLayouts: List<SyllableLayout>? = null,
    isDuoView: Boolean = false,
    textMeasurer: TextMeasurer = rememberTextMeasurer()
) {
    val isLineRtl = remember(line.syllables) { line.syllables.any { it.content.isRtl() } }

    val isRightAligned = remember(line.alignment, isLineRtl) {
        when (line.alignment) {
            KaraokeAlignment.Start, KaraokeAlignment.Unspecified -> isLineRtl
            KaraokeAlignment.End -> !isLineRtl
        }
    }

    val translationTextAlign = remember(isRightAligned) {
        if (isRightAligned) TextAlign.End else TextAlign.Start
    }

    val columnHorizontalAlignment = remember(isRightAligned) {
        if (isRightAligned) Alignment.End else Alignment.Start
    }

    val mainLine = line as? KaraokeLine.MainKaraokeLine
    val accompanimentLinesBeforeMain =
        mainLine?.accompanimentLines?.filter { it.start < line.start }.orEmpty()
    val accompanimentLinesAfterMain =
        mainLine?.accompanimentLines?.filter { it.start >= line.start }.orEmpty()

    @Composable
    fun AccompanimentLines(isBefore: Boolean, accompanimentLines: List<KaraokeLine>) {
        accompanimentLines.forEach { bgLine ->
            val isAccompanimentVisible by remember(bgLine) {
                derivedStateOf {
                    val currentTime = currentTimeProvider()
                    // Use a simplified visibility range for nested lines, or pass ranges down
                    currentTime >= (bgLine.start - 600) && currentTime <= (bgLine.end + 600)
                }
            }

            AnimatedVisibility(
                visible = isAccompanimentVisible,
                enter = scaleIn(
                    tween(600), transformOrigin = TransformOrigin(
                        if (isRightAligned) 1f else 0f, if (isBefore) 1f else 0f
                    )
                ) + fadeIn(tween(600)) + slideInVertically(
                    tween(
                        600
                    )
                ) + expandVertically(tween(600)),
                exit = scaleOut(
                    tween(600), transformOrigin = TransformOrigin(
                        if (isRightAligned) 1f else 0f, if (isBefore) 1f else 0f
                    )
                ) + fadeOut(tween(600)) + slideOutVertically(
                    tween(
                        600
                    )
                ) + shrinkVertically(tween(600)),
            ) {
                LyricsLineItem(
                    isFocused = true,
                    isRightAligned = isRightAligned,
                    onLineClicked = { },
                    onLinePressed = { },
                    blurRadius = { 0f },
                    blendMode = blendMode,
                    activeAlpha = 0.6f,
                    inactiveAlpha = 0.2f
                ) {
                    KaraokeLineText(
                        line = bgLine,
                        currentTimeProvider = currentTimeProvider,
                        normalLineTextStyle = normalLineTextStyle,
                        accompanimentLineTextStyle = accompanimentLineTextStyle,
                        phoneticTextStyle = phoneticTextStyle,
                        activeColor = activeColor,
                        blendMode = blendMode,
                        showDebugRectangles = showDebugRectangles,
                        textMeasurer = textMeasurer
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(
            vertical = 8.dp, horizontal = if (line is KaraokeLine.AccompanimentKaraokeLine) 0.dp
            else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = columnHorizontalAlignment
    ) {
        AccompanimentLines(true, accompanimentLinesBeforeMain)


        BoxWithConstraints {
            val density = LocalDensity.current
            val availableWidthPx = with(density) { maxWidth.toPx() }

            val textStyle = remember(line is KaraokeLine.AccompanimentKaraokeLine) {
                val baseStyle =
                    if (line is KaraokeLine.AccompanimentKaraokeLine) accompanimentLineTextStyle
                    else normalLineTextStyle
                baseStyle.copy(textDirection = TextDirection.Content)
            }

            val spaceWidth = remember(textMeasurer, textStyle) {
                textMeasurer.measure(" ", textStyle).size.width.toFloat()
            }

            val processedSyllables = remember(line.syllables, line.alignment) {
                if (line.alignment == KaraokeAlignment.End) {
                    line.syllables.dropLastWhile { it.content.isBlank() }
                } else {
                    line.syllables
                }
            }

            val initialLayouts by remember(precalculatedLayouts) {
                derivedStateOf {
                    precalculatedLayouts ?: measureSyllablesAndDetermineAnimation(
                        syllables = processedSyllables,
                        textMeasurer = textMeasurer,
                        style = textStyle,
                        phoneticStyle = phoneticTextStyle,
                        isAccompanimentLine = line is KaraokeLine.AccompanimentKaraokeLine,
                        spaceWidth = spaceWidth
                    )
                }
            }

            val wrappedLines by remember {
                derivedStateOf {
                    calculateBalancedLines(
                        syllableLayouts = initialLayouts,
                        availableWidthPx = availableWidthPx,
                        textMeasurer = textMeasurer,
                        style = textStyle
                    )
                }
            }

            val lineHeight = remember(textStyle) {
                textMeasurer.measure("M", textStyle).size.height.toFloat()
            }

            val phoneticHeight = remember(phoneticTextStyle) {
                textMeasurer.measure("M", phoneticTextStyle).size.height.toFloat()
            }

            val finalLineLayouts = remember(
                wrappedLines, availableWidthPx, lineHeight, isLineRtl, isRightAligned
            ) {
                calculateStaticLineLayout(
                    wrappedLines = wrappedLines,
                    isLineRightAligned = isRightAligned,
                    canvasWidth = availableWidthPx,
                    lineHeight = lineHeight,
                    phoneticHeight = phoneticHeight,
                    isRtl = isLineRtl
                )
            }

            val hasPhonetics = remember(initialLayouts) {
                initialLayouts.any { it.phoneticLayoutResult != null }
            }

            val totalHeight = remember(wrappedLines, lineHeight, hasPhonetics, phoneticHeight) {
                var height = lineHeight * wrappedLines.size
                if (hasPhonetics) {
                    height += phoneticHeight * wrappedLines.size
                }
                height
            }

            Canvas(modifier = Modifier.size(maxWidth, (totalHeight.roundToInt() + 8).toDp())) {
                val time = currentTimeProvider()
                drawLyricsLine(
                    lineLayouts = finalLineLayouts,
                    currentTimeMs = time,
                    color = activeColor,
                    blendMode = blendMode,
                    isRtl = isLineRtl,
                    showDebugRectangles = showDebugRectangles
                )
            }
        }

        line.translation?.let { translation ->
            Text(
                text = translation,
                color = activeColor.copy(0.4f),
                modifier = Modifier.graphicsLayer {
                    this.blendMode = blendMode
                },
                textAlign = translationTextAlign
            )
        }

        line.phonetic?.let { phonetic ->
            Text(
                text = phonetic,
                style = phoneticTextStyle,
                color = activeColor.copy(alpha = 0.6f),
                modifier = Modifier.graphicsLayer {
                    this.blendMode = blendMode
                },
                textAlign = translationTextAlign
            )
        }
        AccompanimentLines(false, accompanimentLinesAfterMain)
    }
}

@Composable
private fun Int.toDp(): Dp = with(LocalDensity.current) { this@toDp.toDp() }
