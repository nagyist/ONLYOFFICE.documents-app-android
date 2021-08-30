package app.editors.manager.onedrive.di.component

import app.editors.manager.di.component.AppComponent
import app.editors.manager.di.module.OneDriveModule
import app.editors.manager.di.module.OneDriveScope
import app.editors.manager.onedrive.onedrive.IOneDriveServiceProvider
import dagger.Component


@Component(modules = [OneDriveModule::class], dependencies = [AppComponent::class])
@OneDriveScope
interface OneDriveComponent {

    @Component.Builder
    interface Builder {

        fun appComponent(appComponent: AppComponent): Builder

        fun build(): OneDriveComponent

    }

    val oneDriveServiceProvider: IOneDriveServiceProvider
}