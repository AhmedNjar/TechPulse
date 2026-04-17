package com.example.spindle.di

import com.example.spindle.data.network.SpindleApiClient
import com.example.spindle.data.repository.SimulationRepository
import com.example.spindle.data.repository.SimulationRepositoryImpl
import com.example.spindle.domain.usecases.RunSimulationUseCase
import com.example.spindle.domain.usecases.StartOptimisationUseCase
import com.example.spindle.presentation.viewmodels.*
import org.koin.dsl.module

val appModule = module {

    // Network
    single {
        SpindleApiClient(
            baseUrl = System.getenv("SPINDLE_ENGINE_URL") ?: "http://127.0.0.1:8000"
        )
    }

    // Repository
    single<SimulationRepository> { SimulationRepositoryImpl(api = get()) }

    // Use cases
    factory { RunSimulationUseCase(repository = get()) }
    factory { StartOptimisationUseCase(repository = get()) }

    // ViewModels
    factory { DashboardViewModel(runSimulationUseCase = get(), repository = get()) }
    factory { SimulationSetupViewModel() }
    factory { AnalyticsViewModel(repository = get()) }
    factory { SobolViewModel(apiClient = get()) }
    factory { OptimisationViewModel(startOptimisationUseCase = get()) }
    factory { SettingsViewModel() }
}
