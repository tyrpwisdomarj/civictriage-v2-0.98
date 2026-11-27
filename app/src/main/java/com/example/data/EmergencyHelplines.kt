package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CivicAmber
import com.example.ui.theme.CivicCrimson
import com.example.ui.theme.CivicEmerald
import com.example.ui.theme.CivicRose
import com.example.ui.theme.CivicSky

data class EmergencyHelpline(
    val number: String,
    val title: String,
    val department: String,
    val badgeColor: Color
)

object KeralaEmergencyHelplines {
    val all: List<EmergencyHelpline> = listOf(
        EmergencyHelpline(
            number = "112",
            title = "Police / ERSS",
            department = "24×7 Emergency Response",
            badgeColor = CivicCrimson
        ),
        EmergencyHelpline(
            number = "1930",
            title = "Cyber Fraud",
            department = "Kerala Police Cyber Cell",
            badgeColor = CivicRose
        ),
        EmergencyHelpline(
            number = "1912",
            title = "KSEB Power",
            department = "Live Wire & Outage Desk",
            badgeColor = CivicAmber
        ),
        EmergencyHelpline(
            number = "1916",
            title = "KWA Water",
            department = "Water Supply & Pipe Burst",
            badgeColor = CivicSky
        ),
        EmergencyHelpline(
            number = "1077",
            title = "Disaster Room",
            department = "District Collectorate EOC",
            badgeColor = Color(0xFF7C3AED)
        ),
        EmergencyHelpline(
            number = "108",
            title = "Kaniv Ambulance",
            department = "Emergency Medical Dispatch",
            badgeColor = CivicEmerald
        ),
        EmergencyHelpline(
            number = "1056",
            title = "Disha Health",
            department = "24×7 Kerala Health Helpline",
            badgeColor = Color(0xFF0D9488)
        )
    )
}
