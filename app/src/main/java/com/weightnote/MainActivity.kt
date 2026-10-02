package com.weightnote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.weightnote.reminder.Notifications
import com.weightnote.ui.AppRoot
import com.weightnote.ui.EntryRequest
import com.weightnote.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels {
        viewModelFactory {
            initializer { MainViewModel(application, (application as WeightNoteApp).container) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 处理从提醒通知点进来的意图：切换身份并打开对应分组的记录面板 */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val groupId = intent.getLongExtra(Notifications.EXTRA_GROUP_ID, -1)
        val profileId = intent.getLongExtra(Notifications.EXTRA_PROFILE_ID, -1)
        if (groupId <= 0) return
        if (profileId > 0) vm.switchProfile(profileId)
        vm.entryRequest.value = EntryRequest(groupId)
        intent.removeExtra(Notifications.EXTRA_GROUP_ID)
    }
}
