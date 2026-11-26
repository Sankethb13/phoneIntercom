package com.intercom.ride.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.intercom.ride.audio.AudioQuality
import com.intercom.ride.core.ConnectionRequest
import com.intercom.ride.core.ConnectionState
import com.intercom.ride.core.LinkQuality
import com.intercom.ride.core.PeerDevice
import com.intercom.ride.core.Role
import com.intercom.ride.permissions.PermissionRequirements
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- shared bits

@Composable
private fun Header(title: String, onBack: (() -> Unit)? = null, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(IcColors.Surface)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = IcColors.Muted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = IcColors.Accent, contentColor = Color.Black),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun Logo(size: Int = 112) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "s")
    Box(Modifier.size((size * 1.4).dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).scale(s).clip(CircleShape).background(IcColors.AccentDim))
        Box(
            Modifier.size((size * 0.72).dp).clip(CircleShape).background(IcColors.Accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Headset, null, tint = Color.Black, modifier = Modifier.size((size * 0.38).dp)) }
    }
}

// ---------------------------------------------------------------- 1. Welcome

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Logo()
        Spacer(Modifier.height(24.dp))
        Text("Intercom", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Talk rider-to-pillion, phone-to-phone.\nNo internet, no SIM, no servers — just Bluetooth and local Wi-Fi between your two phones.",
            style = MaterialTheme.typography.bodyLarge, color = IcColors.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Full-duplex"); Pill("Encrypted"); Pill("Offline")
        }
        Spacer(Modifier.weight(1.2f))
        PrimaryButton("Start", onClick = onStart)
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = IcColors.Accent,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(IcColors.AccentDim)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// ---------------------------------------------------------------- 2. Role

@Composable
fun RoleScreen(vm: IntercomViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(settings.role ?: Role.RIDER) }
    Column(Modifier.fillMaxSize()) {
        Header("Who's on this phone?", onBack = vm::back)
        Column(Modifier.padding(horizontal = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Both phones can talk and listen at the same time. The role just helps you tell them apart.",
                color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
            )
            RoleCard(Role.RIDER, Icons.Filled.TwoWheeler, "Riding the bike", selected == Role.RIDER) { selected = Role.RIDER }
            RoleCard(Role.PILLION, Icons.Filled.EventSeat, "Sitting behind the rider", selected == Role.PILLION) { selected = Role.PILLION }
        }
        Box(Modifier.padding(20.dp)) { PrimaryButton("Continue") { vm.chooseRole(selected) } }
    }
}

