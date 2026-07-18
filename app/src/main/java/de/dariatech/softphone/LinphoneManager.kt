package de.dariatech.softphone

import android.content.Context
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

/**
 * Wrapper um den Liblinphone-Core: Registrierung, Anrufe, Stummschaltung,
 * Lautsprecher, Halten, DTMF und die eigene Anrufliste. Die UI hängt sich
 * über [listener] an.
 */
object LinphoneManager {

    interface Listener {
        fun onRegistration(state: RegistrationState?, message: String)
        fun onCallState(call: Call, state: Call.State?, message: String)
    }

    lateinit var core: Core
        private set

    var listener: Listener? = null
    private var appContext: Context? = null

    // Verfolgung des aktuellen Anrufs für die Anrufliste
    private var trackNumber = ""
    private var trackIncoming = false
    private var trackConnected = false

    fun init(context: Context) {
        appContext = context.applicationContext
        val factory = Factory.instance()
        core = factory.createCore(null, null, context)
        core.isPushNotificationEnabled = false
        core.addListener(object : CoreListenerStub() {
            override fun onAccountRegistrationStateChanged(
                core: Core,
                account: Account,
                state: RegistrationState?,
                message: String
            ) {
                listener?.onRegistration(state, message)
            }

            override fun onCallStateChanged(
                core: Core,
                call: Call,
                state: Call.State?,
                message: String
            ) {
                trackCall(call, state)
                listener?.onCallState(call, state, message)
            }
        })
        core.start()
    }

    private fun trackCall(call: Call, state: Call.State?) {
        when (state) {
            Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                trackNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                trackIncoming = true
                trackConnected = false
            }
            Call.State.OutgoingInit -> {
                trackNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                trackIncoming = false
                trackConnected = false
            }
            Call.State.Connected, Call.State.StreamsRunning -> trackConnected = true
            Call.State.End, Call.State.Error -> {
                if (trackNumber.isNotEmpty()) {
                    val direction = when {
                        trackIncoming && !trackConnected -> "missed"
                        trackIncoming -> "in"
                        else -> "out"
                    }
                    appContext?.let {
                        CallLogStore.add(
                            it,
                            CallEntry(trackNumber, direction, System.currentTimeMillis(), call.duration)
                        )
                    }
                    trackNumber = ""
                }
            }
            else -> Unit
        }
    }

    /** Meldet das Konto an; vorhandene Konten werden ersetzt. */
    fun login(username: String, password: String, domain: String, transport: TransportType) {
        core.clearAccounts()
        core.clearAllAuthInfo()

        val authInfo = Factory.instance()
            .createAuthInfo(username, null, password, null, null, domain, null)
        core.addAuthInfo(authInfo)

        val params = core.createAccountParams()
        params.identityAddress = Factory.instance().createAddress("sip:$username@$domain")
        val server = Factory.instance().createAddress("sip:$domain")
        server?.transport = transport
        params.serverAddress = server
        params.isRegisterEnabled = true
        val account = core.createAccount(params)
        core.addAccount(account)
        core.defaultAccount = account
    }

    fun call(number: String) {
        val address = core.interpretUrl(number, true) ?: return
        val params = core.createCallParams(null)
        core.inviteAddressWithParams(address, params ?: return)
    }

    fun answer() {
        core.currentCall?.accept()
    }

    fun hangup() {
        if (core.callsNb > 0) {
            (core.currentCall ?: core.calls.firstOrNull())?.terminate()
        }
    }

    fun toggleMute(): Boolean {
        core.isMicEnabled = !core.isMicEnabled
        return !core.isMicEnabled
    }

    /** Anruf halten/fortsetzen; liefert true = wird gehalten. */
    fun toggleHold(): Boolean {
        val call = core.currentCall ?: core.calls.firstOrNull() ?: return false
        return if (call.state == Call.State.Paused || call.state == Call.State.Pausing) {
            call.resume()
            false
        } else {
            call.pause()
            true
        }
    }

    /** DTMF-Ton im laufenden Gespräch senden (IVR-Menüs). */
    fun sendDtmf(digit: Char) {
        core.currentCall?.sendDtmf(digit)
    }

    /** Laufzeit des aktiven Anrufs in Sekunden. */
    fun currentCallDuration(): Int = core.currentCall?.duration ?: 0

    /** Wechselt zwischen Hörmuschel und Lautsprecher; liefert true = Lautsprecher. */
    fun toggleSpeaker(): Boolean {
        val call = core.currentCall ?: return false
        val current = call.outputAudioDevice
        val wantSpeaker = current?.type != AudioDevice.Type.Speaker
        val target = core.audioDevices.firstOrNull {
            it.type == (if (wantSpeaker) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece) &&
                it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
        }
        if (target != null) call.outputAudioDevice = target
        return wantSpeaker
    }
}
