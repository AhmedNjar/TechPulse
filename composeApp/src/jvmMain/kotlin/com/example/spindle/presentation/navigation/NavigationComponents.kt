package com.example.spindle.presentation.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

enum class NavigationItem(val label: String, val icon: String) {
    DASHBOARD       ("Dashboard",       "⊡"),
    SIMULATION_SETUP("Simulation Setup","⚙"),
    ANALYTICS       ("Analytics",       "⊞"),
    SOBOL           ("Sobol Analysis",  "⌥"),
    OPTIMISATION    ("Optimisation",    "◎"),
    SETTINGS        ("Settings",        "≡")
}

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>
    fun onNavigate(item: NavigationItem)
    sealed interface Child {
        data object Dashboard       : Child
        data object SimulationSetup : Child
        data object Analytics       : Child
        data object Sobol           : Child
        data object Optimisation    : Child
        data object Settings        : Child
    }
}

class DefaultRootComponent(componentContext: ComponentContext)
    : RootComponent, ComponentContext by componentContext {
    private val navigation = StackNavigation<Config>()
    @Serializable private sealed interface Config {
        @Serializable data object Dashboard       : Config
        @Serializable data object SimulationSetup : Config
        @Serializable data object Analytics       : Config
        @Serializable data object Sobol           : Config
        @Serializable data object Optimisation    : Config
        @Serializable data object Settings        : Config
    }
    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation, serializer = Config.serializer(),
        initialConfiguration = Config.Dashboard, handleBackButton = true,
        childFactory = ::createChild
    )
    override fun onNavigate(item: NavigationItem) {
        navigation.bringToFront(when (item) {
            NavigationItem.DASHBOARD        -> Config.Dashboard
            NavigationItem.SIMULATION_SETUP -> Config.SimulationSetup
            NavigationItem.ANALYTICS        -> Config.Analytics
            NavigationItem.SOBOL            -> Config.Sobol
            NavigationItem.OPTIMISATION     -> Config.Optimisation
            NavigationItem.SETTINGS         -> Config.Settings
        })
    }
    private fun createChild(config: Config, @Suppress("UNUSED_PARAMETER") ctx: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Dashboard       -> RootComponent.Child.Dashboard
            Config.SimulationSetup -> RootComponent.Child.SimulationSetup
            Config.Analytics       -> RootComponent.Child.Analytics
            Config.Sobol           -> RootComponent.Child.Sobol
            Config.Optimisation    -> RootComponent.Child.Optimisation
            Config.Settings        -> RootComponent.Child.Settings
        }
}