@Composable
private fun RoleCard(role: Role, icon: ImageVector, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(IcColors.Surface)
            .border(BorderStroke(2.dp, if (selected) IcColors.Accent else Color.Transparent), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(if (selected) IcColors.Accent else IcColors.SurfaceHi),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (selected) Color.Black else IcColors.Text) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(role.label, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

// ---------------------------------------------------------------- 3. Nearby devices

@Composable
fun NearbyScreen(vm: IntercomViewModel, actions: SystemActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val warning by vm.warning.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val digits by vm.authDigits.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Header("Nearby riders", onBack = vm::back) {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh discovery") }
            IconButton(onClick = { vm.go(Screen.SETTINGS) }) { Icon(Icons.Filled.Settings, "Settings") }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "You appear as “${settings.deviceName}” (${settings.role?.label ?: "Rider"}). Open Intercom on the other phone too.",
                color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            warning?.let { w ->
                Banner(w.message, IcColors.Warn, errorAction(w, actions), onDismiss = vm::dismissWarning)
                Spacer(Modifier.height(12.dp))
            }
            when (val s = state) {
                is ConnectionState.Failed -> {
                    Banner(s.error.message, IcColors.Bad, errorAction(s.error, actions) ?: ("Try again" to vm::refresh), null)
                    if (s.error is com.intercom.ride.core.IntercomError.PermissionDenied) {
                        TextButton(onClick = actions.openAppSettings) { Text("Open app settings") }
                    }
                }
                is ConnectionState.Requesting -> Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Waiting for ${s.peer.name}…", style = MaterialTheme.typography.titleMedium)
                            Text(
                                digits?.let { "Check both phones show code $it" } ?: "Ask them to tap Accept",
                                color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(onClick = vm::disconnect) { Text("Cancel") }
                    }
                }
                else -> DiscoveryStatus(state is ConnectionState.Discovering)
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        ) {
            items(peers, key = { it.deviceId }) { p ->
                PeerRow(p, enabled = state is ConnectionState.Discovering) { vm.connect(p) }
            }
            if (peers.isEmpty() && state is ConnectionState.Discovering) {
                item {
                    Text(
                        "No phones found yet. Keep both phones within a few metres with Bluetooth on.",
                        color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryStatus(active: Boolean) {
    val t = rememberInfiniteTransition(label = "scan")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) IcColors.Good.copy(alpha = a) else IcColors.Muted))
        Spacer(Modifier.width(10.dp))
        Text(
            if (active) "Searching nearby over Bluetooth & Wi-Fi…" else "Discovery paused",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PeerRow(p: PeerDevice, enabled: Boolean, onConnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IcColors.Surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(IcColors.SurfaceHi), contentAlignment = Alignment.Center) {
            Icon(if (p.role == Role.RIDER) Icons.Filled.TwoWheeler else Icons.Filled.EventSeat, null, tint = IcColors.Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            Text("${p.role.label} · available", color = IcColors.Good, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = onConnect, enabled = enabled, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IcColors.Accent, contentColor = Color.Black),
        ) { Text("Connect") }
    }
}

@Composable
private fun Banner(text: String, color: Color, action: Pair<String, () -> Unit>?, onDismiss: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            onDismiss?.let { TextButton(onClick = it) { Text("Dismiss", color = IcColors.Muted) } }
            action?.let { (label, fn) -> TextButton(onClick = fn) { Text(label, color = color) } }
        }
    }
}

// ---------------------------------------------------------------- 4. Connection request

@Composable
fun ConnectionRequestDialog(request: ConnectionRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = IcColors.Surface,
        icon = { Icon(Icons.Filled.WifiTethering, null, tint = IcColors.Accent) },
        title = { Text("${request.name} wants to talk") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("${request.role.label} · connection request", color = IcColors.Muted)
                Spacer(Modifier.height(18.dp))
                Text("Security code", style = MaterialTheme.typography.labelSmall, color = IcColors.Muted)
                Text(
                    request.authDigits, fontSize = 40.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace, letterSpacing = 8.sp, color = IcColors.Accent,
                )
                Text(
                    "Accept only if the other phone shows the same code.",
                    color = IcColors.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = IcColors.Good, contentColor = Color.Black),
            ) { Text("Accept") }
        },
        dismissButton = { OutlinedButton(onClick = onReject) { Text("Reject") } },
    )
}

// ---------------------------------------------------------------- 5. Active intercom

@Composable
fun ActiveIntercomScreen(vm: IntercomViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val peerMuted by vm.peerMuted.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val mic by vm.micLevel.collectAsStateWithLifecycle()
    val spk by vm.speakerLevel.collectAsStateWithLifecycle()
    val interrupted by vm.interrupted.collectAsStateWithLifecycle()
    val warning by vm.warning.collectAsStateWithLifecycle()

    val s = state
    val peerName = when (s) {
        is ConnectionState.Connected -> s.peerName
        is ConnectionState.Reconnecting -> s.peerName
        is ConnectionState.Authenticating -> s.peerName
        else -> ""
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (s) {
                        is ConnectionState.Connected -> "CONNECTED"
                        is ConnectionState.Reconnecting -> "RECONNECTING · TRY ${s.attempt}"
                        else -> "SECURING LINK"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (s is ConnectionState.Connected) IcColors.Good else IcColors.Warn,
                )
                Text(peerName, style = MaterialTheme.typography.displaySmall, maxLines = 1)
                if (s is ConnectionState.Connected) {
                    Text(formatDuration(now - s.sinceMs) + " · " + s.peerRole.label, color = IcColors.Muted)
                }
            }
            IconButton(onClick = { vm.go(Screen.SETTINGS) }) { Icon(Icons.Filled.Settings, "Settings") }
        }
        Spacer(Modifier.height(16.dp))

        if (interrupted) {
            Banner("Paused for a phone call or another audio app. Intercom resumes automatically.", IcColors.Warn, null, null)
            Spacer(Modifier.height(10.dp))
        }
        warning?.let { Banner(it.message, IcColors.Warn, null, vm::dismissWarning); Spacer(Modifier.height(10.dp)) }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Link quality", Modifier.weight(1f), color = IcColors.Muted)
                    val (bars, color, label) = qualityVisual(if (s is ConnectionState.Connected) stats.quality else LinkQuality.POOR)
                    Text(label, color = color, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    SignalBars(bars, color)
                }
                if (stats.rttMs >= 0) {
                    Text(
                        "${stats.rttMs} ms round trip · ${"%.1f".format(stats.packetLossPct)}% loss · ${stats.jitterDepthMs} ms buffer",
                        color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(if (muted) "You (muted)" else "You", color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium)
                LevelMeter(if (muted) 0f else mic, IcColors.Accent)
                Text(
                    if (peerMuted) "$peerName (muted)" else peerName.ifEmpty { "Peer" },
                    color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
                )
                LevelMeter(spk, IcColors.Good)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        route.isBluetooth -> Icons.Filled.BluetoothAudio
                        route.isHeadset -> Icons.Filled.Headset
                        else -> Icons.Filled.SpeakerPhone
                    },
                    null, tint = if (route.isHeadset) IcColors.Good else IcColors.Warn,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(route.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (route.isHeadset) "Headset connected" else "No headset — using phone speaker",
                        color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = vm::toggleMute,
                modifier = Modifier.weight(1f).height(84.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (muted) IcColors.Warn else IcColors.SurfaceHi,
                    contentColor = if (muted) Color.Black else IcColors.Text,
                ),
            ) {
                Icon(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, null, Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (muted) "Unmute" else "Mute", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = vm::disconnect,
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = IcColors.Bad, contentColor = Color.White),
            ) { Icon(Icons.Filled.CallEnd, "Disconnect", Modifier.size(34.dp)) }
        }
    }
}

