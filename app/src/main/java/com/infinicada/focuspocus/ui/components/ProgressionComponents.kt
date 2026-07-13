package com.infinicada.focuspocus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.infinicada.focuspocus.R
import com.infinicada.focuspocus.model.LedgerKind
import com.infinicada.focuspocus.model.ManaLedgerEntry
import com.infinicada.focuspocus.model.Sigil
import com.infinicada.focuspocus.model.Trial
import com.infinicada.focuspocus.model.TrialPeriod
import com.infinicada.focuspocus.model.TrialType

/** Display title for a trial, derived from its type/target so persisted state stays locale-free. */
@Composable
fun trialTitle(trial: Trial): String = when (trial.type) {
    TrialType.COMPLETE_SESSIONS ->
        pluralStringResource(R.plurals.trial_sessions_daily, trial.target, trial.target)
    TrialType.FOCUS_MINUTES ->
        if (trial.period == TrialPeriod.DAILY) stringResource(R.string.trial_minutes_daily, trial.target)
        else stringResource(R.string.trial_minutes_weekly, trial.target)
    TrialType.COMPLETE_RITUAL -> stringResource(R.string.trial_ritual_daily)
    TrialType.STAY_UNDER_LIMITS -> stringResource(R.string.trial_under_limits_daily)
    TrialType.NO_REFLEX_OPENS -> stringResource(R.string.trial_reflex_weekly, trial.param)
}

/** Localized one-line reason for a ledger entry. */
@Composable
fun ledgerReason(entry: ManaLedgerEntry): String = when (entry.kind) {
    LedgerKind.SESSION -> stringResource(R.string.ledger_session_reason, entry.minutes)
    LedgerKind.TRIAL -> stringResource(R.string.ledger_trial_reason)
    LedgerKind.BOON -> stringResource(R.string.ledger_boon_reason, entry.title)
    LedgerKind.PERK ->
        if (entry.refId == com.infinicada.focuspocus.model.Perk.SEALED_MINUTES.name)
            stringResource(R.string.ledger_perk_sealed_minutes)
        else stringResource(R.string.ledger_perk_extra_break)
    LedgerKind.MILESTONE -> stringResource(R.string.ledger_milestone_reason)
    LedgerKind.SIGIL -> stringResource(R.string.ledger_sigil_reason)
}

/** Gold mana-balance chip shown next to the streak badge on Home. */
@Composable
fun ManaChip(balance: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                stringResource(R.string.mana_chip, balance),
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.mana_chip_desc, balance),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = modifier
    )
}

/**
 * One trial with its progress bar and, once complete, a Claim button. Rollover-
 * judged trials show a "judged at period end" hint instead of live progress.
 */
@Composable
fun TrialRow(
    trial: Trial,
    onClaim: (Trial) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = trialTitle(trial)
    val judgedAtEnd = trial.type == TrialType.STAY_UNDER_LIMITS || trial.type == TrialType.NO_REFLEX_OPENS
    val progressText = stringResource(R.string.trial_progress, trial.progress, trial.target)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = if (trial.claimed) "$title, claimed" else "$title, $progressText"
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = when {
                        judgedAtEnd && !trial.completed ->
                            if (trial.period == TrialPeriod.DAILY) stringResource(R.string.trial_judged_day_end)
                            else stringResource(R.string.trial_judged_week_end)
                        else -> progressText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                trial.claimed -> Text(
                    stringResource(R.string.trial_claimed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                trial.completed -> TextButton(onClick = { onClaim(trial) }) {
                    Text(stringResource(R.string.trial_claim) + " " + stringResource(R.string.trial_reward, trial.rewardMana))
                }
                else -> Text(
                    stringResource(R.string.trial_reward, trial.rewardMana),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = {
                if (trial.target > 0) (trial.progress.toFloat() / trial.target).coerceIn(0f, 1f) else 0f
            },
            modifier = Modifier.fillMaxWidth(),
            color = if (trial.completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
        )
    }
}

/** A sigil tile: gold and lit when unlocked, dimmed with its hint when locked. */
@Composable
fun SigilTile(
    sigil: Sigil,
    unlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val lockedText = stringResource(R.string.sigil_state_locked)
    val unlockedText = stringResource(R.string.sigil_state_unlocked)
    GlassCard(
        modifier = modifier
            .alpha(if (unlocked) 1f else 0.45f)
            .semantics(mergeDescendants = true) {
                stateDescription = if (unlocked) unlockedText else lockedText
            },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = if (unlocked) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(sigil.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = if (unlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                stringResource(sigil.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
