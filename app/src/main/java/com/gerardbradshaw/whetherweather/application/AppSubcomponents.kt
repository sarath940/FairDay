package com.gerardbradshaw.whetherweather.application

import com.gerardbradshaw.whetherweather.activities.detail.DetailActivityComponent
import dagger.Module

@Module(subcomponents = [DetailActivityComponent::class])
interface AppSubcomponents