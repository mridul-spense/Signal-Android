/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.toLiveData
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Observable
import org.signal.core.ui.Rows
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Renders information about a call (1:1, group, or call link) and provides actions available for
 * said call (e.g., raise hand, kick, etc)
 */
object CallInfoView {

  @Composable
  fun View(webRtcCallViewModel: WebRtcCallViewModel, controlsAndInfoViewModel: ControlsAndInfoViewModel, modifier: Modifier) {
    val participantsState: ParticipantsState by webRtcCallViewModel.callParticipantsState
      .toFlowable(BackpressureStrategy.LATEST)
      .map { state ->
        ParticipantsState(
          inCallLobby = state.callState == WebRtcViewModel.State.CALL_PRE_JOIN,
          ringGroup = state.ringGroup,
          includeSelf = state.groupCallState === WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED || state.groupCallState === WebRtcViewModel.GroupCallState.IDLE,
          participantCount = if (state.participantCount.isPresent) state.participantCount.asLong.toInt() else 0,
          remoteParticipants = state.allRemoteParticipants.sortedBy { it.callParticipantId.recipientId },
          localParticipant = state.localParticipant,
          groupMembers = state.groupMembers.filterNot { it.member.isSelf },
          callRecipient = state.recipient
        )
      }
      .subscribeAsState(ParticipantsState())

    val controlAndInfoState: ControlAndInfoState by controlsAndInfoViewModel.state

    SignalTheme(
      isDarkMode = true
    ) {
      Surface {
        CallInfo(participantsState = participantsState, controlAndInfoState = controlAndInfoState, modifier = modifier)
      }
    }
  }
}

@Preview
@Composable
private fun CallInfoPreview() {
  SignalTheme(isDarkMode = true) {
    Surface {
      CallInfo(
        participantsState = ParticipantsState(remoteParticipants = listOf(CallParticipant(recipient = Recipient.UNKNOWN))),
        controlAndInfoState = ControlAndInfoState()
      )
    }
  }
}

@Composable
private fun CallInfo(
  participantsState: ParticipantsState,
  controlAndInfoState: ControlAndInfoState,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  LaunchedEffect(controlAndInfoState.resetScrollState) {
    listState.scrollToItem(0)
  }

  LazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    item {
      Text(
        text = stringResource(id = R.string.CallLinkInfoSheet__call_info),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 24.dp)
      )
    }

    item {
      Box(
        modifier = Modifier
          .padding(horizontal = 24.dp)
          .defaultMinSize(minHeight = 52.dp)
          .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
      ) {
        Text(
          text = getCallSheetLabel(participantsState),
          style = MaterialTheme.typography.titleSmall
        )
      }
    }

    if (!participantsState.inCallLobby || participantsState.isOngoing()) {
      items(
        items = participantsState.participantsForList,
        key = { it.callParticipantId },
        contentType = { null }
      ) {
        CallParticipantRow(
          callParticipant = it,
          isSelfAdmin = false,
          onBlockClicked = {}
        )
      }
    } else if (participantsState.isGroupCall()) {
      items(
        items = participantsState.groupMembers,
        key = { it.member.id.toLong() },
        contentType = { null }
      ) {
        GroupMemberRow(
          groupMember = it,
          isSelfAdmin = false
        )
      }
    } else {
      item {
        CallParticipantRow(
          initialRecipient = participantsState.callRecipient,
          name = participantsState.callRecipient.getShortDisplayName(LocalContext.current),
          showIcons = false,
          isVideoEnabled = false,
          isMicrophoneEnabled = false,
          isSelfAdmin = false,
          onBlockClicked = {}
        )
      }
    }

    item {
      Spacer(modifier = Modifier.size(48.dp))
    }
  }
}

@Composable
private fun getCallSheetLabel(state: ParticipantsState): String {
  return if (!state.inCallLobby || state.isOngoing()) {
    pluralStringResource(id = R.plurals.CallParticipantsListDialog_in_this_call, count = state.participantCountForDisplay, state.participantCountForDisplay)
  } else if (state.isGroupCall()) {
    val groupSize = state.groupMembers.size
    if (state.ringGroup) {
      pluralStringResource(id = R.plurals.CallParticipantsListDialog__signal_will_ring, count = groupSize, groupSize)
    } else {
      pluralStringResource(id = R.plurals.CallParticipantsListDialog__signal_will_notify, count = groupSize, groupSize)
    }
  } else {
    pluralStringResource(id = R.plurals.CallParticipantsListDialog__signal_will_ring, count = 1, 1)
  }
}

