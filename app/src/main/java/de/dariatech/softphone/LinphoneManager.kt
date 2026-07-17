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
 * Dünner Wrapper um den Liblinphone-Core: Registrierung, Anrufe,
 * Stummschaltung und Lautsprecher. Die UI hängt sich über [listener] an.
 */
object LinphoneManager {

    interface Listener {
        fun onRegistration(state: RegistrationState?, message: String)
        fun onCallState(call: Call, state: Call.State?, message: String)
    }

    lateinit var core: Core
        private set

    var listener: Listener? = null
    private var domain: String = ""

    fun init(context: Context) {
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
                listener?.onCallState(call, state, message)
            }
        })
        core.start()
    }

    /** Meldet das Konto an; vorhandene Konten werden ersetzt. */
    fun login(username: String, password: String, domain: String, transport: TransportType) {
        this.domain = domain
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