private fun qualityVisual(q: LinkQuality): Triple<Int, Color, String> = when (q) {
    LinkQuality.EXCELLENT -> Triple(4, IcColors.Good, "Excellent")
    LinkQuality.GOOD -> Triple(3, IcColors.Good, "Good")
    LinkQuality.FAIR -> Triple(2, IcColors.Warn, "Fair")
    LinkQuality.POOR -> Triple(1, IcColors.Bad, "Poor")
    LinkQuality.UNKNOWN -> Triple(0, IcColors.Muted, "Measuring…")
}

fun formatDuration(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

// ---------------------------------------------------------------- 6. Settings

@Composable
fun SettingsScreen(vm: IntercomViewModel, actions: SystemActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val perms = vm.session.permissions
    var name by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
    var refreshTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(2000); refreshTick++ } }

    Column(Modifier.fillMaxSize()) {
        Header("Settings", onBack = vm::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            SectionLabel("This phone")
            Card {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(24) },
                        label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSessionActive,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.setName(name) }, enabled = name != settings.deviceName) { Text("Save") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Role", Modifier.weight(1f))
                        Role.entries.forEach { r ->
                            RadioButton(selected = settings.role == r, onClick = { vm.setRole(r) }, enabled = !state.isSessionActive)
                            Text(r.label, Modifier.padding(end = 8.dp))
                        }
                    }
                }
            }

            SectionLabel("Audio quality")
            Card {
                Column {
                    AudioQuality.entries.forEach { q ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setQuality(q) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.quality == q, onClick = { vm.setQuality(q) })
                            Text(q.label)
                        }
                    }
                    Text(
                        "Applies to the next connection. Phones without an Opus encoder use G.711 automatically.",
                        color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionLabel("Behaviour")
            Card {
                Column {
                    ToggleRow("Noise suppression", "Reduce wind and engine noise where the phone supports it", settings.noiseSuppression, vm::setNoiseSuppression)
                    ToggleRow("Automatic reconnection", "Re-link silently after brief dropouts", settings.autoReconnect, vm::setAutoReconnect)
                }
            }

            SectionLabel("Audio output")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Route", route.name)
                    InfoRow("Codec", stats.codec)
                    InfoRow("Voice processing", vm.session.audio.effects)
                }
            }

            SectionLabel("Permissions")
            Card {
                key(refreshTick) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val missing = perms.missing()
                    perms.required.map(PermissionRequirements::friendlyName).distinct().forEach { label ->
                        val ok = perms.required.filter { PermissionRequirements.friendlyName(it) == label }.none { it in missing }
                        InfoRow(label, if (ok) "Granted" else "Missing", if (ok) IcColors.Good else IcColors.Bad)
                    }
                    InfoRow("Bluetooth", if (perms.isBluetoothOn()) "On" else "Off", if (perms.isBluetoothOn()) IcColors.Good else IcColors.Bad)
                    InfoRow("Wi-Fi", if (perms.isWifiOn()) "On" else "Off (optional)", if (perms.isWifiOn()) IcColors.Good else IcColors.Warn)
                    InfoRow(
                        "Battery optimisation",
                        if (perms.isIgnoringBatteryOptimizations()) "Unrestricted" else "Restricted",
                        if (perms.isIgnoringBatteryOptimizations()) IcColors.Good else IcColors.Warn,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = actions.requestPermissions) { Text("Grant") }
                        OutlinedButton(onClick = actions.openAppSettings) { Text("App settings") }
                    }
                    if (!perms.isIgnoringBatteryOptimizations()) {
                        OutlinedButton(onClick = actions.requestBatteryExemption, modifier = Modifier.fillMaxWidth()) {
                            Text("Allow running in background")
                        }
                    }
                } }
            }

            SectionLabel("Troubleshooting")
            Card {
                Text(
                    "• Keep both phones' Bluetooth on; Wi-Fi on gives better range and audio.\n" +
                        "• Android 10–12: Location must be switched on for discovery.\n" +
                        "• Pair your helmet headset with the phone in Android Bluetooth settings first. It must support calls (HFP).\n" +
                        "• If audio cuts out with the screen off, set battery usage for Intercom to Unrestricted.\n" +
                        "• If phones don't see each other, tap Refresh on both, or restart Bluetooth.\n" +
                        "• Brands like Xiaomi, Oppo and Vivo may also need ‘Autostart’ enabled.",
                    color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = IcColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = IcColors.Text) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = IcColors.Muted)
        Text(value, color = color, textAlign = TextAlign.End)
    }
}
