package com.jhaago.cadagent.ui.common

import com.jhaago.cadagent.model.JobState
import com.jhaago.cadagent.ui.components.jobStatePresentation

fun JobState.displayName(): String = jobStatePresentation(this).label
