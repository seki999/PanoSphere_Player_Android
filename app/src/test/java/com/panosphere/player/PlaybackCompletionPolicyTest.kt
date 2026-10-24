package com.panosphere.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompletionPolicyTest {
 @Test fun videoStopsEvenWhenAnotherItemExists() {
  for(hasNext in listOf(false,true)) assertEquals(CompletionAction.STOP,PlaybackCompletionPolicy.decide(false,Player.REPEAT_MODE_OFF,hasNext))
 }
 @Test fun staleListLoopCannotAdvanceVideo() {
  for(hasNext in listOf(false,true)) assertEquals(CompletionAction.STOP,PlaybackCompletionPolicy.decide(false,Player.REPEAT_MODE_ALL,hasNext))
 }
 @Test fun singleVideoLoopRemainsAvailable() {
  for(hasNext in listOf(false,true)) assertEquals(CompletionAction.REPEAT_CURRENT,PlaybackCompletionPolicy.decide(false,Player.REPEAT_MODE_ONE,hasNext))
 }
 @Test fun musicKeepsItsQueueBehavior() {
  assertEquals(CompletionAction.ADVANCE,PlaybackCompletionPolicy.decide(true,Player.REPEAT_MODE_OFF,true))
  assertEquals(CompletionAction.STOP,PlaybackCompletionPolicy.decide(true,Player.REPEAT_MODE_OFF,false))
  assertEquals(CompletionAction.ADVANCE,PlaybackCompletionPolicy.decide(true,Player.REPEAT_MODE_ALL,false))
  assertEquals(CompletionAction.REPEAT_CURRENT,PlaybackCompletionPolicy.decide(true,Player.REPEAT_MODE_ONE,true))
 }
}