@Preview
@Composable
private fun CallParticipantRowPreview() {
  SignalTheme(isDarkMode = true) {
    Surface {
      CallParticipantRow(
        CallParticipant(recipient = Recipient.UNKNOWN),
        isSelfAdmin = true
      ) {}
    }
  }
}

@Composable
private fun CallParticipantRow(
  callParticipant: CallParticipant,
  isSelfAdmin: Boolean,
  onBlockClicked: (CallParticipant) -> Unit
) {
  CallParticipantRow(
    initialRecipient = callParticipant.recipient,
    name = callParticipant.getShortRecipientDisplayName(LocalContext.current),
    showIcons = true,
    isVideoEnabled = callParticipant.isVideoEnabled,
    isMicrophoneEnabled = callParticipant.isMicrophoneEnabled,
    isSelfAdmin = isSelfAdmin,
    onBlockClicked = { onBlockClicked(callParticipant) }
  )
}

@Composable
private fun CallParticipantRow(
  initialRecipient: Recipient,
  name: String,
  showIcons: Boolean,
  isVideoEnabled: Boolean,
  isMicrophoneEnabled: Boolean,
  isSelfAdmin: Boolean,
  onBlockClicked: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Rows.defaultPadding())
  ) {
    val recipient by ((if (LocalInspectionMode.current) Observable.just(Recipient.UNKNOWN) else Recipient.observable(initialRecipient.id)))
      .toFlowable(BackpressureStrategy.LATEST)
      .toLiveData()
      .observeAsState(initial = initialRecipient)

    if (LocalInspectionMode.current) {
      Spacer(
        modifier = Modifier
          .size(40.dp)
          .background(color = Color.Red, shape = CircleShape)
      )
    } else {
      AndroidView(
        factory = ::AvatarImageView,
        modifier = Modifier.size(40.dp)
      ) {
        it.setAvatarUsingProfile(recipient)
      }
    }

    Spacer(modifier = Modifier.width(24.dp))

    Text(
      text = name,
      modifier = Modifier
        .weight(1f)
        .align(Alignment.CenterVertically)
    )

    if (showIcons && !isVideoEnabled) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_video_slash_24),
        contentDescription = null,
        modifier = Modifier.align(Alignment.CenterVertically)
      )
    }

    if (showIcons && !isMicrophoneEnabled) {
      if (!isVideoEnabled) {
        Spacer(modifier = Modifier.width(16.dp))
      }

      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_mic_slash_24),
        contentDescription = null,
        modifier = Modifier.align(Alignment.CenterVertically)
      )
    }

    if (showIcons && isSelfAdmin && !recipient.isSelf) {
      if (!isMicrophoneEnabled) {
        Spacer(modifier = Modifier.width(16.dp))
      }

      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_minus_circle_24),
        contentDescription = null,
        modifier = Modifier
          .clickable(onClick = onBlockClicked)
          .align(Alignment.CenterVertically)
      )
    }
  }
}

@Composable
private fun GroupMemberRow(
  groupMember: GroupMemberEntry.FullMember,
  isSelfAdmin: Boolean
) {
  CallParticipantRow(
    initialRecipient = groupMember.member,
    name = groupMember.member.getShortDisplayName(LocalContext.current),
    showIcons = false,
    isVideoEnabled = false,
    isMicrophoneEnabled = false,
    isSelfAdmin = isSelfAdmin
  ) {}
}

private data class ParticipantsState(
  val inCallLobby: Boolean = false,
  val ringGroup: Boolean = true,
  val includeSelf: Boolean = false,
  val participantCount: Int = 0,
  val remoteParticipants: List<CallParticipant> = emptyList(),
  val localParticipant: CallParticipant? = null,
  val groupMembers: List<GroupMemberEntry.FullMember> = emptyList(),
  val callRecipient: Recipient = Recipient.UNKNOWN
) {

  val participantsForList: List<CallParticipant> = if (includeSelf && localParticipant != null) {
    listOf(localParticipant) + remoteParticipants
  } else {
    remoteParticipants
  }

  val participantCountForDisplay: Int = if (participantCount == 0) {
    participantsForList.size
  } else {
    participantCount
  }

  fun isGroupCall(): Boolean {
    return groupMembers.isNotEmpty()
  }

  fun isOngoing(): Boolean {
    return remoteParticipants.isNotEmpty()
  }
}
