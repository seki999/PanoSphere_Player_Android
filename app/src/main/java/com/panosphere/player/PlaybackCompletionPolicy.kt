package com.panosphere.player

import androidx.media3.common.Player

enum class CompletionAction { REPEAT_CURRENT, ADVANCE, STOP }

/** Only music may advance automatically. Video can stop or repeat its current item. */
object PlaybackCompletionPolicy {
 fun decide(music: Boolean, repeatMode: Int, hasNext: Boolean): CompletionAction = when {
  repeatMode == Player.REPEAT_MODE_ONE -> CompletionAction.REPEAT_CURRENT
  music && (hasNext || repeatMode == Player.REPEAT_MODE_ALL) -> CompletionAction.ADVANCE
  else -> CompletionAction.STOP
 }
}
