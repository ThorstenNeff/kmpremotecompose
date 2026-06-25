package com.tneff.kmpremotecompose

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform