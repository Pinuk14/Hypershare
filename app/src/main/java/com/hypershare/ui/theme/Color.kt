package com.hypershare.ui.theme

import androidx.compose.ui.graphics.Color

// Surface Colors (docs/Theme.md)
val BackgroundBase = Color(0xFF0A0A0F)     // Deep space black
val SurfaceCard = Color(0xFF16161F)        // Slightly lifted surface
val GlassOverlay = Color(0x0DFFFFFF)       // 5% white frost layer
val GlassBorder = Color(0x14FFFFFF)        // 8% white rim light
val SurfaceElevated = Color(0xFF1E1E2D)    // Modals, sheets

// Accent Colors
val SignalBlue = Color(0xFF3B82F6)         // Primary CTA, Mode 1
val SignalBlueDim = Color(0xFF1D4ED8)      // Pressed state
val MeshTeal = Color(0xFF14B8A6)           // Mode 2 / Disaster mode
val MeshTealGlow = Color(0x2614B8A6)       // Status halo

// State Colors
val ConnectedGreen = Color(0xFF22C55E)     // Peer connected
val WarningAmber = Color(0xFFF59E0B)       // Weak signal, retrying
val ErrorRed = Color(0xFFEF4444)           // Peer lost, error state
val RelayPurple = Color(0xFFA855F7)        // Relaying for other node
val OfflineGray = Color(0xFF374151)         // Peer in list, unreached

// Text Colors
val TextPrimary = Color(0xFFF1F5F9)        // Main content
val TextSecondary = Color(0xFF94A3B8)      // Metadata, timestamps
val TextDisabled = Color(0xFF475569)       // Inactive / grayed
