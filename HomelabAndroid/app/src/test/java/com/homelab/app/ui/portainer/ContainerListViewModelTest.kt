package com.homelab.app.ui.portainer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.homelab.app.MainDispatcherRule
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.domain.action.ControlledActionCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContainerListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `reads instance id from saved state and uses it for container loading`() = runTest {
        val repository = mockk<PortainerRepository>()
        coEvery { repository.getContainers("instance-123", 5, any()) } returns emptyList()

        val context = mockk<Context>(relaxed = true)
        val viewModel = ContainerListViewModel(
            repository = repository,
            controlledActionCoordinator = ControlledActionCoordinator(),
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "instanceId" to "instance-123",
                    "endpointId" to 5
                )
            )
        )

        advanceUntilIdle()

        assertEquals("instance-123", viewModel.instanceId)
        assertEquals(5, viewModel.endpointId)
        coVerify { repository.getContainers("instance-123", 5, true) }
    }
}
