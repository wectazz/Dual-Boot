package com.dualboot.dsu.model

enum class InstallationStep {
    IDLE,
    DECOMPRESSING,
    CREATING_PARTITION,
    INSTALLING,
    FINISHING,
    SUCCESS,
    ERROR,
}
