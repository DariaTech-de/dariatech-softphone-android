package de.dariatech.softphone

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import de.dariatech.softphone.databinding.ActivityMainBinding
import org.linphone.core.Call
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

class MainActivity : AppCompatActivity(), LinphoneManager.Listener {

    private lateinit var binding: ActivityMainBinding

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        micPermission.launch(Manifest.permission.RECORD_AUDIO)

        binding.transport.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("UDP", "TCP", "TLS"))
        )

        // Gespeicherte Zugangsdaten laden und automatisch anmelden
        val prefs = getSharedPreferences("sip", Context.MODE_PRIVATE)
        binding.username.setText(prefs.getString("username", ""))
        binding.password.setText(prefs.getString("password", ""))
        binding.domain.setText(prefs.getString("domain", "sip.easybell.de"))
        binding.transport.setText(prefs.getString("transport", "UDP"), false)
        if (!prefs.getString("username", "").isNullOrEmpty()) connect()

        binding.connectButton.setOnClickListener {
            prefs.edit()
                .putString("username", binding.username.text.toString().trim())
                .putString("password", binding.password.text.toString())
                .putString("domain", binding.domain.text.toString().trim())
                .putString("transport", binding.transport.text.toString())
                .apply()
            connect()
        }

        binding.callButton.setOnClickListener {
            val number = binding.number.text.toString().trim()
            if (number.isNotEmpty()) LinphoneManager.call(number)
        }
        binding.hangupButton.setOnClickListener { LinphoneManager.hangup() }
        binding.answerButton.setOnClickListener { LinphoneManager.answer() }
        binding.declineButton.setOnClickListener { LinphoneManager.hangup() }
        binding.muteButton.setOnClickListener {
            val muted = LinphoneManager.toggleMute()
            binding.muteButton.text = getString(
                if (muted) R.string.unmute else R.string.mute
            )
        }
        binding.speakerButton.setOnClickListener {
            val speaker = LinphoneManager.toggleSpeaker()
            binding.speakerButton.text = getString(
                if (speaker) R.string.earpiece else R.string.speaker
            )
        }

        LinphoneManager.listener = this
        showCallUi(inCall = false, ringing = false)
    }

    private fun connect() {
        val transport = when (binding.transport.text.toString()) {
            "TCP" -> TransportType.Tcp
            "TLS" -> TransportType.Tls
            else -> TransportType.Udp
        }
        LinphoneManager.login(
            binding.username.text.toString().trim(),
            binding.password.text.toString(),
            binding.domain.text.toString().trim(),
            transport
        )
        binding.status.text = getString(R.string.connecting)
    }

    override fun onRegistration(state: RegistrationState?, message: String) {
        runOnUiThread {
            when (state) {
                RegistrationState.Ok -> {
                    binding.status.text = getString(R.string.connected)
                    binding.statusDot.setBackgroundResource(R.drawable.dot_green)
                }
                RegistrationState.Progress -> binding.status.text = getString(R.string.connecting)
                RegistrationState.Failed -> {
                    binding.status.text = getString(R.string.reg_failed, message)
                    binding.statusDot.setBackgroundResource(R.drawable.dot_red)
                }
                else -> binding.status.text = message
            }
        }
    }

    override fun onCallState(call: Call, state: Call.State?, message: String) {
        runOnUiThread {
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    binding.callerInfo.text = call.remoteAddress.username ?: call.remoteAddress.asString()
                    showCallUi(inCall = false, ringing = true)
                }
                Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> {
                    binding.callerInfo.text = call.remoteAddress.username ?: ""
                    showCallUi(inCall = true, ringing = false)
                }
                Call.State.Connected, Call.State.StreamsRunning -> {
                    showCallUi(inCall = true, ringing = false)
                }
                Call.State.End, Call.State.Released, Call.State.Error -> {
                    showCallUi(inCall = false, ringing = false)
                }
                else -> Unit
            }
        }
    }

    private fun showCallUi(inCall: Boolean, ringing: Boolean) {
        binding.incomingGroup.visibility = if (ringing) View.VISIBLE else View.GONE
        binding.activeGroup.visibility = if (inCall) View.VISIBLE else View.GONE
        binding.callButton.visibility = if (!inCall && !ringing) View.VISIBLE else View.GONE
    }
}
